/**
 * **Le chrome de VINA GEPS** (planche s01, ADR 0073) — contraste calculé, jamais jugé à l'œil.
 *
 * La barre du haut est devenue marine et la colonne du menu blanche. Chaque texte posé
 * directement sur l'une ou l'autre est mesuré ici contre sa surface ; les commandes de la barre
 * (aide, messages, avis, profil) ont leur propre fond blanc et ne dépendent pas du marine.
 */
import fs from "fs";
import path from "path";
import { describe, expect, it } from "vitest";

const source = fs.readFileSync(path.join(__dirname, "..", "src", "styles.css"), "utf-8");

/** La valeur d'une variable déclarée à la racine de la feuille. */
function jeton(nom: string): string {
  const m = source.match(new RegExp(`--${nom}:\\s*(#[0-9a-fA-F]{6})`));
  if (!m) throw new Error(`jeton --${nom} introuvable`);
  return m[1].toLowerCase();
}

function luminance(hex: string): number {
  const canal = (i: number) => {
    const x = parseInt(hex.slice(i, i + 2), 16) / 255;
    return x <= 0.03928 ? x / 12.92 : ((x + 0.055) / 1.055) ** 2.4;
  };
  return 0.2126 * canal(1) + 0.7152 * canal(3) + 0.0722 * canal(5);
}

function rapport(a: string, b: string): number {
  const [c, f] = [luminance(a), luminance(b)].sort((x, y) => y - x);
  return (c + 0.05) / (f + 0.05);
}

const BLANC = "#ffffff";

describe("Chrome VINA GEPS — barre marine, colonne blanche", () => {
  it("la marque en blanc tient 4,5:1 sur la barre marine", () => {
    expect(rapport(BLANC, jeton("marine"))).toBeGreaterThanOrEqual(4.5);
  });

  it("le nom de l'organisme, écrit à côté de la marque, tient aussi le seuil", () => {
    const m = source.match(/\.brand-organisme\s*\{[^}]*color:\s*(#[0-9a-fA-F]{6})/);
    expect(m, "couleur de .brand-organisme").not.toBeNull();
    expect(rapport(m![1].toLowerCase(), jeton("marine"))).toBeGreaterThanOrEqual(4.5);
  });

  it("l'entrée courante du menu se lit sur son fond bleu pâle, et les titres de rubrique sur le blanc", () => {
    expect(rapport(jeton("text"), jeton("nav-courant"))).toBeGreaterThanOrEqual(4.5);
    expect(rapport(jeton("text"), jeton("primary-soft"))).toBeGreaterThanOrEqual(4.5);
    expect(rapport(jeton("muted"), jeton("panel"))).toBeGreaterThanOrEqual(4.5);
  });

  it("le filet de l'entrée courante se distingue de son fond (1.4.11, 3:1)", () => {
    expect(rapport(jeton("primary"), jeton("nav-courant"))).toBeGreaterThanOrEqual(3);
  });
});
