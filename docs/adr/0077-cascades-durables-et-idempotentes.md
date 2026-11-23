# ADR 0077 — Une cascade d'alerte avance par deux tâches durables : l'une décide sous verrou, l'autre envoie

- **Statut** : accepté, le 14 novembre 2026.
- **Décideurs** : équipe VINA.
- **Prolonge** : l'ADR 0074 (contexte `alertes`), phase R2-a ; l'ADR 0014 (ordonnanceur partagé).
- **Concerne** :
  - `alertes/domaine/Cascades.java` (`Politique`, `Etape`, `Cascade`, `CritereArret`, `Cible`, `Trace`) ;
  - `alertes/application/commandes/GestionnaireCascades.java` et `GestionnairePolitiques.java` ;
  - `alertes/infrastructure/persistance/EcheancierOrdonnanceur.java` et `DepotCascades.java` ;
  - le port partagé `partage/disponibilite/DisponibiliteDestinatairesPort`, implanté par `effectifs` ;
  - le changeset `0013-alertes-cascades.xml`.
- **Story** : ST-G04 (FX-AO-20260810, critères 15, 16, 17, 19, 25, 32).

## Contexte

Une alerte sans réaction doit être relancée, puis escaladée, et s'arrêter dès qu'une personne prend en charge. Le
devis l'exige « sans perte ni doublon » : un serveur qui redémarre en pleine cascade ne doit ni oublier une étape, ni
la refaire. Un texto critique envoyé deux fois réveille deux fois ; jamais envoyé, il laisse un patient sans
réponse.

Le dépôt a déjà un ordonnanceur durable (ADR 0014). Ses tâches différées ont une échéance, une prise atomique
(`UPDATE … WHERE statut = ?`) et un verrou qui se périme : un processus mort en pleine tâche la laisse reprendre.
Mais il retente **au moins une fois**. Il ne promet pas « exactement une fois ».

## Options considérées

1. **Deux tâches par étape, et l'idempotence portée par l'état de la cascade** (retenue). La tâche `alerte.etape`
   décide dans une transaction : elle n'agit que si la cascade attend encore **cette** étape. Elle fait avancer la
   cascade par un verrou optimiste (`UPDATE … WHERE etape = ? AND etat = ?`), ajoute les personnes escaladées,
   planifie la suite et une tâche `alerte.relance`, tout dans la même transaction. La tâche `alerte.relance` envoie.
2. **Une seule tâche qui décide et envoie.** Si l'envoi précède l'écriture, un arrêt entre les deux fait envoyer
   deux fois. Si l'écriture précède l'envoi, l'envoi est perdu. Aucun ordre n'est sûr.
3. **Une boucle dédiée aux alertes**, à côté de l'ordonnanceur. Elle reproduirait la prise atomique, les verrous,
   les reprises et la surveillance, déjà éprouvés, pour un seul usage.
4. **Des minuteries en mémoire.** Elles disparaissent au redémarrage : c'est précisément la perte à éviter.

## Décision

- **La décision et l'envoi sont deux tâches.** La décision est idempotente par construction : une tâche reprise
  après une panne, ou prise par deux instances, trouve la cascade déjà avancée et ne fait rien. L'envoi est planifié
  dans la même transaction que la décision. Il part donc même si le serveur s'arrête juste après, et ne part qu'une
  fois par étape décidée.
- **Ce qui reste « au moins une fois »** : la publication d'avis elle-même. Une panne entre la publication par le
  fournisseur et le marquage « faite » de la tâche fait republier cette relance. La fenêtre est étroite, et c'est la
  garantie du module des notifications tout entier. On ne la masque pas : elle est écrite ici.
- **Une politique est versionnée.** Une cascade retient la version avec laquelle elle a été armée ; modifier une
  politique ne change pas une cascade en cours.
- **L'arrêt n'attend pas l'échéance.** Une lecture ou une réponse qui atteint le critère arrête la cascade aussitôt
  et annule son étape en attente (`ReactionsAlertes`). L'étape vérifie aussi le critère à son échéance, au cas où.
- **« Prochain disponible »** consulte le calendrier interne par un port partagé. Une personne est indisponible le
  jour (heure de Montréal) d'une absence **acceptée** ou d'une indisponibilité déclarée ; sans ressource
  d'effectifs, elle est disponible.
- **La reprise manuelle** est un geste de l'expéditeur. Elle remet l'étape choisie à maintenant, passe par le même
  verrou optimiste et est tracée à son nom.
- **La trace est en ajout seul** (critère 19) : armement, chaque étape avec les personnes visées, arrêt,
  épuisement, reprise.

## Conséquences

- **Positives** : ni perte ni doublon d'étape, prouvé par les essais ; l'ordonnanceur reste ignorant du métier ; la
  voix (ST-G06) s'ajoutera comme un canal d'étape, sans toucher à l'orchestration.
- **Négatives** : deux tâches par étape au lieu d'une ; la latence d'une relance vaut au plus l'intervalle du tic (60 s
  en production, 3 s en campagne e2e).
- **Essais** : `CascadesTest` (domaine) ; `ControleurCascadesHttpTest`, avec la vraie base, le vrai ordonnanceur et
  une horloge réglée. Il couvre trois étapes arrêtées par une prise en charge, le **redémarrage** avant et après
  l'écriture d'une étape, la **concurrence** (deux exécutions, une seule avance), la reprise, « prochain
  disponible », « toutes lues » et l'arrêt à l'échéance. S'y ajoutent la BDD `13-alertes.feature` et l'e2e contre le
  Java, où l'étape reprise est exécutée par l'ordonnanceur réel.
