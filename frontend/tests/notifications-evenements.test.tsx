/**
 * **Mes notifications et Notifier une personne** (ADR 0029) : la matrice événement × canal se lit
 * du catalogue et des préférences, un canal fermé par l'interrupteur général grise la colonne, un
 * avis essentiel refuse de perdre ses deux canaux écrits avant tout envoi ; l'envoi manuel nomme,
 * par personne et par canal, ce qui est parti et ce qui a été refusé.
 */
import { act } from "react";
import { createRoot, type Root } from "react-dom/client";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import CanauxNotification from "../src/CanauxNotification";
import NotifierPersonne, { resumerEnvois } from "../src/NotifierPersonne";
import { canauxEffectifs, refusMatrice, type EvenementNotifiable, type PreferencesAvis } from "../src/reglesTextos";

(globalThis as unknown as { IS_REACT_ACT_ENVIRONMENT: boolean }).IS_REACT_ACT_ENVIRONMENT = true;

const EVENEMENTS: EvenementNotifiable[] = [
  { code: "feuille.decidee", libelle: "Feuille de temps approuvée ou refusée", module: "paie", moduleLibelle: "Paie", destinataire: "l'employé", defauts: { courriel: true, texto: false, poussee: true }, essentiel: false },
  { code: "remplacement.annule", libelle: "Remplacement annulé", module: "scolaire", moduleLibelle: "Remplacements scolaires", destinataire: "le suppléant", defauts: { courriel: true, texto: true, poussee: true }, essentiel: true },
];
const PREFS: PreferencesAvis = { courriel: true, texto: true, poussee: true, evenements: {}, updatedAt: null };
const reponse = (corps: unknown, ok = true, status = ok ? 200 : 400) => new Response(JSON.stringify(corps), { status, headers: { "Content-Type": "application/json" } });
const attendre = () => act(async () => { await new Promise((r) => setTimeout(r, 0)); });

describe("règles de la matrice", () => {
  it("un événement non réglé prend les défauts ; un essentiel sans canal écrit est refusé, après la règle générale", () => {
    expect(canauxEffectifs(EVENEMENTS[0], PREFS)).toEqual(EVENEMENTS[0].defauts);
    expect(canauxEffectifs(EVENEMENTS[0], { ...PREFS, evenements: { "feuille.decidee": { courriel: false, texto: true, poussee: false } } })).toEqual({ courriel: false, texto: true, poussee: false });
    expect(refusMatrice(PREFS, EVENEMENTS)).toBeNull();
    expect(refusMatrice({ ...PREFS, courriel: false, texto: false }, EVENEMENTS)).toMatch(/au moins un canal écrit/i);
    expect(refusMatrice({ ...PREFS, evenements: { "remplacement.annule": { courriel: false, texto: false, poussee: true } } }, EVENEMENTS)).toBe("Remplacement annulé : avis essentiel, gardez le courriel ou le texto ouvert.");
    expect(refusMatrice({ ...PREFS, evenements: { "feuille.decidee": { courriel: false, texto: false, poussee: false } } }, EVENEMENTS)).toBeNull();
    // Le texto fermé en général ne compte pas comme ouvert pour l'essentiel.
    expect(refusMatrice({ ...PREFS, texto: false, evenements: { "remplacement.annule": { courriel: false, texto: true, poussee: true } } }, EVENEMENTS)).toMatch(/Remplacement annulé/);
  });

  it("le compte rendu d'un envoi : une phrase par personne, le canal et son sort", () => {
    expect(resumerEnvois([
      { employeeId: "e1", canal: "courriel", statut: "sent", erreur: null }, { employeeId: "e1", canal: "texto", statut: "desactive", erreur: "texto fermé" },
      { employeeId: "e2", canal: "texto", statut: "failed", erreur: "aucun numéro de téléphone connu" },
    ], (id) => (id === "e1" ? "Léa Tremblay" : id))).toEqual([
      "Léa Tremblay — courriel envoyé, texto désactivé (fermé par la personne)",
      "e2 — texto échoué (aucun numéro de téléphone connu)",
    ]);
  });
});

describe("écrans", () => {
  let conteneur: HTMLDivElement;
  let racine: Root;
  beforeEach(() => { conteneur = document.createElement("div"); document.body.appendChild(conteneur); racine = createRoot(conteneur); });
  afterEach(() => { act(() => racine.unmount()); conteneur.remove(); });
  const tid = (id: string) => conteneur.querySelector(`[data-testid="${id}"]`) as HTMLInputElement | null;

  it("la matrice : lignes par module, défauts cochés, colonne grisée par l'interrupteur général, refus nommé avant l'envoi, puis enregistrement", async () => {
    const appels: Array<{ chemin: string; corps: unknown }> = [];
    const call = vi.fn(async (chemin: string, init?: RequestInit) => {
      if (chemin === "/api/notifications/evenements") return reponse(EVENEMENTS);
      if (init?.method === "PUT") { appels.push({ chemin, corps: JSON.parse(String(init.body)) }); return reponse({ ...PREFS, ...(JSON.parse(String(init.body)) as object), updatedAt: "2026-09-16T00:00:00.000Z" }); }
      return reponse(PREFS);
    });
    const confirmer = vi.fn();
    act(() => { racine.render(<CanauxNotification call={call} signaler={vi.fn()} confirmer={confirmer} />); });
    await attendre();
    expect(conteneur.querySelectorAll(".row-group").length).toBe(2);
    expect(tid("account-avis-feuille.decidee-texto")!.checked).toBe(false);
    expect(tid("account-avis-feuille.decidee-courriel")!.checked).toBe(true);
    expect(tid("account-avis-remplacement.annule-essentiel")).not.toBeNull();
    // L'interrupteur général du texto fermé : la colonne se grise (état, pas seulement couleur).
    act(() => { tid("account-canal-texto")!.click(); });
    expect(tid("account-avis-remplacement.annule-texto")!.disabled).toBe(true);
    expect(tid("account-avis-remplacement.annule-texto")!.getAttribute("aria-label")).toContain("canal fermé");
    act(() => { tid("account-canal-texto")!.click(); });
    // Un essentiel sans courriel ni texto : refusé à l'écran, rien n'est envoyé.
    act(() => { tid("account-avis-remplacement.annule-courriel")!.click(); tid("account-avis-remplacement.annule-texto")!.click(); });
    await act(async () => { tid("account-canaux-save")!.closest("form")!.requestSubmit(); });
    expect(tid("account-canaux-refus")!.textContent).toContain("Remplacement annulé");
    expect(appels).toHaveLength(0);
    act(() => { tid("account-avis-remplacement.annule-texto")!.click(); tid("account-avis-feuille.decidee-poussee")!.click(); });
    // Le courriel personnel (CSSMB 21) : vide par défaut, saisi, il part avec le reste, normalisé par le serveur.
    expect(tid("account-courriel-personnel")!.value).toBe("");
    act(() => { Object.getOwnPropertyDescriptor(HTMLInputElement.prototype, "value")!.set!.call(tid("account-courriel-personnel")!, " lea.perso@gmail.com "); tid("account-courriel-personnel")!.dispatchEvent(new Event("input", { bubbles: true })); });
    await act(async () => { tid("account-canaux-save")!.closest("form")!.requestSubmit(); });
    await attendre();
    expect(appels[0].corps).toEqual({ courriel: true, texto: true, poussee: true, courrielPersonnel: "lea.perso@gmail.com", evenements: { "remplacement.annule": { courriel: false, texto: true, poussee: true }, "feuille.decidee": { courriel: true, texto: false, poussee: false } } });
    expect(tid("account-courriel-personnel")!.value).toBe("lea.perso@gmail.com");
    expect(confirmer).toHaveBeenCalledWith("Vos notifications sont enregistrées.");
    expect(tid("account-canaux-refus")).toBeNull();
  });

  it("notifier une personne : la personne choisie par son nom, un canal décoché, envoi, compte rendu nommé, formulaire vidé, journal rechargé", async () => {
    vi.useFakeTimers();
    const call = vi.fn(async (chemin: string, init?: RequestInit) => {
      if (chemin.startsWith("/api/personnel/employes/suggestions")) return reponse([{ id: "emp-1", nom: "Tremblay, Léa", titre: "Enseignante", courriel: null, statut: "active" }]);
      if (chemin === "/api/notifications/envoyer") {
        expect(JSON.parse(String(init!.body))).toEqual({ employeeIds: ["emp-1"], sujet: "Réunion", corps: "Jeudi 9 h", canaux: ["courriel", "poussee"] });
        return reponse({ envois: [{ employeeId: "emp-1", canal: "courriel", statut: "sent", erreur: null }, { employeeId: "emp-1", canal: "poussee", statut: "desactive", erreur: "poussée fermée" }] }, true, 201);
      }
      if (chemin === "/api/personnel/employes/emp-1") return reponse({ id: "emp-1", nom: "Léa Tremblay", titre: null, courriel: null, statut: "active" });
      return reponse([]);
    });
    const apresEnvoi = vi.fn();
    act(() => { racine.render(<NotifierPersonne call={call} signaler={vi.fn()} apresEnvoi={apresEnvoi} />); });
    const saisir = (el: HTMLInputElement | HTMLTextAreaElement, valeur: string) => {
      const proto = el instanceof HTMLTextAreaElement ? HTMLTextAreaElement.prototype : HTMLInputElement.prototype;
      Object.getOwnPropertyDescriptor(proto, "value")!.set!.call(el, valeur);
      el.dispatchEvent(new Event("input", { bubbles: true }));
    };
    act(() => { saisir(tid("notif-personne-cible")!, "Tre"); });
    await act(async () => { await vi.advanceTimersByTimeAsync(250); });
    act(() => { tid("notif-personne-cible-option-emp-1")!.dispatchEvent(new MouseEvent("mousedown", { bubbles: true })); });
    await act(async () => { await vi.advanceTimersByTimeAsync(10); });
    expect(tid("notif-personne-cible-retenu-emp-1")).not.toBeNull();
    act(() => { tid("notif-personne-canal-texto")!.click(); });
    act(() => { saisir(tid("notif-personne-sujet")!, "Réunion"); saisir(tid("notif-personne-corps") as unknown as HTMLTextAreaElement, "Jeudi 9 h"); });
    expect(tid("notif-personne-envoyer")!.textContent).toBe("Envoyer");
    await act(async () => { tid("notif-personne-envoyer")!.closest("form")!.requestSubmit(); await vi.advanceTimersByTimeAsync(10); });
    expect(tid("notif-personne-resultat")!.textContent).toContain("Léa Tremblay — courriel envoyé, poussée désactivé (fermé par la personne)");
    expect(tid("notif-personne-sujet")!.value).toBe("");
    expect(apresEnvoi).toHaveBeenCalled();
    vi.useRealTimers();
  });
});
