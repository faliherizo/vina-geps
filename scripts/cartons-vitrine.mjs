#!/usr/bin/env node
/**
 * **Les cartons-titres des films** : une image plein écran qui dit ce qu'on va voir — un chapitre du film de la vitrine
 * (numéro, titre, essai joué) ou l'écran d'une vidéo d'aide. On sait ce qu'on regarde avant que l'essai commence, même
 * sans le son.
 *
 *   node scripts/cartons-vitrine.mjs <dossier> [largeur] [hauteur]   # les chapitres de scripts/vitrine-chapitres.json
 *
 * Le dessin est du HTML rendu par Chromium : le ffmpeg du poste n'a pas `drawtext`, et le navigateur gère seul les accents
 * et le retour à la ligne. Contraste : texte #ffffff sur #0f2a44 (14,6:1), sous-titre #c9d8e8 sur #0f2a44 (10,1:1), filet
 * #5fb3f0 (6,4:1).
 */
import { mkdirSync, readFileSync } from "node:fs";
import { createRequire } from "node:module";
import { dirname, join } from "node:path";
import { fileURLToPath } from "node:url";

const RACINE = join(dirname(fileURLToPath(import.meta.url)), "..");
const MARQUE = "VINA GePS";
const EDITEUR = "VINA IT Solution";
const majuscule = (t) => t.charAt(0).toUpperCase() + t.slice(1);
const echapper = (t) => t.replace(/&/g, "&amp;").replace(/</g, "&lt;").replace(/>/g, "&gt;");

/** Un carton : `{ surtitre, numero, titre, bas }` — `numero` et `bas` sont facultatifs. */
function html(c, largeur, hauteur) {
  return `<!doctype html><html lang="fr"><meta charset="utf-8"><style>
    html,body{margin:0;width:${largeur}px;height:${hauteur}px;background:#0f2a44;color:#fff;font-family:"Helvetica Neue",Arial,sans-serif}
    main{box-sizing:border-box;height:100%;padding:0 96px;display:flex;flex-direction:column;justify-content:center;gap:20px}
    .avis{font-size:24px;letter-spacing:.04em;color:#c9d8e8}
    .num{font-size:30px;font-weight:600;color:#c9d8e8}
    h1{margin:0;font-size:52px;line-height:1.15;font-weight:700;border-left:8px solid #5fb3f0;padding-left:28px}
    .essai{font-size:22px;color:#c9d8e8;font-family:Menlo,monospace}
    .marque{position:absolute;right:48px;bottom:36px;font-size:22px;font-weight:700;color:#c9d8e8}
  </style><main>
    <div class="avis">${echapper(c.surtitre)}</div>
    ${c.numero ? `<div class="num">${echapper(c.numero)}</div>` : ""}
    <h1>${echapper(majuscule(c.titre))}</h1>
    ${c.bas ? `<div class="essai">${echapper(c.bas)}</div>` : ""}
  </main><div class="marque">${MARQUE}</div></html>`;
}

/** Dessine chaque carton de `liste` (`{ nom, surtitre, numero?, titre, bas? }`) en `<dossier>/<nom>.png`. */
export async function dessinerCartons(liste, dossier, largeur = 1280, hauteur = 720) {
  mkdirSync(dossier, { recursive: true });
  const { chromium } = createRequire(join(RACINE, "e2e", "package.json"))("playwright");
  const navigateur = await chromium.launch();
  try {
    const page = await navigateur.newPage({ viewport: { width: Number(largeur), height: Number(hauteur) } });
    for (const c of liste) {
      await page.setContent(html(c, largeur, hauteur));
      await page.screenshot({ path: join(dossier, `${c.nom}.png`) });
    }
  } finally { await navigateur.close(); }
}

/** Les cartons des chapitres du film de la vitrine, dans l'ordre de `scripts/vitrine-chapitres.json`. */
export function cartonsDesChapitres() {
  const { chapitres } = JSON.parse(readFileSync(join(RACINE, "scripts", "vitrine-chapitres.json"), "utf8"));
  // Le film présente le produit : ni appel d'offres ni client sur un carton, seulement la fonctionnalité.
  return chapitres.map((c, i) => ({
    nom: `carton-${i + 1}`,
    surtitre: `${MARQUE} · ${EDITEUR}`,
    numero: `Chapitre ${i + 1} sur ${chapitres.length}`,
    titre: c.titre,
    bas: "Démonstration jouée contre le serveur, à vitesse réelle",
  }));
}

if (process.argv[1] === fileURLToPath(import.meta.url)) {
  const [, , dossier, largeur = "1280", hauteur = "720"] = process.argv;
  if (!dossier) throw new Error("usage : node scripts/cartons-vitrine.mjs <dossier> [largeur] [hauteur]");
  const liste = cartonsDesChapitres();
  await dessinerCartons(liste, dossier, largeur, hauteur);
  console.log(`✓ ${liste.length} cartons → ${dossier}`);
}
