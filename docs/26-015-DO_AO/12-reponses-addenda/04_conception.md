# 26-015-DO — Conception des stories AD-01 à AD-05

> Le contexte `effectifs` du serveur Java (`backend-java/src/main/java/ca/vina/nexu/effectifs`) porte toutes ces
> stories ; aucune ne crée de contexte. Décisions et options écartées : [ADR 0087](../../adr/0087-reponses-26-015-do-faits-seuils-et-optimisation.md).

## AD-01 — `heures24h`

| Couche | Changement |
|---|---|
| Domaine | `Planification.heuresSur24h(existants, nouveau)` : pour chaque fenêtre de 24 h qui **commence** au début d'un créneau compris entre `début du nouveau − 24 h` et la fin du nouveau, la somme des recouvrements ; le maximum. Le maximum d'une somme de recouvrements sur une fenêtre glissante est atteint quand la fenêtre commence au début d'un créneau : il suffit de ces fenêtres. `EchangeEffectifs.Faits` gagne `heures24h` |
| Catalogue | `ContexteRegle.ECHANGE` et `ATTRIBUTION` gagnent le fait `heures24h` (nombre) |
| Application | `GestionnairePlaces` (attribution) pose le fait ; `GestionnaireEchangesEffectifs` le reçoit par `faitsDUnSens` |
| Interface | Aucune : l'éditeur de règles lit le catalogue (`/api/effectifs/regles/faits`) |

## AD-02 — `seuilJoursLongTerme`

| Couche | Changement |
|---|---|
| Domaine | `TypeReferentiel.CODES_ABSENCE` : attribut borné `seuilJoursLongTerme` (1 à 365, facultatif). `AbsencesEffectifs.estLongTerme(duree, seuil, jours)` : avec un seuil, `jours ≥ seuil` ; sans seuil, la catégorie. `voieDeComblement` reçoit en plus « période en cours » : une place d'une période en cours ne se réserve pas |
| Application | `GestionnaireComblementAbsences.combler` calcule la longueur une fois par absence ; la période en cours se juge par place (état `PUBLIEE` et premier jour atteint) |
| Interface | Aucune : l'écran des référentiels rend les attributs du descripteur |

## AD-03 — seuils de période de paie et par convention

| Couche | Changement |
|---|---|
| Domaine | `TempsEffectifs.Parametres` gagne `seuilPeriodePaieHeures` ; `heuresSupplementaires` ajoute la passe de période de paie sur le reste après jour et semaine. Une feuille **est** une période de paie (`GestionnairePreparationTemps` la borne) : la passe porte sur ses segments, sans date d'ancrage à régler. `SeuilsConvention` : les trois seuils d'une convention ; `Parametres.selon(seuils)` en dérive les paramètres d'une feuille |
| Persistance | Changeset `00NN` : colonne `seuilPeriodePaieHeures` de `eff_temps_parametres` ; table `eff_temps_seuils_conventions (tenantId, conventionCode, seuilJourHeures, seuilSemaineHeures, seuilPeriodePaieHeures)` ; retour `db/retour/00NN-retour.sql` ; `SchemaEssai` |
| Application | Le calcul d'une feuille lit la convention de la ressource et applique ses seuils s'ils existent |
| Web | `PUT /api/effectifs/temps/parametres` accepte les deux nouveaux champs ; `GET/PUT/DELETE /api/effectifs/temps/seuils-conventions/{code}` |
| Interface | Écran des paramètres du temps : deux champs, et un tableau des seuils par convention |

## AD-04 — optimisation du budget

Problème : choisir un sous-ensemble de postes **couverts** (au moins une semaine type déposée couvre tous leurs quarts)
qui maximise les heures hebdomadaires sous le budget. C'est un sac à dos 0-1 où la valeur égale le poids : une
programmation dynamique exacte sur les **quarts d'heure** (budget × 4 cases, au plus 168 h × 4 × n postes) répond en
millisecondes pour une campagne réelle (quelques centaines de postes). À heures égales, l'ordre des postes par nombre de
candidats décroissant départage, ce qui rend la proposition déterministe.

| Couche | Changement |
|---|---|
| Domaine | `OptimisationBudgetAgph` (pur) : `proposer(postes, budgetHeures)` → `Proposition(retenus, ecartes, total, budget, ecart)` |
| Application | Requête de lecture : postes de la campagne, leurs quarts de la semaine type, dépôts ; aucune écriture |
| Web | `GET /api/effectifs/agph/campagnes/{id}/optimisation` (même capacité que la lecture des dépôts) |
| Interface | Écran de la campagne : bouton « Proposer une optimisation », tableau légendé |

## AD-05 — renoncer, délai échu

| Couche | Changement |
|---|---|
| Domaine | `Agph.EtatRang.RENONCE` ; `Agph.TraitementDelaiEchu { ATTRIBUER, PASSER }` |
| Persistance | Changeset : colonne `traitementDelaiEchu` des campagnes (défaut `ATTRIBUER`) ; retour ; `SchemaEssai` |
| Application | `TourDeRoleAgph.solder` lit le traitement ; `renoncer` vérifie le tour ; `ClotureAgph` traite `RENONCE` comme `PASSE` |
| Web | `POST /api/effectifs/agph/campagnes/{id}/renoncer` ; `traitementDelaiEchu` dans `PUT …/parametres` |
| Interface | « Mon AGPH » : bouton « Je ne prends aucun poste », avec confirmation |
