/**
 * Le menu à deux niveaux : **aucun module dupliqué, aucune notion éclatée.**
 *
 * Ce que l'utilisateur a demandé après avoir vu « Déclarer une absence » dans un onglet des
 * Remplacements scolaires et « Absences » dans un menu voisin : qu'une notion n'ait qu'une place.
 * Cet essai en fait une contrainte : chaque page a une entrée principale et une seule, chaque
 * onglet n'est proposé qu'une fois, chaque libellé n'apparaît qu'une fois, et tout ce que le menu
 * nomme existe dans les trois langues.
 */
import { describe, expect, it } from "vitest";
import { NAVIGATION, cleDeNotion, estConcept, ongletAdmis, proprietairesDesOnglets, testIdDe, type Vue } from "../src/navigation";
import { DICTS } from "../src/i18n";

const vues: Vue[] = NAVIGATION.flatMap((g) => g.entrees).flatMap((e) => (estConcept(e) ? e.vues : [e]));
const concepts = NAVIGATION.flatMap((g) => g.entrees).filter(estConcept);

describe("menu à deux niveaux — une notion, une entrée", () => {
  it("chaque page a exactement une entrée principale (celle que les essais désignent par nav-<page>)", () => {
    const principales = vues.filter((v) => !v.onglet || v.principale).map((v) => v.page);
    expect([...new Set(principales)].sort()).toEqual([...principales].sort());
    for (const page of new Set(vues.map((v) => v.page))) expect(principales).toContain(page);
  });

  it("aucun identifiant d'essai n'est porté deux fois", () => {
    const ids = vues.map(testIdDe);
    expect(new Set(ids).size).toBe(ids.length);
  });

  it("une entrée principale qui ouvre un onglet ne porte pas le suffixe d'onglet", () => {
    const acces = vues.find((v) => v.page === "access" && v.principale)!;
    expect(acces.onglet).toBeUndefined();
    expect(testIdDe(acces)).toBe("nav-access");
    expect(testIdDe({ page: "access", onglet: "qui" })).toBe("nav-access-qui");
    expect(testIdDe({ page: "access", onglet: "objets", principale: true })).toBe("nav-access");
  });

  it("aucun libellé n'apparaît deux fois dans une même langue — un doublon de nom est un doublon de module", () => {
    for (const lang of ["fr", "en", "mg"] as const) {
      const libelles = vues.map((v) => DICTS[lang][v.i18nKey ?? `nav.${v.page}`] ?? v.i18nKey ?? `nav.${v.page}`);
      const doublons = libelles.filter((l, i) => libelles.indexOf(l) !== i);
      expect(doublons, lang).toEqual([]);
    }
  });

  it("chaque libellé de notion et de vue existe dans les trois langues", () => {
    const cles = [...concepts.map((c) => c.i18nKey), ...vues.map((v) => v.i18nKey).filter((k): k is string => !!k)];
    for (const lang of ["fr", "en", "mg"] as const) {
      const manquantes = cles.filter((k) => !DICTS[lang][k]);
      expect(manquantes, lang).toEqual([]);
    }
  });

  it("une notion regroupe au moins deux vues — un intertitre d'un seul élément n'est pas une structure", () => {
    for (const c of concepts) expect(c.vues.length, c.key).toBeGreaterThanOrEqual(2);
  });

  it("un onglet appartient à sa notion : l'écran Accès range ses trois vues sous « Accès », la vue principale comprise (ADR 0035)", () => {
    const acces = cleDeNotion("admin", "access");
    expect(proprietairesDesOnglets("access")).toEqual({ parDefaut: acces, parOnglet: { octrois: acces, qui: acces } });
    expect(ongletAdmis("access", "qui", acces)).toBe(true);
    expect(ongletAdmis("access", "qui", cleDeNotion("admin", "autre"))).toBe(false);
    // Hors menu, ou sans notion désignée, tout est admis : la garde ne vaut que pour un menu qui a parlé.
    expect(ongletAdmis("access", "n-importe", null)).toBe(true);
    expect(ongletAdmis("page-hors-menu", "x", acces)).toBe(true);
  });

  it("les écrans de l'AO 26-015-DO vivent sous « Urgences-santé », **une page par entrée**", () => {
    // Ils étaient douze onglets d'un écran et sept d'un autre : une page à son adresse se partage
    // par un lien, revient par le bouton « retour », et se charge seule — ce qu'un onglet ne fait
    // jamais. L'ordre et le découpage suivent les planches s01 à s12.
    const section = NAVIGATION.find((g) => g.key === "urgencessante");
    expect(section).toBeDefined();
    expect(section!.module).toBe("effectifs");
    expect(section!.entrees.filter(estConcept).map((c) => c.key)).toEqual([
      "usdisponibilitesnotion", "usabsencesnotion", "usagphnotion", "usquarts",
      "uspointagenotion", "ustempsnotion", "usechangesnotion", "uscommunication", "usadministration",
    ]);
    // « Mon horaire » est une **vue directe** : une notion d'un seul élément n'est pas une structure.
    expect(section!.entrees.filter((e) => !estConcept(e)).map((v) => (v as Vue).page)).toEqual(["monhoraire"]);

    /**
     * **Aucun onglet** : chaque vue de la section est une page entière. C'est la demande du
     * 2 novembre — « un menu = une page » —, et c'est ce que les planches montrent.
     */
    const vuesDeLaSection = section!.entrees.flatMap((e) => (estConcept(e) ? e.vues : [e]));
    expect(vuesDeLaSection.filter((v) => v.onglet).map((v) => `${v.page}:${v.onglet}`)).toEqual([]);
    expect(vuesDeLaSection).toHaveLength(29);

    /**
     * **La communication du personnel** (2 novembre 2026) : trois vues reprises du module Santé
     * Québec, et non recopiées — la fonction a un foyer, elle gagne une porte (ADR 0034).
     */
    const communication = section!.entrees.filter(estConcept).find((c) => c.key === "uscommunication");
    expect((communication?.vues ?? []).map((v) => v.page)).toEqual(["usmessages", "usappels", "usdiffusion"]);

    // Les anciennes pages à onglets ont disparu : plus personne ne les atteint.
    const toutesLesPages = NAVIGATION.flatMap((g) => g.entrees)
      .flatMap((e) => (estConcept(e) ? e.vues : [e])).map((v) => v.page);
    expect(toutesLesPages.filter((p) => p === "planification" || p === "effectifs")).toEqual([]);

    /**
     * **Chaque notion réunit la vue de l'employé et celle de l'encadrement** d'un même sujet.
     * On cherche un sujet, pas le rôle sous lequel on le regarde ; une notion qui n'aurait qu'une
     * des deux faces serait un demi-sujet, et l'autre moitié redeviendrait introuvable.
     */
    const PAIRES: Record<string, [string, string]> = {
      usdisponibilitesnotion: ["mesdisponibilites", "usdisponibilites"],
      usabsencesnotion: ["mesabsences", "usabsences"],
      usagphnotion: ["monagph", "usagph"],
      uspointagenotion: ["monpointage", "uspresence"],
      ustempsnotion: ["matemps", "ustemps"],
      usechangesnotion: ["mesechanges", "usechanges"],
    };
    for (const [cle, attendues] of Object.entries(PAIRES)) {
      const notion = section!.entrees.filter(estConcept).find((c) => c.key === cle);
      expect((notion?.vues ?? []).map((v) => v.page), cle).toEqual(attendues);
    }
  });
});
