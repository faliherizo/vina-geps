/**
 * L'administration des demandes de liaison (ADR 0093) : un tableau accessible, des boutons qui nomment la personne,
 * l'approbation et le refus envoyés au serveur, un refus du serveur annoncé ; et l'option juste-à-temps de l'annuaire.
 */
import { act } from "react";
import { createRoot, type Root } from "react-dom/client";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import AnnuairesOrganisme, { DemandesLiaisonOrganisme, type DemandeLiaison } from "../src/admin/AnnuairesOrganisme";

(globalThis as unknown as { IS_REACT_ACT_ENVIRONMENT: boolean }).IS_REACT_ACT_ENVIRONMENT = true;

const reponse = (corps: unknown, status = 200) => Promise.resolve({ ok: status < 300, status, json: () => Promise.resolve(corps) } as Response);
const JEAN: DemandeLiaison = { id: "liaison-1", courriel: "jean@urgences-sante.qc.ca", emetteur: "https://kc/realms/r", annuaireId: "ann-1", compteId: "u1",
  statut: "en_attente", creeLe: "2031-01-15T10:00:00Z" };

describe("DemandesLiaisonOrganisme", () => {
  let conteneur: HTMLDivElement;
  let racine: Root;
  let apiFetch: ReturnType<typeof vi.fn>;
  let demandes: DemandeLiaison[];
  beforeEach(() => {
    conteneur = document.createElement("div");
    document.body.appendChild(conteneur);
    racine = createRoot(conteneur);
    demandes = [JEAN, { ...JEAN, id: "liaison-2", courriel: "lea@urgences-sante.qc.ca" }];
    apiFetch = vi.fn((chemin: string, init?: RequestInit) => {
      if (chemin === "/api/auth/liaisons") return reponse({ demandes });
      if (chemin.endsWith("/liaison-2/approuver")) return reponse({ errors: ["le compte de l'application est désactivé"] }, 403);
      if (chemin.endsWith("/liaison-2/refuser")) return reponse(null, 500);
      if (init?.method === "POST") { demandes = demandes.filter((d) => !chemin.includes(d.id)); return reponse({}); }
      return reponse({}, 404);
    });
  });
  afterEach(() => { act(() => racine.unmount()); conteneur.remove(); });
  const tid = (id: string) => document.querySelector(`[data-testid="${id}"]`) as HTMLElement | null;
  const attendre = () => act(async () => { await new Promise((r) => setTimeout(r, 5)); });

  it("liste, approuve et refuse ; un refus du serveur est annoncé", async () => {
    await act(async () => { racine.render(<DemandesLiaisonOrganisme apiFetch={apiFetch} />); });
    await attendre();
    expect(document.querySelector("caption")!.textContent).toBe("Demandes de liaison en attente");
    expect(tid("liaison-liaison-1")!.querySelector('th[scope="row"]')!.textContent).toBe("jean@urgences-sante.qc.ca");
    expect(tid("liaison-liaison-1-approuver")!.getAttribute("aria-label")).toBe("Approuver la liaison de jean@urgences-sante.qc.ca");
    await act(async () => { tid("liaison-liaison-1-approuver")!.click(); });
    await attendre();
    expect(tid("liaisons-info")!.textContent).toBe("Identité de jean@urgences-sante.qc.ca reliée à son compte.");
    expect(tid("liaison-liaison-1")).toBeNull();
    await act(async () => { tid("liaison-liaison-2-approuver")!.click(); });
    await attendre();
    expect(tid("liaisons-erreur")!.textContent).toBe("le compte de l'application est désactivé");
    await act(async () => { tid("liaison-liaison-2-refuser")!.click(); });
    await attendre();
    expect(tid("liaisons-erreur")!.textContent).toBe("La décision a échoué (erreur 500).");
  });

  it("refuser retire la demande ; sans demande, l'état vide ; une lecture impossible est dite", async () => {
    demandes = [JEAN];
    await act(async () => { racine.render(<DemandesLiaisonOrganisme apiFetch={apiFetch} />); });
    await attendre();
    await act(async () => { tid("liaison-liaison-1-refuser")!.click(); });
    await attendre();
    expect(tid("liaisons-info")!.textContent).toBe("Demande de jean@urgences-sante.qc.ca refusée.");
    expect(tid("liaisons-vide")).not.toBeNull();
    act(() => racine.unmount());
    racine = createRoot(conteneur);
    await act(async () => { racine.render(<DemandesLiaisonOrganisme apiFetch={vi.fn(() => Promise.reject(new Error("réseau")))} />); });
    await attendre();
    expect(tid("liaisons-erreur")!.textContent).toBe("Les demandes de liaison n'ont pas pu être lues.");
  });

  it("l'annuaire porte l'option juste-à-temps, désactivée par défaut, expliquée", async () => {
    const envois: unknown[] = [];
    const api = vi.fn((chemin: string, init?: RequestInit) => {
      if (chemin === "/api/auth/assignable-roles") return reponse([{ role: "employee", label: "Employé" }]);
      if (chemin === "/api/auth/annuaires" && !init) return reponse({ annuaires: [], rappel: "" });
      if (init?.method === "POST") { envois.push(JSON.parse(String(init.body))); return reponse({}, 201); }
      return reponse({});
    });
    await act(async () => { racine.render(<AnnuairesOrganisme apiFetch={api} />); });
    await attendre();
    await act(async () => { tid("annuaire-nouveau-ouvrir")!.click(); });
    const jat = tid("annuaire-nouveau-jat") as HTMLInputElement;
    expect(jat.checked).toBe(false);
    expect(document.getElementById(jat.getAttribute("aria-describedby")!)!.textContent).toContain("aucun second compte");
    await act(async () => { jat.click(); });
    await act(async () => { (tid("annuaire-nouveau-formulaire") as HTMLFormElement).requestSubmit(); });
    await attendre();
    expect(envois[0]).toMatchObject({ provisionnementJat: true });
  });
});
