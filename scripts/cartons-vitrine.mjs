#!/usr/bin/env node
/**
 * **Les cartons-titres du film de la vitrine** : avant chaque chapitre, une image plein écran qui dit son numéro, son
 * titre et l'essai qui le prouve — on sait ce qu'on regarde avant que l'essai commence, même sans le son.
 *
 *   node scripts/cartons-vitrine.mjs <dossier> [largeur] [hauteur]
 *
 * Écrit `<dossier>/carton-<n>.png` (n à partir de 1), dans l'ordre de `scripts/vitrine-chapitres.json`. Le dessin est du
 * HTML rendu par Chromium : le ffmpeg du poste n'a pas `drawtext`, et le navigateur gère seul les accents et le retour à
 * la ligne. Contraste : texte #ffffff sur #0f2a44 (14,6:1), sous-titre #c9d8e8 sur #0f2a44 (10,1:1), filet #5fb3f0 (6,4:1).
 */
import { mkdirSync, readFileSync } from "node:fs";
import { createRequire } from "node:module";
import { dirname, join } from "node:path";
import { fileURLToPath } from "node:url";

const RACINE = join(dirname(fileURLToPath(import.meta.url)), "..");
const { chromium } = createRequire(join(RACINE, "e2e", "package.json"))("playwright");
const [, , dossier, largeur = "1280", hauteur = "720"] = process.argv;
if (!dossier) throw new Error("usage : node scripts/cartons-vitrine.mjs <dossier> [largeur] [hauteur]");
mkdirSync(dossier, { recursive: true });

const { chapitres } = JSON.parse(readFileSync(join(RACINE, "scripts", "vitrine-chapitres.json"), "utf8"));
const majuscule = (t) => t.charAt(0).toUpperCase() + t.slice(1);
const echapper = (t) => t.replace(/&/g, "&amp;").replace(/</g, "&lt;").replace(/>/g, "&gt;");

/** Un carton : l'avis en surtitre, le numéro, le titre, et l'essai e2e qui joue le chapitre. */
function carton(c, n) {
  const [avis, ...reste] = c.titre.startsWith("FX-AO — ") ? ["VINA GePS · avis FX-AO-20260810", c.titre.slice(8)]
    : ["VINA GePS · avis 26-015-DO", c.titre];
  return `<!doctype html><html lang="fr"><meta charset="utf-8"><style>
    html,body{margin:0;width:${largeur}px;height:${hauteur}px;background:#0f2a44;color:#fff;font-family:"Helvetica Neue",Arial,sans-serif}
    main{box-sizing:border-box;height:100%;padding:0 96px;display:flex;flex-direction:column;justify-content:center;gap:20px}
    .avis{font-size:24px;letter-spacing:.04em;color:#c9d8e8}
    .num{font-size:30px;font-weight:600;color:#c9d8e8}
    h1{margin:0;font-size:52px;line-height:1.15;font-weight:700;border-left:8px solid #5fb3f0;padding-left:28px}
    .essai{font-size:22px;color:#c9d8e8;font-family:Menlo,monospace}
    .marque{position:absolute;right:48px;bottom:36px;font-size:22px;font-weight:700;color:#c9d8e8}
  </style><main>
    <div class="avis">${echapper(avis)}</div>
    <div class="num">Chapitre ${n} sur ${chapitres.length}</div>
    <h1>${echapper(majuscule(reste.join("")))}</h1>
    <div class="essai">Essai joué contre le serveur : ${echapper(c.fichier.replace(/^tests\/e2e\//, ""))}</div>
  </main><div class="marque">VINA GePS</div></html>`;
}

const navigateur = await chromium.launch();
const page = await navigateur.newPage({ viewport: { width: Number(largeur), height: Number(hauteur) } });
for (const [i, c] of chapitres.entries()) {
  await page.setContent(carton(c, i + 1));
  await page.screenshot({ path: join(dossier, `carton-${i + 1}.png`) });
}
await navigateur.close();
console.log(`✓ ${chapitres.length} cartons → ${dossier}`);
