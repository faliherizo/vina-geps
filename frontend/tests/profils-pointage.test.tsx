/**
 * **Les profils de pointage, vus des deux écrans** (AO 26-015-DO : E-GP-1, E-GP-2, P-GP-1).
 *
 * Ce qui est éprouvé :
 *
 * - **l'écran de l'employé suit son profil** : un profil « après coup » n'offre aucun bouton de
 *   pointage, un profil « temps réel » n'offre pas le formulaire de déclaration. Afficher un geste
 *   que le serveur refusera fait porter à la personne un refus qui n'est pas le sien ;
 * - **la nature de la pause se choisit** et part avec le segment (E-GP-2) ;
 * - **l'écran de configuration dit ce que chaque mode interdit**, et non son code ;
 * - **« aucune limite » est un état affiché**, pas un champ vide.
 */
import { act } from "react";
import { createRoot, type Root } from "react-dom/client";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import MonPointage from "../src/MonPointage";
import ProfilsPointage from "../src/effectifs/presence/ProfilsPointage";

(globalThis as unknown as { IS_REACT_ACT_ENVIRONMENT: boolean }).IS_REACT_ACT_ENVIRONMENT = true;

/**
 * Le contexte d'authentification, remplacé par un porteur mutable.
 *
 * `useApiEffectifs` lit `apiFetch` du contexte ; monter un vrai `AuthProvider` obligerait à
 * simuler une session complète pour éprouver un sélecteur de nature.
 *
 * **L'objet rendu et sa fonction sont stables**, et ce n'est pas un détail : les écrans
 * enveloppent `apiFetch` dans des `useCallback`, et un `useEffect` qui en dépend se rejouerait à
 * chaque rendu si l'identité changeait — le rendu ne se stabiliserait jamais, et l'essai
 * expirerait au lieu d'échouer.
 */
const auth = vi.hoisted(() => {
  const porteur: { appel: (url: string, init?: RequestInit) => Promise<Response> } = {
    appel: () => Promise.reject(new Error("aucun appel branché")),
  };
  const stable = { apiFetch: (url: string, init?: RequestInit) => porteur.appel(url, init) };
  return { porteur, useAuth: () => stable };
});
vi.mock("../src/AuthContext", () => ({ useAuth: auth.useAuth }));

const reponse = (corps: unknown, ok = true) =>
  ({ ok, status: ok ? 200 : 400, json: () => Promise.resolve(corps) }) as unknown as Response;

const profil = (o: Record<string, unknown> = {}) => ({
  code: "DEF", libelle: "Profil par défaut (les deux modes)", mode: "MIXTE",
  porteeType: "ORGANISME", porteeCode: null,
  naturesNonPayees: ["PAUSE", "REPAS", "REPOS"], pausesManuelles: false,
  presencePrealableMinutes: 15, fenetreAPosterioriJours: null, actif: true, ...o,
});

const MA_PRESENCE = {
  ressource: { id: "r1", matricule: "P1", nom: "Julie" },
  quart: {
    milieuId: "m-c12", milieu: "Caserne 12",
    debutPrevu: "2026-09-30T10:00:00.000Z", finPrevue: "2026-09-30T22:00:00.000Z",
    segments: [], minutesPayees: 0, prochains: ["ENTREE"],
    parMilieu: [], parActivite: [], deplacements: [],
  },
  pointages: [],
};

/** Un quart fait sur deux sites, tel que l'API le rend (E-GP-7). */
const SUR_DEUX_SITES = {
  ...MA_PRESENCE,
  quart: {
    ...MA_PRESENCE.quart,
    minutesPayees: 300,
    parMilieu: [
      { milieuId: "m-cs", milieu: "Caserne Sud", minutes: 180 },
      { milieuId: "m-c12", milieu: "Caserne 12", minutes: 120 },
    ],
    parActivite: [
      { activiteCode: "GARDE", minutes: 180 },
      { activiteCode: "INTERV", minutes: 120 },
    ],
    deplacements: [{
      quand: "2026-09-30T14:00:00.000Z", de: "m-c12", vers: "m-cs",
      deLibelle: "Caserne 12", versLibelle: "Caserne Sud",
    }],
  },
};

const MILIEUX = [
  { id: "m-c12", code: "C12", libelle: "Caserne 12" },
  { id: "m-cs", code: "CS", libelle: "Caserne Sud" },
];

const ACTIVITES = [
  { code: "GARDE", libelleFr: "Disponibilité en caserne" },
  { code: "INTERV", libelleFr: "Intervention" },
];

describe("Mon pointage — l'écran suit le profil (E-GP-1, P-GP-1)", () => {
  let conteneur: HTMLDivElement;
  let racine: Root;
  let envois: Array<{ url: string; corps: any }>;

  beforeEach(() => {
    conteneur = document.createElement("div");
    document.body.appendChild(conteneur);
    racine = createRoot(conteneur);
    envois = [];
  });
  afterEach(() => { act(() => racine.unmount()); conteneur.remove(); });

  const tid = (id: string) => conteneur.querySelector(`[data-testid="${id}"]`) as HTMLElement | null;
  const pause = () => new Promise((r) => setTimeout(r, 5));
  const saisir = (id: string, v: string) => act(async () => {
    const el = tid(id)!;
    const proto = el instanceof HTMLTextAreaElement ? HTMLTextAreaElement.prototype
      : el instanceof HTMLSelectElement ? HTMLSelectElement.prototype : HTMLInputElement.prototype;
    Object.getOwnPropertyDescriptor(proto, "value")!.set!.call(el, v);
    el.dispatchEvent(new Event(el instanceof HTMLSelectElement ? "change" : "input", { bubbles: true }));
    await pause();
  });

  const monter = async (p: Record<string, unknown>, presenceRendue: unknown = MA_PRESENCE) => {
    const appel = vi.fn((url: string, init?: RequestInit) => {
      if ((init?.method ?? "GET") !== "GET") {
        envois.push({ url, corps: init?.body ? JSON.parse(String(init.body)) : null });
        return Promise.resolve(reponse([{ id: "x", sens: "ENTREE" }]));
      }
      if (url.endsWith("/mon-profil")) return Promise.resolve(reponse({ profil: p }));
      if (url.endsWith("/presence/moi")) return Promise.resolve(reponse(presenceRendue));
      if (url.endsWith("/effectifs/milieux")) return Promise.resolve(reponse(MILIEUX));
      if (url.endsWith("/referentiels/activites_presence")) return Promise.resolve(reponse(ACTIVITES));
      return Promise.resolve(reponse(null, false));
    });
    auth.porteur.appel = appel;
    await act(async () => {
      racine.render(<MonPointage />);
      await pause();
    });
    return appel;
  };

  it("**un profil « après coup » n'offre aucun bouton de pointage**", async () => {
    await monter(profil({ mode: "A_POSTERIORI", libelle: "Cadres — après coup", fenetreAPosterioriJours: 30 }));
    expect(tid("mpo-profil-a-posteriori")!.textContent).toContain("déclaration après coup");
    expect(tid("mpo-ENTREE")).toBeNull();
    // Et le formulaire de déclaration, lui, est bien offert.
    expect(tid("mpo-declarer-ouvrir")).not.toBeNull();
  });

  it("**un profil « temps réel » n'offre pas le formulaire de déclaration**", async () => {
    await monter(profil({ mode: "TEMPS_REEL", libelle: "Paramédics — temps réel" }));
    expect(tid("mpo-profil-temps-reel")!.textContent).toContain("pointage en temps réel");
    expect(tid("mpo-declarer-ouvrir")).toBeNull();
    expect(tid("mpo-ENTREE")).not.toBeNull();
  });

  it("le profil par défaut offre **les deux**", async () => {
    await monter(profil());
    expect(tid("mpo-ENTREE")).not.toBeNull();
    expect(tid("mpo-declarer-ouvrir")).not.toBeNull();
    expect(tid("mpo-profil-temps-reel")).toBeNull();
    expect(tid("mpo-profil-a-posteriori")).toBeNull();
  });

  it("**la fenêtre est annoncée** quand il y en a une, et taire quand il n'y en a pas", async () => {
    // La déclaration est un dialogue (8 novembre 2026) : la fenêtre s'annonce **dans** le dialogue
    // ouvert, là où l'on saisit la date qu'elle borne.
    await monter(profil({ fenetreAPosterioriJours: 14 }));
    await act(async () => { tid("mpo-declarer-ouvrir")!.click(); await pause(); });
    expect(tid("mpo-declarer-modal")!.textContent).toContain("14 derniers");
    await act(async () => { racine.unmount(); });
    racine = createRoot(conteneur);
    await monter(profil());
    await act(async () => { tid("mpo-declarer-ouvrir")!.click(); await pause(); });
    expect(tid("mpo-declarer-modal")!.textContent).not.toContain("derniers");
  });

  it("**le site se choisit**, et le site du quart est le défaut nommé (E-GP-7)", async () => {
    await monter(profil());
    const options = [...(tid("mpo-site") as HTMLSelectElement).options].map((o) => [o.value, o.textContent]);
    // Le site du quart est l'option vide — le défaut —, et il n'apparaît pas deux fois.
    expect(options).toEqual([["", "Caserne 12 (le site de mon quart)"], ["m-cs", "CS — Caserne Sud"]]);
    await saisir("mpo-site", "m-cs");
    await act(async () => { tid("mpo-ENTREE")!.click(); await pause(); });
    expect(envois[0].corps).toMatchObject({ sens: "ENTREE", milieuId: "m-cs" });
  });

  it("sans site choisi, **rien n'est transmis** : c'est le site du quart qui s'applique", async () => {
    await monter(profil());
    await act(async () => { tid("mpo-ENTREE")!.click(); await pause(); });
    expect(envois[0].corps.milieuId).toBeUndefined();
  });

  it("**la répartition par site et les déplacements s'affichent**, et se taisent sur un seul site", async () => {
    await monter(profil(), SUR_DEUX_SITES);
    expect(tid("mpo-par-milieu")!.textContent).toContain("Caserne Sud");
    expect(tid("mpo-par-milieu")!.textContent).toContain("Caserne 12");
    expect(tid("mpo-deplacements")!.textContent).toContain("Caserne 12 → Caserne Sud");

    await act(async () => { racine.unmount(); });
    racine = createRoot(conteneur);
    await monter(profil());
    // Un quart sur un seul site ne détaille rien : la liste n'apparaît pas.
    expect(tid("mpo-par-milieu")).toBeNull();
    expect(tid("mpo-deplacements")).toBeNull();
  });

  it("**l'activité se déclare en commençant à travailler**, et pas en s'arrêtant (E-GP-15)", async () => {
    await monter(profil());
    const options = [...(tid("mpo-activite") as HTMLSelectElement).options].map((o) => o.value);
    expect(options).toEqual(["", "GARDE", "INTERV"]);
    await saisir("mpo-activite", "INTERV");
    await act(async () => { tid("mpo-ENTREE")!.click(); await pause(); });
    expect(envois[0].corps.activiteCode).toBe("INTERV");
  });

  it("**l'activité ne part pas avec une sortie** : le serveur la refuserait", async () => {
    // Le quart est en cours : seule la sortie ou la pause restent à pointer.
    await monter(profil(), {
      ...MA_PRESENCE,
      quart: { ...MA_PRESENCE.quart, prochains: ["DEBUT_PAUSE", "SORTIE"] },
    });
    // Aucun geste d'ouverture de travail n'est possible : le sélecteur ne paraît pas.
    expect(tid("mpo-activite")).toBeNull();
    await act(async () => { tid("mpo-SORTIE")!.click(); await pause(); });
    expect(envois[0].corps.activiteCode).toBeUndefined();
  });

  it("**la répartition par activité s'affiche**, et se tait quand aucune n'est déclarée", async () => {
    await monter(profil(), SUR_DEUX_SITES);
    expect(tid("mpo-par-activite")!.textContent).toContain("Disponibilité en caserne");
    expect(tid("mpo-par-activite")!.textContent).toContain("Intervention");
    await act(async () => { racine.unmount(); });
    racine = createRoot(conteneur);
    await monter(profil());
    expect(tid("mpo-par-activite")).toBeNull();
  });

  it("**la saisie après coup porte l'activité sur les segments de travail** (E-GP-15)", async () => {
    await monter(profil());
    await act(async () => { tid("mpo-declarer-ouvrir")!.click(); await pause(); });
    await saisir("mpo-s-date", "2026-09-01");
    await saisir("mpo-s-entree", "08:00");
    await saisir("mpo-s-pause-debut", "12:00");
    await saisir("mpo-s-pause-fin", "12:30");
    await saisir("mpo-s-sortie", "16:00");
    await saisir("mpo-s-activite", "INTERV");
    await saisir("mpo-s-justification", "Borne hors service toute la journée");
    await act(async () => { tid("mpo-s-envoyer")!.click(); await pause(); });
    const segments = envois[0].corps.segments;
    expect(segments.map((s: any) => [s.type, s.activiteCode ?? null])).toEqual([
      ["TRAVAIL", "INTERV"], ["PAUSE", null], ["TRAVAIL", "INTERV"],
    ]);
  });

  it("**la nature de la pause se choisit et part avec le segment** (E-GP-2)", async () => {
    await monter(profil({ naturesNonPayees: ["PAUSE", "REPAS"] }));
    // Le dialogue **ouvert d'abord** : constater l'absence du sélecteur dialogue fermé serait vrai
    // d'office, et l'essai ne prouverait plus qu'il attend la pause pour paraître.
    await act(async () => { tid("mpo-declarer-ouvrir")!.click(); await pause(); });
    expect(tid("mpo-s-date")).not.toBeNull();
    // Le sélecteur ne paraît qu'une fois la pause saisie : sans pause, il ne qualifie rien.
    expect(tid("mpo-s-nature")).toBeNull();
    await saisir("mpo-s-date", "2026-09-01");
    await saisir("mpo-s-entree", "08:00");
    await saisir("mpo-s-pause-debut", "12:00");
    await saisir("mpo-s-pause-fin", "12:30");
    const options = [...(tid("mpo-s-nature") as HTMLSelectElement).options].map((o) => o.value);
    expect(options).toEqual(["PAUSE", "REPAS"]);
    await saisir("mpo-s-nature", "REPAS");
    await saisir("mpo-s-sortie", "16:00");
    await saisir("mpo-s-justification", "Borne hors service toute la journée");
    await act(async () => { tid("mpo-s-envoyer")!.click(); await pause(); });
    const segments = envois[0].corps.segments;
    expect(segments).toHaveLength(3);
    expect(segments[1]).toMatchObject({ type: "PAUSE", nature: "REPAS" });
    expect(segments[0].nature).toBeUndefined();
  });
});

describe("Profils de pointage — configuration (E-GP-1, E-GP-2)", () => {
  let conteneur: HTMLDivElement;
  let racine: Root;
  let envois: Array<{ url: string; methode: string; corps: any }>;

  beforeEach(() => {
    conteneur = document.createElement("div");
    document.body.appendChild(conteneur);
    racine = createRoot(conteneur);
    envois = [];
  });
  afterEach(() => { act(() => racine.unmount()); conteneur.remove(); });

  const tid = (id: string) => conteneur.querySelector(`[data-testid="${id}"]`) as HTMLElement | null;
  const pause = () => new Promise((r) => setTimeout(r, 5));
  const cliquer = (id: string) => act(async () => { tid(id)!.click(); await pause(); });
  const saisir = (id: string, v: string) => act(async () => {
    const el = tid(id)!;
    const proto = el instanceof HTMLSelectElement ? HTMLSelectElement.prototype : HTMLInputElement.prototype;
    Object.getOwnPropertyDescriptor(proto, "value")!.set!.call(el, v);
    el.dispatchEvent(new Event(el instanceof HTMLSelectElement ? "change" : "input", { bubbles: true }));
    await pause();
  });

  const monter = async (profils: unknown[] = []) => {
    const appel = vi.fn((url: string, init?: RequestInit) => {
      const methode = (init?.method ?? "GET").toUpperCase();
      if (methode !== "GET") {
        envois.push({ url, methode, corps: init?.body ? JSON.parse(String(init.body)) : null });
        return Promise.resolve(reponse(profil()));
      }
      if (url.endsWith("/referentiels-profils")) {
        return Promise.resolve(reponse({
          modes: ["TEMPS_REEL", "A_POSTERIORI", "MIXTE"],
          naturesNonPayees: ["PAUSE", "REPAS", "REPOS"],
        }));
      }
      if (url.endsWith("/profils")) return Promise.resolve(reponse(profils));
      return Promise.resolve(reponse(null, false));
    });
    auth.porteur.appel = appel;
    await act(async () => {
      racine.render(<ProfilsPointage />);
      await pause();
    });
    return appel;
  };

  it("sans profil, l'écran dit que **les deux modes sont admis pour tout le monde**", async () => {
    await monter();
    expect(tid("pfl-vide")!.textContent).toContain("admet le pointage en temps réel comme la déclaration après coup");
  });

  it("**chaque mode dit ce qu'il interdit**, dans la liste comme au sélecteur", async () => {
    await monter([profil({ code: "TR", mode: "TEMPS_REEL" })]);
    expect(tid("pfl-TR")!.textContent).toContain("refuse la déclaration après coup");
    const options = [...(tid("pfl-mode") as HTMLSelectElement).options].map((o) => o.textContent);
    expect(options).toEqual([
      "Temps réel — refuse la déclaration après coup",
      "Après coup — refuse le pointage en temps réel",
      "Les deux — admet les deux",
    ]);
  });

  it("**« aucune limite » est affiché**, et non un vide", async () => {
    await monter([profil({ code: "SANS" }), profil({ code: "AVEC", fenetreAPosterioriJours: 14 })]);
    expect(tid("pfl-SANS")!.textContent).toContain("aucune limite");
    expect(tid("pfl-AVEC")!.textContent).toContain("14 j");
  });

  it("**le code de portée ne paraît que si la portée en exige un**", async () => {
    await monter();
    expect(tid("pfl-portee-code")).toBeNull();
    await saisir("pfl-portee", "CORPS_EMPLOI");
    expect(tid("pfl-portee-code")).not.toBeNull();
    // Et il bloque l'enregistrement tant qu'il est vide.
    await saisir("pfl-code", "PARA-TR");
    expect((tid("pfl-poser") as HTMLButtonElement).disabled).toBe(true);
    await saisir("pfl-portee-code", "PARA");
    expect((tid("pfl-poser") as HTMLButtonElement).disabled).toBe(false);
  });

  it("**un champ de fenêtre vide part comme « aucune limite »**, jamais comme zéro", async () => {
    await monter();
    await saisir("pfl-code", "ORG");
    await cliquer("pfl-poser");
    expect(envois[0].methode).toBe("PUT");
    expect(envois[0].corps.fenetreAPosterioriJours).toBeNull();
    expect(envois[0].corps.porteeCode).toBeNull();
  });

  it("retirer un profil **dit que la capacité revient**", async () => {
    await monter([profil({ code: "TR", mode: "TEMPS_REEL", libelle: "Temps réel" })]);
    await cliquer("pfl-retirer");
    expect(envois[0].methode).toBe("DELETE");
    expect(tid("pfl-info")!.textContent).toContain("reviennent au profil par défaut, qui admet les deux modes");
  });

  it("**aucune nature cochée bloque l'enregistrement** : sans elle, rien ne se déclare", async () => {
    await monter();
    await saisir("pfl-code", "X");
    for (const n of ["PAUSE", "REPAS", "REPOS"]) await cliquer(`pfl-nature-${n}`);
    expect((tid("pfl-poser") as HTMLButtonElement).disabled).toBe(true);
  });
});
