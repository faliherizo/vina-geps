/**
 * Interface — le menu à **deux niveaux** : une notion, ses vues.
 *
 * Chaque vue se range sous sa notion, et un onglet se rejoint depuis le menu comme une page, avec
 * une URL qui le dit (`?onglet=`). Ce test vérifie l'articulation menu → page → onglet, qui
 * n'appartient à aucune page en propre.
 */
import { test, expect, DEFAULT_MODULES } from "./fixtures/app";
import { ouvrirNav } from "../e2e/helpers/nav";

const MODULES = { "GET /api/saas/my-modules": { modules: DEFAULT_MODULES, planCode: "enterprise" } };

test.describe("Menu — une notion, une entrée", () => {
  test("un onglet se rejoint depuis sa notion, et l'URL le nomme ; l'entrée principale ramène la vue principale", async ({ page, mock }) => {
    await mock(MODULES);
    await page.setViewportSize({ width: 1280, height: 900 });
    await page.goto("/");
    await ouvrirNav(page, "nav-access-qui");
    await expect.poll(() => new URL(page.url()).search).toBe("?onglet=qui");
    await expect(page.getByTestId("access-tab-qui")).toHaveAttribute("aria-selected", "true");
    await expect(page.getByTestId("nav-access-qui")).toHaveAttribute("aria-current", "page");
    await expect(page.getByTestId("nav-access")).not.toHaveAttribute("aria-current", "page");
    await ouvrirNav(page, "nav-access");
    await expect(page.getByTestId("access-tab-objets")).toHaveAttribute("aria-selected", "true");
    await expect(page.getByTestId("nav-access")).toHaveAttribute("aria-current", "page");
  });

  test("une notion ne s'affiche que si l'une de ses vues est ouverte à ce compte", async ({ page, mock }) => {
    await mock({ "GET /api/saas/my-modules": { modules: DEFAULT_MODULES.filter((m) => m !== "effectifs_communication"), planCode: "enterprise" } });
    await page.setViewportSize({ width: 1280, height: 900 });
    await page.goto("/");
    await expect(page.getByTestId("nav-concept-usquarts")).toHaveCount(1);
    await expect(page.getByTestId("nav-concept-uscommunication")).toHaveCount(0);
    await expect(page.getByTestId("nav-usmessages")).toHaveCount(0);
  });

  test("rubriques à titre fixe, une notion dépliée à la fois — et la page courante déplie la sienne", async ({ page, mock }) => {
    await mock(MODULES);
    await page.setViewportSize({ width: 1280, height: 900 });
    await page.goto("/");
    const notionsOuvertes = () => page.locator("details.nav-concept[open]").evaluateAll((els) => els.map((e) => e.getAttribute("data-testid")));

    // Planche s01 : la rubrique est un titre, jamais un accordéon ; le tableau de bord est la
    // première entrée du menu principal, et il n'existe plus de section qui ne contienne que lui.
    await expect(page.locator("details.nav-group")).toHaveCount(0);
    await expect(page.getByTestId("nav-group-urgencessante")).toContainText("Menu principal");
    await expect(page.getByTestId("nav-group-urgencessante").locator("button").first()).toHaveAttribute("data-testid", "nav-dashboard");
    await expect(page.getByTestId("nav-dashboard")).toHaveAttribute("aria-current", "page");
    await expect(page.getByTestId("fil-ariane")).toHaveCount(0);
    await expect.poll(notionsOuvertes).toEqual([]);

    await page.locator('[data-testid="nav-concept-usquarts"] > summary').click();
    await expect(page.getByTestId("nav-uscomblement")).toBeVisible();
    await page.locator('[data-testid="nav-concept-usadministration"] > summary').click();
    await expect.poll(notionsOuvertes).toEqual(["nav-concept-usadministration"]);
    await expect(page.getByTestId("nav-uscomblement")).toBeHidden();

    // Recliquer la notion la replie, au clavier comme à la souris.
    await page.locator('[data-testid="nav-concept-usadministration"] > summary').focus();
    await page.keyboard.press("Enter");
    await expect.poll(notionsOuvertes).toEqual([]);

    // Arriver par l'URL sur un onglet déplie sa notion ; le fil part du tableau de bord.
    await page.goto("/access?onglet=octrois");
    await expect.poll(notionsOuvertes).toEqual(["nav-concept-access"]);
    await expect(page.getByTestId("nav-access-octrois")).toHaveAttribute("aria-current", "page");
    await expect(page.getByTestId("fil-pilotage")).toHaveText("Tableau de bord");
    await page.getByTestId("fil-pilotage").click();
    await expect(page.getByTestId("dashboard-page")).toBeVisible();
  });
});
