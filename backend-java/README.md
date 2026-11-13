# Backend Java — Nexu VINA

Migration à **contrat HTTP figé** depuis le backend Node, qui reste la référence en production et
n'est pas modifié. Plan complet : [docs/PLAN-MIGRATION-BACKEND-JAVA.md](../docs/PLAN-MIGRATION-BACKEND-JAVA.md).

Java 25 LTS · Spring Boot 4.0.7 · Maven · port **4002** par défaut.

## État

| Module | Endpoints | État | Preuve |
|---|---:|---|---|
| `countries` | 7 | **migré, non routé** | diff à **0 écart** sur 66 échanges |
| `scolaire` | 42 | **porté, non routé** | domaine à 100 % (port fidèle de `scolaire/domaine.ts`, `pilotage.ts`, `importation.ts`), dépôts testés sur SQLite réel au schéma des migrations 0060–0064, garde de module ADR 0013, mêmes messages et codes que le Node |
| `horaires` (L1 horaire clinique, L2 contraintes et optimisation, L3 échanges et remplacement) | 72 | **porté, non routé** | groupes et membres ; périodes, fériés, tâches et cases, blocages, éligibilités, quarts, indisponibilités, couverture ; contraintes (cinq familles, message pour message), cibles d'équité, diagnostic, scénarios (optimiseur déterministe à graine, jamais publié de lui-même) ; échanges entre pairs (segments, scission, validation par l'encadrement ou le téléphoniste) ; listes et recherches (depuis un besoin ou depuis un quart) ; journal. Domaine 100 % ; ~72 routes (répertoire et coordonnées L4, messagerie, téléphonie, exploitation, vues, iCal, décomptes, archivage) restent au Node |
| `ordonnanceur` | — | **porté, boucle éteinte** | tâches différées : planifier sans empiler, tic à verrou atomique, retentatives, exécuteur `avancer_recherche` ; la boucle reste au Node tant qu'il tient la production (un second tic exécuterait chaque tâche deux fois) |
| `annuaire`, `comptabilite`, `exploitation`, `facturation`, `integrations`, `media`, `municipal`, `vitrine` | — | portés partiellement | voir chaque contrôleur et son test de dépôt |
| tous les autres | — | non commencés | — |

`scolaire` porte 42 des 56 routes Node : catalogue et amorçage, motifs, fenêtre et cadence, réglages
d'établissement (verrou compris), suppléants et disponibilités, qualifications, favoris personnels et
d'établissement, proscriptions, rattachements et périmètre, calendrier, lecture des absences, reprise
Scolago des corps d'emploi et des ordres d'appel, statistiques. Ce qui n'est **pas** porté répond
`501` avec son motif plutôt que de faire semblant :

- déclarer / annuler une absence, besoins, réponses, synchronisation GRICS — ils traversent le moteur
  `horaires` et l'ordonnanceur (Node) ; les absences alimentent aussi le dossier et le calendrier ;
- reprise des usagers, candidats ATS et établissements, création d'établissement — ils écrivent dans
  le module de paie (employés, départements), dont le Node tient une copie en mémoire ;
- exports `.xlsx` et export intégral.

Les porter avant leurs modules en ferait une seconde source de vérité. La migration continue module par
module : `horaires`, puis la paie (`payroll`, `people`), puis ce reste scolaire.

## Architecture — quatre couches par contexte (ADR 0025)

Référence : [docs/ARCHITECTURE-JAVA-CQRS.md](../docs/ARCHITECTURE-JAVA-CQRS.md). Depuis le 15
septembre 2026, **tous** les contextes ont la découpe `domaine` / `application/{commandes,
requetes,dto}` / `infrastructure/{web,persistance,configuration}` (270 classes rangées ; `services/`
est le sas transitoire des 14 orchestrations Spring restantes, figé par `ArchitectureTest`).
Quinze contextes sont **conformes CQRS** — leurs contrôleurs ne parlent qu'aux gestionnaires de
l'application, par `partage.web.Bord` — et inscrits dans `CONTEXTES_CQRS`, où la règle ArchUnit
interdit à jamais qu'un contrôleur touche un dépôt : `paie`, `facturation`, `media`, `recherche`,
`pays`, `ia`, `ordonnanceur`, `annuaire`, `approbations`, `soutien`, `superadmin`, `vitrine`,
`securite`, `exploitation`, `absences` (façade des absences unifiées, ADR 0026). Les autres (`saas`, `notifications`, `integrations`, `tresorerie`,
`comptabilite`, `budget`, `approvisionnement`, `municipal`, `demonstration`, `auth`, `rh`,
`scolaire`, `horaires`, `varpay`) sont rangés et attendent leur refonte par cas d'utilisation.

## Validation à la frontière

Les corps de requête sont des records validés par Bean Validation (`CommandesScolaires`,
`CommandesHoraires`) ; les identifiants de chemin et les paramètres portent leurs contraintes
(`@Pattern`). `ErreursValidation` traduit chaque refus dans la forme du Node —
`{"errors": ["personId requis; depuis doit être au format AAAA-MM-JJ"]}`, code 400 — avec les
messages du Node, dans l'ordre de déclaration des champs. Ce que le domaine valide lui-même (les
critères d'une liste, un réglage inconnu) n'est pas répété sur le DTO : une règle, une formulation.

Une différence assumée avec le Node : la validation du corps précède la garde de rôle, qui vit
dans le gestionnaire. Un appelant sans droit qui envoie un corps malformé reçoit 400, pas 403.

## Portes de qualité (`mvn verify`)

JaCoCo : global ≥ 96 % lignes / ≥ 92 % branches ; **chaque classe ≥ 96 % lignes** (le rapport
nomme la classe fautive et son chiffre) ; domaines `scolaire`, `horaires`, `ordonnanceur` à 100 %
lignes. Les exclusions sont nominatives et motivées dans le `pom.xml` — les contrôleurs des
premiers modules, en attente de leur test HTTP (`ControleurScolaireHttpTest`,
`ControleurHorairesHttpTest` montrent la forme : MockMvc seul, dépôts réels sur SQLite, session
posée comme le ferait le filtre). ArchUnit : domaine sans framework, base confinée à
l'infrastructure, aucun cycle entre contextes, aucun état statique mutable.

`countries` est **migré mais pas routé** : deux des trois formats officiels (941 américain,
format malgache) ne sont pas portés. {@code FormatsOfficiels} lève une exception explicite plutôt
que de produire un fichier plausible et faux — un verrou dans le code, pas une note dans un
document. Router le module vers Java avant de les porter serait une régression.

## Vérifier

```bash
mvn test                     # 1 700+ essais, seuils JaCoCo vérifiés par `mvn verify`
mvn spring-boot:run          # démarre sur 4002
backend-java/scripts/campagne-e2e.sh              # la campagne Playwright e2e contre le Java seul
backend-java/scripts/campagne-e2e.sh tests/e2e/contracts.spec.ts
```

**Docker.** `backend-java/Dockerfile` construit l'image (Maven → JRE 25 ; au démarrage, le conteneur
attend la base des migrations du Node sur le volume partagé, la complète avec `completer-schema.py`,
puis sert `/api`). C'est **la pile par défaut** : dans `docker-compose.yml`, le service `backend`
est le Java, et le Node n'y est plus qu'un travail `migrations` qui bâtit le schéma puis s'arrête :

```bash
docker compose up -d --build             # migrations (Node) → backend (Java, :4000) → frontend
docker compose up -d --build backend     # reconstruire le seul Java
```

`docker-compose.node.yml` relance l'ancien Node à côté (hôte : 4002) pour comparer une réponse.

`campagne-e2e.sh` bâtit une base par les migrations du Node puis la complète de ce que le Java
attend en plus (`scripts/completer-schema.py`, à partir de `SchemaEssai`), démarre le Java sur un
port privé (`E2E_API_PORT`, 4020) en **environnement d'essai** et lance Vite (`E2E_WEB_PORT`,
5299) — sans toucher à la pile Docker. Résultat du 15 septembre 2026 (campagne complète après la
mise en couches CQRS) : **523 essais e2e passent contre le Java**, 3 sont sautés par conception
(`mobile-suppleant`, `E2E_MOBILE=1`) et 5 sont hors contrat (`demo-charge-4000` lit un
établissement d'une campagne JMeter passée). Ce que cette campagne a révélé : trois tables que le
Node crée paresseusement (`vitrine_content`, `operator_company`, `billing_invoices`) manquaient
sur une base neuve — elles sont désormais dans `SchemaEssai`, donc dans `completer-schema.py` ; deux
courses côté front que le Java, plus lent que le Node en mémoire, rendait visibles (réponse périmée
du tableau de bord scolaire, fondu des onglets sous l'audit de contraste) et une assertion e2e trop
lâche (`toContainText("149.75")` satisfaite par `1149.75`).
Ce que la campagne précédente avait corrigé côté Java : provisionnement `admin`/`superadmin`
au démarrage, boucle de l'ordonnanceur (`ORDONNANCEUR_INTERVALLE_MS`), `X-Tenant-Id` divergent
du jeton refusé 403, champs MIFI de la fiche semés, bannière de maintenance sur base neuve, taux
de change au contrat du Node, période fermée 409, avis au soutien par le courrier configuré,
apostrophes des en-têtes CSV, jours en minuscules, PDF de facture de l'exploitant (pdfkit →
PDFBox).

### Environnement d'essai (`NEXU_ESSAI=true`)

Ce que le Node fait avec `PAYROLL_DB=:memory:` : `POST /api/payroll/_reset` et
`POST /api/auth/_reset` (403 partout ailleurs), `GET /api/auth/_outbox` (la boîte d'envoi du
courrier journalisé, 404 ailleurs), et l'établissement `default` injecté sur `/api/payroll` et
`/api/ai` quand l'en-tête manque (`FiltreTenantEssai`). Jamais en production : la variable
n'existe pas dans `docker-compose.yml`.

## Comparer au backend Node

```bash
node contract/outils/mandataire.cjs --port 4010 --cible 4000 --etiquette pays-node &
node contract/outils/mandataire.cjs --port 4011 --cible 4002 --etiquette pays-java &
# … mêmes appels contre 4010 puis 4011 …
node contract/outils/diff.cjs pays-node pays-java
```

## Ce que la migration de `countries` a appris

Quatre écarts trouvés par le diff, aucun visible à la relecture du code :

1. **`Content-Type`** — Express écrit `application/json; charset=utf-8`, Spring omettait le
   charset. Cause unique des 66 premiers écarts bloquants : un module entier échoue sur une
   ligne d'en-tête.
2. **Champ perdu en silence** — `remittanceAuthorities[].form` (TPZ-1015.R.14, PD7A) manquait de
   mon modèle, et la désérialisation tolérante l'avalait sans un mot. D'où
   `FAIL_ON_UNKNOWN_PROPERTIES` **activé** : mieux vaut un démarrage qui échoue.
3. **Types devinés** — j'avais lu `notes: List<String>` et `remittanceEngine: String` d'après les
   noms de l'interface TypeScript ; ce sont une chaîne et un booléen. Les données extraites
   font foi, pas les noms.
4. **Clés absentes contre clés nulles** — la source mêle les deux (`ceiling: null` explicite,
   `employeeRate` absent). Aucune politique d'inclusion Jackson ne sait exprimer les deux sur un
   même type : le catalogue **retransmet le JSON d'origine** au lieu de le resérialiser.

Ces quatre-là seront les mêmes sur les 648 endpoints restants. C'est ce qui justifie le harnais.

## Migration — état mesuré (15 septembre 2026)

La mesure vient de `node contract/outils/etat-migration.cjs` (routes montées du Node, lues dans le
routeur Express, contre les `@RequestMapping` Java, de classe ou de méthode ; une méthode qui
répond 501 compte **non portée**). Dernière mesure : **1 208 / 1 208 routes portées (100 %)**,
`/api/payroll` compris (231/231). Chaque module se livre avec domaine à 100 %, dépôt sur SQLite
réel au schéma des migrations Node et contrat HTTP prouvé route par route (`mvn clean verify`
vert, 294 classes d'essai, JaCoCo ≥ 96 % lignes par classe, ≥ 92 % branches, ArchUnit sans cycle).

Le port de `payroll` (179 routes le 14 au soir) s'est fait sur l'architecture de
[ARCHITECTURE-JAVA-CQRS.md](../docs/ARCHITECTURE-JAVA-CQRS.md) (ADR 0025) : `paie/domaine`
(entités, objets-valeur, ports), `paie/application/{commandes,requetes,dto}` (gestionnaires sans
Spring ni JDBC, DTO records), `paie/infrastructure/{web,persistance,configuration}`. Les quatre
contrôleurs historiques (référentiels, champs personnalisés, méta, absences) et les deux routes
publiques par jeton (revue comptable, signature de contrat) ont été basculés sur la couche
`application` en fin de port ; `paie` est inscrit dans `CONTEXTES_CQRS` d'`ArchitectureTest`, et
plus aucun de ses contrôleurs ne peut toucher un dépôt. La spécification OpenAPI est servie par
le Java (`GET /api/payroll/openapi.json`, Swagger UI `GET /api/payroll/swagger`) à partir du
socle extrait du Node par `contract/outils/extraire-openapi.cjs`
(`src/main/resources/openapi-socle.json`) complété des routes montées et des schémas de champs
personnalisés du locataire ; les **contrats DTO** rendus par le Node (clé `x-contrats` du socle,
ADR 0028) sont posés sur leurs routes Spring montées à la place de l'entrée générée — corps nommé
avec défauts et exemple, réponse nommée, codes d'erreur.

Familles `payroll` portées du 14 au 15 septembre : comptes d'employé, feuilles de temps et
entrées de temps (grille unifiée, banque d'heures, cédules), projets et budget (WBS, flux SSE),
contrats de travail et gabarits (versions, clauses, alertes, signature), dépenses (pièces,
grand livre), paies et remises (bulletins, dépôt des salaires, remises CPA-005, déclarations
T4/RL-1), déclarations de revenus (calculateur, pièces, invitation du comptable, exports PDF et
Excel), calendrier, réglages personnels (recherches, classification, postes, tableaux de bord).

Portés le 14 septembre (en plus du tableau du 13) : `annuaire` (synchronisations), `auth` (SSO
Entra/SAML, passkeys, profil, TOTP, code QR identique octet pour octet au Node), `recherche`
(périmètre `paie/domaine/Perimetre`), `media` (redevances au grand livre), les PDF (PDFBox :
incidents, facture, compte municipal, contrat à signer, revue comptable), `demonstration`
(registre, commutateurs, ClamAV démo), et **`varpay` 116/116** :

| Lot `varpay` | Routes | Ce qui est porté |
|---|---:|---|
| organisation | 24 | méta, modules, réglages (facteur 1,75), permissions MIFI, champs personnalisés, établissements, professeurs (échelon, historique, synchronisation depuis les employés, usage) |
| calendrier et catalogue | 16 | journées pédagogiques, événements (salles), fériés du Québec, iCalendar, groupes et cours (reprise de l'historique) |
| affectations | 12 | disponibilités (périmètre du professeur par sa fiche), affectations, suggestions par ancienneté, plan de session, conflits, notifications |
| contrats d'engagement | 18 | `Contrats` (validation groupée, reflet dans « Contrats » via `PontContratsTravail`, idempotent), `Horaire` (créneaux, semaine en bloc, cohérence), `CalendrierContrat` (prévisionnel, import feuille de temps idempotent), `AbsencesMifi` (reflet au dossier, remplaçants à l'heure près), heures par catégorie ; **écart voulu** : le statut est validé (le Node écrivait n'importe quoi) |
| règles de paie | 8 | `ReglesPaie` (profil > échelon > date), catalogue d'activités multilingue |
| moteur de paie | 10 | `CalculPaie` (article 22, #39, correction #29 par nouveau calcul), lot idempotent avec avis, `FichiersPlats` (SAGIR 516 car., SAGIP H750), périodes cumulées |
| rapports, plaintes, exigences | 12 | heures travaillées, périodes enseignées (CSV), absences ; plaintes (journal) ; matrice AIN20260525 (`donnees/exigences-mifi.json`, parité prouvée avec la source TypeScript) |
| feuille de temps | 2 | `FeuilleDeTemps` (horaire ou répartition, pédagogique, férié, séances importées) + Excel 2003 |
| hub d'intégration | 14 | `Connecteurs` (exigences par système, validation et masquage), `ClientConnecteurs` (clé d'API, OAuth 2.0, session SAP, Basic, **OpenID Connect délégué au navigateur**, empreinte épinglée — éprouvé contre un serveur tiers réel), `HubIntegrations` (simulé / direct, offre importée au catalogue), statuts de groupe, échelon SAGIR, import Arrima |

Les hubs MIFI et scolaire partagent `partage/demonstration` (`RegistreDemonstrations`, `Commutateur`).

Premier lot `payroll` (22 routes, paquet `paie`) : `GardePaie` (établissement du jeton, sinon
en-tête, sinon paramètre, sinon `default` — `resolveTenantId`), `ControleurReferentiels`
(niveaux hiérarchiques, titres de poste, départements, organigramme) et `ControleurPersonnel`
(liste paginée et cloisonnée par périmètre, suggestion à trois caractères, disponibilités de la
semaine ISO, fiche masquée, création avec compte de connexion et courriel de remise, correctif
libre-service ou RH, départ et retour, suppression, subordonnés). Le dossier ne dépend d'aucun
autre contexte : trois **ports** dans `paie/domaine` — `ComptesDuPersonnel` (adaptateur dans
`auth`), `EvenementsPersonnel` (webhook `employe.cree`, adaptateur dans `integrations`),
`ProfilsEnseignants` (« professeur = personnel », adaptateur dans `varpay`) — pour que
`paie → auth → integrations → scolaire → paie` ne devienne jamais un cycle. Deuxième lot
(14 routes) : `ControleurChampsPersonnalises` — définitions par portée (globales ou d'un
établissement), valeurs par entité (`?partial=1`), sources dynamiques (port `OptionsDeSource`
pour les établissements, adaptateur dans `varpay`) et le catalogue des **types d'enseignant**
(`TypesEnseignant`, `DepotTypesEnseignant` : semé par établissement, désactivé jamais supprimé).
Troisième lot (10 routes) : `ControleurMetaPaie` — barèmes 2025 (`Baremes`, à la valeur près),
fréquences, catégories de temps, types de contrat, formats d'export, fournisseurs de signature,
modes de compensation (`ModesCompensation`, semés). Quatrième lot (6 routes) :
`ControleurAbsencesDossier` — demandes d'absence bornées au périmètre (refus hors périmètre
consigné à l'audit), libre-service de l'employé, décision par l'approbateur désigné ou l'admin,
reflet au calendrier et, par le port `RefletsAbsence` (adaptateur `varpay`), au module
d'enseignement. `soutien` reste à 100 % avec les ajouts du
14 sept. (pièces jointes analysées par l'antivirus, satisfaction, résumé, dispositif d'avis,
avis à l'auteur) ; le courrier part par `partage/courrier/infrastructure/CourrierSmtp` dès que
`SMTP_HOST` est réglé, sinon par le journal — et le dit.

## Dette de migration au 12 septembre 2026

Les modules Node ajoutés le 12 septembre pour les exigences 27–32, 39 et 49 de l'AO CSSDC —
`soutien` (billets, maintenances, disponibilité), `securite/pareFeu` (pare-feu applicatif, verrou
de connexion), `exploitation/securite` (état de sécurité, résidence), la chaîne d'empreintes de
`auth/audit`, le webhook ATS et le connecteur Espace API GRICS — **n'ont pas encore de port Java**.
Ils sont à porter avec le même contrat (routes, codes, messages), après `horaires` et `paie`.

S'y ajoutent les écarts du cadrage RH livrés côté Node (ÉC-1 à ÉC-7, `docs/rh-scolaire/gpt4-conception/`) :
calendrier scolaire (`scolaire/calendrier`, migration 0068), postes et affectations (`rh/postes`, 0069),
contacts d'urgence (`rh/contactsUrgence`, 0070), versions et expiration des documents (0071), règles RH
(`rh/regles`, 0072), webhooks sortants et cartes d'intégration (`integrations/webhooks`, 0073),
assistant d'import (`POST /importations/apercu` en simulation, correspondance des en-têtes,
`GET /importations/:id/rejets.csv`). Le port Java de la reprise Scolago (`ControleurScolaire`) ne
connaît pas encore l'aperçu ni la correspondance : un client qui les appelle sur Java reçoit 404.

