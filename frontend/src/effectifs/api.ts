/**
 * **Accès à l'API des effectifs** pour les écrans : une lecture et une écriture qui remontent le
 * motif du serveur. Un « Erreur 422 » nu ne se corrige pas ; « une modification ne peut pas être
 * rétroactive (2027-01-10 < 2027-01-11) » se corrige.
 */
import { useCallback, useState } from "react";
import { useAuth } from "../AuthContext";

export function useApiEffectifs() {
  const { apiFetch } = useAuth();
  const [erreur, setErreur] = useState("");
  const [succes, setSucces] = useState("");

  const lire = useCallback(async <T,>(chemin: string): Promise<T | null> => {
    const r = await apiFetch(chemin);
    if (!r.ok) {
      const j = await r.json().catch(() => ({}));
      setErreur(j.errors?.join(", ") || `Erreur ${r.status}`);
      return null;
    }
    return r.json();
  }, [apiFetch]);

  /** Écrit ; renvoie le corps en cas de succès, `null` sinon (le motif est alors affiché). */
  const envoyer = useCallback(async <T,>(chemin: string, corps: unknown, methode: "POST" | "PUT" | "PATCH" = "POST"): Promise<T | null> => {
    setErreur("");
    const r = await apiFetch(chemin, { method: methode, headers: { "Content-Type": "application/json" }, body: JSON.stringify(corps) });
    const j = await r.json().catch(() => ({}));
    if (!r.ok) {
      setErreur(j.errors?.join(", ") || `Erreur ${r.status}`);
      return null;
    }
    return j as T;
  }, [apiFetch]);

  /**
   * Écrit et rend la réponse entière, succès ou refus : un refus par règle (409) porte son
   * verdict — les motifs à afficher et la possibilité de forcer (E-GN-11).
   */
  const envoyerBrut = useCallback(async (chemin: string, corps: unknown, methode: "POST" | "PUT" | "PATCH" = "POST"): Promise<{ ok: boolean; statut: number; corps: any }> => {
    setErreur("");
    const r = await apiFetch(chemin, { method: methode, headers: { "Content-Type": "application/json" }, body: JSON.stringify(corps) });
    const j = await r.json().catch(() => ({}));
    if (!r.ok) setErreur(j.errors?.join(", ") || `Erreur ${r.status}`);
    return { ok: r.ok, statut: r.status, corps: j };
  }, [apiFetch]);

  /**
   * Supprime ; rend le corps en cas de succès, `null` sinon (le motif est alors affiché).
   *
   * Le corps est rendu plutôt qu'un booléen : un retrait répond ce qu'il a retiré, et l'écran
   * s'en sert pour le dire à l'utilisateur.
   */
  const supprimer = useCallback(async <T,>(chemin: string): Promise<T | null> => {
    setErreur("");
    const r = await apiFetch(chemin, { method: "DELETE" });
    const j = await r.json().catch(() => ({}));
    if (!r.ok) {
      setErreur(j.errors?.join(", ") || `Erreur ${r.status}`);
      return null;
    }
    return j as T;
  }, [apiFetch]);

  return { lire, envoyer, envoyerBrut, supprimer, erreur, setErreur, succes, setSucces, apiFetch };
}

/** « TEMPS_PARTIEL_REGULIER » → « Temps partiel regulier » : un comportement se lit, il ne s'épelle pas. */
export function lisible(code: string): string {
  const t = code.toLowerCase().replace(/_/g, " ");
  return t.charAt(0).toUpperCase() + t.slice(1);
}

export const aujourdhui = (): string => new Date().toISOString().slice(0, 10);

/** 1080 → « 18:00 » ; 1440 → « 24:00 ». Les heures voyagent en minutes depuis minuit. */
export function heure(minutes: number): string {
  return `${String(Math.floor(minutes / 60)).padStart(2, "0")}:${String(minutes % 60).padStart(2, "0")}`;
}

/** « 18:00 » → 1080 ; « 00:00 » comme fin vaut 1440 (minuit en fin de quart). */
export function minutes(hhmm: string, commeFin = false): number {
  const [h, m] = hhmm.split(":").map(Number);
  const total = (h || 0) * 60 + (m || 0);
  return commeFin && total === 0 ? 1440 : total;
}
