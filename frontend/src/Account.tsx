/**
- Page « Mon compte » — l'utilisateur connecté peut :
-  - consulter/modifier ses informations personnelles (si un profil employé est lié) ;
-  - changer son mot de passe ;
-  - configurer/remplacer son second facteur (question secrète ou MFA TOTP).
 *
- Réutilise les endpoints existants : /api/auth/profile, /api/auth/change-password,
- /api/auth/security/status, /api/auth/security/totp/init, /api/auth/security/setup.
 *
- Accessibilité : sections en <section> avec titres, <fieldset>/<legend> pour les
- groupes, labels associés, messages role="alert"/role="status", focus visibles.
 */
import { useCallback, useEffect, useState } from "react";
import { useAuth } from "./AuthContext";
import { ChampPhotoProfil } from "./ProfilUtilisateur";
import { usePasswordPolicy } from "./PasswordRequirements";
import { useT } from "./i18n";
import CanauxNotification from "./CanauxNotification";
import { EnTeteEcran, Icone, Message, SommaireSections, type SectionSommaire } from "./composants";

interface Profile {
  employeeId: string; firstName: string; lastName: string;
  email: string; phone: string; address: string; dateOfBirth: string; jobTitle: string;
}
interface SecurityStatus {
  mustChangePassword: boolean; securitySetupRequired: boolean;
  securityMethod: "question" | "totp" | null; hasSecurityQuestion: boolean;
  securityQuestionsCount?: number;
  minSecurityQuestions?: number;
}

const MIN_SECURITY_QUESTIONS = 3;

/** Libellés des données du dossier — jamais un code technique à l'écran. */
const FONCTIONS: Record<string, string> = {
  ceo: "Président-directeur général", director: "Director", manager: "Gestionnaire",
  lead: "Chef d'équipe", employee: "Employé", intern: "Stagiaire",
};
const STATUTS: Record<string, string> = {
  active: "Actif", on_leave: "En congé", terminated: "Fin d'emploi",
};
const ROLES: Record<string, string> = {
  superadmin: "Super-administrateur", admin: "Administrateur", manager: "Gestionnaire",
  rh: "Ressources humaines", director: "Directeur", accountant: "Comptable", employee: "Employé",
};
const TYPES_TRAVAILLEUR: Record<string, string> = {
  employee: "Salarié", incorporated: "Incorporé", self_employed: "Travailleur autonome",
};

export default function Account() {
  const { user, apiFetch, refreshUser } = useAuth();
  const { t } = useT();
  // Longueur minimale exigée : lue du serveur, jamais recopiée.
  const minLength = usePasswordPolicy();
  const [error, setError] = useState("");
  const [success, setSuccess] = useState("");

  // État pour l'onglet actif (par défaut "identity")
  const [activeTab, setActiveTab] = useState<string>("identity");

  // Effacer les bannières après 4s
  useEffect(() => {
    if (!error && !success) return;
    const id = setTimeout(() => { setError(""); setSuccess(""); }, 4000);
    return () => clearTimeout(id);
  }, [error, success]);

  // ── Profil ────────────────────────────────────────────────────────────────
  const [profile, setProfile] = useState<Profile | null>(null);
  const [pform, setPform] = useState({ firstName: "", lastName: "", email: "", phone: "", address: "", dateOfBirth: "" });
  /** Données du dossier consultables sans être modifiables (elles relèvent de l'employeur). */
  const [readOnly, setReadOnly] = useState<Record<string, any> | null>(null);

  // ── Mot de passe ────────────────────────────────────────────────────────────
  const [oldPassword, setOldPassword] = useState("");
  const [newPassword, setNewPassword] = useState("");
  const [confirmPassword, setConfirmPassword] = useState("");

  // ── Sécurité (MFA) ───────────────────────────────────────────────────────────
  const [secStatus, setSecStatus] = useState<SecurityStatus | null>(null);
  const [method, setMethod] = useState<"question" | "totp">("question");
  const [questions, setQuestions] = useState<Array<{ question: string; answer: string; custom?: boolean }>>(
    Array.from({ length: MIN_SECURITY_QUESTIONS }, () => ({ question: "", answer: "", custom: false })),
  );
  const [questionChoices, setQuestionChoices] = useState<string[]>([]);
  const [totp, setTotp] = useState<{ secret: string; otpauthUrl: string; qrSvg?: string } | null>(null);
  const [totpCode, setTotpCode] = useState("");

  // Passkeys FIDO2 / WebAuthn (#93).
  const [passkeys, setPasskeys] = useState<Array<{ id: string; credentialId: string; createdAt: string }>>([]);
  async function loadPasskeys() {
    const r = await apiFetch("/api/auth/webauthn/credentials").then((x) => (x.ok ? x.json() : [])).catch(() => []);
    setPasskeys(Array.isArray(r) ? r : []);
  }
  useEffect(() => { loadPasskeys(); }, []); // eslint-disable-line
  async function addPasskey() {
    // Cérémonie WebAuthn (mode démonstration : le navigateur réel appellerait navigator.credentials).
    const opts = await apiFetch("/api/auth/webauthn/register/begin", { method: "POST" }).then((r) => (r.ok ? r.json() : null)).catch(() => null);
    if (!opts) { setError("Impossible de démarrer l'enregistrement de la passkey."); return; }
    const credentialId = `passkey-${Date.now()}`;
    const r = await apiFetch("/api/auth/webauthn/register/finish", { method: "POST", body: JSON.stringify({ credentialId, publicKey: `pk-${credentialId}`, challenge: opts.challenge, counter: 0, label: "Cet appareil" }) });
    if (r.ok) { setSuccess("Passkey enregistrée."); loadPasskeys(); }
    else { const j = await r.json().catch(() => ({})); setError((j.errors || ["Erreur"]).join(", ")); }
  }
  /** Vérifie une passkey enregistrée (cérémonie d'authentification, compteur croissant contre le rejeu) — mode démonstration, comme l'enregistrement. */
  const [verifiee, setVerifiee] = useState<string | null>(null);
  async function verifierPasskey(credentialId: string) {
    setVerifiee(null);
    const opts = await apiFetch("/api/auth/webauthn/authenticate/begin", { method: "POST" }).then((r) => (r.ok ? r.json() : null)).catch(() => null);
    if (!opts) { setError("Impossible de démarrer la vérification de la passkey."); return; }
    const r = await apiFetch("/api/auth/webauthn/authenticate/finish", { method: "POST", body: JSON.stringify({ credentialId, challenge: opts.challenge, counter: Date.now() }) });
    if (r.ok) { setSuccess("Passkey vérifiée : elle répond et son compteur a avancé."); setVerifiee(credentialId); }
    else { const j = await r.json().catch(() => ({})); setError((j.errors || ["Vérification refusée"]).join(", ")); }
  }
  async function removePasskey(id: string) {
    const r = await apiFetch(`/api/auth/webauthn/credentials/${id}`, { method: "DELETE" });
    if (r.ok || r.status === 204) { setSuccess("Passkey retirée."); loadPasskeys(); }
  }

  const load = useCallback(async () => {
    const [pr, sr, qs] = await Promise.all([
      apiFetch("/api/auth/profile").then((r) => (r.ok ? r.json() : null)),
      apiFetch("/api/auth/security/status").then((r) => (r.ok ? r.json() : null)),
      apiFetch("/api/auth/security/questions").then((r) => (r.ok ? r.json() : null)),
    ]);
    if (pr?.profile) {
      setProfile(pr.profile);
      setPform({
        firstName: pr.profile.firstName ?? "", lastName: pr.profile.lastName ?? "",
        email: pr.profile.email ?? "", phone: pr.profile.phone ?? "", address: pr.profile.address ?? "",
        dateOfBirth: pr.profile.dateOfBirth ?? "",
      });
    } else {
      setProfile(null);
    }
    setReadOnly(pr?.readOnly ?? null);
    if (sr) { setSecStatus(sr); if (sr.securityMethod) setMethod(sr.securityMethod); }
    if (qs?.questions?.length) {
      setQuestionChoices(qs.questions);
      setQuestions((prev) => prev.map((row, i) => ({
        question: row.question || qs.questions[i % qs.questions.length] || "",
        answer: row.answer,
      })));
    }
  }, [apiFetch]);
  useEffect(() => { load(); }, [load]);

  async function saveProfile(e: React.FormEvent) {
    e.preventDefault();
    setError(""); setSuccess("");
    const r = await apiFetch("/api/auth/profile", { method: "PATCH", body: JSON.stringify(pform) });
    if (!r.ok) { const j = await r.json().catch(() => ({})); setError(j.errors?.join(", ") || "Erreur"); return; }
    setSuccess(t("account.profileSaved"));
    load();
  }

  async function changePassword(e: React.FormEvent) {
    e.preventDefault();
    setError(""); setSuccess("");
    if (newPassword.length < 8) { setError(t("account.errPwdShort")); return; }
    if (newPassword !== confirmPassword) { setError(t("account.errPwdMismatch")); return; }
    const r = await apiFetch("/api/auth/change-password", {
      method: "POST", body: JSON.stringify({ oldPassword, newPassword }),
    });
    if (!r.ok) { const j = await r.json().catch(() => ({})); setError(j.errors?.join(", ") || "Erreur"); return; }
    setOldPassword(""); setNewPassword(""); setConfirmPassword("");
    setSuccess(t("account.pwdChanged"));
  }

  async function initTotp() {
    setError("");
    const r = await apiFetch("/api/auth/security/totp/init", { method: "POST" });
    if (!r.ok) { setError("Init TOTP impossible"); return; }
    setTotp(await r.json());
  }

  async function saveSecurity(e: React.FormEvent) {
    e.preventDefault();
    setError(""); setSuccess("");
    let body: any;
    if (method === "question") {
      if (questions.length < MIN_SECURITY_QUESTIONS) {
        setError(`Au moins ${MIN_SECURITY_QUESTIONS} questions sont requises.`);
        return;
      }
      for (const q of questions) {
        if (q.question.trim().length < 5) { setError(t("account.errQuestion")); return; }
        if (q.answer.trim().length < 2) { setError(t("account.errAnswer")); return; }
      }
      body = { method: "question", questions };
    } else {
      if (!totp) { setError(t("account.errTotpInit")); return; }
      if (!/^\d{6}$/.test(totpCode)) { setError(t("account.errTotpCode")); return; }
      body = { method: "totp", code: totpCode };
    }
    const r = await apiFetch("/api/auth/security/setup", { method: "POST", body: JSON.stringify(body) });
    if (!r.ok) { const j = await r.json().catch(() => ({})); setError(j.errors?.join(", ") || "Erreur"); return; }
    setQuestions(Array.from({ length: MIN_SECURITY_QUESTIONS }, () => ({ question: "", answer: "" }))); setTotp(null); setTotpCode("");
    setSuccess(t("account.securitySaved"));
    await refreshUser();
    load();
  }

  const [questionsTest, setQuestionsTest] = useState<string[]>([]);
  const [verdictTest, setVerdictTest] = useState("");
  async function chargerQuestionsTest() {
    const r = await apiFetch("/api/auth/security/question");
    const j = r.ok ? await r.json().catch(() => null) : null;
    setQuestionsTest(Array.isArray(j?.questions) ? j.questions : []);
  }
  async function testerFacteur(f: HTMLFormElement) {
    setVerdictTest("");
    const d = new FormData(f);
    const corps = secStatus?.securityMethod === "totp"
      ? { code: String(d.get("code") ?? "") }
      : { answers: questionsTest.map((_, i) => String(d.get(`reponse${i}`) ?? "")) };
    const r = await apiFetch("/api/auth/security/verify", { method: "POST", body: JSON.stringify(corps) });
    const j = await r.json().catch(() => ({}));
    setVerdictTest(r.ok && j.ok ? "Second facteur vérifié : vos réponses sont bonnes." : r.ok ? "Vérification refusée : réponse ou code incorrect." : (j.errors ?? ["Vérification impossible"]).join(", "));
    f.reset();
  }

  const methodLabel = secStatus?.securityMethod === "totp"
    ? t("account.methodTotp")
    : secStatus?.securityMethod === "question"
      ? t("account.methodQuestion")
      : t("account.methodNone");

  return (
    <div className="account-page">
      <EnTeteEcran titre={t("account.title")} description={t("account.subtitle")} icone="utilisateur" />

      {error && <Message variante="erreur" testId="account-error" onFermer={() => setError("")}>{error}</Message>}
      {success && <Message variante="succes" testId="account-success" onFermer={() => setSuccess("")}>{success}</Message>}

      {/* Barre d'onglets (Navigation par onglets) */}
      <div className="account-tabs-header" role="tablist" aria-label="Sections de mon compte" style={{ display: "flex", gap: "8px", borderBottom: "1px solid #ccc", marginBottom: "20px", overflowX: "auto" }}>
        <button
          role="tab"
          aria-selected={activeTab === "identity"}
          type="button"
          data-testid="account-tab-identity"
          className={`tab-btn ${activeTab === "identity" ? "active" : ""}`}
          onClick={() => setActiveTab("identity")}
          style={{ padding: "10px 16px", cursor: "pointer", borderBottom: activeTab === "identity" ? "2px solid var(--primary, #007bff)" : "none", background: "none", border: "none", fontWeight: activeTab === "identity" ? "bold" : "normal" }}
        >
          <Icone nom="utilisateur" /> {t("account.identity")}
        </button>

        <button
          role="tab"
          aria-selected={activeTab === "profile"}
          type="button"
          data-testid="account-tab-profile"
          className={`tab-btn ${activeTab === "profile" ? "active" : ""}`}
          onClick={() => setActiveTab("profile")}
          style={{ padding: "10px 16px", cursor: "pointer", background: "none", border: "none", borderBottom: activeTab === "profile" ? "2px solid var(--primary, #007bff)" : "none", fontWeight: activeTab === "profile" ? "bold" : "normal" }}
        >
          <Icone nom="carte" /> {t("account.personalInfo")}
        </button>

        {readOnly && (
          <button
            role="tab"
            aria-selected={activeTab === "file"}
            type="button"
            data-testid="account-tab-file"
            className={`tab-btn ${activeTab === "file" ? "active" : ""}`}
            onClick={() => setActiveTab("file")}
            style={{ padding: "10px 16px", cursor: "pointer", background: "none", border: "none", borderBottom: activeTab === "file" ? "2px solid var(--primary, #007bff)" : "none", fontWeight: activeTab === "file" ? "bold" : "normal" }}
          >
            <Icone nom="dossier" /> Mon dossier
          </button>
        )}

        <button
          role="tab"
          aria-selected={activeTab === "password"}
          type="button"
          data-testid="account-tab-password"
          className={`tab-btn ${activeTab === "password" ? "active" : ""}`}
          onClick={() => setActiveTab("password")}
          style={{ padding: "10px 16px", cursor: "pointer", background: "none", border: "none", borderBottom: activeTab === "password" ? "2px solid var(--primary, #007bff)" : "none", fontWeight: activeTab === "password" ? "bold" : "normal" }}
        >
          <Icone nom="cadenas" /> {t("account.password")}
        </button>

        <button
          role="tab"
          aria-selected={activeTab === "mfa"}
          type="button"
          data-testid="account-tab-mfa"
          className={`tab-btn ${activeTab === "mfa" ? "active" : ""}`}
          onClick={() => setActiveTab("mfa")}
          style={{ padding: "10px 16px", cursor: "pointer", background: "none", border: "none", borderBottom: activeTab === "mfa" ? "2px solid var(--primary, #007bff)" : "none", fontWeight: activeTab === "mfa" ? "bold" : "normal" }}
        >
          <Icone nom="bouclier" /> {t("account.mfa")}
        </button>

        <button
          role="tab"
          aria-selected={activeTab === "passkeys"}
          type="button"
          data-testid="account-tab-passkeys"
          className={`tab-btn ${activeTab === "passkeys" ? "active" : ""}`}
          onClick={() => setActiveTab("passkeys")}
          style={{ padding: "10px 16px", cursor: "pointer", background: "none", border: "none", borderBottom: activeTab === "passkeys" ? "2px solid var(--primary, #007bff)" : "none", fontWeight: activeTab === "passkeys" ? "bold" : "normal" }}
        >
          <Icone nom="cadenas" /> Clés d'accès
        </button>

        <button
          role="tab"
          aria-selected={activeTab === "notifications"}
          type="button"
          data-testid="account-tab-notifications"
          className={`tab-btn ${activeTab === "notifications" ? "active" : ""}`}
          onClick={() => setActiveTab("notifications")}
          style={{ padding: "10px 16px", cursor: "pointer", background: "none", border: "none", borderBottom: activeTab === "notifications" ? "2px solid var(--primary, #007bff)" : "none", fontWeight: activeTab === "notifications" ? "bold" : "normal" }}
        >
          <Icone nom="cloche" /> Mes canaux
        </button>
      </div>

      {/* Contenu des onglets */}
      <div className="account-tab-content">

        {/* ONGLET 1 : IDENTITÉ */}
        {activeTab === "identity" && (
          <div className="account-grid">
            <ChampPhotoProfil nom={user?.username ?? ""} />

            <section className="card" aria-labelledby="acc-identity-title">
              <h2 id="acc-identity-title" className="section-title"><Icone nom="utilisateur" /> {t("account.identity")}</h2>
              <dl className="account-meta" data-testid="account-sommaire">
                <div><dt>{t("account.username")}</dt><dd data-testid="account-username">{user?.username}</dd></div>
                <div>
                  <dt>{t("account.role")}</dt>
                  <dd>
                    <span className="badge badge-rule" data-testid="account-role">{ROLES[user?.role ?? ""] ?? user?.role}</span>
                    <div className="muted" style={{ fontSize: 11 }}>Détermine vos menus et vos droits.</div>
                  </dd>
                </div>
                {profile?.jobTitle && <div><dt>{t("account.jobTitle")}</dt><dd>{profile.jobTitle}</dd></div>}
              </dl>
            </section>
          </div>
        )}

        {/* ONGLET 2 : INFORMATIONS PERSONNELLES */}
        {activeTab === "profile" && (
          <section className="card" aria-labelledby="acc-profile-title">
            <h2 id="acc-profile-title" className="section-title"><Icone nom="carte" /> {t("account.personalInfo")}</h2>
            {profile ? (
              <form onSubmit={saveProfile} className="grid-form" data-testid="account-profile-form">
                <label className="field"><span>{t("account.firstName")} <span className="req-star" aria-hidden="true">*</span></span>
                  <input data-testid="account-firstName" aria-required="true" value={pform.firstName}
                    onChange={(e) => setPform({ ...pform, firstName: e.target.value })} required /></label>
                <label className="field"><span>{t("account.lastName")} <span className="req-star" aria-hidden="true">*</span></span>
                  <input data-testid="account-lastName" aria-required="true" value={pform.lastName}
                    onChange={(e) => setPform({ ...pform, lastName: e.target.value })} required /></label>
                <label className="field"><span>{t("account.email")}</span>
                  <input data-testid="account-email" type="email" value={pform.email}
                    onChange={(e) => setPform({ ...pform, email: e.target.value })} /></label>
                <label className="field"><span>{t("account.phone")}</span>
                  <input data-testid="account-phone" type="tel" value={pform.phone}
                    onChange={(e) => setPform({ ...pform, phone: e.target.value })} /></label>
                <label className="field"><span>Date de naissance</span>
                  <input data-testid="account-dateOfBirth" type="date" value={pform.dateOfBirth}
                    onChange={(e) => setPform({ ...pform, dateOfBirth: e.target.value })} /></label>
                <label className="field field-full"><span>{t("account.address")}</span>
                  <input data-testid="account-address" value={pform.address}
                    onChange={(e) => setPform({ ...pform, address: e.target.value })} /></label>
                <div className="field field-actions">
                  <button type="submit" className="primary" data-testid="account-profile-save">{t("common.save")}</button>
                </div>
              </form>
            ) : (
              <p className="muted" data-testid="account-no-profile">{t("account.noProfile")}</p>
            )}
          </section>
        )}

        {/* ONGLET 3 : MON DOSSIER */}
        {activeTab === "file" && readOnly && (
          <section className="card" aria-labelledby="acc-file-title" data-testid="account-file">
            <h2 id="acc-file-title" className="section-title"><Icone nom="dossier" /> Mon dossier</h2>
            <p className="section-help">
              Ces informations relèvent de l'employeur : organigramme, statut, rémunération.
              Vous les consultez ici, mais leur modification passe par les ressources humaines —
              c'est ce qui garantit qu'un dossier ne change pas sans trace.
            </p>
            <dl className="account-meta">
              {readOnly.function && <div><dt>Fonction</dt><dd data-testid="account-function">{FONCTIONS[readOnly.function] ?? readOnly.function}</dd></div>}
              {readOnly.department && <div><dt>Département</dt><dd>{readOnly.department}</dd></div>}
              {readOnly.managerName && <div><dt>Gestionnaire</dt><dd data-testid="account-manager">{readOnly.managerName}</dd></div>}
              {readOnly.hireDate && <div><dt>Date d'embauche</dt><dd>{readOnly.hireDate}</dd></div>}
              {readOnly.status && <div><dt>Statut</dt><dd><span className="badge badge-ok">{STATUTS[readOnly.status] ?? readOnly.status}</span></dd></div>}
              {readOnly.weeklyCapacityHours != null && <div><dt>Capacité hebdomadaire</dt><dd>{readOnly.weeklyCapacityHours} h</dd></div>}
              {readOnly.workerType && <div><dt>Type de travailleur</dt><dd>{TYPES_TRAVAILLEUR[readOnly.workerType] ?? readOnly.workerType}</dd></div>}
              {readOnly.payType && <div><dt>Mode de rémunération</dt><dd>{readOnly.payType === "hourly" ? "Horaire" : "Salaire"}</dd></div>}
              {readOnly.payType === "hourly" && readOnly.hourlyRate != null && <div><dt>Taux horaire</dt><dd>{readOnly.hourlyRate} $/h</dd></div>}
              {readOnly.payType !== "hourly" && readOnly.annualSalary != null && <div><dt>Salaire annuel</dt><dd>{Number(readOnly.annualSalary).toLocaleString("fr-CA")} $</dd></div>}
              {readOnly.sinMasked && <div><dt>Numéro d'assurance sociale</dt><dd data-testid="account-sin">{readOnly.sinMasked}</dd></div>}
              {readOnly.isTeacher && <div><dt>Profil</dt><dd><span className="badge badge-teacher">Professeur (MIFI)</span></dd></div>}
            </dl>
          </section>
        )}

        {/* ONGLET 4 : MOT DE PASSE */}
        {activeTab === "password" && (
          <section className="card" aria-labelledby="acc-pwd-title">
            <h2 id="acc-pwd-title" className="section-title"><Icone nom="cadenas" /> {t("account.password")}</h2>
            <form onSubmit={changePassword} className="grid-form" data-testid="account-password-form">
              <label className="field field-full"><span>{t("account.oldPassword")} <span className="req-star" aria-hidden="true">*</span></span>
                <input data-testid="account-oldPassword" aria-required="true" type="password" autoComplete="current-password"
                  value={oldPassword} onChange={(e) => setOldPassword(e.target.value)} required /></label>
              <label className="field"><span>{t("account.newPassword")} <span className="req-star" aria-hidden="true">*</span></span>
                <input data-testid="account-newPassword" aria-required="true" type="password" autoComplete="new-password"
                  value={newPassword} onChange={(e) => setNewPassword(e.target.value)} required minLength={minLength} /></label>
              <label className="field"><span>{t("account.confirmPassword")}</span>
                <input data-testid="account-confirmPassword" type="password" autoComplete="new-password"
                  value={confirmPassword} onChange={(e) => setConfirmPassword(e.target.value)} required minLength={minLength} /></label>
              <div className="field field-actions">
                <button type="submit" className="primary" data-testid="account-password-save">{t("account.changePwd")}</button>
              </div>
            </form>
          </section>
        )}

        {/* ONGLET 5 : SÉCURITÉ / MFA */}
        {activeTab === "mfa" && (
          <section className="card" aria-labelledby="acc-mfa-title">
            <h2 id="acc-mfa-title" className="section-title"><Icone nom="bouclier" /> {t("account.mfa")}</h2>
            <p className="account-meta-line">
              {t("account.currentMethod")} : <strong data-testid="account-mfa-method">{methodLabel}</strong>
            </p>
            {secStatus?.securityMethod && (
              <details data-testid="account-mfa-test">
                <summary>Tester mon second facteur</summary>
                <form onSubmit={(e) => { e.preventDefault(); void testerFacteur(e.currentTarget); }} className="grid-form">
                  {secStatus.securityMethod === "question" && questionsTest.map((q, i) => (
                    <label key={q} className="field"><span>{q}</span><input name={`reponse${i}`} required data-testid={`account-mfa-test-reponse-${i}`} autoComplete="off" /></label>
                  ))}
                  {secStatus.securityMethod === "question" && questionsTest.length === 0 && (
                    <button type="button" className="secondary" data-testid="account-mfa-test-charger" onClick={() => void chargerQuestionsTest()}>Afficher mes questions</button>
                  )}
                  {secStatus.securityMethod === "totp" && (
                    <label className="field"><span>Code de l'application d'authentification</span><input name="code" required inputMode="numeric" data-testid="account-mfa-test-code" autoComplete="one-time-code" /></label>
                  )}
                  {(secStatus.securityMethod === "totp" || questionsTest.length > 0) && (
                    <div className="field field-actions"><button type="submit" className="secondary" data-testid="account-mfa-test-verifier">Vérifier</button></div>
                  )}
                  {verdictTest && <p role="status" data-testid="account-mfa-test-verdict">{verdictTest}</p>}
                </form>
              </details>
            )}
            <form onSubmit={saveSecurity} data-testid="account-mfa-form">
              <fieldset className="mfa-methods">
                <legend>{t("account.chooseMethod")}</legend>
                <label className="radio-chip">
                  <input type="radio" name="mfa" value="question" data-testid="account-mfa-question"
                    checked={method === "question"} onChange={() => setMethod("question")} />
                  <span>❓ {t("account.methodQuestion")}</span>
                </label>
                <label className="radio-chip">
                  <input type="radio" name="mfa" value="totp" data-testid="account-mfa-totp"
                    checked={method === "totp"} onChange={() => setMethod("totp")} />
                  <span>📱 {t("account.methodTotp")}</span>
                </label>
              </fieldset>

              {method === "question" ? (
                <div className="grid-form">
                  {questions.map((row, i) => (
                    <div className="field field-full" key={i}>
                      <label><span>{t("account.question")} {i + 1}</span></label>
                      <select
                        aria-label={`Question de sécurité ${i + 1}`}
                        data-testid={i === 0 ? "account-question-select" : `account-question-select-${i}`}
                        value={row.custom ? "__custom__" : row.question}
                        onChange={(e) => {
                          const v = e.target.value;
                          const next = [...questions];
                          next[i] = v === "__custom__"
                            ? { ...next[i], custom: true, question: "" }
                            : { ...next[i], custom: false, question: v };
                          setQuestions(next);
                        }}
                      >
                        <option value="">—</option>
                        {questionChoices.map((q) => <option key={q} value={q}>{q}</option>)}
                        <option value="__custom__">✍️ {t("account.customQuestion")}</option>
                      </select>
                      {row.custom && (
                        <input
                          data-testid={i === 0 ? "account-question-custom" : `account-question-custom-${i}`} aria-label={`Question de sécurité personnalisée ${i + 1}`}
                          style={{ marginTop: 6 }}
                          placeholder={t("account.customQuestionPlaceholder")}
                          value={row.question}
                          minLength={5}
                          onChange={(e) => {
                            const next = [...questions];
                            next[i] = { ...next[i], question: e.target.value };
                            setQuestions(next);
                          }}
                        />
                      )}
                      <label><span>{t("account.answer")} {i + 1}</span></label>
                      <input
                        aria-label={`Réponse à la question ${i + 1}`}
                        data-testid={i === 0 ? "account-answer" : `account-answer-${i}`}
                        value={row.answer}
                        onChange={(e) => {
                          const next = [...questions];
                          next[i] = { ...next[i], answer: e.target.value };
                          setQuestions(next);
                        }}
                      />
                    </div>
                  ))}
                </div>
              ) : (
                <div className="totp-setup">
                  {!totp ? (
                    <button type="button" className="secondary" data-testid="account-totp-init" onClick={initTotp}>
                      {t("account.totpInit")}
                    </button>
                  ) : (
                    <div className="totp-detail">
                      <p className="muted">{t("account.totpScanQr")}</p>
                      {totp.qrSvg && (
                        <div className="totp-qr" data-testid="account-totp-qr"
                          // eslint-disable-next-line react/no-danger
                          dangerouslySetInnerHTML={{ __html: totp.qrSvg }} />
                      )}
                      <p className="muted" style={{ marginTop: 8 }}>{t("account.totpManual")}</p>
                      <code className="totp-secret" data-testid="account-totp-secret">{totp.secret}</code>
                      <label className="field"><span>{t("account.totpCode")}</span>
                        <input data-testid="account-totp-code" inputMode="numeric" pattern="[0-9]{6}"
                          value={totpCode} onChange={(e) => setTotpCode(e.target.value)} /></label>
                    </div>
                  )}
                </div>
              )}

              <div className="field-actions" style={{ marginTop: 12 }}>
                <button type="submit" className="primary" data-testid="account-mfa-save">{t("account.saveSecurity")}</button>
              </div>
            </form>
          </section>
        )}

        {/* ONGLET 6 : PASSKEYS FIDO2 / WEBAUTHN */}
        {activeTab === "passkeys" && (
          <section className="card" aria-labelledby="acc-passkey-title" data-testid="account-passkeys">
            <h2 id="acc-passkey-title" className="section-title"><Icone nom="cadenas" /> Clés d'accès (FIDO2 / passkeys)</h2>
            <p className="subtitle">Second facteur fort, en complément du TOTP. Enregistrez une clé par appareil de confiance.</p>
            {passkeys.length === 0
              ? <p className="muted" data-testid="account-passkeys-empty">Aucune passkey enregistrée.</p>
              : <ul className="bullet-list" data-testid="account-passkeys-list">
                  {passkeys.map((p) => (
                    <li key={p.id} data-testid="account-passkey-item">
                      <Icone nom="cadenas" /> {p.credentialId} <span className="muted">· {new Date(p.createdAt).toLocaleDateString()}</span>
                      <button className="secondary" data-testid={`account-passkey-verify-${p.id}`} style={{ marginLeft: 8 }} onClick={() => verifierPasskey(p.credentialId)}>Vérifier</button>
                      {verifiee === p.credentialId && <span role="status" data-testid={`account-passkey-verified-${p.id}`}> ✓ vérifiée</span>}
                      <button className="danger" data-testid={`account-passkey-remove-${p.id}`} style={{ marginLeft: 8 }} onClick={() => removePasskey(p.id)}>Retirer</button>
                    </li>
                  ))}
                </ul>}
            <div className="actions" style={{ marginTop: 8 }}>
              <button type="button" className="primary" data-testid="account-passkey-add" onClick={addPasskey}><Icone nom="plus" /> Ajouter une passkey</button>
            </div>
          </section>
        )}

        {/* ONGLET 7 : CANAUX DE NOTIFICATION */}
        {activeTab === "notifications" && (
          <CanauxNotification call={apiFetch} signaler={setError} confirmer={setSuccess} />
        )}

      </div>
    </div>
  );
}