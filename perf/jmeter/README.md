# Essais de performance JMeter — serveur Java d'Urgences-santé

## En quatre commandes

```bash
# 1. Le serveur Java sur un port privé, base vierge bâtie par Liquibase, mode d'essai (port 4020)
perf/jmeter/backend-java-local.sh &
# 2. Les plans, générés de la spécification OpenAPI que ce serveur sert (routes réellement montées)
PERF_API=http://localhost:4020 node perf/jmeter/generer-plans.cjs
# 3. Les données de la campagne : l'organisme d'effectifs et une conversation (→ donnees/identifiants.csv)
node perf/jmeter/preparer.cjs --env local --lignes 3
T=$(cat perf/jmeter/donnees/tenant-local.txt)
# 4. La campagne, puis le verdict SLO/SLA
perf/jmeter/lancer.sh local plans/endpoints-effectifs.jmx -Jtenant=$T   # durée calculée du plan ; -Jduree_balayage=… pour la forcer
perf/jmeter/lancer.sh local scenarios -Jtenant=$T
```

## Le dossier

| Chemin | Rôle |
|---|---|
| `env/{local,dev,preprod,prod}.properties` | Un fichier par environnement : hôte, port, comptes, charge, seuils. Surcharge à la ligne de commande : `-Jcle=valeur`. |
| `donnees/comptes.csv` | Le compte de la campagne (première ligne = en-têtes = noms des variables JMeter). |
| `corps-balayage.json` | Le corps d'une écriture sans exemple de contrat, avec les variables du CSV et `${__UUID()}` ; l'exemple du contrat l'emporte. |
| `donnees/identifiants.csv` | Écrit par `preparer.cjs` : les identifiants semés, dans l'ordre de `COLONNES` (`generer-plans.cjs`). |
| `plans/endpoints-<famille>.jmx` | Chaque endpoint servi, une famille par plan : lectures, puis écritures sous garde `ecritures=true`. `plans/routes.json` : l'inventaire dont ils sortent. |
| `scenarios/*.jmx` | Les parcours : l'employé (horaire, disponibilités, feuille, pointage), le gestionnaire (comblement, équipages, absences, feuilles), la sonde de lecture (production). |
| `operations.json` · `operations/*.jmx` | L'accord de niveau de service : une opération par plan, à débit fixe, cibles SLO/SLA par opération. |
| `gabarits/*.jmx` | Une requête par verbe, avec ses assertions, à copier-coller ; `plan-vierge.jmx`. |
| `slo.json` | Les objectifs par classe d'endpoint (santé, connexion, lecture, écriture, rapport). |
| `lancer.sh` · `analyser.cjs` | JMeter en ligne de commande, tableau de bord HTML, puis SLI (p50/p95/p99, erreurs, débit), **couverture utile** (part des requêtes en 2xx) et verdict. |

## La couverture utile — lire une latence avant de s'en réjouir

Une assertion de plan ne refuse que les **5xx**. Un 404 est une réponse valide et rapide : un
balayage dont les identifiants de chemin sont faux répond 404 partout, et le rapport conclurait
« objectifs tenus ». `analyser.cjs` rend donc la part des requêtes en 2xx et la liste nominative
des autres ; `--min-2xx 80` refuse une campagne concluante à vide.
