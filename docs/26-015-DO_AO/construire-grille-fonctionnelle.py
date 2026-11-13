#!/usr/bin/env python3
"""
Grille de conformité des exigences, préparée pour le dossier de soumission du 26-015-DO.

Mêmes sources que la matrice (`construire-couverture.py`, dont les portes sont réutilisées telles
quelles) : le texte du client, le jugement mesuré, la recette à l'écran et les essais relevés.
Le document se régénère ; il ne s'édite pas à la main, pour qu'une conformité annoncée au client
reste adossée à une preuve qui existe.

Usage : python3 docs/26-015-DO_AO/construire-grille-fonctionnelle.py
"""
import importlib.util
import sys
from collections import Counter
from pathlib import Path

RACINE = Path(__file__).resolve().parent
SORTIE = RACINE / "10-GRILLE-EXIGENCES-FONCTIONNELLES.md"

_spec = importlib.util.spec_from_file_location("couverture", RACINE / "construire-couverture.py")
couverture = importlib.util.module_from_spec(_spec)
_spec.loader.exec_module(couverture)

#: La réponse à inscrire dans la grille du client, selon le verdict mesuré. Un « Non » à un
#: critère essentiel élimine la soumission (Régie 1.07.20) : ce n'est donc jamais une valeur par
#: défaut, mais une décision à prendre sur l'écart affiché.
REPONSE = {
    ("essentiel", "ACQUIS"): "**OUI**",
    ("préférentiel", "ACQUIS"): "**OUI**",
    ("essentiel", "PARTIEL"): "OUI sous engagement — à décider",
    ("préférentiel", "PARTIEL"): "AVEC RÉSERVES",
    ("essentiel", "HORS_CODE"): "Engagement d'entreprise — à décider",
    ("préférentiel", "HORS_CODE"): "Engagement d'entreprise — à décider",
    ("essentiel", "A_BATIR"): "NON tant que non bâti",
    ("préférentiel", "A_BATIR"): "NON",
}


def cellule(texte: str) -> str:
    return texte.replace("|", "/").replace("\n", " ")


def tableau_fonctionnel(exigences, jugements, recettes, type_):
    lignes = [
        "| # | Exigence du client | Réponse à inscrire | Conformité mesurée | Où le constater à l'écran | Preuves automatisées |",
        "|---|---|---|---|---|---|",
    ]
    for e in exigences:
        if e["type"] != type_ or not couverture.EST_FONCTIONNELLE(e["id"]):
            continue
        j, r = jugements[e["id"]], recettes.get(e["id"])
        lignes.append(
            f"| **{e['id']}** | {cellule(e['description'])} | {REPONSE[(type_, j['verdict'])]} "
            f"| {couverture.VERDICTS[j['verdict']]} | {cellule(couverture.recette_en_mots(r))} "
            f"| {couverture.preuves_de(j, r)} |"
        )
    return lignes


def tableau_ecarts(exigences, jugements):
    lignes = [
        "| # | Type | Exigence du client | Conformité mesurée | Réponse à inscrire | Ce qui manque |",
        "|---|---|---|---|---|---|",
    ]
    for e in exigences:
        j = jugements[e["id"]]
        if j["verdict"] == "ACQUIS":
            continue
        lignes.append(
            f"| **{e['id']}** | {e['type']} | {cellule(e['description'])} | {couverture.VERDICTS[j['verdict']]} "
            f"| {REPONSE[(e['type'], j['verdict'])]} | {cellule(j['ecart'] or '—')} |"
        )
    return lignes


def decompte(exigences, jugements, filtre):
    c = Counter(jugements[e["id"]]["verdict"] for e in exigences if filtre(e))
    return " | ".join(str(c.get(v, 0)) for v in couverture.VERDICTS) + f" | {sum(c.values())}"


def main() -> None:
    exigences, jugements = couverture.charger()
    recettes = couverture.charger_verification()
    fonctionnelles = [e for e in exigences if couverture.EST_FONCTIONNELLE(e["id"])]
    non_acquises = [e["id"] for e in fonctionnelles if jugements[e["id"]]["verdict"] != "ACQUIS"]
    est_fonc = lambda t: (lambda e: e["type"] == t and couverture.EST_FONCTIONNELLE(e["id"]))  # noqa: E731
    est_tech = lambda t: (lambda e: e["type"] == t and not couverture.EST_FONCTIONNELLE(e["id"]))  # noqa: E731
    entete = "| ✅ Acquis | 🟡 Partiel | ⛔ À bâtir | 🔒 Hors code | Total |"

    texte = [
        "# 10. Grille de conformité — préparation de la soumission",
        "",
        "> **Fichier généré** par `construire-grille-fonctionnelle.py`. Ne pas l'éditer à la main :",
        "> corriger le jugement (`couverture-26-015-DO.json`) ou la recette, puis régénérer.",
        "",
        "Le texte de chaque exigence est celui de la grille du client (annexe 7.00 P, onglets",
        "« Critères essentiels » et « Critères préférentiels »). La conformité est **mesurée** : une",
        "exigence n'est dite acquise que si un écran la sert, qu'une recette d'interface la vérifie",
        "et qu'au moins un essai automatisé la nomme — la génération échoue sinon.",
        "",
        f"Mesure : {cellule(couverture.json.loads(couverture.COUVERTURE.read_text(encoding='utf-8'))['mesure'])}.",
        "",
        "## Décompte",
        "",
        f"| Ensemble {entete}",
        "|---|---:|---:|---:|---:|---:|",
        f"| Fonctionnelles essentielles (E-GN, E-CH, E-GR, E-GP, E-RF) | {decompte(exigences, jugements, est_fonc('essentiel'))} |",
        f"| Fonctionnelles préférentielles (P-GN, P-GH, P-CH, P-GP, P-RF) | {decompte(exigences, jugements, est_fonc('préférentiel'))} |",
        f"| Technologiques essentielles (E-TN) | {decompte(exigences, jugements, est_tech('essentiel'))} |",
        f"| Technologiques préférentielles (P-TN) | {decompte(exigences, jugements, est_tech('préférentiel'))} |",
        "",
        ("**Les 90 exigences fonctionnelles sont conformes.** Les écarts restants sont tous "
         "technologiques ou d'entreprise : ils sont listés à la section 3." if not non_acquises
         else f"**Exigences fonctionnelles non conformes : {', '.join(non_acquises)}.**"),
        "",
        "## 1. Exigences fonctionnelles essentielles",
        "",
        "Un « Non » à l'une d'elles rend la soumission non conforme (Régie 1.07.20).",
        "",
        *tableau_fonctionnel(exigences, jugements, recettes, "essentiel"),
        "",
        "## 2. Exigences fonctionnelles préférentielles",
        "",
        "Elles comptent pour 25 % de la note de qualité (partie 6 de la grille d'évaluation).",
        "",
        *tableau_fonctionnel(exigences, jugements, recettes, "préférentiel"),
        "",
        "## 3. Ce qui n'est pas conforme à ce jour",
        "",
        "Exigences technologiques et engagements d'entreprise. Pour un critère **essentiel**, la",
        "réponse « OUI » n'est tenable que si l'écart est fermé, ou engagé par écrit dans la",
        "soumission avec son échéance ; la cellule « à décider » le rappelle.",
        "",
        *tableau_ecarts(exigences, jugements),
        "",
    ]
    SORTIE.write_text("\n".join(texte), encoding="utf-8")
    print(f"{SORTIE.name} : {len(fonctionnelles)} fonctionnelles, {len(fonctionnelles) - len(non_acquises)} conformes.")


if __name__ == "__main__":
    sys.exit(main())
