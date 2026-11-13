/**
 * **Le champ « personne » cherche là où on le lui dit.**
 *
 * Il ne connaissait qu'une population : l'effectif. Déléguer ses approbations désigne un
 * **compte**, pas une fiche d'employé — deux populations voisines mais distinctes. Plutôt qu'un
 * second combobox, la source est devenue un paramètre ; ces essais figent les deux choses que
 * cela doit garantir : la source par défaut n'a pas bougé pour les onze écrans qui l'utilisent,
 * et le seuil de trois caractères tient quelle que soit la source.
 */
import { act } from "react";
import { createRoot, type Root } from "react-dom/client";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import ChampPersonne from "../src/ChampPersonne";

(globalThis as unknown as { IS_REACT_ACT_ENVIRONMENT: boolean }).IS_REACT_ACT_ENVIRONMENT = true;

const reponse = (corps: unknown) => ({ ok: true, status: 200, json: () => Promise.resolve(corps) }) as unknown as Response;
const ALEX = { id: "usr-9", nom: "Alex Bérubé", titre: "manager", courriel: "alex@x.ca", statut: "actif" };

describe("ChampPersonne — la population cherchée", () => {
  let conteneur: HTMLDivElement;
  let racine: Root;

  beforeEach(() => {
    vi.useFakeTimers();
    conteneur = document.createElement("div");
    document.body.appendChild(conteneur);
    act(() => { racine = createRoot(conteneur); });
  });
  afterEach(() => {
    act(() => racine.unmount());
    conteneur.remove();
    vi.useRealTimers();
  });

  /**
   * Saisit dans l'entrée puis laisse filer le délai anti-rebond.
   *
   * Poser `value` directement n'atteint pas l'état React — il compare à sa propre valeur et
   * n'émet rien. Le passage par le descripteur natif est la convention du dépôt.
   */
  const taper = async (texte: string) => {
    const entree = conteneur.querySelector("input") as HTMLInputElement;
    await act(async () => {
      Object.getOwnPropertyDescriptor(HTMLInputElement.prototype, "value")!.set!.call(entree, texte);
      entree.dispatchEvent(new Event("input", { bubbles: true }));
    });
    await act(async () => { await vi.advanceTimersByTimeAsync(250); });
  };

  const monter = (props: Partial<Parameters<typeof ChampPersonne>[0]> = {}) => {
    const appel = vi.fn().mockResolvedValue(reponse([ALEX]));
    const onChoisir = vi.fn();
    act(() => {
      racine.render(<ChampPersonne libelle="Délégataire" testId="deleg" valeur="" onChoisir={onChoisir} appel={appel} {...props} />);
    });
    return { appel, onChoisir };
  };

  it("sans source déclarée, interroge l'effectif — les onze écrans qui l'utilisent ne changent pas", async () => {
    const { appel } = monter();
    await taper("ale");
    expect(appel).toHaveBeenCalledWith("/api/personnel/employes/suggestions?q=ale");
  });

  it("avec une source, interroge celle-là et rien d'autre", async () => {
    const { appel } = monter({ source: { suggerer: "/api/approvals/delegataires" } });
    await taper("ale");
    expect(appel).toHaveBeenCalledWith("/api/approvals/delegataires?q=ale");
  });

  it("ne demande rien en deçà de trois caractères, quelle que soit la source", async () => {
    const { appel } = monter({ source: { suggerer: "/api/approvals/delegataires" } });
    await taper("al");
    expect(appel).not.toHaveBeenCalled();
  });

  it("transmet l'identifiant du compte choisi, et affiche son nom", async () => {
    const { appel, onChoisir } = monter({ source: { suggerer: "/api/approvals/delegataires" } });
    await taper("ale");
    const option = conteneur.querySelector('[role="option"]') as HTMLElement;
    expect(option.textContent).toContain("Alex Bérubé");
    await act(async () => { option.dispatchEvent(new MouseEvent("mousedown", { bubbles: true })); option.click(); });
    expect(onChoisir).toHaveBeenCalledWith("usr-9");
    // Sans chemin de résolution, rien de plus n'est demandé : il n'y a rien à relire.
    expect(appel.mock.calls.map((c) => String(c[0]))).toEqual(["/api/approvals/delegataires?q=ale"]);
  });

  it("annonce le nombre de résultats — sans quoi on ignore que la liste s'est remplie", async () => {
    monter({ source: { suggerer: "/api/approvals/delegataires" } });
    await taper("ale");
    const statut = conteneur.querySelector('[role="status"]') as HTMLElement;
    expect(statut.textContent).toMatch(/1/);
    const entree = conteneur.querySelector("input") as HTMLInputElement;
    expect(entree.getAttribute("aria-expanded")).toBe("true");
  });
});
