/**
 * **Chaque écran a son aide, et le clic y mène vraiment.**
 *
 * Deux défauts distincts étaient signalés le 5 octobre 2026, et ils se renforçaient :
 *
 * 1. Le bouton « Aide sur cet écran » tentait le défilement dans un `setTimeout(…, 0)` posé juste
 *    après la navigation. React n'avait pas encore monté la page d'aide, `getElementById` rendait
 *    `null`, et **rien ne se passait, en silence** : on atterrissait en haut du sommaire, quel que
 *    soit l'écran de départ. Le défaut touchait *tous* les écrans, y compris ceux dont l'aide
 *    existait.
 * 2. La carte des aides était **partielle** — 25 écrans sur 76. Les 51 autres retombaient sur
 *    « Premiers pas ». Pour l'utilisateur, l'aide de son écran n'existait pas.
 *
 * Ces essais figent les trois propriétés qui empêchent le retour du défaut : la carte couvre tout,
 * toute section annoncée est rendue, et la page atteint la section qu'on lui demande.
 */
import { act } from "react";
import { createRoot, type Root } from "react-dom/client";
import { afterEach, beforeEach, describe, expect, it } from "vitest";
import { readFileSync } from "node:fs";
import { resolve } from "node:path";
import Help from "../src/Help";
import { AIDE_PAR_PAGE, IDS_SECTIONS, SECTIONS_AIDE, ancreConnue, sectionsVisibles, titreDeSection } from "../src/aideParEcran";
import { FICHES, idFiche } from "../src/aide/fichesEcrans";
import { ModuleAccessProvider } from "../src/moduleAccess";

(globalThis as unknown as { IS_REACT_ACT_ENVIRONMENT: boolean }).IS_REACT_ACT_ENVIRONMENT = true;

/** Les écrans déclarés par l'application, lus à la source : la liste ne se recopie pas. */
const ecrans = (() => {
  const source = readFileSync(resolve(process.cwd(), "src/App.tsx"), "utf8");
  const union = source.match(/^export type Page = ([^;]+);/m);
  if (!union) throw new Error("le type Page n'a pas été trouvé dans App.tsx");
  return [...union[1].matchAll(/"([a-z0-9]+)"/g)].map((m) => m[1]);
})();

describe("la carte des aides couvre tous les écrans", () => {
  it("aucun écran n'est sans aide", () => {
    const sans = ecrans.filter((p) => !(p in AIDE_PAR_PAGE));
    expect({ écransSansAide: sans }).toEqual({ écransSansAide: [] });
    expect(ecrans.length).toBeGreaterThan(35);
  });

  it("aucune aide ne désigne une section inventée", () => {
    const inventées = Object.entries(AIDE_PAR_PAGE)
      .filter(([, section]) => !IDS_SECTIONS.has(section))
      .map(([page, section]) => `${page} → #${section}`);
    expect({ sectionsInventées: inventées }).toEqual({ sectionsInventées: [] });
  });

  it("la carte ne nomme pas d'écran qui n'existe pas", () => {
    // L'inverse compte autant : une entrée laissée après la suppression d'un écran fait croire
    // à une couverture qu'on n'a plus.
    const fantômes = Object.keys(AIDE_PAR_PAGE).filter((p) => !ecrans.includes(p));
    expect({ écransFantômes: fantômes }).toEqual({ écransFantômes: [] });
  });

  it("chaque section porte un intitulé lisible, pour l'annonce du bouton", () => {
    for (const s of SECTIONS_AIDE) {
      expect(titreDeSection(s.id)).toBe(s.titre);
      expect(s.titre.length).toBeGreaterThan(8);
    }
    expect(new Set(SECTIONS_AIDE.map((s) => s.id)).size).toBe(SECTIONS_AIDE.length);
  });
});

describe("la page d'aide rend ce qu'elle annonce", () => {
  let conteneur: HTMLDivElement;
  let racine: Root;

  beforeEach(() => {
    conteneur = document.createElement("div");
    document.body.appendChild(conteneur);
    act(() => { racine = createRoot(conteneur); });
  });
  afterEach(() => {
    act(() => racine.unmount());
    conteneur.remove();
  });

  const rendre = (section?: string) => act(() => { racine.render(<Help section={section ?? null} />); });

  it("toute section du catalogue existe dans la page — sinon le sommaire a un lien mort", () => {
    rendre();
    const absentes = SECTIONS_AIDE.filter((s) => !conteneur.ownerDocument.getElementById(s.id)).map((s) => s.id);
    expect({ sectionsAnnoncéesMaisAbsentes: absentes }).toEqual({ sectionsAnnoncéesMaisAbsentes: [] });
  });

  it("le sommaire mène à chaque section, et à rien d'autre", () => {
    rendre();
    const liens = [...conteneur.querySelectorAll('nav a[href^="#"]')].map((a) => a.getAttribute("href")!.slice(1));
    expect(liens).toEqual(SECTIONS_AIDE.map((s) => s.id));
  });

  it("« Aide sur cet écran » ouvre la fiche de l'écran et lui donne le focus", () => {
    rendre(idFiche("matemps"));
    const fiche = conteneur.querySelector("#fiche-matemps") as HTMLDetailsElement;
    expect(fiche.open).toBe(true);
    expect(document.activeElement).toBe(fiche);
    // La fiche montre l'exemple, champ par champ, dans un tableau à en-têtes.
    expect(fiche.querySelector("table caption")!.textContent).toContain("Ma feuille de temps");
    expect([...fiche.querySelectorAll("tbody th")].map((t) => t.textContent)).toContain("Début de la période");
    // Les autres fiches restent repliées : le guide se parcourt d'un coup d'œil.
    expect((conteneur.querySelector("#fiche-monhoraire") as HTMLDetailsElement).open).toBe(false);
  });

  it("atteint la section demandée et lui donne le focus, sans attendre un délai", () => {
    // C'est le cœur du défaut : la section est atteinte **après le rendu**, par un effet, et non
    // par un `setTimeout` posé avant que la page n'existe.
    rendre("communication");
    const cible = conteneur.querySelector("#communication") as HTMLElement;
    expect(cible).not.toBeNull();
    expect(document.activeElement).toBe(cible);
    expect(cible.getAttribute("tabindex")).toBe("-1");
  });

  it("sans section demandée, ne vole le focus à personne", () => {
    rendre();
    expect(document.activeElement).toBe(document.body);
  });

  it("une section inconnue ne casse rien : la page s'affiche, au début", () => {
    rendre("section-qui-nexiste-pas");
    expect(conteneur.querySelector('[data-testid="help-page"]')).not.toBeNull();
    expect(document.activeElement).toBe(document.body);
  });

  it("l'écran des approbations a son aide à lui, pas une section voisine", () => {
    /**
     * L'écran d'où le défaut a été signalé — deux fois. La première correction l'avait renvoyé à
     * « Déroulements types », qui parle de contrats et de budgets : la section existait, le clic
     * y menait, et l'utilisateur n'y trouvait toujours rien sur l'approbation. Une aide qui
     * s'ouvre sur le mauvais sujet ne vaut pas mieux qu'une aide absente.
     */
    expect(AIDE_PAR_PAGE.approvals).toBe("approbations");
    rendre(AIDE_PAR_PAGE.approvals);
    expect(document.activeElement?.id).toBe("approbations");
    const texte = conteneur.querySelector("#approbations")!.textContent ?? "";
    for (const sujet of ["Demandes", "Règles", "Déléguer", "séparation des tâches"]) {
      expect(texte).toContain(sujet);
    }
  });
});

/**
 * **Le guide suit les modules de l'établissement** (26 octobre 2026).
 *
 * Il décrivait le produit entier à tout le monde : un centre qui n'a que les horaires médicaux
 * lisait l'approvisionnement, la fiscalité, la paie et les grands livres, puis cherchait au menu
 * des entrées qui n'existent pas chez lui. Le guide ne se contentait pas d'être long — il
 * affirmait quelque chose de faux sur son installation.
 *
 * La visibilité est **déduite** de deux tables déjà exhaustives (aide par écran, module par
 * écran), et c'est ce que ces essais fixent : une troisième table aurait divergé sans bruit.
 */
describe("le guide se restreint aux modules souscrits", () => {
  it("sans filtrage — compte global —, le guide reste entier", () => {
    expect(sectionsVisibles(() => true).map((s) => s.id)).toEqual(SECTIONS_AIDE.map((s) => s.id));
  });

  it("un organisme sans la communication garde les effectifs et perd la section de la messagerie", () => {
    const sansCommunication = (code?: string) => code !== "effectifs_communication";
    const vues = sectionsVisibles(sansCommunication).map((s) => s.id);
    expect(vues).toContain("effectifs-planification");
    expect(vues).not.toContain("communication");
  });

  it("les sections générales et celles du socle restent lisibles quand aucun module n'est ouvert", () => {
    // Le prédicat de `useModuleAllowed` : un écran sans code de module est du socle, toujours ouvert.
    const vues = sectionsVisibles((code?: string) => !code).map((s) => s.id);
    // Aucun écran ne renvoie vers elles : elles décrivent le produit, pas un module.
    expect(vues).toContain("accessibilite");
    expect(vues).toContain("notions");
    // Les écrans du socle n'ont pas de module : leur aide ne disparaît jamais non plus.
    expect(vues).toContain("demarrage");
    expect(vues).toContain("soutien");
    expect(vues).not.toContain("effectifs-planification");
  });

  it("une section partagée survit tant qu'un seul de ses écrans est ouvert", () => {
    // Mon espace sert six modules d'effectifs ; un seul ouvert suffit à garder l'aide.
    const unSeul = (code?: string) => code === undefined || code === "effectifs_presence";
    expect(sectionsVisibles(unSeul).map((s) => s.id)).toContain("mon-espace");
  });

  it("les écrans du module renvoient à ses deux sections : les effectifs, et la communication", () => {
    const us = Object.entries(AIDE_PAR_PAGE).filter(([page]) => page.startsWith("us"));
    expect(us).toHaveLength(25);
    expect([...new Set(us.map(([, s]) => s))].sort()).toEqual(["communication", "effectifs-planification"]);
  });
});

describe("une fiche par écran, avec un exemple de remplissage", () => {
  it("chaque écran a sa fiche, et chaque fiche dit quoi faire et donne un exemple", () => {
    const pages = Object.keys(AIDE_PAR_PAGE);
    expect(Object.keys(FICHES).sort()).toEqual(pages.sort());
    const pauvres = Object.entries(FICHES).filter(([, f]) => f.etapes.length === 0 || f.exemple.length === 0 || f.objet.length < 20 || f.resultat.length < 10)
      .map(([p]) => p);
    expect({ fichesSansContenu: pauvres }).toEqual({ fichesSansContenu: [] });
  });

  it("une ancre se reconnaît : section du guide, ou fiche d'un écran qui existe", () => {
    expect(ancreConnue("communication")).toBe(true);
    expect(ancreConnue("fiche-usechanges")).toBe(true);
    expect(ancreConnue("fiche-inexistant")).toBe(false);
    expect(ancreConnue("autre")).toBe(false);
  });
});

/**
 * **L'aide suit le menu de l'établissement.** Un écran que le superadmin a retiré du menu (toutes ses vues masquées,
 * comme l'organisme FX-AO réduit à son avis) n'a ni fiche ni section : l'aide d'une fonctionnalité qu'on n'a pas ne sert
 * à rien, et fait chercher au menu une entrée absente.
 */
describe("le guide se restreint au menu que le superadmin laisse à l'établissement", () => {
  const PAIE = ["paiecycles", "paiedossiers", "paiedeclarations", "mesbulletins", "paieparametres", "paieregles", "paietemps"];

  it("une section dont tous les écrans sont masqués disparaît, même si son module est ouvert", () => {
    const vues = sectionsVisibles(() => true, (page) => PAIE.includes(page)).map((s) => s.id);
    expect(vues).not.toContain("paie");
    expect(vues).toContain("alertes");
  });

  it("une section garde son aide tant qu'un de ses écrans reste au menu", () => {
    const vues = sectionsVisibles(() => true, (page) => PAIE.filter((p) => p !== "mesbulletins").includes(page)).map((s) => s.id);
    expect(vues).toContain("paie");
  });

  it("la page d'aide ne montre ni la fiche ni la section d'un écran masqué, et garde les autres", () => {
    const conteneur = document.createElement("div");
    document.body.appendChild(conteneur);
    const racine = createRoot(conteneur);
    act(() => {
      racine.render(
        <ModuleAccessProvider value={null} pagesMasquees={new Set([...PAIE, "superadmin"])}>
          <Help section={null} />
        </ModuleAccessProvider>,
      );
    });
    const fiches = [...conteneur.querySelectorAll("[data-testid^='help-fiche-']")].map((f) => f.getAttribute("data-testid")!.slice("help-fiche-".length));
    expect(fiches.filter((p) => PAIE.includes(p) || p === "superadmin")).toEqual([]);
    expect(fiches).toContain("alnouvelle");
    expect(conteneur.querySelector("#paie")).toBeNull();
    expect(conteneur.querySelector("[data-testid='help-sommaire-paie']")).toBeNull();
    expect(conteneur.querySelector("#alertes")).not.toBeNull();
    act(() => racine.unmount());
    conteneur.remove();
  });
});
