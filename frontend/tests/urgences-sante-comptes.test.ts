/**
 * **Le document des comptes est le miroir du semoir.**
 *
 * Une liste de comptes tenue à la main finit toujours par décrire des comptes qui n'existent plus :
 * on ajoute un profil au semoir, on oublie le document, et l'évaluateur essaie un identifiant qui
 * ne se connecte pas. C'est le genre de défaut qui se découvre **pendant** la démonstration.
 */
import fs from "fs";
import path from "path";
import { describe, expect, it } from "vitest";
import donnees from "../../e2e/donnees/urgences-sante/demonstration.json";

const DOC = path.join(__dirname, "..", "..", "docs", "urgences-sante", "COMPTES-DEMONSTRATION.md");
const texte = fs.readFileSync(DOC, "utf-8");

interface Personne {
  nom: string; matricule: string | null; profil: string;
  compte: { username: string; role: string };
}
const PERSONNES = donnees.personnes as Personne[];

describe("Urgences-santé — les comptes de démonstration", () => {
  it("le document est lu : sans cela, l'essai passerait à vide", () => {
    expect(texte.length).toBeGreaterThan(1000);
    expect(texte).toContain("Passe!Demo2026");
    expect(PERSONNES.length).toBeGreaterThanOrEqual(6);
  });

  it("chaque compte semé est **documenté**, avec son profil", () => {
    const manquants = PERSONNES.filter((p) => !texte.includes(`\`${p.compte.username}\``))
      .map((p) => p.compte.username);
    expect(manquants, "comptes semés mais absents du document").toEqual([]);
    // Le profil aussi : un identifiant sans rôle n'apprend pas ce qu'on va voir en se connectant.
    expect(PERSONNES.filter((p) => !texte.includes(p.profil)).map((p) => p.compte.username)).toEqual([]);
  });

  it("le document ne promet **aucun compte** que le semoir ne crée pas", () => {
    const connus = new Set([...PERSONNES.map((p) => p.compte.username), "admin"]);
    const cites = [...texte.matchAll(/^\| `([a-z.]+)`/gm)].map((m) => m[1]);
    expect(cites.length).toBeGreaterThanOrEqual(6);
    expect(cites.filter((c) => !connus.has(c))).toEqual([]);
  });

  it("chaque profil dit **ce qu'il voit** : un rôle sans conséquence visible n'est pas un profil", () => {
    expect(PERSONNES.filter((p) => !p.profil?.trim()).map((p) => p.compte.username)).toEqual([]);
    expect(PERSONNES.filter((p) => !(p as unknown as { voit?: string }).voit?.trim())
      .map((p) => p.compte.username)).toEqual([]);
  });

  it("une personne **sans matricule** n'est pas planifiable, et le document le dit", () => {
    const sansRessource = PERSONNES.filter((p) => !p.matricule);
    expect(sansRessource.length).toBeGreaterThanOrEqual(1);
    // Sans cette mention, on croirait à un écran cassé là où c'est une règle du produit.
    expect(texte).toMatch(/n'est pas planifiable|pas planifiable/);
  });
});
