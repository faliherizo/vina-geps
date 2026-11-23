/**
 * **La vitrine de VINA GePS** (`frontend/public/vitrine/index.html`), servie à la racine du site.
 *
 * Une page publique est la première que voit l'évaluateur : elle est tenue aux mêmes exigences que
 * l'application (SGQRI 008 3.0, WCAG 2.2 AA), à quatre largeurs, sans débordement.
 */
import AxeBuilder from "@axe-core/playwright";
import { expect, test, type Page } from "@playwright/test";

const LARGEURS = [390, 768, 1280, 1920];

const ORGANISMES = [
  { nom: "Clincom", slug: "clincom", adresse: "https://clincom.geps.vinaitsolution.com/admin/login" },
  { nom: "Urgences-santé", slug: "urgence-sante", adresse: "https://urgence-sante.geps.vinaitsolution.com/admin/login" },
];

/** La liste que le serveur rend (`GET /api/auth/organismes`) : la page n'a aucun organisme écrit en dur. */
async function servirOrganismes(page: Page, organismes: typeof ORGANISMES) {
  await page.route("**/api/auth/organismes", (r) => r.fulfill({ contentType: "application/json", body: JSON.stringify(organismes) }));
}

/** axe-core sans violation, aucun débordement, commandes ≥ 44 px, ni superposées ni à moins de 8 px. */
async function auditer(page: Page, largeur: number) {
  const debord = await page.evaluate(() => document.documentElement.scrollWidth - document.documentElement.clientWidth);
  expect(debord, `${largeur} px : débordement horizontal`).toBeLessThanOrEqual(1);
  const boites = (await Promise.all(["acceder-select", "acceder-aller", "acceder-exploitation"]
    .map(async (id) => ({ id, b: await page.getByTestId(id).boundingBox() })))).filter((x) => x.b);
  for (const { id, b } of boites) expect(b!.height, `${id} : hauteur`).toBeGreaterThanOrEqual(44);
  for (let i = 0; i < boites.length; i++) for (let j = i + 1; j < boites.length; j++) {
    const [a, c] = [boites[i].b!, boites[j].b!];
    const ecart = Math.max(c.x - (a.x + a.width), a.x - (c.x + c.width), c.y - (a.y + a.height), a.y - (c.y + c.height));
    expect(ecart, `${boites[i].id} / ${boites[j].id}`).toBeGreaterThanOrEqual(8);
  }
  const r = await new AxeBuilder({ page }).withTags(["wcag2a", "wcag2aa", "wcag21a", "wcag21aa", "wcag22aa"]).analyze();
  expect(r.violations.map((v) => `${v.id} : ${v.nodes.map((n) => n.target.join(" ")).slice(0, 3).join(", ")}`)).toEqual([]);
}

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
    test(`${largeur} px : le choix de l'organisme — liste de l'API, axe-core dans chaque état, cibles ≥ 44 px, rien de collé`, async ({ page }) => {
      await page.setViewportSize({ width: largeur, height: 900 });
      await servirOrganismes(page, ORGANISMES);
      await page.goto("/vitrine/acceder.html");
      await expect(page.locator("h1")).toHaveCount(1);
      await expect(page.getByRole("heading", { level: 1 })).toHaveText("Accéder à l'application");
      const liste = page.getByLabel("Organisme");
      await expect(liste).toBeEnabled();
      // L'invite, puis chaque organisme que l'API rend — rien d'écrit en dur dans la page.
      await expect(liste.locator("option")).toHaveText(["— Choisir votre organisme —", "Clincom", "Urgences-santé"]);
      await auditer(page, largeur);
      await liste.selectOption({ label: "Clincom" });
      await expect(page.getByTestId("acceder-adresse")).toHaveText("clincom.geps.vinaitsolution.com");
      await page.getByTestId("acceder-aller").focus();
      await auditer(page, largeur);
      // La connexion de l'hôte commun n'ouvre que les comptes globaux : elle reste atteignable, nommée comme telle.
      await expect(page.getByTestId("acceder-exploitation")).toHaveAttribute("href", "/admin/login");
    });
  }

  test("choisir un organisme mène à la connexion de son hôte", async ({ page }) => {
    await servirOrganismes(page, ORGANISMES);
    await page.route("https://clincom.geps.vinaitsolution.com/**", (r) => r.fulfill({ contentType: "text/html", body: "<!doctype html><title>clincom</title>" }));
    await page.goto("/vitrine/acceder.html");
    await page.getByLabel("Organisme").selectOption({ label: "Clincom" });
    await page.getByTestId("acceder-aller").click();
    await expect(page).toHaveURL("https://clincom.geps.vinaitsolution.com/admin/login");
  });

  test("sans choix : un message dit quoi faire et le focus revient sur la liste", async ({ page }) => {
    await servirOrganismes(page, ORGANISMES);
    await page.goto("/vitrine/acceder.html");
    await expect(page.getByLabel("Organisme")).toBeEnabled();
    await page.getByTestId("acceder-aller").click();
    await expect(page.getByRole("alert")).toContainText("Choisissez un organisme dans la liste");
    await expect(page.getByLabel("Organisme")).toBeFocused();
    await expect(page).toHaveURL(/\/vitrine\/acceder\.html$/);
    await auditer(page, 1280);
  });

  test("aucun organisme ouvert, ou la liste indisponible : l'écran le dit, sans rien proposer", async ({ page }) => {
    await servirOrganismes(page, []);
    await page.goto("/vitrine/acceder.html");
    await expect(page.getByTestId("acceder-etat")).toHaveText("Aucun organisme n'est ouvert pour le moment.");
    await expect(page.getByLabel("Organisme")).toBeDisabled();
    await auditer(page, 390);
    await page.unrouteAll();
    await page.route("**/api/auth/organismes", (r) => r.fulfill({ status: 404, contentType: "application/json", body: '{"errors":["aucun hôte par organisme"]}' }));
    await page.reload();
    await expect(page.getByTestId("acceder-etat")).toContainText("La liste des organismes n'est pas disponible");
    await expect(page.getByLabel("Organisme")).toBeDisabled();
    await auditer(page, 390);
  });

  test("le choix de l'organisme se parcourt au clavier : évitement, liste, bouton, exploitation", async ({ page }) => {
    await servirOrganismes(page, ORGANISMES);
    await page.goto("/vitrine/acceder.html");
    await expect(page.getByLabel("Organisme")).toBeEnabled();
    await page.keyboard.press("Tab");
    await expect(page.getByTestId("acceder-skip-link")).toBeFocused();
    await page.keyboard.press("Enter");
    await expect(page.locator("#contenu")).toBeFocused();
    for (const id of ["acceder-select", "acceder-aller", "acceder-exploitation"]) {
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
