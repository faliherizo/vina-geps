/**
 * **Proposition d'optimisation du budget AGPH** (AD-04 ; addenda 7, Q11) — l'écran demande la proposition, en lit
 * le résumé à voix haute (`role="status"`) et montre les postes retenus et écartés dans deux tableaux légendés.
 */
import { act } from "react";
import { createRoot, type Root } from "react-dom/client";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

(globalThis as unknown as { IS_REACT_ACT_ENVIRONMENT: boolean }).IS_REACT_ACT_ENVIRONMENT = true;

const api = vi.hoisted(() => {
  const etat: { routes: Record<string, unknown>; appels: string[] } = { routes: {}, appels: [] };
  const apiFetch = (url: string, init: RequestInit = {}) => {
    const methode = init.method ?? "GET";
    etat.appels.push(`${methode} ${url}`);
    const corps = etat.routes[`${methode} ${url}`] ?? { errors: ["refusé"] };
    const ok = !(corps as { errors?: unknown }).errors;
    return Promise.resolve({ ok, status: ok ? 200 : 409, json: () => Promise.resolve(corps) } as Response);
  };
  return { etat, useAuth: () => ({ apiFetch, user: { id: "u1", username: "admin", role: "admin" } }) };
});
vi.mock("../src/AuthContext", () => ({ useAuth: api.useAuth }));

const { default: OptimisationBudget, resumeProposition } = await import("../src/effectifs/agph/OptimisationBudget");

let conteneur: HTMLDivElement;
let racine: Root;
const $ = (id: string) => conteneur.querySelector<HTMLElement>(`[data-testid="${id}"]`);
const attendre = async () => { for (let i = 0; i < 5; i++) await act(async () => { await Promise.resolve(); }); };
const URL = "GET /api/effectifs/agph/campagnes/c1/optimisation";
const PROPOSITION = {
  budgetHeures: 80, totalHeures: 77.5, resteHeures: 2.5,
  retenus: [{ id: "p1", code: "EST-1", libelle: "Jour", heuresSemaine: 38.75, candidats: 2, motif: null, motifLibelle: null },
    { id: "p2", code: "EST-2", libelle: "Soir", heuresSemaine: 38.75, candidats: 1, motif: null, motifLibelle: null }],
  ecartes: [{ id: "p3", code: "EST-3", libelle: "Nuit", heuresSemaine: 39.38, candidats: 0, motif: "AUCUNE_DISPONIBILITE",
    motifLibelle: "aucune semaine type déposée ne couvre tous ses quarts" }],
};

beforeEach(() => {
  api.etat.appels = [];
  api.etat.routes = {};
  conteneur = document.createElement("div"); document.body.appendChild(conteneur);
  racine = createRoot(conteneur);
});
afterEach(() => { act(() => racine.unmount()); conteneur.remove(); });

describe("la proposition", () => {
  it("se résume en une phrase : postes, total, budget et reste", () => {
    expect(resumeProposition(PROPOSITION)).toBe("2 poste(s) retenu(s) pour 77,5 h par semaine, sur un budget de 80 h : il reste 2,5 h.");
  });

  it("ne se calcule qu'à la demande, puis montre retenus et écartés avec leur motif", async () => {
    api.etat.routes[URL] = PROPOSITION;
    await act(async () => { racine.render(<OptimisationBudget campagneId="c1" />); });
    await attendre();
    expect(api.etat.appels).not.toContain(URL);
    expect($("pla-agph-optim-resume")).toBeNull();
    await act(async () => { $("pla-agph-optim-proposer")!.click(); });
    await attendre();
    expect(api.etat.appels).toContain(URL);
    expect($("pla-agph-optim-resume")?.getAttribute("role")).toBe("status");
    expect($("pla-agph-optim-retenus")?.querySelector("caption")?.textContent).toBe("Postes proposés");
    expect($("pla-agph-optim-retenus")?.querySelectorAll("tbody tr")).toHaveLength(2);
    expect($("pla-agph-optim-ecartes")?.textContent).toContain("aucune semaine type déposée ne couvre tous ses quarts");
    expect([...conteneur.querySelectorAll("th")].every((th) => th.getAttribute("scope") === "col" || th.getAttribute("scope") === "row")).toBe(true);
  });

  it("sans budget, le refus du serveur s'annonce et aucun tableau ne paraît", async () => {
    api.etat.routes[URL] = { errors: ["fixez d'abord le budget d'heures de la campagne"] };
    await act(async () => { racine.render(<OptimisationBudget campagneId="c1" />); });
    await attendre();
    await act(async () => { $("pla-agph-optim-proposer")!.click(); });
    await attendre();
    expect($("pla-agph-optim-erreur")?.textContent).toContain("fixez d'abord le budget");
    expect($("pla-agph-optim-retenus")).toBeNull();
  });
});
