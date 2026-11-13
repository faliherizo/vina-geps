/**
 * **Gestion des effectifs — lot R2 (moteur de règles), de bout en bout.** AO 26-015-DO.
 *
 * Une règle se **construit à l'écran**, par listes et boutons (jamais par glisser), se publie, puis
 * s'éprouve dans le simulateur : la décision s'affiche avec le message de l'organisme, où le fait
 * a remplacé son jeton (E-GN-12, E-GN-15, E-GN-16). La décision apparaît ensuite dans l'onglet
 * Décisions (E-GN-9). Chaque état visité passe axe-core.
 */
import { test, expect } from "@playwright/test";
import { superadminContext } from "./helpers/auth";
import { createTenant, selectTenant, setTenantModules, uniq } from "./helpers/organisme";
import { ouvrirNav } from "./helpers/nav";
import { expectNoA11yViolations } from "./helpers/a11y";

test.use({ storageState: { cookies: [], origins: [] } });

test("construire une règle de disponibilité, la simuler, lire sa décision", async ({ page }) => {
  const tenantId = await createTenant(page.request, uniq("EffRegles"));
  const sa = await superadminContext();
  try { await setTenantModules(sa, tenantId, ["effectifs_regles"]); } finally { await sa.dispose(); }

  await selectTenant(page, tenantId);
  await ouvrirNav(page, "nav-usregles");
  await expect(page.getByTestId("rgl-ecran")).toBeVisible();
  await page.getByTestId("rgl-contexte").selectOption("DISPONIBILITE");
  await expect(page.getByTestId("rgl-vide")).toBeVisible();

  // ── Construire : « si quarts par semaine < 3, alors bloquer » ─────────────────────────────
  await page.getByTestId("rgl-nouvelle").click();
  await page.getByTestId("rgl-form-cle").fill("minimum-quarts");
  await page.getByTestId("rgl-ajouter-cond-r").click();
  await page.getByTestId("rgl-cond-r-0-fait").selectOption("quartsParSemaine");
  await page.getByTestId("rgl-cond-r-0-op").selectOption("<");
  await page.getByTestId("rgl-cond-r-0-valeur").fill("3");
  await page.getByTestId("rgl-action-BLOQUER").check();
  await page.getByTestId("rgl-form-message").fill("Au moins 3 quarts par semaine ({quartsParSemaine} déclarés)");
  await expectNoA11yViolations(page);
  await page.getByTestId("rgl-form-publier").click();
  await expect(page.getByTestId("rgl-succes")).toContainText("minimum-quarts");
  await expect(page.getByTestId("rgl-ligne-minimum-quarts")).toContainText(/Bloquer/i);

  // ── Simuler un dossier non conforme : le motif rend la valeur du fait ─────────────────────
  await page.getByTestId("rgl-vue-simulateur").click();
  await page.getByTestId("rgl-sim-quartsParSemaine").fill("2");
  await page.getByTestId("rgl-sim-evaluer").click();
  await expect(page.getByTestId("rgl-sim-verdict")).toContainText(/Bloquer/i);
  await expect(page.getByTestId("rgl-sim-motif-0")).toContainText("Au moins 3 quarts par semaine (2 déclarés)");
  await expectNoA11yViolations(page);

  // Un dossier conforme est accepté sans motif.
  await page.getByTestId("rgl-sim-quartsParSemaine").fill("4");
  await page.getByTestId("rgl-sim-evaluer").click();
  await expect(page.getByTestId("rgl-sim-verdict")).toContainText("Aucune règle ne s'y oppose");

  // ── La décision est tracée ─────────────────────────────────────────────────────────────
  await page.getByTestId("rgl-vue-decisions").click();
  await expect(page.getByTestId("rgl-decisions")).toContainText("simulation");
  await expectNoA11yViolations(page);
});
