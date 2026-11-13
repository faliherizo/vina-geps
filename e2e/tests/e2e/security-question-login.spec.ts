/**
 * E2E réel (IHM) — Connexion par **question secrète** : configuration au 1er accès puis
 * reconnexion avec les **bonnes** réponses → accès à l'application. Piloté par JSON.
 */
import { test, expect, request as pwRequest } from "@playwright/test";
import { GLOBAL_ACCOUNT } from "./helpers/auth";
import * as fs from "fs";
import * as path from "path";
import fixture from "../../mocks/security-login.json";

const API = `http://localhost:${process.env.E2E_API_PORT ?? "4000"}`;

test.use({ storageState: { cookies: [], origins: [] } });

async function adminApi() {
  const storagePath = path.join(__dirname, "..", "..", ".auth", "admin.json");
  const state = JSON.parse(fs.readFileSync(storagePath, "utf8"));
  const csrf = state.cookies?.find((c: any) => c.name === "csrf")?.value ?? "";
  return pwRequest.newContext({ storageState: storagePath, extraHTTPHeaders: csrf ? { "X-CSRF-Token": csrf } : {} });
}

for (const c of fixture.cases) {
  test(`Login question secrète — ${c.name}`, async ({ page }) => {
    // 1) Créer un compte avec un mot de passe provisoire.
    const adminCtx = await adminApi();
    const unique = `sq${Date.now().toString().slice(-7)}${Math.floor(Math.random() * 90 + 10)}`;
    const r = await adminCtx.post(`${API}/api/auth/users`, {
      data: { username: unique, password: "Temporaire-2026", role: "employee" },
    });
    expect(r.status()).toBe(201);
    const tempPwd = "Temporaire-2026";
    await adminCtx.dispose();

    // 2) 1re connexion → écran FirstLogin (changement de mot de passe imposé).
    await page.goto("/");
    await page.getByTestId("login-user").fill(unique);
    await page.getByTestId("login-pass").fill(tempPwd);
    await page.getByTestId("login-tenant").selectOption(GLOBAL_ACCOUNT);
    await page.getByTestId("login-submit").click();
    await expect(page.getByTestId("first-login")).toBeVisible({ timeout: 10000 });
    const newPwd = "NouveauPwd!2025";
    await page.getByTestId("first-login-newpwd").fill(newPwd);
    await page.getByTestId("first-login-newpwd2").fill(newPwd);
    await page.getByTestId("first-login-pwd-submit").click();

    // 3) Configuration de la sécurité : questions secrètes + réponses (JSON).
    await page.getByTestId("sec-method-question").check();
    for (let i = 0; i < c.questionIndexes.length; i++) {
      const sel = i === 0 ? "sec-question-select" : `sec-question-select-${i}`;
      const ans = i === 0 ? "sec-answer" : `sec-answer-${i}`;
      await page.getByTestId(sel).selectOption({ index: c.questionIndexes[i] });
      await page.getByTestId(ans).fill(c.answers[i]);
    }
    await page.getByTestId("first-login-sec-submit").click();
    await expect(page.getByTestId("nav-dashboard")).toHaveCount(1, { timeout: 5000 });

    // 4) Déconnexion.
    await page.getByTestId("logout").click();
    await expect(page.getByTestId("login-form")).toBeVisible({ timeout: 10000 });

    // 5) Reconnexion : mot de passe → invite question secrète → **bonnes** réponses → accès app.
    await page.getByTestId("login-user").fill(unique);
    await page.getByTestId("login-pass").fill(newPwd);
    await page.getByTestId("login-tenant").selectOption(GLOBAL_ACCOUNT);
    await page.getByTestId("login-submit").click();
    await expect(page.getByTestId("login-mfa-answer")).toBeVisible({ timeout: 10000 });
    for (let i = 0; i < c.login.length; i++) {
      const ans = i === 0 ? "login-mfa-answer" : `login-mfa-answer-${i}`;
      await page.getByTestId(ans).fill(c.login[i]);
    }
    await page.getByTestId("login-tenant").selectOption(GLOBAL_ACCOUNT);
    await page.getByTestId("login-submit").click();

    // ✅ Doit être authentifié (application visible), pas de nouvelle invite MFA.
    await expect(page.getByTestId("nav-dashboard")).toHaveCount(1, { timeout: 8000 });
    await expect(page.getByTestId("login-mfa-answer")).toHaveCount(0);
  });
}
