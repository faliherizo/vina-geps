# ADR 0069 — Les maquettes Urgences-santé habillent les écrans existants, et une seule de leurs teintes est corrigée

- **Statut** : accepté — appliqué aux huit écrans du module
- **Date** : 1er novembre 2026
- **Décideurs** : équipe Nexu VINA
- **Concerne** : `frontend/src/effectifs/{urgences-sante.css,PageUrgencesSante.tsx}`, les huit écrans
  du module (`PlanificationEffectifs`, `Effectifs`, `MesDisponibilites`, `MesEchanges`, `MonAgph`,
  `MonPointage`, `MaFeuilleTemps`, `MesAbsences`)
- **Source** : `docs/urgences-sante/maquettes/svg/s01` à `s12`

## Contexte

Les douze planches portent leurs teintes **en clair dans le SVG** — neuf couleurs en tout. La
mesure est donc courte et la décision nette, là où un pack d'images aurait demandé de les
échantillonner au pixel.

Mesuré, jamais jugé à l'œil (WCAG 1.4.3, seuil 4,5:1 pour le texte courant) :

| Teinte | Usage dessiné | Sur blanc | Sur le fond de page `#f4f6f9` |
|---|---|---:|---:|
| `#102a43` | bandeau, titres | 14,64:1 | 13,52:1 |
| `#334e68` | texte courant | 8,64:1 | 7,98:1 |
| **`#627d98`** | **sous-titres, en-têtes de colonnes** | **4,28:1** | **3,95:1** |
| `#0b6e69` + texte blanc | bouton primaire | 6,09:1 | — |
| `#d9e2ec` | bordure de carte | 1,31:1 | 1,21:1 |

Une seule teinte est en défaut — mais c'est la deuxième plus employée, et elle porte précisément
ce qui doit rester lisible : les en-têtes de colonnes et tous les sous-titres.

## Décision

**Les teintes des maquettes gardent les surfaces ; `#627d98` devient `#536980` pour le texte.**
C'est la règle de l'ADR 0054, appliquée à un second pack — et elle n'a cette fois qu'une seule
conséquence, parce que la palette était presque conforme.

- `#536980` : **5,68:1** sur blanc, **5,24:1** sur le fond de page, **4,91:1** sur la teinte
  active `#e6f0f7`. Mesuré contre les trois surfaces où il se pose, pas seulement contre le blanc.
- `--us-trait-controle: #6b7f96` (**4,12:1**) entoure les **champs et les boutons** : 1.4.11 exige
  3:1 pour ce qui permet de reconnaître un contrôle. La maquette ne dessine aucun formulaire — elle
  ne pouvait pas trancher, et son contour de carte (1,31:1) aurait été repris par défaut.
- La bordure de carte reste `#d9e2ec` : une carte n'est pas un contrôle, et 1.4.11 ne la vise pas.

**Le chrome est posé une fois**, dans `PageUrgencesSante`, et les huit écrans en héritent : fond de
page, bandeau marine, cartes, tableaux, bouton sarcelle. Un écran n'a rien à faire pour ressembler
aux maquettes, et ne peut pas s'en écarter par omission.

**La barre d'application et la colonne de menu des planches ne sont pas reprises.** L'application a
les siennes, partagées avec tous les modules et réglées sur les maquettes Santé Québec (en-tête
blanc souligné de bleu). Deux chromes empilés — ou un chrome qui change selon l'écran ouvert — se
lisent comme une panne. Le marine revient donc en **bandeau de page** : même teinte, même nom en
capitales, même rapport de texte (11,18:1).

**Aucune carte chiffrée n'est ajoutée là où le chiffre n'existe pas.** Les planches montrent quatre
indicateurs (24, 08, 12, 03) ; le comblement en affiche déjà trois, mesurés. Les quatre de la
planche recopiés tels quels auraient été des mesures inventées, et une mesure inventée se lit comme
une mesure.

## Révision du 1er novembre 2026 (soir) — le menu suit aussi les planches

La première passe avait gardé les sept écrans personnels dans « Mon espace », au motif qu'une
personne n'a qu'un espace personnel. L'organisation a tranché autrement, et les planches lui
donnent raison : leur colonne « MENU PRINCIPAL » réunit **tout** le module, écrans personnels
compris.

La section « Urgences-santé » porte donc les entrées des planches, dans leur ordre : Mon horaire,
Disponibilités, Absences, Confection AGPH, Gestion des quarts, Pointage et présence, Feuilles de
temps, Demandes et échanges, Administration des effectifs.

**Chaque notion réunit la vue de l'employé et celle de l'encadrement** d'un même sujet — on cherche
un sujet, pas le rôle sous lequel on le regarde. Les rôles décident ensuite de ce qui s'affiche.

Une page n'ayant qu'une entrée principale, c'est l'un **ou** l'autre : les sept écrans ont quitté
« Mon espace ». L'essai `frontend/tests/navigation.test.ts` fige l'ordre, le découpage et le fait
qu'aucune autre section n'en garde une vue.

**Le système visuel vaut aussi pour Santé Québec** : les deux modules de santé s'adressent au même
métier et se montrent au même client, et deux chartes pour un même domaine se remarquent surtout
quand on passe de l'une à l'autre. La feuille a quitté `effectifs/` pour
`composants/maquette-sante.css`, et `PageDecrite` prend le bandeau et les jetons sans céder sa
propre enveloppe (largeur de lecture bornée, gouttière).

## Conséquences

- `frontend/tests/palette-urgences-sante.test.ts` recalcule les rapports **et vérifie que chaque
  rapport écrit en commentaire est vrai** : une annotation non vérifiée survit à la valeur qu'elle
  décrivait, et devient le pire des commentaires — celui qu'on croit.
- Pas de thème sombre : l'application n'en a aucun. Un module ne peut pas avoir un thème que
  l'application n'a pas (même raison qu'au 25 octobre 2026 pour Santé Québec).
- Un neuvième écran du module hérite du chrome en l'enveloppant de `PageUrgencesSante`, sans toucher
  à la feuille.

## Options écartées

- **Reprendre la palette à l'identique.** Elle rend les en-têtes de colonnes et les sous-titres non
  conformes, donc un module à refaire après l'audit.
- **Changer la barre d'application pour le marine.** Elle est partagée : le module Santé Québec, le
  scolaire et la facturation y perdraient leur chrome pour qu'un seul module gagne le sien.
- **Un second composant de cartes chiffrées** (`.us-indicateur`) à côté de `Tuile`. Deux composants
  pour une même intention divergent, et l'écran aurait fini par afficher deux bandeaux dont l'un
  vide.
