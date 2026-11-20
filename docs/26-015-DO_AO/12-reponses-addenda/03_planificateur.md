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

| # | Story | Branche | État | Preuve |
|---|---|---|---|---|
| B1 | AD-01 Heures sur 24 h | `features/26-015-DO-AD-01-heures-sur-24h` | ⬜ | |
| B2 | AD-02 Seuil par code d'absence | `features/26-015-DO-AD-02-seuil-code-absence` | ⬜ | |
| B3 | AD-03 Heures supplémentaires par période de paie et par convention | `features/26-015-DO-AD-03-hs-periode-paie` | ⬜ | |
| B4 | AD-04 Optimisation du budget AGPH | `features/26-015-DO-AD-04-optimisation-budget` | ⬜ | |
| B5 | AD-05 Renoncer ; délai échu paramétrable | `features/26-015-DO-AD-05-agph-renoncer` | ⬜ | |
| B6 | AD-06 API d'échange | — | ⬜ | Taille L : après le dépôt |
| B7 | AD-07 Offre en cascade | — | ⬜ | Taille L : après le dépôt |
| B8 | AD-08 Démonstration SP-CSN | — | ⬜ | Après AD-01 et AD-03 |

## C. Portes (à la fin de chaque story)

| Porte | Commande |
|---|---|
| Serveur | `cd backend-java && mvn -o verify` (architecture, normes, JaCoCo ≥ 96 % par classe, suite BDD) |
| Interface | `cd frontend && npx tsc --noEmit && npx vitest run` |
| Accessibilité statique | `node scripts/audit-accessibilite.js` |
| Écrans | `cd e2e && E2E_NO_BACKEND=1 E2E_WEB_PORT=5341 npx playwright test --project=interface` |
| Parcours contre le Java | `E2E_API_PORT=4041 E2E_WEB_PORT=5341 E2E_JAVA_DOSSIER=$TMPDIR/nexu-e2e-26015 backend-java/scripts/campagne-e2e.sh <specs>` |

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
