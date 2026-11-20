# 26-015-DO — Grille des critères v2 (jointe à l'addenda n° 06)

> Conversion automatique (openpyxl, `rich_text=True`) de `26-015-DO_Grille des critèresv2 (1).xlsx` par `questions/convertir-reponses.py`.
> Le texte barré dans une cellule serait rendu ~~ainsi~~ : **aucune cellule barrée** dans ce classeur.
> L'addenda n° 07 impose cette grille pour répondre (« Veuillez utiliser la grille des critèresv2.xlsx »).

## 1. Différences avec la grille d'origine (annexe 7.00 du devis)

Comparaison avec `exigences-26-015-DO.json` (texte extrait du PDF du devis). Une différence de seule
typographie (apostrophe, espace, point final) n'est pas listée.

| Critère | Grille d'origine | Grille v2 | Remplacement déjà consigné (`addenda-26-015-DO.json`) | Concorde avec v2 ? |
|---|---|---|---|---|
| E-GP-15 | La solution doit permettre a un utilisateur autorisé de saisir des heures et des activitée à postérior | La solution doit permettre a un utilisateur autorisé de saisir des heures et des activitée à postériori | — | — |
| E-TN-9 | L’adjudicataire devra assurer la haute disponibilité et la fiabilité de la Solution pour supporter une exploitation sur une base de 20 heures / 7 jours / 365 jours. | L’adjudicataire devra assurer la haute disponibilité et la fiabilité de la Solution pour supporter une exploitation sur une base de 24 heures / 7 jours / 365 jours. | addenda 06 : L'adjudicataire devra assurer la haute disponibilité et la fiabilité de la Solution pour supporter une exploitation sur une base de 24 heures / 7 jours / 365 jours, avec un taux de disponibilité de 99,7 % (2.4 et 6.01). | **non** : paraphrase, reprendre v2 mot pour mot |
| E-TN-11 | Le fournisseur doit être en mesure d'assurer le support technique de la solution durant les heures ouvrables de l'Organisation, soit de 6h00 le matin à 20h00 le soir, du lundi au vendredi selon le fuseau horaire UTC-05:00. | Le fournisseur doit être en mesure d'assurer le support technique de la solution durant les heures ouvrables de l'Organisation, soit de 5h00 le matin à 23h00 le soir, septs jours sur 7 selon le fuseau horaire UTC-05:00. | addenda 06 : Le fournisseur doit être en mesure d'assurer le support technique de la solution de 5 h 00 à 23 h 00, 7 jours sur 7, 365 jours, selon les délais de réponse et de résolution de la clause 5.02.02 (obligations de résultat). | **non** : paraphrase, reprendre v2 mot pour mot |
| E-TN-19 | La solution doit provenir d'un fournisseur hébergé au QUÉBEC | La solution doit provenir d'un fournisseur qui héberge ses serveurs et ses données au CANADA. | addenda 06 : La solution doit provenir d'un fournisseur qui héberge ses serveurs et ses données au CANADA. | oui, mot pour mot |

**Questions d'en-tête à remplir dans le classeur** (déjà posées par la grille d'origine) :

- 3 - Critères essentiels : « Dans quel pays et/ou province sont héberger les données? »
- 3 - Critères essentiels : « Quel est le nom de l’hébergeur? »

4 critère(s) différent(s) ; 144 critères dans la v2, 144 dans l'origine.

## 3 - Critères essentiels

| # | Famille | Description |
|---|---|---|
| E-GN-1 | Généralité | La solution doit permettre de configurer des quarts de travail nécessitant une ou plusieurs ressources selon le corps d’emploi ou le contexte. |
| E-GN-2 | Généralité | La solution doit permettre de définir le nombre minimal de ressources requises pour qu’un quart de travail soit considéré opérationnel. |
| E-GN-3 | Généralité | La solution doit permettre la création de quarts de travail ponctuels ou ad hoc à des fins opérationnelles particulières. |
| E-GN-4 | Généralité | La solution doit permettre de choisir, pour un quart de travail, un mode d’attribution automatisé ou manuel par un utilisateur autorisé. |
| E-GN-5 | Généralité | La solution doit permettre l’autorisation manuelle d’un congé ayant été refusé par une automatisation avec attribution du code d’absence applicable. |
| E-GN-6 | Généralité | La solution doit appliquer automatiquement les règles de comblement à la suite d’une autorisation manuelle. |
| E-GN-7 | Généralité | La solution doit permettre la segmentation complète de la planification et des règles par groupes organisationnels distincts. |
| E-GN-8 | Généralité | La solution doit permettre de restreindre l’accès et les opérations d’un utilisateur à des groupes organisationnels précis. |
| E-GN-9 | Généralité | La solution doit consigner de manière horodatée toute interaction, action, modification ou décision ayant un impact sur les horaires, les quarts, les absences, les paramètres ou les données de temps, incluant l’identification de l’utilisateur ou du mécanisme automatisé à l’origine de l’action. |
| E-GN-10 | Généralité | La solution doit permettre l’audit, l’analyse et la consultation a posteriori de l’ensemble des traces d’exécution et de paramétrage. |
| E-GN-11 | Généralité | Lorsqu’un quart est attribué manuellement par un utilisateur autorisé dans un contexte d’exception opérationnelle, la solution ne doit pas empêcher l’attribution même si certaines règles de priorisation automatisées ne sont pas respectées, tout en assurant la traçabilité complète de la décision. |
| E-GN-12 | Généralité | La solution doit permettre de définir des règles de conformité des disponibilités selon les conventions collectives, le statut d’emploi, le type de poste ou le contexte opérationnel. |
| E-GN-13 | Généralité | La solution doit permettre aux employés d'inscrire leurs disponibilités pour une période donnée. |
| E-GN-14 | Généralité | La solution doit exclure automatiquement une ressource du bassin de candidats à un quart lorsque ses disponibilités ne respectent pas les règles de conformité définies. |
| E-GN-15 | Généralité | La solution doit valider la conformité des disponibilités au moment de leur saisie et empêcher ou signaler toute non-conformité selon des paramètres configurables. |
| E-GN-16 | Généralité | La solution doit informer l’utilisateur des motifs d’inadmissibilité liés à la non-conformité de ses disponibilités. |
| E-GN-17 | Généralité | La solution doit permettre l'identification du milieu physique lié au quart de travail planifié. |
| E-CH-1 | Confection des horaires | La solution doit permettre la réalisation d’un processus annuel structuré de planification et de révision des horaires (AGPH)  pour l'attribution des postes. La solution permet donc aux ressources d'émettre leurs disponibilités (journée/horaire), en respectant les règles prédéfinies de la Convention collective (nombre, durée des quarts et optimisation des budgets) et Conditions particulières ; et paramétrables, <br>selon les besoins, par les personnes identifiées au sein du service de planification des effectifs. |
| E-CH-2 | Confection des horaires | La solution doit permettre la création de quarts de travail et de postes liés aux quarts de travail |
| E-CH-3 | Confection des horaires | La solution doit permettre la modification de quarts de travail et de postes liés aux quarts de travail (1 ou plusieurs postes) |
| E-CH-4 | Confection des horaires | La solution doit permettre la publication de quarts de travail et de postes liés aux quarts de travail |
| E-CH-5 | Confection des horaires | La solution doit permettre d’associer à chaque poste ses paramètres fonctionnels. |
| E-CH-6 | Confection des horaires | La solution doit identifier automatiquement les postes vacants causés par des absences planifiées. |
| E-CH-7 | Confection des horaires | La solution doit permettre l’ouverture d’un processus de remplacements long terme. |
| E-CH-8 | Confection des horaires | La solution doit permettre aux employés admissibles de se manifester pour des remplacements long terme. |
| E-CH-9 | Confection des horaires | La solution doit permettre que certains remplacements demeurent non comblés. |
| E-CH-10 | Confection des horaires | La solution doit permettre le transfert automatique des quarts avec postes non couverts vers la gestion régulière. |
| E-GR-1 | Gestion régulière des horaires | La solution doit permettre la gestion continue des horaires en dehors du processus AGPH. |
| E-GR-2 | Gestion régulière des horaires | La solution doit prendre en charge les quarts non couverts à l’issue de l’AGPH. |
| E-GR-3 | Gestion régulière des horaires | La solution doit évaluer l’impact d’une absence sur le taux de comblement. |
| E-GR-4 | Gestion régulière des horaires | La solution doit identifier automatiquement les quarts à offrir pour comblement. |
| E-GR-5 | Gestion régulière des horaires | La solution doit permettre la gestion d’ajustements journaliers. |
| E-GR-6 | Gestion régulière des horaires | La solution doit permettre la création et la gestion de quarts ad hoc. |
| E-GR-7 | Gestion régulière des horaires | La solution doit permettre la gestion des échanges de quarts incluant les validations d’admissibilité et de contiguïté. |
| E-GR-8 | Gestion régulière des horaires | La solution doit permettre la définition de quotas de comblement distincts par période, plage horaire ou type de quart. |
| E-GR-9 | Gestion régulière des horaires | La solution doit permettre l’ajustement temporaire de paramètres opérationnels incluant les quotas en cours de période sans effet rétroactif. |
| E-GR-10 | Gestion régulière des horaires | La solution doit permettre la gestion des horaires par périodes de planification incluant l’ouverture et la fermeture contrôlées. |
| E-GR-11 | Gestion régulière des horaires | La solution doit permettre le dépôt et l’ajustement des disponibilités par période ouverte. |
| E-GR-12 | Gestion régulière des horaires | La solution doit permettre de configurer si les échanges de quarts entre différentes périodes de planification sont autorisés, restreints ou interdits. |
| E-GR-13 | Gestion régulière des horaires | La solution doit permettre de configurer des quarts de travail non lié à des postes  (Quarts ad hoc). |
| E-GP-1 | Gestion du "punch" | La solution doit permettre aux employés la déclaration des heures de travail en temps réel selon des profils configurables. |
| E-GP-2 | Gestion du "punch" | La solution doit permettre la saisie manuelle des pauses, repas et périodes de repos non payé selon des profils configurables. |
| E-GP-3 | Gestion du "punch" | La solution doit restreindre la modification des heures selon des règles configurables. |
| E-GP-4 | Gestion du "punch" | La solution doit valider les heures saisies selon les règles d’affaires applicables au quart de travail. |
| E-GP-5 | Gestion du "punch" | La solution doit aviser l'utilisateur de la présence d'une anomalie lors de la saisie en cas de non respect d'un ou pluseiurs paramètres identifiés dans les règles d'affaire. |
| E-GP-6 | Gestion du "punch" | La solution doit permettre l’approbation des heures saisies selon des flux configurables. |
| E-GP-7 | Gestion du "punch" | La solution doit permettre la gestion multi-site de la déclaration de présence. |
| E-GP-8 | Gestion du "punch" | La solution doit permettre la comparaison entre heures planifiées et heures déclarées. |
| E-GP-9 | Gestion du "punch" | La solution doit permettre de définir des exigences de présence préalable rémunérée avant un quart de travail. |
| E-GP-10 | Gestion du "punch" | La solution doit permettre de valider que la déclaration de présence est effectuée à partir d’un emplacement conforme au milieu de travail associé au quart. |
| E-GP-11 | Gestion du "punch" | La solution doit supporter un ou plusieurs mécanismes de validation de la présence physique à un milieu de travail identifié au quart de travail. |
| E-GP-12 | Gestion du "punch" | La solution doit permettre de configurer les exigences de validation de présence. |
| E-GP-13 | Gestion du "punch" | La solution doit valider la cohérence entre le lieu de déclaration de présence et le milieu de travail associé au quart, et déclencher une action configurable en cas d’écart. |
| E-GP-14 | Gestion du "punch" | La solution doit consigner les informations de localisation associées aux déclarations de présence et permettre leur audit. |
| E-GP-15 | Gestion du "punch" | La solution doit permettre a un utilisateur autorisé de saisir des heures et des activitée à postériori |
| E-RF-1 | Rapports et formulaires | La solution doit préparer automatiquement la feuille de temps à partir des horaires et affectations. |
| E-RF-2 | Rapports et formulaires | La solution doit permettre la saisie manuelle ou automatisée des données de temps dans la feuille de temps. |
| E-RF-3 | Rapports et formulaires | La solution doit permettre la validation en temps réel des données de feuille de temps. |
| E-RF-4 | Rapports et formulaires | La solution doit permettre l’approbation des feuilles de temps selon des profils autorisés. |
| E-RF-5 | Rapports et formulaires | La solution doit permettre l’évaluation automatique des heures supplémentaires. |
| E-RF-6 | Rapports et formulaires | La solution doit permettre la distinction des types d’heures. |
| E-RF-7 | Rapports et formulaires | La solution doit permettre la production de rapports opérationnels. |
| E-RF-8 | Rapports et formulaires | La solution doit permettre l’exportation des données dans des formats standards. |
| E-RF-9 | Rapports et formulaires | La solution doit détecter automatiquement des anomalies de temps et de présence. |
| E-RF-10 | Rapports et formulaires | La solution doit permettre la configuration de règles de contrôle temporel. |
| E-RF-11 | Rapports et formulaires | La solution doit afficher clairement les anomalies dans la feuille de temps. |
| E-RF-12 | Rapports et formulaires | La solution doit structurer les données de temps afin de permettre leur traitement adéquat par les systèmes de paie. |
| E-RF-13 | Rapports et formulaires | La solution doit permettre de corréler des horodatages provenant de sources multiples (provenance de logiciels tiers) pour la validation de la présence et du temps. |
| E-RF-14 | Rapports et formulaires | La solution doit permettre d'effectuer des modifications aux entrées déjà effectuées selon les profils autorisés. |
| E-TN-1 | Technologie | La solution est un logiciel-service de type infonuagique SaaS (Software as a Service) conforme à la définition SaaS suivante :  La solution SaaS doit être composée d’un seul ensemble de lignes de codes partagées par tous les clients et ne doit nécessiter aucune programmation ni installation sur serveur du client. |
| E-TN-2 | Technologie | La solution authorise (authentification) les utilisateurs via la plateforme Microsoft Entra Id. |
| E-TN-3 | Technologie | La solution SaaS doit doit bénéficier d’une paramétrisation supportant des mises à jour périodiques des fonctionnalités proposées par le fournisseur et rendue disponibles à tous les clients en même temps selon un calendrier d’évolution. Après un processus de validation, les correctifs et l’augmentation du niveau de stabilité et de sécurité peuvent être déployés rapidement. |
| E-TN-4 | Technologie | Le client peut décider d’activer des fonctionnalités nouvelles et des améliorations fonctionnelles. |
| E-TN-5 | Technologie | La solution supporte des profils d'utilisateurs distincts. |
| E-TN-6 | Technologie | Les données opérationnelles, sauvegardées et archivées sont séparées de celles des autres clients. |
| E-TN-7 | Technologie | La solution s'intègre à d'autres applications utilisant un "Entreprise Service Bus" (ESB). |
| E-TN-8 | Technologie | La solution garantit la confidentialité, liaison et disponibilité pour chaque type de transmission (exemples: HTTPS sur Internet, IPSec VPN , MPLS, extension LAN). |
| E-TN-9 | Technologie | L’adjudicataire devra assurer la haute disponibilité et la fiabilité de la Solution pour supporter une exploitation sur une base de 24 heures / 7 jours / 365 jours. |
| E-TN-10 | Technologie | Le support technique pour la solution doit être disponible en français. |
| E-TN-11 | Technologie | Le fournisseur doit être en mesure d'assurer le support technique de la solution durant les heures ouvrables de l'Organisation, soit de 5h00 le matin à 23h00 le soir, septs jours sur 7 selon le fuseau horaire UTC-05:00. |
| E-TN-12 | Technologie | L’adjudicataire s’engage à fournir les procédures de tests d'acceptation pour démontrer que les spécifications de performance et les fonctionnalités décrites dans ce devis sont remplies et respectées. |
| E-TN-13 | Technologie | Une documentation du métamodèle de données est fournie avec la solution. |
| E-TN-14 | Technologie | La solution doit être mise à jour sur une base fréquente et régulière par le fournisseur pour se conformer aux exigences législatives et de cybersécurité. |
| E-TN-15 | Technologie | Le fournisseur doit fournir l'information relative aux sujets des copies de sécurité des données de la solution et de la période de conservation de ces copies de sécurité. |
| E-TN-16 | Technologie | La solution offre des mécanismes de retour en arrière (Rollback). Un maximum de 24 heures est requis. |
| E-TN-17 | Technologie | Les fonctionnalités transactionnelles de base doivent s'exécuter à l'intérieur de 2 secondes. |
| E-TN-18 | Technologie | Le fournisseur doit prévoir un environnement de formation pour les utilisateurs de l'Organisation. |
| E-TN-19 | Technologie | La solution doit provenir d'un fournisseur qui héberge ses serveurs et ses données au CANADA. |
| E-TN-20 | Technologie | La solution offre des interfaces de communication Open-API version 3 ou supérieure afin de partager les données qu'elle contient avec le reste du parc applicatif d’Urgences-santé. |
| E-TN-21 | Technologie | La solution doit avoir une interface en français. |
| E-TN-22 | Technologie | La solution prend en charge un rendu adaptatif sur les ordinateurs de bureau, les ordinateurs portables et les appareils mobiles suivants ainsi que sur les dernières versions des principaux navigateurs web Google Chrome et Microsoft Edge. |
| E-TN-23 | Technologie | La solution offre une protection contre le déni de service (DDoS). |
| E-TN-24 | Technologie | La solution fournit une détection d'intrusion. |
| E-TN-25 | Technologie | Les services professionnels sont disponibles en français. |
| E-TN-26 | Technologie | La documentation et les contenus de formation sont disponibles en français. |
| E-TN-27 | Technologie | La documentation destinée aux administrateurs utilisant la console d'administration, des flux d'affaires, des gabarits de messages et des rapports est disponible. |
| E-TN-28 | Technologie | L’intégrateur est en mesure de présenter au moins trois (3) projets de référence en configuration de la solution (remplir les 3 fiches de référence) terminés en date de la publication de cet appel d’offre. |
| E-TN-29 | Technologie | L'intégrateur est en mesure de présenter les coordonnées (nom, prénom, numéro de téléphone, courriel, nom de l'entreprise) d'au moins trois (3) clients informés et autorisant une prise de référence par l'équipe d'approvisionnement du client. |
| E-TN-30 | Technologie | L'intégrateur propose un service de soutien aux mises à jour SaaS inclus dans la proposition pour la durée du contrat. |
| E-TN-31 | Technologie | Le fournisseur est responsable du paramétrage de la solution et ce paramétrage sera conforme aux exigences du ministère de la cybersécurité et du numérique documenté dans la Liste de vérification de la conformité de la zone d’accueil du nuage externe avant le déploiement des charges de travail, et ce, de concert avec l'équipe de l'organisation. Le paramétrage de la solution consiste à configurer la solution, selon les besoins, les requis, les exigences fonctionnelles, de sécurité applicative et technologique. Le paramétrage comprend aussi les accès sécurisés à la solution, aux rapports de sécurité ainsi que la sécurité des journaux transmis, sauvegardés et gérés par la solution. |

## 4 - Critères préférentiels 

| # | Famille | Description |
|---|---|---|
| P-GN-1 | Généralités | La solution doit permettre l’attribution automatique d’un quart à la ressource prioritaire selon les règles applicables. |
| P-GN-2 | Généralités | La solution doit valider automatiquement l’admissibilité des absences et les restrictions applicables. |
| P-GN-3 | Généralités | La solution doit accepter automatiquement une absence lorsque l’ensemble des règles applicables sont respectées. |
| P-GH-1 | Gestion des horaires | La solution doit permettre aux employés la déclaration d’absences ponctuelles ou planifiées paramétrable selon le titre d'emploi |
| P-GH-2 | Gestion des horaires | La solution doit valider automatiquement la conformité des absences déclarées. |
| P-GH-3 | Gestion des horaires | La solution doit notifier les ressources admissibles à un quart disponible. |
| P-GH-4 | Gestion des horaires | La solution doit attribuer automatiquement un quart de travail disponible à un employé ayant émis une disponibilité correspondante au quart de travail à la fin d'une période de planification et de récupéraiton des disponibilitées déterminée |
| P-CH-1 | Confection des horaires | La solution doit permettre à un employé admissible de visualiser les postes encore disponibles à son tour de rôle déterminé par les règles de convention collective. |
| P-CH-2 | Confection des horaires | La solution doit permettre à un employé de sélectionner un seul poste dans un délai défini lors de son tour de rôle. |
| P-CH-3 | Confection des horaires | La solution doit empêcher toute sélection de poste non admissible selon les règles applicables. |
| P-CH-4 | Confection des horaires | La solution doit permettre l’attribution automatisée des postes selon le tour de rôle. |
| P-CH-5 | Confection des horaires | La solution doit attribuer le statut temps complet (TC) aux employés ayant obtenu un poste de type temps complet (TC) |
| P-CH-6 | Confection des horaires | La solution doit attribuer le statut temps partiel régulier (TPR) aux employés ayant obtenu un poste de type temps partiel régulier (TPR) |
| P-CH-7 | Confection des horaires | La solution doit attribuer le statut temps partiel aux employés n’ayant pas obtenu de poste. |
| P-CH-8 | Confection des horaires | La solution doit permettre l’application de codes d’absence planifiés de moyen ou long terme. |
| P-CH-9 | Confection des horaires | La solution doit attribuer automatiquement les remplacements long terme selon les règles définies. |
| P-GP-1 | Gestion du "punch" | La solution doit permettre aux employés la déclaration des heures de travail  de manière asynchrone (postériorie) selon des profils configurables |
| P-GP-2 | Gestion du "punch" | La solution doit valider les heures saisies selon les règles d’affaires applicables au quart de travail |
| P-GP-3 | Gestion du "punch" | La solution doit permettre la saisie manuelle de plusieurs segments de temps pour un quart exceptionnel. |
| P-GP-4 | Gestion du "punch" | La solution doit permettre le cumul cohérent de plusieurs saisies associées à un même quart. |
| P-RF-1 | Rapport et formulaires | La solution doit produire des relevés statistiques selon différents paramètres définis par l'organisation |
| P-TN-1 | Technologie | Le fournisseur doit configurer les modules de la solution dans les environnements de formation, de tests et de production. |
| P-TN-2 | Technologie | La solution permet l'intégration à plusieurs annuaires actifs (Tel que: Active Directories). |
| P-TN-3 | Technologie | La solution effectue des tests de reprise après sinistre (Disaster Recovery) avec révision des plans au besoin, a minima une fois par année. |
| P-TN-4 | Technologie | Les environnements de reprise après sinistre offrent le même niveau de service qu'en temps normal. |
| P-TN-5 | Technologie | La solution garantie la récupération des processus transactionnels incomplets au moment où une panne ou une défaillance survient. |
| P-TN-6 | Technologie | La solution utilisera les plages d’entretien pour les mises en place de correctifs hors de la période de disponibilité. |
| P-TN-7 | Technologie | La solution permet de verser ses données dans un entrepôt de données (Data Warehouse) à des fins d'analyse via un protocole sécurisé. |
| P-TN-8 | Technologie | La solution doit permettre d'exporter les résultats de recherche sur demande dans ces différents formats : Excel, PDF, CSV, JSON... |
| P-TN-9 | Technologie | La solution doit permettre de personnaliser l'apparence générale de la solution, en intégrant l'image graphique (logo et couleurs officielles) de l'organisation. |
| P-TN-10 | Technologie | La solution permet de définir des règles d'alertage par courriel lors de certaines opérations par les utilisateurs (ex: téléchargement de données). |
| P-TN-11 | Technologie | La solution permet de restreindre les authentifications des utilisateurs en provenance des seuls pays qu'elle autorisera. |
| P-TN-12 | Technologie | La solution devra avoir une procédure de destruction sécuritaire des données dont la vie utile est terminée. |
| P-TN-13 | Technologie | Le prestataire de services détient une expérience pertinente dans le domaine spécifique au contrat à adjuger, soit trois (3) années, et dans des projets de nature et d'importance similaire réalisés. |
| P-TN-14 | Technologie | Le prestataire de services dispose d'une équipe projet disponible au Québec pour accompagner l'organisation au moment du démarrage du projet et soutenir le projet avec vélocité et une continuité de service. |
| P-TN-15 | Technologie | Le prestatairte de services fournit un plan projet indiquant les meilleures pratiques déployées préalablement dans les projets de référence et pouvant aider l'organisation à se préparer, à se mobiliser et à s'organiser pour respecter les échéances du projet. |
| P-TN-16 | Technologie | Le prestatairte de services indique dans son plan de projet le recours à des méthodes de transfert de connaissances de l'équipe d'intégrateur vers les administrateurs et les usagers formateurs. |
| P-TN-17 | Technologie | Le prestatairte de services inclus dans son plan de projet le développement d'interface de programmation d'applications (IPA) ou (API). |
| P-TN-18 | Technologie | La formation des administrateurs de la plateforme lors de la mise en production est incluse dans la proposition. |
| P-TN-19 | Technologie | Une documentation de formation en français et éditable par le client (guide usagers, aide à la tâche, autre) est incluse dans le plan de projet au moment de la mise en production. |
| P-TN-20 | Technologie | Le prestatairte de services indique une date à partir de laquelle l'équipe projet peut se mobiliser pour débuter le projet. |
| P-TN-21 | Technologie | Le prestatairte de services donne un estimé du nombre d'ateliers compte tenu du contexte de l'organisation décrit dans les documents de l'appel d'offre décrivant le contexte organisationnel et les besoins fonctionnels. |
| P-TN-22 | Technologie | Le prestatairte de services indique dans son plan de projet les outils de gestion de projet, de communication et de collaboration qui pourront être utilisés par l'équipe d'intégration avec l'équipe projet (exemple: Portail de communauté) dans le respect des règles de sécurité et de confidentialité de l'organisation. |
| P-TN-23 | Technologie | Le prestatairte de services fournit dans son plan de projet un estimé de la durée totale en mois pour atteindre la mise en production. |
