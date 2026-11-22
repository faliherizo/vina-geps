/**
 * **Offre d'un quart par ancienneté et à tour de rôle** (AD-07 ; art. 15.10, 16.02, 16.03 ; ADR 0089) — l'employé lit
 * le quart qui lui est offert et y répond ; l'encadrement relit l'ordre d'offre et règle le mode de l'organisme.
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
    return Promise.resolve({ ok, status: ok ? 200 : 409, json: () => Promise.resolve(corps) } as Response);
  };
  return { etat, useAuth: () => ({ apiFetch, user: { id: "u1", username: "marc", role: "employee" } }) };
});
vi.mock("../src/AuthContext", () => ({ useAuth: api.useAuth }));

const { default: QuartsOfferts } = await import("../src/effectifs/offres/QuartsOfferts");
const { default: OrdreOffre } = await import("../src/effectifs/offres/OrdreOffre");
const { default: ParametresOffres } = await import("../src/effectifs/offres/ParametresOffres");
const { heureLocale, offresRecues } = await import("../src/effectifs/offres/types");

let conteneur: HTMLDivElement;
let racine: Root;
const $ = (id: string) => conteneur.querySelector<HTMLElement>(`[data-testid="${id}"]`);
const attendre = async () => { for (let i = 0; i < 6; i++) await act(async () => { await Promise.resolve(); }); };
const cliquer = async (id: string) => { await act(async () => { $(id)!.click(); }); await attendre(); };
const rendre = async (el: React.ReactElement) => { await act(async () => { racine.render(el); }); await attendre(); };

const rang = (n: number, nom: string, etat: string, extra: Record<string, unknown> = {}) => ({
  rang: n, ressourceId: `r-${nom}`, nom, anciennete: 10 - n, heuresComptees: n === 3 ? 11.25 : 0, heuresOffertes: 12, etat,
  offertLe: null, echeance: null, reponduLe: null, motif: null, ...extra,
});
const offre = (rangs: unknown[], extra: Record<string, unknown> = {}) => ({
  id: "eofr-1", comblementId: "c1", absenceId: "a1", urgente: false, etat: "OUVERTE", creeLe: "x", attribueeA: null,
  quart: { placeId: "p1", uniteCode: "EST", date: "2030-03-05", debut: 360, fin: 1080 }, rangs, ...extra,
});

beforeEach(() => {
  api.etat.appels = [];
  api.etat.routes = {};
  conteneur = document.createElement("div"); document.body.appendChild(conteneur);
  racine = createRoot(conteneur);
});
afterEach(() => { act(() => racine.unmount()); conteneur.remove(); });

describe("l'heure d'une échéance", () => {
  it("se lit à l'heure de l'établissement ; l'absente se dit d'un tiret", () => {
    expect(heureLocale("2030-02-20T15:10:00.000Z")).toBe("2030-02-20 à 10:10");
    expect(heureLocale(null)).toBe("—");
    expect(heureLocale("pas une date")).toBe("pas une date");
  });
  it("une réponse d'une autre forme ne fait pas tomber l'écran : seules les offres bien formées restent", () => {
    expect(offresRecues({ errors: [] })).toEqual([]);
    expect(offresRecues([null, { id: "x" }, offre([])]).map((o) => o.id)).toEqual(["eofr-1"]);
  });
});

describe("Quarts qui me sont offerts (Mon horaire)", () => {
  it("rien d'offert, ou lecture refusée (module fermé) : la section ne s'affiche pas, sans erreur", async () => {
    api.etat.routes["GET /api/effectifs/offres/miennes"] = [];
    await rendre(<QuartsOfferts />);
    expect($("mho-offres")).toBeNull();
    api.etat.routes = {};
    act(() => racine.unmount());
    racine = createRoot(conteneur);
    await rendre(<QuartsOfferts />);
    expect(conteneur.innerHTML).toBe("");
  });

  it("dit l'échéance et la conséquence du silence avant le geste ; accepter attribue et relit l'horaire", async () => {
    api.etat.routes["GET /api/effectifs/offres/miennes"] = [offre([rang(1, "Marc", "OFFERT", { echeance: "2030-02-20T15:10:00.000Z" })])];
    api.etat.routes["POST /api/effectifs/offres/eofr-1/accepter"] = offre([rang(1, "Marc", "ACCEPTE")], { etat: "POURVUE" });
    const apres = vi.fn();
    await rendre(<QuartsOfferts apresReponse={apres} />);
    expect($("mho-offres")!.querySelector("h2")!.textContent).toBe("Quarts qui me sont offerts");
    expect($("mho-offre-2030-03-05")!.textContent).toContain("2030-03-05 · 06:00–18:00");
    expect($("mho-offre-2030-03-05")!.textContent).toContain("Répondez avant le 2030-02-20 à 10:10. Sans réponse, le quart est réputé refusé.");
    expect($("mho-offre-accepter-2030-03-05")!.textContent).toBe("Accepter le quart du 2030-03-05");
    api.etat.routes["GET /api/effectifs/offres/miennes"] = [];
    await cliquer("mho-offre-accepter-2030-03-05");
    expect(api.etat.appels.some((a) => a.methode === "POST" && a.url === "/api/effectifs/offres/eofr-1/accepter")).toBe(true);
    expect($("mho-offres-info")!.getAttribute("role")).toBe("status");
    expect($("mho-offres-info")!.textContent).toBe("Le quart du 2030-03-05 vous est attribué : il paraît dans votre horaire.");
    expect($("mho-offres-vide")!.textContent).toBe("Aucun quart ne vous est offert en ce moment.");
    expect(apres).toHaveBeenCalledTimes(1);
  });

  it("refuser passe au suivant ; une règle qui écarte se dit avec son motif ; un refus du serveur s'affiche", async () => {
    api.etat.routes["GET /api/effectifs/offres/miennes"] = [offre([rang(1, "Marc", "OFFERT")], { urgente: true })];
    api.etat.routes["POST /api/effectifs/offres/eofr-1/refuser"] = offre([rang(1, "Marc", "REFUSE")]);
    await rendre(<QuartsOfferts />);
    expect($("mho-offre-2030-03-05")!.textContent).toContain("quart urgent : le premier qui accepte le prend");
    await cliquer("mho-offre-refuser-2030-03-05");
    expect($("mho-offres-info")!.textContent).toBe("Vous avez refusé le quart du 2030-03-05 : il est offert à la personne suivante.");

    api.etat.routes["POST /api/effectifs/offres/eofr-1/accepter"] = offre([rang(1, "Marc", "ECARTE", { motif: "« Marc » tient déjà un quart qui chevauche celui-ci" })]);
    await cliquer("mho-offre-accepter-2030-03-05");
    expect($("mho-offres-info")!.textContent).toBe("Le quart du 2030-03-05 ne peut pas vous être attribué : « Marc » tient déjà un quart qui chevauche celui-ci.");

    api.etat.routes["POST /api/effectifs/offres/eofr-1/accepter"] = { errors: ["le délai de réponse est échu : le quart est réputé refusé"] };
    await cliquer("mho-offre-accepter-2030-03-05");
    expect($("mho-offres-erreur")!.textContent).toContain("le délai de réponse est échu");
  });
});

describe("Ordre d'offre (comblement d'une absence)", () => {
  it("un tableau légendé, chaque état dit en mots, le motif d'un écart, l'état de l'offre", async () => {
    await rendre(<OrdreOffre offre={offre([rang(1, "Marc", "REFUSE", { reponduLe: "2030-02-20T15:03:00.000Z" }),
      rang(2, "Théo", "ECARTE", { motif: "repos insuffisant" }), rang(3, "Léa", "OFFERT", { echeance: "2030-02-20T15:23:00.000Z" })])} />);
    const table = conteneur.querySelector("table")!;
    expect(table.querySelector("caption")!.textContent).toBe("Ordre d'offre du quart du 2030-03-05 — par ancienneté et à tour de rôle");
    expect([...table.querySelectorAll("thead th")].every((th) => th.getAttribute("scope") === "col")).toBe(true);
    const lignes = [...table.querySelectorAll("tbody tr")].map((tr) => tr.textContent);
    expect(lignes[0]).toContain("A refusé (compté)");
    expect(lignes[0]).toContain("2030-02-20 à 10:03");
    expect(lignes[1]).toContain("Écarté par une règle (non compté) : repos insuffisant");
    expect(lignes[2]).toContain("11,25 h");
    expect(lignes[2]).toContain("Offert — réponse attendue");
    expect($("pla-abs-ordre-etat-2030-03-05")!.textContent).toBe("Offre en cours.");
    expect(conteneur.querySelector('[role="region"]')!.getAttribute("tabindex")).toBe("0");
  });

  it("urgente : la légende le dit", async () => {
    await rendre(<OrdreOffre offre={offre([rang(1, "Marc", "CLOS")], { urgente: true, etat: "EPUISEE" })} />);
    expect(conteneur.querySelector("caption")!.textContent).toContain("urgent : offert à tous jusqu'au début du quart");
    expect($("pla-abs-ordre-etat-2030-03-05")!.textContent).toBe("Offre épuisée — la place va à la gestion régulière.");
  });
});

describe("Offre des quarts libérés (réglage)", () => {
  it("lit le réglage, passe en cascade avec un délai, et le confirme ; chaque champ a son libellé", async () => {
    api.etat.routes["GET /api/effectifs/offres/parametres"] = { mode: "SIMULTANEE", delaiReponseMinutes: 10, seuilUrgenceMinutes: 120, fenetreRepartitionJours: 91 };
    api.etat.routes["PUT /api/effectifs/offres/parametres"] = { mode: "CASCADE", delaiReponseMinutes: 15, seuilUrgenceMinutes: 120, fenetreRepartitionJours: 91 };
    await rendre(<ParametresOffres />);
    for (const champ of conteneur.querySelectorAll("input")) {
      expect(conteneur.querySelector(`label[for="${champ.id}"]`)).not.toBeNull();
    }
    expect(($("pla-abs-offres-SIMULTANEE") as HTMLInputElement).checked).toBe(true);
    await cliquer("pla-abs-offres-CASCADE");
    const delai = $("pla-abs-offres-delaiReponseMinutes") as HTMLInputElement;
    await act(async () => {
      Object.getOwnPropertyDescriptor(HTMLInputElement.prototype, "value")!.set!.call(delai, "15");
      delai.dispatchEvent(new Event("input", { bubbles: true }));
    });
    await act(async () => { conteneur.querySelector("form")!.dispatchEvent(new Event("submit", { bubbles: true, cancelable: true })); });
    await attendre();
    const put = api.etat.appels.find((a) => a.methode === "PUT")!;
    expect(put.corps).toEqual({ mode: "CASCADE", delaiReponseMinutes: 15, seuilUrgenceMinutes: 120, fenetreRepartitionJours: 91 });
    expect($("pla-abs-offres-info")!.textContent).toBe("Les quarts libérés seront offerts une personne à la fois, 15 minutes pour répondre.");
  });

  it("revenir au simultané se confirme ; un refus du serveur s'affiche ; une lecture refusée aussi", async () => {
    api.etat.routes["GET /api/effectifs/offres/parametres"] = { mode: "CASCADE", delaiReponseMinutes: 10, seuilUrgenceMinutes: 120, fenetreRepartitionJours: 91 };
    api.etat.routes["PUT /api/effectifs/offres/parametres"] = { mode: "SIMULTANEE", delaiReponseMinutes: 10, seuilUrgenceMinutes: 120, fenetreRepartitionJours: 91 };
    await rendre(<ParametresOffres />);
    await cliquer("pla-abs-offres-SIMULTANEE");
    await act(async () => { conteneur.querySelector("form")!.dispatchEvent(new Event("submit", { bubbles: true, cancelable: true })); });
    await attendre();
    expect($("pla-abs-offres-info")!.textContent).toBe("Les quarts libérés seront offerts à tous les admissibles en même temps.");
    api.etat.routes["PUT /api/effectifs/offres/parametres"] = { errors: ["« delaiReponseMinutes » : un nombre entier de 1 à 1440 est attendu"] };
    await act(async () => { conteneur.querySelector("form")!.dispatchEvent(new Event("submit", { bubbles: true, cancelable: true })); });
    await attendre();
    expect($("pla-abs-offres-erreur")!.textContent).toContain("un nombre entier de 1 à 1440");

    act(() => racine.unmount());
    racine = createRoot(conteneur);
    api.etat.routes = {};
    await rendre(<ParametresOffres />);
    expect($("pla-abs-offres-erreur")!.textContent).toContain("refusé");
  });
});
