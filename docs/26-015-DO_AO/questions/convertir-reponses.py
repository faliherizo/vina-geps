"""Convertit les réponses d'Urgences-santé (AO 26-015-DO) en Markdown, et compare la grille v2.

Usage (depuis la racine du dépôt) :
    python3 docs/26-015-DO_AO/questions/convertir-reponses.py

Entrées : `questions/reponses/26-015-DO_addenda N*.pdf` (N = 1 à 7) et
`questions/reponses/26-015-DO_Grille des critèresv2 (1).xlsx`.
Sorties : `questions/reponses/addenda-0N.md`, `questions/reponses/grille-criteres-v2.md`.

Reprend la méthode de `docs/FX-AO-20260810_Dossier/00_sources/convertir-addenda.py` :
le texte barré du PDF (trait horizontal qui traverse le caractère, pdfplumber) et les
fragments barrés des cellules (openpyxl `rich_text=True`) sont rendus ~~ainsi~~, parce
qu'un addenda retire du texte en le barrant. La grille v2 est comparée, critère par
critère, au texte d'origine extrait du devis (`exigences-26-015-DO.json`) et aux
remplacements déjà consignés (`addenda-26-015-DO.json`).
"""
import glob
import json
import re
import sys
import unicodedata
from pathlib import Path

import openpyxl
import pdfplumber

ICI = Path(__file__).resolve().parent
AO = ICI.parent
REP = ICI / "reponses"
GRILLE = REP / "26-015-DO_Grille des critèresv2 (1).xlsx"

# Lignes répétées à chaque page : pied d'addenda, pied de la convention jointe à l'addenda 4.
PIEDS = re.compile(
    r"^(Tout Addenda transmis en conformité.*|SOUMISSIONNAIRE doit prendre connaissance.*|"
    r"formulaire de soumission\.|HORAIRE DE TRAVAIL Page \d+-\d+|.*– Paramédics – Urgences-santé \| \d{4}-\d{2}-\d{2}|"
    r"(ASSIGNATION|HEURES SUPPLÉMENTAIRES|[A-ZÉÈ ’']+) Page \d+-\d+)$"
)
PAGE = re.compile(r"^Page (\d+) de (\d+)$")
# Ce que la conversion ne peut pas trancher seule, dit en tête du fichier concerné.
REMARQUES = {
    4: "> **À lire avec l'original.** L'article 15.14 de la convention jointe juxtapose deux valeurs à deux endroits "
       "(« plus de quatre (4) deux (2) mutations », « plus de deux (2) d'une (1) mutation mutations ») : la rature "
       "de l'original n'a laissé aucun trait dans le PDF (aucun caractère barré détectable). Ne pas paramétrer ces "
       "plafonds sans la convention en vigueur.\n",
    6: "> Ce fichier remplace l'ancien `addenda-01.md`, qui contenait en réalité l'addenda n° 06 (même texte, mot pour "
       "mot, vérifié le 20 novembre 2026 ; sans structure ni détection du barré).\n",
}
DATES = {
    1: "14 octobre 2026", 2: "26 octobre 2026", 3: "29 octobre 2026", 4: "4 novembre 2026",
    5: "5 novembre 2026", 6: "10 novembre 2026", 7: "19 novembre 2026",
}


def barres(page):
    traits = [t for t in page.lines + page.rects if abs(t["top"] - t["bottom"]) < 2.5 and (t["x1"] - t["x0"]) > 8]
    sortie = set()
    for i, c in enumerate(page.chars):
        milieu = (c["top"] + c["bottom"]) / 2
        # Un soulignement passe sous la ligne de base ; un trait de rature passe au milieu du caractère.
        if any(t["x0"] - 1 <= c["x0"] and c["x1"] <= t["x1"] + 1 and abs(t["top"] - milieu) < 2.2 for t in traits):
            sortie.add(i)
    return sortie


def texte_page(page):
    rayes = barres(page)
    if not rayes:
        return page.extract_text() or "", 0
    lignes = {}
    for i, c in enumerate(page.chars):
        lignes.setdefault(round(c["top"]), []).append((c["x0"], c["text"], i in rayes))
    rendu = []
    for haut in sorted(lignes):
        morceau, raye = "", False
        for _, t, x in sorted(lignes[haut]):
            if x != raye:
                morceau += "~~"
                raye = x
            morceau += t
        rendu.append(morceau + ("~~" if raye else ""))
    return "\n".join(rendu), len(rayes)


def structurer(ligne):
    s = ligne.strip()
    if re.match(r"^ADDENDA #\d+$", s):
        return ""
    if re.match(r"^(Question|QUESTION) ?\d+ ?[:.]", s):
        return "\n### " + s
    if re.match(r"^Réponse ?:?$", s):
        return "\n**Réponse**\n"
    if re.match(r"^Réponse ?:", s):
        return "\n**Réponse** :" + s.split(":", 1)[1]
    if re.match(r"^(PARTIE [AB]|Correction de l’addenda|Questions ?/ ?réponses|Questions et réponses|Comblement et absences|"
                r"Échanges et ajustements|AGPH et confection des horaires|Temps et paie|Modification des documents|"
                r"Amendement aux documents|Conditions d’admissibilité|ANNEXE \d)", s):
        return "\n## " + s
    if re.match(r"^ARTICLE \d+$", s):
        return "\n## Convention collective — " + s
    if re.match(r"^SECTION [IVX]+ ", s):
        return "\n### " + s
    if re.match(r"^1[456]\.\d{2} ", s):
        return "\n**" + s[:5] + "**" + s[5:]
    return ligne


def convertir_pdf(chemin, numero):
    blocs, total_rayes = [], 0
    with pdfplumber.open(chemin) as pdf:
        nb_pages = len(pdf.pages)
        for n, page in enumerate(pdf.pages, 1):
            texte, rayes = texte_page(page)
            total_rayes += rayes
            lignes = [structurer(l) for l in texte.split("\n") if not PIEDS.match(l.strip()) and not PAGE.match(l.strip())]
            blocs.append(f"\n<!-- page {n} -->\n" + "\n".join(lignes))
    entete = (
        f"# 26-015-DO — Addenda n° {numero:02d} ({DATES[numero]})\n\n"
        f"> Conversion automatique (pdfplumber) de `{chemin.name}`, {nb_pages} pages, par "
        f"`questions/convertir-reponses.py`. Le texte barré dans l'original est rendu ~~ainsi~~ "
        f"({total_rayes} caractères barrés détectés). Les pieds de page répétés sont retirés.\n"
        f"> Source de vérité : le PDF.\n" + REMARQUES.get(numero, "")
    )
    return entete + "".join(blocs) + "\n", nb_pages, total_rayes


def cellule(c):
    v = c.value
    if v is None:
        return ""
    if isinstance(v, str):
        return f"~~{v}~~" if c.font is not None and c.font.strike else v
    try:
        s = ""
        for part in v:
            if isinstance(part, str):
                s += part
            else:
                s += f"~~{part.text}~~" if part.font is not None and part.font.strike else part.text
        return s
    except TypeError:
        return str(v)


def lire_grille():
    classeur = openpyxl.load_workbook(GRILLE, rich_text=True)
    criteres, questions = [], []
    for onglet in classeur.worksheets:
        if "Critères" not in onglet.title:
            continue
        for rangee in onglet.iter_rows():
            no, famille, texte = (cellule(c).strip() for c in rangee[:3])
            if re.match(r"^[EP]-[A-Z]{2}-\d+$", no):
                criteres.append({"id": no, "famille": famille, "description": texte, "onglet": onglet.title})
            elif not no and texte.endswith("?"):
                questions.append((onglet.title, texte))
    return criteres, questions


def normal(s):
    s = unicodedata.normalize("NFC", s).replace("’", "'").replace("\u00a0", " ")
    return re.sub(r"\s+", " ", s).strip().rstrip(".")


def esc(s):
    return s.replace("|", "\\|").replace("\n", "<br>").strip()


def comparer_grille():
    v2, questions = lire_grille()
    origine = {e["id"]: e for e in json.loads((AO / "exigences-26-015-DO.json").read_text(encoding="utf-8"))["exigences"]}
    remplacees = {
        m["id"]: (a["numero"], m["description"])
        for a in json.loads((AO / "addenda-26-015-DO.json").read_text(encoding="utf-8"))["addenda"]
        for m in a["modifications"]
    }
    par_id = {c["id"]: c for c in v2}
    md = [
        "# 26-015-DO — Grille des critères v2 (jointe à l'addenda n° 06)",
        "",
        f"> Conversion automatique (openpyxl, `rich_text=True`) de `{GRILLE.name}` par `questions/convertir-reponses.py`.",
        "> Le texte barré dans une cellule serait rendu ~~ainsi~~ : **aucune cellule barrée** dans ce classeur.",
        "> L'addenda n° 07 impose cette grille pour répondre (« Veuillez utiliser la grille des critèresv2.xlsx »).",
        "",
        "## 1. Différences avec la grille d'origine (annexe 7.00 du devis)",
        "",
        "Comparaison avec `exigences-26-015-DO.json` (texte extrait du PDF du devis). Une différence de seule",
        "typographie (apostrophe, espace, point final) n'est pas listée.",
        "",
        "| Critère | Grille d'origine | Grille v2 | Remplacement déjà consigné (`addenda-26-015-DO.json`) | Concorde avec v2 ? |",
        "|---|---|---|---|---|",
    ]
    differences = 0
    for ident in sorted(set(origine) | set(par_id), key=lambda i: (i[0], i[2:4], int(i.split("-")[2]))):
        avant = origine.get(ident, {}).get("description")
        apres = par_id.get(ident, {}).get("description")
        if avant is not None and apres is not None and normal(avant) == normal(apres):
            continue
        differences += 1
        consigne = remplacees.get(ident)
        concorde = "—" if not consigne else ("oui, mot pour mot" if apres and normal(consigne[1]) == normal(apres) else "**non** : paraphrase, reprendre v2 mot pour mot")
        md.append(
            f"| {ident} | {esc(avant or '— (absent)')} | {esc(apres or '— (retiré)')} | "
            f"{esc(f'addenda {consigne[0]} : ' + consigne[1]) if consigne else '—'} | {concorde} |"
        )
    if questions:
        md += ["", "**Questions d'en-tête à remplir dans le classeur** (déjà posées par la grille d'origine) :", ""]
        md += [f"- {onglet} : « {q} »" for onglet, q in questions]
    md += ["", f"{differences} critère(s) différent(s) ; {len(v2)} critères dans la v2, {len(origine)} dans l'origine.", ""]
    for onglet in sorted({c["onglet"] for c in v2}):
        md += [f"## {onglet}", "", "| # | Famille | Description |", "|---|---|---|"]
        md += [f"| {c['id']} | {esc(c['famille'])} | {esc(c['description'])} |" for c in v2 if c["onglet"] == onglet]
        md.append("")
    (REP / "grille-criteres-v2.md").write_text("\n".join(md), encoding="utf-8")
    print(f"✓ grille-criteres-v2.md : {len(v2)} critères, {differences} différence(s)")
    return v2


def main():
    for numero in range(1, 8):
        trouves = sorted(glob.glob(str(REP / f"26-015-DO_addenda {numero}*.pdf")))
        if len(trouves) != 1:
            sys.exit(f"addenda {numero} : {len(trouves)} fichier(s) trouvé(s), un seul attendu")
        texte, pages, rayes = convertir_pdf(Path(trouves[0]), numero)
        (REP / f"addenda-{numero:02d}.md").write_text(texte, encoding="utf-8")
        print(f"✓ addenda-{numero:02d}.md : {pages} pages, {rayes} caractères barrés")
    comparer_grille()


if __name__ == "__main__":
    main()
