# Étude : une feuille de temps qui s'adapte à n'importe quel type d'emploi

> Écrit pour l'équipe produit de VINA GePS et pour l'administration d'un organisme client. Ce
> document étudie comment la feuille de temps peut servir un paramédic, une répartitrice, un
> technicien de garde ou une adjointe administrative sans changer de code. Il dit ce qui est livré
> le 14 novembre 2026, et ce qui reste à décider.

## 1. Le constat

La feuille de temps actuelle (lot R9, E-RF-2 à E-RF-13) est **taillée pour le quart** : elle naît
de l'horaire publié et des pointages, et chaque segment dit un jour, des heures et un type d'heure.
C'est juste pour un paramédic. C'est insuffisant dès que l'organisme veut savoir **à quoi** le
temps a servi : une tâche, un projet, un patient transporté, un véhicule, une formation donnée.
Chaque emploi pose des questions différentes, et coder chaque question est une impasse.

Nexu VINA, le produit d'origine, l'avait résolu par des **champs propres à l'organisme**, attachés
à la ligne de temps, et par une **approbation au gestionnaire hiérarchique**.

## 2. Ce qui varie d'un emploi à l'autre

| Dimension | Paramédic | Répartitrice | Technicien de garde | Personnel administratif |
|---|---|---|---|---|
| Source du temps | Horaire publié + pointage | Horaire + pointage | Disponibilité + rappels | Saisie libre, semaine type |
| Granularité | Quart (8 à 12 h) | Quart | Intervention (minutes) | Demi-journée |
| Ce qu'on veut savoir | Véhicule, appels, transport prolongé | Console, appels traités | Site, intervention, durée de rappel | Projet, tâche |
| Qui approuve | Chef d'équipe, puis paie | Superviseure | Supérieur hiérarchique | Supérieur hiérarchique |
| Règles | Repos, heures sup. par quart | Pauses imposées | Rappel minimum payé | Semaine normale |

## 3. Le modèle retenu : trois leviers, aucun code par emploi

1. **Les types d'heures** existent déjà (référentiel daté, comportement et code de paie). Ils disent
   *comment le temps se paie*.
2. **Les champs de la feuille** sont **définis par l'administration de l'organisme**. Ils disent
   *à quoi le temps a servi*. Un champ a :
   - un code ;
   - un libellé ;
   - un type (texte, nombre, oui ou non, liste de valeurs) ;
   - une obligation (requis ou non) ;
   - un état (actif ou désactivé).

   La valeur se range dans le segment, et la feuille vérifie chaque valeur à la saisie : type,
   valeur de liste, obligation. Un champ désactivé ne se propose plus, mais les valeurs déjà saisies
   restent lisibles.
3. **Les règles** (contexte « Feuille de temps ») peuvent lire ces champs, par exemple « transport
   prolongé exige un motif ».

Une **description des tâches** est commune à tous les emplois : c'est un champ intégré, en texte
libre (500 caractères au plus), et non un champ à définir.

## 4. Ce qui est livré le 14 novembre 2026

| Fonction | Où |
|---|---|
| Description des tâches sur chaque segment | Ma feuille de temps : « Ajouter un segment », la correction, colonne « Tâches » |
| Champs propres à l'organisme (définis par l'administration, vérifiés à la saisie) | Console des feuilles › « Champs de la feuille » ; saisie dans Ma feuille de temps |
| La ressource nommée dans les tableaux | Ma feuille de temps et la console : nom et matricule |
| Soumettre au **supérieur hiérarchique** | Flux d'approbation : approbateur « Supérieur hiérarchique », résolu à la soumission depuis l'organigramme (`employees.managerId`). Sans supérieur connu, la feuille va au périmètre de gestion, et l'écran le dit |
| Désigner le supérieur | Console des feuilles › « Supérieurs hiérarchiques » (`PUT /api/personnel/employes/{id}/superieur`, capacité `employee.update`). Rien n'écrivait l'organigramme avant : sans cet écran, la règle retombait toujours sur le niveau GESTION. Soi-même, un inconnu et un cycle sont refusés |
| Le gestionnaire approuve | Console des feuilles : les feuilles qui lui sont désignées, même hors de son périmètre (existant), avec « En attente de … » |
| **Mon équipe** (comme dans Nexu VINA) | Menu principal › « Mon équipe » : les personnes qui relèvent du gestionnaire, directement ou non (`GET /api/personnel/equipe`). Il y rattache une personne sans supérieur, change le supérieur d'un membre (lui-même ou un autre membre), détache un subordonné direct (`PUT /api/personnel/equipe/{id}/superieur`, `employee.update`). Le cloisonnement est au serveur : rien hors de sa chaîne |
| **Suppléance pendant une absence** | « Mon équipe » › « Désigner un suppléant » (délégation datée du moteur d'approbation, `DELETE /api/approvals/delegations/{id}` pour y mettre fin). Pendant la période, les feuilles qui reviennent au gestionnaire parviennent **aussi** au suppléant, même sans périmètre sur l'unité ; l'employé lit « en son absence : … (jusqu'au …) » ; la décision est tracée « pour le compte de ». La délégation existait dans le moteur, mais la console des feuilles l'ignorait : le suppléant ne voyait rien |

### Où c'est vérifié

| Porte | Essai |
|---|---|
| Domaine | `ChampsFeuilleTest`, `FluxApprobationDomaineTest.superieurHierarchique`, `ControleurHierarchieHttpTest.domaine` |
| HTTP | `ControleurTempsEffectifsHttpTest.champsEtTaches`, `ControleurTempsFluxEffectifsHttpTest.superieurHierarchique`, `ControleurHierarchieHttpTest` |
| Domaine (équipe, suppléance) | `FluxApprobationDomaineTest.suppleance` ; `ControleurEquipeHttpTest` (règles de cloisonnement) |
| HTTP (suppléance) | `ControleurTempsFluxEffectifsHttpTest.suppleance` ; `ControleurApprobationsHttpTest` (délégations reçues, révocation) |
| Interface | `frontend/tests/feuille-champs.test.tsx`, `frontend/tests/mon-equipe.test.tsx` ; audit `audit-wcag22` (fiche ouverte, flux « supérieur », champs, supérieurs, Mon équipe et ses dialogues) |
| Bout en bout contre le Java | `e2e/tests/e2e/effectifs-temps-superieur.spec.ts`, `e2e/tests/e2e/mon-equipe-suppleance.spec.ts` |

## 5. Ce qui reste à décider (prochains lots)

| Sujet | Option recommandée | Pourquoi plus tard |
|---|---|---|
| **Modèles de feuille par emploi** (quels champs, quels types d'heures, pour quel corps ou statut d'emploi) | Un champ peut viser des corps d'emploi précis ; vide, il vaut pour tous | Il faut d'abord les vrais emplois du client |
| Saisie hors horaire (personnel administratif sans quarts) | Une « semaine type » qui préremplit la feuille | Elle dépend du modèle par emploi |
| Granularité à la minute (rappels de garde) | Déjà possible en heures et minutes ; une règle de « rappel minimum payé » | Il faut une règle de paie du client |
| Champs calculés (kilométrage, primes) | Une formule sur les champs, évaluée par le moteur de règles | Risque d'un langage de formules : à cadrer |
| Export des champs vers la paie | Colonnes supplémentaires du lot de paie, une par champ marqué « exporté » | Il faut le format du système de paie |
