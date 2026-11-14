/**
 * **Filmer un contexte ouvert à la main.**
 *
 * L'option `video` de Playwright ne vaut que pour la page fournie à l'essai ; un essai qui ouvre deux
 * navigateurs (Julie et Marc) les ouvre par `browser.newContext()`, et ceux-là ne sont pas filmés.
 * Quand on filme la vitrine (`E2E_VIDEO_LARGEUR`), chaque contexte reçoit son enregistrement, rangé
 * avec les sorties de l'essai — le montage les y trouve dans l'ordre.
 */
import { test, type BrowserContextOptions } from "@playwright/test";

export function optionsFilmees(options: BrowserContextOptions = {}): BrowserContextOptions {
  const largeur = Number(process.env.E2E_VIDEO_LARGEUR ?? 0);
  if (!largeur) return options;
  const taille = { width: largeur, height: Math.round(largeur * 9 / 16) };
  return { ...options, viewport: taille, locale: "fr-CA", timezoneId: "America/Toronto", recordVideo: { dir: test.info().outputDir, size: taille } };
}
