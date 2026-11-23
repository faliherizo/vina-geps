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
# Avec VERAPDF_URL, le bac à sable veraPDF REST (bac-a-sable/docker-compose.yml) valide les échantillons.
if [ -n "${VERAPDF_URL:-}" ]; then
  echec=0
  for f in "${fichiers[@]}"; do
    nom="$(basename "$f")"
    rapport="$(curl -sf -X POST -H "Accept: application/json" -F "file=@$f" "$VERAPDF_URL/api/validate/2b")" \
      || { echo "✗ veraPDF injoignable sur $VERAPDF_URL" >&2; exit 2; }
    if python3 -c "import json,sys; v=json.loads(sys.argv[1])['report']['jobs'][0]['validationResult'][0]; print(sys.argv[2], 'PASS' if v['compliant'] else 'FAIL', v['details']['passedRules'], 'règles passées,', v['details']['failedRules'], 'en échec'); sys.exit(0 if v['compliant'] else 1)" "$rapport" "$nom"; then
      :
    else
      echo "✗ $nom : non conforme PDF/A-2b" >&2
      echec=1
    fi
  done
  exit $echec
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
