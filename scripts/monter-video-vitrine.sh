#!/bin/zsh
# **Le film de la vitrine de VINA GEPS** — chaque chapitre est un essai e2e joué contre le Java.
#
#   scripts/monter-video-vitrine.sh            # filme les chapitres, puis monte le film
#   REJEU=0 scripts/monter-video-vitrine.sh    # monte à partir des vidéos déjà filmées
#
# Un chapitre n'est pas une capture d'écran mise en scène : c'est un essai qui **affirme** ce qu'il
# montre, et qui échoue si l'écran ne le fait pas. Le film ne peut donc pas montrer une exigence qui
# ne fonctionne pas. Sortie : frontend/public/vitrine/demo-vina-geps.webm et ses sous-titres .vtt.
set -euo pipefail
RACINE="$(cd "$(dirname "$0")/.." && pwd)"
LARGEUR="${LARGEUR:-1280}"
SORTIE="$RACINE/frontend/public/vitrine"
TRAVAIL="${TMPDIR:-/tmp}/vina-geps-film"
mkdir -p "$SORTIE" "$TRAVAIL"

# Les chapitres, dans l'ordre du film, et leur narration : `scripts/vitrine-chapitres.json`. Deux avis : Urgences-santé
# (26-015-DO, effectifs et paie) puis Santé Québec Laurentides (FX-AO-20260810, communications cliniques). Chaque
# chapitre est un essai qui affirme ce qu'il montre ; le film dure plus de 25 minutes au ralenti par défaut.
CHAPITRES=("${(@f)$(node -e 'for (const c of require(process.argv[1]).chapitres) console.log(`${c.fichier}|${c.titre}`)' "$RACINE/scripts/vitrine-chapitres.json")}")
if [ "${REJEU:-1}" = "1" ]; then
  rm -rf "$RACINE/e2e/test-results"
  FICHIERS=()
  for c in "${CHAPITRES[@]}"; do FICHIERS+=("${c%%|*}"); done
  E2E_VIDEO_LARGEUR="$LARGEUR" E2E_RALENTI="${RALENTI:-600}" E2E_VIDEO=on \
    "$RACINE/backend-java/scripts/campagne-e2e.sh" "${FICHIERS[@]}"
fi

# Les vidéos de chaque chapitre, dans l'ordre où elles ont été filmées.
LISTE="$TRAVAIL/liste.txt"; : > "$LISTE"
VTT="$SORTIE/demo-vina-geps.fr.vtt"
debut=0
DEBUTS=()
for c in "${CHAPITRES[@]}"; do
  fichier="${c%%|*}"; titre="${c#*|}"; base="$(basename "$fichier" .spec.ts)"
  # Playwright tronque le nom du fichier à 26 caractères dans le dossier de sortie (« urgences-sante-demonstrati-… »).
  base="${base[1,26]}"
  videos=("${(@f)$(ls -tr "$RACINE"/e2e/test-results/${base}-*/*.webm 2>/dev/null || true)}")
  [ -z "${videos[1]:-}" ] && { echo "✗ aucun film pour $fichier — le chapitre a-t-il été joué ?"; exit 1; }
  duree_chapitre=0
  for v in "${videos[@]}"; do
    print "file '$v'" >> "$LISTE"
    d=$(ffprobe -v error -show_entries format=duration -of csv=p=0 "$v")
    duree_chapitre=$(( duree_chapitre + d ))
  done
  DEBUTS+=($debut)
  debut=$(( debut + duree_chapitre ))
done

IMAGE="$TRAVAIL/image.webm"
ffmpeg -y -loglevel error -f concat -safe 0 -i "$LISTE" -c:v libvpx-vp9 -b:v 0 -crf 36 -row-mt 1 -an "$IMAGE"
duree=$(ffprobe -v error -show_entries format=duration -of csv=p=0 "$IMAGE")

# **La voix** : chaque chapitre annoncé puis commenté, au début du chapitre ; les sous-titres sont la voix, mot pour mot.
print "{\"debuts\": [${(j:,:)DEBUTS}], \"duree\": $duree}" > "$TRAVAIL/debuts.json"
node "$RACINE/scripts/narrer-vitrine.mjs" "$TRAVAIL/debuts.json" "$TRAVAIL/voix.wav" "$VTT"
ffmpeg -y -loglevel error -i "$IMAGE" -i "$TRAVAIL/voix.wav" -map 0:v:0 -map 1:a:0 -c:v copy -c:a libopus -b:a 64k -af apad \
  -t "$duree" "$SORTIE/demo-vina-geps.webm"
# Le film est promis « en détail » : plus court que DUREE_MIN (25 min par défaut), un chapitre a manqué.
if (( ${duree%.*} < ${DUREE_MIN:-1500} )); then
  echo "✗ le film dure ${duree%.*} s, moins que ${DUREE_MIN:-1500} s : un chapitre manque-t-il ?"; exit 1
fi
echo "✓ $SORTIE/demo-vina-geps.webm ($(du -h "$SORTIE/demo-vina-geps.webm" | cut -f1), $(( ${duree%.*} / 60 )) min, avec voix) — sous-titres : $VTT"
