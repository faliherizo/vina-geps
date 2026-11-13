# Procédure d'essais d'acceptation — gestion des effectifs d'Urgences-santé

> Appel d'offres **26-015-DO**, exigence **E-TN-12** : « L'adjudicataire s'engage à fournir les
> procédures de tests d'acceptation pour démontrer que les spécifications de performance et les
> fonctionnalités décrites dans ce devis sont remplies et respectées. »
>
> Rédigé le 10 novembre 2026 à partir du code du dépôt (backend Java `backend-java/`, front-end
> `frontend/src/urgencessante/` et `frontend/src/effectifs/`). Le cahier de cas (§ 9) est **généré**
> par [`construire-procedure-acceptation.py`](construire-procedure-acceptation.py) ; ne pas l'éditer
> à la main.

## Table des matières

1. [Objet et portée](#1-objet-et-portée)
2. [Références](#2-références)
3. [Environnement d'essai](#3-environnement-dessai)
4. [Jeu de données](#4-jeu-de-données)
5. [Rôles et responsabilités](#5-rôles-et-responsabilités)
6. [Critères d'entrée et de sortie](#6-critères-dentrée-et-de-sortie)
7. [Gestion des anomalies](#7-gestion-des-anomalies)
8. [Déroulement d'une campagne](#8-déroulement-dune-campagne)
9. [Cahier de cas — exigences fonctionnelles](#9-cahier-de-cas--exigences-fonctionnelles)
10. [Essais de performance](#10-essais-de-performance)
11. [Ce que cette procédure ne couvre pas](#11-ce-que-cette-procédure-ne-couvre-pas)
12. [Régénérer le cahier](#12-régénérer-le-cahier)

## 1. Objet et portée

Cette procédure permet à Urgences-santé de **constater par elle-même**, dans l'environnement de
tests et formation, que la solution remplit :

- les **90 exigences fonctionnelles** de l'avis (E-GN, E-CH, E-GR, E-GP, E-RF et les
  préférentielles P-GN, P-GH, P-CH, P-GP, P-RF) — un cas d'essai par exigence (§ 9) ;
- les **spécifications de performance** du devis, au premier chef **E-TN-17** (« les fonctionnalités
  transactionnelles de base doivent s'exécuter à l'intérieur de 2 secondes ») — § 10.

Elle sert au **Banc d'essai** (Contrat, art. 2.08), à l'**inspection initiale** puis **finale**
dans l'Environnement de tests et formation (art. 2.09 et 2.11) et prépare l'**Acceptation
provisoire** (art. 2.12 a : « la Solution est testée et fonctionnelle dans l'Environnement de
tests et formation »).

Les 54 exigences technologiques (E-TN, P-TN) autres que la performance ne se vérifient par aucun
geste d'utilisateur ; elles relèvent de la matrice de couverture et ne sont pas reprises ici.

## 2. Références

| Document | Usage |
|---|---|
| [`../26-015-DO_AO/26-015-DO_AO.md`](../26-015-DO_AO/26-015-DO_AO.md) | Texte de l'avis : Contrat 2.05 (environnements), 2.08 à 2.12 (banc d'essai, inspections, acceptation provisoire), 5.02 (niveaux de priorité) |
| [`../26-015-DO_AO/MATRICE-COUVERTURE.md`](../26-015-DO_AO/MATRICE-COUVERTURE.md) | Verdict de chaque exigence, recette à l'interface, preuves |
| [`../26-015-DO_AO/08-VERIFICATION-EXIGENCES-FONCTIONNELLES.md`](../26-015-DO_AO/08-VERIFICATION-EXIGENCES-FONCTIONNELLES.md) | Méthode de vérification des 90 exigences fonctionnelles |
| [`../26-015-DO_AO/09-CHARGE-ET-NIVEAUX-DE-SERVICE.md`](../26-015-DO_AO/09-CHARGE-ET-NIVEAUX-DE-SERVICE.md) | SLI, SLO, SLA, transactions de base et mesures de charge |
| [`COMPTES-DEMONSTRATION.md`](COMPTES-DEMONSTRATION.md) | Comptes du jeu de données et ce que chacun voit |
| [`GUIDE-ADMINISTRATION-EFFECTIFS.md`](GUIDE-ADMINISTRATION-EFFECTIFS.md) | Paramétrage, rôles, journal et exports (E-TN-27) |
| [`../../perf/jmeter/README.md`](../../perf/jmeter/README.md) | Outillage JMeter |

## 3. Environnement d'essai

### 3.1 L'Environnement de tests et formation (Contrat 2.05 a)

Il est décrit par le même code d'infrastructure que la production, en tailles réduites :
`infra/terraform/aws/urgences-sante-essai.tfvars` (environnement `essai`, pare-feu applicatif actif,
domaine `essai-urgences-sante.nexu.ca`). La production est décrite par
`infra/terraform/aws/urgences-sante.tfvars`. Les essais d'acceptation se déroulent **uniquement**
dans l'environnement d'essai.

### 3.2 L'environnement local du fournisseur (préparation)

Avant chaque livraison au banc d'essai, le fournisseur rejoue la campagne automatisée sur un
backend Java isolé, sans toucher à la pile Docker :

```bash
backend-java/scripts/campagne-e2e.sh tests/e2e/urgences-sante-exigences.spec.ts \
  tests/e2e/urgences-sante-comportements.spec.ts tests/e2e/urgences-sante-parcours.spec.ts
```

Le script démarre le Java en **mode d'essai** (`NEXU_ESSAI=1`, propriété `nexu.essai`) sur le port
4020 et le front-end sur 5299 ; la cadence de l'ordonnanceur y est de 3 s. Le mode d'essai ouvre
des routes de remise à zéro réservées aux essais : **il ne doit jamais être actif dans
l'environnement de tests et formation remis à Urgences-santé** (critère d'entrée E-4).

### 3.3 Postes de travail

Navigateur à jour (Chrome, Edge ou Firefox), écran d'au moins 1280 px pour les consoles ; un
téléphone pour les écrans personnels (pointage, horaire). Les essais d'accessibilité utilisent
l'extension axe DevTools ou l'essai automatisé.

## 4. Jeu de données

### 4.1 Semer l'organisme d'essai

```bash
node scripts/semer-urgences-sante.cjs --api https://essai-urgences-sante.nexu.ca --mdp '<mot de passe de campagne>'
```

Le script (source : `e2e/donnees/urgences-sante/demonstration.json`) :

- crée un organisme horodaté « Urgences-santé — démonstration (…) » par
  `POST /api/superadmin/tenants`, **avec son compte d'administration** ;
- active les modules `effectifs`, `effectifs_regles`, `effectifs_planification`,
  `effectifs_disponibilites`, `effectifs_absences`, `effectifs_echanges`, `effectifs_agph`,
  `effectifs_presence`, `effectifs_temps`, `effectifs_integrations`, `effectifs_communication` et
  `horaires` (`PUT /api/saas/tenants/{id}/modules`) ;
- masque les sections de menu autres que Tableau de bord, Mon espace, Administration et
  Urgences-santé (`PUT /api/saas/tenants/{id}/vues`) — voir la réserve du § 11 ;
- sème l'unité, les corps et statuts d'emploi, les milieux (sites), les plages, les natures de
  quart, les types d'heures, les postes, les quarts du jour, les règles et les feuilles de temps de
  la semaine écoulée, puis les comptes du § 4.2.

Relancé, il crée un **nouvel** organisme plutôt que d'écraser le précédent. Un organisme neuf par
campagne garantit que les résultats ne dépendent pas d'une campagne antérieure.

### 4.2 Comptes

| Compte | Rôle applicatif | Profil | Utilisé pour |
|---|---|---|---|
| `admin` | admin | Administration de l'organisme | Écrans « Administration des effectifs », constructeur de rapports |
| `claire.nadeau` | manager | Planificatrice des effectifs | Consoles (comblement, quarts, absences, présence, feuilles de temps…) |
| `denis.lavoie` | director | Directeur des opérations | Consoles, dérogations |
| `julie.roy` | employee | Paramédic — temps complet | Écrans personnels |
| `marc.dube` | employee | Paramédic — temps partiel régulier | Échanges (il faut deux personnes) |
| `ana.cote` | employee | Paramédic — temps partiel occasionnel | Tour de rôle AGPH |
| `sophie.marchand` | rh | Ressources humaines | Contrôle négatif (aucune ressource planifiable) |

Ces comptes n'ont ni second facteur ni question secrète : c'est un jeu d'essai. Un organisme réel
n'est jamais semé ainsi.

### 4.3 Compléter le jeu : les périmètres d'encadrement

**Le script ne donne aucun périmètre aux comptes d'encadrement.** Or le serveur refuse par défaut
toute unité hors du périmètre de l'acteur (`LecturePerimetres.exiger`) ; seuls les rôles `admin` et
`superadmin` y échappent. Sans ce complément, `claire.nadeau` et `denis.lavoie` ouvrent des
consoles vides ou reçoivent un refus 403.

Avant le premier cas, sous le compte `admin` :

1. Ouvrir **Urgences-santé › Administration des effectifs › Périmètres** (`/usperimetres`).
2. Choisir le compte `claire.nadeau`, puis « Ajouter un accès » : unité de l'organisme semé, étendue
   « L'unité et ses sous-unités », niveau `GESTION`, date du jour ; « Ajouter l'accès ».
3. Refaire pour `denis.lavoie`.

Ce geste est aussi l'objet du cas CA-E-GN-8 ; il est consigné au journal des décisions.

## 5. Rôles et responsabilités

| Rôle | Tenu par | Responsabilités |
|---|---|---|
| Responsable des essais | Urgences-santé (représentant technique) | Approuve la procédure, arbitre la gravité, prononce le verdict de la campagne |
| Exécutant | Usagers pilotes d'Urgences-santé | Déroule les cas, coche le résultat obtenu, ouvre les fiches d'anomalie |
| Personne-ressource du fournisseur | Fournisseur (Contrat 2.08 a : jours ouvrables, 8 h 30 à 16 h 30) | Prépare l'environnement et le jeu de données, assiste l'exécution, qualifie et corrige les anomalies |
| Opérateur de performance | Fournisseur, en présence d'Urgences-santé | Prépare et joue les campagnes JMeter (§ 10), remet les rapports |

## 6. Critères d'entrée et de sortie

### 6.1 Critères d'entrée

| N° | Critère | Comment le constater |
|---|---|---|
| E-1 | La version livrée est identifiée (étiquette de version, image) et figée pendant la campagne | Note de livraison |
| E-2 | La campagne automatisée du fournisseur est verte sur cette version | Rapport de `urgences-sante-exigences.spec.ts` (90 exigences, audit axe-core) et de `urgences-sante-comportements.spec.ts` |
| E-3 | L'organisme d'essai est semé (§ 4) et les sept comptes se connectent | Connexion de chaque compte |
| E-4 | Le mode d'essai (`NEXU_ESSAI`) est **inactif** dans l'environnement de tests et formation | Configuration de déploiement |
| E-5 | Les connecteurs externes sont en mode `SIMULATEUR`, ou leurs points d'accès de test sont fournis par Urgences-santé | Urgences-santé › Administration des effectifs › Intégrations des effectifs |
| E-6 | Pour la performance : machine de charge au repos (charge moyenne inférieure au nombre de cœurs) | `uptime`, `sysctl -n hw.ncpu` (doc 09 § 9.7) |

### 6.2 Critères de sortie

| N° | Critère |
|---|---|
| S-1 | Les 90 cas du § 9 sont exécutés ; aucun n'est « non exécuté » sans motif écrit |
| S-2 | Aucune anomalie ouverte de niveau 1 ou 2 |
| S-3 | Les anomalies de niveau 3 ouvertes ont chacune une solution de contournement acceptée par écrit et une date de correction |
| S-4 | Les six transactions de base (§ 10.2) tiennent leur SLA p95, et le balayage des routes du module répond en 2xx à au moins 80 % |
| S-5 | Zéro violation axe-core sur chaque écran éprouvé, dans chaque état atteint |
| S-6 | Le procès-verbal de campagne est signé par le responsable des essais |

Un critère de sortie non atteint donne lieu à un **avis de non-conformité** (Contrat 2.09–2.10) :
le fournisseur corrige à ses frais dans les dix jours ouvrables et la campagne est rejouée sur les
cas touchés et leurs voisins (même écran).

## 7. Gestion des anomalies

### 7.1 Niveaux de gravité

Les niveaux reprennent ceux du Contrat, art. 5.02.01, pour qu'une anomalie d'essai et un incident de
production se classent de la même façon.

| Niveau | Définition (Contrat 5.02.01) | Exemples en essai |
|---|---|---|
| **1** | Rend la solution inopérante, ou compromet l'intégrité des données | Connexion impossible ; une feuille approuvée modifiée sans trace au journal |
| **2** | Rend inopérant une fonctionnalité ou un module essentiel (inconvénient majeur), ou nuit à la sauvegarde/récupération | Impossible de publier une période ; le comblement ne s'affiche pas ; une exigence essentielle non démontrable |
| **3** | Fonction non essentielle inopérante ou non conforme à la documentation, avec contournement acceptable | Export XLSX en erreur alors que le CSV fonctionne ; libellé erroné |
| **4** | Tout autre problème ou demande d'information | Coquille, suggestion d'ergonomie |

Une violation d'accessibilité WCAG 2.2 AA bloquante (contenu inatteignable au clavier, contraste
insuffisant du texte) est au moins de niveau 3 ; elle est de niveau 2 si elle empêche un usager de
réaliser une exigence essentielle.

### 7.2 Fiche d'anomalie

Chaque anomalie porte : numéro, cas d'essai (CA-…), exigence, compte utilisé, étapes pour
reproduire, résultat attendu, résultat obtenu, capture d'écran, heure (pour retrouver l'entrée du
journal des décisions et le journal applicatif), gravité proposée, gravité arbitrée.

Les fiches se consignent dans le **Soutien** de la plateforme (billets, pièces jointes) ou dans
l'outil de suivi désigné par Urgences-santé ; le numéro est reporté dans la ligne « Résultat
obtenu » du cas.

### 7.3 Cycle

Ouverte → qualifiée (gravité arbitrée) → corrigée (version identifiée) → revérifiée → fermée, ou
rejetée avec motif écrit. Une anomalie fermée est revérifiée par l'exécutant qui l'a ouverte.

## 8. Déroulement d'une campagne

1. Vérifier les critères d'entrée (§ 6.1).
2. Semer un organisme neuf (§ 4.1) ; noter son nom.
3. Dérouler les cas du § 9 **dans l'ordre**, famille par famille : certains cas consomment des
   données que les suivants supposent (une période publiée avant le comblement, une feuille
   soumise avant l'approbation).
4. Pour chaque cas : suivre les étapes **au clavier autant que possible**, comparer au résultat
   attendu, cocher le résultat obtenu, ouvrir une fiche au besoin.
5. Jouer les essais de performance (§ 10) sur une machine au repos.
6. Rédiger le procès-verbal : nombre de cas conformes / non conformes / non exécutés, anomalies par
   niveau, verdict au regard des critères de sortie.

Le profil indiqué par cas est le profil **cible** : un écran d'encadrement s'éprouve sous le compte
d'encadrement, un écran personnel sous le compte de la paramédic. L'essai sous le compte
d'administration ne prouve pas qu'un usager du rôle visé atteint l'écran.

## 9. Cahier de cas — exigences fonctionnelles

Chaque cas reprend la **recette de vérification à l'interface** de la matrice
(`e2e/donnees/urgences-sante/exigences-fonctionnelles.json`). Les identifiants entre accents graves
(`pla-adhoc`, `jrn-exporter`…) sont les identifiants d'essai des commandes : l'exécutant repère la
commande par son libellé à l'écran ; l'identifiant sert à l'automate et à la fiche d'anomalie.

### 9.1 Sommaire

<!-- CAHIER GENERE : sommaire — ne pas editer a la main (construire-procedure-acceptation.py) -->
| Cas | Famille | Type | Écran | Profil | Scénarios d'API | Verdict |
|---|---|---|---|---|---|---|
| [CA-E-GN-1](#ca-e-gn-1--généralités-essentiel) | Généralités | essentiel | `/usequipages` | `claire.nadeau` | — | ✅ Acquis |
| [CA-E-GN-2](#ca-e-gn-2--généralités-essentiel) | Généralités | essentiel | `/usequipages` | `claire.nadeau` | C-01 | ✅ Acquis |
| [CA-E-GN-3](#ca-e-gn-3--généralités-essentiel) | Généralités | essentiel | `/usequipages` | `claire.nadeau` | C-01 | ✅ Acquis |
| [CA-E-GN-4](#ca-e-gn-4--généralités-essentiel) | Généralités | essentiel | `/usequipages` | `claire.nadeau` | C-01 | ✅ Acquis |
| [CA-E-GN-5](#ca-e-gn-5--généralités-essentiel) | Généralités | essentiel | `/usabsences` | `claire.nadeau` | C-07 | ✅ Acquis |
| [CA-E-GN-6](#ca-e-gn-6--généralités-essentiel) | Généralités | essentiel | `/usabsences` | `claire.nadeau` | C-06, C-07 | ✅ Acquis |
| [CA-E-GN-7](#ca-e-gn-7--généralités-essentiel) | Généralités | essentiel | `/usreferentiels` | `admin` | — | ✅ Acquis |
| [CA-E-GN-8](#ca-e-gn-8--généralités-essentiel) | Généralités | essentiel | `/usperimetres` | `admin` | C-02 | ✅ Acquis |
| [CA-E-GN-9](#ca-e-gn-9--généralités-essentiel) | Généralités | essentiel | `/usjournal` | `admin` | C-02, C-07 | ✅ Acquis |
| [CA-E-GN-10](#ca-e-gn-10--généralités-essentiel) | Généralités | essentiel | `/usjournal` | `admin` | C-09 | ✅ Acquis |
| [CA-E-GN-11](#ca-e-gn-11--généralités-essentiel) | Généralités | essentiel | `/usequipages` | `claire.nadeau` | C-02 | ✅ Acquis |
| [CA-E-GN-12](#ca-e-gn-12--généralités-essentiel) | Généralités | essentiel | `/usregles` | `admin` | C-04 | ✅ Acquis |
| [CA-E-GN-13](#ca-e-gn-13--généralités-essentiel) | Généralités | essentiel | `/mesdisponibilites` | `julie.roy` | C-04 | ✅ Acquis |
| [CA-E-GN-14](#ca-e-gn-14--généralités-essentiel) | Généralités | essentiel | `/usdisponibilites` | `claire.nadeau` | C-05 | ✅ Acquis |
| [CA-E-GN-15](#ca-e-gn-15--généralités-essentiel) | Généralités | essentiel | `/mesdisponibilites` | `julie.roy` | C-04 | ✅ Acquis |
| [CA-E-GN-16](#ca-e-gn-16--généralités-essentiel) | Généralités | essentiel | `/mesdisponibilites` | `julie.roy` | C-04, C-05 | ✅ Acquis |
| [CA-E-GN-17](#ca-e-gn-17--généralités-essentiel) | Généralités | essentiel | `/usmilieux` | `admin` | — | ✅ Acquis |
| [CA-E-CH-1](#ca-e-ch-1--confection-des-horaires-essentiel) | Confection des horaires | essentiel | `/usagph` | `claire.nadeau` | — | ✅ Acquis |
| [CA-E-CH-2](#ca-e-ch-2--confection-des-horaires-essentiel) | Confection des horaires | essentiel | `/uspostes` | `claire.nadeau` | C-03 | ✅ Acquis |
| [CA-E-CH-3](#ca-e-ch-3--confection-des-horaires-essentiel) | Confection des horaires | essentiel | `/uspostes` | `claire.nadeau` | — | ✅ Acquis |
| [CA-E-CH-4](#ca-e-ch-4--confection-des-horaires-essentiel) | Confection des horaires | essentiel | `/uspostes` | `claire.nadeau` | C-03 | ✅ Acquis |
| [CA-E-CH-5](#ca-e-ch-5--confection-des-horaires-essentiel) | Confection des horaires | essentiel | `/uspostes` | `claire.nadeau` | C-03 | ✅ Acquis |
| [CA-E-CH-6](#ca-e-ch-6--confection-des-horaires-essentiel) | Confection des horaires | essentiel | `/usabsences` | `claire.nadeau` | C-07 | ✅ Acquis |
| [CA-E-CH-7](#ca-e-ch-7--confection-des-horaires-essentiel) | Confection des horaires | essentiel | `/usagph` | `claire.nadeau` | — | ✅ Acquis |
| [CA-E-CH-8](#ca-e-ch-8--confection-des-horaires-essentiel) | Confection des horaires | essentiel | `/monagph` | `julie.roy` | — | ✅ Acquis |
| [CA-E-CH-9](#ca-e-ch-9--confection-des-horaires-essentiel) | Confection des horaires | essentiel | `/usagph` | `claire.nadeau` | — | ✅ Acquis |
| [CA-E-CH-10](#ca-e-ch-10--confection-des-horaires-essentiel) | Confection des horaires | essentiel | `/uscomblement` | `claire.nadeau` | — | ✅ Acquis |
| [CA-E-GR-1](#ca-e-gr-1--gestion-régulière-des-horaires-essentiel) | Gestion régulière des horaires | essentiel | `/uscomblement` | `claire.nadeau` | — | ✅ Acquis |
| [CA-E-GR-2](#ca-e-gr-2--gestion-régulière-des-horaires-essentiel) | Gestion régulière des horaires | essentiel | `/uscomblement` | `claire.nadeau` | — | ✅ Acquis |
| [CA-E-GR-3](#ca-e-gr-3--gestion-régulière-des-horaires-essentiel) | Gestion régulière des horaires | essentiel | `/usabsences` | `claire.nadeau` | C-06 | ✅ Acquis |
| [CA-E-GR-4](#ca-e-gr-4--gestion-régulière-des-horaires-essentiel) | Gestion régulière des horaires | essentiel | `/usabsences` | `claire.nadeau` | — | ✅ Acquis |
| [CA-E-GR-5](#ca-e-gr-5--gestion-régulière-des-horaires-essentiel) | Gestion régulière des horaires | essentiel | `/usperiodes` | `claire.nadeau` | — | ✅ Acquis |
| [CA-E-GR-6](#ca-e-gr-6--gestion-régulière-des-horaires-essentiel) | Gestion régulière des horaires | essentiel | `/usequipages` | `claire.nadeau` | C-01 | ✅ Acquis |
| [CA-E-GR-7](#ca-e-gr-7--gestion-régulière-des-horaires-essentiel) | Gestion régulière des horaires | essentiel | `/usechanges` | `claire.nadeau` | — | ✅ Acquis |
| [CA-E-GR-8](#ca-e-gr-8--gestion-régulière-des-horaires-essentiel) | Gestion régulière des horaires | essentiel | `/usquotas` | `claire.nadeau` | — | ✅ Acquis |
| [CA-E-GR-9](#ca-e-gr-9--gestion-régulière-des-horaires-essentiel) | Gestion régulière des horaires | essentiel | `/usquotas` | `claire.nadeau` | — | ✅ Acquis |
| [CA-E-GR-10](#ca-e-gr-10--gestion-régulière-des-horaires-essentiel) | Gestion régulière des horaires | essentiel | `/usperiodes` | `claire.nadeau` | C-04 | ✅ Acquis |
| [CA-E-GR-11](#ca-e-gr-11--gestion-régulière-des-horaires-essentiel) | Gestion régulière des horaires | essentiel | `/mesdisponibilites` | `julie.roy` | C-04 | ✅ Acquis |
| [CA-E-GR-12](#ca-e-gr-12--gestion-régulière-des-horaires-essentiel) | Gestion régulière des horaires | essentiel | `/usechanges` | `claire.nadeau` | — | ✅ Acquis |
| [CA-E-GR-13](#ca-e-gr-13--gestion-régulière-des-horaires-essentiel) | Gestion régulière des horaires | essentiel | `/usequipages` | `claire.nadeau` | C-01 | ✅ Acquis |
| [CA-E-GP-1](#ca-e-gp-1--gestion-du-punch-essentiel) | Gestion du "punch" | essentiel | `/monpointage` | `julie.roy` | — | ✅ Acquis |
| [CA-E-GP-2](#ca-e-gp-2--gestion-du-punch-essentiel) | Gestion du "punch" | essentiel | `/uspresence` | `claire.nadeau` | — | ✅ Acquis |
| [CA-E-GP-3](#ca-e-gp-3--gestion-du-punch-essentiel) | Gestion du "punch" | essentiel | `/ustemps` | `claire.nadeau` | — | ✅ Acquis |
| [CA-E-GP-4](#ca-e-gp-4--gestion-du-punch-essentiel) | Gestion du "punch" | essentiel | `/uspresence` | `claire.nadeau` | — | ✅ Acquis |
| [CA-E-GP-5](#ca-e-gp-5--gestion-du-punch-essentiel) | Gestion du "punch" | essentiel | `/matemps` | `julie.roy` | — | ✅ Acquis |
| [CA-E-GP-6](#ca-e-gp-6--gestion-du-punch-essentiel) | Gestion du "punch" | essentiel | `/ustemps` | `claire.nadeau` | — | ✅ Acquis |
| [CA-E-GP-7](#ca-e-gp-7--gestion-du-punch-essentiel) | Gestion du "punch" | essentiel | `/uspresence` | `claire.nadeau` | — | ✅ Acquis |
| [CA-E-GP-8](#ca-e-gp-8--gestion-du-punch-essentiel) | Gestion du "punch" | essentiel | `/matemps` | `julie.roy` | — | ✅ Acquis |
| [CA-E-GP-9](#ca-e-gp-9--gestion-du-punch-essentiel) | Gestion du "punch" | essentiel | `/uspresence` | `claire.nadeau` | — | ✅ Acquis |
| [CA-E-GP-10](#ca-e-gp-10--gestion-du-punch-essentiel) | Gestion du "punch" | essentiel | `/usmilieux` | `admin` | — | ✅ Acquis |
| [CA-E-GP-11](#ca-e-gp-11--gestion-du-punch-essentiel) | Gestion du "punch" | essentiel | `/usmilieux` | `admin` | — | ✅ Acquis |
| [CA-E-GP-12](#ca-e-gp-12--gestion-du-punch-essentiel) | Gestion du "punch" | essentiel | `/usmilieux` | `admin` | — | ✅ Acquis |
| [CA-E-GP-13](#ca-e-gp-13--gestion-du-punch-essentiel) | Gestion du "punch" | essentiel | `/uspresence` | `claire.nadeau` | — | ✅ Acquis |
| [CA-E-GP-14](#ca-e-gp-14--gestion-du-punch-essentiel) | Gestion du "punch" | essentiel | `/uspresence` | `claire.nadeau` | — | ✅ Acquis |
| [CA-E-GP-15](#ca-e-gp-15--gestion-du-punch-essentiel) | Gestion du "punch" | essentiel | `/monpointage` | `julie.roy` | — | ✅ Acquis |
| [CA-E-RF-1](#ca-e-rf-1--rapports-et-formulaires-essentiel) | Rapports et formulaires | essentiel | `/ustemps` | `claire.nadeau` | — | ✅ Acquis |
| [CA-E-RF-2](#ca-e-rf-2--rapports-et-formulaires-essentiel) | Rapports et formulaires | essentiel | `/matemps` | `julie.roy` | — | ✅ Acquis |
| [CA-E-RF-3](#ca-e-rf-3--rapports-et-formulaires-essentiel) | Rapports et formulaires | essentiel | `/matemps` | `julie.roy` | — | ✅ Acquis |
| [CA-E-RF-4](#ca-e-rf-4--rapports-et-formulaires-essentiel) | Rapports et formulaires | essentiel | `/ustemps` | `claire.nadeau` | — | ✅ Acquis |
| [CA-E-RF-5](#ca-e-rf-5--rapports-et-formulaires-essentiel) | Rapports et formulaires | essentiel | `/ustemps` | `claire.nadeau` | — | ✅ Acquis |
| [CA-E-RF-6](#ca-e-rf-6--rapports-et-formulaires-essentiel) | Rapports et formulaires | essentiel | `/usreferentiels` | `admin` | — | ✅ Acquis |
| [CA-E-RF-7](#ca-e-rf-7--rapports-et-formulaires-essentiel) | Rapports et formulaires | essentiel | `/ustemps` | `claire.nadeau` | — | ✅ Acquis |
| [CA-E-RF-8](#ca-e-rf-8--rapports-et-formulaires-essentiel) | Rapports et formulaires | essentiel | `/rapports` | `admin` | C-09 | ✅ Acquis |
| [CA-E-RF-9](#ca-e-rf-9--rapports-et-formulaires-essentiel) | Rapports et formulaires | essentiel | `/ustemps` | `claire.nadeau` | — | ✅ Acquis |
| [CA-E-RF-10](#ca-e-rf-10--rapports-et-formulaires-essentiel) | Rapports et formulaires | essentiel | `/usregles` | `admin` | — | ✅ Acquis |
| [CA-E-RF-11](#ca-e-rf-11--rapports-et-formulaires-essentiel) | Rapports et formulaires | essentiel | `/matemps` | `julie.roy` | — | ✅ Acquis |
| [CA-E-RF-12](#ca-e-rf-12--rapports-et-formulaires-essentiel) | Rapports et formulaires | essentiel | `/ustemps` | `claire.nadeau` | — | ✅ Acquis |
| [CA-E-RF-13](#ca-e-rf-13--rapports-et-formulaires-essentiel) | Rapports et formulaires | essentiel | `/ustemps` | `claire.nadeau` | — | ✅ Acquis |
| [CA-E-RF-14](#ca-e-rf-14--rapports-et-formulaires-essentiel) | Rapports et formulaires | essentiel | `/matemps` | `julie.roy` | — | ✅ Acquis |
| [CA-P-GN-1](#ca-p-gn-1--généralités-préférentiel) | Généralités | préférentiel | `/usabsences` | `claire.nadeau` | — | ✅ Acquis |
| [CA-P-GN-2](#ca-p-gn-2--généralités-préférentiel) | Généralités | préférentiel | `/usregles` | `admin` | C-06 | ✅ Acquis |
| [CA-P-GN-3](#ca-p-gn-3--généralités-préférentiel) | Généralités | préférentiel | `/mesabsences` | `julie.roy` | C-08 | ✅ Acquis |
| [CA-P-GH-1](#ca-p-gh-1--gestion-des-horaires-préférentiel) | Gestion des horaires | préférentiel | `/mesabsences` | `julie.roy` | C-08 | ✅ Acquis |
| [CA-P-GH-2](#ca-p-gh-2--gestion-des-horaires-préférentiel) | Gestion des horaires | préférentiel | `/mesabsences` | `julie.roy` | C-06 | ✅ Acquis |
| [CA-P-GH-3](#ca-p-gh-3--gestion-des-horaires-préférentiel) | Gestion des horaires | préférentiel | `/usabsences` | `claire.nadeau` | — | ✅ Acquis |
| [CA-P-GH-4](#ca-p-gh-4--gestion-des-horaires-préférentiel) | Gestion des horaires | préférentiel | `/usdisponibilites` | `claire.nadeau` | — | ✅ Acquis |
| [CA-P-CH-1](#ca-p-ch-1--confection-des-horaires-préférentiel) | Confection des horaires | préférentiel | `/monagph` | `julie.roy` | — | ✅ Acquis |
| [CA-P-CH-2](#ca-p-ch-2--confection-des-horaires-préférentiel) | Confection des horaires | préférentiel | `/monagph` | `julie.roy` | — | ✅ Acquis |
| [CA-P-CH-3](#ca-p-ch-3--confection-des-horaires-préférentiel) | Confection des horaires | préférentiel | `/monagph` | `julie.roy` | — | ✅ Acquis |
| [CA-P-CH-4](#ca-p-ch-4--confection-des-horaires-préférentiel) | Confection des horaires | préférentiel | `/usagph` | `claire.nadeau` | — | ✅ Acquis |
| [CA-P-CH-5](#ca-p-ch-5--confection-des-horaires-préférentiel) | Confection des horaires | préférentiel | `/usagph` | `claire.nadeau` | — | ✅ Acquis |
| [CA-P-CH-6](#ca-p-ch-6--confection-des-horaires-préférentiel) | Confection des horaires | préférentiel | `/usagph` | `claire.nadeau` | — | ✅ Acquis |
| [CA-P-CH-7](#ca-p-ch-7--confection-des-horaires-préférentiel) | Confection des horaires | préférentiel | `/usagph` | `claire.nadeau` | — | ✅ Acquis |
| [CA-P-CH-8](#ca-p-ch-8--confection-des-horaires-préférentiel) | Confection des horaires | préférentiel | `/usabsences` | `claire.nadeau` | — | ✅ Acquis |
| [CA-P-CH-9](#ca-p-ch-9--confection-des-horaires-préférentiel) | Confection des horaires | préférentiel | `/usagph` | `claire.nadeau` | — | ✅ Acquis |
| [CA-P-GP-1](#ca-p-gp-1--gestion-du-punch-préférentiel) | Gestion du "punch" | préférentiel | `/monpointage` | `julie.roy` | — | ✅ Acquis |
| [CA-P-GP-2](#ca-p-gp-2--gestion-du-punch-préférentiel) | Gestion du "punch" | préférentiel | `/uspresence` | `claire.nadeau` | — | ✅ Acquis |
| [CA-P-GP-3](#ca-p-gp-3--gestion-du-punch-préférentiel) | Gestion du "punch" | préférentiel | `/monpointage` | `julie.roy` | — | ✅ Acquis |
| [CA-P-GP-4](#ca-p-gp-4--gestion-du-punch-préférentiel) | Gestion du "punch" | préférentiel | `/matemps` | `julie.roy` | — | ✅ Acquis |
| [CA-P-RF-1](#ca-p-rf-1--rapports-et-formulaires-préférentiel) | Rapports et formulaires | préférentiel | `/rapports` | `admin` | — | ✅ Acquis |
<!-- FIN CAHIER GENERE : sommaire -->

### 9.2 Les cas

<!-- CAHIER GENERE : cas — ne pas editer a la main (construire-procedure-acceptation.py) -->
### CA-E-GN-1 — Généralités (essentiel)

> La solution doit permettre de configurer des quarts de travail nécessitant une ou plusieurs ressources selon le corps d’emploi ou le contexte.

- **Verdict à la matrice** : ✅ Acquis
- **Écran** : Urgences-santé › Gestion des quarts › Quarts et équipages — `/usequipages`
- **Profil** : `claire.nadeau` (Planificatrice des effectifs)
- **Étapes** :
  1. Se connecter à l'organisme d'essai avec `claire.nadeau` (Planificatrice des effectifs).
  2. Ouvrir **Urgences-santé › Gestion des quarts › Quarts et équipages** (adresse `/usequipages`).
  3. Activer la commande `pla-eq-adhoc` (la composition se définit dans le tiroir de création ; la liste seule ne la montre pas).
  4. Choisir la période, « Quart ad hoc », puis « Ajouter un corps d'emploi » : un quart reçoit une ou plusieurs places, chacune avec son corps d'emploi.
- **Résultat attendu** :
  - les commandes `pla-adhoc`, `pla-ah-ajouter-corps`, `pla-ah-nature` sont présentes et utilisables au clavier ;
  - le geste décrit à la dernière étape produit l'effet annoncé, sans erreur affichée ;
  - l'écran, dans l'état atteint, ne présente aucune violation axe-core (WCAG 2.2 AA).
- **Essais automatisés qui le couvrent déjà** :
  - Écran et commandes : `e2e/tests/e2e/urgences-sante-exigences.spec.ts` (avec audit axe-core)
  - Comportement (Java) : `PlanificationDomaineTest`
- **Résultat obtenu** : ☐ Conforme ☐ Non conforme — anomalie n° ______ — gravité ____ — initiales ____

### CA-E-GN-2 — Généralités (essentiel)

> La solution doit permettre de définir le nombre minimal de ressources requises pour qu’un quart de travail soit considéré opérationnel.

- **Verdict à la matrice** : ✅ Acquis
- **Écran** : Urgences-santé › Gestion des quarts › Quarts et équipages — `/usequipages`
- **Profil** : `claire.nadeau` (Planificatrice des effectifs)
- **Étapes** :
  1. Se connecter à l'organisme d'essai avec `claire.nadeau` (Planificatrice des effectifs).
  2. Ouvrir **Urgences-santé › Gestion des quarts › Quarts et équipages** (adresse `/usequipages`).
  3. Activer la commande `pla-eq-adhoc` (le minimum est un champ de la création du quart).
  4. Dans le tiroir du quart ad hoc, renseigner « Minimum opérationnel » : sous ce nombre, le comblement classe le quart en sous-minimum.
- **Résultat attendu** :
  - les commandes `pla-adhoc`, `pla-ah-minimum` sont présentes et utilisables au clavier ;
  - le geste décrit à la dernière étape produit l'effet annoncé, sans erreur affichée ;
  - l'écran, dans l'état atteint, ne présente aucune violation axe-core (WCAG 2.2 AA).
  - comportement C-01 : Un quart ad hoc se crée hors de tout poste ; hors période, sans composition, à minimum impossible ou sur une plage inconnue, il est refusé.
- **Essais automatisés qui le couvrent déjà** :
  - Écran et commandes : `e2e/tests/e2e/urgences-sante-exigences.spec.ts` (avec audit axe-core)
  - Comportement (Java) : `PlanificationDomaineTest`
  - Scénarios d'API (`e2e/tests/e2e/urgences-sante-comportements.spec.ts`) : C-01
- **Résultat obtenu** : ☐ Conforme ☐ Non conforme — anomalie n° ______ — gravité ____ — initiales ____

### CA-E-GN-3 — Généralités (essentiel)

> La solution doit permettre la création de quarts de travail ponctuels ou ad hoc à des fins opérationnelles particulières.

- **Verdict à la matrice** : ✅ Acquis
- **Écran** : Urgences-santé › Gestion des quarts › Quarts et équipages — `/usequipages`
- **Profil** : `claire.nadeau` (Planificatrice des effectifs)
- **Étapes** :
  1. Se connecter à l'organisme d'essai avec `claire.nadeau` (Planificatrice des effectifs).
  2. Ouvrir **Urgences-santé › Gestion des quarts › Quarts et équipages** (adresse `/usequipages`).
  3. Activer la commande `pla-eq-adhoc` (le quart ponctuel se crée dans ce tiroir).
  4. « Quart ad hoc » : date, plage et milieu libres, hors cycle de poste.
- **Résultat attendu** :
  - les commandes `pla-adhoc`, `pla-ah-date`, `pla-ah-plage`, `pla-ah-milieu` sont présentes et utilisables au clavier ;
  - le geste décrit à la dernière étape produit l'effet annoncé, sans erreur affichée ;
  - l'écran, dans l'état atteint, ne présente aucune violation axe-core (WCAG 2.2 AA).
  - comportement C-01 : Un quart ad hoc se crée hors de tout poste ; hors période, sans composition, à minimum impossible ou sur une plage inconnue, il est refusé.
- **Essais automatisés qui le couvrent déjà** :
  - Écran et commandes : `e2e/tests/e2e/urgences-sante-exigences.spec.ts` (avec audit axe-core)
  - Comportement (Java) : `ControleurPlanificationEffectifsHttpTest`
  - Scénarios d'API (`e2e/tests/e2e/urgences-sante-comportements.spec.ts`) : C-01
- **Résultat obtenu** : ☐ Conforme ☐ Non conforme — anomalie n° ______ — gravité ____ — initiales ____

### CA-E-GN-4 — Généralités (essentiel)

> La solution doit permettre de choisir, pour un quart de travail, un mode d’attribution automatisé ou manuel par un utilisateur autorisé.

- **Verdict à la matrice** : ✅ Acquis
- **Écran** : Urgences-santé › Gestion des quarts › Quarts et équipages — `/usequipages`
- **Profil** : `claire.nadeau` (Planificatrice des effectifs)
- **Étapes** :
  1. Se connecter à l'organisme d'essai avec `claire.nadeau` (Planificatrice des effectifs).
  2. Ouvrir **Urgences-santé › Gestion des quarts › Quarts et équipages** (adresse `/usequipages`).
  3. Activer la commande `pla-eq-adhoc` (le mode est un champ du quart).
  4. Champ « Mode d'attribution » du quart : automatisé ou manuel ; « Modifier en lot » le change pour plusieurs quarts.
- **Résultat attendu** :
  - les commandes `pla-adhoc`, `pla-ah-mode` sont présentes et utilisables au clavier ;
  - le geste décrit à la dernière étape produit l'effet annoncé, sans erreur affichée ;
  - l'écran, dans l'état atteint, ne présente aucune violation axe-core (WCAG 2.2 AA).
  - comportement C-01 : Un quart ad hoc se crée hors de tout poste ; hors période, sans composition, à minimum impossible ou sur une plage inconnue, il est refusé.
- **Essais automatisés qui le couvrent déjà** :
  - Écran et commandes : `e2e/tests/e2e/urgences-sante-exigences.spec.ts` (avec audit axe-core)
  - Comportement (Java) : `PlanificationDomaineTest`
  - Scénarios d'API (`e2e/tests/e2e/urgences-sante-comportements.spec.ts`) : C-01
- **Résultat obtenu** : ☐ Conforme ☐ Non conforme — anomalie n° ______ — gravité ____ — initiales ____

### CA-E-GN-5 — Généralités (essentiel)

> La solution doit permettre l’autorisation manuelle d’un congé ayant été refusé par une automatisation avec attribution du code d’absence applicable.

- **Verdict à la matrice** : ✅ Acquis
- **Écran** : Urgences-santé › Absences des effectifs › Absences et comblement — `/usabsences`
- **Profil** : `claire.nadeau` (Planificatrice des effectifs)
- **Étapes** :
  1. Se connecter à l'organisme d'essai avec `claire.nadeau` (Planificatrice des effectifs).
  2. Ouvrir **Urgences-santé › Absences des effectifs › Absences et comblement** (adresse `/usabsences`).
  3. Ouvrir une absence refusée par l'automatisation, puis « Déroger » : un motif et un code d'absence sont exigés.
- **Résultat attendu** :
  - les commandes `pla-abs`, `pla-abs-filtre`, `pla-abs-table` sont présentes et utilisables au clavier ;
  - le geste décrit à la dernière étape produit l'effet annoncé, sans erreur affichée ;
  - l'écran, dans l'état atteint, ne présente aucune violation axe-core (WCAG 2.2 AA).
  - comportement C-07 : La dérogation exige un droit, un motif et un code ; elle accorde l'absence et rend les places vacantes par un mécanisme journalisé.
- **Essais automatisés qui le couvrent déjà** :
  - Écran et commandes : `e2e/tests/e2e/urgences-sante-exigences.spec.ts` (avec audit axe-core)
  - Comportement (Java) : `AbsencesEffectifsDomaineTest`, `ControleurAbsencesEffectifsHttpTest`
  - Bout en bout : `effectifs-absences.spec.ts`
  - Scénarios d'API (`e2e/tests/e2e/urgences-sante-comportements.spec.ts`) : C-07
- **Résultat obtenu** : ☐ Conforme ☐ Non conforme — anomalie n° ______ — gravité ____ — initiales ____

### CA-E-GN-6 — Généralités (essentiel)

> La solution doit appliquer automatiquement les règles de comblement à la suite d’une autorisation manuelle.

- **Verdict à la matrice** : ✅ Acquis
- **Écran** : Urgences-santé › Absences des effectifs › Absences et comblement — `/usabsences`
- **Profil** : `claire.nadeau` (Planificatrice des effectifs)
- **Étapes** :
  1. Se connecter à l'organisme d'essai avec `claire.nadeau` (Planificatrice des effectifs).
  2. Ouvrir **Urgences-santé › Absences des effectifs › Absences et comblement** (adresse `/usabsences`).
  3. Après la dérogation, l'onglet montre le comblement des places libérées : qui a été retenu, qui a été avisé.
- **Résultat attendu** :
  - les commandes `pla-abs`, `pla-abs-filtre` sont présentes et utilisables au clavier ;
  - le geste décrit à la dernière étape produit l'effet annoncé, sans erreur affichée ;
  - l'écran, dans l'état atteint, ne présente aucune violation axe-core (WCAG 2.2 AA).
  - comportement C-06 : Une absence qui ferait tomber le comblement est refusée d'office, et le refus est conservé avec son motif.
  - comportement C-07 : La dérogation exige un droit, un motif et un code ; elle accorde l'absence et rend les places vacantes par un mécanisme journalisé.
- **Essais automatisés qui le couvrent déjà** :
  - Écran et commandes : `e2e/tests/e2e/urgences-sante-exigences.spec.ts` (avec audit axe-core)
  - Comportement (Java) : `ControleurAbsencesEffectifsHttpTest`
  - Bout en bout : `effectifs-absences.spec.ts`
  - Scénarios d'API (`e2e/tests/e2e/urgences-sante-comportements.spec.ts`) : C-06, C-07
- **Résultat obtenu** : ☐ Conforme ☐ Non conforme — anomalie n° ______ — gravité ____ — initiales ____

### CA-E-GN-7 — Généralités (essentiel)

> La solution doit permettre la segmentation complète de la planification et des règles par groupes organisationnels distincts.

- **Verdict à la matrice** : ✅ Acquis
- **Écran** : Urgences-santé › Administration des effectifs › Référentiels — `/usreferentiels`
- **Profil** : `admin` (administration de l'organisme)
- **Étapes** :
  1. Se connecter à l'organisme d'essai avec `admin` (administration de l'organisme).
  2. Ouvrir **Urgences-santé › Administration des effectifs › Référentiels** (adresse `/usreferentiels`).
  3. Choisir le type « Unités » : chaque unité est un groupe organisationnel, et postes, règles et quotas s'y rattachent.
- **Résultat attendu** :
  - les commandes `ref-ecran`, `ref-type`, `ref-tableau` sont présentes et utilisables au clavier ;
  - le geste décrit à la dernière étape produit l'effet annoncé, sans erreur affichée ;
  - l'écran, dans l'état atteint, ne présente aucune violation axe-core (WCAG 2.2 AA).
- **Essais automatisés qui le couvrent déjà** :
  - Écran et commandes : `e2e/tests/e2e/urgences-sante-exigences.spec.ts` (avec audit axe-core)
  - Comportement (Java) : `SocleEffectifsDomaineTest`
- **Résultat obtenu** : ☐ Conforme ☐ Non conforme — anomalie n° ______ — gravité ____ — initiales ____

### CA-E-GN-8 — Généralités (essentiel)

> La solution doit permettre de restreindre l’accès et les opérations d’un utilisateur à des groupes organisationnels précis.

- **Verdict à la matrice** : ✅ Acquis
- **Écran** : Urgences-santé › Administration des effectifs › Périmètres — `/usperimetres`
- **Profil** : `admin` (administration de l'organisme)
- **Étapes** :
  1. Se connecter à l'organisme d'essai avec `admin` (administration de l'organisme).
  2. Ouvrir **Urgences-santé › Administration des effectifs › Périmètres** (adresse `/usperimetres`).
  3. Choisir une valeur dans `per-utilisateur` (le formulaire d'accès n'apparaît qu'une fois le compte choisi : sans lui, on ne saurait pas à qui on donne l'accès).
  4. Activer la commande `per-ajout-ouvrir` (le formulaire vit dans un dialogue depuis le 8 novembre 2026 : ses champs n'existent qu'ouvert).
  5. Choisir un compte, puis lui donner une unité, un niveau et une étendue : hors de son périmètre, le serveur refuse.
- **Résultat attendu** :
  - les commandes `per-ecran`, `per-utilisateur`, `per-ajout-ouvrir`, `per-unite`, `per-niveau`, `per-ajouter` sont présentes et utilisables au clavier ;
  - le geste décrit à la dernière étape produit l'effet annoncé, sans erreur affichée ;
  - l'écran, dans l'état atteint, ne présente aucune violation axe-core (WCAG 2.2 AA).
  - comportement C-02 : Une règle de repos bloque l'attribution en nommant son motif ; forcer exige le niveau GESTION et une justification, et le forçage est tracé.
- **Essais automatisés qui le couvrent déjà** :
  - Écran et commandes : `e2e/tests/e2e/urgences-sante-exigences.spec.ts` (avec audit axe-core)
  - Comportement (Java) : `SocleEffectifsDomaineTest`
  - Scénarios d'API (`e2e/tests/e2e/urgences-sante-comportements.spec.ts`) : C-02
- **Résultat obtenu** : ☐ Conforme ☐ Non conforme — anomalie n° ______ — gravité ____ — initiales ____

### CA-E-GN-9 — Généralités (essentiel)

> La solution doit consigner de manière horodatée toute interaction, action, modification ou décision ayant un impact sur les horaires, les quarts, les absences, les paramètres ou les données de temps, incluant l’identification de l’utilisateur ou du mécanisme automatisé à l’origine de l’action.

- **Verdict à la matrice** : ✅ Acquis
- **Écran** : Urgences-santé › Administration des effectifs › Journal des décisions — `/usjournal`
- **Profil** : `admin` (administration de l'organisme)
- **Étapes** :
  1. Se connecter à l'organisme d'essai avec `admin` (administration de l'organisme).
  2. Ouvrir **Urgences-santé › Administration des effectifs › Journal des décisions** (adresse `/usjournal`).
  3. Le journal : chaque action horodatée avec son auteur ou le mécanisme qui l'a posée ; filtrable par période et par origine.
- **Résultat attendu** :
  - les commandes `jrn-ecran`, `jrn-filtre-du`, `jrn-filtre-origine`, `jrn-filtrer` sont présentes et utilisables au clavier ;
  - le geste décrit à la dernière étape produit l'effet annoncé, sans erreur affichée ;
  - l'écran, dans l'état atteint, ne présente aucune violation axe-core (WCAG 2.2 AA).
  - comportement C-02 : Une règle de repos bloque l'attribution en nommant son motif ; forcer exige le niveau GESTION et une justification, et le forçage est tracé.
  - comportement C-07 : La dérogation exige un droit, un motif et un code ; elle accorde l'absence et rend les places vacantes par un mécanisme journalisé.
- **Essais automatisés qui le couvrent déjà** :
  - Écran et commandes : `e2e/tests/e2e/urgences-sante-exigences.spec.ts` (avec audit axe-core)
  - Comportement (Java) : `SocleEffectifsDomaineTest`, `ControleurIntegrationsEffectifsHttpTest`, `ControleurPresenceEffectifsHttpTest`, `ActionTest`, `DepotRecherchesTest`, `ControleurHeuresSupplementairesHttpTest`
  - Bout en bout : `effectifs-planification.spec.ts`, `effectifs-regles.spec.ts`, `effectifs-socle.spec.ts`
  - Scénarios d'API (`e2e/tests/e2e/urgences-sante-comportements.spec.ts`) : C-02, C-07
- **Résultat obtenu** : ☐ Conforme ☐ Non conforme — anomalie n° ______ — gravité ____ — initiales ____

### CA-E-GN-10 — Généralités (essentiel)

> La solution doit permettre l’audit, l’analyse et la consultation a posteriori de l’ensemble des traces d’exécution et de paramétrage.

- **Verdict à la matrice** : ✅ Acquis
- **Écran** : Urgences-santé › Administration des effectifs › Journal des décisions — `/usjournal`
- **Profil** : `admin` (administration de l'organisme)
- **Étapes** :
  1. Se connecter à l'organisme d'essai avec `admin` (administration de l'organisme).
  2. Ouvrir **Urgences-santé › Administration des effectifs › Journal des décisions** (adresse `/usjournal`).
  3. Filtrer le journal sur une période passée, puis « Exporter » : l'audit a posteriori se lit à l'écran et s'emporte en CSV.
- **Résultat attendu** :
  - les commandes `jrn-ecran`, `jrn-tableau`, `jrn-exporter` sont présentes et utilisables au clavier ;
  - le geste décrit à la dernière étape produit l'effet annoncé, sans erreur affichée ;
  - l'écran, dans l'état atteint, ne présente aucune violation axe-core (WCAG 2.2 AA).
  - comportement C-09 : Le journal s'exporte en CSV, en-tête fixe : chaque geste humain ou automatique y est retrouvable.
- **Essais automatisés qui le couvrent déjà** :
  - Écran et commandes : `e2e/tests/e2e/urgences-sante-exigences.spec.ts` (avec audit axe-core)
  - Comportement (Java) : `SocleEffectifsDomaineTest`
  - Bout en bout : `effectifs-socle.spec.ts`
  - Scénarios d'API (`e2e/tests/e2e/urgences-sante-comportements.spec.ts`) : C-09
- **Résultat obtenu** : ☐ Conforme ☐ Non conforme — anomalie n° ______ — gravité ____ — initiales ____

### CA-E-GN-11 — Généralités (essentiel)

> Lorsqu’un quart est attribué manuellement par un utilisateur autorisé dans un contexte d’exception opérationnelle, la solution ne doit pas empêcher l’attribution même si certaines règles de priorisation automatisées ne sont pas respectées, tout en assurant la traçabilité complète de la décision.

- **Verdict à la matrice** : ✅ Acquis
- **Écran** : Urgences-santé › Gestion des quarts › Quarts et équipages — `/usequipages`
- **Profil** : `claire.nadeau` (Planificatrice des effectifs)
- **Étapes** :
  1. Se connecter à l'organisme d'essai avec `claire.nadeau` (Planificatrice des effectifs).
  2. Ouvrir **Urgences-santé › Gestion des quarts › Quarts et équipages** (adresse `/usequipages`).
  3. Attribuer une ressource qu'une règle écarte : l'écran liste les avertissements et exige une justification avant de forcer.
- **Résultat attendu** :
  - les commandes `pla-equipages`, `pla-eq-tableau`, `pla-eq-lot` sont présentes et utilisables au clavier ;
  - le geste décrit à la dernière étape produit l'effet annoncé, sans erreur affichée ;
  - l'écran, dans l'état atteint, ne présente aucune violation axe-core (WCAG 2.2 AA).
  - comportement C-02 : Une règle de repos bloque l'attribution en nommant son motif ; forcer exige le niveau GESTION et une justification, et le forçage est tracé.
- **Essais automatisés qui le couvrent déjà** :
  - Écran et commandes : `e2e/tests/e2e/urgences-sante-exigences.spec.ts` (avec audit axe-core)
  - Comportement (Java) : `PlanificationDomaineTest`
  - Bout en bout : `effectifs-planification.spec.ts`
  - Scénarios d'API (`e2e/tests/e2e/urgences-sante-comportements.spec.ts`) : C-02
- **Résultat obtenu** : ☐ Conforme ☐ Non conforme — anomalie n° ______ — gravité ____ — initiales ____

### CA-E-GN-12 — Généralités (essentiel)

> La solution doit permettre de définir des règles de conformité des disponibilités selon les conventions collectives, le statut d’emploi, le type de poste ou le contexte opérationnel.

- **Verdict à la matrice** : ✅ Acquis
- **Écran** : Urgences-santé › Administration des effectifs › Règles des effectifs — `/usregles`
- **Profil** : `admin` (administration de l'organisme)
- **Étapes** :
  1. Se connecter à l'organisme d'essai avec `admin` (administration de l'organisme).
  2. Ouvrir **Urgences-santé › Administration des effectifs › Règles des effectifs** (adresse `/usregles`).
  3. Activer la commande `rgl-nouvelle` (la portée et le fait se choisissent dans le formulaire, pas dans la liste).
  4. Contexte « Disponibilités », « Nouvelle règle » : la portée (unité, corps, statut) et le fait choisis forment la règle de conformité.
- **Résultat attendu** :
  - les commandes `rgl-ecran`, `rgl-formulaire`, `rgl-form-portee`, `rgl-form-cle` sont présentes et utilisables au clavier ;
  - le geste décrit à la dernière étape produit l'effet annoncé, sans erreur affichée ;
  - l'écran, dans l'état atteint, ne présente aucune violation axe-core (WCAG 2.2 AA).
  - comportement C-04 : Hors collecte, le dépôt de disponibilités est refusé en disant pourquoi ; en collecte, un dépôt trop maigre est bloqué avec son motif, un dépôt suffisant passe avec un avertissement.
- **Essais automatisés qui le couvrent déjà** :
  - Écran et commandes : `e2e/tests/e2e/urgences-sante-exigences.spec.ts` (avec audit axe-core)
  - Comportement (Java) : `ReglesEffectifsDomaineTest`
  - Bout en bout : `effectifs-regles.spec.ts`
  - Scénarios d'API (`e2e/tests/e2e/urgences-sante-comportements.spec.ts`) : C-04
- **Résultat obtenu** : ☐ Conforme ☐ Non conforme — anomalie n° ______ — gravité ____ — initiales ____

### CA-E-GN-13 — Généralités (essentiel)

> La solution doit permettre aux employés d'inscrire leurs disponibilités pour une période donnée.

- **Verdict à la matrice** : ✅ Acquis
- **Écran** : Urgences-santé › Disponibilités › Mes disponibilités — `/mesdisponibilites`
- **Profil** : `julie.roy` (Paramédic — temps complet)
- **Étapes** :
  1. Se connecter à l'organisme d'essai avec `julie.roy` (Paramédic — temps complet).
  2. Ouvrir **Urgences-santé › Disponibilités › Mes disponibilités** (adresse `/mesdisponibilites`).
  3. L'employé choisit la période et dépose ses plages par jour.
- **Résultat attendu** :
  - les commandes `mdi-titre`, `mdi-periode`, `mdi-deposer` sont présentes et utilisables au clavier ;
  - le geste décrit à la dernière étape produit l'effet annoncé, sans erreur affichée ;
  - l'écran, dans l'état atteint, ne présente aucune violation axe-core (WCAG 2.2 AA).
  - comportement C-04 : Hors collecte, le dépôt de disponibilités est refusé en disant pourquoi ; en collecte, un dépôt trop maigre est bloqué avec son motif, un dépôt suffisant passe avec un avertissement.
- **Essais automatisés qui le couvrent déjà** :
  - Écran et commandes : `e2e/tests/e2e/urgences-sante-exigences.spec.ts` (avec audit axe-core)
  - Comportement (Java) : `ControleurDisponibilitesEffectifsHttpTest`
  - Scénarios d'API (`e2e/tests/e2e/urgences-sante-comportements.spec.ts`) : C-04
- **Résultat obtenu** : ☐ Conforme ☐ Non conforme — anomalie n° ______ — gravité ____ — initiales ____

### CA-E-GN-14 — Généralités (essentiel)

> La solution doit exclure automatiquement une ressource du bassin de candidats à un quart lorsque ses disponibilités ne respectent pas les règles de conformité définies.

- **Verdict à la matrice** : ✅ Acquis
- **Écran** : Urgences-santé › Disponibilités › Disponibilités de la période — `/usdisponibilites`
- **Profil** : `claire.nadeau` (Planificatrice des effectifs)
- **Étapes** :
  1. Se connecter à l'organisme d'essai avec `claire.nadeau` (Planificatrice des effectifs).
  2. Ouvrir **Urgences-santé › Disponibilités › Disponibilités de la période** (adresse `/usdisponibilites`).
  3. Sur une place, « Bassin » : les candidats retenus et, à part, les exclus avec le motif de leur exclusion.
- **Résultat attendu** :
  - les commandes `pla-dispo`, `pla-dispo-depots` sont présentes et utilisables au clavier ;
  - le geste décrit à la dernière étape produit l'effet annoncé, sans erreur affichée ;
  - l'écran, dans l'état atteint, ne présente aucune violation axe-core (WCAG 2.2 AA).
  - comportement C-05 : Le bassin de candidats d'une place exclut, motif à l'appui, la ressource qui n'a rien déposé.
- **Essais automatisés qui le couvrent déjà** :
  - Écran et commandes : `e2e/tests/e2e/urgences-sante-exigences.spec.ts` (avec audit axe-core)
  - Comportement (Java) : `ControleurDisponibilitesEffectifsHttpTest`
  - Bout en bout : `effectifs-disponibilites.spec.ts`
  - Scénarios d'API (`e2e/tests/e2e/urgences-sante-comportements.spec.ts`) : C-05
- **Résultat obtenu** : ☐ Conforme ☐ Non conforme — anomalie n° ______ — gravité ____ — initiales ____

### CA-E-GN-15 — Généralités (essentiel)

> La solution doit valider la conformité des disponibilités au moment de leur saisie et empêcher ou signaler toute non-conformité selon des paramètres configurables.

- **Verdict à la matrice** : ✅ Acquis
- **Écran** : Urgences-santé › Disponibilités › Mes disponibilités — `/mesdisponibilites`
- **Profil** : `julie.roy` (Paramédic — temps complet)
- **Étapes** :
  1. Se connecter à l'organisme d'essai avec `julie.roy` (Paramédic — temps complet).
  2. Ouvrir **Urgences-santé › Disponibilités › Mes disponibilités** (adresse `/mesdisponibilites`).
  3. Déposer une plage non conforme : le dépôt est refusé ou signalé selon le réglage de la règle, à la saisie.
- **Résultat attendu** :
  - les commandes `mdi-titre`, `mdi-conformite`, `mdi-deposer` sont présentes et utilisables au clavier ;
  - le geste décrit à la dernière étape produit l'effet annoncé, sans erreur affichée ;
  - l'écran, dans l'état atteint, ne présente aucune violation axe-core (WCAG 2.2 AA).
  - comportement C-04 : Hors collecte, le dépôt de disponibilités est refusé en disant pourquoi ; en collecte, un dépôt trop maigre est bloqué avec son motif, un dépôt suffisant passe avec un avertissement.
- **Essais automatisés qui le couvrent déjà** :
  - Écran et commandes : `e2e/tests/e2e/urgences-sante-exigences.spec.ts` (avec audit axe-core)
  - Comportement (Java) : `ControleurDisponibilitesEffectifsHttpTest`
  - Bout en bout : `effectifs-disponibilites.spec.ts`, `effectifs-regles.spec.ts`
  - Scénarios d'API (`e2e/tests/e2e/urgences-sante-comportements.spec.ts`) : C-04
- **Résultat obtenu** : ☐ Conforme ☐ Non conforme — anomalie n° ______ — gravité ____ — initiales ____

### CA-E-GN-16 — Généralités (essentiel)

> La solution doit informer l’utilisateur des motifs d’inadmissibilité liés à la non-conformité de ses disponibilités.

- **Verdict à la matrice** : ✅ Acquis
- **Écran** : Urgences-santé › Disponibilités › Mes disponibilités — `/mesdisponibilites`
- **Profil** : `julie.roy` (Paramédic — temps complet)
- **Étapes** :
  1. Se connecter à l'organisme d'essai avec `julie.roy` (Paramédic — temps complet).
  2. Ouvrir **Urgences-santé › Disponibilités › Mes disponibilités** (adresse `/mesdisponibilites`).
  3. Le refus nomme la règle et ce qu'elle exige : l'employé lit le motif de son inadmissibilité, pas un code.
- **Résultat attendu** :
  - les commandes `mdi-titre`, `mdi-conformite`, `mdi-etat` sont présentes et utilisables au clavier ;
  - le geste décrit à la dernière étape produit l'effet annoncé, sans erreur affichée ;
  - l'écran, dans l'état atteint, ne présente aucune violation axe-core (WCAG 2.2 AA).
  - comportement C-04 : Hors collecte, le dépôt de disponibilités est refusé en disant pourquoi ; en collecte, un dépôt trop maigre est bloqué avec son motif, un dépôt suffisant passe avec un avertissement.
  - comportement C-05 : Le bassin de candidats d'une place exclut, motif à l'appui, la ressource qui n'a rien déposé.
- **Essais automatisés qui le couvrent déjà** :
  - Écran et commandes : `e2e/tests/e2e/urgences-sante-exigences.spec.ts` (avec audit axe-core)
  - Comportement (Java) : `ReglesEffectifsDomaineTest`, `ControleurDisponibilitesEffectifsHttpTest`
  - Bout en bout : `effectifs-disponibilites.spec.ts`, `effectifs-regles.spec.ts`, `urgences-sante.ts`, `audit-wcag22.spec.ts`
  - Scénarios d'API (`e2e/tests/e2e/urgences-sante-comportements.spec.ts`) : C-04, C-05
- **Résultat obtenu** : ☐ Conforme ☐ Non conforme — anomalie n° ______ — gravité ____ — initiales ____

### CA-E-GN-17 — Généralités (essentiel)

> La solution doit permettre l'identification du milieu physique lié au quart de travail planifié.

- **Verdict à la matrice** : ✅ Acquis
- **Écran** : Urgences-santé › Administration des effectifs › Milieux physiques — `/usmilieux`
- **Profil** : `admin` (administration de l'organisme)
- **Étapes** :
  1. Se connecter à l'organisme d'essai avec `admin` (administration de l'organisme).
  2. Ouvrir **Urgences-santé › Administration des effectifs › Milieux physiques** (adresse `/usmilieux`).
  3. Activer la commande `mil-ajouter` (la fiche du milieu est un tiroir).
  4. « Ajouter un milieu » : type, unité, adresse et coordonnées ; le quart planifié s'y rattache.
- **Résultat attendu** :
  - les commandes `mil-ecran`, `mil-fiche`, `mil-form-code`, `mil-form-type`, `mil-form-unite` sont présentes et utilisables au clavier ;
  - le geste décrit à la dernière étape produit l'effet annoncé, sans erreur affichée ;
  - l'écran, dans l'état atteint, ne présente aucune violation axe-core (WCAG 2.2 AA).
- **Essais automatisés qui le couvrent déjà** :
  - Écran et commandes : `e2e/tests/e2e/urgences-sante-exigences.spec.ts` (avec audit axe-core)
  - Comportement (Java) : `SocleEffectifsDomaineTest`
- **Résultat obtenu** : ☐ Conforme ☐ Non conforme — anomalie n° ______ — gravité ____ — initiales ____

### CA-E-CH-1 — Confection des horaires (essentiel)

> La solution doit permettre la réalisation d’un processus annuel structuré de planification et de révision des horaires (AGPH) pour l'attribution des postes. La solution permet donc aux ressources d'émettre leurs disponibilités (journée/horaire), en respectant les règles prédéfinies de la Convention collective (nombre, durée des quarts et optimisation des budgets) et Conditions particulières ; et paramétrables, selon les besoins, par les personnes identifiées au sein du service de planification des effectifs.

- **Verdict à la matrice** : ✅ Acquis
- **Écran** : Urgences-santé › Confection AGPH › Confection annuelle (AGPH) — `/usagph`
- **Profil** : `claire.nadeau` (Planificatrice des effectifs)
- **Étapes** :
  1. Se connecter à l'organisme d'essai avec `claire.nadeau` (Planificatrice des effectifs).
  2. Ouvrir **Urgences-santé › Confection AGPH › Confection annuelle (AGPH)** (adresse `/usagph`).
  3. Activer la commande `pla-agph-budget-edition-ouvrir` (le formulaire vit dans un dialogue depuis le 8 novembre 2026 : ses champs n'existent qu'ouvert).
  4. Campagne annuelle : ouvrir la collecte, fixer le budget d'heures, suivre les dépôts de disponibilités rang par rang.
- **Résultat attendu** :
  - les commandes `pla-agph-dispo`, `pla-agph-budget-edition-ouvrir`, `pla-agph-budget` sont présentes et utilisables au clavier ;
  - le geste décrit à la dernière étape produit l'effet annoncé, sans erreur affichée ;
  - l'écran, dans l'état atteint, ne présente aucune violation axe-core (WCAG 2.2 AA).
- **Essais automatisés qui le couvrent déjà** :
  - Écran et commandes : `e2e/tests/e2e/urgences-sante-exigences.spec.ts` (avec audit axe-core)
  - Comportement (Java) : `AgphDomaineTest`, `ControleurAgphEffectifsHttpTest`
  - Bout en bout : `effectifs-agph.spec.ts`, `audit-wcag22.spec.ts`
- **Résultat obtenu** : ☐ Conforme ☐ Non conforme — anomalie n° ______ — gravité ____ — initiales ____

### CA-E-CH-2 — Confection des horaires (essentiel)

> La solution doit permettre la création de quarts de travail et de postes liés aux quarts de travail

- **Verdict à la matrice** : ✅ Acquis
- **Écran** : Urgences-santé › Gestion des quarts › Postes — `/uspostes`
- **Profil** : `claire.nadeau` (Planificatrice des effectifs)
- **Étapes** :
  1. Se connecter à l'organisme d'essai avec `claire.nadeau` (Planificatrice des effectifs).
  2. Ouvrir **Urgences-santé › Gestion des quarts › Postes** (adresse `/uspostes`).
  3. Activer la commande `pla-poste-nouveau` (le poste se crée dans un tiroir).
  4. « Nouveau poste » : code, libellé, cycle de quarts et début de cycle.
- **Résultat attendu** :
  - les commandes `pla-postes`, `pla-poste-fiche`, `pla-pos-code`, `pla-pos-cycle-ajouter` sont présentes et utilisables au clavier ;
  - le geste décrit à la dernière étape produit l'effet annoncé, sans erreur affichée ;
  - l'écran, dans l'état atteint, ne présente aucune violation axe-core (WCAG 2.2 AA).
  - comportement C-03 : Un poste se publie une fois ; une liste vide ou un poste déjà publié est refusé.
- **Essais automatisés qui le couvrent déjà** :
  - Écran et commandes : `e2e/tests/e2e/urgences-sante-exigences.spec.ts` (avec audit axe-core)
  - Comportement (Java) : `PlanificationDomaineTest`
  - Scénarios d'API (`e2e/tests/e2e/urgences-sante-comportements.spec.ts`) : C-03
- **Résultat obtenu** : ☐ Conforme ☐ Non conforme — anomalie n° ______ — gravité ____ — initiales ____

### CA-E-CH-3 — Confection des horaires (essentiel)

> La solution doit permettre la modification de quarts de travail et de postes liés aux quarts de travail (1 ou plusieurs postes)

- **Verdict à la matrice** : ✅ Acquis
- **Écran** : Urgences-santé › Gestion des quarts › Postes — `/uspostes`
- **Profil** : `claire.nadeau` (Planificatrice des effectifs)
- **Étapes** :
  1. Se connecter à l'organisme d'essai avec `claire.nadeau` (Planificatrice des effectifs).
  2. Ouvrir **Urgences-santé › Gestion des quarts › Postes** (adresse `/uspostes`).
  3. Sélectionner des postes, « Modifier en lot » : le cycle est remplacé pour tous, les conflits sont listés avant d'écrire.
- **Résultat attendu** :
  - les commandes `pla-postes-lot`, `pla-postes-publier` sont présentes et utilisables au clavier ;
  - le geste décrit à la dernière étape produit l'effet annoncé, sans erreur affichée ;
  - l'écran, dans l'état atteint, ne présente aucune violation axe-core (WCAG 2.2 AA).
- **Essais automatisés qui le couvrent déjà** :
  - Écran et commandes : `e2e/tests/e2e/urgences-sante-exigences.spec.ts` (avec audit axe-core)
  - Comportement (Java) : `RepercussionPosteDomaineTest`, `ControleurPlanificationEffectifsHttpTest`
  - Bout en bout : `effectifs-planification.spec.ts`, `audit-wcag22.spec.ts`
- **Résultat obtenu** : ☐ Conforme ☐ Non conforme — anomalie n° ______ — gravité ____ — initiales ____

### CA-E-CH-4 — Confection des horaires (essentiel)

> La solution doit permettre la publication de quarts de travail et de postes liés aux quarts de travail

- **Verdict à la matrice** : ✅ Acquis
- **Écran** : Urgences-santé › Gestion des quarts › Postes — `/uspostes`
- **Profil** : `claire.nadeau` (Planificatrice des effectifs)
- **Étapes** :
  1. Se connecter à l'organisme d'essai avec `claire.nadeau` (Planificatrice des effectifs).
  2. Ouvrir **Urgences-santé › Gestion des quarts › Postes** (adresse `/uspostes`).
  3. « Publier » : les postes passent de brouillon à publié, et leurs quarts entrent dans la planification.
- **Résultat attendu** :
  - les commandes `pla-postes`, `pla-postes-publier`, `pla-postes-tableau` sont présentes et utilisables au clavier ;
  - le geste décrit à la dernière étape produit l'effet annoncé, sans erreur affichée ;
  - l'écran, dans l'état atteint, ne présente aucune violation axe-core (WCAG 2.2 AA).
  - comportement C-03 : Un poste se publie une fois ; une liste vide ou un poste déjà publié est refusé.
- **Essais automatisés qui le couvrent déjà** :
  - Écran et commandes : `e2e/tests/e2e/urgences-sante-exigences.spec.ts` (avec audit axe-core)
  - Comportement (Java) : `ControleurPlanificationEffectifsHttpTest`
  - Scénarios d'API (`e2e/tests/e2e/urgences-sante-comportements.spec.ts`) : C-03
- **Résultat obtenu** : ☐ Conforme ☐ Non conforme — anomalie n° ______ — gravité ____ — initiales ____

### CA-E-CH-5 — Confection des horaires (essentiel)

> La solution doit permettre d’associer à chaque poste ses paramètres fonctionnels.

- **Verdict à la matrice** : ✅ Acquis
- **Écran** : Urgences-santé › Gestion des quarts › Postes — `/uspostes`
- **Profil** : `claire.nadeau` (Planificatrice des effectifs)
- **Étapes** :
  1. Se connecter à l'organisme d'essai avec `claire.nadeau` (Planificatrice des effectifs).
  2. Ouvrir **Urgences-santé › Gestion des quarts › Postes** (adresse `/uspostes`).
  3. Activer la commande `pla-poste-nouveau` (les paramètres sont les champs de la fiche).
  4. Fiche du poste : statut d'emploi, corps, milieu, nature et cycle — les paramètres fonctionnels du poste.
- **Résultat attendu** :
  - les commandes `pla-poste-fiche`, `pla-pos-libelle`, `pla-pos-debut` sont présentes et utilisables au clavier ;
  - le geste décrit à la dernière étape produit l'effet annoncé, sans erreur affichée ;
  - l'écran, dans l'état atteint, ne présente aucune violation axe-core (WCAG 2.2 AA).
  - comportement C-03 : Un poste se publie une fois ; une liste vide ou un poste déjà publié est refusé.
- **Essais automatisés qui le couvrent déjà** :
  - Écran et commandes : `e2e/tests/e2e/urgences-sante-exigences.spec.ts` (avec audit axe-core)
  - Comportement (Java) : `PlanificationDomaineTest`
  - Scénarios d'API (`e2e/tests/e2e/urgences-sante-comportements.spec.ts`) : C-03
- **Résultat obtenu** : ☐ Conforme ☐ Non conforme — anomalie n° ______ — gravité ____ — initiales ____

### CA-E-CH-6 — Confection des horaires (essentiel)

> La solution doit identifier automatiquement les postes vacants causés par des absences planifiées.

- **Verdict à la matrice** : ✅ Acquis
- **Écran** : Urgences-santé › Absences des effectifs › Absences et comblement — `/usabsences`
- **Profil** : `claire.nadeau` (Planificatrice des effectifs)
- **Étapes** :
  1. Se connecter à l'organisme d'essai avec `claire.nadeau` (Planificatrice des effectifs).
  2. Ouvrir **Urgences-santé › Absences des effectifs › Absences et comblement** (adresse `/usabsences`).
  3. Une absence accordée réserve ses places : l'onglet les montre comme vacantes, sans geste de planificateur.
- **Résultat attendu** :
  - les commandes `pla-abs`, `pla-abs-table` sont présentes et utilisables au clavier ;
  - le geste décrit à la dernière étape produit l'effet annoncé, sans erreur affichée ;
  - l'écran, dans l'état atteint, ne présente aucune violation axe-core (WCAG 2.2 AA).
  - comportement C-07 : La dérogation exige un droit, un motif et un code ; elle accorde l'absence et rend les places vacantes par un mécanisme journalisé.
- **Essais automatisés qui le couvrent déjà** :
  - Écran et commandes : `e2e/tests/e2e/urgences-sante-exigences.spec.ts` (avec audit axe-core)
  - Comportement (Java) : `ControleurAbsencesEffectifsHttpTest`
  - Bout en bout : `effectifs-absences.spec.ts`
  - Scénarios d'API (`e2e/tests/e2e/urgences-sante-comportements.spec.ts`) : C-07
- **Résultat obtenu** : ☐ Conforme ☐ Non conforme — anomalie n° ______ — gravité ____ — initiales ____

### CA-E-CH-7 — Confection des horaires (essentiel)

> La solution doit permettre l’ouverture d’un processus de remplacements long terme.

- **Verdict à la matrice** : ✅ Acquis
- **Écran** : Urgences-santé › Confection AGPH › Confection annuelle (AGPH) — `/usagph`
- **Profil** : `claire.nadeau` (Planificatrice des effectifs)
- **Étapes** :
  1. Se connecter à l'organisme d'essai avec `claire.nadeau` (Planificatrice des effectifs).
  2. Ouvrir **Urgences-santé › Confection AGPH › Confection annuelle (AGPH)** (adresse `/usagph`).
  3. « Ouvrir un remplacement long terme » : poste, fenêtre et date limite pour se manifester.
- **Résultat attendu** :
  - les commandes `pla-rlt-table`, `pla-rlt-ouvrir` sont présentes et utilisables au clavier ;
  - le geste décrit à la dernière étape produit l'effet annoncé, sans erreur affichée ;
  - l'écran, dans l'état atteint, ne présente aucune violation axe-core (WCAG 2.2 AA).
- **Essais automatisés qui le couvrent déjà** :
  - Écran et commandes : `e2e/tests/e2e/urgences-sante-exigences.spec.ts` (avec audit axe-core)
  - Comportement (Java) : `ControleurAgphEffectifsHttpTest`
- **Résultat obtenu** : ☐ Conforme ☐ Non conforme — anomalie n° ______ — gravité ____ — initiales ____

### CA-E-CH-8 — Confection des horaires (essentiel)

> La solution doit permettre aux employés admissibles de se manifester pour des remplacements long terme.

- **Verdict à la matrice** : ✅ Acquis
- **Écran** : Urgences-santé › Confection AGPH › Mon AGPH — `/monagph`
- **Profil** : `julie.roy` (Paramédic — temps complet)
- **Étapes** :
  1. Se connecter à l'organisme d'essai avec `julie.roy` (Paramédic — temps complet).
  2. Ouvrir **Urgences-santé › Confection AGPH › Mon AGPH** (adresse `/monagph`).
  3. L'employé admissible voit le remplacement ouvert et se manifeste pour lui.
- **Résultat attendu** :
  - les commandes `agm-titre`, `agm-info` ou `agm-sans-remplacement` sont présentes et utilisables au clavier ;
  - le geste décrit à la dernière étape produit l'effet annoncé, sans erreur affichée ;
  - l'écran, dans l'état atteint, ne présente aucune violation axe-core (WCAG 2.2 AA).
- **Essais automatisés qui le couvrent déjà** :
  - Écran et commandes : `e2e/tests/e2e/urgences-sante-exigences.spec.ts` (avec audit axe-core)
  - Comportement (Java) : `ControleurAgphEffectifsHttpTest`
  - Bout en bout : `effectifs-agph.spec.ts`
- **Résultat obtenu** : ☐ Conforme ☐ Non conforme — anomalie n° ______ — gravité ____ — initiales ____

### CA-E-CH-9 — Confection des horaires (essentiel)

> La solution doit permettre que certains remplacements demeurent non comblés.

- **Verdict à la matrice** : ✅ Acquis
- **Écran** : Urgences-santé › Confection AGPH › Confection annuelle (AGPH) — `/usagph`
- **Profil** : `claire.nadeau` (Planificatrice des effectifs)
- **Étapes** :
  1. Se connecter à l'organisme d'essai avec `claire.nadeau` (Planificatrice des effectifs).
  2. Ouvrir **Urgences-santé › Confection AGPH › Confection annuelle (AGPH)** (adresse `/usagph`).
  3. Clore un remplacement « non comblé » avec son motif : l'état le dit, et il n'est pas forcé à un titulaire.
- **Résultat attendu** :
  - les commandes `pla-agph-etat` sont présentes et utilisables au clavier ;
  - le geste décrit à la dernière étape produit l'effet annoncé, sans erreur affichée ;
  - l'écran, dans l'état atteint, ne présente aucune violation axe-core (WCAG 2.2 AA).
- **Essais automatisés qui le couvrent déjà** :
  - Écran et commandes : `e2e/tests/e2e/urgences-sante-exigences.spec.ts` (avec audit axe-core)
  - Comportement (Java) : `ControleurAgphEffectifsHttpTest`
  - Bout en bout : `effectifs-agph.spec.ts`, `audit-wcag22.spec.ts`
- **Résultat obtenu** : ☐ Conforme ☐ Non conforme — anomalie n° ______ — gravité ____ — initiales ____

### CA-E-CH-10 — Confection des horaires (essentiel)

> La solution doit permettre le transfert automatique des quarts avec postes non couverts vers la gestion régulière.

- **Verdict à la matrice** : ✅ Acquis
- **Écran** : Urgences-santé › Gestion des quarts › Comblement — `/uscomblement`
- **Profil** : `claire.nadeau` (Planificatrice des effectifs)
- **Étapes** :
  1. Se connecter à l'organisme d'essai avec `claire.nadeau` (Planificatrice des effectifs).
  2. Ouvrir **Urgences-santé › Gestion des quarts › Comblement** (adresse `/uscomblement`).
  3. Clore la campagne : les quarts restés sans titulaire apparaissent au tableau de comblement de la gestion régulière.
- **Résultat attendu** :
  - les commandes `uscomblement-titre`, `pla-comblement` sont présentes et utilisables au clavier ;
  - le geste décrit à la dernière étape produit l'effet annoncé, sans erreur affichée ;
  - l'écran, dans l'état atteint, ne présente aucune violation axe-core (WCAG 2.2 AA).
- **Essais automatisés qui le couvrent déjà** :
  - Écran et commandes : `e2e/tests/e2e/urgences-sante-exigences.spec.ts` (avec audit axe-core)
  - Comportement (Java) : `ControleurAgphEffectifsHttpTest`
  - Bout en bout : `effectifs-agph.spec.ts`
- **Résultat obtenu** : ☐ Conforme ☐ Non conforme — anomalie n° ______ — gravité ____ — initiales ____

### CA-E-GR-1 — Gestion régulière des horaires (essentiel)

> La solution doit permettre la gestion continue des horaires en dehors du processus AGPH.

- **Verdict à la matrice** : ✅ Acquis
- **Écran** : Urgences-santé › Gestion des quarts › Comblement — `/uscomblement`
- **Profil** : `claire.nadeau` (Planificatrice des effectifs)
- **Étapes** :
  1. Se connecter à l'organisme d'essai avec `claire.nadeau` (Planificatrice des effectifs).
  2. Ouvrir **Urgences-santé › Gestion des quarts › Comblement** (adresse `/uscomblement`).
  3. Le tableau du matin, hors campagne annuelle : taux de comblement, quarts sous minimum, quotas manqués.
- **Résultat attendu** :
  - les commandes `pla-comblement`, `pla-tuile-taux`, `pla-tuile-sous-minimum` sont présentes et utilisables au clavier ;
  - le geste décrit à la dernière étape produit l'effet annoncé, sans erreur affichée ;
  - l'écran, dans l'état atteint, ne présente aucune violation axe-core (WCAG 2.2 AA).
- **Essais automatisés qui le couvrent déjà** :
  - Écran et commandes : `e2e/tests/e2e/urgences-sante-exigences.spec.ts` (avec audit axe-core)
  - Comportement (Java) : `ControleurPlanificationEffectifsHttpTest`
- **Résultat obtenu** : ☐ Conforme ☐ Non conforme — anomalie n° ______ — gravité ____ — initiales ____

### CA-E-GR-2 — Gestion régulière des horaires (essentiel)

> La solution doit prendre en charge les quarts non couverts à l’issue de l’AGPH.

- **Verdict à la matrice** : ✅ Acquis
- **Écran** : Urgences-santé › Gestion des quarts › Comblement — `/uscomblement`
- **Profil** : `claire.nadeau` (Planificatrice des effectifs)
- **Étapes** :
  1. Se connecter à l'organisme d'essai avec `claire.nadeau` (Planificatrice des effectifs).
  2. Ouvrir **Urgences-santé › Gestion des quarts › Comblement** (adresse `/uscomblement`).
  3. Les quarts non couverts à la clôture de la campagne sont pris en charge ici, et non laissés dans l'AGPH.
- **Résultat attendu** :
  - les commandes `uscomblement-titre`, `pla-sous-minimum` sont présentes et utilisables au clavier ;
  - le geste décrit à la dernière étape produit l'effet annoncé, sans erreur affichée ;
  - l'écran, dans l'état atteint, ne présente aucune violation axe-core (WCAG 2.2 AA).
- **Essais automatisés qui le couvrent déjà** :
  - Écran et commandes : `e2e/tests/e2e/urgences-sante-exigences.spec.ts` (avec audit axe-core)
  - Comportement (Java) : `ControleurAgphEffectifsHttpTest`
  - Bout en bout : `effectifs-agph.spec.ts`
- **Résultat obtenu** : ☐ Conforme ☐ Non conforme — anomalie n° ______ — gravité ____ — initiales ____

### CA-E-GR-3 — Gestion régulière des horaires (essentiel)

> La solution doit évaluer l’impact d’une absence sur le taux de comblement.

- **Verdict à la matrice** : ✅ Acquis
- **Écran** : Urgences-santé › Absences des effectifs › Absences et comblement — `/usabsences`
- **Profil** : `claire.nadeau` (Planificatrice des effectifs)
- **Étapes** :
  1. Se connecter à l'organisme d'essai avec `claire.nadeau` (Planificatrice des effectifs).
  2. Ouvrir **Urgences-santé › Absences des effectifs › Absences et comblement** (adresse `/usabsences`).
  3. Ouvrir une demande d'absence de la liste : sa fiche calcule le taux de comblement qu'elle laisserait, avant toute décision.
- **Résultat attendu** :
  - les commandes `pla-abs`, `pla-abs-filtre`, `pla-abs-table` sont présentes et utilisables au clavier ;
  - le geste décrit à la dernière étape produit l'effet annoncé, sans erreur affichée ;
  - l'écran, dans l'état atteint, ne présente aucune violation axe-core (WCAG 2.2 AA).
  - comportement C-06 : Une absence qui ferait tomber le comblement est refusée d'office, et le refus est conservé avec son motif.
- **Essais automatisés qui le couvrent déjà** :
  - Écran et commandes : `e2e/tests/e2e/urgences-sante-exigences.spec.ts` (avec audit axe-core)
  - Comportement (Java) : `ControleurAbsencesEffectifsHttpTest`
  - Bout en bout : `effectifs-absences.spec.ts`
  - Scénarios d'API (`e2e/tests/e2e/urgences-sante-comportements.spec.ts`) : C-06
- **Résultat obtenu** : ☐ Conforme ☐ Non conforme — anomalie n° ______ — gravité ____ — initiales ____

### CA-E-GR-4 — Gestion régulière des horaires (essentiel)

> La solution doit identifier automatiquement les quarts à offrir pour comblement.

- **Verdict à la matrice** : ✅ Acquis
- **Écran** : Urgences-santé › Absences des effectifs › Absences et comblement — `/usabsences`
- **Profil** : `claire.nadeau` (Planificatrice des effectifs)
- **Étapes** :
  1. Se connecter à l'organisme d'essai avec `claire.nadeau` (Planificatrice des effectifs).
  2. Ouvrir **Urgences-santé › Absences des effectifs › Absences et comblement** (adresse `/usabsences`).
  3. Les places libérées par l'absence accordée sont offertes d'elles-mêmes au comblement, sans les désigner une à une.
- **Résultat attendu** :
  - les commandes `pla-abs`, `pla-abs-table` sont présentes et utilisables au clavier ;
  - le geste décrit à la dernière étape produit l'effet annoncé, sans erreur affichée ;
  - l'écran, dans l'état atteint, ne présente aucune violation axe-core (WCAG 2.2 AA).
- **Essais automatisés qui le couvrent déjà** :
  - Écran et commandes : `e2e/tests/e2e/urgences-sante-exigences.spec.ts` (avec audit axe-core)
  - Comportement (Java) : `ControleurAbsencesEffectifsHttpTest`
- **Résultat obtenu** : ☐ Conforme ☐ Non conforme — anomalie n° ______ — gravité ____ — initiales ____

### CA-E-GR-5 — Gestion régulière des horaires (essentiel)

> La solution doit permettre la gestion d’ajustements journaliers.

- **Verdict à la matrice** : ✅ Acquis
- **Écran** : Urgences-santé › Gestion des quarts › Périodes — `/usperiodes`
- **Profil** : `claire.nadeau` (Planificatrice des effectifs)
- **Étapes** :
  1. Se connecter à l'organisme d'essai avec `claire.nadeau` (Planificatrice des effectifs).
  2. Ouvrir **Urgences-santé › Gestion des quarts › Périodes** (adresse `/usperiodes`).
  3. Ajuster une journée : « Nouvelle période » et l'ajustement d'un quart existant portent les corrections du jour.
- **Résultat attendu** :
  - les commandes `pla-periodes`, `pla-periode-nouvelle` sont présentes et utilisables au clavier ;
  - le geste décrit à la dernière étape produit l'effet annoncé, sans erreur affichée ;
  - l'écran, dans l'état atteint, ne présente aucune violation axe-core (WCAG 2.2 AA).
- **Essais automatisés qui le couvrent déjà** :
  - Écran et commandes : `e2e/tests/e2e/urgences-sante-exigences.spec.ts` (avec audit axe-core)
  - Comportement (Java) : `ControleurPlanificationEffectifsHttpTest`
  - Bout en bout : `effectifs-planification.spec.ts`
- **Résultat obtenu** : ☐ Conforme ☐ Non conforme — anomalie n° ______ — gravité ____ — initiales ____

### CA-E-GR-6 — Gestion régulière des horaires (essentiel)

> La solution doit permettre la création et la gestion de quarts ad hoc.

- **Verdict à la matrice** : ✅ Acquis
- **Écran** : Urgences-santé › Gestion des quarts › Quarts et équipages — `/usequipages`
- **Profil** : `claire.nadeau` (Planificatrice des effectifs)
- **Étapes** :
  1. Se connecter à l'organisme d'essai avec `claire.nadeau` (Planificatrice des effectifs).
  2. Ouvrir **Urgences-santé › Gestion des quarts › Quarts et équipages** (adresse `/usequipages`).
  3. « Quart ad hoc » crée et gère un quart hors cycle ; « Libérer » le retire avec son motif.
- **Résultat attendu** :
  - les commandes `pla-equipages`, `pla-eq-adhoc` sont présentes et utilisables au clavier ;
  - le geste décrit à la dernière étape produit l'effet annoncé, sans erreur affichée ;
  - l'écran, dans l'état atteint, ne présente aucune violation axe-core (WCAG 2.2 AA).
  - comportement C-01 : Un quart ad hoc se crée hors de tout poste ; hors période, sans composition, à minimum impossible ou sur une plage inconnue, il est refusé.
- **Essais automatisés qui le couvrent déjà** :
  - Écran et commandes : `e2e/tests/e2e/urgences-sante-exigences.spec.ts` (avec audit axe-core)
  - Comportement (Java) : `ControleurPlanificationEffectifsHttpTest`
  - Scénarios d'API (`e2e/tests/e2e/urgences-sante-comportements.spec.ts`) : C-01
- **Résultat obtenu** : ☐ Conforme ☐ Non conforme — anomalie n° ______ — gravité ____ — initiales ____

### CA-E-GR-7 — Gestion régulière des horaires (essentiel)

> La solution doit permettre la gestion des échanges de quarts incluant les validations d’admissibilité et de contiguïté.

- **Verdict à la matrice** : ✅ Acquis
- **Écran** : Urgences-santé › Demandes et échanges › Demandes et échanges — `/usechanges`
- **Profil** : `claire.nadeau` (Planificatrice des effectifs)
- **Étapes** :
  1. Se connecter à l'organisme d'essai avec `claire.nadeau` (Planificatrice des effectifs).
  2. Ouvrir **Urgences-santé › Demandes et échanges › Demandes et échanges** (adresse `/usechanges`).
  3. Valider un échange : l'écran montre l'admissibilité et la contiguïté des deux quarts, et le motif d'un rejet.
- **Résultat attendu** :
  - les commandes `pla-ech`, `pla-ech-filtre`, `pla-ech-table` sont présentes et utilisables au clavier ;
  - le geste décrit à la dernière étape produit l'effet annoncé, sans erreur affichée ;
  - l'écran, dans l'état atteint, ne présente aucune violation axe-core (WCAG 2.2 AA).
- **Essais automatisés qui le couvrent déjà** :
  - Écran et commandes : `e2e/tests/e2e/urgences-sante-exigences.spec.ts` (avec audit axe-core)
  - Comportement (Java) : `EchangeEffectifsDomaineTest`, `ControleurEchangesEffectifsHttpTest`
  - Bout en bout : `effectifs-echanges.spec.ts`
- **Résultat obtenu** : ☐ Conforme ☐ Non conforme — anomalie n° ______ — gravité ____ — initiales ____

### CA-E-GR-8 — Gestion régulière des horaires (essentiel)

> La solution doit permettre la définition de quotas de comblement distincts par période, plage horaire ou type de quart.

- **Verdict à la matrice** : ✅ Acquis
- **Écran** : Urgences-santé › Gestion des quarts › Quotas — `/usquotas`
- **Profil** : `claire.nadeau` (Planificatrice des effectifs)
- **Étapes** :
  1. Se connecter à l'organisme d'essai avec `claire.nadeau` (Planificatrice des effectifs).
  2. Ouvrir **Urgences-santé › Gestion des quarts › Quotas** (adresse `/usquotas`).
  3. Activer la commande `pla-quota-definir` (le quota se définit dans un tiroir).
  4. « Définir un quota » : clé (période, plage ou type de quart) et valeur, distincts les uns des autres.
- **Résultat attendu** :
  - les commandes `pla-quotas`, `pla-quota-tiroir`, `pla-quo-quota`, `pla-quo-depuis` sont présentes et utilisables au clavier ;
  - le geste décrit à la dernière étape produit l'effet annoncé, sans erreur affichée ;
  - l'écran, dans l'état atteint, ne présente aucune violation axe-core (WCAG 2.2 AA).
- **Essais automatisés qui le couvrent déjà** :
  - Écran et commandes : `e2e/tests/e2e/urgences-sante-exigences.spec.ts` (avec audit axe-core)
  - Comportement (Java) : `PlanificationDomaineTest`
- **Résultat obtenu** : ☐ Conforme ☐ Non conforme — anomalie n° ______ — gravité ____ — initiales ____

### CA-E-GR-9 — Gestion régulière des horaires (essentiel)

> La solution doit permettre l’ajustement temporaire de paramètres opérationnels incluant les quotas en cours de période sans effet rétroactif.

- **Verdict à la matrice** : ✅ Acquis
- **Écran** : Urgences-santé › Gestion des quarts › Quotas — `/usquotas`
- **Profil** : `claire.nadeau` (Planificatrice des effectifs)
- **Étapes** :
  1. Se connecter à l'organisme d'essai avec `claire.nadeau` (Planificatrice des effectifs).
  2. Ouvrir **Urgences-santé › Gestion des quarts › Quotas** (adresse `/usquotas`).
  3. Surcharger un quota en cours de période : la surcharge est datée de « à partir de », donc sans effet rétroactif.
- **Résultat attendu** :
  - les commandes `pla-quotas`, `pla-quotas-tableau`, `pla-quota-definir` sont présentes et utilisables au clavier ;
  - le geste décrit à la dernière étape produit l'effet annoncé, sans erreur affichée ;
  - l'écran, dans l'état atteint, ne présente aucune violation axe-core (WCAG 2.2 AA).
- **Essais automatisés qui le couvrent déjà** :
  - Écran et commandes : `e2e/tests/e2e/urgences-sante-exigences.spec.ts` (avec audit axe-core)
  - Comportement (Java) : `PlanificationDomaineTest`, `SocleEffectifsDomaineTest`
  - Bout en bout : `effectifs-socle.spec.ts`
- **Résultat obtenu** : ☐ Conforme ☐ Non conforme — anomalie n° ______ — gravité ____ — initiales ____

### CA-E-GR-10 — Gestion régulière des horaires (essentiel)

> La solution doit permettre la gestion des horaires par périodes de planification incluant l’ouverture et la fermeture contrôlées.

- **Verdict à la matrice** : ✅ Acquis
- **Écran** : Urgences-santé › Gestion des quarts › Périodes — `/usperiodes`
- **Profil** : `claire.nadeau` (Planificatrice des effectifs)
- **Étapes** :
  1. Se connecter à l'organisme d'essai avec `claire.nadeau` (Planificatrice des effectifs).
  2. Ouvrir **Urgences-santé › Gestion des quarts › Périodes** (adresse `/usperiodes`).
  3. Activer la commande `pla-periode-nouvelle` (la période se crée dans un tiroir).
  4. Créer une période, ouvrir sa collecte avec sa date de fin, puis la clore : l'ouverture et la fermeture sont des gestes distincts et tracés.
- **Résultat attendu** :
  - les commandes `pla-periodes`, `pla-periode-tiroir`, `pla-per-debut`, `pla-per-fin` sont présentes et utilisables au clavier ;
  - le geste décrit à la dernière étape produit l'effet annoncé, sans erreur affichée ;
  - l'écran, dans l'état atteint, ne présente aucune violation axe-core (WCAG 2.2 AA).
  - comportement C-04 : Hors collecte, le dépôt de disponibilités est refusé en disant pourquoi ; en collecte, un dépôt trop maigre est bloqué avec son motif, un dépôt suffisant passe avec un avertissement.
- **Essais automatisés qui le couvrent déjà** :
  - Écran et commandes : `e2e/tests/e2e/urgences-sante-exigences.spec.ts` (avec audit axe-core)
  - Comportement (Java) : `PlanificationDomaineTest`
  - Scénarios d'API (`e2e/tests/e2e/urgences-sante-comportements.spec.ts`) : C-04
- **Résultat obtenu** : ☐ Conforme ☐ Non conforme — anomalie n° ______ — gravité ____ — initiales ____

### CA-E-GR-11 — Gestion régulière des horaires (essentiel)

> La solution doit permettre le dépôt et l’ajustement des disponibilités par période ouverte.

- **Verdict à la matrice** : ✅ Acquis
- **Écran** : Urgences-santé › Disponibilités › Mes disponibilités — `/mesdisponibilites`
- **Profil** : `julie.roy` (Paramédic — temps complet)
- **Étapes** :
  1. Se connecter à l'organisme d'essai avec `julie.roy` (Paramédic — temps complet).
  2. Ouvrir **Urgences-santé › Disponibilités › Mes disponibilités** (adresse `/mesdisponibilites`).
  3. Tant que la collecte de la période est ouverte, l'employé dépose et ajuste ses disponibilités ; fermée, l'écran passe en lecture seule.
- **Résultat attendu** :
  - les commandes `mdi-titre`, `mdi-periode`, `mdi-etat`, `mdi-deposer` sont présentes et utilisables au clavier ;
  - le geste décrit à la dernière étape produit l'effet annoncé, sans erreur affichée ;
  - l'écran, dans l'état atteint, ne présente aucune violation axe-core (WCAG 2.2 AA).
  - comportement C-04 : Hors collecte, le dépôt de disponibilités est refusé en disant pourquoi ; en collecte, un dépôt trop maigre est bloqué avec son motif, un dépôt suffisant passe avec un avertissement.
- **Essais automatisés qui le couvrent déjà** :
  - Écran et commandes : `e2e/tests/e2e/urgences-sante-exigences.spec.ts` (avec audit axe-core)
  - Comportement (Java) : `ControleurDisponibilitesEffectifsHttpTest`
  - Scénarios d'API (`e2e/tests/e2e/urgences-sante-comportements.spec.ts`) : C-04
- **Résultat obtenu** : ☐ Conforme ☐ Non conforme — anomalie n° ______ — gravité ____ — initiales ____

### CA-E-GR-12 — Gestion régulière des horaires (essentiel)

> La solution doit permettre de configurer si les échanges de quarts entre différentes périodes de planification sont autorisés, restreints ou interdits.

- **Verdict à la matrice** : ✅ Acquis
- **Écran** : Urgences-santé › Demandes et échanges › Demandes et échanges — `/usechanges`
- **Profil** : `claire.nadeau` (Planificatrice des effectifs)
- **Étapes** :
  1. Se connecter à l'organisme d'essai avec `claire.nadeau` (Planificatrice des effectifs).
  2. Ouvrir **Urgences-santé › Demandes et échanges › Demandes et échanges** (adresse `/usechanges`).
  3. Un échange entre deux périodes de planification est accepté, restreint ou refusé selon la règle en vigueur, qui est nommée dans le refus.
- **Résultat attendu** :
  - les commandes `pla-ech`, `pla-ech-table` sont présentes et utilisables au clavier ;
  - le geste décrit à la dernière étape produit l'effet annoncé, sans erreur affichée ;
  - l'écran, dans l'état atteint, ne présente aucune violation axe-core (WCAG 2.2 AA).
- **Essais automatisés qui le couvrent déjà** :
  - Écran et commandes : `e2e/tests/e2e/urgences-sante-exigences.spec.ts` (avec audit axe-core)
  - Comportement (Java) : `ControleurEchangesEffectifsHttpTest`
  - Bout en bout : `effectifs-echanges.spec.ts`
- **Résultat obtenu** : ☐ Conforme ☐ Non conforme — anomalie n° ______ — gravité ____ — initiales ____

### CA-E-GR-13 — Gestion régulière des horaires (essentiel)

> La solution doit permettre de configurer des quarts de travail non lié à des postes (Quarts ad hoc).

- **Verdict à la matrice** : ✅ Acquis
- **Écran** : Urgences-santé › Gestion des quarts › Quarts et équipages — `/usequipages`
- **Profil** : `claire.nadeau` (Planificatrice des effectifs)
- **Étapes** :
  1. Se connecter à l'organisme d'essai avec `claire.nadeau` (Planificatrice des effectifs).
  2. Ouvrir **Urgences-santé › Gestion des quarts › Quarts et équipages** (adresse `/usequipages`).
  3. Activer la commande `pla-eq-adhoc` (l'absence de poste se constate sur le formulaire de création).
  4. Le quart ad hoc n'exige aucun poste : sa nature et sa plage suffisent.
- **Résultat attendu** :
  - les commandes `pla-adhoc`, `pla-ah-nature`, `pla-ah-debut`, `pla-ah-fin` sont présentes et utilisables au clavier ;
  - le geste décrit à la dernière étape produit l'effet annoncé, sans erreur affichée ;
  - l'écran, dans l'état atteint, ne présente aucune violation axe-core (WCAG 2.2 AA).
  - comportement C-01 : Un quart ad hoc se crée hors de tout poste ; hors période, sans composition, à minimum impossible ou sur une plage inconnue, il est refusé.
- **Essais automatisés qui le couvrent déjà** :
  - Écran et commandes : `e2e/tests/e2e/urgences-sante-exigences.spec.ts` (avec audit axe-core)
  - Comportement (Java) : `ControleurPlanificationEffectifsHttpTest`
  - Scénarios d'API (`e2e/tests/e2e/urgences-sante-comportements.spec.ts`) : C-01
- **Résultat obtenu** : ☐ Conforme ☐ Non conforme — anomalie n° ______ — gravité ____ — initiales ____

### CA-E-GP-1 — Gestion du "punch" (essentiel)

> La solution doit permettre aux employés la déclaration des heures de travail en temps réel selon des profils configurables

- **Verdict à la matrice** : ✅ Acquis
- **Écran** : Urgences-santé › Pointage et présence › Mon pointage — `/monpointage`
- **Profil** : `julie.roy` (Paramédic — temps complet)
- **Étapes** :
  1. Se connecter à l'organisme d'essai avec `julie.roy` (Paramédic — temps complet).
  2. Ouvrir **Urgences-santé › Pointage et présence › Mon pointage** (adresse `/monpointage`).
  3. « Déclarer » : l'employé pointe son entrée et sa sortie en temps réel, selon le profil de pointage qui lui est posé.
- **Résultat attendu** :
  - les commandes `mpo-titre`, `mpo-declarer-ouvrir`, `mpo-table` sont présentes et utilisables au clavier ;
  - le geste décrit à la dernière étape produit l'effet annoncé, sans erreur affichée ;
  - l'écran, dans l'état atteint, ne présente aucune violation axe-core (WCAG 2.2 AA).
- **Essais automatisés qui le couvrent déjà** :
  - Écran et commandes : `e2e/tests/e2e/urgences-sante-exigences.spec.ts` (avec audit axe-core)
  - Comportement (Java) : `ProfilsPointageTest`, `ControleurPresenceEffectifsHttpTest`
  - Bout en bout : `effectifs-presence.spec.ts`
- **Résultat obtenu** : ☐ Conforme ☐ Non conforme — anomalie n° ______ — gravité ____ — initiales ____

### CA-E-GP-2 — Gestion du "punch" (essentiel)

> La solution doit permettre la saisie manuelle des pauses, repas et périodes de repos non payé selon des profils configurables.

- **Verdict à la matrice** : ✅ Acquis
- **Écran** : Urgences-santé › Pointage et présence › Pointage et présence (console) — `/uspresence`
- **Profil** : `claire.nadeau` (Planificatrice des effectifs)
- **Étapes** :
  1. Se connecter à l'organisme d'essai avec `claire.nadeau` (Planificatrice des effectifs).
  2. Ouvrir **Urgences-santé › Pointage et présence › Pointage et présence (console)** (adresse `/uspresence`).
  3. Profils de pointage : le profil dit quelles natures de temps non payé (pause, repas, repos) sont admises à la saisie.
- **Résultat attendu** :
  - les commandes `pla-pre`, `pfl-panneau`, `pfl-pauses` sont présentes et utilisables au clavier ;
  - le geste décrit à la dernière étape produit l'effet annoncé, sans erreur affichée ;
  - l'écran, dans l'état atteint, ne présente aucune violation axe-core (WCAG 2.2 AA).
- **Essais automatisés qui le couvrent déjà** :
  - Écran et commandes : `e2e/tests/e2e/urgences-sante-exigences.spec.ts` (avec audit axe-core)
  - Comportement (Java) : `PresenceQualificationDomaineTest`, `ProfilsPointageTest`, `ControleurPresenceEffectifsHttpTest`
- **Résultat obtenu** : ☐ Conforme ☐ Non conforme — anomalie n° ______ — gravité ____ — initiales ____

### CA-E-GP-3 — Gestion du "punch" (essentiel)

> La solution doit restreindre la modification des heures selon des règles configurables.

- **Verdict à la matrice** : ✅ Acquis
- **Écran** : Urgences-santé › Feuilles de temps › Feuilles de temps (console) — `/ustemps`
- **Profil** : `claire.nadeau` (Planificatrice des effectifs)
- **Étapes** :
  1. Se connecter à l'organisme d'essai avec `claire.nadeau` (Planificatrice des effectifs).
  2. Ouvrir **Urgences-santé › Feuilles de temps › Feuilles de temps (console)** (adresse `/ustemps`).
  3. Une feuille soumise ou approuvée n'est plus modifiable par l'employé : la règle de modification restreint selon le statut et le délai.
- **Résultat attendu** :
  - les commandes `pla-tps`, `pla-tps-filtre` sont présentes et utilisables au clavier ;
  - le geste décrit à la dernière étape produit l'effet annoncé, sans erreur affichée ;
  - l'écran, dans l'état atteint, ne présente aucune violation axe-core (WCAG 2.2 AA).
- **Essais automatisés qui le couvrent déjà** :
  - Écran et commandes : `e2e/tests/e2e/urgences-sante-exigences.spec.ts` (avec audit axe-core)
  - Comportement (Java) : `FluxApprobationDomaineTest`, `ControleurTempsEffectifsHttpTest`, `ControleurTempsFluxEffectifsHttpTest`, `FluxEssai`
  - Bout en bout : `effectifs-temps-flux.spec.ts`
- **Résultat obtenu** : ☐ Conforme ☐ Non conforme — anomalie n° ______ — gravité ____ — initiales ____

### CA-E-GP-4 — Gestion du "punch" (essentiel)

> La solution doit valider les heures saisies selon les règles d’affaires applicables au quart de travail

- **Verdict à la matrice** : ✅ Acquis
- **Écran** : Urgences-santé › Pointage et présence › Pointage et présence (console) — `/uspresence`
- **Profil** : `claire.nadeau` (Planificatrice des effectifs)
- **Étapes** :
  1. Se connecter à l'organisme d'essai avec `claire.nadeau` (Planificatrice des effectifs).
  2. Ouvrir **Urgences-santé › Pointage et présence › Pointage et présence (console)** (adresse `/uspresence`).
  3. La fiche d'un pointage montre les motifs retenus par les règles du quart, et l'écart au planifié.
- **Résultat attendu** :
  - les commandes `pla-pre`, `pla-pre-filtre`, `pla-pre-table` sont présentes et utilisables au clavier ;
  - le geste décrit à la dernière étape produit l'effet annoncé, sans erreur affichée ;
  - l'écran, dans l'état atteint, ne présente aucune violation axe-core (WCAG 2.2 AA).
- **Essais automatisés qui le couvrent déjà** :
  - Écran et commandes : `e2e/tests/e2e/urgences-sante-exigences.spec.ts` (avec audit axe-core)
  - Comportement (Java) : `ControleurPresenceEffectifsHttpTest`
- **Résultat obtenu** : ☐ Conforme ☐ Non conforme — anomalie n° ______ — gravité ____ — initiales ____

### CA-E-GP-5 — Gestion du "punch" (essentiel)

> La solution doit aviser l'utilisateur de la présence d'une anomalie lors de la saisie en cas de non respect d'un ou pluseiurs paramètres identifiés dans les règles d'affaire.

- **Verdict à la matrice** : ✅ Acquis
- **Écran** : Urgences-santé › Feuilles de temps › Ma feuille de temps — `/matemps`
- **Profil** : `julie.roy` (Paramédic — temps complet)
- **Étapes** :
  1. Se connecter à l'organisme d'essai avec `julie.roy` (Paramédic — temps complet).
  2. Ouvrir **Urgences-santé › Feuilles de temps › Ma feuille de temps** (adresse `/matemps`).
  3. Une anomalie est nommée sur la feuille au moment de la saisie, avec ce qu'il faut corriger ; une anomalie bloquante empêche la soumission.
- **Résultat attendu** :
  - les commandes `mft-titre`, `mft-preparation-ouvrir`, `mft-anomalies` ou `mft-sans-anomalie` sont présentes et utilisables au clavier ;
  - le geste décrit à la dernière étape produit l'effet annoncé, sans erreur affichée ;
  - l'écran, dans l'état atteint, ne présente aucune violation axe-core (WCAG 2.2 AA).
- **Essais automatisés qui le couvrent déjà** :
  - Écran et commandes : `e2e/tests/e2e/urgences-sante-exigences.spec.ts` (avec audit axe-core)
  - Comportement (Java) : `ControleurTempsEffectifsHttpTest`
- **Résultat obtenu** : ☐ Conforme ☐ Non conforme — anomalie n° ______ — gravité ____ — initiales ____

### CA-E-GP-6 — Gestion du "punch" (essentiel)

> La solution doit permettre l’approbation des heures saisies selon des flux configurables.

- **Verdict à la matrice** : ✅ Acquis
- **Écran** : Urgences-santé › Feuilles de temps › Feuilles de temps (console) — `/ustemps`
- **Profil** : `claire.nadeau` (Planificatrice des effectifs)
- **Étapes** :
  1. Se connecter à l'organisme d'essai avec `claire.nadeau` (Planificatrice des effectifs).
  2. Ouvrir **Urgences-santé › Feuilles de temps › Feuilles de temps (console)** (adresse `/ustemps`).
  3. Activer la commande `feuilles-approbations-ouvrir` (le formulaire vit dans un dialogue depuis le 8 novembre 2026 : ses champs n'existent qu'ouvert).
  4. « Paramètres des feuilles » → flux d'approbation : paliers, approbateur et condition de routage se configurent.
- **Résultat attendu** :
  - les commandes `feuilles-approbations-ouvrir`, `pla-tps-flux`, `pla-tps-flux-palier`, `pla-tps-flux-approbateur`, `pla-tps-flux-champ`, `pla-tps-flux-operateur` sont présentes et utilisables au clavier ;
  - le geste décrit à la dernière étape produit l'effet annoncé, sans erreur affichée ;
  - l'écran, dans l'état atteint, ne présente aucune violation axe-core (WCAG 2.2 AA).
- **Essais automatisés qui le couvrent déjà** :
  - Écran et commandes : `e2e/tests/e2e/urgences-sante-exigences.spec.ts` (avec audit axe-core)
  - Comportement (Java) : `FluxApprobationDomaineTest`, `ControleurPresenceEffectifsHttpTest`, `ControleurTempsEffectifsHttpTest`, `ControleurTempsFluxEffectifsHttpTest`, `FluxEssai`
  - Bout en bout : `effectifs-temps-flux.spec.ts`, `audit-wcag22.spec.ts`
- **Résultat obtenu** : ☐ Conforme ☐ Non conforme — anomalie n° ______ — gravité ____ — initiales ____

### CA-E-GP-7 — Gestion du "punch" (essentiel)

> La solution doit permettre la gestion multi-site de la déclaration de présence.

- **Verdict à la matrice** : ✅ Acquis
- **Écran** : Urgences-santé › Pointage et présence › Pointage et présence (console) — `/uspresence`
- **Profil** : `claire.nadeau` (Planificatrice des effectifs)
- **Étapes** :
  1. Se connecter à l'organisme d'essai avec `claire.nadeau` (Planificatrice des effectifs).
  2. Ouvrir **Urgences-santé › Pointage et présence › Pointage et présence (console)** (adresse `/uspresence`).
  3. Filtrer les pointages par milieu : la déclaration de présence est rattachée au site, et plusieurs sites coexistent.
- **Résultat attendu** :
  - les commandes `pla-pre`, `pla-pre-milieu`, `pla-pre-site` sont présentes et utilisables au clavier ;
  - le geste décrit à la dernière étape produit l'effet annoncé, sans erreur affichée ;
  - l'écran, dans l'état atteint, ne présente aucune violation axe-core (WCAG 2.2 AA).
- **Essais automatisés qui le couvrent déjà** :
  - Écran et commandes : `e2e/tests/e2e/urgences-sante-exigences.spec.ts` (avec audit axe-core)
  - Comportement (Java) : `PresenceQualificationDomaineTest`, `RepartitionPresenceTest`, `ControleurPresenceEffectifsHttpTest`
  - Bout en bout : `audit-wcag22.spec.ts`
- **Résultat obtenu** : ☐ Conforme ☐ Non conforme — anomalie n° ______ — gravité ____ — initiales ____

### CA-E-GP-8 — Gestion du "punch" (essentiel)

> La solution doit permettre la comparaison entre heures planifiées et heures déclarées.

- **Verdict à la matrice** : ✅ Acquis
- **Écran** : Urgences-santé › Feuilles de temps › Ma feuille de temps — `/matemps`
- **Profil** : `julie.roy` (Paramédic — temps complet)
- **Étapes** :
  1. Se connecter à l'organisme d'essai avec `julie.roy` (Paramédic — temps complet).
  2. Ouvrir **Urgences-santé › Feuilles de temps › Ma feuille de temps** (adresse `/matemps`).
  3. Sur la feuille : le tableau « Planifié et déclaré, quart par quart » et son écart.
- **Résultat attendu** :
  - les commandes `mft-titre`, `mft-feuille-detail`, `mft-totaux` sont présentes et utilisables au clavier ;
  - le geste décrit à la dernière étape produit l'effet annoncé, sans erreur affichée ;
  - l'écran, dans l'état atteint, ne présente aucune violation axe-core (WCAG 2.2 AA).
- **Essais automatisés qui le couvrent déjà** :
  - Écran et commandes : `e2e/tests/e2e/urgences-sante-exigences.spec.ts` (avec audit axe-core)
  - Comportement (Java) : `ControleurTempsEffectifsHttpTest`
- **Résultat obtenu** : ☐ Conforme ☐ Non conforme — anomalie n° ______ — gravité ____ — initiales ____

### CA-E-GP-9 — Gestion du "punch" (essentiel)

> La solution doit permettre de définir des exigences de présence préalable rémunérée avant un quart de travail

- **Verdict à la matrice** : ✅ Acquis
- **Écran** : Urgences-santé › Pointage et présence › Pointage et présence (console) — `/uspresence`
- **Profil** : `claire.nadeau` (Planificatrice des effectifs)
- **Étapes** :
  1. Se connecter à l'organisme d'essai avec `claire.nadeau` (Planificatrice des effectifs).
  2. Ouvrir **Urgences-santé › Pointage et présence › Pointage et présence (console)** (adresse `/uspresence`).
  3. Profil de pointage : « Présence préalable rémunérée (minutes) » — le temps exigé avant le quart, et payé.
- **Résultat attendu** :
  - les commandes `pla-pre`, `pfl-panneau`, `pfl-prealable` sont présentes et utilisables au clavier ;
  - le geste décrit à la dernière étape produit l'effet annoncé, sans erreur affichée ;
  - l'écran, dans l'état atteint, ne présente aucune violation axe-core (WCAG 2.2 AA).
- **Essais automatisés qui le couvrent déjà** :
  - Écran et commandes : `e2e/tests/e2e/urgences-sante-exigences.spec.ts` (avec audit axe-core)
  - Comportement (Java) : `ControleurPresenceEffectifsHttpTest`
- **Résultat obtenu** : ☐ Conforme ☐ Non conforme — anomalie n° ______ — gravité ____ — initiales ____

### CA-E-GP-10 — Gestion du "punch" (essentiel)

> La solution doit permettre de valider que la déclaration de présence est effectuée à partir d’un emplacement conforme au milieu de travail associé au quart.

- **Verdict à la matrice** : ✅ Acquis
- **Écran** : Urgences-santé › Administration des effectifs › Milieux physiques — `/usmilieux`
- **Profil** : `admin` (administration de l'organisme)
- **Étapes** :
  1. Se connecter à l'organisme d'essai avec `admin` (administration de l'organisme).
  2. Ouvrir **Urgences-santé › Administration des effectifs › Milieux physiques** (adresse `/usmilieux`).
  3. Activer la commande `mil-ajouter` (le rayon est un champ de la fiche du milieu).
  4. Coordonnées et rayon du milieu : un pointage hors du rayon est un écart d'emplacement.
- **Résultat attendu** :
  - les commandes `mil-fiche`, `mil-form-latitude`, `mil-form-longitude`, `mil-form-rayon` sont présentes et utilisables au clavier ;
  - le geste décrit à la dernière étape produit l'effet annoncé, sans erreur affichée ;
  - l'écran, dans l'état atteint, ne présente aucune violation axe-core (WCAG 2.2 AA).
- **Essais automatisés qui le couvrent déjà** :
  - Écran et commandes : `e2e/tests/e2e/urgences-sante-exigences.spec.ts` (avec audit axe-core)
  - Comportement (Java) : `ControleurPresenceEffectifsHttpTest`
- **Résultat obtenu** : ☐ Conforme ☐ Non conforme — anomalie n° ______ — gravité ____ — initiales ____

### CA-E-GP-11 — Gestion du "punch" (essentiel)

> La solution doit supporter un ou plusieurs mécanismes de validation de la présence physique à un milieu de travail identifié au quart de travail.

- **Verdict à la matrice** : ✅ Acquis
- **Écran** : Urgences-santé › Administration des effectifs › Milieux physiques — `/usmilieux`
- **Profil** : `admin` (administration de l'organisme)
- **Étapes** :
  1. Se connecter à l'organisme d'essai avec `admin` (administration de l'organisme).
  2. Ouvrir **Urgences-santé › Administration des effectifs › Milieux physiques** (adresse `/usmilieux`).
  3. Activer la commande `mil-preuves-C12` (les preuves acceptées sont un tiroir ouvert depuis la ligne du milieu).
  4. « Preuves » d'un milieu : code sur place, code QR, position ou plage réseau — un ou plusieurs mécanismes, par plage horaire.
- **Résultat attendu** :
  - les commandes `mil-ecran`, `mil-preuves`, `mil-preuves-enregistrer` sont présentes et utilisables au clavier ;
  - le geste décrit à la dernière étape produit l'effet annoncé, sans erreur affichée ;
  - l'écran, dans l'état atteint, ne présente aucune violation axe-core (WCAG 2.2 AA).
- **Essais automatisés qui le couvrent déjà** :
  - Écran et commandes : `e2e/tests/e2e/urgences-sante-exigences.spec.ts` (avec audit axe-core)
  - Comportement (Java) : `SocleEffectifsDomaineTest`, `ControleurPresenceEffectifsHttpTest`
  - Bout en bout : `effectifs-presence.spec.ts`
- **Résultat obtenu** : ☐ Conforme ☐ Non conforme — anomalie n° ______ — gravité ____ — initiales ____

### CA-E-GP-12 — Gestion du "punch" (essentiel)

> La solution doit permettre de configurer les exigences de validation de présence.

- **Verdict à la matrice** : ✅ Acquis
- **Écran** : Urgences-santé › Administration des effectifs › Milieux physiques — `/usmilieux`
- **Profil** : `admin` (administration de l'organisme)
- **Étapes** :
  1. Se connecter à l'organisme d'essai avec `admin` (administration de l'organisme).
  2. Ouvrir **Urgences-santé › Administration des effectifs › Milieux physiques** (adresse `/usmilieux`).
  3. Activer la commande `mil-preuves-C12` (le réglage vit dans le tiroir des preuves du milieu).
  4. Les exigences de validation se règlent milieu par milieu et s'enregistrent — aucun code à toucher.
- **Résultat attendu** :
  - les commandes `mil-preuves`, `mil-preuves-enregistrer` sont présentes et utilisables au clavier ;
  - le geste décrit à la dernière étape produit l'effet annoncé, sans erreur affichée ;
  - l'écran, dans l'état atteint, ne présente aucune violation axe-core (WCAG 2.2 AA).
- **Essais automatisés qui le couvrent déjà** :
  - Écran et commandes : `e2e/tests/e2e/urgences-sante-exigences.spec.ts` (avec audit axe-core)
  - Comportement (Java) : `ControleurSocleEffectifsHttpTest`
- **Résultat obtenu** : ☐ Conforme ☐ Non conforme — anomalie n° ______ — gravité ____ — initiales ____

### CA-E-GP-13 — Gestion du "punch" (essentiel)

> La solution doit valider la cohérence entre le lieu de déclaration de présence et le milieu de travail associé au quart, et déclencher une action configurable en cas d’écart.

- **Verdict à la matrice** : ✅ Acquis
- **Écran** : Urgences-santé › Pointage et présence › Pointage et présence (console) — `/uspresence`
- **Profil** : `claire.nadeau` (Planificatrice des effectifs)
- **Étapes** :
  1. Se connecter à l'organisme d'essai avec `claire.nadeau` (Planificatrice des effectifs).
  2. Ouvrir **Urgences-santé › Pointage et présence › Pointage et présence (console)** (adresse `/uspresence`).
  3. Un écart entre le lieu déclaré et le milieu du quart déclenche l'action configurée : ignorer, signaler ou bloquer.
- **Résultat attendu** :
  - les commandes `pla-pre`, `pla-pre-table` sont présentes et utilisables au clavier ;
  - le geste décrit à la dernière étape produit l'effet annoncé, sans erreur affichée ;
  - l'écran, dans l'état atteint, ne présente aucune violation axe-core (WCAG 2.2 AA).
- **Essais automatisés qui le couvrent déjà** :
  - Écran et commandes : `e2e/tests/e2e/urgences-sante-exigences.spec.ts` (avec audit axe-core)
  - Comportement (Java) : `ControleurPresenceEffectifsHttpTest`
  - Bout en bout : `effectifs-presence.spec.ts`
- **Résultat obtenu** : ☐ Conforme ☐ Non conforme — anomalie n° ______ — gravité ____ — initiales ____

### CA-E-GP-14 — Gestion du "punch" (essentiel)

> La solution doit consigner les informations de localisation associées aux déclarations de présence et permettre leur audit

- **Verdict à la matrice** : ✅ Acquis
- **Écran** : Urgences-santé › Pointage et présence › Pointage et présence (console) — `/uspresence`
- **Profil** : `claire.nadeau` (Planificatrice des effectifs)
- **Étapes** :
  1. Se connecter à l'organisme d'essai avec `claire.nadeau` (Planificatrice des effectifs).
  2. Ouvrir **Urgences-santé › Pointage et présence › Pointage et présence (console)** (adresse `/uspresence`).
  3. « Audit des pointages » : la localisation consignée se lit à l'écran et s'exporte en CSV.
- **Résultat attendu** :
  - les commandes `pla-pre`, `pla-pre-audit`, `pla-pre-audit-charger` sont présentes et utilisables au clavier ;
  - le geste décrit à la dernière étape produit l'effet annoncé, sans erreur affichée ;
  - l'écran, dans l'état atteint, ne présente aucune violation axe-core (WCAG 2.2 AA).
- **Essais automatisés qui le couvrent déjà** :
  - Écran et commandes : `e2e/tests/e2e/urgences-sante-exigences.spec.ts` (avec audit axe-core)
  - Comportement (Java) : `ControleurPresenceEffectifsHttpTest`
- **Résultat obtenu** : ☐ Conforme ☐ Non conforme — anomalie n° ______ — gravité ____ — initiales ____

### CA-E-GP-15 — Gestion du "punch" (essentiel)

> La solution doit permettre a un utilisateur autorisé de saisir des heures et des activitée à postérior

- **Verdict à la matrice** : ✅ Acquis
- **Écran** : Urgences-santé › Pointage et présence › Mon pointage — `/monpointage`
- **Profil** : `julie.roy` (Paramédic — temps complet)
- **Étapes** :
  1. Se connecter à l'organisme d'essai avec `julie.roy` (Paramédic — temps complet).
  2. Ouvrir **Urgences-santé › Pointage et présence › Mon pointage** (adresse `/monpointage`).
  3. Activer la commande `mpo-declarer-ouvrir` (le formulaire vit dans un dialogue depuis le 8 novembre 2026 : ses champs n'existent qu'ouvert).
  4. Saisie a posteriori : date, entrée, sortie, activité et justification, sous le profil qui l'autorise.
- **Résultat attendu** :
  - les commandes `mpo-titre`, `mpo-s-date`, `mpo-s-entree`, `mpo-s-sortie`, `mpo-s-justification` sont présentes et utilisables au clavier ;
  - le geste décrit à la dernière étape produit l'effet annoncé, sans erreur affichée ;
  - l'écran, dans l'état atteint, ne présente aucune violation axe-core (WCAG 2.2 AA).
- **Essais automatisés qui le couvrent déjà** :
  - Écran et commandes : `e2e/tests/e2e/urgences-sante-exigences.spec.ts` (avec audit axe-core)
  - Comportement (Java) : `PresenceQualificationDomaineTest`, `RepartitionPresenceTest`, `ControleurPresenceEffectifsHttpTest`
  - Bout en bout : `audit-wcag22.spec.ts`
- **Résultat obtenu** : ☐ Conforme ☐ Non conforme — anomalie n° ______ — gravité ____ — initiales ____

### CA-E-RF-1 — Rapports et formulaires (essentiel)

> La solution doit préparer automatiquement la feuille de temps à partir des horaires et affectations.

- **Verdict à la matrice** : ✅ Acquis
- **Écran** : Urgences-santé › Feuilles de temps › Feuilles de temps (console) — `/ustemps`
- **Profil** : `claire.nadeau` (Planificatrice des effectifs)
- **Étapes** :
  1. Se connecter à l'organisme d'essai avec `claire.nadeau` (Planificatrice des effectifs).
  2. Ouvrir **Urgences-santé › Feuilles de temps › Feuilles de temps (console)** (adresse `/ustemps`).
  3. « Préparer les feuilles » : la feuille de la période est bâtie depuis les quarts attribués, sans saisie.
- **Résultat attendu** :
  - les commandes `pla-tps`, `pla-tps-debut`, `pla-tps-fin`, `pla-tps-preparer` sont présentes et utilisables au clavier ;
  - le geste décrit à la dernière étape produit l'effet annoncé, sans erreur affichée ;
  - l'écran, dans l'état atteint, ne présente aucune violation axe-core (WCAG 2.2 AA).
- **Essais automatisés qui le couvrent déjà** :
  - Écran et commandes : `e2e/tests/e2e/urgences-sante-exigences.spec.ts` (avec audit axe-core)
  - Comportement (Java) : `ControleurTempsEffectifsHttpTest`
  - Bout en bout : `effectifs-temps.spec.ts`
- **Résultat obtenu** : ☐ Conforme ☐ Non conforme — anomalie n° ______ — gravité ____ — initiales ____

### CA-E-RF-2 — Rapports et formulaires (essentiel)

> La solution doit permettre la saisie manuelle ou automatisée des données de temps dans la feuille de temps

- **Verdict à la matrice** : ✅ Acquis
- **Écran** : Urgences-santé › Feuilles de temps › Ma feuille de temps — `/matemps`
- **Profil** : `julie.roy` (Paramédic — temps complet)
- **Étapes** :
  1. Se connecter à l'organisme d'essai avec `julie.roy` (Paramédic — temps complet).
  2. Ouvrir **Urgences-santé › Feuilles de temps › Ma feuille de temps** (adresse `/matemps`).
  3. « Ajouter » un temps : l'employé saisit ce que le pointage n'a pas rempli ; le reste vient des quarts.
- **Résultat attendu** :
  - les commandes `mft-titre`, `mft-feuille-detail`, `mft-ajouter-ouvrir` sont présentes et utilisables au clavier ;
  - le geste décrit à la dernière étape produit l'effet annoncé, sans erreur affichée ;
  - l'écran, dans l'état atteint, ne présente aucune violation axe-core (WCAG 2.2 AA).
- **Essais automatisés qui le couvrent déjà** :
  - Écran et commandes : `e2e/tests/e2e/urgences-sante-exigences.spec.ts` (avec audit axe-core)
  - Comportement (Java) : `ControleurTempsEffectifsHttpTest`
  - Bout en bout : `effectifs-temps.spec.ts`
- **Résultat obtenu** : ☐ Conforme ☐ Non conforme — anomalie n° ______ — gravité ____ — initiales ____

### CA-E-RF-3 — Rapports et formulaires (essentiel)

> La solution doit permettre la validation en temps réel des données de feuille de temps.

- **Verdict à la matrice** : ✅ Acquis
- **Écran** : Urgences-santé › Feuilles de temps › Ma feuille de temps — `/matemps`
- **Profil** : `julie.roy` (Paramédic — temps complet)
- **Étapes** :
  1. Se connecter à l'organisme d'essai avec `julie.roy` (Paramédic — temps complet).
  2. Ouvrir **Urgences-santé › Feuilles de temps › Ma feuille de temps** (adresse `/matemps`).
  3. À l'enregistrement d'un segment, les anomalies et les totaux sont recalculés et réaffichés aussitôt.
- **Résultat attendu** :
  - les commandes `mft-titre`, `mft-feuille-detail`, `mft-segments` sont présentes et utilisables au clavier ;
  - le geste décrit à la dernière étape produit l'effet annoncé, sans erreur affichée ;
  - l'écran, dans l'état atteint, ne présente aucune violation axe-core (WCAG 2.2 AA).
- **Essais automatisés qui le couvrent déjà** :
  - Écran et commandes : `e2e/tests/e2e/urgences-sante-exigences.spec.ts` (avec audit axe-core)
  - Comportement (Java) : `ControleurTempsEffectifsHttpTest`
- **Résultat obtenu** : ☐ Conforme ☐ Non conforme — anomalie n° ______ — gravité ____ — initiales ____

### CA-E-RF-4 — Rapports et formulaires (essentiel)

> La solution doit permettre l’approbation des feuilles de temps selon des profils autorisés.

- **Verdict à la matrice** : ✅ Acquis
- **Écran** : Urgences-santé › Feuilles de temps › Feuilles de temps (console) — `/ustemps`
- **Profil** : `claire.nadeau` (Planificatrice des effectifs)
- **Étapes** :
  1. Se connecter à l'organisme d'essai avec `claire.nadeau` (Planificatrice des effectifs).
  2. Ouvrir **Urgences-santé › Feuilles de temps › Feuilles de temps (console)** (adresse `/ustemps`).
  3. Ouvrir une feuille soumise et « Approuver le palier » — ou la retourner avec un motif.
- **Résultat attendu** :
  - les commandes `pla-tps`, `pla-tps-table`, `pla-tps-filtre` sont présentes et utilisables au clavier ;
  - le geste décrit à la dernière étape produit l'effet annoncé, sans erreur affichée ;
  - l'écran, dans l'état atteint, ne présente aucune violation axe-core (WCAG 2.2 AA).
- **Essais automatisés qui le couvrent déjà** :
  - Écran et commandes : `e2e/tests/e2e/urgences-sante-exigences.spec.ts` (avec audit axe-core)
  - Comportement (Java) : `ControleurTempsEffectifsHttpTest`
  - Bout en bout : `effectifs-temps.spec.ts`, `urgences-sante.ts`
- **Résultat obtenu** : ☐ Conforme ☐ Non conforme — anomalie n° ______ — gravité ____ — initiales ____

### CA-E-RF-5 — Rapports et formulaires (essentiel)

> La solution doit permettre l’évaluation automatique des heures supplémentaires.

- **Verdict à la matrice** : ✅ Acquis
- **Écran** : Urgences-santé › Feuilles de temps › Feuilles de temps (console) — `/ustemps`
- **Profil** : `claire.nadeau` (Planificatrice des effectifs)
- **Étapes** :
  1. Se connecter à l'organisme d'essai avec `claire.nadeau` (Planificatrice des effectifs).
  2. Ouvrir **Urgences-santé › Feuilles de temps › Feuilles de temps (console)** (adresse `/ustemps`).
  3. Activer la commande `feuilles-parametres-ouvrir` (le formulaire vit dans un dialogue depuis le 8 novembre 2026 : ses champs n'existent qu'ouvert).
  4. Les seuils d'heures supplémentaires (jour, semaine) se règlent dans « Paramètres des feuilles » ; le total est reclassé d'office.
- **Résultat attendu** :
  - les commandes `feuilles-parametres-ouvrir`, `pla-tps-p-seuilJourHeures`, `pla-tps-p-seuilSemaineHeures` sont présentes et utilisables au clavier ;
  - le geste décrit à la dernière étape produit l'effet annoncé, sans erreur affichée ;
  - l'écran, dans l'état atteint, ne présente aucune violation axe-core (WCAG 2.2 AA).
- **Essais automatisés qui le couvrent déjà** :
  - Écran et commandes : `e2e/tests/e2e/urgences-sante-exigences.spec.ts` (avec audit axe-core)
  - Comportement (Java) : `ControleurTempsEffectifsHttpTest`
- **Résultat obtenu** : ☐ Conforme ☐ Non conforme — anomalie n° ______ — gravité ____ — initiales ____

### CA-E-RF-6 — Rapports et formulaires (essentiel)

> La solution doit permettre la distinction des types d’heures.

- **Verdict à la matrice** : ✅ Acquis
- **Écran** : Urgences-santé › Administration des effectifs › Référentiels — `/usreferentiels`
- **Profil** : `admin` (administration de l'organisme)
- **Étapes** :
  1. Se connecter à l'organisme d'essai avec `admin` (administration de l'organisme).
  2. Ouvrir **Urgences-santé › Administration des effectifs › Référentiels** (adresse `/usreferentiels`).
  3. Type « Types d'heures » : chaque type porte son comportement et son code de paie ; les totaux de la feuille sont ventilés par type.
- **Résultat attendu** :
  - les commandes `ref-ecran`, `ref-type`, `ref-tableau` sont présentes et utilisables au clavier ;
  - le geste décrit à la dernière étape produit l'effet annoncé, sans erreur affichée ;
  - l'écran, dans l'état atteint, ne présente aucune violation axe-core (WCAG 2.2 AA).
- **Essais automatisés qui le couvrent déjà** :
  - Écran et commandes : `e2e/tests/e2e/urgences-sante-exigences.spec.ts` (avec audit axe-core)
  - Comportement (Java) : `TempsEffectifsDomaineTest`
- **Résultat obtenu** : ☐ Conforme ☐ Non conforme — anomalie n° ______ — gravité ____ — initiales ____

### CA-E-RF-7 — Rapports et formulaires (essentiel)

> La solution doit permettre la production de rapports opérationnels.

- **Verdict à la matrice** : ✅ Acquis
- **Écran** : Urgences-santé › Feuilles de temps › Feuilles de temps (console) — `/ustemps`
- **Profil** : `claire.nadeau` (Planificatrice des effectifs)
- **Étapes** :
  1. Se connecter à l'organisme d'essai avec `claire.nadeau` (Planificatrice des effectifs).
  2. Ouvrir **Urgences-santé › Feuilles de temps › Feuilles de temps (console)** (adresse `/ustemps`).
  3. « Relevés » : choisir le regroupement et afficher le relevé opérationnel de la période.
- **Résultat attendu** :
  - les commandes `pla-tps-rapports`, `pla-tps-regroupement`, `pla-tps-lire-rapport` sont présentes et utilisables au clavier ;
  - le geste décrit à la dernière étape produit l'effet annoncé, sans erreur affichée ;
  - l'écran, dans l'état atteint, ne présente aucune violation axe-core (WCAG 2.2 AA).
- **Essais automatisés qui le couvrent déjà** :
  - Écran et commandes : `e2e/tests/e2e/urgences-sante-exigences.spec.ts` (avec audit axe-core)
  - Comportement (Java) : `ControleurTempsEffectifsHttpTest`
- **Résultat obtenu** : ☐ Conforme ☐ Non conforme — anomalie n° ______ — gravité ____ — initiales ____

### CA-E-RF-8 — Rapports et formulaires (essentiel)

> La solution doit permettre l’exportation des données dans des formats standards.

- **Verdict à la matrice** : ✅ Acquis
- **Écran** : Comptabilité › Constructeur de rapports — `/rapports`
- **Profil** : `admin` (administration de l'organisme)
- ⚠ **Écran masqué dans l'organisme semé** : sa section n'est pas gardée par `semer-urgences-sante.cjs` ; rétablir la vue avant d'exécuter le cas (§ 11).
- **Étapes** :
  1. Se connecter à l'organisme d'essai avec `admin` (administration de l'organisme).
  2. Ouvrir **Comptabilité › Constructeur de rapports** (adresse `/rapports`).
  3. Constructeur de rapports : choisir la source, les colonnes, le format (CSV, Excel, PDF, JSON) et exporter.
- **Résultat attendu** :
  - les commandes `rap-titre`, `rap-source`, `rap-format`, `rap-exporter` sont présentes et utilisables au clavier ;
  - le geste décrit à la dernière étape produit l'effet annoncé, sans erreur affichée ;
  - l'écran, dans l'état atteint, ne présente aucune violation axe-core (WCAG 2.2 AA).
  - comportement C-09 : Le journal s'exporte en CSV, en-tête fixe : chaque geste humain ou automatique y est retrouvable.
- **Essais automatisés qui le couvrent déjà** :
  - Écran et commandes : `e2e/tests/e2e/urgences-sante-exigences.spec.ts` (avec audit axe-core)
  - Comportement (Java) : `ControleurSocleEffectifsHttpTest`
  - Scénarios d'API (`e2e/tests/e2e/urgences-sante-comportements.spec.ts`) : C-09
- **Résultat obtenu** : ☐ Conforme ☐ Non conforme — anomalie n° ______ — gravité ____ — initiales ____

### CA-E-RF-9 — Rapports et formulaires (essentiel)

> La solution doit détecter automatiquement des anomalies de temps et de présence.

- **Verdict à la matrice** : ✅ Acquis
- **Écran** : Urgences-santé › Feuilles de temps › Feuilles de temps (console) — `/ustemps`
- **Profil** : `claire.nadeau` (Planificatrice des effectifs)
- **Étapes** :
  1. Se connecter à l'organisme d'essai avec `claire.nadeau` (Planificatrice des effectifs).
  2. Ouvrir **Urgences-santé › Feuilles de temps › Feuilles de temps (console)** (adresse `/ustemps`).
  3. Les anomalies de temps et de présence sont détectées par règle au contexte « Feuille de temps » ; la fiche de la feuille les liste.
- **Résultat attendu** :
  - les commandes `pla-tps`, `pla-tps-table` sont présentes et utilisables au clavier ;
  - le geste décrit à la dernière étape produit l'effet annoncé, sans erreur affichée ;
  - l'écran, dans l'état atteint, ne présente aucune violation axe-core (WCAG 2.2 AA).
- **Essais automatisés qui le couvrent déjà** :
  - Écran et commandes : `e2e/tests/e2e/urgences-sante-exigences.spec.ts` (avec audit axe-core)
  - Comportement (Java) : `ControleurTempsEffectifsHttpTest`
- **Résultat obtenu** : ☐ Conforme ☐ Non conforme — anomalie n° ______ — gravité ____ — initiales ____

### CA-E-RF-10 — Rapports et formulaires (essentiel)

> La solution doit permettre la configuration de règles de contrôle temporel.

- **Verdict à la matrice** : ✅ Acquis
- **Écran** : Urgences-santé › Administration des effectifs › Règles des effectifs — `/usregles`
- **Profil** : `admin` (administration de l'organisme)
- **Étapes** :
  1. Se connecter à l'organisme d'essai avec `admin` (administration de l'organisme).
  2. Ouvrir **Urgences-santé › Administration des effectifs › Règles des effectifs** (adresse `/usregles`).
  3. Contexte « Feuille de temps » : les règles de contrôle temporel se créent, se simulent et se publient datées.
- **Résultat attendu** :
  - les commandes `rgl-ecran`, `rgl-contexte`, `rgl-tableau` sont présentes et utilisables au clavier ;
  - le geste décrit à la dernière étape produit l'effet annoncé, sans erreur affichée ;
  - l'écran, dans l'état atteint, ne présente aucune violation axe-core (WCAG 2.2 AA).
- **Essais automatisés qui le couvrent déjà** :
  - Écran et commandes : `e2e/tests/e2e/urgences-sante-exigences.spec.ts` (avec audit axe-core)
  - Comportement (Java) : `FluxApprobationDomaineTest`
- **Résultat obtenu** : ☐ Conforme ☐ Non conforme — anomalie n° ______ — gravité ____ — initiales ____

### CA-E-RF-11 — Rapports et formulaires (essentiel)

> La solution doit afficher clairement les anomalies dans la feuille de temps.

- **Verdict à la matrice** : ✅ Acquis
- **Écran** : Urgences-santé › Feuilles de temps › Ma feuille de temps — `/matemps`
- **Profil** : `julie.roy` (Paramédic — temps complet)
- **Étapes** :
  1. Se connecter à l'organisme d'essai avec `julie.roy` (Paramédic — temps complet).
  2. Ouvrir **Urgences-santé › Feuilles de temps › Ma feuille de temps** (adresse `/matemps`).
  3. Les anomalies sont affichées en tête de feuille, nommées, et dites bloquantes en mots — jamais par la seule couleur.
- **Résultat attendu** :
  - les commandes `mft-titre`, `mft-feuille-detail`, `mft-anomalies` ou `mft-sans-anomalie` sont présentes et utilisables au clavier ;
  - le geste décrit à la dernière étape produit l'effet annoncé, sans erreur affichée ;
  - l'écran, dans l'état atteint, ne présente aucune violation axe-core (WCAG 2.2 AA).
- **Essais automatisés qui le couvrent déjà** :
  - Écran et commandes : `e2e/tests/e2e/urgences-sante-exigences.spec.ts` (avec audit axe-core)
  - Comportement (Java) : `ControleurTempsEffectifsHttpTest`
- **Résultat obtenu** : ☐ Conforme ☐ Non conforme — anomalie n° ______ — gravité ____ — initiales ____

### CA-E-RF-12 — Rapports et formulaires (essentiel)

> La solution doit structurer les données de temps afin de permettre leur traitement adéquat par les systèmes de paie

- **Verdict à la matrice** : ✅ Acquis
- **Écran** : Urgences-santé › Feuilles de temps › Feuilles de temps (console) — `/ustemps`
- **Profil** : `claire.nadeau` (Planificatrice des effectifs)
- **Étapes** :
  1. Se connecter à l'organisme d'essai avec `claire.nadeau` (Planificatrice des effectifs).
  2. Ouvrir **Urgences-santé › Feuilles de temps › Feuilles de temps (console)** (adresse `/ustemps`).
  3. « Lot de paie » : produire le lot des feuilles approuvées, codé par type d'heure, avec son empreinte SHA-256.
- **Résultat attendu** :
  - les commandes `pla-tps-paie`, `pla-tps-produire` sont présentes et utilisables au clavier ;
  - le geste décrit à la dernière étape produit l'effet annoncé, sans erreur affichée ;
  - l'écran, dans l'état atteint, ne présente aucune violation axe-core (WCAG 2.2 AA).
- **Essais automatisés qui le couvrent déjà** :
  - Écran et commandes : `e2e/tests/e2e/urgences-sante-exigences.spec.ts` (avec audit axe-core)
  - Comportement (Java) : `ControleurIntegrationsEffectifsHttpTest`, `ControleurTempsEffectifsHttpTest`
  - Bout en bout : `effectifs-temps.spec.ts`
- **Résultat obtenu** : ☐ Conforme ☐ Non conforme — anomalie n° ______ — gravité ____ — initiales ____

### CA-E-RF-13 — Rapports et formulaires (essentiel)

> La solution doit permettre de corréler des horodatages provenant de sources multiples (provenance de logiciels tiers) pour la validation de la présence et du temps.

- **Verdict à la matrice** : ✅ Acquis
- **Écran** : Urgences-santé › Feuilles de temps › Feuilles de temps (console) — `/ustemps`
- **Profil** : `claire.nadeau` (Planificatrice des effectifs)
- **Étapes** :
  1. Se connecter à l'organisme d'essai avec `claire.nadeau` (Planificatrice des effectifs).
  2. Ouvrir **Urgences-santé › Feuilles de temps › Feuilles de temps (console)** (adresse `/ustemps`).
  3. Activer la commande `feuilles-parametres-ouvrir` (le formulaire vit dans un dialogue depuis le 8 novembre 2026 : ses champs n'existent qu'ouvert).
  4. Paramètres : ce que la feuille fait d'un pointage qu'aucune source tierce ne confirme, et de deux sources en désaccord.
- **Résultat attendu** :
  - les commandes `pla-tps`, `pla-tps-p-actionNonCorrobore`, `pla-tps-p-actionSourcesDivergentes` sont présentes et utilisables au clavier ;
  - le geste décrit à la dernière étape produit l'effet annoncé, sans erreur affichée ;
  - l'écran, dans l'état atteint, ne présente aucune violation axe-core (WCAG 2.2 AA).
- **Essais automatisés qui le couvrent déjà** :
  - Écran et commandes : `e2e/tests/e2e/urgences-sante-exigences.spec.ts` (avec audit axe-core)
  - Comportement (Java) : `CorroborationTempsDomaineTest`, `TempsEffectifsDomaineTest`, `ControleurPresenceEffectifsHttpTest`, `ControleurTempsFluxEffectifsHttpTest`
  - Bout en bout : `effectifs-temps-flux.spec.ts`, `audit-wcag22.spec.ts`
- **Résultat obtenu** : ☐ Conforme ☐ Non conforme — anomalie n° ______ — gravité ____ — initiales ____

### CA-E-RF-14 — Rapports et formulaires (essentiel)

> La solution doit permettre d'effectuer des modifications aux entrées déjà effectuées selon les profils autorisés.

- **Verdict à la matrice** : ✅ Acquis
- **Écran** : Urgences-santé › Feuilles de temps › Ma feuille de temps — `/matemps`
- **Profil** : `julie.roy` (Paramédic — temps complet)
- **Étapes** :
  1. Se connecter à l'organisme d'essai avec `julie.roy` (Paramédic — temps complet).
  2. Ouvrir **Urgences-santé › Feuilles de temps › Ma feuille de temps** (adresse `/matemps`).
  3. Modifier une entrée déjà saisie : le motif est exigé et l'historique des versions du segment reste lisible.
- **Résultat attendu** :
  - les commandes `mft-titre`, `mft-feuille-detail`, `mft-segments` sont présentes et utilisables au clavier ;
  - le geste décrit à la dernière étape produit l'effet annoncé, sans erreur affichée ;
  - l'écran, dans l'état atteint, ne présente aucune violation axe-core (WCAG 2.2 AA).
- **Essais automatisés qui le couvrent déjà** :
  - Écran et commandes : `e2e/tests/e2e/urgences-sante-exigences.spec.ts` (avec audit axe-core)
  - Comportement (Java) : `ControleurTempsEffectifsHttpTest`
  - Bout en bout : `effectifs-temps.spec.ts`
- **Résultat obtenu** : ☐ Conforme ☐ Non conforme — anomalie n° ______ — gravité ____ — initiales ____

### CA-P-GN-1 — Généralités (préférentiel)

> La solution doit permettre l’attribution automatique d’un quart à la ressource prioritaire selon les règles applicables.

- **Verdict à la matrice** : ✅ Acquis
- **Écran** : Urgences-santé › Absences des effectifs › Absences et comblement — `/usabsences`
- **Profil** : `claire.nadeau` (Planificatrice des effectifs)
- **Étapes** :
  1. Se connecter à l'organisme d'essai avec `claire.nadeau` (Planificatrice des effectifs).
  2. Ouvrir **Urgences-santé › Absences des effectifs › Absences et comblement** (adresse `/usabsences`).
  3. Sur un quart au mode automatisé, le comblement attribue d'office à la ressource prioritaire que les règles acceptent.
- **Résultat attendu** :
  - les commandes `pla-abs`, `pla-abs-table` sont présentes et utilisables au clavier ;
  - le geste décrit à la dernière étape produit l'effet annoncé, sans erreur affichée ;
  - l'écran, dans l'état atteint, ne présente aucune violation axe-core (WCAG 2.2 AA).
- **Essais automatisés qui le couvrent déjà** :
  - Écran et commandes : `e2e/tests/e2e/urgences-sante-exigences.spec.ts` (avec audit axe-core)
  - Comportement (Java) : `ControleurAbsencesEffectifsHttpTest`
  - Bout en bout : `effectifs-absences.spec.ts`
- **Résultat obtenu** : ☐ Conforme ☐ Non conforme — anomalie n° ______ — gravité ____ — initiales ____

### CA-P-GN-2 — Généralités (préférentiel)

> La solution doit valider automatiquement l’admissibilité des absences et les restrictions applicables.

- **Verdict à la matrice** : ✅ Acquis
- **Écran** : Urgences-santé › Administration des effectifs › Règles des effectifs — `/usregles`
- **Profil** : `admin` (administration de l'organisme)
- **Étapes** :
  1. Se connecter à l'organisme d'essai avec `admin` (administration de l'organisme).
  2. Ouvrir **Urgences-santé › Administration des effectifs › Règles des effectifs** (adresse `/usregles`).
  3. Activer la commande `rgl-vue-simulateur` (le simulateur est une vue secondaire de l'écran des règles).
  4. Vue « Simulateur » : évaluer une situation d'absence et lire le verdict des règles avant d'agir.
- **Résultat attendu** :
  - les commandes `rgl-simulateur`, `rgl-sim-unite`, `rgl-sim-date`, `rgl-sim-evaluer` sont présentes et utilisables au clavier ;
  - le geste décrit à la dernière étape produit l'effet annoncé, sans erreur affichée ;
  - l'écran, dans l'état atteint, ne présente aucune violation axe-core (WCAG 2.2 AA).
  - comportement C-06 : Une absence qui ferait tomber le comblement est refusée d'office, et le refus est conservé avec son motif.
- **Essais automatisés qui le couvrent déjà** :
  - Écran et commandes : `e2e/tests/e2e/urgences-sante-exigences.spec.ts` (avec audit axe-core)
  - Comportement (Java) : `ControleurAbsencesEffectifsHttpTest`
  - Scénarios d'API (`e2e/tests/e2e/urgences-sante-comportements.spec.ts`) : C-06
- **Résultat obtenu** : ☐ Conforme ☐ Non conforme — anomalie n° ______ — gravité ____ — initiales ____

### CA-P-GN-3 — Généralités (préférentiel)

> La solution doit accepter automatiquement une absence lorsque l’ensemble des règles applicables sont respectées.

- **Verdict à la matrice** : ✅ Acquis
- **Écran** : Urgences-santé › Absences des effectifs › Mes absences d'effectifs — `/mesabsences`
- **Profil** : `julie.roy` (Paramédic — temps complet)
- **Étapes** :
  1. Se connecter à l'organisme d'essai avec `julie.roy` (Paramédic — temps complet).
  2. Ouvrir **Urgences-santé › Absences des effectifs › Mes absences d'effectifs** (adresse `/mesabsences`).
  3. Déclarer une absence qui respecte toutes les règles : elle est accordée sans décision humaine, et l'écran le dit.
- **Résultat attendu** :
  - les commandes `mab-titre`, `mab-declarer`, `mab-table` sont présentes et utilisables au clavier ;
  - le geste décrit à la dernière étape produit l'effet annoncé, sans erreur affichée ;
  - l'écran, dans l'état atteint, ne présente aucune violation axe-core (WCAG 2.2 AA).
  - comportement C-08 : Une absence conforme est acceptée d'office ; un code réservé à un titre d'emploi est refusé à qui ne le porte pas.
- **Essais automatisés qui le couvrent déjà** :
  - Écran et commandes : `e2e/tests/e2e/urgences-sante-exigences.spec.ts` (avec audit axe-core)
  - Comportement (Java) : `AbsencesEffectifsDomaineTest`
  - Scénarios d'API (`e2e/tests/e2e/urgences-sante-comportements.spec.ts`) : C-08
- **Résultat obtenu** : ☐ Conforme ☐ Non conforme — anomalie n° ______ — gravité ____ — initiales ____

### CA-P-GH-1 — Gestion des horaires (préférentiel)

> La solution doit permettre aux employés la déclaration d’absences ponctuelles ou planifiées paramétrable selon le titre d'emploi

- **Verdict à la matrice** : ✅ Acquis
- **Écran** : Urgences-santé › Absences des effectifs › Mes absences d'effectifs — `/mesabsences`
- **Profil** : `julie.roy` (Paramédic — temps complet)
- **Étapes** :
  1. Se connecter à l'organisme d'essai avec `julie.roy` (Paramédic — temps complet).
  2. Ouvrir **Urgences-santé › Absences des effectifs › Mes absences d'effectifs** (adresse `/mesabsences`).
  3. Un code « jour même seulement » n'est pas proposé à l'avance ; déclaré d'avance, le refus dit quel code choisir.
- **Résultat attendu** :
  - les commandes `mab-titre`, `mab-declarer` sont présentes et utilisables au clavier ;
  - le geste décrit à la dernière étape produit l'effet annoncé, sans erreur affichée ;
  - l'écran, dans l'état atteint, ne présente aucune violation axe-core (WCAG 2.2 AA).
  - comportement C-08 : Une absence conforme est acceptée d'office ; un code réservé à un titre d'emploi est refusé à qui ne le porte pas.
- **Essais automatisés qui le couvrent déjà** :
  - Écran et commandes : `e2e/tests/e2e/urgences-sante-exigences.spec.ts` (avec audit axe-core)
  - Comportement (Java) : `ControleurAbsencesEffectifsHttpTest`
  - Bout en bout : `effectifs-mes-absences.spec.ts`
  - Scénarios d'API (`e2e/tests/e2e/urgences-sante-comportements.spec.ts`) : C-08
- **Résultat obtenu** : ☐ Conforme ☐ Non conforme — anomalie n° ______ — gravité ____ — initiales ____

### CA-P-GH-2 — Gestion des horaires (préférentiel)

> La solution doit valider automatiquement la conformité des absences déclarées.

- **Verdict à la matrice** : ✅ Acquis
- **Écran** : Urgences-santé › Absences des effectifs › Mes absences d'effectifs — `/mesabsences`
- **Profil** : `julie.roy` (Paramédic — temps complet)
- **Étapes** :
  1. Se connecter à l'organisme d'essai avec `julie.roy` (Paramédic — temps complet).
  2. Ouvrir **Urgences-santé › Absences des effectifs › Mes absences d'effectifs** (adresse `/mesabsences`).
  3. La déclaration est jugée à la saisie : préavis, absences simultanées et taux de comblement sont évalués sans geste d'encadrement.
- **Résultat attendu** :
  - les commandes `mab-titre`, `mab-table` sont présentes et utilisables au clavier ;
  - le geste décrit à la dernière étape produit l'effet annoncé, sans erreur affichée ;
  - l'écran, dans l'état atteint, ne présente aucune violation axe-core (WCAG 2.2 AA).
  - comportement C-06 : Une absence qui ferait tomber le comblement est refusée d'office, et le refus est conservé avec son motif.
- **Essais automatisés qui le couvrent déjà** :
  - Écran et commandes : `e2e/tests/e2e/urgences-sante-exigences.spec.ts` (avec audit axe-core)
  - Comportement (Java) : `ControleurAbsencesEffectifsHttpTest`
  - Scénarios d'API (`e2e/tests/e2e/urgences-sante-comportements.spec.ts`) : C-06
- **Résultat obtenu** : ☐ Conforme ☐ Non conforme — anomalie n° ______ — gravité ____ — initiales ____

### CA-P-GH-3 — Gestion des horaires (préférentiel)

> La solution doit notifier les ressources admissibles à un quart disponible.

- **Verdict à la matrice** : ✅ Acquis
- **Écran** : Urgences-santé › Absences des effectifs › Absences et comblement — `/usabsences`
- **Profil** : `claire.nadeau` (Planificatrice des effectifs)
- **Étapes** :
  1. Se connecter à l'organisme d'essai avec `claire.nadeau` (Planificatrice des effectifs).
  2. Ouvrir **Urgences-santé › Absences des effectifs › Absences et comblement** (adresse `/usabsences`).
  3. Une place offerte au comblement avise les ressources admissibles ; l'écran dit à qui, par quel canal, et ce que le canal a rendu.
- **Résultat attendu** :
  - les commandes `pla-abs`, `pla-abs-table` sont présentes et utilisables au clavier ;
  - le geste décrit à la dernière étape produit l'effet annoncé, sans erreur affichée ;
  - l'écran, dans l'état atteint, ne présente aucune violation axe-core (WCAG 2.2 AA).
- **Essais automatisés qui le couvrent déjà** :
  - Écran et commandes : `e2e/tests/e2e/urgences-sante-exigences.spec.ts` (avec audit axe-core)
  - Comportement (Java) : `ControleurAbsencesEffectifsHttpTest`
  - Bout en bout : `effectifs-absences.spec.ts`
- **Résultat obtenu** : ☐ Conforme ☐ Non conforme — anomalie n° ______ — gravité ____ — initiales ____

### CA-P-GH-4 — Gestion des horaires (préférentiel)

> La solution doit attribuer automatiquement un quart de travail disponible à un employé ayant émis une disponibilité correspondante au quart de travail à la fin d'une période de planification et de récupéraiton des disponibilitées déterminée

- **Verdict à la matrice** : ✅ Acquis
- **Écran** : Urgences-santé › Disponibilités › Disponibilités de la période — `/usdisponibilites`
- **Profil** : `claire.nadeau` (Planificatrice des effectifs)
- **Étapes** :
  1. Se connecter à l'organisme d'essai avec `claire.nadeau` (Planificatrice des effectifs).
  2. Ouvrir **Urgences-santé › Disponibilités › Disponibilités de la période** (adresse `/usdisponibilites`).
  3. À la fin de la collecte, l'attribution depuis les disponibilités part d'elle-même ; avant la confection, l'écran dit qu'elle attend.
- **Résultat attendu** :
  - les commandes `pla-dispo`, `pla-dispo-depots` sont présentes et utilisables au clavier ;
  - le geste décrit à la dernière étape produit l'effet annoncé, sans erreur affichée ;
  - l'écran, dans l'état atteint, ne présente aucune violation axe-core (WCAG 2.2 AA).
- **Essais automatisés qui le couvrent déjà** :
  - Écran et commandes : `e2e/tests/e2e/urgences-sante-exigences.spec.ts` (avec audit axe-core)
  - Comportement (Java) : `DisponibilitesDomaineTest`, `ControleurAgphEffectifsHttpTest`, `ControleurDisponibilitesEffectifsHttpTest`
  - Bout en bout : `effectifs-agph.spec.ts`, `effectifs-disponibilites.spec.ts`, `audit-wcag22.spec.ts`
- **Résultat obtenu** : ☐ Conforme ☐ Non conforme — anomalie n° ______ — gravité ____ — initiales ____

### CA-P-CH-1 — Confection des horaires (préférentiel)

> La solution doit permettre à un employé admissible de visualiser les postes encore disponibles à son tour de rôle déterminé par les règles de convention collective.

- **Verdict à la matrice** : ✅ Acquis
- **Écran** : Urgences-santé › Confection AGPH › Mon AGPH — `/monagph`
- **Profil** : `julie.roy` (Paramédic — temps complet)
- **Étapes** :
  1. Se connecter à l'organisme d'essai avec `julie.roy` (Paramédic — temps complet).
  2. Ouvrir **Urgences-santé › Confection AGPH › Mon AGPH** (adresse `/monagph`).
  3. À son tour de rôle, l'employé admissible voit les postes encore libres de sa campagne.
- **Résultat attendu** :
  - les commandes `agm-titre`, une commande `agm-campagne-…` par objet listé sont présentes et utilisables au clavier ;
  - le geste décrit à la dernière étape produit l'effet annoncé, sans erreur affichée ;
  - l'écran, dans l'état atteint, ne présente aucune violation axe-core (WCAG 2.2 AA).
- **Essais automatisés qui le couvrent déjà** :
  - Écran et commandes : `e2e/tests/e2e/urgences-sante-exigences.spec.ts` (avec audit axe-core)
  - Comportement (Java) : `ControleurAgphEffectifsHttpTest`
  - Bout en bout : `effectifs-agph.spec.ts`
- **Résultat obtenu** : ☐ Conforme ☐ Non conforme — anomalie n° ______ — gravité ____ — initiales ____

### CA-P-CH-2 — Confection des horaires (préférentiel)

> La solution doit permettre à un employé de sélectionner un seul poste dans un délai défini lors de son tour de rôle.

- **Verdict à la matrice** : ✅ Acquis
- **Écran** : Urgences-santé › Confection AGPH › Mon AGPH — `/monagph`
- **Profil** : `julie.roy` (Paramédic — temps complet)
- **Étapes** :
  1. Se connecter à l'organisme d'essai avec `julie.roy` (Paramédic — temps complet).
  2. Ouvrir **Urgences-santé › Confection AGPH › Mon AGPH** (adresse `/monagph`).
  3. Un seul poste se choisit, dans le délai du tour de rôle ; passé le délai, le tour avance.
- **Résultat attendu** :
  - les commandes `agm-titre`, une commande `agm-campagne-…` par objet listé sont présentes et utilisables au clavier ;
  - le geste décrit à la dernière étape produit l'effet annoncé, sans erreur affichée ;
  - l'écran, dans l'état atteint, ne présente aucune violation axe-core (WCAG 2.2 AA).
- **Essais automatisés qui le couvrent déjà** :
  - Écran et commandes : `e2e/tests/e2e/urgences-sante-exigences.spec.ts` (avec audit axe-core)
  - Comportement (Java) : `ControleurAgphEffectifsHttpTest`
  - Bout en bout : `effectifs-agph.spec.ts`
- **Résultat obtenu** : ☐ Conforme ☐ Non conforme — anomalie n° ______ — gravité ____ — initiales ____

### CA-P-CH-3 — Confection des horaires (préférentiel)

> La solution doit empêcher toute sélection de poste non admissible selon les règles applicables.

- **Verdict à la matrice** : ✅ Acquis
- **Écran** : Urgences-santé › Confection AGPH › Mon AGPH — `/monagph`
- **Profil** : `julie.roy` (Paramédic — temps complet)
- **Étapes** :
  1. Se connecter à l'organisme d'essai avec `julie.roy` (Paramédic — temps complet).
  2. Ouvrir **Urgences-santé › Confection AGPH › Mon AGPH** (adresse `/monagph`).
  3. Un poste que les règles écartent n'est pas sélectionnable, et l'écran dit pourquoi.
- **Résultat attendu** :
  - les commandes `agm-titre`, une commande `agm-campagne-…` par objet listé sont présentes et utilisables au clavier ;
  - le geste décrit à la dernière étape produit l'effet annoncé, sans erreur affichée ;
  - l'écran, dans l'état atteint, ne présente aucune violation axe-core (WCAG 2.2 AA).
- **Essais automatisés qui le couvrent déjà** :
  - Écran et commandes : `e2e/tests/e2e/urgences-sante-exigences.spec.ts` (avec audit axe-core)
  - Comportement (Java) : `ControleurAgphEffectifsHttpTest`
- **Résultat obtenu** : ☐ Conforme ☐ Non conforme — anomalie n° ______ — gravité ____ — initiales ____

### CA-P-CH-4 — Confection des horaires (préférentiel)

> La solution doit permettre l’attribution automatisée des postes selon le tour de rôle.

- **Verdict à la matrice** : ✅ Acquis
- **Écran** : Urgences-santé › Confection AGPH › Confection annuelle (AGPH) — `/usagph`
- **Profil** : `claire.nadeau` (Planificatrice des effectifs)
- **Étapes** :
  1. Se connecter à l'organisme d'essai avec `claire.nadeau` (Planificatrice des effectifs).
  2. Ouvrir **Urgences-santé › Confection AGPH › Confection annuelle (AGPH)** (adresse `/usagph`).
  3. « Démarrer » la campagne : les postes s'attribuent d'office selon le tour de rôle, rang par rang.
- **Résultat attendu** :
  - les commandes `pla-agph`, `pla-agph-rangs` sont présentes et utilisables au clavier ;
  - le geste décrit à la dernière étape produit l'effet annoncé, sans erreur affichée ;
  - l'écran, dans l'état atteint, ne présente aucune violation axe-core (WCAG 2.2 AA).
- **Essais automatisés qui le couvrent déjà** :
  - Écran et commandes : `e2e/tests/e2e/urgences-sante-exigences.spec.ts` (avec audit axe-core)
  - Comportement (Java) : `ControleurAgphEffectifsHttpTest`
- **Résultat obtenu** : ☐ Conforme ☐ Non conforme — anomalie n° ______ — gravité ____ — initiales ____

### CA-P-CH-5 — Confection des horaires (préférentiel)

> La solution doit attribuer le statut temps complet (TC) aux employés ayant obtenu un poste de type temps complet (TC)

- **Verdict à la matrice** : ✅ Acquis
- **Écran** : Urgences-santé › Confection AGPH › Confection annuelle (AGPH) — `/usagph`
- **Profil** : `claire.nadeau` (Planificatrice des effectifs)
- **Étapes** :
  1. Se connecter à l'organisme d'essai avec `claire.nadeau` (Planificatrice des effectifs).
  2. Ouvrir **Urgences-santé › Confection AGPH › Confection annuelle (AGPH)** (adresse `/usagph`).
  3. Un employé qui obtient un poste à temps complet prend le statut TC ; le tableau de la campagne le montre.
- **Résultat attendu** :
  - les commandes `pla-agph`, `pla-agph-etat` sont présentes et utilisables au clavier ;
  - le geste décrit à la dernière étape produit l'effet annoncé, sans erreur affichée ;
  - l'écran, dans l'état atteint, ne présente aucune violation axe-core (WCAG 2.2 AA).
- **Essais automatisés qui le couvrent déjà** :
  - Écran et commandes : `e2e/tests/e2e/urgences-sante-exigences.spec.ts` (avec audit axe-core)
  - Comportement (Java) : `AgphDomaineTest`, `ControleurAgphEffectifsHttpTest`
  - Bout en bout : `effectifs-agph.spec.ts`
- **Résultat obtenu** : ☐ Conforme ☐ Non conforme — anomalie n° ______ — gravité ____ — initiales ____

### CA-P-CH-6 — Confection des horaires (préférentiel)

> La solution doit attribuer le statut temps partiel régulier (TPR) aux employés ayant obtenu un poste de type temps partiel régulier (TPR)

- **Verdict à la matrice** : ✅ Acquis
- **Écran** : Urgences-santé › Confection AGPH › Confection annuelle (AGPH) — `/usagph`
- **Profil** : `claire.nadeau` (Planificatrice des effectifs)
- **Étapes** :
  1. Se connecter à l'organisme d'essai avec `claire.nadeau` (Planificatrice des effectifs).
  2. Ouvrir **Urgences-santé › Confection AGPH › Confection annuelle (AGPH)** (adresse `/usagph`).
  3. Activer la commande `pla-agph-creer` (l'unité et le corps d'emploi de la campagne se choisissent dans le tiroir de création).
  4. Créer la campagne pour une unité et un corps d'emploi : le statut posé à la clôture suit le type du poste obtenu.
- **Résultat attendu** :
  - les commandes `pla-agph-creation`, `pla-agph-unite`, `pla-agph-corps` sont présentes et utilisables au clavier ;
  - le geste décrit à la dernière étape produit l'effet annoncé, sans erreur affichée ;
  - l'écran, dans l'état atteint, ne présente aucune violation axe-core (WCAG 2.2 AA).
- **Essais automatisés qui le couvrent déjà** :
  - Écran et commandes : `e2e/tests/e2e/urgences-sante-exigences.spec.ts` (avec audit axe-core)
  - Comportement (Java) : `ControleurAgphEffectifsHttpTest`
  - Bout en bout : `effectifs-agph.spec.ts`
- **Résultat obtenu** : ☐ Conforme ☐ Non conforme — anomalie n° ______ — gravité ____ — initiales ____

### CA-P-CH-7 — Confection des horaires (préférentiel)

> La solution doit attribuer le statut temps partiel aux employés n’ayant pas obtenu de poste.

- **Verdict à la matrice** : ✅ Acquis
- **Écran** : Urgences-santé › Confection AGPH › Confection annuelle (AGPH) — `/usagph`
- **Profil** : `claire.nadeau` (Planificatrice des effectifs)
- **Étapes** :
  1. Se connecter à l'organisme d'essai avec `claire.nadeau` (Planificatrice des effectifs).
  2. Ouvrir **Urgences-santé › Confection AGPH › Confection annuelle (AGPH)** (adresse `/usagph`).
  3. « Clore et poser les statuts », une fois l'attribution faite : les employés sans poste gardent le temps partiel occasionnel.
- **Résultat attendu** :
  - les commandes `pla-agph`, `pla-agph-campagne`, `pla-agph-etat` sont présentes et utilisables au clavier ;
  - le geste décrit à la dernière étape produit l'effet annoncé, sans erreur affichée ;
  - l'écran, dans l'état atteint, ne présente aucune violation axe-core (WCAG 2.2 AA).
- **Essais automatisés qui le couvrent déjà** :
  - Écran et commandes : `e2e/tests/e2e/urgences-sante-exigences.spec.ts` (avec audit axe-core)
  - Comportement (Java) : `AgphDomaineTest`, `ControleurAgphEffectifsHttpTest`
- **Résultat obtenu** : ☐ Conforme ☐ Non conforme — anomalie n° ______ — gravité ____ — initiales ____

### CA-P-CH-8 — Confection des horaires (préférentiel)

> La solution doit permettre l’application de codes d’absence planifiés de moyen ou long terme.

- **Verdict à la matrice** : ✅ Acquis
- **Écran** : Urgences-santé › Absences des effectifs › Absences et comblement — `/usabsences`
- **Profil** : `claire.nadeau` (Planificatrice des effectifs)
- **Étapes** :
  1. Se connecter à l'organisme d'essai avec `claire.nadeau` (Planificatrice des effectifs).
  2. Ouvrir **Urgences-santé › Absences des effectifs › Absences et comblement** (adresse `/usabsences`).
  3. Une absence de moyen ou long terme réserve ses places et ouvre un remplacement long terme qui leur est lié.
- **Résultat attendu** :
  - les commandes `pla-abs`, `pla-abs-table` sont présentes et utilisables au clavier ;
  - le geste décrit à la dernière étape produit l'effet annoncé, sans erreur affichée ;
  - l'écran, dans l'état atteint, ne présente aucune violation axe-core (WCAG 2.2 AA).
- **Essais automatisés qui le couvrent déjà** :
  - Écran et commandes : `e2e/tests/e2e/urgences-sante-exigences.spec.ts` (avec audit axe-core)
  - Comportement (Java) : `ControleurAbsencesEffectifsHttpTest`
  - Bout en bout : `effectifs-absences.spec.ts`
- **Résultat obtenu** : ☐ Conforme ☐ Non conforme — anomalie n° ______ — gravité ____ — initiales ____

### CA-P-CH-9 — Confection des horaires (préférentiel)

> La solution doit attribuer automatiquement les remplacements long terme selon les règles définies.

- **Verdict à la matrice** : ✅ Acquis
- **Écran** : Urgences-santé › Confection AGPH › Confection annuelle (AGPH) — `/usagph`
- **Profil** : `claire.nadeau` (Planificatrice des effectifs)
- **Étapes** :
  1. Se connecter à l'organisme d'essai avec `claire.nadeau` (Planificatrice des effectifs).
  2. Ouvrir **Urgences-santé › Confection AGPH › Confection annuelle (AGPH)** (adresse `/usagph`).
  3. « Ouvrir un remplacement » puis, à la date limite, l'attribution aux intéressés selon les règles.
- **Résultat attendu** :
  - les commandes `pla-rlt-table`, `pla-rlt-ouvrir` sont présentes et utilisables au clavier ;
  - le geste décrit à la dernière étape produit l'effet annoncé, sans erreur affichée ;
  - l'écran, dans l'état atteint, ne présente aucune violation axe-core (WCAG 2.2 AA).
- **Essais automatisés qui le couvrent déjà** :
  - Écran et commandes : `e2e/tests/e2e/urgences-sante-exigences.spec.ts` (avec audit axe-core)
  - Comportement (Java) : `AgphDomaineTest`, `ControleurAgphEffectifsHttpTest`
- **Résultat obtenu** : ☐ Conforme ☐ Non conforme — anomalie n° ______ — gravité ____ — initiales ____

### CA-P-GP-1 — Gestion du "punch" (préférentiel)

> La solution doit permettre aux employés la déclaration des heures de travail de manière asynchrone (postériorie) selon des profils configurables

- **Verdict à la matrice** : ✅ Acquis
- **Écran** : Urgences-santé › Pointage et présence › Mon pointage — `/monpointage`
- **Profil** : `julie.roy` (Paramédic — temps complet)
- **Étapes** :
  1. Se connecter à l'organisme d'essai avec `julie.roy` (Paramédic — temps complet).
  2. Ouvrir **Urgences-santé › Pointage et présence › Mon pointage** (adresse `/monpointage`).
  3. Activer la commande `mpo-declarer-ouvrir` (le formulaire vit dans un dialogue depuis le 8 novembre 2026 : ses champs n'existent qu'ouvert).
  4. « Déclarer un temps » : l'employé saisit après coup, dans la fenêtre que son profil autorise.
- **Résultat attendu** :
  - les commandes `mpo-titre`, `mpo-declarer-ouvrir`, `mpo-s-envoyer` sont présentes et utilisables au clavier ;
  - le geste décrit à la dernière étape produit l'effet annoncé, sans erreur affichée ;
  - l'écran, dans l'état atteint, ne présente aucune violation axe-core (WCAG 2.2 AA).
- **Essais automatisés qui le couvrent déjà** :
  - Écran et commandes : `e2e/tests/e2e/urgences-sante-exigences.spec.ts` (avec audit axe-core)
  - Comportement (Java) : `ProfilsPointageTest`, `ControleurPresenceEffectifsHttpTest`
- **Résultat obtenu** : ☐ Conforme ☐ Non conforme — anomalie n° ______ — gravité ____ — initiales ____

### CA-P-GP-2 — Gestion du "punch" (préférentiel)

> La solution doit valider les heures saisies selon les règles d’affaires applicables au quart de travail

- **Verdict à la matrice** : ✅ Acquis
- **Écran** : Urgences-santé › Pointage et présence › Pointage et présence (console) — `/uspresence`
- **Profil** : `claire.nadeau` (Planificatrice des effectifs)
- **Étapes** :
  1. Se connecter à l'organisme d'essai avec `claire.nadeau` (Planificatrice des effectifs).
  2. Ouvrir **Urgences-santé › Pointage et présence › Pointage et présence (console)** (adresse `/uspresence`).
  3. Les heures saisies sont jugées par les règles du quart : la fiche du pointage liste les motifs retenus.
- **Résultat attendu** :
  - les commandes `pla-pre`, `pla-pre-table` sont présentes et utilisables au clavier ;
  - le geste décrit à la dernière étape produit l'effet annoncé, sans erreur affichée ;
  - l'écran, dans l'état atteint, ne présente aucune violation axe-core (WCAG 2.2 AA).
- **Essais automatisés qui le couvrent déjà** :
  - Écran et commandes : `e2e/tests/e2e/urgences-sante-exigences.spec.ts` (avec audit axe-core)
  - Comportement (Java) : `ControleurPresenceEffectifsHttpTest`
- **Résultat obtenu** : ☐ Conforme ☐ Non conforme — anomalie n° ______ — gravité ____ — initiales ____

### CA-P-GP-3 — Gestion du "punch" (préférentiel)

> La solution doit permettre la saisie manuelle de plusieurs segments de temps pour un quart exceptionnel.

- **Verdict à la matrice** : ✅ Acquis
- **Écran** : Urgences-santé › Pointage et présence › Mon pointage — `/monpointage`
- **Profil** : `julie.roy` (Paramédic — temps complet)
- **Étapes** :
  1. Se connecter à l'organisme d'essai avec `julie.roy` (Paramédic — temps complet).
  2. Ouvrir **Urgences-santé › Pointage et présence › Mon pointage** (adresse `/monpointage`).
  3. Activer la commande `mpo-declarer-ouvrir` (le formulaire vit dans un dialogue depuis le 8 novembre 2026 : ses champs n'existent qu'ouvert).
  4. Saisir « 12:00 » dans `mpo-s-pause-debut` (la nature du temps non payé n'est demandée qu'une fois la pause bornée).
  5. Saisir « 12:30 » dans `mpo-s-pause-fin` (idem : les deux bornes font apparaître le champ).
  6. Déclarer un temps avec une pause : la nature de la période non payée est exigée dès que la pause est bornée — un segment, puis un autre, sur le même quart.
- **Résultat attendu** :
  - les commandes `mpo-declarer-ouvrir`, `mpo-s-date`, `mpo-s-entree`, `mpo-s-sortie`, `mpo-s-nature` sont présentes et utilisables au clavier ;
  - le geste décrit à la dernière étape produit l'effet annoncé, sans erreur affichée ;
  - l'écran, dans l'état atteint, ne présente aucune violation axe-core (WCAG 2.2 AA).
- **Essais automatisés qui le couvrent déjà** :
  - Écran et commandes : `e2e/tests/e2e/urgences-sante-exigences.spec.ts` (avec audit axe-core)
  - Comportement (Java) : `ControleurPresenceEffectifsHttpTest`
- **Résultat obtenu** : ☐ Conforme ☐ Non conforme — anomalie n° ______ — gravité ____ — initiales ____

### CA-P-GP-4 — Gestion du "punch" (préférentiel)

> La solution doit permettre le cumul cohérent de plusieurs saisies associées à un même quart.

- **Verdict à la matrice** : ✅ Acquis
- **Écran** : Urgences-santé › Feuilles de temps › Ma feuille de temps — `/matemps`
- **Profil** : `julie.roy` (Paramédic — temps complet)
- **Étapes** :
  1. Se connecter à l'organisme d'essai avec `julie.roy` (Paramédic — temps complet).
  2. Ouvrir **Urgences-santé › Feuilles de temps › Ma feuille de temps** (adresse `/matemps`).
  3. Sur la feuille : les totaux cumulent les saisies du même quart, et isolent les heures supplémentaires.
- **Résultat attendu** :
  - les commandes `mft-titre`, `mft-feuille-detail`, `mft-totaux`, `mft-hs` sont présentes et utilisables au clavier ;
  - le geste décrit à la dernière étape produit l'effet annoncé, sans erreur affichée ;
  - l'écran, dans l'état atteint, ne présente aucune violation axe-core (WCAG 2.2 AA).
- **Essais automatisés qui le couvrent déjà** :
  - Écran et commandes : `e2e/tests/e2e/urgences-sante-exigences.spec.ts` (avec audit axe-core)
  - Comportement (Java) : `RepartitionPresenceTest`, `ControleurPresenceEffectifsHttpTest`
- **Résultat obtenu** : ☐ Conforme ☐ Non conforme — anomalie n° ______ — gravité ____ — initiales ____

### CA-P-RF-1 — Rapports et formulaires (préférentiel)

> La solution doit produire des relevés statistiques selon différents paramètres définis par l'organisation

- **Verdict à la matrice** : ✅ Acquis
- **Écran** : Comptabilité › Constructeur de rapports — `/rapports`
- **Profil** : `admin` (administration de l'organisme)
- ⚠ **Écran masqué dans l'organisme semé** : sa section n'est pas gardée par `semer-urgences-sante.cjs` ; rétablir la vue avant d'exécuter le cas (§ 11).
- **Étapes** :
  1. Se connecter à l'organisme d'essai avec `admin` (administration de l'organisme).
  2. Ouvrir **Comptabilité › Constructeur de rapports** (adresse `/rapports`).
  3. Constructeur de rapports : la source, les colonnes, les filtres, les regroupements et le tri sont définis par l'organisation, puis enregistrés comme relevé réutilisable.
- **Résultat attendu** :
  - les commandes `rap-titre`, `rap-source`, `rap-colonnes`, `rap-groupes`, `rap-filtres`, `rap-enregistrer` sont présentes et utilisables au clavier ;
  - le geste décrit à la dernière étape produit l'effet annoncé, sans erreur affichée ;
  - l'écran, dans l'état atteint, ne présente aucune violation axe-core (WCAG 2.2 AA).
- **Essais automatisés qui le couvrent déjà** :
  - Écran et commandes : `e2e/tests/e2e/urgences-sante-exigences.spec.ts` (avec audit axe-core)
  - Comportement (Java) : `RelevesEffectifsDomaineTest`, `FaitsRelevesEffectifsHttpTest`, `ControleurTempsEffectifsHttpTest`, `RelevesStatistiquesTest`, `ControleurRapportsHttpTest`
  - Bout en bout : `effectifs-releves.spec.ts`, `audit-wcag22.spec.ts`
- **Résultat obtenu** : ☐ Conforme ☐ Non conforme — anomalie n° ______ — gravité ____ — initiales ____

<!-- FIN CAHIER GENERE : cas -->

## 10. Essais de performance

### 10.1 Ce que le devis exige

- **E-TN-17** : les fonctionnalités transactionnelles de base s'exécutent à l'intérieur de **2 s**.
- Les objectifs (SLO) et engagements (SLA) par classe d'endpoint sont dans `perf/jmeter/slo.json` :
  lecture p95 500 ms, écriture p95 1 000 ms, rapport p95 2 000 ms, connexion p95 800 ms, budget
  d'erreur 99,9 %. Leur justification est au document 09 § 9.4.

### 10.2 Cas de performance

| Cas | Plan | Ce qui est vérifié | Critère d'acceptation |
|---|---|---|---|
| CP-1 | `perf/jmeter/operations/LireComblementDeLaPeriode.jmx` | `GET /api/effectifs/planification/periodes/{id}/comblement` | p95 ≤ SLA de `operations.json`, 0 % de 5xx |
| CP-2 | `perf/jmeter/operations/LireEquipagesDeLaPeriode.jmx` | `GET /api/effectifs/planification/periodes/{id}/equipages` | idem |
| CP-3 | `perf/jmeter/operations/LireMaFeuilleDeTemps.jmx` | `GET /api/effectifs/temps/moi` | idem |
| CP-4 | `perf/jmeter/operations/LireMesDisponibilites.jmx` | `GET /api/effectifs/disponibilites/moi` | idem |
| CP-5 | `perf/jmeter/operations/PointerUneEntree.jmx` | `POST /api/effectifs/presence/pointages` | idem, **et** les réponses sont des 2xx (voir § 10.4) |
| CP-6 | `perf/jmeter/operations/PreparerLesFeuillesDeTemps.jmx` | `POST /api/effectifs/temps/feuilles/preparer` | opération de lot : engagement à part (doc 09 § 9.5) |
| CP-7 | `perf/jmeter/plans/endpoints-effectifs.jmx` | Les routes `/api/effectifs` (balayage) | ≥ 80 % de réponses 2xx (`--min-2xx 80`), 0 % de 5xx, p95 par classe ≤ SLO |
| CP-8 | `perf/jmeter/plans/endpoints-effectifs.jmx` avec `-Jutilisateurs=2500 -Jduree=1200` | La charge contractuelle | p95 ≤ 2 000 ms sur les transactions de base, sur une machine au repos |

### 10.3 Exécution contre le backend Java

```bash
perf/jmeter/backend-java-local.sh &                              # Java isolé, port 4020, base neuve
node perf/jmeter/preparer.cjs --env local --lignes 3             # contexte typique ; écrit donnees/tenant-local.txt
T=$(cat perf/jmeter/donnees/tenant-local.txt)
perf/jmeter/lancer.sh local plans/endpoints-effectifs.jmx -Jtenant=$T                         # CP-7
node perf/jmeter/analyser.cjs perf/jmeter/resultats/local/<horodatage>/endpoints-effectifs.jtl --env local --min-2xx 80
for OP in LireComblementDeLaPeriode LireEquipagesDeLaPeriode LireMaFeuilleDeTemps \
          LireMesDisponibilites PointerUneEntree PreparerLesFeuillesDeTemps; do
  perf/jmeter/lancer.sh local operations/$OP.jmx -Jtenant=$T                                  # CP-1 à CP-6
done
node perf/jmeter/preparer.cjs --env local --profil volumineux    # contexte volumineux, puis rejouer CP-1 à CP-6
```

Dans l'environnement de tests et formation, la même campagne se joue avec un fichier de propriétés
propre à cet environnement (hôte, port, comptes) passé à `lancer.sh`, et la limite du pare-feu
applicatif (`WAF_REQUETES_PAR_MINUTE`) dimensionnée aux adresses des générateurs de charge.

### 10.4 Réserves connues à ce jour (doc 09)

- Les mesures publiées au document 09 ont été prises **sur le backend Node**, sur une machine
  chargée : elles ne sont pas une référence. La campagne d'acceptation se joue sur le Java.
- Les **écritures** ne sont pas mesurées par le balayage : 45 corps de requête générés depuis les
  exemples OpenAPI sont refusés (422) ; ils sont à compléter avant que CP-7 soit concluant sur les
  écritures.
- **CP-5** mesurait le refus de la garde (pointage rejoué sur la même place), non l'écriture : il
  exige le profil volumineux, une place distincte par utilisateur virtuel et par itération.
- **CP-8** (2 500 utilisateurs, dont 500 en écriture) n'a jamais été joué.

## 11. Ce que cette procédure ne couvre pas

- **Le constructeur de rapports dans l'organisme semé** : les cas CA-E-RF-8 et CA-P-RF-1 ouvrent
  `/rapports`, écran rangé dans la section « Comptabilité ». `scripts/semer-urgences-sante.cjs`
  masque cette section pour l'organisme de démonstration ; l'écran n'y est donc pas atteignable
  sans rétablir la vue (Superadmin › Modules et accès › Sous-menus visibles). L'essai automatisé
  ne le voit pas, parce qu'il éprouve un organisme sans masquage. À trancher avant la campagne.
- **Les interfaces réelles** (RAO, SAAV, PFT, BD Corpo, Microsoft Graph, ESB, entrepôt de données) :
  les cas les éprouvent contre les simulateurs. Leur validation avec les systèmes d'Urgences-santé
  relève du Contrat 2.12 c (« l'échange d'information pour chaque interface est validé, testé et
  approuvé ») et d'une procédure d'intégration distincte.
- **Les exigences technologiques** autres que la performance (sécurité, disponibilité, reprise,
  hébergement) : voir la matrice de couverture.
- **Les plans de relève et de transition** (Contrat 2.08 c) : hors de ce document.

## 12. Régénérer le cahier

```bash
python3 docs/urgences-sante/construire-procedure-acceptation.py
```

Le script lit les recettes, les verdicts, les scénarios et le catalogue des vues ; il échoue si une
exigence fonctionnelle n'a pas sa recette, si un écran n'est pas au catalogue ou si un essai cité
n'existe plus, et il signale toute commande citée qu'il ne retrouve pas dans le front-end. Le
relancer après chaque modification de `e2e/donnees/urgences-sante/exigences-fonctionnelles.json`,
de `comportements.json` ou de la matrice.
