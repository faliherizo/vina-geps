/**
 * Les garde-fous de la refonte visuelle (REFONTE-VISUELLE.md § F.3) — deux cliquets :
 *
 * 1. **Les `data-testid` sont gelés.** La liste `docs/refonte-visuelle/testids.txt` est celle que
 *    les essais e2e, les démonstrations filmées et les audits connaissent ; un identifiant qui
 *    disparaît du code fait échouer la construction. On en ajoute (la liste se régénère :
 *    `npm run testids`), on n'en retire pas sans le dire — les attributs `data-testid="…"` comme
 *    les propriétés `testId: "…"` / `testId="…"` des composants communs (onglets, tuiles, tableaux).
 * 2. **Chaque page reste joignable.** Tout `id` de l'union `Page` d'`App.tsx` est atteint depuis
 *    `NAVIGATION` — directement ou par la vue de repli d'une notion — sinon l'écran existe sans
 *    qu'aucun menu n'y mène (voir la mémoire « module absent du catalogue »).
 */
import fs from "node:fs";
import path from "node:path";
import { describe, expect, it } from "vitest";
import { NAVIGATION, estConcept } from "../src/navigation";

const RACINE_SRC = path.join(__dirname, "..", "src");
/**
 * L'extraction est **partagée** avec `scripts/lister-testids.cjs`.
 *
 * Elle y était recopiée. Le jour où le générateur a appris à lire les identifiants construits,
 * ce garde-fou a déclaré 1 274 identifiants « disparus » du code : il mesurait sa propre copie,
 * pas le code. Une règle, un endroit.
 */
// eslint-disable-next-line @typescript-eslint/no-var-requires
const { identifiantsDe } = require("../scripts/extraireTestids.cjs") as
  { identifiantsDe: (source: string) => Set<string> };

function fichiersSource(dossier: string): string[] {
  return fs.readdirSync(dossier, { withFileTypes: true }).flatMap((e) => {
    const p = path.join(dossier, e.name);
    return e.isDirectory() ? fichiersSource(p) : /\.tsx?$/.test(e.name) ? [p] : [];
  });
}

describe("garde-fous de la refonte", () => {
  it("aucun data-testid gelé n'a disparu du code", () => {
    const geles = fs.readFileSync(path.join(__dirname, "..", "..", "docs", "refonte-visuelle", "testids.txt"), "utf-8").split("\n").filter(Boolean);
    expect(geles.length).toBeGreaterThan(1500);
    const presents = new Set<string>();
    for (const f of fichiersSource(RACINE_SRC)) {
      for (const id of identifiantsDe(fs.readFileSync(f, "utf-8"))) presents.add(id);
    }
    const disparus = geles.filter((id) => !presents.has(id));
    expect(disparus, "identifiants d'essai retirés (régénérer testids.txt seulement si le retrait est voulu et documenté)").toEqual([]);
  });

  it("chaque page de l'application est joignable depuis le menu, directement ou par repli", () => {
    const app = fs.readFileSync(path.join(RACINE_SRC, "App.tsx"), "utf-8");
    const union = /^(?:export )?type Page = (.+);$/m.exec(app);
    expect(union, "l'union `type Page` d'App.tsx").not.toBeNull();
    const pages = [...union![1].matchAll(/"([^"]+)"/g)].map((m) => m[1]);
    expect(pages.length).toBeGreaterThan(35);
    const vues = NAVIGATION.flatMap((g) => g.entrees).flatMap((e) => (estConcept(e) ? e.vues : [e]));
    const atteintes = new Set(vues.flatMap((v) => [v.page, v.repli?.page]).filter((p): p is string => !!p));
    expect(pages.filter((p) => !atteintes.has(p)), "pages sans entrée de menu").toEqual([]);
  });
});
