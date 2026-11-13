/** Les formes renvoyées par `/api/effectifs/echanges` (lot R6). */
import type { Motif } from "../planification/types";

export type StatutEchange = "PROPOSE" | "A_VALIDER" | "APPLIQUE" | "REFUSE" | "REJETE" | "ANNULE";

export const LIBELLE_STATUT: Record<StatutEchange, string> = {
  PROPOSE: "Proposé", A_VALIDER: "À valider par le chef", APPLIQUE: "Appliqué", REFUSE: "Refusé", REJETE: "Rejeté", ANNULE: "Annulé",
};
/** Vers les tons communs de `BadgeStatut`. */
export const TON_STATUT: Record<StatutEchange, string> = {
  PROPOSE: "en_attente", A_VALIDER: "en_attente", APPLIQUE: "approuvee", REFUSE: "refusee", REJETE: "refusee", ANNULE: "annulee",
};

export interface Quart { date: string; debut: number; fin: number }
/**
 * Un quart publié, tel qu'on le montre.
 *
 * `milieu` est nul pour un quart sans site rattaché — un ad hoc posé sans milieu. L'écran écrit
 * alors « — », jamais un site deviné : se présenter au mauvais garage n'est pas une erreur
 * rattrapable dans la journée.
 */
export interface QuartEchangeable extends Quart { placeId: string; uniteCode: string; corpsEmploiCode: string; ressourceId: string; nom: string; milieuId: string | null; milieu: string | null }
export interface Echange {
  id: string; statut: StatutEchange; actionRegles: string; motifsRegles: Motif[];
  placeCedeeId: string; placeVoulueId: string; ressourceAId: string; ressourceBId: string; nomA: string; nomB: string;
  quartCede: Quart; quartVoulu: Quart; uniteA: string; uniteB: string; proposeLe: string; motifDecision: string | null;
}
