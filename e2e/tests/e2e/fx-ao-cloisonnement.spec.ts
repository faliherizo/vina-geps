/**
 * **FX-AO-20260810 — ce que le serveur accorde à l'organisme, et ce qu'il refuse.**
 *
 * `fx-ao-menu.spec.ts` prouve que le menu ne montre que l'avis. Ce n'est pas une permission : une vue masquée reste une
 * route. Ici, l'organisme réellement semé (`scripts/semer-fx-ao.cjs`) est interrogé par l'API, profil par profil :
 * - ses modules sont exactement ceux que justifie la grille (critère ou dépendance technique) ;
 * - chaque fonctionnalité hors de l'avis répond 403 à tous ses profils, l'administrateur compris ;
 * - chaque profil atteint ses écrans de travail et pas ceux d'un autre profil ;
 * - le jeton fait autorité : un en-tête X-Tenant-Id vers un autre organisme est refusé.
 *
 * Données : `e2e/donnees/fx-ao/cloisonnement.json`. Lecture seule : aucun appel n'écrit.
 */
import { execFileSync } from "node:child_process";
import path from "node:path";
import { test, expect, request, type APIRequestContext } from "@playwright/test";
import donnees from "../../donnees/fx-ao/cloisonnement.json";

const API = `http://localhost:${process.env.E2E_API_PORT ?? "4000"}`;
const SCRIPT = path.join(__dirname, "..", "..", "..", "scripts", "semer-fx-ao.cjs");
const MDP = "Passe!Demo2026";
const MOIS = new Date().toISOString().slice(0, 7);
const rendre = (chemin: string) => chemin.replace("{{mois}}", MOIS);

test.use({ storageState: { cookies: [], origins: [] } });

async function jeton(tenantId: string, username: string): Promise<string> {
  const ctx = await request.newContext({ baseURL: API });
  const r = await ctx.post("/api/auth/login", { data: { username, password: MDP, tenantId } });
  expect(r.ok(), `connexion de ${username} : ${r.status()}`).toBeTruthy();
  const { token } = await r.json();
  await ctx.dispose();
  return token;
}

async function statut(api: APIRequestContext, chemin: string, entetes: Record<string, string>): Promise<number> {
  return (await api.get(rendre(chemin), { headers: entetes })).status();
}

test.describe("FX-AO-20260810 — le cloisonnement, vérifié par le serveur", () => {
  let tenantId = "";
  let api: APIRequestContext;

  test.beforeAll(async () => {
    const sortie = execFileSync("node", [SCRIPT, "--api", API, "--mdp", MDP], { encoding: "utf-8", timeout: 240_000 });
    tenantId = (sortie.match(/^TENANT=(.+)$/m) ?? [])[1] ?? "";
    expect(tenantId, `le script n'a pas nommé son organisme :\n${sortie}`).toBeTruthy();
    api = await request.newContext({ baseURL: API });
  });

  test.afterAll(async () => { await api?.dispose(); });

  test("les modules de l'organisme sont exactement ceux que la grille justifie", async () => {
    const j = await jeton(tenantId, "admin");
    const r = await api.get("/api/saas/my-modules", { headers: { Authorization: `Bearer ${j}` } });
    const modules: string[] = (await r.json()).modules;
    expect([...modules].sort()).toEqual([...donnees.modules.attendus].sort());
    for (const m of donnees.modules.attendus) expect(donnees.modules.justification, `justification du module ${m}`).toHaveProperty(m);
    expect(modules.filter((m) => donnees.modules.jamais.includes(m))).toEqual([]);
  });

  for (const profil of Object.keys(donnees.profils) as (keyof typeof donnees.profils)[]) {
    test(`${profil} : ses écrans de travail, rien de plus — et rien hors de l'avis`, async () => {
      const entetes = { Authorization: `Bearer ${await jeton(tenantId, profil)}` };
      const ecarts: string[] = [];
      for (const chemin of donnees.profils[profil].permis) {
        const s = await statut(api, chemin, entetes);
        if (s !== 200) ecarts.push(`permis ${chemin} → ${s}`);
      }
      for (const chemin of donnees.profils[profil].refuses) {
        const s = await statut(api, chemin, entetes);
        if (s !== 403) ecarts.push(`refusé (rôle) ${chemin} → ${s}`);
      }
      for (const r of donnees.refusesATous) {
        const s = await statut(api, r.chemin, entetes);
        if (s !== 403) ecarts.push(`hors de l'avis ${r.chemin} → ${s} (${r.pourquoi})`);
      }
      expect(ecarts).toEqual([]);
    });
  }

  test("le jeton fait autorité : un X-Tenant-Id vers un autre organisme est refusé, ou ignoré — jamais suivi", async () => {
    const entetes = { Authorization: `Bearer ${await jeton(tenantId, "admin")}`, "X-Tenant-Id": donnees.autreOrganisme };
    // Les gardes de contexte refusent la discordance (403) ; l'administration des comptes l'ignore et sert l'organisme du
    // jeton (ControleurAuth.tenantDuDemandeur). Les deux sont sûrs : ce qui ne l'est pas, c'est de suivre l'en-tête.
    expect(await statut(api, "/api/alertes/envoyees", entetes)).toBe(403);
    const comptes = await api.get("/api/auth/users", { headers: entetes });
    if (comptes.status() !== 403) {
      expect(comptes.status()).toBe(200);
      const liste: { tenantId?: string }[] = await comptes.json();
      expect(liste.length).toBeGreaterThan(0);
      expect(liste.filter((u) => u.tenantId !== tenantId), "comptes d'un autre organisme").toEqual([]);
    }
  });
});
