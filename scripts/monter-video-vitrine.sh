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

# Les chapitres, dans l'ordre du film : « fichier d'essai|titre affiché ». Deux avis : Urgences-santé (26-015-DO,
# gestion des effectifs et paie) puis Santé Québec Laurentides (FX-AO-20260810, communications cliniques). Chaque
# chapitre est un essai qui affirme ce qu'il montre ; le film dure plus de 25 minutes au ralenti par défaut.
CHAPITRES=(
  "tests/e2e/urgences-sante-demonstration.spec.ts|Urgences-santé — l'organisme de démonstration : tableau de bord, quarts du jour, sites"
  "tests/e2e/effectifs-socle.spec.ts|Socle des effectifs : référentiels datés, unités, ressources, périmètres"
  "tests/e2e/effectifs-planification.spec.ts|Planification : périodes, postes, quarts ad hoc, attribution et règles"
  "tests/e2e/effectifs-disponibilites.spec.ts|Disponibilités : collecte, dépôt, conformité, candidats d'une place"
  "tests/e2e/effectifs-absences.spec.ts|Absences : demande, refus motivé, dérogation, places rendues vacantes"
  "tests/e2e/effectifs-mes-absences.spec.ts|Mes absences : le personnel déclare, suit et annule"
  "tests/e2e/effectifs-echanges.spec.ts|Échanger un quart : proposer, accepter, valider ou refuser"
  "tests/e2e/effectifs-agph.spec.ts|AGPH : processus annuel, choix par ancienneté, remplacements long terme"
  "tests/e2e/effectifs-presence.spec.ts|Présence et pointage : pointer, anomalies, approbation"
  "tests/e2e/effectifs-temps.spec.ts|Feuilles de temps : segments, heures supplémentaires, approbation à paliers"
  "tests/e2e/effectifs-regles.spec.ts|Moteur de règles : conditions, actions, motifs nommés"
  "tests/e2e/effectifs-releves.spec.ts|Relevés et rapports : regroupés, exportés"
  "tests/e2e/effectifs-integrations.spec.ts|Intégrations : lot de paie codé et signé, horodatages externes"
  "tests/e2e/mon-equipe-suppleance.spec.ts|Mon équipe et suppléance"
  "tests/e2e/urgences-sante-saisies.spec.ts|Écrire dans les écrans : feuille de temps, disponibilités, AGPH, pointage, référentiels, milieux, quotas"
  "tests/e2e/urgences-sante-messagerie.spec.ts|Messagerie et appels entre deux personnes, et en groupe"
  "tests/e2e/notifications-evenements.spec.ts|Avis d'événements : préférences par événement, canaux obligatoires"
  "tests/e2e/paie.spec.ts|Paie québécoise : dossier, cycle préparé et traité, remises, bulletin lu par l'employée"
  "tests/e2e/urgences-sante-exigences.spec.ts|Les 90 exigences fonctionnelles du 26-015-DO, écran par écran, axe-core à chacun"
  "tests/e2e/urgences-sante-alertes.spec.ts|FX-AO — paramétrer, envoyer, prendre en charge, suivre ; cascade et reprise"
  "tests/e2e/fx-ao-parcours.spec.ts|FX-AO — copie par courriel, annuaire LDAPS, utilisateurs actifs, relances, note, export"
  "tests/e2e/fx-ao-exigences.spec.ts|FX-AO — les 32 critères fonctionnels de la grille, écran par écran"
)
if [ "${REJEU:-1}" = "1" ]; then
  rm -rf "$RACINE/e2e/test-results"
  FICHIERS=()
  for c in "${CHAPITRES[@]}"; do FICHIERS+=("${c%%|*}"); done
  E2E_VIDEO_LARGEUR="$LARGEUR" E2E_RALENTI="${RALENTI:-600}" E2E_VIDEO=on \
    "$RACINE/backend-java/scripts/campagne-e2e.sh" "${FICHIERS[@]}"
fi

# Les vidéos de chaque chapitre, dans l'ordre où elles ont été filmées.
LISTE="$TRAVAIL/liste.txt"; : > "$LISTE"
VTT="$SORTIE/demo-vina-geps.fr.vtt"; print "WEBVTT\n" > "$VTT"
debut=0
hms() { printf "%02d:%02d:%06.3f" $(( ${1%.*} / 3600 )) $(( ${1%.*} % 3600 / 60 )) $(( $1 - (${1%.*} / 60) * 60 )); }
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
  fin=$(( debut + duree_chapitre ))
  print "$(hms $debut) --> $(hms $(( debut + 6 )))\n$titre\n" >> "$VTT"
  debut=$fin
done

ffmpeg -y -loglevel error -f concat -safe 0 -i "$LISTE" -c:v libvpx-vp9 -b:v 0 -crf 36 -row-mt 1 -an "$SORTIE/demo-vina-geps.webm"
duree=$(ffprobe -v error -show_entries format=duration -of csv=p=0 "$SORTIE/demo-vina-geps.webm")
# Le film est promis « en détail » : plus court que DUREE_MIN (25 min par défaut), un chapitre a manqué.
if (( ${duree%.*} < ${DUREE_MIN:-1500} )); then
  echo "✗ le film dure ${duree%.*} s, moins que ${DUREE_MIN:-1500} s : un chapitre manque-t-il ?"; exit 1
fi
echo "✓ $SORTIE/demo-vina-geps.webm ($(du -h "$SORTIE/demo-vina-geps.webm" | cut -f1), $(( ${duree%.*} / 60 )) min) — sous-titres : $VTT"
