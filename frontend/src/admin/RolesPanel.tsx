/**
 * Rôles et accès de l'établissement.
 *
 * Le catalogue des rôles vivait dans le code : un établissement ne pouvait ni créer le sien —
 * « Coordonnateur », « Doyen », « Agent d'accueil » — ni ajuster ce qu'un rôle existant a le
 * droit de faire. Toute adaptation demandait un déploiement.
 *
 * Cet écran met la décision là où elle se prend. Le serveur reste l'autorité : ce que l'on coche
 * ici est appliqué route par route, pas seulement affiché.
 */
import { useCallback, useEffect, useState } from "react";
import { Message, Icone } from "../composants";
import TbodyPagine from "../TbodyPagine";
import { useAuth } from "../AuthContext";

interface RoleTenant {
  code: string;
  label: string;
  builtin: boolean;
  active: boolean;
  capabilities: string[];
}
interface CapabilityInfo {
  capability: string;
  libelle: string;
  portee: string | null;
}

export default function RolesPanel() {
  const { apiFetch } = useAuth();
  const [roles, setRoles] = useState<RoleTenant[]>([]);
  const [catalogue, setCatalogue] = useState<CapabilityInfo[]>([]);
  const [selection, setSelection] = useState<string>("");
  const [form, setForm] = useState({ code: "", label: "" });
  const [message, setMessage] = useState("");
  const [erreur, setErreur] = useState("");

  const charger = useCallback(async () => {
    const r = await apiFetch("/api/auth/roles");
    if (!r.ok) { setErreur("Vous n'avez pas accès à la gestion des rôles."); return; }
    const data = await r.json();
    setRoles(data.roles ?? []);
    setCatalogue(data.catalog ?? []);
    setSelection((prev) => prev || (data.roles?.[0]?.code ?? ""));
  }, [apiFetch]);

  useEffect(() => { charger(); }, [charger]);

  useEffect(() => {
    if (!message && !erreur) return;
    const h = setTimeout(() => { setMessage(""); setErreur(""); }, 4000);
    return () => clearTimeout(h);
  }, [message, erreur]);

  const roleActif = roles.find((r) => r.code === selection) ?? null;

  async function creerRole() {
    const r = await apiFetch("/api/auth/roles", { method: "POST", body: JSON.stringify(form) });
    if (r.ok) {
      const cree = await r.json();
      setMessage(`Rôle « ${cree.label} » créé. Il ne détient encore aucun accès : cochez ce qu'il peut faire.`);
      setForm({ code: "", label: "" });
      setSelection(cree.code);
      charger();
    } else {
      setErreur(((await r.json().catch(() => ({}))).errors ?? ["Création refusée"]).join(", "));
    }
  }

  async function basculerAcces(capability: string, accorde: boolean) {
    const r = await apiFetch(`/api/auth/roles/${selection}/capabilities/${capability}`, {
      method: "PUT", body: JSON.stringify({ allowed: accorde }),
    });
    if (r.ok) charger();
    else setErreur(((await r.json().catch(() => ({}))).errors ?? ["Modification refusée"]).join(", "));
  }

  async function renommerRole(code: string, label: string) {
    const r = await apiFetch(`/api/auth/roles/${code}`, { method: "PATCH", body: JSON.stringify({ label }) });
    if (!r.ok) { const j = await r.json().catch(() => ({})); setMessage((j.errors ?? ["Renommage impossible"]).join(", ")); return; }
    setMessage(`Rôle « ${label} » renommé.`);
    await charger();
  }

  async function supprimerRole(code: string) {
    const r = await apiFetch(`/api/auth/roles/${code}`, { method: "DELETE" });
    if (r.ok || r.status === 204) { setMessage("Rôle supprimé."); setSelection(""); charger(); }
    else setErreur(((await r.json().catch(() => ({}))).errors ?? ["Suppression refusée"]).join(", "));
  }

  return (
    <section data-testid="admin-roles">
      <h2 className="section-title"><Icone nom="cadenas" /> Rôles &amp; accès</h2>
      <p className="section-help">
        Un rôle décide des menus et des droits de ceux qui le portent. Les rôles livrés avec le
        produit ne se suppriment pas, mais leurs accès s'ajustent — <strong>chez vous seulement</strong>.
        Vos propres rôles partent sans aucun accès : le logiciel ne peut pas deviner ce qu'un
        « Coordonnateur » a le droit de faire.
      </p>

      {erreur && <Message variante="erreur" testId="roles-error">{erreur}</Message>}
      {message && <Message variante="succes" testId="roles-success">{message}</Message>}

      <fieldset className="fieldset">
        <legend>Créer un rôle</legend>
        <div className="grid-form">
          <label className="field"><span>Code</span>
            <input data-testid="role-code" value={form.code} placeholder="coordonnateur"
              onChange={(e) => setForm({ ...form, code: e.target.value })} /></label>
          <label className="field"><span>Nom affiché</span>
            <input data-testid="role-label" value={form.label} placeholder="Coordonnateur"
              onChange={(e) => setForm({ ...form, label: e.target.value })} /></label>
          <div className="field field-actions">
            <button data-testid="role-create" className="primary" onClick={creerRole}
              disabled={!form.code.trim() || !form.label.trim()}>Créer le rôle</button>
          </div>
        </div>
      </fieldset>

      <div className="grid-form">
        <label className="field"><span>Rôle à configurer</span>
          <select data-testid="role-select" value={selection} onChange={(e) => setSelection(e.target.value)}>
            {roles.map((r) => (
              <option key={r.code} value={r.code}>
                {r.label}{r.builtin ? " (livré)" : ""}
              </option>
            ))}
          </select></label>
        {roleActif && !roleActif.builtin && (
          <>
            {/* Renommer un rôle maison : le code reste (il est cité par les comptes), le nom affiché change. */}
            <label className="field"><span>Renommer</span>
              <input data-testid={`role-rename-${roleActif.code}`} defaultValue={roleActif.label} key={roleActif.code}
                onBlur={(e) => { if (e.target.value.trim() && e.target.value.trim() !== roleActif.label) void renommerRole(roleActif.code, e.target.value.trim()); }} /></label>
            <div className="field field-actions">
              <button className="danger" data-testid={`role-delete-${roleActif.code}`}
                onClick={() => supprimerRole(roleActif.code)}>Supprimer ce rôle</button>
            </div>
          </>
        )}
      </div>

      {roleActif && (
        <div className="table-wrap" tabIndex={0} role="region" aria-label="Accès du rôle (zone défilable au clavier)">
          <table data-testid="role-capabilities">
            <caption className="sr-only">Accès accordés au rôle {roleActif.label}</caption>
            <thead>
              <tr>
                <th scope="col">Accordé</th>
                <th scope="col">Ce que le rôle peut faire</th>
                <th scope="col">Portée</th>
              </tr>
            </thead>
            <TbodyPagine items={catalogue} rendre={(c) => {
                const accorde = roleActif.capabilities.includes(c.capability);
                // Les droits de l'administrateur ne sont pas révocables : sans lui, plus
                // personne ne pourrait les rendre.
                const verrouille = roleActif.code === "admin";
                return (
                  <tr key={c.capability} data-testid={`role-cap-${c.capability}`}>
                    <td>
                      <input type="checkbox" checked={accorde} disabled={verrouille}
                        data-testid={`role-cap-toggle-${c.capability}`}
                        aria-label={`${c.libelle} — ${accorde ? "accordé" : "refusé"} au rôle ${roleActif.label}`}
                        onChange={(e) => basculerAcces(c.capability, e.target.checked)} />
                    </td>
                    <td>{c.libelle}</td>
                    <td className="muted">{c.portee ?? "—"}</td>
                  </tr>
                );
              }} />
          </table>
        </div>
      )}

      {roleActif?.code === "admin" && (
        <p className="section-help" role="status">
          Les accès de l'administrateur sont verrouillés : les retirer laisserait l'organisme
          sans personne pour les rendre.
        </p>
      )}
    </section>
  );
}
