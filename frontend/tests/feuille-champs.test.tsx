/**
 * **Une feuille de temps qui s'adapte à l'emploi** (ETUDE-FEUILLE-DE-TEMPS-DYNAMIQUE.md) — les champs de
 * l'organisme, la description des tâches et « En attente de », contre une API simulée qui retient ce
 * qu'on lui envoie.
 */
import { act, useState } from "react";
import { createRoot, type Root } from "react-dom/client";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { decisionEnMots, enAttenteDe, peutRappeler, valeurEnMots, type ChampFeuille, type FeuilleDetaillee } from "../src/effectifs/temps/types";

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

const { ParametresChamps, SaisieChamps, champsAffiches, complementAEnvoyer, COMPLEMENT_VIDE } = await import("../src/effectifs/temps/ChampsFeuille");
const { default: DetailFeuille } = await import("../src/effectifs/temps/DetailFeuille");
const { default: SuperieursHierarchiques } = await import("../src/effectifs/temps/SuperieursHierarchiques");

const VEHICULE: ChampFeuille = { code: "vehicule", libelle: "Véhicule", type: "LISTE", valeurs: ["A-12", "B-07"], requis: true, actif: true, ordre: 0 };
const KM: ChampFeuille = { code: "km", libelle: "Kilomètres", type: "NOMBRE", valeurs: [], requis: false, actif: true, ordre: 1 };
const GARDE: ChampFeuille = { code: "garde", libelle: "De garde", type: "BOOLEEN", valeurs: [], requis: false, actif: true, ordre: 2 };
const NOTE: ChampFeuille = { code: "note", libelle: "Note", type: "TEXTE", valeurs: [], requis: false, actif: true, ordre: 3 };
const ANCIEN: ChampFeuille = { code: "ancien", libelle: "Ancien", type: "TEXTE", valeurs: [], requis: false, actif: false, ordre: 4 };

let conteneur: HTMLDivElement;
let racine: Root;
const $ = (id: string) => conteneur.querySelector<HTMLElement>(`[data-testid="${id}"]`);
const attendre = async () => { for (let i = 0; i < 5; i++) await act(async () => { await Promise.resolve(); }); };
const cliquer = async (id: string) => { await act(async () => { $(id)!.click(); }); await attendre(); };
const saisir = async (id: string, valeur: string) => {
  const el = $(id) as HTMLInputElement;
  const proto = el instanceof HTMLTextAreaElement ? HTMLTextAreaElement.prototype : el instanceof HTMLSelectElement ? HTMLSelectElement.prototype : HTMLInputElement.prototype;
  await act(async () => {
    Object.getOwnPropertyDescriptor(proto, "value")!.set!.call(el, valeur);
    el.dispatchEvent(new Event(el instanceof HTMLSelectElement ? "change" : "input", { bubbles: true }));
  });
  await attendre();
};
const soumettre = async (selecteur: string) => {
  await act(async () => { conteneur.querySelector<HTMLFormElement>(selecteur)!.dispatchEvent(new Event("submit", { bubbles: true, cancelable: true })); });
  await attendre();
};
const rendre = async (element: JSX.Element) => { await act(async () => { racine.render(element); }); await attendre(); };
const envoisVers = (fragment: string) => api.etat.appels.filter((a) => a.methode !== "GET" && a.url.includes(fragment));

beforeEach(() => {
  api.etat.appels = [];
  api.etat.routes = {};
  conteneur = document.createElement("div"); document.body.appendChild(conteneur);
  racine = createRoot(conteneur);
});
afterEach(() => { act(() => racine.unmount()); conteneur.remove(); });

describe("les fonctions pures", () => {
  it("avant la paie, soumise ou approuvée se rappelle ; les décisions hors circuit se disent", () => {
    expect((["BROUILLON", "SOUMISE", "RETOURNEE", "APPROUVEE", "PAYEE"] as const).map(peutRappeler)).toEqual([false, true, false, true, false]);
    expect(["APPROUVER", "RETOURNER", "RAPPELER", "ROUVRIR", "AUTRE"].map(decisionEnMots))
      .toEqual(["approuvé", "retourné", "rappelée par la personne", "rouverte par l'encadrement", "AUTRE"]);
  });

  it("une valeur en mots : jamais « true » ; vide en tiret", () => {
    expect([valeurEnMots(true), valeurEnMots(false), valeurEnMots(42), valeurEnMots("A-12"), valeurEnMots(""), valeurEnMots(undefined)])
      .toEqual(["Oui", "Non", "42", "A-12", "—", "—"]);
  });
  it("« En attente de » : seulement une feuille soumise, au palier courant en attente", () => {
    const flux = { demandeId: "d", statut: "EN_COURS" as const, palierCourant: 2, etapes: [
      { id: "e1", palier: 1, libelle: "le niveau GESTION de l'unité", statut: "APPROUVEE" as const, decidePar: "chef", decideLe: "x", pourLeCompteDe: null },
      { id: "e2", palier: 2, libelle: "rh (personne désignée)", statut: "EN_ATTENTE" as const, decidePar: null, decideLe: null, pourLeCompteDe: null }] };
    expect(enAttenteDe({ statut: "SOUMISE", flux })).toBe("rh (personne désignée)");
    expect(enAttenteDe({ statut: "APPROUVEE", flux })).toBeNull();
    expect(enAttenteDe({ statut: "SOUMISE", flux: null })).toBeNull();
  });
  it("les colonnes : les champs actifs, et un champ désactivé qui porte encore une valeur", () => {
    expect(champsAffiches([VEHICULE, ANCIEN], [{ attributs: null }]).map((c) => c.code)).toEqual(["vehicule"]);
    expect(champsAffiches([VEHICULE, ANCIEN], [{ attributs: { ancien: "x" } }]).map((c) => c.code)).toEqual(["vehicule", "ancien"]);
  });
  it("l'envoi omet les valeurs vides, garde false et 0", () => {
    expect(complementAEnvoyer({ description: "ras", attributs: { a: "", b: false, c: 0, d: "x" } })).toEqual({ description: "ras", attributs: { b: false, c: 0, d: "x" } });
  });
});

describe("SaisieChamps", () => {
  function Hote({ surChange }: { surChange: (c: unknown) => void }) {
    const [v, setV] = useState(COMPLEMENT_VIDE);
    return <SaisieChamps champs={[VEHICULE, KM, GARDE, NOTE, ANCIEN]} valeur={v} prefixe="t" onChange={(c) => { setV(c); surChange(c); }} />;
  }

  it("un contrôle par champ actif, du bon type ; le désactivé n'est pas offert ; l'obligatoire le dit", async () => {
    let dernier: unknown = null;
    await rendre(<Hote surChange={(c) => { dernier = c; }} />);
    expect($("t-champ-ancien")).toBeNull();
    expect(conteneur.querySelector('label[for="t-champ-vehicule"]')!.textContent).toBe("Véhicule (obligatoire)");
    expect(($("t-champ-vehicule") as HTMLSelectElement).required).toBe(true);
    expect(($("t-champ-km") as HTMLInputElement).type).toBe("number");
    expect(($("t-champ-garde") as HTMLInputElement).type).toBe("checkbox");
    await saisir("t-description", "Transport inter-hospitalier");
    await saisir("t-champ-vehicule", "B-07");
    await saisir("t-champ-km", "42.5");
    await saisir("t-champ-note", "ras");
    await cliquer("t-champ-garde");
    expect(dernier).toEqual({ description: "Transport inter-hospitalier", attributs: { vehicule: "B-07", km: 42.5, note: "ras", garde: true } });
    await saisir("t-champ-km", "");
    expect((dernier as { attributs: Record<string, unknown> }).attributs.km).toBe("");
  });
});

describe("ParametresChamps", () => {
  it("liste les champs, en définit un (liste : un choix par ligne), et en désactive un", async () => {
    api.etat.routes = {
      "GET /api/effectifs/temps/champs": [VEHICULE, ANCIEN],
      "POST /api/effectifs/temps/champs": { ...KM, libelle: "Kilomètres" },
      "PATCH /api/effectifs/temps/champs/vehicule": { ...VEHICULE, actif: false },
    };
    await rendre(<ParametresChamps />);
    expect($("pla-tps-champ-vehicule")!.textContent).toContain("Liste de choix : A-12, B-07");
    expect($("pla-tps-champ-vehicule")!.textContent).toContain("Actif");
    expect($("pla-tps-champ-ancien-basculer")!.textContent).toContain("Réactiver");

    await saisir("pla-tps-champ-libelle", "Secteur");
    await saisir("pla-tps-champ-code", "secteur");
    await saisir("pla-tps-champ-type", "liste");
    await saisir("pla-tps-champ-valeurs", "Nord\n\n Sud ");
    await cliquer("pla-tps-champ-requis");
    await soumettre('form[aria-label="Définir un champ"]');
    expect(envoisVers("/temps/champs")[0].corps).toEqual({ code: "secteur", libelle: "Secteur", type: "liste", valeurs: ["Nord", "Sud"], requis: true });
    expect($("pla-tps-champs-info")!.textContent).toBe("Champ « Kilomètres » ajouté à la feuille.");
    expect(($("pla-tps-champ-libelle") as HTMLInputElement).value).toBe("");

    await saisir("pla-tps-champ-type", "nombre");
    expect($("pla-tps-champ-valeurs")).toBeNull();

    await cliquer("pla-tps-champ-vehicule-basculer");
    expect(envoisVers("/temps/champs/vehicule")[0]).toMatchObject({ methode: "PATCH", corps: { actif: false } });
    expect($("pla-tps-champs-info")!.textContent).toContain("désactivé : les valeurs déjà saisies restent lisibles");
  });

  it("sans champ, l'écran dit ce que la feuille offre ; un refus s'affiche ; une réactivation se dit", async () => {
    api.etat.routes = { "GET /api/effectifs/temps/champs": [], "PATCH /api/effectifs/temps/champs/ancien": { ...ANCIEN, actif: true } };
    await rendre(<ParametresChamps />);
    expect($("pla-tps-champs-vide")).not.toBeNull();
    await saisir("pla-tps-champ-libelle", "X");
    await saisir("pla-tps-champ-code", "x");
    await soumettre('form[aria-label="Définir un champ"]');
    expect($("pla-tps-champs-erreur")!.textContent).toContain("refusé");
    expect($("pla-tps-champs-info")).toBeNull();

    api.etat.routes["GET /api/effectifs/temps/champs"] = [ANCIEN];
    await rendre(<div key="2"><ParametresChamps /></div>);
    await cliquer("pla-tps-champ-ancien-basculer");
    expect($("pla-tps-champs-info")!.textContent).toBe("Champ « Ancien » réactivé.");
    api.etat.routes = { "GET /api/effectifs/temps/champs": [ANCIEN] };
    await cliquer("pla-tps-champ-ancien-basculer");
    expect($("pla-tps-champs-erreur")!.textContent).toContain("refusé");
  });
});

describe("DetailFeuille", () => {
  const FEUILLE: FeuilleDetaillee = {
    id: "f1", ressourceId: "r1", nom: "Julie Roy", matricule: "P1", uniteCode: "EST", debut: "2031-01-13", fin: "2031-01-19", statut: "SOUMISE",
    palier: 1, paliersRequis: 1, soumiseLe: "x", approuveeLe: null, totaux: {}, heuresSupplementaires: { parJour: {}, total: 0 }, ecarts: [], anomalies: [], decisions: [],
    segments: [{ id: "s1", date: "2031-01-17", debut: "2031-01-17T13:00:00Z", fin: "2031-01-17T17:00:00Z", typeHeureCode: "REG", comportement: "TRAVAILLEE", source: "SAISIE",
      placeId: null, version: 1, remplaceId: null, motif: null, description: "Transport", attributs: { vehicule: "A-12", garde: true } }],
    flux: { demandeId: "d", statut: "EN_COURS", palierCourant: 1, etapes: [
      { id: "e1", palier: 1, libelle: "Marc Lavoie (personne désignée)", statut: "EN_ATTENTE", decidePar: null, decideLe: null, pourLeCompteDe: null }] },
  };

  it("dit chez qui est la feuille, et nomme la ressource, les tâches et les champs du segment", async () => {
    await rendre(<DetailFeuille f={FEUILLE} prefixe="d" avecSegments champs={[VEHICULE, GARDE]} />);
    expect($("d-attente")!.textContent).toBe("En attente de : Marc Lavoie (personne désignée)");
    const ligne = $("d-segments")!.querySelector("tbody tr")!.textContent!;
    for (const attendu of ["Julie Roy (P1)", "Transport", "A-12", "Oui"]) expect(ligne).toContain(attendu);
    expect([...$("d-segments")!.querySelectorAll("thead th")].map((t) => t.textContent)).toEqual(
      ["Jour", "Ressource", "Heures", "Tâches", "Véhicule", "De garde", "Origine", "Preuve tierce"]);
  });

  it("sans description ni circuit en attente : un tiret, aucun « En attente de »", async () => {
    await rendre(<DetailFeuille f={{ ...FEUILLE, statut: "BROUILLON", segments: [{ ...FEUILLE.segments[0], description: null, attributs: null }] }} prefixe="d" avecSegments />);
    expect($("d-attente")).toBeNull();
    expect($("d-segments")!.querySelector("tbody tr")!.textContent).toContain("—");
  });
  it("anomalies (bloquante nommée), écarts, sources comparées et circuit : tout se lit en mots", async () => {
    await rendre(<DetailFeuille prefixe="d" f={{ ...FEUILLE,
      anomalies: [{ code: "A", date: "2031-01-17", message: "pause manquante", bloquante: true }, { code: "B", date: "2031-01-18", message: "écart", bloquante: false }],
      ecarts: [{ placeId: "p1", date: "2031-01-17", planifie: 480, declare: 510, ecart: 30 }],
      correlation: [{ date: "2031-01-17", sources: ["RAO", "borne"], concordants: 2, divergents: 1 }, { date: "2031-01-18", sources: ["RAO", "borne"], concordants: 1, divergents: 0 }],
    }} />);
    expect($("d-anomalies")!.textContent).toContain("2 anomalies");
    expect($("d-anomalies")!.textContent).toContain("pause manquante (à corriger avant de soumettre)");
    expect($("d-ecarts")!.textContent).toContain("0 h 30");
    expect($("d-correlation")!.textContent).toContain("1 (en désaccord)");
    expect($("d-circuit")!.textContent).toBe("Palier 1 : Marc Lavoie (personne désignée) — en attente (palier courant)");
    expect($("d-segments")).toBeNull();
    await rendre(<DetailFeuille prefixe="d" f={{ ...FEUILLE, anomalies: [{ code: "A", date: "2031-01-17", message: "seule", bloquante: false }] }} />);
    expect($("d-anomalies")!.textContent).toContain("1 anomalie");
    expect($("d-anomalies")!.textContent).not.toContain("1 anomalies");
  });
});

describe("SuperieursHierarchiques", () => {
  const LIENS = [{ id: "e-chef", nom: "Marc Lavoie", superieurId: null }, { id: "e-julie", nom: "Julie Roy", superieurId: "e-chef" }, { id: "e-luc", nom: "Luc Gagnon", superieurId: null }];

  it("montre qui relève de qui ; choisir une personne propose son supérieur actuel ; on désigne, puis on retire", async () => {
    api.etat.routes = { "GET /api/personnel/hierarchie": LIENS, "PUT /api/personnel/employes/e-luc/superieur": { id: "e-luc", nom: "Luc Gagnon", superieurId: "e-chef" } };
    await rendre(<SuperieursHierarchiques />);
    expect($("pla-tps-sup-table")!.textContent).toContain("Julie Roy");
    expect($("pla-tps-sup-table")!.textContent).toContain("Marc Lavoie");
    expect(($("pla-tps-sup-enregistrer") as HTMLButtonElement).disabled).toBe(true);
    await saisir("pla-tps-sup-personne", "e-julie");
    expect(($("pla-tps-sup-superieur") as HTMLSelectElement).value).toBe("e-chef");
    expect([...($("pla-tps-sup-superieur") as HTMLSelectElement).options].map((o) => o.value)).not.toContain("e-julie");
    await saisir("pla-tps-sup-personne", "e-luc");
    expect(($("pla-tps-sup-superieur") as HTMLSelectElement).value).toBe("");
    await saisir("pla-tps-sup-superieur", "e-chef");
    await soumettre('form[aria-label="Désigner un supérieur"]');
    expect(envoisVers("/superieur")[0]).toMatchObject({ methode: "PUT", corps: { superieurId: "e-chef" } });
    expect($("pla-tps-sup-info")!.textContent).toBe("Luc Gagnon relève maintenant de Marc Lavoie.");

    api.etat.routes["PUT /api/personnel/employes/e-luc/superieur"] = { id: "e-luc", nom: "Luc Gagnon", superieurId: null };
    await saisir("pla-tps-sup-superieur", "");
    await soumettre('form[aria-label="Désigner un supérieur"]');
    expect(envoisVers("/superieur")[1].corps).toEqual({ superieurId: null });
    expect($("pla-tps-sup-info")!.textContent).toContain("n'a plus de supérieur désigné");
  });

  it("un refus s'affiche ; sans aucun lien, pas de tableau ; un supérieur inconnu se lit en tiret", async () => {
    api.etat.routes = { "GET /api/personnel/hierarchie": [{ id: "e-a", nom: "A", superieurId: null }] };
    await rendre(<SuperieursHierarchiques />);
    expect($("pla-tps-sup-table")).toBeNull();
    await saisir("pla-tps-sup-personne", "e-a");
    await soumettre('form[aria-label="Désigner un supérieur"]');
    expect($("pla-tps-sup-erreur")!.textContent).toContain("refusé");
    expect($("pla-tps-sup-info")).toBeNull();
    api.etat.routes = { "GET /api/personnel/hierarchie": [{ id: "e-a", nom: "A", superieurId: "e-parti" }] };
    await rendre(<div key="b"><SuperieursHierarchiques /></div>);
    expect($("pla-tps-sup-table")!.textContent).toContain("—");
  });
});
