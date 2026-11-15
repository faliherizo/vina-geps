/**
 * **Paie** (ADR 0076) — les formes reçues de `/api/paie` et les libellés. Les montants arrivent en nombres ;
 * le serveur les a déjà arrondis au cent, l'écran ne recalcule rien.
 */

export type EtatCycle = "prepare" | "traite" | "annule";
export type Frequence = "hebdomadaire" | "aux-deux-semaines" | "bimensuelle" | "mensuelle";
export type Mode = "horaire" | "salaire";

export interface Cycle {
  id: string; debut: string; fin: string; datePaie: string; etat: EtatCycle;
  prepareLe: string; preparePar: string; traiteLe?: string | null; traitePar?: string | null;
}

export interface CotisationsEmployeur { rrq: number; rrq2: number; ae: number; rqap: number; fss: number; cnt: number; cnesst: number }

export interface Retenues {
  brut: number; impotFederal: number; impotQuebec: number; rrq: number; rrq2: number; ae: number; rqap: number;
  gainsRrq: number; gainsAe: number; gainsRqap: number; employeur: CotisationsEmployeur;
}

export interface Bulletin {
  id: string; cycleId: string; ressourceId: string; matricule: string; nom: string; datePaie: string;
  heuresRegulieres: number; heuresSupplementaires: number; retenues: Retenues;
  totalRetenues: number; net: number; cotisationsEmployeur: number; coutEmployeur: number; feuilles: string[];
}

export interface Personne { ressourceId: string; matricule: string; nom: string }

export interface Preparation { cycle: Cycle; bulletins: Bulletin[]; sansDossier: Personne[] }

export interface Dossier {
  ressourceId: string; mode: Mode; frequence: Frequence; tauxHoraire: number | null; salaireAnnuel: number | null;
  montantPersonnelFederal: number; montantPersonnelQuebec: number;
}

export interface PersonneDossier extends Personne { dossier: Dossier | null }

export interface Reglages { annee: number; tauxFss: number; tauxCnesst: number; majorationSupplementaire: number }

export interface Remise {
  mois: string; echeance: string; impotQuebec: number; rrq: number; rqap: number; fss: number; revenuQuebec: number;
  impotFederal: number; ae: number; arc: number; cnt: number; cnesst: number;
}

export interface Releve {
  ressourceId: string; matricule: string; nom: string;
  rl1: { a: number; b: number; rrq2: number; c: number; e: number; g: number; h: number; i: number };
  t4: { c14: number; c17: number; rrq2: number; c18: number; c22: number; c24: number; c26: number; c55: number; c56: number };
}

export const FREQUENCES: ReadonlyArray<{ code: Frequence; libelle: string }> = [
  { code: "hebdomadaire", libelle: "Chaque semaine (52 paies)" },
  { code: "aux-deux-semaines", libelle: "Aux deux semaines (26 paies)" },
  { code: "bimensuelle", libelle: "Deux fois par mois (24 paies)" },
  { code: "mensuelle", libelle: "Chaque mois (12 paies)" },
];

export const MODES: ReadonlyArray<{ code: Mode; libelle: string }> = [
  { code: "horaire", libelle: "À l'heure (les feuilles font le brut)" },
  { code: "salaire", libelle: "Au salaire annuel" },
];

const ETATS: Record<EtatCycle, string> = { prepare: "Préparé", traite: "Traité", annule: "Annulé" };

export const libelleEtat = (e: EtatCycle): string => ETATS[e] ?? e;
export const libelleFrequence = (f: Frequence): string => FREQUENCES.find((x) => x.code === f)?.libelle ?? f;

const MONNAIE = new Intl.NumberFormat("fr-CA", { style: "currency", currency: "CAD" });
const NOMBRE = new Intl.NumberFormat("fr-CA", { maximumFractionDigits: 2 });
const POURCENT = new Intl.NumberFormat("fr-CA", { style: "percent", maximumFractionDigits: 3 });

/** 1 234,56 $ — le format québécois, espace insécable compris. */
export const argent = (v: number | null | undefined): string => (v == null ? "—" : MONNAIE.format(v));
export const heures = (v: number | null | undefined): string => (v == null ? "—" : `${NOMBRE.format(v)} h`);
export const pourcent = (v: number | null | undefined): string => (v == null ? "—" : POURCENT.format(v));

/** Un état de cycle porte aussi son texte : la couleur du badge ne dit rien seule (WCAG 1.4.1). */
export const statutBadge = (e: EtatCycle): string => (e === "traite" ? "paid" : e === "annule" ? "clos" : "en_attente");
