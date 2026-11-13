/**
 * E2E réel (IHM) — **Sous-menus visibles par établissement**, espace superadmin.
 *
 * Le seul contrôle qui compte : une entrée décochée par le superadmin doit **disparaître du menu
 * de l'établissement**, et son URL directe ne doit plus l'ouvrir. Un réglage qui ne ferme que le
 * menu serait une politesse ; on éprouve donc les deux.
 *
 * ⚠️ Ce n'est pas un contrôle d'accès — les routes d'API restent ouvertes — et l'écran le dit.
 * L'essai vérifie aussi que cet avertissement est bien là : c'est ce qui empêche de s'y fier.
 */
import { test, expect } from "@playwright/test";
import { GLOBAL_ACCOUNT, superadminContext } from "./helpers/auth";
import { createTenant, uniq, TENANT_ADMIN_USER, TENANT_ADMIN_PASS } from "./helpers/organisme";
import { ouvrirNav } from "./helpers/nav";
import { expectNoA11yViolations } from "./helpers/a11y";

test.use({ storageState: { cookies: [], origins: [] } });

/** « Organigramme » : une page entière, sous la notion Personnel — visible par défaut partout. */
const VUE = "usjournal";

test("Superadmin : décocher un sous-menu le retire du menu de l'établissement, et ferme son URL", async ({ page }) => {
  const tenantId = await createTenant(page.request, uniq("SousMenus"));

  // ── 1. L'entrée est bien là AVANT le réglage ───────────────────────────────────────────────
  // Sans cette moitié, l'essai passerait aussi si l'entrée n'avait jamais existé.
  await page.goto("/");
  await page.getByTestId("login-tenant").selectOption(tenantId);
  await page.getByTestId("login-user").fill(TENANT_ADMIN_USER);
  await page.getByTestId("login-pass").fill(TENANT_ADMIN_PASS);
  await page.getByTestId("login-submit").click();
  await expect(page.getByTestId("logout")).toBeVisible({ timeout: 30_000 });
  await ouvrirNav(page, `nav-${VUE}`);
  await expect(page.getByTestId(`nav-${VUE}`)).toHaveCount(1);
  await page.getByTestId("logout").click();
  await expect(page.getByTestId("login-form")).toBeVisible({ timeout: 10_000 });

  // ── 2. Le superadmin décoche l'entrée ──────────────────────────────────────────────────────
  await page.getByTestId("login-tenant").selectOption(GLOBAL_ACCOUNT);
  await page.getByTestId("login-user").fill("superadmin");
  await page.getByTestId("login-pass").fill("superadmin");
  await page.getByTestId("login-submit").click();
  await ouvrirNav(page, "nav-superadmin");
  await page.getByTestId("sa-tab-modules").click();
  await expect(page.getByTestId("sous-menus-manager")).toBeVisible();

  // L'écran dit ce qu'il fait — et ce qu'il ne fait pas.
  await expect(page.getByTestId("sous-menus-manager")).toContainText(/pas une permission/i);
  await expectNoA11yViolations(page, '[data-testid="sous-menus-manager"]');

  await page.getByTestId("sousmenus-tenant").selectOption(tenantId);
  // Les sept sections sont **repliées** par défaut : soixante-neuf cases d'un bloc ne se lisent
  // pas. On fait donc ce que fait un utilisateur — ouvrir celle qu'on cherche.
  await page.getByTestId("sousmenus-section-urgencessante").locator("summary").click();
  const case_ = page.getByTestId(`sousmenus-vue-${VUE}`);
  await expect(case_).toBeChecked();                 // visible par défaut : liste de masquage
  await case_.uncheck();
  await page.getByTestId("sousmenus-save").click();
  await expect(page.getByTestId("sa-success")).toContainText(/masqué/i);

  // Persistance : après rechargement, la case reste décochée.
  await page.reload();
  await page.getByTestId("sa-tab-modules").click();
  await page.getByTestId("sousmenus-tenant").selectOption(tenantId);
  await page.getByTestId("sousmenus-section-urgencessante").locator("summary").click();
  await expect(page.getByTestId(`sousmenus-vue-${VUE}`)).not.toBeChecked();

  // ── 3. Côté établissement : l'entrée a disparu, et l'URL ne l'ouvre plus ────────────────────
  await page.getByTestId("logout").click();
  await expect(page.getByTestId("login-form")).toBeVisible({ timeout: 10_000 });
  await page.getByTestId("login-tenant").selectOption(tenantId);
  await page.getByTestId("login-user").fill(TENANT_ADMIN_USER);
  await page.getByTestId("login-pass").fill(TENANT_ADMIN_PASS);
  await page.getByTestId("login-submit").click();
  await expect(page.getByTestId("logout")).toBeVisible({ timeout: 30_000 });

  await expect(page.getByTestId(`nav-${VUE}`)).toHaveCount(0);

  // L'URL directe : le signet d'hier ne doit plus mener à l'écran retiré.
  await page.goto(`/admin/${VUE}`);
  await expect(page.getByTestId("logout")).toBeVisible({ timeout: 30_000 });
  await expect(page.getByTestId(`nav-${VUE}`)).toHaveCount(0);
  // On est ramené au tableau de bord, pas laissé sur un écran qu'on vient de retirer.
  await expect(page).toHaveURL(/\/admin\/dashboard/);
});

test("Un administrateur d'établissement ne règle pas les sous-menus", async ({ page }) => {
  const tenantId = await createTenant(page.request, uniq("SousMenusRefus"));
  const sa = await superadminContext();
  try {
    // La route est fermée, pas seulement l'écran : masquer un menu n'est pas un contrôle d'accès,
    // mais **régler** les menus des autres en serait un, et celui-là doit tenir.
    await sa.dispose();
  } finally { /* rien */ }

  await page.goto("/");
  await page.getByTestId("login-tenant").selectOption(tenantId);
  await page.getByTestId("login-user").fill(TENANT_ADMIN_USER);
  await page.getByTestId("login-pass").fill(TENANT_ADMIN_PASS);
  await page.getByTestId("login-submit").click();
  await expect(page.getByTestId("logout")).toBeVisible({ timeout: 30_000 });

  expect((await page.request.get("/api/saas/vues")).status()).toBe(403);
  expect((await page.request.put(`/api/saas/tenants/${tenantId}/vues`, { data: { masquees: [] } })).status()).toBe(403);
});
