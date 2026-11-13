<!-- gitnexus:start -->
# GitNexus — Code Intelligence

This project is indexed by GitNexus as **vina-geps** (36726 symbols, 89492 relationships, 1589 execution flows).

> Index stale? Run `node .gitnexus/run.cjs analyze --index-only` from the project root — it auto-selects an available runner. No `.gitnexus/run.cjs` yet? Bootstrap with `npx`, `bunx`, or `pnpm dlx` — e.g. `bunx gitnexus@latest analyze` (npm 11 npx crash; #1939).

## Always Do

- **MUST run impact before editing.** Use `impact({target: "symbolName", direction: "upstream"})` or `node .gitnexus/run.cjs impact "symbolName" --direction upstream --repo .`; report callers, processes, and risk. Never substitute grep for graph analysis.
- **MUST analyze graph changes before committing.** Use `detect_changes({scope: "all"})` (MCP) or `node .gitnexus/run.cjs detect-changes --scope all --repo .` (CLI fallback). `partial: true` or `truncated: true` is not a clean check — a zero means unseen, not unaffected; re-run it. For regression review: `detect_changes({scope: "compare", base_ref: "main"})` or `node .gitnexus/run.cjs detect-changes --scope compare --base-ref "main" --repo .`.
- MUST warn on HIGH/CRITICAL `risk` pre-edit; never use `riskSharedAxes` to waive a HIGH/CRITICAL `risk` warning. Compare File/symbol: MCP File omits axes; Graph-RAG expands File.
- **MUST treat `risk: UNKNOWN` as unresolved, not as low.** An empty caller set is not evidence the symbol is unused — it can also mean the callers are not resolvable by the index (plain-object property access, dynamic dispatch, cross-language calls). `impact` pairs `UNKNOWN` with a `riskNote` saying so. Confirm with a text search before treating the symbol as safe to change or delete; do not proceed on the strength of a zero.
- **MUST use `query({search_query: "concept"})` for concepts/flows, `context({name: "symbolName"})` for a named symbol, or `impact` for blast radius, on read-only callers, dependencies, imports, or execution flow.** Graph first; text search only for empty/`UNKNOWN`/literals.
- For security review, `explain({target: "fileOrSymbol"})` lists taint findings (source→sink flows; needs `analyze --pdg`).

## Never Do

- NEVER edit a function, class, or method before MCP/CLI impact analysis.
- NEVER ignore HIGH or CRITICAL risk warnings from impact analysis, and never read `UNKNOWN` as an all-clear — it means the walk could not answer, which is the one verdict that requires confirming by other means.
- NEVER rename symbols with find-and-replace — use `rename` which understands the call graph.
- NEVER commit before MCP/CLI graph change analysis.

## Resources

| Resource | Use for |
| --- | --- |
| `gitnexus://repo/vina-geps/context` | Codebase overview, check index freshness |
| `gitnexus://repo/vina-geps/clusters` | All functional areas |
| `gitnexus://repo/vina-geps/processes` | All execution flows |
| `gitnexus://repo/vina-geps/process/{name}` | Step-by-step execution trace |

## CLI

| Task | Read this skill file |
| --- | --- |
| Understand architecture / "How does X work?" | `.claude/skills/gitnexus/gitnexus-exploring/SKILL.md` |
| Blast radius / "What breaks if I change X?" | `.claude/skills/gitnexus/gitnexus-impact-analysis/SKILL.md` |
| Trace bugs / "Why is X failing?" | `.claude/skills/gitnexus/gitnexus-debugging/SKILL.md` |
| Rename / extract / split / refactor | `.claude/skills/gitnexus/gitnexus-refactoring/SKILL.md` |
| Tools, resources, schema reference | `.claude/skills/gitnexus/gitnexus-guide/SKILL.md` |
| Index, status, clean, wiki CLI commands | `.claude/skills/gitnexus/gitnexus-cli/SKILL.md` |

<!-- gitnexus:end -->

## Accessibilité — SGQRI 008 3.0 et WCAG 2.2 niveau AA (obligatoire)

**Tout** le code front-end produit ici — balisage HTML, composants React, styles — doit respecter
le standard québécois **SGQRI 008 3.0** et les critères **WCAG 2.2 niveau AA**. Ce n'est pas une
préférence de style : c'est l'**exigence #6** de l'avis AIN20260525, vérifiable par un évaluateur
en quelques minutes au clavier. Un écran non conforme est un écran à refaire.

### Règles à appliquer en écrivant, sans attendre l'audit

| Sujet | Règle |
|---|---|
| Champs de formulaire | Un `<label>` lié à chaque `input`, `select`, `textarea`. Un `placeholder` **n'est pas** un libellé. |
| Boutons et liens | Un intitulé textuel accessible. Une icône seule exige un `aria-label`. Jamais « cliquez ici ». |
| Tableaux | `<caption>` (au besoin en `.sr-only`) et `scope="col"` / `scope="row"` sur chaque `<th>`. |
| Images | `alt` décrivant la fonction ; `alt=""` si purement décorative. Les émojis décoratifs : `aria-hidden="true"`. |
| Cibles de pointage | **≥ 24 × 24 px** (WCAG 2.5.8), cases à cocher et boutons radio compris. |
| Couleur | Jamais porteuse de sens à elle seule (1.4.1) : doubler d'un texte ou d'une icône. |
| Mises à jour asynchrones | `role="status"` (ou `aria-live="polite"`) pour un décompte ou une confirmation ; `role="alert"` pour une erreur. Ne pas voler le focus. |
| Glisser-déposer | Interdit comme **seul** moyen d'agir (2.5.7) : toujours une action explicite équivalente. |
| Focus | Visible, jamais masqué par un élément collant (2.4.11). Aucun `tabindex` positif. |
| Structure | Un seul `<h1>` par page, hiérarchie de titres sans saut, `lang` sur `<html>`, `id` uniques, lien d'évitement vers `<main>`. |
| Authentification | Aucun test cognitif sans solution de rechange (3.3.8) : la saisie du mot de passe doit accepter le collage. |

### Vérification — avant de déclarer un travail front-end terminé

```bash
node scripts/audit-accessibilite.js          # balisage : libellés, tableaux, tabulation…
(cd e2e && npm run test:ui -- audit-wcag22) # géométrie : cibles ≥ 24 px, recouvrement, débordement
```

Le scanner statique lit **tout** le dépôt, y compris les branches jamais rendues ; l'audit
géométrique mesure la page rendue à quatre largeurs (390, 768, 1280, 1920 px). Les deux sont
nécessaires : aucun ne voit ce que voit l'autre.

Détail des règles, exceptions admises et justifications : [docs/ACCESSIBILITE.md](docs/ACCESSIBILITE.md).

### Ce qui vaut aussi pour la mise en page

Aucun élément interactif ne doit être **recouvert** par un autre, aucun débordement horizontal
jusqu'à 320 px de large (1.4.10), et les corps de texte suivent l'échelle fluide de
`frontend/src/styles.css` — pas de taille en dur qui casserait le zoom à 200 %.

## Le produit — Urgences-santé, Java seul

Ce dépôt est **Urgences-santé** (AO 26-015-DO, Corporation d'urgences-santé), séparé de Nexu VINA
le 13 novembre 2026. Il n'y a **pas de backend Node** : tout le serveur est `backend-java/`
(Spring Boot 4, Java 25). L'histoire de la séparation, les décisions D1–D5, les mesures et les
dettes : [PLANIFICATION-SEPARATION.md](PLANIFICATION-SEPARATION.md) § 8.

| Ce qui | Où | Règle |
|---|---|---|
| Comportement serveur (route, règle, garde) | `backend-java/` | Normes Java ci-dessous ; un contexte = `domaine` / `application` / `infrastructure/{web,persistance,configuration}` |
| Schéma de la base | `backend-java/src/main/resources/db/changelog/` | Liquibase **crée** la base. Une évolution = un **nouveau** changeset (`0003-…xml`) inclus dans `db.changelog-master.xml` ; jamais la modification d'un changeset appliqué. Ajouter aussi la table à `SchemaEssai` (base d'essai) |
| Interface | `frontend/` (React, Vite) | Accessibilité ci-dessus ; un menu = une page ; `data-testid` gelés (`docs/refonte-visuelle/testids.txt`, `npm run testids` seulement pour un retrait voulu et documenté) |
| Port partagé entre deux contextes | `partage/…` | Jamais dans un contexte qui dépend déjà de l'autre : `ArchitectureTest` refuse les cycles |

### Vérifier — les portes, dans l'ordre

| Porte | Commande |
|---|---|
| Serveur : architecture, normes, essais, JaCoCo ≥ 96 % par classe | `cd backend-java && mvn -o verify` |
| Interface : types, essais | `cd frontend && npx tsc --noEmit && npx vitest run` |
| Accessibilité statique | `node scripts/audit-accessibilite.js` |
| Écrans (axe-core, géométrie à 4 largeurs) | `cd e2e && E2E_NO_BACKEND=1 npx playwright test --project=interface` |
| Parcours contre le Java (base vierge Liquibase) | `E2E_API_PORT=4031 E2E_WEB_PORT=5331 backend-java/scripts/campagne-e2e.sh` |
| Performance | [perf/jmeter/README.md](perf/jmeter/README.md) — le balayage calcule sa durée ; lire la **couverture utile** avant une latence |

Lire le résultat d'une porte en entier : filtrer la sortie de `mvn` sur une seule forme d'erreur a
déjà caché une erreur de compilation (`BUILD FAILURE` / `[ERROR] /chemin`).

Normes Java : [docs/NORMES-JAVA.md](docs/NORMES-JAVA.md) (voir CLAUDE.md).
