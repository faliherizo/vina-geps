/**
 * **Périmètres des utilisateurs** (AO 26-015-DO, écran E4 — E-GN-7, E-GN-8).
 *
 * Refus par défaut : un gestionnaire sans périmètre ne voit rien. L'écran dit donc, pour la
 * personne choisie, ce qu'elle **verra réellement** — un périmètre se vérifie avant de s'enregistrer,
 * pas au premier appel au soutien.
 */
import { useCallback, useEffect, useState } from "react";
import { Badge, EtatVide, Message, Tableau, type Colonne } from "../composants";
import { aujourdhui, lisible, useApiEffectifs } from "./api";
import type { Valeur } from "./Referentiels";
import { FormulaireModal } from "../Modal";

interface Compte { id: string; username: string; role: string }
interface Perimetre { id: string; utilisateurId: string; uniteCode: string; etendue: string; niveau: string; depuis: string; jusqua: string | null }

const ETENDUES = ["SOUS_ARBRE", "UNITE"] as const;
const NIVEAUX = ["LECTURE", "PLANIFICATION", "GESTION"] as const;

export default function Perimetres() {
  const { lire, envoyer, erreur, succes, setSucces } = useApiEffectifs();
  const [comptes, setComptes] = useState<Compte[]>([]);
  const [unites, setUnites] = useState<Valeur[]>([]);
  const [utilisateurId, setUtilisateurId] = useState("");
  const [perimetres, setPerimetres] = useState<Perimetre[]>([]);
  const [uniteCode, setUniteCode] = useState("");
  const [etendue, setEtendue] = useState<string>("SOUS_ARBRE");
  const [niveau, setNiveau] = useState<string>("LECTURE");
  const [depuis, setDepuis] = useState(aujourdhui());

  useEffect(() => {
    void Promise.all([lire<Compte[]>("/api/auth/users"), lire<Valeur[]>("/api/effectifs/referentiels/unites")]).then(([c, u]) => {
      if (c) setComptes(c.filter((x) => x.role !== "admin" && x.role !== "superadmin"));
      if (u) setUnites(u);
    });
  }, [lire]);

  const charger = useCallback(async () => {
    if (!utilisateurId) { setPerimetres([]); return; }
    const p = await lire<Perimetre[]>(`/api/effectifs/perimetres?utilisateurId=${encodeURIComponent(utilisateurId)}`);
    if (p) setPerimetres(p);
  }, [lire, utilisateurId]);

  useEffect(() => { void charger(); }, [charger]);

  const colonnes: ReadonlyArray<Colonne<Perimetre>> = [
    { id: "unite", libelle: "Unité", tri: (p) => p.uniteCode },
    { id: "etendue", libelle: "Étendue" },
    { id: "niveau", libelle: "Niveau", tri: (p) => p.niveau },
    { id: "periode", libelle: "Période", tri: (p) => p.depuis },
    { id: "actions", libelle: "Actions", actions: true },
  ];

  return (
    <div data-testid="per-ecran">
      <p className="aide">Les administrateurs de l'établissement voient toutes les unités. Les autres comptes ne voient que les unités de leur périmètre — aucune par défaut.</p>
      <div className="eff-barre">
        <p>
          <label htmlFor="per-utilisateur">Compte</label>
          <select id="per-utilisateur" data-testid="per-utilisateur" value={utilisateurId} onChange={(e) => { setUtilisateurId(e.target.value); setSucces(""); }}>
            <option value="">Choisir un compte…</option>
            {comptes.map((c) => <option key={c.id} value={c.id}>{c.username} ({c.role})</option>)}
          </select>
        </p>
      </div>
      {erreur && <Message variante="erreur" testId="per-erreur">{erreur}</Message>}
      {succes && <Message variante="succes" testId="per-succes">{succes}</Message>}

      {utilisateurId && (
        <>
          <Tableau
            colonnes={colonnes} lignes={perimetres} legende="Périmètres du compte choisi" testId="per-tableau"
            vide={<EtatVide titre="Aucun périmètre" compact testId="per-vide">Ce compte ne voit aucune unité.</EtatVide>}
            rendre={(p) => (
              <tr key={p.id} data-testid={`per-ligne-${p.uniteCode}`}>
                <td>{p.uniteCode}</td>
                <td>{p.etendue === "SOUS_ARBRE" ? "L'unité et ses sous-unités" : "L'unité seule"}</td>
                <td>{lisible(p.niveau)}</td>
                <td>depuis le {p.depuis}{p.jusqua ? `, jusqu'au ${p.jusqua} (exclu)` : ""}</td>
                <td className="col-menu">
                  {p.jusqua ? <Badge ton="neutre">Fermé</Badge> : (
                    <button type="button" className="secondary" data-testid={`per-fermer-${p.uniteCode}`} onClick={async () => {
                      const r = await envoyer(`/api/effectifs/perimetres/${p.id}/fermer`, {});
                      if (r) { setSucces(`Accès à ${p.uniteCode} retiré à partir d'aujourd'hui.`); await charger(); }
                    }}>Retirer<span className="sr-only"> l'unité {p.uniteCode}</span></button>
                  )}
                </td>
              </tr>
            )}
          />
          {/* Ajouter un accès **crée** un périmètre : il vit dans un dialogue, sous la liste des accès
              qu'il complète. Posé en ligne sous le tableau, il se lisait comme un filtre de la liste. */}
          <div className="eff-actions">
          <FormulaireModal libelle="Ajouter un accès" titre={`Ajouter un accès — ${comptes.find((c) => c.id === utilisateurId)?.username ?? "compte"}`} testId="per-ajout" size="md">
            {(fermer) => (
          <form onSubmit={async (e) => {
            e.preventDefault();
            const r = await envoyer("/api/effectifs/perimetres", { utilisateurId, uniteCode, etendue, niveau, depuis });
            if (r) { fermer(); setSucces(`Accès à ${uniteCode} ajouté (${lisible(niveau)}).`); setUniteCode(""); await charger(); }
          }}>
            <p>
              <label htmlFor="per-unite">Unité</label>
              <select id="per-unite" data-testid="per-unite" required value={uniteCode} onChange={(e) => setUniteCode(e.target.value)}>
                <option value="">Choisir…</option>
                {unites.map((u) => <option key={u.code} value={u.code}>{u.code} — {u.libelleFr}</option>)}
              </select>
            </p>
            <p>
              <label htmlFor="per-etendue">Étendue</label>
              <select id="per-etendue" data-testid="per-etendue" value={etendue} onChange={(e) => setEtendue(e.target.value)}>
                {ETENDUES.map((x) => <option key={x} value={x}>{x === "SOUS_ARBRE" ? "L'unité et ses sous-unités" : "L'unité seule"}</option>)}
              </select>
            </p>
            <p>
              <label htmlFor="per-niveau">Niveau</label>
              <select id="per-niveau" data-testid="per-niveau" value={niveau} onChange={(e) => setNiveau(e.target.value)}>
                {NIVEAUX.map((x) => <option key={x} value={x}>{lisible(x)}</option>)}
              </select>
            </p>
            <p>
              <label htmlFor="per-depuis">À partir du</label>
              <input id="per-depuis" type="date" data-testid="per-depuis" value={depuis} onChange={(e) => setDepuis(e.target.value)} />
            </p>
            {erreur && <Message variante="erreur" testId="per-a-erreur">{erreur}</Message>}
            <button type="submit" data-testid="per-ajouter">Ajouter l'accès</button>
          </form>
            )}
          </FormulaireModal>
          </div>
        </>
      )}
    </div>
  );
}
