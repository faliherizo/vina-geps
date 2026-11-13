#!/usr/bin/env python3
"""
Complète une base SQLite bâtie par les migrations du Node avec ce que le Java attend en plus
(`SchemaEssai`) : les tables que le Node crée paresseusement à leur premier usage, et les
colonnes qu'il ajoute par ALTER hors migration. Idempotent : ne touche pas à ce qui existe.

    python3 backend-java/scripts/completer-schema.py <base.db>

Le Java, en BASELINE Liquibase, ne rejoue pas les migrations : sur une base neuve, c'est ce
script qui la rend complète — voir backend-java/scripts/campagne-e2e.sh.
"""
import re
import sqlite3
import sys
from pathlib import Path

RACINE = Path(__file__).resolve().parents[1]
SCHEMA = RACINE / "src/test/java/ca/vina/nexu/SchemaEssai.java"


def colonnes(defs: str) -> list[str]:
    """Découpe le corps d'un CREATE TABLE en définitions, sans casser les parenthèses ni les chaînes."""
    out, niveau, cur, guillemet = [], 0, "", False
    for ch in defs:
        if ch == "'":
            guillemet = not guillemet
        if not guillemet:
            if ch == "(":
                niveau += 1
            elif ch == ")":
                niveau -= 1
            elif ch == "," and niveau == 0:
                out.append(cur.strip())
                cur = ""
                continue
        cur += ch
    if cur.strip():
        out.append(cur.strip())
    return out


def main(chemin: str) -> None:
    src = SCHEMA.read_text(encoding="utf-8")
    ddl = [d.replace('\\"', '"') for d in re.findall(r'"(CREATE (?:TABLE|INDEX|UNIQUE INDEX)[^"]*)"', src)]
    db = sqlite3.connect(chemin)
    tables = colonnes_ajoutees = index = 0
    for d in ddl:
        m = re.match(r"CREATE TABLE (?:IF NOT EXISTS )?(\w+)\s*\((.*)\)\s*$", d.strip(), re.S)
        if not m:
            db.execute(d.replace("CREATE INDEX", "CREATE INDEX IF NOT EXISTS").replace("CREATE UNIQUE INDEX", "CREATE UNIQUE INDEX IF NOT EXISTS")
                       .replace("IF NOT EXISTS IF NOT EXISTS", "IF NOT EXISTS"))
            index += 1
            continue
        table, corps = m.group(1), m.group(2)
        existantes = {r[1] for r in db.execute(f"PRAGMA table_info({table})")}
        if not existantes:
            db.execute(d)
            tables += 1
            continue
        for c in colonnes(corps):
            nom = re.split(r"[\s(]", c, 1)[0]
            if nom.upper() in ("PRIMARY", "UNIQUE", "FOREIGN", "CHECK", "CONSTRAINT") or nom in existantes:
                continue
            # une colonne ajoutée après coup ne peut être ni clé, ni unique, ni NOT NULL sans défaut
            defn = re.sub(r"\bPRIMARY KEY\b|\bUNIQUE\b|\bNOT NULL\b", "", c).strip()
            db.execute(f"ALTER TABLE {table} ADD COLUMN {defn}")
            colonnes_ajoutees += 1
            existantes.add(nom)
    db.commit()
    print(f"tables créées {tables}, colonnes ajoutées {colonnes_ajoutees}, index {index}")


if __name__ == "__main__":
    if len(sys.argv) != 2:
        sys.exit(__doc__)
    main(sys.argv[1])
