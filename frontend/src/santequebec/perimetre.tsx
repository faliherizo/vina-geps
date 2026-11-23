/**
 * **Le périmètre de travail de la communication** : le groupe (l'unité) dont on lit les membres.
 *
 * Les groupes viennent de `GET /api/communication/groupes` ; le choix survit à la navigation dans
 * l'onglet, pas d'un jour à l'autre. Hors fournisseur, un périmètre vide et inerte est rendu : une
 * page montée seule dans un essai doit s'afficher, pas lever.
 */
import { createContext, useCallback, useContext, useEffect, useMemo, useState, type ReactNode } from "react";
import { useAuth } from "../AuthContext";
import { useCurrentTenant } from "../organismeCourant";

export interface GroupeSq { id: string; code: string; nom: string; actif?: number }

export interface Perimetre {
  groupes: GroupeSq[];
  groupeId: string;
  groupe: GroupeSq | null;
  choisirGroupe: (id: string) => void;
  /** Vrai tant que les groupes n'ont pas répondu : une page ne doit pas crier « aucun groupe » trop tôt. */
  chargement: boolean;
  erreur: string;
}

const Contexte = createContext<Perimetre | null>(null);

/** Émis quand les groupes changent ailleurs : le fournisseur les relit. */
export const EVENEMENT_PERIMETRE = "sq:perimetre";

const CLE_GROUPE = "sq.groupe";

const retenu = (cle: string): string => {
  try { return sessionStorage.getItem(cle) ?? ""; } catch { return ""; }
};
const retenir = (cle: string, valeur: string) => {
  try { if (valeur) sessionStorage.setItem(cle, valeur); else sessionStorage.removeItem(cle); } catch { /* navigation privée */ }
};

/**
 * **Le groupe ouvert d'office** : celui qu'on avait choisi dans la session, s'il existe encore ; sinon **le sien** ;
 * sinon le premier. Le premier de la liste seul faisait ouvrir la messagerie d'une infirmière de Saint-Jérôme sur
 * Argenteuil, « aucun membre dans ce groupe » — constaté sur la démonstration FX-AO le 23 novembre 2026.
 */
export function groupeParDefaut(groupes: GroupeSq[], miens: string[], retenuId: string): string {
  if (groupes.some((x) => x.id === retenuId)) return retenuId;
  return groupes.find((x) => miens.includes(x.id))?.id ?? groupes[0]?.id ?? "";
}

export function FournisseurPerimetre({ children }: { children: ReactNode }) {
  const { apiFetch } = useAuth();
  const { tenantId } = useCurrentTenant();
  const [groupes, setGroupes] = useState<GroupeSq[]>([]);
  const [groupeId, setGroupeId] = useState(() => retenu(CLE_GROUPE));
  const [chargement, setChargement] = useState(true);
  const [erreur, setErreur] = useState("");

  const lire = useCallback(async (chemin: string) => {
    const headers = new Headers();
    if (tenantId) headers.set("X-Tenant-Id", tenantId);
    const r = await apiFetch(chemin, { headers });
    if (!r.ok) {
      const j = await r.json().catch(() => ({}));
      throw new Error(Array.isArray(j?.errors) ? j.errors.join(" · ") : `Erreur ${r.status}`);
    }
    return r.json().catch(() => null);
  }, [apiFetch, tenantId]);

  const [jeton, setJeton] = useState(0);
  useEffect(() => {
    const relire = () => setJeton((j) => j + 1);
    window.addEventListener(EVENEMENT_PERIMETRE, relire);
    return () => window.removeEventListener(EVENEMENT_PERIMETRE, relire);
  }, []);

  useEffect(() => {
    let vivant = true;
    setChargement(true);
    // Les groupes de la personne sont un confort : leur échec ne doit pas empêcher la liste de s'afficher.
    Promise.all([lire("/api/communication/groupes"), lire("/api/communication/groupes/miens").catch(() => [])])
      .then(([g, m]) => {
        if (!vivant) return;
        const liste: GroupeSq[] = Array.isArray(g) ? g : [];
        const miens: string[] = Array.isArray(m) ? m : [];
        setGroupes(liste);
        setErreur("");
        setGroupeId((actuel) => groupeParDefaut(liste, miens, actuel));
      })
      .catch((e: Error) => { if (vivant) { setGroupes([]); setErreur(e.message); } })
      .finally(() => { if (vivant) setChargement(false); });
    return () => { vivant = false; };
  }, [lire, jeton]);

  useEffect(() => { retenir(CLE_GROUPE, groupeId); }, [groupeId]);

  const valeur = useMemo<Perimetre>(() => ({
    groupes, groupeId,
    groupe: groupes.find((g) => g.id === groupeId) ?? null,
    choisirGroupe: setGroupeId,
    chargement, erreur,
  }), [groupes, groupeId, chargement, erreur]);

  return <Contexte.Provider value={valeur}>{children}</Contexte.Provider>;
}

export function useePerimetre(): Perimetre {
  const c = useContext(Contexte);
  return c ?? { groupes: [], groupeId: "", groupe: null, choisirGroupe: () => {}, chargement: false, erreur: "" };
}

/**
 * La barre de périmètre : un `<form>` de filtres dont chaque contrôle porte son libellé, et qui dit
 * pourquoi il est vide plutôt que de laisser une liste muette.
 */
export function BarrePerimetre({ besoins }: { besoins: ReadonlyArray<"groupe"> }) {
  const p = useePerimetre();
  return (
    <section className="sq-panneau" aria-labelledby="sq-perimetre-titre" data-testid="sq-perimetre">
      <h2 id="sq-perimetre-titre" className="sr-only">Périmètre de travail</h2>
      {besoins.includes("groupe") && (
        <div className="sq-rangee">
          <div className="sq-champ">
            <label htmlFor="sq-perimetre-groupe">Groupe ou unité</label>
            <select id="sq-perimetre-groupe" data-testid="sq-perimetre-groupe" value={p.groupeId}
                    onChange={(e) => p.choisirGroupe(e.target.value)}>
              <option value="">— Choisir —</option>
              {p.groupes.map((g) => <option key={g.id} value={g.id}>{g.nom} ({g.code})</option>)}
            </select>
          </div>
        </div>
      )}
      {p.erreur && (
        <p className="sq-erreur" role="alert" data-testid="sq-perimetre-erreur">
          <strong>Les groupes n'ont pas pu être lus :</strong> {p.erreur}
        </p>
      )}
      {!p.chargement && !p.erreur && p.groupes.length === 0 && (
        <p className="sq-vide" data-testid="sq-perimetre-vide">
          Aucune unité n'est en vigueur. Les groupes de travail sont les unités déclarées dans les
          référentiels des effectifs.
        </p>
      )}
    </section>
  );
}
