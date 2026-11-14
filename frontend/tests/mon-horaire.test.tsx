/**
 * **Mon horaire** (maquette s03) — ce que la planche demande, et ce que la donnée sait.
 *
 * Trois décisions s'y éprouvent, et chacune est un choix qu'on pourrait « corriger » à tort :
 *
 * - le **site** n'est jamais deviné : un quart sans milieu affiche « — », parce que se présenter
 *   au mauvais garage n'est pas une erreur qu'on rattrape dans la journée ;
 * - le **statut** dit ce que la donnée sait : « Confirmé », ou « Échange en cours » quand un
 *   échange ouvert concerne le quart — c'est précisément ce qui rend un horaire incertain ;
 * - sans le module des échanges, **aucun appel** n'est fait : un « en attente » qui ne
 *   correspondrait à rien serait pire qu'une colonne constante.
 */
import { act } from "react";
import { createRoot, type Root } from "react-dom/client";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import MonHoraire, { jourEnMots, moisDe, resumeDuMois, semaineDe } from "../src/MonHoraire";

(globalThis as unknown as { IS_REACT_ACT_ENVIRONMENT: boolean }).IS_REACT_ACT_ENVIRONMENT = true;

const auth = vi.hoisted(() => {
  const porteur: { appel: (url: string) => Promise<Response> } = {
    appel: () => Promise.reject(new Error("aucun appel branché")),
  };
  const stable = { apiFetch: (url: string) => porteur.appel(url), user: { id: "u1", role: "employee" } };
  return { porteur, useAuth: () => stable };
});
vi.mock("../src/AuthContext", () => ({ useAuth: auth.useAuth }));

const modules = vi.hoisted(() => ({ autorises: new Set<string>(["effectifs_echanges"]) }));
vi.mock("../src/moduleAccess", () => ({
  useModuleAllowed: () => (code?: string) => !code || modules.autorises.has(code),
}));

const reponse = (corps: unknown) =>
  ({ ok: true, status: 200, json: () => Promise.resolve(corps) }) as unknown as Response;

const QUART = {
  placeId: "pl-1", date: "2026-11-02", debut: 360, fin: 840,
  uniteCode: "OPS", corpsEmploiCode: "PARA", ressourceId: "r1", nom: "Julie Roy",
  milieuId: "m1", milieu: "Centre-ville",
};

describe("Mon horaire — la planche s03", () => {
  let conteneur: HTMLDivElement;
  let racine: Root;
  beforeEach(() => {
    modules.autorises = new Set(["effectifs_echanges"]);
    conteneur = document.createElement("div"); document.body.appendChild(conteneur);
    racine = createRoot(conteneur);
  });
  afterEach(() => { act(() => racine.unmount()); conteneur.remove(); });

  const brancher = (horaire: unknown, echanges: unknown = []) => {
    const vus: string[] = [];
    auth.porteur.appel = (url: string) => {
      vus.push(url);
      return Promise.resolve(reponse(url.includes("mon-horaire") ? horaire : echanges));
    };
    return vus;
  };

  /** Rend l'écran ; la plupart des essais lisent la **liste** de la semaine, le calendrier étant la vue par défaut. */
  const rendre = async (vue: "liste" | "calendrier" = "liste") => {
    await act(async () => { racine.render(<MonHoraire />); });
    await act(async () => { await Promise.resolve(); });
    await act(async () => { await Promise.resolve(); });
    const bascule = conteneur.querySelector<HTMLButtonElement>('[data-testid="mho-vue-liste"]');
    if (vue === "liste" && bascule) {
      await act(async () => { bascule.click(); });
      await act(async () => { await Promise.resolve(); });
      await act(async () => { await Promise.resolve(); });
    }
  };

  it("le jour se lit en mots, et une date illisible reste telle quelle", () => {
    expect(jourEnMots("2026-11-02")).toMatch(/^lundi 2 /);
    // Pas d'invention : une valeur qu'on ne sait pas lire s'affiche telle quelle.
    expect(jourEnMots("pas-une-date")).toBe("pas-une-date");
  });

  it("montre le quart, son site et « Confirmé »", async () => {
    brancher({ ressource: { id: "r1", nom: "Julie Roy" }, quarts: [QUART] });
    await rendre();
    const ligne = conteneur.querySelector('[data-testid="mho-pl-1"]')!;
    expect(ligne.textContent).toContain("Centre-ville");
    expect(ligne.textContent).toContain("06:00");
    expect(ligne.textContent).toContain("PARA");
    expect(ligne.textContent).toContain("Confirmé");
  });

  it("**un quart sans site affiche « — »**, jamais un site deviné", async () => {
    brancher({ ressource: { id: "r1", nom: "Julie Roy" }, quarts: [{ ...QUART, milieuId: null, milieu: null }] });
    await rendre();
    expect(conteneur.querySelector('[data-testid="mho-pl-1"]')!.textContent).toContain("—");
  });

  it("un échange ouvert sur le quart le dit — et le mot, pas la seule couleur", async () => {
    brancher({ ressource: { id: "r1", nom: "Julie Roy" }, quarts: [QUART] },
             [{ id: "x1", statut: "PROPOSE", placeCedeeId: "pl-1", placeVoulueId: "pl-9" }]);
    await rendre();
    expect(conteneur.querySelector('[data-testid="mho-pl-1"]')!.textContent).toContain("Échange en cours");
  });

  it("un échange **appliqué** ne rend pas le quart incertain", async () => {
    brancher({ ressource: { id: "r1", nom: "Julie Roy" }, quarts: [QUART] },
             [{ id: "x1", statut: "APPLIQUE", placeCedeeId: "pl-1", placeVoulueId: "pl-9" }]);
    await rendre();
    expect(conteneur.querySelector('[data-testid="mho-pl-1"]')!.textContent).toContain("Confirmé");
  });

  it("sans le module des échanges, **aucun appel** n'est fait pour les échanges", async () => {
    modules.autorises = new Set();
    const vus = brancher({ ressource: { id: "r1", nom: "Julie Roy" }, quarts: [QUART] });
    await rendre();
    expect(vus.some((u) => u.includes("/echanges"))).toBe(false);
    expect(conteneur.querySelector('[data-testid="mho-pl-1"]')!.textContent).toContain("Confirmé");
  });

  it("un compte sans ressource lit **pourquoi**, et non un tableau vide", async () => {
    brancher({ ressource: null, quarts: [] });
    await rendre();
    expect(conteneur.querySelector('[data-testid="mho-sans-ressource"]')).not.toBeNull();
    expect(conteneur.querySelector('[data-testid="mho-table"]')).toBeNull();
  });

  it("aucun quart à venir : l'écran le dit, et garde son tableau", async () => {
    brancher({ ressource: { id: "r1", nom: "Julie Roy" }, quarts: [] });
    await rendre();
    expect(conteneur.querySelector('[data-testid="mho-vide"]')).not.toBeNull();
  });

  /*
    **La semaine affichée** (ajoutée le 8 novembre 2026).

    L'écran ne montrait que l'à-venir : au moment où sa feuille de temps demande de vérifier le
    planifié de la semaine écoulée, l'employée ne pouvait plus voir cet horaire. Les quatre essais
    qui suivent portent sur la seule chose qui compte ici — que la fenêtre **pilote la requête**,
    et non qu'un bouton existe.
  */
  it("la semaine va du lundi au dimanche, et le décalage la déplace d'une semaine entière", () => {
    // Un jeudi : la semaine commence le lundi qui précède.
    expect(semaineDe("2026-11-05")).toEqual({ du: "2026-11-02", au: "2026-11-08" });
    // Un dimanche appartient à la semaine commencée le lundi, non à celle qui suit.
    expect(semaineDe("2026-11-08")).toEqual({ du: "2026-11-02", au: "2026-11-08" });
    // Un lundi est son propre début.
    expect(semaineDe("2026-11-02")).toEqual({ du: "2026-11-02", au: "2026-11-08" });
    expect(semaineDe("2026-11-05", -1)).toEqual({ du: "2026-10-26", au: "2026-11-01" });
    expect(semaineDe("2026-11-05", 1)).toEqual({ du: "2026-11-09", au: "2026-11-15" });
  });

  it("**le changement d'heure ne décale pas la semaine** : le calcul se fait à midi", () => {
    // Le 1er novembre 2026 est le dimanche du retour à l'heure normale au Québec. Calculé à
    // minuit local, ce dimanche reculait d'un jour et la semaine sautait — sans rien dire.
    expect(semaineDe("2026-11-01")).toEqual({ du: "2026-10-26", au: "2026-11-01" });
    // Et au passage à l'heure avancée, en mars.
    expect(semaineDe("2026-03-08")).toEqual({ du: "2026-03-02", au: "2026-03-08" });
  });

  it("la fenêtre part dans la requête, et la semaine précédente la change", async () => {
    const vus = brancher({ ressource: { id: "r1", nom: "Julie Roy" }, quarts: [QUART] });
    await rendre();
    const courante = semaineDe(new Date().toISOString().slice(0, 10));
    expect(vus.some((u) => u.includes(`du=${courante.du}`) && u.includes(`au=${courante.au}`))).toBe(true);

    await act(async () => { conteneur.querySelector<HTMLButtonElement>('[data-testid="mho-semaine-precedente"]')!.click(); });
    await act(async () => { await Promise.resolve(); });
    const avant = semaineDe(new Date().toISOString().slice(0, 10), -1);
    expect(vus.some((u) => u.includes(`du=${avant.du}`))).toBe(true);
    // « Cette semaine » n'est pas offert quand on y est déjà : un bouton sans effet se clique quand même.
    expect(conteneur.querySelector<HTMLButtonElement>('[data-testid="mho-semaine-courante"]')!.disabled).toBe(false);
  });

  it("la porte vers l'échange s'ouvre avec le module, et seulement s'il y a un quart à céder", async () => {
    brancher({ ressource: { id: "r1", nom: "Julie Roy" }, quarts: [QUART] });
    await rendre();
    expect(conteneur.querySelector('[data-testid="mho-proposer-echange"]')).not.toBeNull();

    // Sans le module : aucune porte, parce qu'elle mènerait à un écran fermé.
    modules.autorises = new Set();
    act(() => racine.unmount());
    racine = createRoot(conteneur);
    brancher({ ressource: { id: "r1", nom: "Julie Roy" }, quarts: [QUART] });
    await rendre();
    expect(conteneur.querySelector('[data-testid="mho-proposer-echange"]')).toBeNull();
  });

  it("le compte des quarts est annoncé, et l'absence se dit en mots", async () => {
    brancher({ ressource: { id: "r1", nom: "Julie Roy" }, quarts: [QUART] });
    await rendre();
    expect(conteneur.querySelector('[data-testid="mho-fenetre"]')!.textContent).toContain("1 quart");

    act(() => racine.unmount());
    racine = createRoot(conteneur);
    brancher({ ressource: { id: "r1", nom: "Julie Roy" }, quarts: [] });
    await rendre();
    // `role="status"` : la semaine change sans rechargement, et un lecteur d'écran doit l'entendre.
    const etat = conteneur.querySelector('[data-testid="mho-fenetre"]')!;
    expect(etat.getAttribute("role")).toBe("status");
    expect(etat.textContent).toContain("Aucun quart");
  });

  /*
    **Le mois en calendrier** (13 novembre 2026) — la vue par défaut, comme la planche s03.
  */
  it("le mois va du premier au dernier jour, février bissextile compris", () => {
    expect(moisDe(new Date(2026, 10, 1))).toEqual({ du: "2026-11-01", au: "2026-11-30" });
    expect(moisDe(new Date(2028, 1, 1))).toEqual({ du: "2028-02-01", au: "2028-02-29" });
    expect(moisDe(new Date(2026, 11, 1))).toEqual({ du: "2026-12-01", au: "2026-12-31" });
  });

  it("le résumé du mois compte les quarts, ou dit leur absence en mots", () => {
    expect(resumeDuMois(0, new Date(2026, 10, 1))).toBe("Aucun quart publié en novembre 2026");
    expect(resumeDuMois(1, new Date(2026, 10, 1))).toBe("1 quart en novembre 2026");
    expect(resumeDuMois(12, new Date(2026, 10, 1))).toBe("12 quarts en novembre 2026");
  });

  it("**le calendrier est la vue par défaut** : le quart s'inscrit dans sa case, avec son site et son poste", async () => {
    const aujourdhui = new Date();
    const mois = moisDe(new Date(aujourdhui.getFullYear(), aujourdhui.getMonth(), 1));
    const vus = brancher({ ressource: { id: "r1", nom: "Julie Roy" }, quarts: [{ ...QUART, date: mois.du }] });
    await rendre("calendrier");
    // Le mois entier est lu d'un seul appel.
    expect(vus.some((u) => u.includes(`du=${mois.du}`) && u.includes(`au=${mois.au}`))).toBe(true);
    expect(conteneur.querySelector('[data-testid="mho-vue-calendrier"]')!.getAttribute("aria-pressed")).toBe("true");
    expect(conteneur.querySelector('[data-testid="mho-table"]')).toBeNull();
    const caseDuJour = conteneur.querySelector(`[data-testid="mho-cal-day-${mois.du}"]`)!;
    const quart = caseDuJour.querySelector('[data-testid="mho-cal-pl-1"]')!;
    expect(quart.textContent).toContain("06:00–14:00");
    expect(quart.textContent).toContain("Centre-ville");
    expect(quart.textContent).toContain("PARA");
    expect(conteneur.querySelector('[data-testid="mho-fenetre"]')!.textContent).toMatch(/^1 quart en /);
  });

  it("dans le calendrier, un échange ouvert se dit en mots, et un site absent reste « — »", async () => {
    const aujourdhui = new Date();
    const debut = moisDe(new Date(aujourdhui.getFullYear(), aujourdhui.getMonth(), 1)).du;
    brancher({ ressource: { id: "r1", nom: "Julie Roy" }, quarts: [{ ...QUART, date: debut, milieuId: null, milieu: null }] },
             [{ id: "x1", statut: "A_VALIDER", placeCedeeId: "pl-1", placeVoulueId: "pl-9" }]);
    await rendre("calendrier");
    const quart = conteneur.querySelector('[data-testid="mho-cal-pl-1"]')!;
    expect(quart.textContent).toContain("Échange en cours");
    expect(quart.textContent).toContain("—");
  });

  it("le mois suivant change la requête, et la liste ramène la semaine", async () => {
    const vus = brancher({ ressource: { id: "r1", nom: "Julie Roy" }, quarts: [] });
    await rendre("calendrier");
    const d = new Date();
    const suivant = moisDe(new Date(d.getFullYear(), d.getMonth() + 1, 1));
    await act(async () => { conteneur.querySelector<HTMLButtonElement>('[data-testid="mho-cal-next"]')!.click(); });
    await act(async () => { await Promise.resolve(); });
    expect(vus.some((u) => u.includes(`du=${suivant.du}`) && u.includes(`au=${suivant.au}`))).toBe(true);
    await act(async () => { conteneur.querySelector<HTMLButtonElement>('[data-testid="mho-vue-liste"]')!.click(); });
    await act(async () => { await Promise.resolve(); });
    expect(conteneur.querySelector('[data-testid="mho-vue-liste"]')!.getAttribute("aria-pressed")).toBe("true");
    expect(conteneur.querySelector('[data-testid="mho-vide"]')).not.toBeNull();
  });

  it("le bandeau des planches enveloppe l'écran", async () => {
    brancher({ ressource: { id: "r1", nom: "Julie Roy" }, quarts: [QUART] });
    await rendre();
    const bandeau = conteneur.querySelector('[data-testid="maquette-bandeau"]')!;
    expect(bandeau.textContent).toContain("Urgences-santé");
    expect(conteneur.querySelector('[data-testid="maquette-bandeau-contexte"]')!.textContent)
      .toContain("mon horaire");
  });
});
