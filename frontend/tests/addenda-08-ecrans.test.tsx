/**
 * **Les écrans de l'addenda 8** (26-015-DO) : demandes opérationnelles et liste de temps supplémentaire (M-08), choix
 * annuel des congés (M-02), autres unités d'une personne (M-09), journal des consultations (M-10). Chaque écran lit,
 * agit, et dit ce qui s'est passé ; un refus du serveur s'affiche.
 */
import { act } from "react";
import { createRoot, type Root } from "react-dom/client";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { DemandesOperationnelles, MesDemandes } from "../src/effectifs/demandes/Demandes";
import { CampagnesConges, MesCongesAnnuels } from "../src/effectifs/conges/CongesAnnuels";
import { RattachementsRessource } from "../src/effectifs/planification/Rattachements";
import ReglageConsultations from "../src/effectifs/ReglageConsultations";
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

let conteneur: HTMLDivElement;
let racine: Root;
const $ = (id: string) => conteneur.querySelector<HTMLElement>(`[data-testid="${id}"]`);
const attendre = async () => { for (let i = 0; i < 6; i++) await act(async () => { await Promise.resolve(); }); };
const rendre = async (el: JSX.Element) => { await act(async () => { racine.render(el); }); await attendre(); };
const cliquer = async (id: string) => { await act(async () => { $(id)!.click(); }); await attendre(); };
const soumettre = async (id: string) => { await act(async () => { ($(id) as HTMLButtonElement).form!.requestSubmit(); }); await attendre(); };
const saisir = async (id: string, valeur: string) => {
  const el = $(id) as HTMLInputElement | HTMLSelectElement | HTMLTextAreaElement;
  const proto = el instanceof HTMLSelectElement ? HTMLSelectElement.prototype : el instanceof HTMLTextAreaElement ? HTMLTextAreaElement.prototype : HTMLInputElement.prototype;
  await act(async () => {
    Object.getOwnPropertyDescriptor(proto, "value")!.set!.call(el, valeur);
    el.dispatchEvent(new Event(el instanceof HTMLSelectElement ? "change" : "input", { bubbles: true }));
  });
};
const envoi = (fragment: string, methode = "POST") => api.etat.appels.filter((a) => a.methode === methode && a.url.includes(fragment)).at(-1)?.corps;

beforeEach(() => {
  api.etat.appels = [];
  conteneur = document.createElement("div");
  document.body.appendChild(conteneur);
  racine = createRoot(conteneur);
});
afterEach(() => { act(() => racine.unmount()); conteneur.remove(); });

const D = "/api/effectifs/planification/demandes";
const TYPES = [{ code: "LISTE-TS", libelleFr: "Liste de temps supplémentaire", comportement: "LISTE_TEMPS_SUPPLEMENTAIRE" }, { code: "FORM", libelleFr: "Formation", comportement: "FORMATION" }];
const DEMANDE = { id: "d1", ressourceId: "r1", ressourceNom: "Julie Roy", uniteCode: "EST", typeCode: "LISTE-TS", du: "2027-02-01", au: "2027-02-28",
  commentaire: "Soirs", statut: "EN_ATTENTE", motifDecision: null };

describe("demandes opérationnelles (M-08)", () => {
  it("la personne envoie une demande, la voit en attente, l'annule", async () => {
    api.etat.routes = { [`GET ${D}/types`]: TYPES, [`GET ${D}/miennes`]: [DEMANDE], [`POST ${D}/d1/annuler`]: { ...DEMANDE, statut: "ANNULEE" }, [`POST ${D}`]: DEMANDE };
    await rendre(<MesDemandes />);
    expect($("dem-d1")!.textContent).toContain("Liste de temps supplémentaire");
    expect($("dem-d1")!.textContent).toContain("En attente");
    await saisir("dem-type", "FORM");
    await saisir("dem-du", "2027-03-01");
    await saisir("dem-au", "2027-03-02");
    await saisir("dem-commentaire", "Cours RCR");
    await soumettre("dem-envoyer");
    expect(envoi(D)).toEqual({ typeCode: "FORM", du: "2027-03-01", au: "2027-03-02", commentaire: "Cours RCR" });
    expect($("dem-succes")!.textContent).toContain("« Formation » envoyée");
    await cliquer("dem-d1-annuler");
    expect($("dem-succes")!.textContent).toContain("Demande du 2027-02-01 annulée");
  });

  it("sans demande, l'écran le dit ; un refus s'affiche", async () => {
    api.etat.routes = { [`GET ${D}/types`]: TYPES, [`GET ${D}/miennes`]: [] };
    await rendre(<MesDemandes />);
    expect($("dem-vide")).not.toBeNull();
    await saisir("dem-type", "FORM");
    await saisir("dem-du", "2027-03-01");
    await saisir("dem-au", "2027-03-02");
    await soumettre("dem-envoyer");
    expect($("dem-erreur")!.textContent).toContain("refusé");
  });

  it("l'encadrement accepte, refuse en motivant, filtre, et lit la liste de temps supplémentaire du jour", async () => {
    api.etat.routes = {
      [`GET ${D}/types`]: TYPES, [`GET ${D}?statut=EN_ATTENTE`]: [DEMANDE], [`GET ${D}?statut=REFUSEE`]: [{ ...DEMANDE, statut: "REFUSEE", motifDecision: "Quota atteint" }],
      [`POST ${D}/d1/decision`]: { ...DEMANDE, statut: "ACCEPTEE" },
      [`GET ${D}/liste-ts`]: [{ ressourceId: "r2", ressourceNom: "Marc Dubé", uniteCode: "EST", anciennete: 9, du: "2027-02-01", au: "2027-02-28" }],
    };
    await rendre(<DemandesOperationnelles />);
    expect($("dem-ts-r2")!.textContent).toContain("Marc Dubé");
    await saisir("dem-motif-d1", "Bienvenue");
    await cliquer("dem-accepter-d1");
    expect(envoi("/d1/decision")).toEqual({ decision: "ACCEPTER", motif: "Bienvenue" });
    expect($("dem-gestion-succes")!.textContent).toContain("Demande de Julie Roy acceptée.");
    await cliquer("dem-refuser-d1");
    expect(envoi("/d1/decision")).toEqual({ decision: "REFUSER", motif: "Bienvenue" });
    expect($("dem-gestion-succes")!.textContent).toContain("refusée");
    for (const col of ["personne", "periode"]) await act(async () => { $(`dem-gestion-table-tri-${col}`)!.click(); });
    await saisir("dem-filtre", "REFUSEE");
    expect($("dem-gestion-d1")!.textContent).toContain("Quota atteint");
    api.etat.routes[`GET ${D}/liste-ts`] = [];
    await saisir("dem-ts-date", "2027-03-15");
    expect(api.etat.appels.some((a) => a.url === `${D}/liste-ts?date=2027-03-15`)).toBe(true);
    expect($("dem-ts-vide")).not.toBeNull();
    await saisir("dem-filtre", "");
    expect($("dem-gestion-erreur")!.textContent).toContain("refusé");
    await saisir("dem-ts-date", "");
  });
});

const C = "/api/effectifs/absences/conges";
const CAMPAGNE = { id: "c1", nom: "Été 2027", uniteCode: "EST", corpsEmploiCode: "PARA", annee: 2027, du: "2027-07-05", au: "2027-07-25", codeAbsence: "VAC", plafondParSemaine: 1, etat: "OUVERTE" };

describe("choix annuel des congés (M-02)", () => {
  it("la personne classe ses semaines dans l'ordre et lit l'octroi", async () => {
    api.etat.routes = {
      [`GET ${C}/miennes`]: [{ campagne: CAMPAGNE, semaines: ["2027-07-05", "2027-07-12", "2027-07-19"], choix: [{ semaine: "2027-07-19", rang: 1, statut: "REFUSE", motif: "plafond atteint" }] }],
      [`PUT ${C}/c1/choix`]: [],
    };
    await rendre(<MesCongesAnnuels />);
    expect(conteneur.textContent).toContain("Refusée : plafond atteint");
    await cliquer("cng-semaine-2027-07-19");
    await cliquer("cng-semaine-2027-07-12");
    await cliquer("cng-semaine-2027-07-05");
    expect(conteneur.textContent).toContain("1ᵉ choix — Semaine du 2027-07-12 au 2027-07-18");
    await soumettre("cng-enregistrer-c1");
    expect(envoi("/c1/choix", "PUT")).toEqual({ semaines: ["2027-07-12", "2027-07-05"] });
    expect($("cng-succes")!.textContent).toContain("Vos 2 choix sont enregistrés pour « Été 2027 »");
  });

  it("sans campagne, l'écran le dit ; fermée, rien ne se modifie ; un refus s'affiche", async () => {
    api.etat.routes = { [`GET ${C}/miennes`]: [] };
    await rendre(<MesCongesAnnuels />);
    expect($("cng-vide")).not.toBeNull();
    api.etat.routes = { [`GET ${C}/miennes`]: [{ campagne: { ...CAMPAGNE, etat: "FERMEE" }, semaines: ["2027-07-05"], choix: [] }] };
    await act(async () => { racine.unmount(); });
    racine = createRoot(conteneur);
    await rendre(<MesCongesAnnuels />);
    expect($("cng-enregistrer-c1")).toBeNull();
    expect(($("cng-semaine-2027-07-05") as HTMLInputElement).closest("fieldset")!.disabled).toBe(true);
  });

  it("l'encadrement crée, ouvre, ferme, octroie et lit les résultats", async () => {
    api.etat.routes = {
      [`GET ${C}`]: [{ ...CAMPAGNE, etat: "BROUILLON" }, { ...CAMPAGNE, id: "c2", etat: "OUVERTE" }, { ...CAMPAGNE, id: "c3", etat: "FERMEE" }],
      "GET /api/effectifs/referentiels/unites": [{ code: "EST", libelleFr: "Est" }], "GET /api/effectifs/referentiels/corps_emploi": [{ code: "PARA", libelleFr: "Paramédics" }],
      "GET /api/effectifs/referentiels/codes_absence": [{ code: "VAC", libelleFr: "Vacances" }], "GET /api/effectifs/milieux": [],
      [`POST ${C}/c1/etat`]: { ...CAMPAGNE, etat: "OUVERTE" }, [`POST ${C}/c2/etat`]: { ...CAMPAGNE, etat: "FERMEE" }, [`POST ${C}/c3/octroyer`]: { octroyes: 1 },
      [`GET ${C}/c3/resultats`]: [{ ressourceNom: "Marc", anciennete: 9, semaine: "2027-07-05", rang: 1, statut: "OCTROYE", motif: null }],
      [`POST ${C}`]: { ...CAMPAGNE, id: "c9", etat: "BROUILLON" },
    };
    await rendre(<CampagnesConges />);
    await cliquer("cng-ouvrir-c1");
    expect(envoi("/c1/etat")).toEqual({ etat: "OUVERTE" });
    await cliquer("cng-fermer-c2");
    expect(envoi("/c2/etat")).toEqual({ etat: "FERMEE" });
    await cliquer("cng-octroyer-c3");
    expect($("cng-gestion-succes")!.textContent).toContain("est octroyée");
    await cliquer("cng-resultats-c3");
    expect($("cng-resultats")!.textContent).toContain("Marc");
    expect($("cng-resultats")!.textContent).toContain("Octroyée");
    await saisir("cng-f-nom", "Hiver 2027");
    await saisir("cng-f-uniteCode", "EST");
    await saisir("cng-f-corpsEmploiCode", "PARA");
    await saisir("cng-f-codeAbsence", "VAC");
    await saisir("cng-f-annee", "2027");
    await saisir("cng-f-du", "2027-01-04");
    await saisir("cng-f-au", "2027-03-28");
    await saisir("cng-f-plafond", "3");
    await soumettre("cng-creer");
    expect(envoi(C)).toEqual({ nom: "Hiver 2027", uniteCode: "EST", corpsEmploiCode: "PARA", annee: 2027, du: "2027-01-04", au: "2027-03-28", codeAbsence: "VAC", plafondParSemaine: 3 });
    expect($("cng-gestion-succes")!.textContent).toContain("Campagne « Hiver 2027 » créée.");
  });

  it("sans campagne ni choix, la console le dit ; un refus s'affiche", async () => {
    api.etat.routes = { [`GET ${C}`]: [{ ...CAMPAGNE, etat: "OCTROYEE" }], [`GET ${C}/c1/resultats`]: [], "GET /api/effectifs/milieux": [],
      "GET /api/effectifs/referentiels/unites": [{ code: "EST", libelleFr: "Est" }], "GET /api/effectifs/referentiels/corps_emploi": [{ code: "PARA", libelleFr: "Paramédics" }],
      "GET /api/effectifs/referentiels/codes_absence": [{ code: "VAC", libelleFr: "Vacances" }] };
    await rendre(<CampagnesConges />);
    await cliquer("cng-resultats-c1");
    expect($("cng-resultats-vide")).not.toBeNull();
    for (const [id, v] of [["cng-f-nom", "X"], ["cng-f-uniteCode", "EST"], ["cng-f-corpsEmploiCode", "PARA"], ["cng-f-codeAbsence", "VAC"], ["cng-f-du", "2027-01-04"], ["cng-f-au", "2027-01-10"]]) await saisir(id, v);
    await soumettre("cng-creer");
    expect($("cng-gestion-erreur")!.textContent).toContain("refusé");
  });
});

describe("autres unités d'une personne (M-09)", () => {
  const JULIE = { id: "r1", matricule: "P1", nom: "Julie Roy", uniteCode: "EST", corpsEmploiCode: "PARA", actif: true, anciennete: 3 } as Ressource;
  const R = "/api/effectifs/planification/ressources/r1/rattachements";
  it("liste les rattachements, en ajoute un et en retire un", async () => {
    api.etat.routes = {
      [`GET ${R}`]: [{ uniteCode: "OUEST", depuis: "2027-01-01", jusqua: null, actif: true }], [`POST ${R}`]: {}, [`DELETE ${R}/OUEST`]: { retire: "OUEST" },
      "GET /api/effectifs/referentiels/unites": [{ code: "EST", libelleFr: "Est" }, { code: "OUEST", libelleFr: "Ouest" }, { code: "NORD", libelleFr: "Nord" }], "GET /api/effectifs/milieux": [],
    };
    await rendre(<RattachementsRessource ressource={JULIE} onFermer={() => {}} />);
    expect($("pla-rattachement-OUEST")!.textContent).toContain("Sans fin");
    expect([...conteneur.querySelectorAll('[data-testid="pla-rattachement-unite"] option')].map((o) => o.textContent)).toEqual(["Choisir…", "Ouest", "Nord"]);
    await saisir("pla-rattachement-unite", "NORD");
    await saisir("pla-rattachement-depuis", "2027-02-01");
    await saisir("pla-rattachement-jusqua", "2027-04-30");
    await soumettre("pla-rattachement-ajouter");
    expect(envoi(R)).toEqual({ uniteCode: "NORD", depuis: "2027-02-01", jusqua: "2027-04-30" });
    expect($("pla-rattachements-succes")!.textContent).toContain("Rattachée à « Nord »");
    await cliquer("pla-rattachement-retirer-OUEST");
    expect(api.etat.appels.some((a) => a.methode === "DELETE" && a.url === `${R}/OUEST?depuis=2027-01-01`)).toBe(true);
    expect($("pla-rattachements-succes")!.textContent).toContain("Rattachement à « Ouest » retiré.");
  });

  it("sans autre unité, l'écran le dit ; un refus s'affiche", async () => {
    api.etat.routes = { [`GET ${R}`]: [], "GET /api/effectifs/milieux": [], "GET /api/effectifs/referentiels/unites": [{ code: "OUEST", libelleFr: "Ouest" }] };
    await rendre(<RattachementsRessource ressource={JULIE} onFermer={() => {}} />);
    expect($("pla-rattachements-vide")).not.toBeNull();
    await saisir("pla-rattachement-unite", "OUEST");
    await saisir("pla-rattachement-depuis", "2027-02-01");
    await soumettre("pla-rattachement-ajouter");
    expect($("pla-rattachements-erreur")!.textContent).toContain("refusé");
  });
});

describe("journal des consultations (M-10)", () => {
  const J = "/api/effectifs/journal/reglages";
  it("se lit, s'active et se désactive, en le disant", async () => {
    api.etat.routes = { [`GET ${J}`]: { consultations: false }, [`PUT ${J}`]: { consultations: true } };
    await rendre(<ReglageConsultations />);
    expect(($("jrn-consultations-actif") as HTMLInputElement).checked).toBe(false);
    await cliquer("jrn-consultations-actif");
    expect(envoi(J, "PUT")).toEqual({ consultations: true });
    expect($("jrn-consultations-succes")!.textContent).toContain("désormais journalisées");
    api.etat.routes[`PUT ${J}`] = { consultations: false };
    await cliquer("jrn-consultations-actif");
    expect($("jrn-consultations-succes")!.textContent).toContain("ne sont plus journalisées");
  });

  it("sans le droit de configurer, la section ne s'affiche pas", async () => {
    api.etat.routes = {};
    await rendre(<ReglageConsultations />);
    expect($("jrn-consultations")).toBeNull();
  });
});
