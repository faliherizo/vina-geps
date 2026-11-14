/**
 * **Mes prochains quarts** — le tableau de bord de l'employé (planche s02).
 *
 * Les sept prochains jours, lus à la même route que « Mon horaire » : la donnée a un seul foyer.
 * Sans le module de planification, rien n'est lu et rien n'est affiché. Un compte qui n'est relié
 * à aucune ressource le lit en mots, avec la marche à suivre, plutôt qu'une table vide.
 */
import { useEffect, useState } from "react";
import { useAuth } from "../AuthContext";
import { useModuleAllowed } from "../moduleAccess";
import { objet } from "../formeRecue";
import { lienVersPage } from "../navigation";
import { EtatVide, Tableau, type Colonne } from "../composants";
import { heure } from "../effectifs/api";
import { jourEnMots } from "../MonHoraire";
import type { QuartEchangeable } from "../effectifs/echanges/types";

/** Combien de jours le tableau de bord regarde devant lui. */
const HORIZON_JOURS = 7;
const JOUR_MS = 86_400_000;

/** La fenêtre [jour, jour + 6] — calculée à midi UTC, pour qu'un changement d'heure ne la décale pas. */
export function horizonDe(jour: string): { du: string; au: string } {
  const fin = new Date(new Date(`${jour}T12:00:00Z`).getTime() + (HORIZON_JOURS - 1) * JOUR_MS);
  return { du: jour, au: fin.toISOString().slice(0, 10) };
}

const COLONNES: ReadonlyArray<Colonne<QuartEchangeable>> = [
  { id: "jour", libelle: "Jour", tri: (q) => q.date }, { id: "quart", libelle: "Quart" },
  { id: "site", libelle: "Site" }, { id: "poste", libelle: "Poste" },
];

type Lecture = { relie: boolean; quarts: QuartEchangeable[] } | null;

export default function ProchainsQuarts({ jour }: { jour: string }) {
  const { apiFetch } = useAuth();
  const avecPlanification = useModuleAllowed()("effectifs_planification");
  const [lu, setLu] = useState<Lecture | undefined>(undefined);
  useEffect(() => {
    if (!avecPlanification) return;
    let vivant = true;
    const { du, au } = horizonDe(jour);
    apiFetch(`/api/effectifs/mon-horaire?du=${du}&au=${au}`)
      .then((r) => (r.ok ? r.json() : null))
      .then((j) => {
        const m = objet<{ ressource: unknown; quarts: QuartEchangeable[] }>(j);
        if (vivant) setLu(m ? { relie: m.ressource !== null, quarts: Array.isArray(m.quarts) ? m.quarts : [] } : null);
      })
      .catch(() => { if (vivant) setLu(null); });
    return () => { vivant = false; };
  }, [apiFetch, jour, avecPlanification]);

  if (!avecPlanification || lu === undefined || lu === null) return null;
  return (
    <section className="card tdb-panneau" aria-labelledby="tdb-prochains" data-testid="tdb-prochains">
      <h2 id="tdb-prochains">Mes prochains quarts</h2>
      {lu.relie ? <TableDesQuarts quarts={lu.quarts} /> : (
        <EtatVide titre="Votre compte n'est relié à aucune ressource planifiable" compact testId="tdb-sans-ressource">
          Demandez à votre planificateur de relier votre compte à votre dossier d'effectif.
        </EtatVide>
      )}
      <p><a href={lienVersPage("monhoraire")} data-testid="tdb-vers-horaire">Voir tout mon horaire</a></p>
    </section>
  );
}

function TableDesQuarts({ quarts }: { quarts: QuartEchangeable[] }) {
  return (
    <Tableau colonnes={COLONNES} lignes={quarts} legende="Mes quarts des sept prochains jours" testId="tdb-table-quarts"
             vide={<EtatVide titre="Aucun quart publié dans les sept prochains jours" compact testId="tdb-aucun-quart">La prochaine période n'est peut-être pas encore publiée.</EtatVide>}
             rendre={(q) => (
               <tr key={q.placeId} data-testid={`tdb-mon-quart-${q.placeId}`}>
                 <td>{jourEnMots(q.date)}</td><td>{heure(q.debut)}–{heure(q.fin)}</td><td>{q.milieu ?? "—"}</td><td>{q.corpsEmploiCode}</td>
               </tr>
             )} />
  );
}
