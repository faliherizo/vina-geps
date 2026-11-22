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

## AD-06 — API d'échange de l'organisme

Découpée en trois branches, chacune livrable seule.

### AD-06a — garde machine et lectures (`/api/echanges/v1`)

| Couche | Changement |
|---|---|
| `partage/securite` | Port `JetonsMachine` (application : client, organisme, portées `LECTURE`/`ECRITURE`) ; `JetonDeSession.TYP_MACHINE`. |
| `partage/web` | `GardeMachine` : jeton `client_credentials` exigé (401), organisme porté par le jeton, portée exigée (403). L'en-tête `X-Tenant-Id` n'a aucun effet. |
| Sécurité (défaut corrigé) | `JetonDeSession.lire` refusait d'identifier un jeton machine : signé par la même clé, il valait session (établissement, sans rôle). Il est désormais refusé. |
| `integrations` | `JetonClient` implémente `JetonsMachine` ; l'ancienne lecture devient `lireRevendications`. |
| `effectifs/domaine` | `EchangesApi` (fenêtre ≤ 62 jours, limite 1 à 500, curseur, formes immuables) et le port `EchangesApiPort`. |
| `effectifs/application` | `LectureEchangesApi` : le curseur des correctifs avance même sur un quart non exposé. |
| `effectifs/infrastructure` | `DepotEchangesApi` (états et types passés en paramètres) ; `ControleurEchangesApi`. |

| Route (portée `read`) | Rend |
|---|---|
| `GET /quarts?du=&au=` | Quarts des périodes publiées ou closes, places et matricule. |
| `GET /quarts/modifications?depuis=&limite=` | Correctifs (journal `equipage` / `place`) avec l'état présent du quart, et le curseur suivant. |
| `GET /releves-presence?depuis=&limite=` | Feuilles approuvées ou payées, segments actifs, et le curseur suivant. |

Limite connue : un client révoqué garde son jeton jusqu'à expiration (une heure), comme avant.

### AD-06b — employés, soldes des banques, jeton standard ([ADR 0088](../../adr/0088-api-echanges-organisme-machine.md))

| Route | Portée | Effet |
|---|---|---|
| `PUT /employes/{matricule}` | `write` | Crée (201) ou met à jour (200) la ressource, par `GestionnaireRessources` ; le journal signe `application:<clientId>`. |
| `PUT /employes/{matricule}/soldes` | `write` | `{"soldes":[{banque, heures, au}]}` : un solde par banque, remplacé à chaque envoi ; écarts nommés ensemble (422). |
| `GET /employes/{matricule}/soldes` | `read` | Les soldes tenus. |
| `POST /api/oauth/token` | public | Formulaire (RFC 6749), `client_secret_basic` ou `_post`, JSON d'origine ; `Cache-Control: no-store`. |

Règles d'absence : le code d'absence porte `banque` et `heuresParJour` ; faits `soldeBanqueConnu`, `soldeBanqueHeures`,
`heuresDemandees`, `soldeBanqueApresHeures` (toujours présents ; faux et zéro sans solde).

Performance : correctifs en trois requêtes par page (au lieu de deux à trois par entrée) ; bornes de curseur indexables ;
index `(tenantId, date)` des équipages et `(tenantId, approuveeLe)` des feuilles (changeset 0023).

### AD-06c — absence constatée aux opérations ; avis des quarts modifiés ([ADR 0090](../../adr/0090-absence-operations-et-avis-des-quarts.md))

| Couche | Changement |
|---|---|
| Domaine | `EchangesApi.cibles` : les quarts que touche une entrée du journal (objet équipage ou place ; places de `places.vacantes` et de `echange.applique`). `EchangesApi.AbsenceOperations` : quart **ou** dates, et un code. `AbsencesEffectifs.Origine {DEMANDE, OPERATIONS}` : statut (acceptée d'office aux opérations), préavis exigé ou non, action du journal ; `estLaMeme` |
| Application | `GestionnaireAbsencesEffectifs.constater` (même chemin que `declarer`, renvoi identique sans effet) ; `GestesEchangesApi.absence` (le quart donne la date, l'employé doit y tenir une place) ; `JournalAvecAvisDesQuarts` (décorateur du journal : `effectifs.quart.modifie` pour chaque quart exposé touché, clé quart + instant) ; `LectureEchangesApi` : un correctif par place |
| Persistance | `DepotEchangesApi.journalQuartsApres` retient aussi les actions `places.vacantes` et `echange.applique` ; aucun changeset |
| Web | `POST /api/echanges/v1/employes/{matricule}/absences` (portée `write`) : 201, 200 au renvoi, 404, 409, 422 |
| Webhooks | `Webhooks.EVENEMENTS` gagne `effectifs.quart.modifie` ; le bus relaie `absence.constatee` comme `effectifs.absence.acceptee` |

## AD-07 — offre par ancienneté et à tour de rôle

Décisions et options écartées : [ADR 0089](../../adr/0089-offre-quart-anciennete-tour-de-role.md). Réutilise le
bassin des admissibles (`LectureDisponibilites.candidats`), l'attribution sous règles (`GestionnairePlaces.attribuer`),
l'ordonnanceur partagé (`DepotTaches`, comme la fin de collecte) et le rattrapage du tour de rôle AGPH ; rien n'est
dupliqué.

```
absence accordée ─▶ GestionnaireComblementAbsences.offrir
                       ├─ SIMULTANEE : AvisDeComblement à chaque admissible (comme avant)
                       └─ CASCADE    : CascadeOffresQuart.ouvrir ─▶ ordre figé ─▶ avancer
avancer : place pourvue ? RETIREE · échus → EXPIRE (CLOS si urgente) · proposition en cours ? rien
          · sinon aAviser() : suivant (ou tous, une fois, si urgente) → OFFERT + tâche + avis · personne : EPUISEE
accepter : rattraper ─▶ exigerOffertA ─▶ GestionnairePlaces.attribuer ─▶ ACCEPTE + POURVUE  | refus de règle : ECARTE ─▶ avancer
refuser  : rattraper ─▶ exigerOffertA ─▶ REFUSE ─▶ avancer
tâche effectifs_offre_echeance (ordonnanceur) ─▶ avancer
```

| Couche | Changement |
|---|---|
| Domaine | `OffresQuart` (pur) : `Mode {SIMULTANEE, CASCADE}`, `EtatOffre`, `EtatRang` (`estCompte`, `pourquoiPasOffert`), `Evenement` (codes du journal), `Parametres` (défauts de la convention, `modifiesPar` borné et nommé), `ordonner` (heures comptées ↑, ancienneté ↓, identifiant), `estUrgente`, `echeance`, `debutFenetre`, `heureLocale`. `OffreQuart` (agrégat) : `echus`, `aUnRangOffert`, `suivant`, `aAviser`, `exigerOffertA`. Ports `DepotOffresQuartPort`, `ParametresOffresPort`, `EcheancesOffresPort` |
| Application | `CascadeOffresQuart` (ouvrir, avancer, rattraper, executer, fermer) ; `ReponsesOffresQuart` (accepter, refuser) ; `GestionnaireParametresOffres` ; `LectureOffresQuart` (miennes, d'une absence, périmètre de l'unité) ; `AvisDeComblement` extrait de `GestionnaireComblementAbsences` (partagé) ; `GestionnaireComblementAbsences.Ecritures` reçoit la cascade |
| Persistance | Changeset **0024** : `eff_offres_parametres`, `eff_offres`, `eff_offre_rangs` (+ index) ; retour `db/retour/0024-retour.sql` ; `SchemaOffresQuart`, `SchemaEssai`. `DepotOffresQuart` lit le quart sur la place et son équipage, le nom sur la ressource ; l'échéance se compare en instant, pas en texte. `AdaptateurOrdonnanceurOffres` : tâche `effectifs_offre_echeance` par offre |
| Web | `ControleurOffresQuart` : `GET /api/effectifs/offres/miennes`, `POST /{id}/accepter`, `POST /{id}/refuser` (socle `effectifs`) ; `GET /api/effectifs/offres?absenceId=`, `GET/PUT /parametres` (module `effectifs_absences`, gestion) |
| Interface | « Mon horaire » : section « Quarts qui me sont offerts » (échéance, Accepter / Refuser, résultat annoncé en `role="status"`) ; comblement d'une absence : tableau légendé « Ordre d'offre » par place offerte ; écran des absences : formulaire « Offre des quarts » (mode, délai, seuil d'urgence, fenêtre) |
