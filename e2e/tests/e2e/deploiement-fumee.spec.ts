/**
 * **Contrôle de fumée d'un déploiement** — vise une pile déjà lancée (`E2E_BASE_URL`).
 *
 * Il ne remplace pas la campagne : il répond à une question que la campagne ne pose pas, parce
 * qu'elle tourne contre un backend d'essai. Ici, c'est **l'image déployée** qui répond, en mode
 * production, sans route `_reset` ni établissement injecté.
 *
 * La question à laquelle il répond : *le backend Node est-il encore nécessaire ?* La pile
 * `docker-compose.yml` le réduit à un travail de migration (`migrations`) qui s'arrête ; ce qui suit
 * s'exécute donc contre le **seul** backend Java.
 *
 * Sans `E2E_BASE_URL`, l'essai se saute — il n'a rien à viser, et le faire échouer en campagne
 * ordinaire n'apprendrait rien à personne.
 */
import { test, expect } from "@playwright/test";
import { GLOBAL_ACCOUNT } from "./helpers/auth";

const DEPLOYE = !!process.env.E2E_BASE_URL;
/**
 * Le mot de passe du superadmin de la pile visée : un déploiement public n'a pas `superadmin/superadmin`. Il vient de
 * l'environnement (secret GEPS_SUPERADMIN_PASSWORD du pipeline), jamais d'un fichier suivi.
 */
const SUPERADMIN = { username: process.env.E2E_SUPERADMIN_USER ?? "superadmin", password: process.env.E2E_SUPERADMIN_PASSWORD ?? "superadmin" };

test.use({ storageState: { cookies: [], origins: [] } });

test.describe("Déploiement — la pile répond sans backend Node", () => {
  test.skip(!DEPLOYE, "vise une pile déployée : poser E2E_BASE_URL");

  test("le superadmin se connecte, et les deux écrans neufs se rendent", async ({ page }) => {
    test.setTimeout(120_000);
    // **La racine sert la vitrine**, pas l'application : sur un déploiement, celle-ci vit sous
    // `/admin`. Viser « / » attendait `login-user` sur une page de présentation — un échec qui
    // ressemble à une panne d'authentification alors que ce n'est qu'une adresse.
    // La liste des organismes arrive après le premier rendu, et son arrivée réinitialise le choix : sélectionner avant
    // envoyait le formulaire sans organisme, et la page restait sur la connexion sans dire pourquoi.
    const organismes = page.waitForResponse((r) => r.url().includes("/api/auth/tenants"), { timeout: 15_000 }).catch(() => null);
    await page.goto("/admin/login");
    await organismes;
    // Le sélecteur d'établissement n'existe que si la résolution se fait par le choix : le
    // superadmin y prend le compte global.
    const selecteur = page.getByTestId("login-tenant");
    if (await selecteur.isVisible().catch(() => false)) await selecteur.selectOption(GLOBAL_ACCOUNT);
    await page.getByTestId("login-user").fill(SUPERADMIN.username);
    await page.getByTestId("login-pass").fill(SUPERADMIN.password);
    await page.getByTestId("login-submit").click();
    await expect(page.getByTestId("logout")).toBeVisible({ timeout: 60_000 });

    // L'espace superadmin, et ses onglets groupés par famille.
    await page.goto("/admin/superadmin");
    await expect(page.getByTestId("sa-tabs")).toBeVisible({ timeout: 30_000 });
    for (const famille of ["clients", "offre", "plateforme"]) {
      await expect(page.getByTestId(`sa-famille-${famille}`)).toBeVisible();
    }

    // Consommation d'avis : l'agrégat inter-organismes, servi par le Java.
    await page.getByTestId("sa-tab-consommation").click();
    await expect(page.getByTestId("sa-conso-table")).toBeVisible();
    await expect(page.getByTestId("sa-conso-resume")).toContainText(/segment/);

    // Sous-menus : le catalogue des 69 vues, servi par le Java.
    await page.getByTestId("sa-tab-modules").click();
    await expect(page.getByTestId("sous-menus-manager")).toBeVisible();
    await expect(page.getByTestId("sousmenus-resume")).toContainText(/entrée\(s\) visible\(s\) sur \d+/);
    await expect(page.getByTestId("sous-menus-manager")).toContainText(/pas une permission/i);
  });

  test("l'API déployée sert les routes neuves — et c'est le Java qui répond", async ({ page }) => {
    const connexion = await page.request.post("/api/auth/login", { data: SUPERADMIN });
    expect(connexion.ok()).toBeTruthy();

    for (const chemin of ["/api/saas/vues", "/api/saas/consommation", "/api/saas/my-modules"]) {
      const r = await page.request.get(chemin);
      expect(r.status(), chemin).toBe(200);
    }
    // Le catalogue est servi et non vide. Son compte exact appartient aux essais du serveur, pas à la fumée : figé ici
    // (69 vues, 7 sections), il est devenu faux au premier menu remanié sans que le déploiement le soit.
    const catalogue = await (await page.request.get("/api/saas/vues")).json();
    expect(catalogue.vues.length).toBeGreaterThan(0);
    expect(catalogue.sections.length).toBeGreaterThan(0);
  });
});
