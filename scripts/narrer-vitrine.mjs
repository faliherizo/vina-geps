#!/usr/bin/env node
/**
 * **La voix du film de la vitrine.** Appelé par `scripts/monter-video-vitrine.sh` une fois les chapitres filmés :
 *
 *   node scripts/narrer-vitrine.mjs <debuts.json> <sortie.wav> <sortie.vtt>
 *
 * `debuts.json` donne le début de chaque chapitre dans le film (secondes), dans l'ordre de `vitrine-chapitres.json`.
 * Chaque chapitre est annoncé (« Chapitre 3 : … »), puis sa narration est dite. Les sous-titres sont la voix, mot pour
 * mot. Une narration plus longue que son chapitre est refusée : elle déborderait sur le suivant, et le film dirait une
 * chose en en montrant une autre.
 */
import { readFileSync, writeFileSync } from "node:fs";
import { dirname, join } from "node:path";
import { fileURLToPath } from "node:url";
import { narrer, vtt } from "./lib/narration.mjs";

const RACINE = join(dirname(fileURLToPath(import.meta.url)), "..");
const [, , fichierDebuts, wavSortie, vttSortie] = process.argv;
const SEULEMENT_TRANSCRIRE = fichierDebuts === "--transcrire";
const { chapitres } = JSON.parse(readFileSync(join(RACINE, "scripts", "vitrine-chapitres.json"), "utf8"));
if (SEULEMENT_TRANSCRIRE) {
  transcrire(chapitres);
  console.log("✓ transcription de la vitrine régénérée");
  process.exit(0);
}
const { debuts, duree } = JSON.parse(readFileSync(fichierDebuts, "utf8"));
if (debuts.length !== chapitres.length) throw new Error(`${debuts.length} débuts pour ${chapitres.length} chapitres`);

const blocs = chapitres.map((c, i) => ({ debut: debuts[i] + 0.5, phrases: [`Chapitre ${i + 1} : ${c.titre}.`, ...c.narration] }));
const travail = join(process.env.TMPDIR ?? "/tmp", "vina-geps-film", "voix");
const r = narrer(blocs, travail, "film");

// Une narration qui déborde sur le chapitre suivant ferait dire une chose en en montrant une autre.
const debordes = r.cues.map((c, i) => ({ c, chapitre: blocs.findLastIndex((b) => b.debut <= c.debut + 0.001) }))
  .filter(({ c, chapitre }) => chapitre + 1 < debuts.length && c.fin > debuts[chapitre + 1]);
if (debordes.length) {
  throw new Error(`narration plus longue que son chapitre : ${[...new Set(debordes.map((d) => chapitres[d.chapitre].fichier))].join(", ")}`);
}
if (r.duree > duree) throw new Error(`la voix (${r.duree.toFixed(0)} s) dépasse le film (${duree.toFixed(0)} s)`);
writeFileSync(vttSortie, vtt(r.cues));
transcrire(chapitres);
console.log(`✓ voix : ${r.cues.length} phrases, ${r.duree.toFixed(0)} s → ${r.wav}`);
writeFileSync(wavSortie, readFileSync(r.wav));

/** La transcription de la page de la vitrine : la même voix, écrite (WCAG 1.2.1 et 1.2.3), régénérée avec le film. */
export function transcrire(liste) {
  const page = join(RACINE, "frontend", "public", "vitrine", "index.html");
  const echapper = (t) => t.replace(/&/g, "&amp;").replace(/</g, "&lt;").replace(/>/g, "&gt;");
  const items = liste.map((c) => `              <li><strong>${echapper(c.titre)}</strong> — ${echapper(c.narration.join(" "))}</li>`).join("\n");
  const html = readFileSync(page, "utf8").replace(/<!-- chapitres:debut -->[\s\S]*<!-- chapitres:fin -->/,
    `<!-- chapitres:debut -->\n            <ol>\n${items}\n            </ol>\n            <!-- chapitres:fin -->`);
  writeFileSync(page, html);
}
