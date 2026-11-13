/**
 * **La matrice de navigateurs de l'annexe 7.00** — exigences 10, 51 et 52 (AO 2026-PLAT-GA).
 *
 * L'exigence 51 demande Edge, Firefox, Chrome et Safari ; les exigences 10 et 52 demandent que la
 * même application serve sur téléphone et tablette, sans rien installer. Ces trois cases étaient
 * cochées « OUI » alors que **rien n'était jamais exécuté ailleurs que sur Chromium** : le projet
 * `edge` lui-même est Chromium (`channel: msedge`). Deux des quatre moteurs du marché — Gecko
 * (Firefox) et WebKit (Safari, et tout navigateur sur iPhone et iPad) — n'avaient donc aucune
 * preuve, et la régie prévoit des essais (1.14.03). Une promesse que personne n'a essayée n'est
 * pas une conformité, c'est une supposition.
 *
 * Ce parcours est volontairement **court et critique** : se connecter par le formulaire, atteindre
 * le travail, ouvrir la déclaration d'absence, tabuler. Un moteur qui casse casse là — sur un
 * `fetch`, un `Intl`, un `<dialog>`, une date, un sélecteur CSS. Il ne remplace pas la campagne
 * complète, qui reste sur Chromium pour sa durée ; il donne à chaque moteur la preuve que sa case
 * exige.
 *
 * Exécution :
 *   npx playwright test --project=firefox --project=webkit --project=e2e navigateurs-conformite
 */
import { expect, test, type Page } from "@playwright/test";
import { superadminContext } from "./helpers/auth";
import { createTenant, setTenantModules, uniq, TENANT_ADMIN_USER, TENANT_ADMIN_PASS } from "./helpers/organisme";
import { ouvrirNav } from "./helpers/nav";

// Aucune session héritée : le formulaire de connexion fait partie de la surface à éprouver, et
// `.auth/admin.json` nous ferait arriver déjà connectés — sur le tableau de bord, pas sur l'écran
// dont l'exigence parle.
test.use({ storageState: { cookies: [], origins: [] } });

/** Les largeurs de l'exigence 52 (téléphone, tablette) et de l'exigence 10 (ordinateur). */
const FORMATS = [
  { nom: "téléphone", largeur: 390, hauteur: 844 },
  { nom: "tablette", largeur: 768, hauteur: 1024 },
  { nom: "ordinateur", largeur: 1280, hauteur: 800 },
] as const;

let tenantId = "";

test.beforeAll(async () => {
  const sa = await superadminContext();
  try {
    tenantId = await createTenant(sa, uniq("Navigateurs — conformité"));
    await setTenantModules(sa, tenantId, ["approvals", "notifications", "effectifs", "effectifs_regles", "effectifs_planification", "effectifs_absences"]);
  } finally {
    await sa.dispose();
  }
});

async function seConnecter(page: Page): Promise<void> {
  await page.goto("/");
  await page.getByTestId("login-tenant").selectOption(tenantId);
  await page.getByTestId("login-user").fill(TENANT_ADMIN_USER);
  await page.getByTestId("login-pass").fill(TENANT_ADMIN_PASS);
  await page.getByTestId("login-submit").click();
  await expect(page.getByTestId("logout")).toBeVisible({ timeout: 30_000 });
}

/**
 * Sur petite largeur, la navigation est **derrière le bouton ☰** — c'est le comportement attendu
 * (exigence 52 : une interface adaptative, pas une interface amputée), pas un défaut. Un essai qui
 * l'ignore conclut « entrée injoignable » et accuse le produit de ce que l'essai n'a pas fait.
 */
async function ouvrirMenuSiReplie(page: Page): Promise<void> {
  const bouton = page.getByTestId("menu-toggle");
  if (await bouton.isVisible().catch(() => false)) await bouton.click();
}

/**
 * Le débordement horizontal, mesuré et non jugé à l'œil (WCAG 1.4.10).
 *
 * On tolère un pixel : les moteurs n'arrondissent pas les sous-pixels de la même façon, et un
 * seuil strict ferait échouer WebKit sur un écart que personne ne voit. Au-delà, la page défile
 * latéralement — sur un téléphone, c'est du contenu hors d'atteinte.
 */
async function debordement(page: Page): Promise<number> {
  return page.evaluate(() => Math.max(0, document.documentElement.scrollWidth - document.documentElement.clientWidth));
}

test.describe("Matrice de navigateurs — exigences 10, 51, 52", () => {
  test("le moteur se nomme, et le parcours critique s'exécute de bout en bout", async ({ page, browserName }) => {
    test.setTimeout(180_000);
    await seConnecter(page);

    // La preuve porte le moteur et sa version : « ça marche » sans dire où ne prouve rien.
    const agent = await page.evaluate(() => navigator.userAgent);
    test.info().annotations.push({ type: "moteur", description: `${browserName} — ${agent}` });

    // Atteindre le travail : la navigation est un accordéon, elle s'ouvre partout ou nulle part.
    await ouvrirMenuSiReplie(page);
    await ouvrirNav(page, "nav-mesabsences");
    await expect(page.getByTestId("mab-declarer")).toBeVisible({ timeout: 30_000 });

    // Déclarer une absence : le tiroir et ses champs — c'est là que les moteurs divergent
    // (champs de date, dialogues natifs, formats de nombre).
    await page.getByTestId("mab-declarer").click();
    await expect(page.getByTestId("abs-formulaire-champs")).toBeVisible({ timeout: 20_000 });

    // Une tabulation doit atteindre un élément interactif : sans clavier, pas de conformité (2.1.1).
    await page.keyboard.press("Tab");
    const focus = await page.evaluate(() => document.activeElement?.tagName ?? "");
    expect(focus, "la tabulation doit atteindre un élément interactif").not.toBe("BODY");
  });

  for (const format of FORMATS) {
    test(`aucun débordement horizontal en ${format.nom} (${format.largeur} px)`, async ({ page, browserName }) => {
      test.setTimeout(150_000);
      await page.setViewportSize({ width: format.largeur, height: format.hauteur });
      await seConnecter(page);

      const trop = await debordement(page);
      expect(trop, `le tableau de bord déborde de ${trop} px en ${format.nom} sous ${browserName}`).toBeLessThanOrEqual(1);

      await ouvrirMenuSiReplie(page);
      await ouvrirNav(page, "nav-mesabsences");
      await expect(page.getByTestId("mab-declarer")).toBeVisible({ timeout: 30_000 });
      const tropAbsences = await debordement(page);
      expect(tropAbsences, `Absences déborde de ${tropAbsences} px en ${format.nom} sous ${browserName}`).toBeLessThanOrEqual(1);
    });
  }
});
