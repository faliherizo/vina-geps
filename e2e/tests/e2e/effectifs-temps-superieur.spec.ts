/**
 * **Feuille de temps adaptée à l'emploi, soumise au supérieur hiérarchique — de bout en bout, contre le
 * Java.** docs/urgences-sante/ETUDE-FEUILLE-DE-TEMPS-DYNAMIQUE.md.
 *
 * L'administration définit un champ « Véhicule » (liste, obligatoire), désigne Marc comme supérieur de
 * Julie et règle le flux : un palier, « supérieur hiérarchique ». Julie ajoute un segment avec la
 * description de ses tâches et son véhicule, voit son nom dans le tableau, soumet, et lit « En attente
 * de : Marc ». Marc, sans aucun périmètre sur l'unité, trouve la feuille dans la console et l'approuve.
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

type Comptes = { julie: string; marc: string };

/** L'organisme : Julie et Marc ont chacun un compte et une ressource (donc une fiche du personnel) ; un quart publié pour Julie. */
async function amorcer(tenantId: string, comptes: Comptes): Promise<void> {
  const adm = await adminDeLOrganisme(tenantId);
  try {
    const sa = await superadminContext();
    await setTenantModules(sa, tenantId, ["effectifs_temps", "effectifs_presence"]);
    await sa.dispose();
    const ref = (type: string, data: Record<string, unknown>) => poser(adm, tenantId, `/api/effectifs/referentiels/${type}`, { effectifDepuis: "2026-01-01", ...data });
    await ref("unites", { code: "EST", libelleFr: "Opérations Est" });
    await ref("corps_emploi", { code: "PARA", libelleFr: "Paramédics" });
    await ref("natures_quart", { code: "REG", libelleFr: "Régulier", comportement: "REGULIER" });
    await ref("types_milieu", { code: "CASERNE", libelleFr: "Caserne", comportement: "CASERNE" });
    await ref("types_heure", { code: "REG", libelleFr: "Régulier", comportement: "TRAVAILLEE", attributs: { codePaie: "100" } });
    const milieu = await poser(adm, tenantId, "/api/effectifs/milieux", { code: "C12", libelle: "Caserne 12", typeCode: "CASERNE", uniteCode: "EST" });
    const ids: Record<string, string> = {};
    for (const [cle, role] of [["julie", "employee"], ["marc", "manager"]] as const) {
      const compte = await poser(adm, tenantId, "/api/auth/users", { username: comptes[cle], password: MDP, role });
      await poser(adm, tenantId, `/api/auth/users/${compte.id}`, { mustChangePassword: false, securitySetupRequired: false }, "patch");
      ids[cle] = compte.id;
    }
    const julie = await poser(adm, tenantId, "/api/effectifs/planification/ressources", { matricule: "P1", nom: "Julie Roy", corpsEmploiCode: "PARA", uniteCode: "EST", utilisateurId: ids.julie });
    await poser(adm, tenantId, "/api/effectifs/planification/ressources", { matricule: "P2", nom: "Marc Lavoie", corpsEmploiCode: "PARA", uniteCode: "EST", utilisateurId: ids.marc });
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

test("champ de l'organisme, description des tâches, soumission au supérieur hiérarchique qui approuve", async ({ browser }) => {
  test.setTimeout(300_000);
  const comptes: Comptes = { julie: uniq("julie").toLowerCase(), marc: uniq("marc").toLowerCase() };
  const sa = await superadminContext();
  const tenantId = await createTenant(sa, uniq("EffSup"));
  await sa.dispose();
  await amorcer(tenantId, comptes);

  // ── L'administration : un champ, un supérieur, un flux ───────────────────────────────────
  const ctxAdm = await browser.newContext(optionsFilmees({ storageState: { cookies: [], origins: [] } }));
  const adm = await ctxAdm.newPage();
  await selectTenant(adm, tenantId);
  await ouvrirNav(adm, "nav-ustemps");
  await adm.getByTestId("feuilles-champs-ouvrir").click();
  await expect(adm.getByTestId("pla-tps-champs-vide")).toBeVisible();
  await adm.getByTestId("pla-tps-champ-libelle").fill("Véhicule");
  await adm.getByTestId("pla-tps-champ-code").fill("vehicule");
  await adm.getByTestId("pla-tps-champ-type").selectOption("liste");
  await adm.getByTestId("pla-tps-champ-valeurs").fill("A-12\nB-07");
  await adm.getByTestId("pla-tps-champ-requis").check();
  await adm.getByTestId("pla-tps-champ-definir").click();
  await expect(adm.getByTestId("pla-tps-champs-info")).toContainText("Champ « Véhicule » ajouté");
  await expect(adm.getByTestId("pla-tps-champ-vehicule")).toContainText("A-12, B-07");
  await expectNoA11yViolations(adm);
  await adm.keyboard.press("Escape");

  await adm.getByTestId("feuilles-superieurs-ouvrir").click();
  const personne = adm.getByTestId("pla-tps-sup-personne");
  await expect(personne.locator("option", { hasText: "Julie Roy" })).toHaveCount(1, { timeout: 30_000 });
  await personne.selectOption({ label: "Julie Roy" });
  await adm.getByTestId("pla-tps-sup-superieur").selectOption({ label: "Marc Lavoie" });
  await adm.getByTestId("pla-tps-sup-enregistrer").click();
  await expect(adm.getByTestId("pla-tps-sup-info")).toContainText("Julie Roy relève maintenant de Marc Lavoie.");
  await expect(adm.getByTestId("pla-tps-sup-table")).toContainText("Marc Lavoie");
  await expectNoA11yViolations(adm);
  await adm.keyboard.press("Escape");

  await adm.getByTestId("feuilles-approbations-ouvrir").click();
  await expect(adm.getByTestId("pla-tps-flux-FEUILLE")).toContainText("flux par défaut");
  await adm.getByTestId("pla-tps-flux-type").selectOption("SUPERIEUR");
  await expect(adm.getByTestId("pla-tps-flux-superieur")).toBeVisible();
  await adm.getByTestId("pla-tps-flux-ajouter").click();
  await expect(adm.getByTestId("pla-tps-flux-info")).toContainText("Palier 1 ajouté au flux des feuilles de temps");
  await expect(adm.getByTestId("pla-tps-flux-FEUILLE")).toContainText("supérieur hiérarchique de la personne");
  await expectNoA11yViolations(adm);
  await ctxAdm.close();

  // ── Julie : sa feuille, un segment décrit, le véhicule exigé, la soumission ───────────────
  const { ctx: ctxJulie, page: julie } = await seConnecter(browser, comptes.julie, tenantId);
  await ouvrirNav(julie, "nav-matemps");
  await julie.getByTestId("mft-preparation-ouvrir").click();
  await julie.getByTestId("mft-debut").fill("2031-03-10");
  await julie.getByTestId("mft-fin").fill("2031-03-16");
  await julie.getByTestId("mft-preparer").click();
  await expect(julie.getByTestId("mft-info")).toContainText("Feuille préparée");
  await expect(julie.getByTestId("mft-ressource")).toContainText("Julie Roy");
  await expect(julie.getByTestId("mft-ressource")).toContainText("P1");
  await expect(julie.getByTestId("mft-segments")).toContainText("Julie Roy (P1)");

  await julie.getByTestId("mft-ajouter-ouvrir").click();
  await julie.getByTestId("mft-a-date").fill("2031-03-14");
  await julie.getByTestId("mft-a-debut").fill("08:00");
  await julie.getByTestId("mft-a-fin").fill("12:00");
  await julie.getByTestId("mft-a-type").selectOption("REG");
  await julie.getByTestId("mft-a-description").fill("Formation RCR donnée à l'équipe de nuit");
  await expect(julie.getByTestId("mft-a-champ-vehicule")).toBeVisible();
  await expectNoA11yViolations(julie);
  await julie.getByTestId("mft-a-champ-vehicule").selectOption("B-07");
  await julie.getByTestId("mft-a-envoyer").click();
  await expect(julie.getByTestId("mft-info")).toContainText("Segment du 2031-03-14 ajouté");
  const ligne = julie.locator('tr[data-testid^="mft-s-"]').filter({ hasText: "Formation RCR" });
  await expect(ligne).toContainText("B-07");
  await expect(julie.getByTestId("mft-segments").locator("th", { hasText: "Véhicule" })).toHaveCount(1);
  await expect(julie.getByTestId("mft-segments").locator("th", { hasText: "Tâches" })).toHaveCount(1);

  await julie.getByTestId("mft-soumettre").click();
  await expect(julie.getByTestId("mft-info")).toContainText("Feuille soumise");
  await expect(julie.getByTestId("mft-attente")).toHaveText(`En attente de : ${comptes.marc} (personne désignée)`);
  await expectNoA11yViolations(julie);
  await ctxJulie.close();

  // ── Marc, son supérieur, sans périmètre sur l'unité : la feuille lui est désignée ─────────
  const { ctx: ctxMarc, page: marc } = await seConnecter(browser, comptes.marc, tenantId);
  await ouvrirNav(marc, "nav-ustemps");
  const ouvrir = marc.locator('[data-testid^="pla-tps-"][data-testid$="-ouvrir"]');
  await expect(ouvrir).toHaveCount(1);
  await ouvrir.click();
  await expect(marc.getByTestId("pla-tps-fiche-attente")).toContainText(`${comptes.marc} (personne désignée)`);
  await expect(marc.getByTestId("pla-tps-fiche-segments")).toContainText("Formation RCR donnée à l'équipe de nuit");
  await expect(marc.getByTestId("pla-tps-fiche-segments")).toContainText("B-07");
  await expectNoA11yViolations(marc);
  await marc.getByTestId("pla-tps-approuver").click();
  await expect(marc.getByTestId("pla-tps-bilan")).toContainText("Feuille de Julie Roy : approuvée.");
  await ctxMarc.close();
});
