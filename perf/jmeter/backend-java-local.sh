#!/bin/zsh
# Lance le serveur Java sur un port privé pour une campagne JMeter locale — la recette de
# backend-java/scripts/campagne-e2e.sh (base vierge que Liquibase bâtit, mode d'essai), sans
# Playwright. Jamais la pile Docker.
#
#   perf/jmeter/backend-java-local.sh            # port 4020 (E2E_API_PORT), reste au premier plan ; Ctrl-C l'arrête
#   PERF_JAVA_DOSSIER=/tmp/nexu-perf perf/jmeter/backend-java-local.sh
set -e
RACINE="$(cd "$(dirname "$0")/../.." && pwd)"
API_PORT="${E2E_API_PORT:-4020}"
D="${PERF_JAVA_DOSSIER:-${TMPDIR:-/tmp}/nexu-perf-java}"
mkdir -p "$D/sauvegardes" "$D/data"
echo "▶ base vierge → $D/perf.db (Liquibase la bâtit au démarrage)"
rm -f "$D/perf.db" "$D/perf.db-wal" "$D/perf.db-shm"
# PERF_JAR : mesurer un autre JAR (une version antérieure, pour comparer avant et après une optimisation).
JAR="${PERF_JAR:-$(ls "$RACINE"/backend-java/target/nexu-vina-backend-*.jar 2>/dev/null | grep -v original | head -1)}"
if [ -z "$JAR" ]; then
  (cd "$RACINE/backend-java" && mvn -B -q -DskipTests -Djacoco.skip=true package)
  JAR=$(ls "$RACINE"/backend-java/target/nexu-vina-backend-*.jar | grep -v original | head -1)
fi
echo "▶ backend Java (port $API_PORT, environnement d'essai) — journal : $D/java.log"
# Le pare-feu applicatif limite chaque adresse IP (WAF_REQUETES_PAR_MINUTE, 20 000 par défaut) : une campagne
# de charge vient d'une seule adresse et le franchirait en quelques secondes — 429, puis la connexion des plans
# suivants refusée. On l'élargit ici ; en préproduction, dimensionner la limite aux adresses des générateurs.
PORT="$API_PORT" NEXU_ESSAI=1 DB_URL="jdbc:sqlite:$D/perf.db" ORDONNANCEUR_INTERVALLE_MS=3000 WAF_REQUETES_PAR_MINUTE="${WAF_REQUETES_PAR_MINUTE:-10000000}" \
  SOUTIEN_COURRIEL=soutien@nexuvina.test SAUVEGARDES_DOSSIER="$D/sauvegardes" NEXU_DONNEES="$D/data" REGION_DONNEES="${REGION_DONNEES:-canadaeast}" \
  exec java -jar "$JAR" > "$D/java.log" 2>&1
