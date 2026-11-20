/** Les formes renvoyées par `/api/effectifs/agph` (lot R7). */
import type { Motif } from "../planification/types";
import type { Conformite, Plage, TypeDisponibilite } from "../disponibilites/types";

export type EtatCampagne = "PREPARATION" | "DEPOT" | "CHOIX" | "ATTRIBUTION" | "REVISION" | "CLOSE";
export type EtatRang = "EN_ATTENTE" | "A_SON_TOUR" | "A_CHOISI" | "PASSE" | "RENONCE";
/** Ce que devient le rang dont le délai expire (addenda 7, Q12) : le premier poste admissible, ou rien. */
export type TraitementDelaiEchu = "ATTRIBUER" | "PASSER";
export const LIBELLE_DELAI_ECHU: Record<TraitementDelaiEchu, string> = {
  ATTRIBUER: "Attribuer le premier poste admissible", PASSER: "Passer au rang suivant, sans poste",
};
export type EtatRemplacement = "OUVERT" | "ATTRIBUE" | "NON_COMBLE";

export const LIBELLE_CAMPAGNE: Record<EtatCampagne, string> = {
  PREPARATION: "En préparation", DEPOT: "Rangs figés — dépôt des disponibilités", CHOIX: "Tour de rôle en cours", ATTRIBUTION: "Tous servis — à réviser ou clore",
  REVISION: "En révision", CLOSE: "Close",
};
export const LIBELLE_RANG: Record<EtatRang, string> = { EN_ATTENTE: "En attente", A_SON_TOUR: "À son tour", A_CHOISI: "A choisi", PASSE: "Passé", RENONCE: "A renoncé à tout poste" };
export const LIBELLE_REMPLACEMENT: Record<EtatRemplacement, string> = {
  OUVERT: "Ouvert aux manifestations", ATTRIBUE: "Attribué", NON_COMBLE: "Non comblé",
};
export const LIBELLE_MODE: Record<string, string> = { EMPLOYE: "par la personne", PLANIFICATEUR: "par le planificateur", AUTOMATIQUE: "automatique (délai écoulé)" };

export interface Campagne {
  id: string; uniteCode: string; annee: number; nom: string; corpsEmploiCode: string; etat: EtatCampagne; delaiChoixMinutes: number;
  budgetHeuresSemaine?: number | null; reviseeLe?: string | null; traitementDelaiEchu?: TraitementDelaiEchu;
}
export interface Rang { ressourceId: string; nom: string; matricule: string; rang: number; anciennete: number; etat: EtatRang; tourDebut: string | null; tourFin: string | null }
export interface Choix { ressourceId: string; posteId: string; posteCode: string; mode: string; choisiLe: string }
export interface VueCampagne extends Campagne { rangs: Rang[]; choix: Choix[]; postesNonPourvus?: Array<{ posteId: string; posteCode: string; etat: string }> }
export interface PosteOffert { id: string; code: string; libelle: string; statutEmploiCode: string; admissible: boolean; motifs: string[] }
export interface PostesDisponibles { campagne: Campagne; monRang: Rang | null; peutChoisir: boolean; postes: PosteOffert[] }
export interface Remplacement {
  id: string; uniteCode: string; posteId: string; posteCode: string; origine: "ABSENCE" | "POSTE_VACANT"; debut: string; fin: string;
  finManifestation: string; etat: EtatRemplacement; attribueANom: string | null; placesAttribuees: number; interesses: number;
  transfereLe?: string | null; quartsTransferes?: number; motifNonComble?: string | null;
}
export interface Interesse { ressourceId: string; nom: string; matricule: string; anciennete: number; deposeLe: string }

/** Un instant ISO lisible : « 2031-01-06 à 13 h 30 ». */
export const instant = (iso: string | null): string => {
  if (!iso) return "—";
  const d = new Date(iso);
  return `${d.toISOString().slice(0, 10)} à ${String(d.getHours()).padStart(2, "0")} h ${String(d.getMinutes()).padStart(2, "0")}`;
};

// ── Semaine type de disponibilités (E-CH-1) ────────────────────────────────

/** La semaine de référence du serveur : un lundi quelconque, sur lequel se pose la semaine type. */
export const SEMAINE_TYPE = ["2001-01-01", "2001-01-02", "2001-01-03", "2001-01-04", "2001-01-05", "2001-01-06", "2001-01-07"] as const;
const NOMS_JOURS = ["Lundi", "Mardi", "Mercredi", "Jeudi", "Vendredi", "Samedi", "Dimanche"];
export const libelleJourType = (date: string): string => NOMS_JOURS[SEMAINE_TYPE.indexOf(date as (typeof SEMAINE_TYPE)[number])] ?? date;

export interface PlageType { jour: number; debut: number; fin: number; type: TypeDisponibilite }
export const versDates = (plages: readonly PlageType[]): Plage[] => plages.map((p) => ({ date: SEMAINE_TYPE[p.jour - 1], debut: p.debut, fin: p.fin, type: p.type }));
export const versJours = (plages: readonly Plage[]): PlageType[] =>
  plages.map((p) => ({ jour: SEMAINE_TYPE.indexOf(p.date as (typeof SEMAINE_TYPE)[number]) + 1, debut: p.debut, fin: p.fin, type: p.type }));

export interface DepotCampagne { ressourceId: string; conformite: Conformite; motifs: Motif[]; heures: number; deposeLe: string; deposePar: string; plages: PlageType[] }
export interface DepotsCampagne {
  campagneId: string; etat: EtatCampagne; budgetHeuresSemaine: number | null; heuresCampagne: number; tauxBudget: number | null; deposes: number;
  lignes: Array<{ ressourceId: string; nom: string; matricule: string; rang: number; depot: DepotCampagne | null }>;
}
/** Le dépôt des disponibilités de la campagne est ouvert au dépôt et à la révision. */
export const depotOuvert = (etat: EtatCampagne): boolean => etat === "DEPOT" || etat === "REVISION";
