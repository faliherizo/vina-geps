# 26-015-DO — Traitement des réponses d'Urgences-santé (addenda 1 à 7, grille v2)

> Établi le **20 novembre 2026**, six jours avant le dépôt (**26 novembre 2026, 14 h 00**, addenda 7).
> Sources converties par [`questions/convertir-reponses.py`](../questions/convertir-reponses.py) dans
> [`questions/reponses/`](../questions/reponses/) : `addenda-01.md` à `addenda-07.md`, `grille-criteres-v2.md`.
> Chaque écart annoncé ici a été **revérifié contre le code** (`backend-java/`, `frontend/`) le 20 novembre : la
> colonne « Vérifié dans le code » cite le symbole lu. Une matrice passée a déjà annoncé douze faux écarts ; aucune
> conclusion ci-dessous ne vient d'une matrice.

## 0. Conversion — ce qui a été vérifié

| Fichier | Contenu | Constat |
|---|---|---|
| `addenda-01.md` | Addenda **n° 1** (14 oct.) : hébergement hors Québec admis au Canada, 16 semaines visant un PMV, licences d'un éditeur tiers | **Nouveau.** L'ancien `addenda-01.md` contenait en réalité l'addenda n° 06 : vérifié mot pour mot contre le PDF (7 073 mots, aucun manquant), puis remplacé par sa conversion structurée `addenda-06.md`. |
| `addenda-02.md` | N° 2 (26 oct.) : contrat d'adhésion non négociable, E-TN-19 « au CANADA », grille de rendement sur 50 | Nouveau |
| `addenda-03.md` | N° 3 (29 oct.) : dépôt reporté au 17 nov. | Nouveau |
| `addenda-04.md` | N° 4 (4 nov.) : garantie de soumission 50 000 $, clause 17.11 réécrite, sous-traitants technologiques, autonomie SaaS, **articles 14 à 16 de la convention SP-CSN des paramédics** | Nouveau. Deux valeurs juxtaposées à l'art. 15.14 (rature non détectable dans le PDF : signalé en tête du fichier) |
| `addenda-05.md` | N° 5 (5 nov.) : **périmètre des interfaces** — le fournisseur expose une API, l'Organisme l'emploie | Nouveau |
| `addenda-06.md` | N° 6 (10 nov.), 14 pages dont la grille modifiée | Déjà traité au [fichier 07](../questions/07-ADDENDA-06-ET-QUESTIONS-METIER.md) ; aucune autre réponse dans les 7 pages de grille |
| `addenda-07.md` | N° 7 (19 nov.) : **réponses à nos 16 questions métier** (fichier 07 § 7.3), compréhension A1 à A8 d'un autre soumissionnaire, correction de 0.01.06 | Nouveau — le cœur de ce dossier |
| `grille-criteres-v2.md` | Grille v2 (Excel), 144 critères ; **aucune cellule barrée** | Quatre écarts avec l'origine : E-TN-9, E-TN-11, E-TN-19 (addenda 6 et 2) et une coquille d'E-GP-15. `addenda-26-015-DO.json` reprend désormais le texte v2 **mot pour mot** (les textes consignés le 12 nov. étaient des paraphrases) ; la précision de l'addenda passe dans `precision`. |

Le texte barré des PDF est détecté comme dans le dossier FX-AO (trait horizontal au milieu du caractère) : **aucun**
caractère barré dans les sept addenda. Les en-têtes « Dans quel pays… / Quel est le nom de l'hébergeur ? » de la
grille v2 existaient déjà dans la grille d'origine (réponse : Canada, Québec — AWS `ca-central-1`).

## 1. Addenda 7 — nos 16 questions, une par une

| Q | Exigence | Réponse d'Urgences-santé | Vérifié dans le code | Effet | Action |
|---|---|---|---|---|---|
| 1 | E-GN-17, E-GP-10/11/13 | Le milieu est un **bâtiment** (point de service), configurable selon le type de quart et le groupe ; la localisation se valide **au début et à la fin** du quart | `Poste.milieuId` (poste = groupe × type de quart), milieu de l'équipage ad hoc ; `GestionnairePointagesEffectifs` juge les preuves à **chaque** pointage, entrée et sortie comprises (`PresenceEffectifs.jugerPreuves`) | Conforme. Le véhicule n'est pas un milieu : rien à retirer, rien à ajouter | Déclarer dans la proposition |
| 2 | E-GP-1, P-GP-1 | **Les deux** moyens : cellulaire géolocalisé dans le bâtiment **ou** poste sur le lieu de travail ; l'asynchrone aussi | `Milieux.Mecanisme` : `GEOREPERAGE`, `CODE_QR_DE_LIEU`, `RESEAU`, `BORNE`, `SOURCE_EXTERNE` | Conforme | Démo : un pointage par géorepérage, un par borne |
| 3 | E-GP-9, E-RF-12 | Présence préalable **rattachée au quart** ; ex. quart de 15 h, présence dès 14 h 45 | Attribut `presencePrealableMinutes` de la nature de quart ; segment `PRESENCE_PREALABLE` porté par la place (`TypeSegment`) | Conforme | — |
| 4 | E-GN-5, E-GN-6 | « Oui » : l'autorisation manuelle reste possible, **même sous le minimum** | `AbsencesEffectifs.Decision.DEROGER` n'interroge pas le minimum ; `GestionnaireComblementAbsences.combler` s'exécute après toute absence accordée | Conforme | — |
| 5 | E-GR-3 | Le taux se mesure par rapport au **niveau cible configuré** (quota) pour la période, la plage ou le type de quart, « c'est-à-dire le nombre de ressources prévu » | `AbsencesEffectifs.Impact` : pourvues / **places prévues** de l'unité | Conforme à la lecture « nombre prévu ». Lecture « quota » : le tableau de comblement montre déjà les quotas manqués (E-GR-8) | Interprétation déclarée |
| 6 | E-GN-11 | Traçabilité **obligatoire** (utilisateur, date-heure, quart, ressource) ; le motif n'est pas exigé | `GestionnairePlaces` : `place.attribution.forcee` au journal, avec l'acteur, l'instant, la place et la ressource ; la justification est exigée en plus | Conforme (au-delà) | — |
| 7 | E-CH-6, P-CH-8, E-CH-7, E-GR-4 | Traiter selon la **durée** : moyen/long terme planifié → remplacement long terme, **sauf si la période horaire est en cours** ; ponctuel → quart par quart. **Un seuil configurable par code d'absence** décide | `GestionnaireComblementAbsences.dureeLongue` lit la **catégorie** du code (`PONCTUELLE`, `MOYEN_TERME`, `LONG_TERME`), sans seuil de durée ni égard à la période en cours | **Écart** : ni seuil, ni exception « période en cours » | **AD-02** |
| 8 | E-GR-7 | Contiguïté = **chevauchement** + **heures maximales par jour de travail** (24 h, sans les 15 min avant le quart), paramétrables par groupe, statut et convention | `EchangeEffectifs.faitsDUnSens` refuse le chevauchement ; faits `reposHeures`, `heuresSemaine`, `contigu`, `memePeriode` — **aucun fait d'heures sur 24 h** | **Écart** : le second aspect n'est pas exprimable. La portée par groupe, statut et convention existe (`PorteeRegle`) | **AD-01** |
| 9 | E-GR-12 | Dans la période et conforme : peut être autorisé ; **hors période : approbation** d'un utilisateur autorisé | Règle `ECHANGE` sur `memePeriode` → `EXIGER_APPROBATION` (décision D-9) ; `EchangeEffectifs.issueDeLAcceptation` → `A_VALIDER` | Conforme | Démo : la règle « hors période → approbation » |
| 10 | E-GR-9 | Sans effet rétroactif = s'applique aux attributions et comblements **faits après** l'entrée en vigueur | Surcharges de quota datées, jamais rétroactives (`GestionnaireQuotas`, `Referentiels`) | Conforme | — |
| 11 | E-CH-1 | L'« optimisation des budgets » est un **calcul d'optimisation proposé par la solution** ; budget en **heures** | `AgphSemaineType.faits` : `budgetHeures`, `tauxBudget` — le budget est une **limite** jugée par les règles `DEPOT_AGPH`, jamais une proposition | **Écart sur un critère essentiel** | **AD-04** |
| 12 | P-CH-2, P-CH-4 | Délai échu : traitement **configurable** (attribution selon une règle prédéfinie). L'employé qui **décide** de ne pas prendre de poste reçoit le **temps partiel** | `TourDeRoleAgph.solder` attribue d'office le premier poste admissible, sans réglage ; **aucun geste « renoncer »** | **Écart** : ni réglage du traitement, ni renonciation | **AD-05** |
| 13 | P-CH-5 à P-CH-7 | Temps partiel attribué = **temps partiel occasionnel**, distinct du régulier ; le changement de statut se **transmet** à la paie et aux RH (interface ou export) | `Comportements.StatutEmploi.TEMPS_PARTIEL_OCCASIONNEL` ; `ClotureAgph` pose le statut, `ressource.statut` au journal, relayé par le connecteur ESB (`RelaisJournalIntegration`) | Conforme ; la transmission par l'API machine relève d'AD-06 | — |
| 14 | E-CH-10 | Le transfert des quarts non couverts se fait **à la confection horaire périodique**, une fois par mois | Les postes non pourvus deviennent places libres à la clôture (`ClotureAgph`), donc présentes à chaque confection | Conforme sur l'effet ; le moment diffère (plus tôt) sans priver la confection de rien | Interprétation déclarée |
| 15 | E-RF-5 | Seuils **quotidiens, hebdomadaires et par période de paie**, paramétrables **par groupe d'employés et règles conventionnées** | `TempsEffectifs.heuresSupplementaires` : seuils jour et semaine **de l'organisme** (`Parametres`) ; aucun seuil de période de paie, aucun par convention | **Écart sur un critère essentiel** | **AD-03** |
| 16 | P-GH-4, P-GN-1 | Mêmes règles d'admissibilité et de priorité | `GestionnaireAttributionFinDeCollecte` et le comblement passent par `attribuerAuPrioritaire` | Conforme | — |

### Partie A (compréhension d'un autre soumissionnaire)

| Point | Réponse | Effet pour nous |
|---|---|---|
| A1, A2, A4, A6 | VRAI | Conformes au produit (milieu par quart, début et fin, action paramétrable sur écart, localisation consultable pour l'audit) |
| A3 « le choix du mécanisme appartient au soumissionnaire » | **Besoin de clarifications** | Non tranché. Nous offrons cinq mécanismes cumulables : aucune lecture restrictive ne nous met en défaut |
| A5 « l'écart est signalé à l'employé et apparaît comme anomalie » | **Besoin de clarifications** | Non tranché ; le produit fait les deux |
| A7 « saisie a posteriori sans validation de lieu, tracée » | VRAI, **mais soumise à un profil autorisé** | Conforme : règles `MODIFICATION_HEURES` sur `roleActeur` et `niveauActeur` ; la démo doit le montrer |
| A8 « RAO et SAAV envoient les horodatages par l'API » | **Besoin de clarifications** : les communications vont **surtout du système d'horaires vers le SAAV** | Le flux sortant (quarts → SAAV) prime : il passe par l'API d'échange (AD-06) et le relais d'événements existant |
| B1 à B4 | Sans réponse propre | B3 (appareils fournis ou personnels) et B4 (logiciels tiers) restent ouverts |

## 2. Addenda 1 à 5 — ce qui touche la solution

| Addenda | Point | Effet | Action |
|---|---|---|---|
| 1 Q1, 2 Q3, 4 Q5 | Données au **Canada** (orientation 2), protection équivalente à l'art. 70.1 ; relève et sauvegardes au Canada admises | E-TN-19 ✅ (`ca-central-1`) ; une relève dans une autre région canadienne est admise | Proposition : nommer la région de relève |
| 1 Q2, 4 Q6 | 16 semaines depuis le démarrage, pour un **PMV** qui tient toutes les exigences opérationnelles critiques ; négociable | Calendrier de déploiement par phases admis | Plan de projet (P-TN-23) |
| 1 Q3, 2 Q1 | Licences d'un éditeur tiers admises, mais **un seul cocontractant** responsable de tout | Sans effet (nous sommes l'éditeur) | — |
| 2 Q2 | Contrat d'adhésion, **non négociable** | Toute réserve contractuelle est exclue | — |
| 2 | Grille de rendement notée sur **50** | Sans effet technique | — |
| 3 | Dépôt reporté (puis 26 nov. par les addenda 6 et 7) | — | — |
| 4 Q1 | **Convention SP-CSN, art. 14 à 16, jointe** (en renégociation, la convention échue s'applique) | Premières valeurs réelles de règles : voir § 3 | **AD-08** (paramétrage de démonstration) |
| 4 Q2, Q3 | Garantie de soumission **50 000 $** ; 17.11 réécrite (plafond = 12 mois de bordereau, sauf 20.06) | Hors code | Soumission |
| 4 Q4 | Les tiers technologiques (hébergement, SMS…) **identifiés au dépôt** sont autorisés sans approbation ultérieure | Hors code : liste à joindre (AWS, fournisseurs SMS, courriel, poussée) | Soumission |
| 4 Q7 | Délais de 5.02 **maintenus** (obligations de résultat) | Déjà fait (ADR 0071, politique « service essentiel ») | — |
| 4 Q9, Q10 | Autonomie attendue = **paramétrage, administration, rapports, interfaces documentées** ; pas d'accès à l'infrastructure | Conforme au SaaS | — |
| 5 Q1 | **Pas d'interface à développer vers RAO, SAAV, PFT, BD Corpo** : le fournisseur expose une **API standard, documentée et sécurisée** pour (a) obtenir les quarts planifiés et leurs correctifs, (b) être informé des modifications en quasi temps réel, (c) **transmettre les employés, leurs mises à jour et les soldes des banques de vacances et de congés**, (d) transmettre des modifications sur les quarts réels (absence déclarée aux opérations), (e) obtenir les relevés de présence validés. Microsoft Graph = intégration M365 / Entra, pas les systèmes internes | Vérifié : (b) existe (connecteur ESB, webhooks `/api/integrations/webhooks`) ; les jetons `client_credentials` sont **émis** (`ControleurOAuth`, `JetonClient`) mais **acceptés par aucune route métier** : un système de l'Organisme ne peut ni lire les quarts (a, e) ni écrire (c, d) en tant qu'application. **Aucun solde de banque** dans le produit | **Écart majeur** (E-TN-7, P-TN-17) | **AD-06** |

## 3. La convention jointe (addenda 4) contre le moteur de règles

La réponse 2 de l'addenda 6 renvoie les règles au **paramétrage** ; l'addenda 4 en donne enfin des valeurs. Chaque
article se traduit en règle sur un fait existant, ou révèle un fait manquant.

| Article | Règle | Contexte et fait | État |
|---|---|---|---|
| 14.01 | Quarts de 7,75 h, 9,5 h ou 11,25 h | `DISPONIBILITE.dureeMinutes`, natures de quart | ✅ exprimable |
| 14.06 | Au moins **10 h** entre deux quarts | `ATTRIBUTION.reposHeures`, `ECHANGE.reposHeures` | ✅ |
| 14.13, 15.11 | **8 h** au changement de plage | `reposHeures` (règle à portée du changement de plage : non distinguée) | 🟡 seuil unique de repos |
| 14.14 | 15 min rémunérées avant le quart | `NATURES_QUART.presencePrealableMinutes` | ✅ |
| 14.15 | Échange interdit s'il fait travailler **deux plages consécutives** (sauf quarts de 7,75 h) | `ECHANGE.contigu` | ✅ |
| 14.16 | Échange avec le **consentement du supérieur** | `ECHANGE` → `EXIGER_APPROBATION` | ✅ |
| 14.17 | Un quart déjà échangé ne s'échange plus | — | ⬜ fait absent (hors des 16 questions ; noté) |
| 16.02, 16.07 | Au plus **16 h par 24 h** | — | ⛔ fait absent → **AD-01** |
| 15.12, 14.11 | Temps partiel : 77,5 h par **période de paie** de 14 jours, plafond 85 h, heures supplémentaires au-delà de **80 h** | — | ⛔ seuil de période de paie absent → **AD-03** |
| 16.01, 16.09 | Heures supplémentaires au-delà de la journée ou de la semaine régulière, à 150 % | `Parametres.seuilJourHeures`, `seuilSemaineHeures` ; majoration par type d'heure | ✅ (organisme) / ⛔ par convention → **AD-03** |
| 16.06 | Férié travaillé à 200 % | `HEURES_SUPPLEMENTAIRES.jourFerie`, types d'heures | ✅ |
| 14.10 | Choix annuel des patrons par ancienneté ; qui **néglige** reçoit son patron ou le plus proche ; qui **ne choisit aucun** patron devient temps partiel (liste de rappel) | AGPH : tour de rôle, attribution d'office | ⛔ renonciation et traitement du délai → **AD-05** |
| 15.04 | Disponibilité minimale : 4 jours par 14 jours, une fin de semaine sur deux, un vendredi sur deux | `DISPONIBILITE.quartsParSemaine`, `finsDeSemaine` | 🟡 le « vendredi sur deux » n'a pas de fait |
| 15.06 | Disponibilité modifiable 13 fois par an, 21 jours d'avance | — | ⬜ compteur absent (noté) |
| 15.10, 16.02, 16.03 | Offre **par ancienneté et à tour de rôle**, **10 minutes** pour répondre, un refus compte comme fait | Offre simultanée aux admissibles (`GestionnaireComblementAbsences.offrir`) | ⛔ → **AD-07** (story S-05 du fichier 08, confirmée par la convention) |
| 15.13 à 15.16 | Assignations de 90 jours et plus, 14 jours et plus, moins de 14 jours : trois processus selon la durée | Rejoint la réponse 7 de l'addenda 7 | → **AD-02** (seuil par code) |

**AD-08** (22 novembre) pose ce paramétrage par les gestes de l'application et le joue contre le serveur :
[05_scenarios/AD-08.md](05_scenarios/AD-08.md). Il précise deux lignes de ce tableau : 15.04 s'exprime en partie (une
fin de semaine aux deux semaines pour la liste de rappel ; ni les 4 jours par 14 jours ni le vendredi), et le seuil de
jour et de semaine de 16.01 et 16.09 ne s'exprime pas (journée de 7,75 h, 9,5 h ou 11,25 h selon l'horaire, seuils
entiers). Quatorze écarts y sont listés avec leur article.

## 4. Nos questions restées sans réponse

Les **16 questions** du fichier 07 § 7.3 ont **toutes** reçu une réponse (addenda 7). Les neuf du fichier 06 avaient
été tranchées par l'addenda 6. Les questions **M-01 à M-19** du fichier 08 n'ont pas été transmises (période close le
18 novembre) ; certaines sont pourtant éclairées par les addenda :

| # | Sujet | État après les addenda | Conséquence |
|---|---|---|---|
| M-01 | Banques de congés | **Éclairée** (addenda 5) : l'Organisme **transmet les soldes** par l'API | S-01 devient une partie d'AD-06 : recevoir et tenir les soldes reçus, signaler une absence qui les dépasse |
| M-02 | Choix annuel des congés | Sans réponse ; la convention jointe ne contient pas l'article des congés annuels (15.17 n'en traite que le remplacement) | S-02 reste une interprétation à ne pas déclarer |
| M-03 | Horaires types | **Éclairée** (art. 14.04 à 14.12) : un horaire type est un **patron d'horaire**, choisi chaque année par ancienneté | C'est le poste de l'AGPH (E-CH-1 à E-CH-5) : **S-03 sans objet** |
| M-04 | Compétences et échéances | Partielle (15.01 e, 15.02 : « exigences normales de la tâche », programme d'orientation complété) | Fait `formationRequiseAcquise` existant ; échéances toujours absentes (S-04 maintenue, non prioritaire) |
| M-05 | Groupes dans la portée | Sans réponse (la convention jointe ne vise que les paramédics) | Interprétation déclarée inchangée |
| M-06 | Application installée | Partielle (addenda 7 Q2 : « appareil cellulaire géolocalisé ») | Application web adaptative sur cellulaire : inchangé |
| M-07, M-08 | Frais SMS, autres demandes | Sans réponse | Interprétations déclarées inchangées |
| M-09 à M-13, M-15, M-17, M-18 | Questions d'atelier | Sans réponse | Ordre du jour des ateliers |
| M-14 | Quota : plafond ou cible | Partielle (addenda 7 Q5 : quota = **niveau cible**) | Notre quota par période, plage et type tient la lecture « cible » |
| M-16 | RAO ou déclaration | Sans réponse (A8 : « besoin de clarifications ») | Atelier |
| M-19 | Offre simultanée ou en cascade | **Éclairée** par la convention (16.02, 16.03, 15.10) : ancienneté, tour de rôle, **10 minutes** | **AD-07** |

Restent aussi sans réponse : A3, A5, A8 et B3, B4 (addenda 7, partie A), et le **lien MCN** d'E-TN-31 (addenda 6,
Q14), absent du PDF comme du texte.

## 5. Décision

Les stories **AD-01 à AD-08** ([02_stories.md](02_stories.md)) ferment les écarts. Les décisions de modélisation sont
dans l'[ADR 0087](../../adr/0087-reponses-26-015-do-faits-seuils-et-optimisation.md). L'état d'avancement fait foi dans
[03_planificateur.md](03_planificateur.md).
