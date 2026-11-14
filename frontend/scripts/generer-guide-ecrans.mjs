#!/usr/bin/env node
/**
 * Génère `docs/urgences-sante/GUIDE-ECRANS.md` à partir de `src/aide/fichesEcrans.ts` — la même
 * source que le guide de l'application. Le document ne s'écrit pas à la main : il divergerait du
 * guide au premier écran modifié.
 *
 *   node scripts/generer-guide-ecrans.mjs            # écrit le document
 *   node scripts/generer-guide-ecrans.mjs --verifier # échoue si le document n'est pas à jour
 */
import { build } from "esbuild";
import { readFileSync, writeFileSync } from "node:fs";
import { dirname, join } from "node:path";
import { fileURLToPath } from "node:url";

const ici = dirname(fileURLToPath(import.meta.url));
const SORTIE = join(ici, "..", "..", "docs", "urgences-sante", "GUIDE-ECRANS.md");

async function charger(fichier) {
  const r = await build({ entryPoints: [join(ici, "..", "src", fichier)], bundle: true, format: "esm", write: false, platform: "node" });
  return import(`data:text/javascript;base64,${Buffer.from(r.outputFiles[0].text).toString("base64")}`);
}

/** Les rubriques du menu, dans l'ordre de la colonne (ADR 0073). */
const RUBRIQUES = [
  ["Menu principal", ["dashboard", "monhoraire", "mesdisponibilites", "usdisponibilites", "mesabsences", "usabsences", "monagph", "usagph",
    "uscomblement", "usequipages", "uspostes", "usperiodes", "usressources", "usquotas", "monpointage", "uspresence", "matemps", "ustemps",
    "mesechanges", "usechanges", "usmessages", "usappels", "usdiffusion", "rapports", "approvals"]],
  ["Administration", ["usreferentiels", "usregles", "usmilieux", "usperimetres", "usintegrations", "usretention", "usjournal",
    "admin", "access", "notifications", "integrations", "apidocs", "superadmin"]],
  ["Aide et compte", ["account", "help", "soutien"]],
];

const cellule = (t) => String(t).replace(/\|/g, "\\|");

function fiche(page, f) {
  const l = [`### ${f.titre}`, "", `*Adresse : \`/admin/${page}\` — ${f.qui}*`, "", f.objet, "", "**Comment faire**", ""];
  f.etapes.forEach((e, i) => l.push(`${i + 1}. ${e}`));
  l.push("", "**Exemple de remplissage**", "", "| Champ | Valeur saisie |", "|---|---|");
  for (const [champ, valeur] of f.exemple) l.push(`| ${cellule(champ)} | ${cellule(valeur)} |`);
  l.push("", `**Résultat :** ${f.resultat}`);
  if (f.attention) l.push("", `> **Attention :** ${f.attention}`);
  l.push("");
  return l;
}

const { FICHES } = await charger("aide/fichesEcrans.ts");
const absentes = Object.keys(FICHES).filter((p) => !RUBRIQUES.some(([, pages]) => pages.includes(p)));
if (absentes.length) { console.error(`✗ écrans sans rubrique dans le générateur : ${absentes.join(", ")}`); process.exit(1); }

const lignes = [
  "# VINA GEPS : guide écran par écran",
  "",
  "> Écrit pour les utilisateurs et les formateurs d'Urgences-santé. Pour chaque écran : à quoi il",
  "> sert, qui l'ouvre, comment s'y prendre et un exemple de remplissage avec les libellés réels.",
  "",
  "<!-- Généré par frontend/scripts/generer-guide-ecrans.mjs depuis frontend/src/aide/fichesEcrans.ts : ne pas modifier à la main. -->",
  "",
  "Dans l'application, le bouton **« Aide sur cet écran »** ouvre directement la fiche de l'écran",
  "courant (`/admin/help?section=fiche-<écran>`). Les exemples reprennent l'organisme de",
  "démonstration ([COMPTES-DEMONSTRATION.md](COMPTES-DEMONSTRATION.md)).",
  "",
];
for (const [rubrique, pages] of RUBRIQUES) {
  lignes.push(`## ${rubrique}`, "");
  for (const p of pages) lignes.push(...fiche(p, FICHES[p]));
}
const texte = lignes.join("\n");

if (process.argv.includes("--verifier")) {
  let actuel = "";
  try { actuel = readFileSync(SORTIE, "utf-8"); } catch { /* absent : pas à jour */ }
  if (actuel !== texte) { console.error("✗ GUIDE-ECRANS.md n'est pas à jour : node frontend/scripts/generer-guide-ecrans.mjs"); process.exit(1); }
  console.log("✓ GUIDE-ECRANS.md à jour");
} else {
  writeFileSync(SORTIE, texte);
  console.log(`✓ ${SORTIE} (${Object.keys(FICHES).length} écrans)`);
}
