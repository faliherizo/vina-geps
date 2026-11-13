/**
 * Interface — la colonne de la barre latérale reste peinte sur toute la hauteur.
 *
 * `.sidebar` est en `position: sticky` avec `height: 100vh` : elle ne peint qu'une hauteur
 * d'écran. Sur une page plus longue, la colonne cessait d'être colorée sous cette limite et
 * une large bande claire apparaissait au bas de l'écran — la mise en page semblait cassée.
 *
 * Le fond est donc porté par le conteneur `.app`. Ce test vérifie qu'il y est bien, et qu'il
 * disparaît en affichage mobile où la barre latérale sort de l'écran.
 */
import { test, expect } from "./fixtures/app";

test.describe("Mise en page — colonne latérale", () => {
  test("la colonne est peinte par le conteneur sur grand écran", async ({ page, mock }) => {
    await mock();
    await page.setViewportSize({ width: 1440, height: 900 });
    await page.goto("/");
    await expect(page.getByTestId("sidebar")).toBeVisible();

    const fond = await page.locator(".app").evaluate((el) => getComputedStyle(el).backgroundImage);
    expect(fond).toContain("linear-gradient");
  });

  test("le document s'arrête au bas du contenu", async ({ page, mock }) => {
    // `#root` était figé à `height: 100%` — la hauteur de la fenêtre. Dès que le contenu la
    // dépassait, `.app` débordait de son conteneur : le document et son enfant annonçaient deux
    // hauteurs différentes, et la page continuait de défiler bien au-delà du contenu.
    await mock();
    await page.setViewportSize({ width: 1440, height: 700 });
    await page.goto("/");
    await expect(page.getByTestId("sidebar")).toBeVisible();

    const h = await page.evaluate(() => ({
      document: document.documentElement.scrollHeight,
      root: Math.round((document.getElementById("root") as HTMLElement).getBoundingClientRect().height),
      app: Math.round((document.querySelector(".app") as HTMLElement).getBoundingClientRect().height),
    }));
    // Aucun conteneur ne dépasse son parent : la chaîne document → #root → .app est cohérente.
    expect(h.root).toBe(h.app);
    expect(h.document).toBe(h.app);
  });

  test("aucune colonne peinte en affichage mobile", async ({ page, mock }) => {
    await mock();
    await page.setViewportSize({ width: 390, height: 844 });
    await page.goto("/");

    // La barre latérale est hors écran : peindre 240 px sombres serait une bande parasite.
    const fond = await page.locator(".app").evaluate((el) => getComputedStyle(el).backgroundImage);
    expect(fond).toBe("none");
  });
});
