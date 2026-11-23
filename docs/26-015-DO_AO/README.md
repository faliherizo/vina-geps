# Appel d'offres 26-015-DO — Urgences-santé, Système de gestion des effectifs

> Analyse du 29 septembre 2026. Dépôt le **3 novembre 2026 à 14:00:59** (35 jours).
> Contrat SaaS de 5 ans + 5 options, 1 100 licences, adjudication au prix ajusté le plus bas
> (qualité ≥ 70, K = 30 %).

## Documents

| Document | Contenu |
|---|---|
| [26-015-DO_AO.md](26-015-DO_AO.md) | Transcription intégrale du PDF (199 pages) : Régie, Contrat, Cahier de charges, Formulaire, Questionnaire, Grille, Bordereau |
| [01-EXIGENCES-SOUMISSIONNAIRES.md](01-EXIGENCES-SOUMISSIONNAIRES.md) | Qui peut soumissionner, sous quelle forme, avec quelles pièces ; cas de rejet ; garanties et assurances ; questions de précision |
| [02-BESOINS-ET-ANALYSE-ECARTS.md](02-BESOINS-ET-ANALYSE-ECARTS.md) | Besoins fonctionnels en 15 capacités, modules existants réutilisés, écarts et leurs causes structurelles, effort |
| [03-CONCEPTION-SOLUTION-LOGICIELLE.md](03-CONCEPTION-SOLUTION-LOGICIELLE.md) | Architecture, données paramétrables, moteur de règles, MCD, séquences, états, API, disponibilité, lots, ADR |
| [04-CONCEPTION-UI.md](04-CONCEPTION-UI.md) | Menu, parcours, 18 écrans web et 5 écrans mobiles, composants, accessibilité, essais |
| [05-ROADMAP-IMPLEMENTATION.md](05-ROADMAP-IMPLEMENTATION.md) | Les neuf lots U0 à U8, leur ordre et leurs dépendances |
| [06-MAQUETTES-ET-ECARTS-RESTANTS.md](06-MAQUETTES-ET-ECARTS-RESTANTS.md) | Les maquettes SVG, leur écran hôte, et l'historique des écarts annoncés |
| [07-FEUILLE-DE-TEMPS-PARTAGEE.md](07-FEUILLE-DE-TEMPS-PARTAGEE.md) | Le socle commun de feuille de temps entre modules |
| [08-VERIFICATION-EXIGENCES-FONCTIONNELLES.md](08-VERIFICATION-EXIGENCES-FONCTIONNELLES.md) | Les 90 exigences fonctionnelles vérifiées une à une, et le dispositif qui empêche un jugement de vieillir |
| [09-CHARGE-ET-NIVEAUX-DE-SERVICE.md](09-CHARGE-ET-NIVEAUX-DE-SERVICE.md) | JMeter sur le module des effectifs, couverture utile, SLI/SLO/SLA et verdict sur E-TN-17 |
| [10-GRILLE-EXIGENCES-FONCTIONNELLES.md](10-GRILLE-EXIGENCES-FONCTIONNELLES.md) | **Généré** (`construire-grille-fonctionnelle.py`) — préparation de la soumission : les 90 exigences fonctionnelles avec la réponse à inscrire dans la grille, puis tout ce qui n'est pas conforme à ce jour |
| [12-reponses-addenda/](12-reponses-addenda/01_traitement_des_reponses.md) | **20 nov. 2026** — les réponses d'Urgences-santé (addenda 1 à 7, grille v2) traitées une à une contre le code : écarts, questions sans réponse, stories AD-01 à AD-08, planificateur, conception et scénarios |
| [questions/](questions/README.md) | Les 137 questions à poser à Urgences-santé — validation de la solution livrée (54), exigences à clarifier (50), contrat et régie (33) — et les 28 données à obtenir, chacune avec sa source, notre hypothèse et le moment où la poser |
| [MATRICE-COUVERTURE.md](MATRICE-COUVERTURE.md) | **Généré** — les 144 exigences avec verdict, lot, recette de vérification à l'interface, preuves et écart |

## Régénérer

```bash
python3 docs/26-015-DO_AO/convertir-ao.py          # PDF → 26-015-DO_AO.md + exigences-26-015-DO.json
node    docs/26-015-DO_AO/relever-essais.cjs       # relève du dépôt les essais qui nomment chaque exigence
python3 docs/26-015-DO_AO/construire-couverture.py # → MATRICE-COUVERTURE.md, 04-referentiel-exigences.md, les chiffres ci-dessous
```

Le dernier script échoue si une exigence n'est pas jugée, **si une exigence fonctionnelle n'a pas
sa recette de vérification à l'interface**, ou si une référence d'essai ne désigne plus de fichier.
Un chiffre de ce dossier se cite d'après sa sortie, jamais de mémoire — ce tableau-ci est écrit par
lui, parce qu'un tableau tenu à la main a donné trois fois des chiffres faux.

## Les chiffres

<!-- TABLEAU GENERE : chiffres — ne pas editer a la main -->
| Type | ✅ Acquis | 🟡 Partiel | ⛔ À bâtir | 🔒 Hors code | Total |
|---|---:|---:|---:|---:|---:|
| Essentiels (un « Non » élimine) | 90 | 5 | 0 | 5 | 100 |
| Préférentiels (25 % de la qualité) | 31 | 2 | 0 | 11 | 44 |
| **Total** | 121 | 7 | 0 | 16 | 144 |

| Nature | ✅ Acquis | 🟡 Partiel | ⛔ À bâtir | 🔒 Hors code | Total |
|---|---:|---:|---:|---:|---:|
| Fonctionnelles (un geste les vérifie) | 90 | 0 | 0 | 0 | 90 |
| Technologiques (engagement, exploitation, mesure) | 31 | 7 | 0 | 16 | 54 |
<!-- FIN TABLEAU GENERE : chiffres -->

## Recommandation

1. **Décider d'abord sur E-TN-28 et E-TN-29** : trois implantations **terminées** de la solution
   et trois clients prêts à être joints. Ce sont des critères essentiels ; sans eux, la soumission
   est rejetée quelle que soit la qualité du produit. Aucune ligne de code ne les satisfait.
2. **Ne pas répondre « Oui » à un critère essentiel qui n'est pas démontrable** : l'organisme peut
   exiger une démonstration avec 5 jours de préavis, et l'échec aux essais est un cas de rejet
   automatique (Régie 1.15.01 g-h). 82 critères essentiels sont ouverts ; le dépôt est dans 25
   jours ouvrables.
3. **Transmettre les 13 questions de précision** (document 01, § 9) **avant le 26 octobre 2026**,
   que l'entreprise dépose ou non : leurs réponses éclairent le marché.
4. **Corriger G5 sans attendre** : les routes cœur des horaires ne vérifient pas le périmètre du
   gestionnaire (document 02, § 4). C'est un défaut de sécurité indépendant de l'avis.
5. Si l'avis n'est pas gagnable maintenant, conduire **U1 → U2 → U3 → U6 → U7** comme feuille de
   route produit : ces lots portent le chemin critique, et le segment de temps (U6) débloque à lui
   seul une quinzaine de critères.

## Démonstration

```bash
node scripts/semer-urgences-sante.cjs --api http://localhost:4000
```

Crée un organisme au **contenu des douze planches** (`docs/urgences-sante/maquettes/svg/`) : son
administration, quatre personnes avec leur compte, les trois sites, les trois quarts du jour avec
leur comblement, les postes, les règles et les feuilles de temps de la semaine écoulée. Les
identifiants sont imprimés à la fin. Correspondance planche par planche :
[`../urgences-sante/maquettes/CORRESPONDANCE-MAQUETTES-ECRANS.md`](../urgences-sante/maquettes/CORRESPONDANCE-MAQUETTES-ECRANS.md).

