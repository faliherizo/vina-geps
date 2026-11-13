/**
 * E2E — Création d'établissement : sélecteur **Pays & monnaie** (Superadmin).
 * Le superadmin choisit le pays hôte (et donc la monnaie) du nouvel établissement.
 */
import { test, expect } from "@playwright/test";
import { GLOBAL_ACCOUNT } from "./helpers/auth";
import fx from "../../mocks/countries.json";
import { ouvrirNav } from "./helpers/nav";

// Superadmin réel via l'IHM (sans storageState admin).
test.use({ storageState: { cookies: [], origins: [] } });

test("le formulaire d'établissement propose un sélecteur pays + monnaie", async ({ page }) => {
  await page.goto("/");
  await page.getByTestId("login-user").fill("superadmin");
  await page.getByTestId("login-pass").fill("superadmin");
  await page.getByTestId("login-tenant").selectOption(GLOBAL_ACCOUNT);
  await page.getByTestId("login-submit").click();

  await ouvrirNav(page, "nav-group-admin").catch(() => {});
  await ouvrirNav(page, "nav-superadmin");
  await expect(page.getByTestId("superadmin-page")).toBeVisible();

  // Le champ pays est un <select> alimenté par le catalogue des pays.
  const country = page.getByTestId("sa-country");
  await expect(country).toBeVisible();
  for (const code of fx.expectedCodes) {
    await expect(country.locator(`option[value="${code}"]`)).toHaveCount(1);
  }

  // Sélectionner la France affiche la monnaie EUR.
  await country.selectOption("FR");
  await expect(page.getByTestId("sa-country-info")).toContainText("EUR");
  // Madagascar → Ariary (MGA).
  await country.selectOption("MG");
  await expect(page.getByTestId("sa-country-info")).toContainText("MGA");
  // USA → USD.
  await country.selectOption("US");
  await expect(page.getByTestId("sa-country-info")).toContainText("USD");
});
