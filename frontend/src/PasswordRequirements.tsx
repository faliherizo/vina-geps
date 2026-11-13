/**
 * Liste d'**exigences de mot de passe élevé** affichée en temps réel (sécurité renforcée).
 * Reflète la politique du backend (`auth/passwordPolicy.ts`). Réutilisable sur les formulaires
 * de premier accès, de changement de mot de passe et de création de compte.
 */
import { useEffect, useState } from "react";

/**
 * Longueur minimale **par défaut**, utilisée tant que le serveur n'a pas répondu.
 * L'autorité reste `GET /api/auth/password-policy` : voir `usePasswordPolicy`.
 */
export const PASSWORD_MIN_LENGTH = 12;

/**
 * Longueur minimale exigée, **lue du serveur**.
 *
 * La politique est appliquée côté serveur ; la recopier ici la fait diverger au premier
 * durcissement — l'écran accepterait alors un mot de passe que l'API refuse, sans que
 * l'utilisateur comprenne pourquoi. La constante ci-dessus n'est qu'un repli le temps de
 * l'aller-retour, et en cas d'échec réseau.
 *
 * La route est publique : elle doit l'être, puisque le premier écran où l'on choisit un mot
 * de passe précède l'authentification.
 */
export function usePasswordPolicy(): number {
  const [minLength, setMinLength] = useState(PASSWORD_MIN_LENGTH);
  useEffect(() => {
    let annule = false;
    fetch("/api/auth/password-policy")
      .then((r) => (r.ok ? r.json() : null))
      .then((p) => {
        if (!annule && p && Number.isFinite(p.minLength) && p.minLength > 0) setMinLength(p.minLength);
      })
      .catch(() => { /* repli sur la valeur par défaut */ });
    return () => { annule = true; };
  }, []);
  return minLength;
}

export interface PwRule { key: string; label: string; ok: boolean; }

/** Évalue les règles pour un mot de passe (et un nom d'utilisateur optionnel). */
export function evaluatePassword(pw: string, username?: string): PwRule[] {
  const u = (username ?? "").trim().toLowerCase();
  return [
    { key: "len", label: `Au moins ${PASSWORD_MIN_LENGTH} caractères`, ok: pw.length >= PASSWORD_MIN_LENGTH },
    { key: "lower", label: "Une lettre minuscule", ok: /[a-z]/.test(pw) },
    { key: "upper", label: "Une lettre majuscule", ok: /[A-Z]/.test(pw) },
    { key: "digit", label: "Un chiffre", ok: /\d/.test(pw) },
    { key: "special", label: "Un caractère spécial", ok: /[^A-Za-z0-9]/.test(pw) },
    { key: "space", label: "Aucun espace", ok: pw.length > 0 && !/\s/.test(pw) },
    { key: "uname", label: "Ne contient pas le nom d'utilisateur", ok: !(u.length >= 3 && pw.toLowerCase().includes(u)) },
  ];
}

/** Vrai si toutes les règles sont satisfaites. */
export function isPasswordStrong(pw: string, username?: string): boolean {
  return evaluatePassword(pw, username).every((r) => r.ok);
}

/**
 * Le verdict du **serveur** sur le mot de passe saisi (`POST /api/auth/password-policy/check`),
 * demandé après une courte pause de frappe : la liste locale guide, le serveur tranche — c'est
 * la même fonction qui refusera le mot de passe à l'enregistrement.
 */
export function useServerPasswordIssues(password: string, username?: string): string[] | null {
  const [issues, setIssues] = useState<string[] | null>(null);
  useEffect(() => {
    if (!password) { setIssues(null); return; }
    let annule = false;
    const minuteur = setTimeout(() => {
      fetch("/api/auth/password-policy/check", { method: "POST", headers: { "Content-Type": "application/json" }, body: JSON.stringify({ password, username }) })
        .then((r) => (r.ok ? r.json() : null))
        .then((j) => { if (!annule && j && Array.isArray(j.issues)) setIssues(j.issues); })
        .catch(() => { /* la liste locale reste ; l'enregistrement fera foi */ });
    }, 350);
    return () => { annule = true; clearTimeout(minuteur); };
  }, [password, username]);
  return issues;
}

export default function PasswordRequirements({ password, username }: { password: string; username?: string }) {
  const rules = evaluatePassword(password, username);
  const serveur = useServerPasswordIssues(password, username);
  return (
    <>
      <ul className="password-rules" data-testid="password-rules" style={{ listStyle: "none", padding: 0, margin: "6px 0", fontSize: 13 }}>
        {rules.map((r) => (
          <li key={r.key} data-testid={`pwrule-${r.key}`} data-ok={r.ok}
            style={{ color: r.ok ? "var(--success, #15803d)" : "var(--muted, #6b7280)", display: "flex", gap: 6, alignItems: "center" }}>
            <span aria-hidden>{r.ok ? "✓" : "○"}</span> {r.label}
          </li>
        ))}
      </ul>
      {serveur && (
        <p role="status" data-testid="password-server-verdict" style={{ fontSize: 13, margin: "0 0 6px" }}>
          {serveur.length === 0 ? "Mot de passe accepté par le serveur." : `Le serveur refuse encore : ${serveur.join(" ; ")}`}
        </p>
      )}
    </>
  );
}
