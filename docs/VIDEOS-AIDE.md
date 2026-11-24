# Les vidéos d'aide : sources, introductions et mise en route

> Pour qui tourne ou met à jour les vidéos du guide (`/admin/help`).

Chaque écran a sa vidéo commentée (voix française du Québec, sous-titres exacts à la voix). Toutes commencent par
**« Avant de commencer »** : un carton dit, dans l'ordre, ce qui doit être en place pour que l'écran serve. Le guide
s'ouvre sur **« Premiers pas : la mise en route »**, une vidéo qui montre toute la configuration, étape par étape.

## La chaîne

| Étape | Script | Sortie |
|---|---|---|
| 1. Vidéo d'un écran, montée depuis les essais e2e racontés | `scripts/monter-recit.mjs` | `frontend/aide-sources/videos/<écran>.webm` et `.fr.vtt` |
| 1 bis. À défaut d'essai raconté, vidéo tournée d'après la fiche | `scripts/tourner-aides.mjs` | même dossier |
| 2. Vidéo de la mise en route | `scripts/tourner-mise-en-route.mjs` | `mise-en-route.webm`, `.fr.vtt`, `.chapitres.json` (sources et `public`) |
| 3. Introduction « Avant de commencer », publication | `scripts/introduire-aides.mjs` | `frontend/public/aide/videos/` et `introductions.json` |
| 4. Parcours en vidéo (FX-AO), joués sur la démonstration déployée | `scripts/tourner-parcours-fx-ao.sh` → `scripts/monter-parcours.mjs` | `frontend/public/aide/videos/parcours-<id>.webm` et `.fr.vtt` |

Les **sources** (`frontend/aide-sources/videos`) ne sont jamais publiées : elles n'entrent pas dans l'image Docker
(`.dockerignore`). Seul `introduire-aides.mjs` écrit les vidéos d'écran du dossier public. Le relancer ne double pas
l'introduction : il repart toujours de la source.

## Quand relancer quoi

| Changement | À relancer |
|---|---|
| Un préalable d'une fiche (`frontend/src/aide/fichesEcrans.ts`) | `node scripts/introduire-aides.mjs --seulement <écran>` |
| Une étape de la mise en route (`frontend/src/aide/miseEnRoute.ts`) | `tourner-mise-en-route.mjs` |
| L'écran lui-même | le montage ou le tournage de l'écran, puis `introduire-aides.mjs --seulement <écran>` |

L'essai `frontend/tests/aide-videos.test.tsx` compare `introductions.json` aux préalables actuels des fiches. Il
vérifie aussi les chapitres de la mise en route par rapport à ses étapes. Un oubli fait donc échouer `npx vitest run`.

## Les parcours en vidéo (FX-AO)

Une fiche montre un écran ; un parcours montre une **tâche entière**, d'une personne à l'autre, sur l'organisme Clincom
de la démonstration : configurer, envoyer une alerte et la suivre, déclarer une absence, déposer ses disponibilités,
lire la liste de temps supplémentaire. Le guide les présente dans « Parcours en vidéo », et chaque fiche d'un écran
traversé y renvoie. Ils ne paraissent qu'aux organismes qui ont le module des alertes et chacun des écrans du parcours.

| Quoi | Où |
|---|---|
| Les parcours, leurs comptes et leurs données | `e2e/donnees/fx-ao/videos-aide.json` |
| L'essai qui les joue et les raconte (il affirme ce qu'il montre) | `e2e/tests/e2e/deploiement-fx-ao-videos.spec.ts` |
| Le catalogue lu par le guide (mêmes identifiants et titres) | `frontend/src/aide/parcoursVideo.ts` |

```bash
scripts/tourner-parcours-fx-ao.sh            # joue sur geps.vinaitsolution.com en racontant, puis monte
REJEU=0 scripts/tourner-parcours-fx-ao.sh    # remonte depuis le dernier tournage
```

L'essai écrit dans la démonstration (absence annulée à la fin, dépôt remplacé, alerte marquée « Exercice e2e ») ; la
configuration ouvre ses formulaires et les referme sans enregistrer. Un parcours dont l'essai échoue n'a pas de récit,
et le montage refuse de le monter en le nommant. `aide-videos.test.tsx` exige la vidéo de chaque parcours et vérifie
que le catalogue et le jeu de l'essai nomment les mêmes parcours.

## Tourner la mise en route

Elle se filme sur un organisme créé par `scripts/creer-organisme-par-defaut.cjs`
([ORGANISME-PAR-DEFAUT.md](ORGANISME-PAR-DEFAUT.md)), avec le compte qui fait chaque étape :

| Étape | Compte |
|---|---|
| organisme et modules | superadmin |
| référentiels, codes, milieux, comptes, périmètres, règles | `admin` |
| ressources, périodes, quarts | `chef-ops` |
| déclarer l'absence | `employe1` |

```bash
node --env-file=.env scripts/creer-organisme-par-defaut.cjs --api http://localhost:8095 --nom "Urgences-santé"
node --env-file=.env scripts/tourner-mise-en-route.mjs --api http://localhost:8095 --organisme <id affiché>
node scripts/introduire-aides.mjs
```

Les mots de passe viennent du fichier local ignoré par git (`scripts/.organisme-par-defaut.<nom>.local.json`) et de
l'environnement pour le superadmin ; aucun n'est affiché. Les formulaires sont remplis avec l'exemple de la fiche, puis
refermés **sans être enregistrés**.

Le guide lit `mise-en-route.chapitres.json` pour ses boutons « Voir dans la vidéo ». Sans ce fichier, les boutons ne
s'affichent pas, mais les étapes et leurs liens restent.

Outils requis : `say` (voix Amélie, macOS), `ffmpeg`, Chromium de Playwright (`e2e/node_modules`).
