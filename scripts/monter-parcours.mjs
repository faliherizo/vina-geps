#!/usr/bin/env node
/**
 * **Les parcours en vidéo de l'aide** : une vidéo par parcours de `e2e/donnees/fx-ao/videos-aide.json`, montée depuis
 * l'essai qui le joue en le racontant (`e2e/tests/e2e/deploiement-fx-ao-videos.spec.ts`).
 *
 *   scripts/tourner-parcours-fx-ao.sh          # joue les parcours sur la démonstration en racontant, puis monte
 *   node scripts/monter-parcours.mjs           # remonte depuis le dernier tournage (e2e/test-results/recit.jsonl)
 *   node scripts/monter-parcours.mjs --seulement disponibilites,quarts
 *
 * Chaque vidéo : un carton qui annonce le parcours, puis chaque phrase de l'essai posée sur ce que la page montrait à
 * cet instant, jusqu'à la phrase suivante. Les temps de connexion disparaissent ; les gestes restent à vitesse normale.
 * Sortie : `frontend/public/aide/videos/parcours-<id>.webm` et `.fr.vtt`, lus par `frontend/src/aide/parcoursVideo.ts`.
 * Un parcours sans récit fait échouer le montage en le nommant : une vidéo ne montre jamais un essai qui n'a pas passé.
 */
import { existsSync, readFileSync, writeFileSync } from "node:fs";
import { dirname, join } from "node:path";
import { fileURLToPath } from "node:url";
import { dessinerCartons } from "./cartons-vitrine.mjs";
import { HAUTEUR, LARGEUR, atelier, vtt } from "./lib/montage.mjs";

const RACINE = join(dirname(fileURLToPath(import.meta.url)), "..");
const JOURNAL = join(RACINE, "e2e", "test-results", "recit.jsonl");
const ESSAI = "tests/e2e/deploiement-fx-ao-videos.spec.ts";
const SORTIE = join(RACINE, "frontend", "public", "aide", "videos");
const TRAVAIL = join(process.env.TMPDIR ?? "/tmp", "vina-geps-parcours");
const i = process.argv.indexOf("--seulement");
const SEULEMENT = i > 0 ? new Set(process.argv[i + 1].split(",")) : null;
const parcours = JSON.parse(readFileSync(join(RACINE, "e2e", "donnees", "fx-ao", "videos-aide.json"), "utf8")).parcours
  .filter((p) => !SEULEMENT || SEULEMENT.has(p.id));

if (!existsSync(JOURNAL)) throw new Error(`aucun récit : ${JOURNAL} — tourner avec scripts/tourner-parcours-fx-ao.sh`);
const entrees = readFileSync(JOURNAL, "utf8").split("\n").filter(Boolean).map((l) => JSON.parse(l))
  .filter((e) => e.essai === ESSAI).sort((a, b) => a.t - b.t);

const sansRecit = parcours.filter((p) => !entrees.some((e) => e.titre === p.essai)).map((p) => p.id);
if (sansRecit.length) throw new Error(`parcours sans récit — l'essai a-t-il passé avec E2E_RECIT=1 ? ${sansRecit.join(", ")}`);

const { encoderCarton, concatener, passages, encoder } = atelier(TRAVAIL);
const cartons = parcours.map((p) => ({ nom: `parcours-${p.id}`, surtitre: "VINA GePS · Aide en vidéo", titre: p.titre,
  bas: "Parcours commenté, joué sur l'organisme de démonstration" }));
await dessinerCartons(cartons, join(TRAVAIL, "cartons"), LARGEUR, HAUTEUR);

for (const p of parcours) {
  const morceaux = [encoderCarton(join(TRAVAIL, "cartons", `parcours-${p.id}.png`), p.annonce),
    ...passages(entrees.filter((e) => e.titre === p.essai)).map(encoder)];
  concatener(morceaux.map((m) => m.fichier), join(SORTIE, `parcours-${p.id}.webm`));
  writeFileSync(join(SORTIE, `parcours-${p.id}.fr.vtt`), vtt(morceaux));
  const total = morceaux.reduce((s, m) => s + m.longueur, 0);
  console.log(`  ✓ parcours ${p.id} : ${morceaux.length - 1} passage(s), ${Math.round(total)} s`);
}
console.log(`✓ ${parcours.length} parcours montés → ${SORTIE}`);
