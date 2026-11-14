/**
 * **Mon équipe** (comme dans Nexu VINA) — la ligne hiérarchique, sa réorganisation dans les bornes du
 * gestionnaire, les feuilles à approuver, et la suppléance pendant une absence ; contre une API simulée.
 */
import { act } from "react";
import { createRoot, type Root } from "react-dom/client";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import type { Equipe } from "../src/effectifs/equipe/MonEquipe";

(globalThis as unknown as { IS_REACT_ACT_ENVIRONMENT: boolean }).IS_REACT_ACT_ENVIRONMENT = true;

type Appel = { url: string; methode: string; corps: unknown };
const api = vi.hoisted(() => {
  const etat: { routes: Record<string, unknown>; statuts: Record<string, number>; appels: Appel[]; role: string } = { routes: {}, statuts: {}, appels: [], role: "manager" };
  const apiFetch = (url: string, init: RequestInit = {}) => {
    const methode = init.method ?? "GET";
    etat.appels.push({ url, methode, corps: init.body ? JSON.parse(String(init.body)) : null });
    const cle = Object.keys(etat.routes).sort((a, b) => b.length - a.length).find((k) => `${methode} ${url}`.startsWith(k));
    const corps = cle ? etat.routes[cle] : { errors: ["refusé"] };
    const statut = cle && etat.statuts[cle] ? etat.statuts[cle] : (cle !== undefined && !(corps as { errors?: unknown })?.errors ? 200 : 403);
    return Promise.resolve({ ok: statut < 300, status: statut, json: () => (corps === null ? Promise.reject(new Error("vide")) : Promise.resolve(corps)) } as Response);
  };
  return { etat, useAuth: () => ({ apiFetch, user: { id: "usr-marc", username: "marc", role: etat.role } }) };
});
vi.mock("../src/AuthContext", () => ({ useAuth: api.useAuth }));

const { default: MonEquipe, etatDelegation } = await import("../src/effectifs/equipe/MonEquipe");

const EQUIPE: Equipe = {
  moiId: "e-marc", moiNom: "Marc Lavoie",
  membres: [
    { id: "e-julie", nom: "Julie Tremblay", titre: "Paramédic", courriel: "julie@us.qc.ca", superieurId: "e-marc", superieurNom: "Marc Lavoie", niveau: 1 },
    { id: "e-luc", nom: "Luc Bouchard", titre: null, courriel: null, superieurId: "e-julie", superieurNom: "Julie Tremblay", niveau: 2 },
  ],
  sansSuperieur: [{ id: "e-lea", nom: "Léa Morin", titre: "Paramédic", courriel: null, superieurId: null, superieurNom: null, niveau: 0 }],
};
const FEUILLE = { id: "f1", ressourceId: "r1", nom: "Julie Tremblay", matricule: "P1", uniteCode: "EST", debut: "2031-03-10", fin: "2031-03-16", statut: "SOUMISE",
  palier: 1, paliersRequis: 1, soumiseLe: "x", approuveeLe: null };

let conteneur: HTMLDivElement;
let racine: Root;
const $ = (id: string) => document.querySelector<HTMLElement>(`[data-testid="${id}"]`);
const attendre = async (ms = 0) => { for (let i = 0; i < 6; i++) await act(async () => { await new Promise((r) => setTimeout(r, ms)); }); };
const cliquer = async (id: string) => { await act(async () => { $(id)!.click(); }); await attendre(); };
const saisir = async (id: string, valeur: string) => {
  const el = $(id) as HTMLInputElement;
  const proto = el instanceof HTMLSelectElement ? HTMLSelectElement.prototype : HTMLInputElement.prototype;
  await act(async () => {
    Object.getOwnPropertyDescriptor(proto, "value")!.set!.call(el, valeur);
    el.dispatchEvent(new Event(el instanceof HTMLSelectElement ? "change" : "input", { bubbles: true }));
  });
  await attendre();
};
const soumettre = async (libelle: string) => {
  await act(async () => { document.querySelector<HTMLFormElement>(`form[aria-label="${libelle}"]`)!.dispatchEvent(new Event("submit", { bubbles: true, cancelable: true })); });
  await attendre();
};
const rendre = async () => { await act(async () => { racine.render(<MonEquipe />); }); await attendre(); };
const envoisVers = (fragment: string) => api.etat.appels.filter((a) => a.methode !== "GET" && a.url.includes(fragment));

beforeEach(() => {
  api.etat.appels = []; api.etat.statuts = {}; api.etat.role = "manager";
  api.etat.routes = {
    "GET /api/personnel/equipe": EQUIPE,
    "GET /api/effectifs/temps/feuilles": [FEUILLE],
    "GET /api/approvals/delegations": [
      { id: "d1", toUserId: "usr-diane", toNom: "Diane Roy", fromDate: "2020-01-01", toDate: "2099-12-31" },
      { id: "d0", toUserId: "usr-paul", fromDate: "2020-01-01", toDate: "2020-01-31" }],
  };
  conteneur = document.createElement("div"); document.body.appendChild(conteneur);
  racine = createRoot(conteneur);
});
afterEach(() => { act(() => racine.unmount()); conteneur.remove(); });

describe("etatDelegation", () => {
  it("à venir, en cours, terminée — bornes comprises, sans date = ouverte", () => {
    expect([etatDelegation({ fromDate: "2031-03-10", toDate: null }, "2031-03-09"), etatDelegation({ fromDate: "2031-03-10", toDate: "2031-03-12" }, "2031-03-10"),
      etatDelegation({ fromDate: null, toDate: "2031-03-12" }, "2031-03-12"), etatDelegation({ fromDate: null, toDate: "2031-03-12" }, "2031-03-13"),
      etatDelegation({ fromDate: null, toDate: null }, "2031-03-13")]).toEqual(["à venir", "en cours", "en cours", "terminée", "en cours"]);
  });
});

describe("Mon équipe", () => {
  it("montre la ligne : direct puis indirect, le supérieur de chacun ; les feuilles à approuver ; mes suppléances et leur état", async () => {
    await rendre();
    expect($("meq-membre-e-julie")!.textContent).toContain("Julie Tremblayjulie@us.qc.caParamédicMarc LavoieDirect");
    expect($("meq-membre-e-luc")!.textContent).toContain("Luc Bouchard—Julie TremblayIndirect (niveau 2)");
    expect($("meq-e-luc-detacher")).toBeNull();
    expect($("meq-feuilles-table")!.textContent).toContain("Julie Tremblay (P1)2031-03-10 au 2031-03-16Soumise");
    expect($("meq-vers-console")!.getAttribute("href")).toContain("ustemps");
    expect($("meq-deleg-d1")!.textContent).toContain("Diane Roy2020-01-012099-12-31en cours");
    expect($("meq-deleg-d0")!.textContent).toContain("usr-paul");
    expect($("meq-deleg-d0-terminer")).toBeNull();
  });

  it("rattache une personne libre, change le supérieur d'un membre, détache un direct — l'équipe rendue par le serveur s'affiche", async () => {
    const apres = { ...EQUIPE, sansSuperieur: [] };
    api.etat.routes["PUT /api/personnel/equipe/e-lea/superieur"] = apres;
    api.etat.routes["PUT /api/personnel/equipe/e-luc/superieur"] = apres;
    api.etat.routes["PUT /api/personnel/equipe/e-julie/superieur"] = { ...apres, membres: [EQUIPE.membres[1]] };
    await rendre();
    await cliquer("meq-rattacher-ouvrir");
    await saisir("meq-rattacher-personne", "e-lea");
    await soumettre("Rattacher une personne");
    expect(envoisVers("e-lea")[0]).toMatchObject({ methode: "PUT", corps: { superieurId: "e-marc" } });
    expect($("meq-info")!.textContent).toBe("Léa Morin rejoint votre équipe.");
    expect(($("meq-rattacher-ouvrir") as HTMLButtonElement).disabled).toBe(true);

    await cliquer("meq-e-luc-changer");
    const choix = [...($("meq-nouveau") as HTMLSelectElement).options].map((o) => o.textContent);
    expect(choix).toEqual(["Marc Lavoie (moi)", "Julie Tremblay"]);
    await saisir("meq-nouveau", "e-marc");
    await soumettre("Changer de supérieur");
    expect(envoisVers("e-luc")[0].corps).toEqual({ superieurId: "e-marc" });
    expect($("meq-info")!.textContent).toBe("Luc Bouchard relève maintenant de Marc Lavoie (moi).");

    await cliquer("meq-e-julie-detacher");
    expect($("meq-detacher-modal")?.textContent ?? document.body.textContent).toContain("Julie Tremblay n'aura plus de supérieur");
    await cliquer("meq-detacher-confirm");
    expect(envoisVers("e-julie")[0].corps).toEqual({ superieurId: null });
    expect($("meq-membre-e-julie")).toBeNull();
  });

  it("un refus du serveur s'affiche et laisse le dialogue ouvert ; annuler ferme sans rien envoyer", async () => {
    await rendre();
    await cliquer("meq-e-luc-changer");
    await soumettre("Changer de supérieur");
    expect($("meq-erreur")!.textContent).toContain("refusé");
    expect($("meq-nouveau")).not.toBeNull();
    await act(async () => { document.dispatchEvent(new KeyboardEvent("keydown", { key: "Escape", bubbles: true })); });
    await attendre();
    await cliquer("meq-e-julie-detacher");
    await cliquer("meq-detacher-cancel");
    expect(envoisVers("superieur")).toHaveLength(1);
  });

  it("un employé voit son équipe sans les gestes ; sans module des feuilles, la section se tait ; sans fiche, l'écran le dit", async () => {
    api.etat.role = "employee";
    delete api.etat.routes["GET /api/effectifs/temps/feuilles"];
    await rendre();
    expect($("meq-membre-e-julie")).not.toBeNull();
    expect($("meq-rattacher-ouvrir")).toBeNull();
    expect($("meq-e-julie-changer")).toBeNull();
    expect($("meq-feuilles")).toBeNull();
    act(() => racine.unmount());
    racine = createRoot(conteneur);
    api.etat.routes["GET /api/personnel/equipe"] = { moiId: null, moiNom: null, membres: [], sansSuperieur: [] };
    api.etat.routes["GET /api/effectifs/temps/feuilles"] = [];
    api.etat.routes["GET /api/approvals/delegations"] = [];
    await rendre();
    expect($("meq-sans-fiche")).not.toBeNull();
    expect($("meq-feuilles-vide")).not.toBeNull();
    expect($("meq-delegations")).toBeNull();
  });

  it("l'équipe illisible : le message d'erreur, sans écran à moitié rendu", async () => {
    delete api.etat.routes["GET /api/personnel/equipe"];
    await rendre();
    expect($("meq-erreur")!.textContent).toContain("refusé");
    expect($("meq-membres")).toBeNull();
  });

  it("désigne un suppléant choisi par son nom, pour une période ; refusé, le motif ; au retour, met fin", async () => {
    api.etat.routes["GET /api/approvals/delegataires"] = [{ id: "usr-rachel", nom: "Rachel Côté", titre: "manager", courriel: null, statut: "actif" }];
    api.etat.routes["POST /api/approvals/delegations"] = { errors: ["delegate account is inactive"] };
    api.etat.statuts["POST /api/approvals/delegations"] = 400;
    await rendre();
    await cliquer("meq-suppleance-ouvrir");
    expect(($("meq-deleguer") as HTMLButtonElement).disabled).toBe(true);
    await saisir("meq-suppleant", "rac");
    await attendre(60);
    const option = $("meq-suppleant-option-usr-rachel")!;
    await act(async () => { option.dispatchEvent(new MouseEvent("mousedown", { bubbles: true })); });
    await saisir("meq-du", "2031-03-20");
    await saisir("meq-au", "2031-03-27");
    await soumettre("Désigner un suppléant");
    expect(envoisVers("/approvals/delegations")[0].corps).toEqual({ toUserId: "usr-rachel", fromDate: "2031-03-20", toDate: "2031-03-27" });
    expect($("meq-suppleance-erreur")!.textContent).toBe("delegate account is inactive");

    api.etat.routes["POST /api/approvals/delegations"] = { id: "d2" };
    api.etat.statuts["POST /api/approvals/delegations"] = 201;
    await soumettre("Désigner un suppléant");
    expect($("meq-suppleance-info")!.textContent).toContain("Suppléance enregistrée");
    expect($("meq-du")).toBeNull();

    api.etat.routes["DELETE /api/approvals/delegations/d1"] = null;
    api.etat.statuts["DELETE /api/approvals/delegations/d1"] = 204;
    await cliquer("meq-deleg-d1-terminer");
    await cliquer("meq-fin-confirm");
    expect(envoisVers("/delegations/d1")[0].methode).toBe("DELETE");
    expect($("meq-suppleance-info")!.textContent).toBe("Suppléance de Diane Roy terminée : vos feuilles vous reviennent.");
    await cliquer("meq-deleg-d1-terminer");
    await cliquer("meq-fin-cancel");
    expect(envoisVers("/delegations/d1")).toHaveLength(1);
  });

  it("un refus sans motif nommé dit son statut", async () => {
    api.etat.routes["POST /api/approvals/delegations"] = {};
    api.etat.statuts["POST /api/approvals/delegations"] = 500;
    api.etat.routes["GET /api/approvals/delegataires"] = [{ id: "usr-rachel", nom: "Rachel Côté", titre: "manager", courriel: null, statut: "actif" }];
    await rendre();
    await cliquer("meq-suppleance-ouvrir");
    await saisir("meq-suppleant", "rac");
    await attendre(60);
    await act(async () => { $("meq-suppleant-option-usr-rachel")!.dispatchEvent(new MouseEvent("mousedown", { bubbles: true })); });
    await soumettre("Désigner un suppléant");
    expect($("meq-suppleance-erreur")!.textContent).toBe("Erreur 500");
  });
});
