/**
 * Le panneau « Actions des utilisateurs » (ADR 0041) : lit la page, écrit le total, le statut en toutes lettres,
 * l'acteur réel sous emprunt ; les filtres repartent en page 0 et voyagent dans la requête ; la pagination ;
 * un refus du serveur se lit dans ses mots ; le champ « Organisme » n'apparaît qu'au superadmin.
 */
import { act } from "react";
import { createRoot, type Root } from "react-dom/client";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import JournalActions, { libelleStatut } from "../src/JournalActions";

(globalThis as unknown as { IS_REACT_ACT_ENVIRONMENT: boolean }).IS_REACT_ACT_ENVIRONMENT = true;
const reponse = (corps: unknown, ok = true, status = ok ? 200 : 400) => ({ ok, status, json: () => Promise.resolve(corps) }) as unknown as Response;
const action = (id: string, extra: Record<string, unknown> = {}) => ({ id, instant: "2026-09-19T14:02:11.412Z", tenantId: "t1", userId: "u1", utilisateur: "lea", acteurReel: null, acteurReelNom: null, methode: "PUT", chemin: "/api/notifications/preferences", statut: 200, dureeMs: 41, requeteId: "a3f9c2d1e4b7", ...extra });

describe("libelleStatut", () => {
  it("écrit le résultat en toutes lettres", () => {
    expect([200, 201, 400, 403, 404, 409, 500].map(libelleStatut)).toEqual(["200 — réussie", "201 — réussie", "400 — rejetée", "403 — refusée", "404 — introuvable", "409 — conflit", "500 — erreur du serveur"]);
  });
});

describe("panneau", () => {
  let conteneur: HTMLDivElement; let racine: Root;
  beforeEach(() => { conteneur = document.createElement("div"); document.body.appendChild(conteneur); racine = createRoot(conteneur); });
  afterEach(() => { act(() => racine.unmount()); conteneur.remove(); });
  const tid = (id: string) => conteneur.querySelector(`[data-testid="${id}"]`) as HTMLElement | null;
  const attendre = () => act(async () => { await new Promise((r) => setTimeout(r, 0)); });
  const saisir = (el: HTMLElement, valeur: string) => act(async () => {
    Object.getOwnPropertyDescriptor(HTMLInputElement.prototype, "value")!.set!.call(el, valeur); el.dispatchEvent(new Event("input", { bubbles: true })); await new Promise((r) => setTimeout(r, 0));
  });

  it("lit la page, écrit total, statut et acteur réel ; les filtres voyagent dans la requête et repartent en page 0 ; pagination", async () => {
    const appels: string[] = [];
    const apiFetch = vi.fn(async (chemin: string) => {
      appels.push(chemin);
      const page = Number(new URL(chemin, "http://x").searchParams.get("page"));
      return reponse({ items: page === 0 ? [action("a1"), action("a2", { statut: 403, methode: "POST", chemin: "/api/scolaire/absences", acteurReel: "adm", acteurReelNom: "admin-un" })] : [action("a3")], total: 30 });
    });
    await act(async () => { racine.render(<JournalActions apiFetch={apiFetch} superadmin={false} />); });
    await attendre();
    expect(tid("actions-total")!.textContent).toBe("(30)");
    expect(tid("action-statut-a1")!.textContent).toBe("200 — réussie");
    expect(tid("action-acteur-reel-a2")!.textContent).toContain("agissant : admin-un");
    expect(tid("action-a2")!.textContent).toContain("POST /api/scolaire/absences");
    expect(tid("actions-filtre-organisme")).toBeNull();
    expect(tid("actions-page")!.textContent).toBe("Page 1 / 2");
    await act(async () => { tid("actions-next")!.click(); await new Promise((r) => setTimeout(r, 0)); });
    expect(appels.at(-1)).toContain("page=1");
    expect(tid("action-a3")).not.toBeNull();
    await saisir(tid("actions-filtre-q")!, "403");
    await saisir(tid("actions-filtre-depuis")!, "2026-09-01");
    const derniere = new URL(appels.at(-1)!, "http://x").searchParams;
    expect(derniere.get("q")).toBe("403"); expect(derniere.get("depuis")).toBe("2026-09-01"); expect(derniere.get("page")).toBe("0");
  });

  it("superadmin : le filtre organisme ; un refus se lit dans les mots du serveur, la table se vide", async () => {
    const apiFetch = vi.fn(async () => reponse({ errors: ["depuis : date AAAA-MM-JJ"] }, false));
    await act(async () => { racine.render(<JournalActions apiFetch={apiFetch} superadmin />); });
    await attendre();
    expect(tid("actions-filtre-organisme")).not.toBeNull();
    expect(tid("actions-erreur")!.textContent).toBe("depuis : date AAAA-MM-JJ");
    expect(tid("actions-vide")).not.toBeNull();
    expect(tid("actions-page")).toBeNull();
  });
});
