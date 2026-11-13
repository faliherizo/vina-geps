# Feuille de temps — trois implantations, une question : que peut-on partager ?

> Mesuré le **1er novembre 2026**, à la source : `frontend/src/Time.tsx` (1 139 lignes),
> `frontend/src/effectifs/temps/` (537 lignes en 4 fichiers), et le module Santé Québec
> (`frontend/src/santequebec/`, 36 pages).
>
> La demande était : « voir si c'est possible d'intégrer dans le module Santé Québec les
> fonctionnalités de `Time.tsx`, ou d'avoir une classe de base et d'implémenter les
> fonctionnalités spécifiques par module ».

---

## 1. Ce que la mesure dit, et qui change la question

Il n'y a pas **une** feuille de temps à généraliser, il y en a **deux qui ne font pas le même
métier** — et une troisième qui n'existe pas.

| | `Time.tsx` (paie et projets) | `effectifs/temps/` (Urgences-santé) | Module Santé Québec |
|---|---|---|---|
| Qui l'utilise | **l'employé**, pour saisir | **l'encadrement**, pour valider | — |
| Portée d'un écran | **une** personne, **une** semaine | **toutes** les personnes d'une unité, une période de paie |— |
| Geste central | saisir des heures dans une grille 7 colonnes | relire planifié/déclaré, approuver ou retourner | — |
| Unité de ligne | mandat × tâche × type horaire × mode de compensation | une **feuille** par personne |— |
| Sortie | heures approuvées → budget de projet | **lot de paie signé** → PFT | — |
| Maquette correspondante | aucune dans cet appel d'offres | **s09** | — |

**La maquette `s09` est un écran de validation, pas une grille de saisie.** Ses quatre tuiles — À
valider, Anomalies, Heures sup., Historique — et son tableau *Employé · Planifié · Déclaré ·
Anomalies · Statut* décrivent exactement `effectifs/temps/TempsPlanification.tsx`, qui est livré.

Autrement dit : **« intégrer `Time.tsx` dans le module Santé Québec » ne répondrait pas à `s09`.**
`Time.tsx` est la grille de saisie de la paie par projet ; `s09` est la console d'approbation des
effectifs. Les rapprocher parce qu'elles s'appellent toutes deux « feuille de temps » produirait
un écran qui ne sert bien ni l'un ni l'autre.

## 2. Ce qui est réellement partageable

En lisant les trois ensembles, ce qui se répète n'est **ni la grille ni la console** : c'est la
couche en dessous.

| Brique | Dans `Time.tsx` | Dans `effectifs/temps` | Verdict |
|---|---|---|---|
| **Vocabulaire d'état** (brouillon → soumise → approuvée / retournée) | `status` en anglais, 4 valeurs, badge `.badge` | `StatutFeuille` en majuscules, `LIBELLE_STATUT`, `TON_STATUT` | **À partager** — deux vocabulaires pour un même cycle, c'est deux endroits où ajouter un état |
| **Durée lisible** (minutes → `8 h 15`) | `fmtH` (décimales d'heures) | `duree` (minutes, gère le négatif) | **À partager** — et celle d'effectifs est la bonne : les écarts sont signés |
| **Fenêtre de semaine** (ancre → dim..sam, navigation ±7 j) | `shiftDate`, `DAY_LABELS` | période de paie, pas de semaine | Partiellement — la semaine est propre à la paie |
| **Grille jour × ligne** avec total par colonne et par ligne | 90 lignes de JSX | absent | **À extraire** si une troisième saisie apparaît, pas avant |
| **Flux d'approbation par paliers** | approbateur unique | `FluxApprobation.tsx`, paliers configurables | **À partager** — c'est `E-GP-6`, et c'est l'écart n° 8 du document 06 |
| **Anomalies** (écart planifié/déclaré, segment manquant) | absent | indicateurs sur la feuille | Propre aux effectifs |
| **Banque d'heures** | accumulation/reprise | absent | Propre à la paie |

### La règle qu'on en tire

> **On partage le vocabulaire et les calculs, pas les écrans.**

Un composant « feuille de temps générique » avec des points d'extension par module aurait à servir
une saisie à sept colonnes *et* une console d'approbation multi-personnes : il porterait les deux
jeux de besoins, et chaque ajout dans l'un demanderait de vérifier l'autre. C'est le contraire de
ce que la séparation en modules a coûté à bâtir.

## 3. Ce qui est proposé — et ce qui ne l'est pas

### Proposé (lot « T »), dans cet ordre

1. **`frontend/src/feuilledetemps/vocabulaire.ts`** — un seul cycle d'état, ses libellés, ses tons,
   et `duree(minutes)`. Les deux écrans existants s'y branchent ; aucun changement visible.
   *Porte* : un essai qui refuse qu'un état soit nommé ailleurs.
2. **`FluxApprobation` rendu partageable** — il est déjà écrit pour les paliers configurables
   (`effectifs/temps/FluxApprobation.tsx`, 171 lignes). Le sortir du contexte `effectifs` ferme
   l'écart **E-GP-6** pour les deux modules à la fois.
3. **Une feuille de temps pour le module Santé Québec**, si elle est demandée : elle naît de la
   **console** (`s09`), pas de la grille — un service clinique valide des gardes, il ne saisit pas
   des mandats de projet. Elle réutiliserait alors les deux briques ci-dessus et le descripteur de
   page du module (`santequebec/noyau.tsx`).

### Non proposé, et pourquoi

- **Une classe de base commune aux trois écrans.** Les deux écrans existants n'ont en commun que
  leur nom. Une base qui les couvrirait tous deux serait un composant à deux visages, dont chaque
  évolution devrait être vérifiée deux fois — le coût que l'héritage est censé éviter.
- **Déplacer `Time.tsx` dans un module.** Il sert la paie et les projets, qui sont vendus
  séparément des effectifs et du module clinique. Le déplacer changerait ce que trois clients
  voient, pour une raison de rangement.

## 4. État au 1er novembre 2026

| Élément | État |
|---|---|
| Console de validation d'effectifs (maquette `s09`) | ✅ livrée — `planification:temps`, module `effectifs_temps` |
| Grille de saisie de la paie | ✅ livrée — `Time.tsx` |
| Feuille de temps du module Santé Québec | ⛔ **inexistante**, et non demandée par l'AO 2027-005 |
| Vocabulaire d'état partagé | ⛔ à faire (lot T·1) |
| Flux d'approbation partagé (E-GP-6) | 🟡 écrit pour les effectifs seulement (lot T·2) |
