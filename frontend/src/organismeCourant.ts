/**
 * **L'organisme courant** et l'appel d'API qui le porte.
 *
 * Sortis de l'écran du module Enseignement, où ils vivaient par hasard d'histoire : tout écran
 * d'un organisme en a besoin, et les laisser là obligeait la messagerie à importer un module
 * qu'elle ne sert pas.
 */
import { useCallback, useEffect, useState } from "react";
import { useAuth } from "./AuthContext";

/** Un appel d'API qui porte l'organisme courant (`X-Tenant-Id`). */
export function useTenantApi(tenantId: string | null) {
  const { apiFetch } = useAuth();
  return useCallback((path: string, init: RequestInit = {}) => {
    const headers = new Headers(init.headers || {});
    if (tenantId) headers.set("X-Tenant-Id", tenantId);
    return apiFetch(path, { ...init, headers });
  }, [apiFetch, tenantId]);
}

/** L'organisme effectif : le sien, sinon l'établissement choisi par un administrateur du système. */
export function useCurrentTenant(): { tenantId: string | null; tenantName: string | null; country: string | null } {
  const { user, establishmentId } = useAuth();
  // Tenant effectif : le tenant propre, sinon l'établissement choisi par un admin système.
  const tenantId = user?.tenantId ?? establishmentId ?? null;
  const [tenantName, setTenantName] = useState<string | null>(null);
  const [country, setCountry] = useState<string | null>(null);
  useEffect(() => {
    if (!tenantId) { setTenantName(null); setCountry(null); return; }
    let cancelled = false;
    fetch("/api/auth/tenants")
      .then((r) => (r.ok ? r.json() : []))
      .then((list: Array<{ id: string; name: string; country?: string }>) => {
        if (cancelled) return;
        const t = list.find((x) => x.id === tenantId);
        setTenantName(t?.name ?? null);
        setCountry(t?.country ?? null);
        // La devise d'affichage (formatMoney) suit le pays de l'établissement.
        if (t?.country) { try { localStorage.setItem("hostCountry", t.country.toUpperCase()); } catch { /* ignore */ } }
      })
      .catch(() => { if (!cancelled) { setTenantName(null); setCountry(null); } });
    return () => { cancelled = true; };
  }, [tenantId]);
  return { tenantId, tenantName, country };
}

export type Msg = { setError: (s: string) => void; setSuccess: (s: string) => void };

/** Le corps d'une réponse réussie ; sinon l'erreur du serveur, transmise telle quelle. */
export async function handleApi(r: Response, onError: (s: string) => void): Promise<any | null> {
  if (r.ok) return r.status === 204 ? null : r.json().catch(() => null);
  const j = await r.json().catch(() => ({}));
  onError(j.errors?.join(", ") || `Erreur ${r.status}`);
  return null;
}
