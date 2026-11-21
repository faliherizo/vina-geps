/**
 * **Seuils d'heures supplémentaires par convention** (AD-03 ; addenda 7, Q15) — l'écran envoie les trois
 * seuils d'une convention, un champ vide valant « celui de l'organisme », et dit ce qui a changé.
 */
import { act } from "react";
import { createRoot, type Root } from "react-dom/client";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

(globalThis as unknown as { IS_REACT_ACT_ENVIRONMENT: boolean }).IS_REACT_ACT_ENVIRONMENT = true;

type Appel = { url: string; methode: string; corps: unknown };
const api = vi.hoisted(() => {
  const etat: { routes: Record<string, unknown>; appels: Appel[] } = { routes: {}, appels: [] };
  const apiFetch = (url: string, init: RequestInit = {}) => {
    const methode = init.method ?? "GET";
    etat.appels.push({ url, methode, corps: init.body ? JSON.parse(String(init.body)) : null });
    const cle = Object.keys(etat.routes).sort((a, b) => b.length - a.length).find((k) => `${methode} ${url}`.startsWith(k));
    const corps = cle ? etat.routes[cle] : { errors: ["refusé"] };
    const ok = cle !== undefined && !(corps as { errors?: unknown })?.errors;
    return Promise.resolve({ ok, status: ok ? 200 : 400, json: () => Promise.resolve(corps) } as Response);
  };
  return { etat, useAuth: () => ({ apiFetch, user: { id: "u1", username: "admin", role: "admin" } }) };
});
vi.mock("../src/AuthContext", () => ({ useAuth: api.useAuth }));

const { default: SeuilsConventions, seuilSaisi, seuilEnMots } = await import("../src/effectifs/temps/SeuilsConventions");

let conteneur: HTMLDivElement;
let racine: Root;
const $ = (id: string) => conteneur.querySelector<HTMLElement>(`[data-testid="${id}"]`);
const attendre = async () => { for (let i = 0; i < 5; i++) await act(async () => { await Promise.resolve(); }); };
const saisir = async (id: string, valeur: string) => {
  const el = $(id) as HTMLInputElement;
  const proto = el instanceof HTMLSelectElement ? HTMLSelectElement.prototype : HTMLInputElement.prototype;
  await act(async () => {
    Object.getOwnPropertyDescriptor(proto, "value")!.set!.call(el, valeur);
    el.dispatchEvent(new Event(el instanceof HTMLSelectElement ? "change" : "input", { bubbles: true }));
  });
  await attendre();
};
const soumettre = async () => {
  await act(async () => { conteneur.querySelector<HTMLFormElement>("form")!.dispatchEvent(new Event("submit", { bubbles: true, cancelable: true })); });
  await attendre();
};
const SPCSN = { code: "SPCSN", libelleFr: "SP-CSN paramédics" };

beforeEach(() => {
  api.etat.appels = [];
  api.etat.routes = {
    "GET /api/effectifs/referentiels/conventions": [SPCSN],
    "GET /api/effectifs/milieux": [],
    "GET /api/effectifs/temps/seuils-conventions": [],
  };
  conteneur = document.createElement("div"); document.body.appendChild(conteneur);
  racine = createRoot(conteneur);
});
afterEach(() => { act(() => racine.unmount()); conteneur.remove(); });

describe("les fonctions pures", () => {
  it("un champ vide hérite de l'organisme, et se dit ainsi", () => {
    expect([seuilSaisi(""), seuilSaisi("  "), seuilSaisi("80")]).toEqual([null, null, 80]);
    expect([seuilEnMots(null), seuilEnMots(80)]).toEqual(["celui de l'organisme", "80 h"]);
  });
});

describe("l'écran", () => {
  it("envoie les trois seuils de la convention choisie, vide = null, puis montre le tableau et la confirmation", async () => {
    api.etat.routes["PUT /api/effectifs/temps/seuils-conventions/SPCSN"] = [
      { conventionCode: "SPCSN", seuilJourHeures: null, seuilSemaineHeures: null, seuilPeriodePaieHeures: 80 }];
    await act(async () => { racine.render(<SeuilsConventions />); });
    await attendre();
    expect(($("pla-tps-sc-enregistrer") as HTMLButtonElement).disabled).toBe(true);
    await saisir("pla-tps-sc-convention", "SPCSN");
    await saisir("pla-tps-sc-seuilPeriodePaieHeures", "80");
    await soumettre();
    const envoi = api.etat.appels.find((a) => a.methode === "PUT");
    expect(envoi?.corps).toEqual({ seuilJourHeures: null, seuilSemaineHeures: null, seuilPeriodePaieHeures: 80 });
    expect($("pla-tps-sc-info")?.textContent).toContain("SP-CSN paramédics enregistrés");
    expect($("pla-tps-sc-table")?.textContent).toContain("celui de l'organisme");
    expect($("pla-tps-sc-table")?.textContent).toContain("80 h");
    expect(conteneur.querySelector("caption")?.textContent).toBe("Conventions qui ont leurs propres seuils");
  });

  it("choisir une convention déjà réglée reprend ses valeurs ; tout vider la retire", async () => {
    api.etat.routes["GET /api/effectifs/temps/seuils-conventions"] = [
      { conventionCode: "SPCSN", seuilJourHeures: 9, seuilSemaineHeures: null, seuilPeriodePaieHeures: 80 }];
    api.etat.routes["PUT /api/effectifs/temps/seuils-conventions/SPCSN"] = [];
    await act(async () => { racine.render(<SeuilsConventions />); });
    await attendre();
    await saisir("pla-tps-sc-convention", "SPCSN");
    expect(($("pla-tps-sc-seuilJourHeures") as HTMLInputElement).value).toBe("9");
    expect(($("pla-tps-sc-seuilSemaineHeures") as HTMLInputElement).value).toBe("");
    await saisir("pla-tps-sc-seuilJourHeures", "");
    await saisir("pla-tps-sc-seuilPeriodePaieHeures", "");
    await soumettre();
    expect($("pla-tps-sc-info")?.textContent).toContain("reprend les seuils de l'organisme");
    expect($("pla-tps-sc-table")).toBeNull();
  });

  it("un refus du serveur s'annonce, sans confirmation", async () => {
    api.etat.routes["PUT /api/effectifs/temps/seuils-conventions/SPCSN"] = { errors: ["« seuilJourHeures » : de 1 à 24"] };
    await act(async () => { racine.render(<SeuilsConventions />); });
    await attendre();
    await saisir("pla-tps-sc-convention", "SPCSN");
    await saisir("pla-tps-sc-seuilJourHeures", "30");
    await soumettre();
    expect($("pla-tps-sc-erreur")?.textContent).toContain("de 1 à 24");
    expect($("pla-tps-sc-info")).toBeNull();
  });
});
