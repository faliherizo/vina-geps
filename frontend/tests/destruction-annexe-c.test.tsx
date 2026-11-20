/**
 * FX-AO-20260810, clause 10.18.04 b) iv) et annexe 10.18.04 C : une fois les données détruites, l'administration
 * télécharge l'attestation au gabarit du contrat en donnant la date de fin du contrat ; un refus est annoncé.
 */
import { act } from "react";
import { createRoot, type Root } from "react-dom/client";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import DestructionDonnees from "../src/DestructionDonnees";

(globalThis as unknown as { IS_REACT_ACT_ENVIRONMENT: boolean }).IS_REACT_ACT_ENVIRONMENT = true;

const ATTESTATION = { id: "a-1", tenantNom: "Laurentides", detruitLe: "2031-04-02T14:00:00Z", detruitPar: "superadmin", motif: null,
  tables: [{ table: "employees", lignes: 3 }], totalLignes: 3, empreinte: "ab12cd34ef56ab12cd34" };

describe("DestructionDonnees — annexe 10.18.04 C", () => {
  let conteneur: HTMLDivElement;
  let racine: Root;
  let telecharges: string[];
  beforeEach(() => {
    conteneur = document.createElement("div");
    document.body.appendChild(conteneur);
    racine = createRoot(conteneur);
    telecharges = [];
    URL.createObjectURL = vi.fn(() => "blob:x");
    URL.revokeObjectURL = vi.fn();
    vi.spyOn(HTMLAnchorElement.prototype, "click").mockImplementation(function (this: HTMLAnchorElement) { telecharges.push(this.download); });
  });
  afterEach(() => { act(() => racine.unmount()); conteneur.remove(); vi.restoreAllMocks(); });
  const tid = (id: string) => document.querySelector(`[data-testid="${id}"]`) as HTMLElement | null;

  async function ouvrir(annexe: () => Promise<Response>) {
    const apiFetch = vi.fn((chemin: string) => chemin.includes("/annexe-c")
      ? annexe()
      : Promise.resolve({ ok: true, json: () => Promise.resolve(ATTESTATION) } as Response));
    await act(async () => {
      racine.render(<DestructionDonnees tenant={{ id: "t-9", name: "Laurentides", status: "terminated" }} apiFetch={apiFetch} signaler={vi.fn()} />);
      await new Promise((r) => setTimeout(r, 5));
    });
    await act(async () => { tid("sa-attestation-t-9")!.click(); await new Promise((r) => setTimeout(r, 5)); });
    const fin = tid("sa-annexe-fin-t-9") as HTMLInputElement;
    await act(async () => {
      Object.getOwnPropertyDescriptor(HTMLInputElement.prototype, "value")!.set!.call(fin, "2031-03-31");
      fin.dispatchEvent(new Event("input", { bubbles: true }));
    });
    await act(async () => { (tid("sa-annexe-c-formulaire-t-9") as HTMLFormElement).requestSubmit(); await new Promise((r) => setTimeout(r, 5)); });
    return apiFetch;
  }

  it("le champ de date est libellé et relié à son aide ; l'annexe se télécharge sous son nom", async () => {
    const apiFetch = await ouvrir(() => Promise.resolve({ ok: true, status: 200, text: () => Promise.resolve("ANNEXE 10.18.04 C") } as Response));
    const fin = tid("sa-annexe-fin-t-9") as HTMLInputElement;
    expect(fin.closest("label")?.textContent).toContain("Date de fin du contrat");
    expect(document.getElementById(fin.getAttribute("aria-describedby")!)?.textContent).toContain("60 jours");
    expect(apiFetch).toHaveBeenCalledWith("/api/saas/tenants/t-9/attestation-destruction/annexe-c?finContrat=2031-03-31");
    expect(telecharges).toEqual(["annexe-10-18-04-C-t-9.txt"]);
    expect(tid("sa-annexe-erreur-t-9")).toBeNull();
  });

  it("un refus nommé du serveur est annoncé comme alerte", async () => {
    await ouvrir(() => Promise.resolve({ ok: false, status: 409,
      text: () => Promise.resolve(JSON.stringify({ errors: ["signataire autorisé non configuré (annexe 10.18.04 C) : SAAS_ATTESTATION_SIGNATAIRE"] })) } as Response));
    const erreur = tid("sa-annexe-erreur-t-9")!;
    expect(erreur.getAttribute("role")).toBe("alert");
    expect(erreur.textContent).toContain("SAAS_ATTESTATION_SIGNATAIRE");
    expect(telecharges).toEqual([]);
  });

  it("une réponse illisible donne un message générique avec le statut", async () => {
    await ouvrir(() => Promise.resolve({ ok: false, status: 502, text: () => Promise.resolve("<html>") } as Response));
    expect(tid("sa-annexe-erreur-t-9")!.textContent).toBe("refus du serveur (502)");
  });
});
