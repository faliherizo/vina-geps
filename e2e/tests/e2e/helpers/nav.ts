/**
 * Ouvrir une entrée du menu latéral, quel que soit son niveau.
 *
 * Le menu est un accordéon : une section ouverte à la fois, une notion à la fois dans la section.
 * L'entrée visée peut donc être derrière deux intitulés repliés — la section (« Paie & RH »), puis
 * la notion (« Absences »). On déplie ce qui la cache, dans l'ordre, sans jamais refermer ce qui
 * est déjà ouvert : cliquer un intitulé déjà déplié le replierait, et l'entrée disparaîtrait.
 *
 * La navigation se re-rend à l'arrivée des modules de l'établissement : on réessaie le clic, pas
 * seulement l'attente.
 */
import type { Page } from "@playwright/test";

/**
 * **Affiché**, pas seulement dimensionné. Un lien d'un `<details>` replié garde dans Chrome la
 * largeur de sa dernière mise en page (le contenu replié n'est plus rendu, il n'est pas remis à
 * zéro) : se fier à la boîte ou à `isVisible()` fait cliquer un lien que personne ne voit.
 */
export async function estAffiche(page: Page, testId: string): Promise<boolean> {
  return page.getByTestId(testId).evaluate((e) => e.checkVisibility()).catch(() => false);
}

/**
 * Sur un écran étroit, le menu est un **tiroir** rangé hors de l'écran : un intitulé qu'on y
 * clique sans l'avoir sorti n'est jamais atteint (le clic expire), et l'entrée reste repliée.
 * L'état se lit sur la position du tiroir, pas sur sa classe, qui survit à la fermeture.
 */
export async function sortirLeTiroir(page: Page): Promise<void> {
  const bascule = page.getByTestId("menu-toggle");
  if (!(await bascule.isVisible().catch(() => false))) return;   // pas de tiroir à cette largeur
  const tiroir = await page.getByTestId("sidebar").boundingBox();
  if (tiroir && tiroir.x >= 0) return;
  // Un tiroir qui **s'ouvre** porte déjà `open` mais n'est pas encore en place : le voile couvre
  // alors le bouton ☰, et le recliquer attendrait jusqu'à la fin de l'essai. On laisse finir.
  const sortant = await page.getByTestId("sidebar").evaluate((s) => s.classList.contains("open")).catch(() => false);
  if (!sortant) await bascule.click({ timeout: 3000 }).catch(() => {});
  await page.waitForFunction(() => {
    const s = document.querySelector('[data-testid="sidebar"]');
    return !!s && s.getBoundingClientRect().x >= 0;
  }, null, { timeout: 2000 }).catch(() => {});
}

/** Déplie la section puis la notion qui cachent l'entrée — sans toucher à ce qui est déjà ouvert. */
export async function deplierJusqua(page: Page, testId: string): Promise<void> {
  for (const niveau of [".nav-group", ".nav-concept"]) {
    const conteneur = page.locator(`${niveau}:has([data-testid="${testId}"])`).first();
    // Une entrée hors de toute notion n'a pas de second niveau : ne pas attendre un élément absent.
    if ((await conteneur.count()) === 0) continue;
    const replie = await conteneur.evaluate((d) => !(d as HTMLDetailsElement).open).catch(() => false);
    if (!replie) continue;
    await sortirLeTiroir(page);
    await conteneur.locator("> summary").click({ timeout: 2000 }).catch(() => {});
  }
}

export async function ouvrirNav(page: Page, testId: string): Promise<void> {
  const lien = page.getByTestId(testId);
  for (let essai = 0; essai < 6; essai++) {
    if (!(await estAffiche(page, testId))) await deplierJusqua(page, testId);
    await sortirLeTiroir(page);
    try { await lien.click({ timeout: 5000 }); return; }
    catch { await page.waitForTimeout(500); }
  }
  throw new Error(`entrée de navigation « ${testId} » injoignable`);
}
