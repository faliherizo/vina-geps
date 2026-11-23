/**
 * **Remises et relevés** (ADR 0076) — ce que l'employeur verse chaque mois à Revenu Québec et à l'ARC, et les cases
 * des relevés de l'année (RL-1, T4). Seuls les cycles traités comptent.
 */
import { useCallback, useEffect, useState } from "react";
import { EnTeteEcran, EtatVide, Message, Tableau, type Colonne } from "../composants";
import { useApiEffectifs as useApi } from "../effectifs/api";
import { liste } from "../formeRecue";
import { argent, type Releve, type Remise } from "./types";

const COL_REMISES: ReadonlyArray<Colonne<Remise>> = [
  { id: "mois", libelle: "Mois de paie", tri: (r) => r.mois }, { id: "echeance", libelle: "À verser au plus tard" },
  { id: "rq", libelle: "Revenu Québec", numerique: true }, { id: "arc", libelle: "ARC", numerique: true },
  { id: "cnt", libelle: "CNT (annuelle)", numerique: true }, { id: "cnesst", libelle: "CNESST", numerique: true },
];
const COL_RELEVES: ReadonlyArray<Colonne<Releve>> = [
  { id: "nom", libelle: "Personne", tri: (r) => r.nom }, { id: "a", libelle: "RL-1 A · T4 14", numerique: true },
  { id: "e", libelle: "RL-1 E (impôt Québec)", numerique: true }, { id: "t22", libelle: "T4 22 (impôt fédéral)", numerique: true },
  { id: "b", libelle: "RL-1 B · T4 17 (RRQ)", numerique: true }, { id: "rrq2", libelle: "dont RRQ2", numerique: true },
  { id: "c", libelle: "RL-1 C · T4 18 (AE)", numerique: true }, { id: "h", libelle: "RL-1 H · T4 55 (RQAP)", numerique: true },
];

export default function DeclarationsPaie() {
  const { lire, erreur } = useApi();
  const [annee, setAnnee] = useState(String(new Date().getFullYear()));
  const [remises, setRemises] = useState<Remise[]>([]);
  const [releves, setReleves] = useState<Releve[]>([]);
  const charger = useCallback(async () => {
    setRemises(liste<Remise>(await lire<Remise[]>(`/api/paie/remises?annee=${annee}`)));
    setReleves(liste<Releve>(await lire<Releve[]>(`/api/paie/releves?annee=${annee}`)));
  }, [lire, annee]);
  useEffect(() => { void charger(); }, [charger]);

  return (
    <div data-testid="pde-ecran">
      <EnTeteEcran titre="Remises et relevés" testId="pde-titre"
        description="Les retenues et les cotisations à verser chaque mois, et les cases des relevés de l'année, tirées des seules paies traitées." />
      {erreur && <Message variante="erreur" testId="pde-erreur">{erreur}</Message>}
      <p><label htmlFor="pde-annee">Année</label>{" "}
        <select id="pde-annee" data-testid="pde-annee" value={annee} onChange={(e) => setAnnee(e.target.value)}>
          {[0, 1, 2].map((n) => { const a = String(new Date().getFullYear() - n); return <option key={a} value={a}>{a}</option>; })}
        </select></p>
      <section className="card" aria-labelledby="pde-remises-h">
        <h2 id="pde-remises-h">Remises mensuelles</h2>
        <p className="muted">Revenu Québec : impôt du Québec, RRQ et RQAP (parts de la personne et de l'employeur), FSS. ARC : impôt fédéral et assurance-emploi (les deux parts).</p>
        <Tableau colonnes={COL_REMISES} lignes={remises} legende={`Remises de ${annee}`} testId="pde-remises"
          vide={<EtatVide titre="Aucune remise" compact testId="pde-remises-vide">Aucune paie traitée cette année.</EtatVide>}
          rendre={(r) => (
            <tr key={r.mois} data-testid={`pde-remise-${r.mois}`}>
              <td>{r.mois}</td><td>{r.echeance}</td><td className="num">{argent(r.revenuQuebec)}</td><td className="num">{argent(r.arc)}</td>
              <td className="num">{argent(r.cnt)}</td><td className="num">{argent(r.cnesst)}</td>
            </tr>
          )} />
      </section>
      <section className="card" aria-labelledby="pde-releves-h">
        <h2 id="pde-releves-h">Relevés de l'année (RL-1 et T4)</h2>
        <p className="muted">Les cases à reporter sur les relevés ; la transmission électronique se fait hors du produit.</p>
        <Tableau colonnes={COL_RELEVES} lignes={releves} legende={`Cases des relevés de ${annee}`} testId="pde-releves"
          vide={<EtatVide titre="Aucun relevé" compact testId="pde-releves-vide">Aucune paie traitée cette année.</EtatVide>}
          rendre={(r) => (
            <tr key={r.ressourceId} data-testid={`pde-releve-${r.ressourceId}`}>
              <td>{r.nom} ({r.matricule})</td><td className="num">{argent(r.rl1.a)}</td><td className="num">{argent(r.rl1.e)}</td>
              <td className="num">{argent(r.t4.c22)}</td><td className="num">{argent(r.rl1.b)}</td><td className="num">{argent(r.rl1.rrq2)}</td>
              <td className="num">{argent(r.rl1.c)}</td><td className="num">{argent(r.rl1.h)}</td>
            </tr>
          )} />
      </section>
    </div>
  );
}
