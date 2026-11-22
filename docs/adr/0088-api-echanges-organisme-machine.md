# ADR 0088 — L'API d'échange de l'organisme : routes machine, jeton standard, soldes reçus

- **Statut** : accepté, le 22 novembre 2026.
- **Décideurs** : équipe VINA.
- **Prolonge** : ADR 0087 (réponses de 26-015-DO) ; AO-5338 #NF-08 (jeton `client_credentials`).
- **Concerne** :
  - `partage/securite/JetonDeSession`, `JetonsMachine` ;
  - `partage/web/GardeMachine` ;
  - `integrations/…/JetonClient`, `ControleurOAuth` ;
  - `effectifs` : `EchangesApi`, `SoldesBanques`, `LectureEchangesApi`, `GestesEchangesApi`, `DepotEchangesApi`, `DepotSoldesBanques`, `ControleurEchangesApi` ;
  - changesets `0022` (soldes) et `0023` (index).
- **Story** : AD-06 (26-015-DO, addenda 5 ; M-01 ; E-TN-7, P-TN-17, E-RF-12).

## Contexte

L'addenda 5 de 26-015-DO renverse l'hypothèse d'intégration. Le fournisseur ne développe aucune interface vers RAO, SAAV, PFT ou
BD Corpo. Il expose une **API standard, documentée et sécurisée**, que l'organisme appelle lui-même pour :
- (a) lire les quarts planifiés et leurs correctifs ;
- (b) être informé des modifications en quasi temps réel ;
- (c) transmettre les employés et les soldes des banques ;
- (d) transmettre les modifications des quarts réels ;
- (e) lire les relevés de présence validés.

Les jetons `client_credentials` étaient déjà émis. Aucune route métier ne les acceptait, et aucun solde n'existait dans le produit.

L'examen a révélé deux défauts préexistants :
1. **Un jeton machine valait session.** Il est signé avec la même clé que le jeton de session, et `JetonDeSession.lire` ne vérifiait
   pas `typ`. Il entrait donc avec l'établissement et sans rôle, ce qui suffisait pour toute route n'exigeant qu'un établissement.
2. **`/api/oauth/token` n'acceptait que du JSON.** La RFC 6749 impose un formulaire et l'authentification `client_secret_basic`.
   Un client OAuth standard (passerelle, iPaaS, bibliothèque) recevait 415.

## Options considérées

1. **Des routes machine dédiées, `/api/echanges/v1`, gardées par le seul jeton d'application** (retenue). Le jeton porte
   l'organisme, et les portées `read` et `write` séparent les lectures des écritures.
2. **Ouvrir les routes existantes de l'interface aux jetons machine.** Leurs formes suivent les écrans, pas un contrat
   versionné. Elles exigent des rôles humains, et chaque évolution d'écran casserait l'intégration de l'organisme.
3. **Pousser vers les systèmes de l'organisme (connecteurs).** L'addenda l'exclut : l'organisme réalise et maintient
   lui-même ses adaptations.
4. **Lire les soldes dans BD Corpo à chaque demande d'absence.** L'organisme transmet les soldes, il ne les expose pas.
   La lecture synchrone rendrait aussi chaque demande dépendante de la disponibilité de BD Corpo.

## Décision

- **Deux frontières qui se tiennent** :
  - `JetonDeSession` refuse un jeton `typ: client`. La constante est partagée avec `JetonClient`.
  - `GardeMachine` refuse toute session, qu'elle vienne d'un jeton ou d'un témoin. Elle exige le jeton machine (401) et la
    portée (403). L'organisme est celui du jeton : l'en-tête `X-Tenant-Id` n'a aucun effet.
- **Jeton standard** : `/api/oauth/token` accepte le formulaire, `client_secret_basic` et `client_secret_post`, et toujours le
  JSON d'origine. La réponse porte `Cache-Control: no-store`. Les refus restent indistincts (`invalid_client`).
- **Lectures** :
  - quarts des seules périodes publiées ou closes, sur une fenêtre de 62 jours au plus ;
  - correctifs du journal des équipages et des places, chacun avec l'état présent de son quart ;
  - relevés : feuilles approuvées ou payées, avec leurs segments actifs.

  Pagination par curseur `horodatage || id`. Le curseur avance même sur un quart non exposé, pour qu'une période en
  confection ne bloque jamais la lecture.
- **Écritures** :
  - un employé se crée ou se met à jour par son matricule, à travers `GestionnaireRessources` : mêmes référentiels et même
    journal qu'à l'écran. Le journal signe `application:<clientId>`.
  - Les soldes se posent banque par banque, en heures décimales exactes. Le code d'une banque est celui de BD Corpo : un
    ensemble ouvert, d'où un format et non un enum.
- **Les règles d'absence voient le solde.** Un code d'absence nomme sa banque (`banque`) et ses heures par jour
  (`heuresParJour`). Quatre faits sont toujours fournis : `soldeBanqueConnu`, `soldeBanqueHeures`, `heuresDemandees` et
  `soldeBanqueApresHeures`. Un fait absent ferait échouer la règle ; sans solde reçu, ces faits valent faux et zéro. Une
  règle `soldeBanqueApresHeures < 0 → AVERTIR` signale une absence qui dépasse le solde, sans rien bloquer d'office.
- **Performance** :
  - Une page de correctifs se lit en trois requêtes, quelle que soit sa taille. La lecture entrée par entrée en faisait
    jusqu'à 1 500 pour 500 entrées.
  - Les curseurs gagnent une borne indexable, `horodatage >= substr(curseur, 1, 24)`. Elle est exacte, car les horodatages
    ont une largeur fixe.
  - Le changeset 0023 indexe les équipages par `(tenantId, date)` et les feuilles par `(tenantId, approuveeLe)`.

## Conséquences

- **Positives** :
  - L'organisme branche ses systèmes avec un client OAuth ordinaire.
  - Le défaut de session est fermé pour toutes les routes, pas seulement pour la nouvelle API.
  - Le contrat est versionné (`v1`) et documenté dans l'OpenAPI (étiquette « Échanges », schéma `clientCredentials`).
- **Négatives** : un client révoqué garde son jeton jusqu'à l'expiration, soit une heure. Une révocation immédiate demanderait
  une liste de révocation consultée à chaque appel.
- **Reste (AD-06c)** : l'absence déclarée aux opérations sur un quart réel (d), et l'avis quasi temps réel des
  modifications (b), par un événement de webhook sur les quarts. La lecture par curseur sert déjà de repli par interrogation.
- **Essais** :
  - `GardeMachineTest`, `JetonClientTest` ;
  - `ControleurOAuthHttpTest` (la classe n'est plus exclue de la couverture) ;
  - `EchangesApiTest`, `LectureEchangesApiTest`, `SoldesBanquesTest`, `ControleurEchangesApiHttpTest` ;
  - `SchemaLiquibaseTest`, `RetourArriereSchemaTest` ;
  - la campagne JMeter `perf/jmeter/campagne-api-echanges.sh`.
