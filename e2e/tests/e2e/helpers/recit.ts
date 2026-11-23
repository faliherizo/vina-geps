/**
 * **Le récit d'un essai** : ce que l'essai va montrer, dit à voix haute pendant le tournage du film de la vitrine et des
 * vidéos d'aide (`E2E_RECIT=1`, voir `scripts/monter-video-vitrine.sh`).
 *
 * Hors tournage, `raconter` ne fait rien : l'essai garde sa vitesse et son verdict. En tournage, la phrase est synthétisée
 * (voix française du Québec), l'instant, la vidéo de la page et l'écran sont notés dans `test-results/recit.jsonl`, puis
 * l'essai **attend la fin de la phrase** — les gestes restent à vitesse normale, c'est la parole qui laisse le temps de
 * voir. Le montage découpe ensuite les vidéos à ces instants et pose chaque phrase sur l'image dont elle parle.
 */
import { execFileSync } from "node:child_process";
import { createHash } from "node:crypto";
import { appendFileSync, existsSync, mkdirSync, statSync } from "node:fs";
import path from "node:path";
import { test, type Page } from "@playwright/test";

const EN_TOURNAGE = process.env.E2E_RECIT === "1";
const DOSSIER = path.join(__dirname, "..", "..", "..", "test-results");
const VOIX = path.join(DOSSIER, "recit-voix");
const JOURNAL = path.join(DOSSIER, "recit.jsonl");
/** Chaque navigation des pages racontées : le montage range un passage sous l'écran qu'il montre le plus longtemps. */
const NAVIGATIONS = path.join(DOSSIER, "recit-navigations.jsonl");
const suivies = new WeakSet<Page>();
const VOIX_NOM = "Amélie";
const DEBIT_MOTS_MINUTE = "180";
/** Le temps de regarder l'écran après la phrase, avant le geste suivant. */
const REGARD_MS = 600;
const ATTENTE_FICHIER_MS = 250;
const ATTENTE_FICHIER_ESSAIS = 60;

const MOIS = ["janvier", "février", "mars", "avril", "mai", "juin", "juillet", "août", "septembre", "octobre", "novembre", "décembre"];

/**
 * **Ce que la voix dit**, écrit pour l'oreille : une date ISO se dit « 12 mai 2031 », une heure « 18 h 30 », et un
 * identifiant technique entre accents graves n'est pas épelé. Le texte noté au journal est celui-ci.
 */
export function pourLOreille(texte: string): string {
  return texte
    .replace(/\b(\d{4})-(\d{2})-(\d{2})\b/g, (_, a, m, j) => `${Number(j) === 1 ? "1er" : Number(j)} ${MOIS[Number(m) - 1]} ${a}`)
    .replace(/\b(\d{1,2}):(\d{2})\b/g, (_, h, m) => (m === "00" ? `${Number(h)} h` : `${Number(h)} h ${m}`))
    .replace(/`/g, "");
}

/** Synthétise la phrase une fois (cache par empreinte) et rend son fichier et sa durée en secondes. */
function voix(texte: string): { fichier: string; duree: number } {
  mkdirSync(VOIX, { recursive: true });
  const fichier = path.join(VOIX, `${createHash("sha256").update(texte).digest("hex").slice(0, 16)}.aiff`);
  if (!existsSync(fichier)) execFileSync("say", ["-v", VOIX_NOM, "-r", DEBIT_MOTS_MINUTE, "-o", fichier, texte]);
  const sortie = execFileSync("ffprobe", ["-v", "error", "-show_entries", "format=duration", "-of", "csv=p=0", fichier], { encoding: "utf-8" });
  return { fichier, duree: Number(sortie.trim()) };
}

/** Note l'écran courant, puis chaque changement d'adresse de la page (navigations de l'application comprises). */
function suivreLesNavigations(page: Page, video: string): void {
  if (suivies.has(page)) return;
  suivies.add(page);
  const noter = (url: string) => appendFileSync(NAVIGATIONS, JSON.stringify({ t: Date.now(), video, url }) + "\n");
  noter(page.url());
  page.on("framenavigated", (cadre) => { if (cadre === page.mainFrame()) noter(cadre.url()); });
}

/**
 * Dit ce que l'essai va faire sur `page`, puis attend la fin de la phrase. La phrase nomme le cas éprouvé : qui agit, sur
 * quel écran, et ce qui doit se produire.
 */
export async function raconter(page: Page, texte: string): Promise<void> {
  if (!EN_TOURNAGE) return;
  const video = page.video();
  if (!video) return;
  const chemin = await video.path();
  // L'enregistreur crée le fichier un peu après la page : sur une machine chargée, la première phrase arrive avant lui.
  for (let essai = 0; !existsSync(chemin) && essai < ATTENTE_FICHIER_ESSAIS; essai++) await page.waitForTimeout(ATTENTE_FICHIER_MS);
  suivreLesNavigations(page, chemin);
  const dit = pourLOreille(texte);
  const { fichier, duree } = voix(dit);
  const attente = Math.round(duree * 1000) + REGARD_MS;
  test.info().setTimeout(test.info().timeout + attente + 1_000);
  appendFileSync(JOURNAL, JSON.stringify({
    t: Date.now(), video: chemin, naissance: statSync(chemin).birthtimeMs, voix: fichier, duree, texte: dit,
    url: page.url(), essai: path.relative(path.join(__dirname, "..", "..", ".."), test.info().file), titre: test.info().title,
  }) + "\n");
  await page.waitForTimeout(attente);
}
