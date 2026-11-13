# Résultats de performance — local — 2026-11-13 17:04

- **Fichier** : `resultats/local/20261113-120322/endpoints-communication.jtl`
- **Environnement** : `local` (http://localhost:4020, 20 utilisateurs, montée 10 s, durée 60 s, réflexion 1000 ms, écritures true)
- **SLA** : p95 < 2000 ms pour 2500 utilisateurs (Devis Santé Québec 26-432315-1226, exigences 8.1–8.2 et annexe C 10.1.1 ; AO-5338 NF)

## Verdict : ❌ 10 requête(s) hors objectif ; total hors objectif

## Couverture utile

**10 des 11 requêtes (91 %) répondent 2xx.** Les autres sont mesurées sur un refus : un identifiant de chemin absent (404), un corps que la validation refuse (422) ou un conflit (409). Leur latence ne dit rien du travail réel.

| Requête mesurée sur un refus | Code dominant |
|---|---|
| `GET /api/communication/conversations/{id}/messages` | Non HTTP response code: java.net.SocketTimeoutException |

Pour les rendre utiles : semer l'entité qui manque (`preparer.cjs`) ou donner au plan un corps que la validation accepte (`openapiContrats.ts`).

## Total

| Requêtes | Erreurs | Débit | p50 | p95 | p99 | max |
|---:|---:|---:|---:|---:|---:|---:|
| 130 | 21 (16.15 %) | 1.8 req/s | 3 ms | 30002 ms | 30002 ms | 30002 ms |

Manques au total : p95 30002 ms > SLA 2000 ms ; erreurs 16.15 % > budget 0.1 %

## Par requête (SLI mesurés, objectif de la classe)

| Requête | Classe | N | Erreurs | p50 | p95 | p99 | SLO p95 | Verdict |
|---|---|---:|---:|---:|---:|---:|---:|---|
| `GET /api/communication/appels/en-cours` | lecture | 19 | 10.5 % | 3 ms | 30002 ms | 30002 ms | 500 ms | ❌ p95 30002 ms > SLO 500 ms ; p99 30002 ms > SLO 1000 ms ; erreurs 10.53 % > SLO 0.1 % ; p95 30002 ms > SLA 2000 ms |
| `GET /api/communication/conversations` | lecture | 15 | 26.7 % | 3 ms | 30002 ms | 30002 ms | 500 ms | ❌ p95 30002 ms > SLO 500 ms ; p99 30002 ms > SLO 1000 ms ; erreurs 26.67 % > SLO 0.1 % ; p95 30002 ms > SLA 2000 ms |
| `GET /api/communication/conversations/non-lus` | lecture | 12 | 33.3 % | 5 ms | 30002 ms | 30002 ms | 500 ms | ❌ p95 30002 ms > SLO 500 ms ; p99 30002 ms > SLO 1000 ms ; erreurs 33.33 % > SLO 0.1 % ; p95 30002 ms > SLA 2000 ms |
| `GET /api/communication/conversations/{id}/acheminement` | lecture | 8 | 50.0 % | 29511 ms | 30002 ms | 30002 ms | 500 ms | ❌ p95 30002 ms > SLO 500 ms ; p99 30002 ms > SLO 1000 ms ; erreurs 50.00 % > SLO 0.1 % ; p95 30002 ms > SLA 2000 ms |
| `GET /api/communication/conversations/{id}/brouillon` | lecture | 5 | 20.0 % | 29511 ms | 30002 ms | 30002 ms | 500 ms | ❌ p95 30002 ms > SLO 500 ms ; p99 30002 ms > SLO 1000 ms ; erreurs 20.00 % > SLO 0.1 % ; p95 30002 ms > SLA 2000 ms |
| `GET /api/communication/conversations/{id}/messages` | lecture | 4 | 100.0 % | 30000 ms | 30002 ms | 30002 ms | 500 ms | ❌ p95 30002 ms > SLO 500 ms ; p99 30002 ms > SLO 1000 ms ; erreurs 100.00 % > SLO 0.1 % ; p95 30002 ms > SLA 2000 ms |
| `GET /api/communication/conversations/{id}` | lecture | 9 | 11.1 % | 3 ms | 30001 ms | 30001 ms | 500 ms | ❌ p95 30001 ms > SLO 500 ms ; p99 30001 ms > SLO 1000 ms ; erreurs 11.11 % > SLO 0.1 % ; p95 30001 ms > SLA 2000 ms |
| `GET /api/communication/appels` | lecture | 20 | 5.0 % | 2 ms | 29555 ms | 30001 ms | 500 ms | ❌ p95 29555 ms > SLO 500 ms ; p99 30001 ms > SLO 1000 ms ; erreurs 5.00 % > SLO 0.1 % ; p95 29555 ms > SLA 2000 ms |
| `GET /api/communication/brouillons` | lecture | 17 | 0.0 % | 3 ms | 29547 ms | 29547 ms | 500 ms | ❌ p95 29547 ms > SLO 500 ms ; p99 29547 ms > SLO 1000 ms ; p95 29547 ms > SLA 2000 ms |
| `GET /api/communication/acheminement/unites` | lecture | 20 | 0.0 % | 3 ms | 29064 ms | 29564 ms | 500 ms | ❌ p95 29064 ms > SLO 500 ms ; p99 29564 ms > SLO 1000 ms ; p95 29064 ms > SLA 2000 ms |
| `POST /api/auth/login` | connexion | 1 | 0.0 % | 79 ms | 79 ms | 79 ms | 800 ms | ✅ |

## Lecture

- **SLI** : ce qui est mesuré (centiles de latence, taux d'erreur, débit). **SLO** : l'objectif par classe d'endpoint (`slo.json`). **SLA** : l'engagement contractuel, p95 < 2 s à 2 500 utilisateurs.
- Les centiles sont calculés sur toutes les observations (rang le plus proche) ; aucune moyenne.
- Une requête en 4xx nommé (validation, droit) compte comme une réponse valide ; un 5xx, un code inattendu ou un transport rompu compte comme une erreur. Une requête trop longue n'est pas une erreur : elle pèse dans les centiles.
