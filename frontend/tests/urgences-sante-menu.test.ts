/**
 * **Le menu du module Urgences-santé et son registre se répondent.**
 *
 * Une entrée de menu sans page produit un lien qui ouvre le vide ; une page sans entrée est un
 * écran livré que personne n'atteint. Les deux sont invisibles jusqu'à ce qu'un client le
 * signale — c'est pourquoi le module Santé Québec a le même essai, et pour la même raison.
 */
import { readFileSync } from "node:fs";
import { resolve } from "node:path";
import { describe, expect, it } from "vitest";
import { CLES_US, ENTREES_US, MODULE_US, NOTIONS_US } from "../src/urgencessante/menu";
import { PAGES_US } from "../src/urgencessante";
import { PAGE_MODULE } from "../src/modulesParPage";
import { DICTS } from "../src/i18n";

describe("Urgences-santé — le menu et ses pages", () => {
  it("chaque entrée a sa page, et chaque page son entrée", () => {
    expect(ENTREES_US.filter((e) => !PAGES_US[e.cle]).map((e) => e.cle)).toEqual([]);
    expect(Object.keys(PAGES_US).filter((c) => !CLES_US.includes(c))).toEqual([]);
    // Vingt-neuf : dix-neuf écrans de pilotage, sept personnels, trois de communication.
    expect(CLES_US).toHaveLength(30);
  });

  it("aucune clé en double : une page ne s'atteint que par une entrée", () => {
    expect(new Set(CLES_US).size).toBe(CLES_US.length);
  });

  it("chaque page porte **le module qui la gouverne**, et il commence par le socle", () => {
    // Une page sans module serait ouverte à tous les établissements, y compris ceux qui n'ont pas
    // acheté l'avis — et la garde serveur refuserait ses données sans que le menu le dise.
    expect(ENTREES_US.filter((e) => !e.module).map((e) => e.cle)).toEqual([]);
    expect(ENTREES_US.filter((e) => !e.module.startsWith(MODULE_US)).map((e) => e.cle)).toEqual([]);
    // Et ce module est bien celui que l'application lit pour décider de l'URL directe.
    expect(ENTREES_US.filter((e) => PAGE_MODULE[e.cle as keyof typeof PAGE_MODULE] !== e.module)
      .map((e) => e.cle)).toEqual([]);
  });

  it("chaque libellé de page et de notion existe dans les trois langues", () => {
    const manquants: string[] = [];
    for (const [langue, dict] of Object.entries(DICTS)) {
      for (const cle of CLES_US) if (!(`nav.${cle}` in dict)) manquants.push(`${langue}:nav.${cle}`);
      for (const n of NOTIONS_US) {
        if (n.cle && !(`concept.${n.cle}` in dict)) manquants.push(`${langue}:concept.${n.cle}`);
      }
    }
    expect(manquants).toEqual([]);
  });

  it("une notion regroupe au moins deux pages — un intertitre d'un seul élément n'est pas une structure", () => {
    expect(NOTIONS_US.filter((n) => n.cle && n.entrees.length < 2).map((n) => n.cle)).toEqual([]);
    // Et une entrée sans notion est un lien direct : chacune n'en porte qu'une (Mon horaire, Mon équipe).
    const directes = NOTIONS_US.filter((n) => n.cle === null);
    expect(directes.map((n) => n.entrees.length)).toEqual(directes.map(() => 1));
    expect(directes.flatMap((n) => n.entrees.map((e) => e.cle))).toEqual(["monhoraire", "monequipe"]);
  });

  it("les écrans personnels sont ouverts à tous, les consoles à l'encadrement seul", () => {
    const personnels = ["monhoraire", "mesdisponibilites", "mesabsences", "monagph",
                        "monpointage", "matemps", "mesechanges"];
    /**
     * **La communication est ouverte à tous, et ce n'est pas une console.**
     *
     * Un paramédic doit joindre son collègue et son chef d'équipe : une messagerie réservée à
     * l'encadrement ne sert à rien — elle ne porte que la moitié des interlocuteurs. Les listes
     * de distribution le sont aussi, parce qu'une liste **appartient à son auteur** (seul lui la
     * modifie, même partagée) : la réserver supposerait qu'un employé n'a personne à joindre
     * deux fois.
     */
    const communication = ["usmessages", "usappels", "usdiffusion"];
    for (const cle of [...personnels, ...communication]) {
      expect(ENTREES_US.find((e) => e.cle === cle)?.roles, cle).toContain("employee");
    }
    // Une console ouverte à l'employé lui montrerait les quarts de tout le monde : la garde
    // serveur refuserait, et l'écran s'ouvrirait vide — le pire des deux états.
    for (const e of ENTREES_US.filter((x) => ![...personnels, ...communication].includes(x.cle))) {
      expect(e.roles, e.cle).not.toContain("employee");
    }
  });

  it("la communication a un seul foyer : les pages d'ici rendent les sections du module Santé Québec", () => {
    // Si quelqu'un recopiait la messagerie plutôt que de la reprendre, ces sections cesseraient
    // d'être partagées — et les deux messageries divergeraient au premier correctif (ADR 0034).
    const source = readFileSync(resolve(process.cwd(), "src/urgencessante/communication.tsx"), "utf8");
    for (const sections of ["SECTIONS_CONVERSATIONS", "SECTIONS_RECHERCHE", "SECTIONS_APPELS", "SECTIONS_LISTES"]) {
      expect(source, sections).toContain(sections);
    }
    // Et les onglets sont retirés : « un menu, une page » (demande du 2 novembre 2026).
    expect(source).toContain("sansOnglets");
    expect(source).toContain('module: "Urgences-santé"');
  });
});
