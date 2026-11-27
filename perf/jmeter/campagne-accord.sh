#!/bin/zsh
# La campagne de l'accord de niveau de service, de bout en bout, sur le jar Java local :
#   base neuve → contexte typique (11 opérations × duree_operation à debit req/s) → profil volumineux → mêmes
#   opérations → docs/SLA/ACCORD-NIVEAU-SERVICE-PERFORMANCE.{md,docx,pdf}.
#
#   perf/jmeter/campagne-accord.sh                 # ≈ 2 × 9 × 10 min sur la machine de développement
#   PERF_DUREE=60 perf/jmeter/campagne-accord.sh   # répétition rapide (1 min par opération) — pas la référence
#
# Jamais la pile Docker (:4000 / :8081) : le Java tourne sur E2E_API_PORT (4020).
set -e
# La campagne dure des heures sur une machine « au repos » — qui s'endort. Un Mac endormi gèle JMeter et le
# serveur : les plans s'étirent sur une heure, une requête en vol expire (« Read timed out »), le débit tombe
# à 0,1 req/s. La campagne du 16 septembre l'a payé. On se relance sous caffeinate (veille, disque, écran, système).
if [ -z "${NEXU_CAFFEINATE:-}" ] && command -v caffeinate >/dev/null 2>&1; then
  NEXU_CAFFEINATE=1 exec caffeinate -dims "$0" "$@"
fi
RACINE="$(cd "$(dirname "$0")/../.." && pwd)"
ICI="$RACINE/perf/jmeter"
API_PORT="${E2E_API_PORT:-4020}"
DUREE="${PERF_DUREE:-600}"
JOURNAL="$ICI/resultats/local/campagne-accord.log"
mkdir -p "$ICI/resultats/local"

demarrer_java() {
  pkill -f "nexu-vina-backend" 2>/dev/null || true
  sleep 2
  "$ICI/backend-java-local.sh" > "$ICI/resultats/local/java-accord.log" 2>&1 &
  for i in $(seq 1 60); do
    sleep 3
    [ "$(curl -s -m 2 -o /dev/null -w '%{http_code}' "http://localhost:$API_PORT/api/health" 2>/dev/null || echo 000)" = "200" ] && return 0
  done
  echo "le backend Java ne répond pas sur :$API_PORT" >&2; exit 1
}

echo "▶ $(date +%H:%M) base neuve, backend Java :$API_PORT" | tee "$JOURNAL"
demarrer_java
node "$ICI/preparer.cjs" --env local --lignes 3 | tee -a "$JOURNAL"

echo "▶ $(date +%H:%M) contexte typique" | tee -a "$JOURNAL"
"$ICI/lancer.sh" local operations -Jduree_operation="$DUREE" -JclientSecret="$(cat "$ICI/donnees/secret-client-local.txt")" 2>&1 | grep -E "▶|summary =|✅|❌|rapports :" | tee -a "$JOURNAL" || true
TYPIQUE="$(ls -td "$ICI"/rapports/local/*/ | head -1)"

# Base neuve avant le profil volumineux : les écritures du contexte typique (12 000 saisies, 12 000 demandes
# d'absence à 20 req/s pendant 600 s) resteraient sinon dans la base, et les lectures « volumineuses »
# mesureraient 12 450 demandes au lieu des 450 du profil — c'est ce qui a fait sortir ObtenirDemandesAbsence
# et ObtenirAbsencesUnifiees de l'accord le 17 septembre 2026, à tort.
echo "▶ $(date +%H:%M) base neuve, profil volumineux" | tee -a "$JOURNAL"
demarrer_java
node "$ICI/preparer.cjs" --env local --profil volumineux | tee -a "$JOURNAL"
node "$ICI/preparer.cjs" --env local --lignes 3 | tee -a "$JOURNAL"

echo "▶ $(date +%H:%M) contexte volumineux" | tee -a "$JOURNAL"
"$ICI/lancer.sh" local operations -Jduree_operation="$DUREE" -JclientSecret="$(cat "$ICI/donnees/secret-client-local.txt")" 2>&1 | grep -E "▶|summary =|✅|❌|rapports :" | tee -a "$JOURNAL" || true
VOLUMINEUX="$(ls -td "$ICI"/rapports/local/*/ | head -1)"

echo "▶ $(date +%H:%M) documents" | tee -a "$JOURNAL"
node "$ICI/accord.cjs" --typique "$TYPIQUE" --volumineux "$VOLUMINEUX" --env local | tee -a "$JOURNAL"
"$ICI/build-docx.sh" | tee -a "$JOURNAL"
pkill -f "nexu-vina-backend" 2>/dev/null || true
echo "▶ $(date +%H:%M) terminé : docs/SLA/ACCORD-NIVEAU-SERVICE-PERFORMANCE.{md,docx,pdf}" | tee -a "$JOURNAL"
