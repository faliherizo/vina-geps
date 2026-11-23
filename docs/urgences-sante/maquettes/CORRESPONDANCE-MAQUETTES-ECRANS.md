# Les douze planches et leurs écrans

> Écrit pour l'évaluateur de l'avis 26-015-DO et pour qui reprend le module : où regarder pour
> voir chaque planche, et ce qui diffère volontairement.

Source des planches : `docs/urgences-sante/maquettes/svg/s01` à `s12`, plus `P11` pour la
messagerie. Le système visuel appliqué est décrit par l'**ADR 0069** ; les décisions de la
messagerie et des avis par l'**ADR 0070**.

## Ce que chaque planche est devenue

| Planche | Écran livré | Comment l'ouvrir |
|---|---|---|
| **s01** Tableau de bord gestionnaire | **Tableau de bord** de l'encadrement (onglets, tuiles, quarts à couvrir), puis Gestion des quarts › Comblement — *refondu le 13 nov. 2026, ADR 0073* | `/dashboard`, `/uscomblement` |
| **s02** Portail employé | Les sept écrans personnels du module | `/monhoraire`, `/mesdisponibilites`, `/monpointage`, `/matemps`, `/mesabsences`, `/mesechanges`, `/monagph` |
| **s03** Mon horaire | **Mon horaire** — *écran créé pour cette planche* ; calendrier du mois par défaut, liste de la semaine à un clic | `/monhoraire` |
| **s04** Confection annuelle (AGPH) | Confection AGPH (console, et la vue de l'employé) | `/usagph`, `/monagph` |
| **s05** Centre de couverture des quarts | Gestion des quarts › Comblement | `/uscomblement` |
| **s06** Détail du quart | Gestion des quarts › Quarts et équipages | `/usequipages` |
| **s07** Demandes et échanges | Demandes et échanges (console, et la vue de l'employé) | `/usechanges`, `/mesechanges` |
| **s08** Pointage et présence | Pointage et présence (console, et la vue de l'employé) | `/uspresence`, `/monpointage` |
| **s09** Feuille de temps | Feuilles de temps (console, et la vue de l'employé) | `/ustemps`, `/matemps` |
| **s10** Rapports et indicateurs | Constructeur de rapports, et les relevés des feuilles | `/rapports`, `/ustemps` |
| **s11** Administration des règles | Administration des effectifs › Règles | `/usregles` |
| **s12** Administration technique | Administration des effectifs › Intégrations, Rétention, Journal | `/usintegrations`, `/usretention`, `/usjournal` |
| **P11** Messagerie sécurisée | **Communication › Messagerie du personnel** (les mêmes sections que Santé Québec) | `/usmessages`, `/usappels`, `/usdiffusion` |

**Les adresses ont changé le 2 novembre 2026**, et c'est l'essentiel de ce tableau : les douze
onglets de « Planification des effectifs » et les sept de « Paramétrage » sont devenus **dix-neuf
pages**, une par entrée de menu. Un `?onglet=` ne se partage pas, ne revient pas par le bouton
« retour » et ne se charge pas seul ; les anciennes adresses n'existent plus.

**P11 a deux hôtes et un seul foyer.** La messagerie du personnel est celle du module Santé
Québec (`sqmessages`, `sqlistesdiffusion`, `appels`) : les pages d'Urgences-santé rendent les
**mêmes sections**, sous leur propre adresse, avec le nom de ce module au bandeau et sans barre
d'onglets (« un menu, une page »). Recopier les écrans aurait produit deux messageries qui
divergent au premier correctif — ce que l'ADR 0034 interdit.

Les vingt-neuf écrans du module portent le même chrome, posé une fois dans `PageMaquette` : un
essai l'exige écran par écran (`e2e/tests/e2e/urgences-sante-menu.spec.ts`), de sorte qu'un
trentième ne puisse pas s'en écarter par omission.

## Le menu, et l'organisme de démonstration

> **Depuis le 13 novembre 2026 (ADR 0073)**, le chrome est celui de la planche s01 : barre marine
> « VINA GePS », colonne blanche à trois rubriques (« Menu principal », « Administration », « Aide
> et compte »). Le détail, par profil, est dans
> [MAQUETTE-NAVIGATION-VINA-GEPS.md](MAQUETTE-NAVIGATION-VINA-GEPS.md). Le texte ci-dessous décrit
> la révision du 1er novembre, dont le découpage en notions est conservé.

**Le menu suit les planches** (révision du 1er novembre, seconde passe) : les neuf écrans du
module vivent sous une section « Urgences-santé » dont les entrées sont celles de la colonne
« MENU PRINCIPAL » des planches — Mon horaire, Disponibilités, Absences, Confection AGPH, Gestion
des quarts, Pointage et présence, Feuilles de temps, Demandes et échanges, Administration des
effectifs.

Chaque notion réunit **la vue de l'employé et celle de l'encadrement** d'un même sujet : on
cherche un sujet, pas le rôle sous lequel on le regarde. Les sept écrans personnels ont donc
quitté « Mon espace » — une page n'a qu'une entrée principale, c'est l'un **ou** l'autre, et les
planches tranchent.

**Un organisme de démonstration** se sème en une commande :

```bash
node scripts/semer-urgences-sante.cjs --api http://localhost:4000
```

Il crée l'organisme, son administration, quatre personnes avec leur compte, les trois sites des
planches (Centre-ville, Nord, Sud), les trois quarts du jour avec leur comblement (3/4, 1/3, 2/2),
les postes P-104 à P-106, les deux règles nommées par s07 et s11, et les feuilles de temps de la
semaine écoulée. Les identifiants sont imprimés à la fin. Les données viennent de
`e2e/donnees/urgences-sante/demonstration.json`, où **chaque valeur nomme sa planche**.

L'essai `e2e/tests/e2e/urgences-sante-demonstration.spec.ts` **joue ce script** plutôt que de
recopier ses appels, puis vérifie que le menu porte les entrées des planches, que chaque écran
porte leur chrome, et que les sites et les personnes semés sont bien à l'écran.

## Ce qui diffère des planches, et pourquoi

**La barre d'application et la colonne de menu ne sont pas celles des planches.** L'application a
les siennes, partagées avec tous les modules et réglées sur les maquettes Santé Québec. Deux
chromes empilés — ou un chrome qui change selon l'écran ouvert — se lisent comme une panne. Le
marine des planches revient donc en **bandeau de page**, avec le même nom en capitales. (ADR 0069)

**Une seule teinte a été corrigée.** `#627d98`, qui porte les sous-titres et tous les en-têtes de
colonnes, mesure **4,28:1** sur blanc et **3,95:1** sur le fond de page : sous le seuil de 4,5:1
(WCAG 1.4.3). Elle devient `#536980` — 5,68:1 / 5,24:1 / 4,91:1 selon la surface. Tout le reste de
la palette est repris tel quel, y compris le sarcelle du bouton primaire, qui passe (6,09:1).

**Les quatre chiffres des planches (24, 08, 12, 03) ne sont pas recopiés.** Le comblement affiche
ses trois indicateurs **mesurés** ; une quatrième tuile reprise de la planche aurait été une mesure
inventée, et une mesure inventée se lit comme une mesure.

**« Mon horaire » a dû être créé.** C'était la seule planche sans hôte : la donnée existait —
l'horaire publié sert déjà à proposer un échange — mais personne ne pouvait le lire comme un
horaire. La colonne « Site » a demandé d'ajouter le milieu au quart publié, côté Node **et** Java.
Son statut dit ce que la donnée sait : « Confirmé », ou « Échange en cours » quand un échange
ouvert concerne le quart.

**La messagerie garde ses identifiants d'essai.** Le fil passe du tableau aux bulles et la liste
des conversations apparaît à gauche, mais `sq-msg-table`, `sq-msg-fil`, `sq-msg-repondre-*`,
`sq-msg-lu-*` et `sq-msg-piece-*` sont inchangés : les renommer n'aurait appris à personne que
l'écran avait changé.
