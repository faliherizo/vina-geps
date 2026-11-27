#!/usr/bin/env bash
# **Essai de panne et de reprise automatique** (AO 26-015-DO, E-TN-9) — joué sur le serveur de la démonstration.
#
#   deploy/geps/essai-bascule.sh
#
# Simule une panne franche du serveur Java (le processus tué, comme un plantage ou un manque de mémoire), sans
# intervention humaine ensuite : la politique de redémarrage le relance. L'essai mesure, du côté du relais web, la
# durée pendant laquelle /api/health ne répond pas, puis la rapporte au budget mensuel d'indisponibilité de l'addenda 6
# (99,7 % : 2 h 10 min par mois hors maintenance annoncée). Coupure attendue : environ une minute.
# Rapport : ~/sauvegardes/geps/exercices/bascule-<date>.md et sortie standard. Code 0 si le service revient seul.
set -euo pipefail
SAUVEGARDES="$HOME/sauvegardes/geps"
DELAI_MAX_S=600
# Addenda 6, réponse 9 : 99,7 % sur un mois de 30 jours.
BUDGET_MENSUEL_S=7776

sante() { docker exec geps-web wget -qO- -T 2 http://127.0.0.1:8080/api/health >/dev/null 2>&1; }

sante || { echo "❌ le service ne répond pas avant l'essai : rien à mesurer"; exit 2; }
REDEMARRAGES_AVANT="$(docker inspect -f '{{.RestartCount}}' geps-backend)"
echo "▶ panne simulée : le processus Java de geps-backend est tué (SIGKILL)"
# Depuis l'hôte : dans son conteneur, Java est le processus 1, qui ignore un SIGKILL venu de l'intérieur.
docker kill --signal KILL geps-backend >/dev/null

# La coupure commence quand la santé tombe, et finit quand elle revient.
premier_echec=""; retour=""
fin=$(( $(date +%s) + DELAI_MAX_S ))
while [ "$(date +%s)" -lt "$fin" ]; do
  if sante; then
    [ -n "$premier_echec" ] && { retour=$(date +%s.%N); break; }
  else
    [ -n "$premier_echec" ] || premier_echec=$(date +%s.%N)
  fi
  sleep 1
done

REDEMARRAGES_APRES="$(docker inspect -f '{{.RestartCount}}' geps-backend)"
mkdir -p "$SAUVEGARDES/exercices"
RAPPORT="$SAUVEGARDES/exercices/bascule-$(date +%Y%m%d-%H%M%S).md"
if [ -n "$retour" ]; then
  COUPURE=$(awk -v a="$premier_echec" -v b="$retour" 'BEGIN { printf "%.0f", b - a }')
  PANNES=$(( BUDGET_MENSUEL_S / (COUPURE > 0 ? COUPURE : 1) ))
  VERDICT="✅ reprise automatique en ${COUPURE} s, sans intervention"
  CODE=0
else
  COUPURE="> ${DELAI_MAX_S}"; PANNES=0
  VERDICT="⛔ le service n'est pas revenu seul en ${DELAI_MAX_S} s"
  CODE=1
fi
{
  echo "# Essai de panne et de reprise automatique — $(date '+%Y-%m-%d %H:%M %Z')"
  echo
  echo "- Panne simulée : processus Java tué (\`docker kill --signal KILL\`), aucune action humaine ensuite"
  echo "- Version : \`$(cat "$(dirname "$0")/.version-courante" 2>/dev/null || echo inconnue)\`"
  echo "- Redémarrages du conteneur : $REDEMARRAGES_AVANT → $REDEMARRAGES_APRES"
  echo "- Indisponibilité mesurée par le relais web (/api/health) : **${COUPURE} s**"
  echo "- Budget de l'addenda 6 (99,7 %, mois de 30 jours) : ${BUDGET_MENSUEL_S} s — soit **${PANNES}** pannes semblables par mois avant d'être en défaut"
  echo "- Verdict : $VERDICT"
} | tee "$RAPPORT"
chmod 600 "$RAPPORT"
exit $CODE
