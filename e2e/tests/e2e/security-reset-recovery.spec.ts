/**
 * E2E réel (IHM) — Récupération de compte : un admin d'établissement a **oublié** ses réponses de
 * sécurité (login bloqué en boucle « mfaRequired »). Le superadmin **réinitialise** sa sécurité
 * depuis son espace ; l'admin peut se reconnecter avec son mot de passe et reconfigurer de
 * nouvelles questions, puis se connecter avec les **nouvelles** réponses.
 */
import { test, expect } from "@playwright/test";
import { superadminContext, GLOBAL_ACCOUNT } from "./helpers/auth";
import { uniq } from "./helpers/organisme";
import { ouvrirNav } from "./helpers/nav";

const API = `http://localhost:${process.env.E2E_API_PORT ?? "4000"}`;
const NEW_PWD = "NouveauPwd!2025";

test.use({ storageState: { cookies: [], origins: [] } });

async function setupQuestions(page: any, answers: string[]) {
  await page.getByTestId("sec-method-question").check();
  for (let i = 0; i < 3; i++) {
    await page.getByTestId(i === 0 ? "sec-question-select" : `sec-question-select-${i}`).selectOption({ index: i + 1 });
    await page.getByTestId(i === 0 ? "sec-answer" : `sec-answer-${i}`).fill(answers[i]);
  }
  await page.getByTestId("first-login-sec-submit").click();
}

test("Récupération : le superadmin réinitialise la sécurité d'un admin bloqué (question oubliée)", async ({ page }) => {
  // 1) Superadmin crée l'établissement + admin (mot de passe provisoire).
  const sa = await superadminContext();
  const created = await (await sa.post(`${API}/api/superadmin/tenants`, {
    data: { name: uniq("Recov"), type: "government", country: "CA", region: "QC", planCode: "enterprise", adminUsername: "admin" },
  })).json();
  await sa.dispose();
  const tenantId: string = created.tenant.id;
  const adminUserId: string = created.admin.userId;
  const tempPwd: string = created.temporaryPassword;

  // 2) 1re connexion admin → change mot de passe → configure des questions (réponses OUBLIÉES ensuite).
  await page.goto("/");
  await page.getByTestId("login-tenant").selectOption(tenantId);
  await page.getByTestId("login-user").fill("admin");
  await page.getByTestId("login-pass").fill(tempPwd);
  await page.getByTestId("login-submit").click();
  await expect(page.getByTestId("first-login")).toBeVisible({ timeout: 10000 });
  await page.getByTestId("first-login-newpwd").fill(NEW_PWD);
  await page.getByTestId("first-login-newpwd2").fill(NEW_PWD);
  await page.getByTestId("first-login-pwd-submit").click();
  await setupQuestions(page, ["Rex", "Tremblay", "Montréal"]);
  await expect(page.getByTestId("nav-dashboard")).toHaveCount(1, { timeout: 5000 });
  await page.getByTestId("logout").click();
  await expect(page.getByTestId("login-form")).toBeVisible({ timeout: 10000 });

  // 3) L'admin a oublié ses réponses : tentative avec de mauvaises réponses → reste bloqué.
  await page.getByTestId("login-tenant").selectOption(tenantId);
  await page.getByTestId("login-user").fill("admin");
  await page.getByTestId("login-pass").fill(NEW_PWD);
  await page.getByTestId("login-submit").click();
  await expect(page.getByTestId("login-mfa-answer")).toBeVisible({ timeout: 10000 });
  for (let i = 0; i < 3; i++) await page.getByTestId(i === 0 ? "login-mfa-answer" : `login-mfa-answer-${i}`).fill("Faly");
  await page.getByTestId("login-submit").click();
  await expect(page.getByTestId("login-error")).toContainText(/recommencer/i);

  // 4) Le superadmin se connecte et réinitialise la sécurité de cet admin depuis son espace (IHM).
  await page.goto("/");
  await page.getByTestId("login-user").fill("superadmin");
  await page.getByTestId("login-pass").fill("superadmin");
  await page.getByTestId("login-tenant").selectOption(GLOBAL_ACCOUNT);
  await page.getByTestId("login-submit").click();
  await ouvrirNav(page, "nav-superadmin");
  await page.getByTestId(`sa-reset-security-${adminUserId}`).click();
  // Confirmation en modal — plus de fenêtre native du navigateur.
  await page.getByTestId("sa-reset-security-modal-confirm").click();
  await expect(page.getByTestId("sa-success")).toContainText(/réinitialisée/i);
  await page.getByTestId("logout").click();
  await expect(page.getByTestId("login-form")).toBeVisible({ timeout: 10000 });

  // 5) L'admin se reconnecte : mot de passe accepté SANS question (sécurité réinitialisée) →
  //    l'écran de configuration de sécurité s'affiche → il définit de NOUVELLES réponses.
  await page.getByTestId("login-tenant").selectOption(tenantId);
  await page.getByTestId("login-user").fill("admin");
  await page.getByTestId("login-pass").fill(NEW_PWD);
  await page.getByTestId("login-submit").click();
  await expect(page.getByTestId("first-login")).toBeVisible({ timeout: 10000 });
  await setupQuestions(page, ["Bella", "Gagnon", "Québec"]);
  await expect(page.getByTestId("nav-dashboard")).toHaveCount(1, { timeout: 5000 });
  await page.getByTestId("logout").click();
  await expect(page.getByTestId("login-form")).toBeVisible({ timeout: 10000 });

  // 6) Connexion finale avec les NOUVELLES réponses → accès accordé.
  await page.getByTestId("login-tenant").selectOption(tenantId);
  await page.getByTestId("login-user").fill("admin");
  await page.getByTestId("login-pass").fill(NEW_PWD);
  await page.getByTestId("login-submit").click();
  await expect(page.getByTestId("login-mfa-answer")).toBeVisible({ timeout: 10000 });
  const ans = ["Bella", "Gagnon", "Québec"];
  for (let i = 0; i < 3; i++) await page.getByTestId(i === 0 ? "login-mfa-answer" : `login-mfa-answer-${i}`).fill(ans[i]);
  await page.getByTestId("login-submit").click();
  await expect(page.getByTestId("nav-dashboard")).toHaveCount(1, { timeout: 8000 });
  await expect(page.getByTestId("login-mfa-answer")).toHaveCount(0);
});
