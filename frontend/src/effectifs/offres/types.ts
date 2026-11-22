/**
 * Les formes de l'offre d'un quart par ancienneté et à tour de rôle (AD-07 ; art. 15.10, 16.02, 16.03 ; ADR 0089),
 * telles que `/api/effectifs/offres` les rend.
 */
export type EtatOffre = "OUVERTE" | "POURVUE" | "EPUISEE" | "RETIREE";
export type EtatRang = "EN_ATTENTE" | "OFFERT" | "ACCEPTE" | "REFUSE" | "EXPIRE" | "ECARTE" | "CLOS";
export type ModeOffre = "SIMULTANEE" | "CASCADE";

export interface RangOffre {
  rang: number; ressourceId: string; nom: string | null; anciennete: number; heuresComptees: number; heuresOffertes: number;
  etat: EtatRang; offertLe: string | null; echeance: string | null; reponduLe: string | null; motif: string | null;
}

export interface OffreQuart {
  id: string; comblementId: string; absenceId: string; urgente: boolean; etat: EtatOffre; creeLe: string; attribueeA: string | null;
  quart: { placeId: string; uniteCode: string; date: string; debut: number; fin: number };
  rangs: RangOffre[];
}

export interface ParametresOffres { mode: ModeOffre; delaiReponseMinutes: number; seuilUrgenceMinutes: number; fenetreRepartitionJours: number }

/**
 * Les offres bien formées d'une réponse : une liste reçue d'une autre forme (route doublée, réponse partielle) ne
 * doit jamais faire tomber l'écran qui l'accueille — « Mon horaire » ou le comblement d'une absence.
 */
export function offresRecues(recu: unknown): OffreQuart[] {
  if (!Array.isArray(recu)) return [];
  return recu.filter((o): o is OffreQuart => !!o && typeof o === "object" && !!(o as OffreQuart).quart && Array.isArray((o as OffreQuart).rangs));
}

/** Chaque état se dit en mots, avec son ton : la couleur n'en porte jamais seule le sens (1.4.1). */
export const ETATS_RANG: Record<EtatRang, { libelle: string; ton: string }> = {
  EN_ATTENTE: { libelle: "En attente de son tour", ton: "en_attente" },
  OFFERT: { libelle: "Offert — réponse attendue", ton: "en_attente" },
  ACCEPTE: { libelle: "A accepté", ton: "approuvee" },
  REFUSE: { libelle: "A refusé (compté)", ton: "refusee" },
  EXPIRE: { libelle: "Sans réponse — réputé refus (compté)", ton: "refusee" },
  ECARTE: { libelle: "Écarté par une règle (non compté)", ton: "refusee" },
  CLOS: { libelle: "Offre close (non compté)", ton: "en_attente" },
};

export const ETATS_OFFRE: Record<EtatOffre, string> = {
  OUVERTE: "en cours",
  POURVUE: "pourvue",
  EPUISEE: "épuisée — la place va à la gestion régulière",
  RETIREE: "retirée — la place a été pourvue autrement",
};

/** « 2030-03-04 à 10:10 », à l'heure de l'établissement. */
export function heureLocale(instant: string | null): string {
  if (!instant) return "—";
  const d = new Date(instant);
  if (Number.isNaN(d.getTime())) return instant;
  const p = new Intl.DateTimeFormat("fr-CA", { timeZone: "America/Toronto", year: "numeric", month: "2-digit", day: "2-digit", hour: "2-digit", minute: "2-digit", hourCycle: "h23" })
    .formatToParts(d).reduce<Record<string, string>>((m, x) => ({ ...m, [x.type]: x.value }), {});
  return `${p.year}-${p.month}-${p.day} à ${p.hour}:${p.minute}`;
}
