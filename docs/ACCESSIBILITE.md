# Accessibilité — SGQRI 008 3.0 / WCAG 2.1 AA

Démarche de conformité et outillage de vérification.

> **Exigences visées** : SGQRI 008 3.0, standard québécois d'accessibilité web, qui transpose
> **WCAG 2.1 niveau AA**. Exigence obligatoire de l'avis MIFI AIN20260525 (générales #1-16) et
> du devis UdeS R-0640-27 (**RNF-014**).

---

## 1. Deux vérifications complémentaires

Aucune des deux ne suffit seule ; elles ne détectent pas les mêmes choses.

| | Portée | Détecte | Ne détecte pas |
|---|---|---|---|
| **Scanner statique**<br>`scripts/audit-accessibilite.js` | **Tout** le balisage du dépôt, y compris les branches jamais rendues en démonstration | Libellé absent, tableau sans en-tête, image sans alternative, arrêts de tabulation parasites | Ce qui dépend du rendu : contraste calculé, ordre de focus réel |
| **Audit dynamique axe-core**<br>`e2e/tests/e2e/helpers/a11y.ts` | Les écrans effectivement parcourus par un test | Contraste, ARIA incohérent au rendu, structure de la page assemblée | Les écrans non couverts par un test |

**C'est l'un qui a rattrapé l'autre**, dans les deux sens : le scanner a trouvé 1 923 arrêts de
tabulation qu'axe ne signale pas, et axe a trouvé un contraste insuffisant sur l'écran de
connexion que le scanner ne pouvait pas calculer.

---

## 2. État actuel

```
$ node scripts/audit-accessibilite.js
Audit d'accessibilité — SGQRI 008 3.0 / WCAG 2.1 AA
66 fichiers analysés

✅ Aucun manquement structurel détecté.
```

Audit dynamique : **7 appels axe-core** répartis sur les parcours budgétaire, affectations et
connexion. Zéro manquement.

### Critères couverts par le scanner

| Critère | WCAG | Vérification |
|---|---|---|
| Image sans alternative | 1.1.1 | `<img>` sans `alt` |
| Champ sans libellé | 1.3.1 / 4.1.2 | Ni `<label for>`, ni libellé enveloppant, ni `aria-label` |
| Liste déroulante sans libellé | 1.3.1 | idem pour `<select>` |
| Tableau sans légende | 1.3.1 | `<table>` sans `<caption>` ni `aria-label` |
| En-tête sans portée | 1.3.1 | `<th>` sans `scope` |
| Champ libellé par le seul texte indicatif | 3.3.2 | `placeholder` tenant lieu de libellé |
| Bouton sans nom accessible | 4.1.2 | contenu vide ou purement iconographique |
| Lien sans intitulé | 2.4.4 | idem pour `<a>` |
| Cadre sans titre | 4.1.2 | `<iframe>` sans `title` |
| Langue non déclarée | 3.1.1 | `<html>` sans `lang` |
| Identifiant dupliqué | 4.1.1 | même `id` deux fois dans un fichier |
| Élément masqué mais focusable | 4.1.2 | `aria-hidden` avec `tabIndex` ≥ 0 |
| Ordre de focus imposé | 2.4.3 | `tabIndex` positif |
| Arrêt de tabulation parasite | 2.4.3 | `tabIndex={0}` sur un élément non interactif |
| Élément cliquable inatteignable au clavier | 2.1.1 | `onClick` sans `role` ni gestion clavier |

---

## 3. Ce que la campagne a corrigé

### Le défaut dominant : 1 923 arrêts de tabulation parasites

`tabIndex={0}` avait été posé sur des titres, des libellés, des paragraphes et des cellules —
544 `<span>`, 473 `<label>`, 198 `<strong>`, 160 `<p>`… L'intention était sans doute de rendre
ces textes « atteignables », mais l'effet est l'inverse de celui recherché : chaque élément
devient un arrêt de tabulation qui **ne fait rien**.

Sur la page Paie, cela représentait **277 arrêts** à franchir avant d'atteindre un contrôle
utile. C'est une infraction directe au critère 2.4.3 (Ordre de focus) et, en pratique, la
principale barrière d'usage pour une personne naviguant au clavier.

Retirés partout où l'élément n'a ni `role` ni gestionnaire de clic — les régions défilables et
les onglets, pour lesquels l'attribut est légitime, sont préservés.

### Les autres correctifs

| Correctif | Nombre |
|---|---:|
| Intitulés accessibles ajoutés (champs, listes, cases à cocher en tableau) | 81 |
| `scope="col"` sur les en-têtes de tableau, y compris les colonnes injectées dynamiquement | 27 |
| Légendes de tableau | 5 |
| Voiles de fond marqués décoratifs (fermeture clavier déjà assurée) | 2 |
| Contraste insuffisant sur l'écran de connexion — `#94a3b8` (~2,6:1) → `--muted` (≥ 4,5:1) | 1 |
| Icônes de navigation retirées de l'ordre de tabulation (`aria-hidden` + `tabIndex={0}`) | 3 |

---

## 4. Choix de conception à connaître

**Le libellé enveloppant est valide.** Le dépôt utilise massivement
`<label>Nom <input/></label>` — technique WCAG H44, parfaitement conforme. Une première version
du scanner l'ignorait et produisait 240 faux signalements ; les « corriger » aurait dégradé un
balisage correct.

**`aria-required` sans `required`.** Sur le sélecteur d'établissement de la page de connexion,
la validation native afficherait une bulle transitoire, illisible par un lecteur d'écran et
effacée au premier clic. Le contrôle se fait à la soumission, avec un message persistant.

**Les voiles de fond restent cliquables.** Le clic n'est qu'un raccourci : la fenêtre modale se
ferme par Échap, la barre latérale par son bouton de menu. Marqués `aria-hidden`, ils
disparaissent de l'arbre d'accessibilité sans perdre leur commodité à la souris.

---

### 4.x Barres d'onglets : un seul délégué clavier (12 sept. 2026)

Dix-neuf écrans portent un `role="tablist"` à *tabindex tournant* (seul l'onglet actif est
atteignable par Tab). Ce motif n'est conforme (2.1.1) que si les flèches déplacent le focus ;
treize écrans ne le faisaient pas — un utilisateur au clavier ne pouvait **jamais** ouvrir un autre
onglet. Plutôt que treize correctifs, `frontend/src/accessibilite.ts` installe un délégué unique sur
le document : ← → ↑ ↓ passent à l'onglet voisin en bouclant, Début / Fin aux extrémités, avec
activation automatique (WAI-ARIA APG). Tout `role="tab"` présent ou futur en bénéficie sans que
l'écran ait à y penser ; l'essai `audit-wcag22` « 2.1.1 — chaque barre d'onglets se parcourt au
clavier » le prouve sur l'écran le plus fourni.

### 4.z Espace superadmin dans l'audit géométrique ; contenu replié (12 sept. 2026)

L'espace superadmin n'apparaît pas dans la navigation d'un administrateur d'établissement : il
manquait à l'audit `audit-wcag22`, qui parcourt les pages avec ce compte. Il a désormais son propre
parcours (compte superadmin doublé), **chaque onglet mesuré aux quatre largeurs** — dont le nouvel
onglet Exploitation (sauvegardes, métadonnées SAML).

Ce parcours a révélé un **faux positif du relevé** : Chromium rapporte une géométrie non nulle
aux champs d'un `<details>` replié, alors qu'ils ne sont ni rendus ni atteignables ; le point
central touchait le `summary`, et chaque champ des panneaux repliés (facturation, vitrine) était
déclaré « recouvert ». Le relevé ignore maintenant le contenu d'un `details` fermé — comme le fait
un lecteur d'écran — sans rien relâcher sur le contenu déployé.

### 4.w Toute la navigation mesurée, panneaux déployés (12 sept. 2026)

L'audit géométrique ne parcourait que 16 pages ; il parcourt désormais les **43 pages** de la
navigation d'un administrateur (plus les 7 onglets du superadmin), et relève chaque page une
seconde fois **tous `<details>` ouverts** — les formulaires repliés n'étaient jamais mesurés. Un
seul défaut trouvé sur les 27 pages ajoutées : les boutons « + » du calendrier (26 × 17 px),
portés à 24 × 24 px (2.5.8). Les pages sont rendues sur doublures vides : les états remplis de
données restent du ressort de la suite e2e.

### 4.x États qu'aucun onglet n'ouvre : `etats` (12 sept. 2026)

Un assistant à étapes, une liste de résultats, un formulaire qui n'apparaît qu'après un bouton :
autant d'états que ni la page ni ses onglets ne rendent d'eux-mêmes. `audit-wcag22` accepte
désormais, par page, une liste `etats: [{ nom, agir, apres? }]` : `agir` conduit l'écran dans
l'état (coller un fichier, avancer d'une étape, lancer une recherche), la mesure se fait, puis
`apres` referme ce qui recouvrirait la navigation. Une action qui échoue est un état
**injoignable**, pas un état sauté. États couverts : assistant d'import (correspondance, aperçu),
formulaire d'incident en gravité élevée, recherche globale ouverte, suppléants disponibles avec
résultat. Trouvé grâce à eux : la liste de résultats de la recherche était **par-dessus** le menu
— remise dans le flux, elle pousse la navigation au lieu de la recouvrir.

Deux effondrements d'écran ont aussi été trouvés par la même campagne : dans le harnais
d'interface, un point non doublé répond `[]`, et `ReglesRh` (`voc.champs.find`) comme `ApiDocs`
(`spec.components.schemas`) plantaient sur cette réponse — et tout le rendu React avec eux, ce
qui rendait **injoignable** chaque page suivante. Règle : un écran survit à une réponse vide.

### 4.y Formulaires : libellés, aide, erreurs

Les libellés de champ sont en couleur de texte à 13 px (et non en gris à 12 px) ; un texte d'aide
`.aide` relié par `aria-describedby` dit ce qu'il faut savoir avant de saisir, à la place d'un
`placeholder` qui disparaît à la frappe ; un champ en erreur (`.field.invalide`) porte bordure
**et** message (`.erreur`), jamais la couleur seule (1.4.1). Les groupes de champs sont des
`fieldset`/`legend`, qui reprennent la grille du formulaire ; sur téléphone, le bouton d'envoi
prend toute la largeur. Les boutons dans les tableaux sont compacts mais gardent une cible
≥ 24 px (2.5.8).

## 5. Limites

Par honnêteté méthodologique.

1. **Un audit automatisé ne couvre qu'une partie des critères WCAG** — de l'ordre du tiers. Il
   ne juge ni la pertinence d'un texte alternatif, ni la clarté d'un intitulé, ni la logique
   d'un ordre de lecture.
2. **L'audit dynamique ne voit que les écrans testés.** Sept points de contrôle aujourd'hui ;
   les autres pages ne sont couvertes que par le scanner statique.
3. **Aucun test avec de vraies technologies d'assistance** (NVDA, JAWS, VoiceOver) n'a été
   mené. C'est la seule façon de valider l'expérience réelle, et cela reste à faire.
4. **Le contraste n'est vérifié que sur les écrans parcourus par axe.** Une couleur codée en
   dur sur une page non testée passerait inaperçue.

**Pour une déclaration de conformité formelle**, un audit externe reste nécessaire — c'est
d'ailleurs ce que demande le critère `D-ACCES-1` de l'annexe 9 du devis UdeS : *« la conformité
de l'accessibilité du produit est documentée »*.

---

## 6. Utilisation

```bash
node scripts/audit-accessibilite.js            # rapport lisible
node scripts/audit-accessibilite.js --json     # sortie machine
node scripts/audit-accessibilite.js --strict   # code de sortie 1 si manquement (intégration continue)

cd e2e && npx playwright test budgeting-planning login-tenant-required   # audits axe-core
```

Le mode `--strict` est conçu pour une porte de qualité en intégration continue : il échoue dès
le premier manquement, ce qui empêche une régression de passer inaperçue.
