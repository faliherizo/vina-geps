#!/usr/bin/env node
/**
 * **Le montage raconté** : le film de la vitrine et les vidéos d'aide, tirés des essais e2e joués contre le serveur.
 *
 *   node scripts/monter-recit.mjs            # après `E2E_RECIT=1` (scripts/monter-video-vitrine.sh)
 *
 * Pendant le tournage, chaque essai a dit ce qu'il allait faire (`e2e/tests/e2e/helpers/recit.ts`) : la phrase, l'instant,
 * la vidéo de la page et l'écran sont dans `e2e/test-results/recit.jsonl`. Le montage coupe chaque vidéo d'une phrase à
 * la suivante et pose la phrase sur son passage : on voit toujours l'écran dont la voix parle, à vitesse normale, et les
 * temps d'installation (semis, connexions) disparaissent.
 *
 * - **Film de la vitrine** : pour chaque chapitre de `scripts/vitrine-chapitres.json`, un carton-titre annoncé à voix
 *   haute, puis ses passages dans l'ordre où ils ont été joués. Aucun sous-titre n'est incrusté ; la piste WebVTT reste
 *   offerte, éteinte par défaut, et la transcription est sur la page (WCAG 1.2.1, 1.2.2).
 * - **Vidéos d'aide** : pour chaque fiche de `frontend/src/aide/fichesEcrans.ts`, un carton annonçant l'écran, puis les
 *   passages joués sur cet écran — scénarios d'abord (ajout, modification, refus motivé, diffusion…), saisies et
 *   exigences ensuite. Un écran qu'aucun scénario ne joue garde sa vidéo précédente, et il est nommé au bilan.
 */
import { execFileSync } from "node:child_process";
import { createHash } from "node:crypto";
import { existsSync, mkdirSync, readFileSync, writeFileSync } from "node:fs";
import { createRequire } from "node:module";
import { dirname, join } from "node:path";
import { fileURLToPath } from "node:url";
import { cartonsDesChapitres, dessinerCartons } from "./cartons-vitrine.mjs";
import { horodatage } from "./lib/narration.mjs";

const RACINE = join(dirname(fileURLToPath(import.meta.url)), "..");
const JOURNAL = join(RACINE, "e2e", "test-results", "recit.jsonl");
const TRAVAIL = join(process.env.TMPDIR ?? "/tmp", "vina-geps-recit");
const VITRINE = join(RACINE, "frontend", "public", "vitrine");
const AIDES = join(RACINE, "frontend", "public", "aide", "videos");
const LARGEUR = 1280;
const HAUTEUR = 720;
const IMAGES_PAR_SECONDE = 25;
const VOIX = "Amélie";
const DEBIT = "180";
/** Après la dernière phrase d'un essai, le temps de voir le résultat. */
const QUEUE_S = 5;
/** Un passage sans phrase pendant plus longtemps est coupé : une attente n'a rien à montrer. */
const PASSAGE_MAX_S = 30;
/** La durée des scénarios d'une vidéo d'aide, carton non compris. */
const AIDE_MAX_S = 150;
/** Le silence après l'annonce d'un carton. */
const SOUFFLE_S = 1.2;
/** Les chapitres qui parcourent tous les écrans : une vidéo d'aide ne les prend qu'à défaut de scénario. */
const CHAPITRES_DE_BALAYAGE = ["tests/e2e/urgences-sante-saisies.spec.ts", "tests/e2e/urgences-sante-exigences.spec.ts", "tests/e2e/fx-ao-exigences.spec.ts"];

const SEULEMENT_AIDES = process.argv.includes("--aides-seulement");
mkdirSync(TRAVAIL, { recursive: true });

const executer = (cmd, args) => execFileSync(cmd, args, { stdio: ["ignore", "ignore", "inherit"] });
const empreinte = (...parts) => createHash("sha256").update(parts.join("|")).digest("hex").slice(0, 20);
const durees = new Map();
function duree(fichier) {
  if (!durees.has(fichier)) {
    const s = execFileSync("ffprobe", ["-v", "error", "-show_entries", "format=duration", "-of", "csv=p=0", fichier], { encoding: "utf8" });
    durees.set(fichier, Number(s.trim()));
  }
  return durees.get(fichier);
}

/** La phrase d'un carton, dite par la même voix que le récit ; rend le fichier et sa durée. */
function dire(texte) {
  const fichier = join(TRAVAIL, `voix-${empreinte(texte)}.aiff`);
  if (!existsSync(fichier)) executer("say", ["-v", VOIX, "-r", DEBIT, "-o", fichier, texte]);
  return { fichier, duree: duree(fichier) };
}

const FILTRE_IMAGE = `scale=${LARGEUR}:${HAUTEUR}:force_original_aspect_ratio=decrease,pad=${LARGEUR}:${HAUTEUR}:(ow-iw)/2:(oh-ih)/2:color=white,fps=${IMAGES_PAR_SECONDE},format=yuv420p,tpad=stop_mode=clone:stop_duration=${PASSAGE_MAX_S + 30}`;
const SORTIE = ["-c:v", "libvpx-vp9", "-crf", "34", "-b:v", "0", "-row-mt", "1", "-deadline", "good", "-cpu-used", "5",
  "-c:a", "libopus", "-b:a", "64k", "-ar", "48000", "-ac", "1"];

/** Un passage : `longueur` secondes de `video` à partir de `debut`, sa phrase au début, en VP9 + Opus uniformes. */
function encoderPassage({ video, debut, longueur, voix }) {
  const sortie = join(TRAVAIL, `p-${empreinte(video, debut.toFixed(2), longueur.toFixed(2), voix)}.webm`);
  if (existsSync(sortie)) return sortie;
  executer("ffmpeg", ["-y", "-loglevel", "error", "-ss", debut.toFixed(3), "-i", video, "-i", voix,
    "-filter_complex", `[0:v]${FILTRE_IMAGE}[v];[1:a]aresample=48000,apad[a]`, "-map", "[v]", "-map", "[a]",
    "-t", longueur.toFixed(3), ...SORTIE, sortie]);
  return sortie;
}

/** Un carton : l'image fixe, et son annonce. */
function encoderCarton(image, texte) {
  const voix = dire(texte);
  const longueur = voix.duree + SOUFFLE_S;
  const sortie = join(TRAVAIL, `c-${empreinte(image, texte)}.webm`);
  if (!existsSync(sortie)) {
    executer("ffmpeg", ["-y", "-loglevel", "error", "-loop", "1", "-framerate", String(IMAGES_PAR_SECONDE), "-i", image, "-i", voix.fichier,
      "-filter_complex", `[0:v]${FILTRE_IMAGE}[v];[1:a]aresample=48000,apad[a]`, "-map", "[v]", "-map", "[a]",
      "-t", longueur.toFixed(3), ...SORTIE, sortie]);
  }
  return { fichier: sortie, longueur, texte };
}

/** Met bout à bout des morceaux encodés de la même façon, sans réencoder. */
function concatener(morceaux, sortie) {
  const liste = join(TRAVAIL, `liste-${empreinte(sortie)}.txt`);
  writeFileSync(liste, morceaux.map((m) => `file '${m}'`).join("\n") + "\n");
  executer("ffmpeg", ["-y", "-loglevel", "error", "-f", "concat", "-safe", "0", "-i", liste, "-c", "copy", sortie]);
}

/** Les cues WebVTT d'une suite de morceaux `{ longueur, phrases: [{ decalage, duree, texte }] }`. */
function vtt(morceaux) {
  const cues = [];
  let t = 0;
  for (const m of morceaux) {
    for (const p of m.phrases) cues.push(`${horodatage(t + p.decalage)} --> ${horodatage(t + p.decalage + p.duree)}\n${p.texte}`);
    t += m.longueur;
  }
  return `WEBVTT\n\n${cues.join("\n\n")}\n`;
}

/** L'écran d'une adresse : `/admin/paieregles?x` → `paieregles`. */
function ecranDe(url) {
  try {
    const parties = new URL(url).pathname.split("/").filter(Boolean);
    return (parties[0] === "admin" ? parties[1] : parties[0]) ?? "";
  } catch { return ""; }
}

// ---------------------------------------------------------------------------------------------------------------------
// Les passages, d'une phrase à la suivante.

if (!existsSync(JOURNAL)) throw new Error(`aucun récit : ${JOURNAL} — lancer le tournage avec E2E_RECIT=1`);
const lignes = (f) => (existsSync(f) ? readFileSync(f, "utf8").split("\n").filter(Boolean).map((l) => JSON.parse(l)) : []);
/** Les adresses successives de chaque vidéo, triées dans le temps. */
const navigations = new Map();
for (const n of lignes(join(RACINE, "e2e", "test-results", "recit-navigations.jsonl")).sort((a, b) => a.t - b.t)) {
  if (!navigations.has(n.video)) navigations.set(n.video, []);
  navigations.get(n.video).push(n);
}

/**
 * **L'écran qu'un passage montre** : celui qui reste affiché le plus longtemps entre `de` et `a` (instants absolus). Une
 * phrase précède souvent le geste qui change d'écran, et une boucle navigue juste avant la phrase suivante : ni l'écran
 * du début ni celui de la fin ne suffisent.
 */
function ecranMontre(video, de, a, repli) {
  const suite = navigations.get(video) ?? [];
  const temps = new Map();
  let courant = repli;
  for (const n of suite) if (n.t <= de) courant = ecranDe(n.url);
  let depuis = de;
  for (const n of suite.filter((x) => x.t > de && x.t < a)) {
    temps.set(courant, (temps.get(courant) ?? 0) + (n.t - depuis));
    courant = ecranDe(n.url);
    depuis = n.t;
  }
  temps.set(courant, (temps.get(courant) ?? 0) + (a - depuis));
  return [...temps.entries()].filter(([e]) => e).sort((x, y) => y[1] - x[1])[0]?.[0] ?? repli;
}

const entrees = readFileSync(JOURNAL, "utf8").split("\n").filter(Boolean).map((l) => JSON.parse(l)).sort((a, b) => a.t - b.t);
const { chapitres } = JSON.parse(readFileSync(join(RACINE, "scripts", "vitrine-chapitres.json"), "utf8"));

function passagesDe(fichier) {
  const es = entrees.filter((e) => e.essai === fichier);
  return es.map((e, i) => {
    const suivante = es[i + 1];
    const debut = Math.max(0, (e.t - e.naissance) / 1000);
    const total = duree(e.video);
    let fin = suivante && suivante.titre === e.titre ? (suivante.t - e.naissance) / 1000 : debut + e.duree + QUEUE_S;
    fin = Math.min(fin, debut + Math.max(PASSAGE_MAX_S, e.duree + 2), total);
    fin = Math.max(fin, debut + e.duree + 0.3);
    return {
      chapitre: fichier, video: e.video, debut, longueur: fin - debut, voix: e.voix, duree: e.duree, texte: e.texte,
      ecran: ecranMontre(e.video, e.t, e.naissance + fin * 1000, ecranDe(e.url)),
    };
  });
}

const passagesParChapitre = new Map(chapitres.map((c) => [c.fichier, passagesDe(c.fichier)]));
const muets = chapitres.filter((c) => passagesParChapitre.get(c.fichier).length === 0).map((c) => c.fichier);
if (muets.length) throw new Error(`chapitres sans récit — l'essai a-t-il été joué avec E2E_RECIT=1 ? ${muets.join(", ")}`);

const encode = (p) => ({ fichier: encoderPassage(p), longueur: p.longueur, phrases: [{ decalage: 0, duree: p.duree, texte: p.texte }] });

// ---------------------------------------------------------------------------------------------------------------------
// Le film de la vitrine.

if (!SEULEMENT_AIDES) {
  const cartons = cartonsDesChapitres();
  await dessinerCartons(cartons, join(TRAVAIL, "cartons"), LARGEUR, HAUTEUR);
  const morceaux = [];
  chapitres.forEach((c, i) => {
    const annonce = `Chapitre ${i + 1} : ${c.titre}.`;
    const carton = encoderCarton(join(TRAVAIL, "cartons", `${cartons[i].nom}.png`), annonce);
    morceaux.push({ ...carton, phrases: [{ decalage: 0, duree: carton.longueur - SOUFFLE_S, texte: annonce }] });
    for (const p of passagesParChapitre.get(c.fichier)) morceaux.push(encode(p));
    process.stdout.write(`  chapitre ${i + 1}/${chapitres.length} monté\n`);
  });
  const film = join(VITRINE, "demo-vina-geps.webm");
  concatener(morceaux.map((m) => m.fichier), film);
  writeFileSync(join(VITRINE, "demo-vina-geps.fr.vtt"), vtt(morceaux));
  transcrire(chapitres.map((c) => ({ titre: c.titre, phrases: passagesParChapitre.get(c.fichier).map((p) => p.texte) })));
  const total = morceaux.reduce((s, m) => s + m.longueur, 0);
  console.log(`✓ film : ${chapitres.length} chapitres, ${morceaux.length} morceaux, ${Math.round(total / 60)} min → ${film}`);
}

// ---------------------------------------------------------------------------------------------------------------------
// Les vidéos d'aide : les passages joués sur chaque écran.

const esbuild = createRequire(join(RACINE, "frontend", "package.json"))("esbuild");
const bundle = esbuild.buildSync({ entryPoints: [join(RACINE, "frontend", "src", "aide", "fichesEcrans.ts")], bundle: true, format: "cjs",
  platform: "node", write: false, logLevel: "silent" });
const module = { exports: {} };
new Function("module", "exports", "require", bundle.outputFiles[0].text)(module, module.exports, createRequire(import.meta.url));
const FICHES = module.exports.FICHES;

const tous = chapitres.flatMap((c) => passagesParChapitre.get(c.fichier));
const rang = (p) => (CHAPITRES_DE_BALAYAGE.includes(p.chapitre) ? 1 : 0);
const cartonsAide = Object.entries(FICHES).map(([ecran, f]) => ({
  nom: `aide-${ecran}`, surtitre: "VINA GePS · Aide", titre: f.titre.replace(/[«»]/g, "").replace(/\s+/g, " ").trim(),
  bas: "Démonstration commentée, jouée contre le serveur",
}));
await dessinerCartons(cartonsAide, join(TRAVAIL, "cartons"), LARGEUR, HAUTEUR);
mkdirSync(AIDES, { recursive: true });

const sansScenario = [];
for (const c of cartonsAide) {
  const ecran = c.nom.slice("aide-".length);
  const candidats = tous.filter((p) => p.ecran === ecran).sort((a, b) => rang(a) - rang(b));
  if (!candidats.length) { sansScenario.push(ecran); continue; }
  const choisis = [];
  let cumul = 0;
  for (const p of candidats) {
    if (choisis.some((x) => x.texte === p.texte)) continue;
    if (choisis.length && cumul + p.longueur > AIDE_MAX_S) continue;
    choisis.push(p);
    cumul += p.longueur;
  }
  const ordre = new Map(tous.map((p, i) => [p, i]));
  choisis.sort((a, b) => ordre.get(a) - ordre.get(b));
  const annonce = `${c.titre}. Voici comment s'en servir, sur des cas réels joués dans l'application.`;
  const carton = encoderCarton(join(TRAVAIL, "cartons", `${c.nom}.png`), annonce);
  const morceaux = [{ ...carton, phrases: [{ decalage: 0, duree: carton.longueur - SOUFFLE_S, texte: annonce }] }, ...choisis.map(encode)];
  concatener(morceaux.map((m) => m.fichier), join(AIDES, `${ecran}.webm`));
  writeFileSync(join(AIDES, `${ecran}.fr.vtt`), vtt(morceaux));
  console.log(`  ✓ aide ${ecran} : ${choisis.length} passage(s), ${Math.round(cumul + carton.longueur)} s`);
}
console.log(`✓ aides : ${cartonsAide.length - sansScenario.length} écrans montés depuis les scénarios`
  + (sansScenario.length ? ` ; sans scénario (vidéo précédente gardée) : ${sansScenario.join(", ")}` : ""));

/** La transcription de la page de la vitrine : la même voix, écrite (WCAG 1.2.1 et 1.2.3). */
function transcrire(liste) {
  const page = join(VITRINE, "index.html");
  const echapper = (t) => t.replace(/&/g, "&amp;").replace(/</g, "&lt;").replace(/>/g, "&gt;");
  const items = liste.map((c) => `              <li><strong>${echapper(c.titre)}</strong> — ${echapper(c.phrases.join(" "))}</li>`).join("\n");
  const html = readFileSync(page, "utf8").replace(/<!-- chapitres:debut -->[\s\S]*<!-- chapitres:fin -->/,
    `<!-- chapitres:debut -->\n            <ol>\n${items}\n            </ol>\n            <!-- chapitres:fin -->`);
  writeFileSync(page, html);
}
