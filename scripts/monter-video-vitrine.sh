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

# Les chapitres, dans l'ordre du film : « fichier d'essai|titre affiché ».
CHAPITRES=(
  "tests/e2e/urgences-sante-saisies.spec.ts|Écrire dans les écrans : feuille de temps, disponibilités, AGPH, pointage, référentiels, milieux, quotas"
  "tests/e2e/effectifs-echanges.spec.ts|Échanger un quart : proposer, accepter, valider ou refuser"
  "tests/e2e/urgences-sante-messagerie.spec.ts|Messagerie et appels entre deux personnes, et en groupe"
)

if [ "${REJEU:-1}" = "1" ]; then
  rm -rf "$RACINE/e2e/test-results"
  FICHIERS=()
  for c in "${CHAPITRES[@]}"; do FICHIERS+=("${c%%|*}"); done
  E2E_VIDEO_LARGEUR="$LARGEUR" E2E_RALENTI="${RALENTI:-350}" E2E_VIDEO=on \
    "$RACINE/backend-java/scripts/campagne-e2e.sh" "${FICHIERS[@]}"
fi

# Les vidéos de chaque chapitre, dans l'ordre où elles ont été filmées.
LISTE="$TRAVAIL/liste.txt"; : > "$LISTE"
VTT="$SORTIE/demo-vina-geps.fr.vtt"; print "WEBVTT\n" > "$VTT"
debut=0
hms() { printf "%02d:%02d:%06.3f" $(( ${1%.*} / 3600 )) $(( ${1%.*} % 3600 / 60 )) $(( $1 - (${1%.*} / 60) * 60 )); }
for c in "${CHAPITRES[@]}"; do
  fichier="${c%%|*}"; titre="${c#*|}"; base="$(basename "$fichier" .spec.ts)"
  videos=("${(@f)$(ls -tr "$RACINE"/e2e/test-results/${base}-*/*.webm 2>/dev/null || true)}")
  [ -z "${videos[1]:-}" ] && { echo "✗ aucun film pour $fichier — le chapitre a-t-il été joué ?"; exit 1; }
  duree_chapitre=0
  for v in "${videos[@]}"; do
    print "file '$v'" >> "$LISTE"
    d=$(ffprobe -v error -show_entries format=duration -of csv=p=0 "$v")
    duree_chapitre=$(( duree_chapitre + d ))
  done
  fin=$(( debut + duree_chapitre ))
  print "$(hms $debut) --> $(hms $(( debut + 6 )))\n$titre\n" >> "$VTT"
  debut=$fin
done

ffmpeg -y -loglevel error -f concat -safe 0 -i "$LISTE" -c:v libvpx-vp9 -b:v 0 -crf 36 -row-mt 1 -an "$SORTIE/demo-vina-geps.webm"
echo "✓ $SORTIE/demo-vina-geps.webm ($(du -h "$SORTIE/demo-vina-geps.webm" | cut -f1)) — sous-titres : $VTT"
