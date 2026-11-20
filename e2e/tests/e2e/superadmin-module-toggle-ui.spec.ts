/**
 * E2E réel (IHM) — Gestion des accès aux modules **depuis l'espace superadmin** :
 * le superadmin décoche un module (case à cocher) pour un organisme, la modification est
 * persistée, et le menu correspondant disparaît côté administration de l'organisme.
 */
import { test, expect, type Page } from "@playwright/test";
import { GLOBAL_ACCOUNT } from "./helpers/auth";
import { createTenant, selectTenant, uniq } from "./helpers/organisme";
import { ouvrirNav } from "./helpers/nav";

test.use({ storageState: { cookies: [], origins: [] } });

/** Le superadmin ouvre « Modules et accès » sur l'organisme visé. */
async function ouvrirModules(page: Page, tenantId: string): Promise<void> {
  await page.goto("/");
  await page.getByTestId("login-user").fill("superadmin");
  await page.getByTestId("login-pass").fill("superadmin");
  await page.getByTestId("login-tenant").selectOption(GLOBAL_ACCOUNT);
  await page.getByTestId("login-submit").click();
  await ouvrirNav(page, "nav-superadmin");
  await page.getByTestId("sa-tab-modules").click();
  await expect(page.getByTestId("tenant-modules-manager")).toBeVisible();
  await page.getByTestId("modmgr-tenant").selectOption(tenantId);
}

test("Superadmin IHM : décocher la communication pour un organisme retire ses menus, et seulement eux", async ({ page }) => {
  const tenantId = await createTenant(page.request, uniq("SaMod")); // enterprise → tout le module
  await ouvrirModules(page, tenantId);
  const communication = page.getByTestId("modmgr-toggle-effectifs_communication");
  await expect(communication).toBeChecked();

  await communication.uncheck();
  await expect(page.getByTestId("sa-success")).toContainText(/désactivé/i);

  // Persistance serveur : rechargement → la case reste décochée.
  await page.reload();
  await page.getByTestId("sa-tab-modules").click();
  await page.getByTestId("modmgr-tenant").selectOption(tenantId);
  await expect(page.getByTestId("modmgr-toggle-effectifs_communication")).not.toBeChecked();

  // Côté organisme : la messagerie a disparu, la planification reste.
  await page.getByTestId("logout").click();
  await expect(page.getByTestId("login-form")).toBeVisible({ timeout: 10000 });
  await selectTenant(page, tenantId);
  await expect(page.getByTestId("nav-usmessages")).toHaveCount(0);
  await expect(page.getByTestId("nav-uscomblement")).toHaveCount(1);
});

/**
 * La cascade socle → modules, par le geste réel : l'écran ne retire que la case cliquée, le
 * serveur emporte les modules qui en dépendent, et l'écran le dit avant comme après.
 */
test("Superadmin IHM : décocher le socle des effectifs désactive tout le module du même clic", async ({ page }) => {
  const tenantId = await createTenant(page.request, uniq("SaCasc"));
  await ouvrirModules(page, tenantId);

  await expect(page.getByTestId("modmgr-famille-effectifs")).toBeVisible();
  await expect(page.getByTestId("modmgr-dependants-effectifs")).toContainText("désactivera");
  for (const code of ["effectifs", "effectifs_regles", "effectifs_planification", "effectifs_temps", "effectifs_communication"]) {
    await expect(page.getByTestId(`modmgr-toggle-${code}`)).toBeChecked();
  }

  await page.getByTestId("modmgr-toggle-effectifs").uncheck();
  await expect(page.getByTestId("sa-success")).toContainText(/désactivé/i);
  for (const code of ["effectifs", "effectifs_regles", "effectifs_planification", "effectifs_temps", "effectifs_communication"]) {
    await expect(page.getByTestId(`modmgr-toggle-${code}`)).not.toBeChecked();
  }

  await page.reload();
  await page.getByTestId("sa-tab-modules").click();
  await page.getByTestId("modmgr-tenant").selectOption(tenantId);
  await expect(page.getByTestId("modmgr-toggle-effectifs_planification")).not.toBeChecked();

  // Côté organisme, chaque vue du module a disparu. La rubrique « Menu principal » (`nav-group-urgencessante`)
  // demeure depuis le chrome VINA GePS (ADR 0073) : elle porte aussi le tableau de bord, qui n'est pas du module.
  await page.getByTestId("logout").click();
  await expect(page.getByTestId("login-form")).toBeVisible({ timeout: 10000 });
  await selectTenant(page, tenantId);
  await expect(page.getByTestId("nav-dashboard")).toHaveCount(1);
  for (const vue of ["nav-uscomblement", "nav-usmessages"]) {
    await expect(page.getByTestId(vue)).toHaveCount(0);
  }
});

test("Superadmin IHM : cocher un sous-module ramène ses prérequis, et l'écran le dit", async ({ page }) => {
  const tenantId = await createTenant(page.request, uniq("SaPrer"));
  await ouvrirModules(page, tenantId);

  // On part du module coupé, socle compris.
  await page.getByTestId("modmgr-toggle-effectifs").uncheck();
  await expect(page.getByTestId("modmgr-toggle-effectifs_agph")).not.toBeChecked();

  // Cocher un module seul : sa chaîne de prérequis revient d'elle-même, et le message l'annonce.
  await page.getByTestId("modmgr-toggle-effectifs_agph").check();
  await expect(page.getByTestId("sa-success")).toContainText(/activé d'office/);
  for (const code of ["effectifs", "effectifs_regles", "effectifs_planification", "effectifs_agph"]) {
    await expect(page.getByTestId(`modmgr-toggle-${code}`)).toBeChecked();
  }
  // Les autres modules ne sont pas revenus : seuls les prérequis le sont.
  await expect(page.getByTestId("modmgr-toggle-effectifs_temps")).not.toBeChecked();
});
