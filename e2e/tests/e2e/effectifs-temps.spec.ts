/**
 * **Gestion des effectifs — lot R9 (feuilles de temps), de bout en bout.** AO 26-015-DO.
 *
 * Julie prépare sa feuille de la semaine : son quart non pointé y figure, repris de l'horaire. Elle
 * le corrige (sortie plus tôt), la correction devient une version, puis elle soumet. Le planificateur
 * la relit dans Planification › Feuilles de temps, l'approuve et produit le lot de paie signé
 * (E-RF-1, E-RF-2, E-RF-4, E-RF-12, E-RF-14). Chaque état passe axe-core.
 */
import { test, expect, request as pwRequest, type APIRequestContext } from "@playwright/test";
import { superadminContext, uiLogin } from "./helpers/auth";
import { createTenant, selectTenant, setTenantModules, TENANT_ADMIN_PASS, TENANT_ADMIN_USER, uniq } from "./helpers/organisme";
import { ouvrirNav } from "./helpers/nav";
import { expectNoA11yViolations } from "./helpers/a11y";
import { optionsFilmees } from "./helpers/film";
import { raconter } from "./helpers/recit";

test.use({ storageState: { cookies: [], origins: [] } });

const API = `http://localhost:${process.env.E2E_API_PORT ?? "4000"}`;
const MDP = "Passe!Test2026";

async function poser(ctx: APIRequestContext, tenantId: string, chemin: string, data: unknown, methode: "post" | "patch" = "post") {
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

/** Un quart publié de Julie le mercredi 15 janvier 2031, et les types d'heures de l'organisme. */
async function amorcer(tenantId: string, identifiant: string): Promise<void> {
  const adm = await adminDeLOrganisme(tenantId);
  try {
    const sa = await superadminContext();
    await setTenantModules(sa, tenantId, ["effectifs_temps"]);
    await sa.dispose();
    const ref = (type: string, data: Record<string, unknown>) => poser(adm, tenantId, `/api/effectifs/referentiels/${type}`, { effectifDepuis: "2026-01-01", ...data });
    await ref("unites", { code: "EST", libelleFr: "Opérations Est" });
    await ref("corps_emploi", { code: "PARA", libelleFr: "Paramédics" });
    await ref("natures_quart", { code: "REG", libelleFr: "Régulier", comportement: "REGULIER" });
    await ref("types_milieu", { code: "CASERNE", libelleFr: "Caserne", comportement: "CASERNE" });
    await ref("types_heure", { code: "REG", libelleFr: "Régulier", comportement: "TRAVAILLEE", attributs: { codePaie: "100" } });
    await ref("types_heure", { code: "HS", libelleFr: "Heures supplémentaires", comportement: "SUPPLEMENTAIRE", attributs: { codePaie: "150" } });
    const milieu = await poser(adm, tenantId, "/api/effectifs/milieux", { code: "C12", libelle: "Caserne 12", typeCode: "CASERNE", uniteCode: "EST" });
    const compte = await poser(adm, tenantId, "/api/auth/users", { username: identifiant, password: MDP, role: "employee" });
    await poser(adm, tenantId, `/api/auth/users/${compte.id}`, { mustChangePassword: false, securitySetupRequired: false }, "patch");
    const julie = await poser(adm, tenantId, "/api/effectifs/planification/ressources", { matricule: "P1", nom: "Julie Roy", corpsEmploiCode: "PARA", uniteCode: "EST", utilisateurId: compte.id });
    const p = await poser(adm, tenantId, "/api/effectifs/planification/periodes", { nom: "Semaine 3", uniteCode: "EST", debut: "2031-01-13", fin: "2031-01-19" });
    await poser(adm, tenantId, `/api/effectifs/planification/periodes/${p.id}/etat`, { etat: "CONFECTION" });
    const e = await poser(adm, tenantId, "/api/effectifs/planification/equipages", {
      periodeId: p.id, date: "2031-01-15", debut: 360, fin: 1080, natureCode: "REG", milieuId: milieu.id, modeAttribution: "MANUEL", composition: [{ corpsEmploiCode: "PARA", nombre: 1 }],
    });
    await poser(adm, tenantId, `/api/effectifs/planification/places/${e.places[0].id}/attribuer`, { ressourceId: julie.id });
    await poser(adm, tenantId, `/api/effectifs/planification/periodes/${p.id}/etat`, { etat: "PUBLIEE" });
  } finally { await adm.dispose(); }
}

test("préparer, corriger et soumettre sa feuille ; l'approuver et produire le lot de paie", async ({ browser }) => {
  test.setTimeout(240_000);
  const identifiant = uniq("julie").toLowerCase();
  const sa = await superadminContext();
  const tenantId = await createTenant(sa, uniq("EffTps"));
  await sa.dispose();
  await amorcer(tenantId, identifiant);

  // ── Julie ──────────────────────────────────────────────────────────────────────────────
  const ctxJulie = await browser.newContext(optionsFilmees({ storageState: { cookies: [], origins: [] } }));
  const julie = await ctxJulie.newPage();
  await julie.goto("/");
  await uiLogin(julie, identifiant, MDP, tenantId);
  await ouvrirNav(julie, "nav-matemps");
  await raconter(julie, "Feuilles de temps. Julie Roy ouvre Ma feuille de temps et prépare celle de la semaine du 13 janvier. La feuille se remplit depuis son horaire et ses pointages ; un quart non pointé est signalé comme anomalie.");
  await expect(julie.getByTestId("mft-vide")).toBeVisible();
  // Préparer crée une feuille : un dialogue depuis le 8 novembre 2026.
  await julie.getByTestId("mft-preparation-ouvrir").click();
  await julie.getByTestId("mft-debut").fill("2031-01-13");
  await julie.getByTestId("mft-fin").fill("2031-01-19");
  await julie.getByTestId("mft-preparer").click();
  await expect(julie.getByTestId("mft-info")).toContainText("Feuille préparée depuis votre horaire et vos pointages.");
  await expect(julie.getByTestId("mft-segments")).toContainText("Horaire (non pointé)");
  await expect(julie.getByTestId("mft-anomalies")).toContainText("n'a pas été pointé");
  await expectNoA11yViolations(julie);
  await raconter(julie, "Elle corrige un segment : fin à 17 heures, avec le motif, départ plus tôt accordé par le chef. Le segment n'est pas écrasé : il passe à sa deuxième version.");
  await julie.locator('[data-testid^="mft-s-"][data-testid$="-modifier"]').click();
  await expect(julie.getByTestId("mft-edition")).toBeVisible();
  await julie.getByTestId("mft-e-fin").fill("17:00");
  await julie.getByTestId("mft-e-motif").fill("Départ plus tôt, accordé par le chef");
  await expectNoA11yViolations(julie);
  await julie.getByTestId("mft-e-enregistrer").click();
  await expect(julie.getByTestId("mft-info")).toContainText("Segment du 2031-01-15 corrigé.");
  await expect(julie.getByTestId("mft-segments")).toContainText("corrigé (v2)");
  await raconter(julie, "Elle soumet sa feuille, qui attend la décision. Puis elle la rappelle, pour la reprendre : la feuille revient au brouillon. Elle la soumet de nouveau.");
  await julie.getByTestId("mft-soumettre").click();
  await expect(julie.getByTestId("mft-info")).toContainText("Feuille soumise : elle attend la décision.");
  await expect(julie.getByTestId("mft-statut")).toContainText("Soumise");
  await expect(julie.getByTestId("mft-soumettre")).toHaveCount(0);
  // Avant la paie, Julie peut rappeler sa feuille pour la corriger, puis la soumettre de nouveau.
  await julie.getByTestId("mft-rappeler").click();
  await expectNoA11yViolations(julie);
  await julie.getByTestId("mft-rappel-confirm").click();
  await expect(julie.getByTestId("mft-info")).toContainText("Feuille rappelée : elle est revenue au brouillon.");
  await expect(julie.getByTestId("mft-statut")).toContainText("Brouillon");
  await julie.getByTestId("mft-soumettre").click();
  await expect(julie.getByTestId("mft-statut")).toContainText("Soumise");
  await ctxJulie.close();

  // ── Le planificateur ───────────────────────────────────────────────────────────────────
  const ctxChef = await browser.newContext(optionsFilmees({ storageState: { cookies: [], origins: [] } }));
  const chef = await ctxChef.newPage();
  await selectTenant(chef, tenantId);
  await ouvrirNav(chef, "nav-ustemps");
  await raconter(chef, "Le chef ouvre les feuilles de temps de la semaine, lit les totaux par code de paie, et approuve la feuille de Julie.");
  await chef.getByTestId("pla-tps-debut").fill("2031-01-13");
  const ouvrir = chef.locator('[data-testid^="pla-tps-"][data-testid$="-ouvrir"]');
  await expect(ouvrir).toHaveCount(1);
  await ouvrir.click();
  await expect(chef.getByTestId("pla-tps-fiche-totaux")).toContainText("REG");
  await expectNoA11yViolations(chef);
  await chef.getByTestId("pla-tps-approuver").click();
  await expect(chef.getByTestId("pla-tps-bilan")).toContainText("Feuille de Julie Roy : approuvée.");
  await raconter(chef, "Il produit le lot de paie : une feuille, avec son empreinte qui garantit que le lot n'a pas été modifié.");
  await chef.getByTestId("pla-tps-paie").locator("summary").click();
  await chef.getByTestId("pla-tps-produire").click();
  await expect(chef.getByTestId("pla-tps-lot")).toContainText("1 feuille(s)");
  await expect(chef.getByTestId("pla-tps-lot")).toContainText("Empreinte");
  await expectNoA11yViolations(chef);
  // Approuvée, pas encore payée : le chef la rouvre pour correction, avec un motif ; l'historique le dit.
  await raconter(chef, "Enfin, il rouvre la feuille approuvée, avec son motif : la formation du 17 est en heures régulières. La feuille retourne à Julie.");
  await chef.getByTestId("pla-tps-filtre").selectOption("APPROUVEE");
  await chef.locator('[data-testid^="pla-tps-"][data-testid$="-ouvrir"]').first().click();
  await chef.getByTestId("pla-tps-motif-rouvrir").fill("La formation du 17 est en heures régulières");
  await expectNoA11yViolations(chef);
  await chef.getByTestId("pla-tps-rouvrir").click();
  await expect(chef.getByTestId("pla-tps-bilan")).toContainText("Feuille de Julie Roy : retournée.");
  await ctxChef.close();
});
