import { test, expect } from "@playwright/test";
import { authedContext } from "./helpers/auth";
import { ouvrirNav } from "./helpers/nav";

const API = `http://localhost:${process.env.E2E_API_PORT ?? "4000"}`;

/**
 * E2E — Page « Mon compte » (Account) : profil, mot de passe et MFA.
 * Un employé dédié (jetable) est créé puis ses flags first-login sont levés
 * pour pouvoir tester la page sans toucher au compte admin.
 */
test.describe("Account — profil · mot de passe · MFA", () => {
  test("un employé sans fiche le voit dit, puis configure ses questions secrètes et son TOTP", async ({ page }) => {
    const uname = `acct.${Date.now().toString().slice(-6)}`;
    const pwd = "Passw0rd-Acct";

    // 1. L'administration crée un compte, puis lève les drapeaux du premier accès (API avec CSRF).
    const admin = await authedContext();
    const cree = await admin.post(`${API}/api/auth/users`, {
      data: { username: uname, password: pwd, role: "employee", mustChangePassword: false },
    });
    expect(cree.status()).toBe(201);

    const users = await admin.get(`${API}/api/auth/users`).then((r) => r.json());
    const u = users.find((x: any) => x.username === uname);
    expect(u).toBeTruthy();
    await admin.patch(`${API}/api/auth/users/${u.id}`, {
      data: { mustChangePassword: false, securitySetupRequired: false },
    });
    await admin.dispose();

    // 2. Login en tant que cet employé dans le navigateur (remplace le cookie admin).
    await page.request.post(`${API}/api/auth/login`, { data: { username: uname, password: pwd } });
    await page.goto("/");

    // 3. Aller sur Mon compte.
    await ouvrirNav(page, "nav-account");
    await expect(page.getByTestId("account-username")).toHaveText(uname);

    // 4. Le profil personnel : ce compte n'est relié à aucune fiche du répertoire, l'onglet le dit.
    await page.getByTestId("account-tab-profile").click();
    await expect(page.getByTestId("account-no-profile")).toBeVisible();

    // 5. Configurer la sécurité par questions secrètes (politique : au moins 3 distinctes).
    await page.getByTestId("account-tab-mfa").click();
    await page.getByTestId("account-mfa-question").check();
    // La liste de questions prédéfinies (≥ 3) alimente chaque menu déroulant.
    const presetSelect = page.getByTestId("account-question-select");
    await expect(presetSelect).toBeVisible();
    const optionCount = await presetSelect.locator("option").count();
    expect(optionCount).toBeGreaterThanOrEqual(3);

    // Sélectionne 3 questions distinctes + réponses.
    await presetSelect.selectOption({ index: 1 });
    await page.getByTestId("account-answer").fill("Rex");
    await page.getByTestId("account-question-select-1").selectOption({ index: 2 });
    await page.getByTestId("account-answer-1").fill("Tremblay");
    await page.getByTestId("account-question-select-2").selectOption({ index: 3 });
    await page.getByTestId("account-answer-2").fill("Montréal");
    await page.getByTestId("account-mfa-save").click();
    await expect(page.getByTestId("account-success")).toBeVisible();
    await expect(page.getByTestId("account-mfa-method")).toContainText(/question/i);

    // 6. Générer un QR code TOTP (Google Authenticator).
    await page.getByTestId("account-mfa-totp").check();
    await page.getByTestId("account-totp-init").click();
    await expect(page.getByTestId("account-totp-qr")).toBeVisible();
    await expect(page.getByTestId("account-totp-qr").locator("svg")).toBeVisible();
    await expect(page.getByTestId("account-totp-secret")).toBeVisible();
  });

  test("l'admin voit son identité et l'absence de profil employé", async ({ page }) => {
    await page.goto("/");
    await ouvrirNav(page, "nav-account");
    await expect(page.getByTestId("account-username")).toHaveText("admin");
    await page.getByTestId("account-tab-profile").click();
    await expect(page.getByTestId("account-no-profile")).toBeVisible();
    // Sections présentes (mot de passe + MFA), chacune dans son onglet
    await page.getByTestId("account-tab-password").click();
    await expect(page.getByTestId("account-password-form")).toBeVisible();
    await page.getByTestId("account-tab-mfa").click();
    await expect(page.getByTestId("account-mfa-form")).toBeVisible();
  });
});
