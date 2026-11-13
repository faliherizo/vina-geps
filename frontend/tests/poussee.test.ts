import { describe, it, expect, vi } from "vitest";
import { activer, cleEnOctets, desactiver, etatActuel, EnvironnementPoussee } from "../src/poussee";

/** Un navigateur factice : permission réglable, abonnement en mémoire. */
function navigateur(permission: NotificationPermission = "granted", abonne = false, supporte = true) {
  let abonnement: any = abonne ? { endpoint: "https://p/1", toJSON: () => ({ endpoint: "https://p/1", keys: { p256dh: "x", auth: "y" } }), unsubscribe: vi.fn(async () => true) } : null;
  const env: EnvironnementPoussee = {
    supporte,
    permission: () => permission,
    demanderPermission: async () => permission,
    enregistrer: async () => ({
      abonnementActuel: async () => abonnement,
      abonner: async () => { abonnement = { endpoint: "https://p/2", toJSON: () => ({ endpoint: "https://p/2", keys: { p256dh: "x", auth: "y" } }), unsubscribe: vi.fn(async () => true) }; return abonnement; },
    }),
  };
  return { env, lire: () => abonnement };
}

/** Les réponses sont des fabriques : une `Response` ne se lit qu'une fois. */
const client = (reponses: Record<string, () => Response>) => ({ call: async (chemin: string, init?: RequestInit) => (reponses[`${init?.method ?? "GET"} ${chemin}`] ?? (() => new Response("{}", { status: 200 })))() });

describe("Notifications poussées — client", () => {
  it("convertit la clé VAPID base64url en octets (point P-256 de 65 octets)", () => {
    const cle = "BA" + "A".repeat(85);   // 87 caractères base64url ≈ 65 octets
    const octets = cleEnOctets(cle);
    expect(octets.length).toBe(65);
    expect(octets[0]).toBe(4);
  });

  it("lit l'état : non supporté, refusé, inactif, actif", async () => {
    expect(await etatActuel(navigateur("granted", false, false).env)).toBe("non_supporte");
    expect(await etatActuel(navigateur("denied").env)).toBe("refuse");
    expect(await etatActuel(navigateur("granted", false).env)).toBe("inactif");
    expect(await etatActuel(navigateur("granted", true).env)).toBe("actif");
    const casse: EnvironnementPoussee = { ...navigateur().env, enregistrer: async () => { throw new Error("sw"); } };
    expect(await etatActuel(casse)).toBe("inactif");
  });

  it("**active** : permission, clé du serveur, abonnement, enregistrement — et nomme chaque refus", async () => {
    Object.defineProperty(globalThis, "navigator", { value: { userAgent: "Essai/1.0" }, configurable: true });
    const ok = client({ "GET /api/notifications/push/cle-publique": () => new Response(JSON.stringify({ cle: "BA" + "A".repeat(85) })), "POST /api/notifications/push/abonnements": () => new Response("{}", { status: 201 }) });
    const n = navigateur();
    expect(await activer(n.env, ok)).toEqual({ etat: "actif", erreur: null });
    expect(n.lire().endpoint).toBe("https://p/2");
    expect((await activer(navigateur("granted", false, false).env, ok)).etat).toBe("non_supporte");
    expect((await activer(navigateur("denied").env, ok)).erreur).toMatch(/Permission refusée/);
    const sansCle = client({ "GET /api/notifications/push/cle-publique": () => new Response("{}", { status: 500 }) });
    expect((await activer(navigateur().env, sansCle)).erreur).toMatch(/Clé du serveur/);
    const refus = client({ "GET /api/notifications/push/cle-publique": () => new Response(JSON.stringify({ cle: "BA" + "A".repeat(85) })), "POST /api/notifications/push/abonnements": () => new Response(JSON.stringify({ errors: ["endpoint : URL https attendue"] }), { status: 400 }) });
    expect((await activer(navigateur().env, refus)).erreur).toBe("endpoint : URL https attendue");
    // Déjà abonné : on réutilise l'abonnement du navigateur sans en prendre un second.
    const deja = navigateur("granted", true);
    expect((await activer(deja.env, ok)).etat).toBe("actif");
    expect(deja.lire().endpoint).toBe("https://p/1");
  });

  it("**désactive** : retire côté serveur puis côté navigateur ; sans abonnement, rien à faire", async () => {
    const appels: string[] = [];
    const c = { call: async (chemin: string, init?: RequestInit) => { appels.push(`${init?.method} ${chemin}`); return new Response("{}"); } };
    const n = navigateur("granted", true);
    const abonnement = n.lire();
    expect(await desactiver(n.env, c)).toBe("inactif");
    expect(appels).toEqual(["DELETE /api/notifications/push/abonnements"]);
    expect(abonnement.unsubscribe).toHaveBeenCalled();
    expect(await desactiver(navigateur("granted", false).env, c)).toBe("inactif");
    expect(await desactiver(navigateur("granted", false, false).env, c)).toBe("non_supporte");
  });
});
