/**
 * **Tâches différées de l'établissement** (ADR 0014) — réservé à l'administration.
 *
 * Une tâche échouée trois fois n'a de sens que si quelqu'un peut la voir : cet écran lit ce que
 * l'ordonnanceur a en attente, en cours, fait ou échoué, et permet un tic à la demande (« fais
 * avancer maintenant ») pour l'exploitation et la démonstration. Il n'interprète rien : les
 * types (`avancer_recherche`, `webhook_envoi`…) sont ceux des modules qui planifient.
 */
import { useCallback, useEffect, useState } from "react";
import { Message } from "./composants";
import TbodyPagine from "./TbodyPagine";
import { useT } from "./i18n";

interface Tache {
  id: string; type: string; cibleId: string; echeanceLe: string; statut: string; tentatives: number;
  derniereErreur: string | null; verrouLe: string | null;
}

interface Bilan { prises: number; faites: number; reportees: number; retentees: number; echouees: number; sansExecuteur: number }

const STATUTS = ["", "en_attente", "en_cours", "faite", "echouee"] as const;

export default function OrdonnanceurPanel({ apiFetch }: { apiFetch: (path: string, init?: RequestInit) => Promise<Response> }) {
  const { t } = useT();
  const [taches, setTaches] = useState<Tache[]>([]);
  const [statut, setStatut] = useState<string>("");
  const [erreur, setErreur] = useState("");
  const [bilan, setBilan] = useState<Bilan | null>(null);
  const [occupe, setOccupe] = useState(false);

  const charger = useCallback(async () => {
    setErreur("");
    const r = await apiFetch(`/api/ordonnanceur/taches${statut ? `?statut=${statut}` : ""}`);
    if (!r.ok) { setErreur(t("ordonnanceur.erreurLecture")); return; }
    const d = await r.json();
    setTaches(Array.isArray(d) ? d : []);
  }, [apiFetch, statut, t]);

  useEffect(() => { charger().catch(() => setErreur(t("ordonnanceur.erreurLecture"))); }, [charger, t]);

  async function tic() {
    setOccupe(true); setErreur(""); setBilan(null);
    try {
      const r = await apiFetch("/api/ordonnanceur/tic", { method: "POST" });
      if (!r.ok) { setErreur(t("ordonnanceur.erreurTic")); return; }
      setBilan(await r.json());
      await charger();
    } finally { setOccupe(false); }
  }

  const libelleStatut = (s: string) => t(`ordonnanceur.statut.${s}`);

  return (
    <section className="card" data-testid="ordonnanceur" aria-labelledby="ordonnanceur-titre">
      <h3 id="ordonnanceur-titre">⏱️ {t("ordonnanceur.titre")}</h3>
      <p className="aide">{t("ordonnanceur.aide")}</p>
      {erreur && <Message variante="erreur" testId="ordonnanceur-erreur">{erreur}</Message>}
      {bilan && (
        <p className="banner banner-success" role="status" data-testid="ordonnanceur-bilan">
          {t("ordonnanceur.bilan", Object.fromEntries(Object.entries(bilan).map(([k, v]) => [k, String(v)])))}
        </p>
      )}
      <div className="actions-row" style={{ marginBottom: 12 }}>
        <label htmlFor="ordonnanceur-statut">{t("ordonnanceur.filtre")}</label>
        <select id="ordonnanceur-statut" data-testid="ordonnanceur-statut" value={statut} onChange={(e) => setStatut(e.target.value)} style={{ width: "auto" }}>
          {STATUTS.map((s) => <option key={s} value={s}>{s ? libelleStatut(s) : t("ordonnanceur.tous")}</option>)}
        </select>
        <button type="button" className="primary" data-testid="ordonnanceur-tic" disabled={occupe} aria-busy={occupe} onClick={tic}>{t("ordonnanceur.tic")}</button>
      </div>
      <div className="table-wrap">
        <table data-testid="ordonnanceur-liste">
          <caption className="sr-only">{t("ordonnanceur.legende")}</caption>
          <thead><tr>
            <th scope="col">{t("ordonnanceur.colEcheance")}</th><th scope="col">{t("ordonnanceur.colType")}</th><th scope="col">{t("ordonnanceur.colCible")}</th>
            <th scope="col">{t("ordonnanceur.colStatut")}</th><th scope="col">{t("ordonnanceur.colTentatives")}</th><th scope="col">{t("ordonnanceur.colErreur")}</th>
          </tr></thead>
          <TbodyPagine items={taches} vide={<tr><td colSpan={6} className="muted" data-testid="ordonnanceur-aucune">{t("ordonnanceur.aucune")}</td></tr>} rendre={(x) => (
              <tr key={x.id} data-testid={`ordonnanceur-tache-${x.id}`}>
                <td>{x.echeanceLe.slice(0, 16).replace("T", " ")}</td>
                <td><code>{x.type}</code></td>
                <td><code>{x.cibleId}</code></td>
                <td><span className={`badge ${x.statut === "echouee" ? "cancelled" : x.statut === "faite" ? "badge-active" : "on_hold"}`}>{libelleStatut(x.statut)}</span></td>
                <td>{x.tentatives}</td>
                <td>{x.derniereErreur ?? "—"}</td>
              </tr>
            )} />
        </table>
      </div>
    </section>
  );
}
