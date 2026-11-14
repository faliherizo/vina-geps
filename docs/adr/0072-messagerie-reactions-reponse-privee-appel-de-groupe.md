# ADR 0072 — La messagerie : un fil à deux unique, une visibilité jugée une fois, des réactions fermées, l'appel de groupe en session, un cycle de vie du message

- **Statut** : accepté
- **Date** : 13 novembre 2026
- **Décideurs** : équipe Nexu VINA
- **Concerne** : `backend-java/.../communication/domaine/Messagerie.java` (`Adresse`, `dansLaPortee`, `filADeux`),
  `Reaction.java`, `AppelsGroupe.java` ; `GestionnaireMessagerie`, `GestionnaireAppelsGroupe`, `LectureMessagerie`,
  `LectureAppelsGroupe` ; `DepotMessagerie`, `DepotAppelsGroupe` ; `ControleurMessagerie`, `ControleurAppelsGroupe` ;
  changeset Liquibase `0003-messagerie-reactions-appels-groupe.xml` ; `frontend/src/santequebec/pages/sqmessages.tsx`,
  `santequebec/appels.tsx`
- **Source** : demande de refonte du 13 nov. 2026 ; `docs/urgences-sante/REFONTE-MESSAGERIE.md`

## Contexte

La messagerie du personnel savait tout faire, mais la réponse privée était **publique** : le serveur
Java ignorait `priveA`, et le message partait à tout le fil. Un fil était lisible de quiconque en
connaissait l'identifiant. Écrire deux fois à la même personne ouvrait deux fils, l'appel ne
joignait qu'une seule personne, et accuser réception coûtait un message à tout le fil.

## Décisions

### 1. La visibilité d'un message se juge une fois, dans `Messagerie.dansLaPortee`

Une `Adresse` (destinataire privé, message auquel on répond) voyage avec le message. Le fil, les
non-lus, la recherche et les réactions passent tous par `dansLaPortee`, et la pièce jointe par la
même règle (`Adresse.visiblePar`). Un fil où l'on ne participe pas répond **introuvable** (404),
jamais interdit : un 403 confirmerait qu'il existe.

### 2. Le fil à deux est unique

`POST /conversations/directe` rend le fil individuel actif entre deux personnes (200) ou le crée
(201). Un fil archivé ne se rouvre pas : un nouveau naît, et l'archive garde son histoire.

### 3. Les réactions forment un ensemble fermé de quatre

`pouce`, `fait`, `merci`, `regarde` : une `enum` dont chaque valeur porte ses mots. Une réaction
libre (un émoji quelconque, un court texte) deviendrait un second canal de messages, hors des
règles de visibilité et sans trace au journal. Réagir bascule (poser, puis retirer), et chaque
geste est journalisé. Un fil archivé, ou dont on est sorti, ne se réagit plus.

### 4. L'appel de groupe est une session, comme l'appel à deux

`AppelsGroupe` tient qui est invité, qui est présent et depuis quand ; un fil ne porte qu'un appel
à la fois. Le dernier qui part termine l'appel, qui est « manqué » si personne ne l'a jamais
rejoint. Une sonnerie expire après le même délai que l'appel à deux, jugée sur un instant passé en
paramètre, jamais lu à l'horloge. Le **transport** de la voix et de l'image n'est pas tranché ici :
il se branchera sur la session sans redécider qui a le droit de quoi.

### 5. Un message a un cycle de vie : envoyé, reçu, lu, modifié, supprimé

L'auteur voit l'acheminement de son message (envoyé, puis reçu par N, puis lu par N, sur N
destinataires) ; personne d'autre ne le voit. « Reçu » est un accusé de réception : relever ses
avis (`GET /conversations/non-lus`, que la cloche interroge) les note, et une lecture vaut
réception. Modifier est réservé à l'auteur : chaque version remplacée est gardée
(`msg_message_versions`) et se lit dans l'historique du message. Supprimer appartient à l'auteur
ou à un modérateur du fil : le message garde sa place, « supprimé par … le … », et son texte, ses
pièces, ses réactions et son historique ne se servent plus. Le journal consigne chaque geste,
jamais le texte.

### 6. Le contenu des pièces jointes se range selon l'hébergement

Le domaine ne sait pas où vit un fichier. Le dépôt confie le contenu à un coffre, choisi par
`PIECES_JOINTES_STOCKAGE` :
- `base` (par défaut) range le contenu dans la colonne `contenu` (le choix du Node, migration 0171), qui suit la base dans chaque copie ;
- `volume` le pose en fichier sur le volume de données (l'EFS sur AWS), écrit sous un nom temporaire puis renommé, et retiré si la ligne ne s'écrit pas.

Un mode inconnu arrête le démarrage. L'antivirus passe avant toute écriture, et un analyseur
injoignable fait échouer l'envoi.

## Options écartées

- **Un drapeau « privé » sans destinataire** : il dit qu'un message est restreint, pas à qui.
  L'écran ne pourrait pas dire « Marc et vous le verrez ».
- **Contrôler la visibilité dans chaque route** : trois routes (fil, non-lus, recherche) l'auraient
  chacune réimplémentée, et c'est en divergeant de cette façon que la réponse privée était devenue
  publique.
- **Réactions libres (Unicode quelconque)** : écartées, voir la décision 3.
- **Étendre `msg_appels` à N appelés** : la machine à états de l'appel à deux (sonner, décrocher,
  refuser) n'a pas de sens à plusieurs, où chacun entre et sort. Deux tables, `msg_appels_groupe` et
  `msg_appels_groupe_participants`, laissent l'appel à deux intact.
- **Supprimer en effaçant la ligne** : le fil perdrait la trace d'un message auquel d'autres ont
  répondu ou réagi, et une suppression ne se distinguerait pas d'un message jamais écrit.
- **Accusé de réception par une route dédiée appelée par l'écran** : il dépendrait d'un écran
  ouvert. La relève des avis, que la cloche fait déjà sur toutes les pages, en est le vrai moment.
- **Choisir le stockage dans le domaine ou par une condition dans le gestionnaire** : un choix d'hébergement est
  une donnée de configuration (DIP), pas une branche du code métier.
- **Transport WebRTC dans ce lot** : il exige un serveur TURN et un choix d'hébergement soumis à la
  clause 17.07.04 (données au Québec). C'est une décision à part entière.

## Conséquences

- La réponse privée est enfin privée. Un message privé reçu **avant** ce changement l'était déjà en
  base (`priveA`) : il devient privé à la lecture, sans migration.
- Dix routes nouvelles, toutes servies dans l'OpenAPI, qui est construite à partir des routes montées.
- Le schéma évolue par les changesets `0003` et `0004`, et `SchemaEssai` les reflète. Sur SQLite,
  la précondition `columnExists` échoue quand la base compte beaucoup de tables : une colonne se
  constate par `pragma_table_info`.
- Le texte d'un message supprimé reste en base, servi à personne : sa destruction relève de la
  politique de rétention (P-TN-12), pas de ce geste.
- En mode `volume`, les pièces ne figurent ni dans les sauvegardes applicatives ni dans les copies par organisme :
  seule la sauvegarde du volume EFS les protège (COPIES-DE-SECURITE.md). D'où `base` par défaut.
- Un stockage objet (S3) n'est pas branché : il s'ajouterait comme un troisième coffre, sans toucher au domaine.
