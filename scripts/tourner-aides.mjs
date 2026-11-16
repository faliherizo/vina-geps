#!/usr/bin/env node
/**
 * **Une vidéo de démonstration, avec sa voix, pour chaque fiche de l'aide** (`frontend/src/aide/fichesEcrans.ts`).
 *
 *   node scripts/tourner-aides.mjs --api http://localhost:8095 --us <organisme Urgences-santé> --fx <organisme FX-AO>
 *   node scripts/tourner-aides.mjs ... --seulement paiecycles,mesbulletins
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
import { mkdirSync, rmSync, writeFileSync, readdirSync } from "node:fs";
import { createRequire } from "node:module";
import { dirname, join } from "node:path";
import { fileURLToPath } from "node:url";
import { narrer, vtt, duree } from "./lib/narration.mjs";

const RACINE = join(dirname(fileURLToPath(import.meta.url)), "..");
const exiger = createRequire(join(RACINE, "e2e", "package.json"));
const { chromium } = exiger("playwright");
const esbuild = createRequire(join(RACINE, "frontend", "package.json"))("esbuild");

const arg = (nom, defaut = "") => { const i = process.argv.indexOf(`--${nom}`); return i > 0 ? process.argv[i + 1] : defaut; };
const API = arg("api", "http://localhost:8095");
const ORGANISMES = { us: arg("us"), fx: arg("fx") };
const MDP = process.env.DEMO_PASSWORD ?? "Passe!Demo2026";
const SEULEMENT = arg("seulement") ? new Set(arg("seulement").split(",")) : null;
const SORTIE = join(RACINE, "frontend", "public", "aide", "videos");
const TRAVAIL = join(process.env.TMPDIR ?? "/tmp", "vina-geps-aides");
const TAILLE = { width: 1280, height: 720 };

/** Qui ouvre l'écran, et dans quel organisme : le compte de démonstration qui a le droit et des données à montrer. */
const QUI = {
  monhoraire: ["us", "julie.roy"], mesdisponibilites: ["us", "julie.roy"], mesabsences: ["us", "julie.roy"], monpointage: ["us", "julie.roy"],
  matemps: ["us", "julie.roy"], mesechanges: ["us", "julie.roy"], mesbulletins: ["us", "julie.roy"], usmessages: ["us", "julie.roy"],
  usappels: ["us", "julie.roy"], usdiffusion: ["us", "julie.roy"], monagph: ["us", "denis.lavoie"], monequipe: ["us", "claire.nadeau"],
  paieregles: ["us", "sophie.marchand"], paiedossiers: ["us", "sophie.marchand"], paieparametres: ["us", "sophie.marchand"],
  paietemps: ["us", "luc.gagnon"], paiecycles: ["us", "luc.gagnon"], paiedeclarations: ["us", "luc.gagnon"],
  alnouvelle: ["fx", "coord.urgence"], alsuivi: ["fx", "coord.urgence"], mesalertes: ["fx", "inf.roy"], alparametres: ["fx", "admin"],
  superadmin: ["sa", process.env.SUPERADMIN_USERNAME ?? "superadmin"],
};
const compteDe = (cle) => QUI[cle] ?? ["us", "admin"];

function fiches() {
  const r = esbuild.buildSync({ entryPoints: [join(RACINE, "frontend", "src", "aide", "fichesEcrans.ts")], bundle: true, format: "cjs",
    platform: "node", write: false, logLevel: "silent" });
  const module = { exports: {} };
  new Function("module", "exports", "require", r.outputFiles[0].text)(module, module.exports, exiger);
  return module.exports.FICHES;
}

/** Ce que la voix dit : la fiche, dans l'ordre de la page d'aide. */
function phrases(f) {
  const p = [`${f.titre}.`, f.objet, `Qui s'en sert : ${f.qui}`, "Comment faire :", ...f.etapes, `Résultat : ${f.resultat}`];
  if (f.attention) p.push(`Attention : ${f.attention}`);
  return p.map((x) => x.replace(/[«»]/g, "").replace(/\s+/g, " ").trim()).filter(Boolean);
}

async function connecter(contexte, organisme, identifiant) {
  const corps = organisme === "sa"
    ? { username: identifiant, password: process.env.SUPERADMIN_PASSWORD ?? "" }
    : { username: identifiant, password: MDP, tenantId: ORGANISMES[organisme] };
  const r = await contexte.request.post(`${API}/api/auth/login`, { data: corps });
  if (!r.ok()) throw new Error(`connexion de ${identifiant} : ${r.status()}`);
  const { token, user } = await r.json();
  await contexte.addInitScript(([t, u, tid]) => {
    localStorage.setItem("token", t); localStorage.setItem("user", JSON.stringify(u));
    if (tid) localStorage.setItem("tenantId", tid);
  }, [token, user, corps.tenantId ?? ""]);
}

/** Surligne un élément le temps qu'on le voie : la vidéo ne montre pas le pointeur. */
async function surligner(page, locator, ms = 900) {
  await locator.evaluate((e, d) => {
    const avant = e.style.outline;
    e.style.outline = "4px solid #f5a623"; e.style.outlineOffset = "2px";
    setTimeout(() => { e.style.outline = avant; }, d);
  }, ms).catch(() => {});
  await page.waitForTimeout(ms);
}

/** Déplier la rubrique, puis la notion, jusqu'à l'entrée ; la surligner ; l'ouvrir. */
async function parLeMenu(page, cle) {
  const testId = `nav-${cle}`;
  await page.locator('nav.nav-groups[aria-busy="false"]').waitFor({ timeout: 30_000 });
  for (const niveau of [".nav-group", ".nav-concept"]) {
    const conteneur = page.locator(`${niveau}:has([data-testid="${testId}"])`).first();
    if ((await conteneur.count()) === 0) continue;
    const replie = await conteneur.evaluate((d) => !d.open).catch(() => false);
    if (!replie) continue;
    await surligner(page, conteneur.locator("> summary"), 700);
    await conteneur.locator("> summary").click({ timeout: 3000 }).catch(() => {});
  }
  const lien = page.getByTestId(testId).first();
  await lien.scrollIntoViewIfNeeded().catch(() => {});
  await surligner(page, lien, 1200);
  await lien.click({ timeout: 5000 }).catch(async () => page.goto(`/admin/${cle}`));
}

/**
 * L'exemple de la fiche, saisi dans le dialogue ouvert — sans jamais l'enregistrer : un champ par libellé, une liste par
 * le texte de son option. Un champ que l'écran ne porte pas est simplement passé.
 */
async function remplirExemple(page, exemple) {
  const dialogue = page.locator('[role="dialog"]:visible').last();
  if ((await dialogue.count()) === 0) return;
  for (const [champ, valeur] of exemple) {
    const cible = dialogue.getByLabel(champ, { exact: false }).first();
    if ((await cible.count()) === 0) continue;
    const nature = await cible.evaluate((e) => (e.tagName === "SELECT" ? "liste" : e.type)).catch(() => "");
    if (nature === "checkbox" || nature === "radio" || nature === "file") continue;
    await cible.scrollIntoViewIfNeeded().catch(() => {});
    if (nature === "liste") await cible.selectOption({ label: valeur }, { timeout: 1500 }).catch(() => {});
    else await cible.fill(valeur, { timeout: 1500 }).catch(() => {});
    await page.waitForTimeout(700);
  }
}

/** Parcourir l'écran au rythme de la voix ; ouvrir son formulaire principal vers le milieu, le refermer avant la fin. */
async function parcourir(page, secondes, exemple = []) {
  const fin = Date.now() + secondes * 1000;
  const ouvrir = page.locator('main [data-testid$="-ouvrir"]:visible').first();
  const aUnFormulaire = (await ouvrir.count()) > 0;
  const hauteur = await page.evaluate(() => document.documentElement.scrollHeight - innerHeight).catch(() => 0);
  const pas = 12;
  for (let i = 1; i <= pas && Date.now() < fin - secondes * 450; i++) {
    await page.evaluate((y) => scrollTo({ top: y, behavior: "smooth" }), (hauteur * i) / pas).catch(() => {});
    await page.waitForTimeout(Math.max(400, (secondes * 1000 * 0.4) / pas));
  }
  await page.evaluate(() => scrollTo({ top: 0, behavior: "smooth" })).catch(() => {});
  await page.waitForTimeout(1200);
  if (aUnFormulaire && Date.now() < fin - 6000) {
    await surligner(page, ouvrir, 1200);
    await ouvrir.click({ timeout: 3000 }).catch(() => {});
    await page.waitForTimeout(800);
    await remplirExemple(page, exemple);
    await page.waitForTimeout(Math.max(1500, fin - Date.now() - 2500));
    await page.keyboard.press("Escape").catch(() => {});
  }
  const reste = fin - Date.now();
  if (reste > 0) await page.waitForTimeout(reste);
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
