#!/bin/zsh
# Produit les versions Word et PDF des documents de performance : le document de méthode, l'accord de niveau
# de service (docs/SLA) et, s'ils sont donnés, des rapports de résultats. Word par pandoc ; PDF par pandoc (HTML)
# et le Chromium de Playwright (perf/jmeter/pdf.cjs) — ni LaTeX ni LibreOffice requis.
#
#   perf/jmeter/build-docx.sh                                   # docs/PERFORMANCE-JMETER.{docx,pdf}, docs/SLA/ACCORD-*.{docx,pdf}
#   perf/jmeter/build-docx.sh perf/jmeter/rapports/local/<horodatage>/<plan>/RESULTATS.md
set -e
RACINE="$(cd "$(dirname "$0")/../.." && pwd)"
command -v pandoc >/dev/null || { echo "pandoc introuvable (brew install pandoc)"; exit 2; }
convertir() {
  local MD="$1"
  pandoc "$MD" --from gfm --to docx -o "${MD%.md}.docx" --resource-path="$(dirname "$MD")"
  node "$RACINE/perf/jmeter/pdf.cjs" "$MD" "${MD%.md}.pdf" >/dev/null
  echo "→ ${MD%.md}.docx · ${MD%.md}.pdf"
}
convertir "$RACINE/docs/PERFORMANCE-JMETER.md"
[ -f "$RACINE/docs/SLA/ACCORD-NIVEAU-SERVICE-PERFORMANCE.md" ] && convertir "$RACINE/docs/SLA/ACCORD-NIVEAU-SERVICE-PERFORMANCE.md"
for MD in "$@"; do convertir "$MD"; done
