#!/usr/bin/env bash
# **Exercice de reprise après sinistre** (AO 26-015-DO, P-TN-3) — joué sur le serveur, à côté de la démonstration en
# service, jamais à sa place.
#
#   deploy/geps/exercice-reprise.sh                    # la dernière copie de ~/sauvegardes/geps
#   deploy/geps/exercice-reprise.sh <archive.tar.gz>   # une copie précise
#
# 1. restaure la copie dans un volume neuf (vina-geps-reprise_donnees) ;
# 2. démarre la version en service sur ce volume, dans une pile isolée (aucun réseau public, aucun port publié) ;
# 3. mesure : intégrité de la base (PRAGMA integrity_check), santé, connexion du superadmin et lecture des organismes ;
#    compare au service vivant ; le point de reprise (âge de la copie) et le temps de reprise (restauration → service
#    vérifié) ;
# 4. écrit le rapport (Markdown) dans ~/sauvegardes/geps/exercices/ et sur la sortie standard ;
# 5. détruit la pile et le volume de l'exercice, même en cas d'échec.
# Code de sortie 0 si chaque vérification passe et que les objectifs (RPO, RTO) sont tenus, 1 sinon.
set -euo pipefail
ICI="$(cd "$(dirname "$0")" && pwd)"
cd "$ICI"
SAUVEGARDES="$HOME/sauvegardes/geps"
PROJET=vina-geps-reprise
CONTENEUR=geps-reprise-backend
VOLUME="${PROJET}_donnees"
# Les objectifs (docs/securite/PLAN-CONTINUITE-REPRISE.md) : reprise en 48 heures au plus (addenda 2, réponse 12).
# Le point de reprise de la production est de 15 minutes ; cette démonstration ne copie qu'à chaque déploiement, d'où
# un plafond d'une semaine ici — le rapport donne l'âge réel de la copie.
RPO_MAX_H="${RPO_MAX_H:-168}"
RTO_MAX_S="${RTO_MAX_S:-172800}"
DELAI_SANTE_S=300

[ -f .env.geps ] || { echo "❌ deploy/geps/.env.geps absent"; exit 2; }
VERSION="$(cat .version-courante 2>/dev/null || true)"
[ -n "$VERSION" ] || { echo "❌ aucune version en service (.version-courante)"; exit 2; }
ARCHIVE="${1:-$(ls -1t "$SAUVEGARDES"/donnees-*.tar.gz 2>/dev/null | head -1)}"
[ -n "$ARCHIVE" ] && [ -f "$ARCHIVE" ] || { echo "❌ aucune copie à restaurer dans $SAUVEGARDES"; exit 2; }

lire_env() { grep -E "^$1=" .env.geps | head -1 | cut -d= -f2-; }
SA_NOM="$(lire_env SUPERADMIN_USERNAME)"; SA_NOM="${SA_NOM:-superadmin}"
SA_MDP="$(lire_env SUPERADMIN_PASSWORD)"
# En résolution par sous-domaine, l'organisme se déduit de l'hôte : le superadmin se connecte sur l'hôte commun.
HOTE="$(lire_env DOMAINE_RACINE)"; HOTE="${HOTE:-localhost}"

nettoyer() {
  docker rm -f "$CONTENEUR" >/dev/null 2>&1 || true
  docker network rm "${PROJET}_interne" >/dev/null 2>&1 || true
  docker volume rm "$VOLUME" >/dev/null 2>&1 || true
}
trap nettoyer EXIT
nettoyer

constats=()
echec=0
noter() { constats+=("| $1 | $2 | $3 |"); [ "$2" = "✅" ] || echec=1; }

# Les organismes vus par le superadmin, sur une instance joignable depuis `conteneur` à `adresse`.
organismes() {
  local conteneur="$1" adresse="$2" jeton
  jeton="$(docker exec -e N="$SA_NOM" -e M="$SA_MDP" -e H="$HOTE" "$conteneur" sh -c \
    'curl -fsS -H "Host: $H" -H "Content-Type: application/json" -d "{\"username\":\"$N\",\"password\":\"$M\"}" '"$adresse"'/api/auth/login' \
    | sed -n 's/.*"token":"\([^"]*\)".*/\1/p')"
  [ -n "$jeton" ] || { echo "-1"; return; }
  docker exec -e J="$jeton" -e H="$HOTE" "$conteneur" sh -c 'curl -fsS -H "Host: $H" -H "Authorization: Bearer $J" '"$adresse"'/api/superadmin/tenants' \
    | grep -o '"modules":' | wc -l | tr -d ' '
}

DEBUT=$SECONDS
AGE_H=$(( ( $(date +%s) - $(stat -c %Y "$ARCHIVE") ) / 3600 ))
echo "▶ exercice de reprise : $(basename "$ARCHIVE") (âge ${AGE_H} h), version $VERSION"

# 1. Restaurer dans un volume neuf.
docker volume create "$VOLUME" >/dev/null
docker run --rm -v "$VOLUME":/donnees -v "$(dirname "$ARCHIVE")":/copies:ro alpine \
  sh -c "tar xzf '/copies/$(basename "$ARCHIVE")' -C /donnees"
INTEGRITE="$(docker run --rm -v "$VOLUME":/d alpine sh -c 'apk add -q --no-cache sqlite >/dev/null && sqlite3 /d/urgences-sante.db "PRAGMA integrity_check;"' 2>&1 | tail -1)"
[ "$INTEGRITE" = "ok" ] && noter "Intégrité de la base restaurée" "✅" "PRAGMA integrity_check : ok" \
  || noter "Intégrité de la base restaurée" "⛔" "$INTEGRITE"

# 2. Démarrer la version en service, isolée. Le réseau est **interne** : aucune sortie vers Internet, donc ni texto, ni
#    courriel, ni webhook ne part de la copie, même si son ordonnanceur trouve des envois en attente.
docker network create --internal "${PROJET}_interne" >/dev/null
docker run -d --name "$CONTENEUR" --network "${PROJET}_interne" --env-file .env.geps \
  -e PORT=4000 -e DB_URL=jdbc:sqlite:/app/data/urgences-sante.db -e NEXU_DONNEES=/app/data/fichiers \
  -e SAUVEGARDES_DOSSIER=/app/data/sauvegardes -e NEXU_VERSION="$VERSION" -e ORDONNANCEUR_INTERVALLE_MS=86400000 \
  -e JAVA_OPTS=-XX:MaxRAMPercentage=75 --memory 1g -v "$VOLUME":/app/data "vina-geps/backend:$VERSION" >/dev/null

fin=$((SECONDS + DELAI_SANTE_S))
until [ "$(docker inspect -f '{{.State.Health.Status}}' "$CONTENEUR" 2>/dev/null)" = "healthy" ] || [ $SECONDS -ge $fin ]; do sleep 5; done
if [ "$(docker inspect -f '{{.State.Health.Status}}' "$CONTENEUR")" = "healthy" ]; then
  noter "Santé de l'instance restaurée" "✅" "/api/health en $((SECONDS - DEBUT)) s"
else
  noter "Santé de l'instance restaurée" "⛔" "aucune réponse après ${DELAI_SANTE_S} s"
  docker logs --tail 60 "$CONTENEUR" 2>&1 || true
fi

# 3. Les données servent : le superadmin se connecte et lit les organismes ; comparaison avec le service vivant.
RESTAURES="$(organismes "$CONTENEUR" http://localhost:4000)"
VIVANTS="$(organismes geps-backend http://localhost:4000)"
if [ "$RESTAURES" -gt 0 ] 2>/dev/null; then
  noter "Connexion et lecture sur la copie" "✅" "superadmin connecté ; $RESTAURES organisme(s) restauré(s), $VIVANTS en service"
else
  noter "Connexion et lecture sur la copie" "⛔" "connexion refusée ou aucun organisme lu ($RESTAURES)"
fi

RTO_S=$((SECONDS - DEBUT))
[ "$AGE_H" -le "$RPO_MAX_H" ] && noter "Point de reprise (RPO)" "✅" "copie de ${AGE_H} h, objectif ≤ ${RPO_MAX_H} h" \
  || noter "Point de reprise (RPO)" "⛔" "copie de ${AGE_H} h, objectif ≤ ${RPO_MAX_H} h"
[ "$RTO_S" -le "$RTO_MAX_S" ] && noter "Temps de reprise (RTO)" "✅" "${RTO_S} s de la restauration au service vérifié, objectif ≤ ${RTO_MAX_S} s" \
  || noter "Temps de reprise (RTO)" "⛔" "${RTO_S} s, objectif ≤ ${RTO_MAX_S} s"

# 4. Le rapport.
mkdir -p "$SAUVEGARDES/exercices"
RAPPORT="$SAUVEGARDES/exercices/exercice-$(date +%Y%m%d-%H%M%S).md"
{
  echo "# Exercice de reprise — $(date '+%Y-%m-%d %H:%M %Z')"
  echo
  echo "- Copie restaurée : \`$(basename "$ARCHIVE")\` ($(du -h "$ARCHIVE" | cut -f1)), âge ${AGE_H} h"
  echo "- Version démarrée : \`$VERSION\`, pile isolée \`$PROJET\` (réseau interne, aucun port publié), détruite à la fin"
  echo "- Verdict : $([ $echec -eq 0 ] && echo '✅ reprise réussie, objectifs tenus' || echo '⛔ écart — voir les constats')"
  echo
  echo "| Vérification | Résultat | Mesure |"
  echo "|---|---|---|"
  printf '%s\n' "${constats[@]}"
} | tee "$RAPPORT"
chmod 600 "$RAPPORT"
exit $echec
