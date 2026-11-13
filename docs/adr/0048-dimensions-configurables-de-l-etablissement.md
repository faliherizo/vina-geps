# ADR 0048 — Site, unité, secteur et spécialité sont des données, pas des colonnes

- **Statut** : accepté
- **Date** : 19 octobre 2026
- **Décideurs** : équipe Nexu VINA
- **Concerne** : `backend/src/referentiel/**`, migration 0140, écran `sqdimensions`
- **Exigences** : AO 2027-005-9529-CS-AOP — BF-005, BF-027, BF-092, BF-131 (et par ricochet BF-116)

## Contexte

Quatre besoins obligatoires du devis demandent de **filtrer, grouper et ventiler** l'horaire
clinique par site, unité, secteur et spécialité :

- **BF-005** veut un référentiel d'organisation propre à l'établissement ;
- **BF-027** veut rechercher une personne par groupe, spécialité et secteur ;
- **BF-092** veut des rapports ventilés par ces axes, et **reproductibles** ;
- **BF-131** veut que l'utilisateur choisisse lui-même ses axes de ventilation.

Aucun de ces champs n'existait. La forme qui vient d'abord à l'esprit — quatre colonnes
`site`, `unite`, `secteur`, `specialite` sur le quart et sur la personne — s'écrit en une heure et
se heurte à trois murs le mois suivant.

D'abord, elle **satisfait un établissement et bloque le suivant**. Lanaudière nomme ses axes ainsi ;
un centre hospitalier universitaire parlera de « département », « programme », « mission ». Le
devis est explicite : le référentiel appartient à l'établissement.

Ensuite, elle **ne sait pas emboîter**. « Joliette › Urgence › Secteur A » est une hiérarchie, et
filtrer sur le site doit ramener ses unités — sinon le gestionnaire doit connaître son arbre par
cœur et cocher chaque feuille.

Enfin, et c'est le mur décisif, elle **réécrit le passé**. Une unité qui change de secteur en mars
changerait, avec une colonne, le résultat d'un rapport de février. Un établissement public est
vérifié : un chiffre qu'on ne sait pas reproduire est un chiffre qu'on ne peut pas défendre.

## Décision

### 1. Une **dimension** est une donnée de l'établissement

`dim_dimensions` porte un code, un libellé, un ordre d'affichage et un drapeau `hierarchique`.
Un administrateur en déclare autant qu'il lui en faut, sans livraison de code. `dim_valeurs` porte
les valeurs, éventuellement emboîtées par `parentId`.

### 2. Le rattachement est une **affectation datée**, pas un attribut

`dim_affectations` relie un objet (`quart`, `tache`, `personne`, `groupe`, `periode`) à une valeur,
avec ses bornes `depuis`/`jusqua`. Lire « le site de ce quart » n'existe pas : on lit « le site de
ce quart **à cette date** ».

Conséquence assumée : il n'y a aucun raccourci. L'absence du raccourci est précisément ce qui rend
BF-092 réalisable.

### 3. Ce qu'une dimension qualifie est un **réglage**

`dim_portees` déclare, par couple (dimension, objet), si la dimension est **obligatoire** et si
elle admet **plusieurs valeurs à la fois**. Un quart tient à un site ; une personne peut porter
deux spécialités. Cette règle vivait naturellement dans le code — une condition par dimension —, et
elle y aurait figé ce que l'énoncé demande de configurer.

### 4. On **retire** sans effacer

Une dimension comme une valeur se désactivent (`actif = 0`), et une valeur retirée emporte sa
descendance. Les affectations déjà posées continuent de nommer ce qu'elles nommaient.

### 5. L'ensemble des objets qualifiables est **fermé**

`OBJETS_DIMENSIONNABLES` est une union TypeScript, convertie **une seule fois** à la frontière
(`objetDe`), qui refuse un code inconnu en nommant les valeurs admises. Ajouter un objet reste une
décision de conception : il faut que quelque chose, côté lecture, sache joindre cette table.

## Conséquences

**Ce que cela coûte.** Quatre tables là où quatre colonnes auraient suffi, et une jointure de plus
à chaque filtre. Toute lecture de dimension exige une date, y compris les écrans qui voulaient
seulement afficher « le site », qui doivent désormais dire *de quand*. Un écran d'administration à
dix sections est nécessaire avant que la première valeur existe — c'est le prix d'un référentiel
qui n'est pas livré avec des axes en dur.

**Ce que cela rend possible.** Un établissement nomme ses propres axes. Un filtre sur un site
ramène ses unités. Un rapport de février reste celui de février après une réorganisation de mars.
Et BF-131 — l'utilisateur qui choisit ses axes — devient une lecture de `dim_dimensions`, non une
livraison de code.

**Ce qui devient interdit.** Ajouter une colonne `site`, `unite`, `secteur` ou `specialite` à une
table du module. Si un écran a besoin d'un axe, il le déclare comme dimension.

## Options écartées

| Option | Pourquoi elle est écartée |
|---|---|
| **Quatre colonnes sur le quart et la personne** | Fige les axes, ne sait pas emboîter, et réécrit les rapports passés à chaque réorganisation. C'est l'option que les quatre besoins excluent ensemble. |
| **Champs personnalisés existants (`custom_field_defs`)** | Ils stockent une **valeur libre** par entité, sans référentiel partagé, sans hiérarchie et sans bornes de validité. On ne peut ni lister les sites, ni filtrer sur la descendance, ni reproduire un passé. L'écart est consigné dans [ADR 0049](0049-registre-des-portees-de-champs-personnalises.md). |
| **Dimensions en configuration de déploiement (fichier, variables)** | Un établissement ne peut pas ajouter une unité sans livraison. Le devis demande l'inverse. |
| **Rattachement sans dates, historisé dans un journal d'audit** | L'audit sert à relire, pas à calculer. Résoudre l'état de février exigerait de rejouer un journal — coûteux, et faux dès qu'une écriture manque. |
| **Dimensions hiérarchiques universelles (tout est un arbre)** | Une spécialité n'est pas un arbre ; forcer la forme ajoute une racine fictive à chaque axe plat, que l'écran doit ensuite masquer. |

## Vérification

- Essais de domaine, sans base (`tests/unit/referentiel-domaine.test.ts`, 56 cas) : fenêtres de
  validité aux bornes, refus de boucle hiérarchique, descendance d'une valeur, **tous** les motifs
  de refus d'une affectation rendus ensemble, dimensions obligatoires manquantes à une date.
- Essais d'intégration par l'API (`tests/integration/referentiel.integration.test.ts`, 25 cas) :
  filtre par site qui ramène l'unité, affectation expirée absente du résultat, dimension retirée
  dont les affectations restent lisibles, garde de rôle route par route.
- Couverture mesurée des trois fichiers du contexte : **100 % des lignes**.
- Écran `sqdimensions` : axe-core sans manquement et géométrie vérifiée à 320, 390, 768 et 1280 px
  (`e2e/tests/ui/sante-quebec-pages.spec.ts`).
