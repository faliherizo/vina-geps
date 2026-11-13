import { test, expect, request as pwRequest } from "@playwright/test";
import { GLOBAL_ACCOUNT } from "./helpers/auth";

/**
 * E2E Phase 6 : authentification par cookies httpOnly + CSRF + logout.
 *
 * - Vérifie que le login pose les cookies `session` (httpOnly) et `csrf` (lisible JS).
 * - Vérifie que le user persiste après reload de la page (cookie persisté).
 * - Vérifie que le logout efface le cookie et redirige vers la page de login.
 * - Vérifie que les requêtes mutantes via cookie envoient bien `X-CSRF-Token`.
 */

test.describe("Phase 6 — Authentification par cookies", () => {
  // Ces tests gèrent leur propre session : on part propre.
  test.use({ storageState: { cookies: [], origins: [] } });

  test.beforeEach(async () => {
    const ctx = await pwRequest.newContext();
    await ctx.post(`http://localhost:${process.env.E2E_API_PORT ?? "4000"}/api/auth/_reset`);
    await ctx.dispose();
  });

  test("login pose les cookies session (httpOnly) + csrf et /me retourne l'utilisateur", async ({ page, context }) => {
    // Vide tout localStorage pour forcer l'auth via cookie.
    await page.goto("/");
    await page.evaluate(() => localStorage.clear());

    await page.goto("/");
    await expect(page.getByTestId("login-form")).toBeVisible();
    await page.getByTestId("login-user").fill("admin");
    await page.getByTestId("login-pass").fill("admin");
    await page.getByTestId("login-tenant").selectOption(GLOBAL_ACCOUNT);
    await page.getByTestId("login-submit").click();

    // On voit le menu après login → cookie session valide.
    await expect(page.getByTestId("auth-user")).toContainText("admin");

    // Vérifie les cookies posés par le backend.
    const cookies = await context.cookies(`http://localhost:${process.env.E2E_API_PORT ?? "4000"}`);
    const session = cookies.find((c) => c.name === "session");
    const csrf = cookies.find((c) => c.name === "csrf");
    expect(session).toBeTruthy();
    expect(session?.httpOnly).toBe(true);
    expect(csrf).toBeTruthy();
    expect(csrf?.httpOnly).toBe(false);
  });

  test("la session persiste après reload (cookie httpOnly survit)", async ({ page }) => {
    await page.goto("/");
    await page.evaluate(() => localStorage.clear());

    await page.goto("/");
    await page.getByTestId("login-user").fill("admin");
    await page.getByTestId("login-pass").fill("admin");
    await page.getByTestId("login-tenant").selectOption(GLOBAL_ACCOUNT);
    await page.getByTestId("login-submit").click();
    await expect(page.getByTestId("auth-user")).toContainText("admin");

    // Reload : on doit rester connecté grâce au cookie httpOnly.
    await page.reload();
    await expect(page.getByTestId("auth-user")).toContainText("admin");
    await expect(page.getByTestId("login-form")).not.toBeVisible();
  });

  test("logout efface le cookie et ramène à la page de login", async ({ page, context }) => {
    await page.goto("/");
    await page.evaluate(() => localStorage.clear());

    await page.goto("/");
    await page.getByTestId("login-user").fill("admin");
    await page.getByTestId("login-pass").fill("admin");
    await page.getByTestId("login-tenant").selectOption(GLOBAL_ACCOUNT);
    await page.getByTestId("login-submit").click();
    await expect(page.getByTestId("auth-user")).toContainText("admin");

    await page.getByTestId("logout").click();
    await expect(page.getByTestId("login-form")).toBeVisible();

    const cookies = await context.cookies(`http://localhost:${process.env.E2E_API_PORT ?? "4000"}`);
    const session = cookies.find((c) => c.name === "session");
    // Le cookie peut être absent ou vide (clearCookie côté serveur).
    expect(session?.value || "").toBe("");

    // Reload : toujours sur la page de login.
    await page.reload();
    await expect(page.getByTestId("login-form")).toBeVisible();
  });

  test("CSRF : une mutation API fonctionne via le cookie + en-tête X-CSRF-Token", async ({ page }) => {
    await page.goto("/");
    await page.evaluate(() => localStorage.clear());
    await page.goto("/");
    await page.getByTestId("login-user").fill("admin");
    await page.getByTestId("login-pass").fill("admin");
    await page.getByTestId("login-tenant").selectOption(GLOBAL_ACCOUNT);
    await page.getByTestId("login-submit").click();
    await expect(page.getByTestId("auth-user")).toContainText("admin");

    // Une mutation émise avec credentials et X-CSRF-Token : la création d'un compte.
    const status = await page.evaluate(async () => {
      const m = document.cookie.match(/(?:^|; )csrf=([^;]*)/);
      const csrf = m ? decodeURIComponent(m[1]) : "";
      const r = await fetch("/api/auth/users", {
        method: "POST",
        credentials: "include",
        headers: { "Content-Type": "application/json", "X-CSRF-Token": csrf },
        body: JSON.stringify({ username: "csrf-" + Date.now(), password: "Temporaire-2026", role: "employee" }),
      });
      return r.status;
    });
    expect(status).toBe(201);
  });

  test("CSRF : une mutation sans X-CSRF-Token est refusée (403)", async ({ page, context }) => {
    await page.goto("/");
    await page.evaluate(() => localStorage.clear());
    await page.goto("/");
    await page.getByTestId("login-user").fill("admin");
    await page.getByTestId("login-pass").fill("admin");
    await page.getByTestId("login-tenant").selectOption(GLOBAL_ACCOUNT);
    await page.getByTestId("login-submit").click();
    await expect(page.getByTestId("auth-user")).toContainText("admin");

    // On émet la requête depuis le contexte navigateur (hors patch global du fetch),
    // en transportant explicitement le cookie session sans X-CSRF-Token.
    const cookies = await context.cookies();
    const session = cookies.find((c) => c.name === "session");
    expect(session).toBeTruthy();
    const r = await page.request.post(`http://localhost:${process.env.E2E_API_PORT ?? "4000"}/api/auth/users`, {
      headers: {
        "Content-Type": "application/json",
        Cookie: `session=${session!.value}`,
      },
      data: { username: "sans-csrf-" + Date.now(), password: "Temporaire-2026", role: "employee" },
    });
    expect(r.status()).toBe(403);
  });
});
