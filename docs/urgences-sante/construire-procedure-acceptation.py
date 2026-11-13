#!/usr/bin/env python3
"""
Le cahier de cas de la procédure d'essais d'acceptation d'Urgences-santé (AO 26-015-DO, E-TN-12).

Un cas par exigence fonctionnelle, **généré** et non recopié : une procédure tenue à la main
décrit tôt ou tard un écran qui a changé de nom, un bouton qui a disparu ou un essai renommé —
exactement le défaut qui a rendu la matrice de couverture fausse trois fois (doc 08 § 8.2).

Sources (lues, jamais modifiées) :
  - docs/26-015-DO_AO/exigences-26-015-DO.json           le texte de l'avis (fait foi) ;
  - docs/26-015-DO_AO/couverture-26-015-DO.json          le verdict de chaque exigence ;
  - e2e/donnees/urgences-sante/exigences-fonctionnelles.json  la recette à l'interface ;
  - e2e/donnees/urgences-sante/comportements.json        les scénarios de comportement joués à l'API ;
  - e2e/donnees/urgences-sante/demonstration.json        les comptes du jeu de données ;
  - backend-java/.../saas/domaine/CatalogueVues.java     sections et notions du menu (backend cible) ;
  - frontend/src/i18n.tsx                                les libellés français du menu ;
  - frontend/src (tout)                                  pour vérifier que chaque commande citée existe.

Sortie : le bloc généré de docs/urgences-sante/PROCEDURE-ESSAIS-ACCEPTATION.md, entre ses
marques. Le reste du document est rédigé à la main et n'est pas touché.

Le script échoue si une exigence fonctionnelle n'a pas sa recette, si un écran n'est pas au
catalogue des vues ou si un essai cité ne désigne plus de fichier. Une commande de recette
introuvable dans le code du front-end est signalée à la sortie, sans arrêter la génération :
elle peut être composée à l'exécution (préfixe + identifiant), et c'est l'essai e2e qui tranche.

Usage : python3 docs/urgences-sante/construire-procedure-acceptation.py
"""
import json
import re
import sys
from pathlib import Path

DEPOT = Path(__file__).resolve().parent.parent.parent
AO = DEPOT / "docs/26-015-DO_AO"
DONNEES = DEPOT / "e2e/donnees/urgences-sante"
CATALOGUE_VUES = DEPOT / "backend-java/src/main/java/ca/vina/nexu/saas/domaine/CatalogueVues.java"
I18N = DEPOT / "frontend/src/i18n.tsx"
FRONTEND = DEPOT / "frontend/src"
SEMOIR = DEPOT / "scripts/semer-urgences-sante.cjs"
SORTIE = Path(__file__).resolve().parent / "PROCEDURE-ESSAIS-ACCEPTATION.md"
MARQUE_DEBUT = "<!-- CAHIER GENERE : {} — ne pas editer a la main (construire-procedure-acceptation.py) -->"
MARQUE_FIN = "<!-- FIN CAHIER GENERE : {} -->"

ESSAI_ECRAN = "e2e/tests/e2e/urgences-sante-exigences.spec.ts"
ESSAI_COMPORTEMENTS = "e2e/tests/e2e/urgences-sante-comportements.spec.ts"

VERDICTS = {"ACQUIS": "✅ Acquis", "PARTIEL": "🟡 Partiel", "A_BATIR": "⛔ À bâtir", "HORS_CODE": "🔒 Hors code"}

#: L'ordre des familles dans le cahier : celui de l'avis.
FAMILLES = ["E-GN", "E-CH", "E-GR", "E-GP", "E-RF", "P-GN", "P-GH", "P-CH", "P-GP", "P-RF"]

#: La notion du menu réservée au paramétrage : ses écrans exigent la capacité `effectifs.configure`
#: (rôle admin) pour écrire ; on les éprouve donc sous le compte d'administration.
NOTION_ADMINISTRATION = "usadministration"


def lire_json(chemin: Path) -> dict:
    return json.loads(chemin.read_text(encoding="utf-8"))


def libelles_fr() -> dict[str, str]:
    """La première occurrence de chaque clé dans i18n.tsx est la française (FR, puis EN, puis MG)."""
    libelles: dict[str, str] = {}
    for cle, valeur in re.findall(r'^\s*"((?:nav|concept)\.[a-z]+)":\s*"([^"]*)"', I18N.read_text(encoding="utf-8"), re.M):
        libelles.setdefault(cle, valeur)
    return libelles


def catalogue_vues() -> tuple[dict[str, str], dict[str, tuple[str, str | None]]]:
    """Sections (code → libellé) et vues sans onglet (page → (section, notion)) du backend Java."""
    texte = CATALOGUE_VUES.read_text(encoding="utf-8")
    sections = dict(re.findall(r'new Section\("([a-z]+)", "([^"]+)"', texte))
    vues: dict[str, tuple[str, str | None]] = {}
    motif = r'new Vue\("([a-z:]+)", "([a-z]+)", (null|"[a-z]+"), "([a-z]+)", (null|"[a-z]+")'
    for _cle, section, notion, page, onglet in re.findall(motif, texte):
        if onglet == "null":
            vues.setdefault(page, (section, None if notion == "null" else notion.strip('"')))
    return sections, vues


def chemin_de_menu(page: str, sections: dict, vues: dict, libelles: dict) -> str:
    if page not in vues:
        sys.exit(f"écran absent du catalogue des vues (CatalogueVues.java) : {page}")
    section, notion = vues[page]
    morceaux = [sections[section]]
    if notion:
        morceaux.append(libelles.get(f"concept.{notion}", notion))
    morceaux.append(libelles.get(f"nav.{page}", page))
    return " › ".join(morceaux)


def sections_gardees_par_le_semoir() -> set[str]:
    """Les sections que le semoir laisse visibles ; un écran ailleurs est masqué dans l'organisme semé."""
    trouve = re.search(r"const SECTIONS_GARDEES = \[([^\]]*)\]", SEMOIR.read_text(encoding="utf-8"))
    if not trouve:
        sys.exit(f"SECTIONS_GARDEES introuvable dans {SEMOIR.relative_to(DEPOT)}")
    return set(re.findall(r'"([a-z]+)"', trouve.group(1)))


def comptes_du_jeu() -> dict[str, str]:
    """Le compte à utiliser par profil, lu dans le jeu de données semé par scripts/semer-urgences-sante.cjs."""
    par_role = {}
    for p in lire_json(DONNEES / "demonstration.json")["personnes"]:
        par_role.setdefault(p["compte"]["role"], f"`{p['compte']['username']}` ({p['profil']})")
    par_role["admin"] = "`admin` (administration de l'organisme)"
    return par_role


def profil_pour(page: str, vues: dict, comptes: dict) -> str:
    section, notion = vues[page]
    if page.startswith(("mon", "mes", "ma")):
        return comptes["employee"]
    if notion == NOTION_ADMINISTRATION or section != "urgencessante":
        return comptes["admin"]
    return comptes["manager"]


def identifiants_du_front() -> tuple[set[str], list[str]]:
    """Les identifiants d'essai écrits en clair dans le front-end, et les préfixes des identifiants composés."""
    litteraux: set[str] = set()
    prefixes: list[str] = []
    for f in FRONTEND.rglob("*.tsx"):
        texte = f.read_text(encoding="utf-8", errors="ignore")
        litteraux.update(re.findall(r'(?:data-testid|testId|testIdPrefixe|prefixe|prefixeIds)=?\s*[:=]?\s*"([a-z0-9-]+)"', texte))
        litteraux.update(re.findall(r'testId:\s*"([a-z0-9-]+)"', texte))
        prefixes += [p for p in re.findall(r'(?:data-testid|testId)=\{`([a-z0-9-]*)\$\{', texte) if p]
    return litteraux, prefixes


def commande_trouvee(preuve: str, litteraux: set[str], prefixes: list[str]) -> bool:
    """
    Une commande citée existe si une de ses variantes (`a|b`) est écrite telle quelle, commence par un
    préfixe composé (`${prefixe}-…`), ou prolonge un identifiant transmis à un composant commun
    (`testId="per-ajout"` → `per-ajout-ouvrir` ; `prefixe="jrn"` → `jrn-exporter`).
    """
    for testid in preuve.removeprefix("prefixe:").split("|"):
        if testid in litteraux or any(testid.startswith(p) for p in prefixes):
            return True
        morceaux = testid.split("-")
        if any("-".join(morceaux[:n]) in litteraux for n in range(1, len(morceaux))):
            return True
    return False


def commande_en_mots(preuve: str) -> str:
    """La notation de la recette (`a|b` : l'une ou l'autre ; `prefixe:x` : une ligne par objet) en français."""
    if preuve.startswith("prefixe:"):
        return f"une commande `{preuve.removeprefix('prefixe:')}…` par objet listé"
    return " ou ".join(f"`{x}`" for x in preuve.split("|"))


def geste_en_mots(g: dict) -> str:
    """Un geste préalable en français (même vocabulaire que construire-couverture.py)."""
    pourquoi = f" ({g['pourquoi'][0].lower()}{g['pourquoi'][1:].rstrip('.')})" if g.get("pourquoi") else ""
    if "cliquer" in g:
        return f"Activer la commande `{g['cliquer']}`{pourquoi}"
    if "choisir" in g:
        return f"Choisir une valeur dans `{g['choisir']}`{pourquoi}"
    if "remplir" in g:
        return f"Saisir « {g.get('valeur', '')} » dans `{g['remplir']}`{pourquoi}"
    sys.exit(f"geste sans verbe dans la recette : {g}")


def scenarios_par_exigence() -> dict[str, list[dict]]:
    par_id: dict[str, list[dict]] = {}
    for s in lire_json(DONNEES / "comportements.json")["scenarios"]:
        for i in s.get("exigences", []):
            par_id.setdefault(i, []).append(s)
    return par_id


def verifier_essais(recette: dict) -> None:
    manquants = [e for e in recette.get("essaiProfond", []) if not (DEPOT / e).exists()]
    if manquants:
        sys.exit(f"essai introuvable pour {recette['id']} : {', '.join(manquants)}")


def essais_en_mots(recette: dict, scenarios: list[dict]) -> list[str]:
    lignes = [f"  - Écran et commandes : `{ESSAI_ECRAN}` (avec audit axe-core)"]
    java = [Path(e).stem for e in recette.get("essaiProfond", []) if e.startswith("backend-java/")]
    e2e = [Path(e).name for e in recette.get("essaiProfond", []) if e.startswith("e2e/tests/")]
    if java:
        lignes.append("  - Comportement (Java) : " + ", ".join(f"`{x}`" for x in java))
    if e2e:
        lignes.append("  - Bout en bout : " + ", ".join(f"`{x}`" for x in e2e))
    if scenarios:
        lignes.append(f"  - Scénarios d'API (`{ESSAI_COMPORTEMENTS}`) : " + ", ".join(s["id"] for s in scenarios))
    return lignes


def cas(exigence: dict, recette: dict, contexte: dict) -> list[str]:
    page = recette["page"]
    chemin = chemin_de_menu(page, contexte["sections"], contexte["vues"], contexte["libelles"])
    profil = profil_pour(page, contexte["vues"], contexte["comptes"])
    scenarios = contexte["scenarios"].get(exigence["id"], [])
    etapes = [f"Se connecter à l'organisme d'essai avec {profil}.", f"Ouvrir **{chemin}** (adresse `/{page}`)."]
    etapes += [geste_en_mots(g) + "." for g in recette.get("prealable", [])]
    etapes.append(recette["geste"])
    commandes = ", ".join(commande_en_mots(c) for c in recette["preuve"])
    lignes = [
        f"### {titre_du_cas(exigence)}",
        "",
        f"> {exigence['description']}",
        "",
        f"- **Verdict à la matrice** : {VERDICTS[contexte['verdicts'][exigence['id']]]}",
        f"- **Écran** : {chemin} — `/{page}`",
        f"- **Profil** : {profil}",
        *([f"- ⚠ **Écran masqué dans l'organisme semé** : sa section n'est pas gardée par `{SEMOIR.name}` ; "
           "rétablir la vue avant d'exécuter le cas (§ 11)."] if contexte["vues"][page][0] not in contexte["gardees"] else []),
        "- **Étapes** :",
        *[f"  {n}. {e}" for n, e in enumerate(etapes, 1)],
        "- **Résultat attendu** :",
        f"  - les commandes {commandes} sont présentes et utilisables au clavier ;",
        "  - le geste décrit à la dernière étape produit l'effet annoncé, sans erreur affichée ;",
        "  - l'écran, dans l'état atteint, ne présente aucune violation axe-core (WCAG 2.2 AA).",
    ]
    for s in scenarios:
        lignes.append(f"  - comportement {s['id']} : {s['titre']}.")
    lignes += ["- **Essais automatisés qui le couvrent déjà** :", *essais_en_mots(recette, scenarios)]
    lignes += ["- **Résultat obtenu** : ☐ Conforme ☐ Non conforme — anomalie n° ______ — gravité ____ — initiales ____", ""]
    return lignes


def sommaire(exigences: list[dict], recettes: dict, contexte: dict) -> list[str]:
    lignes = ["| Cas | Famille | Type | Écran | Profil | Scénarios d'API | Verdict |", "|---|---|---|---|---|---|---|"]
    for e in exigences:
        r = recettes[e["id"]]
        profil = profil_pour(r["page"], contexte["vues"], contexte["comptes"]).split(" ")[0]
        scen = ", ".join(s["id"] for s in contexte["scenarios"].get(e["id"], [])) or "—"
        lignes.append(f"| [CA-{e['id']}](#{ancre(titre_du_cas(e))}) | {e['famille']} | {e['type']} "
                      f"| `/{r['page']}` | {profil} | {scen} | {VERDICTS[contexte['verdicts'][e['id']]]} |")
    return lignes


def titre_du_cas(e: dict) -> str:
    return f"CA-{e['id']} — {e['famille']} ({e['type']})"


def ancre(texte: str) -> str:
    """L'ancre que le rendu Markdown de GitHub donne à un titre (minuscules, ponctuation retirée, espaces → tirets)."""
    return re.sub(r"[^\w\- ]", "", texte.lower()).replace(" ", "-")


def ordre(e: dict) -> tuple[int, int]:
    prefixe, numero = e["id"].rsplit("-", 1)
    return FAMILLES.index(prefixe), int(numero)


def remplacer(texte: str, cle: str, contenu: list[str]) -> str:
    debut, fin = MARQUE_DEBUT.format(cle), MARQUE_FIN.format(cle)
    if debut not in texte or fin not in texte:
        sys.exit(f"marques absentes dans {SORTIE.name} : {cle}")
    avant, reste = texte.split(debut, 1)
    _, apres = reste.split(fin, 1)
    return avant + debut + "\n" + "\n".join(contenu) + "\n" + fin + apres


def main() -> None:
    exigences = [e for e in lire_json(AO / "exigences-26-015-DO.json")["exigences"] if "-TN-" not in e["id"]]
    recettes = {r["id"]: r for r in lire_json(DONNEES / "exigences-fonctionnelles.json")["exigences"]}
    sans_recette = [e["id"] for e in exigences if e["id"] not in recettes]
    if sans_recette:
        sys.exit("exigences fonctionnelles sans recette : " + ", ".join(sans_recette))
    sections, vues = catalogue_vues()
    contexte = {
        "sections": sections, "vues": vues, "libelles": libelles_fr(), "comptes": comptes_du_jeu(),
        "scenarios": scenarios_par_exigence(), "gardees": sections_gardees_par_le_semoir(),
        "verdicts": {j["id"]: j["verdict"] for j in lire_json(AO / "couverture-26-015-DO.json")["jugements"]},
    }
    exigences.sort(key=ordre)
    litteraux, prefixes = identifiants_du_front()
    introuvables = []
    corps: list[str] = []
    for e in exigences:
        verifier_essais(recettes[e["id"]])
        introuvables += [f"{e['id']} → {c}" for c in recettes[e["id"]]["preuve"] if not commande_trouvee(c, litteraux, prefixes)]
        corps += cas(e, recettes[e["id"]], contexte)
    texte = SORTIE.read_text(encoding="utf-8")
    texte = remplacer(texte, "sommaire", sommaire(exigences, recettes, contexte))
    texte = remplacer(texte, "cas", corps)
    SORTIE.write_text(texte, encoding="utf-8")
    essentiels = sum(1 for e in exigences if e["type"] == "essentiel")
    print(f"{len(exigences)} cas écrits ({essentiels} essentiels, {len(exigences) - essentiels} préférentiels) → {SORTIE.relative_to(DEPOT)}")
    if introuvables:
        print(f"{len(introuvables)} commande(s) citée(s) non retrouvée(s) telle(s) quelle(s) dans frontend/src :")
        print("\n".join(f"  {x}" for x in introuvables))


if __name__ == "__main__":
    main()
