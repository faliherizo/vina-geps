/**
 * La carte « Textos » de la page Notifications (grille 25, devis 2.6) : le fournisseur en service
 * — dit en rouge quand ce n'est que le simulateur —, le quota du mois, un texto composé à la
 * main, et le journal (`sms_message`) avec les numéros masqués et le statut redemandé au
 * fournisseur d'un clic. L'administration du système règle la limite mensuelle ici aussi.
 */
import { useCallback, useEffect, useState } from "react";
import Pagination, { usePagination } from "./Pagination";
import { EtatTextos, LIBELLE_STATUT, LONGUEUR_MAX_TEXTO, QuotaTextos, ResumeTextos, Texto, classeStatut, decrireQuota, decrireResume, libelleFournisseur, refusNumero, refusTexte } from "./reglesTextos";

interface Props { call: (chemin: string, init?: RequestInit) => Promise<Response>; adminSysteme: boolean; signaler: (erreur: string) => void; confirmer: (message: string) => void }

async function lireErreur(r: Response): Promise<string> {
  const j = await r.json().catch(() => ({}));
  return j.errors?.join(", ") || `Erreur ${r.status}`;
}

export default function Textos({ call, adminSysteme, signaler, confirmer }: Props) {
  const [etat, setEtat] = useState<EtatTextos | null>(null);
  const [quota, setQuota] = useState<QuotaTextos | null>(null);
  const [journal, setJournal] = useState<Texto[]>([]);
  const [resume, setResume] = useState<ResumeTextos | null>(null);
  const pager = usePagination(journal, 10);
  const [numero, setNumero] = useState("");
  const [texte, setTexte] = useState("");
  const [refus, setRefus] = useState("");
  const [limite, setLimite] = useState("");
  const [occupe, setOccupe] = useState(false);

  const charger = useCallback(async () => {
    const [e, q, j, s] = await Promise.all([
      call("/api/notifications/sms/etat").then((r) => (r.ok ? r.json() : null)),
      call("/api/notifications/sms/quota").then((r) => (r.ok ? r.json() : null)),
      call("/api/notifications/sms?limite=200").then((r) => (r.ok ? r.json() : [])),
      call("/api/notifications/sms/resume").then((r) => (r.ok ? r.json() : null)),
    ]);
    setResume(s && typeof s.total === "number" ? (s as ResumeTextos) : null);
    // Une réponse d'une autre forme (harnais, version de serveur) vaut « inconnu », jamais un plantage.
    const etatLu = e && typeof e.fournisseur === "string" ? (e as EtatTextos) : null;
    const quotaLu = q && typeof q.limite === "number" ? (q as QuotaTextos) : null;
    setEtat(etatLu); setQuota(quotaLu); setJournal(Array.isArray(j) ? j : []);
    if (quotaLu) setLimite(String(quotaLu.limite));
  }, [call]);
  useEffect(() => { void charger(); }, [charger]);

  async function envoyer(ev: React.FormEvent) {
    ev.preventDefault();
    const motif = refusNumero(numero) ?? refusTexte(texte);
    setRefus(motif ?? "");
    if (motif) return;
    setOccupe(true);
    try {
      const r = await call("/api/notifications/sms", { method: "POST", body: JSON.stringify({ numero: numero.trim(), texte: texte.trim() }) });
      if (r.ok) { confirmer("Texto remis au fournisseur."); setNumero(""); setTexte(""); }
      else signaler(await lireErreur(r));
      await charger();
    } finally { setOccupe(false); }
  }

  async function rafraichir(id: number) {
    const r = await call(`/api/notifications/sms/${id}/rafraichir`, { method: "POST" });
    if (!r.ok) { signaler(await lireErreur(r)); return; }
    const t: Texto = await r.json();
    setJournal((j) => j.map((x) => (x.id === t.id ? t : x)));
    confirmer(`Statut redemandé : ${LIBELLE_STATUT[t.statut]}.`);
  }

  async function reglerLimite(ev: React.FormEvent) {
    ev.preventDefault();
    const r = await call("/api/notifications/sms/quota", { method: "PUT", body: JSON.stringify({ limite: Number(limite) }) });
    if (!r.ok) { signaler(await lireErreur(r)); return; }
    setQuota(await r.json());
    confirmer("Limite mensuelle enregistrée.");
  }

  const quotaAtteint = !!quota && !quota.illimite && (quota.restants ?? 0) <= 0;
  return (
    <section className="card" aria-labelledby="textos-titre" data-testid="textos-carte">
      <h3 id="textos-titre">Textos (SMS)</h3>
      {etat && (
        <p data-testid="textos-etat" role="status">
          Fournisseur : <span className={etat.simule ? "badge badge-terminated" : "badge badge-active"} data-testid="textos-fournisseur">{libelleFournisseur(etat.fournisseur)}</span>
          {etat.simule && <span className="muted"> — les textos sont journalisés mais ne partent pas : réglez SMS_FOURNISSEUR=twilio et les identifiants du compte.</span>}
          {etat.manquantes.length > 0 && <span className="muted" data-testid="textos-manquantes"> Variables manquantes : {etat.manquantes.join(", ")}.</span>}
          {etat.rappel ? <span className="muted"> Rappel de statut branché.</span> : <span className="muted"> Sans PUBLIC_API_URL, le statut se redemande à la main.</span>}
        </p>
      )}
      {quota && <p data-testid="textos-quota" role="status" className={quotaAtteint ? "error" : undefined}>{decrireQuota(quota)}</p>}
      {adminSysteme && (
        <form onSubmit={reglerLimite} className="actions-row" data-testid="textos-quota-form">
          <label className="field"><span>Limite mensuelle (0 = sans limite)</span>
            <input type="number" min={0} max={1000000} data-testid="textos-limite" value={limite} onChange={(e) => setLimite(e.target.value)} required /></label>
          <button type="submit" className="secondary" data-testid="textos-limite-enregistrer">Enregistrer la limite</button>
        </form>
      )}

      <form onSubmit={envoyer} className="grid-form" data-testid="textos-form" noValidate>
        <label className="field"><span>Numéro (format international)</span>
          <input type="tel" data-testid="textos-numero" value={numero} onChange={(e) => setNumero(e.target.value)} placeholder="+15145551234" autoComplete="off" aria-describedby="textos-numero-aide" /></label>
        <span className="aide sr-only" id="textos-numero-aide">Format international, ou dix chiffres nord-américains.</span>
        <label className="field" style={{ gridColumn: "1 / -1" }}><span>Message ({texte.trim().length}/{LONGUEUR_MAX_TEXTO})</span>
          <textarea data-testid="textos-texte" value={texte} onChange={(e) => setTexte(e.target.value)} maxLength={LONGUEUR_MAX_TEXTO * 2} /></label>
        {refus && <p className="error" role="alert" data-testid="textos-refus" style={{ gridColumn: "1 / -1" }}>{refus}</p>}
        <div className="field field-actions"><button type="submit" className="primary" data-testid="textos-envoyer" disabled={occupe || quotaAtteint}>Envoyer le texto</button></div>
      </form>

      <div className="table-wrap" tabIndex={0} role="region" aria-label="Journal des textos (zone défilable au clavier)">
        <table data-testid="textos-journal">
          <caption data-testid="textos-resume">{resume ? decrireResume(resume) : "Journal des textos de l'organisme"}</caption>
          <thead><tr>
            <th scope="col">Date</th><th scope="col">Numéro</th><th scope="col">Statut</th><th scope="col">Fournisseur</th><th scope="col">Contexte</th><th scope="col">Erreur</th><th scope="col">Actions</th>
          </tr></thead>
          <tbody>
            {pager.pageItems.map((t) => (
              <tr key={t.id} data-testid={`textos-ligne-${t.id}`}>
                <td>{new Date(t.creeLe).toLocaleString("fr-CA")}</td>
                <td>{t.numero}</td>
                <td><span className={classeStatut(t.statut)} data-testid={`textos-statut-${t.id}`}>{LIBELLE_STATUT[t.statut]}</span></td>
                <td>{libelleFournisseur(t.fournisseur)}</td>
                <td>{t.contexte ?? "—"}</td>
                <td>{t.erreur ?? "—"}</td>
                <td>{t.identifiantFournisseur && <button type="button" className="secondary" data-testid={`textos-rafraichir-${t.id}`} onClick={() => void rafraichir(t.id)}>Rafraîchir le statut</button>}</td>
              </tr>
            ))}
            {journal.length === 0 && <tr><td colSpan={7} className="empty-cell">Aucun texto envoyé.</td></tr>}
          </tbody>
        </table>
        <Pagination page={pager.page} pageCount={pager.pageCount} total={pager.total} pageSize={pager.pageSize} onPage={pager.setPage} testId="textos-pager" />
      </div>
    </section>
  );
}
