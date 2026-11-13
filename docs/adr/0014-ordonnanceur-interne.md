# ADR 0014 — Un ordonnanceur interne au processus, plutôt qu'une file de messages

- **Statut** : accepté
- **Date** : 2026-09-11

## Contexte

L'application n'a **aucun déclencheur temporel**. Deux domaines calculent proprement « quoi faire
et quand » — la sollicitation séquentielle des remplacements (`prochainesActions(recherche,
liste, instant)`) et la cédule de paie (`runDuePayrollSchedules(tenantId, asOf)`) — mais
l'application de ces actions n'est exposée que comme une **route**, que rien n'appelle. Une
recherche de remplacement n'avance que si un écran ou un appel externe la pousse
([ARCHITECTURE.md](../ARCHITECTURE.md), « l'ordonnanceur n'existe pas »).

L'appel d'offres 2026-PLAT-GA rend ce manque contractuel. Une absence déclarée cinquante jours à
l'avance (grille 22) doit déclencher la recherche **au bon moment** ; les envois doivent respecter
une **fenêtre horaire** par corps d'emploi (grille 23) et une **cadence** fonction de l'urgence
(grille 8) ; un besoin annulé doit **aviser automatiquement** le suppléant attribué, par courriel
et texto, même la nuit (grille 25). Aucune de ces exigences ne se satisfait sans horloge.

Trois façons de l'obtenir :

1. **Un `cron` externe** qui appelle les routes existantes.
2. **Une file de messages** (Redis, RabbitMQ) avec des travailleurs séparés.
3. **Une boucle interne** au processus API, à intervalle configurable, lisant une table de tâches
   différées.

## Décision

**Option 3 — une boucle interne, une table `taches_differees`, un verrou par locataire.**

L'ordonnanceur ne connaît aucune règle métier. Il lit les tâches échues, appelle le domaine avec
`maintenant` — `avancerRecherche(tenantId, id, maintenant)`, `notifier(...)`,
`synchroniser(...)` — et consigne le résultat. **Le temps reste une entrée** : les essais appellent
les mêmes fonctions avec les instants qu'ils veulent, sans `setTimeout`, comme le domaine le fait
déjà (ADR 0008).

La **cadence** et la **fenêtre d'appel** sont des fonctions pures dans `horaires/cadence.ts`, que
l'ordonnanceur consulte **avant** d'appeler le domaine. Hors fenêtre et non urgent, il **reporte**
la tâche à la prochaine ouverture ; il ne la saute pas, il ne suspend rien. `prochainesActions`
n'est **pas modifiée** : l'ordonnanceur lui passe une liste dont l'intervalle est celui que la
cadence a calculé pour l'urgence du moment. Une liste sans cadence configurée se comporte
exactement comme aujourd'hui — la clinique n'hérite de rien.

## Options écartées

**Le `cron` externe** aurait laissé le déclenchement hors du dépôt : un déploiement qui l'oublie
ne le voit pas, et la faute n'apparaît que le jour où une secrétaire constate qu'aucun suppléant
n'a été appelé. Le contrat exige que la plateforme soit opérationnelle « sans logiciel à ajouter »
côté client ; un cron est un logiciel de plus côté serveur, invisible à tout essai.

**La file de messages** apporte la durabilité et la répartition — pour un volume qui ne les exige
pas. Le CSSDC compte 2 800 employés et 45 établissements ; une rentrée difficile produit quelques
milliers d'événements dans une matinée. Un processus Node.js en absorbe cent fois plus. Le prix
d'une file — un conteneur de plus, une seconde source de vérité sur l'état des tâches, une
sémantique de relivraison à gérer — se paierait à chaque déploiement et à chaque essai, pour une
capacité qu'on n'utiliserait pas. Si un jour l'horizontalité s'impose, `taches_differees` est le
contrat : la boucle interne se remplace par des travailleurs sans toucher au domaine.

## Conséquences

**Ce qui devient possible.** La déclaration anticipée, la cadence par urgence, la fenêtre par corps
d'emploi, l'avis d'annulation nocturne, la synchronisation GRICS périodique, la cédule de paie —
et les rappels d'échéance que le tableau de bord RH se contente aujourd'hui d'afficher.

**Ce qu'il a fallu décider.** *Un seul processus exécute les tâches d'un locataire à la fois* :
le verrou par locataire dans `taches_differees` l'impose, et c'est ce qui rend l'idempotence
tenable — une tâche rejouée après une panne trouve un état cohérent. *Une tâche échouée est
retentée* trois fois, puis marquée `echouee` avec sa dernière erreur, et **visible** : un envoi de
texto qui échoue en silence est une recherche qui tourne à vide.

**Ce qui reste à surveiller.** La boucle vit dans le processus API : un redémarrage la relance,
une tâche en cours au moment de l'arrêt est reprise au tic suivant grâce au statut `en_cours` et
à son horodatage. Ce comportement est couvert par un essai ; il ne l'est pas contre un processus
qui s'arrête **en milieu d'écriture** sans transaction — la règle est donc qu'une tâche fait son
travail **dans une transaction** et marque son statut dans la même.
