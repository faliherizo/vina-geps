# Trousse de formation — Gestion des effectifs

> AO 26-015-DO, exigence **E-TN-26** (« la documentation et les contenus de formation sont
> disponibles en français ») et préférentielles P-TN-18 et P-TN-19. Document **éditable par
> l'organisme** : il est écrit en Markdown, se convertit en Word (`pandoc TROUSSE-FORMATION.md -o
> trousse.docx`) et chaque module se reprend tel quel comme aide à la tâche.

## Comment s'en servir

- **Environnement** : l'environnement de tests et de formation
  (`infra/terraform/aws/urgences-sante-essai.tfvars`), jamais la production.
- **Données** : l'organisme de démonstration, semé par l'API :
  `node scripts/semer-urgences-sante.cjs --api <adresse>`. Le script imprime les identifiants ;
  les comptes et ce que chacun voit sont décrits dans
  [COMPTES-DEMONSTRATION.md](COMPTES-DEMONSTRATION.md).
- **Aide en ligne** : chaque écran a un bouton « Aide sur cet écran » qui ouvre la section du
  guide intégré correspondant (`Guide & aide › Effectifs`).
- **Référence d'administration** : [GUIDE-ADMINISTRATION-EFFECTIFS.md](GUIDE-ADMINISTRATION-EFFECTIFS.md).

Chaque module donne son public, sa durée, ses objectifs, un exercice pas à pas sur les données de
démonstration, et le résultat qui prouve que l'exercice est réussi. Une **capsule** est un
exercice court (moins de 10 minutes) qu'une personne peut refaire seule.

| Module | Public | Durée | Compte d'exercice |
|---|---|---:|---|
| M1 — Se connecter et s'orienter | Tous | 20 min | `julie.roy` |
| M2 — Mes disponibilités, mes absences, mes échanges | Employés | 45 min | `julie.roy` |
| M3 — Pointer et tenir sa feuille de temps | Employés | 30 min | `ana.cote` |
| M4 — Le comblement du matin | Planification | 60 min | `claire.nadeau` |
| M5 — Périodes, postes, quarts et quotas | Planification | 90 min | `claire.nadeau` |
| M6 — La campagne annuelle (AGPH) | Planification | 60 min | `claire.nadeau` |
| M7 — Approuver les feuilles et produire le lot de paie | Encadrement | 45 min | `claire.nadeau`, `denis.lavoie` |
| M8 — Paramétrer l'organisme | Administrateurs | 2 h | administrateur de l'organisme |
| M9 — Contrôler : journal, exports, rétention | Administrateurs, vérification | 30 min | administrateur de l'organisme |

## M1 — Se connecter et s'orienter

**Objectifs** : se connecter (mot de passe ou annuaire de l'organisme), trouver ses écrans,
ouvrir l'aide d'un écran.

1. Ouvrir la page de connexion, choisir l'organisme, se connecter avec `julie.roy`. Si
   l'organisme a déclaré un annuaire Microsoft Entra ID, le bouton « Se connecter avec
   l'annuaire … » mène à la connexion de l'organisme.
2. Déplier la section du menu « Effectifs » : seuls les écrans permis au rôle apparaissent.
3. Ouvrir « Mon horaire », puis « Aide sur cet écran ».

**Réussi si** : l'horaire de la semaine s'affiche, et l'aide s'ouvre sur la section Effectifs.

## M2 — Mes disponibilités, mes absences, mes échanges

**Objectifs** : déposer ses disponibilités pendant la collecte, comprendre un refus, déclarer
une absence, proposer un échange.

1. **Capsule « déposer »** — « Mes disponibilités » : choisir la période « Prochaine période —
   collecte en cours », déposer des plages pour trois jours. Déposer ensuite une plage non
   conforme : le refus nomme la règle et ce qu'elle exige.
2. **Capsule « absence »** — « Mes absences d'effectifs » : déclarer une absence qui respecte les
   règles ; elle est accordée d'office et l'écran le dit. Déclarer d'avance un code « jour même
   seulement » : le refus dit quel code choisir.
3. **Capsule « échange »** — « Mes échanges » : choisir le quart cédé et le quart voulu de
   `marc.dube`, proposer. La planification le valide ensuite dans « Demandes et échanges » (M4).

**Réussi si** : les dépôts apparaissent dans « Disponibilités de la période » côté planification,
l'absence est accordée, l'échange apparaît dans « Demandes et échanges » en attente de validation.

## M3 — Pointer et tenir sa feuille de temps

1. **Capsule « pointer »** — « Mon pointage » : déclarer l'entrée et la sortie d'un quart du jour.
2. **Capsule « a posteriori »** — « Déclarer un temps » : saisir une journée passée avec une pause
   bornée ; la nature de la période non payée est exigée.
3. « Ma feuille de temps » : lire le tableau « Planifié et déclaré, quart par quart », corriger
   l'anomalie signalée, soumettre.

**Réussi si** : la feuille passe à « Soumise » et n'affiche plus d'anomalie bloquante.

## M4 — Le comblement du matin

1. « Comblement » : lire le taux, les quarts sous le minimum et les quotas manqués.
2. « Absences et comblement » : ouvrir une demande, lire le taux de comblement qu'elle
   laisserait ; refuser, puis **déroger** avec un motif et un code d'absence.
3. Suivre le comblement des places libérées : qui a été retenu, qui a été avisé, par quel canal.
4. « Demandes et échanges » : valider l'échange proposé en M2 ; lire son admissibilité et sa
   contiguïté.
5. « Quarts et équipages » : attribuer une ressource qu'une règle écarte ; lire les avertissements,
   justifier, forcer.

**Réussi si** : la dérogation et l'attribution forcée apparaissent au « Journal des décisions »,
avec l'auteur et le motif.

## M5 — Périodes, postes, quarts et quotas

1. « Périodes » : créer une période, ouvrir sa collecte avec une date de fin.
2. « Postes » : créer un poste avec son cycle, le publier ; modifier deux postes en lot et lire
   les conflits listés avant l'écriture.
3. « Quarts et équipages » : créer un quart ad hoc avec deux corps d'emploi et un minimum
   opérationnel ; choisir le mode d'attribution.
4. « Quotas » : définir un quota, puis le surcharger « à partir de » demain.

**Réussi si** : le quart ad hoc apparaît au comblement, et l'historique du quota montre les deux
valeurs datées.

## M6 — La campagne annuelle (AGPH)

1. « Confection annuelle (AGPH) » : créer la campagne pour l'unité et le corps d'emploi, fixer le
   budget d'heures, ouvrir la collecte.
2. Se connecter en employé, « Mon AGPH » : à son tour de rôle, choisir un poste admissible.
3. Démarrer l'attribution, puis « Clore et poser les statuts » ; ouvrir un remplacement long terme.

**Réussi si** : les statuts TC / TPR / temps partiel sont posés selon les postes obtenus, et les
quarts restés sans titulaire apparaissent au « Comblement ».

## M7 — Approuver les feuilles et produire le lot de paie

1. « Feuilles de temps (console) » : « Préparer les feuilles » pour la semaine écoulée.
2. Ouvrir une feuille soumise, approuver le palier, ou la retourner avec un motif.
3. « Relevés » : choisir le regroupement, afficher, puis **exporter** en Excel et en PDF.
4. « Lot de paie » : produire le lot des feuilles approuvées et noter son empreinte SHA-256.

**Réussi si** : le lot est produit et l'empreinte se retrouve au journal.

## M8 — Paramétrer l'organisme

Suivre l'ordre du guide d'administration ; un exercice par étape :

1. Référentiels (ajouter une unité), Milieux physiques (un milieu et ses preuves), Périmètres
   (donner une unité à un gestionnaire), Règles (créer, simuler, publier une règle de
   disponibilité), Ressources (lier une ressource à un compte).
2. Administration › Organisme : charte (logo, couleurs), pays autorisés à la connexion, règles
   d'alerte par courriel, **annuaires de l'organisme** (déclarer un annuaire Entra ID, ses
   domaines, ses correspondances groupe → rôle).

**Réussi si** : un compte du gestionnaire voit l'unité ajoutée, et la règle simulée rend le
verdict attendu.

## M9 — Contrôler : journal, exports, rétention

1. « Journal des décisions » : filtrer la semaine, l'origine « automatisée », exporter en CSV,
   Excel, PDF et JSON.
2. « Rétention des effectifs » : lire les durées par classe, en modifier une, enregistrer ; la
   purge ne touche que ce qui a dépassé sa durée — à lancer seulement dans l'environnement de formation.
3. Administration › Organisme : lire le journal des alertes (export téléchargé, connexion refusée).

**Réussi si** : les quatre exports s'ouvrent, et l'export apparaît au journal des alertes si une
règle l'exige.

## Évaluer la formation

À la fin de chaque module, la personne refait la capsule sans le document. Une capsule réussie
sans aide est la preuve d'acquisition consignée au plan de formation ; une capsule échouée
renvoie au module.
