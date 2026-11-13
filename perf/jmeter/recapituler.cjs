#!/usr/bin/env node
/**
 * **Le récapitulatif d'une campagne** : un tableau, une ligne par plan (famille ou scénario), à partir
 * des RESULTATS.md d'un dossier de rapports — pour coller dans le document de performance ou lire
 * d'un coup d'œil ce qu'une campagne de 35 familles a donné.
 *
 *   node perf/jmeter/recapituler.cjs perf/jmeter/rapports/local/<horodatage>
 */
const fs = require("fs");
const path = require("path");

function recapituler(dossier) {
  const lignes = [];
  for (const nom of fs.readdirSync(dossier).sort()) {
    const fichier = path.join(dossier, nom, "RESULTATS.md");
    if (!fs.existsSync(fichier)) continue;
    const md = fs.readFileSync(fichier, "utf8");
    const total = md.split("\n").find((l) => l.startsWith("| ") && /req\/s/.test(l));
    const verdict = (md.match(/## Verdict : (.*)/) ?? [])[1] ?? "?";
    const requetes = (md.match(/\| `[^`]+` \|/g) ?? []).length;
    const manques = md.split("\n").filter((l) => l.startsWith("| `") && l.includes("❌")).map((l) => l.match(/\| `([^`]+)`/)[1]);
    const cellules = total ? total.split("|").map((c) => c.trim()).filter(Boolean) : [];
    lignes.push({ nom, requetes, nombre: cellules[0] ?? "?", erreurs: cellules[1] ?? "?", debit: cellules[2] ?? "?", p95: cellules[4] ?? "?", p99: cellules[5] ?? "?", verdict, manques });
  }
  const md = ["| Plan | Endpoints | Requêtes | Erreurs | Débit | p95 | p99 | Verdict |", "|---|---:|---:|---:|---:|---:|---:|---|"];
  for (const l of lignes) md.push(`| ${l.nom} | ${l.requetes} | ${l.nombre} | ${l.erreurs} | ${l.debit} | ${l.p95} | ${l.p99} | ${l.verdict.startsWith("✅") ? "✅" : "❌ " + l.manques.map((m) => `\`${m}\``).join(", ")} |`);
  return md.join("\n");
}

if (require.main === module) {
  const dossier = process.argv[2];
  if (!dossier) { console.error("usage : recapituler.cjs <dossier de rapports>"); process.exit(2); }
  console.log(recapituler(dossier));
}
module.exports = { recapituler };
