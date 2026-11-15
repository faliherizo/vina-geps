/**
 * **Utilisateurs actifs** (ST-G08, bordereau) — combien de personnes une alerte a visées dans le mois civil (heure de
 * Toronto), une fois chacune, avec leur première tentative et l'alerte en cause ; le justificatif s'exporte.
 */
import { useCallback, useEffect, useMemo, useState } from "react";
import { EtatVide, Message, Tableau, type Colonne } from "../composants";
import ExportResultats from "../effectifs/ExportResultats";
import { useApiEffectifs as useApi } from "../effectifs/api";
import { quand } from "./types";

export interface Actif { personId: string; nom: string; premiereTentative: string; alerteId: string; titre: string }
export interface BilanActifs { mois: string; total: number; actifs: Actif[] }

const COL: ReadonlyArray<Colonne<Actif>> = [
  { id: "nom", libelle: "Personne", tri: (a) => a.nom }, { id: "premiere", libelle: "Première tentative", tri: (a) => a.premiereTentative },
  { id: "alerte", libelle: "Alerte en cause" },
];

/** Le mois courant, « AAAA-MM », à l'heure de Toronto. */
export function moisCourant(maintenant: Date = new Date()): string {
  const p = new Intl.DateTimeFormat("fr-CA", { timeZone: "America/Toronto", year: "numeric", month: "2-digit" }).formatToParts(maintenant);
  return `${p.find((x) => x.type === "year")!.value}-${p.find((x) => x.type === "month")!.value}`;
}

export default function UtilisateursActifs() {
  const { lire, erreur } = useApi();
  const [mois, setMois] = useState(moisCourant());
  const [bilan, setBilan] = useState<BilanActifs | null>(null);
  const charger = useCallback(async () => setBilan(await lire<BilanActifs>(`/api/alertes/utilisateurs-actifs?mois=${mois}`)), [lire, mois]);
  useEffect(() => { void charger(); }, [charger]);
  const parametres = useMemo(() => new URLSearchParams({ mois }), [mois]);

  return (
    <section className="card" aria-labelledby="alp-actifs-h" data-testid="alp-actifs">
      <h2 id="alp-actifs-h">Utilisateurs actifs</h2>
      <p className="muted">Une personne compte une fois par mois, dès qu'une alerte, une relance ou un appel la vise.</p>
      {erreur && <Message variante="erreur" testId="alp-actifs-erreur">{erreur}</Message>}
      <div className="eff-barre">
        <p><label htmlFor="alp-actifs-mois">Mois</label>
          <input id="alp-actifs-mois" data-testid="alp-actifs-mois" type="month" required value={mois} onChange={(e) => e.target.value && setMois(e.target.value)} /></p>
        <ExportResultats chemin="/api/alertes/utilisateurs-actifs/export" parametres={parametres} nomFichier={`utilisateurs-actifs-${mois}`} prefixe="alp-actifs-export" />
      </div>
      {bilan && <p role="status" data-testid="alp-actifs-total"><strong>{bilan.total}</strong> utilisateur{bilan.total > 1 ? "s" : ""} actif{bilan.total > 1 ? "s" : ""} en {bilan.mois}.</p>}
      <Tableau colonnes={COL} lignes={bilan?.actifs ?? []} legende={`Utilisateurs actifs de ${mois}`} testId="alp-actifs-table"
        vide={<EtatVide titre="Personne ce mois-ci" compact testId="alp-actifs-vide">Aucune alerte n'a visé personne pendant ce mois.</EtatVide>}
        rendre={(a) => (
          <tr key={a.personId} data-testid={`alp-actif-${a.personId}`}>
            <td>{a.nom}</td><td>{quand(a.premiereTentative)}</td><td>{a.titre}</td>
          </tr>
        )} />
    </section>
  );
}
