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
