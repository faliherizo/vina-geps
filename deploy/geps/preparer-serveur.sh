#!/usr/bin/env bash
# Prépare le VPS pour la démonstration geps.vinaitsolution.com — une seule fois, rejouable sans dommage.
#
#   cd ~/projets/vina-geps && deploy/geps/preparer-serveur.sh
#
# - crée deploy/geps/.env.geps depuis env.geps.exemple : chaque __ALEATOIRE__ devient un secret tiré au hasard, la plage
#   du réseau `web` est lue sur le serveur, le fichier passe en mode 600. Un .env.geps existant n'est JAMAIS réécrit
#   (changer JWT_SECRET fermerait les sessions ; changer SUPERADMIN_PASSWORD ne changerait rien à un compte existant) ;
# - vérifie le réseau `web` et le Nginx central.
# Aucun secret n'est affiché : pour lire celui du superadmin, `grep ^SUPERADMIN_PASSWORD= deploy/geps/.env.geps`.
set -euo pipefail
ICI="$(cd "$(dirname "$0")" && pwd)"
ENV_FICHIER="$ICI/.env.geps"

docker network inspect web >/dev/null 2>&1 || { echo "❌ réseau Docker « web » absent (docs/DEPLOIEMENT-GEPS-OVH.md §2)"; exit 2; }
docker inspect vina_nginx >/dev/null 2>&1 || echo "⚠️  conteneur vina_nginx introuvable : le site ne sera pas servi en HTTPS"

if [ -f "$ENV_FICHIER" ]; then
  echo "= $ENV_FICHIER existe déjà — laissé tel quel"
else
  PLAGE="$(docker network inspect web --format '{{range .IPAM.Config}}{{.Subnet}}{{end}}')"
  umask 077
  while IFS= read -r ligne; do
    while [[ "$ligne" == *__ALEATOIRE__* ]]; do
      # 32 octets en base64 sans « / + = » : utilisable tel quel dans un fichier d'environnement et une URL.
      SECRET="$(openssl rand -base64 48 | tr -d '/+=\n' | cut -c1-40)"
      ligne="${ligne/__ALEATOIRE__/$SECRET}"
    done
    [[ "$ligne" == GEPS_PROXIES_CONFIANCE=* ]] && ligne="GEPS_PROXIES_CONFIANCE=$PLAGE"
    printf '%s\n' "$ligne"
  done < "$ICI/env.geps.exemple" > "$ENV_FICHIER"
  chmod 600 "$ENV_FICHIER"
  echo "✓ $ENV_FICHIER créé (secrets tirés au hasard, réseau web : $PLAGE)"
fi

mkdir -p "$HOME/sauvegardes/geps"
echo "✓ serveur prêt — le pipeline « Déployer geps » peut livrer"
