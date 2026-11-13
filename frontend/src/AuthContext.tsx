/**
 * Contexte d'authentification basé exclusivement sur les cookies httpOnly.
 *
 * - Le cookie de session `session` est posé/effacé par le backend (httpOnly, inaccessible JS).
 * - Un cookie `csrf` lisible côté JS est utilisé en double-submit pour les mutations.
 * - `apiFetch` ajoute systématiquement `credentials: "include"` et l'en-tête `X-CSRF-Token`.
 * - Aucun token JWT n'est stocké côté navigateur : pas de `localStorage`, pas d'`Authorization: Bearer`.
 *   Les clients non-navigateur (mobile, CLI, scripts) peuvent toujours utiliser le token renvoyé
 *   par /api/auth/login en l'envoyant manuellement en Bearer — ce flux n'est pas utilisé par le web.
 */
import React, { createContext, useContext, useEffect, useState, useCallback } from "react";

export interface AuthUser {
  id: string;
  username: string;
  role: "superadmin" | "admin" | "manager" | "employee" | "rh" | "accountant" | "director";
  tenantId?: string | null;
  employeeId?: string | null;
  /**
   * Droits du compte, **calculés par le serveur** (`src/auth/accessPolicy.ts`).
   *
   * L'interface ne recopie plus de listes de rôles pour décider ce qu'elle affiche : une liste
   * recopiée diverge, et l'on obtient un écran ouvert dont tous les boutons échouent en 403.
   * Utiliser `useCapability` plutôt que de tester `user.role`.
   */
  capabilities?: string[];
  /**
   * **Le droit d'approuver**, calculé entièrement par le serveur (`payroll/scope.peutApprouver`).
   *
   * Il ne se déduit **pas** des capacités : approuver dépend aussi de l'organigramme — un chef
   * d'équipe au compte « employé » encadre réellement des personnes, et le serveur lui adresse
   * déjà leurs feuilles. Aucune liste de rôles, ni aucune capacité de rôle, ne sait cela.
   */
  peutApprouver?: boolean;
  mustChangePassword?: boolean;
  securitySetupRequired?: boolean;
  securityMethod?: "question" | "totp" | null;
  /** Session empruntée (ADR 0038) : l'administrateur qui agit sous cette identité, et l'échéance. */
  emprunt?: { id: string; emprunteur: { id: string; username: string }; expireLe: string } | null;
}

interface AuthCtx {
  user: AuthUser | null;
  loading: boolean;
  login: (
    username: string,
    password: string,
    mfa?: { totpCode?: string; securityAnswer?: string; securityAnswers?: string[] },
    tenantId?: string | null
  ) => Promise<{
    ok: boolean;
    error?: string;
    mfaRequired?: boolean;
    securityMethod?: "question" | "totp" | null;
    securityQuestion?: string | null;
    securityQuestions?: string[];
    /** Le login existe dans plusieurs établissements : il faut en désigner un. */
    tenantChoiceRequired?: boolean;
    tenants?: Array<{ id: string; name: string }>;
  }>;
  logout: () => Promise<void>;
  apiFetch: (path: string, init?: RequestInit) => Promise<Response>;
  refreshUser: () => Promise<void>;
  /** Établissement choisi par un admin système sans tenant propre (sinon = user.tenantId). */
  establishmentId: string | null;
  setEstablishment: (id: string | null) => void;
  /** Vrai si le compte est un admin/superadmin global (sans tenant) devant choisir un établissement. */
  needsEstablishment: boolean;
}

const Ctx = createContext<AuthCtx | null>(null);

function readCookie(name: string): string | null {
  const m = document.cookie.match(new RegExp("(?:^|; )" + name.replace(/[.$?*|{}()\[\]\\\/+^]/g, "\\$&") + "=([^;]*)"));
  return m ? decodeURIComponent(m[1]) : null;
}

/**
 * Patch global de `window.fetch` pour les appels `/api/*` :
 *  - force `credentials: "include"` (cookies httpOnly envoyés)
 *  - ajoute automatiquement l'en-tête `X-CSRF-Token` sur les méthodes mutantes
 *    en lisant le cookie `csrf` (double-submit).
 *
 * Ainsi, toutes les pages utilisant le `fetch` natif bénéficient du même contrat
 * que celles passant par `apiFetch`. Idempotent : on ne patche qu'une fois.
 */
/**
 * Tenant courant (établissement) du compte connecté, maintenu hors React pour que le patch
 * global de `fetch` puisse l'injecter en en-tête `X-Tenant-Id` sur les modules de base
 * (multi-tenant strict). Mis à jour par AuthProvider à chaque changement d'utilisateur.
 */
let currentTenantId: string | null = null;
export function setCurrentTenantId(id: string | null) { currentTenantId = id; }

function ensureFetchPatched() {
  const w = window as any;
  if (w.__fetchPatchedForCsrf) return;
  const original = window.fetch.bind(window);
  w.__fetchPatchedForCsrf = true;
  window.fetch = (input: RequestInfo | URL, init: RequestInit = {}) => {
    const url = typeof input === "string" ? input : (input as Request).url ?? String(input);
    const isApi = /^\/api\//.test(url) || /\/api\//.test(url);
    if (!isApi) return original(input, init);
    const headers = new Headers(init.headers || (input instanceof Request ? input.headers : undefined));
    const method = (init.method || (input instanceof Request ? input.method : "GET") || "GET").toUpperCase();
    if (!["GET", "HEAD", "OPTIONS"].includes(method)) {
      const csrf = readCookie("csrf");
      if (csrf && !headers.has("X-CSRF-Token")) headers.set("X-CSRF-Token", csrf);
    }
    // Multi-tenant strict : injecte l'établissement courant sur tous les appels API
    // (les modules de base /api/payroll l'exigent ; sans écraser un en-tête explicite).
    if (currentTenantId && !headers.has("X-Tenant-Id")) headers.set("X-Tenant-Id", currentTenantId);
    return original(input, { ...init, headers, credentials: init.credentials ?? "include" });
  };
}

export function AuthProvider({ children }: { children: React.ReactNode }) {
  const [user, setUser] = useState<AuthUser | null>(null);
  const [loading, setLoading] = useState<boolean>(true);
  // Établissement choisi par un admin système (compte global sans tenant). Persisté
  // pour survivre au rechargement. Ignoré dès que l'utilisateur a son propre tenant.
  const [establishmentId, setEstablishmentId] = useState<string | null>(() => {
    try { return localStorage.getItem("establishmentId"); } catch { return null; }
  });
  const setEstablishment = useCallback((id: string | null) => {
    setEstablishmentId(id);
    // Met à jour immédiatement le tenant courant (hors cycle React) pour que la toute
    // première requête après le choix porte bien l'en-tête X-Tenant-Id (évite une course
    // où une page se monte avant que l'effet de synchronisation n'ait tourné).
    if (id) setCurrentTenantId(id);
    try { id ? localStorage.setItem("establishmentId", id) : localStorage.removeItem("establishmentId"); } catch { /* ignore */ }
  }, []);

  // Patch global avant le premier rendu (et donc avant tout useEffect des pages enfants).
  ensureFetchPatched();

  const apiFetch = useCallback((path: string, init: RequestInit = {}) => {
    const headers = new Headers(init.headers || {});
    const method = (init.method || "GET").toUpperCase();
    if (init.body && !headers.has("Content-Type")) headers.set("Content-Type", "application/json");
    // CSRF double-submit pour les mutations (le patch global le fait déjà, mais on reste explicite).
    if (!["GET", "HEAD", "OPTIONS"].includes(method)) {
      const csrf = readCookie("csrf");
      if (csrf) headers.set("X-CSRF-Token", csrf);
    }
    // Pas de Bearer côté web : seul le cookie session httpOnly authentifie la requête.
    return fetch(path, { ...init, headers, credentials: "include" });
  }, []);

  // Tenant effectif : le tenant propre de l'utilisateur, sinon l'établissement choisi
  // par un admin système (compte global). Maintenu synchronisé pour l'injection
  // X-Tenant-Id du patch fetch global.
  const effectiveTenant = user?.tenantId ?? establishmentId ?? null;
  useEffect(() => { setCurrentTenantId(effectiveTenant); }, [effectiveTenant]);
  const needsEstablishment = !!user && !user.tenantId && (user.role === "admin" || user.role === "superadmin");

  // Au montage : tenter de récupérer l'utilisateur courant via /me (cookie httpOnly).
  useEffect(() => {
    apiFetch("/api/auth/me")
      .then(async (r) => {
        if (r.ok) setUser(await r.json());
        else setUser(null);
      })
      .catch(() => setUser(null))
      .finally(() => setLoading(false));
  }, [apiFetch]);

  async function login(
    username: string,
    password: string,
    mfa?: { totpCode?: string; securityAnswer?: string; securityAnswers?: string[] },
    tenantId?: string | null,
  ) {
    const headers: Record<string, string> = { "Content-Type": "application/json" };
    if (tenantId) headers["X-Tenant-Id"] = tenantId;
    const r = await fetch("/api/auth/login", {
      method: "POST",
      credentials: "include",
      headers,
      body: JSON.stringify({ username, password, tenantId: tenantId || undefined, ...(mfa || {}) }),
    });
    if (!r.ok) {
      const j = await r.json().catch(() => ({}));
      return { ok: false, error: j.errors?.join(", ") || "Identifiants invalides" };
    }
    const j = await r.json();
    // Cas rare et légitime : un même identifiant existe dans plusieurs établissements —
    // un professeur enseignant dans deux commissions scolaires, par exemple.
    if (j?.tenantChoiceRequired) {
      setUser(null);
      return {
        ok: false,
        tenantChoiceRequired: true,
        tenants: Array.isArray(j.tenants) ? j.tenants : [],
        error: "",
      };
    }
    if (j?.mfaRequired) {
      setUser(null);
      return {
        ok: false,
        mfaRequired: true,
        securityMethod: j.securityMethod ?? null,
        securityQuestion: j.securityQuestion ?? null,
        securityQuestions: Array.isArray(j.securityQuestions) ? j.securityQuestions : [],
        error: "Deuxième facteur requis",
      };
    }
    // Le backend pose les cookies session+csrf ; on ne stocke rien côté JS.
    setUser({
      ...j.user,
      mustChangePassword: !!j.mustChangePassword,
      securitySetupRequired: !!j.securitySetupRequired,
      securityMethod: j.securityMethod ?? null,
    });
    return { ok: true };
  }

  async function refreshUser() {
    const r = await apiFetch("/api/auth/me");
    if (r.ok) setUser(await r.json());
  }

  async function logout() {
    try {
      await apiFetch("/api/auth/logout", { method: "POST" });
    } catch { /* ignore */ }
    setUser(null);
    // Purge l'établissement choisi : sinon un X-Tenant-Id résiduel serait injecté sur la
    // prochaine connexion (ex. superadmin) et la cloisonnerait dans un tenant obsolète.
    setEstablishment(null);
    setCurrentTenantId(null);
  }

  return <Ctx.Provider value={{ user, loading, login, logout, apiFetch, refreshUser, establishmentId, setEstablishment, needsEstablishment }}>{children}</Ctx.Provider>;
}

export function useAuth() {
  const v = useContext(Ctx);
  if (!v) throw new Error("AuthProvider missing");
  return v;
}

/**
 * Le compte détient-il ce droit ?
 *
 * À préférer systématiquement à un test sur `user.role`. Le serveur reste l'autorité — cette
 * fonction ne sert qu'à ne pas proposer une action qui sera refusée, ce qui est une question
 * d'ergonomie, jamais de sécurité.
 *
 * Repli prudent : tant que les capacités ne sont pas connues (profil en cours de chargement,
 * serveur d'une version antérieure), on retombe sur le rôle `admin`, qui les détient toutes.
 */
export function useCapability(capability: string): boolean {
  const { user } = useAuth();
  if (!user) return false;
  if (user.capabilities) return user.capabilities.includes(capability);
  return user.role === "admin" || user.role === "superadmin";
}
