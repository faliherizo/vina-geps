/**
 * E-TN-6 vu de l'administration : les copies de l'organisme se listent avec leur instant et leur
 * taille, et se téléchargent sous leur nom ; un échec est annoncé.
 */
import { act } from "react";
import { createRoot, type Root } from "react-dom/client";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import CopiesOrganisme, { instantLisible, taille } from "../src/admin/CopiesOrganisme";

(globalThis as unknown as { IS_REACT_ACT_ENVIRONMENT: boolean }).IS_REACT_ACT_ENVIRONMENT = true;

describe("instantLisible / taille", () => {
  it("rend l'instant et la taille lisibles", () => {
    expect(instantLisible("2026-11-10T08-05-00-000Z")).toBe("2026-11-10 08:05 UTC");
    expect(instantLisible("autre")).toBe("autre");
    expect([taille(512), taille(2048), taille(3 * 1024 * 1024)]).toEqual(["512 o", "2.0 Ko", "3.0 Mo"]);
  });
});

describe("CopiesOrganisme", () => {
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
  const tid = (id: string) => conteneur.querySelector(`[data-testid="${id}"]`) as HTMLElement | null;
  const rendre = (apiFetch: ReturnType<typeof vi.fn>) =>
    act(async () => { racine.render(<CopiesOrganisme apiFetch={apiFetch} />); await new Promise((r) => setTimeout(r, 5)); });

  it("liste les copies et télécharge la copie choisie sous son nom", async () => {
    const apiFetch = vi.fn((chemin: string) => chemin.endsWith("/copies-organisme")
      ? Promise.resolve({ ok: true, json: () => Promise.resolve({ copies: [{ fichier: "2026-11-10T08-00-00-000Z-organisme.sqlite", faiteLe: "2026-11-10T08-00-00-000Z", octets: 4096 }] }) })
      : Promise.resolve({ ok: true, blob: () => Promise.resolve(new Blob(["SQLite"])) }));
    await rendre(apiFetch);
    expect(tid("copies-table")!.querySelector('th[scope="row"]')!.textContent).toBe("2026-11-10 08:00 UTC");
    expect(tid("copies-table")!.textContent).toContain("4.0 Ko");
    await act(async () => { tid("copie-2026-11-10T08-00-00-000Z-telecharger")!.click(); await new Promise((r) => setTimeout(r, 5)); });
    expect(apiFetch).toHaveBeenLastCalledWith("/api/exploitation/copies-organisme/2026-11-10T08-00-00-000Z-organisme.sqlite");
    expect(telecharges).toEqual(["2026-11-10T08-00-00-000Z-organisme.sqlite"]);
  });

  it("annonce un échec de téléchargement, et l'état vide", async () => {
    const apiFetch = vi.fn((chemin: string) => chemin.endsWith("/copies-organisme")
      ? Promise.resolve({ ok: true, json: () => Promise.resolve({ copies: [{ fichier: "f", faiteLe: "x", octets: 1 }] }) })
      : Promise.resolve({ ok: false, status: 404 }));
    await rendre(apiFetch);
    await act(async () => { tid("copie-x-telecharger")!.click(); await new Promise((r) => setTimeout(r, 5)); });
    expect(tid("copies-erreur")!.textContent).toContain("erreur 404");
    const vide = vi.fn(() => Promise.resolve({ ok: false, status: 403 }));
    await rendre(vide);
    expect(tid("copies-vide")).not.toBeNull();
  });

  it("dit quand la liste ne peut pas être lue", async () => {
    await rendre(vi.fn(() => Promise.reject(new Error("réseau"))));
    expect(tid("copies-erreur")!.textContent).toBe("Les copies n'ont pas pu être lues.");
  });
});
