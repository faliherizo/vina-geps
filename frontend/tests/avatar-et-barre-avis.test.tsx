/**
 * **Les visages et « ce qui m'attend »** (maquette P11).
 *
 * Deux pièces, un même principe : ce qui est montré doit être **dit**. Une pastille colorée sans
 * chiffre se devine ; un avatar sans initiales se confond avec tous les autres ; un compteur qui
 * change sans rien annoncer n'existe pas pour qui n'a pas les yeux sur la barre.
 */
import { act } from "react";
import { createRoot, type Root } from "react-dom/client";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import Avatar, { initialesDe, teinteDe } from "../src/composants/Avatar";
import BarreAvis from "../src/BarreAvis";
import { filEnAttente, oublierFil } from "../src/filDemande";

(globalThis as unknown as { IS_REACT_ACT_ENVIRONMENT: boolean }).IS_REACT_ACT_ENVIRONMENT = true;

/** Le contexte d'authentification, remplacé par un porteur mutable et **stable**. */
const auth = vi.hoisted(() => {
  const porteur: { appel: (url: string) => Promise<Response> } = {
    appel: () => Promise.reject(new Error("aucun appel branché")),
  };
  const stable = {
    apiFetch: (url: string) => porteur.appel(url),
    user: { id: "u1", username: "marie.tremblay", role: "employee" },
  };
  return { porteur, useAuth: () => stable };
});
vi.mock("../src/AuthContext", () => ({ useAuth: auth.useAuth }));

const reponse = (corps: unknown) =>
  ({ ok: true, status: 200, json: () => Promise.resolve(corps) }) as unknown as Response;

describe("Avatar — un visage, ou deux lettres", () => {
  it("les initiales disent de qui il s'agit, même sans photo", () => {
    expect(initialesDe("Marie Tremblay")).toBe("MT");
    // Un seul mot donne **deux** lettres : « A » se confondrait avec tous les prénoms en A.
    expect(initialesDe("Ana")).toBe("AN");
    expect(initialesDe("faliherizo.randriamirindra")).toBe("FR");
    expect(initialesDe("prenom_nom")).toBe("PN");
    // Un nom vide ne doit pas produire une pastille vide, qui se lit comme un chargement inachevé.
    expect(initialesDe("")).toBe("?");
    expect(initialesDe(null)).toBe("?");
  });

  it("la teinte est **stable** pour un nom : la même personne garde sa pastille", () => {
    expect(teinteDe("Marie Tremblay")).toBe(teinteDe("Marie Tremblay"));
    // Et elle appartient à la palette mesurée : aucune teinte improvisée.
    const palette = ["#0a66b5", "#0b6e69", "#6b3fa0", "#a3366b", "#8a5200", "#2f6f3e"];
    for (const nom of ["Ana", "Bob", "Cléo", "Dimitri", "Éloïse", "Farid", "Gaby"]) {
      expect(palette).toContain(teinteDe(nom));
    }
  });

  let conteneur: HTMLDivElement;
  let racine: Root;
  beforeEach(() => {
    conteneur = document.createElement("div"); document.body.appendChild(conteneur);
    racine = createRoot(conteneur);
  });
  afterEach(() => { act(() => racine.unmount()); conteneur.remove(); });

  it("sans compte, les initiales ; avec compte, la photo du serveur", async () => {
    await act(async () => { racine.render(<Avatar nom="Marie Tremblay" testId="a1" />); });
    expect(conteneur.querySelector('[data-testid="a1"]')!.textContent).toBe("MT");

    await act(async () => { racine.render(<Avatar personId="u2" nom="Marie Tremblay" testId="a1" />); });
    const img = conteneur.querySelector('[data-testid="a1"]') as HTMLImageElement;
    expect(img.tagName).toBe("IMG");
    expect(img.getAttribute("src")).toBe("/api/auth/personnes/u2/photo");
  });

  it("une photo absente (404) **retombe sur les initiales**, sans silhouette grise", async () => {
    await act(async () => { racine.render(<Avatar personId="u2" nom="Ana Côté" testId="a2" />); });
    const img = conteneur.querySelector('[data-testid="a2"]') as HTMLImageElement;
    await act(async () => { img.dispatchEvent(new Event("error")); });
    expect(conteneur.querySelector('[data-testid="a2"]')!.textContent).toBe("AC");
  });

  it("décoratif à côté d'un nom, annoncé quand il est **seul**", async () => {
    await act(async () => { racine.render(<Avatar nom="Ana Côté" testId="a3" />); });
    expect(conteneur.querySelector('[data-testid="a3"]')!.getAttribute("aria-hidden")).toBe("true");

    await act(async () => { racine.render(<Avatar nom="Ana Côté" seul testId="a3" />); });
    const seul = conteneur.querySelector('[data-testid="a3"]')!;
    expect(seul.getAttribute("aria-hidden")).toBeNull();
    expect(seul.getAttribute("aria-label")).toBe("Ana Côté");
  });
});

describe("Barre d'avis — ce qui m'attend, dans la barre du haut", () => {
  let conteneur: HTMLDivElement;
  let racine: Root;
  beforeEach(() => {
    conteneur = document.createElement("div"); document.body.appendChild(conteneur);
    racine = createRoot(conteneur);
  });
  afterEach(() => { act(() => racine.unmount()); conteneur.remove(); });

  const brancher = (nonLus: unknown, annonces: unknown) => {
    auth.porteur.appel = (url: string) => Promise.resolve(reponse(
      url.includes("non-lus") ? nonLus : annonces));
  };

  const rendre = async (avecMessagerie = true) => {
    await act(async () => {
      racine.render(<BarreAvis avecMessagerie={avecMessagerie} avecAvis aller={() => {}} jeton={1} />);
    });
    await act(async () => { await Promise.resolve(); });
  };

  it("le compte est **écrit en chiffres** et annoncé poliment", async () => {
    brancher({ total: 3, conversations: [{ conversationId: "c1", sujet: "Transfert", nonLus: 3, dernierExtrait: "Civière 4" }] },
             [{ id: "a1", titre: "Entretien samedi", lue: false }]);
    await rendre();

    const messages = conteneur.querySelector('[data-testid="avis-messages-compte"]')!;
    expect(messages.textContent).toBe("3");
    // Une pastille muette se devine ; un compte annoncé se sait (WCAG 4.1.3).
    expect(messages.getAttribute("role")).toBe("status");
    expect(conteneur.querySelector('[data-testid="avis-annonces-compte"]')!.textContent).toBe("1");
  });

  it("rien à lire : **aucune pastille**, et le bouton le dit", async () => {
    brancher({ total: 0, conversations: [] }, []);
    await rendre();
    expect(conteneur.querySelector('[data-testid="avis-messages-compte"]')).toBeNull();
    expect(conteneur.querySelector('[data-testid="avis-messages-bouton"]')!.getAttribute("aria-label"))
      .toMatch(/aucun message non lu/);
  });

  it("sans le module de communication, **pas de compteur de messagerie**", async () => {
    brancher({ total: 9, conversations: [] }, []);
    await rendre(false);
    expect(conteneur.querySelector('[data-testid="avis-messages"]')).toBeNull();
    // Les avis de l'organisme, eux, ne dépendent d'aucun module sectoriel.
    expect(conteneur.querySelector('[data-testid="avis-annonces"]')).not.toBeNull();
  });

  it("le panneau s'ouvre, et **Échap le referme** — il ne piège pas le focus", async () => {
    brancher({ total: 2, conversations: [{ conversationId: "c1", sujet: "Transfert", nonLus: 2, dernierExtrait: "Civière 4" }] }, []);
    await rendre();

    const bouton = conteneur.querySelector('[data-testid="avis-messages-bouton"]') as HTMLElement;
    await act(async () => { bouton.click(); });
    expect((conteneur.querySelector('[data-testid="avis-messages"]') as HTMLDetailsElement).open).toBe(true);
    expect(conteneur.querySelector('[data-testid="avis-message-c1"]')).not.toBeNull();

    await act(async () => { document.dispatchEvent(new KeyboardEvent("keydown", { key: "Escape" })); });
    expect((conteneur.querySelector('[data-testid="avis-messages"]') as HTMLDetailsElement).open).toBe(false);
  });
  it("un message non lu mène à **la messagerie du personnel**, son fil demandé, panneau refermé", async () => {
    oublierFil();
    const vus: string[] = [];
    brancher({ total: 1, conversations: [{ conversationId: "c1", sujet: "Transfert", nonLus: 1, dernierExtrait: "Civière 4" }] }, []);
    await act(async () => { racine.render(<BarreAvis avecMessagerie avecAvis={false} aller={(p) => vus.push(p)} jeton={1} />); });
    await act(async () => { (conteneur.querySelector('[data-testid="avis-messages-bouton"]') as HTMLElement).click(); });
    await act(async () => { (conteneur.querySelector('[data-testid="avis-message-c1"]') as HTMLElement).click(); });
    expect(vus).toEqual(["usmessages"]);
    expect(filEnAttente()).toBe("c1");
    expect((conteneur.querySelector('[data-testid="avis-messages"]') as HTMLDetailsElement).open).toBe(false);
    oublierFil();
  });

  it("un avis ouvert est **marqué lu**, puis l'écran des avis s'ouvre", async () => {
    const appels: string[] = [];
    const vus: string[] = [];
    auth.porteur.appel = (url: string) => {
      appels.push(url);
      return Promise.resolve(reponse(url.includes("non-lus")
        ? { total: 0, conversations: [] }
        : [{ id: "a1", titre: "Entretien samedi", corps: "De 2 h à 5 h.", lue: false }]));
    };
    await act(async () => {
      racine.render(<BarreAvis avecMessagerie={false} avecAvis aller={(p) => vus.push(p)} jeton={1} />);
    });
    await act(async () => { await Promise.resolve(); });

    await act(async () => { (conteneur.querySelector('[data-testid="avis-annonces-bouton"]') as HTMLElement).click(); });
    await act(async () => { (conteneur.querySelector('[data-testid="avis-annonce-a1"]') as HTMLElement).click(); });
    await act(async () => { await Promise.resolve(); });

    // Lu **ici** : laisser l'avis non lu ferait sonner la barre pour ce qu'on vient d'ouvrir.
    expect(appels.some((u) => u.endsWith("/api/notifications/annonces/a1/lue"))).toBe(true);
    expect(vus).toEqual(["notifications"]);
  });

  it("un clic **ailleurs** referme le panneau : il ne recouvre pas la page", async () => {
    brancher({ total: 1, conversations: [{ conversationId: "c1", sujet: "Transfert", nonLus: 1, dernierExtrait: "x" }] }, []);
    await rendre();
    const bouton = conteneur.querySelector('[data-testid="avis-messages-bouton"]') as HTMLElement;
    await act(async () => { bouton.click(); });
    expect((conteneur.querySelector('[data-testid="avis-messages"]') as HTMLDetailsElement).open).toBe(true);

    await act(async () => { document.dispatchEvent(new MouseEvent("mousedown", { bubbles: true })); });
    expect((conteneur.querySelector('[data-testid="avis-messages"]') as HTMLDetailsElement).open).toBe(false);
  });

  it("au-delà de quatre-vingt-dix-neuf, le compte se dit « 99+ »", async () => {
    brancher({ total: 0, conversations: Array.from({ length: 120 }, (_, i) => ({ conversationId: `c${i}`, sujet: "f", nonLus: 1, dernierExtrait: "" })) }, []);
    await rendre();
    // Au-delà, le chiffre exact n'aide plus à décider : il dit seulement « beaucoup ».
    expect(conteneur.querySelector('[data-testid="avis-messages-compte"]')!.textContent).toBe("99+");
  });

  it("sans module de messagerie **ni** d'avis, la barre ne s'affiche pas du tout", async () => {
    brancher({ total: 5, conversations: [] }, [{ id: "a1", titre: "x", lue: false }]);
    await act(async () => {
      racine.render(<BarreAvis avecMessagerie={false} avecAvis={false} aller={() => {}} jeton={1} />);
    });
    await act(async () => { await Promise.resolve(); });
    // Une barre vide occupe la place d'une barre utile, et se clique par erreur.
    expect(conteneur.querySelector('[data-testid="barre-avis"]')).toBeNull();
  });

});
