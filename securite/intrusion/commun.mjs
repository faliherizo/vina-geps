// Harnais des tests d'intrusion de VINA GePS. Il ne dépend d'aucun paquet : `fetch` et `node:test` suffisent.
//
//   CIBLE                l'origine visée, frontal nginx compris (défaut http://localhost:8096) — les en-têtes et la
//                        limitation de débit font partie de ce qui se teste ;
//   SUPERADMIN_USERNAME  / SUPERADMIN_PASSWORD : l'exploitation de la pile visée (jamais écrits, jamais affichés) ;
//   DEMO_PASSWORD        le mot de passe des comptes que sème scripts/semer-fx-ao.cjs (défaut : celui de la démonstration).
//
// La préparation (organisme FX-AO semé, organisme témoin) se fait une fois par cible : son état est gardé dans
// `.etat-<cible>.local.json` (ignoré par git) pour les fichiers de test suivants, qui tournent chacun dans leur processus.
import { execFileSync } from "node:child_process";
import { existsSync, readFileSync, writeFileSync } from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";

const ICI = path.dirname(fileURLToPath(import.meta.url));
export const RACINE = path.join(ICI, "..", "..");
export const CIBLE = (process.env.CIBLE || "http://localhost:8096").replace(/\/+$/, "");
export const SUPERADMIN = { username: process.env.SUPERADMIN_USERNAME || "superadmin", password: process.env.SUPERADMIN_PASSWORD || "" };
export const MDP_DEMO = process.env.DEMO_PASSWORD || "Passe!Demo2026";
/** Une pause entre deux appels : le frontal limite à 20 requêtes/s par adresse ; on mesure l'application, pas la limite. */
const PAUSE_MS = Number(process.env.INTRUSION_PAUSE_MS ?? 80);
const attendre = (ms) => new Promise((ok) => setTimeout(ok, ms));

/** Un appel HTTP brut qui ne lève jamais : { statut, entetes, texte, json, duree }. */
export async function appel(methode, chemin, { corps, jeton, tenant, entetes } = {}) {
  await attendre(PAUSE_MS);
  const h = { ...(entetes || {}) };
  if (corps !== undefined) h["Content-Type"] = h["Content-Type"] || "application/json";
  if (jeton) h.Authorization = `Bearer ${jeton}`;
  if (tenant) h["X-Tenant-Id"] = tenant;
  const debut = Date.now();
  const rep = await fetch(`${CIBLE}${chemin}`, { method: methode, headers: h, redirect: "manual",
    body: corps === undefined ? undefined : typeof corps === "string" ? corps : JSON.stringify(corps) });
  const texte = await rep.text();
  let json = null;
  try { json = JSON.parse(texte); } catch { /* corps non JSON */ }
  return { statut: rep.status, entetes: Object.fromEntries(rep.headers.entries()), texte, json, duree: Date.now() - debut };
}

/** Une session : rend une fonction d'appel qui porte le jeton. */
export async function session({ username, password, tenantId }) {
  const r = await appel("POST", "/api/auth/login", { corps: { username, password, tenantId } });
  if (r.statut !== 200 || !r.json?.token) throw new Error(`connexion refusée (${r.statut}) pour ${username}`);
  const jeton = r.json.token;
  const s = (methode, chemin, opts = {}) => appel(methode, chemin, { jeton, ...opts });
  s.jeton = jeton;
  return s;
}

const FICHIER_ETAT = path.join(ICI, `.etat-${CIBLE.replace(/[^a-z0-9]+/gi, "_")}.local.json`);

/**
 * L'état de la campagne : l'organisme FX-AO semé par son script, et un organisme témoin qui a, lui, les modules que
 * FX-AO n'a pas (integrations) — c'est contre lui que se jouent l'isolation et les fonctions de modules.
 */
export async function preparer() {
  if (existsSync(FICHIER_ETAT)) return JSON.parse(readFileSync(FICHIER_ETAT, "utf8"));
  if (!SUPERADMIN.password) throw new Error("SUPERADMIN_PASSWORD absent de l'environnement");
  const sortie = execFileSync(process.execPath, [path.join(RACINE, "scripts", "semer-fx-ao.cjs"), "--api", CIBLE], {
    encoding: "utf8", timeout: 300_000,
    env: { ...process.env, SUPERADMIN_USER: SUPERADMIN.username, SUPERADMIN_PASS: SUPERADMIN.password, DEMO_PASSWORD: MDP_DEMO, SEMIS_PAUSE_MS: String(PAUSE_MS) },
  });
  const fx = (sortie.match(/^TENANT=(.+)$/m) ?? [])[1];
  if (!fx) throw new Error(`semer-fx-ao n'a pas nommé son organisme :\n${sortie.slice(-500)}`);
  const sa = await session(SUPERADMIN);
  const cree = await sa("POST", "/api/superadmin/tenants", { corps: { name: `Témoin intrusion ${Date.now()}`, type: "public_institution",
    country: "CA", region: "QC", planCode: "enterprise", adminUsername: "admin", adminPassword: MDP_DEMO } });
  if (cree.statut >= 300) throw new Error(`organisme témoin : ${cree.statut} ${cree.texte.slice(0, 200)}`);
  const temoin = cree.json.tenant.id;
  if (cree.json.admin?.userId) {
    await sa("PATCH", `/api/auth/users/${cree.json.admin.userId}`, { corps: { securitySetupRequired: false, mustChangePassword: false } });
  }
  await sa("PUT", `/api/saas/tenants/${temoin}/modules`, { corps: { modules: ["alertes", "integrations", "effectifs", "effectifs_communication"] } });
  // Une destinataire dans le témoin : une alerte n'existe qu'avec quelqu'un à joindre.
  const adminTemoin = await session({ username: "admin", password: MDP_DEMO, tenantId: temoin });
  const employe = await adminTemoin("POST", "/api/auth/users", { corps: { username: "temoin.employe", password: MDP_DEMO, role: "employee", mustChangePassword: false } });
  if (employe.statut >= 300) throw new Error(`destinataire du témoin : ${employe.statut} ${employe.texte.slice(0, 200)}`);
  await adminTemoin("PATCH", `/api/auth/users/${employe.json.id}`, { corps: { securitySetupRequired: false } });
  const etat = { cible: CIBLE, fx, temoin, destinataireTemoin: "temoin.employe" };
  writeFileSync(FICHIER_ETAT, `${JSON.stringify(etat, null, 2)}\n`, { mode: 0o600 });
  return etat;
}

/** La session d'un compte de l'organisme FX-AO (comptes de e2e/donnees/fx-ao/demonstration.json). */
export async function fxao(etat, username) {
  return session({ username, password: MDP_DEMO, tenantId: etat.fx });
}

export async function temoin(etat) {
  return session({ username: "admin", password: MDP_DEMO, tenantId: etat.temoin });
}
