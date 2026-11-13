# 9. Charge, SLI, SLO et SLA du module des effectifs

- **Date** : 1er novembre 2026
- **Demande** : « Valide le tout avec des tests JMeter et optimise après ; valide les SLI, SLO,
  SLA et documente les rapports en `.md`. »
- **Outillage** : `perf/jmeter/` — méthode générale dans
  [docs/PERFORMANCE-JMETER.md](../PERFORMANCE-JMETER.md), mode d'emploi dans
  [perf/jmeter/README.md](../../perf/jmeter/README.md).

## 9.1 Le constat qui ouvre ce document

**Le module de l'avis n'était pas mesuré.** L'inventaire des routes dont les plans JMeter sont
générés datait d'avant son existence :

| | Avant | Après régénération |
|---|---:|---:|
| Points d'entrée inventoriés | 1 351 | **2 129** |
| Familles de plans | 35 | **60** |
| Routes `/api/effectifs` | **0** | **148** |

**778 routes n'avaient jamais été jouées sous charge**, dont les 148 du module des effectifs, et
avec elles 22 autres familles (`rapports`, `fonds`, `bi`, `etl`, `revenus`, `masse-salariale`,
`organisation`, `referentiel`, `habilitations`…). Un inventaire régénéré du routeur ne se périme
pas tout seul : il se périme parce que personne ne le régénère, et rien ne le disait.

## 9.2 Deux défauts de mesure, trouvés et corrigés

### Le 404 qui répond vite

Une assertion de plan ne refuse que les **5xx**. Un 404 est une réponse valide : la campagne le
compte comme un succès et mesure sa latence — deux millisecondes. **84 des 148 routes du module
portent un paramètre de chemin** ; aucune n'avait d'identifiant semé, toutes recevaient le jeton
neutre `x`. Le rapport donnait donc « 0 % d'erreurs, p95 de 7 ms, objectifs tenus » pour une
famille dont **75 requêtes sur 141 ne touchaient aucune donnée**.

Trois corrections :

1. `preparer.cjs` sème un **organisme d'effectifs** complet — unité, corps d'emploi, milieu,
   ressources, postes publiés, période générée et publiée, place attribuée, quota, profil de
   pointage, règle, campagne annuelle, périmètre, absence, feuille de temps avec ses segments ;
2. `generer-plans.cjs` reconnaît les paramètres du module au **préfixe exact** de la route, et non
   au dernier segment d'entité trouvé — `/effectifs/echanges` et `/effectifs/echanges/ressources`
   commencent au même caractère, et leur départage aurait été un hasard d'ordre de tri ;
3. `analyser.cjs` rend la **couverture utile** : la part des requêtes dont le code dominant est un
   2xx, et la liste nominative des autres avec leur code. `--min-2xx <pct>` fait échouer une
   campagne tenue pour concluante à vide.

### La colonne qui décale tout

`COLONNES` déclare les variables du CSV au plan ; `preparer.cjs` écrivait ses colonnes dans
l'ordre des clés de son objet. La colonne `moisCourant` était écrite **sans être déclarée** :
sans effet tant qu'elle était la dernière, elle a décalé toutes les suivantes dès qu'on en a
ajouté. `periodeId` recevait « 2026-11 », et **78 routes répondaient 404 pour cette seule raison**.

Le défaut dormait depuis l'ajout de `moisCourant`. Il est corrigé des deux côtés : `COLONNES`
ordonne le fichier, et `preparer.cjs` **refuse de semer** une colonne qu'il ne déclare pas.

**Effet mesuré de la correction : couverture utile 26 % → 45 %** (38 → 67 requêtes sur 149).

## 9.3 Ce qui est mesuré, et ce qui ne l'est pas

| Méthode | Requêtes en 2xx | Lecture |
|---|---|---|
| `GET` | **55 / 61 (90 %)** | Les lectures du module sont mesurées sur de vraies données. |
| `POST` | **8 / 69 (12 %)** | Les écritures ne le sont pas : le corps généré depuis les exemples OpenAPI est refusé par la validation (422), ou rejoué en conflit (409). |
| `PUT` / `PATCH` / `DELETE` | 4 / 19 | Même cause. |

Les 45 requêtes en 422 sont un **travail nommé, pas une approximation** : chaque exemple de corps
manquant ou incomplet est à compléter dans `openapiContrats.ts` (ADR 0028), et le rapport de
campagne les liste une par une. Tant qu'elles y sont, aucune conclusion ne peut être tirée sur la
latence des écritures du module — et le rapport le dit à la place de laisser croire le contraire.

## 9.4 SLI, SLO, SLA — les trois mots, et ce qu'ils engagent ici

| | Définition retenue | Où il vit |
|---|---|---|
| **SLI** | Ce qui est **mesuré** : centiles de latence (p50, p95, p99), taux d'erreur, débit. Jamais une moyenne — une moyenne cache la queue, et c'est la queue que l'usager subit. | Le `.jtl` de la campagne, résumé par `analyser.cjs` |
| **SLO** | L'objectif qu'on **se fixe**, par classe d'endpoint. Plus serré que le SLA, pour que le dépassement se voie avant d'être une faute. | `perf/jmeter/slo.json`, et par opération dans `operations.json` |
| **SLA** | Ce qu'on **promet** au client. Pour cet avis : **E-TN-17 — « les fonctionnalités transactionnelles de base s'exécutent à l'intérieur de 2 secondes »**. | `slo.json` → `sla`, et par opération dans `operations.json` |

**Budget d'erreur** : SLO de disponibilité 99,9 %, soit 0,1 % des requêtes valides admises en
échec sur la fenêtre mensuelle. Au-delà, le budget est consommé et le déploiement se fige jusqu'à
correction. Le SLA contractuel reste à 99,5 %, pour que l'engagement garde une marge sur l'objectif.

### Les classes d'endpoint

| Classe | SLO p95 | SLO p99 | Erreurs | Ce qu'elle couvre |
|---|---:|---:|---:|---|
| santé | 100 ms | 300 ms | 0 % | `GET /api/health` |
| connexion | 800 ms | 1 500 ms | 0,1 % | `POST /api/auth/login` (bcrypt, volontairement coûteux) |
| lecture | 500 ms | 1 000 ms | 0,1 % | tout `GET` |
| écriture | 1 000 ms | 2 000 ms | 0,5 % | `POST`, `PUT`, `PATCH`, `DELETE` |
| rapport | 2 000 ms | 4 000 ms | 0,5 % | exports, tableaux de bord, statistiques |

## 9.5 Les six transactions de base du module, au catalogue de l'accord

Aucune transaction d'effectifs ne figurait au catalogue de l'accord de niveau de service, alors que
E-TN-17 porte précisément sur elles. Six y entrent, chacune jouée **seule**, à débit fixe, dans
deux contextes de données (typique et volumineux) :

| Opération | Route | SLO p95 (typique) | SLA p95 (typique) | Exigences servies |
|---|---|---:|---:|---|
| LireComblementDeLaPeriode | `GET …/periodes/{id}/comblement` | 120 ms | 800 ms | E-GR-1, E-GR-3 |
| LireEquipagesDeLaPeriode | `GET …/periodes/{id}/equipages` | 100 ms | 800 ms | E-CH-2 à E-CH-5, E-GR-5 |
| LireMaFeuilleDeTemps | `GET /api/effectifs/temps/moi` | 60 ms | 500 ms | E-RF-1 à E-RF-3 |
| LireMesDisponibilites | `GET /api/effectifs/disponibilites/moi` | 60 ms | 500 ms | E-GN-13, E-GN-15, E-GN-16, E-GR-11 |
| PointerUneEntree | `POST /api/effectifs/presence/pointages` | 150 ms | 1 000 ms | E-GP-1, E-GP-4, E-GP-10 à E-GP-13 |
| PreparerLesFeuillesDeTemps | `POST …/temps/feuilles/preparer` | 400 ms | 2 000 ms | E-RF-1 |

**`PointerUneEntree` dicte le dimensionnement.** C'est la seule dont le pic est à la fois
quotidien et concentré : au changement de garde, cinq cents paramédics pointent dans la même
minute, et la requête évalue en même temps les règles du quart (retard, emplacement, preuve).

**`PreparerLesFeuillesDeTemps` n'est pas une transaction de base.** C'est une opération de lot :
elle bâtit une feuille par ressource de l'unité. E-TN-17 ne s'y applique pas ; son engagement est
énoncé à part, et son exécution se place hors période de pointe. Le dire est plus honnête que de
lui appliquer une borne de 2 s qu'elle ne devrait pas avoir à tenir.

## 9.6 Les mesures du 1er novembre 2026

**Contexte** : backend Node sur un port privé, base en mémoire, organisme d'effectifs semé par
l'API ; 4 utilisateurs virtuels, 40 s pour le balayage ; 10 req/s à débit fixe, 45 s par
opération. **Charge de la machine : 14,9 pour 12 cœurs** — voir le §9.7 avant de citer un temps.

### Le balayage des 148 routes du module

| | Valeur |
|---|---|
| Requêtes distinctes jouées | 149 (148 routes + la connexion du `setUp`) |
| **Couverture utile** | **67 / 149 en 2xx (45 %)** — contre 38 / 149 (26 %) avant la correction du décalage de colonnes |
| Codes dominants | `200` × 67 · `422` × 45 · `404` × 26 · `409` × 11 |
| Taux d'erreur serveur (5xx) | **0,00 %** |
| Latence des réponses 2xx (hors connexion) | p50 **3 ms** · p95 **6 ms** · p99 **12 ms** · max **19 ms** |

Par méthode, et c'est là que le chiffre agrégé trompe :

| Méthode | Requêtes en 2xx | Ce qu'on peut en dire |
|---|---|---|
| `GET` | **55 / 61 (90 %)** | Les lectures du module sont mesurées sur de vraies données. |
| `POST` | **8 / 69 (12 %)** | Les écritures ne sont **pas** mesurées : corps refusé par la validation (422) ou rejeu en conflit (409). |
| `PUT` | 3 / 14 (21 %) | Idem. |
| `PATCH` · `DELETE` | 0 / 1 · 1 / 4 | Idem. |

Un agrégé à 45 % laisserait croire à une demi-mesure uniforme. La vérité est plus tranchée : **les
lectures sont mesurées, les écritures ne le sont pas du tout.** Les 45 corps refusés sont à
compléter dans `openapiContrats.ts` (ADR 0028) ; le rapport de campagne les nomme un par un.

### Les six transactions de base, chacune jouée seule

| Opération | N | Codes | p50 | p95 | p99 | max | SLO p95 | SLA p95 | Verdict |
|---|---:|---|---:|---:|---:|---:|---:|---:|---|
| LireComblementDeLaPeriode | 453 | 200×453 | 3 ms | 6 ms | 10 ms | 17 ms | 120 ms | 800 ms | ✅ |
| LireEquipagesDeLaPeriode | 453 | 200×453 | 3 ms | 5 ms | 15 ms | 25 ms | 100 ms | 800 ms | ✅ |
| LireMaFeuilleDeTemps | 452 | 200×452 | 2 ms | 6 ms | 17 ms | 39 ms | 60 ms | 500 ms | ✅ |
| LireMesDisponibilites | 453 | 200×453 | 2 ms | 4 ms | 9 ms | 25 ms | 60 ms | 500 ms | ✅ |
| PointerUneEntree | 453 | 422×453 | 4 ms | 6 ms | 14 ms | 93 ms | 150 ms | 1000 ms | ⚠ non 2xx |
| PreparerLesFeuillesDeTemps | 452 | 201×452 | 4 ms | 8 ms | 17 ms | 63 ms | 400 ms | 2000 ms | ✅ |

Cinq des six tiennent leur SLO avec deux ordres de grandeur de marge. La sixième, le pointage,
**n'est pas mesurée** : le pointage est une machine à états par place et par personne, et rejouer
`ENTREE` sur la même place est refusé — « après l'entrée, on ne peut pointer que le début de pause
ou la sortie ». À débit fixe sur trois places, la campagne mesure donc le coût de la **garde**, pas
celui de l'écriture. Une mesure fidèle exige le profil volumineux : une place distincte par
utilisateur virtuel et par itération. Le dire vaut mieux que de publier 7 ms en laissant croire
qu'on a mesuré un pointage.

`PreparerLesFeuillesDeTemps` répond 201 sur le chemin **idempotent** : la période étant déjà
préparée, elle renvoie les feuilles existantes. Le chemin coûteux — bâtir une feuille par ressource
depuis l'horaire et les pointages — reste à mesurer sur le contexte volumineux.

## 9.7 Pourquoi ces temps ne sont pas une référence — et ce qu'ils permettent tout de même

La machine portait une **charge moyenne de 14,9 pour 12 cœurs** au moment de la campagne (une
machine virtuelle à 386 % d'un cœur, un assistant à 123 %, un lecteur vidéo, deux JVM — des
travaux étrangers à ce dépôt). L'ADR 0046 l'a posé après une mesure à 234 de charge : **au-dessus
du nombre de cœurs, un temps mesuré n'est pas une référence**, et le publier comme tel serait
malhonnête.

Il reste une inférence valide, et une seule, parce qu'elle va dans un seul sens : **la contention
ne peut qu'allonger une latence, jamais la raccourcir**. Un p95 mesuré à 7 ms sur une machine
chargée ne descendra pas en dessous sur une machine au repos. La conclusion « les lectures
transactionnelles du module sont deux ordres de grandeur à l'intérieur de la borne de 2 s de
E-TN-17 » tient donc *a fortiori*.

Ce qu'elle ne couvre pas, et qui reste à faire sur une machine au repos :

- **la charge contractuelle** — 2 500 utilisateurs simultanés dont 500 en écriture (clause 2.3) ;
  la campagne ci-dessus en portait quatre ;
- **le contexte volumineux** — 150 ressources, 1 500 saisies ; la campagne portait le contexte
  typique ;
- **les écritures** — tant que les 45 corps refusés ne sont pas complétés.

E-TN-17 reste donc **🟡 partiel** : la borne est tenue très largement sur les lectures, à faible
charge, sur une machine dont l'état interdit d'en faire une référence. L'annoncer acquis sur cette
base serait exactement l'erreur que la matrice de couverture a commise trois fois.

## 9.8 Rejouer la campagne

```bash
# 1. Un backend Node sur un port privé, base neuve et jetable
cd backend && PORT=4030 PAYROLL_DB=":memory:" npm run start &

# 2. L'organisme d'effectifs, semé par l'API ; l'établissement créé est écrit à côté
node perf/jmeter/preparer.cjs --env local-node --lignes 3
T=$(cat perf/jmeter/donnees/tenant-local-node.txt)

# 3. Les 148 routes du module ; la campagne échoue si moins de 80 % répondent 2xx
perf/jmeter/lancer.sh local-node plans/endpoints-effectifs.jmx -Jtenant=$T
node perf/jmeter/analyser.cjs perf/jmeter/resultats/local-node/<horodatage>/endpoints-effectifs.jtl \
  --env local-node --min-2xx 80

# 4. Les six transactions de base, chacune seule, à débit fixe
for OP in LireComblementDeLaPeriode LireEquipagesDeLaPeriode LireMaFeuilleDeTemps \
          LireMesDisponibilites PointerUneEntree PreparerLesFeuillesDeTemps; do
  perf/jmeter/lancer.sh local-node operations/$OP.jmx -Jtenant=$T
done

# 5. La charge contractuelle (machine au repos, charge < nombre de cœurs)
perf/jmeter/lancer.sh local-node plans/endpoints-effectifs.jmx -Jtenant=$T \
  -Jutilisateurs=2500 -Jduree=1200
```

Avant toute mesure : vérifier la charge (`uptime`, `sysctl -n hw.ncpu`). Une charge supérieure au
nombre de cœurs rend le résultat inexploitable — mieux vaut ne pas mesurer que publier un chiffre
qu'il faudra retirer.
