# Dossier d'analyse et de conception — FX-AO-20260810

**Objet :** Solution logicielle de gestion et de distribution des communications cliniques — Santé Québec Laurentides.  
**Sources analysées :**
- `00_sources/01_devis_descriptif_converti.md`
- `00_sources/02_bordereau_grille_converti.md`
- `00_sources/03` à `07` : addenda 1, 2, 3, contrat de l'addenda 1, bordereau et grille de l'addenda 3 (version à déposer)

## Contenu du dossier
| Dossier | Contenu |
|---|---|
| `00_sources` | Conversion Markdown des deux PDF sources |
| `01_analyse` | Synthèse métier, exigences fonctionnelles, fournisseur, exigences non fonctionnelles et traçabilité |
| `02_architecture` | Architecture de solution, C1-C4, BPMN 2.0, MCD dynamique, séquences, sécurité, déploiement et ADR |
| `03_roadmap_stories` | Roadmap, backlog et analyse individuelle des stories |
| `04_questions_client` | Questions de clarification et décisions attendues |
| `05_maquettes` | Maquettes d'écrans en SVG éditable et PNG |
| `06_gouvernance` | Hypothèses, risques, validation et critères d'acceptation |
| `07_validation_vina_geps` | **Validation du dossier** contre les sources et contre VINA GePS : conformité des 115 critères, écarts, ADR 0074 (module ou projet), feuille de route, stories et maquettes |
| `08_addenda_1_a_3` | **19 nov. 2026** : addenda 1 à 3 et contrat, convertis (`00_sources/03` à `07`), traités question par question ; stories ST-H01 à ST-H05, conception, ADR 0085, planificateur de suivi |

## Démontrer et vérifier

| Quoi | Comment |
|---|---|
| Organisme de démonstration (6 sites, 9 comptes par profil, groupes, alertes déjà envoyées) | `node scripts/semer-fx-ao.cjs --api http://localhost:8095` ; données : `e2e/donnees/fx-ao/demonstration.json` |
| Scénarios e2e, critère par critère, avec leurs données JSON | [docs/SCENARIOS-E2E.md](../SCENARIOS-E2E.md) (généré par `scripts/construire-scenarios-e2e.cjs`) |
| Les 115 critères, leur écran et leurs preuves | `e2e/donnees/fx-ao/exigences.json`, joué par `fx-ao-exigences.spec.ts` |
| Les comportements par l'API (13 scénarios) | `e2e/donnees/fx-ao/comportements.json`, joué par `fx-ao-comportements.spec.ts` |
| Le parcours à l'écran des compléments (copie, LDAPS, relances, note, export) | `fx-ao-parcours.spec.ts` |

## Statut et limites
Ce dossier est une **conception de référence** issue des deux PDF transmis. Il ne constitue ni une réponse fournisseur ni une architecture approuvée par Santé Québec. Les décisions dépendant de l'infrastructure, de la téléphonie, de l'identité, des politiques de sécurité ou des processus cliniques sont identifiées comme à confirmer.

Le PDF du bordereau contient la grille de conformité numérotée de 1 à 115 ainsi que le bordereau de prix. Le devis renvoie à une annexe Excel; seul le PDF fourni est analysé ici. Aucun contenu d'une annexe Excel non transmise n'a été inventé.
