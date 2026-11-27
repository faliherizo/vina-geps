/**
 * **Les modules de l'établissement, lus sans confondre une panne passagère avec un refus.**
 *
 * Une réponse 503 (limitation de débit) ou 429, ou une coupure réseau, était lue comme « aucun module » : le menu
 * perdait toutes ses entrées de module jusqu'au prochain retour sur l'onglet, sans rien dire — la recette du
 * 27 novembre 2026 l'a vu, sous la limitation de nginx, pour un gestionnaire de Clincom privé de ses alertes.
 *
 * Une panne passagère est donc réessayée, avec un délai croissant ; seul un refus définitif (4xx autre que 429)
 * vaut « aucun module ». Si la panne dure, `null` : l'appelant garde ce qu'il savait déjà.
 */
export interface ModulesLus { modules: string[]; vuesMasquees: string[] }

/** Les délais avant chaque nouvel essai : 0,5 s, 1 s, 2 s. */
export const DELAIS_MS = [500, 1000, 2000];

const passager = (statut: number) => statut === 429 || statut >= 500;
const attendreParDefaut = (ms: number) => new Promise<void>((ok) => setTimeout(ok, ms));

export async function lireModules(
  apiFetch: (url: string, init?: RequestInit) => Promise<Response>,
  attendre: (ms: number) => Promise<void> = attendreParDefaut,
): Promise<ModulesLus | null> {
  for (let essai = 0; essai <= DELAIS_MS.length; essai++) {
    if (essai > 0) await attendre(DELAIS_MS[essai - 1]);
    let r: Response;
    try {
      r = await apiFetch("/api/saas/my-modules", { cache: "no-store" });
    } catch {
      continue;
    }
    if (passager(r.status)) continue;
    // Sans établissement (compte global) ou refusé : aucun menu de module — c'est une réponse, pas une panne.
    const j = r.ok ? await r.json().catch(() => null) : null;
    return {
      modules: Array.isArray(j?.modules) ? j.modules : [],
      vuesMasquees: Array.isArray(j?.vuesMasquees) ? j.vuesMasquees : [],
    };
  }
  return null;
}
