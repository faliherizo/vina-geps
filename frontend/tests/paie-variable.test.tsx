/**
 * **Paie, ADR 0084** : règles de paie variable, validation des temps, gains sur la fiche, fiches et registre en PDF,
 * échelon au dossier, exigence de validation dans les réglages.
 */
import { act } from "react";
import { createRoot, type Root } from "react-dom/client";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

(globalThis as unknown as { IS_REACT_ACT_ENVIRONMENT: boolean }).IS_REACT_ACT_ENVIRONMENT = true;

type Appel = { url: string; methode: string; corps: unknown };
const api = vi.hoisted(() => {
  const etat: { routes: Record<string, unknown>; appels: Appel[]; statutPdf: number } = { routes: {}, appels: [], statutPdf: 200 };
  const apiFetch = (url: string, init: RequestInit = {}) => {
    const methode = init.method ?? "GET";
    etat.appels.push({ url, methode, corps: init.body ? JSON.parse(String(init.body)) : null });
    if (url.endsWith("pdf")) {
      const ok = etat.statutPdf === 200;
      return Promise.resolve({ ok, status: etat.statutPdf, blob: () => Promise.resolve(new Blob(["%PDF-"])),
        headers: new Headers(url.includes("registre") ? {} : { "Content-Disposition": "attachment; filename=\"fiche-paie-P1-2026-03-05.pdf\"" }) } as Response);
    }
    const cle = Object.keys(etat.routes).sort((a, b) => b.length - a.length).find((k) => `${methode} ${url}`.startsWith(k));
    const corps = cle ? etat.routes[cle] : { errors: ["refusé"] };
    const ok = cle !== undefined && !(corps as { errors?: unknown })?.errors;
    return Promise.resolve({ ok, status: ok ? 200 : 400, json: () => Promise.resolve(corps) } as Response);
  };
  return { etat, useAuth: () => ({ apiFetch, user: { id: "u1", username: "comptable", role: "accountant" } }) };
});
vi.mock("../src/AuthContext", () => ({ useAuth: api.useAuth }));

const { default: ReglesPaie, valeurLisible, conditionsLisibles } = await import("../src/paie/ReglesPaie");
const { default: ValidationTemps, periodeParDefaut } = await import("../src/paie/ValidationTemps");
const { default: CyclesPaie } = await import("../src/paie/CyclesPaie");
const { default: MesBulletins } = await import("../src/paie/MesBulletins");
const { default: DossiersPaie } = await import("../src/paie/DossiersPaie");
const { default: ParametresPaie } = await import("../src/paie/ParametresPaie");
const { libelleTypeRegle, libelleAnomalie, libelleEtatFeuille } = await import("../src/paie/types");

const REGLE_TAUX = { id: "t1", code: "TAUX_INF_E3", libelle: "Infirmière, échelon 3", type: "taux-de-base", valeur: 34.5, titreEmploi: "INF", convention: "FIQ",
  echelon: 3, heureDebut: null, heureFin: null, jours: [], feriesSeulement: false, codesPaie: [], valideDu: "2026-01-01", valideAu: null, actif: true };
const REGLE_NUIT = { id: "n1", code: "PRIME_NUIT", libelle: "Prime de nuit", type: "prime-pourcentage", valeur: 0.12, titreEmploi: null, convention: null,
  echelon: null, heureDebut: "00:00", heureFin: "08:00", jours: [6, 7], feriesSeulement: true, codesPaie: ["REG"], valideDu: "2026-01-01", valideAu: "2026-12-31", actif: false };

const CYCLE = { id: "c1", debut: "2026-02-15", fin: "2026-02-28", datePaie: "2026-03-05", etat: "traite", prepareLe: "x", preparePar: "comptable" };
const BULLETIN = {
  id: "b1", cycleId: "c1", ressourceId: "r-julie", matricule: "P1", nom: "Julie Roy", datePaie: "2026-03-05", heuresRegulieres: 35, heuresSupplementaires: 2,
  retenues: { brut: 1161, impotFederal: 80, impotQuebec: 100, rrq: 60, rrq2: 0, ae: 15, rqap: 5, gainsRrq: 1161, gainsAe: 1161, gainsRqap: 1161,
    employeur: { rrq: 60, rrq2: 0, ae: 21, rqap: 7, fss: 49, cnt: 1, cnesst: 20 } },
  totalRetenues: 260, net: 901, cotisationsEmployeur: 158, coutEmployeur: 1319, feuilles: ["f1"],
  gains: [
    { code: "REG", libelle: "Heures régulières", nature: "heures-regulieres", quantite: 35, taux: 30, montant: 1050 },
    { code: "PRIME_SOIR", libelle: "Prime de soir", nature: "prime-pourcentage", quantite: 7, taux: 0.1, montant: 21 },
    { code: "SAL", libelle: "Salaire", nature: "salaire", quantite: 1, taux: 90, montant: 90 },
  ],
};

const LIGNE = (id: string, extra: Record<string, unknown>) => ({ feuilleId: id, ressourceId: "r1", matricule: "P1", nom: "Julie Roy", debut: "2026-02-16",
  etat: "approuvee", heuresRegulieres: 35, heuresSupplementaires: 0, anomalies: [], incalculable: null, validee: false, validable: true, validation: null, ...extra });

let conteneur: HTMLDivElement;
let racine: Root;
const $ = (id: string) => conteneur.querySelector<HTMLElement>(`[data-testid="${id}"]`);
const attendre = async () => { for (let i = 0; i < 6; i++) await act(async () => { await Promise.resolve(); }); };
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
const rendre = async (element: JSX.Element) => { await act(async () => { racine.render(element); }); await attendre(); };
const envoisVers = (fragment: string) => api.etat.appels.filter((a) => a.methode !== "GET" && a.url.includes(fragment));
const lus = (fragment: string) => api.etat.appels.filter((a) => a.methode === "GET" && a.url.includes(fragment));
const soumettre = async (libelle: string) => {
  await act(async () => { conteneur.querySelector<HTMLFormElement>(`form[aria-label="${libelle}"]`)!.dispatchEvent(new Event("submit", { bubbles: true, cancelable: true })); });
  await attendre();
};

beforeEach(() => {
  api.etat.appels = [];
  api.etat.statutPdf = 200;
  URL.createObjectURL = vi.fn(() => "blob:essai");
  URL.revokeObjectURL = vi.fn();
  conteneur = document.createElement("div"); document.body.appendChild(conteneur);
  racine = createRoot(conteneur);
});
afterEach(() => { act(() => racine.unmount()); conteneur.remove(); });

describe("Règles de paie variable", () => {
  beforeEach(() => {
    api.etat.routes = {
      "GET /api/paie/regles": [REGLE_TAUX, REGLE_NUIT],
      "PUT /api/paie/regles/n1": { ...REGLE_NUIT, actif: true },
      "PUT /api/paie/regles/t1": REGLE_TAUX,
      "POST /api/paie/regles": { ...REGLE_NUIT, id: "s1", code: "PRIME_SOIR" },
    };
  });

  it("l'échelle et les primes se lisent à part ; valeurs dans leur unité, conditions en une phrase", async () => {
    await rendre(<ReglesPaie />);
    expect($("prg-t1")!.textContent).toContain("34,50");
    expect($("prg-t1")!.textContent).toContain("titre INF · convention FIQ · échelon 3");
    expect($("prg-n1")!.textContent).toContain("12 %");
    expect($("prg-n1")!.textContent).toContain("de 00:00 à 08:00 · sam, dim · jours fériés · heures REG");
    expect($("prg-n1")!.textContent).toContain("Inactive");
    expect($("prg-n1")!.textContent).toContain("au 2026-12-31");
    expect($("prg-t1")!.textContent).toContain("sans fin");
    expect(valeurLisible({ type: "forfait", valeur: 100 }).replace(/\s/g, " ")).toBe("100,00 $");
    expect(conditionsLisibles({ ...REGLE_TAUX, titreEmploi: null, convention: null, echelon: null } as never)).toBe("tout le personnel, toutes les heures");
    expect(libelleTypeRegle("forfait")).toBe("Forfait par paie");
    expect(libelleTypeRegle("x" as never)).toBe("x");
  });

  it("créer une prime : la valeur en % part en fraction, les jours en numéros, les codes en liste", async () => {
    await rendre(<ReglesPaie />);
    await cliquer("prg-nouvelle-ouvrir");
    await saisir("prg-code", "prime_soir");
    await saisir("prg-libelle", "Prime de soir");
    await saisir("prg-valeur", "5");
    await saisir("prg-debut", "16:00");
    await saisir("prg-fin", "00:00");
    await cliquer("prg-jour-1");
    await cliquer("prg-jour-2");
    await cliquer("prg-jour-1");
    await cliquer("prg-feries");
    await saisir("prg-codes", "REG, ");
    await saisir("prg-du", "2026-01-01");
    await soumettre("Nouvelle règle de paie");
    expect(envoisVers("/api/paie/regles").at(-1)!.corps).toEqual({ code: "PRIME_SOIR", libelle: "Prime de soir", type: "prime-pourcentage", valeur: 0.05,
      titreEmploi: "", convention: "", echelon: null, valideDu: "2026-01-01", valideAu: "", actif: true, heureDebut: "16:00", heureFin: "00:00",
      jours: [2], feriesSeulement: true, codesPaie: ["REG"] });
    expect($("prg-succes")!.textContent).toBe("Règle PRIME_SOIR créée.");
  });

  it("un taux de base n'a pas de conditions d'heures ; modifier envoie un PUT ; désactiver et réactiver", async () => {
    await rendre(<ReglesPaie />);
    await cliquer("prg-t1-modifier");
    expect($("prg-heures")).toBeNull();
    expect(($("prg-valeur") as HTMLInputElement).value).toBe("34.5");
    await saisir("prg-echelon", "");
    await soumettre("Modifier la règle TAUX_INF_E3");
    expect(envoisVers("/api/paie/regles/t1").at(-1)!.corps).toMatchObject({ type: "taux-de-base", valeur: 34.5, echelon: null, heureDebut: "", jours: [] });
    expect($("prg-succes")!.textContent).toBe("Règle TAUX_INF_E3 enregistrée.");
    await cliquer("prg-n1-basculer");
    expect(envoisVers("/api/paie/regles/n1").at(-1)!.corps).toMatchObject({ actif: true, code: "PRIME_NUIT" });
    expect($("prg-succes")!.textContent).toContain("réactivée");
    await cliquer("prg-t1-basculer");
    expect($("prg-succes")!.textContent).toContain("désactivée");
    await cliquer("prg-n1-modifier");
    expect(($("prg-valeur") as HTMLInputElement).value).toBe("12");
    await saisir("prg-type", "forfait");
    expect($("prg-heures")).toBeNull();
  });

  it("un refus se lit dans le dialogue ; sans règle, chaque section dit quoi faire", async () => {
    api.etat.routes = { "GET /api/paie/regles": [], "POST /api/paie/regles": { errors: ["« code » : 2 à 20 caractères"] } };
    await rendre(<ReglesPaie />);
    expect($("prg-echelle-vide")).not.toBeNull();
    expect($("prg-primes-vide")).not.toBeNull();
    await cliquer("prg-nouvelle-ouvrir");
    await soumettre("Nouvelle règle de paie");
    expect($("prg-form-erreur")!.textContent).toContain("2 à 20 caractères");
  });
});

describe("Validation des temps", () => {
  const CONTROLE = {
    feuilles: [
      LIGNE("f1", {}),
      LIGNE("f2", { anomalies: ["sans-taux", "heures-elevees"], validee: true, validation: { id: "v", feuilleId: "f2", decision: "validee", motif: null, acteur: "comptable", instant: "t" } }),
      LIGNE("f3", { etat: "soumise", validable: false, anomalies: ["non-approuvee", "incalculable"], incalculable: "type d'heure manquant" }),
      LIGNE("f4", { etat: "retournee", validable: false, anomalies: ["non-approuvee"], validation: { id: "v", feuilleId: "f4", decision: "retournee", motif: "Le 23 manque", acteur: "c", instant: "t" } }),
    ],
    sansFeuille: [{ ressourceId: "r9", matricule: "P9", nom: "Léa Côté", anomalies: ["aucune-feuille"] }],
  };
  beforeEach(() => {
    api.etat.routes = {
      "GET /api/paie/temps": CONTROLE,
      "POST /api/paie/temps/valider-sans-anomalie": { validees: [{ id: "v" }] },
      "POST /api/paie/temps/f1/valider": { id: "v", decision: "validee" },
      "POST /api/paie/temps/f1/retourner": { id: "v", decision: "retournee" },
    };
  });

  it("le bilan, les anomalies en mots et en signe, l'état de chaque validation, et qui n'a pas de feuille", async () => {
    await rendre(<ValidationTemps />);
    expect($("pvt-bilan")!.textContent).toContain("4 feuille(s) ; 1 validée(s) ; 3 avec");
    expect($("pvt-f2-validation")!.textContent).toBe("Validée par comptable");
    expect($("pvt-f4-validation")!.textContent).toBe("Retournée : Le 23 manque");
    expect($("pvt-f1-validation")!.textContent).toBe("—");
    expect($("pvt-f3")!.textContent).toContain("type d'heure manquant");
    expect($("pvt-f3")!.textContent).toContain("⛔");
    expect($("pvt-f2")!.textContent).toContain("⚠️");
    expect($("pvt-f1")!.textContent).toContain("Aucune");
    expect($("pvt-f3-valider")).toBeNull();
    expect($("pvt-f3-retourner")).toBeNull();
    expect($("pvt-f2-valider")).toBeNull();
    expect($("pvt-sans-feuille")!.textContent).toContain("Léa Côté (P9)");
    expect(libelleAnomalie("x")).toBe("x");
    expect(libelleEtatFeuille("payee")).toBe("Payée");
    expect(libelleEtatFeuille("x" as never)).toBe("x");
  });

  it("valider une feuille, valider en lot, retourner avec un motif ; changer la période relit", async () => {
    await rendre(<ValidationTemps />);
    await cliquer("pvt-f1-valider");
    expect(envoisVers("/api/paie/temps/f1/valider")).toHaveLength(1);
    expect($("pvt-succes")!.textContent).toContain("Julie Roy (semaine du 2026-02-16) validée");
    await cliquer("pvt-valider-lot");
    expect(envoisVers("valider-sans-anomalie").at(-1)!.corps).toMatchObject({ debut: expect.any(String), fin: expect.any(String) });
    expect($("pvt-succes")!.textContent).toBe("1 feuille(s) sans anomalie validée(s) pour la paie.");
    await cliquer("pvt-f1-retourner");
    await saisir("pvt-motif", "Le quart du 23 manque");
    await soumettre("Retourner la feuille de Julie Roy");
    expect(envoisVers("/api/paie/temps/f1/retourner").at(-1)!.corps).toEqual({ motif: "Le quart du 23 manque" });
    expect($("pvt-succes")!.textContent).toContain("retournée");
    await saisir("pvt-debut", "2026-02-15");
    await saisir("pvt-fin", "2026-02-28");
    await soumettre("Période à contrôler");
    expect(lus("/api/paie/temps?debut=2026-02-15&fin=2026-02-28").length).toBeGreaterThan(0);
  });

  it("un refus de retour se lit dans le dialogue ; sans feuille, la page le dit ; la période par défaut couvre deux semaines", async () => {
    api.etat.routes["POST /api/paie/temps/f1/retourner"] = { errors: ["seule une feuille approuvée se retourne"] };
    await rendre(<ValidationTemps />);
    await cliquer("pvt-f1-retourner");
    await saisir("pvt-motif", "x");
    await soumettre("Retourner la feuille de Julie Roy");
    expect($("pvt-retour-erreur")!.textContent).toContain("seule une feuille approuvée");
    expect(periodeParDefaut(new Date("2026-03-04T12:00:00Z"))).toEqual({ debut: "2026-02-23", fin: "2026-03-08" });
    expect(periodeParDefaut(new Date("2026-03-08T12:00:00Z"))).toEqual({ debut: "2026-02-23", fin: "2026-03-08" });
    act(() => racine.unmount());
    racine = createRoot(conteneur);
    api.etat.routes["GET /api/paie/temps"] = { feuilles: [], sansFeuille: [] };
    await rendre(<ValidationTemps />);
    expect($("pvt-vide")).not.toBeNull();
    expect($("pvt-sans-feuille")).toBeNull();
    expect(($("pvt-valider-lot") as HTMLButtonElement).disabled).toBe(true);
  });
});

describe("Fiches et registre en PDF, gains sur la fiche", () => {
  it("cycles : registre et fiche se téléchargent sous le nom du serveur ; un échec se dit ; sans taux et non validées sont nommés", async () => {
    api.etat.routes = {
      "GET /api/paie/cycles/c1": { cycle: CYCLE, bulletins: [BULLETIN] },
      "GET /api/paie/cycles": [CYCLE],
      "POST /api/paie/cycles": { cycle: { ...CYCLE, etat: "prepare" }, bulletins: [BULLETIN], sansDossier: [],
        sansTaux: [{ ressourceId: "r-marc", matricule: "P2", nom: "Marc Dubé" }], nonValidees: [{ feuilleId: "f2", ressourceId: "r-julie", nom: "Julie Roy", debut: "2026-02-23" }] },
    };
    const cliques: string[] = [];
    const original = HTMLAnchorElement.prototype.click;
    HTMLAnchorElement.prototype.click = function enregistrer(this: HTMLAnchorElement) { cliques.push(this.download); };
    try {
      await rendre(<CyclesPaie />);
      await cliquer("pcy-cycle-c1-voir");
      await cliquer("pcy-registre");
      expect(lus("/api/paie/cycles/c1/registre.pdf")).toHaveLength(1);
      await cliquer("pcy-bulletin-r-julie-pdf");
      expect(lus("/api/paie/cycles/c1/bulletins/b1/pdf")).toHaveLength(1);
      expect(cliques).toEqual(["registre-paie-2026-03-05.pdf", "fiche-paie-P1-2026-03-05.pdf"]);
      api.etat.statutPdf = 500;
      await cliquer("pcy-registre");
      expect($("pcy-erreur-pdf")!.textContent).toBe("Le PDF n'a pas pu être produit (erreur 500).");
      await cliquer("pcy-preparer-ouvrir");
      await saisir("pcy-debut", "2026-02-15");
      await saisir("pcy-fin", "2026-02-28");
      await saisir("pcy-date-paie", "2026-03-05");
      await soumettre("Préparer un cycle de paie");
      expect($("pcy-sans-taux")!.textContent).toContain("Marc Dubé (P2)");
      expect($("pcy-non-validees")!.textContent).toContain("Julie Roy (semaine du 2026-02-23)");
    } finally {
      HTMLAnchorElement.prototype.click = original;
    }
  });

  it("mes bulletins : la fiche PDF est la sienne ; le détail montre les gains, en % pour une prime en pourcentage", async () => {
    api.etat.routes = { "GET /api/paie/mes-bulletins": [BULLETIN, { ...BULLETIN, id: "b0", gains: undefined }] };
    const original = HTMLAnchorElement.prototype.click;
    HTMLAnchorElement.prototype.click = () => undefined;
    try {
      await rendre(<MesBulletins />);
      await cliquer("mbu-b1-pdf");
      expect(lus("/api/paie/mes-bulletins/b1/pdf")).toHaveLength(1);
      api.etat.statutPdf = 404;
      await cliquer("mbu-b1-pdf");
      expect($("mbu-erreur-pdf")!.textContent).toContain("erreur 404");
      await cliquer("mbu-b1-voir");
      expect($("mbu-b-gain-PRIME_SOIR")!.textContent).toContain("10 %");
      expect($("mbu-b-gain-REG")!.textContent!.replace(/\s/g, " ")).toContain("30,00 $");
      expect($("mbu-b-gain-SAL")!.textContent).toContain("1");
    } finally {
      HTMLAnchorElement.prototype.click = original;
    }
  });

  it("un bulletin d'avant l'ADR 0084 n'a pas de tableau de gains", async () => {
    api.etat.routes = { "GET /api/paie/mes-bulletins": [{ ...BULLETIN, gains: undefined }] };
    await rendre(<MesBulletins />);
    await cliquer("mbu-b1-voir");
    expect($("mbu-b-gains")).toBeNull();
    expect($("mbu-b-net")).not.toBeNull();
  });
});

describe("Dossiers et réglages", () => {
  it("le dossier : l'échelon suffit à l'heure ; le titre d'emploi des effectifs se lit ; la rémunération se dit selon le cas", async () => {
    api.etat.routes = {
      "GET /api/paie/dossiers": [
        { ressourceId: "r1", matricule: "P1", nom: "Julie Roy", titreEmploi: "INF", convention: "FIQ",
          dossier: { ressourceId: "r1", mode: "horaire", frequence: "aux-deux-semaines", tauxHoraire: null, salaireAnnuel: null, montantPersonnelFederal: 1, montantPersonnelQuebec: 1, echelon: 3 } },
        { ressourceId: "r2", matricule: "P2", nom: "Marc Dubé", titreEmploi: null,
          dossier: { ressourceId: "r2", mode: "horaire", frequence: "aux-deux-semaines", tauxHoraire: 41, salaireAnnuel: null, montantPersonnelFederal: 1, montantPersonnelQuebec: 1 } },
        { ressourceId: "r3", matricule: "P3", nom: "Léa", dossier: { ressourceId: "r3", mode: "horaire", frequence: "aux-deux-semaines", tauxHoraire: null,
          salaireAnnuel: null, montantPersonnelFederal: 1, montantPersonnelQuebec: 1 } },
      ],
      "PUT /api/paie/dossiers/r1": { ressourceId: "r1" },
    };
    await rendre(<DossiersPaie />);
    expect($("pdo-r1")!.textContent).toContain("Échelle, échelon 3");
    expect($("pdo-r1")!.textContent).toContain("INF");
    expect($("pdo-r2")!.textContent).toContain("hors échelle");
    expect($("pdo-r3")!.textContent).toContain("échelon —");
    await cliquer("pdo-r1-modifier");
    expect(($("pdo-taux") as HTMLInputElement).required).toBe(false);
    await soumettre("Dossier de rémunération de Julie Roy");
    expect(envoisVers("/api/paie/dossiers/r1").at(-1)!.corps).toMatchObject({ echelon: 3, tauxHoraire: "" });
    await cliquer("pdo-r1-modifier");
    await saisir("pdo-echelon", "");
    expect(($("pdo-taux") as HTMLInputElement).required).toBe(true);
    await soumettre("Dossier de rémunération de Julie Roy");
    expect(envoisVers("/api/paie/dossiers/r1").at(-1)!.corps).toMatchObject({ echelon: null });
  });

  it("les réglages : la validation des temps exigée se coche et part au serveur", async () => {
    api.etat.routes = {
      "GET /api/paie/reglages": { annee: 2026, tauxFss: 0.0426, tauxCnesst: 0.0178, majorationSupplementaire: 0.5, validationTempsExigee: true },
      "GET /api/paie/bareme": { errors: ["x"] },
      "PUT /api/paie/reglages": { annee: 2026, tauxFss: 0.0426, tauxCnesst: 0.0178, majorationSupplementaire: 0.5, validationTempsExigee: false },
    };
    await rendre(<ParametresPaie />);
    expect(($("ppa-validation") as HTMLInputElement).checked).toBe(true);
    await cliquer("ppa-validation");
    await soumettre("Réglages de la paie");
    expect(envoisVers("/api/paie/reglages").at(-1)!.corps).toMatchObject({ validationTempsExigee: false });
  });
});
