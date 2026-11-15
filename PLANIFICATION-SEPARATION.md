# Séparer Urgences-santé du produit Nexu VINA — planification

> **Dépôt source** : `~/Projects/freelance-invoicing` (branche `features/urgence-sante`), **non modifié**.
> **Dépôt cible** : celui-ci, `~/project/urgences-sante`.
> **Mesures** : relevées le 13 novembre 2026 sur le code source (graphe des imports Java et TypeScript,
> schéma d'une base migrée). Les chiffres se recalculent avec les commandes de l'annexe.

## 1. Réponse courte

**Oui, c'est possible**, et le découpage existant le rend raisonnable :

- **Côté serveur**, le module est un contexte Java fermé : `effectifs` (286 fichiers, 24 000 lignes)
  et `analytique` (5 fichiers). Il ne dépend directement que de cinq contextes : `partage`,
  `ordonnanceur`, `rapports`, `organisation` et `approbations`.
- **Côté données**, il a ses propres tables : 42 tables `eff_*` et `analytique_*`. Il ne lit du reste
  que `users`, `tenants`, `tenant_quotas` et `retenues`.
- **Côté interface**, il tient dans `effectifs/`, `urgencessante/` et sept pages de libre-service.

Ce qui demande du travail, c'est **le socle** : connexion, organismes, console superadmin, soutien,
sauvegardes et sécurité. Avec l'espace superadmin, la fermeture des dépendances tire **21 contextes
sur 56**, environ 109 000 lignes sur 194 000. Trois d'entre eux, `scolaire`, `paie` et `horaires`,
n'entrent que par **sept arêtes** à couper (§ 4).

## 2. Ce qui est fait (phase 0)

| Livré ici | Contenu |
|---|---|
| Code du module | `backend-java/…/effectifs`, `…/analytique` et leurs essais (43 classes d'essai) ; `frontend/src/effectifs`, `frontend/src/urgencessante`, les sept pages « Mon… / Mes… », `absences/DeclarationEffectifs.tsx`, 11 suites Vitest |
| Essais de bout en bout | `e2e/tests/e2e/effectifs-*`, `urgences-sante-*` (20 fichiers), `e2e/donnees/urgences-sante` |
| Schéma | `schema/module-urgences-sante.sql` : 42 tables propres et 4 tables du socle, exécutable tel quel |
| Documentation | `docs/urgences-sante`, `docs/26-015-DO_AO`, ADR 0014, 0034, 0048, 0063, 0069 et 0071 |
| Infrastructure | `infra/terraform/aws` (sans `.terraform/` ni état), `scripts/semer-urgences-sante.cjs` |
| Inventaire | `INVENTAIRE.md` : fichiers copiés, dépendances d'interface hors module et leur sort |

**Ce dépôt ne compile pas encore seul** : le module importe le socle, qui n'est pas copié. C'est
voulu (« copier juste le module ») ; les phases 1 à 3 le rendent autonome.

## 3. Cible

Un produit autonome, **Java seul** (sans le Node), composé de :

| Couche | Contenu |
|---|---|
| Module | `effectifs`, `analytique` et la communication (messages, appels, diffusion) |
| Socle serveur | `partage`, `ordonnanceur`, `auth` (annuaires Entra), `acces`, `saas` (organismes, modules), `superadmin`, `securite`, `soutien` (engagements 26-015-DO), `exploitation` (sauvegardes, copies par organisme), `notifications`, `organisation`, `approbations`, `rapports`, `pays`, `annuaire`, `integrations` |
| Interface | Coquille réduite (connexion, menu, Mon compte, Soutien, Aide, console superadmin) et pages du module |
| Livraison | Une image Java, une image nginx, Terraform AWS Montréal, CI GitHub Actions |

## 4. Coupures à faire — les seules arêtes qui tirent du hors-sujet

| # | Arête | Fichier(s) | Traitement proposé |
|---|---|---|---|
| C1 | `integrations` → `scolaire` | `ControleurIntegrations`, `DepotWebhooks` | Retirer les routes GRICS/ATS ; garder les webhooks génériques |
| C2 | `securite` → `scolaire` | `ControleurSecurite` | Retirer la partie scolaire du contrôleur |
| C3 | `rapports` → `horaires` | `FaitsQuartsHoraires` | Ne pas copier cette source de faits : les sources s'inscrivent dans un registre, l'ajout ou le retrait ne modifie rien d'autre |
| C4 | `soutien` → `horaires` | `AdaptateurControlePieceClasseur` | Brancher le contrôle de pièce sur l'antivirus partagé |
| C5 | `auth`, `approbations`, `integrations` → `paie` | 6 adaptateurs (`AdaptateurComptesPersonnel`, …) | Extraire un contexte **`personnel`** minimal (employés, comptes) derrière les **mêmes ports** ; ne pas emporter la paie (300 fichiers) |
| C6 | Communication → `horaires` (messagerie) | `ControleurHorairesMessagerie`, dépôts de messagerie et d'appels | Extraire la messagerie dans un contexte **`communication`** |
| C7 | Interface : déclaration d'absence → MIFI / scolaire | `MesAbsences` → `absences/DeclarationScolaire` → `MIFI.tsx`, `mifi/*` | Ne garder que `DeclarationEffectifs` ; retirer les catégories scolaire et enseignement du formulaire |

Les essais de contrat existants (un par port) prouvent que le remplacement d'un adaptateur ne change
pas le comportement : ils se copient avec leur port.

## 5. Phases

| Phase | Travail | Critère de sortie | Estimation |
|---|---|---|---|
| **0** | Inventaire et copie du module | Ce document, `INVENTAIRE.md`, schéma exécutable | **fait** |
| **1** | Socle serveur : copier les 16 contextes du § 3, faire les coupures C1 à C6, `pom.xml` réduit, `ApplicationNexuVina` renommée | `mvn verify` vert : `ArchitectureTest`, `NormesJavaTest`, JaCoCo ≥ 96 % par classe | 4 à 5 j |
| **2** | Interface : coquille réduite (`App.tsx`, `navigation.tsx`, `modulesParPage.ts` limités au module et au socle), composants communs, i18n, styles, coupure C7, communication déplacée | `tsc` propre, Vitest vert, audit d'accessibilité statique sans défaut | 3 j |
| **3** | Console superadmin : catalogue des modules réduit aux codes `effectifs_*` et au socle ; onglets à garder (§ 6, D3) | Essais e2e superadmin verts, audit WCAG de chaque onglet | 1 j |
| **4** | Schéma : **Flyway dans le Java**, baseline V1 = `schema/module-urgences-sante.sql` et les tables du socle ; reprise des données d'un organisme existant par sa **copie par organisme** (E-TN-6) | Base vierge créée par Flyway ; essai de reprise d'une copie réelle | 2 j |
| **5** | Portes de qualité : campagne e2e Java (`campagne-e2e.sh` adapté), audit WCAG 2.2 limité aux écrans du module, plans JMeter d'Urgences-santé, CI | Campagne verte, audit 0 violation, CI bloquante | 2 à 3 j |
| **6** | Livraison : Dockerfile Java + nginx (sans Node), Terraform AWS, déploiement CI par OIDC | Déploiement d'essai joignable, sauvegardes et essai de restauration actifs | 1 à 2 j |
| **7** | Bascule : décider du sort du module dans le dépôt mère (§ 6, D5) | Une seule source de vérité par fichier | ½ j |

**Total estimé : 14 à 17 jours** de travail, dont la phase 1 et la phase 2 sur le chemin critique.

## 6. Décisions à prendre (avant la phase 1)

| # | Question | Recommandation |
|---|---|---|
| D1 | Garder le Node dans le nouveau produit ? | **Non.** Il ne sert plus que les migrations ; Flyway les reprend (phase 4). Un produit neuf sans deuxième pile se maintient mieux |
| D2 | La paie : contexte `personnel` minimal, ou toute la paie ? | **`personnel` minimal** (coupure C5) ; l'AO 26-015-DO verse les feuilles de temps à la paie de l'organisme (PFT), il ne la calcule pas — **renversée le 14 novembre 2026 par l'[ADR 0076](docs/adr/0076-module-de-paie-quebecois.md)** : un contexte `paie` neuf, réduit à la paie, alimenté par les seules feuilles approuvées |
| D3 | La console superadmin : quels onglets ? | Organismes, modules, soutien, exploitation, sécurité, consommation d'avis. **Retirer** la vitrine, les forfaits et la facturation des abonnements, sauf si le produit se vend à d'autres organismes |
| D4 | Garder l'historique Git des fichiers copiés ? | **Oui si la traçabilité compte pour l'AO** : refaire la copie avec `git filter-repo --paths-from-file` sur un clone du dépôt mère (½ j) ; sinon la copie actuelle suffit |
| D5 | Le dépôt mère après la séparation ? | **Retirer le module** du dépôt mère une fois la phase 5 verte, pour éviter deux versions qui divergent ; à défaut, le geler en lecture seule |

## 7. Risques

| Risque | Parade |
|---|---|
| Deux copies du socle qui divergent (correctif fait d'un seul côté) | D5 ; à défaut, une bibliothèque `socle` publiée et partagée (plus lourd, à n'envisager qu'avec un troisième produit) |
| La coquille d'interface (`App.tsx`, 1 100 lignes) mêle tous les modules | La réécrire réduite plutôt que l'élaguer ligne à ligne ; le registre des pages (`urgencessante/index.tsx`) existe déjà |
| Schéma : tables du socle oubliées | Les essais Java montent le schéma par `SchemaEssai` : un `no such table` les fait échouer tout de suite ; Flyway V1 se valide contre eux |
| Perte de preuves de l'AO (grille, matrice de couverture) | Les scripts `construire-couverture.py` et `construire-grille-fonctionnelle.py` sont copiés ; leurs chemins de preuves sont à repointer en phase 5 |
| Couverture qui baisse en retirant des essais transverses | Recompter par classe à chaque phase (porte JaCoCo) ; un essai transverse retiré se remplace par un essai du module |

## 8. État de la séparation — 13 novembre 2026

Décisions appliquées : **D1** Java seul (Liquibase plutôt que Flyway : il était déjà en place),
**D2** contexte `personnel` minimal, **D3** superadmin réduit (vitrine et demandes d'abonnement
retirées), **D4** sans historique Git. **D5** n'est pas tranchée : le dépôt d'origine n'a pas été touché.

### Ce qui est fait

| Couche | Résultat |
|---|---|
| Serveur | 21 contextes (`habilitations` ajouté pour l'écran Accès) ; coupures C1 à C6 faites ; `communication` extraite d'`horaires` (`/api/communication`) ; catalogue des modules et des vues réduit au module et au socle ; forfaits réécrits |
| Schéma | `0001-schema-urgences-sante` : 134 tables, créées par Liquibase sur base vierge, constatées sur une base reprise |
| Routes ajoutées | `POST /api/effectifs/absences` (la façade `/api/absences` n'a pas suivi), `GET /api/personnel/employes[/suggestions\|/{id}]`, OpenAPI sous `/api/integrations/{swagger,openapi.json}` (générée des routes montées) |
| Interface | Coquille réduite, accueil propre au produit, guide en 12 sections, coupure C7 (formulaire d'absence « effectifs » seul, et déclaration pour une ressource dans la console), communication rebranchée, 168 fichiers non atteints retirés |
| Essais | Voir « Mesures » ci-dessous |
| Livraison | `docker-compose.yml` (Java + nginx), Dockerfile sans Node, CI GitHub Actions bloquante |

### Mesures (13 novembre 2026)

| Porte | Résultat |
|---|---|
| `mvn verify` (ArchUnit, normes Java, JaCoCo ≥ 96 % par classe) | **1131 essais, 0 échec**, porte de couverture tenue |
| Vitest | **35 fichiers, 237 essais**, verts |
| Accessibilité statique (`scripts/audit-accessibilite.js`) | 145 fichiers, **aucun manquement** |
| Playwright « interface » (axe-core WCAG 2.2 AA, géométrie à 390/768/1280/1920 px) | **43/43** |
| Playwright « e2e » contre le Java, base vierge bâtie par Liquibase | **104 réussis, 0 échec** (2 sautés : la fumée de déploiement, qui exige `E2E_BASE_URL`) |
| JMeter — parcours employé, gestionnaire, sonde (20 usagers, 60 s) | **0 erreur serveur**, ~18 req/s, p95 ≤ 10 ms, objectifs tenus |
| JMeter — balayage effectifs (154 routes), communication (35), personnel (4) | **0 erreur serveur**, objectifs tenus ; couverture utile 39 %, 49 %, 75 % |

La couverture utile du balayage est basse pour une raison connue : la spécification ne fournit un
corps d'exemple qu'à 9 routes, les autres écritures partent avec un corps générique que la
validation refuse (422) ; 4 identifiants restent sans valeur semée (remplacement long terme,
échange, message ESB, règle de flux). Les lectures, elles, répondent presque toutes en 2xx.
Suite : décrire les corps des écritures du module dans `openapi-socle.json`.

### Défauts trouvés en séparant

- `DocumentPdf.octets()` levait `NullPointerException` à la seconde lecture ou après fermeture.
- La page publique `/invitation/…` tombait sur la vitrine (nginx), au lieu de l'application.
- Un gestionnaire ne pouvait déclarer d'absence pour une ressource que par l'écran Absences unifié :
  le geste vit maintenant dans la console « Absences et comblement ».
- `GET /api/health` vivait dans le contexte facturation, non copié : la sonde du conteneur et la
  campagne attendaient en vain. Elle est dans `partage/web` (`ControleurSante`).
- Une base neuve restait en journal « delete » (c'est le Node qui posait WAL) : la copie par
  organisme qui suit la sauvegarde bloquait les requêtes (`SQLITE_BUSY`, puis des 500 en cascade).
  La source de données pose `journal_mode=WAL` ; `DemarrageTest` le vérifie.
- `GET /api/saas/my-modules` rendait tous les modules à un compte global sans organisme, alors que
  l'interface attend un ensemble vide : le superadmin voyait le module et atterrissait sur un écran
  interdit, faute d'un repli vers une page permise. Les deux sont corrigés et éprouvés.
- Le constructeur de rapports (`/rapports`), qui porte les exigences E-RF-8 et P-RF-1, avait été
  retiré avec la section Comptabilité : il est rétabli sous « Tableau de bord ».
- La déclaration d'absence ne rendait pas le bilan de comblement que la façade d'origine rendait.
- Le changelog Liquibase n'était éprouvé par aucun essai Java : `SchemaLiquibaseTest` le joue sur
  une base vierge et sur une base reprise.

### Dettes — résolues le 13 novembre 2026

| Dette | Ce qui a été fait |
|---|---|
| Répertoire du personnel en lecture seule | `POST /api/personnel/employes` et `PUT /api/personnel/employes/{id}` (capacités `employee.create` / `employee.update`), cas d'utilisation `GestionnairePersonnes`, essais sur le **schéma Liquibase** (les colonnes de paie obligatoires reçoivent des valeurs neutres). La partie « aviser une personne par son nom » de `notifications-evenements` est rétablie. Une ressource d'effectifs reliée à un compte inscrit désormais sa personne : port `partage.personnel.FichesPersonnelPort` appelé par `GestionnaireRessources` (création et modification), implémenté par `personnel` (`InscriptionDepuisEffectifs`) — fiche créée, compte rattaché, idempotent, borné à l'organisme. Le port vit dans `partage` : dans `effectifs`, il fermait le cycle `approbations → personnel → effectifs → approbations` |
| Démonstrations d'intégration | Contexte `demonstration` repris : Entra, SAML, OAuth, webhooks, Twilio, Telnyx, clamd (sans GRICS, ATS, MIFI). Les codes deviennent l'enum `Demontrable`, les statuts Twilio/Telnyx des enums : le plafond de normes du contexte reste à 7. Le registre `Demonstrations` vit dans `infrastructure/persistance` (adaptateur du port `RegistreDemonstrations`), pas dans le sas `services`. Parcours e2e rétablis : `integrations-{entra,saml,oauth,webhooks}` |
| `ControleurMessagerie` hors CQRS | Ports `LectureMessageriePort` / `EcritureMessageriePort`, `LectureMessagerie` et `GestionnaireMessagerie` ; `DepotMessagerie` ne fait plus que ranger ; refus typés (`RefusCommunication`) au lieu du statut déduit du texte ; `communication` est dans `CONTEXTES_CQRS`. Les routes de la messagerie passent la garde du module `effectifs_communication` : un module coupé coupe l'API, pas seulement le menu |
| Acheminement par défaut | Réglage par unité : table `msg_acheminement_unites` (changeset `0002`), `GET` / `PUT` / `DELETE /api/communication/acheminement/unites[/{uniteId}]` (réglage sous `effectifs.manage`), canaux et modes en enums, section « Acheminement des avis par unité » sur la page Messagerie du personnel. Une unité sans réglage suit le défaut (courriel, sonnerie) |
| `rapports` : sources d'autres produits | Seules les 5 sources d'effectifs restent ; le moteur ne lit plus de tables, seulement des fournisseurs de faits (`MoteurRapports(List<FournisseurFaits>)`) ; branches « source de table » retirées |
| Règles de paie semées | `DepotLocataires` ne sème plus `varpay_pay_rules` ; la table est retirée (changeset `0002`) |
| Tarification et facturation SaaS (D3) | `DepotTarification`, routes `/api/saas/pricing`, contexte de facturation d'`exploitation` (`/api/billing/*`, PDF de facture), onglet « Facturation » du superadmin et `PricingManager` retirés ; tables `pricing_plans`, `billing_invoices`, `operator_company` retirées (changeset `0002`) ; 42 identifiants d'essai gelés retirés de `docs/refonte-visuelle/testids.txt` |
| JMeter : couverture utile | `perf/jmeter/corps-balayage.json` : un corps valide pour 20 écritures sans exemple de contrat (29 corps au lieu de 9), colonnes de référentiel et fiche semées par `preparer.cjs` |

### Mesures après résolution des dettes (13 novembre 2026, soir)

| Porte | Avant | Après |
|---|---|---|
| `mvn verify` (ArchUnit avec `communication` en CQRS, normes Java, JaCoCo ≥ 96 % par classe) | 1131 essais | **1133 essais, 0 échec**, porte de couverture tenue ; plafonds abaissés : `exploitation` 9 → 5, `saas` 16 → 10 |
| Vitest | 237 | **241** (36 fichiers), verts |
| Accessibilité statique | aucun manquement | **aucun manquement** |
| Playwright « interface » | 43/43 | **44/44** (état « acheminement : unité choisie » ajouté à l'audit) |
| Playwright « e2e » contre le Java | 104 réussis, 2 sautés | **111 réussis, 0 échec, 2 sautés** (8,4 min). Une seconde campagne, sur une machine chargée (11,9 min), a vu `effectifs-absences` dépasser 240 s pendant l'analyse axe ; rejoué seul, il passe en 19 s |
| JMeter — parcours et sonde | 0 erreur | **0 erreur**, p95 ≤ 7 ms |
| JMeter — balayage effectifs (`-Jduree=240`) | 154 routes, couverture utile 39 % | **154 routes, 0 erreur, couverture utile 43 %** |
| JMeter — balayage communication | 35 routes, 49 % | **38 routes, 0 erreur, couverture utile 61 %** |
| JMeter — balayage personnel | 4 routes, 75 % | **6 routes, 0 erreur, 100 %** |

La couverture utile reste limitée par deux causes, nommées par le rapport : la plupart des 89
écritures des effectifs n'ont pas encore de corps, et le plan enchaîne des gestes destructeurs sur
l'identifiant semé (archiver la conversation, supprimer la liste) avant ceux qui en ont besoin.
Suite : un corps par écriture des effectifs dans `corps-balayage.json`, et un identifiant semé
par geste destructeur.

### Mesures après la seconde passe (13 novembre 2026, nuit)

| Porte | Résultat |
|---|---|
| `mvn verify` | **1136 essais, 0 échec**, couverture ≥ 96 % par classe, aucun cycle entre contextes |
| Playwright « e2e » contre le Java | **111 réussis, 0 échec, 2 sautés** (7,8 min) |
| JMeter — effectifs (durée calculée) | 154 routes, **0 erreur**, couverture utile **51 %** (43 % avant) |
| JMeter — communication | 38 routes, **0 erreur**, couverture utile **71 %** (61 % avant) |
| JMeter — personnel, parcours, sonde | **0 erreur**, objectifs tenus ; pool sans épuisement |

Ce qui bornait alors la couverture utile des effectifs (4 identifiants non semés, corps AGPH,
intégrations et disponibilités) est traité dans la troisième passe ci-dessous. Vitest et l'audit
d'interface n'ont pas été rejoués : l'interface n'a pas changé depuis leur passage vert (241 essais,
44/44).

### Couverture JMeter — troisième passe (13 novembre 2026)

L'amorce (`preparer.cjs`, `semerComplements`) sème désormais l'état que chaque essai HTTP Java
monte avant d'appeler sa route : une semaine publiée **à venir** et une partenaire pour l'échange
(cible tirée de `…/places/{id}/demandables`), un remplacement long terme sur poste vacant, les
connecteurs d'intégration actifs (PFT, SAAV, GRAPH, ESB, ENTREPOT, RAO et BD_CORPO avec leur jeu
d'essai) puis un message ESB, une règle de flux qui ne s'applique jamais, une période en collecte
(les disponibilités s'y déposent : `{periodeId}` → `${periodeCollecteId}`), la campagne AGPH ouverte
au dépôt. Les **4 identifiants manquants sont semés** ; 11 corps de plus ; `demarrer` et `reviser`
se jouent après les dépôts ; le code de relais semé était en minuscules (`esb`, refusé).

| Balayage | Avant | Après |
|---|---|---|
| Effectifs (154 routes) | 51 % en 2xx | **59 %**, 0 erreur |
| Communication (38) | 71 % | 71 %, 0 erreur |
| Personnel (6), parcours, sonde | 100 % / 83–100 % | inchangé, 0 erreur |

Ce qui reste refusé tient à la **machine à états**, pas aux corps : une transition ne réussit
qu'une fois par entité (ouvrir, démarrer, clore une campagne ; accepter, valider un échange), un
second échange sur le même quart est un conflit, la paie exige une feuille approuvée, et les trois
jeux semés relient un seul compte à des ressources de trois unités — les disponibilités ne passent
que pour le jeu de son unité. Mesurer ces gestes demanderait une entité neuve par itération.

### Défauts trouvés en résolvant les dettes

- **Renommer une liste de distribution vers un nom déjà pris répondait 500** (contrainte d'unicité
  de la base) : la création refusait le doublon en 409, la modification ne le vérifiait pas. Elle le
  vérifie ; c'est l'ordre corrigé du balayage qui l'a révélé.
- **Plans de balayage** : les gestes destructeurs (suppression, archivage, clôture…) se jouent
  désormais après les autres ; la durée du groupe se calcule du nombre de requêtes
  (`duree_balayage`, surchargeable) ; un identifiant vide d'un jeu semé prend la valeur d'un autre
  jeu (les chemins `/absences//annuler` mesuraient un 405) ; 14 corps de plus dans
  `corps-balayage.json`.

- **Épuisement du pool de connexions dans la messagerie.** `DepotMessagerie` lançait une requête
  depuis le lecteur d'une autre (participants d'une conversation, pièces d'un message) : la
  première connexion restait tenue pendant que la seconde en attendait une. Avec le pool de 4,
  quatre lectures simultanées le vidaient, et chaque requête attendait 30 s (balayage JMeter :
  16 % d'erreurs, connexion à 24 s). Les lectures sont désormais séquentielles, les pièces lues en
  une requête.
- L'onglet « Consommation » du superadmin s'effondrait (« Cet écran n'a pas pu s'afficher ») sur une
  réponse non bouchonnée dans l'essai `superadmin-menus` : l'essai la bouchonne et vérifie le tableau.
- Les plans de balayage enchaînent les lectures puis les écritures, à une seconde de réflexion :
  à 60 s, les écritures des effectifs ne sont jamais atteintes. Le plan calcule désormais sa durée (`duree_balayage`).

## Annexe — recalculer les mesures

```bash
# Graphe des contextes Java et fermeture transitive (à lancer dans le dépôt source)
cd backend-java/src/main/java/ca/vina/nexu
grep -rhoE "^import ca\.vina\.nexu\.[a-z]+" effectifs | sort | uniq -c

# Tables lues ou écrites par le module
grep -rhoE "\beff_[a-z_]+\b" backend-java/src/main/java/ca/vina/nexu/effectifs | sort -u
```
