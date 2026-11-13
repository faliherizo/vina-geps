#!/usr/bin/env python3
"""
Codemod de la refonte visuelle : le titre de page et son sous-titre deviennent `EnTeteEcran`.

Forme reconnue (le sous-titre est facultatif ; l'émoji de tête est retiré, l'icône est choisie par écran) :
    <h1 data-testid="t">📐 Titre</h1>
    <p className="subtitle">…</p>
→   <EnTeteEcran titre="Titre" testId="t" description={<>…</>} />
Usage : python3 scripts/codemod-entetes.py src/Fichier.tsx…
"""
import re
import sys
from pathlib import Path

EMOJI = re.compile(r'^[\U0001F000-\U0001FAFF☀-➿⬀-⯿️‍🇦-🇿]+\s*')
MOTIF = re.compile(r'<h1( data-testid="[^"]+")?( id="[^"]+")?>(.*?)</h1>\s*(?:<p className="subtitle"( style=\{[^}]*\}\})?>(.*?)</p>)?', re.S)


def migrer(chemin: Path) -> int:
    src = chemin.read_text(encoding="utf-8")
    m = MOTIF.search(src)
    if not m or "EnTeteEcran" in src:
        return 0
    testid, ident, titre, _style, desc = m.groups()
    titre = EMOJI.sub("", titre.strip())
    if ident:
        titre_jsx = f'{{<span{ident}>{titre}</span>}}' if "{" in titre else f'{{<span{ident}>{titre}</span>}}'
    elif "{" in titre or "&amp;" in titre:
        titre_jsx = f'{{<>{titre}</>}}'
    else:
        titre_jsx = f'"{titre}"'
    attrs = [f'titre={titre_jsx}']
    if testid:
        attrs.append(f'testId="{testid.split(chr(34))[1]}"')
    if desc is not None:
        d = " ".join(desc.split())
        attrs.append(f'description={{<>{d}</>}}')
    rempl = f'<EnTeteEcran {" ".join(attrs)} />'
    out = src[:m.start()] + rempl + src[m.end():]
    prefixe = "./composants" if chemin.parent.name == "src" else "../composants"
    existant = re.search(r'import \{([^}]*)\} from "' + re.escape(prefixe) + r'";', out)
    if existant:
        out = out.replace(existant.group(0), 'import {' + existant.group(1).rstrip() + ', EnTeteEcran } from "' + prefixe + '";')
    else:
        mi = re.search(r'^import .*?;\n', out, re.M)
        out = out[:mi.end()] + f'import {{ EnTeteEcran }} from "{prefixe}";\n' + out[mi.end():]
    chemin.write_text(out, encoding="utf-8")
    return 1


if __name__ == "__main__":
    n = sum(migrer(Path(f)) for f in sys.argv[1:])
    print(f"{n} en-tête(s) migré(s)")
