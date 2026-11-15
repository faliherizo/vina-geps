import { useEffect, useState } from "react";
import { Message } from "./composants";
import { useAuth } from "./AuthContext";
import { LANGUAGES, useT } from "./i18n";

interface TenantOption { id: string; name: string; slug: string | null; }

/**
 * Choix explicite « aucun établissement », réservé aux comptes d'administration système.
 *
 * Une chaîne vide ne conviendrait pas : elle est indiscernable d'un champ non renseigné,
 * et l'on ne pourrait plus rendre la sélection obligatoire.
 */
const GLOBAL_ACCOUNT = "__global__";

export default function Login() {
  const { t, lang, setLang } = useT();
  const { login } = useAuth();
  const [username, setUsername] = useState("");
  const [password, setPassword] = useState("");
  const [mfaMethod, setMfaMethod] = useState<"question" | "totp" | null>(null);
  const [mfaQuestions, setMfaQuestions] = useState<string[]>([]);
  const [totpCode, setTotpCode] = useState("");
  const [securityAnswers, setSecurityAnswers] = useState<string[]>([]);
  const [error, setError] = useState("");
  const [busy, setBusy] = useState(false);

  // Multi-tenant (ADR 0033) : en `sous_domaine`, le **serveur** dit ce que l'hôte désigne
  // (`/api/auth/deploiement`) — l'établissement verrouillé, la console des comptes globaux, ou rien
  // de connu ; en `selecteur` (développement), on choisit dans la liste.
  const [tenants, setTenants] = useState<TenantOption[]>([]);
  const [tenantId, setTenantId] = useState<string>("");
  const [tenantLocked, setTenantLocked] = useState<TenantOption | null>(null);
  const [hote, setHote] = useState<"selecteur" | "etablissement" | "global" | "inconnu">("selecteur");
  // SAML 2.0 (exigence 41) : le bouton n'apparaît que si l'instance a un IdP déclaré —
  // un bouton qui mène à un 503 est une porte peinte sur un mur.
  const [samlActif, setSamlActif] = useState(false);
  // Entra ID : en mode **simulé** (aucun client déclaré) la session se pose en un appel JSON ;
  // face au vrai Entra, c'est le navigateur qui doit aller chez Microsoft et en revenir par le
  // callback — un POST ne peut pas faire ce voyage. Le serveur dit lequel des deux on est.
  const [entraSimule, setEntraSimule] = useState(true);
  // P-TN-2 : les annuaires que l'organisme a déclarés. Ils dépendent de l'organisme : on les relit
  // quand il change, et la liste reste vide tant qu'aucun n'est choisi.
  const [annuaires, setAnnuaires] = useState<Array<{ id: string; nom: string; fournisseur?: string }>>([]);
  // Le **pays/la devise** ne sont PAS un choix utilisateur : ils sont déterminés par
  // l'établissement (tenant) et appliqués après connexion (`useCurrentTenant` renseigne
  // `localStorage.hostCountry` depuis le tenant). Aucun sélecteur de pays au login.
  useEffect(() => {
    // Ce que le déploiement a résolu depuis l'hôte fait foi ; le sélecteur n'existe qu'en mode « selecteur ».
    fetch("/api/auth/deploiement")
      .then(async (r) => ({ ok: r.ok, corps: await r.json().catch(() => null) }))
      .then(({ ok, corps }) => {
        const resolution = corps?.resolution ?? "selecteur";
        if (resolution !== "sous_domaine") {
          setHote("selecteur");
          fetch("/api/auth/tenants").then((r) => (r.ok ? r.json() : [])).then((l) => setTenants(Array.isArray(l) ? l : [])).catch(() => setTenants([]));
          return;
        }
        if (!ok || !corps) { setHote("inconnu"); return; }
        if (corps.etablissement) { setTenantLocked({ id: corps.etablissement.id, name: corps.etablissement.nom, slug: corps.etablissement.slug }); setTenantId(corps.etablissement.id); setHote("etablissement"); }
        else { setTenantId(GLOBAL_ACCOUNT); setHote("global"); }
      })
      .catch(() => { setHote("selecteur"); fetch("/api/auth/tenants").then((r) => (r.ok ? r.json() : [])).then((l) => setTenants(Array.isArray(l) ? l : [])).catch(() => setTenants([])); });
    fetch("/api/auth/sso/entra/status").then((r) => (r.ok ? r.json() : null)).then((j) => setEntraSimule(j?.mock !== false)).catch(() => setEntraSimule(true));
    fetch("/api/auth/sso/saml/status").then((r) => (r.ok ? r.json() : null)).then((j) => setSamlActif(!!j?.enabled)).catch(() => setSamlActif(false));
  }, []);

  useEffect(() => {
    if (!tenantId || tenantId === GLOBAL_ACCOUNT) { setAnnuaires([]); return; }
    fetch(`/api/auth/sso/annuaires?tenantId=${encodeURIComponent(tenantId)}`)
      .then((r) => (r.ok ? r.json() : null))
      .then((j) => setAnnuaires(Array.isArray(j?.annuaires) ? j.annuaires : []))
      .catch(() => setAnnuaires([]));
  }, [tenantId]);

  async function ssoLogin() {
    setError(""); setBusy(true);
    if (!entraSimule) { window.location.assign("/api/auth/sso/entra/login"); return; }
    try {
      const r = await fetch("/api/auth/sso/entra/session", { method: "POST", credentials: "include", headers: { "Content-Type": "application/json" } });
      if (!r.ok) { const j = await r.json().catch(() => ({})); setError((j.errors || ["Échec de la connexion SSO"]).join(", ")); return; }
      // Le backend a posé la session ; on recharge pour que le contexte d'auth la reprenne
      // (fonctionne quel que soit le préfixe de l'app : "/" en dev, "/admin" en production).
      window.location.reload();
    } finally { setBusy(false); }
  }

  // Un annuaire LDAPS (ADR 0083) n'a pas de redirection : l'identifiant et le mot de passe déjà saisis lui sont envoyés.
  async function ldapsLogin(annuaireId: string) {
    if (!username.trim() || !password) { setError(t("login.ldapsChamps")); return; }
    setError(""); setBusy(true);
    try {
      const r = await fetch(`/api/auth/sso/annuaires/${encodeURIComponent(annuaireId)}/connexion`, { method: "POST", credentials: "include",
        headers: { "Content-Type": "application/json" }, body: JSON.stringify({ identifiant: username, motDePasse: password }) });
      if (!r.ok) { const j = await r.json().catch(() => ({})); setError((j.errors || [t("login.ldapsChamps")]).join(", ")); return; }
      window.location.reload();
    } finally { setBusy(false); }
  }

  async function submit(e: React.FormEvent) {
    e.preventDefault();
    if (hote === "inconnu") return;
    if (!tenantLocked && !tenantId) {
      setError("Sélectionnez votre organisme pour vous connecter.");
      return;
    }
    setError(""); setBusy(true);
    // En sous-domaine, rien n'est envoyé : l'hôte a déjà choisi, le serveur refuserait le contraire.
    const r = await login(
      username,
      password,
      mfaMethod === "totp"
        ? { totpCode }
        : mfaMethod === "question"
          ? { securityAnswers }
          : undefined,
      hote !== "selecteur" || tenantId === GLOBAL_ACCOUNT ? null : (tenantId || null),
    );
    setBusy(false);
    if (!r.ok && r.tenantChoiceRequired) {
      // Ne peut plus survenir depuis cet écran, qui impose toujours un établissement.
      // Conservé pour les clients d'API qui se connectent sans en préciser un.
      setError("Cet identifiant existe dans plusieurs organismes. Précisez lequel.");
      return;
    }
    if (!r.ok && r.mfaRequired) {
      // Le backend renvoie le même `mfaRequired` pour la 1re invite ET pour une tentative
      // échouée. On les distingue : si on était **déjà** sur l'étape du 2e facteur (mfaMethod
      // défini) et qu'on a soumis quelque chose, c'est que la réponse/le code était incorrect.
      const wasAnswering = mfaMethod !== null
        && (mfaMethod === "totp" ? totpCode.trim() !== "" : securityAnswers.some((a) => a.trim() !== ""));
      setMfaMethod(r.securityMethod || null);
      const questions = Array.isArray(r.securityQuestions) && r.securityQuestions.length
        ? r.securityQuestions
        : (r.securityQuestion ? [r.securityQuestion] : []);
      setMfaQuestions(questions);
      setSecurityAnswers(new Array(questions.length).fill(""));
      setTotpCode("");
      setError(wasAnswering
        ? ((r.securityMethod || mfaMethod) === "totp"
            ? "Code de vérification incorrect. Une erreur est survenue, veuillez recommencer."
            : "Réponse de sécurité incorrecte. Une erreur est survenue, veuillez recommencer.")
        : "");
      return;
    }
    if (!r.ok) setError(r.error || "Une erreur est survenue, veuillez recommencer.");
  }

  return (
    <div style={{ minHeight: "100vh", display: "grid", placeItems: "center", background: "var(--bg)", padding: 20 }}>
      <form onSubmit={submit} className="card" style={{ width: "100%", maxWidth: 380 }} data-testid="login-form">
        {/* La langue se choisit **avant** d'entrer (dossier UX § 3.1) : le sélecteur est le même qu'après connexion, persisté. */}
        <div data-testid="login-lang-select" role="group" aria-label={t("login.langue")} style={{ display: "flex", gap: 4, justifyContent: "flex-end", marginBottom: 8 }}>
          {LANGUAGES.map((l) => (
            <button key={l.code} type="button" data-testid={`login-lang-${l.code}`} className={lang === l.code ? "primary" : "secondary"} aria-pressed={lang === l.code}
              aria-label={l.label} title={l.label} onClick={() => setLang(l.code)} style={{ minWidth: 44, minHeight: 32, padding: "4px 8px" }}>
              <span aria-hidden="true">{l.flag}</span> {l.code.toUpperCase()}
            </button>
          ))}
        </div>
        <h1 style={{ marginBottom: 4 }}>{t("login.title")}</h1>
        <p className="subtitle">{t("login.subtitle")}</p>
        {error && <Message variante="erreur" testId="login-error">{error}</Message>}
        {hote === "inconnu" ? (
          // Le sous-domaine ne désigne aucun établissement actif : on le dit, sans formulaire ni liste.
          <div role="alert" data-testid="login-tenant-inconnu" style={{ marginBottom: 12 }}>
            <strong>{t("login.etablissementInconnu")}</strong>
            <p className="subtitle" style={{ marginTop: 4 }}>{t("login.etablissementInconnuAide")}</p>
          </div>
        ) : tenantLocked ? (
          // Production : l'établissement provient du sous-domaine. L'utilisateur n'a rien à
          // désigner — l'URL le fait pour lui, et le champ n'est là que pour le confirmer.
          <div style={{ marginBottom: 12 }} data-testid="login-tenant-locked">
            <label htmlFor="f-Login-1">{t("login.etablissement")}</label>
            <input id="f-Login-1" value={tenantLocked.name} readOnly disabled />
          </div>
        ) : hote === "global" ? (
          <p className="subtitle" data-testid="login-comptes-globaux" style={{ marginBottom: 12 }}>{t("login.comptesGlobaux")}</p>
        ) : (
          // Hors sous-domaine dédié : le choix est **obligatoire**. On se connecte toujours
          // « à un établissement », jamais dans l'absolu — c'est lui qui définit le périmètre
          // de données, les règles de paie et la piste d'audit de la session.
          <div style={{ marginBottom: 12 }}>
            <label htmlFor="f-login-tenant">
              {t("login.etablissement")} <span className="req-star" aria-hidden="true">*</span>
            </label>
            {/* `aria-required` sans `required` : la validation native afficherait une bulle
                transitoire, illisible par un lecteur d'écran et disparaissant au premier clic.
                Le contrôle est fait à la soumission, avec un message persistant. */}
            <select id="f-login-tenant" data-testid="login-tenant" aria-required="true"
              value={tenantId} onChange={(e) => setTenantId(e.target.value)}>
              <option value="" disabled>{t("login.choisirEtablissement")}</option>
              {tenants.map((t) => <option key={t.id} value={t.id}>{t.name}</option>)}
              {/* Comptes d'administration système : ils ne relèvent d'aucun établissement. */}
              <option value={GLOBAL_ACCOUNT}>{t("login.administrationSysteme")}</option>
            </select>
          </div>
        )}
        <div style={{ marginBottom: 12 }}>
          <label htmlFor="f-login-user">{t("login.identifiant")} <span className="req-star" aria-hidden="true">*</span></label>
          <input id="f-login-user" data-testid="login-user" autoFocus value={username}
            onChange={(e) => setUsername(e.target.value)} required />
        </div>
        <div style={{ marginBottom: 16 }}>
          <label htmlFor="f-login-pass">{t("login.motDePasse")} <span className="req-star" aria-hidden="true">*</span></label>
          <input id="f-login-pass" data-testid="login-pass" type="password" value={password}
            onChange={(e) => setPassword(e.target.value)} required />
        </div>
        {mfaMethod === "question" && (
          <div style={{ marginBottom: 16 }}>
            {mfaQuestions.map((q, i) => (
              <div key={`${q}-${i}`} style={{ marginBottom: 8 }}>
                <label>{q || t("login.questionSecurite", { n: String(i + 1) })} <span className="req-star" aria-hidden="true">*</span></label>
                <input
                  aria-label={mfaQuestions[i] ?? t("login.reponseSecurite", { n: String(i + 1) })}
              data-testid={i === 0 ? "login-mfa-answer" : `login-mfa-answer-${i}`}
                  value={securityAnswers[i] || ""}
                  onChange={(e) => {
                    const next = [...securityAnswers];
                    next[i] = e.target.value;
                    setSecurityAnswers(next);
                  }}
                  required
                />
              </div>
            ))}
          </div>
        )}
        {mfaMethod === "totp" && (
          <div style={{ marginBottom: 16 }}>
            <label htmlFor="f-login-mfa-totp">{t("login.codeTotp")} <span className="req-star" aria-hidden="true">*</span></label>
            <input id="f-login-mfa-totp"
              data-testid="login-mfa-totp"
              value={totpCode}
              onChange={(e) => setTotpCode(e.target.value)}
              inputMode="numeric"
              pattern="[0-9]{6}"
              required
            />
          </div>
        )}
        <button type="submit" className="primary" data-testid="login-submit" disabled={busy || hote === "inconnu"} style={{ width: "100%" }}>
          {busy ? t("login.enCours") : mfaMethod ? t("login.validerFacteur") : t("login.seConnecter")}
        </button>

        {/* SSO fédéré Microsoft Entra ID (NF-19/20). */}
        {/* `--muted` plutôt qu'un gris clair codé en dur : #94a3b8 sur blanc plafonne à ~2,6:1,
            sous le seuil de 4,5:1 du WCAG 1.4.3 (exigence SGQRI-008 3.0). */}
        <div style={{ display: "flex", alignItems: "center", gap: 8, margin: "14px 0 10px", color: "var(--muted)", fontSize: 12 }}>
          <span style={{ flex: 1, height: 1, background: "#e2e8f0" }} /> {t("login.ou")} <span style={{ flex: 1, height: 1, background: "#e2e8f0" }} />
        </div>
        <button type="button" className="secondary" data-testid="login-sso-entra" disabled={busy} style={{ width: "100%" }} onClick={ssoLogin}>
          <span aria-hidden="true">🪟</span> {t("login.microsoft")}
        </button>
        {samlActif && (
          <a className="btn secondary" data-testid="login-sso-saml" href="/api/auth/sso/saml/login"
            style={{ display: "inline-flex", width: "100%", marginTop: 8, justifyContent: "center", boxSizing: "border-box" }}>
            <span aria-hidden="true">🏛️</span> {t("login.saml")}
          </a>
        )}
        {annuaires.length > 0 && (
          <ul aria-label={t("login.annuaires")} data-testid="login-annuaires" style={{ listStyle: "none", padding: 0, margin: "8px 0 0" }}>
            {annuaires.map((a) => (
              <li key={a.id} style={{ marginTop: 8 }}>
                {a.fournisseur === "ldaps" ? (
                  <button type="button" className="secondary" data-testid={`login-annuaire-${a.id}`} disabled={busy} style={{ width: "100%" }}
                    onClick={() => void ldapsLogin(a.id)}>
                    <span aria-hidden="true">🗂️</span> {t("login.annuaireLdaps", { nom: a.nom })}
                  </button>
                ) : (
                  <a className="btn secondary" data-testid={`login-annuaire-${a.id}`} href={`/api/auth/sso/annuaires/${encodeURIComponent(a.id)}/login`}
                    style={{ display: "inline-flex", width: "100%", justifyContent: "center", boxSizing: "border-box" }}>
                    <span aria-hidden="true">🗂️</span> {t("login.annuaire", { nom: a.nom })}
                  </a>
                )}
              </li>
            ))}
          </ul>
        )}
        <p className="muted" style={{ marginTop: 12, fontSize: 12 }}>
          {t("login.compteDefaut", { compte: "admin / admin" })}
        </p>
        <p style={{ marginTop: 8, fontSize: 13, textAlign: "center" }}>
          <a href="/" data-testid="login-vitrine-link">{t("login.decouvrir")}</a>
        </p>
      </form>
    </div>
  );
}
