/**
 * **Ce que le serveur a rendu n'est pas forcément ce que l'écran attend.**
 *
 * Un écran qui fait `reponse.cadences.join(", ")` sur une réponse qui s'est trouvée être une
 * liste vide lève une `TypeError` — et une `TypeError` pendant le rendu **détruit l'arbre React** :
 * l'utilisateur n'obtient pas un tableau vide, il obtient une **page blanche**, et tout ce qui
 * suivait disparaît avec elle.
 *
 * Ce n'est pas une hypothèse. L'audit géométrique du 29 septembre 2026 a déclaré quatre onglets de
 * la masse salariale « jamais rendus » : `GET /api/masse-salariale/referentiels` avait répondu `[]`
 * là où l'écran attendait `{ cadences, statuts, referencesAvancement }`, et l'écran entier était
 * tombé. Le cas venait d'un doublon d'essai, mais une version d'API décalée, un mandataire qui
 * intercale une réponse d'erreur ou une panne partielle produisent exactement la même chose en
 * production.
 *
 * D'où ces deux gardes, à poser **au moment d'enregistrer** ce qu'on a lu :
 *
 * - `liste()` ne rend une liste que si c'en est une ;
 * - `objet()` ne rend un objet que si c'en est un — et **une liste n'en est pas un**, précisément
 *   parce que c'est la confusion qui fait tomber l'écran.
 *
 * Ce qui ne passe pas la garde vaut **absent**, et l'écran affiche son état vide : une information
 * manquante se voit et se recharge, une page blanche ne se comprend pas.
 */

/** La valeur si c'est bien une liste, sinon une liste vide. */
export function liste<T>(valeur: unknown): T[] {
  return Array.isArray(valeur) ? (valeur as T[]) : [];
}

/**
 * La valeur si c'est bien un objet — ni liste, ni nul —, sinon `null`.
 *
 * `null` plutôt qu'un objet vide : un écran qui teste `if (x)` doit pouvoir distinguer « pas encore
 * chargé » de « chargé, mais vide », et un objet vide effacerait la distinction.
 */
export function objet<T extends object>(valeur: unknown): T | null {
  return valeur !== null && typeof valeur === "object" && !Array.isArray(valeur) ? (valeur as T) : null;
}
