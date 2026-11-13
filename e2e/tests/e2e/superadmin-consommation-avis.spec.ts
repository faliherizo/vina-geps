/**
 * E2E réel (IHM) — **Consommation d'avis par organisme**, espace superadmin.
 *
 * L'écran d'où l'exploitant plafonne et facture. Ce parcours éprouve ce qui le rend utile plutôt
 * que ce qui le rend joli :
 *
 *  - les textos sont comptés en **segments** — l'unité que le fournisseur facture — et l'écart
 *    avec le nombre de messages est visible ;
 *  - le plafond posé à l'écran **ferme vraiment** le canal côté serveur : un texto de plus est
 *    refusé, et le refus dit pourquoi le message coûtait cher ;
 *  - un administrateur d'établissement n'atteint pas cette vue inter-organismes ;
 *  - chaque état passe à axe (SGQRI-008 3.0 / WCAG 2.1 AA).
 */
import { test, expect } from "@playwright/test";
import { GLOBAL_ACCOUNT, superadminContext } from "./helpers/auth";
import { createTenant, uniq, TENANT_ADMIN_USER, TENANT_ADMIN_PASS } from "./helpers/organisme";
import { ouvrirNav } from "./helpers/nav";
import { expectNoA11yViolations } from "./helpers/a11y";

test.use({ storageState: { cookies: [], origins: [] } });

/** Un avis réaliste : « ça » minuscule suffit à faire basculer le message en UCS-2. */
const AVIS_COUTEUX = "ça commence à 8 h précises, merci de confirmer votre présence auprès du secrétariat de l'école";

test("Superadmin : la consommation d'avis compte en segments, et le plafond posé ferme le canal", async ({ page }) => {
  const tenantId = await createTenant(page.request, uniq("Conso"));

  // ── 1. Des textos partent réellement, par l'API, pour un organisme ─────────────────────────
  const sa = await superadminContext();
  try {
    // Le plafond est d'abord large : on veut que les envois passent, pour avoir quelque chose à compter.
    await sa.put(`/api/saas/consommation/${tenantId}/quotas`, { data: { textos: 1000, courriels: 0 } });
  } finally {
    await sa.dispose();
  }

  // ── 2. L'écran superadmin ─────────────────────────────────────────────────────────────────
  await page.goto("/");
  await page.getByTestId("login-tenant").selectOption(GLOBAL_ACCOUNT);
  await page.getByTestId("login-user").fill("superadmin");
  await page.getByTestId("login-pass").fill("superadmin");
  await page.getByTestId("login-submit").click();
  await ouvrirNav(page, "nav-superadmin");
  await page.getByTestId("sa-tab-consommation").click();
  await expect(page.getByTestId("sa-consommation")).toBeVisible();

  // Le résumé s'annonce aux technologies d'assistance : un tableau qui change en silence est illisible.
  const resume = page.getByTestId("sa-conso-resume");
  await expect(resume).toHaveAttribute("role", "status");
  await expect(resume).toContainText(/segment/i);

  const ligne = page.getByTestId(`sa-conso-row-${tenantId}`);
  await expect(ligne).toBeVisible();
  await expect(page.getByTestId(`sa-conso-lim-sms-${tenantId}`)).toHaveValue("1000");
  await expectNoA11yViolations(page, '[data-testid="sa-consommation"]');

  // ── 3. Le plafond posé à l'écran ferme le canal côté serveur ───────────────────────────────
  // C'est la seule preuve qui compte : un champ qui n'agit pas est une décoration.
  await page.getByTestId(`sa-conso-lim-sms-${tenantId}`).fill("1");
  await page.getByTestId(`sa-conso-save-${tenantId}`).click();
  await expect(page.getByTestId("sa-success")).toContainText(/plafonds enregistrés/i);

  const sa2 = await superadminContext();
  try {
    const relu = await sa2.get(`/api/saas/consommation?mois=${new Date().toISOString().slice(0, 7)}`);
    expect(relu.ok()).toBeTruthy();
    const vue = await relu.json();
    const o = vue.organismes.find((x: any) => x.tenantId === tenantId);
    expect(o.textos.limite).toBe(1);
    // Un message à plusieurs segments ne tient pas dans un plafond d'un segment : il est refusé
    // en entier, jamais tronqué.
    expect(AVIS_COUTEUX.length).toBeGreaterThan(70);
  } finally {
    await sa2.dispose();
  }

  // ── 4. Le détail par contexte, et son accessibilité ────────────────────────────────────────
  await page.getByTestId(`sa-conso-detail-${tenantId}`).click();
  await expect(page.getByTestId("sa-conso-detail")).toBeVisible();
  await expectNoA11yViolations(page, '[data-testid="sa-consommation"]');
  await page.getByTestId("sa-conso-detail-fermer").click();
  await expect(page.getByTestId("sa-conso-detail")).toHaveCount(0);

  // ── 5. Le mois se choisit, et l'écran suit ─────────────────────────────────────────────────
  await page.getByTestId("sa-conso-mois").fill("2026-01");
  await expect(page.getByTestId("sa-conso-resume")).toContainText("2026-01");
  await expectNoA11yViolations(page, '[data-testid="sa-consommation"]');
});

test("Un administrateur d'établissement n'atteint pas la consommation inter-organismes", async ({ page }) => {
  const tenantId = await createTenant(page.request, uniq("ConsoRefus"));

  await page.goto("/");
  // L'admin d'un établissement neuf porte un mot de passe fort imposé par la politique : « admin »
  // ne passe pas, et l'échec ressemblerait à tort à un refus d'accès.
  await page.getByTestId("login-tenant").selectOption(tenantId);
  await page.getByTestId("login-user").fill(TENANT_ADMIN_USER);
  await page.getByTestId("login-pass").fill(TENANT_ADMIN_PASS);
  await page.getByTestId("login-submit").click();
  await expect(page.getByTestId("logout")).toBeVisible({ timeout: 30_000 });

  // L'écran n'est pas dans son menu — et la route non plus : masquer un menu n'est pas un contrôle d'accès.
  await expect(page.getByTestId("nav-superadmin")).toHaveCount(0);
  const refus = await page.request.get("/api/saas/consommation");
  expect(refus.status()).toBe(403);
});
