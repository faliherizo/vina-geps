/**
 * **FX-AO-20260810 — les compléments de la grille, à l'écran, contre le Java.**
 *
 * L'administration coche la copie par courriel (critère 8), lit le bordereau des utilisateurs actifs (facturation) et
 * déclare un Active Directory en LDAPS après que `ldap://` a été refusé (critère 82). La planificatrice retrouve au
 * suivi l'alerte relancée par sa cascade — colonne « Relances », filtre « relancée », trace qui nomme Julie (critères
 * 11, 17, 19) —, pose une note sur Julie (critère 2) et exporte le suivi (critère 12). Chaque état passe axe-core.
 *
 * L'alerte et sa politique sont semées par l'API : ce parcours montre ce que l'écran en fait, l'envoi ayant son
 * propre parcours (`urgences-sante-alertes.spec.ts`).
 */
import { test, expect, request as pwRequest, type APIRequestContext, type Browser, type Page } from "@playwright/test";
import { superadminContext } from "./helpers/auth";
import { createTenant, setTenantModules, uniq } from "./helpers/organisme";
import { ouvrirNav } from "./helpers/nav";
import { auditA11y } from "./helpers/a11y";
import { optionsFilmees } from "./helpers/film";
import { adminDeLOrganisme, amorcerOrganisme, identifiantDe, MDP_PROFIL, modulesDeLOrganisme, seConnecterProfil, type Profil } from "./helpers/urgences-sante";
import jeu from "../../donnees/urgences-sante/profils.json";
import { raconter } from "./helpers/recit";

test.use({ storageState: { cookies: [], origins: [] } });

const API = `http://localhost:${process.env.E2E_API_PORT ?? "4000"}`;
const PROFILS = jeu.profils as Profil[];
const profil = (cle: string) => PROFILS.find((p) => p.cle === cle)!;

async function sansViolation(page: Page, etat: string): Promise<void> {
  expect(await auditA11y(page), `axe-core : ${etat}`).toEqual([]);
}

async function ouvrir(browser: Browser, tenantId: string, cle: string, nav: string, temoin: string): Promise<Page> {
  const ctx = await browser.newContext(optionsFilmees({ storageState: { cookies: [], origins: [] } }));
  const page = await ctx.newPage();
  await seConnecterProfil(page, tenantId, identifiantDe(profil(cle), tenantId));
  await ouvrirNav(page, nav);
  await expect(page.getByTestId(temoin)).toBeVisible({ timeout: 30_000 });
  return page;
}

/** Une session d'API au nom d'un profil de l'organisme. */
async function api(tenantId: string, cle: string): Promise<APIRequestContext> {
  const c = await pwRequest.newContext({ storageState: { cookies: [], origins: [] } });
  const r = await c.post(`${API}/api/auth/login`, { data: { username: identifiantDe(profil(cle), tenantId), password: MDP_PROFIL, tenantId } });
  const { token } = await r.json();
  await c.dispose();
  return pwRequest.newContext({ storageState: { cookies: [], origins: [] }, extraHTTPHeaders: { Authorization: `Bearer ${token}`, "X-Tenant-Id": tenantId } });
}

async function json(r: Awaited<ReturnType<APIRequestContext["post"]>>, quoi: string): Promise<{ id: string }> {
  expect(r.ok(), `${quoi} : ${r.status()} ${await r.text()}`).toBeTruthy();
  return (await r.json()) as { id: string };
}

test.describe("FX-AO-20260810 — copie par courriel, LDAPS, utilisateurs actifs, relances, note et export", () => {
  test.describe.configure({ mode: "serial" });
  let tenantId = "";
  const titre = "Code bleu — relance démontrée";

  test.beforeAll(async () => {
    test.setTimeout(240_000);
    const sa = await superadminContext();
    try {
      tenantId = await createTenant(sa, uniq("Demo-Parcours"));
      await setTenantModules(sa, tenantId, [...modulesDeLOrganisme, "alertes"]);
    } finally { await sa.dispose(); }
    await amorcerOrganisme(tenantId, { profils: PROFILS });
    const adm = await adminDeLOrganisme(tenantId);
    const chef = await api(tenantId, "planificateur");
    try {
      const arrive = await json(await adm.post(`${API}/api/alertes/reponses`, { data: { libelle: "J'arrive", semantique: "prise_en_charge" } }), "réponse");
      const politique = await json(await adm.post(`${API}/api/alertes/politiques`, {
        data: { nom: "Relance de garde", arret: "prise_en_charge", etapes: [{ cible: "initiaux", canaux: ["poussee"], delaiMinutes: 30 }] },
      }), "politique");
      const alerte = await json(await chef.post(`${API}/api/alertes`, {
        data: { titre, corps: "Chambre 512", priorite: "urgente", reponseIds: [arrive.id], personnes: [identifiantDe(profil("paramedic"), tenantId)], politiqueId: politique.id },
      }), "alerte");
      // La reprise met l'étape à maintenant : l'ordonnanceur de la campagne l'exécute au tic suivant.
      await json(await chef.post(`${API}/api/alertes/envoyees/${alerte.id}/cascade/reprendre`, { data: { etape: 1 } }), "reprise");
    } finally { await adm.dispose(); await chef.dispose(); }
  });

  test("l'administration : copie par courriel, utilisateurs actifs du mois, annuaire LDAPS (ldap:// refusé)", async ({ browser }) => {
    test.setTimeout(240_000);
    const admin = await ouvrir(browser, tenantId, "administration", "nav-alparametres", "alp-titre");
    await raconter(admin, "Alertes, suite. L'administration coche la copie par courriel : chaque destinataire d'une alerte recevra aussi un courriel. Le réglage tient après rechargement. Plus bas, les utilisateurs actifs du mois : Julie Roy, comptée une fois.");
    const copie = admin.getByTestId("alp-copie");
    await expect(copie).not.toBeChecked();
    // La case ne bascule qu'une fois le réglage enregistré : l'écran ne montre que ce que le serveur a retenu.
    await copie.click();
    await expect(admin.getByTestId("alp-succes")).toContainText("Chaque destinataire recevra aussi une copie par courriel.");
    await expect(copie).toBeChecked();
    await admin.reload();
    await expect(admin.getByTestId("alp-copie")).toBeChecked();
    // Julie a été visée par une tentative ce mois-ci : elle compte, une fois.
    await expect(admin.getByTestId("alp-actifs-total")).toContainText("1");
    await expect(admin.getByTestId("alp-actifs-table")).toContainText(profil("paramedic").nom);
    await sansViolation(admin, "paramètres, copie cochée, utilisateurs actifs");

    await raconter(admin, "Elle déclare ensuite l'annuaire de l'établissement, son Active Directory. Une adresse ldap en clair est refusée : seul LDAPS, chiffré, est accepté. Avec l'adresse ldaps, l'annuaire est déclaré.");
    await ouvrirNav(admin, "nav-admin");
    await admin.getByTestId("admin-tab-organisme").click();
    await admin.getByTestId("annuaire-nouveau-ouvrir").click();
    await admin.getByTestId("annuaire-nouveau-nom").fill("Active Directory des Laurentides");
    await admin.getByTestId("annuaire-nouveau-fournisseur").selectOption("ldaps");
    await admin.getByTestId("annuaire-nouveau-emetteur").fill("ldap://ad.laurentides.local:389/DC=laurentides,DC=local");
    await admin.getByTestId("annuaire-nouveau-client").fill("CN=svc-geps,DC=laurentides,DC=local");
    await admin.getByTestId("annuaire-nouveau-variable").fill("ANNUAIRE_AD");
    await admin.getByTestId("annuaire-nouveau-domaines").fill("laurentides.example");
    await admin.getByTestId("annuaire-nouveau-defaut").selectOption("employee");
    await admin.getByTestId("annuaire-nouveau-enregistrer").click();
    await expect(admin.getByTestId("annuaire-nouveau-erreurs")).toContainText("ldap:// en clair est refusé");
    await sansViolation(admin, "annuaire LDAPS, refus de ldap:// affiché");
    await admin.getByTestId("annuaire-nouveau-emetteur").fill("ldaps://ad.laurentides.local:636/OU=Personnel,DC=laurentides,DC=local");
    await admin.getByTestId("annuaire-nouveau-enregistrer").click();
    await expect(admin.getByTestId("annuaires-info")).toContainText("Annuaire « Active Directory des Laurentides » déclaré.");
    await expect(admin.locator('[data-testid^="annuaire-ann-"]').filter({ hasText: "Active Directory des Laurentides" }))
      .toContainText("Annuaire LDAP (Active Directory), en LDAPS");
    await admin.context().close();
  });

  test("la planificatrice : la relance au suivi, filtrée ; la trace nomme Julie ; une note ; l'export", async ({ browser }) => {
    test.setTimeout(240_000);
    const julie = profil("paramedic");
    const chef = await ouvrir(browser, tenantId, "planificateur", "nav-alsuivi", "als-titre");
    await raconter(chef, "La planificatrice ouvre le suivi des alertes et filtre sur les alertes relancées : le Code bleu a été relancé une fois.");
    const ligne = chef.locator('[data-testid^="als-alerte-"]').filter({ hasText: titre }).first();
    const relances = ligne.locator('[data-testid$="-relances"]');
    // L'ordonnanceur exécute l'étape reprise : la colonne passe à 1.
    await expect.poll(async () => {
      await chef.getByTestId("als-actualiser").click();
      return (await relances.textContent())?.trim();
    }, { timeout: 60_000, intervals: [3_000] }).toBe("1");
    await chef.getByTestId("als-etat").selectOption("relancee");
    await chef.getByTestId("als-filtrer").click();
    await expect(chef.locator('[data-testid^="als-alerte-"][data-testid$="-relances"]')).toHaveCount(1);
    await sansViolation(chef, "suivi filtré sur les alertes relancées");

    await raconter(chef, "Dans le détail, la trace de la cascade nomme Julie. La planificatrice pose une note sur la destinataire, joindre sur le cellulaire de garde, visible des autres expéditeurs.");
    await ligne.locator('[data-testid$="-detail"]').click();
    await expect(chef.getByTestId("als-cascade-trace")).toContainText("Étape exécutée");
    await expect(chef.getByTestId("als-cascade-trace")).toContainText(julie.nom);
    const personne = identifiantDe(julie, tenantId);
    await chef.getByTestId(`als-note-${personne}-modifier`).click();
    await chef.getByTestId("als-note-texte").fill("Joindre sur le cellulaire de garde");
    await sansViolation(chef, "dialogue de note ouvert");
    await chef.getByTestId("als-note-enregistrer").click();
    await expect(chef.getByTestId(`als-note-${personne}`)).toContainText("Joindre sur le cellulaire de garde");
    await sansViolation(chef, "fiche, note posée");

    await raconter(chef, "Enfin, elle exporte le suivi des alertes en fichier CSV.");
    await chef.getByTestId("als-export-format").selectOption("csv");
    const telechargement = chef.waitForEvent("download");
    await chef.getByTestId("als-export-exporter").click();
    expect((await telechargement).suggestedFilename()).toMatch(/^suivi-alertes.*\.csv$/);
    await chef.context().close();
  });
});
