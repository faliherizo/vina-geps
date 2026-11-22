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
