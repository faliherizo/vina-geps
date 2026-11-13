/**
 * **L'acheminement des avis par unité**, vu de l'interface.
 *
 * Ce qui compte : choisir une unité reprend son réglage (on corrige une chaîne, on ne la retape
 * pas), seules les étapes choisies partent au serveur, dans l'ordre, et le tableau dit « défaut »
 * en toutes lettres plutôt que de laisser croire qu'un défaut est un choix.
 */
import { act } from "react";
import { createRoot, type Root } from "react-dom/client";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { SECTION_ACHEMINEMENT } from "../src/urgencessante/acheminementUnites";
import type { ContexteSection } from "../src/santequebec/noyau";

(globalThis as unknown as { IS_REACT_ACT_ENVIRONMENT: boolean }).IS_REACT_ACT_ENVIRONMENT = true;

const EST = { uniteId: "EST", nom: "Secteur Est", parDefaut: false, modifieLe: "x", modifiePar: "julie.roy",
  canaux: [{ canal: "sms", mode: "prioritaire", ordrePriorite: 1, parDefaut: false, libelle: "Texto (SMS) — Prioritaire" },
           { canal: "appel", mode: "sonnerie", ordrePriorite: 2, parDefaut: false, libelle: "Appel téléphonique — Sonnerie" }] };
const OUEST = { uniteId: "OUEST", nom: "Secteur Ouest", parDefaut: true, modifieLe: null, modifiePar: null,
  canaux: [{ canal: "courriel", mode: "sonnerie", ordrePriorite: 1, parDefaut: true, libelle: "Courriel — Sonnerie" }] };
const DONNEES = {
  unites: [EST, OUEST],
  canaux: [{ code: "courriel", libelle: "Courriel" }, { code: "sms", libelle: "Texto (SMS)" }, { code: "appel", libelle: "Appel téléphonique" }],
  modes: [{ code: "sonnerie", libelle: "Sonnerie" }, { code: "prioritaire", libelle: "Prioritaire" }],
};

describe("Acheminement des avis par unité", () => {
  let conteneur: HTMLDivElement;
  let racine: Root;
  let ctx: ContexteSection;
  beforeEach(() => {
    conteneur = document.createElement("div"); document.body.appendChild(conteneur); racine = createRoot(conteneur);
    ctx = { perimetre: {} as ContexteSection["perimetre"], donnees: DONNEES, recharger: vi.fn(), appeler: vi.fn().mockResolvedValue({ uniteId: "OUEST" }),
      annoncer: vi.fn(), signaler: vi.fn() };
  });
  afterEach(() => { act(() => racine.unmount()); conteneur.remove(); });

  const tid = (id: string) => conteneur.querySelector(`[data-testid="${id}"]`) as HTMLSelectElement;
  const choisir = (id: string, valeur: string) => act(async () => {
    const el = tid(id);
    Object.getOwnPropertyDescriptor(HTMLSelectElement.prototype, "value")!.set!.call(el, valeur);
    el.dispatchEvent(new Event("change", { bubbles: true }));
  });

  it("le tableau dit l'origine de chaque réglage, et ne propose de rétablir que ce qui a été réglé", () => {
    const t = SECTION_ACHEMINEMENT.tableau!;
    const lignes = t.lignes!(DONNEES) as typeof DONNEES.unites;
    expect(lignes.map((l) => l.uniteId)).toEqual(["EST", "OUEST"]);
    expect(t.colonnes[1].valeur(EST)).toBe("1. Texto (SMS) — Prioritaire → 2. Appel téléphonique — Sonnerie");
    expect(t.colonnes[2].valeur(EST)).toBe("Réglée par julie.roy");
    expect(t.colonnes[2].valeur(OUEST)).toBe("Défaut de l'établissement");
    expect(t.actions![0].masquer!(OUEST)).toBe(true);
    expect(t.actions![0].masquer!(EST)).toBe(false);
    expect(t.actions![0].libelle(EST)).toContain("Secteur Est");
    expect(t.lignes!(null)).toEqual([]);
  });

  it("rétablir le défaut appelle le serveur, annonce et recharge", async () => {
    await SECTION_ACHEMINEMENT.tableau!.actions![0].executer(EST, ctx);
    expect(ctx.appeler).toHaveBeenCalledWith("/api/communication/acheminement/unites/EST", { method: "DELETE" });
    expect(ctx.annoncer).toHaveBeenCalledWith(expect.stringContaining("Secteur Est"));
    expect(ctx.recharger).toHaveBeenCalled();
  });

  it("choisir une unité reprend son réglage ; seules les étapes choisies partent, dans l'ordre", async () => {
    await act(async () => { racine.render(<>{SECTION_ACHEMINEMENT.rendu!(ctx)}</>); });
    expect(tid("us-ach-enregistrer").hasAttribute("disabled")).toBe(true);
    await choisir("us-ach-unite", "EST");
    expect([tid("us-ach-canal-1").value, tid("us-ach-mode-1").value, tid("us-ach-canal-2").value, tid("us-ach-canal-3").value]).toEqual(["sms", "prioritaire", "appel", ""]);
    // Une unité au défaut reprend le courriel en premier canal, et rien après.
    await choisir("us-ach-unite", "OUEST");
    expect([tid("us-ach-canal-1").value, tid("us-ach-canal-2").value]).toEqual(["courriel", ""]);
    expect(tid("us-ach-mode-2").disabled).toBe(true);
    await choisir("us-ach-canal-2", "appel");
    await choisir("us-ach-mode-2", "prioritaire");
    await act(async () => { (conteneur.querySelector("form") as HTMLFormElement).requestSubmit(); await new Promise((r) => setTimeout(r, 5)); });
    expect(ctx.appeler).toHaveBeenCalledWith("/api/communication/acheminement/unites/OUEST",
      { method: "PUT", body: JSON.stringify({ etapes: [{ canal: "courriel", mode: "sonnerie" }, { canal: "appel", mode: "prioritaire" }] }) });
    expect(ctx.annoncer).toHaveBeenCalledWith("Acheminement de l'unité « Secteur Ouest » enregistré : 2 canal(aux), dans l'ordre choisi.");
  });

  it("un refus du serveur n'annonce rien", async () => {
    ctx.appeler = vi.fn().mockResolvedValue(null);
    await act(async () => { racine.render(<>{SECTION_ACHEMINEMENT.rendu!({ ...ctx, donnees: null })}</>); });
    expect(conteneur.querySelectorAll("#us-ach-unite option")).toHaveLength(1);
    await act(async () => { racine.render(<>{SECTION_ACHEMINEMENT.rendu!(ctx)}</>); });
    await choisir("us-ach-unite", "EST");
    await act(async () => { (conteneur.querySelector("form") as HTMLFormElement).requestSubmit(); await new Promise((r) => setTimeout(r, 5)); });
    expect(ctx.annoncer).not.toHaveBeenCalled();
  });
});
