/**
 * **Paie** (ADR 0076) : préparer un cycle, relire, traiter ou annuler ; tenir les dossiers ; lire remises, relevés et
 * ses propres bulletins ; régler les taux de l'organisme.
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

const { default: CyclesPaie } = await import("../src/paie/CyclesPaie");
const { default: DossiersPaie } = await import("../src/paie/DossiersPaie");
const { default: DeclarationsPaie } = await import("../src/paie/DeclarationsPaie");
const { default: MesBulletins } = await import("../src/paie/MesBulletins");
const { default: ParametresPaie } = await import("../src/paie/ParametresPaie");
const { argent, heures, pourcent, libelleEtat, libelleFrequence, statutBadge } = await import("../src/paie/types");

const CYCLE = { id: "c1", debut: "2026-02-15", fin: "2026-02-28", datePaie: "2026-03-05", etat: "prepare", prepareLe: "x", preparePar: "paie.admin" };
const BULLETIN = {
  id: "b1", cycleId: "c1", ressourceId: "r-julie", matricule: "P1", nom: "Julie Roy", datePaie: "2026-03-05", heuresRegulieres: 70, heuresSupplementaires: 2,
  retenues: { brut: 2190, impotFederal: 150.1, impotQuebec: 190.2, rrq: 129.49, rrq2: 0, ae: 28.47, rqap: 9.42, gainsRrq: 2190, gainsAe: 2190, gainsRqap: 2190,
    employeur: { rrq: 129.49, rrq2: 0, ae: 39.86, rqap: 13.18, fss: 93.29, cnt: 1.31, cnesst: 38.98 } },
  totalRetenues: 507.68, net: 1682.32, cotisationsEmployeur: 316.11, coutEmployeur: 2506.11, feuilles: ["f1", "f2"],
};
const PREPARATION = { cycle: CYCLE, bulletins: [BULLETIN], sansDossier: [{ ressourceId: "r-marc", matricule: "P2", nom: "Marc Dubé" }] };

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

describe("formats", () => {
  it("argent, heures et taux à la québécoise ; états et fréquences en mots ; badge doublé d'un texte", () => {
    expect(argent(1234.5).replace(/\s/g, " ")).toBe("1 234,50 $");
    expect(argent(null)).toBe("—");
    expect(heures(7.5)).toBe("7,5 h");
    expect(heures(undefined)).toBe("—");
    expect(pourcent(0.0426).replace(/\s/g, " ")).toBe("4,26 %");
    expect(pourcent(null)).toBe("—");
    expect([libelleEtat("prepare"), libelleEtat("traite"), libelleEtat("annule")]).toEqual(["Préparé", "Traité", "Annulé"]);
    expect(libelleFrequence("aux-deux-semaines")).toBe("Aux deux semaines (26 paies)");
    expect(libelleFrequence("x" as never)).toBe("x");
    expect([statutBadge("prepare"), statutBadge("traite"), statutBadge("annule")]).toEqual(["en_attente", "paid", "clos"]);
  });
});

describe("Cycles de paie", () => {
  beforeEach(() => {
    api.etat.routes = {
      "GET /api/paie/cycles/c1": { cycle: CYCLE, bulletins: [BULLETIN] },
      "GET /api/paie/cycles": [CYCLE],
      "POST /api/paie/cycles/c1/recalculer": PREPARATION,
      "POST /api/paie/cycles/c1/traiter": { ...CYCLE, etat: "traite" },
      "POST /api/paie/cycles/c1/annuler": { ...CYCLE, etat: "annule" },
      "POST /api/paie/cycles": PREPARATION,
    };
  });

  it("préparer : la période part au serveur ; les bulletins se lisent ; une personne sans dossier est nommée", async () => {
    await rendre(<CyclesPaie />);
    expect($("pcy-cycle-c1")!.textContent).toContain("Préparé");
    await cliquer("pcy-preparer-ouvrir");
    await saisir("pcy-debut", "2026-02-15");
    await saisir("pcy-fin", "2026-02-28");
    await saisir("pcy-date-paie", "2026-03-05");
    await soumettre("Préparer un cycle de paie");
    expect(envoisVers("/api/paie/cycles").at(-1)!.corps).toEqual({ debut: "2026-02-15", fin: "2026-02-28", datePaie: "2026-03-05" });
    expect($("pcy-succes")!.textContent).toBe("Cycle préparé : 1 bulletin(s) à relire.");
    expect($("pcy-sans-dossier")!.textContent).toContain("Marc Dubé (P2)");
    expect($("pcy-bulletin-r-julie")!.textContent).toContain("Julie Roy");
    await cliquer("pcy-bulletin-r-julie-voir");
    expect($("pcy-b-net")!.textContent!.replace(/\s/g, " ")).toContain("1 682,32 $");
    expect($("pcy-b-detail")!.textContent).toContain("2 feuilles de temps");
  });

  it("ouvrir, recalculer, traiter après confirmation ; un cycle traité n'offre plus de geste", async () => {
    await rendre(<CyclesPaie />);
    await cliquer("pcy-cycle-c1-voir");
    expect($("pcy-sans-dossier")).toBeNull();
    await cliquer("pcy-recalculer");
    expect($("pcy-succes")!.textContent).toBe("Cycle recalculé : 1 bulletin(s).");
    await cliquer("pcy-traiter");
    expect($("pcy-confirmer-traiter")).not.toBeNull();
    api.etat.routes["GET /api/paie/cycles/c1"] = { cycle: { ...CYCLE, etat: "traite" }, bulletins: [BULLETIN] };
    await cliquer("pcy-confirmer-traiter-confirm");
    expect(envoisVers("/c1/traiter")).toHaveLength(1);
    expect($("pcy-succes")!.textContent).toBe("Paie du 2026-03-05 traitée : les feuilles payées ne se modifient plus.");
    expect($("pcy-traiter")).toBeNull();
  });

  it("annuler après confirmation ; un refus du serveur s'affiche et ne change rien", async () => {
    await rendre(<CyclesPaie />);
    await cliquer("pcy-cycle-c1-voir");
    await cliquer("pcy-annuler");
    await cliquer("pcy-confirmer-annuler-confirm");
    expect($("pcy-succes")!.textContent).toBe("Cycle annulé : ses feuilles restent approuvées.");
    api.etat.routes["POST /api/paie/cycles/c1/traiter"] = { errors: ["la feuille f2 n'est plus approuvée"] };
    await cliquer("pcy-cycle-c1-voir");
    await cliquer("pcy-traiter");
    await cliquer("pcy-confirmer-traiter-confirm");
    expect($("pcy-erreur")!.textContent).toContain("n'est plus approuvée");
  });

  it("aucun cycle : l'état vide le dit", async () => {
    api.etat.routes["GET /api/paie/cycles"] = [];
    await rendre(<CyclesPaie />);
    expect($("pcy-vide")).not.toBeNull();
  });
});

describe("Dossiers de rémunération", () => {
  beforeEach(() => {
    api.etat.routes = {
      "GET /api/paie/dossiers": [
        { ressourceId: "r-julie", matricule: "P1", nom: "Julie Roy", dossier: { ressourceId: "r-julie", mode: "horaire", frequence: "aux-deux-semaines",
          tauxHoraire: 32.5, salaireAnnuel: null, montantPersonnelFederal: 16452, montantPersonnelQuebec: 18952 } },
        { ressourceId: "r-lea", matricule: "P3", nom: "Léa Côté", dossier: { ressourceId: "r-lea", mode: "salaire", frequence: "mensuelle",
          tauxHoraire: null, salaireAnnuel: 72000, montantPersonnelFederal: 16452, montantPersonnelQuebec: 18952 } },
        { ressourceId: "r-marc", matricule: "P2", nom: "Marc Dubé", dossier: null }],
      "PUT /api/paie/dossiers/r-marc": { ressourceId: "r-marc" },
    };
  });

  it("la liste dit qui manque ; créer un dossier au salaire envoie ce qui est saisi", async () => {
    await rendre(<DossiersPaie />);
    expect($("pdo-manquants")!.textContent).toBe("1 personne(s) sans dossier de rémunération.");
    expect($("pdo-r-julie")!.textContent!.replace(/\s/g, " ")).toContain("32,50 $ l'heure");
    expect($("pdo-r-lea")!.textContent!.replace(/\s/g, " ")).toContain("72 000,00 $ par année");
    expect($("pdo-r-marc")!.textContent).toContain("Sans dossier");
    await cliquer("pdo-r-marc-modifier");
    await saisir("pdo-mode", "salaire");
    await saisir("pdo-frequence", "mensuelle");
    await saisir("pdo-salaire", "65000");
    await saisir("pdo-taux", "30");
    await saisir("pdo-quebec", "20000");
    await saisir("pdo-federal", "17000");
    await soumettre("Dossier de rémunération de Marc Dubé");
    expect(envoisVers("/dossiers/r-marc").at(-1)).toMatchObject({ methode: "PUT", corps: { mode: "salaire", frequence: "mensuelle", salaireAnnuel: "65000",
      tauxHoraire: "30", montantPersonnelQuebec: "20000", montantPersonnelFederal: "17000" } });
    expect($("pdo-succes")!.textContent).toBe("Dossier de Marc Dubé enregistré.");
  });

  it("modifier reprend le dossier ; un refus reste affiché", async () => {
    api.etat.routes["PUT /api/paie/dossiers/r-julie"] = { errors: ["« tauxHoraire » doit être un nombre"] };
    await rendre(<DossiersPaie />);
    await cliquer("pdo-r-julie-modifier");
    expect(($("pdo-taux") as HTMLInputElement).value).toBe("32.5");
    expect($("pdo-salaire")).toBeNull();
    await soumettre("Dossier de rémunération de Julie Roy");
    expect($("pdo-erreur")!.textContent).toContain("tauxHoraire");
  });
});

describe("Remises, relevés et bulletins de chacun", () => {
  it("remises et relevés de l'année choisie", async () => {
    api.etat.routes = {
      "GET /api/paie/remises": [{ mois: "2026-03", echeance: "2026-04-15", impotQuebec: 1, rrq: 1, rqap: 1, fss: 1, revenuQuebec: 612.34, impotFederal: 1, ae: 1, arc: 230.1, cnt: 1.31, cnesst: 38.98 }],
      "GET /api/paie/releves": [{ ressourceId: "r-julie", matricule: "P1", nom: "Julie Roy",
        rl1: { a: 2190, b: 129.49, rrq2: 0, c: 28.47, e: 190.2, g: 2190, h: 9.42, i: 2190 },
        t4: { c14: 2190, c17: 129.49, rrq2: 0, c18: 28.47, c22: 150.1, c24: 2190, c26: 2190, c55: 9.42, c56: 2190 } }],
    };
    await rendre(<DeclarationsPaie />);
    expect($("pde-remise-2026-03")!.textContent!.replace(/\s/g, " ")).toContain("612,34 $");
    expect($("pde-remise-2026-03")!.textContent).toContain("2026-04-15");
    expect($("pde-releve-r-julie")!.textContent!.replace(/\s/g, " ")).toContain("2 190,00 $");
    const annee = String(new Date().getFullYear() - 1);
    await saisir("pde-annee", annee);
    expect(api.etat.appels.some((a) => a.url === `/api/paie/releves?annee=${annee}`)).toBe(true);
  });

  it("sans paie traitée, les tableaux le disent", async () => {
    api.etat.routes = { "GET /api/paie/remises": [], "GET /api/paie/releves": [] };
    await rendre(<DeclarationsPaie />);
    expect($("pde-remises-vide")).not.toBeNull();
    expect($("pde-releves-vide")).not.toBeNull();
  });

  it("mes bulletins : la liste, puis le détail ; aucun bulletin : le message", async () => {
    api.etat.routes = { "GET /api/paie/mes-bulletins": [BULLETIN] };
    await rendre(<MesBulletins />);
    expect($("mbu-b1")!.textContent).toContain("72 h");
    await cliquer("mbu-b1-voir");
    expect($("mbu-b-brut")!.textContent!.replace(/\s/g, " ")).toContain("2 190,00 $");
    expect($("mbu-b-employeur")!.textContent).toContain("Coût total pour l'employeur");
    act(() => racine.unmount());
    racine = createRoot(conteneur);
    api.etat.routes = { "GET /api/paie/mes-bulletins": [] };
    await rendre(<MesBulletins />);
    expect($("mbu-vide")).not.toBeNull();
  });
});

describe("Paramètres de la paie", () => {
  beforeEach(() => {
    api.etat.routes = {
      "GET /api/paie/reglages": { annee: 2026, tauxFss: 0.0426, tauxCnesst: 0.0178, majorationSupplementaire: 0.5 },
      "GET /api/paie/bareme": { annee: 2026, rrq: { tauxBase: 0.053, tauxSupplementaire: 0.01, exemption: 3500, mga: 74600, plafondSupplementaire: 85000, tauxRrq2: 0.04 },
        ae: { tauxEmploye: 0.013, maximumAssurable: 68900 }, rqap: { tauxEmploye: 0.0043, tauxEmployeur: 0.00602, maximumAssurable: 103000 },
        federal: { montantPersonnel: 16452 }, quebec: { montantPersonnel: 18952 } },
      "PUT /api/paie/reglages": { annee: 2026, tauxFss: 0.0165, tauxCnesst: 0.031, majorationSupplementaire: 0.5 },
    };
  });

  it("les taux s'affichent en pour cent et partent en fraction ; le barème se lit", async () => {
    await rendre(<ParametresPaie />);
    expect(($("ppa-fss") as HTMLInputElement).value).toBe("4.26");
    expect(($("ppa-majoration") as HTMLInputElement).value).toBe("50");
    expect($("ppa-bareme")!.textContent!.replace(/\s/g, " ")).toContain("74 600,00 $");
    await saisir("ppa-fss", "1.65");
    await saisir("ppa-cnesst", "3.1");
    await soumettre("Réglages de la paie");
    expect(envoisVers("/api/paie/reglages").at(-1)!.corps).toEqual({ tauxFss: "0.0165", tauxCnesst: "0.031", majorationSupplementaire: "0.5" });
    expect($("ppa-succes")!.textContent).toContain("Réglages de la paie enregistrés");
  });

  it("un refus s'affiche", async () => {
    api.etat.routes["PUT /api/paie/reglages"] = { errors: ["« majorationSupplementaire » : au moins 0,5"] };
    await rendre(<ParametresPaie />);
    await soumettre("Réglages de la paie");
    expect($("ppa-erreur")!.textContent).toContain("au moins 0,5");
  });
});
