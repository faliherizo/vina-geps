/**
 * Choisir une personne dans un champ à autocomplétion.
 *
 * Le champ n'accepte plus qu'on tape un identifiant : il faut saisir **au moins trois lettres**,
 * attendre la liste, puis retenir une proposition. Ce détour est le sujet même du composant —
 * l'ancien champ acceptait n'importe quelle chaîne, et les essais eux-mêmes y écrivaient des
 * identifiants (« emp-1 ») qui ne désignaient personne. Ils passaient au vert sur des données
 * rattachées à des employés inexistants.
 */
import { expect, type Page } from "@playwright/test";

export async function choisirPersonne(page: Page, testId: string, recherche: string) {
  const entree = page.getByTestId(testId);
  await entree.fill(recherche);
  // La liste arrive après un délai de 200 ms et un aller-retour : on attend la liste elle-même,
  // pas une durée arbitraire.
  await expect(page.getByTestId(`${testId}-liste`)).toBeVisible({ timeout: 10_000 });
  await page.getByTestId(`${testId}-liste`).locator('[role="option"]').first().click();
  await expect(page.getByTestId(`${testId}-retenus`)).toBeVisible();
}

/** Choisir plusieurs personnes de suite dans un champ `multiple`. */
export async function choisirPersonnes(page: Page, testId: string, recherches: string[]) {
  for (const r of recherches) await choisirPersonne(page, testId, r);
}
