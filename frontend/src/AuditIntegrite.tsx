/**
 * Intégrité du journal d'audit et **revue indépendante des registres** (grille 39) : la chaîne
 * d'empreintes est vérifiée par le serveur et l'écran nomme la première rupture ; les revues sont
 * consignées (période, entrées, empreinte de fin, constat) par le superadmin — indépendant de
 * l'établissement — et une revue est due tous les 90 jours.
 */
import { useCallback, useEffect, useState } from "react";
import { Message } from "./composants";
import { FormulaireModal } from "./Modal";
import TbodyPagine from "./TbodyPagine";

interface Integrite {
  entrees: number; chainees: number; nonChainees: number; intacte: boolean;
  premiereRupture: { id: string; at: string } | null; derniereEmpreinte: string | null;
  revue: { derniere: string | null; prochaineAvant: string | null; enRetard: boolean };
}
interface Revue { id: string; du: string; au: string; entrees: number; empreinteFin: string | null; reviseurNom: string; constat: string; faiteLe: string }

const quand = (iso: string) => new Date(iso).toLocaleString("fr-CA", { dateStyle: "medium", timeStyle: "short" });

export default function AuditIntegrite({ apiFetch, peutConsigner }: { apiFetch: (path: string, init?: RequestInit) => Promise<Response>; peutConsigner: boolean }) {
  const [integrite, setIntegrite] = useState<Integrite | null>(null);
  const [revues, setRevues] = useState<Revue[]>([]);
  const [erreur, setErreur] = useState("");
  const [message, setMessage] = useState("");

  const charger = useCallback(async () => {
    const [i, r] = await Promise.all([apiFetch("/api/auth/audit/integrite"), apiFetch("/api/auth/audit/revues")]);
    if (i.ok) { const j = await i.json(); if (j && typeof j.intacte === "boolean") setIntegrite(j); }
    if (r.ok) { const j = await r.json(); if (Array.isArray(j)) setRevues(j); }
  }, [apiFetch]);
  useEffect(() => { charger().catch(() => setErreur("Impossible de vérifier le journal.")); }, [charger]);

  return (
    <section className="card" aria-labelledby="audit-integrite-titre" data-testid="audit-integrite" style={{ marginTop: 12 }}>
      <h3 id="audit-integrite-titre">🔏 Intégrité du journal et revues des registres</h3>
      {erreur && <Message variante="erreur">{erreur}</Message>}
      {message && <Message variante="succes" testId="audit-revue-message">{message}</Message>}
      {integrite && (
        <p data-testid="audit-integrite-etat">
          {integrite.intacte
            ? <>Chaîne d'empreintes <strong>intacte</strong> : {integrite.chainees} entrées scellées</>
            : <><strong>Rupture de chaîne</strong> à l'entrée {integrite.premiereRupture?.id} ({integrite.premiereRupture ? quand(integrite.premiereRupture.at) : ""}) — le journal a été modifié après coup</>}
          {integrite.nonChainees > 0 && <> · {integrite.nonChainees} entrées d'avant la chaîne, non scellées</>}
          {" · "}revue {integrite.revue.derniere ? `faite le ${quand(integrite.revue.derniere)}` : "jamais faite"}
          {integrite.revue.prochaineAvant && <>, prochaine avant le {quand(integrite.revue.prochaineAvant)}</>}
          {integrite.revue.enRetard && <strong data-testid="audit-revue-retard"> — revue en retard</strong>}
        </p>
      )}
      {peutConsigner && (
        <FormulaireModal libelle="Consigner une revue" testId="audit-revue">{(fermer) => (
          <form className="grid-form" data-testid="audit-revue-formulaire" onSubmit={async (e) => {
          e.preventDefault(); setErreur(""); setMessage("");
          const f = e.currentTarget; const d = new FormData(f);
          const r = await apiFetch("/api/auth/audit/revues", { method: "POST", headers: { "Content-Type": "application/json" },
            body: JSON.stringify({ du: String(d.get("du") ?? ""), au: String(d.get("au") ?? ""), constat: String(d.get("constat") ?? "") }) });
          if (!r.ok) { const j = await r.json().catch(() => ({})); setErreur(`Revue : ${(j.errors ?? ["refus du serveur"]).join(" ; ")}`); return; }
          setMessage("Revue consignée : elle est elle-même scellée dans le journal."); f.reset(); await charger(); fermer();
        }}>
          <label className="field"><span>Du <span className="req-star" aria-hidden="true">*</span></span><input name="du" type="date" required data-testid="audit-revue-du" /></label>
          <label className="field"><span>Au <span className="req-star" aria-hidden="true">*</span></span><input name="au" type="date" required data-testid="audit-revue-au" /></label>
          <label className="field field-full"><span>Constat <span className="req-star" aria-hidden="true">*</span></span>
            <textarea name="constat" required minLength={10} rows={2} data-testid="audit-revue-constat" aria-describedby="audit-revue-aide" />
            <span className="aide" id="audit-revue-aide">Ce qui a été relu et ce qui a été trouvé ; l'empreinte de fin de période est consignée avec le constat.</span></label>
          <div className="field field-actions"><button type="submit" className="primary" data-testid="audit-revue-consigner">Consigner la revue</button></div>
        </form>
        )}</FormulaireModal>
      )}
      <div className="table-wrap">
        <table data-testid="audit-revues">
          <caption className="sr-only">Revues des registres consignées</caption>
          <thead><tr><th scope="col">Période</th><th scope="col">Entrées</th><th scope="col">Réviseur</th><th scope="col">Faite le</th><th scope="col">Constat</th><th scope="col">Empreinte de fin</th></tr></thead>
          <TbodyPagine items={revues} vide={<tr><td colSpan={6} className="muted">Aucune revue consignée.</td></tr>} rendre={(r) => (
              <tr key={r.id} data-testid={`audit-revue-${r.id}`}>
                <td>{r.du} → {r.au}</td><td>{r.entrees}</td><td>{r.reviseurNom}</td><td>{quand(r.faiteLe)}</td><td>{r.constat}</td>
                <td><code>{r.empreinteFin ? `${r.empreinteFin.slice(0, 12)}…` : "—"}</code></td>
              </tr>
            )} />
        </table>
      </div>
    </section>
  );
}
