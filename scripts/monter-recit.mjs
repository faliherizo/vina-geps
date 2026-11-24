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
import { existsSync, mkdirSync, readFileSync, writeFileSync } from "node:fs";
import { createRequire } from "node:module";
import { dirname, join } from "node:path";
import { fileURLToPath } from "node:url";
import { cartonsDesChapitres, dessinerCartons } from "./cartons-vitrine.mjs";
import { HAUTEUR, LARGEUR, atelier, vtt } from "./lib/montage.mjs";

const RACINE = join(dirname(fileURLToPath(import.meta.url)), "..");
const JOURNAL = join(RACINE, "e2e", "test-results", "recit.jsonl");
const TRAVAIL = join(process.env.TMPDIR ?? "/tmp", "vina-geps-recit");
const VITRINE = join(RACINE, "frontend", "public", "vitrine");
// Sources des vidéos d'aide : `introduire-aides.mjs` y ajoute « Avant de commencer » et publie dans `public/aide/videos`.
const AIDES = join(RACINE, "frontend", "aide-sources", "videos");
/** La durée des scénarios d'une vidéo d'aide, carton non compris. */
const AIDE_MAX_S = 150;
/** Les chapitres qui parcourent tous les écrans : une vidéo d'aide ne les prend qu'à défaut de scénario. */
const CHAPITRES_DE_BALAYAGE = ["tests/e2e/urgences-sante-saisies.spec.ts", "tests/e2e/urgences-sante-exigences.spec.ts", "tests/e2e/fx-ao-exigences.spec.ts"];

const SEULEMENT_AIDES = process.argv.includes("--aides-seulement");
const { encoderCarton, concatener, passages, encoder: encode } = atelier(TRAVAIL);

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
  return passages(entrees.filter((e) => e.essai === fichier)).map((p) => ({
    chapitre: fichier, video: p.video, debut: p.debut, longueur: p.longueur, voix: p.voix, duree: p.duree, texte: p.texte,
    ecran: ecranMontre(p.video, p.t, p.naissance + p.fin * 1000, ecranDe(p.url)),
  }));
}

const passagesParChapitre = new Map(chapitres.map((c) => [c.fichier, passagesDe(c.fichier)]));
const muets = chapitres.filter((c) => passagesParChapitre.get(c.fichier).length === 0).map((c) => c.fichier);
if (muets.length) throw new Error(`chapitres sans récit — l'essai a-t-il été joué avec E2E_RECIT=1 ? ${muets.join(", ")}`);

// ---------------------------------------------------------------------------------------------------------------------
// Le film de la vitrine.

if (!SEULEMENT_AIDES) {
  const cartons = cartonsDesChapitres();
  await dessinerCartons(cartons, join(TRAVAIL, "cartons"), LARGEUR, HAUTEUR);
  const morceaux = [];
  chapitres.forEach((c, i) => {
    const annonce = `Chapitre ${i + 1} : ${c.titre}.`;
    morceaux.push(encoderCarton(join(TRAVAIL, "cartons", `${cartons[i].nom}.png`), annonce));
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
  const morceaux = [carton, ...choisis.map(encode)];
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
