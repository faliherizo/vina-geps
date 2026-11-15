/**
 * **Gestion des effectifs — lot R8 (présence et pointage), de bout en bout.** AO 26-015-DO.
 *
 * Julie, à la caserne (position accordée au navigateur), entre, prend sa pause et en revient :
 * chaque pointage est conforme. Marc refuse la géolocalisation : son entrée, sans la preuve exigée,
 * attend l'approbation. Le planificateur la trouve dans Planification › Présence, lit les preuves et
 * l'approuve ; il affiche aussi le code QR de la caserne (E-GP-1, E-GP-11, E-GP-13). Chaque état
 * passe axe-core.
 */
import { test, expect, request as pwRequest, type APIRequestContext, type Browser } from "@playwright/test";
import { superadminContext, uiLogin } from "./helpers/auth";
import { createTenant, selectTenant, setTenantModules, TENANT_ADMIN_PASS, TENANT_ADMIN_USER, uniq } from "./helpers/organisme";
import { ouvrirNav } from "./helpers/nav";
import { expectNoA11yViolations } from "./helpers/a11y";
import { optionsFilmees } from "./helpers/film";

test.use({ storageState: { cookies: [], origins: [] } });

const API = `http://localhost:${process.env.E2E_API_PORT ?? "4000"}`;
const MDP = "Passe!Test2026";
const CASERNE = { latitude: 45.5017, longitude: -73.5673 };

async function poser(ctx: APIRequestContext, tenantId: string, chemin: string, data: unknown, methode: "post" | "patch" | "put" = "post") {
  const r = await ctx[methode](`${API}${chemin}`, { headers: { "X-Tenant-Id": tenantId }, data });
  if (!r.ok()) throw new Error(`amorce ${chemin} : ${r.status()} ${await r.text()}`);
  return r.json();
}

async function adminDeLOrganisme(tenantId: string): Promise<APIRequestContext> {
  const ctx = await pwRequest.newContext({ storageState: { cookies: [], origins: [] } });
  const { token } = await (await ctx.post(`${API}/api/auth/login`, { data: { username: TENANT_ADMIN_USER, password: TENANT_ADMIN_PASS, tenantId } })).json();
  await ctx.dispose();
  return pwRequest.newContext({ storageState: { cookies: [], origins: [] }, extraHTTPHeaders: { Authorization: `Bearer ${token}` } });
}

/** La date et l'heure (minutes depuis minuit) au Québec : le quart se planifie en heure locale. */
function maintenantAuQuebec(): { date: string; minutes: number } {
  const parts = new Intl.DateTimeFormat("en-CA", { timeZone: "America/Toronto", hourCycle: "h23", year: "numeric", month: "2-digit", day: "2-digit", hour: "2-digit", minute: "2-digit" }).formatToParts(new Date());
  const v = (t: string) => parts.find((p) => p.type === t)!.value;
  return { date: `${v("year")}-${v("month")}-${v("day")}`, minutes: Number(v("hour")) * 60 + Number(v("minute")) };
}

/** Un quart en cours pour Julie et Marc à la caserne 12, qui exige la distance au milieu. */
async function amorcer(tenantId: string, comptes: { julie: string; marc: string }): Promise<Record<string, string>> {
  const adm = await adminDeLOrganisme(tenantId);
  const ids: Record<string, string> = {};
  try {
    const sa = await superadminContext();
    await setTenantModules(sa, tenantId, ["effectifs_presence"]);
    await sa.dispose();
    const ref = (type: string, data: Record<string, unknown>) => poser(adm, tenantId, `/api/effectifs/referentiels/${type}`, { effectifDepuis: "2026-01-01", ...data });
    await ref("unites", { code: "EST", libelleFr: "Opérations Est" });
    await ref("corps_emploi", { code: "PARA", libelleFr: "Paramédics" });
    await ref("natures_quart", { code: "REG", libelleFr: "Régulier", comportement: "REGULIER" });
    await ref("types_milieu", { code: "CASERNE", libelleFr: "Caserne", comportement: "CASERNE" });
    const milieu = await poser(adm, tenantId, "/api/effectifs/milieux", { code: "C12", libelle: "Caserne 12", typeCode: "CASERNE", uniteCode: "EST", ...CASERNE, rayonMetres: 150 });
    await poser(adm, tenantId, `/api/effectifs/milieux/${milieu.id}/preuves`, { preuves: [{ mecanisme: "GEOREPERAGE", exige: true }, { mecanisme: "CODE_QR_DE_LIEU", exige: false }] }, "put");
    ids.milieu = milieu.id;
    for (const [cle, nom, identifiant, matricule] of [["julie", "Julie Roy", comptes.julie, "P1"], ["marc", "Marc Dubé", comptes.marc, "P2"]] as const) {
      const compte = await poser(adm, tenantId, "/api/auth/users", { username: identifiant, password: MDP, role: "employee" });
      await poser(adm, tenantId, `/api/auth/users/${compte.id}`, { mustChangePassword: false, securitySetupRequired: false }, "patch");
      ids[cle] = (await poser(adm, tenantId, "/api/effectifs/planification/ressources", { matricule, nom, corpsEmploiCode: "PARA", uniteCode: "EST", utilisateurId: compte.id })).id;
    }
    const { date, minutes } = maintenantAuQuebec();
    const debut = Math.max(0, minutes - 60);
    const fin = Math.min(1440, debut + 240);
    const p = await poser(adm, tenantId, "/api/effectifs/planification/periodes", { nom: "Semaine en cours", uniteCode: "EST", debut: date, fin: date });
    await poser(adm, tenantId, `/api/effectifs/planification/periodes/${p.id}/etat`, { etat: "CONFECTION" });
    const e = await poser(adm, tenantId, "/api/effectifs/planification/equipages", {
      periodeId: p.id, date, debut, fin, natureCode: "REG", milieuId: milieu.id, modeAttribution: "MANUEL", composition: [{ corpsEmploiCode: "PARA", nombre: 2 }],
    });
    await poser(adm, tenantId, `/api/effectifs/planification/places/${e.places[0].id}/attribuer`, { ressourceId: ids.julie });
    await poser(adm, tenantId, `/api/effectifs/planification/places/${e.places[1].id}/attribuer`, { ressourceId: ids.marc });
    await poser(adm, tenantId, `/api/effectifs/planification/periodes/${p.id}/etat`, { etat: "PUBLIEE" });
    return ids;
  } finally { await adm.dispose(); }
}

async function employe(browser: Browser, identifiant: string, tenantId: string, position: boolean) {
  const ctx = await browser.newContext(optionsFilmees({
    storageState: { cookies: [], origins: [] },
    ...(position ? { geolocation: { ...CASERNE, accuracy: 10 }, permissions: ["geolocation"] } : { permissions: [] }),
  }));
  const page = await ctx.newPage();
  await page.goto("/");
  await uiLogin(page, identifiant, MDP, tenantId);
  await ouvrirNav(page, "nav-monpointage");
  await expect(page.getByTestId("mpo-titre")).toBeVisible();
  return { ctx, page };
}

test("pointer à la caserne, pointer sans preuve, faire approuver l'écart", async ({ browser }) => {
  test.setTimeout(240_000);
  const comptes = { julie: uniq("julie").toLowerCase(), marc: uniq("marc").toLowerCase() };
  const sa = await superadminContext();
  const tenantId = await createTenant(sa, uniq("EffPre"));
  await sa.dispose();
  await amorcer(tenantId, comptes);

  // ── Julie, à la caserne ────────────────────────────────────────────────────────────────
  const julie = await employe(browser, comptes.julie, tenantId, true);
  await expect(julie.page.getByTestId("mpo-quart")).toContainText("Caserne 12");
  await expectNoA11yViolations(julie.page);
  await julie.page.getByTestId("mpo-ENTREE").click();
  await expect(julie.page.getByTestId("mpo-info")).toContainText("Entrée enregistrée");
  await expect(julie.page.getByTestId("mpo-info")).toContainText("Conforme.");
  await expect(julie.page.getByTestId("mpo-DEBUT_PAUSE")).toBeVisible();
  await julie.page.getByTestId("mpo-DEBUT_PAUSE").click();
  await expect(julie.page.getByTestId("mpo-info")).toContainText("Début de pause enregistrée");
  await expect(julie.page.getByTestId("mpo-table")).toContainText("Distance au milieu (0 m)");
  await expectNoA11yViolations(julie.page);
  await julie.ctx.close();

  // ── Marc, sans géolocalisation ─────────────────────────────────────────────────────────
  const marc = await employe(browser, comptes.marc, tenantId, false);
  await marc.page.getByTestId("mpo-position").uncheck();
  await marc.page.getByTestId("mpo-ENTREE").click();
  await expect(marc.page.getByTestId("mpo-info")).toContainText("Écart : preuve exigée absente : votre gestionnaire l'approuvera.");
  await expect(marc.page.getByTestId("mpo-table")).toContainText("À approuver");
  // La déclaration a posteriori est un dialogue : axe-core la mesure **ouverte**.
  await marc.page.getByTestId("mpo-declarer-ouvrir").click();
  await expect(marc.page.getByTestId("mpo-declarer-modal")).toBeVisible();
  await expectNoA11yViolations(marc.page);
  await marc.ctx.close();

  // ── Le planificateur tranche ───────────────────────────────────────────────────────────
  const ctxChef = await browser.newContext(optionsFilmees({ storageState: { cookies: [], origins: [] } }));
  const chef = await ctxChef.newPage();
  await selectTenant(chef, tenantId);
  await ouvrirNav(chef, "nav-uspresence");
  const trancher = chef.locator('[data-testid^="pla-pre-"][data-testid$="-trancher"]');
  await expect(trancher).toHaveCount(1);
  await trancher.click();
  await expect(chef.getByTestId("pla-pre-fiche")).toContainText("Distance au milieu (exigée) : non fournie");
  await expectNoA11yViolations(chef);
  await chef.getByTestId("pla-pre-motif").fill("Téléphone sans GPS, présence confirmée par le répartiteur");
  await chef.getByTestId("pla-pre-approuver").click();
  await expect(chef.getByTestId("pla-pre-bilan")).toContainText("Marc Dubé : pointage approuvé.");
  await chef.getByTestId("pla-pre-qr").locator("summary").click();
  await chef.getByTestId("pla-pre-milieu").selectOption({ label: "C12 — Caserne 12" });
  await chef.getByTestId("pla-pre-afficher-code").click();
  await expect(chef.getByTestId("pla-pre-code")).toContainText("il change toutes les cinq minutes");
  await expectNoA11yViolations(chef);
  await ctxChef.close();
});
