/**
 * **Tableau de bord** (planches s01 et s02) — ce que le gestionnaire lit de sa journée, et ce que
 * l'employé lit de sa semaine.
 *
 * Trois décisions s'y éprouvent :
 * - un chiffre **non lu** (module absent, refus) s'affiche « — », jamais 0 : un zéro dirait « rien
 *   à faire », ce qui serait faux ;
 * - les équipages **à combler** viennent en tête, et leur action mène au comblement ;
 * - la journée est celle de **Montréal**, pas celle du poste (mémoire « fuseau »).
 */
import { act } from "react";
import { createRoot, type Root } from "react-dom/client";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import {
  chiffre, compteurs, estACombler, jourDuClient, periodesDuJour, placesACombler, trierParUrgence, AUCUNE_DONNEE,
} from "../src/tableauDeBord/indicateurs";
import { horizonDe } from "../src/tableauDeBord/ProchainsQuarts";
import type { Equipage, Periode } from "../src/effectifs/planification/types";

(globalThis as unknown as { IS_REACT_ACT_ENVIRONMENT: boolean }).IS_REACT_ACT_ENVIRONMENT = true;

const auth = vi.hoisted(() => {
  const porteur: { appel: (url: string) => Promise<Response>; role: string } = {
    appel: () => Promise.reject(new Error("aucun appel branché")), role: "manager",
  };
  // `apiFetch` **stable**, comme celui de l'application : une identité nouvelle à chaque rendu
  // relancerait la lecture à chaque rendu, sans fin.
  const apiFetch = (url: string) => porteur.appel(url);
  return { porteur, useAuth: () => ({ apiFetch, user: { id: "u1", username: "claire.nadeau", role: porteur.role } }) };
});
vi.mock("../src/AuthContext", () => ({ useAuth: auth.useAuth }));

const modules = vi.hoisted(() => ({ autorises: new Set<string>() }));
vi.mock("../src/moduleAccess", () => ({ useModuleAllowed: () => (code?: string) => !code || modules.autorises.has(code) }));
vi.mock("../src/Annonces", () => ({ AnnoncesAccueil: () => null }));

const navigation = vi.hoisted(() => ({ vers: [] as string[] }));
vi.mock("../src/navigation", async (original) => ({
  ...(await original<typeof import("../src/navigation")>()),
  naviguerVers: (page: string) => { navigation.vers.push(page); },
}));

const { default: Dashboard } = await import("../src/Dashboard");
const { I18nProvider } = await import("../src/i18n");

const equipage = (id: string, debut: number, places: number, pourvues: number, statut: Equipage["statut"] = "OPERATIONNEL"): Equipage => ({
  id, periodeId: "p1", date: "2026-11-13", debut, fin: debut + 480, natureCode: "AMB", plageCode: null, milieuId: "m-cv",
  modeAttribution: "MANUEL", minimumOperationnel: places, note: null, places: Array.from({ length: places }, () => ({}) as never), pourvues, statut,
});

describe("indicateurs du jour — la part pure", () => {
  it("la journée est celle de Montréal : à 2 h UTC, il est encore la veille au Québec", () => {
    expect(jourDuClient(new Date("2026-11-14T02:00:00Z"))).toBe("2026-11-13");
    expect(jourDuClient(new Date("2026-11-14T06:00:00Z"))).toBe("2026-11-14");
  });

  it("seules les périodes qui contiennent le jour portent les équipages d'aujourd'hui, bornes comprises", () => {
    const p = (id: string, debut: string, fin: string) => ({ id, debut, fin }) as Periode;
    expect(periodesDuJour([p("a", "2026-11-01", "2026-11-13"), p("b", "2026-11-14", "2026-11-30"), p("c", "2026-11-13", "2026-11-13")], "2026-11-13")
      .map((x) => x.id)).toEqual(["a", "c"]);
  });

  it("une place à combler ne devient jamais négative, et le sous-minimum suffit à rendre un équipage à combler", () => {
    expect(placesACombler(equipage("e", 0, 2, 3))).toBe(0);
    expect(estACombler(equipage("e", 0, 2, 2, "SOUS_MINIMUM"))).toBe(true);
    expect(estACombler(equipage("e", 0, 2, 1))).toBe(true);
    expect(estACombler(equipage("e", 0, 2, 2))).toBe(false);
  });

  it("les équipages à combler d'abord, puis par heure", () => {
    const tries = trierParUrgence([equipage("ok-tot", 360, 2, 2), equipage("manque-tard", 840, 3, 1), equipage("manque-tot", 360, 4, 3)]);
    expect(tries.map((e) => e.id)).toEqual(["manque-tot", "manque-tard", "ok-tot"]);
  });

  it("**un chiffre non lu vaut « — », jamais 0** ; un chiffre lu se lit sur deux positions, comme la planche", () => {
    expect(compteurs(AUCUNE_DONNEE)).toEqual({ quarts: null, aCombler: null, absences: null, demandes: null, anomalies: null });
    expect(chiffre(null)).toBe("—");
    expect(chiffre(0)).toBe("00");
    expect(chiffre(8)).toBe("08");
    expect(chiffre(24)).toBe("24");
    const c = compteurs({ ...AUCUNE_DONNEE, equipages: [equipage("a", 0, 4, 3), equipage("b", 0, 3, 1)],
      echanges: [{ statut: "A_VALIDER" }, { statut: "PROPOSE" }] as never });
    expect(c.quarts).toBe(2);
    expect(c.aCombler).toBe(3);
    expect(c.demandes).toBe(1);
  });

  it("l'horizon de l'employé couvre sept jours, sans glisser au changement d'heure", () => {
    expect(horizonDe("2026-11-13")).toEqual({ du: "2026-11-13", au: "2026-11-19" });
    expect(horizonDe("2026-10-29")).toEqual({ du: "2026-10-29", au: "2026-11-04" });
  });
});

describe("Tableau de bord — à l'écran", () => {
  let conteneur: HTMLDivElement;
  let racine: Root;
  const vus: string[] = [];
  const reponse = (corps: unknown, ok = true) => ({ ok, status: ok ? 200 : 403, json: () => Promise.resolve(corps) }) as unknown as Response;
  const brancher = (routes: Record<string, unknown>) => {
    vus.length = 0;
    auth.porteur.appel = (url: string) => {
      vus.push(url);
      const cle = Object.keys(routes).find((k) => url.includes(k));
      return Promise.resolve(cle === undefined ? reponse({ errors: ["refusé"] }, false) : reponse(routes[cle]));
    };
  };
  const rendre = async () => {
    await act(async () => { racine.render(<I18nProvider><Dashboard /></I18nProvider>); });
    for (let i = 0; i < 4; i++) await act(async () => { await Promise.resolve(); });
  };
  const texte = (id: string) => conteneur.querySelector(`[data-testid="${id}"]`)?.textContent ?? null;
  const cliquer = async (id: string) => { await act(async () => { conteneur.querySelector<HTMLElement>(`[data-testid="${id}"]`)!.click(); }); };

  beforeEach(() => {
    navigation.vers = [];
    conteneur = document.createElement("div"); document.body.appendChild(conteneur);
    racine = createRoot(conteneur);
  });
  afterEach(() => { act(() => racine.unmount()); conteneur.remove(); });

  const MODULES_COMPLETS = ["effectifs", "effectifs_planification", "effectifs_absences", "effectifs_echanges", "effectifs_presence"];
  const aujourdhui = jourDuClient(new Date());

  it("le gestionnaire lit sa journée : quatre tuiles, les quarts à combler en tête, et « Combler » mène au comblement", async () => {
    auth.porteur.role = "manager";
    modules.autorises = new Set(MODULES_COMPLETS);
    brancher({
      "/planification/periodes/p1/equipages": [equipage("e-ok", 1320, 2, 2), equipage("e-nord", 840, 3, 1, "SOUS_MINIMUM")],
      "/planification/periodes": [{ id: "p1", debut: "2000-01-01", fin: "2999-12-31" }],
      "/absences": [{ id: "a1", ressourceNom: "Marc Dubé", codeAbsence: "MAL", du: aujourdhui, au: aujourdhui, statut: "A_APPROUVER" }],
      "/echanges": [{ id: "x1", statut: "A_VALIDER", nomA: "Julie Roy", nomB: "Ana Côté", quartCede: { date: aujourdhui, debut: 360, fin: 840 }, proposeLe: `${aujourdhui}T10:00:00Z` }],
      "/presence/pointages": [],
      "/milieux": [{ id: "m-cv", libelle: "Centre-ville" }],
    });
    await rendre();
    expect(texte("dashboard-greeting")).toContain("Tableau de bord gestionnaire");
    expect(texte("tdb-valeur-couverture")).toBe("02");
    expect(texte("tdb-valeur-absences")).toBe("01");
    expect(texte("tdb-valeur-demandes")).toBe("01");
    expect(texte("tdb-valeur-anomalies")).toBe("00");
    expect(texte("tdb-tuile-couverture")).toContain("2 place(s) à combler");
    // Les équipages à combler en tête ; le site par son nom, jamais par son identifiant.
    const lignes = [...conteneur.querySelectorAll('[data-testid^="tdb-quart-"]')].map((l) => l.getAttribute("data-testid"));
    expect(lignes).toEqual(["tdb-quart-e-nord", "tdb-quart-e-ok"]);
    expect(texte("tdb-quart-e-nord")).toContain("Centre-ville");
    expect(texte("tdb-quart-e-nord")).toContain("Sous le minimum");
    expect(conteneur.querySelector('[data-testid="tdb-combler-e-nord"]')!.getAttribute("aria-label")).toBe("Combler — quart 14:00–22:00, Centre-ville");
    await cliquer("tdb-combler-e-nord");
    await cliquer("tdb-detail-e-ok");
    expect(navigation.vers).toEqual(["uscomblement", "usequipages"]);
    // Le gestionnaire n'a pas la grille d'accès rapides : le menu est là pour ça.
    expect(conteneur.querySelector('[data-testid="dashboard-tiles"]')).toBeNull();
  });

  it("une tuile ouvre son onglet, et chaque onglet mène à l'écran où l'on agit", async () => {
    auth.porteur.role = "director";
    modules.autorises = new Set(MODULES_COMPLETS);
    brancher({
      "/planification/periodes": [],
      "/absences": [{ id: "a1", ressourceNom: "Marc Dubé", codeAbsence: "MAL", du: "2026-11-13", au: "2026-11-14", statut: "A_APPROUVER" }],
      "/echanges": [{ id: "x1", statut: "A_VALIDER", nomA: "Julie Roy", nomB: "Ana Côté", quartCede: { date: "2026-11-20", debut: 360, fin: 840 }, proposeLe: "2026-11-12T10:00:00Z" },
                    { id: "x2", statut: "APPLIQUE", nomA: "A", nomB: "B", quartCede: { date: "2026-11-20", debut: 0, fin: 1 }, proposeLe: "2026-11-01" }],
      "/presence/pointages": [{ id: "pt1", nom: "Julie Roy", horodatage: "2026-11-13T11:02:00Z", milieuId: "m-cv", ecart: "HORS_MILIEU" }],
      "/milieux": [{ id: "m-cv", libelle: "Centre-ville" }],
    });
    await rendre();
    expect(conteneur.querySelector('[data-testid="tdb-vide-couverture"]')).not.toBeNull();

    await cliquer("tdb-tuile-absences");
    expect(conteneur.querySelector('[data-testid="tdb-onglet-absences"]')!.getAttribute("aria-selected")).toBe("true");
    expect(texte("tdb-absence-a1")).toContain("2026-11-13 au 2026-11-14");
    await cliquer("tdb-decider-a1");

    await cliquer("tdb-onglet-demandes");
    expect(conteneur.querySelectorAll('[data-testid^="tdb-demande-"]')).toHaveLength(1);
    expect(texte("tdb-demande-x1")).toContain("Julie Roy et Ana Côté");
    await cliquer("tdb-valider-x1");

    await cliquer("tdb-onglet-anomalies");
    expect(texte("tdb-pointage-pt1")).toContain("Hors du milieu");
    expect(texte("tdb-pointage-pt1")).toContain("Centre-ville");
    await cliquer("tdb-examiner-pt1");

    await cliquer("tdb-action-principale");
    expect(navigation.vers).toEqual(["usabsences", "usechanges", "uspresence", "uscomblement"]);
    // Le panneau est lié à son onglet : le lecteur d'écran sait ce qu'il lit.
    const panneau = conteneur.querySelector('[role="tabpanel"]')!;
    expect(panneau.getAttribute("aria-labelledby")).toBe("tdb-tabbtn-anomalies");
  });

  it("**sans le module, rien n'est demandé et le chiffre vaut « — »** ; un refus du serveur aussi", async () => {
    auth.porteur.role = "admin";
    modules.autorises = new Set(["effectifs", "effectifs_absences"]);
    brancher({ "/milieux": [] });   // la route des absences répond par un refus
    await rendre();
    expect(vus.some((u) => u.includes("/planification") || u.includes("/echanges") || u.includes("/presence"))).toBe(false);
    expect(vus.some((u) => u.includes("/absences"))).toBe(true);
    for (const id of ["couverture", "absences", "demandes", "anomalies"]) expect(texte(`tdb-valeur-${id}`)).toBe("—");
    expect(conteneur.querySelector('[data-testid="tdb-non-disponible"]')).not.toBeNull();
    // L'action principale mène au comblement : sans la planification, elle n'est pas offerte.
    expect(conteneur.querySelector('[data-testid="tdb-action-principale"]')).toBeNull();
  });

  it("l'employé lit ses sept prochains jours et les portes de **ses** écrans, pas les consoles", async () => {
    auth.porteur.role = "employee";
    modules.autorises = new Set(["effectifs", "effectifs_planification", "effectifs_disponibilites"]);
    brancher({ "/mon-horaire": { ressource: { id: "r1" }, quarts: [{ placeId: "pl-1", date: "2026-11-16", debut: 360, fin: 840, milieu: null, corpsEmploiCode: "PARA" }] } });
    await rendre();
    expect(texte("dashboard-greeting")).toContain("Mon tableau de bord");
    const { du, au } = horizonDe(aujourdhui);
    expect(vus.some((u) => u.includes(`du=${du}`) && u.includes(`au=${au}`))).toBe(true);
    expect(texte("tdb-mon-quart-pl-1")).toContain("06:00–14:00");
    expect(texte("tdb-mon-quart-pl-1")).toContain("—");
    const portes = [...conteneur.querySelectorAll('[data-testid^="dashboard-tile-"]')].map((b) => b.getAttribute("data-testid"));
    // Addenda 8, M-08 : « Mes demandes » relève de la planification, comme « Mon horaire ».
    expect(portes).toEqual(["dashboard-tile-monhoraire", "dashboard-tile-mesdisponibilites", "dashboard-tile-mesdemandes"]);
    await cliquer("dashboard-tile-mesdisponibilites");
    expect(navigation.vers).toEqual(["mesdisponibilites"]);
    expect(conteneur.querySelector('[data-testid="tdb-onglets"]')).toBeNull();
  });

  it("un employé sans ressource lit pourquoi ; sans aucun écran ouvert, l'accueil le dit aussi", async () => {
    auth.porteur.role = "employee";
    modules.autorises = new Set(["effectifs_planification"]);
    brancher({ "/mon-horaire": { ressource: null, quarts: [] } });
    await rendre();
    expect(conteneur.querySelector('[data-testid="tdb-sans-ressource"]')).not.toBeNull();

    act(() => racine.unmount());
    racine = createRoot(conteneur);
    modules.autorises = new Set();
    brancher({});
    await rendre();
    expect(vus).toEqual([]);
    expect(conteneur.querySelector('[data-testid="dashboard-vide"]')).not.toBeNull();
  });

  it("un horaire illisible ne casse pas l'accueil : la section se tait", async () => {
    auth.porteur.role = "employee";
    modules.autorises = new Set(["effectifs_planification"]);
    brancher({ "/mon-horaire": [] });
    await rendre();
    expect(conteneur.querySelector('[data-testid="tdb-prochains"]')).toBeNull();
  });
});
