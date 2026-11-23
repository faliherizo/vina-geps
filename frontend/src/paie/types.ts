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

export type NatureGain = "heures-regulieres" | "heures-supplementaires" | "salaire" | "prime-pourcentage" | "prime-horaire" | "forfait";

/** Une ligne de gains de la fiche de paie (ADR 0084). */
export interface Gain { code: string; libelle: string; nature: NatureGain; quantite: number; taux: number; montant: number }

export interface Bulletin {
  id: string; cycleId: string; ressourceId: string; matricule: string; nom: string; datePaie: string;
  heuresRegulieres: number; heuresSupplementaires: number; retenues: Retenues;
  totalRetenues: number; net: number; cotisationsEmployeur: number; coutEmployeur: number; feuilles: string[];
  /** Absents des bulletins d'avant l'ADR 0084 : leur brut reste dans `retenues`. */
  gains?: Gain[];
}

export interface Personne { ressourceId: string; matricule: string; nom: string }

export interface FeuilleNonValidee { feuilleId: string; ressourceId: string; nom: string; debut: string }

export interface Preparation {
  cycle: Cycle; bulletins: Bulletin[]; sansDossier: Personne[];
  sansTaux?: Personne[]; nonValidees?: FeuilleNonValidee[];
}

export interface Dossier {
  ressourceId: string; mode: Mode; frequence: Frequence; tauxHoraire: number | null; salaireAnnuel: number | null;
  montantPersonnelFederal: number; montantPersonnelQuebec: number; echelon?: number | null;
}

export interface PersonneDossier extends Personne { dossier: Dossier | null; titreEmploi?: string | null; convention?: string | null }

export interface Reglages { annee: number; tauxFss: number; tauxCnesst: number; majorationSupplementaire: number; validationTempsExigee?: boolean }

// ── Règles de paie variable (ADR 0084) ──────────────────────────────────────

export type TypeRegle = "taux-de-base" | "prime-pourcentage" | "prime-horaire" | "forfait";

export interface Regle {
  id: string; code: string; libelle: string; type: TypeRegle; valeur: number;
  titreEmploi: string | null; convention: string | null; echelon: number | null;
  heureDebut: string | null; heureFin: string | null; jours: number[]; feriesSeulement: boolean; codesPaie: string[];
  valideDu: string; valideAu: string | null; actif: boolean;
}

export const TYPES_REGLE: ReadonlyArray<{ code: TypeRegle; libelle: string; unite: string }> = [
  { code: "taux-de-base", libelle: "Taux de base (échelle salariale)", unite: "$ par heure" },
  { code: "prime-pourcentage", libelle: "Prime en pourcentage du taux", unite: "% du taux de base" },
  { code: "prime-horaire", libelle: "Prime horaire", unite: "$ par heure admissible" },
  { code: "forfait", libelle: "Forfait par paie", unite: "$ par paie" },
];

export const libelleTypeRegle = (t: TypeRegle): string => TYPES_REGLE.find((x) => x.code === t)?.libelle ?? t;

/** Les jours en numéros ISO, du lundi (1) au dimanche (7). */
export const JOURS: ReadonlyArray<{ numero: number; court: string; long: string }> = [
  { numero: 1, court: "lun", long: "lundi" }, { numero: 2, court: "mar", long: "mardi" }, { numero: 3, court: "mer", long: "mercredi" },
  { numero: 4, court: "jeu", long: "jeudi" }, { numero: 5, court: "ven", long: "vendredi" }, { numero: 6, court: "sam", long: "samedi" },
  { numero: 7, court: "dim", long: "dimanche" },
];

// ── Validation des temps (ADR 0084) ─────────────────────────────────────────

export type AnomalieTemps = "aucune-feuille" | "non-approuvee" | "incalculable" | "sans-dossier" | "sans-taux" | "heures-elevees" | "validation-perimee";

export const ANOMALIES: Record<AnomalieTemps, { libelle: string; bloquante: boolean }> = {
  "aucune-feuille": { libelle: "Aucune feuille dans la période", bloquante: false },
  "non-approuvee": { libelle: "Pas encore approuvée par le gestionnaire", bloquante: true },
  incalculable: { libelle: "Heures incalculables (paramétrage des effectifs)", bloquante: true },
  "sans-dossier": { libelle: "Sans dossier de rémunération : ne sera pas payée", bloquante: false },
  "sans-taux": { libelle: "Aucun taux (ni au dossier, ni à l'échelle) : ne sera pas payée", bloquante: false },
  "heures-elevees": { libelle: "Plus de 60 heures dans la semaine : à vérifier", bloquante: false },
  "validation-perimee": { libelle: "Validée, mais ses heures ont changé depuis : à revalider", bloquante: false },
};

export const libelleAnomalie = (a: string): string => ANOMALIES[a as AnomalieTemps]?.libelle ?? a;

export interface Validation { id: string; feuilleId: string; decision: "validee" | "retournee"; motif: string | null; acteur: string; instant: string }

export interface LigneControle {
  feuilleId: string; ressourceId: string; matricule: string; nom: string; debut: string;
  etat: "brouillon" | "soumise" | "retournee" | "approuvee" | "payee";
  heuresRegulieres: number; heuresSupplementaires: number; anomalies: string[]; incalculable: string | null;
  validee: boolean; validable: boolean; validation: Validation | null;
}

export interface Controle { feuilles: LigneControle[]; sansFeuille: Array<Personne & { anomalies: string[] }> }

const ETATS_FEUILLE: Record<LigneControle["etat"], string> = {
  brouillon: "Brouillon", soumise: "Soumise", retournee: "Retournée", approuvee: "Approuvée", payee: "Payée",
};
export const libelleEtatFeuille = (e: LigneControle["etat"]): string => ETATS_FEUILLE[e] ?? e;

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
