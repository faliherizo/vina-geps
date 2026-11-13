# Résultats de performance — local — 2026-11-13 17:03

- **Fichier** : `resultats/local/20261113-120209/endpoints-effectifs.jtl`
- **Environnement** : `local` (http://localhost:4020, 20 utilisateurs, montée 10 s, durée 60 s, réflexion 1000 ms, écritures true)
- **SLA** : p95 < 2000 ms pour 2500 utilisateurs (Devis Santé Québec 26-432315-1226, exigences 8.1–8.2 et annexe C 10.1.1 ; AO-5338 NF)

## Verdict : ✅ objectifs tenus

## Couverture utile

**50 des 60 requêtes (83 %) répondent 2xx.** Les autres sont mesurées sur un refus : un identifiant de chemin absent (404), un corps que la validation refuse (422) ou un conflit (409). Leur latence ne dit rien du travail réel.

| Requête mesurée sur un refus | Code dominant |
|---|---|
| `GET /api/effectifs/absences/{id}` | 404 |
| `GET /api/effectifs/absences/{id}/comblement` | 404 |
| `GET /api/effectifs/absences/{id}/impact` | 404 |
| `GET /api/effectifs/agph/remplacements/{id}` | 404 |
| `GET /api/effectifs/agph/remplacements/{id}/interesses` | 404 |
| `GET /api/effectifs/echanges/{id}` | 404 |
| `GET /api/effectifs/presence/milieux/{id}/code-qr` | 422 |
| `GET /api/effectifs/presence/pointages/{id}` | 404 |
| `GET /api/effectifs/regles/{id}` | 404 |
| `GET /api/effectifs/temps/export-paie` | 422 |

Pour les rendre utiles : semer l'entité qui manque (`preparer.cjs`) ou donner au plan un corps que la validation accepte (`openapiContrats.ts`).

## Total

| Requêtes | Erreurs | Débit | p50 | p95 | p99 | max |
|---:|---:|---:|---:|---:|---:|---:|
| 1087 | 0 (0.00 %) | 17.4 req/s | 4 ms | 14 ms | 50 ms | 135 ms |

## Par requête (SLI mesurés, objectif de la classe)

| Requête | Classe | N | Erreurs | p50 | p95 | p99 | SLO p95 | Verdict |
|---|---|---:|---:|---:|---:|---:|---:|---|
| `POST /api/auth/login` | connexion | 1 | 0.0 % | 135 ms | 135 ms | 135 ms | 800 ms | ✅ |
| `GET /api/effectifs/configuration/paquet` | lecture | 20 | 0.0 % | 9 ms | 35 ms | 79 ms | 500 ms | ✅ |
| `GET /api/effectifs/liaisons/organisation` | lecture | 20 | 0.0 % | 3 ms | 27 ms | 36 ms | 500 ms | ✅ |
| `GET /api/effectifs/agph/campagnes/{id}/disponibilites` | lecture | 20 | 0.0 % | 6 ms | 23 ms | 35 ms | 500 ms | ✅ |
| `GET /api/effectifs/agph/remplacements/{id}` | lecture | 20 | 0.0 % | 7 ms | 23 ms | 94 ms | 500 ms | ✅ |
| `GET /api/effectifs/agph/campagnes/{id}` | lecture | 20 | 0.0 % | 6 ms | 20 ms | 36 ms | 500 ms | ✅ |
| `GET /api/effectifs/journal` | lecture | 20 | 0.0 % | 4 ms | 18 ms | 58 ms | 500 ms | ✅ |
| `GET /api/effectifs/absences` | lecture | 20 | 0.0 % | 7 ms | 17 ms | 28 ms | 500 ms | ✅ |
| `GET /api/effectifs/absences/{id}` | lecture | 20 | 0.0 % | 8 ms | 17 ms | 70 ms | 500 ms | ✅ |
| `GET /api/effectifs/agph/campagnes` | lecture | 20 | 0.0 % | 6 ms | 17 ms | 25 ms | 500 ms | ✅ |
| `GET /api/effectifs/agph/campagnes/{id}/postes-disponibles` | lecture | 20 | 0.0 % | 8 ms | 17 ms | 32 ms | 500 ms | ✅ |
| `GET /api/effectifs/agph/remplacements` | lecture | 20 | 0.0 % | 6 ms | 16 ms | 100 ms | 500 ms | ✅ |
| `GET /api/effectifs/agph/remplacements/{id}/interesses` | lecture | 20 | 0.0 % | 6 ms | 16 ms | 104 ms | 500 ms | ✅ |
| `GET /api/effectifs/integrations/connecteurs` | lecture | 20 | 0.0 % | 5 ms | 16 ms | 50 ms | 500 ms | ✅ |
| `GET /api/effectifs/absences/{id}/impact` | lecture | 20 | 0.0 % | 6 ms | 15 ms | 16 ms | 500 ms | ✅ |
| `GET /api/effectifs/agph/campagnes/{id}/disponibilites/moi` | lecture | 20 | 0.0 % | 6 ms | 15 ms | 56 ms | 500 ms | ✅ |
| `GET /api/effectifs/absences/{id}/comblement` | lecture | 20 | 0.0 % | 7 ms | 14 ms | 15 ms | 500 ms | ✅ |
| `GET /api/effectifs/disponibilites/periodes/{periodeId}` | lecture | 20 | 0.0 % | 7 ms | 14 ms | 57 ms | 500 ms | ✅ |
| `GET /api/effectifs/disponibilites/moi` | lecture | 20 | 0.0 % | 6 ms | 13 ms | 58 ms | 500 ms | ✅ |
| `GET /api/effectifs/disponibilites/periodes/{periodeId}/attribution-automatique` | lecture | 20 | 0.0 % | 5 ms | 12 ms | 13 ms | 500 ms | ✅ |
| `GET /api/effectifs/disponibilites/places/{placeId}/candidats` | lecture | 20 | 0.0 % | 5 ms | 10 ms | 21 ms | 500 ms | ✅ |
| `GET /api/effectifs/presence/audit-localisations` | lecture | 20 | 0.0 % | 3 ms | 10 ms | 31 ms | 500 ms | ✅ |
| `GET /api/effectifs/disponibilites/periodes/{periodeId}/ressources/{ressourceId}` | lecture | 20 | 0.0 % | 5 ms | 9 ms | 11 ms | 500 ms | ✅ |
| `GET /api/effectifs/echanges/moi` | lecture | 20 | 0.0 % | 4 ms | 9 ms | 11 ms | 500 ms | ✅ |
| `GET /api/effectifs/echanges` | lecture | 20 | 0.0 % | 4 ms | 8 ms | 8 ms | 500 ms | ✅ |
| `GET /api/effectifs/echanges/places/{placeId}/demandables` | lecture | 20 | 0.0 % | 5 ms | 8 ms | 13 ms | 500 ms | ✅ |
| `GET /api/effectifs/planification/periodes/{id}/comblement` | lecture | 20 | 0.0 % | 5 ms | 8 ms | 9 ms | 500 ms | ✅ |
| `GET /api/effectifs/presence/audit-localisations.csv` | rapport | 20 | 0.0 % | 3 ms | 8 ms | 48 ms | 2000 ms | ✅ |
| `GET /api/effectifs/echanges/{id}` | lecture | 20 | 0.0 % | 4 ms | 7 ms | 8 ms | 500 ms | ✅ |
| `GET /api/effectifs/integrations/messages` | lecture | 20 | 0.0 % | 3 ms | 7 ms | 27 ms | 500 ms | ✅ |
| `GET /api/effectifs/presence/pointages` | lecture | 20 | 0.0 % | 2 ms | 7 ms | 12 ms | 500 ms | ✅ |
| `GET /api/effectifs/echanges/ressources/{id}/quarts` | lecture | 20 | 0.0 % | 4 ms | 6 ms | 6 ms | 500 ms | ✅ |
| `GET /api/effectifs/ma-ressource` | lecture | 20 | 0.0 % | 3 ms | 6 ms | 21 ms | 500 ms | ✅ |
| `GET /api/effectifs/planification/periodes/{id}/equipages` | lecture | 20 | 0.0 % | 4 ms | 6 ms | 7 ms | 500 ms | ✅ |
| `GET /api/effectifs/presence/milieux/{id}/code-qr` | lecture | 20 | 0.0 % | 3 ms | 6 ms | 29 ms | 500 ms | ✅ |
| `GET /api/effectifs/presence/moi` | lecture | 20 | 0.0 % | 4 ms | 6 ms | 13 ms | 500 ms | ✅ |
| `GET /api/effectifs/presence/mon-profil` | lecture | 20 | 0.0 % | 3 ms | 6 ms | 29 ms | 500 ms | ✅ |
| `GET /api/effectifs/retention` | lecture | 7 | 0.0 % | 2 ms | 6 ms | 6 ms | 500 ms | ✅ |
| `GET /api/effectifs/milieux` | lecture | 20 | 0.0 % | 2 ms | 5 ms | 6 ms | 500 ms | ✅ |
| `GET /api/effectifs/planification/ressources` | lecture | 20 | 0.0 % | 2 ms | 5 ms | 6 ms | 500 ms | ✅ |
| `GET /api/effectifs/presence/pointages/{id}` | lecture | 20 | 0.0 % | 3 ms | 5 ms | 6 ms | 500 ms | ✅ |
| `GET /api/effectifs/referentiels/{type}/{code}/historique` | lecture | 18 | 0.0 % | 2 ms | 5 ms | 5 ms | 500 ms | ✅ |
| `GET /api/effectifs/temps/feuilles` | lecture | 3 | 0.0 % | 2 ms | 5 ms | 5 ms | 500 ms | ✅ |
| `GET /api/effectifs/perimetres` | lecture | 20 | 0.0 % | 3 ms | 4 ms | 6 ms | 500 ms | ✅ |
| `GET /api/effectifs/perimetres/moi` | lecture | 20 | 0.0 % | 3 ms | 4 ms | 5 ms | 500 ms | ✅ |
| `GET /api/effectifs/planification/periodes` | lecture | 20 | 0.0 % | 2 ms | 4 ms | 6 ms | 500 ms | ✅ |
| `GET /api/effectifs/planification/quotas` | lecture | 20 | 0.0 % | 3 ms | 4 ms | 4 ms | 500 ms | ✅ |
| `GET /api/effectifs/presence/ressources/{id}/pointages` | lecture | 20 | 0.0 % | 2 ms | 4 ms | 8 ms | 500 ms | ✅ |
| `GET /api/effectifs/regles` | lecture | 16 | 0.0 % | 2 ms | 4 ms | 4 ms | 500 ms | ✅ |
| `GET /api/effectifs/regles/evaluations` | lecture | 14 | 0.0 % | 3 ms | 4 ms | 4 ms | 500 ms | ✅ |
| `GET /api/effectifs/regles/{id}` | lecture | 10 | 0.0 % | 3 ms | 4 ms | 4 ms | 500 ms | ✅ |
| `GET /api/effectifs/temps/feuilles/{id}` | lecture | 1 | 0.0 % | 4 ms | 4 ms | 4 ms | 500 ms | ✅ |
| `GET /api/effectifs/mon-horaire` | lecture | 20 | 0.0 % | 2 ms | 3 ms | 3 ms | 500 ms | ✅ |
| `GET /api/effectifs/planification/postes` | lecture | 20 | 0.0 % | 2 ms | 3 ms | 3 ms | 500 ms | ✅ |
| `GET /api/effectifs/presence/profils` | lecture | 20 | 0.0 % | 2 ms | 3 ms | 4 ms | 500 ms | ✅ |
| `GET /api/effectifs/presence/referentiels-profils` | lecture | 20 | 0.0 % | 2 ms | 3 ms | 4 ms | 500 ms | ✅ |
| `GET /api/effectifs/referentiels` | lecture | 20 | 0.0 % | 2 ms | 3 ms | 3 ms | 500 ms | ✅ |
| `GET /api/effectifs/referentiels/{type}` | lecture | 20 | 0.0 % | 2 ms | 3 ms | 3 ms | 500 ms | ✅ |
| `GET /api/effectifs/regles/faits` | lecture | 12 | 0.0 % | 1 ms | 3 ms | 3 ms | 500 ms | ✅ |
| `GET /api/effectifs/temps/export-paie` | rapport | 5 | 0.0 % | 2 ms | 3 ms | 3 ms | 2000 ms | ✅ |

## Lecture

- **SLI** : ce qui est mesuré (centiles de latence, taux d'erreur, débit). **SLO** : l'objectif par classe d'endpoint (`slo.json`). **SLA** : l'engagement contractuel, p95 < 2 s à 2 500 utilisateurs.
- Les centiles sont calculés sur toutes les observations (rang le plus proche) ; aucune moyenne.
- Une requête en 4xx nommé (validation, droit) compte comme une réponse valide ; un 5xx, un code inattendu ou un transport rompu compte comme une erreur. Une requête trop longue n'est pas une erreur : elle pèse dans les centiles.
