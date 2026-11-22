# ADR 0089 — Offre d'un quart par ancienneté et à tour de rôle : un compte d'heures, un ordre figé, l'ordonnanceur et le rattrapage

- **Statut** : accepté, le 22 novembre 2026.
- **Décideurs** : équipe VINA.
- **Concerne** : contexte `effectifs` du serveur Java — `domaine/{OffresQuart, OffreQuart, DepotOffresQuartPort,
  ParametresOffresPort, EcheancesOffresPort}`, `application/commandes/{CascadeOffresQuart, ReponsesOffresQuart,
  GestionnaireParametresOffres, AvisDeComblement, GestionnaireComblementAbsences}`,
  `application/requetes/LectureOffresQuart`, `infrastructure/web/ControleurOffresQuart` ; changeset Liquibase 0024.
- **Story** : AD-07 ([02_stories.md](../26-015-DO_AO/12-reponses-addenda/02_stories.md)).
- **Source** : convention SP-CSN jointe à l'addenda 4, art. 15.10, 16.02, 16.03, 16.04 ; question M-19 (ex-S-05).

## Contexte

Le comblement d'une absence (lot U5) offre un quart manuel libéré **à tous les admissibles en même temps** : chacun
reçoit un avis, et le planificateur attribue ensuite la place à l'un d'eux. La convention demande autre chose :

- 16.02 : offrir « aux personnes salariées disponibles **par ancienneté et à tour de rôle**, de façon à les répartir
  équitablement » ; la personne dispose de **dix minutes** pour accepter, « sauf si les heures supplémentaires doivent
  être effectuées dans les **deux prochaines heures** » ; « à défaut de réponse dans le délai, la personne salariée
  est **réputée avoir refusé** » ;
- 16.03 : « chaque fois que la personne salariée refuse, elle est **considérée avoir effectué** les heures offertes » ;
- 15.10 : le même délai de dix minutes pour le rappel des temps partiels ;
- 16.04 : la compilation des heures supplémentaires se remet au syndicat tous les trois mois.

Trois questions de modélisation se posent : ce qu'est le « tour de rôle », comment le temps avance (une échéance de
dix minutes que personne ne regarde), et où vit l'offre par rapport au comblement existant.

## Décisions

### 1. Le tour de rôle est un **compte d'heures** sur une fenêtre glissante ; l'ancienneté départage

L'ordre d'une offre range les admissibles par heures **comptées** croissantes — acceptées, refusées ou laissées
expirer (16.03), sur les `fenetreRepartitionJours` derniers jours (91 par défaut : le trimestre de 16.04) —, puis par
ancienneté décroissante, puis par identifiant (ordre total, donc déterministe). Un écart par une règle ou une offre
close ne compte pas : la personne n'a ni refusé ni travaillé.

Options écartées :
- *Un pointeur de rotation* (« après Marc vient Théo ») : il ne sait pas qu'un quart de 11,25 h n'en vaut pas un de
  7,75 h, il ne répartit donc pas « équitablement », et il ne dit rien du refus que 16.03 compte comme fait.
- *L'ancienneté seule* : le plus ancien recevrait toutes les offres ; c'est l'ordre d'appel de 16.02, pas la
  répartition qu'il exige.
- *Les heures réellement travaillées* (feuilles de temps) : 16.03 compte justement ce qui n'a pas été travaillé, et
  les feuilles sont approuvées des jours plus tard ; l'ordre d'une offre ne peut pas attendre la paie.

### 2. L'ordre est **figé à l'ouverture** ; seul le rang offert répond

L'offre enregistre, rang par rang, la personne, ses heures comptées et son ancienneté **au moment de l'ouverture**.
L'encadrement relit l'ordre qui a été annoncé ; une réponse donnée ailleurs pendant l'offre ne fait pas repasser les
autres. Accepter attribue la place par le geste existant (`GestionnairePlaces.attribuer`), donc sous les règles
`ATTRIBUTION` (repos, 16 h par 24 h d'AD-01) : une règle qui refuse **écarte** la personne sans la compter, et l'offre
continue.

Option écartée : *recalculer l'ordre à chaque passage* — l'ordre deviendrait invérifiable après coup, et un employé
avisé pourrait perdre son tour sans avoir rien fait.

### 3. Le temps avance par **l'ordonnanceur** et par **rattrapage**

Chaque proposition planifie une tâche différée (`effectifs_offre_echeance`, ADR 0014) à son échéance ; la tâche fait
expirer le silence et avise le rang suivant. Toute lecture et tout geste sur les offres commencent aussi par traiter
les échéances passées (comme le tour de rôle de l'AGPH) : une boucle arrêtée ne laisse jamais une personne accepter
une offre échue, et l'essai rejoue le déroulé à l'identique en avançant une horloge.

Options écartées :
- *Le rattrapage seul* (comme l'AGPH) : le rang suivant ne serait avisé qu'à la prochaine lecture, peut-être des heures
  plus tard ; un quart à combler n'attend pas.
- *L'ordonnanceur seul* : son tic (60 s) rendrait l'échéance floue d'une minute, et une panne de la boucle figerait
  l'offre.

### 4. L'urgence offre **à tous à la fois**, sans compter le silence

Quand le quart commence dans le seuil d'urgence (120 minutes par défaut), « le délai de dix minutes ne s'applique
pas » (16.02) : l'offre est faite à tous les rangs ensemble, jusqu'au début du quart ; le premier qui accepte le prend.
Le silence n'y est pas réputé refus : le délai qui fonde cette présomption ne courait pas.

Option écartée : *une cascade à délai réduit* — le texte supprime le délai, il n'en fixe pas un autre ; un délai
inventé serait une règle que la convention ne contient pas.

### 5. Un **mode** de l'organisme, l'offre simultanée par défaut

`SIMULTANEE` reste le défaut : aucun organisme ne change de comportement sans geste, et un organisme sans convention
d'ancienneté garde l'offre à tous. Le réglage (mode, délai, seuil d'urgence, fenêtre) est une donnée, non une
condition dans le code (addenda 6, Q2 : paramétrer sans développer).

### 6. L'offre est un **agrégat à part**, branché sur le comblement

`GestionnaireComblementAbsences.offrir` reste le point où une place devient offerte ; en mode `CASCADE`, il ouvre une
offre (`CascadeOffresQuart`) au lieu d'aviser tout le monde. L'envoi d'un avis rattaché au comblement est extrait dans
`AvisDeComblement`, partagé par les deux voies : l'écran du comblement lit tous les avis d'une place au même endroit.

## Conséquences

- Nouvelles tables `eff_offres_parametres`, `eff_offres`, `eff_offre_rangs` (changeset 0024, retour
  `db/retour/0024-retour.sql`). Le retour perd l'ordre des offres passées et leur compte d'heures.
- Nouvelles routes `/api/effectifs/offres` : `GET /miennes`, `POST /{id}/accepter`, `POST /{id}/refuser` (socle
  `effectifs`) ; `GET ?absenceId=`, `GET/PUT /parametres` (module `effectifs_absences`, capacité de gestion).
- Le journal des effectifs porte huit événements d'offre ; le compte d'heures de 16.04 se relit dans
  `eff_offre_rangs`.
- Limites : le « délai supplémentaire suffisant » d'une personne déjà au travail (15.10, 16.02) reste un geste du
  planificateur ; le titre d'emploi et le centre opérationnel de préférence suivent le bassin existant (corps de la
  place, unité). L'offre ne naît que du comblement d'une absence : une place vacante créée autrement se comble comme
  avant.
