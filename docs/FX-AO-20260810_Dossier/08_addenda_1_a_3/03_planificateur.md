# FX-AO-20260810 — Planificateur du traitement des addenda 1 à 3

> Tenu à jour à chaque étape. Un état ✅ cite sa preuve (essai, fichier, commande). Ouverture des soumissions :
> **7 décembre 2026, 13 h 30** (addenda 1).

**États** : ⬜ à faire · 🔄 en cours · ✅ fait (preuve citée) · ⛔ bloqué (par qui) · 🏢 hors code (qui).

## A. Sources et analyse

| # | Tâche | État | Preuve |
|---|---|---|---|
| A1 | Convertir les 4 PDF et les 3 classeurs en Markdown | ✅ | `00_sources/03` à `07`, script `00_sources/convertir-addenda.py` ; le texte barré est détecté (10.20, critère 99) |
| A2 | Comparer les trois versions de la grille et du bordereau | ✅ | `07_bordereau_grille_addenda_3_converti.md` § 1 : 7, 18, 38, 39, 40, 44, 81, 99 modifiés, 99.1 ajouté ; prix identiques |
| A3 | Traiter chaque question et réponse, chaque clause du contrat | ✅ | [01_traitement_des_addenda.md](01_traitement_des_addenda.md) |
| A4 | Écrire les stories | ✅ | [02_stories.md](02_stories.md) |
| A5 | ADR | ✅ | [ADR 0085](../../adr/0085-addenda-fx-ao-preavis-suivi-facturation.md) |
| A6 | Reporter les changements dans la grille de conformité | ✅ | `07_validation_vina_geps/02_conformite_115_criteres.md`, « Mise à jour du 19 novembre 2026 » : 116 critères, 83 ✅, 2 🟡, 31 📄/🏢 |

## B. Implémentation

| # | Story | État | Preuve |
|---|---|---|---|
| B1 | ST-H01 Minimum facturable | ✅ | `ControleurUtilisateursActifsHttpTest` (minimumFacturable, exportPorteLeFacturable), `alertes-cascades.test.tsx` |
| B2 | ST-H02 Préavis par nature de changement | ✅ | `PreavisDeChangementTest`, `ControleurSoutienHttpTest.maintenanceSelonSaNature`, changeset 0019 (`SchemaLiquibaseTest`, `RetourArriereSchemaTest`) |
| B3 | ST-H03 Suivre sans envoyer, quatre profils | ✅ | `ControleurAlertesHttpTest.suivreSansEnvoyer`, `DecisionAccesTest`, `droits-de-menu.test.ts` ; rôle `expediteur` semé (`demonstration.json`) |
| B4 | ST-H04 Aucun renseignement de santé | ✅ | `ContenuSansRenseignementDeSanteTest`, `alertes.test.tsx` |
| B5 | ST-H05 Politique « communications cliniques » | ✅ | `EngagementsDeServiceTest.CommunicationsCliniques`, `ControleurEngagementsSoutienHttpTest` |
| B6 | Portes : `mvn -o verify`, `tsc`, `vitest`, audit d'accessibilité | ✅ | 20 nov. : `mvn -o verify` BUILD SUCCESS, 1387 essais, « All coverage checks have been met » (JaCoCo ≥ 96 % par classe), normes et architecture vertes ; `tsc` propre ; `vitest` vert ; `audit-accessibilite.js` sans écart. Non rejoués : e2e `interface` et campagne contre le Java |

## C. Proposition et engagements hors code

| # | Tâche | État | Source |
|---|---|---|---|
| C1 | Déposer sur la grille de l'**addenda 3** ; commenter 51, 56 à 61, 64, 69 et 70 comme **sans objet en SaaS** | 🏢 soumission | Addenda 3, Q5 |
| C2 | Nommer hébergement, SMS, appels et notifications comme services technologiques, non comme sous-contrats | 🏢 soumission | Addenda 1, Q2 |
| C3 | Retirer la TGV du registre des risques | ✅ sans objet | Aucune mention de la TGV hors du dossier des addenda (recherche du 20 nov.) |
| C4 | Mettre à jour `CALENDRIER-EVOLUTION-ET-MISES-A-JOUR.md` : 21 et 3 jours ouvrables | ✅ | § 3, étape 4 : tableau des trois natures |
| C5 | Responsable de la sécurité nommé dans les 10 jours de l'adjudication | 🏢 entreprise | 10.18.02 d |
| C6 | Politique de télétravail et d'appareils mobiles | 🏢 entreprise | 10.18.02 c |
| C7 | Ligne sans frais de 7 h à 18 h en semaine ; assistance 24/7 pour les incidents critiques | 🏢 dotation | 10.19.02, critère 101 |
| C8 | Attestation de destruction conforme à l'annexe 10.18.04 C | 🔄 | Le mécanisme existe (`saas/domaine/Destruction` : attestation scellée SHA-256) ; confronter son texte au gabarit de l'annexe C, absent des addenda, et au délai de 60 jours (10.18.04 b) |
| C9 | RACI et ordre de déploiement des six sites | 🏢 plan d'implantation | Addenda 2, Q8 et Q33 |
| C10 | veraPDF (critère 39), Q-ALC (critère 18) : toujours ouverts | ⛔ permission, Apple | Mémoire du 15 nov. |
| C11 | Bacs à sable déployables : poussée au contrat d'Expo + téléphones simulés, veraPDF officiel (ADR 0086) | ✅ poussée · ⛔ veraPDF | `bac-a-sable/` ; `npm test` 23/23 (100 % des lignes) ; `bac-a-sable/campagne.sh` contre le Java : BAC-01 à 07 verts le 20 nov. ; BAC-08/09 attendent le conteneur veraPDF (lancement refusé par le classificateur de permissions) ; travail CI `bacs-a-sable` |

## D. Décisions à prendre

| # | Décision | Recommandation |
|---|---|---|
| D-H2 | Profil d'hébergement : l'addenda 3 (Q5) rend l'installation chez l'établissement facultative | SaaS (profil A) seul ; le profil B reste une option, sans être nécessaire à la conformité |
| D-H3 | Le gestionnaire envoie-t-il ? L'addenda 2 ne le lui donne pas, sans l'interdire | Laisser l'organisme décider, par la surcharge de capacité `alertes.envoyer` du rôle `manager` |
