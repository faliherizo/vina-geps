#!/usr/bin/env node
/**
 * **Relève les essais qui nomment chaque exigence**, et les inscrit dans
 * `e2e/donnees/urgences-sante/exigences-fonctionnelles.json` sous `essaiProfond`.
 *
 * Pourquoi un outil plutôt qu'une saisie : une référence d'essai écrite à la main se périme sans
 * bruit — c'est exactement ce qui est arrivé à la matrice de couverture, deux fois. Ici la liste
 * est **relevée du dépôt** : un essai renommé disparaît de la liste à la régénération suivante, et
 * la porte de `construire-couverture.py` le dit.
 *
 * Convention : un essai « nomme » une exigence s'il contient son identifiant (`E-GN-1`…) dans son
 * fichier — en-tête de suite, titre d'essai (`@DisplayName`) ou commentaire. C'est la convention
 * déjà suivie par le dépôt ; ce script ne fait que la rendre exploitable.
 *
 * **Les preuves profondes sont celles du backend Java**, la cible (CLAUDE.md, « Quel backend ? »),
 * et des essais de bout en bout qui la servent (`backend-java/scripts/campagne-e2e.sh`). Les essais
 * du Node n'en sont plus : un comportement prouvé sur le Node seul n'est pas prouvé sur ce que le
 * client recevra.
 *
 * Usage : node docs/26-015-DO_AO/relever-essais.cjs [--verifier]
 *   --verifier : n'écrit rien, sort en échec si le fichier n'est pas à jour (porte de CI).
 */
const { execFileSync } = require("node:child_process");
const fs = require("node:fs");
const path = require("node:path");

const RACINE = path.resolve(__dirname, "..", "..");
const JEU = path.join(RACINE, "e2e/donnees/urgences-sante/exigences-fonctionnelles.json");
const SOURCES = ["backend-java/src/test", "e2e/tests"];
/** Les fichiers d'essai : Java pour le backend, TypeScript pour les essais de bout en bout. */
const INCLUS = ["--include=*.java", "--include=*.ts"];
/** Un identifiant d'exigence de l'avis : famille à deux lettres, numéro. */
const MOTIF = "[EP]-(GN|GH|CH|GR|GP|RF)-[0-9]+";

/** Les fichiers d'essai qui nomment chaque exigence, relevés en une passe. */
function relever() {
  const par = new Map();
  const ids = new Set(execFileSync("grep", ["-rhoE", ...INCLUS, MOTIF, ...SOURCES], { cwd: RACINE, encoding: "utf8" })
    .split("\n").filter(Boolean));
  for (const id of ids) {
    const fichiers = execFileSync("grep", ["-rlF", ...INCLUS, `${id}`, ...SOURCES], { cwd: RACINE, encoding: "utf8" })
      .split("\n").filter(Boolean)
      // `E-GN-1` est un préfixe de `E-GN-17` : écarter les correspondances partielles.
      .filter((f) => new RegExp(`${id}(?![0-9])`).test(fs.readFileSync(path.join(RACINE, f), "utf8")));
    par.set(id, fichiers.sort());
  }
  return par;
}

/**
 * **Les scénarios de comportement** nomment leurs exigences dans leur fichier de données, et non
 * dans le code de l'essai, qui n'est qu'un interprète. Sans cette lecture, les preuves jouées par
 * l'API contre le backend Java seraient invisibles à la matrice.
 */
const COMPORTEMENTS = "e2e/donnees/urgences-sante/comportements.json";

function releverComportements(par) {
  const { scenarios } = JSON.parse(fs.readFileSync(path.join(RACINE, COMPORTEMENTS), "utf8"));
  for (const id of new Set(scenarios.flatMap((s) => s.exigences))) {
    par.set(id, [...new Set([...(par.get(id) ?? []), COMPORTEMENTS])].sort());
  }
  return par;
}

const jeu = JSON.parse(fs.readFileSync(JEU, "utf8"));
const releve = releverComportements(relever());
const sans = [];
for (const e of jeu.exigences) {
  const essais = releve.get(e.id) ?? [];
  if (essais.length === 0) sans.push(e.id);
  e.essaiProfond = essais;
}

const rendu = `${JSON.stringify(jeu, null, 2)}\n`;
if (process.argv.includes("--verifier")) {
  if (fs.readFileSync(JEU, "utf8") !== rendu) {
    console.error("exigences-fonctionnelles.json n'est pas à jour : relancer sans --verifier.");
    process.exit(1);
  }
  console.log("à jour.");
} else {
  fs.writeFileSync(JEU, rendu);
  console.log(`${jeu.exigences.length} exigences, ${[...releve.values()].flat().length} références d'essai relevées.`);
}
if (sans.length > 0) console.log(`sans essai qui les nomme : ${sans.join(", ")}`);
