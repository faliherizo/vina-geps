#!/usr/bin/env bash
# Déploie une version de VINA GePS sur le VPS — appelé par le pipeline (.github/workflows/deployer-geps.yml), ou à la main.
#
#   deploy/geps/deployer.sh <version>          # les images vina-geps/{backend,web}:<version> doivent être chargées
#   deploy/geps/deployer.sh --retour-arriere   # revient à la version précédente
#
# 1. sauvegarde le volume de données (serveur arrêté le temps de la copie : une base SQLite copiée en cours d'écriture
#    peut être incohérente) — ~/sauvegardes/geps, les 10 dernières ;
# 2. démarre la version ; 3. attend que le serveur Java ET le relais nginx répondent /api/health ;
# 4. en cas d'échec, revient à la version précédente et sort en erreur ; 5. ne garde que les images courante et précédente.
set -euo pipefail
ICI="$(cd "$(dirname "$0")" && pwd)"
cd "$ICI"
SAUVEGARDES="$HOME/sauvegardes/geps"
DELAI_SANTE_S=240

[ -f .env.geps ] || { echo "❌ deploy/geps/.env.geps absent — lancer deploy/geps/preparer-serveur.sh"; exit 2; }
PRECEDENTE="$(cat .version-courante 2>/dev/null || true)"

if [ "${1:-}" = "--retour-arriere" ]; then
  VERSION="$(cat .version-precedente 2>/dev/null || true)"
  [ -n "$VERSION" ] || { echo "❌ aucune version précédente connue"; exit 2; }
else
  VERSION="${1:?version attendue (sha court)}"
fi

composer() { GEPS_VERSION="$1" docker compose -p vina-geps --env-file .env.geps -f docker-compose.yml "${@:2}"; }

for image in backend web; do
  docker image inspect "vina-geps/$image:$VERSION" >/dev/null 2>&1 || { echo "❌ image vina-geps/$image:$VERSION absente (docker load)"; exit 2; }
done

sauvegarder() {
  docker volume inspect vina-geps_donnees >/dev/null 2>&1 || return 0
  mkdir -p "$SAUVEGARDES"
  docker stop geps-backend >/dev/null 2>&1 || true
  local archive="$SAUVEGARDES/donnees-$(date +%Y%m%d-%H%M%S)-${PRECEDENTE:-initiale}.tar.gz"
  # Le conteneur lit le volume en root (fichiers 600 de l'utilisateur du serveur Java) : c'est lui qui rend l'archive
  # à `deploy`, mode 600 — `deploy` ne pourrait ni la relire ni changer ses droits après coup.
  docker run --rm -v vina-geps_donnees:/donnees:ro -v "$SAUVEGARDES":/sortie alpine sh -c \
    "tar czf '/sortie/$(basename "$archive")' -C /donnees . && chown $(id -u):$(id -g) '/sortie/$(basename "$archive")' && chmod 600 '/sortie/$(basename "$archive")'"
  ls -1t "$SAUVEGARDES"/donnees-*.tar.gz | tail -n +11 | xargs -r rm -f
  echo "✓ sauvegarde : $archive"
}

en_sante() {
  local fin=$((SECONDS + DELAI_SANTE_S))
  while [ $SECONDS -lt $fin ]; do
    if [ "$(docker inspect -f '{{.State.Health.Status}}' geps-backend 2>/dev/null)" = "healthy" ] \
      && docker exec geps-web wget -qO- http://127.0.0.1:8080/api/health >/dev/null 2>&1; then
      return 0
    fi
    sleep 5
  done
  return 1
}

echo "▶ déploiement de $VERSION (précédente : ${PRECEDENTE:-aucune})"
sauvegarder
composer "$VERSION" up -d --remove-orphans

if ! en_sante; then
  echo "❌ $VERSION ne répond pas après ${DELAI_SANTE_S} s — journal du serveur :"
  docker logs --tail 80 geps-backend 2>&1 || true
  if [ -n "$PRECEDENTE" ] && [ "$PRECEDENTE" != "$VERSION" ]; then
    echo "↩ retour à $PRECEDENTE"
    composer "$PRECEDENTE" up -d --remove-orphans
    en_sante && echo "✓ $PRECEDENTE rétablie" || echo "❌ $PRECEDENTE ne répond pas non plus"
  fi
  exit 1
fi

if [ "$VERSION" != "$PRECEDENTE" ]; then
  [ -n "$PRECEDENTE" ] && echo "$PRECEDENTE" > .version-precedente
  echo "$VERSION" > .version-courante
fi
GARDER="$(cat .version-courante) $(cat .version-precedente 2>/dev/null || true)"
for ref in $(docker images --format '{{.Repository}}:{{.Tag}}' | grep '^vina-geps/'); do
  [[ " $GARDER " == *" ${ref##*:} "* ]] || docker rmi "$ref" >/dev/null 2>&1 || true
done
echo "✓ $VERSION en service — https://geps.vinaitsolution.com"
