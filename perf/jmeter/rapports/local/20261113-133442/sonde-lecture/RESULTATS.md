# Résultats de performance — local — 2026-11-13 18:37

- **Fichier** : `resultats/local/20261113-133442/sonde-lecture.jtl`
- **Environnement** : `local` (http://localhost:4020, 20 utilisateurs, montée 10 s, durée 60 s, réflexion 1000 ms, écritures true)
- **SLA** : p95 < 2000 ms pour 2500 utilisateurs (Devis Santé Québec 26-432315-1226, exigences 8.1–8.2 et annexe C 10.1.1 ; AO-5338 NF)

## Verdict : ✅ objectifs tenus

## Couverture utile

**5 des 5 requêtes (100 %) répondent 2xx.** Les autres sont mesurées sur un refus : un identifiant de chemin absent (404), un corps que la validation refuse (422) ou un conflit (409). Leur latence ne dit rien du travail réel.

## Total

| Requêtes | Erreurs | Débit | p50 | p95 | p99 | max |
|---:|---:|---:|---:|---:|---:|---:|
| 1086 | 0 (0.00 %) | 17.9 req/s | 2 ms | 6 ms | 6 ms | 56 ms |

## Par requête (SLI mesurés, objectif de la classe)

| Requête | Classe | N | Erreurs | p50 | p95 | p99 | SLO p95 | Verdict |
|---|---|---:|---:|---:|---:|---:|---:|---|
| `POST /api/auth/login` | connexion | 1 | 0.0 % | 56 ms | 56 ms | 56 ms | 800 ms | ✅ |
| `GET /api/communication/conversations` | lecture | 263 | 0.0 % | 5 ms | 6 ms | 7 ms | 500 ms | ✅ |
| `GET /api/effectifs/referentiels/unites` | lecture | 268 | 0.0 % | 2 ms | 3 ms | 4 ms | 500 ms | ✅ |
| `GET /api/saas/my-modules` | lecture | 279 | 0.0 % | 1 ms | 2 ms | 2 ms | 500 ms | ✅ |
| `GET /api/notifications/evenements` | lecture | 275 | 0.0 % | 1 ms | 2 ms | 2 ms | 500 ms | ✅ |

## Lecture

- **SLI** : ce qui est mesuré (centiles de latence, taux d'erreur, débit). **SLO** : l'objectif par classe d'endpoint (`slo.json`). **SLA** : l'engagement contractuel, p95 < 2 s à 2 500 utilisateurs.
- Les centiles sont calculés sur toutes les observations (rang le plus proche) ; aucune moyenne.
- Une requête en 4xx nommé (validation, droit) compte comme une réponse valide ; un 5xx, un code inattendu ou un transport rompu compte comme une erreur. Une requête trop longue n'est pas une erreur : elle pèse dans les centiles.
