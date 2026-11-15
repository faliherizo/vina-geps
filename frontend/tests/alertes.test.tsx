/**
 * **Communications cliniques** (ADR 0074) — les quatre écrans, contre une API simulée qui retient ce
 * qu'on lui envoie : paramètres (réponses, modèles), console d'envoi, suivi, et « Mes alertes ».
 */
import { act } from "react";
import { createRoot, type Root } from "react-dom/client";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { etatRemise, libellePriorite, libelleSemantique, quand, tonPriorite, type Reponse } from "../src/alertes/types";

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
  return { etat, useAuth: () => ({ apiFetch, user: { id: "u1", username: "chef", role: "manager" } }) };
});
vi.mock("../src/AuthContext", () => ({ useAuth: api.useAuth }));

const { default: ParametresAlertes } = await import("../src/alertes/ParametresAlertes");
const { default: NouvelleAlerte } = await import("../src/alertes/NouvelleAlerte");
const { default: SuiviAlertes } = await import("../src/alertes/SuiviAlertes");
const { default: MesAlertes } = await import("../src/alertes/MesAlertes");

const ARRIVE: Reponse = { id: "r1", libelle: "J'arrive", semantique: "prise_en_charge", active: true };
const INDISPO: Reponse = { id: "r2", libelle: "Indisponible", semantique: "refus", active: false };
const REMISE = { personId: "julie", nom: "Julie Roy", envoyeeLe: "2026-11-14T14:02:00Z", lueLe: null, reponseId: null, reponduLe: null };
const ALERTE = { id: "a1", titre: "Code bleu 512", corps: "5e étage", priorite: "critique", expediteurId: "chef", envoyeeLe: "2026-11-14T14:02:00Z", reponses: [ARRIVE] };

let conteneur: HTMLDivElement;
let racine: Root;
const $ = (id: string) => conteneur.querySelector<HTMLElement>(`[data-testid="${id}"]`);
const attendre = async () => { for (let i = 0; i < 5; i++) await act(async () => { await Promise.resolve(); }); };
const cliquer = async (id: string) => { await act(async () => { $(id)!.click(); }); await attendre(); };
/** Saisir dans un champ contrôlé : le setter natif, puis l'événement que React écoute. */
const saisir = async (id: string, valeur: string) => {
  const el = $(id) as HTMLInputElement;
  const proto = el instanceof HTMLTextAreaElement ? HTMLTextAreaElement.prototype : el instanceof HTMLSelectElement ? HTMLSelectElement.prototype : HTMLInputElement.prototype;
  await act(async () => {
    Object.getOwnPropertyDescriptor(proto, "value")!.set!.call(el, valeur);
    el.dispatchEvent(new Event(el instanceof HTMLSelectElement ? "change" : "input", { bubbles: true }));
  });
  await attendre();
};
const rendre = async (element: JSX.Element) => { await act(async () => { racine.render(element); }); await attendre(); };
const envoisVers = (fragment: string) => api.etat.appels.filter((a) => a.methode !== "GET" && a.url.includes(fragment));

beforeEach(() => {
  api.etat.appels = [];
  conteneur = document.createElement("div"); document.body.appendChild(conteneur);
  racine = createRoot(conteneur);
});
afterEach(() => { act(() => racine.unmount()); conteneur.remove(); });

describe("libellés et états — en mots, jamais par la seule couleur", () => {
  it("priorités, sémantiques, tons et états de remise", () => {
    expect(libellePriorite("critique")).toBe("Critique");
    expect(libellePriorite("inconnue")).toBe("inconnue");
    expect(libelleSemantique("refus")).toBe("Refus");
    expect(libelleSemantique("x")).toBe("x");
    expect([tonPriorite("critique"), tonPriorite("urgente"), tonPriorite("information")]).toEqual(["refusee", "en_attente", "ouvert"]);
    expect(etatRemise(REMISE, [ARRIVE])).toBe("Envoyé");
    expect(etatRemise({ ...REMISE, lueLe: "x" }, [ARRIVE])).toBe("Lu");
    expect(etatRemise({ ...REMISE, reponseId: "r1" }, [ARRIVE])).toBe("Répondu : J'arrive");
    expect(etatRemise({ ...REMISE, reponseId: "r9" }, [ARRIVE])).toBe("Répondu : r9");
    expect(quand(null)).toBe("—");
    expect(quand("pas-une-date")).toBe("pas-une-date");
    expect(quand("2026-11-14T19:02:00Z")).toMatch(/2026-11-14.*14/);
  });
});

describe("Paramètres des alertes", () => {
  it("liste réponses et modèles, crée une réponse et un modèle dans leur dialogue, désactive", async () => {
    api.etat.routes = {
      "GET /api/alertes/reponses": [ARRIVE, INDISPO],
      "GET /api/alertes/modeles": [{ id: "m1", titre: "Code bleu", corps: "x", priorite: "critique", reponseIds: ["r1", "r9"], actif: false }],
      "POST /api/alertes/reponses": { ...ARRIVE, id: "r3", libelle: "Je prends" },
      "POST /api/alertes/modeles": { id: "m2", titre: "Remplacement", corps: "y", priorite: "urgente", reponseIds: [], actif: true },
      "PATCH /api/alertes": { id: "x" },
    };
    await rendre(<ParametresAlertes />);
    expect($("alp-reponse-r1")!.textContent).toContain("Prise en charge");
    expect($("alp-reponse-r2")!.textContent).toContain("Désactivée");
    expect($("alp-modele-m1")!.textContent).toContain("J'arrive · r9");
    await cliquer("alp-reponse-r1-basculer");
    expect(envoisVers("/reponses/r1")[0].corps).toEqual({ active: false });
    await cliquer("alp-modele-m1-basculer");
    expect(envoisVers("/modeles/m1")[0].corps).toEqual({ actif: true });

    await cliquer("alp-reponse-ouvrir");
    await saisir("alp-r-libelle", "Je prends");
    await saisir("alp-r-semantique", "refus");
    await act(async () => { ($("alp-r-creer") as HTMLButtonElement).form!.requestSubmit(); }); await attendre();
    expect(envoisVers("/api/alertes/reponses").at(-1)!.corps).toEqual({ libelle: "Je prends", semantique: "refus" });
    expect($("alp-succes")!.textContent).toContain("Réponse « Je prends » créée");

    await cliquer("alp-modele-ouvrir");
    await saisir("alp-m-titre", "Remplacement");
    await saisir("alp-m-corps", "Qui prend la nuit ?");
    await saisir("alp-m-priorite", "critique");
    await cliquer("alp-m-r-r1");
    await cliquer("alp-m-r-r1");
    await cliquer("alp-m-r-r1");
    await act(async () => { ($("alp-m-creer") as HTMLButtonElement).form!.requestSubmit(); }); await attendre();
    expect(envoisVers("/api/alertes/modeles").at(-1)!.corps).toEqual({ titre: "Remplacement", corps: "Qui prend la nuit ?", priorite: "critique", reponseIds: ["r1"], groupeIds: [] });
  });

  it("sans réponse ni modèle, l'écran dit quoi faire ; un refus du serveur s'affiche", async () => {
    api.etat.routes = { "GET /api/alertes/reponses": [], "GET /api/alertes/modeles": [] };
    await rendre(<ParametresAlertes />);
    expect($("alp-reponses-vide")).not.toBeNull();
    expect($("alp-modeles-vide")).not.toBeNull();
    await cliquer("alp-modele-ouvrir");
    expect(conteneur.textContent).toContain("Aucune réponse active");
    await saisir("alp-m-titre", "X");
    await saisir("alp-m-corps", "Y");
    await act(async () => { ($("alp-m-creer") as HTMLButtonElement).form!.requestSubmit(); }); await attendre();
    expect($("alp-erreur")!.textContent).toContain("refusé");
  });
});

describe("Nouvelle alerte", () => {
  const routes = {
    "GET /api/alertes/modeles": [{ id: "m1", titre: "Code bleu", corps: "Chambre 512", priorite: "critique", reponseIds: ["r1", "r2"], actif: true },
      { id: "m0", titre: "Vieux", corps: "x", priorite: "urgente", reponseIds: [], actif: false }],
    "GET /api/alertes/reponses": [ARRIVE, INDISPO],
    "GET /api/alertes/groupes/EST/membres": [{ personId: "julie", nom: "Julie Roy" }, { personId: "marc", nom: "Marc Dubé" }],
    "GET /api/alertes/groupes": [{ id: "EST", nom: "Opérations Est" }],
    "GET /api/alertes/listes": [{ id: "l-garde", nom: "Garde de nuit" }],
    "GET /api/alertes/droits": { envoyer: true, critique: true, configurer: false },
    "POST /api/alertes": { ...ALERTE, bilan: { destinataires: 3, lues: 0, repondues: 0, relances: 0, priseEnChargePar: null, priseEnChargeLe: null }, remises: [] },
  };

  it("un modèle remplit l'alerte ; groupes et personnes par leur nom ; la critique le dit ; l'envoi part complet", async () => {
    api.etat.routes = routes;
    await rendre(<NouvelleAlerte />);
    expect(($("aln-envoyer") as HTMLButtonElement).disabled).toBe(true);
    expect(conteneur.querySelectorAll('[data-testid="aln-modele"] option')).toHaveLength(2);
    await saisir("aln-modele", "m1");
    expect(($("aln-titre-champ") as HTMLInputElement).value).toBe("Code bleu");
    expect($("aln-critique")).not.toBeNull();
    // La réponse désactivée n'est ni proposée ni reprise du modèle.
    expect($("aln-r-r2")).toBeNull();
    expect(($("aln-r-r1") as HTMLInputElement).checked).toBe(true);
    await saisir("aln-modele", "");
    await cliquer("aln-g-EST");
    await saisir("aln-groupe-personnes", "EST");
    await cliquer("aln-pers-marc");
    await cliquer("aln-pers-julie");
    await cliquer("aln-pers-julie");
    expect($("aln-compte")!.textContent).toBe("1 groupe(s) et 1 personne(s) choisis : Marc Dubé.");
    await cliquer("aln-r-r1");
    await cliquer("aln-r-r1");
    await cliquer("aln-p-urgente");
    expect($("aln-critique")).toBeNull();
    await cliquer("aln-envoyer");
    expect(envoisVers("/api/alertes").at(-1)!.corps).toEqual({
      titre: "Code bleu", corps: "Chambre 512", priorite: "urgente", reponseIds: ["r1"], groupes: ["EST"], listes: [], personnes: ["marc"],
    });
    expect($("aln-envoyee")!.textContent).toContain("envoyée à 3 personne(s)");
    await saisir("aln-groupe-personnes", "");
    expect($("aln-pers-marc")).toBeNull();
  });

  it("ST-G01/G02 : une liste de distribution se vise ; le modèle choisi part avec l'alerte ; sans le droit, la critique est inactive et l'écran dit pourquoi", async () => {
    api.etat.routes = { ...routes, "GET /api/alertes/droits": { envoyer: true, critique: false, configurer: false },
      "GET /api/alertes/modeles": [...routes["GET /api/alertes/modeles"], { id: "m3", titre: "Relève", corps: "Qui prend la nuit ?", priorite: "urgente", reponseIds: [], actif: true }] };
    await rendre(<NouvelleAlerte />);
    // Le modèle critique n'est pas proposé à qui n'a pas le droit du critique.
    expect([...conteneur.querySelectorAll('[data-testid="aln-modele"] option')].map((o) => o.textContent)).toEqual(["— Aucun —", "Relève"]);
    expect(($("aln-p-critique") as HTMLInputElement).disabled).toBe(true);
    expect($("aln-p-critique")!.getAttribute("aria-describedby")).toBe("aln-critique-refusee");
    expect($("aln-critique-refusee")!.textContent).toContain("réservée");
    await saisir("aln-modele", "m3");
    await cliquer("aln-l-l-garde");
    expect(($("aln-envoyer") as HTMLButtonElement).disabled).toBe(false);
    await cliquer("aln-envoyer");
    expect(envoisVers("/api/alertes").at(-1)!.corps).toMatchObject({ listes: ["l-garde"], modeleId: "m3", groupes: [], personnes: [] });
    await cliquer("aln-l-l-garde");
  });

  it("sans modèle ni réponse, l'écran reste utilisable ; un refus s'affiche", async () => {
    api.etat.routes = { "GET /api/alertes/modeles": [], "GET /api/alertes/reponses": [], "GET /api/alertes/groupes": [{ id: "EST", nom: "Opérations Est" }] };
    await rendre(<NouvelleAlerte />);
    expect($("aln-modele")).toBeNull();
    expect(conteneur.textContent).toContain("sans réponse proposée");
    await cliquer("aln-g-EST");
    await saisir("aln-titre-champ", "Info");
    await saisir("aln-corps", "Texte");
    await act(async () => { ($("aln-envoyer") as HTMLButtonElement).form!.requestSubmit(); }); await attendre();
    expect($("aln-erreur")!.textContent).toContain("refusé");
  });
});

describe("Suivi des alertes", () => {
  it("le bilan par alerte, puis les destinataires au détail", async () => {
    api.etat.routes = { "GET /api/alertes/envoyees": [
      { ...ALERTE, bilan: { destinataires: 2, lues: 1, repondues: 1, relances: 2, priseEnChargePar: "Julie Roy", priseEnChargeLe: "2026-11-14T14:03:00Z" },
        remises: [{ ...REMISE, lueLe: "2026-11-14T14:03:00Z", reponseId: "r1", reponduLe: "2026-11-14T14:03:00Z" }, { ...REMISE, personId: "marc", nom: "Marc Dubé" }] },
      { ...ALERTE, id: "a2", priorite: "information", bilan: { destinataires: 1, lues: 0, repondues: 0, relances: 0, priseEnChargePar: null, priseEnChargeLe: null }, remises: [] },
    ] };
    await rendre(<SuiviAlertes />);
    expect($("als-alerte-a1")!.textContent).toContain("1 / 2");
    expect($("als-alerte-a1")!.textContent).toContain("Julie Roy");
    expect($("als-alerte-a2")!.textContent).toContain("Personne encore");
    expect($("als-alerte-a1-relances")!.textContent).toBe("2");
    expect($("als-alerte-a2-relances")!.textContent).toBe("0");
    await cliquer("als-alerte-a1-detail");
    expect($("als-remise-julie")!.textContent).toContain("Répondu : J'arrive");
    expect($("als-remise-marc")!.textContent).toContain("Envoyé");
    await cliquer("als-alerte-a1-detail");
    expect($("als-fiche")).toBeNull();
    await cliquer("als-actualiser");
    expect(api.etat.appels.filter((a) => a.url === "/api/alertes/envoyees")).toHaveLength(2);
  });
});

describe("Mes alertes", () => {
  it("ouvrir vaut lecture ; répondre d'un geste ; une réponse donnée se lit en mots", async () => {
    api.etat.routes = {
      "GET /api/alertes/recues": [{ ...ALERTE, maRemise: REMISE }, { ...ALERTE, id: "a2", titre: "Déjà répondue", reponses: [ARRIVE],
        maRemise: { ...REMISE, lueLe: "x", reponseId: "r1", reponduLe: "2026-11-14T14:05:00Z" } },
        { ...ALERTE, id: "a3", titre: "Sans réponse", reponses: [], maRemise: { ...REMISE, lueLe: "x" } }],
      "POST /api/alertes/recues": { ok: true },
    };
    await rendre(<MesAlertes />);
    expect($("mal-alerte-a1")!.textContent).toContain("Non lue");
    const details = $("mal-alerte-a1")!.querySelector("details")!;
    // jsdom émet lui-même « toggle » quand le repli s'ouvre.
    await act(async () => { details.open = true; await new Promise((r) => setTimeout(r, 0)); }); await attendre();
    expect(envoisVers("/recues/a1/lue")).toHaveLength(1);
    await cliquer("mal-alerte-a1-reponse-r1");
    expect(envoisVers("/recues/a1/reponse")[0].corps).toEqual({ reponseId: "r1" });
    expect($("mal-succes")!.textContent).toContain("« J'arrive » envoyée");
    expect($("mal-alerte-a2-repondue")!.textContent).toContain("J'arrive");
    expect($("mal-alerte-a3")!.textContent).toContain("Aucune réponse n'est attendue");
    // Une alerte déjà lue ne se relit pas au serveur.
    const lue = $("mal-alerte-a3")!.querySelector("details")!;
    await act(async () => { lue.open = true; await new Promise((r) => setTimeout(r, 0)); }); await attendre();
    expect(envoisVers("/recues/a3/lue")).toHaveLength(0);
  });

  it("aucune alerte : l'écran le dit", async () => {
    api.etat.routes = { "GET /api/alertes/recues": [] };
    await rendre(<MesAlertes />);
    expect($("mal-vide")).not.toBeNull();
  });
});
