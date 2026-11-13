/** Types et libellés de la présence (lot R8), tels que l'API `/api/effectifs/presence` les rend. */
import type { TonBadge } from "../../composants";

export type Sens = "ENTREE" | "DEBUT_PAUSE" | "FIN_PAUSE" | "SORTIE";
export type StatutPointage = "ENREGISTRE" | "A_APPROUVER" | "APPROUVE" | "REJETE";

export interface VerdictPreuve { mecanisme: string; exigee: boolean; fournie: boolean; satisfaite: boolean; distanceMetres?: number; detail?: string }
export interface Motif { cle: string; message: string }

export interface Pointage {
  id: string; ressourceId: string; nom: string; placeId: string | null; milieuId: string | null; uniteCode: string;
  sens: Sens; horodatage: string; origine: "APPAREIL" | "SAISIE"; saisieId: string | null;
  resultat: "CONFORME" | "ECART" | "NON_VERIFIE"; ecart: "HORS_MILIEU" | "MAUVAIS_MILIEU" | "PREUVE_ABSENTE" | null; distanceMetres: number | null;
  actionRegles: string; motifsRegles: Motif[]; statut: StatutPointage; justification: string | null;
  saisiLe: string; saisiPar: string; decideLe: string | null; decidePar: string | null; motifDecision: string | null;
  preuves: VerdictPreuve[]; corrobore: boolean;
  /** Le flux d'approbation à paliers (E-GP-6), rendu par la fiche d'un pointage. */
  flux?: { statut: string; palierCourant: number | null; etapes: Array<{ id: string; palier: number; libelle: string; statut: "EN_ATTENTE" | "APPROUVEE" | "REJETEE" }> } | null;
}

export interface Segment {
  debut: string; fin: string | null; type: "PRESENCE_PREALABLE" | "TRAVAIL" | "PAUSE"; minutes: number;
  /** Le site où ce temps a été fait (E-GP-7) ; nul quand le pointage ne l'a pas dit. */
  milieuId: string | null;
  /** L'activité de ce temps (E-GP-15) ; nulle quand elle n'a pas été déclarée. */
  activiteCode: string | null;
}

/** Les natures de temps non payé (E-GP-2) : la convention les plafonne séparément. */
export type NatureNonPayee = "PAUSE" | "REPAS" | "REPOS";

export const LIBELLE_NATURE: Record<NatureNonPayee, string> = {
  PAUSE: "Pause", REPAS: "Pause-repas", REPOS: "Période de repos",
};

/** Le mode de saisie qu'un profil impose (E-GP-1, P-GP-1). */
export type ModeSaisie = "TEMPS_REEL" | "A_POSTERIORI" | "MIXTE";

/**
 * Le profil de pointage appliqué à la personne, tel que `/mon-profil` le rend.
 *
 * C'est lui qui décide de ce que l'écran offre : afficher un geste que le serveur refusera fait
 * porter à la personne un refus qui n'est pas le sien.
 */
export interface ProfilPointage {
  code: string; libelle: string; mode: ModeSaisie;
  /** La population visée, du plus général au plus particulier. */
  porteeType: "ORGANISME" | "UNITE" | "CONVENTION" | "STATUT_EMPLOI" | "CORPS_EMPLOI" | "TITRE_EMPLOI";
  porteeCode: string | null;
  naturesNonPayees: NatureNonPayee[];
  pausesManuelles: boolean;
  presencePrealableMinutes: number;
  /** `null` : aucune limite au passé qu'une déclaration peut couvrir. */
  fenetreAPosterioriJours: number | null;
  actif: boolean;
}

export interface QuartDuMoment {
  placeId: string; milieuId: string; milieu: string; uniteCode: string; date: string; debut: number; fin: number;
  debutPrevu: string; finPrevue: string; presencePrealableMinutes: number; prochains: Sens[]; segments: Segment[]; minutesPayees: number;
  /** Le temps payé **réparti par site** (E-GP-7) ; les pauses n'y sont pas, elles ne sont pas payées. */
  parMilieu: Array<{ milieuId: string | null; milieu: string; minutes: number }>;
  /** Le temps payé **réparti par activité** (E-GP-15). */
  parActivite: Array<{ activiteCode: string | null; minutes: number }>;
  /** Les changements de site du quart, déduits des pointages. */
  deplacements: Array<{ quand: string; de: string; vers: string; deLibelle: string; versLibelle: string }>;
}

export interface MaPresence { ressource: { id: string; nom: string } | null; quart: QuartDuMoment | null; pointages: Pointage[] }

export const LIBELLE_SENS: Record<Sens, string> = { ENTREE: "Entrée", DEBUT_PAUSE: "Début de pause", FIN_PAUSE: "Fin de pause", SORTIE: "Sortie" };
export const LIBELLE_STATUT: Record<StatutPointage, string> = { ENREGISTRE: "Enregistré", A_APPROUVER: "À approuver", APPROUVE: "Approuvé", REJETE: "Rejeté" };
export const TON_STATUT: Record<StatutPointage, string> = { ENREGISTRE: "actif", A_APPROUVER: "en_attente", APPROUVE: "approuvee", REJETE: "refusee" };
export const LIBELLE_ECART: Record<NonNullable<Pointage["ecart"]>, string> = {
  HORS_MILIEU: "hors du milieu", MAUVAIS_MILIEU: "code d'un autre milieu", PREUVE_ABSENTE: "preuve exigée absente",
};
export const LIBELLE_SEGMENT: Record<Segment["type"], string> = { PRESENCE_PREALABLE: "Présence préalable", TRAVAIL: "Travail", PAUSE: "Pause" };
export const LIBELLE_MECANISME: Record<string, string> = {
  GEOREPERAGE: "Distance au milieu", CODE_QR_DE_LIEU: "Code QR du lieu", RESEAU: "Réseau du milieu", BORNE: "Borne", SOURCE_EXTERNE: "Source externe",
};

/** Le résultat d'un pointage en mots : la couleur ne porte jamais seule le sens (WCAG 1.4.1). */
export function resultatEnMots(p: Pick<Pointage, "resultat" | "ecart">): { texte: string; ton: TonBadge } {
  if (p.resultat === "CONFORME") return { texte: "Conforme", ton: "succes" };
  if (p.resultat === "NON_VERIFIE") return { texte: "Non vérifié", ton: "neutre" };
  return { texte: `Écart : ${p.ecart ? LIBELLE_ECART[p.ecart] : "inconnu"}`, ton: "avertissement" };
}

export const horaire = (iso: string) => new Date(iso).toLocaleString("fr-CA", { dateStyle: "short", timeStyle: "short" });
export const duree = (minutes: number) => `${Math.floor(minutes / 60)} h ${String(minutes % 60).padStart(2, "0")}`;

/** Une ligne de l'audit des localisations (E-GP-14). **La position n'y figure pas.** */
export interface LigneAudit {
  pointageId: string; ressourceId: string; nom: string; sens: Sens; horodatage: string;
  milieuId: string | null; milieu: string; uniteCode: string;
  resultat: string; ecart: string | null;
  /** La distance au milieu, en mètres : elle suffit à juger le lieu. */
  distanceMetres: number | null;
  mecanismes: Array<{ mecanisme: string; exigee: boolean; fournie: boolean; satisfaite: boolean; detail: string | null }>;
  origine: "APPAREIL" | "SAISIE";
  statut: string;
}

export interface Audit { lignes: LigneAudit[]; motifs: string[] }
