/**
 * Écran "premier login" : impose le changement du mot de passe temporaire,
 * puis force la configuration d'une couche de sécurité (question secrète OU MFA TOTP).
 *
 * Tant que ces deux étapes ne sont pas faites, on reste sur cet écran.
 */
import { useEffect, useState } from "react";
import { Message, EnTeteEcran } from "./composants";
import { useAuth } from "./AuthContext";
import { useT } from "./i18n";
import PasswordRequirements, { isPasswordStrong, usePasswordPolicy } from "./PasswordRequirements";

type Step = "password" | "security";
const MIN_SECURITY_QUESTIONS = 3;

export default function FirstLogin() {
  const { user, apiFetch, refreshUser, logout } = useAuth();
  const { t } = useT();
  // Longueur minimale exigée : lue du serveur, jamais recopiée.
  const minLength = usePasswordPolicy();
  const [step, setStep] = useState<Step>(user?.mustChangePassword ? "password" : "security");
  const [error, setError] = useState("");
  const [busy, setBusy] = useState(false);

  // Étape 1 : mot de passe
  const [pwd, setPwd] = useState("");
  const [pwd2, setPwd2] = useState("");

  // Étape 2 : choix
  const [method, setMethod] = useState<"question" | "totp">("question");
  const [questions, setQuestions] = useState<Array<{ question: string; answer: string; custom?: boolean }>>(
    Array.from({ length: MIN_SECURITY_QUESTIONS }, () => ({ question: "", answer: "", custom: false })),
  );
  const [questionChoices, setQuestionChoices] = useState<string[]>([]);
  const [totp, setTotp] = useState<{ secret: string; otpauthUrl: string; qrSvg?: string } | null>(null);
  const [totpCode, setTotpCode] = useState("");

  useEffect(() => {
    if (!user?.mustChangePassword) setStep("security");
  }, [user?.mustChangePassword]);

  useEffect(() => {
    apiFetch("/api/auth/security/questions")
      .then((r) => (r.ok ? r.json() : null))
      .then((j) => {
        if (j?.questions?.length) {
          const choices: string[] = j.questions;
          setQuestionChoices(choices);
          setQuestions((prev) => prev.map((row, i) => ({
            question: row.question || choices[i % choices.length] || "",
            answer: row.answer,
          })));
        }
      })
      .catch(() => { /* ignore */ });
  }, [apiFetch]);

  async function changePassword(e: React.FormEvent) {
    e.preventDefault();
    setError("");
    if (!isPasswordStrong(pwd, user?.username)) { setError("Mot de passe trop faible : respectez toutes les exigences ci-dessous."); return; }
    if (pwd !== pwd2) { setError(t("firstLogin.errPwdMismatch")); return; }
    setBusy(true);
    const r = await apiFetch("/api/auth/first-login/change-password", {
      method: "POST", body: JSON.stringify({ newPassword: pwd }),
    });
    setBusy(false);
    if (!r.ok) {
      const j = await r.json().catch(() => ({}));
      setError(j.errors?.join(", ") || "Erreur");
      return;
    }
    await refreshUser();
    setStep("security");
  }

  async function initTotp() {
    setError("");
    const r = await apiFetch("/api/auth/security/totp/init", { method: "POST" });
    if (!r.ok) { setError("Init TOTP impossible"); return; }
    setTotp(await r.json());
  }

  async function setupSecurity(e: React.FormEvent) {
    e.preventDefault();
    setError("");
    let body: any;
    if (method === "question") {
      if (questions.length < MIN_SECURITY_QUESTIONS) {
        setError(`Au moins ${MIN_SECURITY_QUESTIONS} questions sont requises.`);
        return;
      }
      for (const q of questions) {
        if (q.question.trim().length < 5) { setError(t("firstLogin.errQuestion")); return; }
        if (q.answer.trim().length < 2) { setError(t("firstLogin.errAnswer")); return; }
      }
      body = { method: "question", questions };
    } else {
      if (!totp) { setError(t("firstLogin.errTotpInit")); return; }
      if (!/^\d{6}$/.test(totpCode)) { setError(t("firstLogin.errTotpCode")); return; }
      body = { method: "totp", code: totpCode };
    }
    setBusy(true);
    const r = await apiFetch("/api/auth/security/setup", {
      method: "POST", body: JSON.stringify(body),
    });
    setBusy(false);
    if (!r.ok) {
      const j = await r.json().catch(() => ({}));
      setError(j.errors?.join(", ") || "Erreur");
      return;
    }
    await refreshUser();
  }

  return (
    <div style={{ minHeight: "100vh", display: "grid", placeItems: "center", background: "var(--bg)", padding: 20 }}>
      <div className="card" style={{ width: "100%", maxWidth: 480 }} data-testid="first-login">
        <EnTeteEcran icone="cadenas" titre={<>{t("firstLogin.title")}</>} description={<>{step === "password" ? t("firstLogin.stepPwd") : t("firstLogin.stepSecurity")}</>} />
        {error && <Message variante="erreur" testId="first-login-error">{error}</Message>}

        {step === "password" ? (
          <form onSubmit={changePassword} data-testid="first-login-pwd-form">
            <div style={{ marginBottom: 12 }}>
              <label>{t("firstLogin.newPassword")}</label>
              <input type="password" data-testid="first-login-newpwd" aria-label="Nouveau mot de passe"
                value={pwd} onChange={(e) => setPwd(e.target.value)} required minLength={minLength} />
            </div>
            <div style={{ marginBottom: 8 }}>
              <label>{t("firstLogin.confirmPassword")}</label>
              <input type="password" data-testid="first-login-newpwd2" aria-label="Confirmer le nouveau mot de passe"
                value={pwd2} onChange={(e) => setPwd2(e.target.value)} required minLength={minLength} />
            </div>
            <PasswordRequirements password={pwd} username={user?.username} />
            <button type="submit" className="primary" data-testid="first-login-pwd-submit"
              disabled={busy || !isPasswordStrong(pwd, user?.username)} style={{ width: "100%", marginTop: 8 }}>
              {busy ? "…" : t("firstLogin.changePwd")}
            </button>
          </form>
        ) : (
          <form onSubmit={setupSecurity} data-testid="first-login-sec-form">
            <div style={{ display: "flex", gap: 12, marginBottom: 16 }}>
              <label>
                <input type="radio" name="m" value="question"
                  checked={method === "question"}
                  data-testid="sec-method-question"
                  onChange={() => setMethod("question")} />
                {" "}{t("firstLogin.methodQuestion")}
              </label>
              <label>
                <input type="radio" name="m" value="totp"
                  checked={method === "totp"}
                  data-testid="sec-method-totp"
                  onChange={() => setMethod("totp")} />
                {" "}{t("firstLogin.methodTotp")}
              </label>
            </div>

            {method === "question" ? (
              <>
                {questions.map((row, i) => (
                  <div key={i} style={{ marginBottom: 12 }}>
                    <label>{t("firstLogin.question")} {i + 1}</label>
                    <select
                      aria-label={`Question de sécurité ${i + 1}`}
                  data-testid={i === 0 ? "sec-question-select" : `sec-question-select-${i}`}
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
                      <option value="__custom__">✍️ {t("firstLogin.customQuestion")}</option>
                    </select>
                    {row.custom && (
                      <input
                        data-testid={i === 0 ? "sec-question-custom" : `sec-question-custom-${i}`} aria-label={`Question de sécurité personnalisée ${i + 1}`}
                        style={{ marginTop: 6 }}
                        placeholder={t("firstLogin.customQuestionPlaceholder")}
                        value={row.question}
                        minLength={5}
                        onChange={(e) => {
                          const next = [...questions];
                          next[i] = { ...next[i], question: e.target.value };
                          setQuestions(next);
                        }}
                      />
                    )}
                    <label style={{ marginTop: 8 }}>{t("firstLogin.answer")} {i + 1}</label>
                    <input
                      aria-label={`Réponse à la question ${i + 1}`}
                  data-testid={i === 0 ? "sec-answer" : `sec-answer-${i}`}
                      value={row.answer}
                      onChange={(e) => {
                        const next = [...questions];
                        next[i] = { ...next[i], answer: e.target.value };
                        setQuestions(next);
                      }}
                      required
                    />
                  </div>
                ))}
              </>
            ) : (
              <>
                {!totp ? (
                  <button type="button" className="secondary" data-testid="sec-totp-init"
                    onClick={initTotp} style={{ marginBottom: 12 }}>
                    {t("firstLogin.totpGenerate")}
                  </button>
                ) : (
                  <>
                    <div className="muted" style={{ marginBottom: 8 }}>
                      {t("account.totpScanQr")}
                    </div>
                    {totp.qrSvg && (
                      <div className="totp-qr" data-testid="sec-totp-qr"
                        // eslint-disable-next-line react/no-danger
                        dangerouslySetInnerHTML={{ __html: totp.qrSvg }} />
                    )}
                    <div className="muted" style={{ margin: "8px 0 4px" }}>{t("account.totpManual")}</div>
                    <code data-testid="sec-totp-secret" style={{
                      display: "block", padding: 8, background: "#f0f0f0",
                      borderRadius: 4, marginBottom: 12, wordBreak: "break-all",
                    }}>{totp.secret}</code>
                    <div style={{ marginBottom: 16 }}>
                      <label>{t("firstLogin.totpCode")}</label>
                      <input data-testid="sec-totp-code" aria-label="Code de vérification à six chiffres" value={totpCode}
                        onChange={(e) => setTotpCode(e.target.value)}
                        placeholder="123456" maxLength={6} required />
                    </div>
                  </>
                )}
              </>
            )}

            <button type="submit" className="primary" data-testid="first-login-sec-submit"
              disabled={busy} style={{ width: "100%" }}>
              {busy ? "…" : t("firstLogin.activate")}
            </button>
          </form>
        )}

        <button className="secondary" onClick={logout} style={{ marginTop: 16, width: "100%" }}>
          {t("common.logout")}
        </button>
      </div>
    </div>
  );
}
