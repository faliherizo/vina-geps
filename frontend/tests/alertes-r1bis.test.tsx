/**
 * **Communications cliniques — R1-bis** (08_plan_implantation.md) : un modèle se modifie en versions, son périmètre se
 * coche, la copie par courriel se règle ; le suivi se filtre et s'exporte ; les groupes transverses se tiennent.
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

const { default: ParametresAlertes } = await import("../src/alertes/ParametresAlertes");
const { default: SuiviAlertes, parametresDe } = await import("../src/alertes/SuiviAlertes");
const { default: GroupesTransverses, adapterPersonnes, libelleNature, periode } = await import("../src/alertes/GroupesTransverses");

const ARRIVE = { id: "r1", libelle: "J'arrive", semantique: "prise_en_charge", active: true };
const MODELE = { id: "m1", titre: "Code bleu", corps: "Chambre 512", priorite: "urgente", reponseIds: ["r1"], groupeIds: ["gt-garde"], version: 2, actif: true };
const LIBRE = { id: "m2", titre: "Info", corps: "x", priorite: "information", reponseIds: [], actif: true };
const GARDE = { id: "gt-garde", nom: "Garde de nuit", nature: "garde", actif: true,
  membres: [{ personId: "lea", nom: "Léa Morin", depuis: "2031-03-01", jusqua: "2031-03-07", moderateur: true }] };

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

describe("fonctions pures", () => {
  it("le filtre ne garde que les bornes remplies ; natures et périodes se disent en mots ; la recherche de personnes s'adapte au champ", () => {
    expect(parametresDe({ du: "2031-03-01", au: "", priorite: " urgente ", etat: "", expediteur: "" }).toString()).toBe("du=2031-03-01&priorite=urgente");
    expect([libelleNature("garde"), libelleNature("comite"), libelleNature("x")]).toEqual(["Garde", "Comité", "x"]);
    expect([periode({ depuis: "a", jusqua: "b" }), periode({ depuis: "a", jusqua: null }), periode({ depuis: null, jusqua: "b" }), periode({ depuis: null, jusqua: null })])
      .toEqual(["du a au b", "depuis le a", "jusqu'au b", "sans date"]);
    expect(adapterPersonnes([{ id: "lea", nom: "Léa" }])).toEqual([{ id: "lea", nom: "Léa", titre: null, courriel: null, statut: "active" }]);
    expect(adapterPersonnes({ pas: "une liste" })).toEqual([]);
  });
});

describe("Paramètres des alertes — versions, périmètre, réglages", () => {
  beforeEach(() => {
    api.etat.routes = {
      "GET /api/alertes/reponses": [ARRIVE],
      "GET /api/alertes/modeles/m1/versions": [
        { version: 2, titre: "Code bleu", corps: "x", priorite: "urgente", reponseIds: ["r1"], creeLe: "2026-11-14T14:02:00Z", creePar: "admin" },
        { version: 1, titre: "Code bleu (ancien)", corps: "x", priorite: "critique", reponseIds: [], creeLe: "reprise", creePar: "reprise" }],
      "GET /api/alertes/modeles": [MODELE, LIBRE],
      "GET /api/alertes/groupes": [{ id: "EST", nom: "Opérations Est" }, { id: "gt-garde", nom: "Garde de nuit" }],
      "GET /api/alertes/reglages": { copieCourriel: false },
      "PUT /api/alertes/modeles/m1": { ...MODELE, titre: "Code bleu — 5e", version: 3 },
      "PUT /api/alertes/reglages": { copieCourriel: true },
      "GET /api/communication/groupes-transverses": [],
    };
  });

  it("le tableau dit version et périmètre ; modifier fait une version nouvelle ; l'historique se lit ; la copie se règle", async () => {
    await rendre(<ParametresAlertes />);
    expect($("alp-modele-m1")!.textContent).toContain("2");
    expect($("alp-modele-m1")!.textContent).toContain("Garde de nuit");
    expect($("alp-modele-m2")!.textContent).toContain("Tout l'organisme");
    await cliquer("alp-modele-m1-modifier");
    expect(($("alp-m-titre") as HTMLInputElement).value).toBe("Code bleu");
    expect(($("alp-m-g-gt-garde") as HTMLInputElement).checked).toBe(true);
    await saisir("alp-m-titre", "Code bleu — 5e");
    await cliquer("alp-m-g-EST");
    await cliquer("alp-m-g-gt-garde");
    await soumettre("Modifier le modèle d'alerte");
    expect(envoisVers("/api/alertes/modeles/m1").at(-1)).toMatchObject({ methode: "PUT", corps: { titre: "Code bleu — 5e", groupeIds: ["EST"] } });
    expect($("alp-succes")!.textContent).toBe("Modèle « Code bleu — 5e » modifié : version 3.");

    await cliquer("alp-modele-m1-versions");
    expect($("alp-versions-table")!.textContent).toContain("Code bleu (ancien)");
    expect($("alp-versions-table")!.textContent).toContain("admin");

    await cliquer("alp-copie");
    expect(envoisVers("/api/alertes/reglages").at(-1)!.corps).toEqual({ copieCourriel: true });
    expect($("alp-succes")!.textContent).toContain("copie par courriel");
  });

  it("retirer la copie le dit ; un refus du serveur laisse le réglage tel quel", async () => {
    api.etat.routes["GET /api/alertes/reglages"] = { copieCourriel: true };
    api.etat.routes["PUT /api/alertes/reglages"] = { copieCourriel: false };
    await rendre(<ParametresAlertes />);
    await cliquer("alp-copie");
    expect($("alp-succes")!.textContent).toBe("La copie par courriel est retirée.");
    api.etat.routes["PUT /api/alertes/reglages"] = { errors: ["refusé"] };
    await cliquer("alp-copie");
    expect($("alp-erreur")!.textContent).toContain("refusé");
  });
});

describe("Suivi — filtres et export", () => {
  it("le filtre part avec la requête et l'export ; vider le filtre revient à tout", async () => {
    api.etat.routes = { "GET /api/alertes/envoyees": [] };
    await rendre(<SuiviAlertes />);
    expect(api.etat.appels.at(-1)!.url).toBe("/api/alertes/envoyees");
    await saisir("als-du", "2026-11-01");
    await saisir("als-priorite", "critique");
    await saisir("als-etat", "sans_prise_en_charge");
    await saisir("als-expediteur", "chef");
    await saisir("als-au", "2026-11-30");
    await soumettre("Filtrer le suivi");
    expect(api.etat.appels.at(-1)!.url).toBe("/api/alertes/envoyees?du=2026-11-01&au=2026-11-30&priorite=critique&etat=sans_prise_en_charge&expediteur=chef");
    expect($("als-export-exporter")).not.toBeNull();
  });
});

describe("Groupes transverses", () => {
  beforeEach(() => {
    api.etat.routes = {
      "GET /api/communication/groupes-transverses": [GARDE],
      "POST /api/communication/groupes-transverses": { id: "gt-comite", nom: "Comité éthique", nature: "comite", actif: true, membres: [] },
      "PATCH /api/communication/groupes-transverses/gt-garde": { ...GARDE, nom: "Garde de fin de semaine" },
      "PUT /api/communication/groupes-transverses/gt-garde/membres/marc": { ...GARDE, membres: [...GARDE.membres, { personId: "marc", nom: "Marc", depuis: null, jusqua: null, moderateur: false }] },
      "DELETE /api/communication/groupes-transverses/gt-garde/membres/lea": { ...GARDE, membres: [] },
      "GET /api/communication/groupes-transverses/personnes": [{ id: "marc", nom: "Marc Dubé" }],
    };
  });

  it("créer, renommer, désactiver ; inscrire par le nom, pour une période ; retirer", async () => {
    await rendre(<GroupesTransverses />);
    expect($("alp-gt-gt-garde")!.textContent).toContain("Garde");
    await cliquer("alp-gt-nouveau-ouvrir");
    await saisir("alp-gt-nom", "Comité éthique");
    await saisir("alp-gt-nature", "comite");
    await soumettre("Nouveau groupe transverse");
    expect(envoisVers("/communication/groupes-transverses").at(-1)!.corps).toEqual({ nom: "Comité éthique", nature: "comite" });
    expect($("alp-gt-info")!.textContent).toBe("Groupe « Comité éthique » créé.");

    await cliquer("alp-gt-gt-garde-gerer");
    expect($("alp-gt-membre-lea")!.textContent).toContain("du 2031-03-01 au 2031-03-07");
    expect($("alp-gt-membre-lea")!.textContent).toContain("Modératrice ou modérateur");
    await saisir("alp-gt-renommer", "Garde de fin de semaine");
    await soumettre("Renommer le groupe");
    expect(envoisVers("/gt-garde").at(-1)).toMatchObject({ methode: "PATCH", corps: { nom: "Garde de fin de semaine", actif: true } });
    await cliquer("alp-gt-basculer");
    expect(envoisVers("/gt-garde").at(-1)!.corps).toEqual({ nom: "Garde de fin de semaine", actif: false });

    await saisir("alp-gt-personne", "mar");
    await act(async () => { await new Promise((r) => setTimeout(r, 260)); });
    await attendre();
    await act(async () => { $("alp-gt-personne-option-marc")!.dispatchEvent(new MouseEvent("mousedown", { bubbles: true })); });
    await attendre();
    await saisir("alp-gt-depuis", "2031-03-08");
    await cliquer("alp-gt-moderateur");
    await soumettre("Inscrire une personne");
    expect(envoisVers("/membres/marc").at(-1)).toMatchObject({ methode: "PUT", corps: { depuis: "2031-03-08", jusqua: "", moderateur: true } });
    expect($("alp-gt-info")!.textContent).toBe("Inscription enregistrée.");

    await cliquer("alp-gt-membre-lea-retirer");
    expect(envoisVers("/membres/lea").at(-1)!.methode).toBe("DELETE");
    expect($("alp-gt-membres-vide")).not.toBeNull();
  });

  it("un refus s'affiche dans le dialogue ; la liste vide le dit", async () => {
    api.etat.routes["DELETE /api/communication/groupes-transverses/gt-garde/membres/lea"] = { errors: ["« lea » n'est pas membre de ce groupe"] };
    api.etat.routes["POST /api/communication/groupes-transverses"] = { errors: ["« nature » : specialite, garde ou comite"] };
    await rendre(<GroupesTransverses />);
    await cliquer("alp-gt-nouveau-ouvrir");
    await saisir("alp-gt-nom", "X");
    await soumettre("Nouveau groupe transverse");
    expect($("alp-gt-info")).toBeNull();
    await act(async () => { document.dispatchEvent(new KeyboardEvent("keydown", { key: "Escape", bubbles: true })); });
    await attendre();
    await cliquer("alp-gt-gt-garde-gerer");
    await cliquer("alp-gt-membre-lea-retirer");
    expect($("alp-gt-fiche-erreur")!.textContent).toContain("n'est pas membre");
    act(() => racine.unmount());
    racine = createRoot(conteneur);
    api.etat.routes["GET /api/communication/groupes-transverses"] = [];
    await rendre(<GroupesTransverses />);
    expect($("alp-gt-vide")).not.toBeNull();
  });
});
