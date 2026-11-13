/**
 * **Emprunt d'identité** (CSSMB 25 ; ADR 0038) — le panneau de l'administration : le réglage de
 * l'organisme (fermé par défaut), le geste « agir comme » (un compte inférieur, un motif écrit au
 * journal, une heure), et le journal des emprunts. Le bandeau de la session empruntée vit dans
 * la coquille de l'application ({@link EmpruntBandeau}).
 */
import { useCallback, useEffect, useState } from "react";
import { Message } from "../composants";
import { useAuth } from "../AuthContext";
import { naviguerVers } from "../navigation";

type Appel = (chemin: string, init?: RequestInit) => Promise<Response>;
interface Compte { id: string; username: string; role: string; active: boolean }
interface Reglage { empruntIdentite: boolean; modifieLe: string | null; modifiePar: string | null }
interface Ligne { id: string; emprunteur: string; cible: string; motif: string; debuteLe: string; expireLe: string; termineLe: string | null; actif: boolean }

const NON_EMPRUNTABLES = ["admin", "superadmin"];
/** « 10 h 42 » — l'heure locale, en toutes lettres, sans dépendre d'une icône. */
export const heureCourte = (iso: string) => new Date(iso).toLocaleTimeString("fr-CA", { hour: "numeric", minute: "2-digit" }).replace(":", " h ");

export default function EmpruntIdentitePanel({ apiFetch, comptes }: { apiFetch: Appel; comptes: Compte[] }) {
  const { user, refreshUser } = useAuth();
  const [reglage, setReglage] = useState<Reglage | null>(null);
  const [journal, setJournal] = useState<Ligne[]>([]);
  const [cible, setCible] = useState("");
  const [motif, setMotif] = useState("");
  const [erreur, setErreur] = useState("");
  const [info, setInfo] = useState("");

  const charger = useCallback(async () => {
    const [r, j] = await Promise.all([apiFetch("/api/auth/emprunt/reglage"), apiFetch("/api/auth/emprunt/journal")]);
    setReglage(r.ok ? await r.json().catch(() => null) : null);
    const lignes = j.ok ? await j.json().catch(() => []) : [];
    setJournal(Array.isArray(lignes) ? lignes : []);
  }, [apiFetch]);
  useEffect(() => { void charger(); }, [charger]);

  const lire = async (r: Response) => { const d = await r.json().catch(() => ({})); if (!r.ok) { setErreur((d.errors ?? [`refus du serveur (${r.status})`]).join(" ; ")); return null; } return d; };

  const regler = async (actif: boolean) => {
    setErreur(""); setInfo("");
    const d = await lire(await apiFetch("/api/auth/emprunt/reglage", { method: "PUT", body: JSON.stringify({ actif }) }));
    if (d) { setReglage(d); setInfo(actif ? "Emprunt d'identité ouvert pour l'organisme — consigné au journal d'audit." : "Emprunt d'identité fermé."); }
  };
  const emprunter = async () => {
    setErreur(""); setInfo("");
    const d = await lire(await apiFetch("/api/auth/emprunt", { method: "POST", body: JSON.stringify({ cibleId: cible, motif }) }));
    if (!d) return;
    // La session est désormais celle de la cible : on relit le profil et on repart de l'accueil, comme elle.
    await refreshUser();
    naviguerVers("dashboard");
  };

  const empruntables = comptes.filter((c) => c.active && c.id !== user?.id && !NON_EMPRUNTABLES.includes(c.role));
  return (
    <section className="card" aria-labelledby="emprunt-titre" data-testid="emprunt-panneau">
      <h3 id="emprunt-titre">Emprunt d'identité</h3>
      <p className="section-help">
        L'administration peut <strong>agir comme</strong> un compte de l'organisme pendant une heure — pour reproduire un problème ou terminer une saisie.
        Chaque geste est journalisé sous <strong>les deux identités</strong> ; le mot de passe, les facteurs et les questions de la personne restent hors de portée ;
        un compte d'administration ne s'emprunte jamais. Fermé par défaut.
      </p>
      {erreur && <Message variante="erreur" testId="emprunt-erreur">{erreur}</Message>}
      {info && <Message variante="succes" testId="emprunt-info" role="status">{info}</Message>}
      <label className="field field-inline">
        <input type="checkbox" data-testid="emprunt-reglage" checked={!!reglage?.empruntIdentite} onChange={(e) => void regler(e.target.checked)} />
        <span>Autoriser l'emprunt d'identité par l'administration{reglage?.modifieLe ? ` (réglé le ${reglage.modifieLe.slice(0, 10)})` : ""}</span>
      </label>
      {reglage?.empruntIdentite && (
        <form className="grid-form" onSubmit={(e) => { e.preventDefault(); void emprunter(); }} aria-label="Agir comme un compte">
          <label className="field"><span>Compte</span>
            <select data-testid="emprunt-cible" value={cible} onChange={(e) => setCible(e.target.value)} required>
              <option value="">— choisir —</option>
              {empruntables.map((c) => <option key={c.id} value={c.id}>{c.username} ({c.role})</option>)}
            </select></label>
          <label className="field field-full"><span>Motif (écrit au journal d'audit)</span>
            <input data-testid="emprunt-motif" value={motif} onChange={(e) => setMotif(e.target.value)} minLength={5} required aria-describedby="emprunt-motif-aide" />
            <span className="aide" id="emprunt-motif-aide">Cinq caractères au moins : ce que vous allez faire, et pourquoi.</span></label>
          <div className="field field-actions"><button type="submit" className="primary" data-testid="emprunt-confirmer" disabled={!cible || motif.trim().length < 5}>Agir comme ce compte (une heure)</button></div>
        </form>
      )}
      {journal.length > 0 && (
        <div className="table-wrap" tabIndex={0} role="region" aria-label="Journal des emprunts (zone défilable au clavier)">
          <table data-testid="emprunt-journal">
            <caption className="sr-only">Emprunts d'identité récents</caption>
            <thead><tr><th scope="col">Emprunteur</th><th scope="col">Comme</th><th scope="col">Motif</th><th scope="col">Début</th><th scope="col">Fin</th><th scope="col">État</th></tr></thead>
            <tbody>
              {journal.map((l) => (
                <tr key={l.id} data-testid={`emprunt-${l.id}`}>
                  <th scope="row">{l.emprunteur}</th><td>{l.cible}</td><td>{l.motif}</td><td>{l.debuteLe.slice(0, 16).replace("T", " ")}</td>
                  <td>{(l.termineLe ?? l.expireLe).slice(0, 16).replace("T", " ")}</td><td>{l.actif ? "En cours" : l.termineLe ? "Terminé" : "Expiré"}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </section>
  );
}

/** Le bandeau d'une session empruntée : qui agit, comme qui, jusqu'à quand — et « Terminer ». Il pousse la page, ne la recouvre pas. */
export function EmpruntBandeau() {
  const { user, apiFetch, refreshUser } = useAuth();
  const [erreur, setErreur] = useState("");
  if (!user?.emprunt) return null;
  const terminer = async () => {
    setErreur("");
    const r = await apiFetch("/api/auth/emprunt/terminer", { method: "POST", body: "{}" });
    if (!r.ok) { const d = await r.json().catch(() => ({})); setErreur((d.errors ?? ["refus du serveur"]).join(" ; ")); return; }
    await refreshUser();
    naviguerVers("admin");
  };
  return (
    <div role="status" data-testid="emprunt-bandeau" className="emprunt-bandeau">
      <span>
        <strong>{user.emprunt.emprunteur.username}</strong>, vous agissez comme <strong>{user.username}</strong> ({user.role}) — jusqu'à {heureCourte(user.emprunt.expireLe)}.
        {erreur ? ` ${erreur}` : ""}
      </span>
      <button type="button" className="secondary" data-testid="emprunt-terminer" onClick={() => void terminer()}>Terminer l'emprunt</button>
    </div>
  );
}
