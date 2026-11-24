/**
 * **L'atelier de montage** des vidéos racontées : couper un passage d'une vidéo d'essai et y poser sa phrase, dessiner
 * l'annonce d'un carton, mettre bout à bout sans réencoder, écrire les sous-titres. Partagé par `monter-recit.mjs` (film
 * de la vitrine, aide par écran) et `monter-parcours.mjs` (parcours en vidéo de l'aide).
 *
 * Tous les morceaux sortent dans le même format (VP9 + Opus, 1280 × 720, 25 i/s) : c'est ce qui permet de les
 * concaténer par copie. Chaque morceau est mis en cache sous l'empreinte de ce qui le produit.
 */
import { execFileSync } from "node:child_process";
import { createHash } from "node:crypto";
import { existsSync, mkdirSync, writeFileSync } from "node:fs";
import { join } from "node:path";
import { horodatage } from "./narration.mjs";

export const LARGEUR = 1280;
export const HAUTEUR = 720;
const IMAGES_PAR_SECONDE = 25;
const VOIX = "Amélie";
const DEBIT = "180";
/** Après la dernière phrase d'un essai, le temps de voir le résultat. */
export const QUEUE_S = 5;
/** Un passage sans phrase pendant plus longtemps est coupé : une attente n'a rien à montrer. */
export const PASSAGE_MAX_S = 30;
/** Le silence après l'annonce d'un carton. */
export const SOUFFLE_S = 1.2;

const FILTRE_IMAGE = `scale=${LARGEUR}:${HAUTEUR}:force_original_aspect_ratio=decrease,pad=${LARGEUR}:${HAUTEUR}:(ow-iw)/2:(oh-ih)/2:color=white,fps=${IMAGES_PAR_SECONDE},format=yuv420p,tpad=stop_mode=clone:stop_duration=${PASSAGE_MAX_S + 30}`;
const SORTIE = ["-c:v", "libvpx-vp9", "-crf", "34", "-b:v", "0", "-row-mt", "1", "-deadline", "good", "-cpu-used", "5",
  "-c:a", "libopus", "-b:a", "64k", "-ar", "48000", "-ac", "1"];

const executer = (cmd, args) => execFileSync(cmd, args, { stdio: ["ignore", "ignore", "inherit"] });
export const empreinte = (...parts) => createHash("sha256").update(parts.join("|")).digest("hex").slice(0, 20);

/** Un atelier dont les morceaux et les voix sont rangés dans `travail`. */
export function atelier(travail) {
  mkdirSync(travail, { recursive: true });
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
    const fichier = join(travail, `voix-${empreinte(texte)}.aiff`);
    if (!existsSync(fichier)) executer("say", ["-v", VOIX, "-r", DEBIT, "-o", fichier, texte]);
    return { fichier, duree: duree(fichier) };
  }

  /** Un passage : `longueur` secondes de `video` à partir de `debut`, sa phrase au début. */
  function encoderPassage({ video, debut, longueur, voix }) {
    const sortie = join(travail, `p-${empreinte(video, debut.toFixed(2), longueur.toFixed(2), voix)}.webm`);
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
    const sortie = join(travail, `c-${empreinte(image, texte)}.webm`);
    if (!existsSync(sortie)) {
      executer("ffmpeg", ["-y", "-loglevel", "error", "-loop", "1", "-framerate", String(IMAGES_PAR_SECONDE), "-i", image, "-i", voix.fichier,
        "-filter_complex", `[0:v]${FILTRE_IMAGE}[v];[1:a]aresample=48000,apad[a]`, "-map", "[v]", "-map", "[a]",
        "-t", longueur.toFixed(3), ...SORTIE, sortie]);
    }
    return { fichier: sortie, longueur, phrases: [{ decalage: 0, duree: voix.duree, texte }] };
  }

  /** Met bout à bout des morceaux encodés de la même façon, sans réencoder. */
  function concatener(morceaux, sortie) {
    const liste = join(travail, `liste-${empreinte(sortie)}.txt`);
    writeFileSync(liste, morceaux.map((m) => `file '${m}'`).join("\n") + "\n");
    executer("ffmpeg", ["-y", "-loglevel", "error", "-f", "concat", "-safe", "0", "-i", liste, "-c", "copy", sortie]);
  }

  /**
   * Les passages d'une suite d'entrées du journal du récit (`e2e/test-results/recit.jsonl`), triées dans le temps :
   * chacun va de sa phrase à la phrase suivante du même essai, borné à `PASSAGE_MAX_S`, et dure au moins sa phrase.
   */
  function passages(entrees) {
    return entrees.map((e, i) => {
      const suivante = entrees[i + 1];
      const debut = Math.max(0, (e.t - e.naissance) / 1000);
      const total = duree(e.video);
      let fin = suivante && suivante.titre === e.titre
        ? (suivante.t - e.naissance) / 1000 : debut + e.duree + QUEUE_S;
      fin = Math.min(fin, debut + Math.max(PASSAGE_MAX_S, e.duree + 2), total);
      fin = Math.max(fin, debut + e.duree + 0.3);
      return { ...e, debut, longueur: fin - debut, fin };
    });
  }

  /** Le morceau encodé d'un passage, avec sa phrase pour les sous-titres. */
  const encoder = (p) => ({ fichier: encoderPassage(p), longueur: p.longueur, phrases: [{ decalage: 0, duree: p.duree, texte: p.texte }] });

  return { duree, dire, encoderPassage, encoderCarton, concatener, passages, encoder };
}

/** Les cues WebVTT d'une suite de morceaux `{ longueur, phrases: [{ decalage, duree, texte }] }`. */
export function vtt(morceaux) {
  const cues = [];
  let t = 0;
  for (const m of morceaux) {
    for (const p of m.phrases) cues.push(`${horodatage(t + p.decalage)} --> ${horodatage(t + p.decalage + p.duree)}\n${p.texte}`);
    t += m.longueur;
  }
  return `WEBVTT\n\n${cues.join("\n\n")}\n`;
}
