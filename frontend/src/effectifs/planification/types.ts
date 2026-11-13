/** Les formes renvoyées par `/api/effectifs/planification` (lot R3). */
export interface Periode {
  id: string; uniteCode: string; nom: string; debut: string; fin: string;
  etat: "PREPARATION" | "COLLECTE" | "CONFECTION" | "PUBLIEE" | "CLOSE"; finCollecte: string | null;
}
export interface Ressource {
  id: string; matricule: string; nom: string; uniteCode: string; corpsEmploiCode: string; titreEmploiCode: string | null;
  statutEmploiCode: string | null; conventionCode: string | null; anciennete: number; actif: boolean;
}
export interface Poste {
  id: string; uniteCode: string; code: string; libelle: string; statutEmploiCode: string; corpsEmploiCode: string; milieuId: string;
  natureCode: string; cycle: Array<Array<string | null>>; debutCycle: string; parametres: Record<string, string | number | boolean>;
  etat: "BROUILLON" | "PUBLIE" | "ATTRIBUE" | "VACANT"; titulaireId: string | null;
}
/** Ce que la modification d'un poste a fait à ses quarts futurs déjà générés (E-CH-3). */
export interface Repercussion {
  deplacees: number; requalifiees: number; retirees: number; ajoutees: number; pointeesIgnorees: number;
  conflits: Array<{ placeId: string; date: string; ressourceId: string; motif: string }>;
}
export type PosteModifie = Poste & { repercussion: Repercussion };
export interface Place {
  id: string; rang: number; corpsEmploiCode: string; posteId: string | null; ressourceId: string | null; ressourceNom: string | null;
  attribueePar: string | null; justificationForcage: string | null;
}
export interface Equipage {
  id: string; periodeId: string; date: string; debut: number; fin: number; natureCode: string; plageCode: string | null; milieuId: string;
  modeAttribution: "AUTOMATISE" | "MANUEL"; minimumOperationnel: number; note: string | null; places: Place[]; pourvues: number;
  statut: "OPERATIONNEL" | "SOUS_MINIMUM";
}
export interface Comblement {
  equipages: number; places: number; pourvues: number; taux: number;
  sousMinimum: Array<{ equipageId: string; date: string; debut: number; fin: number; milieuId: string; pourvues: number; minimum: number; mode: string }>;
  quotas: Array<{ cle: string; plageCode: string | null; natureCode: string | null; jours: Array<{ date: string; quota: number | null; origine: string | null; operationnels: number; atteint: boolean }> }>;
}
export interface Quota {
  cle: string; uniteCode: string; plageCode: string | null; natureCode: string | null;
  versions: Array<{ id: string; quota: number; effectifDepuis: string; effectifJusqua: string | null }>;
  surcharges: Array<{ id: string; valeur: number; depuis: string; jusqua: string; motif: string }>;
  enVigueur: { quota: number; origine: string } | null;
}
export interface Motif { cle: string; action: string; message: string }
export interface Verdict { action: string; motifs: Motif[] }
