#!/usr/bin/env python3
"""
Dictionnaire de données et métamodèle, **lus du schéma réel** — E-TN-13 (« une documentation du
métamodèle de données est fournie avec la solution ») et Contrat 6.05 (« le dictionnaire de données
qui donne la définition et le format des champs [...] le schéma de la base »).

Pourquoi lire une base plutôt que les migrations : le Java crée une partie de ses tables à son
démarrage (les `Schema*` des contextes, comme les effectifs). Seule une base sur laquelle le
backend a démarré porte le schéma complet. Une documentation écrite à la main, elle, se périmerait
à la première migration.

    python3 backend-java/scripts/dictionnaire-donnees.py <base.db>              # régénère
    python3 backend-java/scripts/dictionnaire-donnees.py <base.db> --verifier   # échoue si périmé

La base : celle de la campagne e2e après démarrage du Java (`campagne-e2e.sh`), ou une copie de
celle de la pile Docker. Rien n'y est écrit : elle est ouverte en lecture seule.
"""
import json
import sqlite3
import sys
from collections import defaultdict
from pathlib import Path

DEPOT = Path(__file__).resolve().parents[2]
SORTIE_MD = DEPOT / "docs/donnees/DICTIONNAIRE-DONNEES.md"
SORTIE_JSON = DEPOT / "docs/donnees/metamodele.json"

#: Les colonnes qui portent l'organisme : leur présence fait le cloisonnement par établissement.
COLONNES_ORGANISME = ("tenantId", "tenant_id")
#: Les tables du moteur, sans donnée métier : décrites à part pour ne pas noyer le dictionnaire.
TECHNIQUES = {"DATABASECHANGELOG", "DATABASECHANGELOGLOCK", "schema_migrations", "seq"}
#: Le préfixe d'une table désigne son domaine ; les principaux sont nommés, les autres gardent leur préfixe.
DOMAINES = {
    "eff": "Gestion des effectifs (AO 26-015-DO)",
    "horaire": "Horaires cliniques",
    "rh": "Ressources humaines",
    "scol": "Scolaire",
    "mun": "Municipal",
    "varpay": "Paie variable",
    "budget": "Budget",
    "gl": "Grand livre",
    "absence": "Absences",
    "pointage": "Pointage",
    "timesheet": "Feuilles de temps (historique)",
    "tenant": "Organismes",
    "auth": "Authentification",
    "audit": "Audit",
    "notification": "Avis",
    "msg": "Messagerie",
    "analytique": "Analytique",
    "etl": "Échanges de données",
}


def lire(chemin: str) -> list[dict]:
    base = sqlite3.connect(f"file:{chemin}?mode=ro", uri=True)
    noms = [r[0] for r in base.execute("SELECT name FROM sqlite_master WHERE type = 'table' AND name NOT LIKE 'sqlite_%' ORDER BY name")]
    tables = []
    for nom in noms:
        colonnes = [{"nom": c[1], "type": c[2] or "—", "obligatoire": bool(c[3]), "defaut": c[4], "cle": c[5] > 0}
                    for c in base.execute(f'PRAGMA table_info("{nom}")')]
        index = []
        for i in base.execute(f'PRAGMA index_list("{nom}")'):
            # Une colonne sans nom est une expression indexée (p. ex. lower(code)).
            cols = [r[2] or "(expression)" for r in base.execute(f'PRAGMA index_info("{i[1]}")')]
            index.append({"nom": i[1], "unique": bool(i[2]), "colonnes": cols})
        tables.append({
            "nom": nom,
            "domaine": domaine(nom),
            "technique": nom in TECHNIQUES,
            "cloisonnee": any(c["nom"] in COLONNES_ORGANISME for c in colonnes),
            "colonnes": colonnes,
            "index": sorted(index, key=lambda x: x["nom"]),
        })
    return tables


def domaine(nom: str) -> str:
    prefixe = nom.split("_")[0]
    return DOMAINES.get(prefixe, prefixe)


def cellule(v) -> str:
    return "—" if v is None else str(v).replace("|", "/")


def markdown(tables: list[dict]) -> str:
    metier = [t for t in tables if not t["technique"]]
    cloisonnees = sum(t["cloisonnee"] for t in metier)
    par_domaine = defaultdict(list)
    for t in metier:
        par_domaine[t["domaine"]].append(t)
    l = [
        "# Dictionnaire de données et métamodèle",
        "",
        "> **Fichier généré** par `backend-java/scripts/dictionnaire-donnees.py` depuis le schéma d'une",
        "> base sur laquelle le backend Java a démarré. Ne pas l'éditer à la main. Version lisible par",
        "> une machine : [metamodele.json](metamodele.json).",
        "",
        "## Lecture",
        "",
        f"- **{len(metier)} tables métier**, plus {len(tables) - len(metier)} tables du moteur de migration.",
        f"- **{cloisonnees} tables portent l'organisme** (`tenantId`) : c'est le cloisonnement entre",
        "  établissements, filtré par chaque requête. Les autres sont des tables globales (organismes,",
        "  plans) ou des tables filles rattachées à une ligne qui, elle, porte l'organisme.",
        "- **L'intégrité référentielle est applicative** : SQLite ne déclare pas de clé étrangère ici ;",
        "  une colonne `xxxId` désigne l'identifiant d'une ligne de la table `xxx`, vérifié par le",
        "  service qui écrit.",
        "- Les dates sont du texte ISO-8601 (`AAAA-MM-JJ` ou instant UTC) ; les montants et durées sont",
        "  des nombres ; un champ `TEXT` nommé `attributs`, `parametres`, `avant` ou `apres` contient du JSON.",
        "",
        "## Domaines",
        "",
        "| Domaine | Tables | Cloisonnées par organisme |",
        "|---|---:|---:|",
    ]
    for d in sorted(par_domaine, key=lambda x: (-len(par_domaine[x]), x)):
        ts = par_domaine[d]
        l.append(f"| {d} | {len(ts)} | {sum(t['cloisonnee'] for t in ts)} |")
    l += ["", "## Tables", ""]
    for d in sorted(par_domaine):
        l += [f"### {d}", ""]
        for t in par_domaine[d]:
            l += [f"#### `{t['nom']}`" + ("" if t["cloisonnee"] else " — sans colonne d'organisme"), "",
                  "| Colonne | Type | Obligatoire | Défaut | Clé |", "|---|---|---|---|---|"]
            for c in t["colonnes"]:
                l.append(f"| `{c['nom']}` | {cellule(c['type'])} | {'oui' if c['obligatoire'] else 'non'} | {cellule(c['defaut'])} | {'PK' if c['cle'] else ''} |")
            index = [i for i in t["index"] if not i["nom"].startswith("sqlite_autoindex")]
            if index:
                l += ["", "Index : " + " ; ".join(f"`{i['nom']}` ({', '.join(i['colonnes'])}){' unique' if i['unique'] else ''}" for i in index)]
            l.append("")
    return "\n".join(l)


def main() -> None:
    if len(sys.argv) < 2:
        sys.exit(__doc__)
    tables = lire(sys.argv[1])
    md = markdown(tables)
    meta = json.dumps({"tables": tables}, ensure_ascii=False, indent=1) + "\n"
    if "--verifier" in sys.argv:
        perimes = [p.name for p, contenu in ((SORTIE_MD, md), (SORTIE_JSON, meta)) if not p.exists() or p.read_text(encoding="utf-8") != contenu]
        if perimes:
            sys.exit(f"dictionnaire périmé : {', '.join(perimes)} — régénérer avec dictionnaire-donnees.py")
        print("à jour.")
        return
    SORTIE_MD.parent.mkdir(parents=True, exist_ok=True)
    SORTIE_MD.write_text(md, encoding="utf-8")
    SORTIE_JSON.write_text(meta, encoding="utf-8")
    print(f"{SORTIE_MD.relative_to(DEPOT)} : {len(tables)} tables.")


if __name__ == "__main__":
    main()
