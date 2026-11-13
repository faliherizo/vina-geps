#!/usr/bin/env python3
"""
Codemod de la refonte visuelle : la barre d'onglets « maison » des écrans RH devient `Onglets`.

Forme reconnue :
    <div className="tabs" role="tablist" aria-label="…">
      {([["k", "Libellé"], …] as const).map(([k, l]) => (
        <button key={k} role="tab" type="button" id={`<p>-tab-${k}`} data-testid={`<p>-tab-${k}`} … onClick={() => setOnglet(k)}>…</button>
      ))}
    </div>
→   <Onglets libelle="…" actif={onglet} onChange={setOnglet} prefixeIds="<p>" onglets={[…]} />
et les panneaux `id="<p>-panel-k" aria-labelledby="<p>-tab-k"` → `id="<p>-tabpanel-k" aria-labelledby="<p>-tabbtn-k"`.
"""
import re
import sys
from pathlib import Path

BLOC = re.compile(
    r'<div className="tabs" role="tablist" aria-label="([^"]+)">\s*'
    r'\{\(\[(.*?)\] as const\)\.map\(\(\[k, l\]\) => \(\s*'
    r'<button key=\{k\} role="tab" type="button" id=\{`([a-z]+)-tab-\$\{k\}`\}.*?'
    r'onClick=\{\(\) => (?:setOnglet\(k\)|\{ setOnglet\(k\);([^}]*)\})\}>\s*\{l\}\s*</button>\s*\)\)\}\s*</div>',
    re.S,
)
PAIRE = re.compile(r'\["([a-z-]+)", "([^"]+)"\]')


def migrer(chemin: Path) -> int:
    src = chemin.read_text(encoding="utf-8")
    m = BLOC.search(src)
    if not m:
        return 0
    libelle, paires, prefixe, effets = m.groups()
    onglets = ", ".join(f'{{ id: "{k}", libelle: "{l}", testId: "{prefixe}-tab-{k}" }}' for k, l in PAIRE.findall(paires))
    on_change = "setOnglet" if not effets else f"(k) => {{ setOnglet(k);{effets} }}"
    rempl = f'<Onglets libelle="{libelle}" actif={{onglet}} onChange={{{on_change}}} prefixeIds="{prefixe}" onglets={{[{onglets}]}} />'
    out = src[:m.start()] + rempl + src[m.end():]
    out = re.sub(rf'id="{prefixe}-panel-([a-z-]+)" aria-labelledby="{prefixe}-tab-([a-z-]+)"', rf'id="{prefixe}-tabpanel-\1" aria-labelledby="{prefixe}-tabbtn-\2"', out)
    out = re.sub(rf'id=\{{`{prefixe}-panel-\$\{{([a-z]+)\}}`\}} aria-labelledby=\{{`{prefixe}-tab-\$\{{([a-z]+)\}}`\}}', rf'id={{`{prefixe}-tabpanel-${{\1}}`}} aria-labelledby={{`{prefixe}-tabbtn-${{\2}}`}}', out)
    prefixe_import = "./composants" if chemin.parent.name == "src" else "../composants"
    existant = re.search(r'import \{([^}]*)\} from "' + re.escape(prefixe_import) + r'";', out)
    if existant and "Onglets" not in existant.group(1):
        out = out.replace(existant.group(0), 'import {' + existant.group(1).rstrip() + ', Onglets } from "' + prefixe_import + '";')
    elif not existant:
        mi = re.search(r'^import .*?;\n', out, re.M)
        out = out[:mi.end()] + f'import {{ Onglets }} from "{prefixe_import}";\n' + out[mi.end():]
    chemin.write_text(out, encoding="utf-8")
    return 1


if __name__ == "__main__":
    print(sum(migrer(Path(f)) for f in sys.argv[1:]), "barre(s) d'onglets migrée(s)")
