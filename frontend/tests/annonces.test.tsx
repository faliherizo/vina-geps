/**
 * Les annonces (lot E-4, ADR 0039) : à l'accueil, les non lues se marquent lues et l'état s'écrit ; rien à dire, rien de rendu ;
 * l'administration publie (rôles, période), lit les refus dans les mots du serveur, et retire.
 */
import { act } from "react";
import { createRoot, type Root } from "react-dom/client";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import AnnoncesAdmin, { AnnoncesAccueil, libelleRoles } from "../src/Annonces";

(globalThis as unknown as { IS_REACT_ACT_ENVIRONMENT: boolean }).IS_REACT_ACT_ENVIRONMENT = true;
const reponse = (corps: unknown, ok = true, status = ok ? 200 : 400) => ({ ok, status, json: () => Promise.resolve(corps) }) as unknown as Response;
const A1 = { id: "a1", titre: "Rentrée", corps: "Les horaires changent.", roles: ["employee"], du: "2026-08-24", au: "2026-09-04", auteur: "u", creeeLe: "x", lue: false, lectures: 2 };

describe("libelleRoles", () => {
  it("dit « Tous » sans rôle, sinon les libellés", () => {
    expect(libelleRoles([])).toBe("Tous");
    expect(libelleRoles(["employee", "rh", "x"])).toBe("Employés, Ressources humaines, x");
  });
});

describe("écrans", () => {
  let conteneur: HTMLDivElement;
  let racine: Root;
  beforeEach(() => { conteneur = document.createElement("div"); document.body.appendChild(conteneur); racine = createRoot(conteneur); });
  afterEach(() => { act(() => racine.unmount()); conteneur.remove(); });
  const tid = (id: string) => conteneur.querySelector(`[data-testid="${id}"]`) as HTMLElement | null;
  const cliquer = (id: string) => act(async () => { tid(id)!.click(); await new Promise((r) => setTimeout(r, 0)); });
  const saisir = (el: HTMLElement, valeur: string) => act(async () => {
    const proto = el instanceof HTMLTextAreaElement ? HTMLTextAreaElement.prototype : HTMLInputElement.prototype;
    Object.getOwnPropertyDescriptor(proto, "value")!.set!.call(el, valeur); el.dispatchEvent(new Event("input", { bubbles: true }));
  });

  it("accueil : rien sans annonce ; une non lue se marque lue et l'état s'écrit", async () => {
    let lue = false;
    const call = vi.fn(async (chemin: string, init?: RequestInit) => {
      if (init?.method === "POST") { lue = true; return reponse({ ...A1, lue: true }); }
      return reponse(chemin.endsWith("/annonces") ? [{ ...A1, lue }] : []);
    });
    const vide = vi.fn(async () => reponse([]));
    await act(async () => { racine.render(<AnnoncesAccueil call={vide} />); });
    expect(tid("annonces")).toBeNull();
    await act(async () => { racine.render(<AnnoncesAccueil call={call} />); });
    expect(tid("annonce-a1")!.textContent).toContain("Rentrée — non lue · jusqu'au 2026-09-04");
    await cliquer("annonce-lue-a1");
    expect(tid("annonce-a1")!.textContent).toContain("— lue");
    expect(tid("annonce-lue-a1")).toBeNull();
  });

  it("administration : publie avec rôles et période, dit le refus, retire", async () => {
    const appels: Array<{ chemin: string; methode: string; corps?: unknown }> = [];
    let refuser = true;
    const liste = [A1];
    const call = vi.fn(async (chemin: string, init?: RequestInit) => {
      const methode = init?.method ?? "GET";
      appels.push({ chemin, methode, corps: init?.body ? JSON.parse(String(init.body)) : undefined });
      if (methode === "POST") { if (refuser) { refuser = false; return reponse({ errors: ["au doit suivre du"] }, false); } liste.push({ ...A1, id: "a2", titre: "Grève" }); return reponse({ id: "a2", titre: "Grève" }); }
      if (methode === "DELETE") { liste.splice(0, liste.length); return { ok: true, status: 204, json: () => Promise.reject(new Error("vide")) } as unknown as Response; }
      return reponse([...liste]);
    });
    const signaler = vi.fn(); const confirmer = vi.fn();
    await act(async () => { racine.render(<AnnoncesAdmin call={call} signaler={signaler} confirmer={confirmer} />); });
    expect(tid("annonce-admin-a1")!.textContent).toContain("Employés");
    expect(tid("annonce-admin-a1")!.textContent).toContain("2");
    await saisir(tid("annonce-titre")!, "Grève");
    await saisir(tid("annonce-corps")!, "Mardi, pas de cours.");
    await cliquer("annonce-role-employee");
    await cliquer("annonce-role-director");
    await saisir(tid("annonce-du")!, "2026-10-10");
    await saisir(tid("annonce-au")!, "2026-10-01");
    await act(async () => { tid("annonce-publier")!.closest("form")!.requestSubmit(); await new Promise((r) => setTimeout(r, 0)); });
    expect(signaler).toHaveBeenCalledWith("au doit suivre du");
    await act(async () => { tid("annonce-publier")!.closest("form")!.requestSubmit(); await new Promise((r) => setTimeout(r, 0)); });
    expect(appels.filter((a) => a.methode === "POST").at(-1)!.corps).toEqual({ titre: "Grève", corps: "Mardi, pas de cours.", roles: ["employee", "director"], du: "2026-10-10", au: "2026-10-01" });
    expect(confirmer).toHaveBeenCalledWith("Annonce « Grève » publiée.");
    expect((tid("annonce-titre") as HTMLInputElement).value).toBe("");
    expect(tid("annonce-admin-a2")).not.toBeNull();
    await cliquer("annonce-retirer-a1");
    expect(confirmer).toHaveBeenCalledWith("Annonce « Rentrée » retirée.");
    expect(tid("annonces-vide")).not.toBeNull();
  });
});
