# ADR 0034 — Un module ne se retrouve pas dans un autre : la déclaration d'absence n'a qu'un lieu

- **Statut** : accepté
- **Date** : 2026-09-16
- **Complète** : [ADR 0026](0026-absences-unifiees-un-formulaire-trois-modules.md) (façade `/api/absences`, formulaire unique)

## Contexte

L'ADR 0026 avait donné aux absences **un** formulaire (tiroir de la notion Absences) et une façade
`POST /api/absences`. Mais trois écrans continuaient de déclarer des absences depuis un autre
module, chacun avec son propre formulaire :

- `/admin/scolaire?onglet=declarer` — l'écran des remplacements scolaires portait un onglet
  « Déclarer une absence » plus riche que le tiroir (plages multiples, périodes, spécialité,
  clientèle, pièce, note interne, collègues à aviser, verdict du calendrier) ;
- le foyer du module Enseignement (`mifi › act-absences`) et la vue Absences › Enseignement
  rendaient un fieldset « Déclarer une absence » (`POST /api/varpay/absences`) — la vue Absences
  hébergeait donc **deux** formulaires de déclaration ;
- l'onglet « Absences » des horaires cliniques s'intitulait « Déclarer une absence », alors qu'il
  saisit une **indisponibilité** de planification (une journée ou une demi-journée à ne pas
  placer), pas une absence du dossier.

Un module qui s'installe dans un autre a deux formulaires à maintenir, deux jeux de règles, deux
adresses pour la même action — et l'utilisateur ne sait plus laquelle fait foi.

## Décision

1. **La déclaration d'absence vit dans la notion Absences, et nulle part ailleurs.** Le tiroir
   unique (`FormulaireAbsence`) accueille les champs de chaque nature dans un composant à elle :
   `absences/DeclarationScolaire.tsx` porte tout ce que l'écran scolaire savait faire (plages avec
   parties et périodes, spécialité et clientèle du remplaçant, pièce fournie, consignes, note
   interne, courriels à aviser, verdict du calendrier scolaire avant l'envoi, périmètre d'école
   d'un compte ordinaire). La façade reçoit `plages` et `noteInterne` (Node et Java) ; `du`/`au`
   en découlent.
2. **La nature « enseignement » désigne le professeur** (`abs-professeur`, `teacherId`) : c'est
   lui que la paie variable connaît ; l'employé lié en découle au serveur. Le fieldset de
   déclaration disparaît de `AbsencesTab` (MIFI), qui ne fait plus que suivre et pourvoir la
   suppléance. L'onglet `act-absences` du foyer MIFI reste — il est la vue de **repli** quand
   l'établissement a fermé la notion Absences (D.28) — mais il ne déclare plus.
3. **L'écran scolaire suit, il ne déclare pas** : son onglet devient « Suivi des absences »
   (`?onglet=suivi`) — lien vers la notion Absences, horaire de la personne, absences déclarées
   avec « Suivre le remplacement » et « Annuler ». L'entrée de menu devient « Suivi des absences
   scolaires » et la notion Absences s'ouvre à la direction (`director`), qui déclare pour son
   personnel.
4. **Les horaires cliniques nomment leur objet** : l'onglet s'appelle « Indisponibilités » et
   son formulaire « Déclarer une indisponibilité ». L'objet reste au contexte `horaires` — ce
   n'est pas une absence du dossier, qui arrive dans les horaires par le rapport « absences
   approuvées ».

## Options écartées

- **Garder l'onglet scolaire et retirer la nature scolaire du tiroir** : l'ADR 0026 aurait été
  défaite ; le tiroir n'aurait plus été « un formulaire, trois modules ».
- **Deux formulaires, l'un « simple » dans le tiroir, l'autre « complet » dans le scolaire** : la
  situation de départ, avec ses deux jeux de règles.
- **Retirer l'onglet `act-absences` du foyer MIFI** : la vue de repli (notion Absences fermée)
  n'aurait plus eu d'hôte.

## Conséquences

- Identifiants d'essai retirés (liste gelée régénérée) : `absence-{ouvrir,modal,formulaire,
  declarer,corps,etablissement,motif,remplacement,consignes,commentaire}` (scolaire),
  `absence-{teacher,contract,date,hours,reason,create}` (MIFI), `abs-partie`, `abs-sans-professeur`,
  `scolaire-tab-declarer`. Ajoutés : `scolaire-tab-suivi`, `scolaire-vers-absences`,
  `abs-professeur`, `absence-plage-retirer-*`, `absence-declarer-ailleurs`. Les champs scolaires
  communs gardent leurs identifiants du tiroir (`abs-etablissement`, `abs-corps`, `abs-motif`,
  `abs-remplacement`, `abs-consignes`, `abs-commentaire`) ; les champs riches gardent ceux de
  l'écran scolaire (`absence-plage-*`, `absence-specialite`, `absence-clientele`,
  `absence-justificatif`, `absence-note-interne`, `absence-courriels-aviser`,
  `absence-calendrier-aide`, `absence-personne`).
- Les essais de bout en bout déclarent depuis la notion Absences (`e2e/tests/e2e/helpers/absences.ts`)
  puis reviennent au scolaire ou au foyer MIFI pour suivre.
- Contexte Java `absences` : `Partie` devient un enum (cliquet `absences=7`),
  `DeclarationAbsence` lit `plages` et `noteInterne` comme le Node.
