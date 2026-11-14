/**
 * **La vitrine de VINA GEPS** (`frontend/public/vitrine/index.html`), servie à la racine du site.
 *
 * Une page publique est la première que voit l'évaluateur : elle est tenue aux mêmes exigences que
 * l'application (SGQRI 008 3.0, WCAG 2.2 AA), à quatre largeurs, sans débordement.
 */
import AxeBuilder from "@axe-core/playwright";
import { expect, test } from "@playwright/test";

const LARGEURS = [390, 768, 1280, 1920];

test.describe("Vitrine VINA GEPS", () => {
  for (const largeur of LARGEURS) {
    test(`${largeur} px : axe-core sans violation, aucun débordement, la démonstration sous-titrée`, async ({ page }) => {
      await page.setViewportSize({ width: largeur, height: 900 });
      await page.goto("/vitrine/index.html");
      await expect(page.getByRole("heading", { level: 1 })).toContainText("VINA GEPS");
      await expect(page.locator("h1")).toHaveCount(1);
      await expect(page.locator("html")).toHaveAttribute("lang", "fr-CA");
      // La vidéo porte ses sous-titres et un texte de repli ; la démonstration est décrite en mots.
      await expect(page.getByTestId("vitrine-video-el").locator("track[kind=captions]")).toHaveCount(1);
      await expect(page.getByTestId("vitrine-transcript")).toBeVisible();
      await expect(page.getByTestId("vitrine-cta-app")).toHaveAttribute("href", "/admin/login");
      const debord = await page.evaluate(() => document.documentElement.scrollWidth - document.documentElement.clientWidth);
      expect(debord, "débordement horizontal").toBeLessThanOrEqual(1);
      const r = await new AxeBuilder({ page }).withTags(["wcag2a", "wcag2aa", "wcag21a", "wcag21aa", "wcag22aa"]).analyze();
      expect(r.violations.map((v) => `${v.id} : ${v.nodes.map((n) => n.target.join(" ")).slice(0, 3).join(", ")}`)).toEqual([]);
    });
  }

  test("le lien d'évitement mène au contenu, au clavier", async ({ page }) => {
    await page.goto("/vitrine/index.html");
    await page.keyboard.press("Tab");
    await expect(page.getByTestId("vitrine-skip-link")).toBeFocused();
    await page.keyboard.press("Enter");
    await expect(page.locator("#contenu")).toBeFocused();
  });
});
