/**
 * **Les décisions de l'avis 26-015-DO, jouées par l'API — et jugées sur ce qu'elles rendent.**
 *
 * Les essais d'écran prouvent qu'une commande existe, qu'elle est atteignable et qu'on peut y
 * écrire. Aucun ne prouve qu'une règle **décide juste** : un bouton présent n'est pas une règle
 * appliquée. Celui-ci joue `donnees/urgences-sante/comportements.json` contre le backend qui sert la
 * campagne — le Java, par `backend-java/scripts/campagne-e2e.sh` — et compare chaque réponse à un
 * attendu qui porte sur une **valeur** : un statut, un motif, un taux.
 *
 * L'essai est un interprète, et il le reste : toute la connaissance métier est dans le fichier de
 * données. Prouver une exigence de plus, c'est ajouter un scénario, pas du code.
 */
import { test, expect, request as pwRequest, type APIRequestContext } from "@playwright/test";
import { superadminContext } from "./helpers/auth";
import { createTenant, setTenantModules, uniq } from "./helpers/organisme";
import { adminDeLOrganisme, MDP_PROFIL, modulesDeLOrganisme } from "./helpers/urgences-sante";
import jeu from "../../donnees/urgences-sante/comportements.json";

test.use({ storageState: { cookies: [], origins: [] } });

const API = `http://localhost:${process.env.E2E_API_PORT ?? "4000"}`;

interface Attendu { statut: number; corps?: unknown; texte?: string }
interface Etape {
  pourquoi?: string; acteur?: string; methode: "GET" | "POST" | "PUT" | "PATCH" | "DELETE";
  chemin: string; corps?: unknown; attendu: Attendu; capturer?: Record<string, string>;
}
interface Scenario { id: string; titre: string; exigences: string[]; etapes: Etape[] }

const GABARIT_SEUL = /^\{\{([\w:-]+)\}\}$/;
const GABARIT = /\{\{([\w:-]+)\}\}/g;

/** Rend `{{nom}}` ; un gabarit seul garde le type de la valeur capturée (un nombre reste un nombre). */
export function rendre(valeur: unknown, vars: Record<string, unknown>): unknown {
  if (typeof valeur === "string") {
    const seul = GABARIT_SEUL.exec(valeur);
    if (seul) return lire(vars, seul[1]);
    return valeur.replace(GABARIT, (_, nom: string) => String(lire(vars, nom)));
  }
  if (Array.isArray(valeur)) return valeur.map((v) => rendre(v, vars));
  if (valeur && typeof valeur === "object") {
    return Object.fromEntries(Object.entries(valeur).map(([k, v]) => [k, rendre(v, vars)]));
  }
  return valeur;
}

function lire(vars: Record<string, unknown>, nom: string): unknown {
  if (!(nom in vars)) throw new Error(`gabarit « {{${nom}}} » : aucune valeur capturée sous ce nom`);
  return vars[nom];
}

/** `places.0.id` → la valeur à ce chemin ; un chemin qui ne mène nulle part est une erreur nommée. */
export function aChemin(corps: unknown, chemin: string): unknown {
  return chemin.split(".").reduce<unknown>((v, cle) => {
    if (v === null || v === undefined) throw new Error(`capture « ${chemin} » : rien sous « ${cle} »`);
    return (v as Record<string, unknown>)[cle];
  }, corps);
}

/**
 * **Sous-ensemble** : chaque clé de l'attendu vaut ce qui est écrit ; un tableau se compare élément
 * par élément sur ses premiers éléments. Rend la liste des écarts, nommés par leur chemin.
 */
export function ecartsDe(attendu: unknown, recu: unknown, chemin = "$"): string[] {
  if (Array.isArray(attendu)) {
    if (!Array.isArray(recu)) return [`${chemin} : un tableau était attendu, reçu ${JSON.stringify(recu)}`];
    return attendu.flatMap((a, i) => ecartsDe(a, recu[i], `${chemin}[${i}]`));
  }
  if (attendu && typeof attendu === "object") {
    if (!recu || typeof recu !== "object") return [`${chemin} : un objet était attendu, reçu ${JSON.stringify(recu)}`];
    return Object.entries(attendu).flatMap(([k, a]) => ecartsDe(a, (recu as Record<string, unknown>)[k], `${chemin}.${k}`));
  }
  return Object.is(attendu, recu) ? [] : [`${chemin} : attendu ${JSON.stringify(attendu)}, reçu ${JSON.stringify(recu)}`];
}

async function connecter(username: string, tenantId: string): Promise<APIRequestContext> {
  const c = await pwRequest.newContext({ storageState: { cookies: [], origins: [] } });
  const r = await c.post(`${API}/api/auth/login`, { data: { username, password: MDP_PROFIL, tenantId } });
  const { token } = await r.json();
  await c.dispose();
  expect(token, `connexion de ${username} : ${r.status()}`).toBeTruthy();
  return pwRequest.newContext({
    storageState: { cookies: [], origins: [] },
    extraHTTPHeaders: { Authorization: `Bearer ${token}`, "X-Tenant-Id": tenantId },
  });
}

test.describe("26-015-DO — les décisions des règles, jugées par l'API", () => {
  const SCENARIOS = jeu.scenarios as Scenario[];
  const vars: Record<string, unknown> = {};
  const acteurs: Record<string, APIRequestContext> = {};

  /** Joue une étape et rend les écarts constatés — vides si tout est conforme. */
  async function jouer(e: Etape): Promise<string[]> {
    const qui = acteurs[e.acteur ?? "admin"];
    if (!qui) return [`acteur « ${e.acteur} » inconnu du fichier`];
    const chemin = String(rendre(e.chemin, vars));
    const corps = e.corps === undefined ? undefined : rendre(e.corps, vars);
    const r = await qui.fetch(`${API}${chemin}`, { method: e.methode, data: corps });
    const texte = await r.text();
    let json: unknown = null;
    try { json = JSON.parse(texte); } catch { /* une réponse CSV n'est pas du JSON : l'attendu « texte » la lit telle quelle */ }
    const ou = `${e.methode} ${chemin}`;
    const ecarts: string[] = [];
    if (r.status() !== e.attendu.statut) ecarts.push(`${ou} : statut ${r.status()} au lieu de ${e.attendu.statut} — ${texte.slice(0, 300)}`);
    if (e.attendu.corps !== undefined) {
      ecarts.push(...ecartsDe(rendre(e.attendu.corps, vars), json).map((x) => `${ou} : ${x}`));
    }
    if (e.attendu.texte !== undefined && !new RegExp(e.attendu.texte, "m").test(texte)) {
      ecarts.push(`${ou} : le texte ne contient pas /${e.attendu.texte}/ — ${texte.slice(0, 300)}`);
    }
    for (const [nom, c] of Object.entries(e.capturer ?? {})) {
      try { vars[nom] = aChemin(json, c); } catch (err) { ecarts.push(`${ou} : ${(err as Error).message}`); }
    }
    return ecarts;
  }

  test.beforeAll(async () => {
    test.setTimeout(240_000);
    const sa = await superadminContext();
    let tenantId = "";
    try {
      tenantId = await createTenant(sa, uniq("US-COMP"));
      await setTenantModules(sa, tenantId, modulesDeLOrganisme);
    } finally { await sa.dispose(); }
    vars.suffixe = tenantId.slice(-6);
    acteurs.admin = await adminDeLOrganisme(tenantId);

    // Les comptes : créés par l'administration, rendus utilisables sans franchir le mur de première
    // connexion (mot de passe, second facteur) — ce mur a ses propres essais.
    for (const [cle, a] of Object.entries(jeu.acteurs as Record<string, { role: string }>)) {
      const username = `${cle}-${vars.suffixe}`;
      const c = await acteurs.admin.post(`${API}/api/auth/users`, {
        data: { username, password: MDP_PROFIL, role: a.role, mustChangePassword: false },
      });
      expect(c.ok(), `compte ${username} : ${c.status()} ${await c.text()}`).toBeTruthy();
      const id = (await c.json()).id as string;
      await acteurs.admin.patch(`${API}/api/auth/users/${id}`, { data: { securitySetupRequired: false } });
      vars[`u_${cle}`] = id;
      acteurs[cle] = await connecter(username, tenantId);
    }

    const ecarts: string[] = [];
    for (const e of jeu.amorce as Etape[]) ecarts.push(...await jouer(e));
    expect(ecarts, `l'amorce doit passer en entier :\n${ecarts.join("\n")}`).toEqual([]);
  });

  test.afterAll(async () => {
    await Promise.all(Object.values(acteurs).map((a) => a.dispose()));
  });

  // Ordonnés et solidaires : un scénario s'appuie sur les captures des précédents.
  test.describe.configure({ mode: "serial" });

  for (const s of SCENARIOS) {
    test(`${s.id} — ${s.titre} (${s.exigences.join(", ")})`, async () => {
      const ecarts: string[] = [];
      for (const e of s.etapes) {
        const ici = await jouer(e);
        ecarts.push(...ici.map((x) => (e.pourquoi ? `${x}\n    pourquoi : ${e.pourquoi}` : x)));
      }
      expect(ecarts, ecarts.join("\n")).toEqual([]);
    });
  }
});
