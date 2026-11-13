/**
 * **L'extraction des identifiants d'essai, à un seul endroit.**
 *
 * Deux outils la demandent : `lister-testids.cjs`, qui gèle la liste, et
 * `tests/garde-fous-refonte.test.ts`, qui refuse qu'un identifiant gelé disparaisse. Ils en
 * portaient chacun une copie — « la même expression que… », disait le commentaire — et le jour où
 * l'une a appris à lire les identifiants **construits**, l'autre a vu 1 274 identifiants
 * « disparus » du code. Le garde-fou accusait le code d'un défaut qui était dans le garde-fou.
 *
 * La règle vit donc ici, et nulle part ailleurs.
 */

/** Identifiants littéraux : `data-testid="x"`, `testId="x"`, `testId: "x"`. */
const LITTERAL = /(?:data-testid|testId)(?:="|: ")([^"]+)"/g;

/**
 * Identifiants **construits** : `` data-testid={`x-${id}`} `` ou `` testId: (l) => `x-${l.id}` ``.
 *
 * Seul le préfixe stable est retenu — c'est lui qu'un essai peut chercher, et c'est lui qui dit
 * qu'un geste par ligne existe à l'écran.
 */
const CONSTRUIT = /(?:data-testid=\{|testId:\s*\([^)]*\)\s*=>\s*)`([^`$]+)\$\{/g;

/** Les identifiants d'un fichier source. Les préfixes trop courts ne désignent rien. */
function identifiantsDe(source) {
  const trouves = new Set();
  for (const m of source.matchAll(LITTERAL)) trouves.add(m[1]);
  for (const m of source.matchAll(CONSTRUIT)) if (m[1].length > 2) trouves.add(m[1]);
  return trouves;
}

module.exports = { identifiantsDe };
