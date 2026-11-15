/** Types et libellés des feuilles de temps (lot R9), tels que l'API `/api/effectifs/temps` les rend. */
export type StatutFeuille = "BROUILLON" | "SOUMISE" | "RETOURNEE" | "APPROUVEE" | "PAYEE";

export interface Feuille {
  id: string; ressourceId: string; nom: string; matricule: string; uniteCode: string; debut: string; fin: string;
  statut: StatutFeuille; palier: number; paliersRequis: number; soumiseLe: string | null; approuveeLe: string | null;
}

export interface Segment {
  id: string; date: string; debut: string; fin: string; typeHeureCode: string; comportement: string; source: "GENERE" | "POINTAGE" | "SAISIE" | "EXTERNE";
  placeId: string | null; version: number; remplaceId: string | null; motif: string | null;
  /** E-RF-13 : la source tierce d'un temps proposé, s'il attend une confirmation, et ce qui confirme un temps pointé. */
  sourceExterne?: string | null; aConfirmer?: boolean; corrobore?: boolean | null; sourcesCorroborantes?: string[];
  /** Ce à quoi le temps a servi : la description des tâches, et les valeurs des champs de l'organisme. */
  description?: string | null; attributs?: Record<string, ValeurChamp> | null;
}

/** Un champ de la feuille défini par l'organisme (ETUDE-FEUILLE-DE-TEMPS-DYNAMIQUE.md). Le serveur rend le type en majuscules. */
export type TypeChamp = "texte" | "nombre" | "booleen" | "liste";
export type ValeurChamp = string | number | boolean;
export interface ChampFeuille { code: string; libelle: string; type: string; valeurs: string[]; requis: boolean; actif: boolean; ordre: number }
export const typeDe = (c: ChampFeuille) => c.type.toLowerCase() as TypeChamp;

/** Une valeur de champ en mots : jamais « true ». */
export const valeurEnMots = (v: ValeurChamp | undefined) => v === undefined || v === "" ? "—" : v === true ? "Oui" : v === false ? "Non" : String(v);

/** Qui doit agir maintenant sur une feuille soumise : le libellé du palier courant du circuit. */
export const enAttenteDe = (f: { statut: StatutFeuille; flux?: Flux | null }) =>
  f.statut !== "SOUMISE" ? null : f.flux?.etapes.find((e) => e.palier === f.flux?.palierCourant && e.statut === "EN_ATTENTE")?.libelle ?? null;

/** Ce que deux sources tierces disent l'une de l'autre, un jour donné (E-RF-13). */
export interface CorrelationSources { date: string; sources: [string, string]; concordants: number; divergents: number }

/** Un palier du flux d'approbation (E-GP-6). */
export interface EtapeFlux {
  id: string; palier: number; libelle: string; statut: "EN_ATTENTE" | "APPROUVEE" | "REJETEE" | "RETIREE";
  decidePar: string | null; decideLe: string | null; pourLeCompteDe: string | null;
}
export interface Flux { demandeId: string; statut: "EN_COURS" | "APPROUVE" | "REJETE" | "RETIRE"; etapes: EtapeFlux[]; palierCourant: number | null }

export interface Anomalie { code: string; date: string; message: string; bloquante: boolean }

export interface FeuilleDetaillee extends Feuille {
  segments: Segment[]; totaux: Record<string, number>; heuresSupplementaires: { parJour: Record<string, number>; total: number };
  ecarts: Array<{ placeId: string; date: string; planifie: number; declare: number; ecart: number }>; anomalies: Anomalie[];
  decisions: Array<{ palier: number; decision: string; motif: string | null; decideLe: string; decidePar: string }>;
  correlation?: CorrelationSources[]; flux?: Flux | null;
}

export const LIBELLE_STATUT: Record<StatutFeuille, string> = { BROUILLON: "Brouillon", SOUMISE: "Soumise", RETOURNEE: "Retournée", APPROUVEE: "Approuvée", PAYEE: "Payée" };
export const TON_STATUT: Record<StatutFeuille, string> = { BROUILLON: "brouillon", SOUMISE: "en_attente", RETOURNEE: "refusee", APPROUVEE: "approuvee", PAYEE: "clos" };
export const LIBELLE_SOURCE: Record<Segment["source"], string> = { GENERE: "Horaire (non pointé)", POINTAGE: "Pointé", SAISIE: "Saisi", EXTERNE: "Horodatage externe" };

/** L'origine d'un segment en mots, source tierce et confirmation comprises (E-RF-13). */
export const origineEnMots = (s: Segment) =>
  `${LIBELLE_SOURCE[s.source]}${s.sourceExterne ? ` (${s.sourceExterne})` : ""}${s.aConfirmer ? " — à confirmer" : ""}`;

/** Ce qui confirme un segment : jamais par la seule couleur, toujours en mots. */
export const preuveEnMots = (s: Segment) =>
  s.corrobore === true ? `Confirmé par ${(s.sourcesCorroborantes ?? []).join(", ")}` : s.corrobore === false ? "Non confirmé par une source tierce" : "—";

export const LIBELLE_ETAPE: Record<EtapeFlux["statut"], string> = { EN_ATTENTE: "en attente", APPROUVEE: "approuvé", REJETEE: "retourné", RETIREE: "retiré (feuille rappelée)" };

export const duree = (minutes: number) => `${minutes < 0 ? "−" : ""}${Math.floor(Math.abs(minutes) / 60)} h ${String(Math.abs(minutes) % 60).padStart(2, "0")}`;
export const heureDe = (iso: string) => new Date(iso).toLocaleTimeString("fr-CA", { hour: "2-digit", minute: "2-digit" });
/** Une heure locale saisie (HH:MM) le jour donné, en ISO UTC. */
export const instantDe = (date: string, hhmm: string) => new Date(`${date}T${hhmm}`).toISOString();
export const hhmmDe = (iso: string) => { const d = new Date(iso); return `${String(d.getHours()).padStart(2, "0")}:${String(d.getMinutes()).padStart(2, "0")}`; };
export const statutEnMots = (f: Pick<Feuille, "statut" | "palier" | "paliersRequis">) =>
  f.statut === "SOUMISE" && f.paliersRequis > 1 ? `${LIBELLE_STATUT[f.statut]} — palier ${f.palier} sur ${f.paliersRequis}` : LIBELLE_STATUT[f.statut];

/** Une décision de l'historique, en mots : un palier, ou un geste hors circuit (rappel, réouverture). */
export const LIBELLE_DECISION: Record<string, string> = {
  APPROUVER: "approuvé", RETOURNER: "retourné", RAPPELER: "rappelée par la personne", ROUVRIR: "rouverte par l'encadrement",
};
export const decisionEnMots = (d: string) => LIBELLE_DECISION[d] ?? d;

/** Avant la paie, une feuille soumise ou approuvée se rappelle ; payée, jamais. */
export const peutRappeler = (s: StatutFeuille) => s === "SOUMISE" || s === "APPROUVEE";
