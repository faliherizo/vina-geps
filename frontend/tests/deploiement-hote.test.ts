/**
 * Ce que l'hôte désigne, lu avec patience : une réponse muette (502, 503, réseau) n'est pas le mode sélecteur. La page
 * de connexion de Clincom retombait sur une liste d'organismes vide pendant un redéploiement (23 novembre 2026).
 */
import { describe, expect, it } from "vitest";
import { lireDeploiement } from "../src/deploiementHote";

const json = (ok: boolean, corps: unknown) => ({ ok, json: async () => corps });
const html502 = { ok: false, json: async () => { throw new SyntaxError("<html>502 Bad Gateway"); } };
const sansAttente = async () => {};

describe("lecture de ce que l'hôte désigne", () => {
  it("réessaie après une réponse muette, puis rend l'organisme verrouillé", async () => {
    const reponses = [html502, json(false, { errors: ["trop de requêtes"] }), json(true, { resolution: "sous_domaine", etablissement: { id: "e7", nom: "Clincom", slug: "clincom" } })];
    const attentes: number[] = [];
    const l = await lireDeploiement(async () => reponses.shift()!, [10, 20, 40], async (ms) => { attentes.push(ms); });
    expect(l).toEqual({ etat: "lu", ok: true, corps: { resolution: "sous_domaine", etablissement: { id: "e7", nom: "Clincom", slug: "clincom" } } });
    expect(attentes).toEqual([10, 20]);
  });

  it("une réponse qui nomme un refus est une réponse : l'hôte inconnu ne se réessaie pas", async () => {
    let appels = 0;
    const l = await lireDeploiement(async () => { appels++; return json(false, { resolution: "sous_domaine", etablissement: null }); }, [10], sansAttente);
    expect(l).toEqual({ etat: "lu", ok: false, corps: { resolution: "sous_domaine", etablissement: null } });
    expect(appels).toBe(1);
  });

  it("toujours muet : indisponible, jamais le mode sélecteur deviné", async () => {
    let appels = 0;
    const l = await lireDeploiement(async () => { appels++; throw new TypeError("réseau coupé"); }, [1, 2], sansAttente);
    expect(l).toEqual({ etat: "indisponible" });
    expect(appels).toBe(3);
  });

  it("le mode sélecteur ne vient que du serveur", async () => {
    expect(await lireDeploiement(async () => json(true, { resolution: "selecteur" }), [], sansAttente))
      .toEqual({ etat: "lu", ok: true, corps: { resolution: "selecteur" } });
  });

  it("attend vraiment entre deux essais quand rien n'est injecté", async () => {
    let appels = 0;
    const debut = Date.now();
    const l = await lireDeploiement(async () => { appels++; return html502; }, [5]);
    expect(l).toEqual({ etat: "indisponible" });
    expect(appels).toBe(2);
    expect(Date.now() - debut).toBeGreaterThanOrEqual(4);
  });
});
