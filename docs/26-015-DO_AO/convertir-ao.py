#!/usr/bin/env python3
"""
Conversion de l'appel d'offres **Urgences-santé 26-015-DO** (Système de gestion des effectifs) en
Markdown, et extraction de sa grille d'exigences en JSON.

Le PDF (199 pages) agrège sept documents : Régie, Contrat (annexes comprises), Cahier de charges et
devis technique, Formulaire de soumission, Questionnaire d'évaluation de la qualité, Grille des
critères (essentiels et préférentiels) et Bordereau de prix. La conversion les restitue dans
l'ordre, sans résumer ni réordonner : un appel d'offres se cite, et une citation approximative ne
vaut rien.

Ce qui est retiré, et rien d'autre : les en-têtes et pieds de page répétés à chaque feuillet. Les
clauses numérotées (« 1.07.05 Contenu de la Soumission ») deviennent des titres, pour que le
document se navigue ; la grille des critères devient deux tableaux.

pdfplumber plutôt que pypdf : pypdf coupe des mots dans les pages pivotées de la grille
(« organisation nels », « trava il »), ce qui rendrait les exigences incitables.

Usage : python3 docs/26-015-DO_AO/convertir-ao.py
Sorties : 26-015-DO_AO.md, exigences-26-015-DO.json (144 exigences)
"""
import json
import re
import sys
from pathlib import Path

try:
    import pdfplumber
except ImportError:  # pragma: no cover - dépendance d'outillage, pas de production
    sys.exit("pdfplumber manquant : python3 -m pip install pdfplumber")

RACINE = Path(__file__).resolve().parent
SOURCE = RACINE / "26-015-DO_AO.pdf"
SORTIE_MD = RACINE / "26-015-DO_AO.md"
SORTIE_JSON = RACINE / "exigences-26-015-DO.json"

# Pages de début (1-indexées) de chaque document agrégé dans le PDF.
DOCUMENTS = [
    (1, "Régie de l'appel d'offres"),
    (49, "Contrat de licence d'utilisation de solution infonuagique"),
    (143, "Cahier de charges et devis technique"),
    (152, "Formulaire de soumission"),
    (181, "Annexe 7.00 — Questionnaire d'évaluation de la qualité"),
    (193, "Annexe 7.00 P / 1.16.03 A — Grille des critères essentiels et préférentiels"),
    (199, "Annexe 2.00 — Bordereau de prix"),
]
PAGE_GRILLE = (193, 198)

# Trois pages sont des formulaires à colonnes pivotées : l'extraction de texte y entrelace les
# lettres des en-têtes (« Q (e u n a u n ni t t i é t ) é »). Leur contenu est reconstitué en
# tableau, champ pour champ, et signalé comme tel — le PDF fait foi.
AVIS_RECONSTITUTION = "*Formulaire reconstitué en tableau à partir du PDF, qui fait foi.*"
RECONSTITUTIONS = {
    46: [
        "ANNEXE 1.16.03 B - GRILLE D'ÉVALUATION — PARTIE 1 : ÉVALUATION DE LA QUALITÉ", "",
        AVIS_RECONSTITUTION, "",
        "| Critère | Poids (P) | Note obtenue (N, 0 à 100) | Note pondérée (P × N) |",
        "|---|---:|---:|---:|",
        "| Expérience | 15 % | | |",
        "| Capacité | 25 % | | |",
        "| Organisation | 10 % | | |",
        "| Assurance qualité | 5 % | | |",
        "| Services professionnels | 20 % | | |",
        "| Critères préférentiels | 25 % | | |",
        "| **Note finale pour la qualité** (somme des notes pondérées) | **100 %** | | /100 |",
        "",
        "Colonnes répétées pour les soumissionnaires A à E. La grille prévoit de cocher les critères",
        "pour lesquels un minimum de 70 points est exigé ; aucun ne l'est dans le document publié.",
        "",
        "Soumissions acceptables : dans le cas où aucun critère n'a été coché à la PARTIE 1, les",
        "soumissions acceptables sont celles ayant obtenu une note finale d'au moins 70 points. Dans le",
        "cas où un ou des critères ont été cochés à la PARTIE 1, les soumissions acceptables sont celles",
        "ayant obtenu un minimum de 70 points pour chacun des critères cochés et une note finale d'au",
        "moins 70 points.",
    ],
    47: [
        "PARTIE 2 : ÉVALUATION DES PRIX — valeur du paramètre K (entre 15 % et 30 %) : **30 %**", "",
        AVIS_RECONSTITUTION, "",
        "| # | Ligne | Formule |",
        "|---|---|---|",
        "| | Prix soumis (soumissions acceptables seulement) | $ |",
        "| 1 | Coefficient d'ajustement pour la qualité | 1 + K × (note finale pour la qualité − 70) / 30 |",
        "| 2 | Prix ajusté | prix soumis / coefficient d'ajustement pour la qualité |",
        "| | Marge préférentielle (assurance qualité ou développement durable, s'il y a lieu) | ___ % |",
        "| | Prix réduit (assurance qualité ou développement durable, s'il y a lieu) | $ |",
        "| | Marge préférentielle (valeur ajoutée québécoise ou autrement canadienne, s'il y a lieu) | ___ % |",
        "| | Prix réduit (valeur ajoutée québécoise ou autrement canadienne, s'il y a lieu) | $ |",
        "| | Coûts additionnels (coût total d'acquisition, s'il y a lieu) | $ |",
        "| | Prix incluant les coûts additionnels | $ |",
        "| | **Prix ajusté le plus bas (aux fins d'adjudication)** | $ |",
        "",
        "Colonnes répétées pour les soumissionnaires A à E.",
    ],
    199: [
        "ANNEXE 2.0 - BORDEREAU DE PRIX — Appel d'offres n° 26-015-DO — SYSTÈME DE GESTION DES EFFECTIFS", "",
        AVIS_RECONSTITUTION, "",
        "Veuillez fournir une soumission détaillée avec le bordereau de prix, les prix inscrits doivent",
        "être les mêmes dans les deux documents. En cas de différence, seuls les prix indiqués au",
        "bordereau de prix seront considérés pour l'adjudication. Ne complétez que les sections en gris,",
        "les calculs sont automatisés.", "",
        "| Section | Livrable | Quantité (unités) | Prix | Total |",
        "|---|---|---:|---|---|",
        "| A. Solution technologique qui répond aux besoins d'affaires exprimés dans le devis technique ET à la grille des critères essentiels | Solution technologique (quantité de licences) | 1 100 | prix forfaitaire mensuel | $ |",
        "| B. Configuration des environnements | Configuration et mise en service (à forfait) | 1 | prix forfaitaire | $ |",
        "| B. Configuration des environnements | Autres — à préciser | 1 | prix forfaitaire | $ |",
        "| C. Intégration des interfaces | Installation, configuration et mise en service (à forfait) | 1 | prix forfaitaire | $ |",
        "| C. Intégration des interfaces | Autres — à préciser | 1 | prix forfaitaire | $ |",
        "| D. Formation | Formation des employés, matériel pédagogique inclus (à forfait pour 6 personnes), de type « Train the trainer » | 1 | prix forfaitaire | $ |",
        "| D. Formation | Capsules d'auto-apprentissage pour les paramédics | 1 | prix forfaitaire | $ |",
        "| E. Services professionnels additionnels : banque d'heures (1) | Formateur | 500 | tarif horaire | $ |",
        "| E. | Chargé de projet | 500 | tarif horaire | $ |",
        "| E. | Analyste ou intégrateur | 500 | tarif horaire | $ |",
        "| E. | Programmeur ou technicien | 500 | tarif horaire | $ |",
        "| | **Total servant à l'adjudication** | | | $ |",
        "| | TPS 5,00 % · TVQ 9,975 % · total des taxes 14,975 % | | | $ |",
        "| | Total taxes incluses | | | $ |",
        "",
        "Sections A et E : aux seules fins de l'adjudication, le prix soumis sera considéré pour les",
        "cinq (5) années du contrat et les cinq (5) années optionnelles. En pratique, les prix des années",
        "deux (2) à dix (10) seront ajustés annuellement selon l'IPC, à la date anniversaire de la mise en",
        "production de la solution comme prévu au Contrat. La quantité sera ajustée selon le nombre réel",
        "nécessaire.", "",
        "(1) La présente banque d'heures est une estimation du besoin de l'organisme public en cours de",
        "Contrat. Ce nombre d'heures ne représente pas un engagement de la part de l'Organisme public et",
        "ne sert qu'au calcul de la soumission.", "",
        "LE SOUMISSIONNAIRE S'ENGAGE À EXÉCUTER LE PROJET AU PRIX COURANT INDIQUÉ CI-DESSUS. EN FOI DE QUOI,",
        "LE SOUMISSIONNAIRE PAR SON REPRÉSENTANT DÛMENT AUTORISÉ A SIGNÉ LES PRÉSENTES CE : date, nom du",
        "soumissionnaire (**le même que celui associé au NEQ fourni par le Registraire des entreprises du",
        "Québec**), no d'entreprise (NEQ), signature et nom du représentant autorisé.",
    ],
}

BRUIT = re.compile(
    r"^\s*(URGENCES-SANTÉ APPEL D['’]OFFRES NO 26-015-DO"
    r"|#\d{7}v\d+"
    r"|Système de gestion des effectifs\s*-?\s*Page \d+ de \d+.*"
    r"|DATE D['’]IMPRESSION.*"
    r"|Page \d+ / \d+"
    r"|Page \d+ de 6"
    r"|Régie|Contrat|Formulaire de soumission|Système de gestion des effectifs"
    r"|26-015-DO|\d{1,2})\s*$",
    re.I,
)
POINTILLES = re.compile(r"\s*[\._]{4,}\s*(\d+)?")
# « 1.07.05 Contenu … » ; « 0.01.01 Accord … » ; « 2.00 EXIGENCES … ». Jamais une ligne de sommaire.
CLAUSE = re.compile(r"^(\d{1,2}\.\d{2}(?:\.\d{2})?)\s+(\S.{1,110})$")
# Le cahier de charges numérote autrement : « 1 CONTEXTE DE L'INITIATIVE », « 2.4 Niveau de services ».
PAGES_CAHIER = range(145, 152)
SECTION_CAHIER = re.compile(r"^(\d)(\.\d)?\s+([A-ZÉÈÀ][^.;:]{2,80})$")

FAMILLES = (
    r"Généralités?|Confection des horaires|Gestion régulière des horaires|Gestion des horaires"
    r"|Gestion du \"punch\"|Rapports? et formulaires|Technologie"
)
EXIGENCE = re.compile(rf"^([EP]-[A-Z]{{2}}-\d+)\s*({FAMILLES})\s*(.*)$")
FIN_EXIGENCE = re.compile(
    r"^(Critères (essentiels|préférentiels)|ANNEXE|Page \d+ de \d+|Dans quel pays|Quel est le nom"
    r"|# Famille|Commentaires du|Vous pouvez|\(Oui|Références dans|Nom du document|Réponse)"
)


def niveau_titre(numero: str) -> str:
    """0.01 → ###, 0.01.01 → ####, 1.00 → ##."""
    if numero.endswith(".00"):
        return "##"
    return "###" if numero.count(".") == 1 else "####"


def nettoyer_page(texte: str) -> list[str]:
    lignes = []
    for brute in texte.replace("\xa0", " ").splitlines():
        ligne = re.sub(r"[ \t]+", " ", brute).strip()
        if BRUIT.match(ligne):
            continue
        lignes.append(ligne)
    return lignes


def en_markdown(lignes: list[str], cahier: bool = False) -> list[str]:
    sortie = []
    for ligne in lignes:
        if ligne.startswith("•"):
            ligne = "- " + ligne.lstrip("• ").strip()
        s = SECTION_CAHIER.match(ligne) if cahier else None
        if s:
            niveau = "###" if s.group(2) else "##"
            sortie += ["", f"{niveau} {s.group(1)}{s.group(2) or ''} {s.group(3)}", ""]
            continue
        if POINTILLES.search(ligne):
            # Ligne de sommaire : « Titre ..... 12 » → « Titre — p. 12 », jamais un titre.
            ligne = POINTILLES.sub(lambda m: f" — p. {m.group(1)}" if m.group(1) else "", ligne)
            sortie.append(f"- {ligne}")
            continue
        m = CLAUSE.match(ligne)
        if m and not ligne.endswith((",", ";")):
            sortie += ["", f"{niveau_titre(m.group(1))} {m.group(1)} {m.group(2)}", ""]
            continue
        sortie.append(ligne)
    return sortie


def famille_normalisee(famille: str) -> str:
    """La grille écrit « Généralité » et « Généralités », « Rapport » et « Rapports » : une seule famille."""
    if famille.startswith("Généralité"):
        return "Généralités"
    return "Rapports et formulaires" if famille.startswith("Rapport") else famille


def extraire_exigences(pages: list[str]) -> list[dict]:
    exigences: list[dict] = []
    courante = None
    for texte in pages:
        for brute in texte.replace("\xa0", " ").splitlines():
            ligne = re.sub(r"[ \t]+", " ", brute).strip()
            m = EXIGENCE.match(ligne)
            if m:
                courante = {
                    "id": m.group(1),
                    "type": "essentiel" if m.group(1).startswith("E-") else "préférentiel",
                    "famille": famille_normalisee(m.group(2)),
                    "description": m.group(3).strip(),
                }
                exigences.append(courante)
            elif courante and ligne and not FIN_EXIGENCE.match(ligne):
                courante["description"] = f"{courante['description']} {ligne}".strip()
            elif FIN_EXIGENCE.match(ligne):
                courante = None
    return exigences


def tableau(exigences: list[dict]) -> list[str]:
    lignes = ["| # | Famille | Description |", "|---|---|---|"]
    for e in exigences:
        lignes.append(f"| {e['id']} | {e['famille']} | {e['description'].replace('|', '/')} |")
    return lignes


def main() -> None:
    with pdfplumber.open(str(SOURCE)) as pdf:
        pages = [(p.extract_text() or "") for p in pdf.pages]

    exigences = extraire_exigences(pages[PAGE_GRILLE[0] - 1 : PAGE_GRILLE[1]])
    essentiels = [e for e in exigences if e["type"] == "essentiel"]
    preferentiels = [e for e in exigences if e["type"] == "préférentiel"]

    md = [
        "# Appel d'offres 26-015-DO — Urgences-santé",
        "",
        "> **Système de gestion des effectifs (solution infonuagique SaaS)**",
        "",
        "| | |",
        "|---|---|",
        "| Donneur d'ordre | Urgences-santé, 6700 rue Jarry Est, Montréal (Québec) H1P 0A4 |",
        "| Numéro | 26-015-DO |",
        "| Émission | 29 septembre 2026 (acte daté du 3 août 2026) |",
        "| Dépôt des soumissions | 3 novembre 2026, 14:00:59 |",
        "| Modalité | Contrat ferme, 5 ans + 5 options de renouvellement d'un an |",
        "| Mode de sollicitation | Public, avec soumission électronique (SEAO) |",
        "| Mode d'adjudication | Prix ajusté le plus bas (qualité ≥ 70, paramètre K = 30 %) |",
        "| Garanties | Soumission 100 000 $ ; exécution 100 000 $ |",
        "| Gestionnaire du dossier | Claude Forget — appeldoffres@urgences-sante.qc.ca — 514 723-5600 #5849 |",
        "",
        "*Transcription intégrale du PDF (199 pages), produite par `docs/26-015-DO_AO/convertir-ao.py`.*",
        "*Les en-têtes et pieds de page répétés sont retirés ; le texte n'est ni résumé ni réordonné.*",
        "*La grille des critères (pages 193-198) est restituée sous forme de tableaux ; elle est aussi*",
        f"*extraite dans `{SORTIE_JSON.name}` ({len(essentiels)} essentiels, {len(preferentiels)} préférentiels).*",
        "",
        "## Sommaire des documents",
        "",
    ]
    for debut, titre in DOCUMENTS:
        ancre = re.sub(r"[^a-z0-9à-ÿ -]", "", titre.lower()).replace(" ", "-")
        md.append(f"- [{titre}](#{ancre}) — p. {debut}")
    md += ["", "---", ""]

    debuts = {debut: titre for debut, titre in DOCUMENTS}
    for numero, texte in enumerate(pages, start=1):
        if numero in debuts:
            md += ["", f"# {debuts[numero]}", ""]
        if PAGE_GRILLE[0] <= numero <= PAGE_GRILLE[1]:
            if numero == PAGE_GRILLE[0]:
                md += [f"<!-- pages {PAGE_GRILLE[0]}-{PAGE_GRILLE[1]} -->", ""]
                md += nettoyer_page(texte)
                md += ["", "Questions préalables de l'onglet *Critères essentiels* :", "",
                       "- Dans quel pays et/ou province sont hébergées les données ?",
                       "- Quel est le nom de l'hébergeur ?", "",
                       "Colonnes à remplir par le soumissionnaire pour chaque ligne : réponse (Oui/Non ;",
                       "Oui/Non/Avec réserves pour les préférentiels), commentaires, références dans la",
                       "documentation (nom du document, page).", "",
                       f"## Critères essentiels ({len(essentiels)}) — un « Non » rend la soumission non conforme", ""]
                md += tableau(essentiels)
                md += ["", f"## Critères préférentiels ({len(preferentiels)}) — 25 % de la note de qualité", ""]
                md += tableau(preferentiels)
                md.append("")
            continue
        if numero in RECONSTITUTIONS:
            md += [f"<!-- page {numero} -->", ""] + RECONSTITUTIONS[numero] + [""]
            continue
        contenu = en_markdown(nettoyer_page(texte), cahier=numero in PAGES_CAHIER)
        if any(contenu):
            md += [f"<!-- page {numero} -->", ""] + contenu + [""]

    texte_md = re.sub(r"\n{3,}", "\n\n", "\n".join(md)).strip() + "\n"
    SORTIE_MD.write_text(texte_md, encoding="utf-8")
    SORTIE_JSON.write_text(
        json.dumps({"source": SOURCE.name, "exigences": exigences}, ensure_ascii=False, indent=2) + "\n",
        encoding="utf-8",
    )
    print(f"{SORTIE_MD.name} — {SORTIE_MD.stat().st_size // 1024} Ko, {len(pages)} pages")
    print(f"{SORTIE_JSON.name} — {len(essentiels)} essentiels, {len(preferentiels)} préférentiels")


if __name__ == "__main__":
    main()
