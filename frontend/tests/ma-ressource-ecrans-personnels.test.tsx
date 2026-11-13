/**
 * **Un écran personnel n'offre pas un geste impossible.**
 *
 * Défaut mesuré le 2 novembre 2026 sur `/admin/matemps` : un compte **sans dossier d'effectif**
 * lisait « Aucune feuille de temps · Préparez la feuille d'une période », pressait le bouton, et
 * recevait « « ressourceId » ou « uniteCode » : pour qui préparer les feuilles ? » — la question
 * d'un planificateur, posée à quelqu'un qui demandait la sienne.
 *
 * Deux portes, donc, et la seconde est la vraie :
 *
 * 1. l'écran **dit** ce qui manque, dans les mêmes mots que « Mon horaire » et « Mes échanges » ;
 * 2. l'écran **n'offre plus** le formulaire — un état vide qui invite au geste impossible coûte
 *    plus cher qu'un écran muet, parce qu'il fait croire à une panne.
 *
 * La même question se posait sur « Mon AGPH », où « Vous serez avisé à l'ouverture de la
 * prochaine campagne » promettait un courriel à quelqu'un qui ne figurera dans aucune campagne.
 */
import { act } from "react";
import { createRoot, type Root } from "react-dom/client";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import MaFeuilleTemps from "../src/MaFeuilleTemps";
import MonAgph from "../src/MonAgph";

(globalThis as unknown as { IS_REACT_ACT_ENVIRONMENT: boolean }).IS_REACT_ACT_ENVIRONMENT = true;

const auth = vi.hoisted(() => {
  const porteur: { appel: (url: string) => Promise<Response> } = {
    appel: () => Promise.reject(new Error("aucun appel branché")),
  };
  const stable = { apiFetch: (url: string) => porteur.appel(url), user: { id: "u1", role: "admin" } };
  return { porteur, useAuth: () => stable };
});
vi.mock("../src/AuthContext", () => ({ useAuth: auth.useAuth }));
vi.mock("../src/moduleAccess", () => ({ useModuleAllowed: () => () => true }));

const reponse = (corps: unknown) =>
  ({ ok: true, status: 200, json: () => Promise.resolve(corps) }) as unknown as Response;

const RESSOURCE = {
  id: "rs-1", matricule: "P1", nom: "Julie Roy", utilisateurId: "u1", uniteCode: "EST",
  corpsEmploiCode: "PARA", titreEmploiCode: null, statutEmploiCode: null, conventionCode: null,
  anciennete: 3, actif: true,
};

describe("les écrans personnels devant un compte sans dossier d'effectif", () => {
  let conteneur: HTMLDivElement;
  let racine: Root;
  beforeEach(() => {
    conteneur = document.createElement("div"); document.body.appendChild(conteneur);
    racine = createRoot(conteneur);
  });
  afterEach(() => { act(() => racine.unmount()); conteneur.remove(); });

  /** Branche les lectures : la ressource demandée, et une liste vide pour tout le reste. */
  const brancher = (ressource: unknown) => {
    const vus: string[] = [];
    auth.porteur.appel = (url: string) => {
      vus.push(url);
      if (url.includes("/ma-ressource")) return Promise.resolve(reponse({ ressource }));
      return Promise.resolve(reponse([]));
    };
    return vus;
  };

  const rendre = async (element: JSX.Element) => {
    await act(async () => { racine.render(element); });
    for (let i = 0; i < 4; i += 1) await act(async () => { await Promise.resolve(); });
  };

  const trouver = (testId: string) => conteneur.querySelector(`[data-testid="${testId}"]`);

  it("« Ma feuille de temps » dit ce qui manque et ne propose pas de préparer", async () => {
    brancher(null);
    await rendre(<MaFeuilleTemps />);
    expect(trouver("mft-sans-ressource")?.textContent)
      .toContain("Votre compte n'est relié à aucune ressource planifiable");
    // Le geste impossible a disparu — c'est lui qui produisait le message incompréhensible. On le
    // prouve sur l'**ouvreur** : depuis que la préparation est un dialogue (8 novembre 2026), ses
    // champs sont absents pour tout le monde tant qu'il est fermé — les constater absents ne
    // prouverait plus rien.
    expect(trouver("mft-preparation-ouvrir")).toBeNull();
    // Et l'état vide ne promet plus une préparation : il ne s'affiche pas du tout.
    expect(trouver("mft-vide")).toBeNull();
  });

  it("avec un dossier, le formulaire de préparation est là et l'état vide reste honnête", async () => {
    brancher(RESSOURCE);
    await rendre(<MaFeuilleTemps />);
    expect(trouver("mft-sans-ressource")).toBeNull();
    expect(trouver("mft-preparation-ouvrir")).not.toBeNull();
    expect(trouver("mft-vide")?.textContent).toContain("Préparez la feuille d'une période");
  });

  it("« Mon AGPH » ne promet pas un avis à qui ne figurera dans aucune campagne", async () => {
    brancher(null);
    await rendre(<MonAgph />);
    expect(trouver("agm-sans-ressource")?.textContent)
      .toContain("Votre compte n'est relié à aucune ressource planifiable");
    expect(trouver("agm-sans-campagne")).toBeNull();
  });

  it("avec un dossier, « Mon AGPH » retrouve son attente", async () => {
    brancher(RESSOURCE);
    await rendre(<MonAgph />);
    expect(trouver("agm-sans-ressource")).toBeNull();
    expect(trouver("agm-sans-campagne")?.textContent).toContain("Aucune campagne en cours pour vous");
  });

  it("la lecture de la ressource est posée une fois, et sur la route du socle", async () => {
    const vus = brancher(RESSOURCE);
    await rendre(<MaFeuilleTemps />);
    expect(vus.filter((u) => u.includes("/ma-ressource"))).toEqual(["/api/effectifs/ma-ressource"]);
  });

  it("une réponse d'une autre forme ne fait pas croire à un dossier", async () => {
    // Un refus traduit en tableau, un corps vide : sans objet lisible, il n'y a pas de dossier —
    // et l'écran retombe sur l'état vide plutôt que d'offrir un formulaire qui échouerait.
    auth.porteur.appel = (url: string) => Promise.resolve(reponse(url.includes("/ma-ressource") ? [] : []));
    await rendre(<MaFeuilleTemps />);
    expect(trouver("mft-sans-ressource")).not.toBeNull();
    expect(trouver("mft-preparation-ouvrir")).toBeNull();
  });
});
