/**
 * **Ce que l'hôte désigne, lu au serveur** (`/api/auth/deploiement`, ADR 0033) — avec patience.
 *
 * Une réponse qui ne dit rien (502 pendant un redéploiement, 503 de la limitation de débit, réseau coupé) n'est pas
 * une réponse « mode sélecteur » : la page de connexion d'un organisme retombait pourtant sur la liste des organismes,
 * vide, sans organisme choisi (constaté sur clincom.geps le 23 novembre 2026). On réessaie, puis on dit que le service
 * est momentanément indisponible ; seul le serveur décide du mode.
 */

export interface Etablissement { id: string; nom: string; slug: string }
export interface Deploiement { resolution: string; etablissement?: Etablissement | null }

/** `lu` : le serveur a répondu (même un refus, qui nomme un hôte inconnu) ; `indisponible` : il n'a rien dit. */
export type LectureDeploiement = { etat: "lu"; ok: boolean; corps: Deploiement } | { etat: "indisponible" };

/** Les attentes entre deux essais : un redéploiement remet le serveur en ligne en quelques secondes. */
export const ATTENTES_MS = [1000, 2000, 4000];

type Lire = (chemin: string) => Promise<{ ok: boolean; json: () => Promise<unknown> }>;

async function uneFois(lire: Lire): Promise<{ ok: boolean; corps: Deploiement } | null> {
  try {
    const r = await lire("/api/auth/deploiement");
    const corps = await r.json().catch(() => null) as Deploiement | null;
    return corps && typeof corps.resolution === "string" ? { ok: r.ok, corps } : null;
  } catch {
    return null;
  }
}

export async function lireDeploiement(lire: Lire = fetch, attentes: number[] = ATTENTES_MS,
  attendre: (ms: number) => Promise<void> = (ms) => new Promise((ok) => setTimeout(ok, ms))): Promise<LectureDeploiement> {
  for (let essai = 0; ; essai++) {
    const r = await uneFois(lire);
    if (r) return { etat: "lu", ...r };
    if (essai >= attentes.length) return { etat: "indisponible" };
    await attendre(attentes[essai]);
  }
}
