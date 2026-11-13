/**
 * P-TN-8 vu de l'écran : le format choisi part au serveur avec les filtres de la recherche, le
 * fichier reçu prend l'extension du format, et un refus est annoncé avec son motif.
 */
import { act } from "react";
import { createRoot, type Root } from "react-dom/client";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

const apiFetch = vi.fn();
vi.mock("../src/AuthContext", () => ({ useAuth: () => ({ apiFetch }) }));

import ExportResultats from "../src/effectifs/ExportResultats";

(globalThis as unknown as { IS_REACT_ACT_ENVIRONMENT: boolean }).IS_REACT_ACT_ENVIRONMENT = true;

describe("ExportResultats", () => {
  let conteneur: HTMLDivElement;
  let racine: Root;
  let telecharges: string[];
  beforeEach(() => {
    conteneur = document.createElement("div");
    document.body.appendChild(conteneur);
    racine = createRoot(conteneur);
    telecharges = [];
    apiFetch.mockReset();
    URL.createObjectURL = vi.fn(() => "blob:x");
    URL.revokeObjectURL = vi.fn();
    vi.spyOn(HTMLAnchorElement.prototype, "click").mockImplementation(function (this: HTMLAnchorElement) { telecharges.push(this.download); });
  });
  afterEach(() => { act(() => racine.unmount()); conteneur.remove(); vi.restoreAllMocks(); });
  const tid = (id: string) => conteneur.querySelector(`[data-testid="${id}"]`) as HTMLElement | null;
  const rendre = (desactive = false) => act(async () => {
    racine.render(<ExportResultats chemin="/api/effectifs/journal" parametres={new URLSearchParams({ action: "temps.export" })}
      nomFichier="journal-effectifs" prefixe="jrn" desactive={desactive} />);
  });
  const choisir = (valeur: string) => act(async () => {
    const s = tid("jrn-format") as HTMLSelectElement;
    s.value = valeur;
    s.dispatchEvent(new Event("change", { bubbles: true }));
  });
  const exporter = () => act(async () => { tid("jrn-exporter")!.click(); await new Promise((r) => setTimeout(r, 5)); });

  it("offre les quatre formats, chacun nommé, sous un libellé lié au champ", async () => {
    await rendre();
    const options = [...(tid("jrn-format") as HTMLSelectElement).options].map((o) => [o.value, o.textContent]);
    expect(options).toEqual([["csv", "CSV"], ["xlsx", "Excel (XLSX)"], ["pdf", "PDF"], ["json", "JSON"]]);
    expect(conteneur.querySelector('label[for="jrn-format"]')?.textContent).toBe("Format d'export");
    expect(tid("jrn-exporter")!.textContent).toContain("Exporter");
  });

  it("garde les filtres, ajoute le format choisi et nomme le fichier par son extension", async () => {
    apiFetch.mockResolvedValue({ ok: true, blob: () => Promise.resolve(new Blob(["x"])) });
    await rendre();
    await choisir("xlsx");
    await exporter();
    expect(apiFetch).toHaveBeenCalledWith("/api/effectifs/journal?action=temps.export&format=xlsx");
    expect(telecharges).toEqual(["journal-effectifs.xlsx"]);
    expect(tid("jrn-export-erreur")).toBeNull();
  });

  it("annonce le motif du refus, ou le code à défaut, sans télécharger", async () => {
    apiFetch.mockResolvedValueOnce({ ok: false, status: 422, json: () => Promise.resolve({ errors: ["format inconnu"] }) });
    await rendre();
    await exporter();
    expect(tid("jrn-export-erreur")!.getAttribute("role")).toBe("alert");
    expect(tid("jrn-export-erreur")!.textContent).toContain("format inconnu");
    apiFetch.mockResolvedValueOnce({ ok: false, status: 503, json: () => Promise.reject(new Error("pas de JSON")) });
    await exporter();
    expect(tid("jrn-export-erreur")!.textContent).toContain("L'export a échoué (erreur 503).");
    expect(telecharges).toEqual([]);
  });

  it("se désactive tant que la recherche n'est pas complète", async () => {
    await rendre(true);
    expect((tid("jrn-exporter") as HTMLButtonElement).disabled).toBe(true);
  });
});
