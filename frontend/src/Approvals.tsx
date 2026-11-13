/**
 * Page Approbations : visible uniquement par managers/admins.
 * Liste les feuilles soumises, permet d'approuver/rejeter avec commentaire,
 * et affiche les insights IA.
 */
import { useEffect, useState } from "react";
import TbodyPagine from "./TbodyPagine";
import { Modal } from "./Modal";
import { useAuth } from "./AuthContext";
import { BadgeStatut, EnTeteEcran, EtatVide, Icone, Message, Onglets, Tableau, Tiroir, type Colonne, type Onglet } from "./composants";
import { useOnglet } from "./navigation";
import ChampPersonne from "./ChampPersonne";

const VUES = ["demandes", "regles"] as const;
type VueAppr = (typeof VUES)[number];
const estVue = (s: string): s is VueAppr => (VUES as readonly string[]).includes(s);


export default function Approvals() {
  const { apiFetch, user } = useAuth();
  const [vue, setVue] = useOnglet<VueAppr>("approvals", "demandes", estVue);
  const [delegationOuverte, setDelegationOuverte] = useState(false);
  const [error, setError] = useState("");
  // Délégation d'autorité (Global-14) : déléguer ses validations à un collègue pour une période.
  const [deleg, setDeleg] = useState({ toUserId: "", fromDate: "", toDate: "" });
  const [delegMsg, setDelegMsg] = useState("");
  /** Mes délégations en vigueur ou passées : ce que j'ai confié, à qui, et pour quand — sinon une délégation oubliée continue de signer. */
  const [delegations, setDelegations] = useState<Array<{ id: string; toUserId: string; toNom?: string; fromDate: string | null; toDate: string | null; createdAt: string }>>([]);
  async function loadDelegations() {
    const r = await apiFetch("/api/approvals/delegations?mine=1");
    const d = r.ok ? await r.json().catch(() => []) : [];
    setDelegations(Array.isArray(d) ? d : []);
  }
  useEffect(() => { loadDelegations(); }, []); // eslint-disable-line react-hooks/exhaustive-deps
  async function submitDelegation() {
    setDelegMsg("");
    const r = await apiFetch("/api/approvals/delegations", { method: "POST", body: JSON.stringify(deleg) });
    if (r.ok) { setDelegMsg("Délégation enregistrée ; le délégataire a été notifié."); setDeleg({ toUserId: "", fromDate: "", toDate: "" }); loadDelegations(); }
    else { const j = await r.json().catch(() => ({})); setError((j.errors || ["Erreur"]).join(", ")); }
  }

  /**
   * **Le catalogue des types de document**, servi par l'organisme.
   *
   * Les deux champs « Type de document » — celui des règles et celui de la recherche — étaient
   * des saisies libres : il fallait connaître `purchase_order` pour poser une règle, et une
   * faute de frappe donnait une règle **muette**, qui ne correspondait jamais à aucun document.
   * Le catalogue se configure dans l'espace admin ; ici on ne fait que le lire.
   */
  const [typesDoc, setTypesDoc] = useState<Array<{ code: string; labelFr: string }>>([]);
  useEffect(() => {
    void (async () => {
      const r = await apiFetch("/api/approvals/document-types");
      const d = r.ok ? await r.json().catch(() => []) : [];
      setTypesDoc(Array.isArray(d) ? d : []);
    })();
  }, []); // eslint-disable-line react-hooks/exhaustive-deps

  // Moteur d'approbation générique multi-niveaux (SoD) : règles configurables + demandes + décisions.
  const canConfigRules = user?.role === "admin" || user?.role === "manager";
  const [rules, setRules] = useState<any[]>([]);
  const [ruleForm, setRuleForm] = useState({ documentType: "", field: "amount", operator: "gte", value: "", level: "1", approverRole: "manager" });
  const [recherche, setRecherche] = useState({ documentType: "", documentId: "" });
  const [resultatRecherche, setResultatRecherche] = useState("");
  /** Saisies en modal — une fenêtre native ne permet ni champ typé, ni validation, ni test. */
  const [ajoutApprobateur, setAjoutApprobateur] = useState<{ requestId: string; approverId: string } | null>(null);
  const [changementMontant, setChangementMontant] = useState<{ rq: any; ancien: string; nouveau: string } | null>(null);
  const [requests, setRequests] = useState<any[]>([]);
  const [genMsg, setGenMsg] = useState("");
  async function loadGeneric() {
    const okJson = async (r: Response) => (r.ok ? r.json().catch(() => []) : []);
    const [rl, rq] = await Promise.all([
      apiFetch("/api/approvals/rules").then(okJson),
      apiFetch("/api/approvals/requests").then(okJson),
    ]);
    setRules(Array.isArray(rl) ? rl : []); setRequests(Array.isArray(rq) ? rq : []);
  }
  useEffect(() => { loadGeneric(); }, []); // eslint-disable-line react-hooks/exhaustive-deps
  async function addRule() {
    setGenMsg("");
    if (!ruleForm.documentType.trim() || !ruleForm.field.trim() || ruleForm.value === "") { setGenMsg("Type de document, champ et valeur requis."); return; }
    const r = await apiFetch("/api/approvals/rules", { method: "POST", body: JSON.stringify({ ...ruleForm, value: Number(ruleForm.value) || ruleForm.value, level: Number(ruleForm.level) }) });
    if (r.ok) { setRuleForm({ ...ruleForm, documentType: "", value: "" }); loadGeneric(); }
    else { const j = await r.json().catch(() => ({})); setGenMsg((j.errors || ["Règle refusée"]).join(", ")); }
  }
  async function decideStep(stepId: string, decision: "approved" | "rejected") {
    setGenMsg("");
    const r = await apiFetch(`/api/approvals/steps/${stepId}/decide`, { method: "POST", body: JSON.stringify({ decision }) });
    if (r.ok) { loadGeneric(); }
    else { const j = await r.json().catch(() => ({})); setGenMsg((j.errors || ["Décision refusée"]).join(", ")); } // affiche p.ex. le blocage SoD
  }

  /** Recherche d'une demande d'approbation à partir du document qu'elle vise. */
  async function chercherParDocument() {
    const { documentType, documentId } = recherche;
    if (!documentType.trim() || !documentId.trim()) {
      setResultatRecherche("Indiquez le type et l'identifiant du document.");
      return;
    }
    const q = new URLSearchParams({ documentType: documentType.trim(), documentId: documentId.trim() });
    const r = await apiFetch(`/api/approvals/by-document?${q.toString()}`);
    if (r.status === 404) {
      // Un 404 est ici une **réponse**, pas une panne : ce document n'a pas de circuit.
      setResultatRecherche("Aucune demande d'approbation pour ce document.");
      return;
    }
    if (!r.ok) { setResultatRecherche("Recherche impossible."); return; }
    const rq = await r.json();
    const enAttente = (rq.steps || []).filter((s: any) => s.status === "pending").length;
    setResultatRecherche(
      `Demande ${rq.id} — statut « ${rq.status} », ${rq.steps?.length ?? 0} étape(s), ${enAttente} en attente.`,
    );
  }

  /**
   * Ajoute un approbateur à une demande en cours (#Global-10).
   *
   * Utile quand un dossier prend une tournure qui appelle un regard de plus — un montant
   * inhabituel, un fournisseur sous surveillance — sans redéfinir la règle d'approbation pour
   * tous les dossiers à venir.
   */
  async function ajouterApprobateur(requestId: string, approverId: string) {
    if (!approverId.trim()) return;
    setGenMsg("");
    const r = await apiFetch(`/api/approvals/requests/${requestId}/additional-approver`, {
      method: "POST", body: JSON.stringify({ approverId: approverId.trim() }),
    });
    if (r.ok) { setAjoutApprobateur(null); loadGeneric(); }
    else { const j = await r.json().catch(() => ({})); setGenMsg((j.errors || ["Ajout refusé"]).join(", ")); }
  }

  /**
   * Relance conditionnelle après modification du montant d'un document (#Global-11).
   *
   * La relance n'est **pas** systématique : le serveur ne rouvre le circuit que si l'écart
   * dépasse la tolérance. C'est le point de la règle — un ajustement de quelques dollars ne
   * doit pas refaire approuver tout un dossier, sans quoi plus personne ne lit les approbations.
   */
  async function signalerChangementMontant() {
    if (!changementMontant) return;
    const { rq, ancien, nouveau } = changementMontant;
    const oldAmount = Number(String(ancien).replace(",", "."));
    const newAmount = Number(String(nouveau).replace(",", "."));
    if (!Number.isFinite(oldAmount) || !Number.isFinite(newAmount)) {
      setGenMsg("Indiquez deux montants valides.");
      return;
    }
    setGenMsg("");
    const r = await apiFetch("/api/approvals/relaunch", {
      method: "POST",
      body: JSON.stringify({
        documentType: rq.documentType, documentId: rq.documentId, oldAmount, newAmount,
      }),
    });
    if (r.ok) {
      const out = await r.json().catch(() => ({}));
      setGenMsg(out?.relaunched
        ? "Écart au-delà de la tolérance : le circuit d'approbation est relancé."
        : "Écart dans la tolérance : aucune relance nécessaire.");
      setChangementMontant(null);
      loadGeneric();
    } else {
      const j = await r.json().catch(() => ({}));
      setGenMsg((j.errors || ["Relance refusée"]).join(", "));
    }
  }



  const onglets: Onglet<VueAppr>[] = [
    { id: "demandes", libelle: "Demandes", testId: "appr-tab-demandes", icone: "document", compteur: requests.filter((r) => r.status === "pending").length },
    { id: "regles", libelle: "Règles", testId: "appr-tab-regles", icone: "reglages" },
  ];
  const badgeDemande = (statut: string) => <BadgeStatut statut={statut} />;

  return (
    <div>
      <EnTeteEcran titre="Approbations" icone="coche"
                   description={<>Connecté en tant que <strong>{user?.username}</strong> ({user?.role}).{user?.role === "admin" && " — admin : voit toutes les feuilles."}</>}
                   actions={<button type="button" className="secondary" data-testid="appr-delegation-ouvrir" onClick={() => setDelegationOuverte(true)}><Icone nom="personnes" /> Déléguer mes approbations</button>} />
      {error && <Message variante="erreur" onFermer={() => setError("")}>{error}</Message>}

      {/* Délégation d'autorité (Global-14) : déléguer ses validations pour une période (vacances). */}
      <Tiroir ouvert={delegationOuverte} onFermer={() => setDelegationOuverte(false)} titre="Déléguer mes approbations" testId="appr-delegation-tiroir">
        <div data-testid="appr-delegation">
          <p className="subtitle">Confiez vos validations à un collègue pour une période (ex. vacances). Le délégataire est notifié et sa signature est tracée « pour le compte de ».</p>
          {delegMsg && <Message variante="succes" testId="appr-delegation-msg">{delegMsg}</Message>}
          <div className="grid-form">
            {/*
              Le champ demandait un **identifiant de compte** (`usr-1788…`) que personne ne
              connaît, et acceptait n'importe quelle chaîne : une délégation vers un identifiant
              inexistant ne confiait rien à personne, et rien ne le disait. On cherche désormais
              par nom, dès trois caractères, parmi les comptes du même organisme.
            */}
            <div className="field field-full">
              <ChampPersonne
                libelle="Délégataire"
                testId="appr-deleg-to"
                requis
                placeholder="Nom du collègue (3 caractères minimum)"
                valeur={deleg.toUserId}
                onChoisir={(id) => setDeleg({ ...deleg, toUserId: id })}
                appel={apiFetch}
                source={{ suggerer: "/api/approvals/delegataires" }}
              />
            </div>
            <label className="field"><span>Du</span>
              <input type="date" data-testid="appr-deleg-from" value={deleg.fromDate} onChange={(e) => setDeleg({ ...deleg, fromDate: e.target.value })} /></label>
            <label className="field"><span>Au</span>
              <input type="date" data-testid="appr-deleg-until" value={deleg.toDate} onChange={(e) => setDeleg({ ...deleg, toDate: e.target.value })} /></label>
            <div className="field field-actions">
              <button type="button" className="primary" data-testid="appr-deleg-save" onClick={submitDelegation} disabled={!deleg.toUserId}>Déléguer</button>
            </div>
          </div>
          <h3>Mes délégations</h3>
          <div className="table-wrap" tabIndex={0} role="region" aria-label="Mes délégations (zone défilable au clavier)">
            <table data-testid="appr-deleg-liste">
              <caption className="sr-only">Mes délégations : délégataire, période, enregistrée le</caption>
              <thead><tr><th scope="col">Délégataire</th><th scope="col">Du</th><th scope="col">Au</th><th scope="col">Enregistrée le</th></tr></thead>
              <TbodyPagine items={delegations} vide={<tr><td colSpan={4} className="muted" data-testid="appr-deleg-aucune">Aucune délégation.</td></tr>} rendre={(d) => (
                  <tr key={d.id} data-testid={`appr-deleg-${d.id}`}>
                    <td>{d.toNom || d.toUserId}</td><td>{d.fromDate ?? "—"}</td><td>{d.toDate ?? "—"}</td><td>{d.createdAt.slice(0, 10)}</td>
                  </tr>
                )} />
            </table>
          </div>
        </div>
      </Tiroir>

      <Onglets onglets={onglets} actif={vue} onChange={setVue} libelle="Approbations">
        {vue === "demandes" && (
          <div className="card" data-testid="appr-engine">
            <h2>Demandes d'approbation</h2>
            <p className="subtitle">Bons de commande, contrats, factures : chaque demande suit les règles configurées, niveau par niveau ; la séparation des tâches (SoD) interdit au demandeur d'approuver sa propre demande et à un même approbateur de décider deux niveaux.</p>
            {genMsg && <Message variante="erreur" testId="appr-engine-msg">{genMsg}</Message>}

            {requests.length === 0 && <EtatVide titre="Aucune demande d'approbation" icone="document" compact />}
            {requests.map((rq) => (
              <div key={rq.id} className="card carte-secondaire" data-testid={`appr-request-${rq.id}`}>
                <div><strong>{rq.documentType} · {rq.documentId}</strong> — <BadgeStatut statut={rq.status} testId={`appr-request-status-${rq.id}`} />{rq.requestedBy ? <span className="muted"> · demandé par {rq.requestedBy}</span> : null}</div>
                <table data-testid={`appr-steps-${rq.id}`} style={{ marginTop: 6 }}>
                  <caption className="sr-only">Étapes d'approbation : niveau, approbateur, statut et action</caption>
                  <thead><tr><th scope="col">Niveau</th><th scope="col">Approbateur</th><th scope="col">Statut</th><th scope="col">Action</th></tr></thead>
                  <TbodyPagine items={rq.steps} rendre={(st: any) => (
                      <tr key={st.id}>
                        <td>{st.level}{st.additional ? " (add.)" : ""}</td>
                        <td>{st.approverRole || st.approverId || "—"}</td>
                        <td>{badgeDemande(st.status)}</td>
                        <td>{st.status === "pending" && (
                          <span className="actions-row">
                            <button type="button" data-testid={`appr-step-approve-${st.id}`} onClick={() => decideStep(st.id, "approved")}>Approuver</button>
                            <button type="button" className="danger" data-testid={`appr-step-reject-${st.id}`} onClick={() => decideStep(st.id, "rejected")}>Rejeter</button>
                          </span>
                        )}</td>
                      </tr>
                    )} />
                </table>
                <div className="actions-row" style={{ marginTop: 6 }}>
                  <button type="button" className="secondary" data-testid={`appr-add-approver-${rq.id}`}
                    onClick={() => setAjoutApprobateur({ requestId: rq.id, approverId: "" })}>
                    <Icone nom="plus" /> Ajouter un approbateur
                  </button>
                  <button type="button" className="secondary" data-testid={`appr-relaunch-${rq.id}`}
                    onClick={() => setChangementMontant({ rq, ancien: "", nouveau: "" })}>
                    <Icone nom="rafraichir" /> Signaler un changement de montant
                  </button>
                </div>
              </div>
            ))}

            <fieldset className="fieldset" style={{ marginTop: 12 }}>
              <legend>Retrouver l'approbation d'un document</legend>
              <p className="section-help">
                Où en est l'approbation d'un bon de commande, d'un contrat ou d'une facture précise ?
                Cette recherche part du document, et non de la liste — c'est la question qu'on se pose
                quand un dossier est bloqué.
              </p>
              <div className="grid-form">
                <label className="field"><span>Type de document</span>
                  <select data-testid="appr-find-type" value={recherche.documentType}
                    onChange={(e) => setRecherche({ ...recherche, documentType: e.target.value })}>
                    <option value="">— Choisir —</option>
                    {typesDoc.map((t) => <option key={t.code} value={t.code}>{t.labelFr}</option>)}
                  </select></label>
                <label className="field"><span>Identifiant du document</span>
                  <input data-testid="appr-find-id" value={recherche.documentId}
                    onChange={(e) => setRecherche({ ...recherche, documentId: e.target.value })} /></label>
                <div className="field field-actions">
                  <button type="button" data-testid="appr-find-run" className="secondary" onClick={chercherParDocument}><Icone nom="rechercher" /> Rechercher</button>
                </div>
              </div>
              {resultatRecherche && (
                <p className="section-help" data-testid="appr-find-result" role="status">{resultatRecherche}</p>
              )}
            </fieldset>
          </div>
        )}

        {vue === "regles" && (
          <div className="card" data-testid="appr-rules">
            <h2>Règles d'approbation</h2>
            <p className="subtitle">Règles configurables par type de document et par seuil : qui approuve, à quel niveau, à partir de quel montant.</p>
            {canConfigRules && (
              <div className="row-form" data-testid="appr-rule-form" style={{ alignItems: "end" }}>
                <div>
                  <label htmlFor="appr-rule-doctype">Type de document <span aria-hidden="true">*</span></label>
                  <select id="appr-rule-doctype" data-testid="appr-rule-doctype" aria-required="true"
                          value={ruleForm.documentType} onChange={(e) => setRuleForm({ ...ruleForm, documentType: e.target.value })}>
                    <option value="">— Choisir un type —</option>
                    {typesDoc.map((t) => <option key={t.code} value={t.code}>{t.labelFr}</option>)}
                  </select>
                  {/* Le catalogue vide se dit, au lieu de laisser un sélecteur inerte sans explication. */}
                  {typesDoc.length === 0 && <p className="muted" data-testid="appr-rule-doctype-vide">Aucun type actif. L'administration les configure dans Administration › Configuration › Listes dynamiques.</p>}
                </div>
                <div><label htmlFor="appr-rule-field">Champ</label><input id="appr-rule-field" data-testid="appr-rule-field" value={ruleForm.field} onChange={(e) => setRuleForm({ ...ruleForm, field: e.target.value })} /></div>
                <div><label htmlFor="appr-rule-op">Opérateur</label>
                  <select id="appr-rule-op" data-testid="appr-rule-op" value={ruleForm.operator} onChange={(e) => setRuleForm({ ...ruleForm, operator: e.target.value })}>
                    <option value="gte">≥</option><option value="lte">≤</option><option value="eq">=</option><option value="neq">≠</option>
                  </select></div>
                <div><label htmlFor="appr-rule-value">Valeur <span aria-hidden="true">*</span></label><input id="appr-rule-value" data-testid="appr-rule-value" value={ruleForm.value} onChange={(e) => setRuleForm({ ...ruleForm, value: e.target.value })} /></div>
                <div><label htmlFor="appr-rule-level">Niveau</label><input id="appr-rule-level" type="number" min={1} data-testid="appr-rule-level" value={ruleForm.level} onChange={(e) => setRuleForm({ ...ruleForm, level: e.target.value })} style={{ width: 70 }} /></div>
                <div><label htmlFor="appr-rule-role">Rôle approbateur</label>
                  <select id="appr-rule-role" data-testid="appr-rule-role" value={ruleForm.approverRole} onChange={(e) => setRuleForm({ ...ruleForm, approverRole: e.target.value })}>
                    <option value="manager">manager</option><option value="director">director</option><option value="admin">admin</option>
                  </select></div>
                <div><button type="button" className="primary" data-testid="appr-rule-add" onClick={addRule}><Icone nom="plus" /> Ajouter la règle</button></div>
              </div>
            )}
            <div className="table-wrap" tabIndex={0} role="region" aria-label="Règles d'approbation (zone défilable au clavier)">
              <table data-testid="appr-rules-table">
                <caption className="sr-only">Règles d'approbation : type de document, condition, niveau, approbateur</caption>
                <thead><tr><th scope="col">Document</th><th scope="col">Condition</th><th scope="col">Niveau</th><th scope="col">Approbateur</th></tr></thead>
                <TbodyPagine items={rules} vide={<tr><td colSpan={4} className="muted">Aucune règle configurée.</td></tr>} rendre={(r) => (
                    <tr key={r.id} data-testid={`appr-rule-${r.id}`}><td>{typesDoc.find((t) => t.code === (r as any).documentType)?.labelFr ?? (r as any).documentType ?? "—"}</td><td>{r.field} {r.operator} {String(r.value)}</td><td>{r.level}</td><td>{r.approverRole || r.approverId || "—"}</td></tr>
                  )} />
              </table>
            </div>
          </div>
        )}

      </Onglets>

      <Modal open={!!ajoutApprobateur} onClose={() => setAjoutApprobateur(null)}
        title="Ajouter un approbateur" testId="appr-add-approver-modal" size="sm">
        {ajoutApprobateur && (
          <>
            <p className="muted">
              Un regard de plus sur ce dossier seulement — la règle d'approbation générale n'est pas modifiée.
            </p>
            <label className="field"><span>Identifiant de l'approbateur</span>
              <input data-testid="appr-add-approver-input" value={ajoutApprobateur.approverId}
                onChange={(e) => setAjoutApprobateur({ ...ajoutApprobateur, approverId: e.target.value })} /></label>
            <div className="modal-actions">
              <button className="secondary" onClick={() => setAjoutApprobateur(null)}>Annuler</button>
              <button className="primary" data-testid="appr-add-approver-confirm"
                onClick={() => ajouterApprobateur(ajoutApprobateur.requestId, ajoutApprobateur.approverId)}>Ajouter</button>
            </div>
          </>
        )}
      </Modal>

      <Modal open={!!changementMontant} onClose={() => setChangementMontant(null)}
        title="Signaler un changement de montant" testId="appr-relaunch-modal" size="sm">
        {changementMontant && (
          <>
            <p className="muted">
              Le circuit n'est relancé que si l'écart dépasse la tolérance : un ajustement de quelques
              dollars ne doit pas refaire approuver tout un dossier.
            </p>
            <label className="field"><span>Montant approuvé à l'origine</span>
              <input data-testid="appr-relaunch-old" type="number" step="0.01" value={changementMontant.ancien}
                onChange={(e) => setChangementMontant({ ...changementMontant, ancien: e.target.value })} /></label>
            <label className="field"><span>Nouveau montant</span>
              <input data-testid="appr-relaunch-new" type="number" step="0.01" value={changementMontant.nouveau}
                onChange={(e) => setChangementMontant({ ...changementMontant, nouveau: e.target.value })} /></label>
            <div className="modal-actions">
              <button className="secondary" onClick={() => setChangementMontant(null)}>Annuler</button>
              <button className="primary" data-testid="appr-relaunch-confirm" onClick={signalerChangementMontant}>Signaler</button>
            </div>
          </>
        )}
      </Modal>

    </div>
  );
}
