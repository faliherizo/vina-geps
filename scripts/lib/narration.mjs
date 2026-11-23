/**
 * **La narration d'une vidéo** : des phrases dites par une voix française du Québec (`say`, voix Amélie), mises bout à
 * bout avec un court silence, et les sous-titres WebVTT qui leur correspondent **au centième près** — chaque cue dure
 * exactement le temps de sa phrase. Les sous-titres ne sont donc pas une transcription approximative : ils sont l'audio
 * (WCAG 2.2, critère 1.2.2).
 *
 * Utilisée par le tournage des fiches d'aide (`scripts/tourner-aides.mjs`) et pour leurs horodatages par le montage raconté
 * (`scripts/monter-recit.mjs`).
 */
import { execFileSync } from "node:child_process";
import { mkdirSync, writeFileSync } from "node:fs";
import { join } from "node:path";

export const VOIX = process.env.NARRATION_VOIX ?? "Amélie";
export const DEBIT = process.env.NARRATION_DEBIT ?? "180";
const ECART = 0.45;
const ECHANTILLONNAGE = "48000";

export function duree(fichier) {
  return Number(execFileSync("ffprobe", ["-v", "error", "-show_entries", "format=duration", "-of", "csv=p=0", fichier], { encoding: "utf8" }).trim());
}

/** 00:01:02.345 */
export function horodatage(secondes) {
  const h = Math.floor(secondes / 3600);
  const m = Math.floor((secondes % 3600) / 60);
  const s = secondes - h * 3600 - m * 60;
  return `${String(h).padStart(2, "0")}:${String(m).padStart(2, "0")}:${s.toFixed(3).padStart(6, "0")}`;
}

function silence(fichier, secondes) {
  execFileSync("ffmpeg", ["-y", "-loglevel", "error", "-f", "lavfi", "-i", `anullsrc=r=${ECHANTILLONNAGE}:cl=mono`, "-t", secondes.toFixed(3),
    "-c:a", "pcm_s16le", fichier]);
}

/** Une phrase dite, en WAV mono 48 kHz ; rend sa durée. */
function dire(texte, fichier) {
  const aiff = `${fichier}.aiff`;
  execFileSync("say", ["-v", VOIX, "-r", DEBIT, "-o", aiff, texte]);
  execFileSync("ffmpeg", ["-y", "-loglevel", "error", "-i", aiff, "-ar", ECHANTILLONNAGE, "-ac", "1", "-c:a", "pcm_s16le", fichier]);
  return duree(fichier);
}

/**
 * Des blocs placés dans le temps : `[{ debut, phrases }]`, triés. Chaque bloc commence à `debut` (ou après le bloc
 * précédent s'il déborde). Rend le WAV complet, ses cues VTT et sa durée.
 */
export function narrer(blocs, dossier, nom) {
  mkdirSync(dossier, { recursive: true });
  const morceaux = [];
  const cues = [];
  let curseur = 0;
  let n = 0;
  for (const bloc of blocs) {
    if (bloc.debut > curseur) {
      const s = join(dossier, `${nom}-s${n++}.wav`);
      silence(s, bloc.debut - curseur);
      morceaux.push(s);
      curseur = bloc.debut;
    }
    for (const phrase of bloc.phrases) {
      const f = join(dossier, `${nom}-p${n++}.wav`);
      const d = dire(phrase, f);
      morceaux.push(f);
      cues.push({ debut: curseur, fin: curseur + d, texte: phrase });
      curseur += d;
      const s = join(dossier, `${nom}-s${n++}.wav`);
      silence(s, ECART);
      morceaux.push(s);
      curseur += ECART;
    }
  }
  const liste = join(dossier, `${nom}-liste.txt`);
  writeFileSync(liste, morceaux.map((m) => `file '${m}'`).join("\n"));
  const wav = join(dossier, `${nom}.wav`);
  execFileSync("ffmpeg", ["-y", "-loglevel", "error", "-f", "concat", "-safe", "0", "-i", liste, "-c:a", "pcm_s16le", wav]);
  return { wav, cues, duree: curseur };
}

export function vtt(cues, entete = []) {
  const lignes = ["WEBVTT", ""];
  for (const c of [...entete, ...cues].sort((a, b) => a.debut - b.debut)) {
    lignes.push(`${horodatage(c.debut)} --> ${horodatage(c.fin)}`, c.texte, "");
  }
  return lignes.join("\n");
}
