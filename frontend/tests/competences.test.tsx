/**
 * **Compétences et échéances** (addenda 8, M-04) : la fiche d'une personne dit ce qu'elle détient et ce que son corps
 * exige sans qu'elle l'ait ; on y entre, renouvelle ou retire une compétence ; le relevé liste ce qui échoit, par horizon,
 * et s'exporte en CSV.
 */
import { act } from "react";
import { createRoot, type Root } from "react-dom/client";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { CompetencesRessource, EcheancesCompetences, csv } from "../src/effectifs/planification/Competences";
import type { Ressource } from "../src/effectifs/planification/types";

(globalThis as unknown as { IS_REACT_ACT_ENVIRONMENT: boolean }).IS_REACT_ACT_ENVIRONMENT = true;

const api = vi.hoisted(() => {
  const etat: { routes: Record<string, unknown>; appels: Array<{ url: string; methode: string; corps: unknown }> } = { routes: {}, appels: [] };
  const apiFetch = (url: string, init: RequestInit = {}) => {
    const methode = init.method ?? "GET";
    etat.appels.push({ url, methode, corps: init.body ? JSON.parse(String(init.body)) : null });
    const cle = Object.keys(etat.routes).sort((a, b) => b.length - a.length).find((k) => `${methode} ${url}`.startsWith(k));
    const corps = cle ? etat.routes[cle] : { errors: ["refusé"] };
    const ok = cle !== undefined && !(corps as { errors?: unknown })?.errors;
    return Promise.resolve({ ok, status: ok ? 200 : 422, json: () => Promise.resolve(corps) } as unknown as Response);
  };
  const stable = { apiFetch, user: { id: "u-admin", role: "admin" } };
  return { etat, useAuth: () => stable };
});
vi.mock("../src/AuthContext", () => ({ useAuth: api.useAuth }));

const JULIE: Ressource = { id: "r-1", matricule: "P1", nom: "Julie Roy", uniteCode: "EST", corpsEmploiCode: "PARA", actif: true, anciennete: 3 } as Ressource;
const REFS = { "GET /api/effectifs/referentiels/competences": [{ code: "RCR", libelleFr: "Réanimation cardiorespiratoire" }, { code: "PERMIS", libelleFr: "Permis 4A" }],
  "GET /api/effectifs/milieux": [] };

let conteneur: HTMLDivElement;
let racine: Root;
const $ = (id: string) => conteneur.querySelector<HTMLElement>(`[data-testid="${id}"]`);
const attendre = async () => { for (let i = 0; i < 6; i++) await act(async () => { await Promise.resolve(); }); };
const saisir = async (id: string, valeur: string) => {
  const el = $(id) as HTMLInputElement | HTMLSelectElement;
  const proto = el instanceof HTMLSelectElement ? HTMLSelectElement.prototype : HTMLInputElement.prototype;
  await act(async () => {
    Object.getOwnPropertyDescriptor(proto, "value")!.set!.call(el, valeur);
    el.dispatchEvent(new Event(el instanceof HTMLSelectElement ? "change" : "input", { bubbles: true }));
  });
};

beforeEach(() => {
  api.etat.appels = [];
  conteneur = document.createElement("div");
  document.body.appendChild(conteneur);
  racine = createRoot(conteneur);
});
afterEach(() => { act(() => racine.unmount()); conteneur.remove(); });

describe("compétences d'une personne", () => {
  it("dit ce qui manque, liste ce qu'elle détient, entre une compétence et en retire une", async () => {
    api.etat.routes = {
      ...REFS,
      "GET /api/effectifs/planification/ressources/r-1/competences": { detenues: [{ code: "PERMIS", obtenueLe: "2020-05-01", echeance: "2026-11-01", valide: false }], manquantes: ["RCR"] },
      "PUT /api/effectifs/planification/ressources/r-1/competences/RCR": { code: "RCR" },
      "DELETE /api/effectifs/planification/ressources/r-1/competences/PERMIS": { retiree: "PERMIS" },
    };
    await act(async () => { racine.render(<CompetencesRessource ressource={JULIE} onFermer={() => {}} />); });
    await attendre();
    expect($("pla-competences-manquantes")!.textContent).toContain("Réanimation cardiorespiratoire");
    expect($("pla-competence-PERMIS")!.textContent).toContain("Échue");
    expect($("pla-competence-PERMIS")!.textContent).toContain("Permis 4A");

    await saisir("pla-competence-code", "RCR");
    await saisir("pla-competence-obtenue", "2026-01-10");
    await saisir("pla-competence-echeance", "2028-01-10");
    await act(async () => { ($("pla-competence-enregistrer") as HTMLButtonElement).form!.requestSubmit(); });
    await attendre();
    expect(api.etat.appels.find((a) => a.methode === "PUT")!.corps).toEqual({ obtenueLe: "2026-01-10", echeance: "2028-01-10" });
    expect($("pla-competences-succes")!.textContent).toContain("« Réanimation cardiorespiratoire » enregistrée");

    await act(async () => { $("pla-competence-retirer-PERMIS")!.click(); });
    await attendre();
    expect(api.etat.appels.some((a) => a.methode === "DELETE")).toBe(true);
    expect($("pla-competences-succes")!.textContent).toContain("« Permis 4A » retirée");
  });

  it("sans compétence, l'écran le dit ; un refus du serveur s'affiche", async () => {
    api.etat.routes = { ...REFS, "GET /api/effectifs/planification/ressources/r-1/competences": { detenues: [], manquantes: [] } };
    await act(async () => { racine.render(<CompetencesRessource ressource={JULIE} onFermer={() => {}} />); });
    await attendre();
    expect($("pla-competences-vide")).not.toBeNull();
    expect($("pla-competences-manquantes")).toBeNull();
    await saisir("pla-competence-code", "PERMIS");
    await saisir("pla-competence-obtenue", "2026-01-10");
    await act(async () => { ($("pla-competence-enregistrer") as HTMLButtonElement).form!.requestSubmit(); });
    await attendre();
    expect($("pla-competences-erreur")!.textContent).toContain("refusé");
  });
});

describe("relevé des échéances", () => {
  it("liste les échues et les à échoir, change d'horizon, s'exporte en CSV", async () => {
    api.etat.routes = {
      ...REFS,
      "GET /api/effectifs/planification/competences/echeances?dans=60": [
        { ressourceId: "r-1", ressourceNom: "Julie Roy", uniteCode: "EST", code: "PERMIS", echeance: "2026-11-20", etat: "echue", jours: -3 },
        { ressourceId: "r-2", ressourceNom: "Marc \"Mac\" Dubé", uniteCode: "EST", code: "RCR", echeance: "2026-12-30", etat: "a_echoir", jours: 37 }],
      "GET /api/effectifs/planification/competences/echeances?dans=30": [],
    };
    await act(async () => { racine.render(<EcheancesCompetences />); });
    await attendre();
    expect($("pla-echeance-r-1-PERMIS")!.textContent).toContain("Échue");
    expect($("pla-echeance-r-2-RCR")!.textContent).toContain("À échoir");
    const lien = $("pla-echeances-csv") as HTMLAnchorElement;
    const texte = decodeURIComponent(lien.href.split(",")[1]);
    expect(texte).toContain("\"Personne\";\"Unité\";\"Compétence\"");
    expect(texte).toContain("\"Marc \"\"Mac\"\" Dubé\";\"EST\";\"Réanimation cardiorespiratoire\";\"2026-12-30\";\"À échoir\";\"37\"");
    // Chaque colonne triable se trie : la plus pressante d'abord par jours, puis par nom, unité et échéance.
    for (const col of ["personne", "unite", "echeance", "jours"]) await act(async () => { $(`pla-echeances-tableau-tri-${col}`)!.click(); });
    expect($("pla-echeances-tableau")!.querySelectorAll("tbody tr")[0].getAttribute("data-testid")).toBe("pla-echeance-r-1-PERMIS");
    await saisir("pla-echeances-horizon", "30");
    await attendre();
    expect($("pla-echeances-vide")).not.toBeNull();
  });

  it("le CSV commence par la marque d'ordre des octets, pour qu'Excel lise les accents", () => {
    expect(decodeURIComponent(csv([], []).split(",")[1]).startsWith("﻿")).toBe(true);
  });
});
