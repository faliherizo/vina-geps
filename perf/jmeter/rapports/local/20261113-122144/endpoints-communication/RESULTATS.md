# Résultats de performance — local — 2026-11-13 17:23

- **Fichier** : `resultats/local/20261113-122144/endpoints-communication.jtl`
- **Environnement** : `local` (http://localhost:4020, 20 utilisateurs, montée 10 s, durée 60 s, réflexion 1000 ms, écritures true)
- **SLA** : p95 < 2000 ms pour 2500 utilisateurs (Devis Santé Québec 26-432315-1226, exigences 8.1–8.2 et annexe C 10.1.1 ; AO-5338 NF)

## Verdict : ✅ objectifs tenus

## Couverture utile

**23 des 38 requêtes (61 %) répondent 2xx.** Les autres sont mesurées sur un refus : un identifiant de chemin absent (404), un corps que la validation refuse (422) ou un conflit (409). Leur latence ne dit rien du travail réel.

| Requête mesurée sur un refus | Code dominant |
|---|---|
| `GET /api/communication/listes-distribution/{id}` | 404 |
| `GET /api/communication/listes-distribution/{id}/destinataires` | 404 |
| `GET /api/communication/messages/recherche` | 400 |
| `GET /api/communication/pieces-jointes/{id}` | 404 |
| `POST /api/communication/appels/{id}/decrocher` | 404 |
| `POST /api/communication/appels/{id}/raccrocher` | 404 |
| `POST /api/communication/appels/{id}/refuser` | 404 |
| `POST /api/communication/conversations/{id}/archiver` | 400 |
| `PUT /api/communication/conversations/{id}/brouillon` | 400 |
| `POST /api/communication/conversations/{id}/messages` | 400 |
| `POST /api/communication/conversations/{id}/participants` | 400 |
| `DELETE /api/communication/conversations/{id}/participants/{personId}` | 400 |
| `DELETE /api/communication/listes-distribution/{id}` | 404 |
| `PUT /api/communication/listes-distribution/{id}` | 404 |
| `POST /api/communication/listes-distribution/{id}/conversation` | 404 |

Pour les rendre utiles : semer l'entité qui manque (`preparer.cjs`) ou donner au plan un corps que la validation accepte (`openapiContrats.ts`).

## Total

| Requêtes | Erreurs | Débit | p50 | p95 | p99 | max |
|---:|---:|---:|---:|---:|---:|---:|
| 2271 | 0 (0.00 %) | 18.9 req/s | 2 ms | 7 ms | 13 ms | 60 ms |

## Par requête (SLI mesurés, objectif de la classe)

| Requête | Classe | N | Erreurs | p50 | p95 | p99 | SLO p95 | Verdict |
|---|---|---:|---:|---:|---:|---:|---:|---|
| `POST /api/auth/login` | connexion | 1 | 0.0 % | 60 ms | 60 ms | 60 ms | 800 ms | ✅ |
| `GET /api/communication/conversations/non-lus` | lecture | 64 | 0.0 % | 7 ms | 15 ms | 22 ms | 500 ms | ✅ |
| `POST /api/communication/conversations/{id}/lu` | ecriture | 60 | 0.0 % | 6 ms | 10 ms | 10 ms | 1000 ms | ✅ |
| `PUT /api/communication/acheminement/unites/{uniteId}` | ecriture | 60 | 0.0 % | 4 ms | 8 ms | 14 ms | 1000 ms | ✅ |
| `POST /api/communication/appels` | ecriture | 60 | 0.0 % | 3 ms | 8 ms | 13 ms | 1000 ms | ✅ |
| `GET /api/communication/conversations` | lecture | 66 | 0.0 % | 4 ms | 7 ms | 8 ms | 500 ms | ✅ |
| `DELETE /api/communication/acheminement/unites/{uniteId}` | ecriture | 60 | 0.0 % | 3 ms | 7 ms | 23 ms | 1000 ms | ✅ |
| `POST /api/communication/appels/{id}/decrocher` | ecriture | 60 | 0.0 % | 2 ms | 7 ms | 26 ms | 1000 ms | ✅ |
| `POST /api/communication/appels/{id}/raccrocher` | ecriture | 60 | 0.0 % | 2 ms | 7 ms | 28 ms | 1000 ms | ✅ |
| `POST /api/communication/conversations` | ecriture | 60 | 0.0 % | 4 ms | 7 ms | 9 ms | 1000 ms | ✅ |
| `POST /api/communication/listes-distribution` | ecriture | 60 | 0.0 % | 4 ms | 7 ms | 9 ms | 1000 ms | ✅ |
| `GET /api/communication/conversations/{id}/messages` | lecture | 60 | 0.0 % | 4 ms | 6 ms | 6 ms | 500 ms | ✅ |
| `GET /api/communication/messages/recherche` | lecture | 60 | 0.0 % | 4 ms | 6 ms | 7 ms | 500 ms | ✅ |
| `POST /api/communication/appels/{id}/refuser` | ecriture | 60 | 0.0 % | 2 ms | 6 ms | 19 ms | 1000 ms | ✅ |
| `POST /api/communication/conversations/{id}/archiver` | ecriture | 60 | 0.0 % | 2 ms | 6 ms | 6 ms | 1000 ms | ✅ |
| `DELETE /api/communication/conversations/{id}/brouillon` | ecriture | 60 | 0.0 % | 3 ms | 6 ms | 15 ms | 1000 ms | ✅ |
| `PUT /api/communication/conversations/{id}/brouillon` | ecriture | 60 | 0.0 % | 2 ms | 6 ms | 14 ms | 1000 ms | ✅ |
| `POST /api/communication/conversations/{id}/participants` | ecriture | 60 | 0.0 % | 3 ms | 6 ms | 7 ms | 1000 ms | ✅ |
| `PUT /api/communication/listes-distribution/{id}` | ecriture | 60 | 0.0 % | 2 ms | 6 ms | 13 ms | 1000 ms | ✅ |
| `DELETE /api/communication/conversations/{id}/participants/{personId}` | ecriture | 60 | 0.0 % | 3 ms | 5 ms | 7 ms | 1000 ms | ✅ |
| `POST /api/communication/listes-distribution/{id}/conversation` | ecriture | 58 | 0.0 % | 2 ms | 5 ms | 7 ms | 1000 ms | ✅ |
| `GET /api/communication/listes-distribution` | lecture | 60 | 0.0 % | 2 ms | 4 ms | 4 ms | 500 ms | ✅ |
| `POST /api/communication/conversations/{id}/messages` | ecriture | 60 | 0.0 % | 2 ms | 4 ms | 14 ms | 1000 ms | ✅ |
| `DELETE /api/communication/listes-distribution/{id}` | ecriture | 60 | 0.0 % | 2 ms | 4 ms | 6 ms | 1000 ms | ✅ |
| `POST /api/communication/messages/{id}/lu` | ecriture | 56 | 0.0 % | 2 ms | 4 ms | 4 ms | 1000 ms | ✅ |
| `GET /api/communication/acheminement/unites` | lecture | 74 | 0.0 % | 2 ms | 3 ms | 7 ms | 500 ms | ✅ |
| `GET /api/communication/appels` | lecture | 72 | 0.0 % | 1 ms | 3 ms | 3 ms | 500 ms | ✅ |
| `GET /api/communication/conversations/{id}/acheminement` | lecture | 60 | 0.0 % | 2 ms | 3 ms | 4 ms | 500 ms | ✅ |
| `GET /api/communication/messages/{id}/lectures` | lecture | 60 | 0.0 % | 2 ms | 3 ms | 8 ms | 500 ms | ✅ |
| `GET /api/communication/appels/en-cours` | lecture | 70 | 0.0 % | 1 ms | 2 ms | 3 ms | 500 ms | ✅ |
| `GET /api/communication/brouillons` | lecture | 68 | 0.0 % | 1 ms | 2 ms | 4 ms | 500 ms | ✅ |
| `GET /api/communication/conversations/{id}` | lecture | 62 | 0.0 % | 1 ms | 2 ms | 3 ms | 500 ms | ✅ |
| `GET /api/communication/conversations/{id}/brouillon` | lecture | 60 | 0.0 % | 1 ms | 2 ms | 3 ms | 500 ms | ✅ |
| `GET /api/communication/groupes` | lecture | 60 | 0.0 % | 1 ms | 2 ms | 2 ms | 500 ms | ✅ |
| `GET /api/communication/groupes/{id}/membres` | lecture | 60 | 0.0 % | 2 ms | 2 ms | 2 ms | 500 ms | ✅ |
| `GET /api/communication/listes-distribution/{id}` | lecture | 60 | 0.0 % | 2 ms | 2 ms | 7 ms | 500 ms | ✅ |
| `GET /api/communication/listes-distribution/{id}/destinataires` | lecture | 60 | 0.0 % | 1 ms | 2 ms | 7 ms | 500 ms | ✅ |
| `GET /api/communication/pieces-jointes/{id}` | lecture | 60 | 0.0 % | 1 ms | 2 ms | 4 ms | 500 ms | ✅ |

## Lecture

- **SLI** : ce qui est mesuré (centiles de latence, taux d'erreur, débit). **SLO** : l'objectif par classe d'endpoint (`slo.json`). **SLA** : l'engagement contractuel, p95 < 2 s à 2 500 utilisateurs.
- Les centiles sont calculés sur toutes les observations (rang le plus proche) ; aucune moyenne.
- Une requête en 4xx nommé (validation, droit) compte comme une réponse valide ; un 5xx, un code inattendu ou un transport rompu compte comme une erreur. Une requête trop longue n'est pas une erreur : elle pèse dans les centiles.
