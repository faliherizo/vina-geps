/**
 * **Le client HTTP de l'organisme par défaut** : l'API publique, rien d'autre — aucune écriture directe en base.
 *
 * Un refus lève une `ErreurApi` qui porte le statut (un 409 se reconnaît sans analyser une phrase) et nomme la
 * méthode, le chemin et le début du motif. Le frontal limite le débit (20 requêtes par seconde par adresse,
 * `frontend/nginx.conf`) : le client le respecte par une pause entre deux appels (`SEMIS_PAUSE_MS`), il ne le
 * contourne pas. Un corps de requête n'apparaît jamais dans un message d'erreur : il peut porter un mot de passe.
 */
const { resolveurHote } = require("../hote-etablissement.cjs");

const PAUSE_MS = Number(process.env.SEMIS_PAUSE_MS ?? 70);
const attendre = (ms) => new Promise((ok) => setTimeout(ok, ms));

class ErreurApi extends Error {
  constructor(methode, chemin, statut, corps) {
    super(`${methode} ${chemin.split("?")[0]} → ${statut} ${(typeof corps === "string" ? corps : JSON.stringify(corps ?? "")).slice(0, 300)}`);
    this.statut = statut;
    this.corps = corps;
  }
}

function clientApi(api) {
  const baseDeConnexion = resolveurHote(api);
  let jetonGlobal;

  /** L'appel brut : le statut et le corps, sans lever — l'autocontrôle juge lui-même un 403 attendu. */
  async function essayer(chemin, { methode = "GET", corps, jeton, tenantId, base = api } = {}) {
    await attendre(PAUSE_MS);
    const entetes = { "Content-Type": "application/json" };
    if (jeton) entetes.Authorization = `Bearer ${jeton}`;
    if (tenantId) entetes["X-Tenant-Id"] = tenantId;
    const r = await fetch(`${base}${chemin}`, { method: methode, headers: entetes, body: corps ? JSON.stringify(corps) : undefined });
    const texte = await r.text();
    let json = texte;
    try { json = texte ? JSON.parse(texte) : null; } catch { json = texte; }
    return { statut: r.status, corps: json };
  }

  async function appeler(chemin, options = {}) {
    const { statut, corps } = await essayer(chemin, options);
    if (statut >= 300) throw new ErreurApi(options.methode ?? "GET", chemin, statut, corps);
    return corps;
  }

  /** Le jeton d'une session, ou `null` si les identifiants sont refusés (l'appelant décide de réinitialiser). */
  /** Un compte d'organisme se connecte sur l'hôte de son organisme (déploiement par sous-domaine, hote-etablissement.cjs). */
  async function connecter({ username, password, tenantId }) {
    const base = await baseDeConnexion(tenantId, jetonGlobal);
    const { statut, corps } = await essayer("/api/auth/login", { methode: "POST", corps: { username, password, tenantId }, base });
    const jeton = statut === 200 && corps?.token ? corps.token : null;
    if (jeton && !tenantId) jetonGlobal = jeton;
    return jeton;
  }

  return { essayer, appeler, connecter };
}

module.exports = { clientApi, ErreurApi };
