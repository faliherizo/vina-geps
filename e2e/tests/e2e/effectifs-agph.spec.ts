/**
 * **Gestion des effectifs — lot R7 (AGPH et remplacements long terme), de bout en bout.** AO 26-015-DO.
 *
 * Julie (rang 1) choisit son poste dans Mon espace › Mon AGPH, à son tour, et se manifeste pour un
 * remplacement long terme ouvert à son corps d'emploi. Le planificateur saisit ensuite le choix de
 * Marc (rang 2), reçu au téléphone ; tous servis, il clôt la campagne : les statuts d'emploi
 * suivent les postes obtenus (P-CH-1, P-CH-2, P-CH-5, P-CH-6, E-CH-8). Chaque état passe axe-core.
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

/** Les comptes d'employés se créent par l'administrateur de l'organisme (voir effectifs-disponibilites.spec). */
async function adminDeLOrganisme(tenantId: string): Promise<APIRequestContext> {
  const ctx = await pwRequest.newContext({ storageState: { cookies: [], origins: [] } });
  const { token } = await (await ctx.post(`${API}/api/auth/login`, { data: { username: TENANT_ADMIN_USER, password: TENANT_ADMIN_PASS, tenantId } })).json();
  await ctx.dispose();
  return pwRequest.newContext({ storageState: { cookies: [], origins: [] }, extraHTTPHeaders: { Authorization: `Bearer ${token}` } });
}

async function amorcer(tenantId: string, identifiant: string, options: { demarrer?: boolean; modules?: string[] } = {}): Promise<Record<string, string>> {
  const adm = await adminDeLOrganisme(tenantId);
  const ids: Record<string, string> = {};
  try {
    const sa = await superadminContext();
    await setTenantModules(sa, tenantId, options.modules ?? ["effectifs_agph"]);
    await sa.dispose();
    const ref = (type: string, data: Record<string, unknown>) => poser(adm, tenantId, `/api/effectifs/referentiels/${type}`, { effectifDepuis: "2026-01-01", ...data });
    await ref("unites", { code: "EST", libelleFr: "Opérations Est" });
    await ref("corps_emploi", { code: "PARA", libelleFr: "Paramédics" });
    for (const [code, libelleFr, comportement] of [["TC", "Temps complet", "TEMPS_COMPLET"], ["TPR", "Temps partiel régulier", "TEMPS_PARTIEL_REGULIER"], ["TP", "Temps partiel", "TEMPS_PARTIEL_OCCASIONNEL"]]) {
      await ref("statuts_emploi", { code, libelleFr, comportement });
    }
    await ref("natures_quart", { code: "REG", libelleFr: "Régulier", comportement: "REGULIER" });
    await ref("types_milieu", { code: "CASERNE", libelleFr: "Caserne", comportement: "CASERNE" });
    await ref("plages_horaires", { code: "J", libelleFr: "Jour", attributs: { debut: 360, fin: 1080 } });
    const milieu = await poser(adm, tenantId, "/api/effectifs/milieux", { code: "C12", libelle: "Caserne 12", typeCode: "CASERNE", uniteCode: "EST" });
    const compte = await poser(adm, tenantId, "/api/auth/users", { username: identifiant, password: MDP, role: "employee" });
    await poser(adm, tenantId, `/api/auth/users/${compte.id}`, { mustChangePassword: false, securitySetupRequired: false }, "patch");
    ids.julie = (await poser(adm, tenantId, "/api/effectifs/planification/ressources", { matricule: "P1", nom: "Julie Roy", corpsEmploiCode: "PARA", uniteCode: "EST", statutEmploiCode: "TP", anciennete: 12, utilisateurId: compte.id })).id;
    ids.marc = (await poser(adm, tenantId, "/api/effectifs/planification/ressources", { matricule: "P2", nom: "Marc Dubé", corpsEmploiCode: "PARA", uniteCode: "EST", statutEmploiCode: "TP", anciennete: 8 })).id;
    const cycle = [["J", "J", "J", null, null, null, null]];
    const postes = [];
    for (const [code, statut] of [["A-TC", "TC"], ["B-TPR", "TPR"], ["C-TPR", "TPR"]]) {
      postes.push((await poser(adm, tenantId, "/api/effectifs/planification/postes", { code, libelle: `Poste ${code}`, uniteCode: "EST", statutEmploiCode: statut, corpsEmploiCode: "PARA", milieuId: milieu.id, natureCode: "REG", cycle, debutCycle: "2031-01-06" })).id);
    }
    await poser(adm, tenantId, "/api/effectifs/planification/postes/publier", { ids: postes });
    [ids.posteA, ids.posteB, ids.posteC] = postes;
    ids.milieu = milieu.id;
    const c = await poser(adm, tenantId, "/api/effectifs/agph/campagnes", { uniteCode: "EST", corpsEmploiCode: "PARA", annee: 2031, delaiChoixMinutes: 60 });
    await poser(adm, tenantId, `/api/effectifs/agph/campagnes/${c.id}/ouvrir`, {});
    if (options.demarrer !== false) await poser(adm, tenantId, `/api/effectifs/agph/campagnes/${c.id}/demarrer`, {});
    ids.campagne = c.id;
    return ids;
  } finally { await adm.dispose(); }
}

test("choisir son poste à son tour, saisir le choix du rang suivant, clore la campagne", async ({ browser }) => {
  test.setTimeout(240_000);
  const identifiant = uniq("julie").toLowerCase();
  const sa = await superadminContext();
  const tenantId = await createTenant(sa, uniq("EffAgph"));
  await sa.dispose();
  const ids = await amorcer(tenantId, identifiant);

  // ── Julie, à son tour ───────────────────────────────────────────────────────────────────
  const ctxJulie = await browser.newContext(optionsFilmees({ storageState: { cookies: [], origins: [] } }));
  const julie = await ctxJulie.newPage();
  await julie.goto("/");
  await uiLogin(julie, identifiant, MDP, tenantId);
  await ouvrirNav(julie, "nav-monagph");
  await raconter(julie, "AGPH, le choix annuel des postes. Julie Roy ouvre Confection AGPH : elle a le plus d'ancienneté, son rang est le premier, et c'est son tour. Elle choisit le poste B à temps partiel régulier.");
  await expect(julie.getByTestId(`agm-tour-${ids.campagne}`)).toBeVisible();
  await expect(julie.getByTestId(`agm-rang-${ids.campagne}`)).toContainText("Votre rang : 1 (À son tour)");
  await expectNoA11yViolations(julie);
  await julie.getByTestId("agm-choisir-B-TPR").click();
  await expect(julie.getByTestId("agm-info")).toContainText("Poste B-TPR choisi");
  await expect(julie.getByTestId(`agm-rang-${ids.campagne}`)).toContainText("A choisi");
  await ctxJulie.close();

  // ── Le planificateur : saisie du choix de Marc, clôture ──────────────────────────────────
  const ctxChef = await browser.newContext(optionsFilmees({ storageState: { cookies: [], origins: [] } }));
  const chef = await ctxChef.newPage();
  await selectTenant(chef, tenantId);
  await ouvrirNav(chef, "nav-usagph");
  await raconter(chef, "Le planificateur ouvre la campagne : c'est au tour de Marc Dubé, qui n'a pas accès à l'écran. Le planificateur saisit pour lui le poste A à temps complet. Tous sont servis, et il clôt la campagne : chaque attribution dit qui l'a faite.");
  await expect(chef.getByTestId("pla-agph-rang-2")).toContainText("À son tour");
  await chef.getByTestId("pla-agph-saisir-2").click();
  await chef.getByTestId("pla-agph-saisie-A-TC").click();
  await expect(chef.getByTestId("pla-agph-info")).toContainText("Poste A-TC saisi pour Marc Dubé");
  await expect(chef.getByTestId("pla-agph-etat")).toContainText("Tous servis");
  await expectNoA11yViolations(chef);
  await chef.getByTestId("pla-agph-clore").click();
  await expect(chef.getByTestId("pla-agph-info")).toContainText("Campagne close");
  await expect(chef.getByTestId("pla-agph-rang-1")).toContainText("B-TPR (par la personne)");
  await expect(chef.getByTestId("pla-agph-rang-2")).toContainText("A-TC (par le planificateur)");

  // ── Un remplacement long terme sur le poste resté libre ─────────────────────────────────
  const demain = new Date(Date.now() + 86_400_000).toISOString();
  const adm = await adminDeLOrganisme(tenantId);
  const rlt = await poser(adm, tenantId, "/api/effectifs/agph/remplacements", { posteId: ids.posteC, origine: "POSTE_VACANT", debut: "2031-06-02", fin: "2031-06-30", finManifestation: demain });
  await adm.dispose();
  await ctxChef.close();

  const ctxJulie2 = await browser.newContext(optionsFilmees({ storageState: { cookies: [], origins: [] } }));
  const julie2 = await ctxJulie2.newPage();
  await julie2.goto("/");
  await uiLogin(julie2, identifiant, MDP, tenantId);
  await ouvrirNav(julie2, "nav-monagph");
  await raconter(julie2, "Un remplacement long terme s'ouvre sur le poste C. Julie manifeste son intérêt, depuis son espace.");
  await julie2.getByTestId(`agm-rlt-${rlt.id}-manifester`).click();
  await expect(julie2.getByTestId("agm-info")).toContainText("Intérêt enregistré pour le remplacement du poste C-TPR");
  await expectNoA11yViolations(julie2);
  await ctxJulie2.close();
});

/**
 * **E-CH-1, E-CH-9, E-CH-10, E-GR-2, P-GH-4 — le processus annuel, de la semaine type à la gestion
 * régulière.** Julie dépose sa semaine type (refusée par la règle de la convention, puis acceptée) ;
 * la planification fixe le budget, saisit la semaine type de Marc (au-delà du budget : avertie),
 * démarre, révise l'attribution de Marc avec motif, clôt : les postes restés libres sont transférés.
 * Leurs quarts naissent vacants dans le comblement ; un remplacement long terme est laissé non comblé
 * avec motif ; le bassin d'une place vacante propose Marc, et l'attribution de fin de collecte est
 * planifiée sans geste. Chaque état passe axe-core.
 */
test("semaine type, budget, révision, clôture : les quarts non couverts passent à la gestion régulière", async ({ browser }) => {
  test.setTimeout(300_000);
  const identifiant = uniq("julie").toLowerCase();
  const sa = await superadminContext();
  const tenantId = await createTenant(sa, uniq("EffAgphRev"));
  await sa.dispose();
  const ids = await amorcer(tenantId, identifiant, { demarrer: false, modules: ["effectifs_agph", "effectifs_disponibilites"] });
  const aujourdhui = new Date().toISOString().slice(0, 10);
  let adm = await adminDeLOrganisme(tenantId);
  await poser(adm, tenantId, "/api/effectifs/regles", { contexte: "DEPOT_AGPH", cle: "minimum-quarts", condition: { fait: "quartsParSemaine", op: "<", valeur: 2 },
    action: "BLOQUER", message: "Au moins 2 quarts par semaine ({quartsParSemaine} offert)", effectifDepuis: aujourdhui });
  await poser(adm, tenantId, "/api/effectifs/regles", { contexte: "DEPOT_AGPH", cle: "budget", condition: { fait: "tauxBudget", op: ">", valeur: 100 },
    action: "AVERTIR", message: "Les disponibilités offertes dépassent le budget ({tauxBudget} %)", effectifDepuis: aujourdhui });
  await adm.dispose();

  // ── Julie émet sa semaine type pour l'année ─────────────────────────────────────────────
  const ctxJulie = await browser.newContext(optionsFilmees({ storageState: { cookies: [], origins: [] } }));
  const julie = await ctxJulie.newPage();
  await julie.goto("/");
  await uiLogin(julie, identifiant, MDP, tenantId);
  await ouvrirNav(julie, "nav-monagph");
  await raconter(julie, "Deuxième cas : avant le tour de rôle, chacun dépose sa semaine type. Julie n'offre qu'un quart : la règle en exige deux par semaine, le dépôt est refusé. Elle en ajoute un, et sa semaine devient conforme : 24 heures offertes.");
  const D = `agm-dispo-${ids.campagne}`;
  await expect(julie.getByTestId(D)).toBeVisible();
  await julie.getByTestId(`${D}-enregistrer`).click();
  await expect(julie.getByTestId(`${D}-erreur`)).toContainText("Au moins 2 quarts par semaine (1 offert)");
  await expectNoA11yViolations(julie);
  await julie.getByTestId(`${D}-ajouter`).click();
  await expect(julie.getByTestId(`${D}-plage-1-date`)).toHaveValue("2001-01-02");
  await julie.getByTestId(`${D}-enregistrer`).click();
  await expect(julie.getByTestId(`${D}-conformite`)).toContainText("Conforme");
  await expect(julie.getByTestId(`${D}-verdict`)).toContainText("24 h offertes par semaine");
  await expectNoA11yViolations(julie);
  await ctxJulie.close();

  // ── La planification : budget, semaine type de Marc, tour de rôle ───────────────────────
  const ctxChef = await browser.newContext(optionsFilmees({ storageState: { cookies: [], origins: [] } }));
  const chef = await ctxChef.newPage();
  await selectTenant(chef, tenantId);
  await ouvrirNav(chef, "nav-usagph");
  await expect(chef.getByTestId("pla-agph-etat")).toContainText("dépôt des disponibilités");
  // Le budget modifie la campagne : un dialogue depuis le 8 novembre 2026.
  await raconter(chef, "Le planificateur fixe le budget de l'unité à 20 heures : l'offre de Julie le dépasse, à 120 pour cent, et c'est signalé.");
  await chef.getByTestId("pla-agph-budget-edition-ouvrir").click();
  await chef.getByTestId("pla-agph-budget").fill("20");
  await chef.getByTestId("pla-agph-budget-fixer").click();
  await expect(chef.getByTestId("pla-agph-dispo-info")).toContainText("Budget fixé à 20 h");
  await expect(chef.getByTestId("pla-agph-dispo-resume")).toContainText("24 h offertes par semaine · budget 20 h (120 %)");
  await expect(chef.getByTestId("pla-agph-dispo-resume")).toContainText("Au-delà du budget");
  await raconter(chef, "Il saisit la semaine type de Marc : elle est acceptée, avec un avertissement de dépassement du budget. Puis il démarre le tour de rôle.");
  await chef.getByTestId("pla-agph-dispo-saisir-2").click();
  await chef.getByTestId("pla-agph-dispo-saisie-ajouter").click();
  await chef.getByTestId("pla-agph-dispo-saisie-enregistrer").click();
  await expect(chef.getByTestId("pla-agph-dispo-saisie-conformite")).toContainText("avec avertissement");
  await expect(chef.getByTestId("pla-agph-dispo-saisie-motifs")).toContainText("dépassent le budget (240 %)");
  await expectNoA11yViolations(chef);
  await chef.keyboard.press("Escape");
  await expect(chef.getByTestId("pla-agph-dispo-2")).toContainText("avec avertissement");
  await chef.getByTestId("pla-agph-demarrer").click();
  await expect(chef.getByTestId("pla-agph-etat")).toContainText("Tour de rôle en cours");

  adm = await adminDeLOrganisme(tenantId);
  await poser(adm, tenantId, `/api/effectifs/agph/campagnes/${ids.campagne}/choix`, { ressourceId: ids.julie, posteId: ids.posteB });
  await poser(adm, tenantId, `/api/effectifs/agph/campagnes/${ids.campagne}/choix`, { ressourceId: ids.marc, posteId: ids.posteA });
  await adm.dispose();

  // ── Révision motivée, puis clôture : les postes restés libres passent à la gestion régulière ──
  await raconter(chef, "Tous ont choisi. Le planificateur passe en révision et révise l'attribution de Marc, avec son motif : le poste A est réservé à une réorganisation.");
  await chef.reload();
  await expect(chef.getByTestId("pla-agph-etat")).toContainText("Tous servis — à réviser ou clore");
  await chef.getByTestId("pla-agph-reviser").click();
  await expect(chef.getByTestId("pla-agph-etat")).toContainText("En révision");
  await chef.getByTestId("pla-agph-reviser-2").click();
  await expect(chef.getByTestId("pla-agph-revision-poste")).toBeVisible();
  await chef.getByTestId("pla-agph-revision-motif").fill("Le poste A-TC est réservé à une réorganisation");
  await expectNoA11yViolations(chef);
  await chef.getByTestId("pla-agph-revision-envoyer").click();
  await expect(chef.getByTestId("pla-agph-info")).toContainText("Attribution de Marc Dubé révisée");
  await expect(chef.getByTestId("pla-agph-rang-2")).toContainText("Passé");
  await raconter(chef, "À la clôture, les postes restés vacants sont transférés à la gestion régulière.");
  await chef.getByTestId("pla-agph-clore").click();
  await expect(chef.getByTestId("pla-agph-transferes")).toContainText("A-TC — vacant");
  await expect(chef.getByTestId("pla-agph-transferes")).toContainText("C-TPR — vacant");
  await expectNoA11yViolations(chef);

  // ── Gestion régulière : une période, ses quarts, un remplacement long terme ────────────
  adm = await adminDeLOrganisme(tenantId);
  const periode = await poser(adm, tenantId, "/api/effectifs/planification/periodes", { nom: "Janvier 2031", uniteCode: "EST", debut: "2031-01-06", fin: "2031-01-12" });
  await poser(adm, tenantId, `/api/effectifs/planification/periodes/${periode.id}/generer`, {});
  await poser(adm, tenantId, `/api/effectifs/planification/periodes/${periode.id}/etat`, { etat: "COLLECTE", finCollecte: aujourdhui });
  const jour = (date: string) => ({ date, debut: 360, fin: 1080, type: "DISPONIBLE" });
  const depot = await adm.put(`${API}/api/effectifs/disponibilites/periodes/${periode.id}/ressources/${ids.marc}`, {
    headers: { "X-Tenant-Id": tenantId }, data: { plages: [jour("2031-01-06"), jour("2031-01-07"), jour("2031-01-08")] },
  });
  expect(depot.ok()).toBeTruthy();
  const demain = new Date(Date.now() + 86_400_000).toISOString();
  const rlt = await poser(adm, tenantId, "/api/effectifs/agph/remplacements", { posteId: ids.posteC, origine: "POSTE_VACANT", debut: "2031-01-06", fin: "2031-01-12", finManifestation: demain });
  await adm.dispose();

  await raconter(chef, "Un remplacement long terme est laissé non comblé, motif : budget de l'unité atteint. Ses trois quarts passent à la gestion régulière.");
  await chef.reload();
  await chef.getByTestId(`pla-rlt-${rlt.id}-non-comble`).click();
  await chef.getByTestId("pla-rlt-non-comble-motif").fill("Budget de l'unité atteint");
  await expectNoA11yViolations(chef);
  await chef.getByTestId("pla-rlt-non-comble-envoyer").click();
  await expect(chef.getByTestId("pla-agph-info")).toContainText("laissé non comblé : 3 quart(s) transféré(s) à la gestion régulière");
  await expect(chef.getByTestId(`pla-rlt-${rlt.id}-etat`)).toContainText("Non comblé — 3 quart(s) transféré(s) à la gestion régulière");
  await expect(chef.getByTestId(`pla-rlt-${rlt.id}-etat`)).toContainText("Motif : Budget de l'unité atteint");

  // Les quarts des postes vacants sont dans le comblement : sous leur minimum, deux places libres sur trois.
  await raconter(chef, "Ils apparaissent aussitôt au comblement, sous le minimum, et dans les disponibilités, où Marc Dubé est candidat admissible.");
  await ouvrirNav(chef, "nav-uscomblement");
  await expect(chef.getByTestId("pla-tuile-sous-minimum")).toContainText("3 quart(s)");
  await expect(chef.getByTestId("pla-sous-minimum")).toContainText("1 / 3");
  await expectNoA11yViolations(chef);

  // Le bassin d'une place vacante propose Marc ; l'attribution de fin de collecte est planifiée, sans geste.
  await ouvrirNav(chef, "nav-usdisponibilites");
  await expect(chef.getByTestId("pla-dispo-auto")).toContainText(`au lendemain de la fin de collecte (${aujourdhui}) : aucun geste n'est requis`);
  await chef.getByTestId("pla-dispo-place").selectOption({ index: 1 });
  await chef.getByTestId("pla-dispo-candidats").click();
  await expect(chef.getByTestId("pla-dispo-admissibles")).toContainText("Marc Dubé");
  await expectNoA11yViolations(chef);
  await ctxChef.close();
});
