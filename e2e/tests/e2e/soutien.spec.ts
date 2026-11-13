/**
 * E2E — **Soutien** (AO 2026-PLAT-GA, exigences 27 à 31), par l'écran et contre le vrai serveur :
 * un billet reçoit son échéance, le soutien y répond et le résout, l'auteur ferme ; une maintenance
 * sous 72 h est refusée en le disant, une maintenance annoncée s'affiche à tout le monde ; la
 * disponibilité du mois se lit en chiffres.
 */
import { test, expect, type Page } from "@playwright/test";
import { GLOBAL_ACCOUNT, superadminContext } from "./helpers/auth";
import { createTenant, selectTenant, uniq } from "./helpers/organisme";
import { ouvrirNav } from "./helpers/nav";
import { expectNoA11yViolations } from "./helpers/a11y";

test.use({ storageState: { cookies: [], origins: [] } });

async function connecter(page: Page, user: string, pass: string) {
  await page.goto("/");
  await page.getByTestId("login-user").fill(user);
  await page.getByTestId("login-pass").fill(pass);
  await page.getByTestId("login-tenant").selectOption(GLOBAL_ACCOUNT);
  await page.getByTestId("login-submit").click();
}

test.describe.serial("Soutien — billets, délais, maintenances, disponibilité", () => {
  let ticketId = "";
  const sujet = `Export vide ${Date.now()}`;

  test("**l'administrateur ouvre un billet N2 : l'échéance est à deux heures, et l'écran dit qu'il reste du temps**", async ({ page }) => {
    await connecter(page, "admin", "admin");
    await ouvrirNav(page, "nav-soutien");
    // Refonte visuelle (D.6) : les niveaux et délais sont une vue à part ; les billets, la vue par défaut.
    await page.getByTestId("soutien-tab-niveaux").click();
    await expect(page.getByTestId("soutien-niveaux")).toContainText("N1");
    await expect(page.getByTestId("soutien-niveaux")).toContainText("24 h");
    await page.getByTestId("soutien-tab-billets").click();
    if (!(await page.getByTestId("soutien-billet-modal").isVisible().catch(() => false))) await page.getByTestId("soutien-billet-ouvrir").click();
    await page.getByTestId("soutien-sujet").fill(sujet);
    await page.getByTestId("soutien-categorie").selectOption("donnees");
    await page.getByTestId("soutien-niveau").selectOption("N2");
    await page.getByTestId("soutien-impact").selectOption("equipe");
    await page.getByTestId("soutien-description").fill("Le classeur des statistiques n'a aucune ligne pour septembre.");
    await page.getByTestId("soutien-ecran").fill("/admin/scolaire › Pilotage");
    // Deux pièces : une capture et un journal ; un exécutable est refusé avant l'envoi, en le disant.
    await page.getByTestId("soutien-pieces-fichiers").setInputFiles([
      { name: "capture.png", mimeType: "image/png", buffer: Buffer.from("png-factice") },
      { name: "journal.log", mimeType: "", buffer: Buffer.from("ERR 500 export") },
    ]);
    await expect(page.getByTestId("soutien-pieces-liste")).toContainText("capture.png");
    await expect(page.getByTestId("soutien-pieces-liste")).toContainText("journal.log");
    await page.getByTestId("soutien-pieces-fichiers").setInputFiles([{ name: "virus.exe", mimeType: "application/x-msdownload", buffer: Buffer.from("MZ") }]);
    await expect(page.getByTestId("soutien-pieces-refus")).toContainText("virus.exe : type non admis");
    await page.getByTestId("soutien-pieces-retirer-1").click();
    await expect(page.getByTestId("soutien-pieces-liste")).not.toContainText("journal.log");
    await page.getByTestId("soutien-ouvrir").click();
    await page.getByTestId("soutien-billet-modal").waitFor({ state: "hidden", timeout: 4000 }).catch(() => {});
    await expect(page.getByTestId("soutien-succes")).toContainText("Billet ouvert");
    const billet = page.locator('[data-testid^="soutien-billet-"]').filter({ hasText: sujet });
    await expect(billet).toContainText("Nouveau");
    await expect(billet).toContainText(/#\d+ — /);
    await expect(billet).toContainText("Donnée fausse ou manquante · touche mon équipe");
    await expect(billet).toContainText("écran : /admin/scolaire › Pilotage");
    await expect(billet.locator('[data-testid^="soutien-sla-"]')).toContainText(/il reste (2 h|1 h 5\d min)/);
    ticketId = (await billet.getAttribute("data-testid"))!.replace("soutien-billet-", "");
    // La pièce se lit sous le billet et se télécharge sous son nom.
    await expect(page.getByTestId(`soutien-pieces-${ticketId}`)).toContainText("capture.png");
    const telechargement = page.waitForEvent("download");
    await page.getByTestId(`soutien-pieces-${ticketId}`).locator("button").first().click();
    expect((await telechargement).suggestedFilename()).toBe("capture.png");
  });

  test("**le soutien répond et résout ; une maintenance sous 72 h est refusée, une à 96 h est annoncée**", async ({ page }) => {
    await connecter(page, "superadmin", "superadmin");
    await ouvrirNav(page, "nav-superadmin");
    // L'onglet Soutien porte le compte des billets ouverts ; le dispositif d'avis dit son transport et ses destinataires.
    await expect(page.getByTestId("sa-soutien-badge")).toHaveText(/\d+/);
    await page.getByTestId("sa-tab-soutien").click();
    await expect(page.getByTestId("sa-avis-transport-journal")).toContainText("SMTP_HOST");
    await expect(page.getByTestId("sa-avis-destinataires")).toContainText("soutien@nexuvina.test");
    await page.getByTestId("sa-avis-courriel").fill("chef.soutien@nexuvina.test");
    await page.getByTestId("sa-avis-enregistrer").click();
    await expect(page.getByTestId("sa-soutien-message")).toContainText("Courriel du compte enregistré");
    await expect(page.getByTestId("sa-avis-destinataires")).toContainText("chef.soutien@nexuvina.test");
    // Les billets sont groupés par établissement, nommé — et le courriel au soutien est parti à l'envoi.
    const groupe = page.locator('[data-testid^="sa-etablissement-"]', { hasText: sujet });
    await expect(groupe).toContainText(/\d+ billet\(s\), \d+ ouvert\(s\)/);
    await expect(page.getByTestId("sa-billets-retard")).toContainText("en retard");
    const billet = page.getByTestId(`sa-billet-${ticketId}`);
    await expect(billet).toContainText(sujet);
    const boite = await (await page.request.get("/api/auth/_outbox")).json();
    const avis = boite.filter((m: any) => String(m.subject).startsWith("[Soutien] Nouveau billet N2") && String(m.subject).includes(sujet));
    expect(avis.length, "avis courriel au soutien (SOUTIEN_COURRIEL)").toBeGreaterThan(0);
    expect(avis.map((m: any) => m.to)).toContain("soutien@nexuvina.test");
    await expect(billet).toContainText("Donnée fausse ou manquante · touche mon équipe");
    await expect(page.getByTestId(`sa-pieces-${ticketId}`)).toContainText("capture.png");
    await page.getByTestId(`sa-billet-reponse-${ticketId}`).fill("Nous reproduisons le problème, correctif en cours.");
    await page.getByTestId(`sa-billet-pieces-${ticketId}-fichiers`).setInputFiles([{ name: "correctif.pdf", mimeType: "application/pdf", buffer: Buffer.from("%PDF-1.4") }]);
    await page.getByTestId(`sa-billet-envoyer-${ticketId}`).click();
    await expect(page.getByTestId("sa-soutien-message")).toContainText("Réponse envoyée");
    await expect(billet).toContainText("En cours");
    await expect(billet).toContainText("correctif.pdf");
    await page.getByTestId(`sa-billet-resoudre-${ticketId}`).click();
    await expect(billet).toContainText("Résolu");
    await expect(page.getByTestId(`sa-billet-sla-${ticketId}`)).toContainText("résolu à temps");

    // Une maintenance demain : refusée, et la raison est écrite avec les heures qu'il reste.
    const local = (d: Date) => new Date(d.getTime() - d.getTimezoneOffset() * 60_000).toISOString().slice(0, 16);
    const demain = new Date(Date.now() + 24 * 3_600_000);
    if (!(await page.getByTestId("sa-maint-modal").isVisible().catch(() => false))) await page.getByTestId("sa-maint-ouvrir").click();
    await page.getByTestId("sa-maint-debut").fill(local(demain));
    await page.getByTestId("sa-maint-fin").fill(local(new Date(demain.getTime() + 3_600_000)));
    await page.getByTestId("sa-maint-description").fill("Migration de la base de données");
    await page.getByTestId("sa-maint-annoncer").click();
    await page.getByTestId("sa-maint-modal").waitFor({ state: "hidden", timeout: 4000 }).catch(() => {});
    await expect(page.getByTestId("sa-soutien-erreur")).toContainText("préavis insuffisant : 72 h");
    // À 96 h : acceptée, listée.
    const dans4j = new Date(Date.now() + 96 * 3_600_000);
    if (!(await page.getByTestId("sa-maint-modal").isVisible().catch(() => false))) await page.getByTestId("sa-maint-ouvrir").click();
    await page.getByTestId("sa-maint-debut").fill(local(dans4j));
    await page.getByTestId("sa-maint-fin").fill(local(new Date(dans4j.getTime() + 2 * 3_600_000)));
    await page.getByTestId("sa-maint-description").fill("Migration de la base de données");
    await page.getByTestId("sa-maint-annoncer").click();
    await page.getByTestId("sa-maint-modal").waitFor({ state: "hidden", timeout: 4000 }).catch(() => {});
    await expect(page.getByTestId("sa-soutien-message")).toContainText("Maintenance annoncée");
    await expect(page.getByTestId("sa-maint-liste")).toContainText("Planifiée");
    // La disponibilité du mois : des chiffres, pas un feu vert.
    await expect(page.getByTestId("sa-dispo-resume")).toContainText(/minutes observées|Aucune minute observée/);
  });

  test("**l'auteur voit la réponse, ferme le billet, et la maintenance annoncée lui est montrée**", async ({ page }) => {
    await connecter(page, "admin", "admin");
    await expect(page.getByTestId("maintenance-annonce")).toContainText("Maintenance annoncée");
    await expect(page.getByTestId("maintenance-annonce")).toContainText("Migration de la base de données");
    await ouvrirNav(page, "nav-soutien");
    const billet = page.getByTestId(`soutien-billet-${ticketId}`);
    await expect(billet).toContainText("Nous reproduisons le problème");
    await expect(billet).toContainText("correctif.pdf");
    await expect(billet).toContainText("Résolu");
    // Résolu : l'auteur note sa satisfaction, puis confirme et ferme ; le billet quitte la liste « Ouverts ».
    await page.getByTestId(`satisfaction-${ticketId}-4`).check();
    await page.getByTestId(`satisfaction-${ticketId}-commentaire`).fill("Réglé vite.");
    await page.getByTestId(`satisfaction-${ticketId}-envoyer`).click();
    await expect(page.getByTestId("soutien-succes")).toContainText("votre avis est noté");
    await expect(page.getByTestId(`soutien-satisfaction-${ticketId}`)).toContainText("votre note : 4/5");
    await page.getByTestId(`soutien-fermer-${ticketId}`).click();
    await expect(page.getByTestId("soutien-succes")).toContainText("Billet fermé");
    await expect(page.getByTestId("soutien-filtre-tous")).toBeVisible();
    await expect(billet).toHaveCount(0);
    await page.getByTestId("soutien-filtre-tous").click();
    await expect(page.getByTestId(`soutien-statut-${ticketId}`)).toContainText("Fermé");
  });

  test("**la satisfaction se lit dans la console du soutien**", async ({ page }) => {
    await connecter(page, "superadmin", "superadmin");
    await ouvrirNav(page, "nav-superadmin");
    await page.getByTestId("sa-tab-soutien").click();
    await expect(page.getByTestId(`sa-billet-satisfaction-${ticketId}`)).toContainText("satisfaction 4/5 « Réglé vite. »");
  });

  test("**un organisme sous contrat 26-015-DO : le soutien lui attribue le service essentiel, l'organisme voit le rappel puis la résolution**", async ({ page, browser }) => {
    const nom = uniq("Soutien contrat");
    const sa = await superadminContext();
    const tenantId = await createTenant(sa, nom);
    await sa.dispose();
    await connecter(page, "superadmin", "superadmin");
    await ouvrirNav(page, "nav-superadmin");
    await page.getByTestId("sa-tab-soutien").click();
    await page.getByTestId("sa-engagements-filtre").fill(nom);
    await page.getByTestId(`sa-engagement-choix-${tenantId}`).selectOption("service-essentiel");
    await expect(page.getByTestId("sa-engagements-annonce")).toContainText(`${nom} : politique « Service essentiel`);
    await expect(page.getByTestId(`sa-engagement-${tenantId}`)).toContainText(" par ");
    await expectNoA11yViolations(page);

    const ctx = await browser.newContext({ storageState: { cookies: [], origins: [] } });
    const organisme = await ctx.newPage();
    await selectTenant(organisme, tenantId);
    await ouvrirNav(organisme, "nav-soutien");
    await organisme.getByTestId("soutien-tab-niveaux").click();
    await expect(organisme.getByTestId("soutien-heures")).toContainText("de 05:00 à 23:00, 7 jours sur 7");
    await expect(organisme.getByTestId("soutien-niveaux")).toContainText("30 min / 1 h");
    await expect(organisme.getByTestId("soutien-niveaux")).toContainText("7 j");
    await expectNoA11yViolations(organisme);
    await organisme.getByTestId("soutien-tab-billets").click();
    if (!(await organisme.getByTestId("soutien-billet-modal").isVisible().catch(() => false))) await organisme.getByTestId("soutien-billet-ouvrir").click();
    const sujetContrat = `Horaire illisible ${Date.now()}`;
    await organisme.getByTestId("soutien-sujet").fill(sujetContrat);
    await organisme.getByTestId("soutien-niveau").selectOption("N1");
    await organisme.getByTestId("soutien-description").fill("Aucune affectation ne s'affiche dans l'horaire de la nuit.");
    await organisme.getByTestId("soutien-ouvrir").click();
    await expect(organisme.getByTestId("soutien-succes")).toContainText("Billet ouvert");
    const billet = organisme.locator('[data-testid^="soutien-billet-"]').filter({ hasText: sujetContrat });
    await expect(billet.locator('[data-testid^="soutien-rappel-"]')).toContainText("rappel attendu, il reste");
    await expectNoA11yViolations(organisme);
    await ctx.close();
  });

  test.afterAll(async ({ request }) => {
    // Sans cela, la bannière d'annonce resterait allumée pour toute la campagne.
    const r = await request.post(`http://localhost:${process.env.E2E_API_PORT ?? "4000"}/api/auth/login`, { data: { username: "superadmin", password: "superadmin" } });
    const jeton = (await r.json()).token;
    const liste = await (await request.get(`http://localhost:${process.env.E2E_API_PORT ?? "4000"}/api/soutien/maintenances`, { headers: { Authorization: `Bearer ${jeton}` } })).json();
    for (const m of liste.filter((x: any) => x.statut === "planifiee")) {
      await request.post(`http://localhost:${process.env.E2E_API_PORT ?? "4000"}/api/soutien/maintenances/${m.id}/annuler`, { headers: { Authorization: `Bearer ${jeton}` } });
    }
  });
});
