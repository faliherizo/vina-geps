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
