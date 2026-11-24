#!/usr/bin/env node
/**
 * **Une vidéo de démonstration, avec sa voix, pour chaque fiche de l'aide** (`frontend/src/aide/fichesEcrans.ts`).
 *
 *   node scripts/tourner-aides.mjs --api http://localhost:8095 --us <organisme Urgences-santé> --fx <organisme FX-AO>
 *   node scripts/tourner-aides.mjs ... --seulement paiecycles,mesbulletins
 *   node scripts/tourner-aides.mjs ... --comptes scripts/.organisme-par-defaut.organisme-par-defaut.local.json
 *
 * Pour chaque écran : la fiche est **dite** (voix française du Québec), puis filmée sur un organisme de démonstration
 * semé, avec le compte qui a le droit de l'ouvrir. On part du tableau de bord, on déplie le menu et le sous-menu jusqu'à
 * l'entrée (surlignée), on parcourt l'écran, et l'on ouvre son formulaire principal s'il en a un. L'image dure ce que
 * dure la voix. Sortie : `frontend/public/aide/videos/<écran>.webm` et ses sous-titres `<écran>.fr.vtt`, exacts à la voix.
 *
 * Les mots de passe des comptes de démonstration sont ceux du semis (`DEMO_PASSWORD`) ; celui du superadmin est lu
 * dans l'environnement (`SUPERADMIN_USERNAME`, `SUPERADMIN_PASSWORD`, celles du `.env`), jamais écrit ici.
 */
import { execFileSync } from "node:child_process";
import { mkdirSync, rmSync, writeFileSync, readdirSync, readFileSync } from "node:fs";
import { join } from "node:path";
import { narrer, vtt, duree } from "./lib/narration.mjs";
import { RACINE, TAILLE, chargerModuleInterface, connecter as connecterApi, exiger, parLeMenu, parcourir } from "./lib/tournage.mjs";

const { chromium } = exiger("playwright");

const arg = (nom, defaut = "") => { const i = process.argv.indexOf(`--${nom}`); return i > 0 ? process.argv[i + 1] : defaut; };
const API = arg("api", "http://localhost:8095");
const ORGANISMES = { us: arg("us"), fx: arg("fx") };
const MDP = process.env.DEMO_PASSWORD ?? "Passe!Demo2026";
// L'organisme par défaut donne à chaque compte un mot de passe tiré au hasard, consigné dans son fichier local
// (`scripts/.organisme-par-defaut.*.local.json`, hors git) : `--comptes <fichier>` l'y lit pour les comptes « us ».
const COMPTES_US = arg("comptes") ? JSON.parse(readFileSync(arg("comptes"), "utf8")).comptes : [];
const motDePasseDe = (organisme, identifiant) =>
  (organisme === "us" && COMPTES_US.find((c) => c.username === identifiant)?.password) || MDP;
const SEULEMENT = arg("seulement") ? new Set(arg("seulement").split(",")) : null;
// Les vidéos tournées sont des **sources** : `introduire-aides.mjs` leur ajoute l'introduction « Avant de commencer »
// et publie le résultat dans `frontend/public/aide/videos`.
const SORTIE = join(RACINE, "frontend", "aide-sources", "videos");
const TRAVAIL = join(process.env.TMPDIR ?? "/tmp", "vina-geps-aides");

/** Qui ouvre l'écran, et dans quel organisme : le compte de démonstration qui a le droit et des données à montrer. */
const QUI = {
  monhoraire: ["us", "julie.roy"], mesdisponibilites: ["us", "julie.roy"], mesabsences: ["us", "julie.roy"], monpointage: ["us", "julie.roy"],
  matemps: ["us", "julie.roy"], mesechanges: ["us", "julie.roy"], mesbulletins: ["us", "julie.roy"], usmessages: ["us", "julie.roy"],
  usappels: ["us", "julie.roy"], usdiffusion: ["us", "julie.roy"], monagph: ["us", "denis.lavoie"], monequipe: ["us", "claire.nadeau"],
  paieregles: ["us", "sophie.marchand"], paiedossiers: ["us", "sophie.marchand"], paieparametres: ["us", "sophie.marchand"],
  paietemps: ["us", "luc.gagnon"], paiecycles: ["us", "luc.gagnon"], paiedeclarations: ["us", "luc.gagnon"],
  mesdemandes: ["us", "employe1"], mescongesannuels: ["us", "employe2"],
  alnouvelle: ["fx", "coord.urgence"], alsuivi: ["fx", "coord.urgence"], mesalertes: ["fx", "inf.roy"], alparametres: ["fx", "admin"],
  superadmin: ["sa", process.env.SUPERADMIN_USERNAME ?? "superadmin"],
};
const compteDe = (cle) => QUI[cle] ?? ["us", "admin"];

const fiches = () => chargerModuleInterface(join("aide", "fichesEcrans.ts")).FICHES;

/** Ce que la voix dit : la fiche, dans l'ordre de la page d'aide. */
function phrases(f) {
  const p = [`${f.titre}.`, f.objet, `Qui s'en sert : ${f.qui}`, "Comment faire :", ...f.etapes, `Résultat : ${f.resultat}`];
  if (f.attention) p.push(`Attention : ${f.attention}`);
  return p.map((x) => x.replace(/[«»]/g, "").replace(/\s+/g, " ").trim()).filter(Boolean);
}

async function connecter(contexte, organisme, identifiant) {
  const corps = organisme === "sa"
    ? { username: identifiant, password: process.env.SUPERADMIN_PASSWORD ?? "" }
    : { username: identifiant, password: motDePasseDe(organisme, identifiant), tenantId: ORGANISMES[organisme] };
  await connecterApi(contexte, API, corps);
}

async function tourner(navigateur, cle, fiche) {
  const narration = narrer([{ debut: 0, phrases: phrases(fiche) }], join(TRAVAIL, cle), cle);
  const [organisme, identifiant] = compteDe(cle);
  const dossierVideo = join(TRAVAIL, cle, "video");
  rmSync(dossierVideo, { recursive: true, force: true });
  const contexte = await navigateur.newContext({ viewport: TAILLE, locale: "fr-CA", timezoneId: "America/Toronto",
    recordVideo: { dir: dossierVideo, size: TAILLE }, baseURL: API });
  const depart = Date.now();
  await connecter(contexte, organisme, identifiant);
  const page = await contexte.newPage();
  await page.goto(organisme === "sa" ? "/admin/superadmin" : "/admin/dashboard");
  await page.locator('nav.nav-groups[aria-busy="false"]').waitFor({ timeout: 30_000 }).catch(() => {});
  await page.waitForTimeout(800);
  const decalage = (Date.now() - depart) / 1000;
  if (organisme !== "sa") await parLeMenu(page, cle);
  await page.locator("main h1").first().waitFor({ timeout: 15_000 }).catch(() => {});
  const ecoule = (Date.now() - depart) / 1000 - decalage;
  await parcourir(page, Math.max(4, narration.duree - ecoule + 0.8), fiche.exemple ?? []);
  await contexte.close();
  const brut = join(dossierVideo, readdirSync(dossierVideo).find((f) => f.endsWith(".webm")));
  const sortie = join(SORTIE, `${cle}.webm`);
  execFileSync("ffmpeg", ["-y", "-loglevel", "error", "-ss", decalage.toFixed(2), "-i", brut, "-i", narration.wav,
    "-map", "0:v:0", "-map", "1:a:0", "-t", (narration.duree + 0.6).toFixed(2), "-c:v", "libvpx-vp9", "-b:v", "0", "-crf", "40",
    "-row-mt", "1", "-deadline", "realtime", "-cpu-used", "8", "-c:a", "libopus", "-b:a", "48k", sortie]);
  writeFileSync(join(SORTIE, `${cle}.fr.vtt`), vtt(narration.cues));
  return { cle, duree: duree(sortie), compte: identifiant };
}

async function principal() {
  if (!ORGANISMES.us || !ORGANISMES.fx) throw new Error("--us et --fx : les organismes de démonstration (semer-urgences-sante, semer-fx-ao)");
  mkdirSync(SORTIE, { recursive: true });
  const toutes = fiches();
  const cles = Object.keys(toutes).filter((c) => !SEULEMENT || SEULEMENT.has(c));
  const navigateur = await chromium.launch();
  const bilan = [];
  for (const cle of cles) {
    try {
      const r = await tourner(navigateur, cle, toutes[cle]);
      bilan.push(r);
      console.log(`✓ ${cle} — ${r.duree.toFixed(0)} s (${r.compte})`);
    } catch (e) {
      console.log(`✗ ${cle} — ${e.message.split("\n")[0]}`);
      bilan.push({ cle, erreur: e.message });
    }
  }
  await navigateur.close();
  const echecs = bilan.filter((b) => b.erreur);
  console.log(`\n${bilan.length - echecs.length} vidéo(s) tournée(s), ${echecs.length} échec(s) — ${SORTIE}`);
  process.exit(echecs.length ? 1 : 0);
}

principal().catch((e) => { console.error(`❌ ${e.message}`); process.exit(1); });
