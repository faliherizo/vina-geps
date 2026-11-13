/**
 * E2E réel (IHM) — Connexion par **question secrète** pour un **admin d'établissement** (tenant),
 * c.-à-d. le cas exact rapporté : username « admin » + établissement sélectionné. Piloté par JSON.
 */
import { test, expect } from "@playwright/test";
import { superadminContext } from "./helpers/auth";
import { uniq } from "./helpers/organisme";
import fixture from "../../mocks/security-login.json";

const API = `http://localhost:${process.env.E2E_API_PORT ?? "4000"}`;

test.use({ storageState: { cookies: [], origins: [] } });

for (const c of fixture.cases) {
  test(`Login question secrète (admin tenant) — ${c.name}`, async ({ page }) => {
    // 1) Le superadmin crée un établissement + son admin (mot de passe provisoire, setup imposé).
    const sa = await superadminContext();
    const name = uniq("SQTenant");
    const created = await (await sa.post(`${API}/api/superadmin/tenants`, {
      data: { name, type: "government", country: "CA", region: "QC", planCode: "enterprise", adminUsername: "admin" },
    })).json();
    await sa.dispose();
    const tenantId: string = created.tenant.id;
    const tempPwd: string = created.temporaryPassword;
    expect(tempPwd).toBeTruthy();

    // 2) 1re connexion IHM : établissement sélectionné + admin + mot de passe provisoire → FirstLogin.
    await page.goto("/");
    await page.getByTestId("login-tenant").selectOption(tenantId);
    await page.getByTestId("login-user").fill("admin");
    await page.getByTestId("login-pass").fill(tempPwd);
    await page.getByTestId("login-submit").click();
    await expect(page.getByTestId("first-login")).toBeVisible({ timeout: 10000 });
    const newPwd = "NouveauPwd!2025";
    await page.getByTestId("first-login-newpwd").fill(newPwd);
    await page.getByTestId("first-login-newpwd2").fill(newPwd);
    await page.getByTestId("first-login-pwd-submit").click();

    // 3) Configuration des questions secrètes + réponses (JSON).
    await page.getByTestId("sec-method-question").check();
    for (let i = 0; i < c.questionIndexes.length; i++) {
      await page.getByTestId(i === 0 ? "sec-question-select" : `sec-question-select-${i}`).selectOption({ index: c.questionIndexes[i] });
      await page.getByTestId(i === 0 ? "sec-answer" : `sec-answer-${i}`).fill(c.answers[i]);
    }
    await page.getByTestId("first-login-sec-submit").click();
    await expect(page.getByTestId("nav-dashboard")).toHaveCount(1, { timeout: 5000 });

    // 4) Déconnexion.
    await page.getByTestId("logout").click();
    await expect(page.getByTestId("login-form")).toBeVisible({ timeout: 10000 });

    // 5) Reconnexion admin/établissement : mot de passe → question secrète → **bonnes** réponses → app.
    await page.getByTestId("login-tenant").selectOption(tenantId);
    await page.getByTestId("login-user").fill("admin");
    await page.getByTestId("login-pass").fill(newPwd);
    await page.getByTestId("login-submit").click();
    await expect(page.getByTestId("login-mfa-answer")).toBeVisible({ timeout: 10000 });
    for (let i = 0; i < c.login.length; i++) {
      await page.getByTestId(i === 0 ? "login-mfa-answer" : `login-mfa-answer-${i}`).fill(c.login[i]);
    }
    await page.getByTestId("login-submit").click();

    // ✅ Authentifié (app visible), pas de nouvelle invite MFA.
    await expect(page.getByTestId("nav-dashboard")).toHaveCount(1, { timeout: 8000 });
    await expect(page.getByTestId("login-mfa-answer")).toHaveCount(0);
  });
}
