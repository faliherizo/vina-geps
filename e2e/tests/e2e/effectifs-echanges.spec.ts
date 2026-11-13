/**
 * **Gestion des effectifs — lot R6 (échanges de quarts), de bout en bout.** AO 26-015-DO.
 *
 * Julie propose, depuis Mon espace › Mes échanges, son lundi contre le lundi suivant de Marc. La
 * règle de l'organisme dit « échange entre périodes : restreint » : Marc accepte, puis le chef
 * valide dans Planification › Échanges, et les quarts changent de mains (E-GR-7, E-GR-12). Chaque
 * état visité passe axe-core.
 */
import { test, expect, request as pwRequest, type APIRequestContext, type Browser } from "@playwright/test";
import { superadminContext, uiLogin } from "./helpers/auth";
import { createTenant, selectTenant, setTenantModules, TENANT_ADMIN_PASS, TENANT_ADMIN_USER, uniq } from "./helpers/organisme";
import { ouvrirNav } from "./helpers/nav";
import { expectNoA11yViolations } from "./helpers/a11y";

test.use({ storageState: { cookies: [], origins: [] } });

const API = `http://localhost:${process.env.E2E_API_PORT ?? "4000"}`;
const MDP = "Passe!Test2026";

/**
 * Les comptes d'employés se créent comme en vrai : par l'administrateur de l'organisme. Créé par le
 * superadmin, un compte naît sans organisme (compte global) et ses appels d'effectifs répondent 400.
 */
async function adminDeLOrganisme(tenantId: string): Promise<APIRequestContext> {
  const ctx = await pwRequest.newContext({ storageState: { cookies: [], origins: [] } });
  const r = await ctx.post(`${API}/api/auth/login`, { data: { username: TENANT_ADMIN_USER, password: TENANT_ADMIN_PASS, tenantId } });
  const { token } = await r.json();
  await ctx.dispose();
  return pwRequest.newContext({ storageState: { cookies: [], origins: [] }, extraHTTPHeaders: { Authorization: `Bearer ${token}` } });
}

async function poser(sa: APIRequestContext, tenantId: string, chemin: string, data: unknown, methode: "post" | "patch" = "post") {
  const r = await sa[methode](`${API}${chemin}`, { headers: { "X-Tenant-Id": tenantId }, data });
  if (!r.ok()) throw new Error(`amorce ${chemin} : ${r.status()} ${await r.text()}`);
  return r.json();
}

/** Deux paramédics, deux semaines publiées, une règle « entre périodes : le chef valide ». */
async function amorcer(tenantId: string, comptes: { julie: string; marc: string }): Promise<Record<string, string>> {
  const sa = await superadminContext();
  const ids: Record<string, string> = {};
  try {
    await setTenantModules(sa, tenantId, ["effectifs_echanges"]);
    const ref = (type: string, data: Record<string, unknown>) => poser(sa, tenantId, `/api/effectifs/referentiels/${type}`, { effectifDepuis: "2026-01-01", ...data });
    await ref("unites", { code: "EST", libelleFr: "Opérations Est" });
    await ref("corps_emploi", { code: "PARA", libelleFr: "Paramédics" });
    await ref("natures_quart", { code: "REG", libelleFr: "Régulier", comportement: "REGULIER" });
    await ref("types_milieu", { code: "CASERNE", libelleFr: "Caserne", comportement: "CASERNE" });
    const milieu = await poser(sa, tenantId, "/api/effectifs/milieux", { code: "C12", libelle: "Caserne 12", typeCode: "CASERNE", uniteCode: "EST" });
    for (const [cle, nom, identifiant] of [["julie", "Julie Roy", comptes.julie], ["marc", "Marc Dubé", comptes.marc]] as const) {
      const adm = await adminDeLOrganisme(tenantId);
      const compte = await poser(adm, tenantId, "/api/auth/users", { username: identifiant, password: MDP, role: "employee" });
      await poser(adm, tenantId, `/api/auth/users/${compte.id}`, { mustChangePassword: false, securitySetupRequired: false }, "patch");
      await adm.dispose();
      ids[cle] = (await poser(sa, tenantId, "/api/effectifs/planification/ressources", {
        matricule: cle === "julie" ? "P1" : "P2", nom, corpsEmploiCode: "PARA", uniteCode: "EST", utilisateurId: compte.id,
      })).id;
    }
    for (const [nom, debut, fin, qui] of [["Semaine 10", "2030-03-04", "2030-03-10", "julie"], ["Semaine 11", "2030-03-11", "2030-03-17", "marc"]] as const) {
      const p = await poser(sa, tenantId, "/api/effectifs/planification/periodes", { nom, uniteCode: "EST", debut, fin });
      await poser(sa, tenantId, `/api/effectifs/planification/periodes/${p.id}/etat`, { etat: "CONFECTION" });
      const e = await poser(sa, tenantId, "/api/effectifs/planification/equipages", {
        periodeId: p.id, date: debut, debut: 360, fin: 1080, natureCode: "REG", milieuId: milieu.id, modeAttribution: "MANUEL", composition: [{ corpsEmploiCode: "PARA", nombre: 1 }],
      });
      await poser(sa, tenantId, `/api/effectifs/planification/places/${e.places[0].id}/attribuer`, { ressourceId: ids[qui] });
      await poser(sa, tenantId, `/api/effectifs/planification/periodes/${p.id}/etat`, { etat: "PUBLIEE" });
      ids[`place_${qui}`] = e.places[0].id;
    }
    await poser(sa, tenantId, "/api/effectifs/regles", {
      contexte: "ECHANGE", cle: "entre-periodes", condition: { fait: "memePeriode", op: "=", valeur: false }, action: "EXIGER_APPROBATION",
      message: "échange entre deux périodes : le chef valide",
    });
    return ids;
  } finally { await sa.dispose(); }
}

async function employe(browser: Browser, identifiant: string, tenantId: string) {
  const ctx = await browser.newContext({ storageState: { cookies: [], origins: [] } });
  const page = await ctx.newPage();
  await page.goto("/");
  await uiLogin(page, identifiant, MDP, tenantId);
  await ouvrirNav(page, "nav-mesechanges");
  await expect(page.getByTestId("mec-titre")).toBeVisible();
  return { ctx, page };
}

test("proposer un échange entre périodes, l'accepter, le faire valider par le chef", async ({ browser }) => {
  // Trois acteurs, trois sessions, des contrôles axe à chaque état : le budget par défaut ne suffit pas sur une machine chargée.
  test.setTimeout(240_000);
  const comptes = { julie: uniq("julie").toLowerCase(), marc: uniq("marc").toLowerCase() };
  const systeme = await superadminContext();
  const tenantId = await createTenant(systeme, uniq("EffEch"));
  await systeme.dispose();
  const ids = await amorcer(tenantId, comptes);

  // ── Julie propose ──────────────────────────────────────────────────────────────────────
  const julie = await employe(browser, comptes.julie, tenantId);
  await julie.page.getByTestId("mec-cede").selectOption(ids.place_julie);
  await julie.page.getByTestId("mec-voulu").selectOption(ids.place_marc);
  await expectNoA11yViolations(julie.page);
  await julie.page.getByTestId("mec-proposer").click();
  await expect(julie.page.getByTestId("mec-info")).toContainText("Échange proposé à Marc Dubé. Une fois accepté, il devra être validé par le chef.");
  await expect(julie.page.getByTestId("mec-table")).toContainText("Proposé à Marc Dubé");
  await julie.ctx.close();

  // ── Marc accepte ───────────────────────────────────────────────────────────────────────
  const marc = await employe(browser, comptes.marc, tenantId);
  const accepter = marc.page.locator('[data-testid^="mec-"][data-testid$="-accepter"]');
  await expect(accepter).toHaveCount(1);
  await expect(marc.page.getByTestId("mec-table")).toContainText("échange entre deux périodes : le chef valide");
  await expectNoA11yViolations(marc.page);
  await accepter.click();
  await expect(marc.page.getByTestId("mec-info")).toContainText("Échange à valider par le chef.");
  await marc.ctx.close();

  // ── Le chef valide ─────────────────────────────────────────────────────────────────────
  const ctxChef = await browser.newContext({ storageState: { cookies: [], origins: [] } });
  const chef = await ctxChef.newPage();
  await selectTenant(chef, tenantId);
  await ouvrirNav(chef, "nav-usechanges");
  await chef.locator('[data-testid^="pla-ech-"][data-testid$="-trancher"]').click();
  await expect(chef.getByTestId("pla-ech-fiche-motifs")).toContainText("Pour Marc Dubé");
  await expectNoA11yViolations(chef);
  await chef.getByTestId("pla-ech-motif").fill("Remplacement couvert");
  await chef.getByTestId("pla-ech-valider").click();
  await expect(chef.getByTestId("pla-ech-bilan")).toContainText("Échange entre Julie Roy et Marc Dubé : appliqué.");
  await ctxChef.close();

  // Les quarts ont changé de mains.
  const sa = await superadminContext();
  const quartsJulie = await (await sa.get(`${API}/api/effectifs/echanges/ressources/${ids.julie}/quarts`, { headers: { "X-Tenant-Id": tenantId } })).json();
  await sa.dispose();
  expect(quartsJulie.map((q: { placeId: string }) => q.placeId)).toEqual([ids.place_marc]);
});
