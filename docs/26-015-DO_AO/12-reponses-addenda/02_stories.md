# 26-015-DO — Stories issues des réponses (addenda 1 à 7)

> Série **AD** (addenda). Chaque story nomme sa source exacte et ne compte comme faite que si un essai automatisé tient
> chacun de ses critères d'acceptation : essai Java, scénario BDD (décrit dans [05_scenarios.md](05_scenarios.md) et
> joué contre le serveur réel) et, si l'écran change, vitest et audit d'accessibilité. Décisions :
> [ADR 0087](../../adr/0087-reponses-26-015-do-faits-seuils-et-optimisation.md).
>
> Rappel de l'addenda 6, Q4 : un « Oui » exige une fonction **commercialisée le 26 novembre**. Une story livrée après
> cette date ne change pas la réponse à inscrire ; elle ferme l'écart entre la grille et le besoin.

| Story | Objet | Source | Exigences | Taille | Priorité |
|---|---|---|---|---|---|
| AD-01 | Heures sur 24 h : le second aspect de la contiguïté | Addenda 7 Q8 ; convention 16.02, 16.07 | E-GR-7 (essentielle) | S | 1 |
| AD-02 | Le seuil de durée d'un code d'absence choisit le processus de remplacement | Addenda 7 Q7 ; convention 15.13 à 15.16 | E-CH-6, E-CH-7 (essentielles), P-CH-8, E-GR-4 | S | 1 |
| AD-03 | Heures supplémentaires : seuil de période de paie, seuils par convention | Addenda 7 Q15 ; convention 14.11, 15.12 | E-RF-5 (essentielle) | M | 1 |
| AD-04 | AGPH : proposition d'optimisation du budget d'heures | Addenda 7 Q11 | E-CH-1 (essentielle) | M | 1 |
| AD-05 | AGPH : renoncer à tout poste ; traitement du délai échu paramétrable | Addenda 7 Q12, Q13 ; convention 14.10 | P-CH-2, P-CH-4, P-CH-7 | M | 2 |
| AD-06 | API d'échange pour les systèmes internes, soldes des banques compris | Addenda 5 Q1 ; addenda 7 A8 ; M-01 (ex-S-01) | E-TN-7 (essentielle), P-TN-17, E-RF-12 | L | 2 |
| AD-07 | Offre d'un quart par ancienneté et à tour de rôle, avec délai de réponse | Convention 15.10, 16.02, 16.03 ; M-19 (ex-S-05) | P-GH-3, P-GN-1 | L | 3 |
| AD-08 | Paramétrage de démonstration de la convention SP-CSN | Addenda 4 Q1 ; addenda 6 Q2 (démontrer le paramétrage) | E-GN-12, E-CH-1 | S | 2 (après AD-01, AD-03) |
| AD-09 | Compétences et échéances ; inadmissibilité selon une règle ; relevé des échéances | Addenda 8, M-04 (ex-S-04) | E-GN-11, E-GN-16 | M | 1 |
| AD-10 | Choix annuel des congés par formulaire, paramétrable par groupe et selon la convention | Addenda 8, M-02 (ex-S-02) | Cahier 2.1 | L | 5 |
| AD-11 | Demandes opérationnelles : liste de temps supplémentaire, événements, autres | Addenda 8, M-08 | Cahier 2.1 (formulaires) | M | 3 |
| AD-12 | Appartenance datée à plusieurs groupes ; visibilité paramétrable | Addenda 8, M-09 | E-GN-7, E-GN-8 | L | 6 |
| AD-13 | Journal des consultations, activable par l'organisme | Addenda 8, M-10 | E-GN-9 | M | 4 |
| AD-14 | Relevés « horaires » et « budget en heures » | Addenda 8, M-18 | E-RF-7, E-RF-8 | S | 2 |

Stories du fichier 08 : **S-01** absorbée par AD-06 ; **S-02** maintenue, non déclarée ; **S-03** sans objet (un
horaire type est un patron d'horaire, c'est-à-dire le poste de l'AGPH) ; **S-04** maintenue, non prioritaire ;
**S-05** devient AD-07.

---

## AD-01 — Heures sur 24 h : le second aspect de la contiguïté

**Pour** que la validation de contiguïté vise les deux aspects que nomme l'addenda 7 (Q8). **En tant que**
planificatrice, **je veux** une règle qui refuse un échange ou une attribution qui ferait travailler plus de N heures
sur une période de 24 heures.

Critères d'acceptation :
1. Un fait `heures24h` (nombre) s'ajoute aux contextes `ECHANGE` et `ATTRIBUTION` : le **maximum** d'heures planifiées
   de la personne sur toute fenêtre de 24 heures qui touche le quart reçu, ce quart compris.
2. Le calcul porte sur les créneaux des quarts : les 15 minutes de présence préalable n'y entrent pas (addenda 7 Q8).
3. Un quart de nuit qui déborde sur le lendemain compte pour ses heures réelles dans chaque fenêtre.
4. Le chevauchement reste un refus d'office, avant toute règle (premier aspect).
5. Une règle `ECHANGE` `heures24h > 16 → BLOQUER` (art. 16.02) refuse l'échange en le motivant pour la personne
   concernée ; la même règle sous un autre seuil, à portée d'une convention ou d'un statut, ne vise que ses dossiers.
6. Le catalogue des faits (`GET /api/effectifs/regles/faits`) l'expose : l'éditeur de règles le propose sans autre
   développement.

## AD-02 — Le seuil de durée d'un code d'absence choisit le processus

**Pour** que chaque absence suive le processus que fixe l'addenda 7 (Q7). **En tant qu'**administratrice des
référentiels, **je veux** régler, par code d'absence, la durée à partir de laquelle une absence planifiée ouvre un
remplacement long terme.

Critères d'acceptation :
1. Le code d'absence reçoit l'attribut facultatif `seuilJoursLongTerme` (nombre entier de 1 à 365).
2. Avec un seuil, la voie se décide par la **durée** de l'absence : au moins le seuil → remplacement long terme ;
   sous le seuil → comblement quart par quart (gestion régulière). Sans seuil, la catégorie de durée du code décide,
   comme avant (aucune donnée existante ne change de comportement).
3. Une place dont la **période est en cours** (période publiée dont le premier jour est passé) se comble en gestion
   régulière, même pour une absence longue : l'addenda réserve le remplacement long terme aux périodes à venir.
4. Le bilan du comblement compte les places réservées et les places comblées, et le journal le dit.
5. Le seuil est versionné et daté comme toute valeur de référentiel : jamais rétroactif.

## AD-03 — Heures supplémentaires : période de paie et seuils par convention

**Pour** que l'évaluation automatique couvre les trois seuils de l'addenda 7 (Q15), réglés par groupe et par
convention. **En tant que** responsable de la paie, **je veux** régler un seuil par période de paie, et des seuils
propres à une convention collective.

Critères d'acceptation :
1. Les paramètres de l'organisme gagnent `seuilPeriodePaieHeures` (0 = aucun, jusqu'à 744). Une feuille de temps
   couvre **une période de paie** (sa préparation l'exige déjà) : le seuil s'applique au total de la feuille.
2. L'excédent se compte dans cet ordre, une minute n'étant jamais comptée deux fois : jour, puis semaine sur le reste,
   puis période de paie sur le reste. Une période de paie à 80 h avec des semaines de 40 h donne des heures
   supplémentaires dès qu'une semaine dépasse 40 h, et dès que la période dépasse 80 h.
3. Une **convention** peut porter ses propres seuils (jour, semaine, période de paie) ; une feuille se calcule avec les
   seuils de la convention de sa ressource, sinon avec ceux de l'organisme.
4. Le contexte de règles `HEURES_SUPPLEMENTAIRES` expose `heuresPeriodePaie`.
5. Le schéma évolue par un **nouveau** changeset Liquibase, avec son script de retour et la table dans `SchemaEssai`.
6. L'écran des paramètres du temps montre et règle les nouveaux seuils (libellés liés, erreurs annoncées).

## AD-04 — AGPH : proposition d'optimisation du budget d'heures

**Pour** répondre à l'addenda 7 (Q11) : l'optimisation des budgets est un **calcul proposé par la solution**, en
heures. **En tant que** service de planification, **je veux** que la solution me propose, pour une campagne AGPH, les
postes à offrir qui couvrent le mieux le besoin sans dépasser le budget d'heures hebdomadaires.

Critères d'acceptation :
1. `GET /api/effectifs/agph/campagnes/{id}/optimisation` rend une **proposition** : les postes retenus, leurs heures
   hebdomadaires, le total, le budget, l'écart, et pour chaque poste écarté son **motif** (« dépasse le budget »,
   « aucune disponibilité déposée ne le couvre »).
2. Un poste n'est retenu que si au moins une semaine type déposée le couvre ; parmi ces postes, la proposition
   **maximise les heures couvertes** sous le budget (calcul exact), puis préfère, à heures égales, les postes qui ont
   le plus de candidats.
3. Sans budget fixé, la réponse le dit (409 nommé) : aucune proposition sur une donnée qui n'existe pas.
4. La proposition ne modifie rien : le service de planification décide, et la décision reste la sienne (retrait des
   postes par les gestes existants).
5. L'écran de la campagne montre la proposition dans un tableau légendé, avec le total et l'écart au budget.

## AD-05 — AGPH : renoncer ; traitement du délai échu paramétrable

**Pour** suivre l'addenda 7 (Q12, Q13) et l'art. 14.10. **En tant qu'**employé, **je veux** pouvoir déclarer que je
ne prends aucun poste ; **en tant que** service de planification, **je veux** choisir ce qui arrive au rang dont le
délai expire.

Critères d'acceptation :
1. La campagne porte `traitementDelaiEchu` : `ATTRIBUER` (le premier poste admissible, défaut actuel) ou `PASSER`.
2. À son tour, l'employé peut **renoncer** : son rang passe à `RENONCE`, le tour va au suivant, et le journal le dit.
3. À la clôture, l'employé qui a renoncé, ou qui est passé sans poste, reçoit le statut **temps partiel occasionnel**
   (P-CH-7) ; le changement de statut est journalisé (donc relayé par le connecteur ESB).
4. Hors de son tour, renoncer est refusé (409 nommé).
5. Le schéma évolue par un nouveau changeset, avec retour et `SchemaEssai`.

## AD-06 — API d'échange pour les systèmes internes

**Pour** répondre à l'addenda 5 : l'Organisme alimente RAO, SAAV, PFT et BD Corpo **lui-même**, par notre API.

Critères d'acceptation :
1. Une application de l'Organisme obtient un jeton `client_credentials` (`/api/oauth/token`, existant) ; les routes
   `/api/echanges/v1/**` l'**exigent** et refusent un jeton de session (et inversement).
2. Lire : quarts planifiés et leurs correctifs depuis un curseur ; relevés de présence **validés**.
3. Écrire : employés et mises à jour ; **soldes des banques** de vacances et de congés ; absence déclarée aux
   opérations sur un quart réel.
4. Une absence qui dépasse le solde reçu est signalée par une règle (fait `soldeBanqueHeures` rétabli au catalogue).
5. Les modifications de quarts sont poussées en quasi temps réel (relais existant) ; l'OpenAPI documente tout.
6. Essais de contrat et d'isolation par organisme.

## AD-07 — Offre par ancienneté et à tour de rôle

**Pour** suivre la convention SP-CSN : « offrir aux personnes salariées disponibles par ancienneté et à tour de
rôle, de façon à les répartir équitablement » (16.02), « dix (10) minutes pour accepter » sauf dans les deux
prochaines heures (15.10, 16.02), « à défaut de réponse dans le délai, réputée avoir refusé » (16.02), et « chaque
fois que la personne salariée refuse, elle est considérée avoir effectué les heures supplémentaires offertes »
(16.03). **En tant que** service de planification, **je veux** que le quart libéré par une absence soit offert un
rang à la fois, dans cet ordre, avec ce délai ; **en tant qu'**employé, **je veux** accepter ou refuser le quart qui
m'est offert. Décision : [ADR 0089](../../adr/0089-offre-quart-anciennete-tour-de-role.md).

Critères d'acceptation :
1. **Réglage par organisme** (`GET/PUT /api/effectifs/offres/parametres`) : `mode` `SIMULTANEE` (défaut, le
   comportement d'avant) ou `CASCADE` ; `delaiReponseMinutes` (1 à 1 440, défaut 10) ; `seuilUrgenceMinutes`
   (0 à 1 440, défaut 120) ; `fenetreRepartitionJours` (1 à 366, défaut 91). Une valeur hors de son ensemble ou de
   ses bornes est refusée en la nommant (422). Le réglage est journalisé (`offres.parametres`, avant et après).
2. **Ordre** : en mode `CASCADE`, la place d'un quart manuel libérée par une absence ouvre une offre. Les admissibles
   sont ceux de l'offre simultanée (même bassin : disponibilité déposée, aucun chevauchement, pas absent) ; ils sont
   rangés par **heures comptées** croissantes sur la fenêtre de répartition (tour de rôle, 16.02), puis par
   **ancienneté** décroissante, puis par identifiant. L'ordre est figé à l'ouverture.
3. **Un rang à la fois** : seul le rang 1 est avisé, avec l'échéance (maintenant + délai) ; une tâche de
   l'ordonnanceur est planifiée à cette échéance.
4. **Accepter** (`POST /api/effectifs/offres/{id}/accepter`, la personne du rang offert seulement) : la place lui est
   attribuée sous les règles `ATTRIBUTION` (repos, 16 h par 24 h…) ; l'offre est **pourvue**, la tâche annulée. Si
   une règle ou un chevauchement refuse, la personne est **écartée** (motif conservé, heures non comptées) et le rang
   suivant est avisé.
5. **Refuser** (`POST …/refuser`) : le refus est **compté** (16.03) et le rang suivant est avisé aussitôt.
6. **Échéance** : sans réponse à l'échéance, le rang **expire** (réputé refus, compté) et le suivant est avisé — par
   l'ordonnanceur, et par rattrapage au début de toute lecture ou de tout geste sur les offres.
7. **Urgence** (16.02) : un quart qui commence dans le seuil d'urgence est offert à **tous** les rangs à la fois,
   jusqu'à son début ; le premier qui accepte le prend ; le silence n'est pas compté.
8. **Fin** : sans plus personne à qui offrir, l'offre est **épuisée** et la place reste vacante (gestion
   régulière) ; une place pourvue autrement pendant l'offre la **retire**.
9. **Refus nommés** : hors de l'ordre, 404 ; dans l'ordre mais pas offert, 409 qui dit pourquoi (« ce n'est pas encore
   votre tour », « vous avez déjà refusé ce quart », « le délai de réponse est échu », « l'offre de ce quart est
   close »).
10. **Traçabilité** : chaque geste au journal des effectifs (`offre.ouverte`, `.proposee`, `.acceptee`, `.refusee`,
    `.expiree`, `.ecartee`, `.epuisee`, `.retiree`), signé du mécanisme ou de l'employé ; l'encadrement relit l'ordre
    complet d'une absence (`GET /api/effectifs/offres?absenceId=`) ; les avis restent rattachés au comblement.
    L'employé, lui, ne lit que son propre rang (ni le nom, ni les heures, ni l'ancienneté des collègues).
11. **Écrans** : l'employé lit et répond dans « Mon horaire » (« Quarts qui me sont offerts ») ; l'encadrement voit
    l'ordre d'offre dans le comblement d'une absence et règle le mode dans l'écran des absences.
12. **Schéma** : changeset **0024**, script de retour, table dans `SchemaEssai`.

Hors périmètre, noté : le « délai supplémentaire suffisant » d'une personne déjà au travail (15.10, 16.02) reste un
geste du planificateur (il attribue la place lui-même) ; le titre d'emploi et le centre opérationnel de préférence
(16.02) suivent le bassin existant (corps d'emploi de la place, unité).

## AD-08 — Paramétrage de démonstration de la convention SP-CSN

Le jeu de démonstration porte les règles de la convention jointe (repos 10 h, 16 h sur 24 h, échange avec
approbation du supérieur, deux plages consécutives, seuils d'heures supplémentaires de la période de paie), chacune
citant son article dans son message : la démonstration de l'addenda 6 (Q2) montre une règle réelle, pas une règle
d'essai.

Livré le 22 novembre, sans code serveur : paramétrage, semeur, scénarios et écarts dans
[05_scenarios/AD-08.md](05_scenarios/AD-08.md). Le « vendredi sur deux » de 15.04, l'exception des quarts de 7,75 h de
14.15, les 8 h au changement de plage (14.13) et la journée régulière de 16.01 ne s'expriment pas : ils sont listés
comme écarts, pas contournés.

---

## AD-09 — Compétences et échéances (addenda 8, M-04)

**Pour** qu'une personne dont une compétence exigée est échue ne soit pas placée sur un quart. **En tant que**
planificatrice, **je veux** entrer les compétences de chacun avec leur échéance, que la règle de l'organisme décide de
l'admissibilité, et voir ce qui échoit.

Défaut fermé au passage : le fait `formationRequiseAcquise` était **fixé à `true`** (`GestionnairePlaces`). Une règle
écrite sur lui ne refusait jamais personne.

Critères d'acceptation :
1. Un référentiel `competences` déclare chaque compétence et les corps d'emploi qui l'exigent (attribut `corps`, vide =
   suivie sans être exigée).
2. L'encadrement de l'unité (périmètre `GESTION`) pose, renouvelle ou retire la compétence d'une personne : date
   d'obtention exigée, échéance facultative et postérieure. La personne ne s'atteste pas elle-même. Chaque geste est au
   journal, avec l'avant et l'après.
3. À l'attribution, quel que soit son chemin (manuelle, automatique, offre acceptée), les faits `competencesManquantes`
   et `joursAvantEcheance` se calculent **au jour du quart** ; `formationRequiseAcquise` en découle.
4. La règle de l'organisme décide : `competencesManquantes > 0 → BLOQUER` refuse en nommant le motif ; une compétence
   échue la veille du quart bloque ; renouvelée, elle débloque.
5. Le relevé liste les compétences échues ou à échoir dans un horizon (60 jours par défaut, 0 à 3 650), la plus pressante
   d'abord, limité au périmètre de l'acteur ; il s'exporte en CSV.
6. L'écran « Ressources » ouvre la fiche des compétences d'une personne et montre le relevé ; WCAG 2.2 AA.

Preuves : `CompetencesTest`, `ControleurPlanificationEffectifsHttpTest.competences`, changeset 0027 (`SchemaLiquibaseTest`,
`RetourArriereSchemaTest`), BDD `02-quarts-et-attribution.feature` (@M-04), vitest `competences.test.tsx`, audit
géométrique « Ressources » (fiche ouverte). Scénarios : [05_scenarios/AD-09.md](05_scenarios/AD-09.md).

---

## AD-14 — Relevés « horaires » et « budget en heures » (addenda 8, M-18)

**Pour** que les rapports attendus dès la mise en production existent tous : horaires, taux de comblement, absences,
heures supplémentaires, anomalies de présence **et budget en heures**, en Excel, CSV et PDF.

Critères d'acceptation :
1. Source `effectifs_horaires` (module planification) : une ligne par quart tenu — personne, jour, début et fin
   (`HH:MM`), plage, milieu, nature, heures (un quart de nuit compte ses heures réelles).
2. Source `effectifs_budget` (module AGPH) : par semaine (son lundi dans la période), unité et corps d'emploi, le budget
   hebdomadaire de la campagne AGPH de l'année, les heures de toutes les places, les heures pourvues et l'écart
   planifié moins budget ; sans campagne, budget nul.
3. Les deux sont bornées au périmètre de l'acteur, se regroupent et s'exportent comme les autres relevés.

Preuves : `RelevesEffectifsDomaineTest.horairesEtBudget`, `FaitsRelevesEffectifsHttpTest.horairesEtBudget`.


---

## AD-11 — Demandes opérationnelles et liste de temps supplémentaire (addenda 8, M-08)

**Pour** que le formulaire couvre aussi la liste de temps supplémentaire, les événements, la formation et les autres
demandes opérationnelles. **En tant que** paramédic, **je veux** soumettre une demande datée et la suivre ; **en tant
qu'**encadrement, **je veux** l'accepter ou la refuser avec un motif, et lire la liste de temps supplémentaire d'un jour.

Critères d'acceptation :
1. Le référentiel `types_demande` déclare chaque type avec son comportement (`LISTE_TEMPS_SUPPLEMENTAIRE`, `EVENEMENT`,
   `FORMATION`, `AUTRE`). L'organisme en ajoute sans code.
2. La personne soumet une demande (type, du, au, précisions ≤ 500 caractères ; « au » avant « du » refusé) et peut
   l'annuler tant qu'elle est en attente.
3. L'encadrement de l'unité (périmètre `GESTION`) accepte ou refuse ; un refus exige un motif. Une demande décidée ne
   se redécide pas.
4. La liste de temps supplémentaire d'un jour réunit les inscriptions **acceptées** qui le couvrent, par ancienneté
   décroissante, dans le périmètre de l'acteur.
5. Écrans « Mes demandes » (espace personnel) et « Demandes opérationnelles » (gestion) ; WCAG 2.2 AA.

Preuves : `DemandesOperationnellesTest`, `ControleurDemandesEffectifsHttpTest`, changeset 0028, vitest
`addenda-08-ecrans.test.tsx`, audit géométrique « Mes demandes » et « Demandes opérationnelles », e2e déployé
`deploiement-addenda-08.spec.ts` (lecture, et gestes soumettre, annuler, refuser).

---

## AD-13 — Journal des consultations (addenda 8, M-10)

**Pour** que la **consultation** des dossiers et des horaires puisse être tracée, au choix de l'organisme.

Critères d'acceptation :
1. Un réglage par organisme, `consultations`, désactivé par défaut ; seule l'administration (`CONFIGURER`) le change.
2. Activé, toute lecture réussie d'un dossier (liste des ressources, compétences, rattachements) ou d'un horaire
   (quarts, pointages, équipages d'une période, disponibilités) s'inscrit au journal des actions : acteur, action
   `consultation`, type d'objet et chemin lu. Une lecture refusée ou une écriture n'y entre pas.
3. Le réglage se pose depuis « Journal des décisions » ; WCAG 2.2 AA.

Preuves : `ConsultationsTest`, `ControleurReglagesJournalHttpTest`, changeset 0029, vitest `addenda-08-ecrans.test.tsx`,
e2e déployé (réglage activé au semis).

---

## AD-10 — Choix annuel des congés (addenda 8, M-02)

**Pour** que le choix des congés annuels se fasse par formulaire et s'octroie par ancienneté, avec un plafond
d'absents par semaine paramétrable par groupe.

Critères d'acceptation :
1. Une campagne vise une unité et un corps d'emploi, une année, une plage de dates, un code d'absence, un plafond
   d'absents par semaine et un maximum de semaines par personne. États : brouillon → choix ouverts → choix fermés →
   octroyée ; aucun retour en arrière.
2. Pendant les choix ouverts, la personne visée classe ses semaines (lundis de la plage, sans doublon) ; elle
   remplace son classement tant que les choix sont ouverts.
3. L'octroi, choix fermés, sert les personnes par ancienneté décroissante (puis nom), chacune dans l'ordre de ses
   rangs, sans dépasser le plafond d'une semaine ni le maximum de semaines par personne ; un refus porte son motif.
4. Chaque semaine octroyée devient une absence déclarée du lundi au dimanche, jugée par les règles de l'organisme :
   une absence « à approuver » le dit dans le motif.
5. Écrans « Mes congés annuels » et « Congés annuels » (campagnes, résultats) ; WCAG 2.2 AA.

Preuves : `CongesAnnuelsTest`, `ControleurAbsencesEffectifsHttpTest.congesAnnuels`, changeset 0031, vitest
`addenda-08-ecrans.test.tsx`, audit géométrique (résultats ouverts), e2e déployé.

---

## AD-12 — Appartenance datée à d'autres unités (addenda 8, M-09)

**Pour** qu'un paramédic affecté à deux secteurs, ou prêté, figure parmi les ressources de l'autre unité pendant la
durée voulue, sans changer son unité principale.

Critères d'acceptation :
1. L'encadrement rattache une personne à une autre unité, depuis une date, jusqu'à une date facultative ; deux
   rattachements à la même unité ne se chevauchent pas ; l'unité principale est refusée.
2. Pendant sa durée, la personne apparaît dans la liste des ressources et des candidats de l'autre unité, pour qui en
   a le périmètre ; qui voit ses quarts reste décidé par les périmètres (profil et droits) — c'est la visibilité
   paramétrable de M-09.
3. Un rattachement se retire ; chaque geste est au journal.
4. « Ressources » ouvre les autres unités d'une personne ; WCAG 2.2 AA.

Preuves : `RattachementsTest`, `ControleurRattachementsEffectifsHttpTest`, changeset 0030, vitest
`addenda-08-ecrans.test.tsx`, audit géométrique « Ressources » (autres unités ouvertes), e2e déployé.
