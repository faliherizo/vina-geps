/**
 * « Relier mon compte » (ADR 0093) : la page dit ce que l'annuaire a vérifié sans révéler le courriel en clair,
 * propose les trois preuves avec des champs libellés, annonce les refus, et part vers l'application une fois reliée.
 */
import { act } from "react";
import { createRoot, type Root } from "react-dom/client";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import RelierCompte from "../src/RelierCompte";
import { versLiaison } from "../src/Login";

(globalThis as unknown as { IS_REACT_ACT_ENVIRONMENT: boolean }).IS_REACT_ACT_ENVIRONMENT = true;

const reponse = (corps: unknown, status = 200) => Promise.resolve({ ok: status < 300, status, json: () => Promise.resolve(corps) } as Response);
const ETAT = { indice: "j***@urgences-sante.qc.ca", candidat: true, demandeEnAttente: false, essaisRestants: 5 };

describe("RelierCompte", () => {
  let conteneur: HTMLDivElement;
  let racine: Root;
  let routes: Record<string, (init?: RequestInit) => Promise<Response>>;
  let assign: ReturnType<typeof vi.fn>;
  beforeEach(() => {
    conteneur = document.createElement("div");
    document.body.appendChild(conteneur);
    racine = createRoot(conteneur);
    routes = { "GET /api/auth/sso/liaison": () => reponse(ETAT) };
    vi.stubGlobal("fetch", vi.fn((chemin: string, init?: RequestInit) => {
      const cle = `${init?.method ?? "GET"} ${chemin}`;
      return (routes[cle] ?? (() => reponse({ errors: ["inattendu"] }, 500)))(init);
    }));
    assign = vi.fn();
    vi.stubGlobal("location", { ...window.location, assign });
  });
  afterEach(() => { act(() => racine.unmount()); conteneur.remove(); vi.unstubAllGlobals(); });
  const tid = (id: string) => document.querySelector(`[data-testid="${id}"]`) as HTMLElement | null;
  const attendre = () => act(async () => { await new Promise((r) => setTimeout(r, 5)); });
  const rendre = async () => { await act(async () => { racine.render(<RelierCompte />); }); await attendre(); };
  const saisir = async (id: string, valeur: string) => {
    const champ = tid(id) as HTMLInputElement;
    await act(async () => {
      Object.getOwnPropertyDescriptor(HTMLInputElement.prototype, "value")!.set!.call(champ, valeur);
      champ.dispatchEvent(new Event("input", { bubbles: true }));
    });
  };

  it("montre l'indice masqué, un seul h1, des champs libellés, et relie par le mot de passe", async () => {
    let envoi: unknown = null;
    routes["POST /api/auth/sso/liaison/mot-de-passe"] = (init) => { envoi = JSON.parse(String(init?.body)); return reponse({ retour: "https://app/", role: "manager" }); };
    await rendre();
    expect(tid("relier-indice")!.textContent).toContain("j***@urgences-sante.qc.ca");
    expect(tid("relier-indice")!.textContent).toContain("5 essai(s)");
    expect(document.querySelectorAll("h1")).toHaveLength(1);
    for (const id of ["relier-identifiant", "relier-mdp", "relier-invitation"]) {
      expect(document.querySelector(`label[for="${id}"]`)).not.toBeNull();
    }
    expect(tid("relier-mdp")!.getAttribute("autocomplete")).toBe("current-password");
    expect(tid("relier-sans-compte")).toBeNull();
    await saisir("relier-identifiant", "jean");
    await saisir("relier-mdp", "Passe!Jean2031");
    await act(async () => { tid("relier-mdp-envoyer")!.closest("form")!.requestSubmit(); });
    await attendre();
    expect(envoi).toEqual({ identifiant: "jean", motDePasse: "Passe!Jean2031" });
    expect(assign).toHaveBeenCalledWith("https://app/");
    expect(tid("relier-succes")!.getAttribute("role")).toBe("status");
    expect(tid("relier-continuer")!.getAttribute("href")).toBe("https://app/");
  });

  it("annonce un refus en alerte et recharge l'état ; trop d'essais ferme la liaison", async () => {
    routes["POST /api/auth/sso/liaison/invitation"] = () => reponse({ errors: ["la preuve ne permet pas de relier ce compte"] }, 403);
    await rendre();
    await saisir("relier-invitation", "https://app/admin/invitation/x");
    await act(async () => { tid("relier-invitation-envoyer")!.closest("form")!.requestSubmit(); });
    await attendre();
    expect(tid("relier-erreur")!.getAttribute("role")).toBe("alert");
    expect(tid("relier-erreur")!.textContent).toContain("la preuve ne permet pas de relier ce compte");
    routes["POST /api/auth/sso/liaison/mot-de-passe"] = () => reponse({ errors: ["trop d'essais"] }, 429);
    await saisir("relier-mdp", "x");
    await act(async () => { tid("relier-mdp-envoyer")!.closest("form")!.requestSubmit(); });
    await attendre();
    expect(tid("relier-expiree")!.textContent).toContain("trop d'essais");
    expect(tid("relier-vers-connexion")!.getAttribute("href")).toBe("/admin");
  });

  it("sans compte au courriel : le dit clairement, la demande est indisponible ; sans liaison : le dit", async () => {
    routes["GET /api/auth/sso/liaison"] = () => reponse({ ...ETAT, candidat: false });
    await rendre();
    expect(tid("relier-sans-compte")!.textContent).toContain("aucun n'a été créé");
    expect((tid("relier-demande") as HTMLButtonElement).disabled).toBe(true);
    act(() => racine.unmount());
    racine = createRoot(conteneur);
    routes["GET /api/auth/sso/liaison"] = () => reponse({}, 404);
    await rendre();
    expect(tid("relier-expiree")!.textContent).toContain("Aucune liaison en cours");
  });

  it("transmet la demande à l'administration, et dit qu'une demande attend déjà", async () => {
    routes["POST /api/auth/sso/liaison/demande"] = () => reponse({ demandeId: "d1", message: "Votre demande est transmise." }, 202);
    await rendre();
    await act(async () => { tid("relier-demande")!.click(); });
    await attendre();
    expect(tid("relier-demande-envoyee")!.textContent).toContain("Votre demande est transmise.");
    act(() => racine.unmount());
    racine = createRoot(conteneur);
    routes["GET /api/auth/sso/liaison"] = () => reponse({ ...ETAT, demandeEnAttente: true });
    await rendre();
    expect(tid("relier-demande-envoyee")!.textContent).toContain("attend la décision");
    act(() => racine.unmount());
    racine = createRoot(conteneur);
    routes["GET /api/auth/sso/liaison"] = () => reponse(ETAT);
    routes["POST /api/auth/sso/liaison/demande"] = () => reponse({ errors: ["le compte de l'application est désactivé"] }, 403);
    await rendre();
    await act(async () => { tid("relier-demande")!.click(); });
    await attendre();
    expect(tid("relier-erreur")!.textContent).toContain("désactivé");
  });

  it("la page de connexion suit la liaison rendue en JSON (LDAPS, simulé), et ignore le reste", async () => {
    expect(await versLiaison({ clone: () => ({ json: () => Promise.resolve({ liaisonRequise: true, retour: "/connexion/relier" }) }) } as unknown as Response)).toBe(true);
    expect(assign).toHaveBeenCalledWith("/connexion/relier");
    expect(await versLiaison({ clone: () => ({ json: () => Promise.resolve({ retour: "/admin" }) }) } as unknown as Response)).toBe(false);
    expect(await versLiaison({ clone: () => ({ json: () => Promise.reject(new Error("x")) }) } as unknown as Response)).toBe(false);
  });
});
