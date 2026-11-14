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
export interface Modele { id: string; titre: string; corps: string; priorite: Priorite; reponseIds: string[]; actif: boolean }
export interface Groupe { id: string; nom: string }
export interface Personne { personId: string; nom: string }
export interface Remise { personId: string; nom: string; envoyeeLe: string; lueLe: string | null; reponseId: string | null; reponduLe: string | null }
export interface AlerteBase { id: string; titre: string; corps: string; priorite: Priorite; expediteurId: string; envoyeeLe: string; reponses: Reponse[] }
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
