#!/usr/bin/env bash
# Tests d'intrusion de VINA GePS — le même lanceur sur le poste et dans le pipeline (.github/workflows/intrusion.yml).
#
#   securite/intrusion/lancer.sh                       # pile éphémère (secrets tirés au hasard, port 8097), tests, démontage
#   ZAP=1 securite/intrusion/lancer.sh                 # + balayage ZAP de base (passif) contre la pile
#   CIBLE=https://geps.vinaitsolution.com SUPERADMIN_PASSWORD=… securite/intrusion/lancer.sh
#                                                      # une pile déjà déployée — avec l'autorisation écrite de son exploitant
#
# Sortie : securite/intrusion/resultats/<horodatage>/ (journal, JUnit, rapport ZAP). Code de sortie non nul si un test échoue.
set -euo pipefail
ICI="$(cd "$(dirname "$0")" && pwd)"
RACINE="$(cd "$ICI/../.." && pwd)"
HORODATAGE="$(date +%Y%m%d-%H%M%S)"
SORTIE="$ICI/resultats/$HORODATAGE"
mkdir -p "$SORTIE"
PROJET=vina-intrusion
export US_WEB_PORT="${US_WEB_PORT:-8097}"
COMPOSE=(docker compose -f "$RACINE/docker-compose.yml" -f "$ICI/docker-compose.intrusion.yml" -p "$PROJET")
PILE_LOCALE=0

aleatoire() { openssl rand -base64 48 | tr -d '/+=\n' | cut -c1-32; }

if [ -z "${CIBLE:-}" ]; then
  PILE_LOCALE=1
  export JWT_SECRET="$(aleatoire)$(aleatoire)" SUPERADMIN_USERNAME=superadmin SUPERADMIN_PASSWORD="Sa!$(aleatoire)" ADMIN_PASSWORD="Ad!$(aleatoire)"
  export CIBLE="http://localhost:$US_WEB_PORT"
  trap '"${COMPOSE[@]}" logs --no-color backend > "$SORTIE/backend.log" 2>&1 || true; "${COMPOSE[@]}" down -v >/dev/null 2>&1 || true' EXIT
  echo "▶ pile éphémère $PROJET sur $CIBLE (secrets tirés au hasard, jamais écrits)"
  "${COMPOSE[@]}" up -d --build --wait --wait-timeout 420
fi
: "${SUPERADMIN_PASSWORD:?SUPERADMIN_PASSWORD attendu pour une cible déjà déployée}"

for i in $(seq 1 60); do curl -fsS "$CIBLE/api/health" >/dev/null 2>&1 && break; sleep 3; done
rm -f "$ICI"/.etat-*.local.json
echo "▶ tests scriptés FX-AO contre $CIBLE"
STATUT=0
( cd "$ICI" && mkdir -p resultats && node --test --test-concurrency=1 --test-reporter=spec --test-reporter-destination=stdout \
    --test-reporter=junit --test-reporter-destination="$SORTIE/junit-fx-ao.xml" fx-ao/*.test.mjs ) 2>&1 | tee "$SORTIE/tests-fx-ao.log" || STATUT=1

if [ "${ZAP:-0}" = "1" ]; then
  echo "▶ ZAP — balayage de base (passif, sans attaque active)"
  CIBLE_ZAP="$CIBLE"
  [ "$PILE_LOCALE" = 1 ] && CIBLE_ZAP="http://host.docker.internal:$US_WEB_PORT"
  # ZAP écrit sous son propre utilisateur (il configure son dossier personnel) : c'est le dossier de sortie qui s'ouvre.
  chmod 777 "$SORTIE"
  docker run --rm --add-host=host.docker.internal:host-gateway -v "$SORTIE:/zap/wrk:rw" \
    "${ZAP_IMAGE:-zaproxy/zap-stable:2.16.1}" zap-baseline.py -t "$CIBLE_ZAP/admin/login" \
    -r zap-rapport.html -J zap-rapport.json -I 2>&1 | tail -20 | tee "$SORTIE/zap.log" || true
fi
rm -f "$ICI"/.etat-*.local.json
echo "résultats : $SORTIE"
exit $STATUT
