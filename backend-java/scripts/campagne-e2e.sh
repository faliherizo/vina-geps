#!/bin/zsh
# Campagne e2e Playwright contre le backend **Java seul**, branché au front-end Vite — sur des
# ports privés, sans toucher à la pile Docker (:4000 / :8081).
#
#   backend-java/scripts/campagne-e2e.sh                 # toute la suite e2e
#   backend-java/scripts/campagne-e2e.sh tests/e2e/contracts.spec.ts   # un ou plusieurs fichiers
#
# Variables : E2E_API_PORT (4020), E2E_WEB_PORT (5299), E2E_JAVA_DOSSIER (répertoire de travail).
#
# La base est vierge : Liquibase la bâtit au démarrage du Java. Le Java démarre en environnement
# d'essai (NEXU_ESSAI=1 : routes _reset et _outbox), avec la cadence d'ordonnanceur de la campagne
# (3 s). Playwright lance le front Vite, dont le mandataire vise ce serveur.
set -e
RACINE="$(cd "$(dirname "$0")/../.." && pwd)"
API_PORT="${E2E_API_PORT:-4020}"
WEB_PORT="${E2E_WEB_PORT:-5299}"
D="${E2E_JAVA_DOSSIER:-${TMPDIR:-/tmp}/nexu-e2e-java}"
mkdir -p "$D/sauvegardes" "$D/data"

echo "▶ base vierge → $D/e2e.db (Liquibase la bâtit au démarrage)"
rm -f "$D/e2e.db" "$D/e2e.db-wal" "$D/e2e.db-shm"

echo "▶ backend Java (port $API_PORT, environnement d'essai)"
JAR=$(ls "$RACINE"/backend-java/target/nexu-vina-backend-*.jar 2>/dev/null | grep -v original | head -1)
if [ -z "$JAR" ]; then
  (cd "$RACINE/backend-java" && mvn -B -q -DskipTests -Djacoco.skip=true package)
  JAR=$(ls "$RACINE"/backend-java/target/nexu-vina-backend-*.jar | grep -v original | head -1)
fi
PORT="$API_PORT" NEXU_ESSAI=1 DB_URL="jdbc:sqlite:$D/e2e.db" ORDONNANCEUR_INTERVALLE_MS=3000 \
  CORS_ORIGINS="http://localhost:$WEB_PORT,http://localhost:5173,http://localhost:5174,http://localhost:5300" \
  SOUTIEN_COURRIEL=soutien@nexuvina.test SAUVEGARDES_DOSSIER="$D/sauvegardes" NEXU_DONNEES="$D/data" \
  REGION_DONNEES=canadaeast HEBERGEUR="OVH VPS (Beauharnois)" \
  java -jar "$JAR" > "$D/java.log" 2>&1 &
JAVA_PID=$!
trap 'echo "▶ arrêt du backend Java"; kill $JAVA_PID 2>/dev/null || true' EXIT
for i in $(seq 1 90); do
  sleep 2
  kill -0 $JAVA_PID 2>/dev/null || { echo "✗ le Java s'est arrêté au démarrage :"; tail -40 "$D/java.log"; exit 1; }
  [ "$(curl -s -o /dev/null -w '%{http_code}' "http://localhost:$API_PORT/api/health" 2>/dev/null || echo 000)" = "200" ] && break
done
echo "  santé : $(curl -s "http://localhost:$API_PORT/api/health")"

echo "▶ campagne e2e (front Vite :$WEB_PORT → Java :$API_PORT)"
cd "$RACINE/e2e"
E2E_API_PORT="$API_PORT" E2E_WEB_PORT="$WEB_PORT" npx playwright test --project=e2e --workers=1 --reporter=list,html,junit "$@"
