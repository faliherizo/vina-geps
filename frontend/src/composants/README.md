# Composants de la refonte visuelle

Bibliothèque commune des écrans (plan : [docs/refonte-visuelle/REFONTE-VISUELLE.md](../../../docs/refonte-visuelle/REFONTE-VISUELLE.md), partie B.3).
Un écran migré **n'ajoute pas de logique** : il confie sa forme à ces composants et garde ses `data-testid`.

| Composant | Rôle | Garanties d'accessibilité (WCAG 2.2 AA / SGQRI 008 3.0) |
|---|---|---|
| `EnTeteEcran` | titre `h1` (ou `h2` en `niveau={2}`), description, actions de création | un seul `h1` par page ; titre focalisable (`tabIndex=-1`) |
| `Onglets` | barre `role="tablist"` ; `prefixeIds` quand l'écran rend ses propres panneaux | `aria-selected`, tabindex tournant, flèches (délégué global), `aria-controls` seulement vers un panneau présent |
| `BarreFiltres` | filtres étiquetés + décompte `role="status"` + « Réinitialiser » | 4.1.3 |
| `Tableau` | `caption`, `scope="col"`, tri `aria-sort`, pagination `TbodyPagine`, état vide | 1.3.1 ; la ligne cliquable n'est jamais le seul geste (2.5.7) |
| `Tiroir` | fiche latérale = `Modal` en variante `tiroir` | dialogue titré, focus piégé et rendu, Échap |
| `MenuActions` | bouton `⋯` → `role="menu"` | flèches, Échap, clic hors menu ; action irréversible annoncée |
| `Message` | info · succès · avertissement · erreur | icône **et** texte (1.4.1) ; `alert` pour l'erreur, `status` sinon ; le texte reste celui de l'appelant |
| `EtatVide` | phrase + action qui crée le premier élément | — |
| `Tuile` | valeur, libellé, tendance dite en mots | bouton ≥ 44 px si cliquable |
| `Etapes` | assistant : `aria-current="step"`, « terminée » | rendu en pastilles (`.etapes` de styles.css) |
| `Badge` / `BadgeStatut` | ton + forme (● ○ ◐) | jamais la couleur seule |
| `SommaireSections` | ancres d'une page de réglages (G5) | focus posé sur le titre visé (2.4.3) |
| `FilAriane` | Section › Notion › Vue (rendu par `App.tsx`) | `nav` étiqueté, `aria-current="page"` |
| `Icone` | tracés Lucide (MIT) en SVG `currentColor` | toujours `aria-hidden` |
| `Calendrier` | grille mensuelle (G4) : barre de mois, en-tête des jours, cases conteneur ou bouton | `role="grid"` étiqueté, `columnheader`, `aria-selected` sur la case choisie |

Tests : `frontend/tests/composants.test.tsx` (Vitest + jsdom). Jetons et styles : `composants.css`
(contrastes calculés, `prefers-reduced-motion`). Codemods d'écran : `frontend/scripts/codemod-*.py`.
