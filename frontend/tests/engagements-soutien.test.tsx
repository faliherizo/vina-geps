/**
 * Engagements de service vus de l'écran : le soutien attribue la politique d'un organisme, la
 * console annonce le changement ; le mois est jugé politique par politique, en toutes lettres ;
 * un billet du service essentiel dit où en est le rappel.
 */
import { act } from "react";
import { createRoot, type Root } from "react-dom/client";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

import EngagementsSoutien, { VerdictsDisponibilite } from "../src/soutien/EngagementsSoutien";
import { formaterDelai, formaterReponse } from "../src/Soutien";

(globalThis as unknown as { IS_REACT_ACT_ENVIRONMENT: boolean }).IS_REACT_ACT_ENVIRONMENT = true;

const reponse = (corps: unknown, status = 200) => Promise.resolve({ ok: status < 300, status, json: () => Promise.resolve(corps) } as Response);
const POLITIQUES = [{ code: "standard", libelle: "Plateforme" }, { code: "service-essentiel", libelle: "Service essentiel" }];
const organisme = (politique: string, libelle: string, modifieLe: string | null = null) =>
  ({ tenantId: "t-us", nom: "Urgences-santé", politique, libelle, modifieLe, modifiePar: modifieLe ? "usr-sa" : null });

describe("mise en forme des délais", () => {
  it("écrit les durées comme le contrat les compte", () => {
    expect(formaterDelai(30)).toBe("30 min");
    expect(formaterDelai(120)).toBe("2 h");
    expect(formaterDelai(1440)).toBe("24 h");
    expect(formaterDelai(2880)).toBe("48 h");
    expect(formaterDelai(10080)).toBe("7 j");
  });

  it("dit l'état du rappel en mots", () => {
    const r = { echeanceLe: "x", atteintLe: null, ecartMinutes: 20 };
    expect(formaterReponse({ ...r, etat: "dans_les_delais" })).toBe("rappel attendu, il reste 20 min");
    expect(formaterReponse({ ...r, etat: "en_retard", ecartMinutes: -10 })).toBe("rappel en retard de 10 min");
    expect(formaterReponse({ ...r, etat: "tenu" })).toBe("rappel à temps");
    expect(formaterReponse({ ...r, etat: "manque", ecartMinutes: -15 })).toBe("rappel tardif (15 min)");
  });
});

describe("EngagementsSoutien", () => {
  let conteneur: HTMLDivElement;
  let racine: Root;
  const apiFetch = vi.fn();
  beforeEach(() => {
    conteneur = document.createElement("div");
    document.body.appendChild(conteneur);
    racine = createRoot(conteneur);
    apiFetch.mockReset();
  });
  afterEach(() => { act(() => racine.unmount()); conteneur.remove(); });
  const tid = (id: string) => conteneur.querySelector(`[data-testid="${id}"]`) as HTMLElement | null;
  const attendre = () => new Promise((r) => setTimeout(r, 5));
  const choisir = async (valeur: string) => {
    const s = tid("sa-engagement-choix-t-us") as HTMLSelectElement;
    await act(async () => { s.value = valeur; s.dispatchEvent(new Event("change", { bubbles: true })); await attendre(); });
  };

  it("liste les organismes, attribue une politique et l'annonce", async () => {
    const onChange = vi.fn();
    apiFetch
      .mockReturnValueOnce(reponse({ politiques: POLITIQUES, organismes: [organisme("standard", "Plateforme")] }))
      .mockReturnValueOnce(reponse(organisme("service-essentiel", "Service essentiel", "2026-11-12T10:00:00.000Z")))
      .mockReturnValueOnce(reponse({ politiques: POLITIQUES, organismes: [organisme("service-essentiel", "Service essentiel", "2026-11-12T10:00:00.000Z")] }));
    await act(async () => { racine.render(<EngagementsSoutien apiFetch={apiFetch} onChange={onChange} />); await attendre(); });
    expect(tid("sa-engagement-t-us")!.textContent).toContain("jamais changée");
    expect(conteneur.querySelector("label[for='sa-engagement-choix-t-us']")!.textContent).toBe("Politique de soutien de Urgences-santé");

    await choisir("service-essentiel");
    expect(apiFetch.mock.calls[1][0]).toBe("/api/soutien/engagements/t-us");
    expect(apiFetch.mock.calls[1][1]).toMatchObject({ method: "PUT", body: JSON.stringify({ politique: "service-essentiel" }) });
    expect(tid("sa-engagements-annonce")!.textContent).toBe("Urgences-santé : politique « Service essentiel » appliquée.");
    expect(tid("sa-engagement-t-us")!.textContent).toContain("2026-11-12 par usr-sa");
    expect(onChange).toHaveBeenCalledTimes(1);
  });

  it("dit le refus du serveur, et l'échec de lecture", async () => {
    apiFetch
      .mockReturnValueOnce(reponse({ politiques: POLITIQUES, organismes: [organisme("standard", "Plateforme")] }))
      .mockReturnValueOnce(reponse({ errors: ["organisme introuvable"] }, 404))
      .mockReturnValueOnce(Promise.resolve({ ok: false, status: 500, json: () => Promise.reject(new Error("vide")) } as unknown as Response));
    await act(async () => { racine.render(<EngagementsSoutien apiFetch={apiFetch} />); await attendre(); });
    await choisir("service-essentiel");
    expect(tid("sa-engagements-erreur")!.textContent).toContain("Urgences-santé : organisme introuvable");
    await choisir("service-essentiel");
    expect(tid("sa-engagements-erreur")!.textContent).toContain("refus du serveur");

    act(() => racine.unmount());
    racine = createRoot(conteneur);
    apiFetch.mockReset().mockReturnValueOnce(reponse({}, 403));
    await act(async () => { racine.render(<EngagementsSoutien apiFetch={apiFetch} />); await attendre(); });
    expect(tid("sa-engagements-erreur")!.textContent).toContain("Impossible de lire les engagements");
    expect(tid("sa-engagements-table")).toBeNull();

    // Une liste là où un objet est attendu : la console le dit au lieu de s'effondrer.
    for (const forme of [[], { politiques: [] }]) {
      act(() => racine.unmount());
      racine = createRoot(conteneur);
      apiFetch.mockReset().mockReturnValueOnce(reponse(forme));
      await act(async () => { racine.render(<EngagementsSoutien apiFetch={apiFetch} />); await attendre(); });
      expect(tid("sa-engagements-erreur")!.textContent).toContain("Impossible de lire les engagements");
    }
  });

  it("filtre les organismes par nom, sans égard à la casse", async () => {
    apiFetch.mockReturnValueOnce(reponse({ politiques: POLITIQUES, organismes: [organisme("standard", "Plateforme"),
      { tenantId: "t-css", nom: "CSS des Chênes", politique: "standard", libelle: "Plateforme", modifieLe: "2026-11-01T00:00:00.000Z", modifiePar: null }] }));
    await act(async () => { racine.render(<EngagementsSoutien apiFetch={apiFetch} />); await attendre(); });
    expect(tid("sa-engagement-t-css")!.textContent).toContain("2026-11-01 par —");
    const champ = tid("sa-engagements-filtre") as HTMLInputElement;
    await act(async () => {
      Object.getOwnPropertyDescriptor(HTMLInputElement.prototype, "value")!.set!.call(champ, " URGENCES ");
      champ.dispatchEvent(new Event("input", { bubbles: true }));
    });
    expect(tid("sa-engagement-t-us")).not.toBeNull();
    expect(tid("sa-engagement-t-css")).toBeNull();
  });

  it("montre « aucun organisme » quand la liste est vide", async () => {
    apiFetch.mockReturnValueOnce(reponse({ politiques: POLITIQUES, organismes: [] }));
    await act(async () => { racine.render(<EngagementsSoutien apiFetch={apiFetch} />); await attendre(); });
    expect(tid("sa-engagements-table")!.textContent).toContain("Aucun organisme.");
  });
});

describe("VerdictsDisponibilite", () => {
  let conteneur: HTMLDivElement;
  let racine: Root;
  beforeEach(() => { conteneur = document.createElement("div"); document.body.appendChild(conteneur); racine = createRoot(conteneur); });
  afterEach(() => { act(() => racine.unmount()); conteneur.remove(); });

  it("écrit le verdict de chaque politique, et rien sans mesure", () => {
    act(() => racine.render(<VerdictsDisponibilite verdicts={[
      { politique: "standard", libelle: "Plateforme", objectifPourcent: 99.5, seuilManquementMajeurPourcent: null, manquement: "aucun" },
      { politique: "service-essentiel", libelle: "Service essentiel", objectifPourcent: 99.7, seuilManquementMajeurPourcent: 99, manquement: "manquement_majeur" },
      { politique: "autre", libelle: "Autre", objectifPourcent: 90, seuilManquementMajeurPourcent: null, manquement: "inconnu" },
    ]} />));
    expect(conteneur.querySelector("[data-testid='sa-dispo-verdict-standard']")!.textContent).toBe("Plateforme — objectif 99,5 % : objectif atteint");
    expect(conteneur.querySelector("[data-testid='sa-dispo-verdict-service-essentiel']")!.textContent)
      .toBe("Service essentiel — objectif 99,7 %, manquement majeur sous 99 % : manquement majeur");
    expect(conteneur.querySelector("[data-testid='sa-dispo-verdict-autre']")!.textContent).toContain(": inconnu");
    act(() => racine.render(<VerdictsDisponibilite verdicts={[]} />));
    expect(conteneur.innerHTML).toBe("");
  });
});
