# Guide d'administration — gestion des effectifs d'Urgences-santé

> Appel d'offres **26-015-DO**, exigence **E-TN-27** : « La documentation destinée aux
> administrateurs utilisant la console d'administration, des flux d'affaires, des gabarits de
> messages et des rapports est disponible. »
>
> Rédigé le 10 novembre 2026 **à partir du code** : écrans de `frontend/src/urgencessante/` et
> `frontend/src/effectifs/`, contrôleurs du backend Java `backend-java/src/main/java/ca/vina/nexu/effectifs/infrastructure/web/`
> (les routes réelles), catalogue des vues `CatalogueVues.java`, politique d'accès
> `backend-java/src/main/resources/politique-acces.json`. Ce qui n'existe pas dans le code est
> marqué **non disponible**.

## Table des matières

1. [À qui s'adresse ce guide](#1-à-qui-sadresse-ce-guide)
2. [Repères : menu, adresses, routes](#2-repères--menu-adresses-routes)
3. [Rôles et accès](#3-rôles-et-accès)
4. [Activation des modules](#4-activation-des-modules)
5. [Mise en service : l'ordre du paramétrage](#5-mise-en-service--lordre-du-paramétrage)
6. [Paramétrage](#6-paramétrage)
7. [Flux d'approbation](#7-flux-dapprobation)
8. [Gabarits de messages et avis](#8-gabarits-de-messages-et-avis)
9. [Journal des décisions](#9-journal-des-décisions)
10. [Rapports et exports](#10-rapports-et-exports)
11. [Tâches périodiques](#11-tâches-périodiques)
12. [Index : où trouver chaque réglage](#12-index--où-trouver-chaque-réglage)
13. [Limites connues et fonctions non disponibles](#13-limites-connues-et-fonctions-non-disponibles)

## 1. À qui s'adresse ce guide

| Lecteur | Ce qu'il y trouve |
|---|---|
| **Administration de l'organisme** (rôle `admin`) | Paramétrage : référentiels, périmètres, règles, flux, intégrations, rétention ; comptes et rôles |
| **Encadrement** (rôles `manager`, `director`) | Postes, périodes, quarts, quotas, milieux, profils de pointage, journal, relevés |
| **Exploitation du fournisseur** (rôle `superadmin`) | Activation des modules par organisme, tâches différées, sous-menus visibles |

Les écrans personnels (Mon horaire, Mes disponibilités, Mon pointage, Ma feuille de temps…) ne
sont pas décrits ici.

## 2. Repères : menu, adresses, routes

- **Menu** : la section **Urgences-santé** porte toutes les pages du module. Le paramétrage est
  sous **Urgences-santé › Administration des effectifs** ; la planification sous **Gestion des
  quarts**. Chaque entrée de menu est une page à son adresse (`/<clé>`), par exemple
  `/usreferentiels`.
- **Routes** : toutes les routes du module commencent par `/api/effectifs`. Elles exigent une
  session, un établissement et le **module** qui les gouverne ; certaines exigent en plus une
  **capacité** (§ 3.2). Un module désactivé ferme ses routes (403 « module '…' non activé pour cet
  organisme »).
- **Codes d'erreur** : 422 donnée invalide, 404 introuvable, 409 conflit ou refus par une règle
  (la réponse porte alors le `verdict` de l'évaluation), 403 hors périmètre ou capacité manquante,
  502 système externe indisponible.
- **Documentation d'API** : le contrat OpenAPI est servi par l'application (Swagger).

## 3. Rôles et accès

### 3.1 Les rôles

| Rôle | Ce qu'il fait dans le module |
|---|---|
| `admin` | Paramètre tout ; voit toutes les unités (le périmètre ne le borne pas) |
| `superadmin` | Exploitation : active les modules des organismes ; voit toutes les unités |
| `director`, `manager` | Planifient, comblent, approuvent — **dans leur périmètre** |
| `rh` | Écrans personnels ; pas de console d'effectifs |
| `employee` | Écrans personnels |

Le menu affiche les entrées d'encadrement aux rôles `admin`, `superadmin`, `director`, `manager`
(`ENCADREMENT` dans `frontend/src/urgencessante/menu.ts`) et les écrans personnels à tous.

### 3.2 Les capacités

Le serveur décide par capacité (`politique-acces.json`) :

| Capacité | Rôles par défaut | Ce qu'elle ouvre |
|---|---|---|
| `effectifs.configure` | `admin` | Écrire les référentiels, les périmètres, les règles, les paramètres et flux des feuilles de temps, la configuration des connecteurs, la rétention, l'alignement sur l'organigramme |
| `effectifs.manage` | `admin`, `director`, `manager` | Planifier (ressources, postes, périodes, quarts, places, quotas), décider (absences, échanges, pointages, feuilles), gérer les milieux et profils de pointage, lire le journal, produire relevés et lot de paie, déclencher les envois d'intégration |

Les capacités par rôle se modifient pour l'organisme :

- **Écran** : Administration › onglet « Rôles et accès » (page `/admin`).
- **Routes** : `GET /api/auth/roles`, `POST /api/auth/roles`, `PATCH /api/auth/roles/{code}`,
  `PUT /api/auth/roles/{code}/capabilities/{capability}` (corps `{ "allowed": true|false }`),
  `GET /api/auth/capabilities`.

### 3.3 Les comptes

- **Écran** : Administration › onglet « Comptes et rôles » (`/admin`).
- **Routes** : `GET|POST /api/auth/users`, `PATCH|DELETE /api/auth/users/{id}`,
  `POST /api/auth/users/{id}/reset-security`.
- Un compte planifiable doit être **relié à une ressource** (§ 6.4) ; sans ressource, ses écrans
  personnels disent qu'il n'est relié à aucune ressource planifiable. **Ce lien ne se pose pas à
  l'écran** : il passe par le champ `utilisateurId` de `POST /api/effectifs/planification/ressources`
  (voir § 13).

### 3.4 Les périmètres

Un `manager` ou un `director` n'agit que sur les unités qu'on lui a données : **le refus est le
défaut**. Un compte d'encadrement sans périmètre ouvre des consoles vides.

- **Écran** : Urgences-santé › Administration des effectifs › **Périmètres** (`/usperimetres`).
- **Geste** : choisir le compte, « Ajouter un accès », puis l'unité, l'étendue (« L'unité seule » ou
  « L'unité et ses sous-unités »), le niveau (`LECTURE`, `PLANIFICATION`, `GESTION` — chaque niveau
  inclut les précédents) et la date de début ; « Ajouter l'accès ». Un périmètre se **ferme** à une
  date, il ne s'efface pas.
- **Routes** : `GET /api/effectifs/perimetres?utilisateurId=…`, `POST /api/effectifs/perimetres`,
  `POST /api/effectifs/perimetres/{id}/fermer` (capacité `effectifs.configure`) ;
  `GET /api/effectifs/perimetres/moi` (ses propres unités).

## 4. Activation des modules

Le module est découpé en codes activables **par organisme**. Activer un code sans celui dont il
dépend est refusé.

| Code | Ouvre | Requiert |
|---|---|---|
| `effectifs` | Socle : référentiels, milieux, périmètres, journal, rétention | — |
| `effectifs_regles` | Moteur de règles | `effectifs` |
| `effectifs_planification` | Mon horaire, comblement, quarts, postes, périodes, ressources, quotas | `effectifs_regles` |
| `effectifs_disponibilites` | Disponibilités | `effectifs_planification` |
| `effectifs_absences` | Absences et comblement | `effectifs_planification` |
| `effectifs_echanges` | Demandes et échanges | `effectifs_planification` |
| `effectifs_agph` | Confection annuelle (AGPH) et remplacements long terme | `effectifs_planification` |
| `effectifs_presence` | Pointage et présence | `effectifs_planification` |
| `effectifs_temps` | Feuilles de temps | `effectifs_presence` |
| `effectifs_integrations` | Intégrations des effectifs | `effectifs_temps` |
| `effectifs_communication` | Messagerie, appels, listes de diffusion du personnel | `horaires` |

- **Écran** : Superadmin › onglet « Modules et accès » (`/superadmin`), réservé à l'exploitation.
- **Routes** : `GET /api/saas/modules` (catalogue), `PUT /api/saas/tenants/{id}/modules`
  (corps `{ "modules": [...] }`), `GET /api/saas/my-modules` (modules effectifs de la session).
- **Sous-menus visibles** (même onglet) : masque des entrées de menu pour un organisme
  (`GET|PUT /api/saas/tenants/{id}/vues`). **Ce n'est pas un contrôle d'accès** : il dégarnit le
  menu, la frontière de sécurité reste le module.

## 5. Mise en service : l'ordre du paramétrage

Chaque étape suppose les précédentes.

1. Activer les modules (§ 4).
2. Créer les comptes (§ 3.3).
3. Référentiels : unités, corps et titres d'emploi, statuts d'emploi, conventions, natures de
   quart, plages horaires, types de milieu, codes d'absence, types d'heures, jours fériés (§ 6.1).
   Facultatif : aligner les unités sur l'organigramme.
4. Milieux physiques (sites) et leurs preuves de présence (§ 6.2).
5. Périmètres de l'encadrement (§ 3.4).
6. Ressources planifiables, reliées aux comptes (§ 6.4).
7. Postes et leur cycle, puis publication (§ 6.5).
8. Règles des effectifs (§ 6.7) ; paramètres et flux des feuilles de temps (§ 6.9, § 7).
9. Profils de pointage (§ 6.8) ; quotas (§ 6.6).
10. Connecteurs d'intégration (§ 6.10) ; politique de rétention (§ 6.11).
11. Première période : créer, ouvrir la collecte, générer les quarts, publier (§ 6.6).

Un jeu complet peut être semé par l'API pour la formation : `node scripts/semer-urgences-sante.cjs`
(voir [COMPTES-DEMONSTRATION.md](COMPTES-DEMONSTRATION.md)). Le script ne crée pas de périmètres :
les ajouter ensuite (§ 3.4).

## 6. Paramétrage

### 6.1 Référentiels

**Écran** : Urgences-santé › Administration des effectifs › **Référentiels** (`/usreferentiels`).

Un référentiel est une liste **datée** : chaque valeur a une date d'entrée en vigueur, se remplace
par une nouvelle version et se ferme à une date. Le filtre « en vigueur le » montre l'état à une
date donnée ; « Historique » montre les versions d'une valeur.

| Type (code) | Libellé | Attributs propres |
|---|---|---|
| `unites` | Unités organisationnelles | Unité parente |
| `corps_emploi` | Corps d'emploi | — |
| `titres_emploi` | Titres d'emploi | Corps d'emploi |
| `statuts_emploi` | Statuts d'emploi | Comportement |
| `conventions` | Conventions collectives | — |
| `natures_quart` | Natures de quart | Comportement ; compte dans les quotas ; présence préalable rémunérée (minutes) |
| `plages_horaires` | Plages horaires | Début et fin (minutes depuis minuit) |
| `types_milieu` | Types de milieu physique | Comportement |
| `codes_absence` | Codes d'absence | Comportement ; catégorie de durée ; planifiable d'avance ; titres admissibles |
| `types_heure` | Types d'heures | Comportement ; code de paie (PFT) ; majoration (%) |
| `jours_feries` | Jours fériés | — |
| `parametres_poste` | Paramètres de poste | Type de valeur |
| `activites_presence` | Activités de présence | Comportement ; code de paie (PFT) ; refacturable |

Le **comportement** d'une valeur est ce que le code sait en faire (par exemple un type d'heure
`TRAVAILLEE`) ; le code et le libellé appartiennent à l'organisme.

Commandes : « Ajouter une valeur », « Remplacer » (nouvelle version datée), « Fermer » (date de
fermeture), « Historique ». Sur le type « Unités » : **« Aligner sur l'organigramme »** (aperçu du
plan, puis application).

| Geste | Route | Capacité |
|---|---|---|
| Lire | `GET /api/effectifs/referentiels`, `GET /api/effectifs/referentiels/{type}?date=…`, `GET …/{type}/{code}/historique` | module `effectifs` |
| Ajouter | `POST /api/effectifs/referentiels/{type}` | `effectifs.configure` |
| Remplacer | `POST /api/effectifs/referentiels/{type}/{code}/versions` | `effectifs.configure` |
| Fermer | `POST /api/effectifs/referentiels/{type}/{code}/fermer` | `effectifs.configure` |
| Aligner sur l'organigramme | `GET` puis `POST /api/effectifs/liaisons/organisation` | `effectifs.configure` |

### 6.2 Sites : les milieux physiques

**Écran** : Urgences-santé › Administration des effectifs › **Milieux physiques** (`/usmilieux`).

Un milieu (centre opérationnel, point de service…) porte : code, libellé, type de milieu, unité,
adresse, latitude, longitude, rayon, état actif. « **Preuves** » fixe, par milieu, les mécanismes
de preuve de présence exigés au pointage — `GEOREPERAGE`, `CODE_QR_DE_LIEU`, `RESEAU`, `BORNE`,
`SOURCE_EXTERNE` — et les plages où ils s'appliquent.

| Geste | Route | Capacité |
|---|---|---|
| Lire | `GET /api/effectifs/milieux` | module `effectifs` |
| Ajouter / modifier | `POST /api/effectifs/milieux`, `PUT /api/effectifs/milieux/{id}` | `effectifs.manage` |
| Preuves exigées | `PUT /api/effectifs/milieux/{id}/preuves` | `effectifs.manage` |
| Code QR du lieu (à afficher sur place) | `GET /api/effectifs/presence/milieux/{id}/code-qr` — écran Pointage et présence (console) › « Code QR d'un milieu » | `effectifs.manage` |

### 6.3 Quarts : natures et plages

La forme d'un quart se paramètre dans les **référentiels** : `natures_quart` (régulier, ad hoc…) et
`plages_horaires` (jour, soir, nuit). Les quarts eux-mêmes se **génèrent** depuis les postes
publiés à l'ouverture d'une période (§ 6.6) ou se créent ponctuellement (« Quart ad hoc », écran
Gestion des quarts › **Quarts et équipages**, `/usequipages`, avec composition par corps d'emploi
et minimum opérationnel).

| Geste | Route | Capacité |
|---|---|---|
| Quarts d'une période | `GET /api/effectifs/planification/periodes/{id}/equipages` | `effectifs.manage` |
| Quart ad hoc / en lot | `POST /api/effectifs/planification/equipages`, `POST …/equipages/lot` | `effectifs.manage` |
| Ajuster un quart | `PATCH /api/effectifs/planification/equipages/{id}` | `effectifs.manage` |
| Attribuer / libérer une place | `POST /api/effectifs/planification/places/{id}/attribuer`, `…/liberer` | `effectifs.manage` |

### 6.4 Ressources

**Écran** : Gestion des quarts › **Ressources** (`/usressources`) — « Nouvelle ressource » :
matricule, nom, unité, corps d'emploi, titre d'emploi, statut d'emploi, convention collective,
ancienneté, état actif. Le **compte relié** n'est pas un champ de l'écran : il se pose par l'API
(`utilisateurId`), par exemple au chargement initial.

Routes : `GET|POST /api/effectifs/planification/ressources`,
`PUT /api/effectifs/planification/ressources/{id}` (`effectifs.manage`).
L'ancienneté peut aussi être tirée de la BD Corpo (§ 6.10).

### 6.5 Postes

**Écran** : Gestion des quarts › **Postes** (`/uspostes`).

« Nouveau poste » : code, libellé, date de début, **cycle** (semaine par semaine, jour par jour, la
plage travaillée). « Modifier la sélection » change plusieurs postes en lot (y compris remplacer le
cycle). Les postes en brouillon se **publient** (« Publier ») ; les conflits sont listés.

Routes : `GET|POST /api/effectifs/planification/postes`, `PUT …/postes/{id}`,
`POST …/postes/lot`, `POST …/postes/publier` (`effectifs.manage`).

### 6.6 Périodes, comblement et quotas

**Périodes** — Gestion des quarts › **Périodes** (`/usperiodes`) : « Nouvelle période » (nom, unité,
début, fin) ; « Ouvrir la collecte » (fin de collecte des disponibilités) ; « Générer les quarts ».
États : `PREPARATION` → `COLLECTE` (facultatif) → `CONFECTION` → `PUBLIEE` → `CLOSE`.
Routes : `GET|POST /api/effectifs/planification/periodes`, `POST …/periodes/{id}/etat`,
`POST …/periodes/{id}/generer` (`effectifs.manage`).

**Comblement** — Gestion des quarts › **Comblement** (`/uscomblement`) : tuiles « Taux de
comblement », « Quarts sous le minimum », « Jours sous le quota » ; « Combler le quart du … » sur
un quart sous le minimum. Route : `GET /api/effectifs/planification/periodes/{id}/comblement`.

**Quotas** — Gestion des quarts › **Quotas** (`/usquotas`) : « Définir un quota » (unité, plage
horaire, nature de quart, nombre, depuis) et **surcharges temporaires** (valeur, depuis, jusqu'à,
motif). Routes : `GET|POST /api/effectifs/planification/quotas`,
`POST …/quotas/{id}/surcharges` (`effectifs.manage`).

### 6.7 Règles des effectifs

**Écran** : Urgences-santé › Administration des effectifs › **Règles des effectifs** (`/usregles`),
trois vues : **Règles**, **Simulateur**, **Décisions**.

Une règle appartient à un **contexte** : `DISPONIBILITE`, `ABSENCE`, `ATTRIBUTION`, `ECHANGE`,
`POINTAGE`, `FEUILLE_DE_TEMPS`, `HEURES_SUPPLEMENTAIRES`, `CHOIX_AGPH`, `INTERET_REMPLACEMENT`,
`DEPOT_AGPH`, `MODIFICATION_HEURES`. « Nouvelle règle » : clé, portée (`ORGANISME`, `UNITE`,
`CONVENTION`, `STATUT_EMPLOI`, `CORPS_EMPLOI`, `TITRE_EMPLOI`) et son code, conditions (fait,
opérateur, valeur ; groupes « toutes vraies », « au moins une », « aucune »),
action (`ACCEPTER`, `AVERTIR`, `EXIGER_APPROBATION`, `EXCLURE_DU_BASSIN`, `BLOQUER`, `REFUSER`),
priorité, message affiché à l'usager, date d'entrée en vigueur. « **Aperçu** » montre l'impact
avant de publier ; « **Publier** » crée la règle ; une modification est une **nouvelle version**
datée ; « Retirer » la met hors vigueur. Le **Simulateur** évalue une situation (unité, date, faits)
et rend le verdict ; **Décisions** liste les évaluations passées et leurs motifs.

| Geste | Route | Capacité |
|---|---|---|
| Lire les règles, les faits | `GET /api/effectifs/regles?contexte=…`, `GET /api/effectifs/regles/{id}`, `GET /api/effectifs/regles/faits` | module `effectifs_regles` |
| Décisions | `GET /api/effectifs/regles/evaluations?contexte=…` | `effectifs.manage` |
| Aperçu, simulation | `POST /api/effectifs/regles/apercu`, `POST /api/effectifs/regles/simuler` | `effectifs.configure` |
| Publier, versionner, retirer | `POST /api/effectifs/regles`, `POST …/regles/{id}/versions`, `POST …/regles/{id}/retirer` | `effectifs.configure` |

### 6.8 Profils de pointage

**Écran** : Pointage et présence › **Pointage et présence (console)** (`/uspresence`), panneau
« **Profils de pointage** » : code, libellé, mode de déclaration, fenêtre, natures de pause non
payées, présence préalable, portée (organisme ou code). « Poser » enregistre ; « Retirer » supprime.

Routes : `GET /api/effectifs/presence/referentiels-profils`, `GET|PUT /api/effectifs/presence/profils`,
`DELETE /api/effectifs/presence/profils/{code}` (`effectifs.manage` pour écrire).

### 6.9 Paramètres des feuilles de temps

**Écran** : Feuilles de temps › **Feuilles de temps (console)** (`/ustemps`), bouton
« **Paramètres des feuilles** » :

| Paramètre (clé) | Libellé | Bornes |
|---|---|---|
| `seuilJourHeures` | Heures par jour avant les heures supplémentaires | 1–24 |
| `seuilSemaineHeures` | Heures par semaine avant les heures supplémentaires | 1–168 |
| `paliersApprobation` | Paliers d'approbation par défaut | 1–3 |
| `toleranceEcartMinutes` | Tolérance d'écart au planifié (minutes) | 0–240 |
| `toleranceCorroborationMinutes` | Tolérance de corroboration par une source tierce (minutes) | 0–60 |
| `actionNonCorrobore` | Pointage non confirmé par une source tierce | Ignorer / Signaler à qui approuve / Bloquer la soumission |
| `actionSourcesDivergentes` | Deux sources tierces en désaccord | idem |

Routes : `GET /api/effectifs/temps/parametres` (module), `PUT /api/effectifs/temps/parametres`
(`effectifs.configure`).

### 6.10 Intégrations

**Écran** : Urgences-santé › Administration des effectifs › **Intégrations des effectifs**
(`/usintegrations`).

| Connecteur | Sens | Échange |
|---|---|---|
| `RAO` | entrant | Horodatages de la répartition assistée par ordinateur |
| `SAAV` | sortant | Équipages (attitrage des véhicules) |
| `PFT` | sortant | Lots de paie |
| `BD_CORPO` | entrant | Ancienneté (dossier employé) |
| `GRAPH` | sortant | Quarts au calendrier Microsoft |
| `ESB` | sortant | Événements du journal |
| `ENTREPOT` | sortant | Versements du journal à l'entrepôt de données |

« **Configurer** » un connecteur : mode (`SIMULATEUR`, `HTTP` ou `SFTP`), URL, **nom de la
variable** qui contient le jeton ou la clé (jamais le secret lui-même), actif.

**Mode `SFTP` — fichiers de lot** (addenda 06, réponse 3). L'adresse s'écrit
`sftp://compte@hôte[:port]/répertoire?empreinte=SHA256:…`. L'**empreinte de la clé de l'hôte** est
exigée, telle que `ssh-keygen -lf` l'écrit : sans elle, la première connexion ferait confiance à
n'importe quel serveur qui répond à l'adresse. La variable nommée porte la **clé privée** du compte
(PEM PKCS#8). Un connecteur sortant dépose chaque message dans `répertoire/<type>/<clé>.json` (par
exemple `lot-paie/`, `evenement/`), sous un nom temporaire puis renommé : le système d'en face ne
lit jamais un fichier à moitié écrit, et un message déjà déposé n'est pas réécrit. Un connecteur
entrant cueille les lots déposés par l'autre système, chacun une liste JSON, dans
`répertoire/horodatages/` (RAO) ou `répertoire/dossiers/` (BD Corpo) ; les fichiers cachés et les
dépôts en cours (`.x.json.part`) sont ignorés. Un dépôt n'a pas de réponse : un refus du
destinataire revient par un autre lot, pas par l'état `REJETE`. Panneau « **Envois et tirages** » : transmettre
le lot de paie, publier les équipages, mettre à jour les anciennetés, publier les quarts d'une
personne au calendrier, relayer le journal au bus, verser le journal à l'entrepôt, tirer les
horodatages RAO. **Boîte d'envoi** : chaque message avec son état (`EN_ATTENTE`, `ENVOYE`,
`REJETE`, `ECHEC`) ; « Rejouer » un message rejeté ou en échec.

| Geste | Route | Capacité |
|---|---|---|
| Lire les connecteurs | `GET /api/effectifs/integrations/connecteurs` | `effectifs.manage` |
| Configurer | `PUT /api/effectifs/integrations/connecteurs/{code}` | `effectifs.configure` |
| Boîte d'envoi, rejouer | `GET /api/effectifs/integrations/messages`, `POST …/messages/{id}/rejouer` | `effectifs.manage` |
| Envois et tirages | `POST /api/effectifs/integrations/paie`, `…/equipages`, `…/calendriers/{ressourceId}`, `…/relais/{code}`, `…/horodatages/tirer`, `…/anciennetes/{uniteCode}` | `effectifs.manage` |

Les horodatages externes peuvent aussi être importés depuis la console de présence (« Importer des
horodatages externes », `POST /api/effectifs/presence/horodatages`).

### 6.11 Rétention des données

**Écran** : Urgences-santé › Administration des effectifs › **Rétention des effectifs**
(`/usretention`). Par classe de données, une durée de 1 à 30 ans : `HORAIRES` (quarts, places,
échanges), `PRESENCE` (pointages, preuves, horodatages externes), `TEMPS` (feuilles approuvées),
`ABSENCES`, `JOURNAUX` (journal des décisions et évaluations des règles). Sans durée, rien ne se
purge. L'écran montre la date limite et ce que chaque durée purgerait aujourd'hui ;
« **Purger maintenant (N)** » supprime après confirmation (« Supprimer définitivement »). La
politique et chaque purge sont consignées au journal.

Routes : `GET|PUT /api/effectifs/retention`, `POST /api/effectifs/retention/appliquer`
(`effectifs.configure`).

## 7. Flux d'approbation

**Écran** : Feuilles de temps › **Feuilles de temps (console)** (`/ustemps`), bouton « **Flux
d'approbation** ».

Deux flux : **Feuilles de temps** et **Pointages en attente**. Chaque règle est un **palier** :

- une **condition de routage** sur un champ — « Toujours (1) », heures déclarées, heures
  supplémentaires, nombre d'anomalies, jours non corroborés, segments externes ou corrigés, plus
  grand écart au planifié, unité (feuilles) ; écart à l'heure prévue, déclaré après coup, écart de
  lieu ou de preuve, corroboré, unité (pointages) — avec un opérateur `≥`, `≤`, `=`, `≠` et une
  valeur ;
- un **approbateur** : un niveau de périmètre de l'unité (`PERIMETRE`), un rôle (`ROLE`) ou une
  personne désignée (`PERSONNE`).

Un palier dont la condition est fausse n'existe pas pour ce document. **Sans règle**, le flux par
défaut s'applique : le nombre de paliers du paramètre `paliersApprobation`, au niveau `GESTION` de
l'unité. Les règles vivent dans le moteur d'approbation générique de l'application.

Routes : `GET /api/effectifs/temps/flux` (`effectifs.manage`), `POST /api/effectifs/temps/flux`,
`DELETE /api/effectifs/temps/flux/{id}` (`effectifs.configure`).

Les **décisions** suivent ces flux : `POST /api/effectifs/temps/feuilles/{id}/approuver` et
`…/retourner` ; `POST /api/effectifs/presence/pointages/{id}/approuver` et `…/rejeter`.

Les autres décisions de l'encadrement ne passent pas par un flux configurable mais par les **règles**
de leur contexte (§ 6.7) et la capacité `effectifs.manage` : absences
(`POST /api/effectifs/absences/{id}/decisions`), échanges (`POST /api/effectifs/echanges/{id}/valider`
et `…/rejeter`), disponibilités (`POST …/disponibilites/periodes/{periodeId}/ressources/{ressourceId}/approuver`).

## 8. Gabarits de messages et avis

**Ce qui existe.**

- Le module **envoie des avis** lors du comblement des absences : offre d'un quart aux ressources
  admissibles (`quart.offert`) et attribution d'office (`quart.attribue`). Chaque avis envoyé est
  tracé par place et par canal ; il se lit dans la console Absences et comblement (`/usabsences`,
  fiche de l'absence › comblement ; `GET /api/effectifs/absences/{id}/comblement`).
- Le **catalogue des événements** notifiables est commun à la plateforme :
  `GET /api/notifications/evenements` (code, libellé, destinataire, canaux par défaut — courriel,
  texto, poussée — et caractère essentiel).
- Chaque personne règle ses **canaux par événement** : Mon espace › Mon compte, onglet « Mes
  canaux » (`GET|PUT /api/notifications/preferences`). Un événement essentiel garde au moins
  un canal écrit.
- Le **message affiché à l'usager** quand une règle avertit ou refuse est un champ de la règle
  (§ 6.7) : c'est le seul texte de message que l'organisme rédige lui-même dans le module.

**Non disponible.**

- **Gabarits de messages modifiables** (objet, corps, variables) pour les avis d'effectifs : les
  libellés des événements sont fixés dans le code (`Evenements.java`).
- L'écran **Notifications** de la plateforme (annonces à l'organisme, envoi manuel à une personne,
  quota de textos) est rangé dans la section « Enseignement / CONFORMITÉ », gouvernée par le module
  `mifi` : il n'est pas atteignable dans un organisme qui n'a que les modules d'effectifs. Ses
  routes existent (`/api/notifications/annonces`, `POST /api/notifications/envoyer`,
  `/api/notifications/sms/quota`).
- Le **regroupement des avis** (résumé périodique) n'a d'écran que dans le module Santé Québec
  (`/api/notifications/regroupement`).
- Les événements `quart.offert` et `quart.attribue` sont rangés au catalogue sous le module
  « Horaires et planification », et non sous les effectifs : une personne qui règle ses préférences
  ne les trouve pas sous un intitulé d'Urgences-santé.

## 9. Journal des décisions

**Écran** : Urgences-santé › Administration des effectifs › **Journal des décisions**
(`/usjournal`).

Chaque geste du module s'écrit avec sa trace : horodatage, acteur **ou mécanisme** qui l'a posé,
action, objet, état avant et après. Filtres : acteur, action, type d'objet, identifiant d'objet,
origine (`UTILISATEUR` ou `SYSTEME` — décision automatique), du / au (exclu). « Filtrer »
applique ; le détail d'une ligne montre l'avant / après.

**Export** : choisir le format (CSV, Excel XLSX, PDF, JSON) puis « Exporter ». Le fichier est
produit par le serveur **avec les filtres de l'écran** (`journal-effectifs.<format>`). Le CSV
protège chaque cellule contre l'injection de formule ; le PDF omet l'avant / après, le tableur les
garde.

Route : `GET /api/effectifs/journal?acteurId=&origine=&action=&objetType=&objetId=&du=&au=&limite=&format=csv|xlsx|pdf|json`
(`effectifs.manage`). Sans `format`, la réponse est le JSON de l'écran.

Le **journal d'audit** général de la plateforme (connexions, comptes) est distinct : Administration
› onglet « Journal d'audit » (`/api/auth/journal-actions`).

## 10. Rapports et exports

| Rapport ou export | Où | Formats | Route | Capacité |
|---|---|---|---|---|
| **Relevé de temps** (planifié, déclaré, supplémentaires, anomalies) regroupé par unité, personne, type d'heure ou semaine | Feuilles de temps (console) › « Relevés » : « Afficher le relevé » ; format puis « Exporter » | CSV, XLSX, PDF, JSON | `GET /api/effectifs/temps/rapports?debut=&regroupement=unite\|ressource\|typeHeure\|semaine&format=` | `effectifs.manage` |
| **Journal des décisions** | § 9 | CSV, XLSX, PDF, JSON | `GET /api/effectifs/journal?…&format=` | `effectifs.manage` |
| **Lot de paie** (feuilles approuvées codées par type d'heure, empreinte SHA-256) | Feuilles de temps (console) › « Lot de paie » : « Produire le lot », « Télécharger le lot (CSV) » | CSV (fichier composé à l'écran) | `GET /api/effectifs/temps/export-paie?debut=&uniteCode=` | `effectifs.manage` |
| **Audit des localisations** de pointage | Pointage et présence (console) › « Audit des localisations » | Écran, CSV | `GET /api/effectifs/presence/audit-localisations`, `GET …/audit-localisations.csv` | `effectifs.manage` |
| **Indicateurs de comblement** | Gestion des quarts › Comblement (tuiles) | Écran | `GET /api/effectifs/planification/periodes/{id}/comblement` | `effectifs.manage` |
| **Relevés statistiques** paramétrables (colonnes, filtres, regroupements, tri, enregistrés) sur cinq sources : heures par type, journées de temps, pointages, comblement des quarts, absences | **Constructeur de rapports** (`/rapports`) | CSV, XLSX, PDF, JSON | `GET /api/rapports/sources`, `POST /api/rapports/executer`, `POST /api/rapports/exporter`, `GET|POST /api/rapports/enregistres`, `PUT|DELETE /api/rapports/enregistres/{id}`, `POST /api/rapports/enregistres/{id}/executer` et `…/exporter` | `effectifs.manage` (sources d'effectifs) |

Le constructeur de rapports applique le périmètre de l'acteur : un gestionnaire ne compte que ses
unités. Il est rangé dans la section **Comptabilité** du menu (voir § 13).

## 11. Tâches périodiques

Les traitements différés passent par l'**ordonnanceur** de la plateforme : une boucle qui exécute
les tâches échues toutes les `ORDONNANCEUR_INTERVALLE_MS` millisecondes (60 000 par défaut,
propriété `nexu.ordonnanceur.intervalle-ms`).

| Tâche (type) | Planifiée par | Effet |
|---|---|---|
| `effectifs_fin_collecte` | L'ouverture de la collecte d'une période (Périodes › « Ouvrir la collecte ») ; annulée à la publication | À l'échéance, clôt la collecte des disponibilités et lance l'attribution automatique |
| `effectifs_livraison` | Le dépôt d'un message sortant d'intégration (§ 6.10) | Livre le message au destinataire ; retentée sur une panne passagère, abandonnée après trois essais (état `ECHEC`, rejouable à la main) |

- **Suivre et forcer** : Administration › onglet « **Tâches différées** » (`/admin`) ;
  `GET /api/ordonnanceur/taches?statut=&type=`, `POST /api/ordonnanceur/tic` (exécute les tâches
  échues maintenant). Réservé aux rôles `admin` et `superadmin`.
- **Gestes manuels, non planifiés** : la purge de rétention (§ 6.11), le tirage des horodatages
  RAO, la mise à jour des anciennetés BD Corpo, la publication des équipages au SAAV et la
  transmission du lot de paie au PFT (§ 6.10) se déclenchent à l'écran ; **aucune planification
  récurrente** (quotidienne, hebdomadaire) n'est offerte pour eux.

## 12. Index : où trouver chaque réglage

| Réglage | Chemin de menu | Adresse | Route principale | Capacité d'écriture |
|---|---|---|---|---|
| Activation des modules | Superadmin › Modules et accès | `/superadmin` | `PUT /api/saas/tenants/{id}/modules` | superadmin |
| Sous-menus visibles | Superadmin › Modules et accès | `/superadmin` | `PUT /api/saas/tenants/{id}/vues` | superadmin |
| Comptes | Administration › Comptes et rôles | `/admin` | `/api/auth/users` | admin |
| Capacités par rôle | Administration › Rôles et accès | `/admin` | `PUT /api/auth/roles/{code}/capabilities/{capability}` | admin |
| Périmètres | Urgences-santé › Administration des effectifs › Périmètres | `/usperimetres` | `/api/effectifs/perimetres` | `effectifs.configure` |
| Référentiels | … › Administration des effectifs › Référentiels | `/usreferentiels` | `/api/effectifs/referentiels/{type}` | `effectifs.configure` |
| Alignement sur l'organigramme | … › Référentiels (type Unités) | `/usreferentiels` | `/api/effectifs/liaisons/organisation` | `effectifs.configure` |
| Sites (milieux) et preuves | … › Administration des effectifs › Milieux physiques | `/usmilieux` | `/api/effectifs/milieux` | `effectifs.manage` |
| Règles | … › Administration des effectifs › Règles des effectifs | `/usregles` | `/api/effectifs/regles` | `effectifs.configure` |
| Connecteurs | … › Administration des effectifs › Intégrations des effectifs | `/usintegrations` | `/api/effectifs/integrations/connecteurs/{code}` | `effectifs.configure` |
| Rétention | … › Administration des effectifs › Rétention des effectifs | `/usretention` | `/api/effectifs/retention` | `effectifs.configure` |
| Journal | … › Administration des effectifs › Journal des décisions | `/usjournal` | `/api/effectifs/journal` | lecture `effectifs.manage` |
| Ressources | … › Gestion des quarts › Ressources | `/usressources` | `/api/effectifs/planification/ressources` | `effectifs.manage` |
| Postes | … › Gestion des quarts › Postes | `/uspostes` | `/api/effectifs/planification/postes` | `effectifs.manage` |
| Périodes | … › Gestion des quarts › Périodes | `/usperiodes` | `/api/effectifs/planification/periodes` | `effectifs.manage` |
| Quarts ad hoc | … › Gestion des quarts › Quarts et équipages | `/usequipages` | `/api/effectifs/planification/equipages` | `effectifs.manage` |
| Quotas | … › Gestion des quarts › Quotas | `/usquotas` | `/api/effectifs/planification/quotas` | `effectifs.manage` |
| Profils de pointage | … › Pointage et présence › Pointage et présence (console) | `/uspresence` | `/api/effectifs/presence/profils` | `effectifs.manage` |
| Paramètres des feuilles | … › Feuilles de temps › Feuilles de temps (console) | `/ustemps` | `/api/effectifs/temps/parametres` | `effectifs.configure` |
| Flux d'approbation | … › Feuilles de temps › Feuilles de temps (console) | `/ustemps` | `/api/effectifs/temps/flux` | `effectifs.configure` |
| Campagne AGPH (paramètres) | … › Confection AGPH › Confection annuelle (AGPH) | `/usagph` | `PUT /api/effectifs/agph/campagnes/{id}/parametres` | `effectifs.manage` |
| Préférences d'avis | Mon espace › Mon compte | `/account` | `/api/notifications/preferences` | chaque personne |
| Tâches différées | Administration › Tâches différées | `/admin` | `/api/ordonnanceur/taches` | admin |
| Relevés statistiques | Comptabilité › Constructeur de rapports | `/rapports` | `/api/rapports/enregistres` | `effectifs.manage` |

## 13. Limites connues et fonctions non disponibles

| Constat | Effet | Où |
|---|---|---|
| **Gabarits de messages modifiables : non disponible** | L'organisme ne peut pas rédiger le texte des avis d'effectifs | § 8 |
| **Écran Notifications hors d'atteinte** dans un organisme sans le module `mifi` | Pas d'annonce ni d'envoi manuel à l'écran pour Urgences-santé | § 8 |
| **Constructeur de rapports dans la section Comptabilité** | `scripts/semer-urgences-sante.cjs` masque cette section : l'écran n'est pas atteignable dans l'organisme de démonstration sans rétablir la vue | § 10 |
| **Planification récurrente des traitements : non disponible** | Purge, tirage RAO, anciennetés, envois SAAV et PFT sont déclenchés à la main | § 11 |
| **Rapports planifiés ou envoyés par courriel : non disponible** | Un relevé enregistré s'exécute à la demande | § 10 |
| **Menu et capacité ne coïncident pas** pour Périmètres et Rétention | Les entrées s'affichent à `manager` et `director`, mais la **lecture** exige `effectifs.configure` (rôle `admin`) : l'encadrement voit l'entrée et reçoit un refus | § 3.2, § 3.4, § 6.11 |
| **Lier une ressource à un compte : non disponible à l'écran** | Un nouvel employé ne voit ni horaire ni feuille de temps tant que sa ressource n'est pas reliée par l'API (`utilisateurId`) | § 3.3, § 6.4 |
| **Le semoir ne crée aucun périmètre** | `claire.nadeau` et `denis.lavoie` ouvrent des consoles vides dans l'organisme de démonstration tant qu'on ne leur en donne pas | § 3.4, § 5 |
