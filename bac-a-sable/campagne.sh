#!/bin/zsh
# Campagne e2e des bacs à sable (FX-AO-20260810, critères 18 et 39) : le vrai serveur Java, branché sur le service de
# poussée du bac à sable par EXPO_PUSH_URL — sa variable de production —, et veraPDF officiel pour les PDF.
#
#   bac-a-sable/campagne.sh
#
# Variables : E2E_BAC_POUSSEE_URL (http://127.0.0.1:18499), E2E_BAC_VERAPDF_URL (http://127.0.0.1:18492),
# E2E_BAC_JETON_CONTROLE (si le bac à sable en exige un), et celles de backend-java/scripts/campagne-e2e.sh.
# Le service de poussée est démarré ici s'il ne répond pas (Node seul) ; veraPDF doit l'être par le compose.
set -e
RACINE="$(cd "$(dirname "$0")/.." && pwd)"
export E2E_BAC_POUSSEE_URL="${E2E_BAC_POUSSEE_URL:-http://127.0.0.1:18499}"
export E2E_BAC_VERAPDF_URL="${E2E_BAC_VERAPDF_URL:-http://127.0.0.1:18492}"

if ! curl -sf "$E2E_BAC_POUSSEE_URL/sante" >/dev/null 2>&1; then
  echo "▶ service de poussée du bac à sable (Node) sur ${E2E_BAC_POUSSEE_URL##*:}"
  PORT="${E2E_BAC_POUSSEE_URL##*:}" HOTE=127.0.0.1 BAC_JETON_CONTROLE="${E2E_BAC_JETON_CONTROLE:-}" \
    node "$RACINE/bac-a-sable/poussee/src/serveur.mjs" > "${TMPDIR:-/tmp}/bac-poussee.log" 2>&1 &
  POUSSEE_PID=$!
  trap 'kill $POUSSEE_PID 2>/dev/null || true' EXIT
  for i in $(seq 1 20); do curl -sf "$E2E_BAC_POUSSEE_URL/sante" >/dev/null 2>&1 && break; sleep 0.5; done
fi
curl -sf "$E2E_BAC_POUSSEE_URL/sante" >/dev/null || { echo "✗ le service de poussée ne répond pas"; exit 2; }

if ! curl -sf "$E2E_BAC_VERAPDF_URL/api/info" >/dev/null 2>&1; then
  echo "✗ veraPDF ne répond pas sur $E2E_BAC_VERAPDF_URL — docker compose -f bac-a-sable/docker-compose.yml up -d verapdf"
  [ "${BAC_SANS_VERAPDF:-0}" = "1" ] || exit 2
  echo "  BAC_SANS_VERAPDF=1 : la campagne continue ; les scénarios PDF/A échoueront en le disant"
fi

export EXPO_PUSH_URL="$E2E_BAC_POUSSEE_URL/--/api/v2/push/send"
export E2E_BAC_A_SABLE=1
"$RACINE/backend-java/scripts/campagne-e2e.sh" tests/e2e/fx-ao-bac-a-sable.spec.ts "$@"
