/**
 * E2E — **Gestion des accès aux modules par établissement** (espace superadmin).
 *
 * Scénario : un tenant « professional » n'a PAS le module `complaints` par défaut. Le superadmin
 * l'active depuis l'onglet « 🧩 Modules & accès », ce qui débloque la page Plaintes côté tenant.
 */
import { test, expect, type APIRequestContext } from "@playwright/test";
import { superadminContext, GLOBAL_ACCOUNT } from "./helpers/auth";
import { createTenant, uniq } from "./helpers/organisme";
import { ouvrirNav } from "./helpers/nav";

const API = `http://localhost:${process.env.E2E_API_PORT ?? "4000"}`;

// Superadmin réel via l'IHM (sans storageState admin).
test.use({ storageState: { cookies: [], origins: [] } });

/** Les modules d'un organisme, lus comme le superadmin les lit : `quota.modules` de sa fiche. */
async function modulesDe(ctx: APIRequestContext, tenantId: string): Promise<string[]> {
  const fiche = await ctx.get(`${API}/api/saas/tenants/${tenantId}`).then((r) => r.json());
  return fiche?.quota?.modules ?? [];
}

test("le superadmin active/désactive un module pour un établissement", async ({ page }) => {
  const sa = await superadminContext();
  let tenantId = "";
  try {
    tenantId = await createTenant(sa, uniq("ModAccess"), "professional");
    // État initial : l'option « infrastructure dédiée » n'est pas comprise dans le forfait professional.
    const before = await modulesDe(sa, tenantId);
    expect(before).not.toContain("dedicated");
  } finally {
    await sa.dispose();
  }

  await page.goto("/");
  await page.getByTestId("login-user").fill("superadmin");
  await page.getByTestId("login-pass").fill("superadmin");
  await page.getByTestId("login-tenant").selectOption(GLOBAL_ACCOUNT);
  await page.getByTestId("login-submit").click();

  await ouvrirNav(page, "nav-group-admin").catch(() => {});
  await ouvrirNav(page, "nav-superadmin");
  await expect(page.getByTestId("superadmin-page")).toBeVisible();

  // Onglet Modules & accès.
  await page.getByTestId("sa-tab-modules").click();
  await expect(page.getByTestId("tenant-modules-manager")).toBeVisible();

  // Sélectionne l'établissement de test et active l'option.
  await page.getByTestId("modmgr-tenant").selectOption(tenantId);
  const toggle = page.getByTestId("modmgr-toggle-dedicated");
  await expect(toggle).not.toBeChecked();
  await toggle.check();
  await expect(page.getByTestId("sa-success")).toBeVisible();
  await expect(toggle).toBeChecked();

  // Vérifie côté API que le module est bien activé pour ce tenant.
  const sa2 = await superadminContext();
  expect(await modulesDe(sa2, tenantId)).toContain("dedicated");
  await sa2.dispose();

  // Désactivation : décoche → API repasse à false.
  await toggle.uncheck();
  await expect(toggle).not.toBeChecked();
  const sa3 = await superadminContext();
  await expect.poll(() => modulesDe(sa3, tenantId)).not.toContain("dedicated");
  await sa3.dispose();
});
