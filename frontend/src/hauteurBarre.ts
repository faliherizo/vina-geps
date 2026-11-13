/**
 * **La hauteur de la barre d'en-tête, mesurée — jamais supposée.**
 *
 * `--topbar-h` valait 52 px, en dur. La barre en mesure 68 dès que le menu du profil y figure
 * (46 px de haut, plus le rembourrage et le liseré). La colonne de menu, collée à
 * `top: var(--topbar-h)`, se serait donc logée 16 px **sous** la barre — à toutes les largeurs de
 * poste. Relevé par l'audit géométrique le 8 novembre 2026 sur « Ma feuille de temps »
 * (`nav-uspostes`) et, à l'identique, sur « Mes échanges » (`nav-usquotas`).
 *
 * Une constante qui décrit la hauteur d'un contenu est fausse au premier bouton ajouté. La
 * variable est donc posée **depuis la mesure**, et suivie à chaque changement de taille : un
 * libellé traduit plus long, une icône de plus, un passage à la ligne.
 *
 * **Une référence par rappel, et non `useRef` + effet.** La barre n'existe pas au premier rendu
 * de la coquille : un effet indexé sur un objet `ref` s'exécute une fois, trouve `null`, et ne se
 * relance jamais — c'est ce qu'a montré la première mesure (la variable restait à 52 px). Un
 * rappel est appelé avec l'élément au moment exact où il apparaît, et avec `null` quand il part.
 *
 * Le plancher reste en CSS (`--topbar-min`) : la barre se dessine à la bonne taille avant la
 * mesure, et une mesure qui servirait aussi de minimum ne pourrait plus jamais rapetisser.
 */
import { useCallback, useRef } from "react";

export const VARIABLE_HAUTEUR = "--topbar-h";

/** La hauteur à publier : arrondie **au-dessus**, parce qu'un demi-pixel manquant laisse un liseré passer dessous. */
export function hauteurPubliee(hauteur: number): string {
  return `${Math.ceil(hauteur)}px`;
}

/** Rend la référence à poser sur la barre (`ref={…}`). */
export function useHauteurBarre(): (el: HTMLElement | null) => void {
  const arreter = useRef<() => void>(() => undefined);
  return useCallback((el: HTMLElement | null) => {
    arreter.current();
    arreter.current = () => undefined;
    if (!el) return;
    const racine = document.documentElement;
    const poser = () => racine.style.setProperty(VARIABLE_HAUTEUR, hauteurPubliee(el.getBoundingClientRect().height));
    poser();
    // Sans `ResizeObserver` (environnements d'essai anciens), la mesure initiale reste juste :
    // c'est le suivi qui manque, pas la valeur.
    const observateur = typeof ResizeObserver === "undefined" ? null : new ResizeObserver(poser);
    observateur?.observe(el);
    arreter.current = () => {
      observateur?.disconnect();
      racine.style.removeProperty(VARIABLE_HAUTEUR);
    };
  }, []);
}
