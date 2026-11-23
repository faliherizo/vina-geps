#!/bin/zsh
# **Le film de la vitrine de VINA GePS, et les vidéos d'aide** — chaque passage est un essai e2e joué contre le Java.
#
#   scripts/monter-video-vitrine.sh            # joue les chapitres en racontant, puis monte le film et les aides
#   REJEU=0 scripts/monter-video-vitrine.sh    # remonte à partir du dernier tournage
#
# Un chapitre n'est pas une capture d'écran mise en scène : c'est un essai qui **affirme** ce qu'il montre, et qui
# échoue si l'écran ne le fait pas. Pendant le tournage (`E2E_RECIT=1`), chaque essai dit à voix haute le cas qu'il
# éprouve (`e2e/tests/e2e/helpers/recit.ts`) et attend la fin de sa phrase : les gestes gardent leur vitesse normale.
# `scripts/monter-recit.mjs` coupe ensuite les vidéos d'une phrase à la suivante. Sorties :
# frontend/public/vitrine/demo-vina-geps.webm et frontend/public/aide/videos/<écran>.webm.
set -euo pipefail
RACINE="$(cd "$(dirname "$0")/.." && pwd)"
LARGEUR="${LARGEUR:-1280}"
# Le film présente le produit de VINA IT Solution : l'organisme semé ne porte le nom d'aucun client.
export SEMIS_NOM_ORGANISME="${SEMIS_NOM_ORGANISME:-Organisme de démonstration}"

# Les chapitres, dans l'ordre du film : `scripts/vitrine-chapitres.json` — la gestion des effectifs et la paie, puis
# les alertes cliniques.
FICHIERS=("${(@f)$(node -e 'for (const c of require(process.argv[1]).chapitres) console.log(c.fichier)' "$RACINE/scripts/vitrine-chapitres.json")}")
if [ "${REJEU:-1}" = "1" ]; then
  rm -rf "$RACINE/e2e/test-results"
  E2E_RECIT=1 E2E_VIDEO_LARGEUR="$LARGEUR" "$RACINE/backend-java/scripts/campagne-e2e.sh" "${FICHIERS[@]}"
fi
node "$RACINE/scripts/monter-recit.mjs"
