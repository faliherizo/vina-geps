/**
 * **Communications cliniques** (ADR 0074) — les formes que sert `/api/alertes`, et leurs libellés.
 * Les codes (priorité, sémantique) sont des ensembles fermés : un libellé par code, jamais un texte deviné.
 */
export type Priorite = "information" | "urgente" | "critique";
export type Semantique = "prise_en_charge" | "refus" | "information";

export const PRIORITES: ReadonlyArray<{ code: Priorite; libelle: string }> = [
  { code: "information", libelle: "Information" },
  { code: "urgente", libelle: "Urgente" },
  { code: "critique", libelle: "Critique" },
];
export const SEMANTIQUES: ReadonlyArray<{ code: Semantique; libelle: string }> = [
  { code: "prise_en_charge", libelle: "Prise en charge" },
  { code: "refus", libelle: "Refus" },
  { code: "information", libelle: "Information" },
];
export const libellePriorite = (p: string) => PRIORITES.find((x) => x.code === p)?.libelle ?? p;
export const libelleSemantique = (s: string) => SEMANTIQUES.find((x) => x.code === s)?.libelle ?? s;
/** Le ton du badge : la couleur double toujours un mot (WCAG 1.4.1). */
export const tonPriorite = (p: string) => (p === "critique" ? "refusee" : p === "urgente" ? "en_attente" : "ouvert");

export interface Reponse { id: string; libelle: string; semantique: Semantique; active: boolean }
/** Un modèle : chaque modification en fait une version ; son périmètre (groupes) borne qui le voit, vide = tout l'organisme. */
export interface Modele { id: string; titre: string; corps: string; priorite: Priorite; reponseIds: string[]; groupeIds?: string[]; version?: number; actif: boolean }
export interface VersionModele { version: number; titre: string; corps: string; priorite: Priorite; reponseIds: string[]; creeLe: string; creePar: string }
export interface Liste { id: string; nom: string }
/** Ce que l'écran doit offrir à la personne connectée (ST-G01). */
export interface Droits { envoyer: boolean; critique: boolean; configurer: boolean }
export interface Reglages { copieCourriel: boolean }

/** Les états du suivi, tels que le serveur les filtre (ST-G03). */
export type EtatSuivi = "prise_en_charge" | "sans_prise_en_charge" | "non_lue";
export const ETATS: ReadonlyArray<{ code: EtatSuivi; libelle: string }> = [
  { code: "prise_en_charge", libelle: "Prise en charge" },
  { code: "sans_prise_en_charge", libelle: "Sans prise en charge" },
  { code: "non_lue", libelle: "Pas encore lue de tous" },
];
export interface Groupe { id: string; nom: string }
export interface Personne { personId: string; nom: string }
export interface Remise { personId: string; nom: string; envoyeeLe: string; lueLe: string | null; reponseId: string | null; reponduLe: string | null }
export interface AlerteBase {
  id: string; titre: string; corps: string; priorite: Priorite; expediteurId: string; envoyeeLe: string; reponses: Reponse[];
  modeleId?: string | null; modeleVersion?: number | null;
}
export interface AlerteSuivie extends AlerteBase {
  bilan: { destinataires: number; lues: number; repondues: number; priseEnChargePar: string | null; priseEnChargeLe: string | null };
  remises: Remise[];
}
export interface AlerteRecue extends AlerteBase { maRemise: Remise }

/** « 2026-11-14 14:02 » à l'heure du client, depuis un instant ISO. */
export function quand(iso: string | null | undefined): string {
  if (!iso) return "—";
  const d = new Date(iso);
  if (Number.isNaN(d.getTime())) return iso;
  return d.toLocaleString("fr-CA", { timeZone: "America/Toronto", dateStyle: "short", timeStyle: "short" });
}

/** L'état d'une remise, en mots. */
export function etatRemise(r: Remise, reponses: Reponse[]): string {
  if (r.reponseId) return `Répondu : ${reponses.find((x) => x.id === r.reponseId)?.libelle ?? r.reponseId}`;
  return r.lueLe ? "Lu" : "Envoyé";
}

/* ── Relances et cascades (ST-G04) ─────────────────────────────────────────── */

export type CibleEtape = "initiaux" | "groupe" | "personne" | "prochain_disponible";
export type CritereArret = "prise_en_charge" | "toutes_lues";
export type CanalAlerte = "poussee" | "texto" | "courriel" | "appel";
export type EtatCascade = "armee" | "arretee" | "epuisee";

export const CIBLES: ReadonlyArray<{ code: CibleEtape; libelle: string }> = [
  { code: "initiaux", libelle: "Relancer les destinataires qui n'ont pas réagi" },
  { code: "groupe", libelle: "Escalader à tout un groupe" },
  { code: "personne", libelle: "Escalader à une personne" },
  { code: "prochain_disponible", libelle: "Le prochain disponible d'un groupe" },
];
export const ARRETS: ReadonlyArray<{ code: CritereArret; libelle: string }> = [
  { code: "prise_en_charge", libelle: "Dès qu'une personne prend en charge" },
  { code: "toutes_lues", libelle: "Dès que tous ont lu" },
];
export const CANAUX: ReadonlyArray<{ code: CanalAlerte; libelle: string }> = [
  { code: "poussee", libelle: "Poussée" }, { code: "texto", libelle: "Texto" }, { code: "courriel", libelle: "Courriel" },
  { code: "appel", libelle: "Appel automatique" },
];
const RESULTATS_APPEL: Record<string, string> = {
  lance: "En cours", repondu: "Répondu au clavier", decroche: "Décroché, sans touche", messagerie: "Messagerie vocale",
  occupe: "Occupé", sans_reponse: "Sans réponse", echec: "Échec (ou sans numéro)",
};
export const libelleResultatAppel = (r: string) => RESULTATS_APPEL[r] ?? r;
const ETATS_CASCADE: Record<EtatCascade, string> = { armee: "En cours", arretee: "Arrêtée", epuisee: "Toutes les étapes sont parties" };
const EVENEMENTS: Record<string, string> = { armee: "Armée à l'envoi", etape: "Étape exécutée", arretee: "Arrêtée", epuisee: "Épuisée", reprise: "Reprise manuelle" };

export const libelleCible = (c: string) => CIBLES.find((x) => x.code === c)?.libelle ?? c;
export const libelleArret = (c: string) => ARRETS.find((x) => x.code === c)?.libelle ?? c;
export const libelleCanal = (c: string) => CANAUX.find((x) => x.code === c)?.libelle ?? c;
export const libelleEtatCascade = (e: string) => ETATS_CASCADE[e as EtatCascade] ?? e;
export const libelleEvenement = (e: string) => EVENEMENTS[e] ?? e;

export interface EtapePolitique { cible: CibleEtape; cibleId: string | null; canaux: CanalAlerte[]; delaiMinutes: number }
export interface Politique { id: string; nom: string; arret: CritereArret; version: number; actif: boolean; etapes: EtapePolitique[] }
export interface TraceCascade { evenement: string; etape: number; personnes: string[]; instant: string; par: string }
export interface AppelCascade { personId: string; etape: number; resultat: string; touche: string | null; dureeSecondes: number | null; lanceLe: string; termineLe: string | null }
export interface SuiviCascade {
  cascade: { politique: Politique; etape: number; etapes: number; echeanceLe: string | null; etat: EtatCascade } | null;
  traces: TraceCascade[];
  appels?: AppelCascade[];
}

/** Une étape en mots : « Après 5 min : relancer…, par poussée ». */
export function etapeEnMots(e: EtapePolitique): string {
  const cible = e.cibleId ? `${libelleCible(e.cible)} (${e.cibleId})` : libelleCible(e.cible);
  return `Après ${e.delaiMinutes} min : ${cible}, par ${e.canaux.map(libelleCanal).join(", ")}`;
}
