/**
 * **Appeler l'API d'un semis de démonstration** — un seul endroit pour les semis d'Urgences-santé et de FX-AO.
 *
 * Un refus du serveur lève une erreur qui nomme la méthode, le chemin, le statut et le début du motif : un semis
 * qui échoue doit dire où, pas seulement qu'il a échoué.
 *
 * Le frontal limite le débit (20 requêtes par seconde par adresse, `frontend/nginx.conf`) : c'est un contrôle de
 * sécurité, que le semis respecte au lieu de le contourner — une pause entre deux appels (`SEMIS_PAUSE_MS`).
 */
const { resolveurHote } = require("./hote-etablissement.cjs");

const PAUSE_MS = Number(process.env.SEMIS_PAUSE_MS ?? 70);
const attendre = (ms) => new Promise((ok) => setTimeout(ok, ms));

function clientApi(api) {
  const baseDeConnexion = resolveurHote(api);
  let jetonGlobal;

  async function appeler(chemin, { methode = "GET", corps, jeton, tenantId, base = api } = {}) {
    await attendre(PAUSE_MS);
    const entetes = { "Content-Type": "application/json" };
    if (jeton) entetes.Authorization = `Bearer ${jeton}`;
    if (tenantId) entetes["X-Tenant-Id"] = tenantId;
    const r = await fetch(`${base}${chemin}`, { method: methode, headers: entetes, body: corps ? JSON.stringify(corps) : undefined });
    const texte = await r.text();
    if (!r.ok) throw new Error(`${methode} ${chemin} → ${r.status} ${texte.slice(0, 300)}`);
    return texte ? JSON.parse(texte) : null;
  }

  /** Un compte d'organisme se connecte sur l'hôte de son organisme (déploiement par sous-domaine) ; un compte global, ici. */
  async function connecter(identifiants) {
    const base = await baseDeConnexion(identifiants.tenantId, jetonGlobal);
    const r = await appeler("/api/auth/login", { methode: "POST", corps: identifiants, base });
    if (!r?.token) throw new Error(`connexion refusée pour ${identifiants.username}`);
    if (!identifiants.tenantId) jetonGlobal = r.token;
    return r.token;
  }

  return { appeler, connecter };
}

/** `--nom valeur` sur la ligne de commande, sinon le défaut. */
const argument = (nom, defaut) => {
  const i = process.argv.indexOf(`--${nom}`);
  return i > 0 && process.argv[i + 1] ? process.argv[i + 1] : defaut;
};

/** Un jour en avant ou en arrière d'aujourd'hui, au format `AAAA-MM-JJ`. */
const jour = (decalage) => {
  const d = new Date();
  d.setDate(d.getDate() + decalage);
  return d.toISOString().slice(0, 10);
};

module.exports = { clientApi, argument, jour };
