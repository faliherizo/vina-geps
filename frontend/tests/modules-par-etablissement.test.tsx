/**
 * **L'écran des modules par établissement doit montrer *tous* les modules d'une famille.**
 *
 * Le rangement par arbre de dépendances ne descendait que d'un cran : le socle, puis ses enfants
 * directs. Les familles existantes étaient en **étoile** — les neuf modules du RH exigent tous
 * `rh` —, donc rien ne manquait et le défaut dormait.
 *
 * Urgences-santé est une **chaîne** : socle → règles → planification → disponibilités → … Huit de
 * ses dix modules n'arrivaient jamais au tableau. Ils étaient au catalogue du serveur, accordables
 * par l'API, cités dans la documentation — et impossibles à cocher. Le superadmin concluait que
 * le module n'était pas gérable, et il avait raison de le conclure.
 *
 * Cet essai rend la profondeur mesurable : une chaîne de cinq rangs affiche cinq lignes.
 */
import { act } from "react";
import { createRoot, type Root } from "react-dom/client";
import { afterEach, beforeEach, describe, expect, it } from "vitest";
import TenantModulesManager from "../src/TenantModulesManager";

(globalThis as unknown as { IS_REACT_ACT_ENVIRONMENT: boolean }).IS_REACT_ACT_ENVIRONMENT = true;

/** Une chaîne de cinq rangs et une étoile de trois : les deux formes que le rangement doit tenir. */
const CATALOGUE = {
  familles: [{ cle: "chaine", libelle: "Chaîne" }, { cle: "etoile", libelle: "Étoile" }],
  modules: [
    { code: "c0", label: "Socle", famille: "chaine", requiert: null, dependants: ["c1", "c2", "c3", "c4"] },
    { code: "c1", label: "Rang 1", famille: "chaine", requiert: "c0", dependants: ["c2", "c3", "c4"] },
    { code: "c2", label: "Rang 2", famille: "chaine", requiert: "c1", dependants: ["c3", "c4"] },
    { code: "c3", label: "Rang 3", famille: "chaine", requiert: "c2", dependants: ["c4"] },
    { code: "c4", label: "Rang 4", famille: "chaine", requiert: "c3", dependants: ["c5", "c6", "c7", "c8", "c9"] },
    { code: "c5", label: "Rang 5", famille: "chaine", requiert: "c4", dependants: ["c6", "c7", "c8", "c9"] },
    { code: "c6", label: "Rang 6", famille: "chaine", requiert: "c5", dependants: ["c7", "c8", "c9"] },
    { code: "c7", label: "Rang 7", famille: "chaine", requiert: "c6", dependants: ["c8", "c9"] },
    { code: "c8", label: "Rang 8", famille: "chaine", requiert: "c7", dependants: ["c9"] },
    { code: "c9", label: "Rang 9", famille: "chaine", requiert: "c8", dependants: [] },
    /**
     * **Le onzième de sa famille, et son prérequis est ailleurs.**
     *
     * C'est la forme qu'a prise la famille Urgences-santé en gagnant la communication du
     * personnel, dont le prérequis est le socle des horaires cliniques. Deux pièges s'y
     * rencontrent : il n'est le descendant d'aucun socle de sa famille (il doit donc s'afficher
     * comme une racine), et il est le **onzième** — donc le premier que la pagination par dix
     * renvoie à la page suivante.
     */
    { code: "cx", label: "Hors chaîne", famille: "chaine", requiert: "ailleurs", dependants: [] },
    { code: "e0", label: "Socle étoile", famille: "etoile", requiert: null, dependants: ["e1", "e2"] },
    { code: "e1", label: "Branche 1", famille: "etoile", requiert: "e0", dependants: [] },
    { code: "e2", label: "Branche 2", famille: "etoile", requiert: "e0", dependants: [] },
  ],
};

const TENANTS = [{ id: "t1", name: "Organisme", planCode: "enterprise", modules: ["c0"] }];

/** Ce que le serveur répond au prochain enregistrement, et ce que l'écran lui a envoyé. */
const serveur: { ok: boolean; corps: unknown; envois: Array<{ chemin: string; modules: string[] }> } = {
  ok: true, corps: { modules: [], ajoutes: [], retires: [] }, envois: [],
};

/** Le `apiFetch` que l'écran reçoit : le catalogue en lecture, et le PUT observé. */
const apiFetch = async (chemin: string, init?: RequestInit): Promise<Response> => {
  if (chemin === "/api/saas/modules") return { ok: true, json: async () => CATALOGUE } as Response;
  if (init?.method === "PUT") {
    serveur.envois.push({ chemin, modules: JSON.parse(String(init.body ?? "{}")).modules ?? [] });
    return { ok: serveur.ok, json: async () => serveur.corps } as Response;
  }
  throw new Error(`appel inattendu : ${chemin}`);
};

describe("Modules par établissement — le tableau d'une famille", () => {
  let conteneur: HTMLDivElement;
  let racine: Root;
  beforeEach(() => {
    serveur.ok = true;
    serveur.corps = { modules: [], ajoutes: [], retires: [] };
    serveur.envois = [];
    conteneur = document.createElement("div"); document.body.appendChild(conteneur);
    racine = createRoot(conteneur);
  });
  afterEach(() => { act(() => racine.unmount()); conteneur.remove(); });

  const rendre = async () => {
    await act(async () => {
      racine.render(<TenantModulesManager tenants={TENANTS} apiFetch={apiFetch}
        onChanged={() => {}} setError={() => {}} setSuccess={() => {}} />);
    });
    await act(async () => { await Promise.resolve(); });
  };

  it("**une famille s'affiche entière**, même au-delà du pas de pagination", async () => {
    /**
     * Défaut mesuré le 2 novembre 2026 : la famille Urgences-santé est passée à onze modules, et
     * la pagination par dix en a renvoyé un à la page 2 — précisément celui qu'on venait
     * chercher. Rien ne tombe, rien n'est rouge : le superadmin conclut que le module n'existe
     * pas, et il a de bonnes raisons de le croire.
     */
    await rendre();
    const lignes = conteneur.querySelectorAll('[data-testid^="modmgr-row-c"]');
    expect(lignes.length).toBe(11);
    // Et le pied de pagination n'a plus lieu d'être : une page, pas deux.
    expect(conteneur.querySelector('[data-testid="pager"]')).toBeNull();
  });

  it("un module dont le prérequis est **hors de sa famille** s'affiche quand même, comme une racine", async () => {
    // Sans cela, déclarer un prérequis dans une autre famille revient à retirer le module de
    // l'écran : il reste au catalogue, accordable par l'API, et introuvable à la case qui le règle.
    await rendre();
    const ligne = conteneur.querySelector('[data-testid="modmgr-row-cx"]');
    expect(ligne).not.toBeNull();
    expect(ligne!.className).not.toContain("module-enfant");
    expect(conteneur.querySelector('[data-testid="modmgr-toggle-cx"]')).not.toBeNull();
  });

  it("**une chaîne de dépendances s'affiche en entier**, pas seulement son premier rang", async () => {
    await rendre();
    const manquants = ["c0", "c1", "c2", "c3", "c4"]
      .filter((code) => !conteneur.querySelector(`[data-testid="modmgr-row-${code}"]`));
    // Nommer les absents : « 2 lignes au lieu de 5 » n'indique pas lesquelles manquent.
    expect(manquants).toEqual([]);
  });

  it("l'indentation suit le rang, et chaque descendant l'annonce au lecteur d'écran", async () => {
    await rendre();
    // Une hiérarchie portée par la seule mise en page n'existe pas pour qui ne voit pas l'écran.
    for (const code of ["c1", "c2", "c3", "c4"]) {
      const ligne = conteneur.querySelector(`[data-testid="modmgr-row-${code}"]`)!;
      expect(ligne.className, code).toContain("module-enfant");
      expect(ligne.querySelector("th")!.textContent, code).toContain("Dépend du module précédent");
    }
    expect(conteneur.querySelector('[data-testid="modmgr-row-c0"]')!.className).not.toContain("module-enfant");
  });

  it("une famille en étoile reste entière — la forme qui masquait le défaut", async () => {
    await rendre();
    for (const code of ["e0", "e1", "e2"]) {
      expect(conteneur.querySelector(`[data-testid="modmgr-row-${code}"]`), code).not.toBeNull();
    }
  });

  it("le filtre ignore les accents : « etoile » trouve « Étoile »", async () => {
    await rendre();
    const champ = conteneur.querySelector('[data-testid="modmgr-filtre"]') as HTMLInputElement;
    // React mémorise la valeur du champ : l'affecter directement ne lui apprend rien, et le
    // filtre resterait vide — l'essai passerait alors en ne filtrant jamais.
    const poser = Object.getOwnPropertyDescriptor(HTMLInputElement.prototype, "value")!.set!;
    await act(async () => {
      poser.call(champ, "etoile");
      champ.dispatchEvent(new Event("input", { bubbles: true }));
    });
    expect(conteneur.querySelector('[data-testid="modmgr-famille-etoile"]')).not.toBeNull();
    expect(conteneur.querySelector('[data-testid="modmgr-aucun"]')).toBeNull();
    expect(conteneur.querySelector('[data-testid="modmgr-famille-chaine"]')).toBeNull();
  });
  it("cocher un module **l'envoie au serveur**, et l'écran dit ce que le serveur a corrigé", async () => {
    await rendre();
    serveur.corps = { modules: ["c0", "c1", "c2"], ajoutes: ["c1", "c2"], retires: [] };
    const toggle = conteneur.querySelector('[data-testid="modmgr-toggle-c2"]') as HTMLInputElement;
    await act(async () => { toggle.click(); });
    await act(async () => { await Promise.resolve(); });

    // La résolution des dépendances est faite au **serveur** : l'écran envoie le choix, pas l'arbre.
    expect(serveur.envois).toHaveLength(1);
    expect(serveur.envois[0].modules).toContain("c2");
    // Et il reflète ce que le serveur a renvoyé — y compris ce qu'il a ajouté de lui-même.
    expect((conteneur.querySelector('[data-testid="modmgr-toggle-c1"]') as HTMLInputElement).checked).toBe(true);
  });

  it("un refus du serveur **rend la case à son état d'avant** : rien ne se perd en silence", async () => {
    await rendre();
    serveur.ok = false;
    serveur.corps = { errors: ["module inconnu"] };
    const toggle = conteneur.querySelector('[data-testid="modmgr-toggle-c4"]') as HTMLInputElement;
    expect(toggle.checked).toBe(false);
    await act(async () => { toggle.click(); });
    await act(async () => { await Promise.resolve(); });
    expect((conteneur.querySelector('[data-testid="modmgr-toggle-c4"]') as HTMLInputElement).checked).toBe(false);
  });

  it("« tout activer » et « tout retirer » agissent sur **la famille réelle**, filtre compris", async () => {
    await rendre();
    serveur.corps = { modules: ["c0", "c1", "c2", "c3", "c4"], ajoutes: ["c1", "c2", "c3", "c4"], retires: [] };
    await act(async () => { (conteneur.querySelector('[data-testid="modmgr-famille-tout-chaine"]') as HTMLElement).click(); });
    await act(async () => { await Promise.resolve(); });
    // Les cinq rangs de la chaîne, et non les seuls enfants directs du socle.
    for (const code of ["c0", "c1", "c2", "c3", "c4"]) expect(serveur.envois[0].modules).toContain(code);

    serveur.envois = [];
    serveur.corps = { modules: [], ajoutes: [], retires: ["c0", "c1", "c2", "c3", "c4"] };
    await act(async () => { (conteneur.querySelector('[data-testid="modmgr-famille-rien-chaine"]') as HTMLElement).click(); });
    await act(async () => { await Promise.resolve(); });
    for (const code of ["c0", "c1", "c2", "c3", "c4"]) expect(serveur.envois[0].modules).not.toContain(code);
  });

  it("un filtre qui ne trouve rien **le dit**, plutôt que d'afficher une page vide", async () => {
    await rendre();
    const champ = conteneur.querySelector('[data-testid="modmgr-filtre"]') as HTMLInputElement;
    const poser = Object.getOwnPropertyDescriptor(HTMLInputElement.prototype, "value")!.set!;
    await act(async () => {
      poser.call(champ, "introuvable");
      champ.dispatchEvent(new Event("input", { bubbles: true }));
    });
    const vide = conteneur.querySelector('[data-testid="modmgr-aucun"]')!;
    expect(vide.textContent).toContain("introuvable");
    // Une absence annoncée est une information ; un écran vide est une panne supposée.
    expect(vide.getAttribute("role")).toBe("status");
  });

  it("« ce qu'il débloque » est lisible **avant** de cocher, jamais découvert après", async () => {
    await rendre();
    expect(conteneur.querySelector('[data-testid="modmgr-debloque-c0"]')).not.toBeNull();
    // La dépendance aussi : un socle qui emporte quatre modules le dit.
    expect(conteneur.querySelector('[data-testid="modmgr-requiert-c1"]')!.textContent).toContain("Socle");
  });

});
