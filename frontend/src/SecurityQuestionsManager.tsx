/**
 * Gestionnaire du **catalogue de questions secrètes** (MFA par questions).
 *
 * Réutilisable pour deux portées :
 *  - **admin du tenant** : ne passe pas de `scopeParam` → l'API cible son propre établissement ;
 *  - **superadmin** : passe `scopeParam="global"` (défauts) ou un `tenantId` précis.
 *
 * Permet d'ajouter, activer/désactiver et supprimer des questions proposées à la
 * configuration du second facteur. Les utilisateurs peuvent de toute façon saisir leur
 * propre question à l'enregistrement.
 */
import { useCallback, useEffect, useState } from "react";
import { Message, Icone } from "./composants";
import { useConfirm } from "./Modal";

interface CatalogQuestion {
  id: string;
  tenantId: string | null;
  question: string;
  active: boolean;
  sortOrder: number;
}

export default function SecurityQuestionsManager({
  apiFetch,
  scopeParam,
  title = "Questions de sécurité (MFA)",
}: {
  apiFetch: (path: string, init?: RequestInit) => Promise<Response>;
  scopeParam?: string; // undefined = tenant de l'admin ; "global" ou tenantId pour le superadmin
  title?: string;
}) {
  // Confirmations en **modal** : une fenêtre native n'est ni stylable, ni testable,
  // ni lisible par un lecteur d'écran dans le flux de la page.
  const { confirm, ConfirmHost } = useConfirm();
  const [items, setItems] = useState<CatalogQuestion[]>([]);
  const [newQ, setNewQ] = useState("");
  const [error, setError] = useState("");
  const [loading, setLoading] = useState(true);

  const qs = scopeParam !== undefined ? `?tenantId=${encodeURIComponent(scopeParam)}` : "";

  const load = useCallback(async () => {
    setLoading(true);
    setError("");
    try {
      const r = await apiFetch(`/api/auth/security/catalog${qs}`);
      if (r.ok) { const j = await r.json(); setItems(Array.isArray(j.questions) ? j.questions : []); }
      else { const j = await r.json().catch(() => ({})); setError(j.errors?.join(", ") || "Chargement impossible"); }
    } finally { setLoading(false); }
  }, [apiFetch, qs]);
  useEffect(() => { load(); }, [load]);

  async function add() {
    setError("");
    const question = newQ.trim();
    if (question.length < 5) { setError("La question doit comporter au moins 5 caractères."); return; }
    const body: any = { question };
    if (scopeParam !== undefined) body.tenantId = scopeParam;
    const r = await apiFetch("/api/auth/security/catalog", { method: "POST", body: JSON.stringify(body) });
    if (r.ok) { setNewQ(""); load(); }
    else { const j = await r.json().catch(() => ({})); setError(j.errors?.join(", ") || "Ajout impossible"); }
  }

  async function toggleActive(q: CatalogQuestion) {
    const r = await apiFetch(`/api/auth/security/catalog/${q.id}`, { method: "PATCH", body: JSON.stringify({ active: !q.active }) });
    if (r.ok) load(); else { const j = await r.json().catch(() => ({})); setError(j.errors?.join(", ") || "Erreur"); }
  }

  async function rename(q: CatalogQuestion) {
    const next = prompt("Modifier la question :", q.question);
    if (next == null) return;
    const r = await apiFetch(`/api/auth/security/catalog/${q.id}`, { method: "PATCH", body: JSON.stringify({ question: next }) });
    if (r.ok) load(); else { const j = await r.json().catch(() => ({})); setError(j.errors?.join(", ") || "Erreur"); }
  }

  async function remove(q: CatalogQuestion) {
    if (!await confirm({
      title: "Supprimer la question",
      message: "Retirer cette question du catalogue ? Les comptes qui l'ont choisie devront en désigner une autre.",
      confirmLabel: "Supprimer", variant: "danger", testId: "sq-delete-modal",
    })) return;
    const r = await apiFetch(`/api/auth/security/catalog/${q.id}`, { method: "DELETE" });
    if (r.ok || r.status === 204) load(); else { const j = await r.json().catch(() => ({})); setError(j.errors?.join(", ") || "Erreur"); }
  }

  return (
    <div className="card" data-testid="secq-manager">
      <h3><Icone nom="cadenas" /> {title}</h3>
      <p className="section-help" style={{ marginTop: -4 }}>
        Questions proposées lors de la configuration du second facteur. Les utilisateurs peuvent
        aussi saisir leur propre question au moment de l'enregistrement.
      </p>
      {error && <Message variante="erreur" testId="secq-error">{error}</Message>}

      <div className="row-form" style={{ marginBottom: 8 }}>
        <div style={{ flex: 1, minWidth: 260 }}>
          <label htmlFor="secq-new">Nouvelle question <span className="req-star" aria-hidden="true">*</span></label>
          <input id="secq-new" data-testid="secq-new" required aria-required="true" value={newQ} minLength={5}
            onChange={(e) => setNewQ(e.target.value)} placeholder="Ex. Quel est le nom de votre premier employeur ?" />
        </div>
        <div style={{ alignSelf: "end" }}>
          <button className="primary" data-testid="secq-add" onClick={add}>Ajouter</button>
        </div>
      </div>

      {loading ? (
        <div className="muted">Chargement…</div>
      ) : (
        <ul className="bullet-list" role="list" data-testid="secq-list">
          {items.map((q) => (
            <li key={q.id} data-testid={`secq-item-${q.id}`} style={{ display: "flex", justifyContent: "space-between", alignItems: "center", gap: 8, flexWrap: "wrap" }}>
              <span style={q.active ? undefined : { opacity: 0.5, textDecoration: "line-through" }}>
                {q.question}{q.tenantId === null ? <span className="badge badge-rule" style={{ marginLeft: 6 }}>global</span> : null}
              </span>
              <span style={{ display: "flex", gap: 6 }}>
                <button data-testid={`secq-toggle-${q.id}`} onClick={() => toggleActive(q)}>{q.active ? "Désactiver" : "Activer"}</button>
                <button data-testid={`secq-rename-${q.id}`} onClick={() => rename(q)}>Modifier</button>
                <button className="danger" data-testid={`secq-delete-${q.id}`} onClick={() => remove(q)}>Supprimer</button>
              </span>
            </li>
          ))}
          {items.length === 0 && <li className="empty-cell">Aucune question dans ce catalogue.</li>}
        </ul>
      )}
      {ConfirmHost}
    </div>
  );
}
