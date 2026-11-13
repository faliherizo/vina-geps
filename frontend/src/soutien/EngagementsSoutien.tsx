/**
 * **Engagements de service par organisme** — le soutien attribue à chaque organisme la politique
 * de son contrat : « standard » (résolution depuis l'ouverture, 99,5 %) ou « service essentiel »
 * (AO 26-015-DO, 5.02 et 6.01 : rappel puis résolution, soutien de 5 h à 23 h 7 j/7, 99,7 %).
 * Le changement est daté et signé par le serveur ; l'écran ne fait que le demander.
 */
import { useCallback, useEffect, useState } from "react";
import { Icone, Message } from "../composants";
import TbodyPagine from "../TbodyPagine";

interface PolitiqueVue { code: string; libelle: string }
interface Affectation { tenantId: string; nom: string; politique: string; libelle: string; modifieLe: string | null; modifiePar: string | null }
interface Engagements { politiques: PolitiqueVue[]; organismes: Affectation[] }

/** Le mois jugé contre une politique en usage, tel que `/api/soutien/disponibilite` le rend. */
export interface Verdict { politique: string; libelle: string; objectifPourcent: number; seuilManquementMajeurPourcent: number | null; manquement: string }

const LIBELLE_MANQUEMENT: Record<string, string> = {
  aucun: "objectif atteint", manquement: "manquement", manquement_majeur: "manquement majeur",
};

const pourcent = (n: number) => `${n.toLocaleString("fr-CA")} %`;

/** Le verdict du mois par politique : écrit en toutes lettres, jamais porté par la couleur seule. */
export function VerdictsDisponibilite({ verdicts }: { verdicts: Verdict[] }) {
  if (verdicts.length === 0) return null;
  return (
    <ul data-testid="sa-dispo-verdicts">
      {verdicts.map((v) => (
        <li key={v.politique} data-testid={`sa-dispo-verdict-${v.politique}`}>
          {v.libelle} — objectif {pourcent(v.objectifPourcent)}
          {v.seuilManquementMajeurPourcent !== null && <>, manquement majeur sous {pourcent(v.seuilManquementMajeurPourcent)}</>} :{" "}
          <strong>{LIBELLE_MANQUEMENT[v.manquement] ?? v.manquement}</strong>
        </li>
      ))}
    </ul>
  );
}

interface Props {
  apiFetch: (path: string, init?: RequestInit) => Promise<Response>;
  /** Après un changement : la console recharge billets et disponibilité, jugés autrement. */
  onChange?: () => unknown;
}

export default function EngagementsSoutien({ apiFetch, onChange }: Props) {
  const [donnees, setDonnees] = useState<Engagements | null>(null);
  const [annonce, setAnnonce] = useState("");
  const [erreur, setErreur] = useState("");
  const [filtre, setFiltre] = useState("");

  const charger = useCallback(async () => {
    const r = await apiFetch("/api/soutien/engagements");
    const corps: unknown = r.ok ? await r.json() : null;
    // Une réponse d'une autre forme (une liste vide, un objet partiel) ne doit pas effondrer la console.
    const lu = corps as Partial<Engagements> | null;
    if (!lu || !Array.isArray(lu.politiques) || !Array.isArray(lu.organismes)) { setErreur("Impossible de lire les engagements des organismes."); return; }
    setDonnees({ politiques: lu.politiques, organismes: lu.organismes });
  }, [apiFetch]);
  useEffect(() => { charger(); }, [charger]);

  async function fixer(organisme: Affectation, politique: string) {
    setErreur(""); setAnnonce("");
    const r = await apiFetch(`/api/soutien/engagements/${encodeURIComponent(organisme.tenantId)}`, {
      method: "PUT", headers: { "Content-Type": "application/json" }, body: JSON.stringify({ politique }),
    });
    if (!r.ok) {
      const d = await r.json().catch(() => ({}));
      setErreur(`${organisme.nom} : ${(d.errors ?? ["refus du serveur"]).join(" ; ")}`);
      return;
    }
    const a: Affectation = await r.json();
    setAnnonce(`${a.nom} : politique « ${a.libelle} » appliquée.`);
    await charger();
    await onChange?.();
  }

  return (
    <section className="card" aria-labelledby="sa-engagements-titre" data-testid="sa-engagements">
      <h3 id="sa-engagements-titre"><Icone nom="signature" /> Engagements de service par organisme</h3>
      <p className="aide">La politique fixe les délais des billets de l'organisme et l'objectif de disponibilité contre lequel son mois est jugé.
        Elle suit le contrat signé : la changer ne réécrit pas l'historique, les billets ouverts sont simplement jugés selon la nouvelle.</p>
      <div role="status" aria-live="polite" data-testid="sa-engagements-annonce">{annonce}</div>
      {erreur && <Message variante="erreur" testId="sa-engagements-erreur">{erreur}</Message>}
      {donnees && (
        <>
        <p className="field">
          <label htmlFor="sa-engagements-filtre">Filtrer les organismes par nom</label>
          <input id="sa-engagements-filtre" type="search" data-testid="sa-engagements-filtre" value={filtre} onChange={(e) => setFiltre(e.target.value)} />
        </p>
        <div className="table-wrap">
          <table data-testid="sa-engagements-table">
            <caption className="sr-only">Politique de soutien de chaque organisme</caption>
            <thead><tr><th scope="col">Organisme</th><th scope="col">Politique</th><th scope="col">Dernier changement</th></tr></thead>
            <TbodyPagine items={donnees.organismes.filter((o) => o.nom.toLocaleLowerCase("fr-CA").includes(filtre.trim().toLocaleLowerCase("fr-CA")))} colonnes={3} testId="sa-engagements-pager"
                         vide={<tr><td colSpan={3} className="muted">Aucun organisme.</td></tr>}
                         rendre={(o) => (
              <tr key={o.tenantId} data-testid={`sa-engagement-${o.tenantId}`}>
                <th scope="row">{o.nom}</th>
                <td>
                  <label className="sr-only" htmlFor={`sa-engagement-choix-${o.tenantId}`}>Politique de soutien de {o.nom}</label>
                  <select id={`sa-engagement-choix-${o.tenantId}`} value={o.politique} data-testid={`sa-engagement-choix-${o.tenantId}`}
                          onChange={(e) => fixer(o, e.target.value)}>
                    {donnees.politiques.map((p) => <option key={p.code} value={p.code}>{p.libelle}</option>)}
                  </select>
                </td>
                <td>{o.modifieLe ? `${o.modifieLe.slice(0, 10)} par ${o.modifiePar ?? "—"}` : <span className="muted">jamais changée</span>}</td>
              </tr>
            )} />
          </table>
        </div>
        </>
      )}
    </section>
  );
}
