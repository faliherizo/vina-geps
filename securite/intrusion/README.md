# Tests d'intrusion scriptés

Suite `node:test` sans dépendance, jouée contre une pile **réelle**, frontal nginx compris.

| Fichier | Rôle |
|---|---|
| `lancer.sh` | Monte une pile éphémère (port 8097, secrets tirés au hasard), joue la suite, ZAP si `ZAP=1`, démonte. Avec `CIBLE`, vise une pile déjà déployée. |
| `docker-compose.intrusion.yml` | Surcharge de la composition : noms de conteneurs distincts de la pile de développement, aucune variable du poste |
| `commun.mjs` | Harnais : appels, sessions, préparation unique (organisme FX-AO semé, organisme témoin) |
| `fx-ao/*.test.mjs` | Les tests de l'organisme FX-AO-20260810 : cloisonnement, ReDoS, isolation, rôles, confidentialité, injection |

Plan : [docs/securite/fx-ao/PLAN-TESTS-INTRUSION-FX-AO.md](../../docs/securite/fx-ao/PLAN-TESTS-INTRUSION-FX-AO.md).
Rapport : [docs/securite/fx-ao/RAPPORT-TESTS-INTRUSION-FX-AO-2026-11-22.md](../../docs/securite/fx-ao/RAPPORT-TESTS-INTRUSION-FX-AO-2026-11-22.md).
Pipeline : `.github/workflows/intrusion.yml`, appelé aussi par l'orchestrateur `devsecops.yml`.

```bash
securite/intrusion/lancer.sh               # pile éphémère
ZAP=1 securite/intrusion/lancer.sh         # + ZAP passif
```

La suite **écrit** (organismes, comptes, alertes). Elle ne vise jamais une base qui porte des données réelles, et une
recette ou une production seulement avec l'autorisation écrite de son exploitant.
