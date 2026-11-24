# ADDENDA #08

**Date :** 23 novembre 2026  
**Appel d’offres :** 26-015-DO  
**Objet :** Système de gestion des effectifs

## Questions/réponses

### Question de l’Addenda 6 qui n’avait pas été répondue

**Question 14 :** L'exigence E-TN-31 renvoie à la Liste de vérification de la conformité de la zone d'accueil du nuage externe du ministère de la Cybersécurité et du Numérique. Ce document peut-il être joint à un addenda pour que tous les soumissionnaires évaluent leur conformité sur la même version ?

**Réponse :**

La liste dont il est question est un document du MCN qui n’est pas public et que les organismes doivent compléter. Cependant le critère a été modifié pour en tenir compte :

Dans la grille des critères essentiels, veuillez remplacer le libellé du critère **E-TN-31** par :

> Le fournisseur est responsable du paramétrage de la solution et ce paramétrage sera conforme aux exigences du Devis technique avant le déploiement des charges de travail, et ce, de concert avec l'équipe de l'organisation. Le paramétrage de la solution consiste à configurer la solution, selon les besoins, les requis, les exigences fonctionnelles, de sécurité applicative et technologique. Le paramétrage comprend aussi les accès sécurisés à la solution, aux rapports de sécurité ainsi que la sécurité des journaux transmis, sauvegardés et gérés par la solution.

## Questions complémentaires et réponses

| # | Source | Question neutre (si un addenda rouvre la période) | Réponses |
|---|---|---|---|
| **M-01** | Cahier 2.1 « gestion des congés » ; 2.3 « portée : gestion des banques de congés et d'absence » ; 2.3 « BD Corpo […] pour la gestion des banques de congés et d'absences » | La portée de 2.3 inclut la gestion des banques de congés, et la même section en confie la gestion à BD Corpo. La Solution doit-elle tenir les soldes des banques, ou les lire dans BD Corpo pour valider une demande d'absence ? | Les banques congés, absences, vacances sont lues dans la BD corpo afin de valider une demande. |
| **M-02** | Cahier 2.1 « divers calendriers de congés annuels, droits au congé selon l'ancienneté et l'historique » | Le processus annuel de choix des congés annuels (affichage du calendrier, choix par ancienneté, nombre maximal d'absents par période) fait-il partie de la Solution ? Aucune exigence des grilles ne le nomme. | Le processus de choix de congés annuels et hors-saison doit se faire via un formulaire et l’octroi est paramétrables par groupe d’employés et selon les règles conventionnées. |
| **M-03** | Cahier 2.1 « création d'horaires types, gestion des horaires fixes » | Un horaire type est-il un modèle de quarts réutilisable d'une période à l'autre (rotation, cycle de semaines), distinct des postes attribués à l'AGPH ? | L'horaire type choisi à l’AGPH est un modèle de quarts réutilisable d'une période à l'autre pour une année. Pour les employés sans postes, il n’y a pas d’horaire type. |
| **M-04** | Cahier 2.1 « remplacements en fonction de critères tels que l'ancienneté et les compétences » | Les compétences (permis, accréditations, formations obligatoires) et leur date d'échéance sont-elles tenues dans la Solution, ou lues dans BD Corpo ? Une compétence échue rend-elle l'employé inadmissible au quart ? | Les compétences et leurs dates d'échéance sont entrées dans le système des horaires. Une compétence échue rend l'employé inadmissible au quart, selon une règle paramétrable. Un rapport sur les dates des compétences échues devrait être disponible. |
| **M-05** | Cahier 2.2 « environ 1000 usagers internes (paramédics, répartiteurs médicaux d'urgence, etc.) » ; 2.6 « Paramédics : 1000 » | Les horaires des répartiteurs médicaux d'urgence du CCS et du personnel de la logistique sont-ils dans la portée dès la mise en production, ou seulement ceux des paramédics ? | La Solution doit être configurable par groupe d'employés. Les paramédics, les répartiteurs médicaux d'urgence, la logistique et autres employés opérationnels selon le plan de déploiement d'Urgences-santé. |
| **M-06** | Cahier 3 « accessible sur tous types de plateforme (mobile, tablette, ordinateur) » | Une application mobile installée (magasins Apple et Google) est-elle attendue, ou une application web adaptative suffit-elle ? | Une application mobile et web est souhaitée. |
| **M-07** | Cahier 2.1 « notifications par divers moyens (courriels, SMS, etc.) » | Les frais d'envoi des SMS sont-ils inclus au prix forfaitaire, ou facturés à l'usage ? | Ce sont des notifications par divers moyens (courriel, push ou texto) donc c’est inclus dans le prix forfaitaire. |
| **M-08** | Cahier 2.1 « formulaires électroniques […] les ressources peuvent soumettre leurs demandes » | Au-delà des absences, disponibilités et échanges, quelles autres demandes des employés passent par la Solution (formation, changement de statut, remboursement) ? | Ce type de formulaire doit inclure les demandes d’absences, de disponibilités, d’échanges, de remplacements, de fériés, de vacances, de formation, d’évènements, à la liste temps supplémentaire et autres demandes opérationnelles. |
| **M-09** | E-GN-7, E-GN-8 | Un employé peut-il appartenir à plusieurs groupes organisationnels à la fois (paramédic affecté à deux secteurs, prêt temporaire) ? Le gestionnaire d'un groupe voit-il alors les quarts de l'autre ? | Oui. La visibilité des quarts de l'autre groupe est paramétrable selon le profil et les droits de l'utilisateur. |
| **M-10** | E-GN-9 | « Toute interaction » inclut-elle les consultations (qui a ouvert un horaire, un dossier), ou seulement les actions qui modifient ? | Toutes les actions qui modifient les données doivent être journalisées (utilisateur, date et heure, objet). La consultation des dossiers et horaires doit aussi pouvoir être journalisée, de façon paramétrable. |
| **M-11** | E-GN-14 | L'exclusion du bassin pour disponibilités non conformes vaut-elle pour toute la période, ou seulement pour les quarts touchés par la non-conformité ? | L'exclusion vise toute la période. La règle doit être paramétrable. |
| **M-12** | E-CH-9 | Un remplacement long terme qui « demeure non comblé » relève-t-il d'une décision du gestionnaire (motif obligatoire ?) ou d'une règle (personne admissible, délai échu) ? | Les absences de moyen ou long terme, identifiées par leurs codes d'absence et planifiées qui rendent un poste vacant relèvent du processus de remplacement long terme à part si la période horaire est en cours. Les absences ponctuelles ou de courte durée se comblent quart par quart en gestion régulière. La solution doit permettre de configurer, par code d'absence, le seuil qui détermine le processus applicable. |
| **M-13** | E-GR-5 | Que recouvrent les ajustements journaliers : déplacer l'heure d'un quart, réaffecter une ressource entre équipages, fermer un véhicule, ou tout cela ? | Toutes modifications au quart. Changement d’heures, de codes, etc. Chaque ajustement est journalisé. |
| **M-14** | E-GR-8, E-GR-9 | Un quota de comblement est-il un plafond (au plus N quarts offerts en heures supplémentaires) ou une cible (au moins N quarts comblés), et par quoi se compte-t-il : quarts, heures ou personnes ? | Le quota est une cible (niveau de comblement cible), configurée par période, plage horaire ou type de quart. Il se compte en nombre de ressources prévu au quart. Un ajustement en cours de période s'applique sans effet rétroactif, après son entrée en vigueur. |
| **M-15** | E-GP-15 | Les activités saisies a posteriori sont-elles une liste fermée (formation, tâche connexe, déplacement) distincte des types d'heures, et ont-elles un code de paie ? | Les saisies a posteriori sont faites par un profil autorisé et journalisées. Ceci peut inclure un code ou des heures. |
| **M-16** | E-RF-5, E-RF-13 | Lorsqu'une intervention se termine après la fin du quart, les heures supplémentaires de fin de quart se calculent-elles depuis l'horodatage de fin d'affectation transmis par le RAO, ou depuis la déclaration de l'employé ? Laquelle prime en cas d'écart ? | La déclaration de l'employé. L'horodatage de la RAO sert à valider la cohérence et à signaler les écarts comme anomalies. Les seuils d'heures supplémentaires sont paramétrables. |
| **M-17** | E-RF-6, E-RF-12 | Les primes (soir, nuit, fin de semaine, ancienneté, responsabilité) sont-elles calculées par la Solution, ou seulement les heures qualifiées, la paie appliquant les taux ? | La Solution qualifie les heures (soir, nuit, fin de semaine, etc.) et transmet les données à la paie (E-RF-12), qui applique les taux. Les règles de qualification sont paramétrables par groupe et selon les règles conventionnées. |
| **M-18** | E-RF-7, E-RF-8 | Quels rapports opérationnels sont attendus dès la mise en production, et quels formats d'exportation sont « standards » (CSV, Excel, PDF) ? [DÉMO] : l'évaluateur peut le demander. | Rapports attendus : horaires, taux de comblement, absences, heures supplémentaires, anomalies de présence et budget en heures, etc. Formats : Excel, CSV et PDF. |
| **M-19** | P-GH-3 | La notification d'un quart disponible part-elle à tous les admissibles en même temps, ou en cascade par ordre de priorité, avec un délai de réponse ? Par quel canal ? | Mode paramétrable : envoi simultané ou en cascade par ordre de priorité conventionné, avec ou sans délai de réponse. Canaux : courriel, SMS ou notification mobile. L'attribution suit les règles d'admissibilité et de priorisation. |

## Report de la date de dépôt des soumissions

<badge>Nouvelle échéance</badge>

**2 décembre 2026 à 14 h 00**

Veuillez utiliser la **grille des critèresv2.xlsx** qui était jointe à l’addenda 6 pour répondre.

## Possibilité de plainte auprès de l’AMP

En ce qui concerne le présent addenda, s’il modifie les documents d’appel d’offres pendant la période débutant DEUX (2) jours avant la date limite de réception des plaintes, soyez avisés qu’il vous est possible de formuler une plainte directement auprès de l’Autorité des marchés publics conformément à l’article 40 de la Loi sur l’Autorité des marchés publics [RLRQ c. A-33.2.1].

Le cas échéant, une telle plainte doit être déposée auprès de l’Autorité des marchés publics au plus tard DEUX (2) jours avant la date limite de réception des soumissions qui est le **2 décembre 2026**.

## Signature du soumissionnaire

- **Nom du soumissionnaire :** ______________________________________
- **Nom de l'officier autorisé :** ____________________________________
- **Titre de l'officier autorisé :** ___________________________________
- **Signature de l'officier autorisé :** ______________________________
- **Date de signature :** ____________________________________________

**Tous les autres termes et conditions demeurent inchangés.**

---

Tout Addenda transmis en conformité avec les présentes devient partie intégrante des Documents d’Appel d’offres. Le SOUMISSIONNAIRE doit prendre connaissance des Addenda. Prière de les signer et de les inclurent à la fin de votre formulaire de soumission.
