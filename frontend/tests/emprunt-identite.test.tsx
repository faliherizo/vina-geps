/**
 * L'emprunt d'identité (lot E-3, ADR 0038) vu de l'écran : le panneau règle, choisit un compte inférieur, exige un motif
 * et repart de l'accueil ; le bandeau dit qui agit comme qui jusqu'à quand, et « Terminer » rend la session.
 */
import { act } from "react";
import { createRoot, type Root } from "react-dom/client";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

const auth = vi.hoisted(() => ({ user: null as any, refreshUser: vi.fn(async () => {}), apiFetch: vi.fn() }));
const nav = vi.hoisted(() => ({ naviguerVers: vi.fn() }));
vi.mock("../src/AuthContext", () => ({ useAuth: () => auth }));
vi.mock("../src/navigation", () => ({ naviguerVers: nav.naviguerVers }));
import EmpruntIdentitePanel, { EmpruntBandeau, heureCourte } from "../src/admin/EmpruntIdentite";

(globalThis as unknown as { IS_REACT_ACT_ENVIRONMENT: boolean }).IS_REACT_ACT_ENVIRONMENT = true;
const reponse = (corps: unknown, ok = true, status = ok ? 200 : 400) => ({ ok, status, json: () => Promise.resolve(corps) }) as unknown as Response;
const COMPTES = [{ id: "u-admin", username: "admin", role: "admin", active: true }, { id: "u-emp", username: "lea", role: "employee", active: true }, { id: "u-off", username: "off", role: "rh", active: false }, { id: "u-sa", username: "sa", role: "superadmin", active: true }];

describe("heureCourte", () => {
  it("écrit l'heure locale en toutes lettres", () => {
    expect(heureCourte(new Date(2026, 8, 17, 10, 42).toISOString())).toMatch(/^10 h 42/);
  });
});

describe("EmpruntIdentitePanel et EmpruntBandeau", () => {
  let conteneur: HTMLDivElement;
  let racine: Root;
  beforeEach(() => { conteneur = document.createElement("div"); document.body.appendChild(conteneur); racine = createRoot(conteneur); auth.user = { id: "u-admin", username: "admin", role: "admin" }; nav.naviguerVers.mockClear(); auth.refreshUser.mockClear(); });
  afterEach(() => { act(() => racine.unmount()); conteneur.remove(); });
  const tid = (id: string) => conteneur.querySelector(`[data-testid="${id}"]`) as HTMLElement | null;
  const cliquer = (id: string) => act(async () => { tid(id)!.click(); await new Promise((r) => setTimeout(r, 0)); });
  const saisir = (el: HTMLElement, valeur: string) => act(async () => { Object.getOwnPropertyDescriptor(HTMLInputElement.prototype, "value")!.set!.call(el, valeur); el.dispatchEvent(new Event("input", { bubbles: true })); });
  const choisir = (el: HTMLElement, valeur: string) => act(async () => { (el as HTMLSelectElement).value = valeur; el.dispatchEvent(new Event("change", { bubbles: true })); });

  it("fermé par défaut ; ouvert, seuls les comptes inférieurs actifs sont proposés ; le motif est exigé ; un refus est dit ; l'emprunt repart de l'accueil", async () => {
    const appels: Array<{ chemin: string; methode: string; corps?: unknown }> = [];
    let reglage = { empruntIdentite: false, modifieLe: null, modifiePar: null };
    let refuser = true;
    const call = vi.fn(async (chemin: string, init?: RequestInit) => {
      const methode = init?.method ?? "GET"; appels.push({ chemin, methode, corps: init?.body ? JSON.parse(String(init.body)) : undefined });
      if (chemin === "/api/auth/emprunt/reglage" && methode === "PUT") { reglage = { ...reglage, empruntIdentite: (init!.body as string).includes("true"), modifieLe: "2026-09-17T10:00:00.000Z" }; return reponse(reglage); }
      if (chemin === "/api/auth/emprunt/reglage") return reponse(reglage);
      if (chemin === "/api/auth/emprunt/journal") return reponse([{ id: "e-1", emprunteur: "admin", cible: "lea", motif: "vérifier", debuteLe: "2026-09-17T10:00:00.000Z", expireLe: "2026-09-17T11:00:00.000Z", termineLe: null, actif: true }, { id: "e-0", emprunteur: "admin", cible: "lea", motif: "ancien", debuteLe: "2026-09-16T10:00:00.000Z", expireLe: "2026-09-16T11:00:00.000Z", termineLe: "2026-09-16T10:30:00.000Z", actif: false }]);
      if (chemin === "/api/auth/emprunt") { if (refuser) { refuser = false; return reponse({ errors: ["compte désactivé"] }, false, 403); } return reponse({ token: "t", user: { id: "u-emp" } }); }
      return reponse({});
    });
    await act(async () => { racine.render(<EmpruntIdentitePanel apiFetch={call} comptes={COMPTES} />); });
    expect((tid("emprunt-reglage") as HTMLInputElement).checked).toBe(false);
    expect(tid("emprunt-cible")).toBeNull();
    expect(tid("emprunt-e-1")!.textContent).toContain("En cours");
    expect(tid("emprunt-e-0")!.textContent).toContain("Terminé");
    await cliquer("emprunt-reglage");
    expect(tid("emprunt-info")!.textContent).toContain("ouvert");
    const options = Array.from(tid("emprunt-cible")!.querySelectorAll("option")).map((o) => o.textContent);
    expect(options).toEqual(["— choisir —", "lea (employee)"]);   // ni soi-même, ni un admin, ni un compte inactif
    expect((tid("emprunt-confirmer") as HTMLButtonElement).disabled).toBe(true);
    await choisir(tid("emprunt-cible")!, "u-emp");
    await saisir(tid("emprunt-motif")!, "rep");
    expect((tid("emprunt-confirmer") as HTMLButtonElement).disabled).toBe(true);
    await saisir(tid("emprunt-motif")!, "reproduire le problème");
    await cliquer("emprunt-confirmer");
    expect(tid("emprunt-erreur")!.textContent).toBe("compte désactivé");
    expect(nav.naviguerVers).not.toHaveBeenCalled();
    await cliquer("emprunt-confirmer");
    expect(appels.filter((a) => a.chemin === "/api/auth/emprunt").at(-1)!.corps).toEqual({ cibleId: "u-emp", motif: "reproduire le problème" });
    expect(auth.refreshUser).toHaveBeenCalledTimes(1);
    expect(nav.naviguerVers).toHaveBeenCalledWith("dashboard");
    await cliquer("emprunt-reglage");
    expect(tid("emprunt-info")!.textContent).toContain("fermé");
  });

  it("le bandeau n'existe que sous emprunt ; il nomme les deux identités et l'échéance ; Terminer rend la session (un refus est dit)", async () => {
    await act(async () => { racine.render(<EmpruntBandeau />); });
    expect(tid("emprunt-bandeau")).toBeNull();
    auth.user = { id: "u-emp", username: "lea", role: "employee", emprunt: { id: "e-1", emprunteur: { id: "u-admin", username: "admin" }, expireLe: new Date(2026, 8, 17, 11, 0).toISOString() } };
    let refuser = true;
    auth.apiFetch = vi.fn(async () => { if (refuser) { refuser = false; return reponse({ errors: ["cette session n'est pas empruntée"] }, false); } return reponse({ token: "t" }); });
    await act(async () => { racine.render(<EmpruntBandeau />); });
    expect(tid("emprunt-bandeau")!.textContent).toContain("admin, vous agissez comme lea (employee) — jusqu'à 11 h 00");
    await cliquer("emprunt-terminer");
    expect(tid("emprunt-bandeau")!.textContent).toContain("cette session n'est pas empruntée");
    await cliquer("emprunt-terminer");
    expect(auth.refreshUser).toHaveBeenCalled();
    expect(nav.naviguerVers).toHaveBeenCalledWith("admin");
  });
});
