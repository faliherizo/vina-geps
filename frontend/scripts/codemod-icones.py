#!/usr/bin/env python3
"""
Codemod de la refonte visuelle : l'émoji en tête d'un bouton ou d'un titre devient une `Icone`.

    <button …>➕ Ajouter</button>     →  <button …><Icone nom="plus" /> Ajouter</button>
    <h3>📄 Contrats</h3>              →  <h3><Icone nom="document" /> Contrats</h3>
Un émoji change de dessin selon le système et n'a ni contraste ni taille de cible fiables ;
le tracé SVG hérite de la couleur du texte et reste décoratif (`aria-hidden`). Seuls les émojis
placés **en tête** du contenu d'un `button`, `h2`–`h4`, `summary`, `legend` ou `strong` sont touchés.
"""
import re
import sys
from pathlib import Path

CORRESPONDANCE = {
    "➕": "plus", "📄": "document", "⚠": "avertissement", "📤": "televerser", "📊": "graphique", "⬇": "telecharger", "📅": "calendrier",
    "✉": "courriel", "📧": "courriel", "🧾": "facture", "🔒": "cadenas", "📝": "modifier", "🏦": "banque", "✓": "coche", "✔": "coche",
    "⏳": "horloge", "🗑": "supprimer", "🔗": "lien", "📥": "telecharger", "📐": "reglages", "💾": "coche", "🧮": "graphique", "🔑": "cadenas",
    "🔄": "rafraichir", "📦": "dossier", "👁": "voir", "🎓": "etoile", "🌐": "globe", "⭐": "etoile", "✨": "etoile", "🤖": "graphique",
    "⬆": "televerser", "✗": "fermer", "🔍": "rechercher", "🖨": "imprimer", "☎": "telephone", "📞": "telephone", "🚪": "sortie",
    "🧑‍💼": "utilisateur", "📁": "dossier", "📒": "document", "🏫": "ecole", "👥": "personnes", "💵": "pieces", "🎫": "aide", "🛡": "bouclier",
    "🧩": "reglages", "📋": "document", "📑": "document", "📖": "document", "💡": "info", "🗺": "globe", "🕑": "horloge", "🌱": "etoile",
    "➤": "fleche-droite", "✍": "signature", "↻": "rafraichir", "📈": "hausse", "📉": "baisse", "🔔": "cloche", "🔐": "cadenas", "🧬": "reglages",
    "🏛": "banque", "🏘": "accueil", "⚖": "reglages", "🔁": "rafraichir", "🗓": "calendrier", "🚫": "fermer", "🤝": "personnes", "🏥": "personnes",
    "📆": "calendrier", "📇": "telephone", "💬": "courriel", "📜": "document", "🗄": "dossier", "🏗": "banque", "🎬": "etoile", "💳": "carte",
    "💱": "globe", "🧭": "fleche-droite", "🏝": "calendrier", "🌍": "globe", "🧑‍🤝‍🧑": "personnes", "🎛": "reglages", "🧪": "graphique", "📣": "cloche",
    "🌳": "personnes", "🪪": "carte", "🛟": "aide", "🛠": "reglages", "🔌": "lien", "💼": "projet", "🏠": "accueil", "🤗": "personnes", "🎯": "coche",
}
EMOJIS = sorted(CORRESPONDANCE, key=len, reverse=True)
MOTIF = re.compile(r'(<(?:button|h2|h3|h4|summary|legend|strong)\b[^>]*>)\s*(' + "|".join(re.escape(e) for e in EMOJIS) + r')️?\s+')


def migrer(chemin: Path) -> int:
    src = chemin.read_text(encoding="utf-8")
    out, n = MOTIF.subn(lambda m: f'{m.group(1)}<Icone nom="{CORRESPONDANCE[m.group(2)]}" /> ', src)
    if not n:
        return 0
    prefixe = "./composants" if chemin.parent.name == "src" else "../composants"
    existant = re.search(r'import \{([^}]*)\} from "' + re.escape(prefixe) + r'";', out)
    if existant:
        if "Icone" not in [x.strip() for x in existant.group(1).split(",")]:
            out = out.replace(existant.group(0), 'import {' + existant.group(1).rstrip() + ', Icone } from "' + prefixe + '";')
    else:
        mi = re.search(r'^import .*?;\n', out, re.M)
        out = out[:mi.end()] + f'import {{ Icone }} from "{prefixe}";\n' + out[mi.end():]
    chemin.write_text(out, encoding="utf-8")
    return n


if __name__ == "__main__":
    total = 0
    for f in sys.argv[1:]:
        k = migrer(Path(f))
        if k:
            print(f"{f}: {k}")
        total += k
    print(f"{total} icône(s)")
