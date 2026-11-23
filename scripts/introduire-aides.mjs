#!/usr/bin/env node
/**
 * **« Avant de commencer » en tête de chaque vidéo d'aide.**
 *
 *   node scripts/introduire-aides.mjs                       # toutes les fiches
 *   node scripts/introduire-aides.mjs --seulement mesabsences,monhoraire
 *
 * Une vidéo d'écran montre l'écran ; elle ne disait pas ce qui doit être en place pour qu'il serve. La personne qui
 * l'avait regardée ouvrait « Mes absences » et lisait « votre compte n'est relié à aucune ressource planifiable ».
 * Chaque vidéo publiée commence donc par un carton qui énumère, à voix haute, les préalables de la fiche
 * (`resumePrealable`), dans l'ordre, et renvoie à la vidéo « Mise en route » du guide qui les montre tous.
 *
 * Sources : `frontend/aide-sources/videos/<écran>.webm` et `.fr.vtt` (tournées par `tourner-aides.mjs` ou montées par
 * `monter-recit.mjs`, jamais modifiées ici). Sortie : `frontend/public/aide/videos/<écran>.webm`, ses sous-titres décalés
 * de la durée de l'introduction, et `introductions.json` — les préalables annoncés par vidéo, que l'essai
 * `aide-videos.test.tsx` compare aux fiches : un préalable modifié sans relancer ce script fait échouer l'essai.
 * La vidéo de mise en route (`tourner-mise-en-route.mjs`) est recopiée telle quelle.
 */
import { execFileSync } from "node:child_process";
import { copyFileSync, existsSync, mkdirSync, readFileSync, writeFileSync } from "node:fs";
import { join } from "node:path";
import { duree, horodatage, narrer, vtt } from "./lib/narration.mjs";
import { RACINE, TAILLE, chargerModuleInterface, exiger } from "./lib/tournage.mjs";

const SOURCES = join(RACINE, "frontend", "aide-sources", "videos");
const SORTIE = join(RACINE, "frontend", "public", "aide", "videos");
const TRAVAIL = join(process.env.TMPDIR ?? "/tmp", "vina-geps-introductions");
const IMAGES_PAR_SECONDE = "25";
const MARGE_FINALE = 0.6;
const MISE_EN_ROUTE = ["mise-en-route.webm", "mise-en-route.fr.vtt", "mise-en-route.chapitres.json"];
const arg = (nom) => { const i = process.argv.indexOf(`--${nom}`); return i > 0 ? process.argv[i + 1] : ""; };
const SEULEMENT = arg("seulement") ? new Set(arg("seulement").split(",")) : null;

const echapper = (t) => t.replace(/&/g, "&amp;").replace(/</g, "&lt;").replace(/>/g, "&gt;");
/** Ce que la voix lit : sans guillemets ni soulignés (« effectifs_absences » se dit « effectifs absences »). */
const pourLaVoix = (t) => t.replace(/[«»]/g, "").replace(/_/g, " ").replace(/\s+/g, " ").trim();

/** Ce que dit l'introduction : le titre, le nombre de préalables, chacun, puis le renvoi à la mise en route. */
function phrasesIntroduction(titre, prealables) {
  const n = prealables.length;
  return [
    `${titre}. Avant de commencer.`,
    n === 1 ? "Un préalable doit être en place :" : `${n} préalables doivent être en place, dans cet ordre :`,
    ...prealables.map((p) => `${p}.`),
    "Chacun est montré dans la vidéo Mise en route, en tête du guide.",
  ].map(pourLaVoix);
}

/**
 * Le carton : texte #ffffff sur #0f2a44 (14,6:1), liste #e6eef7 (13,1:1), renvoi #c9d8e8 (10,1:1) — la palette des
 * cartons de la vitrine (`cartons-vitrine.mjs`).
 */
function htmlCarton(titre, prealables) {
  const taille = prealables.length > 6 ? 24 : 28;
  return `<!doctype html><html lang="fr"><meta charset="utf-8"><style>
    html,body{margin:0;width:${TAILLE.width}px;height:${TAILLE.height}px;background:#0f2a44;color:#fff;font-family:"Helvetica Neue",Arial,sans-serif}
    main{box-sizing:border-box;height:100%;padding:48px 96px;display:flex;flex-direction:column;justify-content:center;gap:14px}
    .avis{font-size:24px;letter-spacing:.04em;color:#c9d8e8}
    h1{margin:0;font-size:44px;line-height:1.15;font-weight:700;border-left:8px solid #5fb3f0;padding-left:24px}
    ol{margin:8px 0 0;padding-left:40px;font-size:${taille}px;line-height:1.35;color:#e6eef7}
    .renvoi{font-size:22px;color:#c9d8e8}
    .marque{position:absolute;right:48px;bottom:28px;font-size:20px;font-weight:700;color:#c9d8e8}
  </style><main>
    <div class="avis">Avant de commencer</div>
    <h1>${echapper(titre)}</h1>
    <ol>${prealables.map((p) => `<li>${echapper(p)}</li>`).join("")}</ol>
    <div class="renvoi">Toutes ces étapes en vidéo : Guide › Premiers pas : la mise en route</div>
  </main><div class="marque">VINA GePS</div></html>`;
}

const secondes = (h) => { const [hh, mm, ss] = h.split(":"); return Number(hh) * 3600 + Number(mm) * 60 + Number(ss); };
/** Chaque cue `00:00:01.000 --> 00:00:02.500` décalée de `decalage` secondes. */
function decalerVtt(texte, decalage) {
  return texte.replace(/(\d{2}:\d{2}:\d{2}\.\d{3}) --> (\d{2}:\d{2}:\d{2}\.\d{3})/g,
    (_, a, b) => `${horodatage(secondes(a) + decalage)} --> ${horodatage(secondes(b) + decalage)}`);
}

function encoderIntroduction(image, narration, sortie) {
  execFileSync("ffmpeg", ["-y", "-loglevel", "error", "-loop", "1", "-framerate", IMAGES_PAR_SECONDE, "-i", image, "-i", narration.wav,
    "-map", "0:v:0", "-map", "1:a:0", "-t", (narration.duree + MARGE_FINALE).toFixed(2), "-r", IMAGES_PAR_SECONDE, "-pix_fmt", "yuv420p",
    "-c:v", "libvpx-vp9", "-b:v", "0", "-crf", "40", "-row-mt", "1", "-deadline", "realtime", "-cpu-used", "8",
    "-c:a", "libopus", "-b:a", "48k", "-ar", "48000", "-ac", "1", sortie]);
}

/** Introduction + source, normalisées (25 i/s, Opus mono 48 kHz) puis enchaînées en un seul fichier. */
function enchainer(intro, source, sortie) {
  execFileSync("ffmpeg", ["-y", "-loglevel", "error", "-i", intro, "-i", source, "-filter_complex",
    `[0:v]fps=${IMAGES_PAR_SECONDE},format=yuv420p[v0];[1:v]fps=${IMAGES_PAR_SECONDE},format=yuv420p[v1];`
    + "[0:a]aresample=48000,aformat=channel_layouts=mono[a0];[1:a]aresample=48000,aformat=channel_layouts=mono[a1];"
    + "[v0][a0][v1][a1]concat=n=2:v=1:a=1[v][a]",
    "-map", "[v]", "-map", "[a]", "-c:v", "libvpx-vp9", "-b:v", "0", "-crf", "36", "-row-mt", "1", "-deadline", "realtime",
    "-cpu-used", "8", "-c:a", "libopus", "-b:a", "48k", sortie]);
}

async function introduire(page, cle, fiche, resumePrealable) {
  const prealables = fiche.prealables.map(resumePrealable);
  const dossier = join(TRAVAIL, cle);
  mkdirSync(dossier, { recursive: true });
  await page.setContent(htmlCarton(fiche.titre, prealables));
  const image = join(dossier, "carton.png");
  await page.screenshot({ path: image });
  const narration = narrer([{ debut: 0, phrases: phrasesIntroduction(fiche.titre, prealables) }], dossier, "intro");
  const intro = join(dossier, "intro.webm");
  encoderIntroduction(image, narration, intro);
  const decalage = duree(intro);
  enchainer(intro, join(SOURCES, `${cle}.webm`), join(SORTIE, `${cle}.webm`));
  const sousTitres = join(SOURCES, `${cle}.fr.vtt`);
  const corps = existsSync(sousTitres) ? decalerVtt(readFileSync(sousTitres, "utf8"), decalage).replace(/^WEBVTT\s*/, "") : "";
  writeFileSync(join(SORTIE, `${cle}.fr.vtt`), `${vtt(narration.cues).trimEnd()}\n\n${corps}`);
  return { prealables, decalage };
}

async function principal() {
  const { FICHES, resumePrealable } = chargerModuleInterface(join("aide", "fichesEcrans.ts"));
  mkdirSync(SORTIE, { recursive: true });
  const registre = join(SORTIE, "introductions.json");
  const annonces = existsSync(registre) ? JSON.parse(readFileSync(registre, "utf8")) : {};
  const cles = Object.keys(FICHES).filter((c) => !SEULEMENT || SEULEMENT.has(c));
  const { chromium } = exiger("playwright");
  const navigateur = await chromium.launch();
  const page = await navigateur.newPage({ viewport: TAILLE });
  let echecs = 0;
  try {
    for (const cle of cles) {
      if (!existsSync(join(SOURCES, `${cle}.webm`))) { console.log(`✗ ${cle} — source absente`); echecs++; continue; }
      const { prealables, decalage } = await introduire(page, cle, FICHES[cle], resumePrealable);
      annonces[cle] = prealables;
      console.log(`✓ ${cle} — introduction ${decalage.toFixed(1)} s, ${prealables.length} préalable(s)`);
    }
  } finally { await navigateur.close(); }
  writeFileSync(registre, `${JSON.stringify(Object.fromEntries(Object.entries(annonces).sort()), null, 2)}\n`);
  for (const f of MISE_EN_ROUTE) if (existsSync(join(SOURCES, f))) copyFileSync(join(SOURCES, f), join(SORTIE, f));
  console.log(`\n${cles.length - echecs} vidéo(s) introduite(s), ${echecs} échec(s) — ${SORTIE}`);
  process.exit(echecs ? 1 : 0);
}

principal().catch((e) => { console.error(`❌ ${e.message}`); process.exit(1); });
