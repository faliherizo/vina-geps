# 26-015-DO — Planificateur du traitement des réponses (addenda 1 à 7)

> Tenu à jour à chaque étape. Un état ✅ cite sa preuve (essai, fichier, commande). Dépôt : **26 novembre 2026,
> 14 h 00** (addenda 7).

**États** : ⬜ à faire · 🔄 en cours · ✅ fait (preuve citée) · ⛔ bloqué (par qui) · 🏢 hors code (qui).

## A. Sources et analyse

| # | Tâche | État | Preuve |
|---|---|---|---|
| A1 | Convertir les 7 addenda (PDF) et la grille v2 (Excel) en Markdown | ✅ | `questions/reponses/addenda-01.md` à `addenda-07.md`, `grille-criteres-v2.md` ; script `questions/convertir-reponses.py` (barré détecté : 0 caractère, 0 cellule) |
| A2 | Vérifier l'ancien `addenda-01.md` | ✅ | C'était l'addenda 6 : mot pour mot contre le PDF (7 073 mots), remplacé par `addenda-06.md` structuré ; renvois corrigés (fichier 07, `addenda-26-015-DO.json`) |
| A3 | Comparer la grille v2 à l'origine | ✅ | `grille-criteres-v2.md` § 1 : E-TN-9, E-TN-11, E-TN-19, coquille E-GP-15 ; `addenda-26-015-DO.json` repris mot pour mot ; `MATRICE-COUVERTURE.md` et `10-GRILLE…` régénérés |
| A4 | Réparer la génération de la grille | ✅ | `construire-couverture.py` échouait sur develop : trois essais du contexte `horaires` (retiré à la séparation) cités pour E-GN-9 dans `e2e/donnees/urgences-sante/exigences-fonctionnelles.json` ; retirés |
| A5 | Traiter chaque réponse contre le code | ✅ | [01_traitement_des_reponses.md](01_traitement_des_reponses.md) |
| A6 | Questions sans réponse | ✅ | 01 § 4 |
| A7 | Stories | ✅ | [02_stories.md](02_stories.md) |
| A8 | ADR | ✅ | [ADR 0087](../../adr/0087-reponses-26-015-do-faits-seuils-et-optimisation.md) |
| A9 | Feuille de route | ✅ | [05-ROADMAP-IMPLEMENTATION.md](../05-ROADMAP-IMPLEMENTATION.md), section « Réponses du 20 novembre 2026 » |

## B. Implémentation

| # | Story | Branche (commit) | État | Preuve |
|---|---|---|---|---|
| B1 | AD-01 Heures sur 24 h | `features/26-015-DO-AD-01-heures-sur-24h` (5ca6677) | ✅ fusionnée (4c5a198) | `PlanificationDomaineTest.heuresSur24hGlissantes`, `EchangeEffectifsDomaineTest`, BDD `05-echanges.feature` ; [scénarios](05_scenarios/AD-01.md) |
| B2 | AD-02 Seuil par code d'absence | `features/26-015-DO-AD-02-seuil-code-absence` (91e30fb) | ✅ fusionnée (48b3ec8) | `AbsencesEffectifsDomaineTest.seuilLongTerme`, `.periodeEnCours`, `.comblement`, `ControleurAbsencesEffectifsHttpTest`, BDD `04-absences.feature` ; [scénarios](05_scenarios/AD-02.md) |
| B3 | AD-03 HS par période de paie et par convention | `features/26-015-DO-AD-03-hs-periode-paie` (bf048ba) | ✅ fusionnée (8b42e56) | `TempsEffectifsDomaineTest.periodeDePaie`, `.seuilsDeConvention`, `ControleurTempsEffectifsHttpTest.seuilsParConvention`, changeset 0020, vitest `seuils-conventions.test.tsx`, BDD `08-feuilles-de-temps.feature` ; [scénarios](05_scenarios/AD-03.md) |
| B4 | AD-04 Optimisation du budget AGPH | `features/26-015-DO-AD-04-optimisation-budget` (140d86e) | ✅ fusionnée (31111b6) | `OptimisationBudgetAgphTest` (dont le cas où un glouton se trompe), vitest `optimisation-budget-agph.test.tsx`, BDD `06-agph.feature` ; [scénarios](05_scenarios/AD-04.md) |
| B5 | AD-05 Renoncer ; délai échu paramétrable | `features/26-015-DO-AD-05-agph-renoncer` (79b2fca) | ✅ fusionnée (b90afe3) | `AgphDelaiEchuTest`, `ControleurAgphEffectifsHttpTest.renoncerEtDelaiEchu`, changeset 0021, BDD `06-agph.feature` ; [scénarios](05_scenarios/AD-05.md) |
| B6 | AD-06 API d'échange | — | 🔄 | Découpée en trois branches (voir `04_conception.md`) |
| B6a | AD-06a Garde machine et lectures (critères 1, 2 ; 6 en partie) | `features/26-015-DO-AD-06a-api-echanges-lecture` | ✅ | `GardeMachineTest`, `EchangesApiTest`, `ControleurEchangesApiHttpTest` (quarts, correctifs, relevés, isolation, refus), `JetonClientTest.unJetonMachineNestPasUneSession` ; OpenAPI : tag « Échanges », schéma `clientCredentials` |
| B6b | AD-06b Employés et soldes des banques (critères 3, 4) | — | ⬜ | Changeset à venir ; fait `soldeBanqueHeures` |
| B6c | AD-06c Absence déclarée aux opérations ; avis des modifications (critères 3, 5) | — | ⬜ | Événement de webhook sur les quarts |
| B7 | AD-07 Offre en cascade | — | ⬜ | Taille L |
| B8 | AD-08 Démonstration SP-CSN | `features/26-015-DO-AD-08-demo-sp-csn` | ✅ | Aucun code serveur : `convention-sp-csn.json` (9 règles, seuil de période 80 h, seuil 14 jours, 14 écarts avec leur article), `scripts/semer-sp-csn.cjs` ; e2e `urgences-sante-sp-csn.spec.ts` 11/11 contre le Java (chaque règle déclenche et ne déclenche pas) ; BDD `02-quarts-et-attribution.feature` ; [scénarios](05_scenarios/AD-08.md) |

## C. Portes jouées le 20 novembre 2026

Machine très chargée pendant toute la mesure (moyenne de charge de 180 à 450, d'autres projets compilant en
parallèle) : les temps d'attente de 10 s de Playwright ont sauté plusieurs fois sur des écrans **non touchés**.

| Porte | AD-01 à AD-04 (develop 31111b6) | AD-05 (develop b90afe3) |
|---|---|---|
| `mvn -o verify` | ✅ BUILD SUCCESS, 1 408 essais, 0 échec, « All coverage checks have been met » ; BDD 71/71 | ✅ BUILD SUCCESS, 1 411 essais, 0 échec, « All coverage checks have been met » ; BDD 72/72 |
| `tsc --noEmit`, `vitest run` | ✅ 52 fichiers, 360 essais | ✅ 52 fichiers, 360 essais |
| `audit-accessibilite.js` | ✅ aucun manquement | ✅ aucun manquement |
| e2e `--project=interface` (suite complète) | 🟡 49/51 ; portable rejoué ✅ ; **mobile (390 px) ⛔** : `page.goto` puis `locator.click` > 10 s dès le premier écran, deux fois | Audit WCAG seul : tablette ✅ (au second essai), portable ✅ ; mobile et suite complète non rejoués |
| Campagne contre le Java | ✅ `effectifs-agph`, `-absences`, `-echanges`, `-temps`, `-temps-flux` : 8/8 (18 h 25) | 🟡 `effectifs-agph` 1/2 (20 h 42) : l'essai pose sa règle `DEPOT_AGPH` à la date **UTC** du jour, déjà le lendemain à Montréal après 19 h ; la règle n'est pas encore en vigueur. Défaut de l'essai (horloge), indépendant d'AD-05 ; à corriger dans `effectifs-agph.spec.ts` |

**AD-08 (22 novembre, branche avant fusion)** : `mvn -o verify` ✅ BUILD SUCCESS, 1 426 essais, 0 échec, « All coverage
checks have been met », BDD 73/73 — au second passage : le premier a échoué sur `DemarrageTest`
(`SQLITE_IOERR_SHORT_READ` sur `${java.io.tmpdir}/nexu-demarrage-essai.db`, fichier partagé par tous les arbres de travail
qui vérifient en même temps), rejoué avec `JAVA_TOOL_OPTIONS=-Djava.io.tmpdir=<dossier privé>`. `tsc` + `vitest` ✅ 52
fichiers, 360 essais ; `audit-accessibilite.js` ✅ ; campagne Java (ports 4061 / 5361) : `urgences-sante-sp-csn`,
`urgences-sante-comportements`, `fx-ao-comportements`, `urgences-sante-technologie`, `fx-ao-bac-a-sable` : 43 réussis,
1 sauté (bacs à sable non réglés, prévu), 0 échec. Aucun écran touché : la suite `interface` n'a pas été rejouée.

Commandes : section « Vérifier — les portes » de [CLAUDE.md](../../../CLAUDE.md) ; e2e sur les ports 4041 / 5341.

## D. Proposition et hors code

| # | Tâche | État | Source |
|---|---|---|---|
| D1 | Répondre sur la **grille v2** | 🏢 soumission | Addenda 7 |
| D2 | En-tête de la grille : « Canada, Québec » ; hébergeur AWS (`ca-central-1`) | 🏢 soumission | Grille v2, onglet 3 |
| D3 | Lister les fournisseurs de services technologiques (hébergement, SMS, courriel, poussée) | 🏢 soumission | Addenda 4 Q4 |
| D4 | Garantie de soumission de 50 000 $ (annexe 4.01 jointe à l'addenda 4) | 🏢 entreprise | Addenda 4 Q2 |
| D5 | Interprétations déclarées : taux de comblement (Q5), moment du transfert (Q14) | 🏢 soumission | 01 § 1 |
| D6 | Calendrier en phases vers un PMV en 16 semaines | 🏢 plan de projet | Addenda 1 Q2 |
| D7 | Lien MCN d'E-TN-31 | ⛔ Urgences-santé | Absent de l'addenda 6 |
