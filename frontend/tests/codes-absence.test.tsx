/**
 * **Codes d'absence** : une page à leur nom, qui réutilise l'éditeur des référentiels verrouillé sur `codes_absence` —
 * sans le choix du type, ni la promotion de configuration, qui concernent tous les référentiels.
 */
import { act } from "react";
import { createRoot, type Root } from "react-dom/client";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

(globalThis as unknown as { IS_REACT_ACT_ENVIRONMENT: boolean }).IS_REACT_ACT_ENVIRONMENT = true;

const api = vi.hoisted(() => {
  const etat: { lus: string[] } = { lus: [] };
  const routes: Record<string, unknown> = {
    "/api/effectifs/referentiels/codes_absence": [{ id: "v1", type: "codes_absence", code: "MAL", libelleFr: "Maladie", libelleEn: null, comportement: "ABSENCE_PAYEE",
      ordre: 1, attributs: {}, effectifDepuis: "2026-01-01", effectifJusqua: null }],
    "/api/effectifs/referentiels": [{ type: "unites", libelle: "Unités", attributs: [] },
      { type: "codes_absence", libelle: "Codes d'absence", comportements: ["ABSENCE_PAYEE", "ABSENCE_NON_PAYEE"], attributs: [] }],
  };
  const apiFetch = (url: string) => {
    etat.lus.push(url);
    const cle = Object.keys(routes).sort((a, b) => b.length - a.length).find((k) => url.startsWith(k));
    return Promise.resolve({ ok: cle !== undefined, status: 200, json: () => Promise.resolve(cle ? routes[cle] : {}) } as Response);
  };
  return { etat, useAuth: () => ({ apiFetch, user: { id: "u1", username: "admin", role: "admin" } }) };
});
vi.mock("../src/AuthContext", () => ({ useAuth: api.useAuth }));

const { default: Uscodesabsence } = await import("../src/urgencessante/pages/uscodesabsence");
const { default: Referentiels } = await import("../src/effectifs/Referentiels");

let conteneur: HTMLDivElement;
let racine: Root;
const $ = (id: string) => conteneur.querySelector<HTMLElement>(`[data-testid="${id}"]`);
const attendre = async () => { for (let i = 0; i < 6; i++) await act(async () => { await Promise.resolve(); }); };

beforeEach(() => {
  api.etat.lus = [];
  conteneur = document.createElement("div"); document.body.appendChild(conteneur);
  racine = createRoot(conteneur);
});
afterEach(() => { act(() => racine.unmount()); conteneur.remove(); });

describe("Codes d'absence", () => {
  it("la page lit les seuls codes d'absence, sans choix du type, et offre d'en ajouter, remplacer et fermer", async () => {
    await act(async () => { racine.render(<Uscodesabsence />); });
    await attendre();
    expect($("uscodesabsence-titre")!.textContent).toContain("Codes d'absence");
    expect($("ref-type")).toBeNull();
    expect(api.etat.lus.some((u) => u.startsWith("/api/effectifs/referentiels/codes_absence?date="))).toBe(true);
    expect(api.etat.lus.some((u) => u.startsWith("/api/effectifs/referentiels/unites"))).toBe(false);
    expect($("ref-ligne-MAL")!.textContent).toContain("Maladie");
    expect($("ref-remplacer-MAL")).not.toBeNull();
    expect($("ref-fermer-MAL")).not.toBeNull();
    expect(($("ref-ajouter") as HTMLButtonElement).disabled).toBe(false);
  });

  it("sans type verrouillé, l'écran des référentiels garde son choix du type", async () => {
    await act(async () => { racine.render(<Referentiels />); });
    await attendre();
    expect($("ref-type")).not.toBeNull();
    expect(api.etat.lus.some((u) => u.startsWith("/api/effectifs/referentiels/unites?date="))).toBe(true);
  });
});
