/**
 * Le formulaire d'absence d'effectifs : ce qu'il refuse avant d'envoyer (en disant quoi corriger),
 * le corps qu'il envoie à `POST /api/effectifs/absences`, et ce qu'il fait de la réponse.
 */
import { act } from "react";
import { createRoot, type Root } from "react-dom/client";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import FormulaireAbsence, { CHAMPS_VIDES, corpsDeclaration, manques, type ChampsAbsence } from "../src/absences/FormulaireAbsence";

(globalThis as unknown as { IS_REACT_ACT_ENVIRONMENT: boolean }).IS_REACT_ACT_ENVIRONMENT = true;

const base: ChampsAbsence = { ...CHAMPS_VIDES, du: "2030-03-04", au: "2030-03-05", motif: "MAL" };

describe("manques — ce que l'écran refuse avant d'envoyer", () => {
  it("rien ne manque quand le code et des dates ordonnées sont là", () => {
    expect(manques(base)).toEqual([]);
  });
  it("chaque manque dit quoi corriger", () => {
    expect(manques({ ...base, du: "", motif: "" })).toEqual(["Indiquez la date de début.", "Choisissez le code d'absence."]);
    expect(manques({ ...base, au: "2030-03-01" })).toEqual(["La date de fin doit suivre la date de début."]);
  });
});

describe("corpsDeclaration — le corps de POST /api/effectifs/absences", () => {
  it("soi-même par défaut ; la personne et le commentaire ne partent que s'ils sont donnés", () => {
    expect(corpsDeclaration(base)).toEqual({ du: "2030-03-04", au: "2030-03-05", motif: "MAL" });
    expect(corpsDeclaration({ ...base, au: "", personneId: "res-1", commentaire: "  Grippe " }))
      .toEqual({ du: "2030-03-04", au: "2030-03-04", motif: "MAL", personneId: "res-1", commentaire: "Grippe" });
  });
});

describe("FormulaireAbsence — l'écran", () => {
  let conteneur: HTMLDivElement;
  let racine: Root;
  beforeEach(() => { conteneur = document.createElement("div"); document.body.appendChild(conteneur); racine = createRoot(conteneur); });
  afterEach(() => { act(() => racine.unmount()); conteneur.remove(); });
  const tid = (id: string) => conteneur.querySelector(`[data-testid="${id}"]`) as HTMLElement | null;
  const reponse = (corps: unknown, ok = true, status = ok ? 200 : 400) => ({ ok, status, json: () => Promise.resolve(corps) }) as unknown as Response;
  const choisir = (el: HTMLElement | null, valeur: string) => act(() => {
    const champ = el as HTMLSelectElement;
    Object.getOwnPropertyDescriptor(HTMLSelectElement.prototype, "value")!.set!.call(champ, valeur);
    champ.dispatchEvent(new Event("change", { bubbles: true }));
  });

  function api(post: (corps: unknown) => Response) {
    const envois: unknown[] = [];
    const apiFetch = vi.fn(async (chemin: string, init?: RequestInit) => {
      if (init?.method === "POST") { const corps = JSON.parse(String(init.body)); envois.push({ chemin, corps }); return post(corps); }
      if (chemin.includes("codes_absence")) return reponse([{ code: "MAL", libelleFr: "Maladie", attributs: { duree: "PONCTUELLE" } }]);
      return reponse([]);
    });
    return { apiFetch, envois };
  }

  it("refuse sans code en disant quoi corriger, puis envoie et remonte la déclaration", async () => {
    const declaree = vi.fn();
    const { apiFetch, envois } = api(() => reponse({ categorie: "effectifs", ids: ["a-1"], statut: "ACCEPTEE" }, true, 201));
    await act(async () => { racine.render(<FormulaireAbsence apiFetch={apiFetch} estEmploye onDeclaree={declaree} />); });
    await act(async () => { tid("abs-submit")!.click(); });
    expect(tid("abs-form-error")!.textContent).toContain("Choisissez le code d'absence.");
    expect(envois).toHaveLength(0);
    choisir(tid("abs-eff-code"), "MAL");
    await act(async () => { tid("abs-submit")!.click(); });
    expect(envois).toEqual([{ chemin: "/api/effectifs/absences", corps: expect.objectContaining({ motif: "MAL" }) }]);
    expect(declaree).toHaveBeenCalledWith({ categorie: "effectifs", ids: ["a-1"], statut: "ACCEPTEE" });
  });

  it("une erreur du serveur s'affiche telle quelle ; sans corps lisible, son code", async () => {
    const declaree = vi.fn();
    const { apiFetch } = api(() => reponse({ errors: ["période close", "unité fermée"] }, false, 422));
    await act(async () => { racine.render(<FormulaireAbsence apiFetch={apiFetch} estEmploye onDeclaree={declaree} />); });
    choisir(tid("abs-eff-code"), "MAL");
    await act(async () => { tid("abs-submit")!.click(); });
    expect(tid("abs-form-error")!.textContent).toBe("période close ; unité fermée");
    expect(declaree).not.toHaveBeenCalled();
    const muet = api(() => reponse({}, false, 500));
    await act(async () => { racine.render(<FormulaireAbsence apiFetch={muet.apiFetch} estEmploye onDeclaree={declaree} />); });
    choisir(tid("abs-eff-code"), "MAL");
    await act(async () => { tid("abs-submit")!.click(); });
    expect(tid("abs-form-error")!.textContent).toBe("Erreur 500");
  });
});
