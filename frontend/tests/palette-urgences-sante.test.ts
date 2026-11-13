/**
 * **La palette des maquettes Urgences-santé, mesurée** (ADR 0069).
 *
 * Une maquette est dessinée à l'œil, sur un bon écran, en plein jour. Celle-ci portait un gris
 * doux (`#627d98`) à **4,28:1** sur blanc et **3,95:1** sur son propre fond de page, pour les
 * sous-titres et tous les en-têtes de colonnes — sous le seuil de 4,5:1 (WCAG 1.4.3). La suivre à
 * la lettre aurait livré un module à refaire après l'audit, c'est-à-dire après l'avoir montré.
 *
 * Cet essai relit `src/composants/maquette-sante.css` et recalcule. Il vérifie deux choses, et la
 * seconde est celle qu'on oublie :
 *
 *  1. chaque paire texte/fond tient son seuil ;
 *  2. **chaque rapport écrit en commentaire est vrai.** Une annotation est une promesse ; non
 *     vérifiée, elle survit à la valeur qu'elle décrivait et devient le pire des commentaires —
 *     celui qu'on croit.
 */
import fs from "fs";
import path from "path";
import { describe, expect, it } from "vitest";

const FEUILLE = path.join(__dirname, "..", "src", "composants", "maquette-sante.css");
const source = fs.readFileSync(FEUILLE, "utf-8");

/** Les jetons `--maquette-*` déclarés par la feuille, par leur nom sans préfixe. */
function jetons(css: string): Record<string, string> {
  const trouves: Record<string, string> = {};
  for (const m of css.matchAll(/--maquette-([a-z-]+):\s*(#[0-9a-fA-F]{6})/g)) trouves[m[1]] = m[2].toLowerCase();
  return trouves;
}

function luminance(hex: string): number {
  const canal = (deux: string) => {
    const x = parseInt(deux, 16) / 255;
    return x <= 0.03928 ? x / 12.92 : ((x + 0.055) / 1.055) ** 2.4;
  };
  const h = hex.replace("#", "");
  return 0.2126 * canal(h.slice(0, 2)) + 0.7152 * canal(h.slice(2, 4)) + 0.0722 * canal(h.slice(4, 6));
}

function rapport(a: string, b: string): number {
  const la = luminance(a); const lb = luminance(b);
  return (Math.max(la, lb) + 0.05) / (Math.min(la, lb) + 0.05);
}

const T = jetons(source);
const BLANC = "#ffffff";

describe("Palette Urgences-santé — calculée, jamais jugée à l'œil", () => {
  it("la feuille déclare bien ses jetons : sans cela l'essai passerait à vide", () => {
    for (const nom of ["fond", "surface", "marine", "teinte", "trait", "filet", "encre",
                       "courant", "doux", "sur-marine", "primaire", "primaire-appui",
                       "accent-texte", "trait-controle"]) {
      expect(T[nom], `jeton --maquette-${nom}`).toMatch(/^#[0-9a-f]{6}$/);
    }
  });

  /**
   * Le texte courant : 4,5:1 (1.4.3). Chaque teinte est mesurée contre **toutes** les surfaces où
   * elle apparaît — un gris qui passe sur blanc peut tomber sous le seuil sur le fond de page, et
   * c'est exactement ce qui arrivait à celui de la maquette.
   */
  it("tout texte tient 4,5:1 sur chacune des surfaces où il se pose", () => {
    const surfaces = { blanc: BLANC, page: T.fond, teinte: T.teinte };
    const sous: string[] = [];
    for (const [nomTexte, texte] of Object.entries({ encre: T.encre, courant: T.courant, doux: T.doux, "accent-texte": T["accent-texte"] })) {
      for (const [nomFond, fond] of Object.entries(surfaces)) {
        const r = rapport(texte, fond);
        if (r < 4.5) sous.push(`${nomTexte} (${texte}) sur ${nomFond} (${fond}) : ${r.toFixed(2)}:1`);
      }
    }
    // Lister les fautifs plutôt que s'arrêter au premier : l'échec doit nommer ce qu'il faut corriger.
    expect(sous).toEqual([]);
  });

  it("le texte posé sur le marine et sur le sarcelle tient le seuil", () => {
    expect(rapport(T["sur-marine"], T.marine)).toBeGreaterThanOrEqual(4.5);
    expect(rapport(BLANC, T.marine)).toBeGreaterThanOrEqual(4.5);
    // Bouton primaire : le texte blanc dessus, et le bouton lui-même reconnaissable sur la page.
    expect(rapport(BLANC, T.primaire)).toBeGreaterThanOrEqual(4.5);
    expect(rapport(BLANC, T["primaire-appui"])).toBeGreaterThanOrEqual(4.5);
  });

  /**
   * 1.4.11 : ce qui permet de **reconnaître** un champ ou un bouton tient 3:1. La maquette ne
   * dessine aucun formulaire — elle ne pouvait donc pas trancher, et le contour de carte qu'elle
   * donne (`#d9e2ec`, 1,31:1) aurait été repris par défaut.
   */
  it("le contour d'un contrôle tient 3:1, sur le blanc comme sur le fond de page", () => {
    expect(rapport(T["trait-controle"], BLANC)).toBeGreaterThanOrEqual(3);
    expect(rapport(T["trait-controle"], T.fond)).toBeGreaterThanOrEqual(3);
    // Et la bordure de carte reste celle de la maquette : c'est une surface, pas un contrôle.
    expect(T.trait).toBe("#d9e2ec");
  });

  it("**chaque rapport écrit en commentaire est vrai** — une annotation non vérifiée se croit", () => {
    const teintes = [...new Set([...Object.values(T), BLANC])];
    const possibles = new Set<string>();
    for (const a of teintes) for (const b of teintes) possibles.add(rapport(a, b).toFixed(2).replace(".", ","));
    // Les rapports cités pour désigner un défaut de la maquette (#627d98) ou un seuil de la norme
    // ne sont pas des paires de jetons : ils sont énumérés, et recalculés ici.
    const horsJetons = new Map([
      ["4,28", rapport("#627d98", BLANC)], ["3,95", rapport("#627d98", T.fond)],
      ["3,70", rapport("#627d98", T.teinte)], ["1,31", rapport("#d9e2ec", BLANC)],
    ]);
    const faux: string[] = [];
    for (const m of source.matchAll(/(\d+,\d{2}):1/g)) {
      const cite = m[1];
      if (possibles.has(cite)) continue;
      const calcule = horsJetons.get(cite);
      if (calcule !== undefined && calcule.toFixed(2).replace(".", ",") === cite) continue;
      faux.push(cite);
    }
    expect(faux, "rapports annotés dans la feuille qu'aucun calcul ne produit").toEqual([]);
  });
});
