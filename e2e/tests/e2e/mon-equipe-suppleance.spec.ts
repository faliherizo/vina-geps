/**
 * **Mon équipe et la suppléance — de bout en bout, contre le Java** (comme dans Nexu VINA).
 *
 * Marc, gestionnaire, voit Julie dans son équipe et y rattache Léa, encore sans supérieur. Avant son
 * absence, il désigne Diane, directrice sans aucun périmètre sur l'unité, comme suppléante. Julie
 * soumet sa feuille : elle lit « en son absence : … », et la feuille parvient à Diane, qui la trouve
 * dans « Mon équipe » et l'approuve pour le compte de Marc. À son retour, Marc met fin à la suppléance.
 * Chaque état passe axe-core.
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

type Comptes = { julie: string; marc: string; diane: string; lea: string };

/** L'organisme : Julie, Marc, Diane et Léa ont chacun un compte et une ressource (donc une fiche) ; un quart publié pour Julie. */
async function amorcer(tenantId: string, comptes: Comptes): Promise<void> {
  const adm = await adminDeLOrganisme(tenantId);
  try {
    const sa = await superadminContext();
    await setTenantModules(sa, tenantId, ["effectifs", "effectifs_temps", "effectifs_presence", "approvals"]);
    await sa.dispose();
    const ref = (type: string, data: Record<string, unknown>) => poser(adm, tenantId, `/api/effectifs/referentiels/${type}`, { effectifDepuis: "2026-01-01", ...data });
    await ref("unites", { code: "EST", libelleFr: "Opérations Est" });
    await ref("corps_emploi", { code: "PARA", libelleFr: "Paramédics" });
    await ref("natures_quart", { code: "REG", libelleFr: "Régulier", comportement: "REGULIER" });
    await ref("types_milieu", { code: "CASERNE", libelleFr: "Caserne", comportement: "CASERNE" });
    await ref("types_heure", { code: "REG", libelleFr: "Régulier", comportement: "TRAVAILLEE", attributs: { codePaie: "100" } });
    const milieu = await poser(adm, tenantId, "/api/effectifs/milieux", { code: "C12", libelle: "Caserne 12", typeCode: "CASERNE", uniteCode: "EST" });
    const ids: Record<string, string> = {};
    for (const [cle, role] of [["julie", "employee"], ["marc", "manager"], ["diane", "director"], ["lea", "employee"]] as const) {
      const compte = await poser(adm, tenantId, "/api/auth/users", { username: comptes[cle], password: MDP, role });
      await poser(adm, tenantId, `/api/auth/users/${compte.id}`, { mustChangePassword: false, securitySetupRequired: false }, "patch");
      ids[cle] = compte.id;
    }
    const julie = await poser(adm, tenantId, "/api/effectifs/planification/ressources", { matricule: "P1", nom: "Julie Roy", corpsEmploiCode: "PARA", uniteCode: "EST", utilisateurId: ids.julie });
    await poser(adm, tenantId, "/api/effectifs/planification/ressources", { matricule: "P2", nom: "Marc Lavoie", corpsEmploiCode: "PARA", uniteCode: "EST", utilisateurId: ids.marc });
    await poser(adm, tenantId, "/api/effectifs/planification/ressources", { matricule: "D1", nom: "Diane Roy", corpsEmploiCode: "PARA", uniteCode: "EST", utilisateurId: ids.diane });
    await poser(adm, tenantId, "/api/effectifs/planification/ressources", { matricule: "P3", nom: "Léa Morin", corpsEmploiCode: "PARA", uniteCode: "EST", utilisateurId: ids.lea });
    // L'organigramme : Julie relève de Marc ; Léa n'a pas encore de supérieur. Le flux des feuilles va au supérieur.
    const fiches = await (await adm.get(`${API}/api/personnel/hierarchie`, { headers: { "X-Tenant-Id": tenantId } })).json() as Array<{ id: string; nom: string }>;
    const fiche = (nom: string) => fiches.find((f) => f.nom === nom)!.id;
    const r = await adm.put(`${API}/api/personnel/employes/${fiche("Julie Roy")}/superieur`, { headers: { "X-Tenant-Id": tenantId }, data: { superieurId: fiche("Marc Lavoie") } });
    if (!r.ok()) throw new Error(`organigramme : ${r.status()} ${await r.text()}`);
    await poser(adm, tenantId, "/api/effectifs/temps/flux", { objet: "FEUILLE", palier: 1, champ: "toujours", operateur: "eq", valeur: 1, approbateur: { type: "SUPERIEUR" } });
    const p = await poser(adm, tenantId, "/api/effectifs/planification/periodes", { nom: "Semaine 11", uniteCode: "EST", debut: "2031-03-10", fin: "2031-03-16" });
    await poser(adm, tenantId, `/api/effectifs/planification/periodes/${p.id}/etat`, { etat: "CONFECTION" });
    const e = await poser(adm, tenantId, "/api/effectifs/planification/equipages", {
      periodeId: p.id, date: "2031-03-12", debut: 360, fin: 1080, natureCode: "REG", milieuId: milieu.id, modeAttribution: "MANUEL", composition: [{ corpsEmploiCode: "PARA", nombre: 1 }],
    });
    await poser(adm, tenantId, `/api/effectifs/planification/places/${e.places[0].id}/attribuer`, { ressourceId: julie.id });
    await poser(adm, tenantId, `/api/effectifs/planification/periodes/${p.id}/etat`, { etat: "PUBLIEE" });
  } finally { await adm.dispose(); }
}

async function seConnecter(browser: Browser, identifiant: string, tenantId: string) {
  const ctx = await browser.newContext(optionsFilmees({ storageState: { cookies: [], origins: [] } }));
  const page = await ctx.newPage();
  await page.goto("/");
  await uiLogin(page, identifiant, MDP, tenantId);
  return { ctx, page };
}

test("Mon équipe : voir, rattacher, désigner un suppléant ; la feuille va au suppléant, qui l'approuve ; fin de la suppléance", async ({ browser }) => {
  test.setTimeout(300_000);
  const comptes: Comptes = { julie: uniq("julie").toLowerCase(), marc: uniq("marc").toLowerCase(), diane: uniq("diane").toLowerCase(), lea: uniq("lea").toLowerCase() };
  const sa = await superadminContext();
  const tenantId = await createTenant(sa, uniq("EffEquipe"));
  await sa.dispose();
  await amorcer(tenantId, comptes);

  // ── Marc : son équipe, Léa rattachée, Diane suppléante ────────────────────────────────────
  const { ctx: ctxMarc, page: marc } = await seConnecter(browser, comptes.marc, tenantId);
  await ouvrirNav(marc, "nav-monequipe");
  await expect(marc.getByTestId("monequipe-titre")).toBeVisible();
  const julie = marc.locator('[data-testid^="meq-membre-"]').filter({ hasText: "Julie Roy" });
  await expect(julie).toContainText("Direct");
  await expectNoA11yViolations(marc);
  await marc.getByTestId("meq-rattacher-ouvrir").click();
  await marc.getByTestId("meq-rattacher-personne").selectOption({ label: "Léa Morin" });
  await marc.getByTestId("meq-rattacher-enregistrer").click();
  await expect(marc.getByTestId("meq-info")).toHaveText("Léa Morin rejoint votre équipe.");
  await expect(marc.locator('[data-testid^="meq-membre-"]').filter({ hasText: "Léa Morin" })).toContainText("Marc Lavoie");

  await marc.getByTestId("meq-suppleance-ouvrir").click();
  await marc.getByTestId("meq-suppleant").fill("Diane");
  const option = marc.locator('[data-testid^="meq-suppleant-option-"]').first();
  await expect(option).toContainText("Diane", { timeout: 10_000 });
  await option.click();
  await expectNoA11yViolations(marc);
  await marc.getByTestId("meq-deleguer").click();
  await expect(marc.getByTestId("meq-suppleance-info")).toContainText("Suppléance enregistrée");
  await expect(marc.getByTestId("meq-delegations")).toContainText("en cours");
  await ctxMarc.close();

  // ── Julie : sa feuille part vers la suppléante, et elle le lit ────────────────────────────
  const { ctx: ctxJulie, page: pJulie } = await seConnecter(browser, comptes.julie, tenantId);
  await ouvrirNav(pJulie, "nav-matemps");
  await pJulie.getByTestId("mft-preparation-ouvrir").click();
  await pJulie.getByTestId("mft-debut").fill("2031-03-10");
  await pJulie.getByTestId("mft-fin").fill("2031-03-16");
  await pJulie.getByTestId("mft-preparer").click();
  await expect(pJulie.getByTestId("mft-info")).toContainText("Feuille préparée");
  await pJulie.getByTestId("mft-soumettre").click();
  await expect(pJulie.getByTestId("mft-info")).toContainText("Feuille soumise");
  await expect(pJulie.getByTestId("mft-attente")).toContainText(`${comptes.marc} (personne désignée) — en son absence : ${comptes.diane}`);
  await expectNoA11yViolations(pJulie);
  await ctxJulie.close();

  // ── Diane, sans périmètre sur l'unité : la feuille lui parvient, elle l'approuve ──────────
  const { ctx: ctxDiane, page: diane } = await seConnecter(browser, comptes.diane, tenantId);
  await ouvrirNav(diane, "nav-monequipe");
  await expect(diane.getByTestId("meq-feuilles-table")).toContainText("Julie Roy (P1)");
  await expectNoA11yViolations(diane);
  await diane.getByTestId("meq-vers-console").click();
  const ouvrir = diane.locator('[data-testid^="pla-tps-"][data-testid$="-ouvrir"]');
  await expect(ouvrir).toHaveCount(1);
  await ouvrir.click();
  await expect(diane.getByTestId("pla-tps-fiche-attente")).toContainText("en son absence");
  await diane.getByTestId("pla-tps-approuver").click();
  await expect(diane.getByTestId("pla-tps-bilan")).toContainText("Feuille de Julie Roy : approuvée.");
  await ctxDiane.close();

  // ── Marc revient : il met fin à la suppléance ─────────────────────────────────────────────
  const { ctx: ctxRetour, page: retour } = await seConnecter(browser, comptes.marc, tenantId);
  await ouvrirNav(retour, "nav-monequipe");
  await retour.locator('[data-testid^="meq-deleg-"][data-testid$="-terminer"]').first().click();
  await retour.getByTestId("meq-fin-confirm").click();
  await expect(retour.getByTestId("meq-suppleance-info")).toContainText("terminée : vos feuilles vous reviennent");
  await ctxRetour.close();
});
