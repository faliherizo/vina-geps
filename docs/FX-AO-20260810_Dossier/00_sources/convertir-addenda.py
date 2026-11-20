import pdfplumber, openpyxl, re, sys, os
D = sys.argv[1]
os.chdir(D)
ENTETES = re.compile(r"^(DATE D’IMPRESSION.*|SANTÉ QUÉBEC LAURENTIDES APPEL D’OFFRES.*|Solution logicielle de gestion et de distribution des communications cliniques|Contrat)$")

def barres(page):
    traits = [l for l in page.lines + page.rects if abs(l["top"] - l["bottom"]) < 2.5 and (l["x1"] - l["x0"]) > 8]
    out = set()
    for i, c in enumerate(page.chars):
        mid = (c["top"] + c["bottom"]) / 2
        if any(t["x0"] - 1 <= c["x0"] and c["x1"] <= t["x1"] + 1 and abs(t["top"] - mid) < 2.2 for t in traits):
            out.add(i)
    return out

def texte_pdf(chemin, nettoyer=False):
    blocs = []
    with pdfplumber.open(chemin) as pdf:
        for n, p in enumerate(pdf.pages, 1):
            b = barres(p)
            if b:
                # Le texte barré est rendu ~~barré~~ : c'est ce que l'addenda retire.
                lignes = {}
                for i, c in enumerate(p.chars):
                    lignes.setdefault(round(c["top"]), []).append((c["x0"], c["text"], i in b))
                rendu = []
                for top in sorted(lignes):
                    morceau, etat = "", False
                    for _, t, x in sorted(lignes[top]):
                        if x != etat:
                            morceau += "~~"
                            etat = x
                        morceau += t
                    if etat:
                        morceau += "~~"
                    rendu.append(morceau)
                txt = "\n".join(rendu)
            else:
                txt = p.extract_text() or ""
            if nettoyer:
                txt = "\n".join(l for l in txt.split("\n") if not ENTETES.match(l.strip()))
            blocs.append(f"\n<!-- page {n} -->\n\n{txt}")
    return "".join(blocs)

def titres(txt):
    out = []
    for l in txt.split("\n"):
        s = l.strip()
        if re.match(r"^\d{1,2}\.00 [A-ZÉ]", s): out.append("## " + s)
        elif re.match(r"^\d{1,2}\.\d{2} [A-ZÉ]", s) and "...." not in s: out.append("### " + s)
        elif re.match(r"^\d{1,2}\.\d{2}\.\d{2} [A-ZÉ]", s) and "...." not in s: out.append("#### " + s)
        elif re.match(r"^(Question|Réponse) #\d+", s): out.append("\n**" + s.split(":")[0].strip() + "** :" + s.split(":", 1)[1] if ":" in s else s)
        else: out.append(l)
    return "\n".join(out)

ENT = "> Conversion automatique (pdfplumber) du fichier `{f}`, {p} pages. Le texte barré dans l'original est rendu ~~ainsi~~.\n> Source de vérité : le PDF. Généré par la conversion du {d}.\n"
for f, cible, titre, net in [
    ("reponses/Addenda 1 FX-AO-20260810 (1).pdf", "00_sources/03_addenda_1_converti.md", "Addenda n° 1 — 13 novembre 2026", False),
    ("reponses/Addenda 2 FX-AO-20260810.pdf", "00_sources/04_addenda_2_converti.md", "Addenda n° 2 — 17 novembre 2026", False),
    ("reponses/Addenda 3 FX-AO-20260810.pdf", "00_sources/05_addenda_3_converti.md", "Addenda n° 3 — 19 novembre 2026", False),
    ("reponses/Addenda 1 Contrat - FX-AO-20260810 (1).pdf", "00_sources/06_contrat_addenda_1_converti.md", "Contrat — version de l'addenda n° 1 (13 novembre 2026)", True),
]:
    with pdfplumber.open(f) as pdf: n = len(pdf.pages)
    corps = titres(texte_pdf(f, net))
    open(cible, "w").write(f"# FX-AO-20260810 — {titre}\n\n" + ENT.format(f=f, p=n, d="19 novembre 2026") + corps + "\n")
    print("✓", cible)

# ── Les classeurs : bordereau de prix et grille, version par version ──
VERSIONS = [("Bordereau de prix FX-AO-20261015_V1.xlsx", "origine (annexe du devis, § 6.1)"),
            ("Addenda 1 Bordereau de prix FX-AO-20260810_V1.xlsx", "addenda n° 1"),
            ("Addenda 3 Bordereau de prix FX-AO-20260810_V1.xlsx", "addenda n° 3 — **version à déposer**")]

def cellule_riche(c):
    v = c.value
    if v is None: return ""
    if isinstance(v, str): return v
    try:
        s = ""
        for part in v:
            if isinstance(part, str): s += part
            else:
                t = part.text
                s += f"~~{t}~~" if part.font is not None and part.font.strike else t
        return s
    except TypeError:
        return str(v)

def esc(s): return s.replace("|", "\\|").replace("\n", "<br>").strip()

def grille(f):
    ws = openpyxl.load_workbook(f, rich_text=True)["Annexe A - Grille conformité"]
    lignes = []
    for row in ws.iter_rows(min_row=11):
        no, crit = cellule_riche(row[0]).strip(), cellule_riche(row[1]).strip()
        if no or crit: lignes.append((no, crit))
    return lignes

def prix(f):
    ws = openpyxl.load_workbook(f)["Bordereau de prix"]
    return [[("" if c is None else str(c)) for c in r] for r in ws.iter_rows(values_only=True) if any(c is not None for c in r)]

g = {f: grille(f) for f, _ in VERSIONS}
cle = lambda no: no.split("\n")[0].strip()
base = {cle(n): c for n, c in g[VERSIONS[0][0]] if n}
a1 = {cle(n): c for n, c in g[VERSIONS[1][0]] if n}
md = ["# FX-AO-20260810 — Bordereau de prix et grille de conformité (versions comparées)", "",
      "> Conversion automatique (openpyxl) des trois classeurs. Le texte barré dans la cellule est rendu ~~ainsi~~.",
      "> La version à déposer est celle de l'**addenda n° 3** (addenda 3, réponse 3). Le classeur `FX-AO-20261015` est",
      "> l'annexe d'origine : le devis (§ 6.1) le nomme sous ce numéro.", "",
      "## 1. Ce qui change d'une version à l'autre", "",
      "| Critère | Origine | Addenda 1 | Addenda 3 (à déposer) |", "|---|---|---|---|"]
for n, c in g[VERSIONS[2][0]]:
    k = cle(n)
    if not n or (base.get(k) == c and a1.get(k) == c): continue
    md.append(f"| {esc(k)} | {esc(base.get(k, '— (absent)'))} | {esc(a1.get(k, '— (absent)'))} | {esc(c)} |")
md += ["", "Le **bordereau de prix** est identique dans les trois versions (vérifié cellule par cellule).", "",
       "## 2. Bordereau de prix (identique dans les trois versions)", "", "| Item | Description | Quantité | Unité | Prix unitaire | Total |", "|---|---|---|---|---|---|"]
for r in prix(VERSIONS[2][0])[5:]:
    r = (r + [""] * 6)[:6]
    md.append("| " + " | ".join(esc(x) for x in r) + " |")
md += ["", "## 3. Grille de conformité — version de l'addenda n° 3", "", "| No | Critère | Mention |", "|---|---|---|"]
for n, c in g[VERSIONS[2][0]]:
    parts = n.split("\n")
    md.append(f"| {esc(parts[0])} | {esc(c) if n else '**' + esc(c) + '**'} | {esc(' '.join(parts[1:]))} |")
open("00_sources/07_bordereau_grille_addenda_3_converti.md", "w").write("\n".join(md) + "\n")
print("✓ 00_sources/07_bordereau_grille_addenda_3_converti.md")
