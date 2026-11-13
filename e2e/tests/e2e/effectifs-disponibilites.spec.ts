/**
 * **Gestion des effectifs — lot R4 (disponibilités), de bout en bout.** AO 26-015-DO.
 *
 * Julie, paramédic, dépose ses disponibilités de la semaine depuis son espace. Un seul quart
 * offert : la règle de l'organisme refuse, et l'écran dit quoi corriger avec ses propres chiffres
 * (E-GN-15, E-GN-16). Elle complète, offre un samedi : le dépôt passe, mais attend l'accord de son
 * gestionnaire. Côté planificateur, Julie est exclue du bassin **avec son motif** tant que le dépôt
 * n'est pas approuvé (E-GN-14) ; approuvée, elle y entre, et l'attribution de fin de collecte lui
 * donne les quarts (P-GH-4). Chaque état visité passe axe-core.
 */
import { test, expect, request as pwRequest, type APIRequestContext } from "@playwright/test";
import { superadminContext, uiLogin } from "./helpers/auth";
import { createTenant, selectTenant, setTenantModules, TENANT_ADMIN_PASS, TENANT_ADMIN_USER, uniq } from "./helpers/organisme";
import { ouvrirNav } from "./helpers/nav";
import { expectNoA11yViolations } from "./helpers/a11y";

test.use({ storageState: { cookies: [], origins: [] } });

const API = `http://localhost:${process.env.E2E_API_PORT ?? "4000"}`;
const DEBUT = "2030-03-04";
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

async function poser(sa: APIRequestContext, tenantId: string, chemin: string, data: unknown, methode: "post" | "put" | "patch" = "post") {
  const r = await sa[methode](`${API}${chemin}`, { headers: { "X-Tenant-Id": tenantId }, data });
  if (!r.ok()) throw new Error(`amorce ${chemin} : ${r.status()} ${await r.text()}`);
  return r.json();
}

/** Une caserne, une paramédic reliée à son compte, deux quarts automatisés, une collecte ouverte, deux règles. */
async function amorcer(tenantId: string, identifiant: string): Promise<{ periodeId: string }> {
  const sa = await superadminContext();
  try {
    await setTenantModules(sa, tenantId, ["effectifs_disponibilites"]);
    const ref = (type: string, data: Record<string, unknown>) => poser(sa, tenantId, `/api/effectifs/referentiels/${type}`, { effectifDepuis: "2026-01-01", ...data });
    await ref("unites", { code: "EST", libelleFr: "Opérations Est" });
    await ref("corps_emploi", { code: "PARA", libelleFr: "Paramédics" });
    await ref("statuts_emploi", { code: "TPR", libelleFr: "Temps partiel régulier", comportement: "TEMPS_PARTIEL_REGULIER" });
    await ref("natures_quart", { code: "REG", libelleFr: "Régulier", comportement: "REGULIER" });
    await ref("types_milieu", { code: "CASERNE", libelleFr: "Caserne", comportement: "CASERNE" });
    const milieu = await poser(sa, tenantId, "/api/effectifs/milieux", { code: "C12", libelle: "Caserne 12", typeCode: "CASERNE", uniteCode: "EST" });
    const adm = await adminDeLOrganisme(tenantId);
    const compte = await poser(adm, tenantId, "/api/auth/users", { username: identifiant, password: MDP, role: "employee" });
    await poser(adm, tenantId, `/api/auth/users/${compte.id}`, { mustChangePassword: false, securitySetupRequired: false }, "patch");
    await adm.dispose();
    await poser(sa, tenantId, "/api/effectifs/planification/ressources", {
      matricule: "P1", nom: "Julie Roy", corpsEmploiCode: "PARA", uniteCode: "EST", statutEmploiCode: "TPR", anciennete: 4, utilisateurId: compte.id,
    });
    const periode = await poser(sa, tenantId, "/api/effectifs/planification/periodes", { nom: "Semaine 10", uniteCode: "EST", debut: DEBUT, fin: "2030-03-10" });
    for (const date of [DEBUT, "2030-03-05"]) {
      await poser(sa, tenantId, "/api/effectifs/planification/equipages", {
        periodeId: periode.id, date, debut: 360, fin: 1080, natureCode: "REG", milieuId: milieu.id, modeAttribution: "AUTOMATISE",
        composition: [{ corpsEmploiCode: "PARA", nombre: 1 }],
      });
    }
    await poser(sa, tenantId, `/api/effectifs/planification/periodes/${periode.id}/etat`, { etat: "COLLECTE", finCollecte: "2030-03-01" });
    await poser(sa, tenantId, "/api/effectifs/regles", {
      contexte: "DISPONIBILITE", cle: "minimum-quarts", condition: { fait: "quartsParSemaine", op: "<", valeur: 2 }, action: "BLOQUER",
      message: "Au moins 2 quarts par semaine ({quartsParSemaine} offert)",
    });
    await poser(sa, tenantId, "/api/effectifs/regles", {
      contexte: "DISPONIBILITE", cle: "fins-de-semaine", condition: { fait: "finsDeSemaine", op: ">", valeur: 0 }, action: "EXIGER_APPROBATION",
      message: "Une fin de semaine offerte demande l'accord du chef",
    });
    return { periodeId: periode.id };
  } finally { await sa.dispose(); }
}

test("déposer ses disponibilités, être refusé avec le motif, puis approuvé et retenu par l'attribution", async ({ browser }) => {
  // Trois acteurs, trois sessions, des contrôles axe à chaque état : le budget par défaut ne suffit pas sur une machine chargée.
  test.setTimeout(240_000);
  const identifiant = uniq("julie").toLowerCase();
  const systeme = await superadminContext();
  const tenantId = await createTenant(systeme, uniq("EffDispo"));
  await systeme.dispose();
  const { periodeId } = await amorcer(tenantId, identifiant);

  // ── L'employée, depuis son espace ──────────────────────────────────────────────────────
  const ctxJulie = await browser.newContext({ storageState: { cookies: [], origins: [] } });
  const julie = await ctxJulie.newPage();
  await julie.goto("/");
  await uiLogin(julie, identifiant, MDP, tenantId);
  await ouvrirNav(julie, "nav-mesdisponibilites");
  await expect(julie.getByTestId("mdi-titre")).toBeVisible();
  await expect(julie.getByTestId("mdi-etat")).toContainText("Collecte ouverte jusqu'au 2030-03-01");
  await expect(julie.getByTestId("mdi-conformite")).toContainText("Aucun dépôt");

  await julie.getByTestId("mdi-ajouter").click();
  await julie.getByTestId("mdi-deposer").click();
  await expect(julie.getByTestId("mdi-refus")).toContainText("Au moins 2 quarts par semaine (1 offert)");
  await expectNoA11yViolations(julie);

  await julie.getByTestId("mdi-ajouter").click();
  await julie.getByTestId("mdi-plage-1-type").selectOption("PREFERENCE");
  await julie.getByTestId("mdi-ajouter").click();
  await julie.getByTestId("mdi-plage-2-date").selectOption("2030-03-09");
  await julie.getByTestId("mdi-deposer").click();
  await expect(julie.getByTestId("mdi-confirme")).toContainText("attend l'approbation de votre gestionnaire");
  await expect(julie.getByTestId("mdi-refus")).toHaveCount(0);
  await expect(julie.getByTestId("mdi-conformite")).toContainText("En attente d'approbation");
  await expect(julie.getByTestId("mdi-avertissements")).toContainText("Une fin de semaine offerte demande l'accord du chef");
  await expectNoA11yViolations(julie);
  await ctxJulie.close();

  // ── Le planificateur : bassin, motif d'exclusion, approbation ─────────────────────────
  const ctxChef = await browser.newContext({ storageState: { cookies: [], origins: [] } });
  const chef = await ctxChef.newPage();
  await selectTenant(chef, tenantId);
  await ouvrirNav(chef, "nav-usdisponibilites");
  await expect(chef.getByTestId("pla-dispo-P1-conformite")).toContainText("En attente d'approbation");
  await chef.getByTestId("pla-dispo-place").selectOption({ index: 1 });
  await chef.getByTestId("pla-dispo-candidats").click();
  await expect(chef.getByTestId("pla-dispo-exclus")).toContainText("en attente d'approbation");
  await expectNoA11yViolations(chef);

  await chef.getByTestId("pla-dispo-P1-voir").click();
  await expect(chef.getByTestId("pla-dispo-fiche-motifs")).toContainText("fin de semaine");
  await expectNoA11yViolations(chef);
  await chef.getByTestId("pla-dispo-approuver").click();
  await expect(chef.getByTestId("pla-dispo-P1-conformite")).toContainText("Conforme");
  await chef.getByTestId("pla-dispo-candidats").click();
  await expect(chef.getByTestId("pla-dispo-admissibles")).toContainText("Julie Roy");
  await expect(chef.getByTestId("pla-dispo-attribuer")).toBeDisabled();

  // ── Collecte fermée : l'attribution de fin de collecte ────────────────────────────────
  const sa = await superadminContext();
  await poser(sa, tenantId, `/api/effectifs/planification/periodes/${periodeId}/etat`, { etat: "CONFECTION" });
  await sa.dispose();
  await chef.reload();
  await chef.getByTestId("pla-dispo-attribuer").click();
  await expect(chef.getByTestId("pla-dispo-bilan")).toContainText("2 place(s) attribuée(s)");
  await expect(chef.getByTestId("pla-dispo-place")).toContainText("Aucune place vacante");
  await expectNoA11yViolations(chef);
  await ctxChef.close();
});
