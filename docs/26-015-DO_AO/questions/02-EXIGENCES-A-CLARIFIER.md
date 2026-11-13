# 2. Exigences du client à clarifier

But : lever les ambiguïtés, contradictions et trous des exigences. Classées par effet : d'abord
celles qui peuvent **éliminer** la soumission ou **changer le prix**, puis celles qui changent la
conception, enfin celles qui ne règlent qu'un paramètre.

Légende d'effet : 🔴 éliminatoire ou prix · 🟠 conception · 🟢 paramétrage.

---

## 2.1 Contradictions entre documents

| # | Effet | Moment | Sources | Question | Notre hypothèse |
|---|---|---|---|---|---|
| X-01 | 🔴 | DÉPÔT | E-TN-9 ; Cahier 2.4 ; Contrat 6.01 | Haute disponibilité « **20 h / 7 j / 365 j** » (E-TN-9) contre SLA de **99,7 % en 24/7** (Cahier, Contrat). Quelle plage de service fait foi ? *(ex-Q-2)* | 24/7 à 99,7 % (le plus exigeant) |
| X-02 | 🔴 | DÉPÔT | E-TN-11 ; Contrat 5.02.02 | Heures de soutien : **6 h–20 h en semaine** (grille, éliminatoire) contre **5 h–23 h, 7 j/7** (contrat). *(ex-Q-5)* | 5 h–23 h 7/7 (le contrat prime, 0.03) |
| X-03 | 🔴 | DÉPÔT | Régie annexe 1.16.03 ; Questionnaire p. 11 | Partie 5 : **20 %** (Régie) ou **25 %** (questionnaire) — total à 105 % ? *(ex-Q-1)* | Grille de la Régie |
| X-04 | 🟠 | DÉMARRAGE | Contrat 2.08 c) ; E-TN-1 | Le banc d'essai prévoit « l'installation [des logiciels] **sur les plateformes de l'Organisme public** » et « les serveurs » fournis par lui, alors que E-TN-1 exige un SaaS **sans aucune installation chez le client**. Que faut-il installer chez vous ? | Rien : accès navigateur à l'environnement de tests SaaS |
| X-05 | 🟠 | DÉMARRAGE | Contrat 4.05, 4.06 ; E-TN-1, E-TN-3 | Le contrat confie l'**installation des mises à jour** à votre service informatique, avec un délai de 6 mois ; un SaaS mono-code les déploie pour tous les clients en même temps. Votre rôle est-il de **valider et activer** (E-TN-4) plutôt qu'installer ? | Vous validez en tests puis activez les fonctionnalités ; nous déployons |
| X-06 | 🟠 | DÉMARRAGE | Contrat 2.06.04 | Accès direct « pour l'année courante et **trois (2)** années additionnelles » : deux ou trois ans ? *(ex-Q-7)* | Trois ans en ligne, deux en archive (prudent) |
| X-07 | 🟠 | DÉMARRAGE | Contrat 6.01.03 ; Cahier 2.4 | Temps total « 24 h × **30 jours** (ou 31) » contre « nombre total de **minutes du mois** » ; février ? | Minutes réelles du mois |
| X-08 | 🟢 | ATELIER | Cahier 2.2 ; Bordereau A | Volumétrie : ~100 administratifs + ~1 000 internes, mais **1 100 licences** au bordereau et « 1000 paramédics » à la section 2.6 sans compter les répartiteurs. Combien de comptes actifs par profil ? | 1 100 comptes nommés |

## 2.2 Règles de gestion (conventions collectives)

| # | Effet | Moment | Sources | Question | Notre hypothèse |
|---|---|---|---|---|---|
| X-09 | 🔴 | DÉMARRAGE | Cahier 1.2 ; E-CH-1 ; E-GN-12 | **Quelles conventions collectives et ententes locales** s'appliquent (paramédics, répartiteurs médicaux d'urgence, personnel de soutien) ? Pouvez-vous fournir les textes et les lettres d'entente ? | Moteur de règles versionné par contexte et portée ; règles à saisir en atelier |
| X-10 | 🔴 | DÉMARRAGE | Contrat 7.01 b) ; Cahier 2.5 | Quelles règles doivent être **paramétrées dès le lot initial**, et lesquelles relèvent plus tard des Services professionnels (banque d'heures) ? | Toutes les règles d'attribution, disponibilités, absences, HS au lot initial |
| X-11 | 🟠 | ATELIER | E-CH-1 | « Nombre, durée des quarts et **optimisation des budgets** » dans l'AGPH : quel indicateur budgétaire la confection doit-elle respecter (heures budgétées, ETC, coût) ? | Contrainte d'heures par unité ; pas de coût |
| X-12 | 🟠 | ATELIER | P-CH-1, P-CH-3 | Qu'est-ce qui rend un poste **non admissible** pour un employé au tour de rôle (titre d'emploi, accréditation, statut, restriction médicale) ? | Règles CHOIX_AGPH par titre d'emploi |
| X-13 | 🟠 | ATELIER | E-GR-10, E-GR-11 | Durée et calendrier des **périodes de planification** régulières (28 jours ? 4 semaines ?) ; délais d'ouverture et de fermeture de la collecte des disponibilités ? | Périodes paramétrables |
| X-14 | 🟢 | ATELIER | E-GN-3, E-GR-6, E-GR-13 | Exemples de **quarts ad hoc** (événement, renfort, formation) : sont-ils rémunérés comme des quarts réguliers ? | Quart ad hoc sans poste, mêmes règles de temps |
| X-15 | 🟢 | ATELIER | E-GP-2, P-GP-3, P-GP-4 | Pauses, repas, repos **non payés** : durées et règles (repas payé si interrompu par un appel ?) | Segments typés payés / non payés |

## 2.3 Présence et renseignements personnels

| # | Effet | Moment | Sources | Question | Notre hypothèse |
|---|---|---|---|---|---|
| X-43 | 🔴 | DÉMO | E-GP-10 à E-GP-14 | Quels **mécanismes de validation de présence** sont acceptables (géorepérage du téléphone, code QR de lieu, réseau, badge, borne) et sur quels **appareils** (personnels ou fournis) ? *(ex-Q-11)* | Distance + QR + réseau, au choix par milieu |
| X-16 | 🟠 | DÉMARRAGE | E-GP-14 ; Contrat 17.07.04 | La localisation consignée est-elle un **renseignement personnel** soumis à une **EFVP** ? Qui la réalise, et quelle durée de rétention ? | Aucune position conservée, verdict seulement (V-28) |
| X-17 | 🟠 | ATELIER | E-GP-7, E-GN-17 | Un paramédic pointe-t-il **à la caserne/au centre** ou **depuis le véhicule** (MDT) ? | Lieu fixe |

## 2.4 Intégrations (écosystème)

| # | Effet | Moment | Sources | Question | Notre hypothèse |
|---|---|---|---|---|---|
| X-18 | 🔴 | DÉMARRAGE | Cahier 2.3 | **Formats et protocoles** d'échange de RAO, SAAV, PFT et BD Corpo (API REST, fichiers, files de messages), sens, fréquence, volumes. Documentation disponible ? *(ex-Q-10)* | Conventions **supposées** derrière un port + simulateur ; adaptateur réel à écrire |
| X-19 | 🔴 | DÉMARRAGE | Cahier 2.3 ; 2.01.01 c) | Quelles interfaces RAO/SAAV sont **dans le périmètre de réalisation** du contrat à forfait (bordereau C) ? | Sept connecteurs prévus |
| X-20 | 🟠 | DÉMARRAGE | Cahier 2.2 | Rôle exact de **Microsoft Graph** « passerelle bidirectionnelle » : calendriers Outlook, Teams, annuaire, présence ? Quelles données dans chaque sens ? | Quarts publiés vers le calendrier ; inscription d'application Entra par vous |
| X-21 | 🟠 | DÉMARRAGE | E-TN-7 | Quel **ESB** (produit, version, protocole) ? Événements attendus ? | Relais du journal vers le bus, par curseur |
| X-22 | 🟠 | DÉMARRAGE | P-TN-7 | **Entrepôt de données** : produit, protocole sécurisé, données et fréquence ? | Relais par lot, HTTPS |
| X-23 | 🟠 | ATELIER | Cahier 2.3 | **BD Corpo** est-elle la source maître des employés, de l'ancienneté et des banques de congés ? Propriété de chaque attribut employé ? | BD Corpo maître ; nous lisons, nous n'écrivons pas les soldes |
| X-24 | 🟠 | DÉMARRAGE | Cahier 3 | **Migration** : quelles données historiques reprendre des systèmes actuels (horaires, soldes, ancienneté) et sur combien d'années ? Stratégie de décommissionnement ? | Reprise des référentiels et soldes d'ouverture seulement |

## 2.5 Exigences techniques

| # | Effet | Moment | Sources | Question | Notre hypothèse |
|---|---|---|---|---|---|
| X-25 | 🔴 | DÉPÔT | E-TN-19 | « Fournisseur **hébergé** au Québec » : l'exigence vise-t-elle les **données** (hébergement à Montréal) ou le **siège** du fournisseur ? | Données et sauvegardes au Québec (région Montréal) |
| X-26 | 🔴 | DÉPÔT | E-TN-31 ; Q-12 | Pouvez-vous transmettre la **Liste de vérification de la conformité de la zone d'accueil du nuage externe** du MCN ? | Paramétrage conforme, preuve à produire |
| X-27 | 🟠 | DÉMO | E-TN-17 | Définition opérationnelle des **2 secondes** : quelles transactions de base, quel percentile (p95 ?), sous quelle charge simultanée ? | Six transactions, p95 < 2 s (document 09) |
| X-28 | 🟠 | DÉMARRAGE | E-TN-16 | **Rollback de 24 h maximum** : retour arrière d'une **version** logicielle, ou **restauration des données** à un point dans les 24 h (RPO) ? | Les deux : déploiement réversible + RPO 15 min |
| X-29 | 🟠 | DÉMARRAGE | E-TN-15 ; P-TN-3, P-TN-4 | **RTO/RPO** attendus et durée de rétention des sauvegardes ? | RTO 1 h, RPO 15 min, 30 sauvegardes complètes |
| X-30 | 🟠 | DÉMARRAGE | E-TN-8 | Liaisons à prévoir : HTTPS Internet seul, ou **IPSec VPN / MPLS** vers votre réseau (pour RAO/SAAV) ? | HTTPS ; VPN site à site si une interface l'exige |
| X-31 | 🟠 | DÉMARRAGE | E-TN-24 | **Détection d'intrusion** : réseau/hôte (IDS/IPS) ou applicative suffit-elle ? Raccordement à **votre SIEM** ? | WAF + détection applicative + export SIEM |
| X-32 | 🟢 | DÉMARRAGE | E-TN-13 | Format attendu du **métamodèle de données** (dictionnaire, diagramme entité-relation, OpenAPI) ? | Dictionnaire généré + OpenAPI |
| X-33 | 🟢 | DÉMARRAGE | E-TN-20 ; Contrat 2.06.02 | Accès aux données « via API **ou par la base de données** » : un accès direct en lecture à la base est-il exigé, ou l'API + entrepôt suffisent ? | API OpenAPI 3 + versement à l'entrepôt |
| X-34 | 🟢 | DÉMARRAGE | E-TN-18 ; Contrat 2.05 | L'environnement **de tests et formation** est-il unique (contrat : deux environnements) ou faut-il distinguer tests et formation ? | Deux environnements : tests/formation et production |
| X-35 | 🟢 | DÉMARRAGE | P-TN-6 ; Cahier 2.4 | Plages de maintenance acceptables (jours, heures) et préavis (Cahier : 3 jours) ? | Annonce 72 h, ≤ 12 h, hors période critique |

## 2.6 Formation et documentation

| # | Effet | Moment | Sources | Question | Notre hypothèse |
|---|---|---|---|---|---|
| X-36 | 🟠 | DÉMARRAGE | Contrat 2.13.02 | **Capsules d'auto-apprentissage** : nombre, durée, format (vidéo, interactif SCORM), plateforme d'hébergement (votre LMS ?) | Vidéos courtes par parcours |
| X-37 | 🟢 | DÉMARRAGE | Contrat 2.13.01 | Six usagers pilotes, « minimalement six séances » : durée d'une séance ? | Séances d'une demi-journée |
| X-38 | 🟢 | ATELIER | P-TN-19 | Documentation « **éditable** par le client » : format (Word, wiki, Markdown) ? | Word + aide en ligne |
| X-39 | 🟢 | DÉMARRAGE | Contrat 3.01.03 | Les **guides d'entretien** (sauvegarde, restauration, épuration) visent-ils vos équipes alors que l'exploitation SaaS est la nôtre ? | Guides d'administration fonctionnelle |

## 2.7 Références et admissibilité de la solution

| # | Effet | Moment | Sources | Question | Notre hypothèse |
|---|---|---|---|---|---|
| X-40 | 🔴 | DÉPÔT | E-TN-28 | Un projet de référence doit-il porter sur **la solution proposée** (même produit), et « **terminé** » signifie-t-il en production ? *(ex-Q-9)* | Même produit, en production |
| X-41 | 🔴 | DÉPÔT | E-TN-28, E-TN-29 | Les **trois contacts** (E-TN-29) doivent-ils correspondre aux **trois projets** (E-TN-28) ? | Non exigé par le texte |
| X-42 | 🟠 | DÉMO | Contrat 2.08 ; E-TN-12 | Fournirez-vous des **scénarios d'essai** pour le banc d'essai, ou attendez-vous nos procédures d'acceptation (E-TN-12) seules ? | Nos procédures, enrichies des vôtres |

## 2.8 Questions nées de la vérification du 8 novembre 2026

| # | Effet | Moment | Sources | Question | Notre hypothèse |
|---|---|---|---|---|---|
| X-44 | 🔴 | DÉMARRAGE | E-TN-19 ; Contrat 17.07.04 | La région AWS **ca-central-1** a ses centres de données à **Montréal** : satisfait-elle « hébergé au Québec », alors que l'hébergeur est une société américaine soumise au *CLOUD Act* ? Exigez-vous un hébergeur **de droit québécois ou canadien** ? | Région de Montréal (ADR 0043) ; chiffrement avec clés gérées par nous |
| X-45 | 🟠 | DÉMARRAGE | E-TN-6 | Les **sauvegardes et archives** doivent-elles être **séparées par organisme** (copie propre à Urgences-santé), ou une sauvegarde de l'instance avec données cloisonnées logiquement suffit-elle ? | Aujourd'hui : instance entière ; une instance dédiée réglerait la question |
| X-46 | 🟠 | DÉMARRAGE | E-TN-6, E-TN-19 | Souhaitez-vous une **instance dédiée** (base et sauvegardes à vous seul) plutôt qu'un établissement d'une instance partagée ? L'écart de prix serait à inscrire au bordereau. | Établissement d'une instance mutualisée |
| X-47 | 🟠 | DÉMARRAGE | P-GH-3 ; Cahier 2.1 « notifications » | Les **textos** d'offre de quart passent par un fournisseur de messagerie : un fournisseur canadien est-il exigé ? Le texto peut-il nommer le quart et le site, ou seulement inviter à se connecter ? | Texto minimal, détail dans l'application |
| X-48 | 🟠 | DÉMARRAGE | Cahier 2.2 | **Microsoft Graph** exige l'inscription d'une application dans votre locataire Entra, avec des permissions accordées par votre administrateur. Qui la crée, et quelles permissions acceptez-vous (calendriers en écriture ? annuaire en lecture ?) | Vous créez l'inscription ; nous fournissons la liste minimale des permissions |
| X-49 | 🟠 | DÉMARRAGE | E-GP-10, E-GP-11 | Les coordonnées et le **rayon** de chaque milieu (caserne, centre) : qui les fournit, et quelle tolérance accepter quand la précision du téléphone est mauvaise (intérieur, sous-sol) ? | Rayon par milieu, tolérance de précision plafonnée |
| X-50 | 🟢 | ATELIER | E-RF-13 | Les **horodatages** de RAO et SAAV sont-ils en heure locale ou UTC, et à quelle fréquence peut-on les tirer ? | UTC, tirage périodique |
