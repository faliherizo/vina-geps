/**
 * **Annonces** (CSSMB 21 ; ADR 0039) — un panneau, pas un courrier. L'administration publie
 * ({@link AnnoncesAdmin} : titre, corps, rôles visés, période, retrait, lectures) ; à l'accueil,
 * chacun lit ce qui le vise et le marque lu ({@link AnnoncesAccueil}). L'état « lue » est écrit,
 * jamais porté par la seule couleur.
 */
import { useCallback, useEffect, useState } from "react";
import { Message } from "./composants";

type Appel = (chemin: string, init?: RequestInit) => Promise<Response>;
export interface Annonce { id: string; titre: string; corps: string; roles: string[]; du: string; au: string; auteur: string; creeeLe: string; lue?: boolean; lectures?: number }

export const ROLES_ANNONCE: Array<{ code: string; libelle: string }> = [
  { code: "employee", libelle: "Employés" }, { code: "manager", libelle: "Gestionnaires" }, { code: "director", libelle: "Directions" },
  { code: "rh", libelle: "Ressources humaines" }, { code: "accountant", libelle: "Comptabilité" }, { code: "admin", libelle: "Administration" },
];
export const libelleRoles = (roles: string[]) => (roles.length === 0 ? "Tous" : roles.map((r) => ROLES_ANNONCE.find((x) => x.code === r)?.libelle ?? r).join(", "));

/** Le bloc de l'accueil : les annonces en cours qui visent le rôle, les non lues d'abord. Rien à afficher : rien de rendu. */
export function AnnoncesAccueil({ call }: { call: Appel }) {
  const [annonces, setAnnonces] = useState<Annonce[]>([]);
  const charger = useCallback(async () => {
    const r = await call("/api/notifications/annonces");
    const l = r.ok ? await r.json().catch(() => []) : [];
    setAnnonces(Array.isArray(l) ? l : []);
  }, [call]);
  useEffect(() => { void charger(); }, [charger]);
  const lire = async (id: string) => { const r = await call(`/api/notifications/annonces/${id}/lue`, { method: "POST", body: "{}" }); if (r.ok) await charger(); };
  if (annonces.length === 0) return null;
  return (
    <section className="card" aria-labelledby="annonces-titre" data-testid="annonces">
      <h2 id="annonces-titre">Annonces</h2>
      <ul className="liste-annonces">
        {annonces.map((a) => (
          <li key={a.id} data-testid={`annonce-${a.id}`}>
            <h3>{a.titre} <span className="muted">— {a.lue ? "lue" : "non lue"} · jusqu'au {a.au}</span></h3>
            <p style={{ whiteSpace: "pre-wrap" }}>{a.corps}</p>
            {!a.lue && <button type="button" className="secondary" data-testid={`annonce-lue-${a.id}`} onClick={() => void lire(a.id)}>Marquer comme lue</button>}
          </li>
        ))}
      </ul>
    </section>
  );
}

/** L'administration publie et retire ; la liste dit qui a lu (un nombre, pas des noms). */
export default function AnnoncesAdmin({ call, signaler, confirmer }: { call: Appel; signaler: (m: string) => void; confirmer: (m: string) => void }) {
  const [annonces, setAnnonces] = useState<Annonce[]>([]);
  const aujourdhui = new Date().toISOString().slice(0, 10);
  const [form, setForm] = useState({ titre: "", corps: "", roles: [] as string[], du: aujourdhui, au: aujourdhui });
  const charger = useCallback(async () => {
    const r = await call("/api/notifications/annonces/toutes");
    const l = r.ok ? await r.json().catch(() => []) : [];
    setAnnonces(Array.isArray(l) ? l : []);
  }, [call]);
  useEffect(() => { void charger(); }, [charger]);

  const publier = async (e: React.FormEvent) => {
    e.preventDefault();
    const r = await call("/api/notifications/annonces", { method: "POST", body: JSON.stringify(form) });
    const d = await r.json().catch(() => ({}));
    if (!r.ok) { signaler((d.errors ?? ["refus du serveur"]).join(" ; ")); return; }
    confirmer(`Annonce « ${d.titre} » publiée.`);
    setForm({ titre: "", corps: "", roles: [], du: aujourdhui, au: aujourdhui });
    await charger();
  };
  const retirer = async (a: Annonce) => {
    const r = await call(`/api/notifications/annonces/${a.id}`, { method: "DELETE" });
    if (!r.ok && r.status !== 204) { signaler(`Retrait refusé (${r.status}).`); return; }
    confirmer(`Annonce « ${a.titre} » retirée.`);
    await charger();
  };

  return (
    <section className="card" aria-labelledby="annonces-admin-titre" data-testid="annonces-admin">
      <h2 id="annonces-admin-titre">Annonces à l'accueil</h2>
      <p className="section-help">Une annonce s'affiche à l'accueil de chaque compte visé pendant sa période, et la notification web en reprend le titre. Ce n'est pas un courrier : personne ne la ferme, chacun la marque lue.</p>
      <form className="grid-form" onSubmit={(e) => void publier(e)} aria-label="Publier une annonce">
        <label className="field"><span>Titre</span><input data-testid="annonce-titre" value={form.titre} maxLength={120} required onChange={(e) => setForm({ ...form, titre: e.target.value })} /></label>
        <label className="field field-full"><span>Texte</span><textarea data-testid="annonce-corps" rows={3} value={form.corps} maxLength={2000} required onChange={(e) => setForm({ ...form, corps: e.target.value })} /></label>
        <fieldset className="fieldset field-full" data-testid="annonce-roles">
          <legend>Rôles visés (aucun coché : tous)</legend>
          {ROLES_ANNONCE.map((r) => (
            <label key={r.code} className="field-inline"><input type="checkbox" data-testid={`annonce-role-${r.code}`} checked={form.roles.includes(r.code)}
              onChange={(e) => setForm({ ...form, roles: e.target.checked ? [...form.roles, r.code] : form.roles.filter((x) => x !== r.code) })} /> {r.libelle}</label>
          ))}
        </fieldset>
        <label className="field"><span>Du</span><input type="date" data-testid="annonce-du" value={form.du} required onChange={(e) => setForm({ ...form, du: e.target.value })} /></label>
        <label className="field"><span>Au</span><input type="date" data-testid="annonce-au" value={form.au} required onChange={(e) => setForm({ ...form, au: e.target.value })} /></label>
        <div className="field field-actions"><button type="submit" className="primary" data-testid="annonce-publier">Publier</button></div>
      </form>
      {annonces.length === 0 && <p className="muted" data-testid="annonces-vide">Aucune annonce.</p>}
      {annonces.length > 0 && (
        <div className="table-wrap" tabIndex={0} role="region" aria-label="Annonces (zone défilable au clavier)">
          <table data-testid="annonces-table">
            <caption className="sr-only">Annonces de l'organisme</caption>
            <thead><tr><th scope="col">Titre</th><th scope="col">Rôles</th><th scope="col">Du</th><th scope="col">Au</th><th scope="col">Lectures</th><th scope="col">Actions</th></tr></thead>
            <tbody>
              {annonces.map((a) => (
                <tr key={a.id} data-testid={`annonce-admin-${a.id}`}>
                  <th scope="row">{a.titre}</th><td>{libelleRoles(a.roles)}</td><td>{a.du}</td><td>{a.au}</td><td>{a.lectures ?? 0}</td>
                  <td><button type="button" className="danger" data-testid={`annonce-retirer-${a.id}`} onClick={() => void retirer(a)}>Retirer</button></td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </section>
  );
}
