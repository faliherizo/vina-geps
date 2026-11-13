import { test, expect } from "@playwright/test";
import { forcerFrancais } from "./helpers/auth";
import { createTenant, uniq, TENANT_ADMIN_USER, TENANT_ADMIN_PASS } from "./helpers/organisme";
import { expectNoA11yViolations } from "./helpers/a11y";

/**
 * E2E « Connexion à un établissement ».
 *
 * On ne se connecte jamais dans l'absolu : c'est l'établissement qui définit le périmètre de
 * données, les règles de paie et la piste d'audit de la session. Le choix est donc obligatoire.
 *
 * En production, il provient du **sous-domaine** — chaque établissement a son URL — et
 * l'utilisateur n'a rien à désigner. Le sélecteur est l'équivalent hors sous-domaine dédié.
 */
test.use({ storageState: { cookies: [], origins: [] } });

test.describe("Connexion — établissement obligatoire", () => {
  test.beforeEach(async ({ page }) => { await forcerFrancais(page); });   // les libellés lus sont français
  test("refuse la soumission sans établissement, puis connecte une fois choisi", async ({ page }) => {
    const tenantName = uniq("École Connexion");
    await createTenant(page.request, tenantName);

    await page.goto("/");
    await expect(page.getByTestId("login-form")).toBeVisible();

    // Aucun établissement n'est présélectionné : le placeholder est désactivé, donc rien
    // ne part par défaut.
    const select = page.getByTestId("login-tenant");
    await expect(select).toBeVisible();
    await expect(select).toHaveValue("");

    // Tenter de se connecter sans choisir : refus explicite, aucune requête d'authentification.
    await page.getByTestId("login-user").fill(TENANT_ADMIN_USER);
    await page.getByTestId("login-pass").fill(TENANT_ADMIN_PASS);
    await page.getByTestId("login-submit").click();
    await expect(page.getByTestId("login-error")).toContainText(/Sélectionnez votre organisme/);
    await expect(page.getByTestId("login-form")).toBeVisible();

    await expectNoA11yViolations(page);

    // Choix de l'établissement : la connexion aboutit.
    const value = await select.locator("option", { hasText: tenantName }).first().getAttribute("value");
    await select.selectOption(value as string);
    await page.getByTestId("login-submit").click();
    await expect(page.getByTestId("login-form")).toHaveCount(0);
  });

  test("propose un choix explicite pour les comptes d'administration système", async ({ page }) => {
    await page.goto("/");
    const select = page.getByTestId("login-tenant");
    // Les comptes globaux ne relèvent d'aucun établissement : l'option existe et se
    // distingue d'une absence de choix.
    await expect(select.locator("option", { hasText: "Administration système" })).toHaveCount(1);

    await select.selectOption("__global__");
    await page.getByTestId("login-user").fill("superadmin");
    await page.getByTestId("login-pass").fill("superadmin");
    await page.getByTestId("login-submit").click();
    await expect(page.getByTestId("login-form")).toHaveCount(0);
  });
});
