/**
 * **Avant de commencer : ce qu'il faut configurer avant qu'un écran serve.**
 *
 * Défaut constaté le 22 novembre 2026 sur `/admin/mesabsences` : déclarer une absence répondait « votre
 * compte n'est relié à aucune ressource planifiable : demandez-le à votre planificateur », et la fiche
 * d'aide de l'écran ne disait nulle part qu'une ressource, reliée au compte, devait exister d'abord. Trois
 * portes, donc :
 *
 * 1. chaque fiche porte ses préalables, et la page d'aide les rend avant « Comment faire » ;
 * 2. le refus garde son message, et gagne le lien vers ce qui manque ;
 * 3. le lien se fait vraiment à l'écran : « Ressources » propose le compte, et ne l'efface plus à la
 *    première correction (la modification remplace la ressource entière).
 */
import { act } from "react";
import { createRoot, type Root } from "react-dom/client";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import Help from "../src/Help";
import { FICHES } from "../src/aide/fichesEcrans";
import FormulaireAbsence from "../src/absences/FormulaireAbsence";
import { SansDossierEffectif } from "../src/effectifs/maRessource";
import Ressources from "../src/effectifs/planification/Ressources";

(globalThis as unknown as { IS_REACT_ACT_ENVIRONMENT: boolean }).IS_REACT_ACT_ENVIRONMENT = true;

const auth = vi.hoisted(() => {
  const porteur: { appel: (url: string, init?: RequestInit) => Promise<Response> } = {
    appel: () => Promise.reject(new Error("aucun appel branché")),
  };
  const stable = { apiFetch: (url: string, init?: RequestInit) => porteur.appel(url, init), user: { id: "u-admin", role: "admin" } };
  return { porteur, useAuth: () => stable };
});
vi.mock("../src/AuthContext", () => ({ useAuth: auth.useAuth }));

const reponse = (corps: unknown, ok = true, status = ok ? 200 : 400) => ({ ok, status, json: () => Promise.resolve(corps) }) as unknown as Response;

describe("les préalables de chaque fiche", () => {
  const fiches = Object.entries(FICHES);

  it("chaque fiche dit ce qu'il faut mettre en place avant de commencer", () => {
    const sansPrealable = fiches.filter(([, f]) => f.prealables.length === 0 || f.prealables.some((x) => x.texte.trim().length < 20)).map(([p]) => p);
    expect({ fichesSansPrealable: sansPrealable }).toEqual({ fichesSansPrealable: [] });
  });

  it("un préalable qui nomme un écran nomme un écran qui existe", () => {
    const inconnus = fiches.flatMap(([p, f]) => f.prealables.filter((x) => x.ecran && !(x.ecran in FICHES)).map((x) => `${p} → ${x.ecran}`));
    expect(inconnus).toEqual([]);
  });

  it("Mes absences dit que la ressource doit être reliée au compte, où cela se fait, et ce que dit le refus", () => {
    const lien = FICHES.mesabsences.prealables.find((x) => x.ecran === "usressources");
    expect(lien?.texte).toContain("« Compte utilisateur »");
    expect(lien?.texte).toContain("votre compte n'est relié à aucune ressource planifiable");
    const textes = FICHES.mesabsences.prealables.map((x) => x.texte).join("\n");
    expect(textes).toContain("« effectifs_absences »");
    expect(textes).toContain("codes d'absence");
  });
});

describe("le rendu", () => {
  let conteneur: HTMLDivElement;
  let racine: Root;
  beforeEach(() => { conteneur = document.createElement("div"); document.body.appendChild(conteneur); racine = createRoot(conteneur); });
  afterEach(() => { act(() => racine.unmount()); conteneur.remove(); });
  const tid = (id: string) => conteneur.querySelector<HTMLElement>(`[data-testid="${id}"]`);

  it("la fiche rend « Avant de commencer » en liste ordonnée, sous son titre, avant « Comment faire », avec le lien vers l'écran", () => {
    act(() => { racine.render(<Help section="fiche-mesabsences" />); });
    const fiche = tid("help-fiche-mesabsences")!;
    const titres = [...fiche.querySelectorAll("h3, h4")].map((h) => `${h.tagName}:${h.textContent}`);
    expect(titres.slice(0, 3)).toEqual(["H3:Mes absences d'effectifs", "H4:Avant de commencer", "H4:Comment faire"]);
    const liste = tid("aide-prealables-mesabsences")!;
    expect(liste.tagName).toBe("OL");
    expect(liste.querySelectorAll(":scope > li")).toHaveLength(FICHES.mesabsences.prealables.length);
    const rang = FICHES.mesabsences.prealables.findIndex((x) => x.ecran === "usressources") + 1;
    const lien = tid(`aide-prealable-mesabsences-${rang}-lien`) as HTMLAnchorElement;
    expect(lien.getAttribute("href")).toBe("/usressources");
    expect(lien.textContent).toBe("Ouvrir « Ressources »");
  });

  it("le refus « aucune ressource planifiable » garde son message et mène à la fiche", async () => {
    const message = "votre compte n'est relié à aucune ressource planifiable : demandez-le à votre planificateur";
    const apiFetch = vi.fn(async (chemin: string, init?: RequestInit) => {
      if (init?.method === "POST") return reponse({ errors: [message] }, false, 404);
      if (chemin.includes("codes_absence")) return reponse([{ code: "MAL", libelleFr: "Maladie", attributs: {} }]);
      return reponse([]);
    });
    await act(async () => { racine.render(<FormulaireAbsence apiFetch={apiFetch} estEmploye onDeclaree={() => undefined} />); });
    const code = tid("abs-eff-code") as HTMLSelectElement;
    act(() => {
      Object.getOwnPropertyDescriptor(HTMLSelectElement.prototype, "value")!.set!.call(code, "MAL");
      code.dispatchEvent(new Event("change", { bubbles: true }));
    });
    await act(async () => { tid("abs-submit")!.click(); });
    expect(tid("abs-form-error")!.textContent).toContain(message);
    const lien = tid("abs-form-error-prealables") as HTMLAnchorElement;
    expect(lien.getAttribute("href")).toBe("/help?section=fiche-mesabsences");
    expect(lien.textContent).toBe("Voir ce qu'il faut configurer avant d'utiliser l'écran « Mes absences d'effectifs »");
  });

  it("une autre erreur ne porte pas ce lien", async () => {
    const apiFetch = vi.fn(async (chemin: string, init?: RequestInit) => {
      if (init?.method === "POST") return reponse({ errors: ["période close"] }, false, 422);
      if (chemin.includes("codes_absence")) return reponse([{ code: "MAL", libelleFr: "Maladie", attributs: {} }]);
      return reponse([]);
    });
    await act(async () => { racine.render(<FormulaireAbsence apiFetch={apiFetch} estEmploye onDeclaree={() => undefined} />); });
    const code = tid("abs-eff-code") as HTMLSelectElement;
    act(() => {
      Object.getOwnPropertyDescriptor(HTMLSelectElement.prototype, "value")!.set!.call(code, "MAL");
      code.dispatchEvent(new Event("change", { bubbles: true }));
    });
    await act(async () => { tid("abs-submit")!.click(); });
    expect(tid("abs-form-error")!.textContent).toBe("période close");
    expect(tid("abs-form-error-prealables")).toBeNull();
  });

  it("l'état « sans dossier d'effectif » mène à la fiche de son écran", () => {
    act(() => { racine.render(<SansDossierEffectif testId="mft-sans-ressource" page="matemps" />); });
    expect(tid("mft-sans-ressource-prealables")!.getAttribute("href")).toBe("/help?section=fiche-matemps");
  });
});

describe("Ressources : le compte relié se choisit, et se garde", () => {
  let conteneur: HTMLDivElement;
  let racine: Root;
  beforeEach(() => { conteneur = document.createElement("div"); document.body.appendChild(conteneur); racine = createRoot(conteneur); });
  afterEach(() => { act(() => racine.unmount()); conteneur.remove(); });
  const tid = (id: string) => conteneur.querySelector<HTMLElement>(`[data-testid="${id}"]`);

  const RESSOURCE = {
    id: "rs-1", matricule: "P1", nom: "Julie Roy", utilisateurId: "u-julie", uniteCode: "EST", corpsEmploiCode: "PARA",
    titreEmploiCode: null, statutEmploiCode: null, conventionCode: null, anciennete: 3, actif: true,
  };

  /** Les lectures de l'écran ; `comptes` à `null` : la liste des comptes est refusée (rôle autre que l'administration). */
  function brancher(comptes: unknown[] | null) {
    const envois: Array<{ chemin: string; methode?: string; corps: Record<string, unknown> }> = [];
    auth.porteur.appel = async (url, init) => {
      if (init?.method === "PUT" || init?.method === "POST") {
        const corps = JSON.parse(String(init.body)) as Record<string, unknown>;
        envois.push({ chemin: url, methode: init.method, corps });
        return reponse({ ...RESSOURCE, ...corps });
      }
      if (url === "/api/auth/users") return comptes ? reponse(comptes) : reponse({ errors: ["réservé à l'administration"] }, false, 403);
      if (url.endsWith("/referentiels/unites")) return reponse([{ code: "EST", libelleFr: "Opérations Est" }]);
      if (url.endsWith("/referentiels/corps_emploi")) return reponse([{ code: "PARA", libelleFr: "Paramédics" }]);
      if (url.endsWith("/planification/ressources")) return reponse([RESSOURCE]);
      return reponse([]);
    };
    return envois;
  }

  async function modifierEtEnregistrer() {
    await act(async () => { racine.render(<Ressources />); });
    await act(async () => { tid("pla-ressource-modifier-P1")!.click(); });
  }

  it("l'administration choisit le compte parmi ceux de l'organisme ; la modification le renvoie", async () => {
    const envois = brancher([{ id: "u-julie", username: "julie.roy", role: "employee" }, { id: "u-sa", username: "superadmin", role: "superadmin" }]);
    await modifierEtEnregistrer();
    const champ = tid("pla-res-utilisateur") as HTMLSelectElement;
    expect(champ.value).toBe("u-julie");
    expect([...champ.options].map((o) => o.textContent)).toEqual(["Aucun", "julie.roy"]);
    expect(conteneur.querySelector('label[for="pla-res-utilisateur"]')?.textContent).toBe("Compte utilisateur (facultatif)");
    await act(async () => { tid("pla-res-enregistrer")!.click(); });
    expect(envois).toEqual([expect.objectContaining({ chemin: "/api/effectifs/planification/ressources/rs-1", methode: "PUT",
      corps: expect.objectContaining({ utilisateurId: "u-julie" }) })]);
  });

  it("sans la liste des comptes, le lien existant est lu et gardé : corriger un nom ne délie plus la personne", async () => {
    const envois = brancher(null);
    await modifierEtEnregistrer();
    expect(tid("pla-res-utilisateur")).toBeNull();
    expect(tid("pla-res-utilisateur-lecture")!.textContent).toBe("Compte utilisateur : relié.");
    expect(tid("pla-ressources-erreur")).toBeNull();
    await act(async () => { tid("pla-res-enregistrer")!.click(); });
    expect(envois[0]?.corps.utilisateurId).toBe("u-julie");
  });
});
