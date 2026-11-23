# 8. Questions métier complémentaires — ce que les fichiers 05 et 07 ne couvrent pas

> Établi le **19 novembre 2026**. Point de départ : les 9 questions du fichier 05 (tranchées par
> l'addenda n° 06) et les 16 questions métier du fichier 07 § 7.3. Les questions ci-dessous ne
> reprennent **aucune** d'entre elles, ni les questions de paramétrage X-09 à X-24 du fichier 02, ni
> les données D-01 à D-28 du fichier 04, déjà renvoyées au démarrage et aux ateliers.

## 8.0 Le calendrier : les questions ne peuvent plus partir avant le dépôt

| Repère | Date |
|---|---|
| Dépôt (addenda n° 06) | **jeudi 26 novembre 2026, 14 h 00** |
| Dernier jour pour une question qui doit être prise en compte (Régie 1.04.01 : plus de 5 jours ouvrables avant) | **mercredi 18 novembre 2026** |
| Aujourd'hui | 19 novembre : cinq jours ouvrables avant le dépôt. L'Organisme **peut ignorer** une question reçue maintenant. |

**Conséquence.** La section A ne s'envoie pas comme question. Chaque point devient une
**interprétation déclarée** dans la proposition. On retient la lecture la plus favorable au client,
formulée comme un engagement et jamais comme une condition (Régie 1.15.01 l). Le même texte servira
de question si un nouvel addenda reporte le dépôt. La section B se pose **aux ateliers**, après
l'adjudication (Contrat 2.07) : c'est là que la solution s'ajuste à 100 % aux besoins réels.

Rappel de l'addenda n° 06, Q4 : un « Oui » à un critère essentiel exige une fonction **disponible
dans la version commercialisée le 26 novembre**. Une interprétation déclarée ne peut donc pas
annoncer ce qui n'existe pas encore. Si le produit ne le fait pas, la proposition le range dans les
services professionnels de configuration, ou se tait.

---

## 8.A Besoins du cahier des charges qu'aucune exigence des grilles ne nomme

Le cahier (sections 2.1 et 2.3) décrit des besoins « notamment ». Les grilles servent à
l'évaluation, mais le contrat engage sur **toute** la solution décrite (Contrat 0.03 ; cahier 4,
« solution clé en main »). Ce sont les écarts les plus probables entre ce que le client attend et ce
que la solution fait.

| # | Source | Question neutre (si un addenda rouvre la période) | Notre produit aujourd'hui | Interprétation à déclarer |
|---|---|---|---|---|
| M-01 | Cahier 2.1 « gestion des congés » ; 2.3 « portée : gestion des banques de congés et d'absence » ; 2.3 « BD Corpo […] pour la gestion des banques de congés et d'absences » | La portée de 2.3 inclut la gestion des banques de congés, et la même section en confie la gestion à BD Corpo. La Solution doit-elle **tenir** les soldes des banques, ou les **lire** dans BD Corpo pour valider une demande d'absence ? | **Aucune banque ni aucun solde** dans le contexte `effectifs`. L'ancienneté est lue (`Ressource.anciennete`). Le fait `soldeBanqueHeures` a été retiré du catalogue des règles (roadmap, lot R9). | BD Corpo reste maître des soldes. La Solution les lit et refuse ou signale une absence qui dépasse le solde. **Exige la story S-01.** |
| M-02 | Cahier 2.1 « divers calendriers de congés annuels, droits au congé selon l'ancienneté et l'historique » | Le processus annuel de **choix des congés annuels** (affichage du calendrier, choix par ancienneté, nombre maximal d'absents par période) fait-il partie de la Solution ? Aucune exigence des grilles ne le nomme. | Absent. L'AGPH (E-CH-1) attribue des **postes** ; les absences se déclarent une à une (P-GH-1). | Le choix annuel des congés se mène dans la Solution, par ancienneté, avec un plafond d'absents par période. **Exige la story S-02**, sinon à retirer de la proposition. |
| M-03 | Cahier 2.1 « création d'horaires types, gestion des horaires fixes » | Un **horaire type** est-il un modèle de quarts réutilisable d'une période à l'autre (rotation, cycle de semaines), distinct des postes attribués à l'AGPH ? | Le poste porte ses quarts ; la « semaine type » de l'AGPH est une **disponibilité** de l'employé, pas un modèle d'horaire. Aucun cycle de rotation à vérifier dans le code avant de l'affirmer. | Un horaire type est un cycle de quarts appliqué à un poste, puis reconduit à chaque période. À vérifier ; **story S-03** si absent. |
| M-04 | Cahier 2.1 « remplacements en fonction de critères tels que l'ancienneté et les **compétences** » | Les compétences (permis, accréditations, formations obligatoires) et leur **date d'échéance** sont-elles tenues dans la Solution, ou lues dans BD Corpo ? Une compétence échue rend-elle l'employé inadmissible au quart ? | La ressource porte corps, titre et statut d'emploi, convention et ancienneté. **Aucune compétence**, ni échéance. | Les compétences et leurs échéances sont lues de BD Corpo. Une compétence requise par le poste et échue exclut du bassin, avec son motif (cf. E-GN-14, E-GN-16). **Story S-04.** |
| M-05 | Cahier 2.2 « environ 1000 usagers internes (paramédics, **répartiteurs médicaux d'urgence**, etc.) » ; 2.6 « Paramédics : 1000 » | Les horaires des répartiteurs médicaux d'urgence du CCS et du personnel de la logistique sont-ils dans la portée **dès la mise en production**, ou seulement ceux des paramédics ? | Le moteur est multi-groupes (E-GN-7). Le nombre de conventions à paramétrer change l'effort d'ateliers (P-TN-21) et donc le prix. | Tous les groupes nommés en 2.2 sont dans la portée ; les ateliers se chiffrent par convention (renvoi à X-09). |
| M-06 | Cahier 3 « accessible sur tous types de plateforme (mobile, tablette, ordinateur) » | Une **application mobile installée** (magasins Apple et Google) est-elle attendue, ou une application web adaptative suffit-elle ? | Application web adaptative, testée de 320 px à 1920 px ; notifications poussées par le navigateur. | Application web adaptative, installable sur l'écran d'accueil. Aucune application native promise. |
| M-07 | Cahier 2.1 « notifications par divers moyens (courriels, SMS, etc.) » | Les **frais d'envoi des SMS** sont-ils inclus au prix forfaitaire, ou facturés à l'usage ? | Courriel, SMS et notification poussée existent ; le quota de SMS se compte en segments. | Inclus au prix jusqu'à un volume déclaré au bordereau ; au-delà, refacturé au coût. Hypothèse explicite, non conditionnelle. |
| M-08 | Cahier 2.1 « formulaires électroniques […] les ressources peuvent soumettre leurs demandes » | Au-delà des absences, disponibilités et échanges, quelles **autres demandes** des employés passent par la Solution (formation, changement de statut, remboursement) ? | Trois demandes : absence, disponibilité, échange de quart. Le gabarit de types de demande est paramétrable. | Les demandes liées aux horaires et au temps. Les autres demandes RH restent dans BD Corpo. |

---

## 8.B Exigences des grilles dont le sens reste ouvert — à poser aux ateliers

Ce sont les points où deux lectures honnêtes produisent deux comportements différents. Ils
n'empêchent pas un « Oui », car le produit fait déjà les deux ou se règle. Mais la réponse décide
du **paramétrage livré**. Moment : **[ATELIER]**, sauf mention contraire.

| # | Exigence | Question | Ce que fait le produit | Effet d'une réponse différente |
|---|---|---|---|---|
| M-09 | E-GN-7, E-GN-8 | Un employé peut-il appartenir à **plusieurs groupes organisationnels** à la fois (paramédic affecté à deux secteurs, prêt temporaire) ? Le gestionnaire d'un groupe voit-il alors les quarts de l'autre ? | Périmètres par unité ; une ressource a une unité. | Rattachement multiple daté : changement de modèle de la ressource. |
| M-10 | E-GN-9 | « Toute **interaction** » inclut-elle les **consultations** (qui a ouvert un horaire, un dossier), ou seulement les actions qui modifient ? | Journal des actions et décisions qui modifient ; la trace des simples consultations reste à vérifier. | Journaliser les lectures : volume ×10, rétention et EFVP (renvoi X-16). |
| M-11 | E-GN-14 | L'exclusion du bassin pour disponibilités non conformes vaut-elle pour **toute la période**, ou seulement pour les quarts touchés par la non-conformité ? | Exclusion évaluée par quart, avec motif. | Exclusion de période : règle à ajouter, sans effet sur le modèle. |
| M-12 | E-CH-9 | Un remplacement long terme qui « demeure non comblé » relève-t-il d'une **décision** du gestionnaire (motif obligatoire ?) ou d'une **règle** (personne admissible, délai échu) ? | Les deux possibles ; la décision manuelle est tracée. | Motif obligatoire : un champ et une règle. |
| M-13 | E-GR-5 | Que recouvrent les **ajustements journaliers** : déplacer l'heure d'un quart, réaffecter une ressource entre équipages, fermer un véhicule, ou tout cela ? | Modification d'heures, réaffectation et quarts ad hoc dans la journée. | Fermer ou ouvrir un véhicule dans la journée : lien avec SAAV (renvoi X-18). |
| M-14 | E-GR-8, E-GR-9 | Un **quota de comblement** est-il un **plafond** (au plus N quarts offerts en heures supplémentaires) ou une **cible** (au moins N quarts comblés), et par quoi se compte-t-il : quarts, heures ou personnes ? | Quota par période, plage horaire et type de quart, en nombre de quarts. | Quota en heures ou en cible : nouveau mode de calcul. |
| M-15 | E-GP-15 | Les **activités** saisies a posteriori sont-elles une liste fermée (formation, tâche connexe, déplacement) distincte des types d'heures, et ont-elles un code de paie ? | Saisie a posteriori de segments typés par type d'heure. | Référentiel « activité » distinct : un référentiel de plus, exporté à la paie. |
| M-16 | E-RF-5, E-RF-13 | Lorsqu'une intervention se termine après la fin du quart, les **heures supplémentaires de fin de quart** se calculent-elles depuis l'horodatage de fin d'affectation transmis par le RAO, ou depuis la déclaration de l'employé ? Laquelle prime en cas d'écart ? | Corroboration du pointage par horodatages externes, écart signalé ; aucune source n'écrase l'autre. | Le RAO fait foi : la source externe remplace la déclaration. Une règle de priorité des sources. |
| M-17 | E-RF-6, E-RF-12 | Les **primes** (soir, nuit, fin de semaine, ancienneté, responsabilité) sont-elles **calculées** par la Solution, ou seulement les heures qualifiées, la paie appliquant les taux ? | Types d'heures distingués ; les montants restent à la paie. | Calcul des primes dans la Solution : règles de rémunération, hors de la portée « horaires ». |
| M-18 | E-RF-7, E-RF-8 | Quels **rapports opérationnels** sont attendus dès la mise en production, et quels formats d'exportation sont « standards » (CSV, Excel, PDF) ? **[DÉMO]** : l'évaluateur peut le demander. | Rapports paramétrables ; exportation CSV, Excel et PDF. | Rapport précis manquant : se construit en atelier. |
| M-19 | P-GH-3 | La notification d'un quart disponible part-elle **à tous les admissibles en même temps**, ou **en cascade** par ordre de priorité, avec un délai de réponse ? Par quel canal ? | Offre **simultanée** à tous les admissibles, canal selon les préférences de l'employé ; aucune cascade dans le contexte `effectifs`. | Cascade avec délai de réponse : **story S-05**. |

---

## 8.C Ce qui en sort pour la proposition

1. **M-01 à M-08** : une phrase par point dans la proposition, au chapitre de la solution, sous
   la forme « la Solution fait X ; la valeur Y se fixe en atelier ». **Ne pas déclarer** M-01 à
   M-04 comme faits tant que les stories S-01 à S-04 ne sont pas en production commerciale.
2. **M-09 à M-19** : ordre du jour du premier cycle d'ateliers (Contrat 2.07). Les réponses
   s'ajoutent au fichier 04 comme données à obtenir.
3. Une réponse qui change une exigence ou un prix se consigne par **avenant** (Contrat 19.05).

## 8.D Stories qui en découlent

Aucune n'est un critère des grilles : elles ne changent pas la note, mais l'écart entre la
solution et le besoin décrit au cahier.

| Story | Objet | Origine | Avant le 26 nov. ? |
|---|---|---|---|
| S-01 | Soldes des banques de congés lus de BD Corpo (port de lecture, simulateur) ; une demande d'absence qui dépasse le solde est refusée ou signalée, avec son motif ; le fait `soldeBanqueHeures` revient au catalogue des règles | M-01 | Non : à déclarer comme configuration d'intégration |
| S-02 | Choix annuel des congés : calendrier publié, tour par ancienneté, plafond d'absents par période et par groupe, résultat converti en absences planifiées | M-02 | Non : à retirer de la proposition s'il n'est pas livré |
| S-03 | Horaire type : cycle de quarts sur N semaines attaché à un poste, reconduit à chaque période (après vérification du code) | M-03 | À vérifier d'abord |
| S-04 | Compétences et échéances par ressource, exigées par le poste ; une compétence échue exclut du bassin avec son motif | M-04 | Non |
| S-05 | Offre d'un quart en cascade : par rang de priorité, délai de réponse, passage au rang suivant | M-19 | Non |
