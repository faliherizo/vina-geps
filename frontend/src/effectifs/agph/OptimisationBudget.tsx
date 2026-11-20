/**
 * **Proposition d'optimisation du budget d'heures** d'une campagne AGPH (E-CH-1 ; addenda 7, Q11 ; ADR 0087, AD-04).
 *
 * La solution calcule, parmi les postes offerts que couvre au moins une semaine type déposée, l'ensemble qui
 * maximise les heures hebdomadaires sans dépasser le budget. C'est une proposition : rien n'est modifié, le service
 * de planification décide.
 */
import { useState } from "react";
import { Message } from "../../composants";
import { liste, objet } from "../../formeRecue";
import { useApiEffectifs } from "../api";

export interface PosteJuge { id: string; code: string; libelle: string; heuresSemaine: number; candidats: number; motif: string | null; motifLibelle: string | null }
export interface Proposition { budgetHeures: number; totalHeures: number; resteHeures: number; retenus: PosteJuge[]; ecartes: PosteJuge[] }

const heures = (n: number) => `${n.toLocaleString("fr-CA")} h`;

/** La phrase qui résume la proposition, lue par le lecteur d'écran à son arrivée. */
export const resumeProposition = (p: Proposition): string =>
  `${p.retenus.length} poste(s) retenu(s) pour ${heures(p.totalHeures)} par semaine, sur un budget de ${heures(p.budgetHeures)} : il reste ${heures(p.resteHeures)}.`;

function TablePostes({ postes, legende, testId, avecMotif }: { postes: PosteJuge[]; legende: string; testId: string; avecMotif: boolean }) {
  return (
    <table className="eff-table-compacte" data-testid={testId}>
      <caption>{legende}</caption>
      <thead>
        <tr>
          <th scope="col">Poste</th><th scope="col">Heures par semaine</th><th scope="col">Semaines types qui le couvrent</th>
          {avecMotif && <th scope="col">Motif</th>}
        </tr>
      </thead>
      <tbody>
        {postes.map((p) => (
          <tr key={p.id}>
            <th scope="row">{p.code} — {p.libelle}</th>
            <td className="col-numerique">{heures(p.heuresSemaine)}</td>
            <td className="col-numerique">{p.candidats}</td>
            {avecMotif && <td>{p.motifLibelle ?? "—"}</td>}
          </tr>
        ))}
      </tbody>
    </table>
  );
}

export default function OptimisationBudget({ campagneId }: { campagneId: string }) {
  const { lire, erreur } = useApiEffectifs();
  const [proposition, setProposition] = useState<Proposition | null>(null);
  const proposer = async () => {
    setProposition(objet<Proposition>(await lire<Proposition>(`/api/effectifs/agph/campagnes/${campagneId}/optimisation`)));
  };
  return (
    <section aria-labelledby="pla-agph-optim-titre" data-testid="pla-agph-optim">
      <h5 id="pla-agph-optim-titre">Optimisation du budget</h5>
      <p>La solution propose les postes à offrir qui couvrent le plus d'heures sans dépasser le budget, parmi ceux qu'au moins une semaine type déposée couvre. Rien n'est modifié.</p>
      <p className="eff-actions"><button type="button" className="secondary" data-testid="pla-agph-optim-proposer" onClick={() => void proposer()}>Proposer une optimisation</button></p>
      {erreur && <Message variante="erreur" testId="pla-agph-optim-erreur">{erreur}</Message>}
      {proposition && (
        <>
          <p role="status" data-testid="pla-agph-optim-resume">{resumeProposition(proposition)}</p>
          {liste<PosteJuge>(proposition.retenus).length > 0
            && <TablePostes postes={proposition.retenus} legende="Postes proposés" testId="pla-agph-optim-retenus" avecMotif={false} />}
          {liste<PosteJuge>(proposition.ecartes).length > 0
            && <TablePostes postes={proposition.ecartes} legende="Postes écartés et pourquoi" testId="pla-agph-optim-ecartes" avecMotif />}
        </>
      )}
    </section>
  );
}
