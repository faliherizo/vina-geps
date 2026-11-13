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

  test("accordéon : une section à la fois, une notion à la fois — et la page courante déplie la sienne", async ({ page, mock }) => {
    await mock(MODULES);
    await page.setViewportSize({ width: 1280, height: 900 });
    await page.goto("/");
    const ouverts = () => page.locator("details.nav-group[open]").evaluateAll((els) => els.map((e) => e.getAttribute("data-testid")));
    const notionsOuvertes = () => page.locator("details.nav-concept[open]").evaluateAll((els) => els.map((e) => e.getAttribute("data-testid")));

    // À l'arrivée sur le tableau de bord, aucune section n'est dépliée : c'est l'écran d'arrivée.
    await expect.poll(ouverts).toEqual([]);

    await page.locator('[data-testid="nav-group-urgencessante"] > summary').click();
    await expect.poll(ouverts).toEqual(["nav-group-urgencessante"]);
    await page.locator('[data-testid="nav-concept-usquarts"] > summary').click();
    await expect(page.getByTestId("nav-uscomblement")).toBeVisible();
    await page.locator('[data-testid="nav-concept-usadministration"] > summary').click();
    await expect.poll(notionsOuvertes).toEqual(["nav-concept-usadministration"]);
    await expect(page.getByTestId("nav-uscomblement")).toBeHidden();

    // Déplier une autre section replie la première.
    await page.locator('[data-testid="nav-group-admin"] > summary').click();
    await expect.poll(ouverts).toEqual(["nav-group-admin"]);

    // Recliquer la section la replie, au clavier comme à la souris.
    await page.locator('[data-testid="nav-group-admin"] > summary').focus();
    await page.keyboard.press("Enter");
    await expect.poll(ouverts).toEqual([]);

    // Arriver par l'URL sur un onglet déplie sa section et sa notion — l'entrée courante est visible.
    await page.goto("/access?onglet=octrois");
    await expect.poll(ouverts).toEqual(["nav-group-admin"]);
    await expect.poll(notionsOuvertes).toEqual(["nav-concept-access"]);
    await expect(page.getByTestId("nav-access-octrois")).toHaveAttribute("aria-current", "page");
  });
});
