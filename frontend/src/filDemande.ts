/**
 * **Le fil à montrer en arrivant dans la messagerie.** Le demandeur (la barre du haut, un formulaire voisin) et la
 * section « Lire et écrire » ne sont pas toujours montés ensemble : la demande est gardée jusqu'à ce que la liste des
 * conversations la contienne, et un signal prévient la section déjà affichée.
 */
const FIL_DEMANDE = "vina:fil-ouvert";
let enAttente = "";

export function demanderFil(id: string): void {
  enAttente = id;
  window.dispatchEvent(new CustomEvent<string>(FIL_DEMANDE, { detail: id }));
}

export const filEnAttente = (): string => enAttente;

export function oublierFil(): void { enAttente = ""; }

/** Écoute les demandes ; rend de quoi se désabonner. */
export function ecouterFil(recevoir: (id: string) => void): () => void {
  const noter = (e: Event) => recevoir((e as CustomEvent<string>).detail);
  window.addEventListener(FIL_DEMANDE, noter);
  return () => window.removeEventListener(FIL_DEMANDE, noter);
}
