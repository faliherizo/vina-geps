/**
 * P-TN-1 vu de l'écran : exporter donne un fichier daté ; un paquet choisi se simule, son bilan
 * s'affiche ligne par ligne, puis s'applique à la date choisie ; un fichier illisible est refusé.
 */
import { act } from "react";
import { createRoot, type Root } from "react-dom/client";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

const apiFetch = vi.fn();
vi.mock("../src/AuthContext", () => ({ useAuth: () => ({ apiFetch }) }));

import PromotionConfiguration, { lendemain } from "../src/effectifs/PromotionConfiguration";

(globalThis as unknown as { IS_REACT_ACT_ENVIRONMENT: boolean }).IS_REACT_ACT_ENVIRONMENT = true;

const reponse = (corps: unknown, status = 200) => Promise.resolve({ ok: status < 300, status, json: () => Promise.resolve(corps) });
const BILAN = { applique: false, priseDEffet: "2027-02-01", creer: 1, remplacer: 1, inchange: 0,
  lignes: [{ objet: "referentiels.unites", cle: "US", action: "creer" }, { objet: "regle", cle: "ECHANGE|contigu|ORGANISME|null", action: "remplacer" }] };

describe("lendemain", () => {
  it("passe au jour suivant, fin de mois comprise", () => {
    expect(lendemain("2027-01-31")).toBe("2027-02-01");
    expect(lendemain("2028-02-28")).toBe("2028-02-29");
  });
});

describe("PromotionConfiguration", () => {
  let conteneur: HTMLDivElement;
  let racine: Root;
  let telecharges: string[];
  let appliques: string[];
  beforeEach(() => {
    conteneur = document.createElement("div");
    document.body.appendChild(conteneur);
    racine = createRoot(conteneur);
    telecharges = [];
    appliques = [];
    apiFetch.mockReset();
    URL.createObjectURL = vi.fn(() => "blob:x");
    URL.revokeObjectURL = vi.fn();
    vi.spyOn(HTMLAnchorElement.prototype, "click").mockImplementation(function (this: HTMLAnchorElement) { telecharges.push(this.download); });
  });
  afterEach(() => { act(() => racine.unmount()); conteneur.remove(); vi.restoreAllMocks(); });
  const tid = (id: string) => document.querySelector(`[data-testid="${id}"]`) as HTMLElement | null;
  const attendre = () => new Promise((r) => setTimeout(r, 5));
  const ouvrir = async () => {
    await act(async () => { racine.render(<PromotionConfiguration onApplique={(m) => appliques.push(m)} />); });
    await act(async () => { tid("ref-promotion")!.click(); });
  };
  const choisirFichier = async (contenu: string) => {
    const champ = tid("ref-promotion-fichier") as HTMLInputElement;
    const fichier = new File([contenu], "paquet.json", { type: "application/json" });
    Object.defineProperty(fichier, "text", { value: () => Promise.resolve(contenu) });
    Object.defineProperty(champ, "files", { value: [fichier], configurable: true });
    await act(async () => { champ.dispatchEvent(new Event("change", { bubbles: true })); await attendre(); });
  };

  it("exporte le paquet en un fichier daté", async () => {
    apiFetch.mockReturnValueOnce(reponse({ format: "nexu-effectifs-configuration" }));
    await ouvrir();
    await act(async () => { tid("ref-promotion-exporter")!.click(); await attendre(); });
    expect(apiFetch).toHaveBeenCalledWith("/api/effectifs/configuration/paquet");
    expect(telecharges[0]).toMatch(/^configuration-effectifs-\d{4}-\d{2}-\d{2}\.json$/);
  });

  it("simule, montre le bilan, puis applique à la date choisie", async () => {
    await ouvrir();
    expect((tid("ref-promotion-simuler") as HTMLButtonElement).disabled).toBe(true);
    await choisirFichier("{\"format\":\"nexu-effectifs-configuration\",\"version\":1}");
    await act(async () => {
      const d = tid("ref-promotion-depuis") as HTMLInputElement;
      const setter = Object.getOwnPropertyDescriptor(HTMLInputElement.prototype, "value")!.set!;
      setter.call(d, "2027-02-01");
      d.dispatchEvent(new Event("input", { bubbles: true }));
    });
    apiFetch.mockReturnValueOnce(reponse(BILAN));
    await act(async () => { tid("ref-promotion-simuler")!.click(); await attendre(); });
    expect(apiFetch.mock.calls[0][0]).toBe("/api/effectifs/configuration/paquet?appliquer=false&depuis=2027-02-01");
    expect(tid("ref-promotion-bilan")!.textContent).toContain("Simulation : 1 à créer, 1 à remplacer (à partir du 2027-02-01), 0 inchangé(s).");
    expect([...tid("ref-promotion-lignes")!.querySelectorAll("tbody tr")].map((tr) => tr.lastElementChild!.textContent)).toEqual(["À créer", "À remplacer"]);
    apiFetch.mockReturnValueOnce(reponse({ ...BILAN, applique: true }));
    await act(async () => { tid("ref-promotion-appliquer")!.click(); await attendre(); });
    expect(apiFetch.mock.calls[1][0]).toContain("appliquer=true");
    expect(appliques).toEqual(["Configuration appliquée : 1 création(s), 1 remplacement(s) à partir du 2027-02-01."]);
    expect(tid("ref-promotion-appliquer")).toBeNull();
  });

  it("refuse un fichier illisible, et dit le refus du serveur", async () => {
    await ouvrir();
    await choisirFichier("pas du json");
    expect(tid("ref-promotion-erreur")!.textContent).toContain("« paquet.json » n'est pas un fichier JSON lisible.");
    await choisirFichier("{}");
    apiFetch.mockReturnValueOnce(reponse({ errors: ["ce document n'est pas un paquet de configuration des effectifs"] }, 422));
    await act(async () => { tid("ref-promotion-simuler")!.click(); await attendre(); });
    expect(tid("ref-promotion-erreur")!.textContent).toContain("pas un paquet de configuration");
    expect(tid("ref-promotion-bilan")).toBeNull();
  });
});
