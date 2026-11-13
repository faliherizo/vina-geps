/**
 * E2E — l'emprunt d'identité (lot E-3, ADR 0038), vu de l'écran : l'administration ouvre le réglage,
 * agit comme un employé (motif exigé), voit le bandeau qui nomme les deux identités et l'échéance,
 * ne peut pas toucher aux secrets de la personne, puis termine et retrouve sa session — le journal
 * des emprunts le consigne.
 */
import { test, expect, request as pwRequest, type Page } from "@playwright/test";
import { superadminContext, forcerFrancais } from "./helpers/auth";
import { createTenant, setTenantModules, uniq, MODULES_SOCLE, TENANT_ADMIN_USER, TENANT_ADMIN_PASS } from "./helpers/organisme";
import { ouvrirNav } from "./helpers/nav";

const API = `http://localhost:${process.env.E2E_API_PORT ?? "4000"}`;

test.use({ storageState: { cookies: [], origins: [] } });

async function seConnecter(page: Page, tenantId: string, user = TENANT_ADMIN_USER, pass = TENANT_ADMIN_PASS) {
  await page.goto("/");
  await page.context().clearCookies();
  await page.evaluate(() => localStorage.clear()).catch(() => {});
  await page.goto("/");
  await page.getByTestId("login-tenant").selectOption(tenantId);
  await page.getByTestId("login-user").fill(user);
  await page.getByTestId("login-pass").fill(pass);
  await page.getByTestId("login-submit").click();
  await expect(page.getByTestId("dashboard-page")).toBeVisible({ timeout: 20_000 });
}

test.describe.serial("Emprunt d'identité", () => {
  test.beforeEach(async ({ page }) => { await forcerFrancais(page); });
  let tenantId = "";
  let cibleId = "";

  test.beforeAll(async () => {
    const ctx = await superadminContext();
    try {
      tenantId = await createTenant(ctx, uniq("EMPR"));
      await setTenantModules(ctx, tenantId, [...MODULES_SOCLE]);
    } finally { await ctx.dispose(); }
    const connexion = await pwRequest.newContext({ storageState: { cookies: [], origins: [] } });
    const jeton = (await (await connexion.post(`${API}/api/auth/login`, { data: { username: TENANT_ADMIN_USER, password: TENANT_ADMIN_PASS, tenantId } })).json()).token;
    await connexion.dispose();
    const api = await pwRequest.newContext({ storageState: { cookies: [], origins: [] }, extraHTTPHeaders: { Authorization: `Bearer ${jeton}`, "X-Tenant-Id": tenantId } });
    try {
      const u = await (await api.post(`${API}/api/auth/users`, { data: { username: "lea-emprunt", password: "Passe!Test2026", role: "employee" } })).json();
      cibleId = u.id;
      expect(cibleId).toBeTruthy();
    } finally { await api.dispose(); }
  });

  test("**fermé par défaut ; ouvert, l'administration agit comme un employé, le bandeau le dit, les secrets restent interdits, Terminer rend la session**", async ({ page }) => {
    await seConnecter(page, tenantId);
    await ouvrirNav(page, "nav-admin");
    await page.getByTestId("admin-tab-comptes").click();
    await expect(page.getByTestId("emprunt-panneau")).toBeVisible();
    await expect(page.getByTestId("emprunt-reglage")).not.toBeChecked();
    await expect(page.getByTestId("emprunt-cible")).toHaveCount(0);
    // Une case dont l'état vient du serveur (relu après l'écriture) : on clique, puis on attend l'état — `check()` exige un changement immédiat.
    await page.getByTestId("emprunt-reglage").click();
    await expect(page.getByTestId("emprunt-reglage")).toBeChecked();
    await expect(page.getByTestId("emprunt-info")).toContainText("ouvert");
    await page.getByTestId("emprunt-cible").selectOption(cibleId);
    await page.getByTestId("emprunt-motif").fill("Reproduire le problème de saisie signalé");
    await page.getByTestId("emprunt-confirmer").click();
    // La session est celle de l'employé : le bandeau nomme les deux identités, l'échéance, et le menu est celui d'un employé.
    await expect(page.getByTestId("emprunt-bandeau")).toContainText("vous agissez comme lea-emprunt (employee) — jusqu'à");
    await expect(page.getByTestId("dashboard-page")).toBeVisible();
    await expect(page.getByTestId("nav-admin")).toHaveCount(0);
    // Les secrets de la personne restent hors de portée : le serveur refuse, nommément.
    const refus = await page.evaluate(async () => {
      const csrf = document.cookie.split("; ").find((c) => c.startsWith("csrf="))?.slice(5) ?? "";
      const r = await fetch("/api/auth/change-password", { method: "POST", headers: { "content-type": "application/json", "X-CSRF-Token": csrf }, body: JSON.stringify({ currentPassword: "x", newPassword: "Nouveau!Passe2026" }) });
      return { status: r.status, corps: await r.json() };
    });
    expect(refus.status).toBe(403);
    expect(refus.corps.errors[0]).toContain("emprunt : interdit");
    // Terminer : la session de l'administration revient, le journal consigne l'emprunt terminé.
    await page.getByTestId("emprunt-terminer").click();
    await expect(page.getByTestId("emprunt-bandeau")).toHaveCount(0);
    await expect(page.getByTestId("dashboard-page")).toBeVisible();
    await ouvrirNav(page, "nav-admin");
    await page.getByTestId("admin-tab-comptes").click();
    await expect(page.getByTestId("emprunt-journal")).toContainText("lea-emprunt");
    await expect(page.getByTestId("emprunt-journal").locator("tbody tr").first()).toContainText("Terminé");
    await expect(page.getByTestId("emprunt-journal")).toContainText("Reproduire le problème de saisie signalé");
    // Le journal d'audit porte l'acteur réel.
    await page.getByTestId("admin-tab-audit").click();
    await expect(page.getByTestId("audit-table")).toContainText("auth.emprunt.fin");
  });
});
