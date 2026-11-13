/**
 * **La barre d'en-tête et le menu collants ne se recouvrent jamais.**
 *
 * Deux défauts superposés, relevés le 8 novembre 2026 par l'audit géométrique sous la forme
 * « `nav-uspostes` est recouvert par `topbar` » — à 1280 px seulement, et seulement sur les écrans
 * dont la page défile assez pour qu'une entrée tombe dans la bande fautive :
 *
 * 1. **le menu ne collait pas.** `overflow-x: hidden` sur `html, body` faisait de `body` un
 *    conteneur de défilement ; la colonne `sticky` collait donc à un conteneur immobile, défilait
 *    avec la page et passait sous la barre (haut du menu à −332 px après 400 px de défilement) ;
 * 2. **la barre mentait sur sa hauteur.** `--topbar-h` valait 52 px en dur pour une barre de 68 :
 *    une fois le collage réparé, le menu se serait logé 16 px sous elle.
 *
 * Un défaut qui n'apparaît qu'à une largeur et dans un état de défilement revient sans bruit :
 * d'où cet essai, qui mesure les deux, à chaque largeur de poste.
 */
import { expect, test } from "./fixtures/app";

const MODULES = { "GET /api/saas/my-modules": { modules: ["effectifs", "effectifs_planification", "effectifs_temps", "effectifs_echanges", "effectifs_communication", "horaires"], planCode: "enterprise" } };

// Sous 769 px, le menu est un tiroir fixe ouvert par « ☰ Menu » : il ne colle pas, il recouvre
// par conception, et c'est l'audit géométrique qui le mesure dans cet état.
for (const largeur of [1024, 1280, 1440, 1920]) {
  test(`${largeur} px : après défilement, le menu reste collé sous la barre, sans la chevaucher`, async ({ page, mock }) => {
    await mock(MODULES);
    await page.setViewportSize({ width: largeur, height: 720 });
    await page.goto("/");
    await page.locator(".topbar").waitFor();
    // La page doit pouvoir défiler, sinon l'essai mesurerait une page immobile et passerait toujours.
    // Le contenu s'allonge **dans la page**, pas seulement le `body` : un élément collant ne colle que
    // dans les limites de son parent, et l'accueil est court — un `body` allongé laisserait le parent
    // de la barre latérale plus court que l'écran, et le menu partirait avec lui par construction.
    await page.evaluate(() => { (document.querySelector("main") as HTMLElement).style.minHeight = "3000px"; window.scrollTo(0, 600); });
    await expect.poll(() => page.evaluate(() => window.scrollY), { message: "la page doit défiler pour que la mesure ait un sens" })
      .toBeGreaterThan(0);

    const mesurer = () => page.evaluate(() => {
      const barre = document.querySelector(".topbar")!.getBoundingClientRect();
      const menu = document.querySelector(".sidebar")!.getBoundingClientRect();
      return {
        barreBas: barre.bottom,
        menuHaut: menu.top,
        variable: parseFloat(getComputedStyle(document.documentElement).getPropertyValue("--topbar-h")),
      };
    });
    /*
      **Attendre que la géométrie se pose**, sans rien relâcher de ce qu'on exige.

      La barre peut changer de hauteur pendant le chargement (73 px, puis 68), et `ResizeObserver`
      livre sa mesure de façon asynchrone : lue juste après le défilement, la variable avait encore
      l'ancienne valeur (1920 px, 8 novembre 2026). L'attente borne ce délai ; l'exigence reste
      au pixel près, et un menu qui ne colle pas échoue toujours — il ne se « pose » jamais.
    */
    await expect.poll(async () => { const x = await mesurer(); return Math.abs(x.menuHaut - x.barreBas) <= 1 && Math.abs(x.variable - x.barreBas) <= 1; },
      { timeout: 3_000, message: "le menu doit finir collé sous la barre, et la variable dire sa hauteur" }).toBe(true).catch(() => undefined);
    const m = await mesurer();
    // Le menu **colle** : il ne part pas avec la page.
    expect(m.menuHaut, "le menu a défilé avec la page : il ne colle pas").toBeGreaterThanOrEqual(0);
    // Et il colle **sous** la barre, pas dessous ni avec un vide : au pixel près, arrondi compris.
    expect(Math.abs(m.menuHaut - m.barreBas), `menu à ${m.menuHaut} px, barre jusqu'à ${m.barreBas} px`).toBeLessThanOrEqual(1);
    // La variable que le style emploie dit la hauteur réelle — c'est elle qui avait menti.
    expect(Math.abs(m.variable - m.barreBas), `--topbar-h à ${m.variable} px pour une barre de ${m.barreBas} px`).toBeLessThanOrEqual(1);
  });
}
