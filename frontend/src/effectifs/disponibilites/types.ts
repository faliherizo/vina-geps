/** Les formes renvoyées par `/api/effectifs/disponibilites` (lot R4). */
import type { Motif, Periode, Ressource } from "../planification/types";
import type { RegleApplicable } from "./ReglesApplicables";

export type TypeDisponibilite = "DISPONIBLE" | "PREFERENCE" | "INDISPONIBLE";
export const TYPES_DISPONIBILITE: ReadonlyArray<{ code: TypeDisponibilite; libelle: string }> = [
  { code: "DISPONIBLE", libelle: "Disponible" },
  { code: "PREFERENCE", libelle: "Disponible, de préférence" },
  { code: "INDISPONIBLE", libelle: "Indisponible" },
];

export type Conformite = "CONFORME" | "AVERTIE" | "APPROBATION_REQUISE";
export const LIBELLE_CONFORMITE: Record<Conformite, string> = {
  CONFORME: "Conforme",
  AVERTIE: "Conforme, avec avertissement",
  APPROBATION_REQUISE: "En attente d'approbation",
};

export interface Plage { date: string; debut: number; fin: number; type: TypeDisponibilite }
export interface Depot {
  periodeId: string; ressourceId: string; conformite: Conformite; motifs: Motif[];
  deposeLe: string; deposePar: string; approuveLe: string | null; approuvePar: string | null; plages: Plage[];
}
export interface MesDisponibilites {
  ressource: Ressource | null;
  /** Chaque période porte le dépôt de la personne **et** les règles qui le jugeront (E-GN-16). */
  periodes: Array<Periode & { depot: Depot | null; regles?: RegleApplicable[] }>;
}
export interface LigneDepot {
  ressourceId: string; nom: string; matricule: string;
  depot: { conformite: Conformite; motifs: Motif[]; plages: number; deposeLe: string } | null;
}
export interface Candidats {
  admissibles: Array<{ ressourceId: string; nom: string; preference: boolean; anciennete: number; heuresPlanifiees: number }>;
  exclus: Array<{ ressourceId: string; nom: string; motif: string }>;
}

/** L'attribution de fin de collecte planifiée à l'ordonnanceur (P-GH-4). */
export interface AttributionAutomatique {
  periodeId: string; finCollecte: string | null; echeanceLe: string | null; statut: "en_attente" | "en_cours" | "faite" | "echouee" | "annulee" | null;
  derniere: { le: string; attribuees: number; restees: number } | null;
}
