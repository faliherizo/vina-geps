#!/usr/bin/env bash
# Valide les échantillons PDF/A-2b du produit avec veraPDF (FX-AO-20260810, ST-G10, critère 39).
# Les échantillons sont déposés par PdfAConformiteTest dans backend-java/target/pdfa/ : lancer `mvn verify` d'abord.
# veraPDF tourne en conteneur (image officielle) ; sans Docker, le script échoue en le disant — il ne passe jamais à vide.
set -euo pipefail

DOSSIER="${1:-backend-java/target/pdfa}"
IMAGE="${VERAPDF_IMAGE:-verapdf/cli:latest}"

shopt -s nullglob
fichiers=("$DOSSIER"/*.pdf)
if [ ${#fichiers[@]} -eq 0 ]; then
  echo "aucun échantillon PDF dans $DOSSIER : lancez d'abord mvn verify" >&2
  exit 2
fi
if ! command -v docker >/dev/null 2>&1; then
  echo "Docker absent : veraPDF ne peut pas valider les échantillons ici" >&2
  exit 2
fi

absolu="$(cd "$DOSSIER" && pwd)"
echec=0
for f in "${fichiers[@]}"; do
  nom="$(basename "$f")"
  rapport="$(docker run --rm -v "$absolu:/data:ro" "$IMAGE" --flavour 2b --format text "/data/$nom" 2>&1 || true)"
  echo "$rapport"
  if ! grep -q "^PASS" <<<"$rapport"; then
    echo "✗ $nom : non conforme PDF/A-2b" >&2
    echec=1
  fi
done
exit $echec
