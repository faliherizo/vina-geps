/**
 * **L'interprète des comportements par l'API** — un seul endroit pour les deux avis (26-015-DO, FX-AO-20260810).
 *
 * Un jeu de données décrit des acteurs, une amorce et des scénarios : chaque étape est un appel HTTP et un
 * attendu qui porte sur une **valeur** (statut, champ, texte). L'interprète ne sait rien du métier ; prouver
 * une exigence de plus, c'est ajouter un scénario au fichier, pas du code.
 */
import { test, expect, request as pwRequest, type APIRequestContext } from "@playwright/test";
import { superadminContext } from "./auth";
import { createTenant, setTenantModules, uniq } from "./organisme";
import { adminDeLOrganisme, MDP_PROFIL } from "./urgences-sante";

const API = `http://localhost:${process.env.E2E_API_PORT ?? "4000"}`;

/**
 * `verifie` nomme un vérificateur des options (p. ex. veraPDF) qui juge les octets de la réponse. `absent` est le
 * contraire de `texte` : une expression qui **ne doit pas** se trouver — le cas où une règle ne se déclenche pas se
 * prouve par l'absence de son motif, pas seulement par un statut qui passerait aussi pour une autre raison.
 */
export interface Attendu { statut: number; corps?: unknown; texte?: string; absent?: string; verifie?: string }
/**
 * `service` vise un service externe des options (un bac à sable) plutôt que le serveur ; `reessayer` (secondes) rejoue
 * l'étape jusqu'à ce qu'elle passe — pour ce qui arrive de façon asynchrone, comme une poussée.
 */
export interface Etape {
  pourquoi?: string; acteur?: string; service?: string; methode: "GET" | "POST" | "PUT" | "PATCH" | "DELETE";
  chemin: string; corps?: unknown; attendu: Attendu; capturer?: Record<string, string>; reessayer?: number;
}
export interface ServiceExterne { url: string; entetes?: Record<string, string> }
/** Rend les écarts constatés sur les octets d'une réponse ; vide si conforme. */
export type Verificateur = (octets: Buffer) => Promise<string[]>;
/**
 * `parametrer` agit sur l'organisme neuf, ses modules posés, **avant** les comptes et l'amorce : c'est là qu'un semis
 * de démonstration pose son paramétrage par ses propres appels, pour que les scénarios éprouvent le semis lui-même.
 */
export interface OptionsComportements {
  prefixe: string; modules: string[];
  services?: Record<string, ServiceExterne>; verificateurs?: Record<string, Verificateur>;
  parametrer?: (tenantId: string) => void | Promise<void>;
}
/** Ce qu'un scénario éprouve d'un paramétrage : une règle (par sa clé) ou un paramètre, et le cas joué. */
export interface Preuve { regle?: string; parametre?: string; cas: "declenche" | "ne-declenche" | "lu" }
export interface Scenario { id: string; titre: string; exigences: string[]; etapes: Etape[]; prouve?: Preuve[] }
export interface JeuComportements { acteurs: Record<string, { role: string }>; amorce: Etape[]; scenarios: Scenario[] }

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


/**
 * Déclare un `describe` qui crée un organisme neuf (`prefixe`, `modules`), ses comptes, joue l'amorce, puis un
 * essai par scénario — dans l'ordre, chacun pouvant lire les captures des précédents.
 */
export function declarerComportements(titre: string, jeu: JeuComportements, options: OptionsComportements): void {
  test.describe(titre, () => {
    const vars: Record<string, unknown> = {};
    const acteurs: Record<string, APIRequestContext> = {};
    let externe: APIRequestContext | undefined;

    /** Joue une étape, en la rejouant au besoin jusqu'à son délai, et rend les écarts du dernier essai. */
    async function jouer(e: Etape): Promise<string[]> {
      const fin = Date.now() + (e.reessayer ?? 0) * 1000;
      let ecarts = await jouerUneFois(e);
      while (ecarts.length && Date.now() < fin) {
        await new Promise((ok) => setTimeout(ok, 500));
        ecarts = await jouerUneFois(e);
      }
      return ecarts;
    }

    /** Joue une étape une fois et rend les écarts constatés — vides si tout est conforme. */
    async function jouerUneFois(e: Etape): Promise<string[]> {
      const service = e.service === undefined ? undefined : options.services?.[e.service];
      if (e.service !== undefined && !service) return [`service « ${e.service} » non configuré`];
      const qui = service ? externe : acteurs[e.acteur ?? "admin"];
      if (!qui) return [`acteur « ${e.acteur} » inconnu du fichier`];
      const chemin = String(rendre(e.chemin, vars));
      const corps = e.corps === undefined ? undefined : rendre(e.corps, vars);
      const r = await qui.fetch(`${service ? service.url : API}${chemin}`, { method: e.methode, data: corps, headers: service?.entetes });
      const octets = await r.body();
      const texte = octets.toString("utf8");
      let json: unknown = null;
      try { json = JSON.parse(texte); } catch { /* une réponse CSV n'est pas du JSON : l'attendu « texte » la lit telle quelle */ }
      const ou = `${e.methode} ${chemin}`;
      const ecarts: string[] = [];
      if (r.status() !== e.attendu.statut) ecarts.push(`${ou} : statut ${r.status()} au lieu de ${e.attendu.statut} — ${texte.slice(0, 300)}`);
      if (e.attendu.corps !== undefined) {
        ecarts.push(...ecartsDe(rendre(e.attendu.corps, vars), json).map((x) => `${ou} : ${x}`));
      }
      if (e.attendu.texte !== undefined && !new RegExp(String(rendre(e.attendu.texte, vars)), "m").test(texte)) {
        ecarts.push(`${ou} : le texte ne contient pas /${e.attendu.texte}/ — ${texte.slice(0, 300)}`);
      }
      const absent = e.attendu.absent === undefined ? null : new RegExp(String(rendre(e.attendu.absent, vars)), "m").exec(texte);
      if (absent) ecarts.push(`${ou} : le texte contient « ${absent[0]} », qui devait être absent — ${texte.slice(0, 300)}`);
      if (e.attendu.verifie !== undefined) {
        const verifier = options.verificateurs?.[e.attendu.verifie];
        ecarts.push(...(verifier ? await verifier(octets) : [`vérificateur « ${e.attendu.verifie} » non configuré`]).map((x) => `${ou} : ${x}`));
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
        tenantId = await createTenant(sa, uniq(options.prefixe));
        await setTenantModules(sa, tenantId, options.modules);
      } finally { await sa.dispose(); }
      await options.parametrer?.(tenantId);
      vars.suffixe = tenantId.slice(-6);
      vars.mois = new Date().toISOString().slice(0, 7);
      vars.mdp = MDP_PROFIL;
      acteurs.admin = await adminDeLOrganisme(tenantId);
      externe = await pwRequest.newContext({ storageState: { cookies: [], origins: [] } });

      // Les comptes : créés par l'administration, rendus utilisables sans franchir le mur de première
      // connexion (mot de passe, second facteur) — ce mur a ses propres essais.
      for (const [cle, a] of Object.entries(jeu.acteurs)) {
        const username = `${cle}-${vars.suffixe}`;
        const c = await acteurs.admin.post(`${API}/api/auth/users`, {
          data: { username, password: MDP_PROFIL, role: a.role, mustChangePassword: false },
        });
        expect(c.ok(), `compte ${username} : ${c.status()} ${await c.text()}`).toBeTruthy();
        const id = (await c.json()).id as string;
        await acteurs.admin.patch(`${API}/api/auth/users/${id}`, { data: { securitySetupRequired: false } });
        vars[`u_${cle}`] = id;
        vars[`p_${cle}`] = username;
        acteurs[cle] = await connecter(username, tenantId);
      }

      const ecarts: string[] = [];
      for (const e of jeu.amorce) ecarts.push(...await jouer(e));
      expect(ecarts, `l'amorce doit passer en entier :\n${ecarts.join("\n")}`).toEqual([]);
    });

    test.afterAll(async () => {
      await Promise.all([...Object.values(acteurs), ...(externe ? [externe] : [])].map((a) => a.dispose()));
    });

    // Ordonnés et solidaires : un scénario s'appuie sur les captures des précédents.
    test.describe.configure({ mode: "serial" });

    for (const s of jeu.scenarios) {
      test(`${s.id} — ${s.titre} (${s.exigences.join(", ")})`, async () => {
        const ecarts: string[] = [];
        for (const e of s.etapes) {
          // Un gabarit sans valeur naît d'une étape précédente en échec : on s'arrête, mais en montrant d'abord cet échec.
          const ici = await jouer(e).catch((err: Error) => [`${e.methode} ${e.chemin} : ${err.message}`]);
          ecarts.push(...ici.map((x) => (e.pourquoi ? `${x}\n    pourquoi : ${e.pourquoi}` : x)));
          if (ici.some((x) => x.includes("aucune valeur capturée"))) break;
        }
        expect(ecarts, ecarts.join("\n")).toEqual([]);
      });
    }
  });
}
