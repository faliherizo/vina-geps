#!/usr/bin/env node
/**
 * **La vidéo « Mise en route »** : tout ce qu'il faut configurer avant qu'une personne puisse se servir de
 * l'application, étape par étape (`frontend/src/aide/miseEnRoute.ts`), filmée avec le compte qui fait chaque étape.
 *
 *   node --env-file=.env scripts/tourner-mise-en-route.mjs --api http://localhost:8095 --organisme <id>
 *
 * L'organisme est celui de `scripts/creer-organisme-par-defaut.cjs` : ses identifiants sont lus dans
 * `scripts/.organisme-par-defaut.<nom>.local.json` (`--identifiants`, par défaut celui d'« Urgences-santé ») ; celui du
 * superadmin dans l'environnement (`SUPERADMIN_USERNAME`, `SUPERADMIN_PASSWORD`). Aucun n'est écrit ni affiché ici.
 *
 * Chaque étape : un carton dit son numéro, son titre et qui la fait ; puis l'écran est ouvert par le menu, parcouru au
 * rythme de la voix, et son formulaire rempli avec l'exemple de la fiche — **jamais enregistré**. Sortie, dans
 * `frontend/aide-sources/videos` (recopiée dans `public` par `introduire-aides.mjs`, et ici) : `mise-en-route.webm`,
 * `mise-en-route.fr.vtt` et `mise-en-route.chapitres.json` (début de chaque étape, en secondes) que le guide lit pour
 * ses boutons « Voir dans la vidéo ».
 */
import { execFileSync } from "node:child_process";
import { copyFileSync, mkdirSync, readdirSync, readFileSync, rmSync, writeFileSync } from "node:fs";
import { join } from "node:path";
import { dessinerCartons } from "./cartons-vitrine.mjs";
import { duree, narrer, vtt } from "./lib/narration.mjs";
import { RACINE, TAILLE, chargerModuleInterface, connecter, exiger, parLeMenu, parcourir } from "./lib/tournage.mjs";

const arg = (nom, defaut = "") => { const i = process.argv.indexOf(`--${nom}`); return i > 0 ? process.argv[i + 1] : defaut; };
const API = arg("api", "http://localhost:8095");
const ORGANISME = arg("organisme");
const IDENTIFIANTS = arg("identifiants", join(RACINE, "scripts", ".organisme-par-defaut.urgences-sante.local.json"));
const SOURCES = join(RACINE, "frontend", "aide-sources", "videos");
const PUBLIC = join(RACINE, "frontend", "public", "aide", "videos");
const TRAVAIL = join(process.env.TMPDIR ?? "/tmp", "vina-geps-mise-en-route");
const IMAGES_PAR_SECONDE = "25";
const MARGE = 0.6;
const ENCODAGE = ["-r", IMAGES_PAR_SECONDE, "-pix_fmt", "yuv420p", "-c:v", "libvpx-vp9", "-b:v", "0", "-crf", "38", "-row-mt", "1",
  "-deadline", "realtime", "-cpu-used", "8", "-c:a", "libopus", "-b:a", "48k", "-ar", "48000", "-ac", "1"];

/** Le compte de démonstration de chaque acteur, dans l'organisme par défaut. */
const COMPTE_DE = { administration: "admin", planification: "chef-ops", personne: "employe1" };

function corpsDeConnexion(acteur, comptes) {
  if (acteur === "exploitation") {
    return { username: process.env.SUPERADMIN_USERNAME ?? "superadmin", password: process.env.SUPERADMIN_PASSWORD ?? "" };
  }
  const c = comptes.find((x) => x.cle === COMPTE_DE[acteur]);
  if (!c) throw new Error(`compte « ${COMPTE_DE[acteur]} » absent de ${IDENTIFIANTS}`);
  return { username: c.username, password: c.password, tenantId: ORGANISME };
}

/** Un carton fixe, dit à voix haute : il ouvre la vidéo et chaque étape. */
function clipCarton(image, phrases, dossier, nom) {
  const narration = narrer([{ debut: 0, phrases }], dossier, nom);
  const sortie = join(dossier, `${nom}.webm`);
  execFileSync("ffmpeg", ["-y", "-loglevel", "error", "-loop", "1", "-framerate", IMAGES_PAR_SECONDE, "-i", image, "-i", narration.wav,
    "-map", "0:v:0", "-map", "1:a:0", "-t", (narration.duree + MARGE).toFixed(2), ...ENCODAGE, sortie]);
  return { fichier: sortie, cues: narration.cues };
}

/** L'écran de l'étape, ouvert par le menu avec le compte qui la fait, parcouru pendant que la voix dit les gestes. */
async function clipEcran(navigateur, etape, exemple, corps, dossier) {
  const narration = narrer([{ debut: 0, phrases: etape.gestes }], dossier, "gestes");
  const dossierVideo = join(dossier, "video");
  rmSync(dossierVideo, { recursive: true, force: true });
  const contexte = await navigateur.newContext({ viewport: TAILLE, locale: "fr-CA", timezoneId: "America/Toronto",
    recordVideo: { dir: dossierVideo, size: TAILLE }, baseURL: API });
  const depart = Date.now();
  await connecter(contexte, API, corps);
  const page = await contexte.newPage();
  const superadmin = etape.acteur === "exploitation";
  await page.goto(superadmin ? "/admin/superadmin" : "/admin/dashboard");
  await page.locator('nav.nav-groups[aria-busy="false"]').waitFor({ timeout: 30_000 }).catch(() => {});
  await page.waitForTimeout(800);
  const decalage = (Date.now() - depart) / 1000;
  if (!superadmin) await parLeMenu(page, etape.ecran);
  await page.locator("main h1").first().waitFor({ timeout: 15_000 }).catch(() => {});
  const ecoule = (Date.now() - depart) / 1000 - decalage;
  await parcourir(page, Math.max(4, narration.duree - ecoule + 0.8), exemple);
  await contexte.close();
  const brut = join(dossierVideo, readdirSync(dossierVideo).find((f) => f.endsWith(".webm")));
  const sortie = join(dossier, "ecran.webm");
  execFileSync("ffmpeg", ["-y", "-loglevel", "error", "-ss", decalage.toFixed(2), "-i", brut, "-i", narration.wav,
    "-map", "0:v:0", "-map", "1:a:0", "-t", (narration.duree + MARGE).toFixed(2), ...ENCODAGE, sortie]);
  return { fichier: sortie, cues: narration.cues };
}

/** Les clips bout à bout, chacun avec ses sous-titres décalés de ce qui le précède. */
function assembler(clips, sortie) {
  const liste = join(TRAVAIL, "liste.txt");
  writeFileSync(liste, clips.map((c) => `file '${c.fichier}'`).join("\n"));
  execFileSync("ffmpeg", ["-y", "-loglevel", "error", "-f", "concat", "-safe", "0", "-i", liste, "-c", "copy", sortie]);
  const cues = [];
  let t = 0;
  for (const c of clips) {
    cues.push(...c.cues.map((q) => ({ ...q, debut: q.debut + t, fin: q.fin + t })));
    c.debut = t;
    t += duree(c.fichier);
  }
  return cues;
}

async function principal() {
  if (!ORGANISME) throw new Error("--organisme : l'identifiant de l'organisme créé par creer-organisme-par-defaut.cjs");
  const { comptes } = JSON.parse(readFileSync(IDENTIFIANTS, "utf8"));
  const { MISE_EN_ROUTE, LIBELLE_ACTEUR } = chargerModuleInterface(join("aide", "miseEnRoute.ts"));
  const { FICHES } = chargerModuleInterface(join("aide", "fichesEcrans.ts"));
  rmSync(TRAVAIL, { recursive: true, force: true });
  mkdirSync(TRAVAIL, { recursive: true });
  const n = MISE_EN_ROUTE.length;
  await dessinerCartons([
    { nom: "ouverture", surtitre: "Guide · Premiers pas", titre: "Mise en route : configurer avant d'utiliser l'application",
      bas: `${n} étapes, chacune avec le compte qui la fait` },
    ...MISE_EN_ROUTE.map((e, i) => ({ nom: `etape-${i + 1}`, surtitre: "Mise en route", numero: `Étape ${i + 1} sur ${n}${e.facultative ? " · facultative" : ""}`,
      titre: e.titre, bas: `${LIBELLE_ACTEUR[e.acteur]} · écran « ${FICHES[e.ecran].titre} »` })),
  ], TRAVAIL);
  const clips = [clipCarton(join(TRAVAIL, "ouverture.png"), [
    "Mise en route.",
    "Avant qu'une personne puisse déclarer une absence, lire son horaire ou pointer, l'organisme doit être configuré.",
    `Voici les ${n} étapes, dans l'ordre : chacune prépare la suivante.`,
  ], TRAVAIL, "ouverture")];
  const { chromium } = exiger("playwright");
  const navigateur = await chromium.launch();
  const debuts = {};
  try {
    for (const [i, e] of MISE_EN_ROUTE.entries()) {
      const dossier = join(TRAVAIL, e.id);
      mkdirSync(dossier, { recursive: true });
      const carton = clipCarton(join(TRAVAIL, `etape-${i + 1}.png`),
        [`Étape ${i + 1}${e.facultative ? ", facultative" : ""} : ${e.titre}.`, `${LIBELLE_ACTEUR[e.acteur]}.`.replace(/[()]/g, "")], dossier, "carton");
      carton.etape = e.id;
      clips.push(carton, await clipEcran(navigateur, e, FICHES[e.ecran].exemple ?? [], corpsDeConnexion(e.acteur, comptes), dossier));
      console.log(`✓ ${i + 1}/${n} ${e.titre}`);
    }
  } finally { await navigateur.close(); }
  mkdirSync(SOURCES, { recursive: true });
  const video = join(SOURCES, "mise-en-route.webm");
  const cues = assembler(clips, video);
  for (const c of clips) if (c.etape) debuts[c.etape] = Math.round(c.debut * 10) / 10;
  writeFileSync(join(SOURCES, "mise-en-route.fr.vtt"), vtt(cues));
  writeFileSync(join(SOURCES, "mise-en-route.chapitres.json"), `${JSON.stringify(debuts, null, 2)}\n`);
  mkdirSync(PUBLIC, { recursive: true });
  for (const f of ["mise-en-route.webm", "mise-en-route.fr.vtt", "mise-en-route.chapitres.json"]) copyFileSync(join(SOURCES, f), join(PUBLIC, f));
  console.log(`\nMise en route : ${duree(video).toFixed(0)} s, ${n} étapes — ${video}`);
}

principal().catch((e) => { console.error(`❌ ${e.message.split("\n")[0]}`); process.exit(1); });
