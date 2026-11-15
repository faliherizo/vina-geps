/**
 * **Relances et cascades** (ST-G04) : l'administration compose une politique d'étapes ; l'expéditeur la choisit à
 * l'envoi ; le suivi montre la cascade, sa trace, et la reprend à une étape.
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
  return { etat, useAuth: () => ({ apiFetch, user: { id: "u1", username: "chef", role: "manager" } }) };
});
vi.mock("../src/AuthContext", () => ({ useAuth: api.useAuth }));

const { default: PolitiquesRelance } = await import("../src/alertes/PolitiquesRelance");
const { default: CascadeAlerte } = await import("../src/alertes/CascadeAlerte");
const { etapeEnMots, libelleArret, libelleCanal, libelleCible, libelleEtatCascade, libelleEvenement } = await import("../src/alertes/types");

const POLITIQUE = { id: "po1", nom: "Code bleu", arret: "prise_en_charge", version: 2, actif: true, etapes: [
  { cible: "initiaux", cibleId: null, canaux: ["poussee"], delaiMinutes: 5 }, { cible: "groupe", cibleId: "GARDE", canaux: ["texto", "courriel"], delaiMinutes: 10 }] };
const GROUPES = [{ id: "GARDE", nom: "Garde de nuit" }];

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


const soumettre = async (libelle: string) => {
  await act(async () => { conteneur.querySelector<HTMLFormElement>(`form[aria-label="${libelle}"]`)!.dispatchEvent(new Event("submit", { bubbles: true, cancelable: true })); });
  await attendre();
};

describe("libellés", () => {
  it("cibles, arrêts, canaux, états et événements en mots ; un code inconnu reste lisible", () => {
    expect(etapeEnMots(POLITIQUE.etapes[1] as never)).toBe("Après 10 min : Escalader à tout un groupe (GARDE), par Texto, Courriel");
    expect(etapeEnMots(POLITIQUE.etapes[0] as never)).toBe("Après 5 min : Relancer les destinataires qui n'ont pas réagi, par Poussée");
    expect([libelleArret("toutes_lues"), libelleCanal("x"), libelleCible("x"), libelleEtatCascade("epuisee"), libelleEtatCascade("x"), libelleEvenement("reprise"), libelleEvenement("x")])
      .toEqual(["Dès que tous ont lu", "x", "x", "Toutes les étapes sont parties", "x", "Reprise manuelle", "x"]);
  });
});

describe("Politiques de relance", () => {
  beforeEach(() => {
    api.etat.routes = {
      "GET /api/alertes/politiques": [POLITIQUE],
      "POST /api/alertes/politiques": { ...POLITIQUE, id: "po2", nom: "Nuit", version: 1 },
      "PUT /api/alertes/politiques/po1": { ...POLITIQUE, version: 3 },
      "PATCH /api/alertes/politiques/po1": { ...POLITIQUE, actif: false },
    };
  });

  it("créer une politique de deux étapes : groupe et personne, canaux cochés, délai saisi", async () => {
    await rendre(<PolitiquesRelance groupes={GROUPES} />);
    expect($("alp-pol-po1")!.textContent).toContain("Escalader à tout un groupe (GARDE)");
    await cliquer("alp-politique-ouvrir");
    await saisir("alp-pol-nom", "Nuit");
    await saisir("alp-pol-arret", "toutes_lues");
    await saisir("alp-pol-e0-delai", "3");
    await cliquer("alp-pol-e0-texto");
    await cliquer("alp-pol-e0-poussee");
    await cliquer("alp-pol-ajouter-etape");
    await saisir("alp-pol-e1-cible", "prochain_disponible");
    await saisir("alp-pol-e1-groupe", "GARDE");
    await cliquer("alp-pol-ajouter-etape");
    await saisir("alp-pol-e2-cible", "personne");
    await saisir("alp-pol-e2-personne", "chef");
    await cliquer("alp-pol-e2-retirer");
    expect($("alp-pol-e2")).toBeNull();
    await soumettre("Nouvelle politique de relance");
    expect(envoisVers("/api/alertes/politiques").at(-1)!.corps).toEqual({ nom: "Nuit", arret: "toutes_lues", etapes: [
      { cible: "initiaux", cibleId: null, canaux: ["texto"], delaiMinutes: 3 },
      { cible: "prochain_disponible", cibleId: "GARDE", canaux: ["poussee"], delaiMinutes: 5 }] });
    expect($("alp-pol-succes")!.textContent).toBe("Politique « Nuit » créée : version 1.");
  });

  it("modifier fait une version ; désactiver le dit ; un refus s'affiche", async () => {
    await rendre(<PolitiquesRelance groupes={GROUPES} />);
    await cliquer("alp-pol-po1-modifier");
    expect(($("alp-pol-e1-groupe") as HTMLSelectElement).value).toBe("GARDE");
    await soumettre("Modifier la politique de relance");
    expect(envoisVers("/politiques/po1").at(-1)!.methode).toBe("PUT");
    expect($("alp-pol-succes")!.textContent).toBe("Politique « Code bleu » modifiée : version 3.");
    await cliquer("alp-pol-po1-basculer");
    expect(envoisVers("/politiques/po1").at(-1)).toMatchObject({ methode: "PATCH", corps: { actif: false } });
    expect($("alp-pol-succes")!.textContent).toBe("« Code bleu » désactivée.");
    api.etat.routes["PATCH /api/alertes/politiques/po1"] = { errors: ["politique introuvable"] };
    await cliquer("alp-pol-po1-basculer");
    expect($("alp-pol-erreur")!.textContent).toContain("introuvable");
  });

  it("aucune politique : l'état vide le dit ; désactivée, elle offre de la réactiver", async () => {
    api.etat.routes["GET /api/alertes/politiques"] = [];
    await rendre(<PolitiquesRelance groupes={GROUPES} />);
    expect($("alp-politiques-vide")).not.toBeNull();
    act(() => racine.unmount());
    racine = createRoot(conteneur);
    api.etat.routes["GET /api/alertes/politiques"] = [{ ...POLITIQUE, actif: false }];
    await rendre(<PolitiquesRelance groupes={GROUPES} />);
    expect($("alp-pol-po1-basculer")!.textContent).toContain("Réactiver");
  });
});

describe("Cascade d'une alerte", () => {
  it("l'état, la trace, et la reprise à l'étape choisie", async () => {
    api.etat.routes = {
      "GET /api/alertes/envoyees/a1/cascade": { cascade: { politique: POLITIQUE, etape: 2, etapes: 2, echeanceLe: "2030-03-04T14:15:00Z", etat: "armee" },
        traces: [{ evenement: "armee", etape: 1, personnes: [], instant: "2030-03-04T14:00:00Z", par: "chef" },
          { evenement: "etape", etape: 1, personnes: ["julie"], instant: "2030-03-04T14:05:00Z", par: "cascade" }] },
      "POST /api/alertes/envoyees/a1/cascade/reprendre": { cascade: null, traces: [] },
    };
    await rendre(<CascadeAlerte alerteId="a1" />);
    expect($("als-cascade-etat")!.textContent).toContain("étape 2 sur 2");
    expect($("als-cascade-trace")!.textContent).toContain("julie");
    expect($("als-cascade-trace")!.textContent).toContain("Étape exécutée");
    await saisir("als-cascade-etape", "1");
    await soumettre("Reprendre la cascade");
    expect(envoisVers("/cascade/reprendre").at(-1)!.corps).toEqual({ etape: 1 });
    expect($("als-cascade-succes")!.textContent).toBe("Cascade reprise à l'étape 1 : elle part maintenant.");
  });

  it("sans politique, elle le dit ; arrêtée, elle n'annonce pas d'échéance", async () => {
    api.etat.routes = { "GET /api/alertes/envoyees/a2/cascade": { cascade: null, traces: [] } };
    await rendre(<CascadeAlerte alerteId="a2" />);
    expect($("als-cascade-aucune")).not.toBeNull();
    act(() => racine.unmount());
    racine = createRoot(conteneur);
    api.etat.routes = { "GET /api/alertes/envoyees/a3/cascade": { cascade: { politique: POLITIQUE, etape: 2, etapes: 2, echeanceLe: null, etat: "arretee" }, traces: [] } };
    await rendre(<CascadeAlerte alerteId="a3" />);
    expect($("als-cascade-etat")!.textContent).toContain("Arrêtée");
    expect($("als-cascade-etat")!.textContent).not.toContain("prévue");
  });
});
