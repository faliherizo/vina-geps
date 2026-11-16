# ADR 0073 — VINA GePS : le chrome de la planche s01, un menu à trois rubriques, un tableau de bord qui chiffre la journée

- **Statut** : accepté. Remplace la décision de l'ADR 0069 sur le « marine en bandeau de page ».
- **Date** : 13 novembre 2026
- **Décideurs** : équipe VINA
- **Concerne** :
  - `frontend/src/navigation.tsx` (`NAVIGATION`), `App.tsx` (barre, colonne, fil d'Ariane) et `styles.css` ;
  - `composants/PageMaquette.tsx` et `maquette-sante.css` (le bandeau devient un surtitre) ;
  - `Dashboard.tsx` et `tableauDeBord/*` ;
  - `MonHoraire.tsx` ;
  - `i18n.tsx` (langue initiale).
- **Source** : demande du 13 novembre 2026 : renommer le produit, refondre l'interface selon la
  planche s01, mettre de l'ordre dans le menu et présenter Mon horaire en calendrier.
- **Documents** :
  - [MAQUETTE-NAVIGATION-VINA-GEPS.md](../urgences-sante/maquettes/MAQUETTE-NAVIGATION-VINA-GEPS.md) ;
  - [UTILISATEURS-PROFILS-ACCES.md](../urgences-sante/UTILISATEURS-PROFILS-ACCES.md).

## Contexte

Le produit, séparé de Nexu VINA le 13 novembre 2026, s'appelle désormais **VINA GePS** (VINA
Gestion des Personnels de la Santé). Urgences-santé en est le premier client.

Le menu avait hérité de l'application mère :
- **quatre sections repliables** (« Tableau de bord », « Urgences-santé », « Mon espace »,
  « Administration ») ;
- chaque section contenait des notions, elles aussi repliables ;
- la section « Tableau de bord » ne contenait que le tableau de bord, d'où le fil « Tableau de
  bord › Tableau de bord » ;
- l'accueil était une grille de 29 boutons qui recopiait le menu.

L'ADR 0069 avait placé le marine des planches dans un **bandeau de page**, parce que la barre
d'application était partagée avec d'autres modules. Cette raison a disparu avec la séparation, et
il restait deux chromes empilés.

## Décisions

1. **La barre d'application est marine** (`--marine: #13294b`). Elle porte la marque `VINA GePS`,
   le nom de l'organisme (sa charte, P-TN-9) et les commandes, qui sont des pastilles à fond
   blanc. **La colonne du menu est blanche** et l'entrée courante est marquée en bleu pâle, en
   gras et par un filet. La couleur seule ne porte donc jamais l'information (1.4.1).
2. **Trois rubriques, chacune un titre et non un accordéon** : « Menu principal », « Administration »
   et « Aide et compte ». Le menu a **deux niveaux au plus** : une rubrique contient des notions
   repliables ou des liens directs. Le tableau de bord est la première entrée du menu principal.
   Le paramétrage du module (« Paramètres des effectifs ») rejoint l'administration de
   l'organisme : c'est le même public, et ce n'est pas un travail quotidien.
3. **Chaque écran du module porte le socle `effectifs`** au menu. Son sous-module reste jugé par
   `PAGE_MODULE`. Sans le socle, tous les écrans du module disparaissent, mais le tableau de bord
   et les rapports restent.
4. **Le fil d'Ariane part du tableau de bord** (« Tableau de bord › Notion › Écran ») et n'est pas
   affiché sur le tableau de bord lui-même.
5. **Le bandeau de page devient un surtitre texte** : le nom du module et le contexte de l'écran.
   Le `data-testid` `maquette-bandeau` est conservé.
6. **Le tableau de bord gestionnaire chiffre la journée** (planche s01) avec quatre onglets,
   quatre tuiles, une table par sujet, une action par ligne et l'action principale. Il n'utilise
   **que des routes existantes**. Un chiffre non lu affiche « — », jamais 0. L'employé y lit ses
   sept prochains jours et les portes de ses propres écrans.
7. **Mon horaire est un calendrier mensuel par défaut**, construit sur le composant commun
   `Calendrier`. La liste de la semaine reste à un clic. Sur téléphone, le mois devient un agenda
   des jours travaillés.
8. **Le français est la langue initiale**, quelle que soit la langue du navigateur (E-TN-21). Un
   autre choix fait par la personne est retenu.

## Options écartées

- **Garder les sections repliables en ouvrant la première par défaut** : on gardait trois niveaux
  et la section qui se nomme comme son unique entrée.
- **Un menu plat sans notions** (une vingtaine d'entrées) : il mélangerait les vues de l'employé et
  les consoles. La notion réunit les deux faces d'un même sujet (« Absences » : les miennes et la
  console), ce qui reste le bon découpage.
- **Une route serveur « indicateurs du jour »** : elle aurait dupliqué quatre lectures existantes
  et leurs gardes de périmètre. Le tableau de bord lit donc les routes qui appliquent déjà le
  périmètre du gestionnaire. Une route agrégée se justifiera si la latence l'exige (budget JMeter).
- **Le titre de rubrique en `<h2>`** : la colonne précède `<main>`, et un `h2` s'y lirait avant le
  `h1` de l'écran. Un groupe nommé (`role="group"` avec `aria-labelledby`) suffit.

## Conséquences

- Les essais qui décrivaient l'ancien menu ont été réécrits : `navigation.test.ts`,
  `tenant-urgences-sante-menu.test.ts` et `menu-notions.spec.ts`. Les identifiants d'essai sont
  conservés et `fil-pilotage` désigne désormais le premier maillon (« Tableau de bord »).
- Les contrastes du nouveau chrome sont calculés par `palette-vina-geps.test.ts`.
- Les noms des conteneurs Docker (`urgences-sante-*`) ne changent pas : ils désignent un
  déploiement du client, pas le produit.
