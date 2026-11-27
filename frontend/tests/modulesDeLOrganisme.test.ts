/**
 * Les modules de l'établissement : une panne passagère (503, 429, coupure) est réessayée et n'efface jamais le menu ;
 * seul un refus définitif vaut « aucun module ».
 */
import { describe, expect, it } from "vitest";
import { DELAIS_MS, lireModules } from "../src/modulesDeLOrganisme";

const rep = (status: number, corps: unknown = {}) =>
  ({ ok: status >= 200 && status < 300, status, json: () => Promise.resolve(corps) }) as unknown as Response;

function serveur(reponses: Array<Response | Error>) {
  const appels: string[] = [];
  const attentes: number[] = [];
  const apiFetch = (url: string) => {
    appels.push(url);
    const r = reponses.shift()!;
    return r instanceof Error ? Promise.reject(r) : Promise.resolve(r);
  };
  return { apiFetch, appels, attentes, attendre: (ms: number) => { attentes.push(ms); return Promise.resolve(); } };
}

describe("lireModules", () => {
  it("une limitation (503) puis la réponse : les modules arrivent, après une attente", async () => {
    const s = serveur([rep(503), rep(429), rep(200, { modules: ["alertes"], vuesMasquees: ["rapports"] })]);
    expect(await lireModules(s.apiFetch, s.attendre)).toEqual({ modules: ["alertes"], vuesMasquees: ["rapports"] });
    expect(s.attentes).toEqual([DELAIS_MS[0], DELAIS_MS[1]]);
  });

  it("une coupure réseau est une panne passagère, pas un refus", async () => {
    const s = serveur([new Error("réseau"), rep(200, { modules: ["effectifs"] })]);
    expect(await lireModules(s.apiFetch, s.attendre)).toEqual({ modules: ["effectifs"], vuesMasquees: [] });
  });

  it("une panne qui dure rend null : l'appelant garde ce qu'il savait", async () => {
    const s = serveur([rep(503), rep(503), rep(502), rep(500)]);
    expect(await lireModules(s.apiFetch, s.attendre)).toBeNull();
    expect(s.appels).toHaveLength(DELAIS_MS.length + 1);
  });

  it("un refus définitif (403) ou un compte sans établissement : aucun module, sans nouvel essai", async () => {
    const s = serveur([rep(403)]);
    expect(await lireModules(s.apiFetch, s.attendre)).toEqual({ modules: [], vuesMasquees: [] });
    expect(s.appels).toHaveLength(1);
    const global = serveur([rep(200, { modules: null })]);
    expect(await lireModules(global.apiFetch, global.attendre)).toEqual({ modules: [], vuesMasquees: [] });
  });
});
