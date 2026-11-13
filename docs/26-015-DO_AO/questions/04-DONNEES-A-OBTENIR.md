# 4. Les données à obtenir d'Urgences-santé

But : lister ce que **seul le client possède** et sans quoi la solution, entièrement paramétrable,
tourne sur nos exemples. Chaque ligne nomme le référentiel ou l'écran qui la reçoit, pour que la
demande soit précise et que la réponse se charge sans retraitement.

Le moteur d'`effectifs` ne code **aucune** règle de convention : tout ce qui suit est une donnée
datée (`effectifDepuis`), versionnée et journalisée. C'est ce qui permet de suivre une convention
renégociée sans développement (Cahier 2.5) — à condition d'avoir la donnée.

Moment : **[DÉMARRAGE]** pour la structure, **[ATELIER]** pour les règles.

## 4.1 Structure de l'organisation

| # | Donnée | Reçue par | Format souhaité | Exigences |
|---|---|---|---|---|
| D-01 | Arbre des **unités** (direction → service → secteur), avec dates d'effet | Référentiel `unites` (attribut `parent`) | Tableau : code, libellé, parent, depuis | E-GN-7, E-GN-8 |
| D-02 | **Corps d'emploi** (paramédics, RMU, logistique…) et **titres d'emploi** rattachés (PSA, TAP…) | `corps_emploi`, `titres_emploi` (attribut `corps`) | code, libellé, corps | E-GN-1, P-GH-1 |
| D-03 | **Statuts d'emploi** (TC, TPR, TP, occasionnel…) et leur sens à la convention | `statuts_emploi` (comportement) | code, libellé, comportement | P-CH-5 à P-CH-7 |
| D-04 | **Milieux physiques** : casernes, centres, CCS — adresse, coordonnées, rayon de présence, réseau | Écran Milieux ; preuves par milieu | code, libellé, unité, lat./long., rayon (m), plages IP | E-GN-17, E-GP-10 à E-GP-13 |
| D-05 | **Postes** existants (poste-ligne, véhicule, cycle de rotation, statut offert) | Écran Postes | code, unité, corps, statut, cycle sur 7 jours, paramètres | E-CH-2 à E-CH-5 |
| D-06 | **Paramètres de poste** utilisés (véhicule, alternance, langue…) et leur type | `parametres_poste` (`typeValeur`) | code, libellé, type (texte, booléen, nombre) | E-CH-5 |

## 4.2 Personnes et accès

| # | Donnée | Reçue par | Format souhaité | Exigences |
|---|---|---|---|---|
| D-07 | **Ressources** : matricule, nom, unité, corps, titre, statut, **ancienneté** (unité de mesure à préciser) | Écran Ressources (import possible) | Extraction de BD Corpo | P-CH-1, P-CH-4 |
| D-08 | **Comptes** : correspondance matricule ↔ identité Entra ID | Liaison compte ↔ ressource | UPN Entra par matricule | E-TN-2 |
| D-09 | **Périmètres** d'encadrement : qui planifie, qui gère, qui consulte, quelles unités | Écran Périmètres | personne, unité, étendue, niveau | E-GN-8 |

## 4.3 Temps, quarts et absences

| # | Donnée | Reçue par | Format souhaité | Exigences |
|---|---|---|---|---|
| D-10 | **Plages horaires** (jour, soir, nuit…) avec heures de début et de fin | `plages_horaires` | code, début, fin (minutes) | E-GR-8 |
| D-11 | **Natures de quart** (régulier, ad hoc, formation, renfort) et **présence préalable rémunérée** par nature | `natures_quart` | code, comportement, minutes préalables | E-GN-3, E-GP-9 |
| D-12 | **Codes d'absence** : catégorie de durée (ponctuelle, moyen, long terme), effet de paie, titres admis, planifiable d'avance | `codes_absence` | code, comportement, attributs | P-GH-1, P-CH-8 |
| D-13 | **Types d'heures** et **codes de paie** (régulières, HS et majoration, fériées, primes de soir, nuit, fin de semaine) | `types_heure` (attribut `codePaie`) | code, comportement, code de paie, majoration | E-RF-6, E-RF-12 |
| D-14 | **Jours fériés** reconnus, par année | `jours_feries` | date, libellé | E-RF-5 |
| D-15 | **Quotas de comblement** par unité, plage et nature | Écran Quotas | unité, plage, nature, quota, depuis | E-GR-8 |
| D-16 | **Profils de pointage** (mode, fenêtre, natures admises) | Profils de présence | par corps ou unité | E-GP-1, P-GP-1 |

## 4.4 Règles de convention (moteur de règles)

Une règle est un **fait**, un **opérateur**, un **seuil** et une **action** (accepter, avertir,
exiger une approbation, refuser, bloquer), avec son message. Pour chacune, il nous faut le texte de
l'article qui la fonde : c'est lui qui expliquera la décision à l'employé.

| # | Contexte | Exemples de règles à fournir | Exigences |
|---|---|---|---|
| D-17 | **ATTRIBUTION** | repos minimal entre quarts, plafond hebdomadaire, qualification requise | E-GN-11, P-GN-1 |
| D-18 | **DISPONIBILITE** | quarts minimaux par semaine selon le statut, fins de semaine, heures minimales par période | E-GN-12 à E-GN-16 |
| D-19 | **ABSENCE** | taux de comblement plancher, préavis, durée nécessitant l'accord du chef | P-GN-2, P-GN-3, P-GH-2 |
| D-20 | **ECHANGE** | contiguïté, repos, échange entre périodes (autorisé / approbation / interdit) | E-GR-7, E-GR-12 |
| D-21 | **CHOIX_AGPH** | réserve du temps complet aux premiers rangs, admissibilité au poste | P-CH-3 |
| D-22 | **INTERET_REMPLACEMENT** | qui peut se manifester pour un remplacement long terme | E-CH-8, P-CH-9 |
| D-23 | **POINTAGE** | tolérance de retard, distance maximale, mécanismes exigés | E-GP-4, E-GP-13 |
| D-24 | **FEUILLE_DE_TEMPS** et **MODIFICATION_HEURES** | seuils d'HS (jour, semaine), délai de correction par l'employé (p. ex. 72 h), période fermée | E-RF-5, E-RF-10, E-GP-3 |

## 4.5 Intégrations et historique

| # | Donnée | Pour | Exigences |
|---|---|---|---|
| D-25 | Spécifications d'interface de **RAO, SAAV, PFT, BD Corpo** (format, sens, fréquence, environnement d'essai) | Remplacer les simulateurs par les adaptateurs réels (le port, le simulateur et la suite de contrat existent) | Cahier 2.3, E-RF-12, E-RF-13 |
| D-26 | **Inscription d'application Entra** (identifiant client, locataire, permissions Graph accordées) | SSO et passerelle Graph | E-TN-2, Cahier 2.2 |
| D-27 | **Historique** à reprendre : soldes de banques d'ouverture, ancienneté, horaires publiés de l'année en cours | Démarrage sans ressaisie | Contrat 2.01.01 c) |
| D-28 | **Calendrier AGPH** de l'année : dates d'ouverture, délai de choix par rang, budget d'heures par semaine type | Première campagne en production | E-CH-1 |
