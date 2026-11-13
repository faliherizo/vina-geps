/**
 * E2E — Sélecteur de pays hôte (page de login, pré-auth) + accessibilité des tableaux (WCAG/SGQRI).
 * Données pilotées par la fixture JSON `mocks/countries.json`.
 */
import { test, expect, request as pwRequest } from "@playwright/test";
import fx from "../../mocks/countries.json";
import { ouvrirNav } from "./helpers/nav";

const API = `http://localhost:${process.env.E2E_API_PORT ?? "4000"}`;

test.describe("Pays hôte & accessibilité", () => {
  // Page de login = pré-auth : contexte sans cookie (sinon storageState admin nous connecte).
  test.describe("sélecteur de pays (login)", () => {
    test.use({ storageState: { cookies: [], origins: [] } });

    test("le catalogue public des pays est exposé et calcule les taxes par pays", async () => {
      const ctx = await pwRequest.newContext();
      const cat = await ctx.get(`${API}/api/countries`).then((r) => r.json());
      const codes = cat.countries.map((c: any) => c.code);
      for (const c of fx.expectedCodes) expect(codes).toContain(c);

      // Calcul de taxe par pays (extension par pack) — vérifie CA (TPS+TVQ) et FR (TVA).
      for (const code of fx.expectedCodes) {
        const st = await ctx.get(`${API}/api/countries/${code}/sales-tax?amount=100`).then((r) => r.json());
        expect(st.totalTax).toBeCloseTo((fx.byCode as any)[code].salesTaxOn100, 2);
      }
      await ctx.dispose();
    });

    test("la page de login ne propose PAS de choix de pays (déterminé par l'établissement)", async ({ page }) => {
      await page.goto("/admin/login");
      await expect(page.getByTestId("login-form")).toBeVisible();
      // Le pays/la devise ne sont plus un choix utilisateur : ils viennent du tenant.
      await expect(page.getByTestId("login-country")).toHaveCount(0);
    });
  });

  // Accessibilité des tableaux : les en-têtes portent scope="col" (lecteur d'écran lie donnée↔colonne).
  test("les tableaux de données sont accessibles (th[scope=col]) après connexion", async ({ page }) => {
    await page.goto("/");
    await ouvrirNav(page, "nav-admin");
    // Attendre un tableau puis vérifier que tous ses en-têtes ont scope="col".
    const firstTable = page.locator("table").first();
    await expect(firstTable).toBeVisible();
    const headers = firstTable.locator("thead th");
    const count = await headers.count();
    expect(count).toBeGreaterThan(0);
    const scoped = await firstTable.locator('thead th[scope="col"]').count();
    expect(scoped).toBe(count);
  });
});
