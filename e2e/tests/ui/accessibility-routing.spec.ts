/**
 * E2E — Accessibilité (WCAG 2.1 AA / SGQRI 008) + routage interne SPA.
 *  - après une navigation dans le menu, le focus entre dans le contenu principal (<main>) ;
 *  - l'URL reflète la page courante (routage interne) au lieu de rester sur /admin/login ;
 *  - un lien d'évitement mène au contenu principal.
 */
import { test, expect } from "./fixtures/app";
import { ouvrirNav } from "../e2e/helpers/nav";

test.describe("Accessibilité & routage SPA", () => {
  test("la navigation déplace le focus dans <main> et met à jour l'URL", async ({ page, mock }) => {
    await mock();
    await page.setViewportSize({ width: 1366, height: 900 });
    await page.goto("/");

    // Le contenu principal est un landmark focusable.
    const main = page.getByTestId ? page.locator("#main-content") : page.locator("#main-content");
    await expect(main).toHaveAttribute("id", "main-content");

    // Navigue vers les approbations via le menu.
    await ouvrirNav(page, "nav-approvals");

    // 1) Le focus est déplacé dans le contenu principal (accessibilité clavier / lecteur d'écran).
    await expect.poll(async () => page.evaluate(() => document.activeElement?.id)).toBe("main-content");

    // 2) L'URL interne reflète la page (plus d'URL statique de type /admin/login).
    await expect.poll(async () => new URL(page.url()).pathname).toContain("/approvals");
    expect(new URL(page.url()).pathname).not.toContain("/login");

    // Navigue vers une autre page → l'URL suit.
    await ouvrirNav(page, "nav-soutien");
    await expect.poll(async () => new URL(page.url()).pathname).toContain("/soutien");
  });

  test("le lien d'évitement cible le contenu principal", async ({ page, mock }) => {
    await mock();
    await page.goto("/");
    const skip = page.locator("a.skip-link");
    await expect(skip).toHaveAttribute("href", "#main-content");
  });
});
