# Performance de l'API d'échange — campagne JMeter du 22 novembre 2026

> AD-06 (addenda 5), [ADR 0088](../../adr/0088-api-echanges-organisme-machine.md). Banc : `perf/jmeter/campagne-api-echanges.sh`.

## Conditions

| Paramètre | Valeur |
|---|---|
| Serveur | Java 25, SQLite, base vierge bâtie par Liquibase, mode d'essai, poste de développement partagé avec d'autres builds |
| Données | 1 organisme, 40 semaines de quarts générés, attribués et publiés (≈ 400 entrées de quarts au journal), feuilles approuvées par une directrice distincte de qui soumet (séparation des tâches) |
| Charge | Débit fixe de 20 requêtes/s, 10 unités, 120 s par opération ; jeton `client_credentials` obtenu en préparation |
| Comparaison | Même plan, mêmes données : version d'avant optimisation (`d06c424`, AD-06a fusionnée) et version fusionnée (`1ad9372`) |

## Résultats

Couverture utile : 100 % de réponses 2xx dans chaque opération. Aucune erreur.

| Opération | Avant : moy. / p95 | Après : moy. / p95 | SLO p95 (typique) | Verdict |
|---|---:|---:|---:|---|
| `GET /quarts` (fenêtre d'un mois) | 2 / 4 ms | 2 / 4 ms | 120 ms | ✅ |
| `GET /quarts/modifications?limite=500` | **53 / 68 ms** | **9 / 12 ms** | 150 ms | ✅ |
| `GET /releves-presence?limite=500` | 3 / 5 ms | 3 / 5 ms | 120 ms | ✅ |
| `PUT /employes/{matricule}/soldes` | — (route absente) | 4 / 9 ms | 120 ms | ✅ |

## Lecture

- **Correctifs** : la moyenne est divisée par 5,9. L'ancienne lecture faisait deux à trois requêtes par entrée, soit environ
  1 000 requêtes pour une page de 400 entrées. La nouvelle en fait trois, quelle que soit la taille de la page.
- **Quarts et relevés** : rien ne change à ce volume, puisque l'organisme tient en mémoire. Les index du changeset 0023 et
  les bornes de curseur indexables servent quand le journal et les feuilles s'accumulent sur plusieurs années. Ils évitent
  alors un balayage de toutes les lignes de l'organisme à chaque page.
- La version d'avant a approuvé 80 feuilles et non 120. Son semis suit la même procédure, mais l'état d'une partie des
  feuilles diffère d'une version à l'autre. La lecture des relevés porte quand même sur des pages non vides dans les deux
  cas.

## Ce que la campagne a corrigé dans le banc lui-même

Le premier passage annonçait « 0 relevés approuvés ». Le semis avalait ses erreurs, et la lecture des relevés était donc
mesurée sur une page vide. La cause était la séparation des tâches, qui refuse à qui soumet d'approuver : c'est le produit
qui avait raison. Le semis crée désormais une directrice distincte, avec un périmètre sur l'unité, et il affiche le motif
de tout refus au lieu de l'avaler.

## Limites

- Le poste de développement était partagé, et les valeurs absolues ne valent que pour lui. La comparaison avant/après a
  été faite dans la foulée, sur le même poste et avec le même plan.
- Le profil « volumineux » (plusieurs années, plusieurs organismes) n'a pas été joué.
