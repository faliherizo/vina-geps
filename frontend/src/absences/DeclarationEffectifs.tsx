/**
 * **Les champs d'une absence d'effectifs** dans le formulaire unique (ADR 0026, ADR 0034 ; AO
 * 26-015-DO, lot R5). Le motif est un **code d'absence** du référentiel de l'organisme — pas un
 * texte libre — parce que les règles et la paie le lisent ; le commentaire, lui, est libre.
 *
 * L'employé déclare pour lui-même ; le planificateur choisit la personne parmi les ressources de
 * son périmètre (le serveur refuse les autres).
 */
import { useEffect, useState } from "react";
import type { ChampsAbsence } from "./FormulaireAbsence";

interface CodeAbsence { code: string; libelleFr: string; attributs: { duree?: string; planifiable?: boolean } }
interface RessourceLite { id: string; matricule: string; nom: string }

const DUREES: Record<string, string> = { PONCTUELLE: "ponctuelle", MOYEN_TERME: "moyen terme", LONG_TERME: "long terme" };

/** La durée et la planification du code, dites dans l'option : « Maladie (ponctuelle, jour même seulement) ». */
function libelleAttributs(k: CodeAbsence): string {
  const parties = [k.attributs.duree ? DUREES[k.attributs.duree] ?? k.attributs.duree : "", k.attributs.planifiable === false ? "jour même seulement" : ""].filter(Boolean);
  return parties.length ? ` (${parties.join(", ")})` : "";
}

interface Props {
  apiFetch: (chemin: string, init?: RequestInit) => Promise<Response>;
  c: ChampsAbsence;
  maj: (partiel: Partial<ChampsAbsence>) => void;
  estEmploye: boolean;
}

export default function DeclarationEffectifs({ apiFetch, c, maj, estEmploye }: Props) {
  const [codes, setCodes] = useState<CodeAbsence[]>([]);
  const [ressources, setRessources] = useState<RessourceLite[]>([]);

  useEffect(() => {
    let annule = false;
    apiFetch("/api/effectifs/referentiels/codes_absence").then((r) => (r.ok ? r.json() : [])).catch(() => []).then((l: CodeAbsence[]) => { if (!annule) setCodes(l); });
    if (!estEmploye) {
      apiFetch("/api/effectifs/planification/ressources").then((r) => (r.ok ? r.json() : [])).catch(() => []).then((l: RessourceLite[]) => { if (!annule) setRessources(l); });
    }
    return () => { annule = true; };
  }, [apiFetch, estEmploye]);

  return (
    <>
      {!estEmploye && (
        <div>
          <label htmlFor="f-abs-eff-ressource">Personne absente</label>
          <select id="f-abs-eff-ressource" data-testid="abs-eff-ressource" value={c.personneId} onChange={(e) => maj({ personneId: e.target.value })}
                  aria-describedby="f-abs-eff-ressource-aide">
            <option value="">Moi-même</option>
            {ressources.map((r) => <option key={r.id} value={r.id}>{r.nom} ({r.matricule})</option>)}
          </select>
          <span className="aide" id="f-abs-eff-ressource-aide">Les ressources de votre périmètre de planification.</span>
        </div>
      )}
      <div>
        <label htmlFor="f-abs-eff-code">Code d'absence <span className="req-star" aria-hidden="true">*</span></label>
        <select id="f-abs-eff-code" data-testid="abs-eff-code" required aria-required="true" value={c.motif} onChange={(e) => maj({ motif: e.target.value })}
                aria-describedby="f-abs-eff-code-aide">
          <option value="">— Choisir —</option>
          {codes.map((k) => <option key={k.code} value={k.code}>{k.libelleFr}{libelleAttributs(k)}</option>)}
        </select>
        <span className="aide" id="f-abs-eff-code-aide" data-testid="abs-eff-code-aide">
          {codes.find((k) => k.code === c.motif)?.attributs.planifiable === false
            ? "Ce code ne se planifie pas d'avance : il se déclare à partir du premier jour de l'absence."
            : "Un code « jour même » ne se déclare pas d'avance ; les autres peuvent être planifiés."}
        </span>
      </div>
      <div style={{ gridColumn: "1 / -1" }}>
        <label htmlFor="f-abs-eff-commentaire">Commentaire</label>
        <textarea id="f-abs-eff-commentaire" data-testid="abs-eff-commentaire" rows={2} maxLength={500} value={c.commentaire} onChange={(e) => maj({ commentaire: e.target.value })} />
      </div>
    </>
  );
}
