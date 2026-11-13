# Résultats de performance — local — 2026-11-13 15:09

- **Fichier** : `resultats/local/20261113-100841/endpoints-effectifs.jtl`
- **Environnement** : `local` (http://localhost:4020, 20 utilisateurs, montée 10 s, durée 60 s, réflexion 1000 ms, écritures true)
- **SLA** : p95 < 2000 ms pour 2500 utilisateurs (Devis Santé Québec 26-432315-1226, exigences 8.1–8.2 et annexe C 10.1.1 ; AO-5338 NF)

## Verdict : ✅ objectifs tenus

## Couverture utile

**24 des 30 requêtes (80 %) répondent 2xx.** Les autres sont mesurées sur un refus : un identifiant de chemin absent (404), un corps que la validation refuse (422) ou un conflit (409). Leur latence ne dit rien du travail réel.

| Requête mesurée sur un refus | Code dominant |
|---|---|
| `GET /api/effectifs/absences/{id}` | 404 |
| `GET /api/effectifs/absences/{id}/comblement` | 404 |
| `GET /api/effectifs/absences/{id}/impact` | 404 |
| `GET /api/effectifs/agph/remplacements/{id}` | 404 |
| `GET /api/effectifs/agph/remplacements/{id}/interesses` | 404 |
| `GET /api/effectifs/echanges/{id}` | 404 |

Pour les rendre utiles : semer l'entité qui manque (`preparer.cjs`) ou donner au plan un corps que la validation accepte (`openapiContrats.ts`).

## Total

| Requêtes | Erreurs | Débit | p50 | p95 | p99 | max |
|---:|---:|---:|---:|---:|---:|---:|
| 126 | 0 (0.00 %) | 4.1 req/s | 3 ms | 20 ms | 93 ms | 177 ms |

## Par requête (SLI mesurés, objectif de la classe)

| Requête | Classe | N | Erreurs | p50 | p95 | p99 | SLO p95 | Verdict |
|---|---|---:|---:|---:|---:|---:|---:|---|
| `GET /api/effectifs/absences/{id}` | lecture | 5 | 0.0 % | 6 ms | 177 ms | 177 ms | 500 ms | ✅ |
| `GET /api/effectifs/agph/campagnes` | lecture | 5 | 0.0 % | 3 ms | 93 ms | 93 ms | 500 ms | ✅ |
| `POST /api/auth/login` | connexion | 1 | 0.0 % | 70 ms | 70 ms | 70 ms | 800 ms | ✅ |
| `GET /api/effectifs/agph/remplacements/{id}` | lecture | 5 | 0.0 % | 4 ms | 37 ms | 37 ms | 500 ms | ✅ |
| `GET /api/effectifs/agph/campagnes/{id}/disponibilites` | lecture | 5 | 0.0 % | 4 ms | 35 ms | 35 ms | 500 ms | ✅ |
| `GET /api/effectifs/agph/campagnes/{id}/postes-disponibles` | lecture | 5 | 0.0 % | 5 ms | 33 ms | 33 ms | 500 ms | ✅ |
| `GET /api/effectifs/configuration/paquet` | lecture | 5 | 0.0 % | 4 ms | 20 ms | 20 ms | 500 ms | ✅ |
| `GET /api/effectifs/agph/remplacements/{id}/interesses` | lecture | 5 | 0.0 % | 5 ms | 19 ms | 19 ms | 500 ms | ✅ |
| `GET /api/effectifs/agph/campagnes/{id}` | lecture | 5 | 0.0 % | 3 ms | 14 ms | 14 ms | 500 ms | ✅ |
| `GET /api/effectifs/disponibilites/places/{placeId}/candidats` | lecture | 5 | 0.0 % | 3 ms | 14 ms | 14 ms | 500 ms | ✅ |
| `GET /api/effectifs/journal` | lecture | 2 | 0.0 % | 6 ms | 14 ms | 14 ms | 500 ms | ✅ |
| `GET /api/effectifs/liaisons/organisation` | lecture | 2 | 0.0 % | 3 ms | 12 ms | 12 ms | 500 ms | ✅ |
| `GET /api/effectifs/absences/{id}/comblement` | lecture | 5 | 0.0 % | 2 ms | 10 ms | 10 ms | 500 ms | ✅ |
| `GET /api/effectifs/disponibilites/periodes/{periodeId}/attribution-automatique` | lecture | 5 | 0.0 % | 2 ms | 10 ms | 10 ms | 500 ms | ✅ |
| `GET /api/effectifs/integrations/connecteurs` | lecture | 3 | 0.0 % | 2 ms | 8 ms | 8 ms | 500 ms | ✅ |
| `GET /api/effectifs/agph/campagnes/{id}/disponibilites/moi` | lecture | 5 | 0.0 % | 6 ms | 7 ms | 7 ms | 500 ms | ✅ |
| `GET /api/effectifs/agph/remplacements` | lecture | 5 | 0.0 % | 3 ms | 7 ms | 7 ms | 500 ms | ✅ |
| `GET /api/effectifs/disponibilites/moi` | lecture | 5 | 0.0 % | 3 ms | 7 ms | 7 ms | 500 ms | ✅ |
| `GET /api/effectifs/echanges` | lecture | 5 | 0.0 % | 3 ms | 6 ms | 6 ms | 500 ms | ✅ |
| `GET /api/effectifs/echanges/places/{placeId}/demandables` | lecture | 5 | 0.0 % | 3 ms | 5 ms | 5 ms | 500 ms | ✅ |
| `GET /api/effectifs/absences` | lecture | 5 | 0.0 % | 3 ms | 4 ms | 4 ms | 500 ms | ✅ |
| `GET /api/effectifs/absences/{id}/impact` | lecture | 5 | 0.0 % | 2 ms | 4 ms | 4 ms | 500 ms | ✅ |
| `GET /api/effectifs/disponibilites/periodes/{periodeId}` | lecture | 5 | 0.0 % | 2 ms | 4 ms | 4 ms | 500 ms | ✅ |
| `GET /api/effectifs/disponibilites/periodes/{periodeId}/ressources/{ressourceId}` | lecture | 5 | 0.0 % | 3 ms | 4 ms | 4 ms | 500 ms | ✅ |
| `GET /api/effectifs/echanges/{id}` | lecture | 4 | 0.0 % | 3 ms | 4 ms | 4 ms | 500 ms | ✅ |
| `GET /api/effectifs/integrations/messages` | lecture | 3 | 0.0 % | 3 ms | 4 ms | 4 ms | 500 ms | ✅ |
| `GET /api/effectifs/milieux` | lecture | 1 | 0.0 % | 4 ms | 4 ms | 4 ms | 500 ms | ✅ |
| `GET /api/effectifs/echanges/moi` | lecture | 5 | 0.0 % | 3 ms | 3 ms | 3 ms | 500 ms | ✅ |
| `GET /api/effectifs/echanges/ressources/{id}/quarts` | lecture | 4 | 0.0 % | 3 ms | 3 ms | 3 ms | 500 ms | ✅ |
| `GET /api/effectifs/ma-ressource` | lecture | 1 | 0.0 % | 2 ms | 2 ms | 2 ms | 500 ms | ✅ |

## Lecture

- **SLI** : ce qui est mesuré (centiles de latence, taux d'erreur, débit). **SLO** : l'objectif par classe d'endpoint (`slo.json`). **SLA** : l'engagement contractuel, p95 < 2 s à 2 500 utilisateurs.
- Les centiles sont calculés sur toutes les observations (rang le plus proche) ; aucune moyenne.
- Une requête en 4xx nommé (validation, droit) compte comme une réponse valide ; un 5xx, un code inattendu ou un transport rompu compte comme une erreur. Une requête trop longue n'est pas une erreur : elle pèse dans les centiles.
