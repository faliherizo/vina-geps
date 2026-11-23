/**
 * **Déclarer une absence d'effectifs** : qui (soi-même par défaut), du, au, le code d'absence et un
 * commentaire. La déclaration part vers `POST /api/effectifs/absences`, qui la juge selon les règles
 * publiées : acceptée d'office, à approuver, ou refusée avec ses motifs — refusée, elle est tout de
 * même enregistrée, pour que le gestionnaire puisse l'accorder par dérogation.
 */
import { useState } from "react";
import { Message } from "../composants";
import DeclarationEffectifs from "./DeclarationEffectifs";
import LienPrealables, { estSansRessource } from "../aide/LienPrealables";

export interface AbsenceDeclaree {
  categorie: "effectifs"; ids: string[];
  /** La décision automatique, ses motifs, les places rendues vacantes. */
  statut?: "ACCEPTEE" | "A_APPROUVER" | "REFUSEE_AUTO"; motifs?: Array<{ cle: string; message: string }>; placesLiberees?: number;
}

export interface ChampsAbsence {
  personneId: string; du: string; au: string; motif: string; commentaire: string;
}

const aujourdhui = () => new Date().toISOString().slice(0, 10);

export const CHAMPS_VIDES: ChampsAbsence = { personneId: "", du: aujourdhui(), au: aujourdhui(), motif: "", commentaire: "" };

/** Ce que l'écran vérifie avant d'envoyer : les messages disent quoi corriger (WCAG 3.3.3). */
export function manques(c: ChampsAbsence): string[] {
  const m: string[] = [];
  if (!c.du) m.push("Indiquez la date de début.");
  if (c.au && c.au < c.du) m.push("La date de fin doit suivre la date de début.");
  if (!c.motif) m.push("Choisissez le code d'absence.");
  return m;
}

/** Le motif est le code ; la personne n'est envoyée que si elle a été choisie (sinon, soi-même). */
export function corpsDeclaration(c: ChampsAbsence): Record<string, unknown> {
  const corps: Record<string, unknown> = { du: c.du, au: c.au || c.du, motif: c.motif };
  if (c.personneId) corps.personneId = c.personneId;
  if (c.commentaire.trim()) corps.commentaire = c.commentaire.trim();
  return corps;
}

interface Props {
  apiFetch: (chemin: string, init?: RequestInit) => Promise<Response>;
  estEmploye: boolean;
  onDeclaree: (r: AbsenceDeclaree) => void;
}

export default function FormulaireAbsence({ apiFetch, estEmploye, onDeclaree }: Props) {
  const [c, setC] = useState<ChampsAbsence>(CHAMPS_VIDES);
  const [erreur, setErreur] = useState("");
  const [envoi, setEnvoi] = useState(false);
  const maj = (partiel: Partial<ChampsAbsence>) => setC((prev) => ({ ...prev, ...partiel }));

  async function envoyer() {
    setErreur("");
    const m = manques(c);
    if (m.length) { setErreur(m.join(" ")); return; }
    setEnvoi(true);
    try {
      const r = await apiFetch("/api/effectifs/absences", { method: "POST", body: JSON.stringify(corpsDeclaration(c)) });
      const j = await r.json().catch(() => ({}));
      if (!r.ok) { setErreur(Array.isArray(j.errors) ? j.errors.join(" ; ") : `Erreur ${r.status}`); return; }
      onDeclaree(j as AbsenceDeclaree);
      setC({ ...CHAMPS_VIDES, personneId: c.personneId });
    } finally { setEnvoi(false); }
  }

  return (
    <form data-testid="abs-formulaire-champs" onSubmit={(e) => { e.preventDefault(); void envoyer(); }} noValidate>
      {/* Le message du serveur reste tel quel ; un refus que la personne ne lève pas seule y gagne le chemin de ce qui manque. */}
      {erreur && (
        <Message variante="erreur" testId="abs-form-error">
          {erreur}
          {estSansRessource(erreur) && <> <LienPrealables page="mesabsences" testId="abs-form-error-prealables" /></>}
        </Message>
      )}
      <div className="row-form">
        <div>
          <label htmlFor="f-abs-start">Du <span className="req-star" aria-hidden="true">*</span></label>
          <input id="f-abs-start" data-testid="abs-start" type="date" required aria-required="true" value={c.du}
                 onChange={(e) => maj({ du: e.target.value, au: e.target.value > c.au ? e.target.value : c.au })} />
        </div>
        <div>
          <label htmlFor="f-abs-end">Au</label>
          <input id="f-abs-end" data-testid="abs-end" type="date" min={c.du} value={c.au} onChange={(e) => maj({ au: e.target.value })} />
        </div>
        <DeclarationEffectifs apiFetch={apiFetch} c={c} maj={maj} estEmploye={estEmploye} />
      </div>
      <div className="actions-row">
        <button type="submit" className="primary" data-testid="abs-submit" disabled={envoi}>Déclarer</button>
      </div>
    </form>
  );
}
