/**
 * **Sous-menus visibles par établissement** (superadmin) vu de l'interface.
 *
 * Ce qui compte n'est pas que la liste s'affiche, mais qu'elle ne **mente pas sur ce qu'elle
 * fait** : c'est un dégarnissage de menu, pas une permission. L'écran doit le dire, la case
 * cochée doit signifier « visible » (l'état par défaut de tout le monde), et rien ne doit partir
 * au serveur avant le clic.
 */
import { act } from "react";
import { createRoot, type Root } from "react-dom/client";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import SousMenusManager from "../src/SousMenusManager";
import { I18nProvider } from "../src/i18n";

(globalThis as unknown as { IS_REACT_ACT_ENVIRONMENT: boolean }).IS_REACT_ACT_ENVIRONMENT = true;
const reponse = (corps: unknown, ok = true, status = ok ? 200 : 400) =>
  ({ ok, status, json: () => Promise.resolve(corps) }) as unknown as Response;

const SECTIONS = [
  { cle: "hr", libelle: "Paie & RH", module: null },
  { cle: "mifi", libelle: "Enseignement / CONFORMITÉ", module: "mifi" },
];
const VUES = [
  { cle: "orgchart", section: "hr", notion: "personnel", page: "orgchart", onglet: null, i18nKey: null, module: null },
  { cle: "scolaire:reprise", section: "hr", notion: "etablissement", page: "scolaire", onglet: "reprise", i18nKey: "vue.etablissement.reprise", module: "scolaire_reprise" },
  { cle: "complaints", section: "mifi", notion: null, page: "complaints", onglet: null, i18nKey: null, module: null },
];
const TENANTS = [{ id: "chene", name: "CSS des Chênes", modules: ["personnel"] }];

describe("Sous-menus visibles — superadmin", () => {
  let conteneur: HTMLDivElement;
  let racine: Root;
  beforeEach(() => { conteneur = document.createElement("div"); document.body.appendChild(conteneur); racine = createRoot(conteneur); });
  afterEach(() => { act(() => racine.unmount()); conteneur.remove(); });

  const tid = (id: string) => conteneur.querySelector(`[data-testid="${id}"]`) as HTMLElement | null;
  const cliquer = (id: string) => act(async () => { tid(id)!.click(); await new Promise((r) => setTimeout(r, 5)); });
  /**
   * Cocher/décocher par un **vrai clic**. Poser `checked` à la main puis émettre `change` ne
   * suffit pas : le clic qui suit rebascule la case, et l'état de React reste celui d'avant —
   * l'essai passait alors à côté de ce qu'il croyait mesurer.
   */
  const cocher = (id: string, valeur: boolean) => act(async () => {
    const el = tid(id) as HTMLInputElement;
    if (el.checked !== valeur) el.click();
    await new Promise((r) => setTimeout(r, 5));
  });

  const monter = async (call: any, setSuccess: (s: string) => void = () => {}, setError: (s: string) => void = () => {}) => {
    await act(async () => {
      racine.render(<I18nProvider><SousMenusManager tenants={TENANTS} apiFetch={call} setError={setError} setSuccess={setSuccess} /></I18nProvider>);
      await new Promise((r) => setTimeout(r, 10));
    });
  };
  const harnais = (masquees: string[] = []) => vi.fn(async (chemin: string) =>
    reponse(chemin === "/api/saas/vues" ? { sections: SECTIONS, vues: VUES } : { masquees }));

  it("**l'écran dit qu'il ne s'agit pas d'une permission** — sinon on finirait par s'y fier", async () => {
    await monter(harnais());
    const texte = conteneur.textContent ?? "";
    expect(texte).toContain("dégarnit le menu");
    expect(texte).toMatch(/pas une permission/i);
    // Et il indique où se ferme réellement un accès.
    expect(texte).toContain("coupez le module");
  });

  it("une case cochée signifie **visible** : l'état par défaut, sans rien avoir coché", async () => {
    await monter(harnais());
    for (const cle of ["orgchart", "scolaire:reprise", "complaints"]) {
      expect((tid(`sousmenus-vue-${cle}`) as HTMLInputElement).checked, cle).toBe(true);
    }
    expect(tid("sousmenus-resume")!.textContent).toContain("3 entrée(s) visible(s) sur 3");
  });

  it("une entrée déjà masquée au serveur revient décochée", async () => {
    await monter(harnais(["orgchart"]));
    expect((tid("sousmenus-vue-orgchart") as HTMLInputElement).checked).toBe(false);
    expect((tid("sousmenus-vue-complaints") as HTMLInputElement).checked).toBe(true);
    expect(tid("sousmenus-resume")!.textContent).toContain("1 masquée(s)");
  });

  it("rien ne part **avant le clic**, et le bouton reste inerte tant que rien n'a changé", async () => {
    const appels: Array<{ chemin: string; methode: string; corps?: any }> = [];
    const call = vi.fn(async (chemin: string, init?: RequestInit) => {
      appels.push({ chemin, methode: init?.method ?? "GET", corps: init?.body ? JSON.parse(String(init.body)) : undefined });
      // Le serveur ne masque rien au départ : décocher est alors un vrai changement. Partir d'une
      // entrée déjà masquée rendait le clic sans effet, et l'essai mesurait autre chose que son titre.
      return reponse(chemin === "/api/saas/vues" ? { sections: SECTIONS, vues: VUES }
        : { masquees: init?.method === "PUT" ? ["orgchart"] : [] });
    });
    await monter(call);

    expect((tid("sousmenus-save") as HTMLButtonElement).disabled).toBe(true);
    await cocher("sousmenus-vue-orgchart", false);
    expect((tid("sousmenus-save") as HTMLButtonElement).disabled).toBe(false);
    expect(appels.filter((a) => a.methode === "PUT")).toHaveLength(0);

    await cliquer("sousmenus-save");
    const put = appels.find((a) => a.methode === "PUT")!;
    expect(put.chemin).toBe("/api/saas/tenants/chene/vues");
    // L'état **complet** est envoyé : le serveur remplace, il ne fusionne pas.
    expect(put.corps).toEqual({ masquees: ["orgchart"] });
  });

  it("« Tout afficher » vide la sélection, et se désactive quand il n'y a rien à afficher", async () => {
    await monter(harnais(["orgchart"]));
    expect((tid("sousmenus-tout") as HTMLButtonElement).disabled).toBe(false);
    await cliquer("sousmenus-tout");
    expect((tid("sousmenus-vue-orgchart") as HTMLInputElement).checked).toBe(true);
    expect((tid("sousmenus-tout") as HTMLButtonElement).disabled).toBe(true);
  });

  it("**signale un module coupé par un mot**, jamais par la seule couleur (WCAG 1.4.1)", async () => {
    // « scolaire:reprise » dépend d'un module que ce tenant n'a pas : masquer l'entrée ne
    // servirait à rien, elle est déjà absente. Le dire évite un réglage inutile.
    await monter(harnais());
    const ligne = tid("sousmenus-vue-scolaire:reprise")!.closest("label")!;
    expect(ligne.textContent).toContain("module « scolaire_reprise » désactivé");
    expect(tid("sousmenus-section-mifi")!.textContent).toContain("module « mifi » désactivé");
  });

  it("le filtre restreint la liste, et le dit quand il ne retient rien", async () => {
    await monter(harnais());
    const champ = tid("sousmenus-filtre") as HTMLInputElement;
    await act(async () => {
      Object.getOwnPropertyDescriptor(HTMLInputElement.prototype, "value")!.set!.call(champ, "reprise");
      champ.dispatchEvent(new Event("input", { bubbles: true }));
    });
    expect(tid("sousmenus-vue-scolaire:reprise")).not.toBeNull();
    expect(tid("sousmenus-vue-complaints")).toBeNull();

    await act(async () => {
      Object.getOwnPropertyDescriptor(HTMLInputElement.prototype, "value")!.set!.call(champ, "zzz");
      champ.dispatchEvent(new Event("input", { bubbles: true }));
    });
    expect(tid("sousmenus-vide")).not.toBeNull();
  });

  it("chaque case porte un **libellé lié**, et le résumé s'annonce", async () => {
    await monter(harnais());
    for (const cle of ["orgchart", "scolaire:reprise"]) {
      const champ = tid(`sousmenus-vue-${cle}`)!;
      expect(conteneur.querySelector(`label[for="${champ.id}"]`), cle).not.toBeNull();
    }
    expect(conteneur.querySelector('label[for="sousmenus-tenant"]')).not.toBeNull();
    expect(tid("sousmenus-resume")!.getAttribute("role")).toBe("status");
  });

  it("une erreur du serveur est rapportée telle qu'elle est nommée", async () => {
    const erreurs: string[] = [];
    const call = vi.fn(async (chemin: string, init?: RequestInit) =>
      init?.method === "PUT"
        ? reponse({ errors: ["vues inconnues : inventé"] }, false, 400)
        : reponse(chemin === "/api/saas/vues" ? { sections: SECTIONS, vues: VUES } : { masquees: [] }));
    await monter(call, () => {}, (s) => erreurs.push(s));
    await cocher("sousmenus-vue-orgchart", false);
    await cliquer("sousmenus-save");
    expect(erreurs).toContain("vues inconnues : inventé");
  });
});
