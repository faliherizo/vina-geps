/**
 * **Les gestes du tournage des vidéos d'aide** : se connecter, suivre le menu jusqu'à l'écran, surligner ce qu'on
 * montre (la vidéo n'a pas de pointeur), parcourir l'écran au rythme de la voix, et saisir l'exemple dans un
 * formulaire sans jamais l'enregistrer. Partagés par `tourner-aides.mjs` (une vidéo par fiche) et
 * `tourner-mise-en-route.mjs` (la configuration, étape par étape).
 */
import { createRequire } from "node:module";
import { dirname, join } from "node:path";
import { fileURLToPath } from "node:url";

export const RACINE = join(dirname(fileURLToPath(import.meta.url)), "..", "..");
export const exiger = createRequire(join(RACINE, "e2e", "package.json"));
const esbuild = createRequire(join(RACINE, "frontend", "package.json"))("esbuild");
export const TAILLE = { width: 1280, height: 720 };

/** Un module TypeScript de l'interface (les fiches, la mise en route), lu tel que l'interface le compile. */
export function chargerModuleInterface(cheminRelatif) {
  const r = esbuild.buildSync({ entryPoints: [join(RACINE, "frontend", "src", cheminRelatif)], bundle: true, format: "cjs",
    platform: "node", write: false, logLevel: "silent" });
  const module = { exports: {} };
  new Function("module", "exports", "require", r.outputFiles[0].text)(module, module.exports, exiger);
  return module.exports;
}

/** Ouvre la session dans le contexte du navigateur : `corps` est celui de `POST /api/auth/login`. */
export async function connecter(contexte, api, corps) {
  const r = await contexte.request.post(`${api}/api/auth/login`, { data: corps });
  if (!r.ok()) throw new Error(`connexion de ${corps.username} : ${r.status()}`);
  const { token, user } = await r.json();
  await contexte.addInitScript(([t, u, tid]) => {
    localStorage.setItem("token", t); localStorage.setItem("user", JSON.stringify(u));
    if (tid) localStorage.setItem("tenantId", tid);
  }, [token, user, corps.tenantId ?? ""]);
}

/** Surligne un élément le temps qu'on le voie : la vidéo ne montre pas le pointeur. */
export async function surligner(page, locator, ms = 900) {
  await locator.evaluate((e, d) => {
    const avant = e.style.outline;
    e.style.outline = "4px solid #f5a623"; e.style.outlineOffset = "2px";
    setTimeout(() => { e.style.outline = avant; }, d);
  }, ms).catch(() => {});
  await page.waitForTimeout(ms);
}

/** Déplier la rubrique, puis la notion, jusqu'à l'entrée ; la surligner ; l'ouvrir. */
export async function parLeMenu(page, cle) {
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
export async function remplirExemple(page, exemple) {
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
export async function parcourir(page, secondes, exemple = []) {
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

