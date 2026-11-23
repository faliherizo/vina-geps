/**
 * **Où se connecte un compte d'organisme** — l'hôte que le déploiement exige (ADR 0033).
 *
 * En déploiement par sous-domaine (`TENANT_RESOLUTION=sous_domaine`), le serveur ne croit que l'hôte : un compte
 * d'organisme ne s'ouvre que sur `https://<slug>.<racine>`, et la racine n'accepte plus que les comptes globaux. Un
 * jeton obtenu sur le sous-domaine reste valable sur la racine : seule la **connexion** change d'adresse.
 *
 * Le slug se lit dans la liste du superadmin, avec le jeton global que le semis a déjà ouvert. En mode sélecteur
 * (poste local, essais), rien ne change : la connexion part vers l'adresse donnée.
 */
function resolveurHote(api) {
  let deploiement;
  const slugs = new Map();

  async function lireDeploiement() {
    deploiement ??= await fetch(`${api}/api/auth/deploiement`).then((r) => (r.ok ? r.json() : {})).catch(() => ({}));
    return deploiement;
  }

  async function chargerSlugs(jetonGlobal) {
    const r = await fetch(`${api}/api/superadmin/tenants`, { headers: { Authorization: `Bearer ${jetonGlobal}` } });
    if (!r.ok) return;
    for (const t of await r.json()) if (t.slug) slugs.set(t.id, t.slug);
  }

  /** L'adresse où connecter un compte de `tenantId` ; l'adresse donnée pour un compte global ou en mode sélecteur. */
  return async function baseDeConnexion(tenantId, jetonGlobal) {
    if (!tenantId) return api;
    const d = await lireDeploiement();
    if (d.resolution !== "sous_domaine" || !d.domaineRacine) return api;
    if (!slugs.has(tenantId) && jetonGlobal) await chargerSlugs(jetonGlobal);
    const slug = slugs.get(tenantId);
    if (!slug) {
      throw new Error(`l'organisme ${tenantId} n'a pas de sous-domaine : en déploiement par sous-domaine, ses comptes ne peuvent se connecter nulle part`);
    }
    const u = new URL(api);
    u.hostname = `${slug}.${d.domaineRacine}`;
    return u.origin;
  };
}

module.exports = { resolveurHote };
