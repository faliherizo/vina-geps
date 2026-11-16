/**
 * Administration des utilisateurs (admin uniquement).
 */
import { useEffect, useState } from "react";
import { Message, EnTeteEcran, Onglets, Icone } from "./composants";
import TbodyPagine from "./TbodyPagine";
import AuditIntegrite from "./AuditIntegrite";
import JournalActions from "./JournalActions";
import IncidentsConfidentialite from "./IncidentsConfidentialite";
import OrdonnanceurPanel from "./OrdonnanceurPanel";
import { useModuleAllowed } from "./moduleAccess";
import { useAuth } from "./AuthContext";
import RolesPanel from "./admin/RolesPanel";
import EmpruntIdentitePanel from "./admin/EmpruntIdentite";
import OptionsOrganisme from "./admin/OptionsOrganisme";
import AnnuairesOrganisme from "./admin/AnnuairesOrganisme";
import CopiesOrganisme from "./admin/CopiesOrganisme";
import { useConfirm } from "./Modal";
import Pagination, { usePagination } from "./Pagination";

interface User {
  id: string; username: string; role: "admin" | "manager" | "employee" | "rh";
  employeeId?: string | null; active: boolean;
}
interface Emp {
  id: string; firstName: string; lastName: string;
  email?: string; phone?: string; address?: string; dateOfBirth?: string;
  managerId?: string | null;
}

/** Si la liste du serveur ne se lit pas, la création propose au moins les rôles de base. */
const ROLES_DE_BASE: ReadonlyArray<{ role: string; label: string }> = [
  { role: "employee", label: "Employé" }, { role: "manager", label: "Gestionnaire" },
  { role: "rh", label: "Ressources humaines (RH)" }, { role: "admin", label: "Administrateur" },
];

export default function Admin() {
  const { apiFetch, user } = useAuth();
  const [users, setUsers] = useState<User[]>([]);
  /** Rôles attribuables et leurs libellés — lus du serveur, jamais recopiés (voir Admin.tsx). */
  const [rolesAttribuables, setRolesAttribuables] = useState<Array<{ role: string; label: string }>>([]);
  useEffect(() => {
    apiFetch("/api/auth/assignable-roles")
      .then((r) => (r.ok ? r.json() : []))
      .then((rs) => { if (Array.isArray(rs) && rs.length) setRolesAttribuables(rs); })
      .catch(() => { /* la liste reste vide : le sélecteur n'affiche alors que le rôle courant */ });
  }, [apiFetch]);
  const [employees, setEmployees] = useState<Emp[]>([]);
  const [audit, setAudit] = useState<any[]>([]);
  const [auditTotal, setAuditTotal] = useState(0);
  const [auditPage, setAuditPage] = useState(0);
  const [auditQ, setAuditQ] = useState("");
  const AUDIT_PAGE_SIZE = 15;
  // Console SIEM + rétention/Loi 25.
  const [siem, setSiem] = useState<any>(null);
  const [siemLevel, setSiemLevel] = useState("");
  const [retentionDays, setRetentionDays] = useState("365");
  const [eraseUser, setEraseUser] = useState("");
  const [secMsg, setSecMsg] = useState("");
  // Clés d'API / OAuth2 client-credentials (NF-08).
  const [apiClients, setApiClients] = useState<any[]>([]);
  const [apiClientName, setApiClientName] = useState("");
  const [apiClientScopes, setApiClientScopes] = useState<string[]>(["read"]);
  const [newClientSecret, setNewClientSecret] = useState<any>(null);
  const [form, setForm] = useState({ username: "", password: "", role: "employee" as User["role"], employeeId: "" });
  const usersPager = usePagination(users, 15);
  const isAdmin = user?.role === "admin";
  // Regroupement du contenu par sous-menus pour éviter une page unique très longue.
  const [section, setSection] = useState<"comptes" | "roles" | "config" | "audit" | "confidentialite" | "taches" | "organisme">("comptes");
  const moduleAutorise = useModuleAllowed();
  const [error, setError] = useState("");
  const { confirm, ConfirmHost } = useConfirm();

  async function load() {
    const [u, e] = await Promise.all([
      apiFetch("/api/auth/users").then((r) => r.ok ? r.json() : []),
      apiFetch("/api/personnel/employes").then((r) => r.ok ? r.json() : []),
    ]);
    setUsers(Array.isArray(u) ? u : []); setEmployees(Array.isArray(e) ? e : []);
    loadAudit();
  }

  /** Audit paginé + filtré + isolé par tenant (côté serveur). */
  async function loadAudit() {
    const params = new URLSearchParams({ page: String(auditPage), pageSize: String(AUDIT_PAGE_SIZE) });
    if (auditQ.trim()) params.set("q", auditQ.trim());
    const r = await apiFetch(`/api/auth/audit?${params.toString()}`);
    if (r.ok) { const env = await r.json(); setAudit(env.items ?? []); setAuditTotal(env.total ?? 0); }
  }
  useEffect(() => { load(); }, []);
  useEffect(() => { loadAudit(); }, [auditPage, auditQ]); // eslint-disable-line react-hooks/exhaustive-deps

  async function loadSiem() {
    const qs = siemLevel ? `?minLevel=${siemLevel}` : "";
    const r = await apiFetch(`/api/auth/security/siem-console${qs}`);
    if (r.ok) setSiem(await r.json());
  }
  async function purgeRetention() {
    setSecMsg("");
    const r = await apiFetch("/api/auth/security/retention/purge", { method: "POST", body: JSON.stringify({ retentionDays: Number(retentionDays) }) });
    const j = await r.json().catch(() => ({}));
    if (!r.ok) { setSecMsg((j.errors || ["Purge impossible"]).join(", ")); return; }
    setSecMsg(`${j.purged} entrée(s) purgée(s) (antérieures au ${j.cutoff.slice(0, 10)}).`); loadAudit(); loadSiem();
  }
  async function eraseUserData() {
    setSecMsg("");
    if (!eraseUser.trim()) { setSecMsg("Nom d'utilisateur requis pour l'effacement."); return; }
    const r = await apiFetch("/api/auth/security/retention/erase-user", { method: "POST", body: JSON.stringify({ username: eraseUser.trim() }) });
    const j = await r.json().catch(() => ({}));
    if (!r.ok) { setSecMsg((j.errors || ["Effacement impossible"]).join(", ")); return; }
    setSecMsg(`${j.anonymized} entrée(s) anonymisée(s) pour « ${j.username} ».`); setEraseUser(""); loadAudit();
  }
  async function loadApiClients() {
    const r = await apiFetch("/api/integrations/api-clients");
    if (r.ok) setApiClients(await r.json());
  }
  useEffect(() => { if (section === "config" && isAdmin) loadApiClients(); }, [section]); // eslint-disable-line react-hooks/exhaustive-deps
  async function createApiClient() {
    if (!apiClientName.trim()) { setSecMsg("Nom du client d'API requis."); return; }
    const r = await apiFetch("/api/integrations/api-clients", { method: "POST", body: JSON.stringify({ name: apiClientName.trim(), scopes: apiClientScopes }) });
    const j = await r.json().catch(() => ({}));
    if (!r.ok) { setSecMsg((j.errors || ["Création impossible"]).join(", ")); return; }
    setNewClientSecret(j); setApiClientName(""); loadApiClients();
  }
  async function revokeApiClient(id: string) {
    const r = await apiFetch(`/api/integrations/api-clients/${id}`, { method: "DELETE" });
    if (r.ok || r.status === 204) loadApiClients();
  }

  async function create() {
    setError("");
    const body: any = { ...form };
    if (!body.employeeId) delete body.employeeId;
    const r = await apiFetch("/api/auth/users", { method: "POST", body: JSON.stringify(body) });
    if (!r.ok) { const j = await r.json(); setError(j.errors?.join(", ") || "Erreur"); return; }
    setForm({ username: "", password: "", role: "employee", employeeId: "" });
    load();
  }

  async function toggleActive(u: User) {
    await apiFetch(`/api/auth/users/${u.id}`, { method: "PATCH", body: JSON.stringify({ active: !u.active }) });
    load();
  }

  async function changeRole(u: User, role: User["role"]) {
    await apiFetch(`/api/auth/users/${u.id}`, { method: "PATCH", body: JSON.stringify({ role }) });
    load();
  }

  async function remove(u: User) {
    const ok = await confirm({
      title: "Supprimer l'utilisateur",
      message: <>Voulez-vous vraiment supprimer le compte <strong>{u.username}</strong> ? Cette action est irréversible.</>,
      confirmLabel: "Supprimer",
      variant: "danger",
      testId: "admin-delete-modal",
    });
    if (!ok) return;
    await apiFetch(`/api/auth/users/${u.id}`, { method: "DELETE" });
    load();
  }

  function empLabel(id?: string | null) {
    if (!id) return "—";
    const e = employees.find((x) => x.id === id);
    return e ? `${e.firstName} ${e.lastName}` : id;
  }

  return (
    <div data-testid="admin-page">
      <EnTeteEcran icone="reglages" titre="Administration" description={<>Gestion des comptes, rôles et audit log.</>} />

      <Onglets libelle="Sections de l'administration" testId="admin-tabs" actif={section} onChange={(k) => setSection(k)} onglets={[
        ...(isAdmin ? [
          { id: "comptes" as const, libelle: "Comptes et rôles", testId: "admin-tab-comptes", icone: "personnes" as const },
          { id: "roles" as const, libelle: "Rôles et accès", testId: "admin-tab-roles", icone: "cadenas" as const },
          { id: "config" as const, libelle: "Clés d'API", testId: "admin-tab-config", icone: "reglages" as const },
          { id: "audit" as const, libelle: "Journal d'audit", testId: "admin-tab-audit", icone: "document" as const },
          ...(moduleAutorise("confidentialite") ? [{ id: "confidentialite" as const, libelle: "Confidentialité", testId: "admin-tab-confidentialite", icone: "bouclier" as const }] : []),
          { id: "taches" as const, libelle: "Tâches différées", testId: "admin-tab-taches", icone: "horloge" as const },
          // Charte, pays, alertes : des options d'un organisme — un compte global n'en a pas (lot R11).
          ...(user?.tenantId ? [{ id: "organisme" as const, libelle: "Organisme", testId: "admin-tab-organisme", icone: "reglages" as const }] : []),
        ] : []),
      ]} />

      {section === "comptes" && isAdmin && (
      <div className="card">
        <h3>Nouvel utilisateur</h3>
        {error && <Message variante="erreur">{error}</Message>}
        <div className="row-form">
          <div><label htmlFor="f-admin-username">Identifiant <span className="req-star" aria-hidden="true">*</span></label>
            <input id="f-admin-username" data-testid="admin-username" required aria-required="true" value={form.username} onChange={(e) => setForm({ ...form, username: e.target.value })} />
          </div>
          <div><label htmlFor="f-admin-password">Mot de passe provisoire <span className="req-star" aria-hidden="true">*</span></label>
            <input id="f-admin-password" data-testid="admin-password" required aria-required="true" type="password" minLength={8} value={form.password} onChange={(e) => setForm({ ...form, password: e.target.value })} placeholder="≥ 8 caractères" />
            <small className="muted" style={{ fontSize: 11, display: "block" }}>Provisoire (≥ 8). L'utilisateur définira un mot de passe fort + MFA + 3 questions au 1er accès.</small>
          </div>
          <div><label htmlFor="f-admin-role">Rôle</label>
            {/* La même liste que pour changer le rôle d'un compte : celle du serveur, rôles personnalisés compris.
                Écrite ici en dur, elle omettait le comptable, le directeur et tout rôle créé dans « Rôles ». */}
            <select id="f-admin-role" data-testid="admin-role" value={form.role} onChange={(e) => setForm({ ...form, role: e.target.value as any })}>
              {(rolesAttribuables.length > 0 ? rolesAttribuables : ROLES_DE_BASE).map((r) => <option key={r.role} value={r.role}>{r.label}</option>)}
            </select>
          </div>
          <div><label htmlFor="f-Admin-10">Lié à l'employé (optionnel)</label>
            <select id="f-Admin-10" value={form.employeeId} onChange={(e) => setForm({ ...form, employeeId: e.target.value })}>
              <option value="">—</option>
              {employees.map((e) => <option key={e.id} value={e.id}>{e.firstName} {e.lastName}</option>)}
            </select>
          </div>
        </div>
        <div className="actions" style={{ marginTop: 12 }}>
          <button className="primary" data-testid="admin-create" onClick={create}>Créer</button>
        </div>
      </div>
      )}

      {section === "comptes" && isAdmin && (
      <div className="card">
        <h3>Utilisateurs</h3>
        <div className="table-wrap" tabIndex={0} role="region" aria-label="Tableau (zone défilable au clavier)">
          <table>
            <caption className="sr-only">Utilisateurs</caption>
            <thead><tr><th scope="col">Identifiant</th><th scope="col">Rôle</th><th scope="col">Employé lié</th><th scope="col">Actif</th><th scope="col"></th></tr></thead>
            <tbody data-testid="admin-users-list">
              {usersPager.pageItems.map((u) => (
                <tr key={u.id}>
                  <td>{u.username}</td>
                  <td>
                    {/* Les rôles viennent du serveur : la liste écrite ici en dur ignorait
                        `director` et `accountant`, pourtant reconnus — on ne pouvait donc pas
                        les attribuer, et un directeur ne naissait que d'une bascule SSO. */}
                    <select disabled={u.id === user?.id} aria-label={`Rôle de ${u.username}`} value={u.role} onChange={(e) => changeRole(u, e.target.value as any)}>
                      {rolesAttribuables.map((r) => (
                        <option key={r.role} value={r.role}>{r.label}</option>
                      ))}
                    </select>
                  </td>
                  <td>{empLabel(u.employeeId)}</td>
                  <td>
                    <button className={u.active ? "success" : "danger"} disabled={u.id === user?.id} onClick={() => toggleActive(u)}>
                      {u.active ? "actif" : "inactif"}
                    </button>
                  </td>
                  <td>
                    {u.id !== user?.id && <button className="danger" onClick={() => remove(u)}>Supprimer</button>}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
        <Pagination page={usersPager.page} pageCount={usersPager.pageCount} total={usersPager.total} pageSize={usersPager.pageSize} onPage={usersPager.setPage} testId="admin-users-pager" />
      </div>
      )}

      {section === "comptes" && isAdmin && <EmpruntIdentitePanel apiFetch={apiFetch} comptes={users} />}

      {section === "roles" && isAdmin && <RolesPanel />}

      {/* Clés d'API / OAuth2 client-credentials (NF-08) pour intégrations externes. */}
      {section === "config" && isAdmin && (
      <div className="card" data-testid="api-clients">
        <h3><Icone nom="cadenas" /> Clés d'API / OAuth2 (intégrations externes)</h3>
        <p className="muted">Créez un client d'API ; le secret n'est affiché qu'une seule fois. Les intégrations obtiennent un jeton via <code>POST /api/oauth/token</code> (grant <code>client_credentials</code>).</p>
        <div className="actions-row">
          <input data-testid="api-client-name" required value={apiClientName} onChange={(e) => setApiClientName(e.target.value)} placeholder="Nom du client (ex. ERP)" aria-label="Nom du client d'API" />
          <label><input type="checkbox" data-testid="api-scope-read" checked={apiClientScopes.includes("read")} onChange={(e) => setApiClientScopes((s) => e.target.checked ? [...new Set([...s, "read"])] : s.filter((x) => x !== "read"))} /> read</label>
          <label><input type="checkbox" data-testid="api-scope-write" checked={apiClientScopes.includes("write")} onChange={(e) => setApiClientScopes((s) => e.target.checked ? [...new Set([...s, "write"])] : s.filter((x) => x !== "write"))} /> write</label>
          <button className="primary" data-testid="api-client-create" onClick={createApiClient}>Créer le client</button>
        </div>
        {newClientSecret && (
          <div className="info-box" data-testid="api-client-secret" style={{ marginTop: 8 }}>
            ⚠ Notez ce secret maintenant (affiché une seule fois) — client_id : <code>{newClientSecret.clientId}</code> · client_secret : <code data-testid="api-client-secret-value">{newClientSecret.clientSecret}</code>
          </div>
        )}
        <div className="table-wrap" tabIndex={0} role="region" aria-label="Clients d'API (zone défilable)" style={{ marginTop: 8 }}>
          <table data-testid="api-clients-table">
            <caption className="sr-only">Clients d'API : nom, identifiant, portées, statut, action</caption>
            <thead><tr><th scope="col">Nom</th><th scope="col">client_id</th><th scope="col">Portées</th><th scope="col">Statut</th><th scope="col">Action</th></tr></thead>
            <TbodyPagine items={apiClients} vide={<tr><td colSpan={5} className="muted">Aucun client d'API.</td></tr>} rendre={(c) => (
                <tr key={c.id} data-testid={`api-client-row-${c.id}`}>
                  <td>{c.name}</td><td><code>{c.clientId}</code></td><td>{c.scopes.join(", ")}</td>
                  <td>{c.active ? <span className="badge badge-active">Actif</span> : <span className="badge cancelled">Révoqué</span>}</td>
                  <td>{c.active && <button className="danger" data-testid={`api-client-revoke-${c.id}`} onClick={() => revokeApiClient(c.id)}>Révoquer</button>}</td>
                </tr>
              )} />
          </table>
        </div>
      </div>
      )}

      {section === "audit" && isAdmin && (
      <div className="card">
        <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", gap: 8, flexWrap: "wrap", marginBottom: 12 }}>
          <h3 style={{ margin: 0 }}>Journal d'audit ({auditTotal})</h3>
          <div className="actions-row">
            <input data-testid="audit-search" type="search" placeholder="🔍 Filtrer (utilisateur, action, cible, organisme…)"
              aria-label="Filtrer le journal d'audit" value={auditQ}
              onChange={(e) => { setAuditQ(e.target.value); setAuditPage(0); }} style={{ minWidth: 280 }} />
            {/* Export SIEM (#86) : CEF (ArcSight/Sentinel/Splunk) ou NDJSON. */}
            <a className="btn secondary" data-testid="audit-siem-cef" href="/api/auth/audit/siem-export?format=cef" target="_blank" rel="noreferrer" style={{ textDecoration: "none" }}>⬇ SIEM (CEF)</a>
            <a className="btn secondary" data-testid="audit-siem-ndjson" href="/api/auth/audit/siem-export?format=ndjson" target="_blank" rel="noreferrer" style={{ textDecoration: "none" }}>⬇ NDJSON</a>
          </div>
        </div>
        <div className="table-wrap" tabIndex={0} role="region" aria-label="Tableau (zone défilable au clavier)">
          <table data-testid="audit-table">
            <caption className="sr-only">Tableau de données</caption>
            <thead><tr><th scope="col">Date</th><th scope="col">Organisme</th><th scope="col">Utilisateur</th><th scope="col">Action</th><th scope="col">Cible</th></tr></thead>
            <TbodyPagine items={audit} vide={<tr><td colSpan={5} className="muted">Aucun événement.</td></tr>} rendre={(a) => (
                <tr key={a.id} data-testid={`audit-row-${a.id}`}>
                  <td>{a.at?.slice(0, 19).replace("T", " ")}</td>
                  <td data-testid={`audit-tenant-${a.id}`}>{a.tenantName || (a.tenantId ? a.tenantId : "— global —")}</td>
                  {/* Sous une identité empruntée (ADR 0038), l'acteur réel est écrit à côté de l'identité agissante. */}
                  <td>{a.username || "—"}{a.acteurReel ? <span className="muted" data-testid={`audit-acteur-reel-${a.id}`}> (agissant : {a.acteurReel})</span> : null}</td>
                  <td>{a.action}</td>
                  <td>{a.target || "—"}</td>
                </tr>
              )} />
          </table>
        </div>
        {auditTotal > AUDIT_PAGE_SIZE && (
          <div style={{ display: "flex", justifyContent: "center", alignItems: "center", gap: 8, marginTop: 8 }}>
            <button className="secondary" data-testid="audit-prev" disabled={auditPage === 0} onClick={() => setAuditPage((p) => Math.max(0, p - 1))} aria-label="Page précédente">‹</button>
            <span className="muted" data-testid="audit-page">Page {auditPage + 1} / {Math.max(1, Math.ceil(auditTotal / AUDIT_PAGE_SIZE))}</span>
            <button className="secondary" data-testid="audit-next" disabled={(auditPage + 1) * AUDIT_PAGE_SIZE >= auditTotal} onClick={() => setAuditPage((p) => p + 1)} aria-label="Page suivante">›</button>
          </div>
        )}
      </div>
      )}

      {/* Le journal des actions (ADR 0041) : exhaustif et mécanique, à côté de l'audit sélectif et scellé. */}
      {section === "audit" && isAdmin && <JournalActions apiFetch={apiFetch} superadmin={user?.role === "superadmin"} />}
      {/* Console SIEM (sévérité/niveau) + rétention & effacement Loi 25. */}
      {section === "audit" && isAdmin && <AuditIntegrite apiFetch={apiFetch} peutConsigner={false} />}
      {section === "confidentialite" && isAdmin && moduleAutorise("confidentialite") && <IncidentsConfidentialite apiFetch={apiFetch} />}
      {section === "taches" && isAdmin && <OrdonnanceurPanel apiFetch={apiFetch} />}
      {section === "organisme" && isAdmin && user?.tenantId && <OptionsOrganisme apiFetch={apiFetch} />}
      {section === "organisme" && isAdmin && user?.tenantId && <AnnuairesOrganisme apiFetch={apiFetch} />}
      {section === "organisme" && isAdmin && user?.tenantId && <CopiesOrganisme apiFetch={apiFetch} />}
      {section === "audit" && isAdmin && (
      <div className="card" data-testid="siem-console" style={{ marginTop: 12 }}>
        <h3><Icone nom="bouclier" /> Console SIEM & rétention (Loi 25)</h3>
        {secMsg && <Message variante="info" testId="sec-msg">{secMsg}</Message>}
        <div className="actions-row" style={{ marginBottom: 8 }}>
          <label htmlFor="f-siem-level">Niveau minimum</label>
          <select id="f-siem-level" data-testid="siem-level" value={siemLevel} onChange={(e) => setSiemLevel(e.target.value)} style={{ width: "auto" }}>
            <option value="">Tous</option>
            <option value="high">Élevé</option>
            <option value="medium">Moyen</option>
            <option value="low">Faible</option>
          </select>
          <button className="primary" data-testid="siem-load" onClick={loadSiem}>Charger les événements</button>
        </div>
        {siem && (
          <>
            <div className="totals" data-testid="siem-summary" style={{ marginBottom: 8 }}>
              <span className="badge cancelled" data-testid="siem-high">Élevé : {siem.summary.high}</span>{" "}
              <span className="badge on_hold" data-testid="siem-medium">Moyen : {siem.summary.medium}</span>{" "}
              <span className="badge badge-active" data-testid="siem-low">Faible : {siem.summary.low}</span>{" "}
              <span className="muted">Total : {siem.summary.total}</span>
            </div>
            <div className="table-wrap" tabIndex={0} role="region" aria-label="Événements SIEM (zone défilable)">
              <table data-testid="siem-table">
                <caption className="sr-only">Événements de sécurité : date, niveau, sévérité, utilisateur, action</caption>
                <thead><tr><th scope="col">Date</th><th scope="col">Niveau</th><th scope="col">Sév.</th><th scope="col">Utilisateur</th><th scope="col">Action</th></tr></thead>
                <TbodyPagine items={siem.events} vide={<tr><td colSpan={5} className="muted">Aucun événement.</td></tr>} rendre={(e: any, i: number) => (
                    <tr key={i}><td>{e.at?.slice(0, 19).replace("T", " ")}</td><td><span className={`badge ${e.level === "high" ? "cancelled" : e.level === "medium" ? "on_hold" : "badge-active"}`}>{e.level}</span></td><td>{e.severity}</td><td>{e.username || "—"}</td><td>{e.action}</td></tr>
                  )} />
              </table>
            </div>
          </>
        )}
        <div style={{ marginTop: 12, paddingTop: 12, borderTop: "1px solid #ddd" }}>
          <h4>Rétention & effacement (Loi 25)</h4>
          <div className="actions-row">
            <label htmlFor="f-retention">Rétention (jours) <span aria-hidden="true">*</span></label>
            <input id="f-retention" type="number" min={365} data-testid="retention-days" required value={retentionDays} onChange={(e) => setRetentionDays(e.target.value)} style={{ width: 100 }} />
            <button className="danger" data-testid="retention-purge" onClick={purgeRetention}>Purger l'audit ancien</button>
          </div>
          <div className="actions-row" style={{ marginTop: 8 }}>
            <label htmlFor="f-erase-user">Effacer les données d'un utilisateur</label>
            <input id="f-erase-user" data-testid="erase-user" value={eraseUser} onChange={(e) => setEraseUser(e.target.value)} placeholder="nom d'utilisateur" />
            <button className="danger" data-testid="erase-run" onClick={eraseUserData}>Droit à l'oubli</button>
          </div>
        </div>
      </div>
      )}
      {ConfirmHost}
    </div>
  );
}
