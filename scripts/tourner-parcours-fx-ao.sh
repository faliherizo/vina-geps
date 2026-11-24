#!/bin/zsh
# **Les parcours en vidéo de l'aide FX-AO** — joués sur l'organisme Clincom de la démonstration, racontés, puis montés.
#
#   scripts/tourner-parcours-fx-ao.sh                                  # démonstration publique
#   E2E_BASE_URL=https://autre.hote scripts/tourner-parcours-fx-ao.sh  # une autre pile déployée et semée
#   REJEU=0 scripts/tourner-parcours-fx-ao.sh                          # remonte sans rejouer
#   SEULEMENT=disponibilites scripts/tourner-parcours-fx-ao.sh         # certains parcours, par identifiant (virgules)
#
# L'essai `deploiement-fx-ao-videos.spec.ts` affirme ce qu'il montre : un parcours qui échoue n'est pas monté.
# Il écrit dans la démonstration (absence annulée à la fin, dépôt remplacé, alerte marquée « Exercice e2e »).
set -euo pipefail
RACINE="$(cd "$(dirname "$0")/.." && pwd)"
# Les titres des essais des parcours choisis, en expression : le filtre de Playwright porte sur le titre, pas l'identifiant.
FILTRE=""
if [ -n "${SEULEMENT:-}" ]; then
  FILTRE="$(node -e 'const [f, ids] = process.argv.slice(1);
    console.log(require(f).parcours.filter((p) => ids.split(",").includes(p.id)).map((p) => p.essai).join("|"))' \
    "$RACINE/e2e/donnees/fx-ao/videos-aide.json" "$SEULEMENT")"
fi
if [ "${REJEU:-1}" = "1" ]; then
  rm -rf "$RACINE/e2e/test-results"
  (cd "$RACINE/e2e" && E2E_BASE_URL="${E2E_BASE_URL:-https://geps.vinaitsolution.com}" E2E_COMPTES_DEMO=1 E2E_PARCOURS_DEMO=1 \
    E2E_RECIT=1 E2E_VIDEO_LARGEUR=1280 npx playwright test --project=e2e tests/e2e/deploiement-fx-ao-videos.spec.ts --workers=1 --reporter=line \
    ${FILTRE:+--grep "$FILTRE"})
fi
node "$RACINE/scripts/monter-parcours.mjs" ${SEULEMENT:+--seulement "$SEULEMENT"}
