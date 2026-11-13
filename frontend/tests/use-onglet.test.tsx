/**
 * `useOnglet` : l'écran suit la navigation. Un onglet nommé s'affiche ; une navigation vers la page
 * sans onglet (entrée principale du menu, retour vers `/page`) ramène la vue par défaut — avant le
 * 12 nov. 2026, l'écran gardait l'onglet courant alors que l'adresse et le menu désignaient la vue
 * principale. Une navigation vers une autre page, ou un onglet inconnu, ne touche à rien.
 */
import { act } from "react";
import { createRoot, type Root } from "react-dom/client";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

import { OngletContext, type OngletDemande, useOnglet } from "../src/navigation";

(globalThis as unknown as { IS_REACT_ACT_ENVIRONMENT: boolean }).IS_REACT_ACT_ENVIRONMENT = true;

type Vue = "liste" | "detail" | "bilan";
const estVue = (s: string): s is Vue => ["liste", "detail", "bilan"].includes(s);

let changer: (t: Vue) => void = () => {};
function Ecran({ defaut = "liste" }: { defaut?: Vue }) {
  const [onglet, setOnglet] = useOnglet<Vue>("rapports", defaut, estVue);
  changer = setOnglet;
  return <p data-testid="onglet">{onglet}</p>;
}

describe("useOnglet", () => {
  let conteneur: HTMLDivElement;
  let racine: Root;
  beforeEach(() => {
    conteneur = document.createElement("div");
    document.body.appendChild(conteneur);
    racine = createRoot(conteneur);
    window.history.replaceState(null, "", "/rapports");
  });
  afterEach(() => { act(() => racine.unmount()); conteneur.remove(); vi.restoreAllMocks(); });

  const rendre = (demande: Partial<OngletDemande>, defaut?: Vue) => act(() => racine.render(
    <OngletContext.Provider value={{ page: "rapports", onglet: null, jeton: 0, notion: null, ...demande }}>
      <Ecran defaut={defaut} />
    </OngletContext.Provider>,
  ));
  const affiche = () => conteneur.querySelector('[data-testid="onglet"]')!.textContent;

  it("affiche l'onglet nommé, puis revient à la vue principale quand la navigation n'en nomme aucun", () => {
    rendre({ onglet: "detail", jeton: 1 });
    expect(affiche()).toBe("detail");
    rendre({ onglet: null, jeton: 2 });
    expect(affiche()).toBe("liste");
  });

  it("recliquer l'entrée principale ramène la vue par défaut après un changement d'onglet sur place", () => {
    rendre({ onglet: null, jeton: 1 });
    act(() => changer("bilan"));
    expect(affiche()).toBe("bilan");
    expect(window.location.search).toBe("?onglet=bilan");
    rendre({ onglet: null, jeton: 3 });
    expect(affiche()).toBe("liste");
  });

  it("une navigation vers une autre page, ou un onglet inconnu, laisse l'onglet tel quel", () => {
    rendre({ onglet: "bilan", jeton: 1 });
    rendre({ page: "finance", onglet: null, jeton: 2 });
    expect(affiche()).toBe("bilan");
    rendre({ onglet: "inconnu", jeton: 3 });
    expect(affiche()).toBe("bilan");
  });

  it("un défaut qui change de lui-même n'arrache pas l'utilisateur à son onglet", () => {
    rendre({ onglet: "detail", jeton: 1 });
    rendre({ onglet: "detail", jeton: 1 }, "bilan");
    expect(affiche()).toBe("detail");
    rendre({ onglet: null, jeton: 2 }, "bilan");
    expect(affiche()).toBe("bilan");
  });
});
