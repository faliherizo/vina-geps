# Résultats de performance — local — 2026-11-13 15:06

- **Fichier** : `resultats/local/20261113-100524/parcours-employe.jtl`
- **Environnement** : `local` (http://localhost:4020, 20 utilisateurs, montée 10 s, durée 60 s, réflexion 1000 ms, écritures true)
- **SLA** : p95 < 2000 ms pour 2500 utilisateurs (Devis Santé Québec 26-432315-1226, exigences 8.1–8.2 et annexe C 10.1.1 ; AO-5338 NF)

## Verdict : ✅ objectifs tenus

## Couverture utile

**5 des 6 requêtes (83 %) répondent 2xx.** Les autres sont mesurées sur un refus : un identifiant de chemin absent (404), un corps que la validation refuse (422) ou un conflit (409). Leur latence ne dit rien du travail réel.

| Requête mesurée sur un refus | Code dominant |
|---|---|
| `POST /api/effectifs/presence/pointages` | 422 |

Pour les rendre utiles : semer l'entité qui manque (`preparer.cjs`) ou donner au plan un corps que la validation accepte (`openapiContrats.ts`).

## Total

| Requêtes | Erreurs | Débit | p50 | p95 | p99 | max |
|---:|---:|---:|---:|---:|---:|---:|
| 1091 | 0 (0.00 %) | 18.0 req/s | 3 ms | 6 ms | 9 ms | 55 ms |

## Par requête (SLI mesurés, objectif de la classe)

| Requête | Classe | N | Erreurs | p50 | p95 | p99 | SLO p95 | Verdict |
|---|---|---:|---:|---:|---:|---:|---:|---|
| `POST /api/auth/login` | connexion | 1 | 0.0 % | 55 ms | 55 ms | 55 ms | 800 ms | ✅ |
| `POST /api/effectifs/presence/pointages` | ecriture | 210 | 0.0 % | 5 ms | 7 ms | 14 ms | 1000 ms | ✅ |
| `GET /api/effectifs/disponibilites/moi` | lecture | 222 | 0.0 % | 3 ms | 6 ms | 9 ms | 500 ms | ✅ |
| `GET /api/effectifs/temps/moi` | lecture | 218 | 0.0 % | 3 ms | 6 ms | 7 ms | 500 ms | ✅ |
| `GET /api/effectifs/absences` | lecture | 214 | 0.0 % | 2 ms | 5 ms | 7 ms | 500 ms | ✅ |
| `GET /api/effectifs/mon-horaire` | lecture | 226 | 0.0 % | 2 ms | 4 ms | 7 ms | 500 ms | ✅ |

## Lecture

- **SLI** : ce qui est mesuré (centiles de latence, taux d'erreur, débit). **SLO** : l'objectif par classe d'endpoint (`slo.json`). **SLA** : l'engagement contractuel, p95 < 2 s à 2 500 utilisateurs.
- Les centiles sont calculés sur toutes les observations (rang le plus proche) ; aucune moyenne.
- Une requête en 4xx nommé (validation, droit) compte comme une réponse valide ; un 5xx, un code inattendu ou un transport rompu compte comme une erreur. Une requête trop longue n'est pas une erreur : elle pèse dans les centiles.
