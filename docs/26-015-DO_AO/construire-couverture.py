#!/usr/bin/env python3
"""
Couverture des 144 exigences de l'appel d'offres 26-015-DO par la plateforme existante.

Deux sources, un produit :
  - exigences-26-015-DO.json   extrait du PDF par convertir-ao.py (le texte fait foi) ;
  - addenda-26-015-DO.json     les textes que les addenda remplacent, appliqués par-dessus ;
  - couverture-26-015-DO.json  un jugement par exigence : verdict, lot cible, preuves, écart ;
  - e2e/donnees/urgences-sante/exigences-fonctionnelles.json  la recette de vérification à
    l'interface de chaque exigence fonctionnelle, exécutée par l'essai du même nom ;
  → MATRICE-COUVERTURE.md       la matrice complète et ses décomptes, régénérés ;
  → docs/urgences-sante/architecture/04-referentiel-exigences.md  ses deux tableaux de grille.

Le script échoue si une exigence n'est pas jugée, si un jugement vise une exigence inconnue ou si
un verdict sort de l'ensemble admis. Les chiffres des documents 02 à 04 se citent d'après sa
sortie, jamais de mémoire.

Usage : python3 docs/26-015-DO_AO/construire-couverture.py
"""
import json
import sys
from collections import Counter
from pathlib import Path

RACINE = Path(__file__).resolve().parent
DEPOT = RACINE.parent.parent
EXIGENCES = RACINE / "exigences-26-015-DO.json"
COUVERTURE = RACINE / "couverture-26-015-DO.json"
ADDENDA = RACINE / "addenda-26-015-DO.json"
SORTIE = RACINE / "MATRICE-COUVERTURE.md"
VERIFICATION = DEPOT / "e2e/donnees/urgences-sante/exigences-fonctionnelles.json"
REFERENTIEL = DEPOT / "docs/urgences-sante/architecture/04-referentiel-exigences.md"
LISEZ_MOI = RACINE / "README.md"
MARQUE_DEBUT = "<!-- TABLEAU GENERE : {} — ne pas editer a la main -->"
MARQUE_FIN = "<!-- FIN TABLEAU GENERE : {} -->"

#: Une exigence technologique ne se vérifie par aucun geste d'utilisateur : elle est hors du jeu.
EST_FONCTIONNELLE = lambda id_: "-TN-" not in id_  # noqa: E731

VERDICTS = {
    "ACQUIS": "✅ Acquis",
    "PARTIEL": "🟡 Partiel",
    "A_BATIR": "⛔ À bâtir",
    "HORS_CODE": "🔒 Hors code",
}
LOTS = {
    "U0": "Socle de conformité (SaaS, sécurité, disponibilité)",
    "U1": "Organisation, référentiels et traçabilité",
    "U2": "Quarts, postes et équipages",
    "U3": "Moteur de règles, disponibilités et quotas",
    "U4": "AGPH et remplacements long terme",
    "U5": "Absences et comblement",
    "U6": "Pointage et présence",
    "U7": "Feuille de temps, heures et rapports",
    "U8": "Intégrations de l'écosystème",
}


def charger_verification() -> dict[str, dict]:
    """
    La recette de vérification à l'écran, par exigence — **source unique** de la colonne du même
    nom. La porte est ici et non dans une relecture : une exigence fonctionnelle sans recette, une
    recette sans commande, ou une référence d'essai qui ne désigne plus de fichier arrêtent la
    génération. C'est ce qui manquait quand la matrice a été fausse deux fois de suite.
    """
    jeu = json.loads(VERIFICATION.read_text(encoding="utf-8"))["exigences"]
    par_id = {e["id"]: e for e in jeu}
    erreurs = [f"recette sans commande : {e['id']}" for e in jeu if not e.get("preuve")]
    erreurs += [f"recette sans geste : {e['id']}" for e in jeu if not e.get("geste")]
    for e in jeu:
        for essai in e.get("essaiProfond", []):
            if not (DEPOT / essai).exists():
                erreurs.append(f"essai introuvable : {e['id']} → {essai}")
        if not e.get("essaiProfond"):
            erreurs.append(f"aucun essai ne nomme l'exigence : {e['id']} (lancer relever-essais.cjs)")
    if erreurs:
        sys.exit("\n".join(erreurs))
    return par_id


def appliquer_addenda(exigences: list[dict]) -> list[str]:
    """
    Remplace le texte des exigences que les addenda modifient, et le dit dans le texte même :
    la grille publiée doit montrer qu'elle suit l'addenda, pas la version d'origine.
    """
    par_id = {e["id"]: e for e in exigences}
    erreurs = []
    for addendum in json.loads(ADDENDA.read_text(encoding="utf-8"))["addenda"]:
        for m in addendum["modifications"]:
            if m["id"] not in par_id:
                erreurs.append(f"addenda {addendum['numero']} : exigence inconnue {m['id']}")
                continue
            par_id[m["id"]]["description"] = f"{m['description']} *(addenda {addendum['numero']}, réponse {m['reponse']})*"
    return erreurs


def charger() -> tuple[list[dict], dict[str, dict]]:
    exigences = json.loads(EXIGENCES.read_text(encoding="utf-8"))["exigences"]
    erreurs_addenda = appliquer_addenda(exigences)
    jugements = json.loads(COUVERTURE.read_text(encoding="utf-8"))["jugements"]
    par_id = {j["id"]: j for j in jugements}
    connues = {e["id"] for e in exigences}
    erreurs = erreurs_addenda + [f"non jugée : {i}" for i in sorted(connues - par_id.keys())]
    erreurs += [f"inconnue : {i}" for i in sorted(par_id.keys() - connues)]
    erreurs += [f"verdict invalide : {j['id']} = {j['verdict']}" for j in jugements if j["verdict"] not in VERDICTS]
    erreurs += [f"lot invalide : {j['id']} = {j['lot']}" for j in jugements if j["lot"] and j["lot"] not in LOTS]
    erreurs += [f"lot manquant : {j['id']}" for j in jugements if j["verdict"] != "HORS_CODE" and not j["lot"]]
    recettes = json.loads(VERIFICATION.read_text(encoding="utf-8"))["exigences"]
    attendues = {e["id"] for e in exigences if EST_FONCTIONNELLE(e["id"])}
    connues_recette = {e["id"] for e in recettes}
    erreurs += [f"sans recette de vérification : {i}" for i in sorted(attendues - connues_recette)]
    erreurs += [f"recette d'une exigence inconnue : {i}" for i in sorted(connues_recette - attendues)]
    if erreurs:
        sys.exit("\n".join(erreurs))
    return exigences, par_id


def decompte(exigences: list[dict], par_id: dict[str, dict], type_: str) -> Counter:
    return Counter(par_id[e["id"]]["verdict"] for e in exigences if e["type"] == type_)


def ligne_decompte(libelle: str, c: Counter) -> str:
    total = sum(c.values())
    cellules = " | ".join(str(c.get(v, 0)) for v in VERDICTS)
    return f"| {libelle} | {cellules} | {total} |"


def geste_en_mots(g: dict) -> str:
    """Un geste préalable, dit en français : l'évaluateur lit la cellule, pas le fichier JSON."""
    if "cliquer" in g:
        return f"Ouvrir d'abord `{g['cliquer']}`."
    if "choisir" in g:
        return f"Choisir d'abord une valeur dans `{g['choisir']}`."
    if "remplir" in g:
        return f"Saisir d'abord « {g.get('valeur', '')} » dans `{g['remplir']}`."
    raise SystemExit(f"geste sans verbe dans la recette : {g}")


def recette_en_mots(r: dict | None) -> str:
    """La recette en une cellule : l'écran, puis les gestes, puis les commandes à y trouver."""
    if r is None:
        return "— (exigence technologique : aucun geste d'utilisateur)"
    ecran = f"`/{r['page']}" + (f"?onglet={r['onglet']}`" if r.get("onglet") else "`")
    gestes = "".join(f" {geste_en_mots(g)}" for g in r.get("prealable", []))
    commandes = ", ".join(f"`{c}`" for c in r["preuve"])
    return f"{ecran} — {r['geste']}{gestes} Commandes : {commandes}"


def preuves_de(j: dict, recette: dict | None) -> str:
    """
    **Les preuves d'une exigence fonctionnelle se calculent, elles ne s'écrivent pas.** Ce sont les
    essais relevés par `relever-essais.cjs` — Java pour le backend, Playwright pour le bout en bout —
    et non une prose qui nommait des fichiers du Node et vieillissait avec eux. Une exigence
    technologique, qu'aucun essai ne nomme, garde le jugement écrit.
    """
    essais = (recette or {}).get("essaiProfond") or []
    if not essais:
        return (j["preuves"] or "—").replace("|", "/")
    java = [Path(x).stem for x in essais if x.startswith("backend-java/")]
    e2e = [Path(x).name for x in essais if x.startswith("e2e/")]
    parties = []
    if java:
        parties.append("Java : " + ", ".join(f"`{x}`" for x in java))
    if e2e:
        parties.append("bout en bout : " + ", ".join(f"`{x}`" for x in e2e))
    return " ; ".join(parties)


def tableau_referentiel(exigences: list[dict], par_id: dict[str, dict], recettes: dict[str, dict], type_: str) -> list[str]:
    """
    Le tableau de la grille de conformité, **avec** la colonne de vérification. La réponse du
    soumissionnaire reste à remplir : répondre « Oui » à un critère éliminatoire est une décision
    d'entreprise, pas une conclusion que ce script peut tirer. Le verdict mesuré est à côté, pour
    que la décision se prenne sur une mesure.
    """
    reponse = "OUI / NON" if type_ == "essentiel" else "OUI / NON / AVEC RÉSERVES"
    lignes = [
        "| # | Famille | Description de la fonctionnalité | Réponse du soumissionnaire | Verdict mesuré | Comment le vérifier à l'interface | Références dans la documentation |",
        "|---|---|---|---|---|---|---|",
    ]
    for e in (x for x in exigences if x["type"] == type_):
        j = par_id[e["id"]]
        texte = e["description"].replace("|", "/")
        recette = recette_en_mots(recettes.get(e["id"])).replace("|", "/")
        preuves = preuves_de(j, recettes.get(e["id"]))
        lignes.append(
            f"| **{e['id']}** | {e['famille']} | {texte} | {reponse} | {VERDICTS[j['verdict']]} "
            f"| {recette} | {preuves} |"
        )
    return lignes


def ecrire_referentiel(exigences: list[dict], par_id: dict[str, dict], recettes: dict[str, dict]) -> None:
    """Remplace, entre ses marques, chaque tableau du référentiel 04 — le reste du texte est à nous."""
    texte = REFERENTIEL.read_text(encoding="utf-8")
    for cle, type_ in (("essentiels", "essentiel"), ("preferentiels", "préférentiel")):
        debut, fin = MARQUE_DEBUT.format(cle), MARQUE_FIN.format(cle)
        if debut not in texte or fin not in texte:
            sys.exit(f"{REFERENTIEL.name} : marques « {cle} » absentes — les poser avant de régénérer.")
        avant, reste = texte.split(debut, 1)
        _, apres = reste.split(fin, 1)
        bloc = "\n".join(tableau_referentiel(exigences, par_id, recettes, type_))
        texte = f"{avant}{debut}\n{bloc}\n{fin}{apres}"
    REFERENTIEL.write_text(texte, encoding="utf-8")
    print(f"→ {REFERENTIEL.relative_to(DEPOT)}")


def ecrire_chiffres(ess: Counter, pref: Counter, exigences: list[dict], par_id: dict[str, dict]) -> None:
    """Les décomptes du README, écrits par le script. Tenus à la main, ils ont menti trois fois."""
    fonc = [e for e in exigences if EST_FONCTIONNELLE(e["id"])]
    tech = [e for e in exigences if not EST_FONCTIONNELLE(e["id"])]
    bloc = [
        "| Type | " + " | ".join(VERDICTS.values()) + " | Total |",
        "|---|" + "---:|" * (len(VERDICTS) + 1),
        ligne_decompte("Essentiels (un « Non » élimine)", ess),
        ligne_decompte("Préférentiels (25 % de la qualité)", pref),
        ligne_decompte("**Total**", ess + pref),
        "",
        "| Nature | " + " | ".join(VERDICTS.values()) + " | Total |",
        "|---|" + "---:|" * (len(VERDICTS) + 1),
        ligne_decompte("Fonctionnelles (un geste les vérifie)",
                       Counter(par_id[e["id"]]["verdict"] for e in fonc)),
        ligne_decompte("Technologiques (engagement, exploitation, mesure)",
                       Counter(par_id[e["id"]]["verdict"] for e in tech)),
    ]
    texte = LISEZ_MOI.read_text(encoding="utf-8")
    debut, fin = MARQUE_DEBUT.format("chiffres"), MARQUE_FIN.format("chiffres")
    if debut not in texte or fin not in texte:
        sys.exit(f"{LISEZ_MOI.name} : marques « chiffres » absentes.")
    avant, reste = texte.split(debut, 1)
    _, apres = reste.split(fin, 1)
    LISEZ_MOI.write_text(f"{avant}{debut}\n" + "\n".join(bloc) + f"\n{fin}{apres}", encoding="utf-8")
    print(f"→ {LISEZ_MOI.relative_to(DEPOT)}")


def main() -> None:
    exigences, par_id = charger()
    recettes = charger_verification()
    ess, pref = decompte(exigences, par_id, "essentiel"), decompte(exigences, par_id, "préférentiel")

    md = [
        "# 26-015-DO — Matrice de couverture des exigences",
        "",
        "> **Fichier généré** par `construire-couverture.py` à partir de `exigences-26-015-DO.json`",
        "> et `couverture-26-015-DO.json`. Ne pas l'éditer à la main : corriger le jugement, puis",
        "> régénérer.",
        "",
        "Légende : ✅ acquis et prouvé par des essais · 🟡 partiel, le socle existe · ⛔ à bâtir ·",
        "🔒 hors code (engagement, dotation ou fait commercial de l'entreprise).",
        "",
        "## Décompte",
        "",
        "| Type | " + " | ".join(VERDICTS.values()) + " | Total |",
        "|---|" + "---:|" * (len(VERDICTS) + 1),
        ligne_decompte("Essentiels (un « Non » élimine)", ess),
        ligne_decompte("Préférentiels (25 % de la qualité)", pref),
        ligne_decompte("**Total**", ess + pref),
        "",
        "## Par famille",
        "",
        "| Famille | Type | " + " | ".join(VERDICTS.values()) + " | Total |",
        "|---|---|" + "---:|" * (len(VERDICTS) + 1),
    ]
    familles = sorted({(e["famille"], e["type"]) for e in exigences}, key=lambda f: (f[1] != "essentiel", f[0]))
    for famille, type_ in familles:
        c = Counter(par_id[e["id"]]["verdict"] for e in exigences if e["famille"] == famille and e["type"] == type_)
        md.append(ligne_decompte(f"{famille} | {type_}", c))

    md += ["", "## Par lot de réalisation", "", "| Lot | Objet | Essentiels ouverts | Préférentiels ouverts |", "|---|---|---:|---:|"]
    for lot, objet in LOTS.items():
        ouverts = [e for e in exigences if par_id[e["id"]]["lot"] == lot and par_id[e["id"]]["verdict"] in ("PARTIEL", "A_BATIR")]
        n_ess = sum(1 for e in ouverts if e["type"] == "essentiel")
        md.append(f"| {lot} | {objet} | {n_ess} | {len(ouverts) - n_ess} |")

    md += ["", "## Matrice complète", ""]
    for type_ in ("essentiel", "préférentiel"):
        md += [f"### Critères {type_}s", "", "| # | Exigence | Verdict | Lot | Comment le vérifier à l'interface | Preuves dans le dépôt | Écart |", "|---|---|---|---|---|---|---|"]
        for e in (x for x in exigences if x["type"] == type_):
            j = par_id[e["id"]]
            texte = e["description"].replace("|", "/")
            recette = recette_en_mots(recettes.get(e["id"])).replace("|", "/")
            md.append(
                f"| {e['id']} | {texte} | {VERDICTS[j['verdict']]} | {j['lot'] or '—'} | {recette} "
                f"| {preuves_de(j, recettes.get(e['id']))} | {(j['ecart'] or '—').replace('|', '/')} |"
            )
        md.append("")

    SORTIE.write_text("\n".join(md).rstrip() + "\n", encoding="utf-8")
    ecrire_referentiel(exigences, par_id, recettes)
    ecrire_chiffres(ess, pref, exigences, par_id)
    for libelle, c in (("Essentiels", ess), ("Préférentiels", pref)):
        print(f"{libelle:14} " + "  ".join(f"{VERDICTS[v]} {c.get(v, 0)}" for v in VERDICTS) + f"  (total {sum(c.values())})")
    print(f"→ {SORTIE.name}")


if __name__ == "__main__":
    main()
