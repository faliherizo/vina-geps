# 26-015-DO — Traitement de l'addenda n° 8 (réponses à nos questions M-01 à M-19)

> Établi le **23 novembre 2026** pour l'équipe de soumission. Source : [addenda n° 8](../questions/reponses/26-015-DO_addenda_8.md)
> (23 nov.), qui répond aux 19 questions du [fichier 08](../questions/08-QUESTIONS-METIER-COMPLEMENTAIRES.md) et à la
> question 14 de l'addenda 6. Chaque verdict a été **revérifié dans le code** le 23 novembre : la colonne « Dans le
> produit » cite le symbole lu. Le [traitement du 20 novembre](01_traitement_des_reponses.md) § 4 avait déduit ces
> réponses des addenda 1 à 7 ; celui-ci le remplace pour M-01 à M-19.

## 0. Ce qui change, en six lignes

1. **Dépôt reporté au 2 décembre 2026, 14 h 00**, sur la grille v2 de l'addenda 6. Neuf jours.
2. Rappel de l'addenda 6, Q4 : un « Oui » exige une fonction **commercialisée le jour du dépôt**. Ce qui est livré
   avant le 2 décembre peut donc changer une réponse.
3. **Douze réponses sont tenues par le produit tel qu'il est**, deux par une interprétation déclarée.
4. **Cinq réponses ouvrent un écart** : compétences et échéances (M-04), choix annuel des congés (M-02), demandes
   opérationnelles (M-08), appartenance à plusieurs groupes (M-09), journal des consultations (M-10). S'y ajoute un
   rapport manquant (M-18 : budget en heures, horaires).
5. Les stories **AD-09 à AD-14** les ferment ([02_stories.md](02_stories.md)), par ordre de priorité.
6. **E-TN-31** est réécrit : le fournisseur paramètre la solution conformément au devis, avec l'organisation, accès,
   rapports et journaux de sécurité compris. La liste du MCN n'est pas publique.

## 1. Les 19 réponses, une par une

| # | Réponse d'Urgences-santé | Dans le produit (vérifié le 23 nov.) | Verdict | Action |
|---|---|---|---|---|
| M-01 | Les banques (congés, absences, vacances) sont **lues** dans BD Corpo pour valider une demande | AD-06b : soldes reçus par l'API d'échange (`SoldesBanques`), règle `solde-insuffisant-absence` (`REFUSER`) ; code d'absence relié à sa banque (`banque`, `heuresParJour`) | ✅ Tenue | Aucune |
| M-02 | Le choix des congés annuels et hors saison se fait **par formulaire** ; l'octroi est paramétrable par groupe et selon les règles conventionnées | Absent : aucune campagne de choix des congés. Les absences se déclarent une à une (`GestionnaireAbsencesEffectifs`) | ❌ Écart | **AD-10** |
| M-03 | L'horaire type choisi à l'AGPH est un modèle réutilisable pour **une année** ; pas d'horaire type pour qui n'a pas de poste | Le poste de l'AGPH porte ses quarts et se reconduit à chaque période de la campagne (`AgphCampagne`, `Agph.ModeChoix`) | ✅ Tenue | Aucune (S-03 reste sans objet) |
| M-04 | Les compétences et leurs **échéances sont entrées dans le système** ; une compétence échue rend **inadmissible**, selon une règle paramétrable ; un **rapport** des échéances | Absent : le fait `formationRequiseAcquise` est **fixé à `true`** (`GestionnairePlaces`, ligne 76) ; aucune compétence ni échéance | ❌ Écart, et le fait actuel ment | **AD-09** (priorité 1) |
| M-05 | Configurable par groupe d'employés : paramédics, répartiteurs, logistique et autres, selon le plan de déploiement | Moteur multi-groupes : unités, corps d'emploi, conventions et règles par portée (E-GN-7) | ✅ Tenue | Proposition : tous les groupes nommés, ateliers par convention |
| M-06 | Une application **mobile et web** est **souhaitée** | Application web adaptative, installable (manifeste, travailleur de service, notifications poussées) ; aucune application de magasin dans ce produit | 🟡 Souhait, non exigence | Proposition : application web installable sur l'écran d'accueil ; application de magasin en option |
| M-07 | Les notifications (courriel, poussée, texto) sont **incluses** au prix forfaitaire | Quota de textos par organisme compté en segments (`/api/saas/consommation`) | ✅ Tenue (prix) | **Bordereau** : inclure l'envoi des textos au forfait ; le quota devient un seuil d'alerte, plus une limite facturée |
| M-08 | Le formulaire couvre absences, disponibilités, échanges, **remplacements, fériés, vacances, formation, événements, liste de temps supplémentaire** et autres demandes opérationnelles | Absences (codes VAC, FERM, FORM : vacances, fériés, formation), disponibilités, échanges, offres de quart ✅ ; **liste de temps supplémentaire, événements et autres demandes** : absents | 🟡 Écart partiel | **AD-11** |
| M-09 | Un employé peut appartenir à **plusieurs groupes** ; la visibilité des quarts de l'autre groupe est paramétrable selon le profil | Une ressource a **une** unité (`Ressource.uniteCode`) ; les périmètres sont par unité (`Perimetres`) | ❌ Écart | **AD-12** |
| M-10 | Toute modification est journalisée ; la **consultation** des dossiers et horaires doit **pouvoir** l'être, de façon paramétrable | Journal des décisions et des actions qui modifient (`JournalEffectifs`, `journal_actions`) ; **aucune trace des lectures** | 🟡 Écart sur l'option | **AD-13** |
| M-11 | L'exclusion vise **toute la période** ; la règle est paramétrable | Le dépôt se juge pour la période : un dépôt non conforme exclut de **chaque** quart de la période, avec son motif (`LectureDisponibilites.jugerDepot`) ; règles `DISPONIBILITE` paramétrables | ✅ Tenue | Aucune |
| M-12 | Absences de moyen ou long terme → remplacement long terme, sauf période en cours ; seuil **par code d'absence** | AD-02 : `seuilJoursLongTerme` par code, période en cours exclue | ✅ Tenue | Aucune |
| M-13 | Toute modification au quart (heures, codes…) est journalisée | Journal des quarts et avis `effectifs.quart.modifie` (AD-06c, `JournalAvecAvisDesQuarts`) | ✅ Tenue | Aucune |
| M-14 | Le quota est une **cible**, en **nombre de ressources** au quart ; un ajustement en cours de période ne s'applique **pas rétroactivement** | Quota par unité, plage et type, en places (`Quota`) ; un remplacement ne part que d'aujourd'hui ou plus tard (`GestionnaireQuotas`, refus d'une date passée) | ✅ Tenue | Aucune |
| M-15 | Les saisies a posteriori sont faites par un **profil autorisé** et journalisées ; code ou heures | Saisie a posteriori par la personne : en attente d'approbation ; par l'encadrement de l'unité (périmètre `GESTION`) : approuvée à son nom ; consignée au journal (`GestionnairePointagesEffectifs`) | ✅ Tenue | Aucune |
| M-16 | La **déclaration de l'employé** fait foi ; l'horodatage du RAO valide et signale les écarts en anomalies ; seuils paramétrables | Corroboration : aucune source n'écrase l'autre, l'écart devient une anomalie (`CorroborationTemps`) ; seuils d'HS par convention (AD-03) | ✅ Tenue | Aucune |
| M-17 | La solution **qualifie** les heures et transmet à la paie, qui applique les taux ; règles par groupe et convention | Types d'heures qualifiés, export de paie (`/export-paie`), seuils par convention | ✅ Tenue | Aucune |
| M-18 | Rapports : horaires, taux de comblement, absences, HS, anomalies de présence, **budget en heures** ; formats Excel, CSV, PDF | Relevés `effectifs_places` (comblement), `effectifs_absences`, `effectifs_journees` (HS), `effectifs_pointages` (anomalies) ; XLSX, CSV, PDF ✅ ; **horaires** et **budget en heures** : aucune source | 🟡 Écart partiel | **AD-14** |
| M-19 | Mode paramétrable : **simultané** ou **en cascade** par priorité, avec ou sans délai ; courriel, SMS ou mobile | `OffresQuart.Mode` `SIMULTANEE` / `CASCADE`, délai paramétrable (`delaiReponseMinutes`) ; canaux selon les préférences | ✅ Tenue | Aucune |

**Addenda 6, Q14 — E-TN-31.** Le libellé devient un engagement de paramétrage. Il ne demande aucune fonction nouvelle :
la proposition décrit le paramétrage livré (règles, conventions, profils, accès, rapports et journaux de sécurité), fait
avec l'équipe de l'Organisme avant le déploiement des charges de travail.

## 2. Ce que la proposition déclare

| # | Texte à inscrire | Condition |
|---|---|---|
| M-04 | « Les compétences et leurs échéances sont tenues dans la Solution ; une compétence échue rend la personne inadmissible au quart selon une règle paramétrable ; un rapport liste les échéances. » | **AD-09 livrée avant le 2 décembre**, sinon : « configuration livrée au démarrage » |
| M-02 | « Le choix des congés annuels se fait par formulaire, par ancienneté, avec un plafond d'absents paramétrable par groupe. » | **AD-10 livrée**, sinon services de configuration |
| M-06 | « Application web installable sur cellulaire et tablette, avec notifications ; une application de magasin peut s'ajouter. » | Aucune |
| M-07 | « L'envoi des notifications, textos compris, est inclus au prix forfaitaire. » | Bordereau à ajuster |
| M-08 à M-10, M-18 | Selon la livraison d'AD-11 à AD-14 | Idem |

## 3. Priorités

| Ordre | Story | Pourquoi d'abord |
|---|---|---|
| 1 | **AD-09** Compétences et échéances | Le fait `formationRequiseAcquise` est toujours vrai : une règle d'admissibilité paramétrée sur lui ne refuse jamais personne. C'est un défaut, pas seulement un manque |
| 2 | **AD-14** Rapports horaires et budget en heures | L'évaluateur peut les demander en démonstration (M-18 « [DÉMO] ») ; petit |
| 3 | **AD-11** Demandes opérationnelles | Liste de temps supplémentaire : utilisée tous les jours par les paramédics |
| 4 | **AD-13** Journal des consultations | Option, mais exigée « de façon paramétrable » |
| 5 | **AD-10** Choix annuel des congés | Gros ; processus annuel |
| 6 | **AD-12** Rattachements multiples | Change le modèle de la ressource et les périmètres ; risque le plus élevé |

## 4. Livraison

Les six écarts sont fermés dans le code (détail et critères : [02_stories.md](02_stories.md), AD-09 à AD-14) :
AD-09 compétences (M-04), AD-10 congés annuels (M-02), AD-11 demandes opérationnelles (M-08), AD-12 autres unités
(M-09), AD-13 journal des consultations (M-10), AD-14 relevés horaires et budget (M-18). Les deux organismes de la
démonstration — Urgences-santé et Clincom (FX-AO) — sont semés avec des données qui les montrent, et
`e2e/tests/e2e/deploiement-addenda-08.spec.ts` les constate à l'écran sur chacun, d'après
`e2e/donnees/addenda-08/demonstration.json`. Chaque nouvel écran a sa vidéo d'aide.
