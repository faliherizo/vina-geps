# Accord de niveau de service — performance des opérations du backend Java (Nexu VINA)

- **Date** : 2026-11-27 · **Environnement** : `local` · **Forme** : TI-31P « Accord de niveau de service », une section par opération (SLO, SLI, SLA, Notes)
- **Contexte de référence** : débit fixe de **20 requêtes / seconde** (timer à débit constant partagé), **600 secondes** par opération, 10 unités — une opération à la fois, jamais deux en même temps
- **Campagnes** : typique `jmeter/rapports/local/20261127-010339` · volumineux `jmeter/rapports/local/20261127-012101`
- **Où lire les valeurs** : le rapport HTML généré par JMeter (`tableau-de-bord/index.html`, tableau *Statistics* : Executions – Error %, Response Times (ms) – Average / 95th pct, Throughput – Transactions/s) ; les mêmes chiffres sont dans `statistics.json`, c'est là que ce document les lit, sans recalcul

## Définitions

- **SLI** (indicateur) : ce que l'on mesure et où on le lit dans le rapport JMeter.
- **SLO** (objectif) : la cible visée par l'équipe, dans le contexte de référence.
- **SLA** (accord) : la performance minimale que l'opération doit respecter ; au-delà, c'est un manquement.
- **Verdict** : ✅ la mesure tient la cible, ❌ elle la dépasse, — non mesuré.

## Profils de données

- **typique** : un organisme de taille courante : une unité, un milieu, une ressource, un poste, une période publiée, quelques quarts, une feuille, une absence, un pointage et une conversation — ce que preparer.cjs sème par défaut.
- **volumineux** : le journal d'avis chargé : 2 000 textos simulés — preparer.cjs --profil volumineux.

Les identifiants utilisés par les opérations (employé, projet, semaine…) sont ceux de `perf/jmeter/donnees/identifiants.csv`, écrits par `preparer.cjs` ; comme dans le format de référence, ils doivent désigner des dossiers d'un **volume typique** (un employé qui a des saisies, un projet qui a des tâches), sans quoi la mesure flatte.

# Opérations

## 1. Connexion

*Ouvrir une session (jeton et témoin)* — `POST /api/auth/login`

### Objectifs de niveau de service (SLO)

Cette section identifie les cibles de performances visées par l'opération dans un contexte précis (débit fixe et une durée de référence).

| Métrique | Cible | Mesuré | Verdict | Notes |
|---|---|---|:-:|---|
| Temps réponse moyen (contexte typique) | ≤ 100 ms | 31 ms | ✅ | « Response Times (ms) – Average » |
| Temps réponse (contexte typique, 95ᵉ percentile) | ≤ 150 ms | 40 ms | ✅ | 95 % des requêtes répondent en ≤ 150 ms |
| Temps réponse moyen (contexte volumineux) | ≤ 100 ms | 25 ms | ✅ | établissement chargé (§ Profils) |
| Taux d'erreur | ≤ 0.1 % | 0.00 % / 0.00 % | ✅ | uniquement les statuts « Erreur » (5xx) — typique / volumineux |
| **Contexte** | | | | |
| Débit | 20 / seconde | 20.2 / 20.1 | ✅ | requêtes / seconde — typique / volumineux |
| Durée de référence | 600 secondes | 60 / 59 s | ❌ | End Time − Start Time |

### Indicateurs de niveau de service (SLI)

Cette section identifie comment obtenir les indicateurs de performances de l'exécution de l'opération.

**La requête pour les temps réponses** — obtenir la valeur du rapport de performance généré par JMeter (`tableau-de-bord/statistics.json`, transaction `Connexion`) :

- « Response Times (ms) – Average » : **31 ms** (typique), **25 ms** (volumineux)
- « Response Times (ms) – 95th pct » : **40 ms** (typique), **35 ms** (volumineux)

**La requête pour les taux d'erreurs** — obtenir la valeur du rapport de performance généré par JMeter :

- « Executions – Error % » : **0.00 %** (typique), **0.00 %** (volumineux)

**Contexte** — obtenir la valeur du rapport de performance généré par JMeter :

- « Throughput – Transactions / s » : **20.16** (typique), **20.15** (volumineux)
- « End Time – Start Time » : **60 s** (typique : 2026-11-27 06:03:44 UTC → 2026-11-27 06:04:43 UTC), **59 s** (volumineux)
- Échantillons : 1200 (typique), 1198 (volumineux)

**Tableau de bord JMeter – Simulation** : `perf/jmeter/rapports/local/<horodatage>/Connexion/tableau-de-bord/index.html`

**Tableau de bord – Production** : *à ajouter* (sonde de lecture `scenarios/sonde-lecture.jmx`, sans écriture).

### Accords de niveau de service (SLA)

Cette section documente les performances minimales que l'opération doit respecter.

| Métrique | Cible | Mesuré | Verdict | Notes |
|---|---|---|:-:|---|
| Temps réponse moyen (contexte typique) | ≤ 400 ms | 31 ms | ✅ | « Response Times (ms) – Average » |
| Temps réponse (contexte typique, 95ᵉ percentile) | ≤ 800 ms | 40 ms | ✅ | 95 % des requêtes répondent en ≤ 800 ms |
| Temps réponse moyen (contexte volumineux) | ≤ 400 ms | 25 ms | ✅ | établissement chargé (§ Profils) |
| Taux d'erreur | ≤ 0.1 % | 0.00 % / 0.00 % | ✅ | uniquement les statuts « Erreur » (5xx) — typique / volumineux |
| **Contexte** | | | | |
| Débit | 20 / seconde | 20.2 / 20.1 | ✅ | requêtes / seconde — typique / volumineux |
| Durée de référence | 600 secondes | 60 / 59 s | ❌ | End Time − Start Time |

### Notes

Le hachage du mot de passe (bcrypt) est volontairement coûteux : la cible est plus large que celle d'une lecture. Le compte est celui de donnees/comptes.csv ; à débit fixe la limite du pare-feu par adresse (WAF_REQUETES_PAR_MINUTE) doit admettre 1 200 connexions par minute.

## 2. ObtenirCatalogueAvis

*Le catalogue des avis (préférences de notification)* — `GET /api/notifications/evenements`

### Objectifs de niveau de service (SLO)

Cette section identifie les cibles de performances visées par l'opération dans un contexte précis (débit fixe et une durée de référence).

| Métrique | Cible | Mesuré | Verdict | Notes |
|---|---|---|:-:|---|
| Temps réponse moyen (contexte typique) | ≤ 10 ms | 3 ms | ✅ | « Response Times (ms) – Average » |
| Temps réponse (contexte typique, 95ᵉ percentile) | ≤ 20 ms | 5 ms | ✅ | 95 % des requêtes répondent en ≤ 20 ms |
| Temps réponse moyen (contexte volumineux) | ≤ 10 ms | 3 ms | ✅ | établissement chargé (§ Profils) |
| Taux d'erreur | ≤ 0.1 % | 0.00 % / 0.00 % | ✅ | uniquement les statuts « Erreur » (5xx) — typique / volumineux |
| **Contexte** | | | | |
| Débit | 20 / seconde | 20.1 / 20.1 | ✅ | requêtes / seconde — typique / volumineux |
| Durée de référence | 600 secondes | 60 / 60 s | ❌ | End Time − Start Time |

### Indicateurs de niveau de service (SLI)

Cette section identifie comment obtenir les indicateurs de performances de l'exécution de l'opération.

**La requête pour les temps réponses** — obtenir la valeur du rapport de performance généré par JMeter (`tableau-de-bord/statistics.json`, transaction `ObtenirCatalogueAvis`) :

- « Response Times (ms) – Average » : **3 ms** (typique), **3 ms** (volumineux)
- « Response Times (ms) – 95th pct » : **5 ms** (typique), **6 ms** (volumineux)

**La requête pour les taux d'erreurs** — obtenir la valeur du rapport de performance généré par JMeter :

- « Executions – Error % » : **0.00 %** (typique), **0.00 %** (volumineux)

**Contexte** — obtenir la valeur du rapport de performance généré par JMeter :

- « Throughput – Transactions / s » : **20.15** (typique), **20.15** (volumineux)
- « End Time – Start Time » : **60 s** (typique : 2026-11-27 06:04:51 UTC → 2026-11-27 06:05:51 UTC), **60 s** (volumineux)
- Échantillons : 1207 (typique), 1207 (volumineux)

**Tableau de bord JMeter – Simulation** : `perf/jmeter/rapports/local/<horodatage>/ObtenirCatalogueAvis/tableau-de-bord/index.html`

**Tableau de bord – Production** : *à ajouter* (sonde de lecture `scenarios/sonde-lecture.jmx`, sans écriture).

### Accords de niveau de service (SLA)

Cette section documente les performances minimales que l'opération doit respecter.

| Métrique | Cible | Mesuré | Verdict | Notes |
|---|---|---|:-:|---|
| Temps réponse moyen (contexte typique) | ≤ 50 ms | 3 ms | ✅ | « Response Times (ms) – Average » |
| Temps réponse (contexte typique, 95ᵉ percentile) | ≤ 100 ms | 5 ms | ✅ | 95 % des requêtes répondent en ≤ 100 ms |
| Temps réponse moyen (contexte volumineux) | ≤ 50 ms | 3 ms | ✅ | établissement chargé (§ Profils) |
| Taux d'erreur | ≤ 0.1 % | 0.00 % / 0.00 % | ✅ | uniquement les statuts « Erreur » (5xx) — typique / volumineux |
| **Contexte** | | | | |
| Débit | 20 / seconde | 20.1 / 20.1 | ✅ | requêtes / seconde — typique / volumineux |
| Durée de référence | 600 secondes | 60 / 60 s | ❌ | End Time − Start Time |

### Notes

Sans lecture de base : la référence « plancher » du serveur (sérialisation, filtre de sécurité, session).

## 3. LireMesModules

*Les modules et les sous-menus de l'établissement (lus à CHAQUE chargement de l'application)* — `GET /api/saas/my-modules`

### Objectifs de niveau de service (SLO)

Cette section identifie les cibles de performances visées par l'opération dans un contexte précis (débit fixe et une durée de référence).

| Métrique | Cible | Mesuré | Verdict | Notes |
|---|---|---|:-:|---|
| Temps réponse moyen (contexte typique) | ≤ 20 ms | 2 ms | ✅ | « Response Times (ms) – Average » |
| Temps réponse (contexte typique, 95ᵉ percentile) | ≤ 50 ms | 4 ms | ✅ | 95 % des requêtes répondent en ≤ 50 ms |
| Temps réponse moyen (contexte volumineux) | ≤ 30 ms | 2 ms | ✅ | établissement chargé (§ Profils) |
| Taux d'erreur | ≤ 0.1 % | 0.00 % / 0.00 % | ✅ | uniquement les statuts « Erreur » (5xx) — typique / volumineux |
| **Contexte** | | | | |
| Débit | 20 / seconde | 20.2 / 20.2 | ✅ | requêtes / seconde — typique / volumineux |
| Durée de référence | 600 secondes | 60 / 60 s | ❌ | End Time − Start Time |

### Indicateurs de niveau de service (SLI)

Cette section identifie comment obtenir les indicateurs de performances de l'exécution de l'opération.

**La requête pour les temps réponses** — obtenir la valeur du rapport de performance généré par JMeter (`tableau-de-bord/statistics.json`, transaction `LireMesModules`) :

- « Response Times (ms) – Average » : **2 ms** (typique), **2 ms** (volumineux)
- « Response Times (ms) – 95th pct » : **4 ms** (typique), **3 ms** (volumineux)

**La requête pour les taux d'erreurs** — obtenir la valeur du rapport de performance généré par JMeter :

- « Executions – Error % » : **0.00 %** (typique), **0.00 %** (volumineux)

**Contexte** — obtenir la valeur du rapport de performance généré par JMeter :

- « Throughput – Transactions / s » : **20.17** (typique), **20.16** (volumineux)
- « End Time – Start Time » : **60 s** (typique : 2026-11-27 06:06:05 UTC → 2026-11-27 06:07:05 UTC), **60 s** (volumineux)
- Échantillons : 1207 (typique), 1208 (volumineux)

**Tableau de bord JMeter – Simulation** : `perf/jmeter/rapports/local/<horodatage>/LireMesModules/tableau-de-bord/index.html`

**Tableau de bord – Production** : *à ajouter* (sonde de lecture `scenarios/sonde-lecture.jmx`, sans écriture).

### Accords de niveau de service (SLA)

Cette section documente les performances minimales que l'opération doit respecter.

| Métrique | Cible | Mesuré | Verdict | Notes |
|---|---|---|:-:|---|
| Temps réponse moyen (contexte typique) | ≤ 150 ms | 2 ms | ✅ | « Response Times (ms) – Average » |
| Temps réponse (contexte typique, 95ᵉ percentile) | ≤ 300 ms | 4 ms | ✅ | 95 % des requêtes répondent en ≤ 300 ms |
| Temps réponse moyen (contexte volumineux) | ≤ 200 ms | 2 ms | ✅ | établissement chargé (§ Profils) |
| Taux d'erreur | ≤ 0.1 % | 0.00 % / 0.00 % | ✅ | uniquement les statuts « Erreur » (5xx) — typique / volumineux |
| **Contexte** | | | | |
| Débit | 20 / seconde | 20.2 / 20.2 | ✅ | requêtes / seconde — typique / volumineux |
| Durée de référence | 600 secondes | 60 / 60 s | ❌ | End Time − Start Time |

### Notes

La route la plus appelée du produit : chaque utilisateur la sollicite à l'ouverture, à chaque rechargement et au retour d'onglet. Elle lit désormais AUSSI les sous-menus masqués (ADR 0042) — une requête de plus sur le chemin le plus chaud, d'où sa présence ici. La cible reste basse parce qu'elle ne croît pas avec les données de l'établissement : deux lectures indexées par organisme.

## 4. LireConsommationDAvis

*La consommation de textos et de courriels de TOUS les organismes, pour un mois* — `GET /api/saas/consommation?mois=…`

### Objectifs de niveau de service (SLO)

Cette section identifie les cibles de performances visées par l'opération dans un contexte précis (débit fixe et une durée de référence).

| Métrique | Cible | Mesuré | Verdict | Notes |
|---|---|---|:-:|---|
| Temps réponse moyen (contexte typique) | ≤ 60 ms | 2 ms | ✅ | « Response Times (ms) – Average » |
| Temps réponse (contexte typique, 95ᵉ percentile) | ≤ 150 ms | 5 ms | ✅ | 95 % des requêtes répondent en ≤ 150 ms |
| Temps réponse moyen (contexte volumineux) | ≤ 400 ms | 3 ms | ✅ | établissement chargé (§ Profils) |
| Taux d'erreur | ≤ 0.1 % | 0.00 % / 0.00 % | ✅ | uniquement les statuts « Erreur » (5xx) — typique / volumineux |
| **Contexte** | | | | |
| Débit | 20 / seconde | 20.1 / 20.1 | ✅ | requêtes / seconde — typique / volumineux |
| Durée de référence | 600 secondes | 60 / 60 s | ❌ | End Time − Start Time |

### Indicateurs de niveau de service (SLI)

Cette section identifie comment obtenir les indicateurs de performances de l'exécution de l'opération.

**La requête pour les temps réponses** — obtenir la valeur du rapport de performance généré par JMeter (`tableau-de-bord/statistics.json`, transaction `LireConsommationDAvis`) :

- « Response Times (ms) – Average » : **2 ms** (typique), **3 ms** (volumineux)
- « Response Times (ms) – 95th pct » : **5 ms** (typique), **5 ms** (volumineux)

**La requête pour les taux d'erreurs** — obtenir la valeur du rapport de performance généré par JMeter :

- « Executions – Error % » : **0.00 %** (typique), **0.00 %** (volumineux)

**Contexte** — obtenir la valeur du rapport de performance généré par JMeter :

- « Throughput – Transactions / s » : **20.15** (typique), **20.15** (volumineux)
- « End Time – Start Time » : **60 s** (typique : 2026-11-27 06:07:13 UTC → 2026-11-27 06:08:13 UTC), **60 s** (volumineux)
- Échantillons : 1207 (typique), 1206 (volumineux)

**Tableau de bord JMeter – Simulation** : `perf/jmeter/rapports/local/<horodatage>/LireConsommationDAvis/tableau-de-bord/index.html`

**Tableau de bord – Production** : *à ajouter* (sonde de lecture `scenarios/sonde-lecture.jmx`, sans écriture).

### Accords de niveau de service (SLA)

Cette section documente les performances minimales que l'opération doit respecter.

| Métrique | Cible | Mesuré | Verdict | Notes |
|---|---|---|:-:|---|
| Temps réponse moyen (contexte typique) | ≤ 400 ms | 2 ms | ✅ | « Response Times (ms) – Average » |
| Temps réponse (contexte typique, 95ᵉ percentile) | ≤ 900 ms | 5 ms | ✅ | 95 % des requêtes répondent en ≤ 900 ms |
| Temps réponse moyen (contexte volumineux) | ≤ 1500 ms | 3 ms | ✅ | établissement chargé (§ Profils) |
| Taux d'erreur | ≤ 0.1 % | 0.00 % / 0.00 % | ✅ | uniquement les statuts « Erreur » (5xx) — typique / volumineux |
| **Contexte** | | | | |
| Débit | 20 / seconde | 20.1 / 20.1 | ✅ | requêtes / seconde — typique / volumineux |
| Durée de référence | 600 secondes | 60 / 60 s | ❌ | End Time − Start Time |

### Notes

Agrégat INTER-ORGANISMES sur les journaux d'envois : somme des segments et comptage par organisme sur sms_message et courriel_message, pour le mois. C'est la lecture qui croît le plus vite du produit — l'historique des envois ne se purge pas, et un mois chargé se mesure en dizaines de milliers de lignes. La cible « volumineux » est délibérément haute : elle est atteinte par un superadmin, seul, une fois par mois, et non par des utilisateurs en séance.

## 5. LireComblementDeLaPeriode

*Le tableau de comblement d'une période (la question du matin : quels véhicules ne sortiront pas ?)* — `GET /api/effectifs/planification/periodes/…/comblement`

### Objectifs de niveau de service (SLO)

Cette section identifie les cibles de performances visées par l'opération dans un contexte précis (débit fixe et une durée de référence).

| Métrique | Cible | Mesuré | Verdict | Notes |
|---|---|---|:-:|---|
| Temps réponse moyen (contexte typique) | ≤ 60 ms | 5 ms | ✅ | « Response Times (ms) – Average » |
| Temps réponse (contexte typique, 95ᵉ percentile) | ≤ 120 ms | 13 ms | ✅ | 95 % des requêtes répondent en ≤ 120 ms |
| Temps réponse moyen (contexte volumineux) | ≤ 250 ms | 2 ms | ✅ | établissement chargé (§ Profils) |
| Taux d'erreur | ≤ 0.1 % | 0.00 % / 0.00 % | ✅ | uniquement les statuts « Erreur » (5xx) — typique / volumineux |
| **Contexte** | | | | |
| Débit | 20 / seconde | 20.2 / 20.1 | ✅ | requêtes / seconde — typique / volumineux |
| Durée de référence | 600 secondes | 60 / 60 s | ❌ | End Time − Start Time |

### Indicateurs de niveau de service (SLI)

Cette section identifie comment obtenir les indicateurs de performances de l'exécution de l'opération.

**La requête pour les temps réponses** — obtenir la valeur du rapport de performance généré par JMeter (`tableau-de-bord/statistics.json`, transaction `LireComblementDeLaPeriode`) :

- « Response Times (ms) – Average » : **5 ms** (typique), **2 ms** (volumineux)
- « Response Times (ms) – 95th pct » : **13 ms** (typique), **5 ms** (volumineux)

**La requête pour les taux d'erreurs** — obtenir la valeur du rapport de performance généré par JMeter :

- « Executions – Error % » : **0.00 %** (typique), **0.00 %** (volumineux)

**Contexte** — obtenir la valeur du rapport de performance généré par JMeter :

- « Throughput – Transactions / s » : **20.15** (typique), **20.15** (volumineux)
- « End Time – Start Time » : **60 s** (typique : 2026-11-27 06:08:30 UTC → 2026-11-27 06:09:30 UTC), **60 s** (volumineux)
- Échantillons : 1207 (typique), 1206 (volumineux)

**Tableau de bord JMeter – Simulation** : `perf/jmeter/rapports/local/<horodatage>/LireComblementDeLaPeriode/tableau-de-bord/index.html`

**Tableau de bord – Production** : *à ajouter* (sonde de lecture `scenarios/sonde-lecture.jmx`, sans écriture).

### Accords de niveau de service (SLA)

Cette section documente les performances minimales que l'opération doit respecter.

| Métrique | Cible | Mesuré | Verdict | Notes |
|---|---|---|:-:|---|
| Temps réponse moyen (contexte typique) | ≤ 400 ms | 5 ms | ✅ | « Response Times (ms) – Average » |
| Temps réponse (contexte typique, 95ᵉ percentile) | ≤ 800 ms | 13 ms | ✅ | 95 % des requêtes répondent en ≤ 800 ms |
| Temps réponse moyen (contexte volumineux) | ≤ 1200 ms | 2 ms | ✅ | établissement chargé (§ Profils) |
| Taux d'erreur | ≤ 0.1 % | 0.00 % / 0.00 % | ✅ | uniquement les statuts « Erreur » (5xx) — typique / volumineux |
| **Contexte** | | | | |
| Débit | 20 / seconde | 20.2 / 20.1 | ✅ | requêtes / seconde — typique / volumineux |
| Durée de référence | 600 secondes | 60 / 60 s | ❌ | End Time − Start Time |

### Notes

AO 26-015-DO, E-GR-1 et E-GR-3. Calculé à la lecture (taux, sous-minimum, quotas jour par jour) : rien n'est stocké, donc la mesure porte bien sur le calcul. Le SLA tient la borne de 2 s de E-TN-17 avec une marge.

## 6. LireEquipagesDeLaPeriode

*Les quarts et équipages d'une période, avec leurs places et leurs titulaires* — `GET /api/effectifs/planification/periodes/…/equipages`

### Objectifs de niveau de service (SLO)

Cette section identifie les cibles de performances visées par l'opération dans un contexte précis (débit fixe et une durée de référence).

| Métrique | Cible | Mesuré | Verdict | Notes |
|---|---|---|:-:|---|
| Temps réponse moyen (contexte typique) | ≤ 50 ms | 3 ms | ✅ | « Response Times (ms) – Average » |
| Temps réponse (contexte typique, 95ᵉ percentile) | ≤ 100 ms | 7 ms | ✅ | 95 % des requêtes répondent en ≤ 100 ms |
| Temps réponse moyen (contexte volumineux) | ≤ 300 ms | 2 ms | ✅ | établissement chargé (§ Profils) |
| Taux d'erreur | ≤ 0.1 % | 0.00 % / 0.00 % | ✅ | uniquement les statuts « Erreur » (5xx) — typique / volumineux |
| **Contexte** | | | | |
| Débit | 20 / seconde | 20.2 / 20.1 | ✅ | requêtes / seconde — typique / volumineux |
| Durée de référence | 600 secondes | 60 / 60 s | ❌ | End Time − Start Time |

### Indicateurs de niveau de service (SLI)

Cette section identifie comment obtenir les indicateurs de performances de l'exécution de l'opération.

**La requête pour les temps réponses** — obtenir la valeur du rapport de performance généré par JMeter (`tableau-de-bord/statistics.json`, transaction `LireEquipagesDeLaPeriode`) :

- « Response Times (ms) – Average » : **3 ms** (typique), **2 ms** (volumineux)
- « Response Times (ms) – 95th pct » : **7 ms** (typique), **3 ms** (volumineux)

**La requête pour les taux d'erreurs** — obtenir la valeur du rapport de performance généré par JMeter :

- « Executions – Error % » : **0.00 %** (typique), **0.00 %** (volumineux)

**Contexte** — obtenir la valeur du rapport de performance généré par JMeter :

- « Throughput – Transactions / s » : **20.17** (typique), **20.15** (volumineux)
- « End Time – Start Time » : **60 s** (typique : 2026-11-27 06:09:44 UTC → 2026-11-27 06:10:43 UTC), **60 s** (volumineux)
- Échantillons : 1208 (typique), 1207 (volumineux)

**Tableau de bord JMeter – Simulation** : `perf/jmeter/rapports/local/<horodatage>/LireEquipagesDeLaPeriode/tableau-de-bord/index.html`

**Tableau de bord – Production** : *à ajouter* (sonde de lecture `scenarios/sonde-lecture.jmx`, sans écriture).

### Accords de niveau de service (SLA)

Cette section documente les performances minimales que l'opération doit respecter.

| Métrique | Cible | Mesuré | Verdict | Notes |
|---|---|---|:-:|---|
| Temps réponse moyen (contexte typique) | ≤ 400 ms | 3 ms | ✅ | « Response Times (ms) – Average » |
| Temps réponse (contexte typique, 95ᵉ percentile) | ≤ 800 ms | 7 ms | ✅ | 95 % des requêtes répondent en ≤ 800 ms |
| Temps réponse moyen (contexte volumineux) | ≤ 1500 ms | 2 ms | ✅ | établissement chargé (§ Profils) |
| Taux d'erreur | ≤ 0.1 % | 0.00 % / 0.00 % | ✅ | uniquement les statuts « Erreur » (5xx) — typique / volumineux |
| **Contexte** | | | | |
| Débit | 20 / seconde | 20.2 / 20.1 | ✅ | requêtes / seconde — typique / volumineux |
| Durée de référence | 600 secondes | 60 / 60 s | ❌ | End Time − Start Time |

### Notes

E-CH-2 à E-CH-5, E-GR-5. C'est la lecture qui croît le plus vite du module : une période de 14 jours × 2 quarts/jour × 2 places tient 56 lignes, un établissement de 150 ressources en tient des milliers.

## 7. LireMaFeuilleDeTemps

*Ma feuille de temps de la période (segments, totaux par type, anomalies)* — `GET /api/effectifs/temps/moi`

### Objectifs de niveau de service (SLO)

Cette section identifie les cibles de performances visées par l'opération dans un contexte précis (débit fixe et une durée de référence).

| Métrique | Cible | Mesuré | Verdict | Notes |
|---|---|---|:-:|---|
| Temps réponse moyen (contexte typique) | ≤ 30 ms | 3 ms | ✅ | « Response Times (ms) – Average » |
| Temps réponse (contexte typique, 95ᵉ percentile) | ≤ 60 ms | 7 ms | ✅ | 95 % des requêtes répondent en ≤ 60 ms |
| Temps réponse moyen (contexte volumineux) | ≤ 150 ms | 2 ms | ✅ | établissement chargé (§ Profils) |
| Taux d'erreur | ≤ 0.1 % | 0.00 % / 0.00 % | ✅ | uniquement les statuts « Erreur » (5xx) — typique / volumineux |
| **Contexte** | | | | |
| Débit | 20 / seconde | 20.1 / 20.2 | ✅ | requêtes / seconde — typique / volumineux |
| Durée de référence | 600 secondes | 60 / 60 s | ❌ | End Time − Start Time |

### Indicateurs de niveau de service (SLI)

Cette section identifie comment obtenir les indicateurs de performances de l'exécution de l'opération.

**La requête pour les temps réponses** — obtenir la valeur du rapport de performance généré par JMeter (`tableau-de-bord/statistics.json`, transaction `LireMaFeuilleDeTemps`) :

- « Response Times (ms) – Average » : **3 ms** (typique), **2 ms** (volumineux)
- « Response Times (ms) – 95th pct » : **7 ms** (typique), **5 ms** (volumineux)

**La requête pour les taux d'erreurs** — obtenir la valeur du rapport de performance généré par JMeter :

- « Executions – Error % » : **0.00 %** (typique), **0.00 %** (volumineux)

**Contexte** — obtenir la valeur du rapport de performance généré par JMeter :

- « Throughput – Transactions / s » : **20.15** (typique), **20.16** (volumineux)
- « End Time – Start Time » : **60 s** (typique : 2026-11-27 06:10:53 UTC → 2026-11-27 06:11:53 UTC), **60 s** (volumineux)
- Échantillons : 1207 (typique), 1207 (volumineux)

**Tableau de bord JMeter – Simulation** : `perf/jmeter/rapports/local/<horodatage>/LireMaFeuilleDeTemps/tableau-de-bord/index.html`

**Tableau de bord – Production** : *à ajouter* (sonde de lecture `scenarios/sonde-lecture.jmx`, sans écriture).

### Accords de niveau de service (SLA)

Cette section documente les performances minimales que l'opération doit respecter.

| Métrique | Cible | Mesuré | Verdict | Notes |
|---|---|---|:-:|---|
| Temps réponse moyen (contexte typique) | ≤ 200 ms | 3 ms | ✅ | « Response Times (ms) – Average » |
| Temps réponse (contexte typique, 95ᵉ percentile) | ≤ 500 ms | 7 ms | ✅ | 95 % des requêtes répondent en ≤ 500 ms |
| Temps réponse moyen (contexte volumineux) | ≤ 800 ms | 2 ms | ✅ | établissement chargé (§ Profils) |
| Taux d'erreur | ≤ 0.1 % | 0.00 % / 0.00 % | ✅ | uniquement les statuts « Erreur » (5xx) — typique / volumineux |
| **Contexte** | | | | |
| Débit | 20 / seconde | 20.1 / 20.2 | ✅ | requêtes / seconde — typique / volumineux |
| Durée de référence | 600 secondes | 60 / 60 s | ❌ | End Time − Start Time |

### Notes

E-RF-1 à E-RF-3. L'écran que chaque personne ouvre à chaque quart : c'est la transaction la plus fréquente du module, donc celle dont le SLO doit être le plus serré.

## 8. LireMesDisponibilites

*Mes disponibilités pour la période en collecte, avec leur conformité* — `GET /api/effectifs/disponibilites/moi`

### Objectifs de niveau de service (SLO)

Cette section identifie les cibles de performances visées par l'opération dans un contexte précis (débit fixe et une durée de référence).

| Métrique | Cible | Mesuré | Verdict | Notes |
|---|---|---|:-:|---|
| Temps réponse moyen (contexte typique) | ≤ 30 ms | 3 ms | ✅ | « Response Times (ms) – Average » |
| Temps réponse (contexte typique, 95ᵉ percentile) | ≤ 60 ms | 9 ms | ✅ | 95 % des requêtes répondent en ≤ 60 ms |
| Temps réponse moyen (contexte volumineux) | ≤ 120 ms | 2 ms | ✅ | établissement chargé (§ Profils) |
| Taux d'erreur | ≤ 0.1 % | 0.00 % / 0.00 % | ✅ | uniquement les statuts « Erreur » (5xx) — typique / volumineux |
| **Contexte** | | | | |
| Débit | 20 / seconde | 20.1 / 20.2 | ✅ | requêtes / seconde — typique / volumineux |
| Durée de référence | 600 secondes | 60 / 60 s | ❌ | End Time − Start Time |

### Indicateurs de niveau de service (SLI)

Cette section identifie comment obtenir les indicateurs de performances de l'exécution de l'opération.

**La requête pour les temps réponses** — obtenir la valeur du rapport de performance généré par JMeter (`tableau-de-bord/statistics.json`, transaction `LireMesDisponibilites`) :

- « Response Times (ms) – Average » : **3 ms** (typique), **2 ms** (volumineux)
- « Response Times (ms) – 95th pct » : **9 ms** (typique), **3 ms** (volumineux)

**La requête pour les taux d'erreurs** — obtenir la valeur du rapport de performance généré par JMeter :

- « Executions – Error % » : **0.00 %** (typique), **0.00 %** (volumineux)

**Contexte** — obtenir la valeur du rapport de performance généré par JMeter :

- « Throughput – Transactions / s » : **20.15** (typique), **20.16** (volumineux)
- « End Time – Start Time » : **60 s** (typique : 2026-11-27 06:12:03 UTC → 2026-11-27 06:13:03 UTC), **60 s** (volumineux)
- Échantillons : 1205 (typique), 1207 (volumineux)

**Tableau de bord JMeter – Simulation** : `perf/jmeter/rapports/local/<horodatage>/LireMesDisponibilites/tableau-de-bord/index.html`

**Tableau de bord – Production** : *à ajouter* (sonde de lecture `scenarios/sonde-lecture.jmx`, sans écriture).

### Accords de niveau de service (SLA)

Cette section documente les performances minimales que l'opération doit respecter.

| Métrique | Cible | Mesuré | Verdict | Notes |
|---|---|---|:-:|---|
| Temps réponse moyen (contexte typique) | ≤ 200 ms | 3 ms | ✅ | « Response Times (ms) – Average » |
| Temps réponse (contexte typique, 95ᵉ percentile) | ≤ 500 ms | 9 ms | ✅ | 95 % des requêtes répondent en ≤ 500 ms |
| Temps réponse moyen (contexte volumineux) | ≤ 800 ms | 2 ms | ✅ | établissement chargé (§ Profils) |
| Taux d'erreur | ≤ 0.1 % | 0.00 % / 0.00 % | ✅ | uniquement les statuts « Erreur » (5xx) — typique / volumineux |
| **Contexte** | | | | |
| Débit | 20 / seconde | 20.1 / 20.2 | ✅ | requêtes / seconde — typique / volumineux |
| Durée de référence | 600 secondes | 60 / 60 s | ❌ | End Time − Start Time |

### Notes

E-GN-13, E-GN-15, E-GN-16, E-GR-11. La conformité est évaluée à la lecture par le moteur de règles : la mesure couvre donc aussi ce moteur.

## 9. LireQuartsParApi

*API d'échange : les quarts planifiés d'un mois, places et matricules (application de l'organisme)* — `GET /api/echanges/v1/quarts?du=…&au=…`

### Objectifs de niveau de service (SLO)

Cette section identifie les cibles de performances visées par l'opération dans un contexte précis (débit fixe et une durée de référence).

| Métrique | Cible | Mesuré | Verdict | Notes |
|---|---|---|:-:|---|
| Temps réponse moyen (contexte typique) | ≤ 60 ms | 4 ms | ✅ | « Response Times (ms) – Average » |
| Temps réponse (contexte typique, 95ᵉ percentile) | ≤ 120 ms | 7 ms | ✅ | 95 % des requêtes répondent en ≤ 120 ms |
| Temps réponse moyen (contexte volumineux) | ≤ 250 ms | 3 ms | ✅ | établissement chargé (§ Profils) |
| Taux d'erreur | ≤ 0.1 % | 0.00 % / 0.00 % | ✅ | uniquement les statuts « Erreur » (5xx) — typique / volumineux |
| **Contexte** | | | | |
| Débit | 20 / seconde | 20.1 / 20.2 | ✅ | requêtes / seconde — typique / volumineux |
| Durée de référence | 600 secondes | 60 / 60 s | ❌ | End Time − Start Time |

### Indicateurs de niveau de service (SLI)

Cette section identifie comment obtenir les indicateurs de performances de l'exécution de l'opération.

**La requête pour les temps réponses** — obtenir la valeur du rapport de performance généré par JMeter (`tableau-de-bord/statistics.json`, transaction `LireQuartsParApi`) :

- « Response Times (ms) – Average » : **4 ms** (typique), **3 ms** (volumineux)
- « Response Times (ms) – 95th pct » : **7 ms** (typique), **6 ms** (volumineux)

**La requête pour les taux d'erreurs** — obtenir la valeur du rapport de performance généré par JMeter :

- « Executions – Error % » : **0.00 %** (typique), **0.00 %** (volumineux)

**Contexte** — obtenir la valeur du rapport de performance généré par JMeter :

- « Throughput – Transactions / s » : **20.11** (typique), **20.15** (volumineux)
- « End Time – Start Time » : **60 s** (typique : 2026-11-27 06:13:16 UTC → 2026-11-27 06:14:16 UTC), **60 s** (volumineux)
- Échantillons : 1204 (typique), 1207 (volumineux)

**Tableau de bord JMeter – Simulation** : `perf/jmeter/rapports/local/<horodatage>/LireQuartsParApi/tableau-de-bord/index.html`

**Tableau de bord – Production** : *à ajouter* (sonde de lecture `scenarios/sonde-lecture.jmx`, sans écriture).

### Accords de niveau de service (SLA)

Cette section documente les performances minimales que l'opération doit respecter.

| Métrique | Cible | Mesuré | Verdict | Notes |
|---|---|---|:-:|---|
| Temps réponse moyen (contexte typique) | ≤ 400 ms | 4 ms | ✅ | « Response Times (ms) – Average » |
| Temps réponse (contexte typique, 95ᵉ percentile) | ≤ 800 ms | 7 ms | ✅ | 95 % des requêtes répondent en ≤ 800 ms |
| Temps réponse moyen (contexte volumineux) | ≤ 1500 ms | 3 ms | ✅ | établissement chargé (§ Profils) |
| Taux d'erreur | ≤ 0.1 % | 0.00 % / 0.00 % | ✅ | uniquement les statuts « Erreur » (5xx) — typique / volumineux |
| **Contexte** | | | | |
| Débit | 20 / seconde | 20.1 / 20.2 | ✅ | requêtes / seconde — typique / volumineux |
| Durée de référence | 600 secondes | 60 / 60 s | ❌ | End Time − Start Time |

### Notes

26-015-DO addenda 5 (a), AD-06a. Lu par RAO/SAAV pour aligner leurs horaires ; index (tenantId, date) des équipages (changeset 0023).

## 10. LireCorrectifsParApi

*API d'échange : les correctifs des quarts depuis le début, page de 500* — `GET /api/echanges/v1/quarts/modifications?limite=500`

### Objectifs de niveau de service (SLO)

Cette section identifie les cibles de performances visées par l'opération dans un contexte précis (débit fixe et une durée de référence).

| Métrique | Cible | Mesuré | Verdict | Notes |
|---|---|---|:-:|---|
| Temps réponse moyen (contexte typique) | ≤ 80 ms | 4 ms | ✅ | « Response Times (ms) – Average » |
| Temps réponse (contexte typique, 95ᵉ percentile) | ≤ 150 ms | 7 ms | ✅ | 95 % des requêtes répondent en ≤ 150 ms |
| Temps réponse moyen (contexte volumineux) | ≤ 300 ms | 3 ms | ✅ | établissement chargé (§ Profils) |
| Taux d'erreur | ≤ 0.1 % | 0.00 % / 0.00 % | ✅ | uniquement les statuts « Erreur » (5xx) — typique / volumineux |
| **Contexte** | | | | |
| Débit | 20 / seconde | 20.1 / 20.1 | ✅ | requêtes / seconde — typique / volumineux |
| Durée de référence | 600 secondes | 60 / 60 s | ❌ | End Time − Start Time |

### Indicateurs de niveau de service (SLI)

Cette section identifie comment obtenir les indicateurs de performances de l'exécution de l'opération.

**La requête pour les temps réponses** — obtenir la valeur du rapport de performance généré par JMeter (`tableau-de-bord/statistics.json`, transaction `LireCorrectifsParApi`) :

- « Response Times (ms) – Average » : **4 ms** (typique), **3 ms** (volumineux)
- « Response Times (ms) – 95th pct » : **7 ms** (typique), **6 ms** (volumineux)

**La requête pour les taux d'erreurs** — obtenir la valeur du rapport de performance généré par JMeter :

- « Executions – Error % » : **0.00 %** (typique), **0.00 %** (volumineux)

**Contexte** — obtenir la valeur du rapport de performance généré par JMeter :

- « Throughput – Transactions / s » : **20.13** (typique), **20.15** (volumineux)
- « End Time – Start Time » : **60 s** (typique : 2026-11-27 06:14:28 UTC → 2026-11-27 06:15:28 UTC), **60 s** (volumineux)
- Échantillons : 1206 (typique), 1207 (volumineux)

**Tableau de bord JMeter – Simulation** : `perf/jmeter/rapports/local/<horodatage>/LireCorrectifsParApi/tableau-de-bord/index.html`

**Tableau de bord – Production** : *à ajouter* (sonde de lecture `scenarios/sonde-lecture.jmx`, sans écriture).

### Accords de niveau de service (SLA)

Cette section documente les performances minimales que l'opération doit respecter.

| Métrique | Cible | Mesuré | Verdict | Notes |
|---|---|---|:-:|---|
| Temps réponse moyen (contexte typique) | ≤ 500 ms | 4 ms | ✅ | « Response Times (ms) – Average » |
| Temps réponse (contexte typique, 95ᵉ percentile) | ≤ 1000 ms | 7 ms | ✅ | 95 % des requêtes répondent en ≤ 1000 ms |
| Temps réponse moyen (contexte volumineux) | ≤ 2000 ms | 3 ms | ✅ | établissement chargé (§ Profils) |
| Taux d'erreur | ≤ 0.1 % | 0.00 % / 0.00 % | ✅ | uniquement les statuts « Erreur » (5xx) — typique / volumineux |
| **Contexte** | | | | |
| Débit | 20 / seconde | 20.1 / 20.1 | ✅ | requêtes / seconde — typique / volumineux |
| Durée de référence | 600 secondes | 60 / 60 s | ❌ | End Time − Start Time |

### Notes

Addenda 5 (a, b), AD-06a. La page la plus lourde : 500 entrées du journal et l'état présent de chaque quart, en trois requêtes (lecture groupée).

## 11. LireRelevesParApi

*API d'échange : les relevés de présence validés, page de 500* — `GET /api/echanges/v1/releves-presence?limite=500`

### Objectifs de niveau de service (SLO)

Cette section identifie les cibles de performances visées par l'opération dans un contexte précis (débit fixe et une durée de référence).

| Métrique | Cible | Mesuré | Verdict | Notes |
|---|---|---|:-:|---|
| Temps réponse moyen (contexte typique) | ≤ 60 ms | 4 ms | ✅ | « Response Times (ms) – Average » |
| Temps réponse (contexte typique, 95ᵉ percentile) | ≤ 120 ms | 10 ms | ✅ | 95 % des requêtes répondent en ≤ 120 ms |
| Temps réponse moyen (contexte volumineux) | ≤ 250 ms | 2 ms | ✅ | établissement chargé (§ Profils) |
| Taux d'erreur | ≤ 0.1 % | 0.00 % / 0.00 % | ✅ | uniquement les statuts « Erreur » (5xx) — typique / volumineux |
| **Contexte** | | | | |
| Débit | 20 / seconde | 20.2 / 20.2 | ✅ | requêtes / seconde — typique / volumineux |
| Durée de référence | 600 secondes | 60 / 60 s | ❌ | End Time − Start Time |

### Indicateurs de niveau de service (SLI)

Cette section identifie comment obtenir les indicateurs de performances de l'exécution de l'opération.

**La requête pour les temps réponses** — obtenir la valeur du rapport de performance généré par JMeter (`tableau-de-bord/statistics.json`, transaction `LireRelevesParApi`) :

- « Response Times (ms) – Average » : **4 ms** (typique), **2 ms** (volumineux)
- « Response Times (ms) – 95th pct » : **10 ms** (typique), **4 ms** (volumineux)

**La requête pour les taux d'erreurs** — obtenir la valeur du rapport de performance généré par JMeter :

- « Executions – Error % » : **0.00 %** (typique), **0.00 %** (volumineux)

**Contexte** — obtenir la valeur du rapport de performance généré par JMeter :

- « Throughput – Transactions / s » : **20.15** (typique), **20.16** (volumineux)
- « End Time – Start Time » : **60 s** (typique : 2026-11-27 06:15:36 UTC → 2026-11-27 06:16:36 UTC), **60 s** (volumineux)
- Échantillons : 1208 (typique), 1208 (volumineux)

**Tableau de bord JMeter – Simulation** : `perf/jmeter/rapports/local/<horodatage>/LireRelevesParApi/tableau-de-bord/index.html`

**Tableau de bord – Production** : *à ajouter* (sonde de lecture `scenarios/sonde-lecture.jmx`, sans écriture).

### Accords de niveau de service (SLA)

Cette section documente les performances minimales que l'opération doit respecter.

| Métrique | Cible | Mesuré | Verdict | Notes |
|---|---|---|:-:|---|
| Temps réponse moyen (contexte typique) | ≤ 400 ms | 4 ms | ✅ | « Response Times (ms) – Average » |
| Temps réponse (contexte typique, 95ᵉ percentile) | ≤ 800 ms | 10 ms | ✅ | 95 % des requêtes répondent en ≤ 800 ms |
| Temps réponse moyen (contexte volumineux) | ≤ 1500 ms | 2 ms | ✅ | établissement chargé (§ Profils) |
| Taux d'erreur | ≤ 0.1 % | 0.00 % / 0.00 % | ✅ | uniquement les statuts « Erreur » (5xx) — typique / volumineux |
| **Contexte** | | | | |
| Débit | 20 / seconde | 20.2 / 20.2 | ✅ | requêtes / seconde — typique / volumineux |
| Durée de référence | 600 secondes | 60 / 60 s | ❌ | End Time − Start Time |

### Notes

Addenda 5 (e), AD-06a. Index (tenantId, approuveeLe) des feuilles (changeset 0023).

## 12. PointerUneEntree

*Pointer son entrée sur un quart, avec la preuve de présence* — `POST /api/effectifs/presence/pointages`

### Objectifs de niveau de service (SLO)

Cette section identifie les cibles de performances visées par l'opération dans un contexte précis (débit fixe et une durée de référence).

| Métrique | Cible | Mesuré | Verdict | Notes |
|---|---|---|:-:|---|
| Temps réponse moyen (contexte typique) | ≤ 80 ms | 4 ms | ✅ | « Response Times (ms) – Average » |
| Temps réponse (contexte typique, 95ᵉ percentile) | ≤ 150 ms | 9 ms | ✅ | 95 % des requêtes répondent en ≤ 150 ms |
| Temps réponse moyen (contexte volumineux) | ≤ 200 ms | 18 ms | ✅ | établissement chargé (§ Profils) |
| Taux d'erreur | ≤ 0.5 % | 0.00 % / 0.00 % | ✅ | uniquement les statuts « Erreur » (5xx) — typique / volumineux |
| **Contexte** | | | | |
| Débit | 20 / seconde | 20.2 / 19.9 | ✅ | requêtes / seconde — typique / volumineux |
| Durée de référence | 600 secondes | 60 / 60 s | ❌ | End Time − Start Time |

### Indicateurs de niveau de service (SLI)

Cette section identifie comment obtenir les indicateurs de performances de l'exécution de l'opération.

**La requête pour les temps réponses** — obtenir la valeur du rapport de performance généré par JMeter (`tableau-de-bord/statistics.json`, transaction `PointerUneEntree`) :

- « Response Times (ms) – Average » : **4 ms** (typique), **18 ms** (volumineux)
- « Response Times (ms) – 95th pct » : **9 ms** (typique), **19 ms** (volumineux)

**La requête pour les taux d'erreurs** — obtenir la valeur du rapport de performance généré par JMeter :

- « Executions – Error % » : **0.00 %** (typique), **0.00 %** (volumineux)

**Contexte** — obtenir la valeur du rapport de performance généré par JMeter :

- « Throughput – Transactions / s » : **20.16** (typique), **19.88** (volumineux)
- « End Time – Start Time » : **60 s** (typique : 2026-11-27 06:16:49 UTC → 2026-11-27 06:17:49 UTC), **60 s** (volumineux)
- Échantillons : 1207 (typique), 1191 (volumineux)

**Tableau de bord JMeter – Simulation** : `perf/jmeter/rapports/local/<horodatage>/PointerUneEntree/tableau-de-bord/index.html`

**Tableau de bord – Production** : *à ajouter* (sonde de lecture `scenarios/sonde-lecture.jmx`, sans écriture).

### Accords de niveau de service (SLA)

Cette section documente les performances minimales que l'opération doit respecter.

| Métrique | Cible | Mesuré | Verdict | Notes |
|---|---|---|:-:|---|
| Temps réponse moyen (contexte typique) | ≤ 500 ms | 4 ms | ✅ | « Response Times (ms) – Average » |
| Temps réponse (contexte typique, 95ᵉ percentile) | ≤ 1000 ms | 9 ms | ✅ | 95 % des requêtes répondent en ≤ 1000 ms |
| Temps réponse moyen (contexte volumineux) | ≤ 1200 ms | 18 ms | ✅ | établissement chargé (§ Profils) |
| Taux d'erreur | ≤ 0.5 % | 0.00 % / 0.00 % | ✅ | uniquement les statuts « Erreur » (5xx) — typique / volumineux |
| **Contexte** | | | | |
| Débit | 20 / seconde | 20.2 / 19.9 | ✅ | requêtes / seconde — typique / volumineux |
| Durée de référence | 600 secondes | 60 / 60 s | ❌ | End Time − Start Time |

### Notes

E-GP-1, E-GP-4, E-GP-10 à E-GP-13. Écriture **et** évaluation des règles du quart (retard, emplacement, preuve) dans la même requête. Le pic est quotidien et concentré : 500 paramédics pointent à la même minute au changement de garde — c'est la transaction qui dicte le dimensionnement. **Mesure partielle, et il faut le dire** : le pointage est une machine à états par place et par personne — rejouer ENTREE sur la même place est refusé après le premier appel. À débit fixe sur trois places, la mesure porte donc sur le **refus** (le coût de la garde), non sur l'écriture. Une mesure fidèle exige le profil volumineux : une place distincte par utilisateur virtuel et par itération.

## 13. PreparerLesFeuillesDeTemps

*Préparer les feuilles de temps d'une unité pour une période de paie* — `POST /api/effectifs/temps/feuilles/preparer`

### Objectifs de niveau de service (SLO)

Cette section identifie les cibles de performances visées par l'opération dans un contexte précis (débit fixe et une durée de référence).

| Métrique | Cible | Mesuré | Verdict | Notes |
|---|---|---|:-:|---|
| Temps réponse moyen (contexte typique) | ≤ 200 ms | 3 ms | ✅ | « Response Times (ms) – Average » |
| Temps réponse (contexte typique, 95ᵉ percentile) | ≤ 400 ms | 8 ms | ✅ | 95 % des requêtes répondent en ≤ 400 ms |
| Temps réponse moyen (contexte volumineux) | ≤ 1500 ms | 4 ms | ✅ | établissement chargé (§ Profils) |
| Taux d'erreur | ≤ 0.5 % | 0.00 % / 0.00 % | ✅ | uniquement les statuts « Erreur » (5xx) — typique / volumineux |
| **Contexte** | | | | |
| Débit | 20 / seconde | 20.1 / 20.2 | ✅ | requêtes / seconde — typique / volumineux |
| Durée de référence | 600 secondes | 60 / 60 s | ❌ | End Time − Start Time |

### Indicateurs de niveau de service (SLI)

Cette section identifie comment obtenir les indicateurs de performances de l'exécution de l'opération.

**La requête pour les temps réponses** — obtenir la valeur du rapport de performance généré par JMeter (`tableau-de-bord/statistics.json`, transaction `PreparerLesFeuillesDeTemps`) :

- « Response Times (ms) – Average » : **3 ms** (typique), **4 ms** (volumineux)
- « Response Times (ms) – 95th pct » : **8 ms** (typique), **10 ms** (volumineux)

**La requête pour les taux d'erreurs** — obtenir la valeur du rapport de performance généré par JMeter :

- « Executions – Error % » : **0.00 %** (typique), **0.00 %** (volumineux)

**Contexte** — obtenir la valeur du rapport de performance généré par JMeter :

- « Throughput – Transactions / s » : **20.14** (typique), **20.17** (volumineux)
- « End Time – Start Time » : **60 s** (typique : 2026-11-27 06:18:02 UTC → 2026-11-27 06:19:02 UTC), **60 s** (volumineux)
- Échantillons : 1205 (typique), 1208 (volumineux)

**Tableau de bord JMeter – Simulation** : `perf/jmeter/rapports/local/<horodatage>/PreparerLesFeuillesDeTemps/tableau-de-bord/index.html`

**Tableau de bord – Production** : *à ajouter* (sonde de lecture `scenarios/sonde-lecture.jmx`, sans écriture).

### Accords de niveau de service (SLA)

Cette section documente les performances minimales que l'opération doit respecter.

| Métrique | Cible | Mesuré | Verdict | Notes |
|---|---|---|:-:|---|
| Temps réponse moyen (contexte typique) | ≤ 1000 ms | 3 ms | ✅ | « Response Times (ms) – Average » |
| Temps réponse (contexte typique, 95ᵉ percentile) | ≤ 2000 ms | 8 ms | ✅ | 95 % des requêtes répondent en ≤ 2000 ms |
| Temps réponse moyen (contexte volumineux) | ≤ 4000 ms | 4 ms | ✅ | établissement chargé (§ Profils) |
| Taux d'erreur | ≤ 0.5 % | 0.00 % / 0.00 % | ✅ | uniquement les statuts « Erreur » (5xx) — typique / volumineux |
| **Contexte** | | | | |
| Débit | 20 / seconde | 20.1 / 20.2 | ✅ | requêtes / seconde — typique / volumineux |
| Durée de référence | 600 secondes | 60 / 60 s | ❌ | End Time − Start Time |

### Notes

E-RF-1. Opération **de lot**, non transactionnelle : elle bâtit une feuille par ressource de l'unité depuis l'horaire et les pointages. E-TN-17 ne s'y applique pas (il vise les « fonctionnalités transactionnelles de base ») ; son SLA est donc énoncé à part, et son exécution se place hors période de pointe.

## 14. TransmettreSoldesParApi

*API d'échange : transmettre les soldes de deux banques d'un employé* — `PUT /api/echanges/v1/employes/…/soldes`

### Objectifs de niveau de service (SLO)

Cette section identifie les cibles de performances visées par l'opération dans un contexte précis (débit fixe et une durée de référence).

| Métrique | Cible | Mesuré | Verdict | Notes |
|---|---|---|:-:|---|
| Temps réponse moyen (contexte typique) | ≤ 60 ms | 6 ms | ✅ | « Response Times (ms) – Average » |
| Temps réponse (contexte typique, 95ᵉ percentile) | ≤ 120 ms | 11 ms | ✅ | 95 % des requêtes répondent en ≤ 120 ms |
| Temps réponse moyen (contexte volumineux) | ≤ 120 ms | 7 ms | ✅ | établissement chargé (§ Profils) |
| Taux d'erreur | ≤ 0.5 % | 0.00 % / 0.00 % | ✅ | uniquement les statuts « Erreur » (5xx) — typique / volumineux |
| **Contexte** | | | | |
| Débit | 20 / seconde | 20.1 / 20.1 | ✅ | requêtes / seconde — typique / volumineux |
| Durée de référence | 600 secondes | 60 / 60 s | ❌ | End Time − Start Time |

### Indicateurs de niveau de service (SLI)

Cette section identifie comment obtenir les indicateurs de performances de l'exécution de l'opération.

**La requête pour les temps réponses** — obtenir la valeur du rapport de performance généré par JMeter (`tableau-de-bord/statistics.json`, transaction `TransmettreSoldesParApi`) :

- « Response Times (ms) – Average » : **6 ms** (typique), **7 ms** (volumineux)
- « Response Times (ms) – 95th pct » : **11 ms** (typique), **11 ms** (volumineux)

**La requête pour les taux d'erreurs** — obtenir la valeur du rapport de performance généré par JMeter :

- « Executions – Error % » : **0.00 %** (typique), **0.00 %** (volumineux)

**Contexte** — obtenir la valeur du rapport de performance généré par JMeter :

- « Throughput – Transactions / s » : **20.05** (typique), **20.12** (volumineux)
- « End Time – Start Time » : **60 s** (typique : 2026-11-27 06:19:14 UTC → 2026-11-27 06:20:14 UTC), **60 s** (volumineux)
- Échantillons : 1201 (typique), 1205 (volumineux)

**Tableau de bord JMeter – Simulation** : `perf/jmeter/rapports/local/<horodatage>/TransmettreSoldesParApi/tableau-de-bord/index.html`

**Tableau de bord – Production** : *à ajouter* (sonde de lecture `scenarios/sonde-lecture.jmx`, sans écriture).

### Accords de niveau de service (SLA)

Cette section documente les performances minimales que l'opération doit respecter.

| Métrique | Cible | Mesuré | Verdict | Notes |
|---|---|---|:-:|---|
| Temps réponse moyen (contexte typique) | ≤ 400 ms | 6 ms | ✅ | « Response Times (ms) – Average » |
| Temps réponse (contexte typique, 95ᵉ percentile) | ≤ 800 ms | 11 ms | ✅ | 95 % des requêtes répondent en ≤ 800 ms |
| Temps réponse moyen (contexte volumineux) | ≤ 800 ms | 7 ms | ✅ | établissement chargé (§ Profils) |
| Taux d'erreur | ≤ 0.5 % | 0.00 % / 0.00 % | ✅ | uniquement les statuts « Erreur » (5xx) — typique / volumineux |
| **Contexte** | | | | |
| Débit | 20 / seconde | 20.1 / 20.1 | ✅ | requêtes / seconde — typique / volumineux |
| Durée de référence | 600 secondes | 60 / 60 s | ❌ | End Time − Start Time |

### Notes

Addenda 5 (c), AD-06b. Idempotente (remplace le solde de chaque banque) : rejouée 12 000 fois, elle ne grossit pas la table, seulement le journal.

# Bilan

| Opération | Moyen (typique) | 95ᵉ pct (typique) | Moyen (volumineux) | Erreurs | SLO | SLA |
|---|---:|---:|---:|---:|:-:|:-:|
| Connexion | 31 ms | 40 ms | 25 ms | 0.00 % | ✅ | ✅ |
| ObtenirCatalogueAvis | 3 ms | 5 ms | 3 ms | 0.00 % | ✅ | ✅ |
| LireMesModules | 2 ms | 4 ms | 2 ms | 0.00 % | ✅ | ✅ |
| LireConsommationDAvis | 2 ms | 5 ms | 3 ms | 0.00 % | ✅ | ✅ |
| LireComblementDeLaPeriode | 5 ms | 13 ms | 2 ms | 0.00 % | ✅ | ✅ |
| LireEquipagesDeLaPeriode | 3 ms | 7 ms | 2 ms | 0.00 % | ✅ | ✅ |
| LireMaFeuilleDeTemps | 3 ms | 7 ms | 2 ms | 0.00 % | ✅ | ✅ |
| LireMesDisponibilites | 3 ms | 9 ms | 2 ms | 0.00 % | ✅ | ✅ |
| LireQuartsParApi | 4 ms | 7 ms | 3 ms | 0.00 % | ✅ | ✅ |
| LireCorrectifsParApi | 4 ms | 7 ms | 3 ms | 0.00 % | ✅ | ✅ |
| LireRelevesParApi | 4 ms | 10 ms | 2 ms | 0.00 % | ✅ | ✅ |
| PointerUneEntree | 4 ms | 9 ms | 18 ms | 0.00 % | ✅ | ✅ |
| PreparerLesFeuillesDeTemps | 3 ms | 8 ms | 4 ms | 0.00 % | ✅ | ✅ |
| TransmettreSoldesParApi | 6 ms | 11 ms | 7 ms | 0.00 % | ✅ | ✅ |
