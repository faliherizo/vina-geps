/**
 * **Destruction des données d'un établissement résilié** (contrat 9.25.06, EFVP 7) — le geste le
 * plus irréversible de l'application, donc le plus explicite : le nom retapé, le motif écrit, et
 * l'**attestation** délivrée aussitôt, relisible ensuite en JSON ou en clair.
 */
import { useCallback, useEffect, useState } from "react";
import { FormulaireModal, useFermerFormulaire } from "./Modal";

interface Attestation { id: string; tenantNom: string; detruitLe: string; detruitPar: string; motif: string | null; tables: Array<{ table: string; lignes: number }>; totalLignes: number; empreinte: string }

interface Props {
  tenant: { id: string; name: string; status: string };
  apiFetch: (chemin: string, init?: RequestInit) => Promise<Response>;
  signaler: (message: string, erreur?: boolean) => void;
}

function Formulaire({ tenant, apiFetch, signaler, apres }: Props & { apres: (a: Attestation) => void }) {
  const fermer = useFermerFormulaire();
  const [confirmation, setConfirmation] = useState("");
  const [motif, setMotif] = useState("fin du contrat");
  const [erreur, setErreur] = useState("");
  return (
    <form data-testid="sa-destruction-formulaire" onSubmit={async (e) => {
      e.preventDefault(); setErreur("");
      const r = await apiFetch(`/api/saas/tenants/${tenant.id}/detruire-donnees`, { method: "POST", body: JSON.stringify({ confirmation, motif }) });
      const d = await r.json().catch(() => ({}));
      if (!r.ok) { setErreur((d.errors ?? ["refus du serveur"]).join(" ; ")); return; }
      apres(d as Attestation); fermer(); signaler(`Données de « ${tenant.name} » détruites : ${d.totalLignes} ligne(s), attestation délivrée.`);
    }}>
      <p role="alert" className="banner banner-error">
        Toutes les lignes rattachées à <strong>{tenant.name}</strong> — personnes, absences, besoins, comptes, journaux du module — seront
        supprimées, les pages réécrites et le fichier compacté. <strong>Rien ne se récupère.</strong> Les copies de sauvegarde expirent selon la rétention.
      </p>
      <label className="field"><span>Retapez le nom exact de l'organisme <span className="req-star" aria-hidden="true">*</span></span>
        <input required data-testid="sa-destruction-confirmation" value={confirmation} onChange={(e) => setConfirmation(e.target.value)} autoComplete="off" /></label>
      <label className="field"><span>Motif (porté par l'attestation)</span>
        <input data-testid="sa-destruction-motif" value={motif} onChange={(e) => setMotif(e.target.value)} /></label>
      {erreur && <p role="alert" className="field-error" data-testid="sa-destruction-erreur">{erreur}</p>}
      <div className="modal-actions">
        <button type="button" className="secondary" onClick={fermer}>Annuler</button>
        <button type="submit" className="danger-solid" data-testid="sa-destruction-confirmer" disabled={confirmation !== tenant.name}>Détruire définitivement</button>
      </div>
    </form>
  );
}

export default function DestructionDonnees({ tenant, apiFetch, signaler }: Props) {
  const [attestation, setAttestation] = useState<Attestation | null>(null);
  const [ouverte, setOuverte] = useState(false);
  const charger = useCallback(async () => {
    const r = await apiFetch(`/api/saas/tenants/${tenant.id}/attestation-destruction`);
    const d = r.ok ? await r.json().catch(() => null) : null;
    setAttestation(d && Array.isArray(d.tables) && d.empreinte ? d : null);
  }, [apiFetch, tenant.id]);
  useEffect(() => { if (tenant.status === "terminated") void charger(); }, [charger, tenant.status]);
  if (tenant.status !== "terminated") return null;

  if (attestation) {
    return (
      <>
        <button type="button" className="secondary" data-testid={`sa-attestation-${tenant.id}`} onClick={() => setOuverte(true)}>Attestation de destruction</button>
        <FormulaireModal libelle="Attestation" titre={`Attestation de destruction — ${attestation.tenantNom}`} testId={`sa-attestation-${tenant.id}`} ouvert={ouverte} onFermer={() => setOuverte(false)} size="md">
          <dl className="account-meta" data-testid="sa-attestation-detail">
            <div><dt>Détruit le</dt><dd>{attestation.detruitLe.slice(0, 16).replace("T", " ")}</dd></div>
            <div><dt>Par</dt><dd>{attestation.detruitPar}</dd></div>
            <div><dt>Motif</dt><dd>{attestation.motif ?? "fin du contrat"}</dd></div>
            <div><dt>Lignes détruites</dt><dd>{attestation.totalLignes} dans {attestation.tables.length} table(s)</dd></div>
            <div><dt>Empreinte</dt><dd><code>{attestation.empreinte.slice(0, 16)}…</code></dd></div>
          </dl>
          <div className="modal-actions">
            <button type="button" className="secondary" data-testid={`sa-attestation-texte-${tenant.id}`}
                    onClick={async () => {
                      const r = await apiFetch(`/api/saas/tenants/${tenant.id}/attestation-destruction?format=texte`);
                      const url = URL.createObjectURL(new Blob([await r.text()], { type: "text/plain;charset=utf-8" }));
                      const a = document.createElement("a"); a.href = url; a.download = `attestation-destruction-${tenant.id}.txt`; document.body.appendChild(a); a.click(); a.remove();
                      setTimeout(() => URL.revokeObjectURL(url), 10_000);
                    }}>Télécharger en clair</button>
            <button type="button" className="primary" onClick={() => setOuverte(false)}>Fermer</button>
          </div>
        </FormulaireModal>
      </>
    );
  }
  return (
    <FormulaireModal libelle="Détruire les données" titre={`Détruire les données — ${tenant.name}`} testId={`sa-destruction-${tenant.id}`} className="danger" icone="🗑" size="md">
      <Formulaire tenant={tenant} apiFetch={apiFetch} signaler={signaler} apres={(a) => setAttestation(a)} />
    </FormulaireModal>
  );
}
