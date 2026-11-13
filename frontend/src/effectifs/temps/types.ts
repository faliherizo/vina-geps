/** Types et libellés des feuilles de temps (lot R9), tels que l'API `/api/effectifs/temps` les rend. */
export type StatutFeuille = "BROUILLON" | "SOUMISE" | "RETOURNEE" | "APPROUVEE";

export interface Feuille {
  id: string; ressourceId: string; nom: string; matricule: string; uniteCode: string; debut: string; fin: string;
  statut: StatutFeuille; palier: number; paliersRequis: number; soumiseLe: string | null; approuveeLe: string | null;
}

export interface Segment {
  id: string; date: string; debut: string; fin: string; typeHeureCode: string; comportement: string; source: "GENERE" | "POINTAGE" | "SAISIE" | "EXTERNE";
  placeId: string | null; version: number; remplaceId: string | null; motif: string | null;
  /** E-RF-13 : la source tierce d'un temps proposé, s'il attend une confirmation, et ce qui confirme un temps pointé. */
  sourceExterne?: string | null; aConfirmer?: boolean; corrobore?: boolean | null; sourcesCorroborantes?: string[];
}

/** Ce que deux sources tierces disent l'une de l'autre, un jour donné (E-RF-13). */
export interface CorrelationSources { date: string; sources: [string, string]; concordants: number; divergents: number }

/** Un palier du flux d'approbation (E-GP-6). */
export interface EtapeFlux {
  id: string; palier: number; libelle: string; statut: "EN_ATTENTE" | "APPROUVEE" | "REJETEE";
  decidePar: string | null; decideLe: string | null; pourLeCompteDe: string | null;
}
export interface Flux { demandeId: string; statut: "EN_COURS" | "APPROUVE" | "REJETE"; etapes: EtapeFlux[]; palierCourant: number | null }

export interface Anomalie { code: string; date: string; message: string; bloquante: boolean }

export interface FeuilleDetaillee extends Feuille {
  segments: Segment[]; totaux: Record<string, number>; heuresSupplementaires: { parJour: Record<string, number>; total: number };
  ecarts: Array<{ placeId: string; date: string; planifie: number; declare: number; ecart: number }>; anomalies: Anomalie[];
  decisions: Array<{ palier: number; decision: string; motif: string | null; decideLe: string; decidePar: string }>;
  correlation?: CorrelationSources[]; flux?: Flux | null;
}

export const LIBELLE_STATUT: Record<StatutFeuille, string> = { BROUILLON: "Brouillon", SOUMISE: "Soumise", RETOURNEE: "Retournée", APPROUVEE: "Approuvée" };
export const TON_STATUT: Record<StatutFeuille, string> = { BROUILLON: "brouillon", SOUMISE: "en_attente", RETOURNEE: "refusee", APPROUVEE: "approuvee" };
export const LIBELLE_SOURCE: Record<Segment["source"], string> = { GENERE: "Horaire (non pointé)", POINTAGE: "Pointé", SAISIE: "Saisi", EXTERNE: "Horodatage externe" };

/** L'origine d'un segment en mots, source tierce et confirmation comprises (E-RF-13). */
export const origineEnMots = (s: Segment) =>
  `${LIBELLE_SOURCE[s.source]}${s.sourceExterne ? ` (${s.sourceExterne})` : ""}${s.aConfirmer ? " — à confirmer" : ""}`;

/** Ce qui confirme un segment : jamais par la seule couleur, toujours en mots. */
export const preuveEnMots = (s: Segment) =>
  s.corrobore === true ? `Confirmé par ${(s.sourcesCorroborantes ?? []).join(", ")}` : s.corrobore === false ? "Non confirmé par une source tierce" : "—";

export const LIBELLE_ETAPE: Record<EtapeFlux["statut"], string> = { EN_ATTENTE: "en attente", APPROUVEE: "approuvé", REJETEE: "retourné" };

export const duree = (minutes: number) => `${minutes < 0 ? "−" : ""}${Math.floor(Math.abs(minutes) / 60)} h ${String(Math.abs(minutes) % 60).padStart(2, "0")}`;
export const heureDe = (iso: string) => new Date(iso).toLocaleTimeString("fr-CA", { hour: "2-digit", minute: "2-digit" });
/** Une heure locale saisie (HH:MM) le jour donné, en ISO UTC. */
export const instantDe = (date: string, hhmm: string) => new Date(`${date}T${hhmm}`).toISOString();
export const hhmmDe = (iso: string) => { const d = new Date(iso); return `${String(d.getHours()).padStart(2, "0")}:${String(d.getMinutes()).padStart(2, "0")}`; };
export const statutEnMots = (f: Pick<Feuille, "statut" | "palier" | "paliersRequis">) =>
  f.statut === "SOUMISE" && f.paliersRequis > 1 ? `${LIBELLE_STATUT[f.statut]} — palier ${f.palier} sur ${f.paliersRequis}` : LIBELLE_STATUT[f.statut];
