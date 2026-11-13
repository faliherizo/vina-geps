/**
 * **Le prédicat des modules garde son identité** entre deux rendus : il entre dans les
 * dépendances d'effets (`useAccesEnseignement`), et une fonction neuve à chaque rendu y
 * relançait la lecture des permissions sans fin — les onglets d'Absences clignotaient
 * (signalement du 16 septembre 2026).
 */
import { act, useEffect, useRef, useState } from "react";
import { createRoot, type Root } from "react-dom/client";
import { afterEach, beforeEach, describe, expect, it } from "vitest";
import { ModuleAccessProvider, useModuleAllowed } from "../src/moduleAccess";

(globalThis as unknown as { IS_REACT_ACT_ENVIRONMENT: boolean }).IS_REACT_ACT_ENVIRONMENT = true;

/** Compte les exécutions d'un effet qui dépend du prédicat, et se rend une seconde fois par un état local. */
function Sonde({ journal }: { journal: string[] }) {
  const peut = useModuleAllowed();
  const [, rendre] = useState(0);
  const rendus = useRef(0);
  rendus.current += 1;
  useEffect(() => { journal.push(`effet:${peut("mifi")}`); }, [peut, journal]);
  useEffect(() => { if (rendus.current === 1) rendre(1); }, []);
  return <span data-testid="sonde">{String(peut("mifi"))}/{String(peut())}</span>;
}

describe("useModuleAllowed", () => {
  let conteneur: HTMLDivElement;
  let racine: Root;
  beforeEach(() => { conteneur = document.createElement("div"); document.body.appendChild(conteneur); racine = createRoot(conteneur); });
  afterEach(() => { act(() => racine.unmount()); conteneur.remove(); });

  it("un second rendu ne relance pas l'effet ; un ensemble nouveau, oui ; sans filtrage tout est ouvert", async () => {
    const journal: string[] = [];
    await act(async () => { racine.render(<ModuleAccessProvider value={new Set(["mifi"])}><Sonde journal={journal} /></ModuleAccessProvider>); });
    await act(async () => { await Promise.resolve(); });
    expect(conteneur.textContent).toBe("true/true");
    expect(journal).toEqual(["effet:true"]);
    await act(async () => { racine.render(<ModuleAccessProvider value={new Set(["scolaire"])}><Sonde journal={journal} /></ModuleAccessProvider>); });
    expect(conteneur.textContent).toBe("false/true");
    expect(journal).toEqual(["effet:true", "effet:false"]);
    await act(async () => { racine.render(<ModuleAccessProvider value={null}><Sonde journal={journal} /></ModuleAccessProvider>); });
    expect(conteneur.textContent).toBe("true/true");
  });
});
