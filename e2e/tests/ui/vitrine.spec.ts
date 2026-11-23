/**
 * **La vitrine de VINA GePS** (`frontend/public/vitrine/index.html`), servie à la racine du site.
 *
 * Une page publique est la première que voit l'évaluateur : elle est tenue aux mêmes exigences que
 * l'application (SGQRI 008 3.0, WCAG 2.2 AA), à quatre largeurs, sans débordement.
 */
import AxeBuilder from "@axe-core/playwright";
import { expect, test } from "@playwright/test";

const LARGEURS = [390, 768, 1280, 1920];

test.describe("Vitrine VINA GePS", () => {
  for (const largeur of LARGEURS) {
    test(`${largeur} px : axe-core sans violation, aucun débordement, la démonstration sous-titrée`, async ({ page }) => {
      await page.setViewportSize({ width: largeur, height: 900 });
      await page.goto("/vitrine/index.html");
      await expect(page.getByRole("heading", { level: 1 })).toContainText("VINA GePS");
      await expect(page.locator("h1")).toHaveCount(1);
      await expect(page.locator("html")).toHaveAttribute("lang", "fr-CA");
      // La vidéo porte ses sous-titres et un texte de repli ; la démonstration est décrite en mots.
      await expect(page.getByTestId("vitrine-video-el").locator("track[kind=captions]")).toHaveCount(1);
      await expect(page.getByTestId("vitrine-transcript")).toBeVisible();
      // « Accéder à l'application » mène au choix de l'organisme : chaque organisme a son hôte (ADR 0033).
      await expect(page.getByTestId("vitrine-cta-app")).toHaveAttribute("href", "/acceder");
      await expect(page.getByTestId("vitrine-nav-app")).toHaveAttribute("href", "/acceder");
      const debord = await page.evaluate(() => document.documentElement.scrollWidth - document.documentElement.clientWidth);
      expect(debord, "débordement horizontal").toBeLessThanOrEqual(1);
      const r = await new AxeBuilder({ page }).withTags(["wcag2a", "wcag2aa", "wcag21a", "wcag21aa", "wcag22aa"]).analyze();
      expect(r.violations.map((v) => `${v.id} : ${v.nodes.map((n) => n.target.join(" ")).slice(0, 3).join(", ")}`)).toEqual([]);
    });
  }

  for (const largeur of LARGEURS) {
    test(`${largeur} px : le choix de l'organisme — axe-core sans violation, cibles ≥ 44 px, rien de collé ni de superposé`, async ({ page }) => {
      await page.setViewportSize({ width: largeur, height: 900 });
      await page.goto("/vitrine/acceder.html");
      await expect(page.locator("h1")).toHaveCount(1);
      await expect(page.getByRole("heading", { level: 1 })).toHaveText("Accéder à l'application");
      await expect(page.getByTestId("acceder-urgence-sante")).toHaveAttribute("href", "https://urgence-sante.geps.vinaitsolution.com/admin/login");
      await expect(page.getByTestId("acceder-clincom")).toHaveAttribute("href", "https://clincom.geps.vinaitsolution.com/admin/login");
      // La connexion de l'hôte commun n'ouvre que les comptes globaux : elle reste atteignable, mais nommée comme telle.
      await expect(page.getByTestId("acceder-exploitation")).toHaveAttribute("href", "/admin/login");
      const debord = await page.evaluate(() => document.documentElement.scrollWidth - document.documentElement.clientWidth);
      expect(debord, "débordement horizontal").toBeLessThanOrEqual(1);
      const commandes = ["acceder-urgence-sante", "acceder-clincom", "acceder-exploitation"];
      const boites = await Promise.all(commandes.map(async (id) => ({ id, b: (await page.getByTestId(id).boundingBox())! })));
      for (const { id, b } of boites) expect(b.height, `${id} : hauteur`).toBeGreaterThanOrEqual(44);
      // Deux commandes voisines : ni superposées, ni à moins de 8 px (CLAUDE.md, « Aucun bouton collé »).
      for (let i = 0; i < boites.length; i++) for (let j = i + 1; j < boites.length; j++) {
        const [a, c] = [boites[i].b, boites[j].b];
        const ecartX = Math.max(c.x - (a.x + a.width), a.x - (c.x + c.width));
        const ecartY = Math.max(c.y - (a.y + a.height), a.y - (c.y + c.height));
        expect(Math.max(ecartX, ecartY), `${boites[i].id} / ${boites[j].id}`).toBeGreaterThanOrEqual(8);
      }
      const r = await new AxeBuilder({ page }).withTags(["wcag2a", "wcag2aa", "wcag21a", "wcag21aa", "wcag22aa"]).analyze();
      expect(r.violations.map((v) => `${v.id} : ${v.nodes.map((n) => n.target.join(" ")).slice(0, 3).join(", ")}`)).toEqual([]);
    });
  }

  test("le choix de l'organisme se parcourt au clavier : évitement, puis chaque organisme, puis l'exploitation", async ({ page }) => {
    await page.goto("/vitrine/acceder.html");
    await page.keyboard.press("Tab");
    await expect(page.getByTestId("acceder-skip-link")).toBeFocused();
    await page.keyboard.press("Enter");
    await expect(page.locator("#contenu")).toBeFocused();
    for (const id of ["acceder-urgence-sante", "acceder-clincom", "acceder-exploitation"]) {
      await page.keyboard.press("Tab");
      await expect(page.getByTestId(id)).toBeFocused();
    }
  });

  test("le lien d'évitement mène au contenu, au clavier", async ({ page }) => {
    await page.goto("/vitrine/index.html");
    await page.keyboard.press("Tab");
    await expect(page.getByTestId("vitrine-skip-link")).toBeFocused();
    await page.keyboard.press("Enter");
    await expect(page.locator("#contenu")).toBeFocused();
  });
});
