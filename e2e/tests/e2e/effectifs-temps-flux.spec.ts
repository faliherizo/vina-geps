/**
 * **Feuilles de temps — sources multiples, règles de modification, flux d'approbation, de bout en
 * bout.** AO 26-015-DO : E-RF-13, E-GP-3, E-GP-6.
 *
 * Julie n'a pas pointé les 12 et 13 mars 2031 ; la borne et le répartiteur (RAO) l'ont vue —
 * d'accord le 12, en désaccord le 13. L'administrateur règle à l'écran ce que la feuille fait de
 * deux sources en désaccord et compose le flux d'approbation : palier 1 au niveau GESTION de
 * l'unité, palier 2 à une personne désignée au-delà de 4 heures supplémentaires. Julie voit les
 * temps proposés par les sources tierces, les confirme, voit le refus motivé d'une modification
 * que la règle de l'organisme interdit, et soumet. Le chef approuve le palier 1, la personne
 * désignée le palier 2. Chaque état passe axe-core.
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

type Comptes = { julie: string; chef: string; rh: string };

/** L'organisme : deux quarts non pointés de Julie, les horodatages de deux sources, une règle de modification. */
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
    await ref("types_heure", { code: "HS", libelleFr: "Heures supplémentaires", comportement: "SUPPLEMENTAIRE", attributs: { codePaie: "150" } });
    const milieu = await poser(adm, tenantId, "/api/effectifs/milieux", { code: "C12", libelle: "Caserne 12", typeCode: "CASERNE", uniteCode: "EST" });
    const ids: Record<string, string> = {};
    for (const [cle, role] of [["julie", "employee"], ["chef", "manager"], ["rh", "manager"]] as const) {
      const compte = await poser(adm, tenantId, "/api/auth/users", { username: comptes[cle], password: MDP, role });
      await poser(adm, tenantId, `/api/auth/users/${compte.id}`, { mustChangePassword: false, securitySetupRequired: false }, "patch");
      ids[cle] = compte.id;
    }
    await poser(adm, tenantId, "/api/effectifs/perimetres", { utilisateurId: ids.chef, uniteCode: "EST", etendue: "UNITE", niveau: "GESTION", depuis: "2026-01-01" });
    const julie = await poser(adm, tenantId, "/api/effectifs/planification/ressources", { matricule: "P1", nom: "Julie Roy", corpsEmploiCode: "PARA", uniteCode: "EST", utilisateurId: ids.julie });
    const p = await poser(adm, tenantId, "/api/effectifs/planification/periodes", { nom: "Semaine 11", uniteCode: "EST", debut: "2031-03-10", fin: "2031-03-16" });
    await poser(adm, tenantId, `/api/effectifs/planification/periodes/${p.id}/etat`, { etat: "CONFECTION" });
    for (const date of ["2031-03-12", "2031-03-13"]) {
      const e = await poser(adm, tenantId, "/api/effectifs/planification/equipages", {
        periodeId: p.id, date, debut: 360, fin: 1080, natureCode: "REG", milieuId: milieu.id, modeAttribution: "MANUEL", composition: [{ corpsEmploiCode: "PARA", nombre: 1 }],
      });
      await poser(adm, tenantId, `/api/effectifs/planification/places/${e.places[0].id}/attribuer`, { ressourceId: julie.id });
    }
    await poser(adm, tenantId, `/api/effectifs/planification/periodes/${p.id}/etat`, { etat: "PUBLIEE" });
    const importer = (source: string, l: Array<[string, string, string]>) => poser(adm, tenantId, "/api/effectifs/presence/horodatages",
      { source, lignes: l.map(([cle, sens, horodatage]) => ({ cle, matricule: "P1", sens, horodatage })) });
    await importer("BORNE", [["b1", "ENTREE", "2031-03-12T10:00:00Z"], ["b2", "SORTIE", "2031-03-12T22:01:00Z"], ["b3", "ENTREE", "2031-03-13T11:15:00Z"], ["b4", "SORTIE", "2031-03-13T22:00:00Z"]]);
    await importer("RAO", [["r1", "ENTREE", "2031-03-12T10:02:00Z"], ["r2", "SORTIE", "2031-03-12T21:58:00Z"], ["r3", "ENTREE", "2031-03-13T10:00:00Z"], ["r4", "SORTIE", "2031-03-13T22:00:00Z"]]);
    // E-GP-3 : seule une personne au niveau GESTION retire un segment.
    await poser(adm, tenantId, "/api/effectifs/regles", {
      contexte: "MODIFICATION_HEURES", cle: "retrait-gestion", action: "BLOQUER", message: "seul le niveau GESTION retire un segment", effectifDepuis: "2027-01-01",
      condition: { tous: [{ fait: "operation", op: "=", valeur: "RETRAIT" }, { fait: "niveauActeur", op: "hors", valeur: ["GESTION", "ETABLISSEMENT"] }] },
    });
  } finally { await adm.dispose(); }
}

async function seConnecter(browser: Browser, identifiant: string, tenantId: string) {
  const ctx = await browser.newContext(optionsFilmees({ storageState: { cookies: [], origins: [] } }));
  const page = await ctx.newPage();
  await page.goto("/");
  await uiLogin(page, identifiant, MDP, tenantId);
  return { ctx, page };
}

test("sources tierces corrélées, modification restreinte par règle, approbation à paliers routés", async ({ browser }) => {
  test.setTimeout(360_000);
  const comptes: Comptes = { julie: uniq("julie").toLowerCase(), chef: uniq("chef").toLowerCase(), rh: uniq("rh").toLowerCase() };
  const sa = await superadminContext();
  const tenantId = await createTenant(sa, uniq("EffFlux"));
  await sa.dispose();
  await amorcer(tenantId, comptes);

  // ── L'administrateur : paramètres de corroboration et flux d'approbation, à l'écran ──────
  const ctxAdm = await browser.newContext(optionsFilmees({ storageState: { cookies: [], origins: [] } }));
  const adm = await ctxAdm.newPage();
  await selectTenant(adm, tenantId);
  await ouvrirNav(adm, "nav-ustemps");
  // Paramètres et flux sont deux dialogues depuis le 8 novembre 2026 (un objet, un dialogue).
  await adm.getByTestId("feuilles-parametres-ouvrir").click();
  // Les paramètres lus du serveur d'abord : changer avant leur arrivée serait écrasé par la lecture.
  await expect(adm.getByTestId("pla-tps-p-actionSourcesDivergentes")).toHaveValue("BLOQUER");
  await adm.getByTestId("pla-tps-p-actionSourcesDivergentes").selectOption("SIGNALER");
  await adm.getByTestId("pla-tps-p-enregistrer").click();
  await expect(adm.getByTestId("pla-tps-p-info")).toContainText("Paramètres enregistrés");
  // Le dialogue s'est fermé sur l'enregistrement ; **rouvert**, il relit le serveur : la valeur
  // affichée est donc bien celle qui a été enregistrée, et non celle qu'on venait de choisir.
  await expect(adm.getByTestId("feuilles-parametres-modal")).toBeHidden();
  await adm.getByTestId("feuilles-parametres-ouvrir").click();
  await expect(adm.getByTestId("pla-tps-p-actionSourcesDivergentes")).toHaveValue("SIGNALER");
  await adm.keyboard.press("Escape");
  await adm.getByTestId("feuilles-approbations-ouvrir").click();
  await expect(adm.getByTestId("pla-tps-flux-FEUILLE")).toContainText("flux par défaut");
  // Palier 1 : toujours, le niveau GESTION de l'unité (valeurs proposées par défaut).
  await adm.getByTestId("pla-tps-flux-ajouter").click();
  await expect(adm.getByTestId("pla-tps-flux-info")).toContainText("Palier 1 ajouté au flux des feuilles de temps");
  // Palier 2 : au-delà de 4 heures supplémentaires, la personne désignée.
  await adm.getByTestId("pla-tps-flux-palier").fill("2");
  await adm.getByTestId("pla-tps-flux-champ").selectOption("heuresSupplementaires");
  await adm.getByTestId("pla-tps-flux-operateur").selectOption("gte");
  await adm.getByTestId("pla-tps-flux-valeur").fill("4");
  await adm.getByTestId("pla-tps-flux-type").selectOption("PERSONNE");
  await adm.getByTestId("pla-tps-flux-approbateur").selectOption({ label: `${comptes.rh} (manager)` });
  await adm.getByTestId("pla-tps-flux-ajouter").click();
  await expect(adm.getByTestId("pla-tps-flux-info")).toContainText("Palier 2 ajouté");
  await expect(adm.getByTestId("pla-tps-flux-FEUILLE")).toContainText(`${comptes.rh} (personne désignée)`);
  await expect(adm.getByTestId("pla-tps-flux-FEUILLE")).toContainText("Heures supplémentaires ≥ 4");
  // Les pointages : le niveau GESTION, puis la personne désignée pour toute déclaration après coup.
  await adm.getByTestId("pla-tps-flux-objet").selectOption("POINTAGE");
  await adm.getByTestId("pla-tps-flux-ajouter").click();
  await expect(adm.getByTestId("pla-tps-flux-info")).toContainText("Palier 1 ajouté au flux des pointages en attente");
  await adm.getByTestId("pla-tps-flux-objet").selectOption("POINTAGE");
  await adm.getByTestId("pla-tps-flux-palier").fill("2");
  await adm.getByTestId("pla-tps-flux-champ").selectOption("saisieAPosteriori");
  await adm.getByTestId("pla-tps-flux-valeur").fill("1");
  await adm.getByTestId("pla-tps-flux-type").selectOption("PERSONNE");
  await adm.getByTestId("pla-tps-flux-approbateur").selectOption({ label: `${comptes.rh} (manager)` });
  await adm.getByTestId("pla-tps-flux-ajouter").click();
  await expect(adm.getByTestId("pla-tps-flux-POINTAGE")).toContainText(`${comptes.rh} (personne désignée)`);
  await expectNoA11yViolations(adm);
  await ctxAdm.close();

  // ── Julie : temps proposés par les sources tierces, confirmation, refus motivé, soumission ──
  const { ctx: ctxJulie, page: julie } = await seConnecter(browser, comptes.julie, tenantId);
  await ouvrirNav(julie, "nav-matemps");
  await julie.getByTestId("mft-preparation-ouvrir").click();
  await julie.getByTestId("mft-debut").fill("2031-03-10");
  await julie.getByTestId("mft-fin").fill("2031-03-16");
  await julie.getByTestId("mft-preparer").click();
  await expect(julie.getByTestId("mft-info")).toContainText("Feuille préparée");
  await expect(julie.getByTestId("mft-segments")).toContainText("Horodatage externe (BORNE) — à confirmer");
  await expect(julie.getByTestId("mft-anomalies")).toContainText("confirmez-le ou corrigez-le avant de soumettre");
  await expect(julie.getByTestId("mft-correlation")).toContainText("BORNE et RAO");
  await expect(julie.getByTestId("mft-correlation")).toContainText("(en désaccord)");
  await expectNoA11yViolations(julie);
  const confirmer = julie.locator('[data-testid^="mft-s-"][data-testid$="-confirmer"]');
  await expect(confirmer).toHaveCount(2);
  await confirmer.first().click();
  await expect(julie.getByTestId("mft-info")).toContainText("confirmé");
  await expect(confirmer).toHaveCount(1);
  await confirmer.first().click();
  await expect(confirmer).toHaveCount(0);
  await expect(julie.getByTestId("mft-segments")).not.toContainText("à confirmer");
  // E-GP-3 : la règle de l'organisme refuse le retrait, et l'écran dit pourquoi.
  await julie.locator('[data-testid^="mft-s-"][data-testid$="-modifier"]').first().click();
  await expect(julie.getByTestId("mft-edition")).toBeVisible();
  await julie.getByTestId("mft-e-motif").fill("Je n'étais pas là ce jour-là");
  await julie.getByTestId("mft-e-retirer").click();
  await expect(julie.getByTestId("mft-e-erreur")).toContainText("modification des heures refusée : seul le niveau GESTION retire un segment");
  await expectNoA11yViolations(julie);
  await julie.keyboard.press("Escape");
  await expect(julie.getByTestId("mft-edition")).toBeHidden();
  await julie.getByTestId("mft-soumettre").click();
  await expect(julie.getByTestId("mft-info")).toContainText("Feuille soumise");
  await expect(julie.getByTestId("mft-circuit")).toContainText("Palier 1 : le niveau GESTION de l'unité — en attente (palier courant)");
  await expect(julie.getByTestId("mft-circuit")).toContainText(`Palier 2 : ${comptes.rh} (personne désignée)`);
  await expectNoA11yViolations(julie);
  await ctxJulie.close();
  // Julie déclare aussi, après coup, une formation d'hier : le pointage suit le flux des pointages.
  // Le jeton seul, sans le cookie de session de la connexion (sinon la garde CSRF s'applique).
  const connexion = await pwRequest.newContext({ storageState: { cookies: [], origins: [] } });
  const { token } = await (await connexion.post(`${API}/api/auth/login`, { data: { username: comptes.julie, password: MDP, tenantId } })).json();
  await connexion.dispose();
  const api = await pwRequest.newContext({ storageState: { cookies: [], origins: [] } });
  const hier = new Date(Date.now() - 86_400_000).toISOString().slice(0, 10);
  const saisie = await api.post(`${API}/api/effectifs/presence/saisies`, { headers: { Authorization: `Bearer ${token}`, "X-Tenant-Id": tenantId },
    data: { justification: "Formation à la caserne", segments: [{ debut: `${hier}T13:00:00.000Z`, fin: `${hier}T15:00:00.000Z`, type: "TRAVAIL" }] } });
  expect(saisie.status()).toBe(201);
  await api.dispose();

  // ── Le chef (niveau GESTION) : palier 1 ───────────────────────────────────────────────────
  const { ctx: ctxChef, page: chef } = await seConnecter(browser, comptes.chef, tenantId);
  await ouvrirNav(chef, "nav-ustemps");
  const ouvrirChef = chef.locator('[data-testid^="pla-tps-"][data-testid$="-ouvrir"]');
  await expect(ouvrirChef).toHaveCount(1);
  await ouvrirChef.click();
  await expect(chef.getByTestId("pla-tps-fiche-segments")).toContainText("Horodatage externe (BORNE)");
  await expect(chef.getByTestId("pla-tps-approuver")).toContainText("Approuver le palier 1 (le niveau GESTION de l'unité)");
  await expectNoA11yViolations(chef);
  await chef.getByTestId("pla-tps-approuver").click();
  await expect(chef.getByTestId("pla-tps-bilan")).toContainText("palier 2 sur 2");
  await ouvrirNav(chef, "nav-uspresence");
  await chef.locator('[data-testid^="pla-pre-"][data-testid$="-trancher"]').first().click();
  await expect(chef.getByTestId("pla-pre-fiche-circuit")).toContainText(`Palier 2 : ${comptes.rh} (personne désignée)`);
  await expect(chef.getByTestId("pla-pre-approuver")).toContainText("Approuver le palier 1");
  await expectNoA11yViolations(chef);
  await chef.getByTestId("pla-pre-approuver").click();
  await expect(chef.getByTestId("pla-pre-bilan")).toContainText("palier approuvé, le pointage passe au palier suivant");
  await ctxChef.close();

  // ── La personne désignée, hors du périmètre de l'unité : palier 2 ─────────────────────────
  const { ctx: ctxRh, page: rh } = await seConnecter(browser, comptes.rh, tenantId);
  await ouvrirNav(rh, "nav-ustemps");
  const ouvrirRh = rh.locator('[data-testid^="pla-tps-"][data-testid$="-ouvrir"]');
  await expect(ouvrirRh).toHaveCount(1);
  await ouvrirRh.click();
  await expect(rh.getByTestId("pla-tps-fiche-circuit")).toContainText("Palier 1 : le niveau GESTION de l'unité — approuvé");
  await expectNoA11yViolations(rh);
  await rh.getByTestId("pla-tps-approuver").click();
  await expect(rh.getByTestId("pla-tps-bilan")).toContainText("Feuille de Julie Roy : approuvée.");
  await ouvrirNav(rh, "nav-uspresence");
  await rh.locator('[data-testid^="pla-pre-"][data-testid$="-trancher"]').first().click();
  await expect(rh.getByTestId("pla-pre-approuver")).toContainText("Approuver le palier 2");
  await rh.getByTestId("pla-pre-approuver").click();
  await expect(rh.getByTestId("pla-pre-bilan")).toContainText("2 pointages approuvés");
  await ctxRh.close();
});
