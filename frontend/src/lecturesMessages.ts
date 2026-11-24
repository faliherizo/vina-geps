/**
 * **Des messages viennent d'être lus.** La messagerie le signale, la barre du haut relit son compte et sa liste : sans
 * ce signal, l'icône annonçait des messages déjà lus jusqu'au prochain changement d'écran.
 */
const LECTURES = "vina:messages-lus";

export function signalerLectures(): void { window.dispatchEvent(new Event(LECTURES)); }

/** Écoute les lectures ; rend de quoi se désabonner. */
export function ecouterLectures(relire: () => void): () => void {
  window.addEventListener(LECTURES, relire);
  return () => window.removeEventListener(LECTURES, relire);
}
