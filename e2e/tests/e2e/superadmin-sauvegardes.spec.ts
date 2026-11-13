/**
 * E2E — **Sauvegardes de l'instance** depuis l'écran superadmin (grille scolaire, exigence 47).
 *
 * Jusqu'ici `/api/exploitation/sauvegardes` n'avait pas d'écran : la copie ne se déclenchait
 * qu'en ligne de commande. Le scénario passe par l'onglet « Exploitation », déclenche une
 * complète, et vérifie que la copie apparaît dans la liste — donc qu'elle a réellement été
 * écrite et vérifiée (`PRAGMA integrity_check`) par le serveur, pas seulement annoncée.
 */
import { test, expect } from "@playwright/test";
import { GLOBAL_ACCOUNT } from "./helpers/auth";
import { ouvrirNav } from "./helpers/nav";

test.use({ storageState: { cookies: [], origins: [] } });

test("le superadmin déclenche une sauvegarde complète depuis l'écran et la voit listée", async ({ page }) => {
  await page.goto("/");
  await page.getByTestId("login-user").fill("superadmin");
  await page.getByTestId("login-pass").fill("superadmin");
  await page.getByTestId("login-tenant").selectOption(GLOBAL_ACCOUNT);
  await page.getByTestId("login-submit").click();
  await ouvrirNav(page, "nav-group-admin").catch(() => {});
  await ouvrirNav(page, "nav-superadmin");
  await page.getByTestId("sa-tab-exploitation").click();

  const politique = page.getByTestId("sa-sauvegardes-politique");
  await expect(politique).toContainText("toutes les 15 min, 96 copies conservées");
  await expect(politique).toContainText("toutes les 24 h, 30 copies conservées");

  await page.getByTestId("sa-sauvegarde-complete").click();
  const message = page.getByTestId("sa-sauvegardes-message");
  await expect(message).toContainText("Sauvegarde complète faite");
  const fichier = (await message.textContent())!.split(" : ")[1].trim();
  expect(fichier).toMatch(/-complete\.sqlite$/);
  const ligne = page.getByTestId(`sa-sauvegarde-${fichier}`);
  await expect(ligne).toContainText("Complète");
  await expect(ligne).toContainText(/Ko|Mo/);

  // Les cadences se (ré)amorcent d'un clic ; l'écran répète les intervalles que le serveur a retenus.
  await page.getByTestId("sa-sauvegardes-planifier").click();
  await expect(page.getByTestId("sa-sauvegardes-message")).toContainText("incrémentale toutes les 15 min, complète toutes les 24 h");
});

test("un administrateur d'établissement n'atteint pas les sauvegardes, même par l'API", async ({ page }) => {
  const r = await page.request.post(`http://localhost:${process.env.E2E_API_PORT ?? "4000"}/api/auth/login`, { data: { username: "admin", password: "admin" } });
  expect(r.ok()).toBeTruthy();
  const sauvegardes = await page.request.get(`http://localhost:${process.env.E2E_API_PORT ?? "4000"}/api/exploitation/sauvegardes`);
  expect(sauvegardes.status()).toBe(403);
});
