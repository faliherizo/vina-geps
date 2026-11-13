/**
 * **L'écran de consommation d'avis** (superadmin) vu de l'interface.
 *
 * Ce qui compte ici n'est pas que le tableau s'affiche, mais qu'il ne **laisse pas croire** à une
 * enveloppe respectée : les segments et les messages sont montrés séparément, le dépassement
 * porte un mot (et pas seulement une couleur), et le plafond ne part au serveur qu'au clic.
 */
import { act } from "react";
import { createRoot, type Root } from "react-dom/client";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import ConsommationAvis from "../src/ConsommationAvis";

(globalThis as unknown as { IS_REACT_ACT_ENVIRONMENT: boolean }).IS_REACT_ACT_ENVIRONMENT = true;
const reponse = (corps: unknown, ok = true, status = ok ? 200 : 400) =>
  ({ ok, status, json: () => Promise.resolve(corps) }) as unknown as Response;

const canal = (o: Partial<{ utilises: number; messages: number; limite: number; illimite: boolean; depassement: number; echecs: number }> = {}) =>
  ({ utilises: 0, messages: 0, limite: 0, illimite: true, depassement: 0, echecs: 0, ...o });

// Deux organismes : l'un qui déborde, l'autre non — c'est le tri et le mot « dépassement » qu'on éprouve.
const VUE = {
  mois: "2026-09",
  organismes: [
    { tenantId: "calme", nom: "École Calme", textos: canal({ utilises: 40, messages: 40, limite: 1000, illimite: false }), courriels: canal({ utilises: 12, messages: 12 }) },
    { tenantId: "chene", nom: "CSS des Chênes", textos: canal({ utilises: 3100, messages: 1200, limite: 2000, illimite: false, depassement: 1100 }), courriels: canal({ utilises: 900, messages: 900 }) },
  ],
  totaux: { textosSegments: 3140, textosMessages: 1240, courriels: 912, depassementTextos: 1100, depassementCourriels: 0 },
};

describe("Consommation d'avis — superadmin", () => {
  let conteneur: HTMLDivElement;
  let racine: Root;
  beforeEach(() => { conteneur = document.createElement("div"); document.body.appendChild(conteneur); racine = createRoot(conteneur); });
  afterEach(() => { act(() => racine.unmount()); conteneur.remove(); });

  const tid = (id: string) => conteneur.querySelector(`[data-testid="${id}"]`) as HTMLElement | null;
  const attendre = () => act(async () => { await new Promise((r) => setTimeout(r, 5)); });
  const cliquer = (id: string) => act(async () => { tid(id)!.click(); await new Promise((r) => setTimeout(r, 5)); });
  const saisir = (el: HTMLElement, v: string) => act(async () => {
    Object.getOwnPropertyDescriptor(HTMLInputElement.prototype, "value")!.set!.call(el, v);
    el.dispatchEvent(new Event("input", { bubbles: true }));
  });

  const monter = async (call: any) => {
    await act(async () => {
      racine.render(<ConsommationAvis apiFetch={call} setError={() => {}} setSuccess={() => {}} />);
      await new Promise((r) => setTimeout(r, 5));
    });
  };

  it("**montre les segments et les messages séparément** — l'écart est le propos de l'écran", async () => {
    await monter(vi.fn(async () => reponse(VUE)));
    // 3 100 segments pour 1 200 messages : une colonne « textos » seule aurait affiché 1 200 et
    // laissé croire que l'enveloppe de 2 000 était respectée.
    expect(tid("sa-conso-segments-chene")!.textContent).toContain("3");
    expect(tid("sa-conso-total-segments")!.textContent).not.toBe(tid("sa-conso-total-courriels")!.textContent);
    const resume = tid("sa-conso-resume")!;
    expect(resume.getAttribute("role")).toBe("status");   // une mise à jour asynchrone s'annonce
    expect(resume.textContent).toContain("segment");
    expect(resume.textContent).toContain("1 organisme(s) en dépassement.");
  });

  it("le dépassement porte un **mot**, jamais la seule couleur (WCAG 1.4.1)", async () => {
    await monter(vi.fn(async () => reponse(VUE)));
    expect(tid("sa-conso-depassement-chene")!.textContent).toContain("Dépassement");
    expect(tid("sa-conso-depassement-calme")!.textContent).toContain("Dans l'enveloppe");
  });

  it("place les organismes **en dépassement en tête** — c'est ce qu'on vient facturer", async () => {
    await monter(vi.fn(async () => reponse(VUE)));
    const lignes = [...conteneur.querySelectorAll('[data-testid^="sa-conso-row-"]')];
    expect(lignes[0].getAttribute("data-testid")).toBe("sa-conso-row-chene");
  });

  it("le plafond ne part **qu'au clic**, et le bouton reste inerte tant que rien n'a changé", async () => {
    const appels: Array<{ chemin: string; methode: string; corps?: any }> = [];
    const call = vi.fn(async (chemin: string, init?: RequestInit) => {
      appels.push({ chemin, methode: init?.method ?? "GET", corps: init?.body ? JSON.parse(String(init.body)) : undefined });
      return reponse(chemin.includes("quotas") ? { textos: { limite: 2500 } } : VUE);
    });
    await monter(call);

    // Sans modification, rien à enregistrer : le bouton est désactivé.
    expect((tid("sa-conso-save-chene") as HTMLButtonElement).disabled).toBe(true);

    await saisir(tid("sa-conso-lim-sms-chene")!, "2500");
    expect((tid("sa-conso-save-chene") as HTMLButtonElement).disabled).toBe(false);
    // La frappe seule n'appelle pas le serveur.
    expect(appels.filter((a) => a.methode === "PUT")).toHaveLength(0);

    await cliquer("sa-conso-save-chene");
    const put = appels.find((a) => a.methode === "PUT")!;
    expect(put.chemin).toBe("/api/saas/consommation/chene/quotas");
    // Les deux plafonds partent ensemble : en régler un seul laisserait l'autre à l'ancienne valeur.
    expect(put.corps).toEqual({ textos: 2500, courriels: 0 });
  });

  it("le détail par contexte explique la facture, et se ferme", async () => {
    const call = vi.fn(async (chemin: string) => reponse(
      chemin.includes("/consommation/chene?")
        ? { mois: "2026-09", contextes: [{ contexte: "scolaire:recherche-suppleant", canal: "texto", messages: 800, utilises: 2400 }] }
        : VUE,
    ));
    await monter(call);
    expect(tid("sa-conso-detail")).toBeNull();
    await cliquer("sa-conso-detail-chene");
    expect(tid("sa-conso-detail")!.textContent).toContain("scolaire:recherche-suppleant");
    // Le séparateur de milliers français est une espace **fine insécable** (U+202F) : chercher
    // une espace ordinaire échouerait alors que l'affichage est juste.
    expect(tid("sa-conso-detail")!.textContent).toMatch(/2\s400/);
    await cliquer("sa-conso-detail-fermer");
    expect(tid("sa-conso-detail")).toBeNull();
  });

  it("chaque champ de saisie porte un **libellé lié** — un `placeholder` n'en est pas un", async () => {
    await monter(vi.fn(async () => reponse(VUE)));
    for (const id of ["sa-conso-mois", "sa-conso-lim-sms-chene", "sa-conso-lim-mail-chene"]) {
      const champ = tid(id)!;
      const label = conteneur.querySelector(`label[for="${champ.id}"]`);
      expect(label, `le champ ${id} n'a pas de libellé lié`).not.toBeNull();
      expect(label!.textContent!.trim().length).toBeGreaterThan(0);
    }
    // Le tableau se décrit : un lecteur d'écran annonce à quoi il sert avant de le parcourir.
    const legende = conteneur.querySelector("table caption");
    expect(legende!.textContent).toContain("par organisme");
    expect([...conteneur.querySelectorAll("thead th")].every((th) => th.getAttribute("scope") === "col")).toBe(true);
  });

  it("un changement de mois **redemande** la consommation de ce mois", async () => {
    const chemins: string[] = [];
    const call = vi.fn(async (chemin: string) => { chemins.push(chemin); return reponse(VUE); });
    await monter(call);
    await saisir(tid("sa-conso-mois")!, "2026-01");
    await attendre();
    expect(chemins.some((c) => c.includes("mois=2026-01"))).toBe(true);
  });

  it("un mois effacé ne demande pas une URL vide — il retombe sur le mois courant", async () => {
    const chemins: string[] = [];
    const call = vi.fn(async (chemin: string) => { chemins.push(chemin); return reponse(VUE); });
    await monter(call);
    await saisir(tid("sa-conso-mois")!, "");
    await attendre();
    expect(chemins.every((c) => /mois=\d{4}-\d{2}$/.test(c) || c.includes("/consommation/"))).toBe(true);
  });

  it("une erreur du serveur est rapportée, et le tableau ne ment pas en affichant des zéros", async () => {
    const erreurs: string[] = [];
    await act(async () => {
      racine.render(<ConsommationAvis apiFetch={vi.fn(async () => reponse({ errors: ["mois attendu au format AAAA-MM"] }, false, 400))}
                                      setError={(s) => erreurs.push(s)} setSuccess={() => {}} />);
      await new Promise((r) => setTimeout(r, 5));
    });
    expect(erreurs).toContain("mois attendu au format AAAA-MM");
    expect(tid("sa-conso-resume")!.textContent).toContain("chargement");
  });
});
