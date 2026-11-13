/**
 * **Actions des utilisateurs** (ADR 0041) : qui a fait quelle requête mutante, quand, avec quel
 * résultat — jamais le corps, qui n'a pas été gardé. L'administrateur lit son organisme, un compte
 * global tout. L'identifiant de requête est celui de la ligne `http` du journal serveur : de cet
 * écran à la ligne, un seul saut.
 */
import { useCallback, useEffect, useState } from "react";
import TbodyPagine from "./TbodyPagine";

type Appel = (chemin: string, init?: RequestInit) => Promise<Response>;
export interface ActionUtilisateur {
  id: string; instant: string; tenantId: string | null; userId: string; utilisateur: string; acteurReel: string | null; acteurReelNom: string | null;
  methode: string; chemin: string; statut: number; dureeMs: number; requeteId: string;
}

const PAGE = 25;

/** Un statut se lit en toutes lettres : la couleur ne porte jamais le sens à elle seule. */
export function libelleStatut(statut: number): string {
  if (statut >= 500) return `${statut} — erreur du serveur`;
  if (statut === 403) return "403 — refusée";
  if (statut === 404) return "404 — introuvable";
  if (statut === 409) return "409 — conflit";
  if (statut >= 400) return `${statut} — rejetée`;
  return `${statut} — réussie`;
}

export default function JournalActions({ apiFetch, superadmin }: { apiFetch: Appel; superadmin: boolean }) {
  const [items, setItems] = useState<ActionUtilisateur[]>([]);
  const [total, setTotal] = useState(0);
  const [page, setPage] = useState(0);
  const [filtre, setFiltre] = useState({ q: "", utilisateur: "", depuis: "", jusqua: "", organisme: "" });
  const [erreur, setErreur] = useState("");

  const charger = useCallback(async () => {
    const params = new URLSearchParams({ page: String(page), pageSize: String(PAGE) });
    for (const [k, v] of Object.entries(filtre)) if (v.trim()) params.set(k, v.trim());
    const r = await apiFetch(`/api/auth/journal-actions?${params}`);
    const j = await r.json().catch(() => ({}));
    if (!r.ok) { setErreur((j.errors ?? ["lecture refusée"]).join(" ; ")); setItems([]); setTotal(0); return; }
    setErreur(""); setItems(j.items ?? []); setTotal(j.total ?? 0);
  }, [apiFetch, page, filtre]);
  useEffect(() => { void charger(); }, [charger]);

  const champ = (cle: keyof typeof filtre, valeur: string) => { setFiltre({ ...filtre, [cle]: valeur }); setPage(0); };
  const pages = Math.max(1, Math.ceil(total / PAGE));

  return (
    <div className="card" data-testid="journal-actions" style={{ marginTop: 12 }}>
      <h3>Actions des utilisateurs <span className="muted" data-testid="actions-total">({total})</span></h3>
      <p className="section-help">
        Chaque requête qui change quelque chose (créer, modifier, supprimer), réussie ou refusée — jamais son contenu.
        L'identifiant de requête retrouve la ligne correspondante du journal serveur.
      </p>
      <div className="grid-form">
        <label className="field"><span>Filtrer</span><input type="search" value={filtre.q} onChange={(e) => champ("q", e.target.value)} placeholder="chemin, méthode, statut" data-testid="actions-filtre-q" /></label>
        <label className="field"><span>Identifiant du compte</span><input value={filtre.utilisateur} onChange={(e) => champ("utilisateur", e.target.value)} data-testid="actions-filtre-utilisateur" /></label>
        <label className="field"><span>Du</span><input type="date" value={filtre.depuis} onChange={(e) => champ("depuis", e.target.value)} data-testid="actions-filtre-depuis" /></label>
        <label className="field"><span>Au</span><input type="date" value={filtre.jusqua} onChange={(e) => champ("jusqua", e.target.value)} data-testid="actions-filtre-jusqua" /></label>
        {superadmin && <label className="field"><span>Organisme</span><input value={filtre.organisme} onChange={(e) => champ("organisme", e.target.value)} data-testid="actions-filtre-organisme" /></label>}
      </div>
      {erreur && <p role="alert" className="alert alert-error" data-testid="actions-erreur">{erreur}</p>}
      <div className="table-wrap" tabIndex={0} role="region" aria-label="Actions des utilisateurs (zone défilable au clavier)">
        <table data-testid="actions-table">
          <caption className="sr-only">Actions des utilisateurs</caption>
          <thead><tr><th scope="col">Instant</th><th scope="col">Compte</th><th scope="col">Action</th><th scope="col">Résultat</th><th scope="col">Durée</th><th scope="col">Requête</th></tr></thead>
          <TbodyPagine items={items} vide={<tr><td colSpan={6} className="muted" data-testid="actions-vide">Aucune action pour ces critères.</td></tr>} rendre={(a) => (
            <tr key={a.id} data-testid={`action-${a.id}`}>
              <td>{a.instant.slice(0, 19).replace("T", " ")}</td>
              <td>{a.utilisateur}{a.acteurReel ? <span className="muted" data-testid={`action-acteur-reel-${a.id}`}> (agissant : {a.acteurReelNom ?? a.acteurReel})</span> : null}</td>
              <td><code>{a.methode} {a.chemin}</code></td>
              <td data-testid={`action-statut-${a.id}`}>{libelleStatut(a.statut)}</td>
              <td>{a.dureeMs} ms</td>
              <td><code>{a.requeteId}</code></td>
            </tr>
          )} />
        </table>
      </div>
      {total > PAGE && (
        <div style={{ display: "flex", justifyContent: "center", alignItems: "center", gap: 8, marginTop: 8 }}>
          <button type="button" className="secondary" data-testid="actions-prev" disabled={page === 0} onClick={() => setPage((p) => Math.max(0, p - 1))} aria-label="Page précédente">‹</button>
          <span className="muted" data-testid="actions-page">Page {page + 1} / {pages}</span>
          <button type="button" className="secondary" data-testid="actions-next" disabled={page + 1 >= pages} onClick={() => setPage((p) => p + 1)} aria-label="Page suivante">›</button>
        </div>
      )}
    </div>
  );
}
