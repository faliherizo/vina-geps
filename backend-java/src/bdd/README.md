# Scénarios BDD des exigences du 26-015-DO

Les exigences du client, écrites en Gherkin français et jouées contre le **serveur Java réel**. Chaque
scénario porte l'étiquette de l'exigence qu'il prouve (`@E-GN-5`, `@P-CH-9`…). Le tableau qui
croise les 144 exigences avec leurs scénarios et leur dernier résultat est
[docs/26-015-DO_AO/11-EXIGENCES-BDD.md](../../../docs/26-015-DO_AO/11-EXIGENCES-BDD.md).

## Ce qui se joue

- **Un serveur, une fois** : `ServeurEssai` démarre `ApplicationNexuVina` dans le processus, sur un port
  libre, en mode essai (`NEXU_ESSAI=1`), avec une base SQLite vierge que Liquibase crée.
- **Un organisme neuf par scénario** : `Organisme` le fait créer par le superadmin, pose les modules du
  produit et crée les acteurs `julie`, `marc` (employés), `chef` (gestionnaire) et `directeur`, leur
  mur de première connexion levé. Aucun état ne fuit d'un scénario à l'autre.
- **Par HTTP seulement** : `ClientApi` envoie le jeton de session et `X-Tenant-Id`, comme le
  frontal. Rien ne touche la base directement.

## Arborescence

| Chemin | Rôle |
|---|---|
| `java/ca/vina/nexu/bdd/ExigencesBddTest.java` | La suite JUnit Platform qui lance Cucumber |
| `java/ca/vina/nexu/bdd/EtapesExigences.java` | Les étapes (voir ci-dessous) |
| `java/ca/vina/nexu/bdd/Organisme.java` | L'organisme et les acteurs d'un scénario |
| `java/ca/vina/nexu/bdd/ServeurEssai.java` | Le serveur Java démarré une fois |
| `java/ca/vina/nexu/bdd/ClientApi.java`, `Gabarits.java` | Appels HTTP, gabarits `{{valeur}}`, comparaison par sous-ensemble |
| `resources/amorce/organisme-effectifs.json` | Les référentiels, le milieu C12 (`{{c12}}`) et les ressources Julie (`{{P1}}`) et Marc (`{{P2}}`) |
| `resources/features/*.feature` | Un fichier par famille d'exigences |

Le `pom.xml` ajoute `src/bdd/java` aux sources d'essai et `src/bdd/resources` aux ressources d'essai
(`build-helper-maven-plugin`).

## Lancer

```bash
cd backend-java
rm -rf target/classes target/test-classes target/maven-status
mvn -o test -Dtest=ExigencesBddTest -Djacoco.skip=true                                   # toute la suite
mvn -o test -Dtest=ExigencesBddTest -Djacoco.skip=true -Dcucumber.filter.tags="@E-GN-5"   # une exigence
mvn -o test -Dtest=ExigencesBddTest -Djacoco.skip=true -Dcucumber.filter.tags="@P-CH-1 or @E-CH-1"
```

Rapports : `target/bdd/exigences.html` (lisible) et `target/bdd/exigences.json` (lu par le tableau).
`mvn verify` joue aussi la suite, avec les autres essais.

Après un passage, régénérer le tableau, et vérifier qu'aucune exigence fonctionnelle n'a perdu son
scénario :

```bash
python3 docs/26-015-DO_AO/construire-tableau-bdd.py
python3 docs/26-015-DO_AO/construire-tableau-bdd.py --verifier
```

## Les étapes

| Étape | Effet |
|---|---|
| `Soit un organisme d'Urgences-santé neuf` | Organisme, modules, acteurs |
| `Soit un organisme d'Urgences-santé amorcé` | Le même, plus l'amorce (`{{c12}}`, `{{P1}}`, `{{P2}}`) |
| `Soit un second organisme d'Urgences-santé amorcé, dont les acteurs et les valeurs portent le suffixe B` | Un deuxième organisme dans le même scénario : `adminB`, `julieB`, `{{P1B}}` |
| `Quand <acteur> envoie <MÉTHODE> <chemin>` | Appel sans corps |
| `Quand <acteur> envoie <MÉTHODE> <chemin> avec :` + bloc JSON | Appel avec corps |
| `Alors la réponse a le statut <n>` | Statut exact |
| `Et la réponse contient :` + bloc JSON | Comparaison par **sous-ensemble** : chaque clé attendue doit être là, avec sa valeur ; un tableau se compare élément par élément, dans l'ordre |
| `Et la réponse correspond à "<regex>"` / `ne correspond pas à` | Expression régulière (multiligne) sur le corps brut |
| `Et je retiens "<chemin>" sous le nom "<nom>"` | Capture (`id`, `places.0.id`, `0.id`) réutilisable en `{{nom}}` |
| `Et la réponse compte <n> élément(s)` | Taille d'un tableau |
| `Et la réponse arrive en moins de <n> millisecondes` | Durée de l'appel |

Valeurs toujours disponibles : `{{tenant}}`, `{{suffixe}}`, `{{demain}}` (une règle ne prend pas effet
dans le passé), `{{aujourdhui}}` et `{{hier}}` (dans le fuseau des quarts), `{{u_julie}}`, `{{u_chef}}`…
(les identifiants des comptes), `{{n_julie}}`, `{{n_chef}}`… (les noms de connexion, qui désignent les personnes dans la messagerie). Un `{{x}}` seul dans une valeur JSON garde le type de la valeur retenue.

## Écrire un scénario

- Étiqueter le scénario par **toutes** les exigences qu'il prouve, et seulement celles-là.
- Attendre une **valeur** (un statut, un motif, un compte), jamais une simple présence : un attendu qui
  passerait quoi qu'il arrive ne prouve rien.
- Dater dans le futur (2030 et au-delà) ce qui ne dépend pas de l'instant présent, et employer
  `{{demain}}` pour une règle : une date fixe finirait dans le passé.
- Ce qui exige d'attendre (expiration d'un tour, échéance de collecte) ne se joue pas en temps réel :
  le dire en commentaire dans la feature, nommer l'essai Java à horloge figée qui le prouve, et
  l'inscrire dans `SANS_SCENARIO` ou `REMARQUES` de `construire-tableau-bdd.py`.
