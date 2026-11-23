#!/bin/zsh
# Campagne JMeter de l'API d'échange (26-015-DO, addenda 5 ; AD-06) : le serveur Java sur une base vierge, l'organisme
# semé avec un client d'API, un employé transmis par l'API et un volume de quarts et de relevés, puis les quatre
# opérations de l'accord à débit fixe, et leur verdict SLO.
#
#   perf/jmeter/campagne-api-echanges.sh [semaines=40] [duree_s=120]
#
# PERF_JAR : un autre JAR que celui de backend-java/target (p. ex. la version d'avant une optimisation, pour comparer).
# OPERATIONS : les opérations à jouer (défaut : les quatre ; une version d'avant AD-06b n'a que les trois lectures).
set -e
ICI="$(cd "$(dirname "$0")" && pwd)"
SEMAINES="${1:-40}"
DUREE="${2:-120}"
PORT="${E2E_API_PORT:-4020}"

"$ICI/backend-java-local.sh" &
JAVA_PID=$!
trap 'kill $JAVA_PID 2>/dev/null || true' EXIT  # backend-java-local.sh fait « exec java » : ce PID est le serveur
for i in $(seq 1 90); do
  sleep 2
  [ "$(curl -s -o /dev/null -w '%{http_code}' "http://localhost:$PORT/api/health" 2>/dev/null || echo 000)" = "200" ] && break
done
echo "▶ semis : 1 jeu, $SEMAINES semaines de quarts et de feuilles"
node "$ICI/preparer.cjs" --env local --lignes 1 --quarts "$SEMAINES"
T="$(cat "$ICI/donnees/tenant-local.txt")"
# Le secret du client d'API : écrit par preparer.cjs hors du CSV versionné, passé ici en propriété.
SECRET="$(cat "$ICI/donnees/secret-client-local.txt")"
for OP in ${=OPERATIONS:-LireQuartsParApi LireCorrectifsParApi LireRelevesParApi TransmettreSoldesParApi}; do
  "$ICI/lancer.sh" local "operations/$OP.jmx" -Jtenant="$T" -Jduree_operation="$DUREE" -JclientSecret="$SECRET" || true
done
