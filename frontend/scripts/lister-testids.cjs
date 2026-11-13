#!/usr/bin/env node
/**
 * Régénère `docs/refonte-visuelle/testids.txt`, la liste **gelée** des identifiants d'essai du
 * front (REFONTE-VISUELLE.md § F.3) : attributs `data-testid="…"` et propriétés `testId: "…"` /
 * `testId="…"` des composants communs. L'essai `tests/garde-fous-refonte.test.ts` refuse qu'un
 * identifiant gelé disparaisse ; on ne régénère qu'après un retrait voulu et documenté.
 */
const fs = require("fs");
const path = require("path");
const SRC = path.join(__dirname, "..", "src");
const SORTIE = path.join(__dirname, "..", "..", "docs", "refonte-visuelle", "testids.txt");
const { identifiantsDe } = require("./extraireTestids.cjs");
const ids = new Set();
(function parcourir(d) {
  for (const e of fs.readdirSync(d, { withFileTypes: true })) {
    const p = path.join(d, e.name);
    if (e.isDirectory()) parcourir(p);
    else if (/\.tsx?$/.test(e.name)) {
      for (const id of identifiantsDe(fs.readFileSync(p, "utf-8"))) ids.add(id);
    }
  }
})(SRC);
fs.writeFileSync(SORTIE, [...ids].sort().join("\n") + "\n");
console.log(`${ids.size} identifiants d'essai gelés → ${path.relative(process.cwd(), SORTIE)}`);
