#!/bin/zsh
# Lance une campagne JMeter (mode ligne de commande, jamais l'interface) contre un environnement,
# puis mesure les SLI et rend le verdict SLO/SLA (analyser.cjs) — la commande échoue si un
# objectif est manqué.
#
#   perf/jmeter/lancer.sh <env> <plan|scenarios|endpoints|operations|tous> [-Jcle=valeur …]
#
#   perf/jmeter/lancer.sh local scenarios/parcours-feuille-de-temps.jmx
#   perf/jmeter/lancer.sh local endpoints                 # tous les plans plans/endpoints-*.jmx
#   perf/jmeter/lancer.sh preprod scenarios -Jutilisateurs=2500 -Jduree=1200
#   perf/jmeter/lancer.sh prod scenarios/sonde-lecture.jmx
#   perf/jmeter/lancer.sh local operations                # l'accord de niveau de service : une opération à la fois, débit fixe
#
# Résultats : resultats/<env>/<horodatage>/<plan>.jtl ; rapports : rapports/<env>/<horodatage>/<plan>/
# (tableau de bord HTML de JMeter avec Apdex, RESULTATS.md et RESULTATS.docx de l'analyse).
set -e
ICI="$(cd "$(dirname "$0")" && pwd)"
ENV="${1:?environnement attendu : local | dev | preprod | prod}"
CIBLE="${2:?plan attendu : scenarios/x.jmx | plans/endpoints-x.jmx | operations/x.jmx | scenarios | endpoints | operations | tous}"
shift 2
PROPS="$ICI/env/$ENV.properties"
[ -f "$PROPS" ] || { echo "environnement inconnu : $ENV (voir $ICI/env/)"; exit 2; }
command -v jmeter >/dev/null || { echo "jmeter introuvable (brew install jmeter, ou https://jmeter.apache.org/)"; exit 2; }

# En production, rien n'écrit : la propriété est forcée, quoi que dise la ligne de commande.
EXTRA=("$@")
if [ "$ENV" = "prod" ]; then EXTRA+=("-Jecritures=false"); fi

HORODATAGE="$(date +%Y%m%d-%H%M%S)"
case "$CIBLE" in
  scenarios) PLANS=("$ICI"/scenarios/*.jmx) ;;
  endpoints) PLANS=("$ICI"/plans/endpoints-*.jmx) ;;
  # Les opérations dans l'ordre du catalogue (lectures d'abord, écritures en dernier), pas l'ordre alphabétique.
  operations) PLANS=($(node -e 'for (const o of require(process.argv[1]).operations) console.log(process.argv[2] + "/operations/" + o.nom + ".jmx")' "$ICI/operations.json" "$ICI")) ;;
  tous)      PLANS=("$ICI"/scenarios/*.jmx "$ICI"/plans/endpoints-*.jmx) ;;
  *)         PLANS=("$ICI/$CIBLE") ;;
esac

ECHECS=0
for PLAN in "${PLANS[@]}"; do
  NOM="$(basename "$PLAN" .jmx)"
  RES="$ICI/resultats/$ENV/$HORODATAGE"; RAP="$ICI/rapports/$ENV/$HORODATAGE/$NOM"
  mkdir -p "$RES" "$RAP"
  echo "▶ $ENV › $NOM"
  # -n : sans interface ; -t : le plan ; -q : les propriétés de l'environnement ; -l : les résultats ;
  # -e -o : le tableau de bord HTML (Apdex : seuils satisfait / toléré de l'environnement).
  APDEX_S="$(grep '^apdex_satisfait=' "$PROPS" | cut -d= -f2)"; APDEX_T="$(grep '^apdex_tolere=' "$PROPS" | cut -d= -f2)"
  ( cd "$ICI" && jmeter -n -t "$PLAN" -q "$PROPS" -l "$RES/$NOM.jtl" -j "$RES/$NOM.log" -e -o "$RAP/tableau-de-bord" \
      -Jjmeter.save.saveservice.output_format=csv -Jjmeter.save.saveservice.response_data=false \
      -Jjmeter.reportgenerator.apdex_satisfied_threshold="${APDEX_S:-500}" -Jjmeter.reportgenerator.apdex_tolerated_threshold="${APDEX_T:-1500}" \
      "${EXTRA[@]}" ) | grep -E "summary =|Err:|Tidying" | tail -3
  node "$ICI/analyser.cjs" "$RES/$NOM.jtl" --env "$ENV" --sortie "$RAP" || ECHECS=$((ECHECS + 1))
done
echo "rapports : $ICI/rapports/$ENV/$HORODATAGE/"
[ "$ECHECS" -eq 0 ] || { echo "❌ $ECHECS plan(s) hors objectif"; exit 1; }
