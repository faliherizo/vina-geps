#!/usr/bin/env python3
"""
Codemod de la refonte visuelle : les messages « maison » deviennent le composant `Message`.

Ne touche que les formes **sur une ligne** dont le sens est sans ambiguïté :
  {x && <div className="errors" data-testid="t">{x}</div>}      → <Message variante="erreur" testId="t">
  <div className="alert alert-error" role="alert" data-testid="t">…</div>   → erreur
  <div className="alert alert-success" role="status" …>…</div>              → succes
  <div className="banner banner-error|banner-success|banner-warn" …>       → erreur | succes | avertissement
  <div className="info-box" data-testid="t">{x}</div>                       → info
  <p role="alert" className="error" …>⚠ {x}</p> / <p role="status" className="success" …>✔ {x}</p>
Les `data-testid` sont conservés ; l'import est ajouté s'il manque. Usage : python3 scripts/codemod-messages.py src/Fichier.tsx…
"""
import re
import sys
from pathlib import Path

REGLES = [
    (re.compile(r'<div className="errors"( data-testid="[^"]+")?>\{([^{}]+)\}</div>'), r'<Message variante="erreur"\1>{\2}</Message>'),
    (re.compile(r'<div className="alert alert-error" role="alert"( data-testid="[^"]+")?>(.+?)</div>'), r'<Message variante="erreur"\1>\2</Message>'),
    (re.compile(r'<div className="alert alert-success" role="status"( data-testid="[^"]+")?>(.+?)</div>'), r'<Message variante="succes"\1>\2</Message>'),
    (re.compile(r'<div className="banner banner-error" role="alert"( data-testid="[^"]+")?( tabIndex=\{0\})?>(?:⚠ )?(.+?)</div>'), r'<Message variante="erreur"\1>\3</Message>'),
    (re.compile(r'<div className="banner banner-success" role="status"( data-testid="[^"]+")?>(?:✓ |✔ )?(.+?)</div>'), r'<Message variante="succes"\1>\2</Message>'),
    (re.compile(r'<output className="banner banner-success"( data-testid="[^"]+")?>(?:✓ |✔ )?(.+?)</output>'), r'<Message variante="succes"\1>\2</Message>'),
    (re.compile(r'<div className="banner banner-warn" role="status"( data-testid="[^"]+")?>(.+?)</div>'), r'<Message variante="avertissement"\1>\2</Message>'),
    (re.compile(r'<div className="info-box"( data-testid="[^"]+")?>\{([^{}]+)\}</div>'), r'<Message variante="info"\1>{\2}</Message>'),
    (re.compile(r'<p role="alert" className="error"( data-testid="[^"]+")?>(?:⚠ )?\{([^{}]+)\}</p>'), r'<Message variante="erreur"\1>{\2}</Message>'),
    (re.compile(r'<p role="status" className="success"( data-testid="[^"]+")?>(?:✔ |✓ )?\{([^{}]+)\}</p>'), r'<Message variante="succes"\1>{\2}</Message>'),
    (re.compile(r'<p className="error" role="alert"( data-testid="[^"]+")?>\{([^{}]+)\}</p>'), r'<Message variante="erreur"\1>{\2}</Message>'),
    (re.compile(r'<p className="success" role="status"( data-testid="[^"]+")?>\{([^{}]+)\}</p>'), r'<Message variante="succes"\1>{\2}</Message>'),
]
# data-testid="x" → testId="x"
TESTID = re.compile(r'(<Message[^>]*?) data-testid="([^"]+)"')


def migrer(chemin: Path) -> int:
    src = chemin.read_text(encoding="utf-8")
    out = src
    n = 0
    for motif, remplacement in REGLES:
        out, k = motif.subn(remplacement, out)
        n += k
    out = TESTID.sub(r'\1 testId="\2"', out)
    if n and "Message" not in re.findall(r'import \{([^}]*)\} from "\.{1,2}/composants"', out).__str__():
        # ajoute l'import après le premier import
        m = re.search(r'^import .*?;\n', out, re.M)
        prefixe = "./composants" if chemin.parent.name == "src" else "../composants"
        existant = re.search(r'import \{([^}]*)\} from "' + re.escape(prefixe) + r'";', out)
        if existant:
            out = out.replace(existant.group(0), 'import {' + existant.group(1).rstrip() + ', Message } from "' + prefixe + '";')
        else:
            out = out[:m.end()] + f'import {{ Message }} from "{prefixe}";\n' + out[m.end():]
    if out != src:
        chemin.write_text(out, encoding="utf-8")
    return n


if __name__ == "__main__":
    total = 0
    for f in sys.argv[1:]:
        k = migrer(Path(f))
        if k:
            print(f"{f}: {k}")
        total += k
    print(f"{total} message(s) migré(s)")
