/**
 * **Copies de l'organisme** — AO 26-015-DO E-TN-6 (« les données sauvegardées et archivées sont
 * séparées de celles des autres clients »).
 *
 * Chaque sauvegarde complète de l'instance donne une copie qui ne contient que les données de
 * l'organisme. L'administrateur les liste et les télécharge ici ; le téléchargement déclenche les
 * règles d'alerte « export téléchargé » de l'organisme, comme tout export.
 */
import { useCallback, useEffect, useState } from "react";
import { EtatVide, Message } from "../composants";
import { liste, objet } from "../formeRecue";

type Appel = (chemin: string, init?: RequestInit) => Promise<Response>;
export interface Copie { fichier: string; faiteLe: string; octets: number }

/** « 2026-11-10T08-00-00-000Z » → « 2026-11-10 08:00 UTC » : l'instant lisible, sans fuseau inventé. */
export function instantLisible(brut: string): string {
  const m = /^(\d{4}-\d{2}-\d{2})T(\d{2})-(\d{2})/.exec(brut);
  return m ? `${m[1]} ${m[2]}:${m[3]} UTC` : brut;
}

export function taille(octets: number): string {
  if (octets < 1024) return `${octets} o`;
  if (octets < 1024 * 1024) return `${(octets / 1024).toFixed(1)} Ko`;
  return `${(octets / 1024 / 1024).toFixed(1)} Mo`;
}

export default function CopiesOrganisme({ apiFetch }: { apiFetch: Appel }) {
  const [copies, setCopies] = useState<Copie[]>([]);
  const [erreur, setErreur] = useState("");

  const charger = useCallback(async () => {
    const r = await apiFetch("/api/exploitation/copies-organisme");
    setCopies(liste<Copie>(objet<{ copies: Copie[] }>(r.ok ? await r.json() : null)?.copies));
  }, [apiFetch]);
  useEffect(() => { charger().catch(() => setErreur("Les copies n'ont pas pu être lues.")); }, [charger]);

  async function telecharger(c: Copie) {
    setErreur("");
    const r = await apiFetch(`/api/exploitation/copies-organisme/${encodeURIComponent(c.fichier)}`);
    if (!r.ok) { setErreur(`Le téléchargement a échoué (erreur ${r.status}).`); return; }
    const url = URL.createObjectURL(await r.blob());
    const a = document.createElement("a");
    a.href = url;
    a.download = c.fichier;
    a.click();
    URL.revokeObjectURL(url);
  }

  return (
    <section className="card" aria-labelledby="copies-titre" data-testid="copies-organisme">
      <h3 id="copies-titre">Copies de l'organisme</h3>
      <p className="muted">
        Une copie par sauvegarde complète, qui ne contient que les données de l'organisme. Le format est une base SQLite,
        lisible par tout outil SQL. Les plus anciennes sont supprimées selon la rétention des sauvegardes.
      </p>
      {erreur && <Message variante="erreur" testId="copies-erreur">{erreur}</Message>}
      {copies.length === 0 ? <EtatVide testId="copies-vide" titre="Aucune copie pour l'instant" compact /> : (
        <div className="table-wrap" tabIndex={0} role="region" aria-label="Copies (zone défilable au clavier)">
          <table data-testid="copies-table">
            <caption className="sr-only">Copies des données de l'organisme</caption>
            <thead><tr><th scope="col">Faite le</th><th scope="col">Taille</th><th scope="col">Action</th></tr></thead>
            <tbody>
              {copies.map((c) => (
                <tr key={c.fichier}>
                  <th scope="row">{instantLisible(c.faiteLe)}</th>
                  <td>{taille(c.octets)}</td>
                  <td>
                    <button type="button" className="secondary" data-testid={`copie-${c.faiteLe}-telecharger`} onClick={() => telecharger(c)}
                      aria-label={`Télécharger la copie du ${instantLisible(c.faiteLe)}`}>Télécharger</button>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </section>
  );
}
