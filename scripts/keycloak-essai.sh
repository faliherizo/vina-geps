#!/bin/zsh
# Keycloak d'essai pour l'OIDC de VINA GePS (ADR 0075, docs/urgences-sante/OIDC-KEYCLOAK.md).
#
#   scripts/keycloak-essai.sh start    # démarre Keycloak sur :8180 et importe e2e/donnees/keycloak/realm-vina-geps.json
#   scripts/keycloak-essai.sh stop     # arrête et supprime le conteneur (le royaume est réimporté au prochain départ)
#   scripts/keycloak-essai.sh status
#
# Variables : KEYCLOAK_PORT (8180), KEYCLOAK_IMAGE (quay.io/keycloak/keycloak:26.8).
# Le conteneur est éphémère : chaque départ repart du royaume versionné, sans état d'un essai précédent.
# Les mots de passe de ce royaume sont des valeurs d'essai, jamais à reprendre ailleurs.
set -e
RACINE="$(cd "$(dirname "$0")/.." && pwd)"
PORT="${KEYCLOAK_PORT:-8180}"
IMAGE="${KEYCLOAK_IMAGE:-quay.io/keycloak/keycloak:26.8}"
NOM="urgences-sante-keycloak-essai"
ROYAUME="$RACINE/e2e/donnees/keycloak"

attendre() {
  for i in $(seq 1 90); do
    [ "$(curl -s -o /dev/null -w '%{http_code}' "http://localhost:$PORT/realms/vina-geps/.well-known/openid-configuration" 2>/dev/null || echo 000)" = "200" ] && {
      echo "✓ Keycloak prêt : http://localhost:$PORT/realms/vina-geps (console : http://localhost:$PORT/admin, admin / admin)"; return 0; }
    sleep 2
  done
  echo "✗ Keycloak n'a pas répondu en 3 minutes :"; docker logs --tail 40 "$NOM"; return 1
}

case "${1:-start}" in
  start)
    docker rm -f "$NOM" >/dev/null 2>&1 || true
    docker run -d --name "$NOM" -p "$PORT:8080" \
      -e KC_BOOTSTRAP_ADMIN_USERNAME=admin -e KC_BOOTSTRAP_ADMIN_PASSWORD=admin \
      -v "$ROYAUME/realm-vina-geps.json:/opt/keycloak/data/import/realm-vina-geps.json:ro" \
      "$IMAGE" start-dev --import-realm --hostname "http://localhost:$PORT" >/dev/null
    attendre ;;
  stop)
    docker rm -f "$NOM" >/dev/null 2>&1 && echo "✓ Keycloak d'essai arrêté" || echo "Keycloak d'essai déjà arrêté" ;;
  status)
    docker ps --filter "name=$NOM" --format '{{.Names}} {{.Status}} {{.Ports}}' ;;
  *)
    echo "usage : $0 start|stop|status"; exit 2 ;;
esac
