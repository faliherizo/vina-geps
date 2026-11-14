/**
 * **Les indicateurs du jour** (planche s01) — la part pure du tableau de bord gestionnaire.
 *
 * Rien ici ne lit le réseau ni l'horloge : la date du jour et les réponses arrivent en paramètre,
 * de sorte que chaque règle s'éprouve sans serveur. La lecture est dans `useTableauDeBord`.
 *
 * Une lecture qui n'a pas eu lieu (module non souscrit, droit absent, serveur injoignable) vaut
 * `null`, jamais `[]` : un zéro dirait « rien à faire aujourd'hui », ce qui serait faux.
 */
import type { Equipage, Periode } from "../effectifs/planification/types";
import type { Echange } from "../effectifs/echanges/types";
import type { Pointage } from "../effectifs/presence/types";

/** Le fuseau du client : la journée d'Urgences-santé est celle de Montréal, pas celle du poste. */
export const FUSEAU_CLIENT = "America/Toronto";

/** Une absence telle que la console la sert (la part que le tableau de bord affiche). */
export interface AbsenceATraiter {
  id: string; ressourceNom: string; codeAbsence: string; du: string; au: string; statut: string;
}

/** Ce que le tableau de bord a pu lire ; `null` = non lu. */
export interface DonneesDuJour {
  equipages: Equipage[] | null;
  absences: AbsenceATraiter[] | null;
  echanges: Echange[] | null;
  pointages: Pointage[] | null;
  milieux: Map<string, string>;
}

export const AUCUNE_DONNEE: DonneesDuJour = { equipages: null, absences: null, echanges: null, pointages: null, milieux: new Map() };

/** Les quatre vues du tableau de bord, dans l'ordre de la planche. */
export const VUES_TABLEAU = ["couverture", "absences", "demandes", "anomalies"] as const;
export type VueTableau = (typeof VUES_TABLEAU)[number];

/** « 2026-11-13 » : la date du jour dans le fuseau du client. */
export function jourDuClient(instant: Date): string {
  return instant.toLocaleDateString("en-CA", { timeZone: FUSEAU_CLIENT });
}

/** Les périodes dont l'intervalle contient le jour : ce sont elles qui portent les équipages d'aujourd'hui. */
export function periodesDuJour(periodes: ReadonlyArray<Periode>, jour: string): Periode[] {
  return periodes.filter((p) => p.debut <= jour && jour <= p.fin);
}

/** Les places d'un équipage qui restent à pourvoir — jamais négatif, même si la donnée l'est. */
export function placesACombler(e: Equipage): number {
  return Math.max(0, e.places.length - e.pourvues);
}

/** Un équipage est à combler s'il est sous le minimum opérationnel **ou** s'il lui reste une place. */
export function estACombler(e: Equipage): boolean {
  return e.statut === "SOUS_MINIMUM" || placesACombler(e) > 0;
}

/** Les équipages du jour, ceux à combler d'abord, puis par heure de début. */
export function trierParUrgence(equipages: ReadonlyArray<Equipage>): Equipage[] {
  return [...equipages].sort((a, b) => Number(estACombler(b)) - Number(estACombler(a)) || a.debut - b.debut);
}

/** Les quatre chiffres des tuiles ; `null` quand la lecture n'a pas eu lieu. */
export interface Compteurs { quarts: number | null; aCombler: number | null; absences: number | null; demandes: number | null; anomalies: number | null }

export function compteurs(d: DonneesDuJour): Compteurs {
  return {
    quarts: d.equipages?.length ?? null,
    aCombler: d.equipages ? d.equipages.reduce((n, e) => n + placesACombler(e), 0) : null,
    absences: d.absences?.length ?? null,
    demandes: d.echanges ? d.echanges.filter((e) => e.statut === "A_VALIDER").length : null,
    anomalies: d.pointages?.length ?? null,
  };
}

/** « 08 », comme la planche ; « — » pour un chiffre qu'on ne connaît pas. */
export function chiffre(n: number | null): string {
  return n === null ? "—" : String(n).padStart(2, "0");
}
