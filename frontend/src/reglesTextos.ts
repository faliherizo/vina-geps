/**
 * Les textos vus de l'écran (grille 25, devis 2.6) : les mêmes règles que le serveur — numéro
 * E.164 ou nord-américain, texte borné — dites avant l'envoi plutôt qu'en 400 ; les libellés
 * des statuts et des fournisseurs ; le quota en une phrase ; et les canaux d'un compte, dont
 * au moins un canal écrit reste ouvert.
 */

export const LONGUEUR_MAX_TEXTO = 480;

export type StatutTexto = "en_attente" | "envoye" | "livre" | "echoue";

export interface Texto {
  id: number; numero: string; texte: string; statut: StatutTexto; fournisseur: string | null; identifiantFournisseur: string | null;
  creeLe: string; envoyeLe: string | null; erreur: string | null; contexte: string | null; expediteur: string | null;
}

export interface EtatTextos { fournisseur: string; simule: boolean; manquantes: string[]; rappel: string | null; appel: string }

export interface QuotaTextos { mois: string; limite: number; utilises: number; restants: number | null; illimite: boolean; longueurMax?: number }

export interface ResumeTextos { total: number; parStatut: Record<StatutTexto, number>; dernierLe: string | null }

/** Le journal en une ligne : « 12 textos — 9 livrés, 2 envoyés, 1 échoué ». */
export function decrireResume(r: ResumeTextos): string {
  if (r.total === 0) return "Aucun texto au journal.";
  const parts = (Object.keys(LIBELLE_STATUT) as StatutTexto[]).filter((s) => r.parStatut[s] > 0).map((s) => `${r.parStatut[s]} ${LIBELLE_STATUT[s].toLowerCase()}${r.parStatut[s] > 1 ? "s" : ""}`);
  return `${r.total} texto${r.total > 1 ? "s" : ""} au journal — ${parts.join(", ")}.`;
}

export interface Canaux { courriel: boolean; texto: boolean; poussee: boolean }

export const LIBELLE_STATUT: Record<StatutTexto, string> = { en_attente: "En attente", envoye: "Envoyé", livre: "Livré", echoue: "Échoué" };

export const LIBELLE_FOURNISSEUR: Record<string, string> = { twilio: "Twilio", telnyx: "Telnyx", http: "Passerelle HTTP", simule: "Simulateur (aucun envoi réel)" };

export function libelleFournisseur(code: string | null | undefined): string {
  return code ? LIBELLE_FOURNISSEUR[code] ?? code : "—";
}

/** Le refus du numéro, dans les mots de l'écran, ou rien s'il passe : « + » puis 8 à 15 chiffres, ou 10 chiffres nord-américains (11 avec le 1). */
export function refusNumero(numero: string): string | null {
  const brut = numero.trim();
  if (!brut) return "Le numéro est requis.";
  const chiffres = brut.replace(/[\s().-]/g, "");
  if (/^\+[1-9]\d{7,14}$/.test(chiffres)) return null;
  if (/^1?\d{10}$/.test(chiffres)) return null;
  return "Numéro invalide : format international (+15145551234) ou dix chiffres nord-américains.";
}

/** Le refus du texte : vide, ou plus long que ce que le fournisseur accepte en une fois. */
export function refusTexte(texte: string): string | null {
  const t = texte.trim();
  if (!t) return "Le message est requis.";
  if (t.length > LONGUEUR_MAX_TEXTO) return `Message trop long : ${t.length} caractères sur ${LONGUEUR_MAX_TEXTO} au plus.`;
  return null;
}

/** Le quota en une phrase : ce qui a été consommé ce mois-ci, et ce qui reste. */
export function decrireQuota(q: QuotaTextos): string {
  const mois = moisLisible(q.mois);
  if (q.illimite) return `${q.utilises} texto(s) envoyé(s) en ${mois} — sans limite.`;
  if (q.restants !== null && q.restants <= 0) return `Quota mensuel atteint : ${q.utilises} sur ${q.limite} en ${mois}. Plus aucun texto ne partira avant le mois prochain.`;
  return `${q.utilises} sur ${q.limite} texto(s) en ${mois} — ${q.restants} restant(s).`;
}

/** « 2026-09 » → « septembre 2026 ». */
export function moisLisible(mois: string): string {
  const [annee, m] = mois.split("-").map(Number);
  if (!annee || !m || m < 1 || m > 12) return mois;
  return `${new Date(Date.UTC(annee, m - 1, 1)).toLocaleDateString("fr-CA", { month: "long", timeZone: "UTC" })} ${annee}`;
}

/** Le refus d'un réglage de canaux : fermer le courriel et le texto à la fois laisserait la personne sans avis écrit. */
export function refusCanaux(c: Canaux): string | null {
  if (!c.courriel && !c.texto) return "Au moins un canal écrit (courriel ou texto) doit rester actif.";
  return null;
}

/** Un événement du catalogue des avis (ADR 0029), tel que le serveur le décrit. */
export interface EvenementNotifiable { code: string; libelle: string; module: string; moduleLibelle: string; destinataire: string; defauts: Canaux; essentiel: boolean }

/** Les préférences complètes : les canaux généraux et, par événement, ceux que la personne a réglés. */
export interface PreferencesAvis extends Canaux {
  evenements: Record<string, Canaux>;
  /** L'adresse personnelle où les avis par courriel partent **aussi** (CSSMB 21) ; `null` : la seule adresse institutionnelle. */
  courrielPersonnel?: string | null;
  updatedAt: string | null;
}

/** Les canaux d'un événement pour l'écran : ceux réglés par la personne, sinon les défauts du catalogue. */
export function canauxEffectifs(e: EvenementNotifiable, p: PreferencesAvis): Canaux {
  return p.evenements[e.code] ?? e.defauts;
}

/**
 * Le refus de la matrice, dit avant l'envoi : la règle générale, puis chaque événement essentiel
 * dont la personne aurait fermé le courriel et le texto. Rien si tout passe.
 */
export function refusMatrice(p: PreferencesAvis, evenements: EvenementNotifiable[]): string | null {
  const general = refusCanaux(p);
  if (general) return general;
  const fautif = evenements.find((e) => e.essentiel && !(p.courriel && canauxEffectifs(e, p).courriel) && !(p.texto && canauxEffectifs(e, p).texto));
  return fautif ? `${fautif.libelle} : avis essentiel, gardez le courriel ou le texto ouvert.` : null;
}

/** La couleur d'un statut, doublée du libellé (1.4.1) : le badge existant du projet. */
export function classeStatut(statut: StatutTexto): string {
  return statut === "livre" || statut === "envoye" ? "badge badge-active" : statut === "echoue" ? "badge badge-terminated" : "badge badge-rule";
}
