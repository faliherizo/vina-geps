#!/usr/bin/env python3
"""
Tableau des 144 exigences du 26-015-DO, revalidées par les scénarios BDD joués contre le serveur Java.

Croise quatre sources, sans en éditer aucune :
  - le besoin du client, mot pour mot (exigences-26-015-DO.json) ;
  - le statut mesuré et les preuves Java de la grille (10-GRILLE-EXIGENCES-FONCTIONNELLES.md,
    couverture-26-015-DO.json) ;
  - les scénarios Gherkin qui portent l'étiquette de l'exigence (backend-java/src/bdd/resources/features) ;
  - le résultat du dernier passage de la suite BDD (backend-java/target/bdd/exigences.json), s'il existe.

Le document se régénère ; il ne s'édite pas à la main.

Usage :
  python3 docs/26-015-DO_AO/construire-tableau-bdd.py             # écrit 11-EXIGENCES-BDD.md
  python3 docs/26-015-DO_AO/construire-tableau-bdd.py --verifier  # échoue si une exigence fonctionnelle
                                                                  # n'a pas de scénario (hors exceptions motivées)
"""
import json
import re
import sys
from collections import Counter, defaultdict
from pathlib import Path

RACINE = Path(__file__).resolve().parent
DEPOT = RACINE.parent.parent
SORTIE = RACINE / "11-EXIGENCES-BDD.md"
FEATURES = DEPOT / "backend-java/src/bdd/resources/features"
RESULTATS = DEPOT / "backend-java/target/bdd/exigences.json"
ETIQUETTE = re.compile(r"@([EP]-[A-Z]{2}-\d+)")

#: Une exigence fonctionnelle sans scénario BDD doit l'être pour une raison écrite ici, et prouvée ailleurs.
SANS_SCENARIO = {
    "P-CH-4": "Le choix automatique part à l'expiration du tour (5 minutes au moins) : un scénario en temps réel "
              "ne peut l'attendre. Prouvé horloge figée : `ControleurAgphEffectifsHttpTest#expiration`.",
}

#: Ce que la revalidation a appris au-delà du vert : limites du scénario, défauts trouvés.
REMARQUES = {
    "P-GH-4": "Le scénario prouve que l'ouverture de la collecte planifie l'échéance et que l'attribution donne le quart "
              "au déposant ; le départ de la tâche à l'échéance est prouvé horloge figée "
              "(`ControleurDisponibilitesEffectifsHttpTest#finDeCollecteAutomatique`).",
    "P-CH-7": "Prouvé par la révision (le poste retiré laisse la personne sans poste) ; le passage par expiration du tour "
              "l'est horloge figée.",
    "E-GP-13": "Le scénario sautait de 19 h à minuit (`Contexte.aujourdhui()` datait en UTC) : **corrigé le 13 nov. 2026**, le jour est celui de Montréal.",
    "P-GP-4": "**Écart corrigé le 13 nov. 2026** : une saisie après coup hors de tout quart qui recouvrait une saisie précédente était acceptée ; "
              "elle est désormais refusée comme sur un quart (`ControleurPresenceEffectifsHttpTest#cumulHorsQuart`).",
    "E-RF-13": "Le scénario couvre l'import des horodatages tiers (doublons, lignes fautives) ; la corrélation dans la "
               "feuille (temps proposé, sources divergentes) est prouvée horloge figée "
               "(`ControleurTempsFluxEffectifsHttpTest#recit`).",
    "E-TN-17": "Plancher à vide (moins de 2 s par transaction de base) ; la mesure contractuelle sous charge relève "
               "de la campagne JMeter (`perf/jmeter`).",
}

ICONE = {"ACQUIS": "✅ Acquis", "PARTIEL": "🟡 Partiel", "HORS_CODE": "🔒 Hors code"}


CLAUSES = RACINE / "clauses-exigences.json"


def clauses():
    """Les clauses vérifiables de chaque exigence fonctionnelle, et la preuve de chacune."""
    return {k: v for k, v in json.loads(CLAUSES.read_text(encoding="utf-8")).items() if not k.startswith("_")}


def resoudre(preuve, scs):
    """Une preuve « bdd:<fichier>|<début du titre> » désigne un scénario ; « java:… » un essai à horloge figée."""
    if not preuve:
        return None
    if preuve.startswith("java:"):
        return {"java": preuve[5:]}
    fichier, titre = preuve[4:].split("|", 1)
    return next((s for s in scs if s["fichier"] == f"{fichier}.feature" and s["nom"].startswith(titre)), None)


def exigences():
    d = json.loads((RACINE / "exigences-26-015-DO.json").read_text(encoding="utf-8"))
    return d if isinstance(d, list) else next(v for v in d.values() if isinstance(v, list))


def jugements():
    c = json.loads((RACINE / "couverture-26-015-DO.json").read_text(encoding="utf-8"))
    return {j["id"]: j for j in c["jugements"]}


def grille():
    """Statut et preuves par exigence, tels que la grille les affiche."""
    lignes = {}
    for ligne in (RACINE / "10-GRILLE-EXIGENCES-FONCTIONNELLES.md").read_text(encoding="utf-8").splitlines():
        m = re.match(r"^\| \*\*([EP]-[A-Z]{2}-\d+)\*\* \|(.*)\|$", ligne)
        if not m:
            continue
        cellules = [c.strip() for c in m.group(2).split("|")]
        statut = next((c for c in cellules if c[:1] in "✅🟡🔒❌"), "")
        lignes[m.group(1)] = {"statut": statut, "preuves": cellules[-1] if statut.startswith("✅") else ""}
    return lignes


def scenarios():
    """Chaque scénario : son fichier, sa ligne, son nom, ses étiquettes d'exigence."""
    trouves = []
    for f in sorted(FEATURES.glob("*.feature")):
        etiquettes = []
        for n, ligne in enumerate(f.read_text(encoding="utf-8").splitlines(), start=1):
            texte = ligne.strip()
            if texte.startswith("@"):
                etiquettes += ETIQUETTE.findall(texte)
            elif texte.startswith("Scénario:"):
                trouves.append({"fichier": f.name, "ligne": n, "nom": texte.split(":", 1)[1].strip(), "exigences": etiquettes})
                etiquettes = []
            elif texte and not texte.startswith("#"):
                etiquettes = []
    return trouves


def resultats():
    """Le statut de chaque scénario au dernier passage : passé, échoué, sauté ; vide si la suite n'a pas tourné."""
    if not RESULTATS.exists():
        return {}
    statuts = {}
    for feature in json.loads(RESULTATS.read_text(encoding="utf-8")):
        fichier = Path(feature.get("uri", "")).name
        for s in feature.get("elements", []):
            etapes = [e.get("result", {}).get("status") for e in s.get("before", []) + s.get("steps", [])]
            statut = "failed" if "failed" in etapes else "skipped" if any(x in ("skipped", "pending", "undefined") for x in etapes) else "passed"
            statuts[(fichier, s.get("line"))] = statut
    return statuts


def revalidation(scs, statuts, exigence):
    if not scs:
        return "— sans scénario" if exigence not in SANS_SCENARIO else "⏱ hors temps réel"
    vus = [statuts.get((s["fichier"], s["ligne"])) for s in scs]
    if not statuts:
        return "▫ non joué"
    if "failed" in vus:
        return "❌ échoue"
    if all(v == "passed" for v in vus):
        return "✅ vérifié"
    if "passed" in vus:
        return "✅ vérifié (un scénario sauté)"
    return "⏭ sauté"


def cellule(texte):
    return " ".join(str(texte).split()).replace("|", "\\|")


def construire():
    exs, juges, grl, scs, statuts, cls = exigences(), jugements(), grille(), scenarios(), resultats(), clauses()
    par_exigence = defaultdict(list)
    for s in scs:
        for e in s["exigences"]:
            par_exigence[e].append(s)
    lignes_tableau, compteur = [], Counter()
    for e in exs:
        i = e["id"]
        fonctionnelle = i.split("-")[1] != "TN"
        g = grl.get(i, {})
        statut = g.get("statut") or ICONE.get(juges.get(i, {}).get("verdict"), "?")
        preuves = g.get("preuves") or juges.get(i, {}).get("preuves", "")
        reval = revalidation(par_exigence[i], statuts, i)
        compteur[("fonctionnelle" if fonctionnelle else "technologique", reval)] += 1
        liens = "<br>".join(f"[{s['fichier']}:{s['ligne']}](../../backend-java/src/bdd/resources/features/{s['fichier']}#L{s['ligne']}) "
                            f"{cellule(s['nom'])}" for s in par_exigence[i]) or "—"
        remarque = REMARQUES.get(i) or SANS_SCENARIO.get(i) or ("" if fonctionnelle or par_exigence[i] else "Se prouve hors de l'API.")
        cl = cls.get(i, [])
        prouvees = [c for c in cl if resoudre(c[1], scs)]
        non = [c[0] for c in cl if not resoudre(c[1], scs)]
        par_java = [c[0] for c in cl if (resoudre(c[1], scs) or {}).get("java")]
        clauses_txt = "—" if not fonctionnelle else (f"{len(prouvees)}/{len(cl)}" + (f" — **non prouvées** : {cellule('; '.join(non))}" if non else "")
                                                     + (f" — horloge figée : {cellule('; '.join(par_java))}" if par_java else ""))
        lignes_tableau.append(f"| **{i}** | {'essentiel' if i.startswith('E') else 'préférentiel'} | {cellule(e['description'])} | "
                              f"{statut} | {reval} | {clauses_txt} | {liens} | {cellule(preuves) or '—'} | {cellule(remarque) or '—'} |")
    return compteur, lignes_tableau, len(scs)


def ecrire(compteur, lignes_tableau, nb_scenarios):
    scs, cls = scenarios(), clauses()
    toutes = [c for v in cls.values() for c in v]
    nb_clauses = len(toutes)
    nb_java = sum(1 for c in toutes if (resoudre(c[1], scs) or {}).get("java"))
    nb_sans = sum(1 for c in toutes if not resoudre(c[1], scs))
    nb_bdd = nb_clauses - nb_java - nb_sans
    synthese = []
    for genre in ("fonctionnelle", "technologique"):
        total = sum(v for (g, _), v in compteur.items() if g == genre)
        detail = " · ".join(f"{r} : {v}" for (g, r), v in sorted(compteur.items()) if g == genre)
        synthese.append(f"| Exigences {genre}s | {total} | {detail} |")
    texte = f"""# Exigences du 26-015-DO — revalidation par les scénarios BDD

> Généré par `construire-tableau-bdd.py` ; ne pas éditer à la main. Les scénarios vivent dans
> [`backend-java/src/bdd`](../../backend-java/src/bdd/README.md) et se jouent contre le serveur Java
> réel (base Liquibase vierge, un organisme neuf par scénario). La colonne « Revalidation BDD » lit le
> dernier passage de la suite (`backend-java/target/bdd/exigences.json`).

## Synthèse

{nb_scenarios} scénarios Gherkin. Les 90 exigences fonctionnelles sont découpées en {nb_clauses} clauses
vérifiables (`clauses-exigences.json`), mot pour mot du client autant que possible : {nb_bdd} sont prouvées
par un scénario joué contre le serveur, {nb_java} par un essai Java à horloge figée (le temps réel ne peut
pas attendre une échéance), {nb_sans} sans preuve.

| | Total | Revalidation |
|---|---:|---|
{chr(10).join(synthese)}

Légende : ✅ vérifié — tous les scénarios de l'exigence passent · ⏭ sauté — la précondition n'est pas
réunie à l'heure du passage (voir la remarque) · ⏱ hors temps réel — prouvé horloge figée par un essai
Java · — sans scénario — exigence technologique ou engagement qui ne se constate pas par l'API.

## Défauts et écarts trouvés par la revalidation

Corrigés le 13 novembre 2026 :

- **Fuseau du « jour »** — `Contexte.aujourdhui()` rendait la date UTC alors que les quarts sont datés à
  l'heure de Montréal. Le soir, une règle créée sans date ne valait pas pour le quart du jour, et la dater
  du jour était refusé comme « dans le passé ». Le jour est désormais celui de Montréal ; les 1 185 essais
  Java passent sans retouche.
- **Cumul hors quart (P-GP-4)** — deux saisies après coup qui se recouvraient hors de tout quart planifié
  étaient toutes deux acceptées. Le chevauchement est maintenant refusé partout.

Ouvert :

- **Bombe à retardement des données e2e** — `e2e/donnees/urgences-sante/comportements.json` date des règles
  au `2027-01-01` : dès cette date passée, leur création sera refusée (« dans le passé »). Les scénarios
  BDD emploient `{{{{demain}}}}`.

## Tableau

| Exigence | Type | Besoin du client | Statut (grille) | Revalidation BDD | Clauses prouvées | Scénario(s) | Preuves back-end (Java) | Remarque |
|---|---|---|---|---|---|---|---|---|
{chr(10).join(lignes_tableau)}
"""
    SORTIE.write_text(texte, encoding="utf-8")


def verifier():
    exs, scs = exigences(), scenarios()
    couvertes = {e for s in scs for e in s["exigences"]}
    manquantes = [e["id"] for e in exs if e["id"].split("-")[1] != "TN" and e["id"] not in couvertes and e["id"] not in SANS_SCENARIO]
    inconnues = sorted(couvertes - {e["id"] for e in exs})
    cls, ecarts = clauses(), []
    for e in exs:
        i = e["id"]
        if i.split("-")[1] == "TN":
            continue
        if i not in cls:
            print(f"exigence fonctionnelle sans découpage en clauses : {i}")
            ecarts.append(i)
        for texte, preuve in cls.get(i, []):
            if not resoudre(preuve, scs):
                print(f"{i} — clause sans preuve ou preuve introuvable : « {texte} » ({preuve})")
                ecarts.append(i)
    for m in manquantes:
        print(f"exigence fonctionnelle sans scénario : {m}")
    for i in inconnues:
        print(f"étiquette qui ne nomme aucune exigence : {i}")
    return 1 if manquantes or inconnues or ecarts else 0


if __name__ == "__main__":
    if "--verifier" in sys.argv:
        sys.exit(verifier())
    ecrire(*construire())
    print(f"écrit : {SORTIE.relative_to(DEPOT)}")
