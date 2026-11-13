/**
 * **Tableau** — en-tête `scope="col"`, `caption` (visible ou lecteur d'écran seulement), tri par
 * colonne annoncé (`aria-sort`), pagination à dix par `TbodyPagine`, état vide en une cellule.
 * Les lignes restent celles de l'appelant (`rendre`), avec leurs `key` et `data-testid`.
 *
 * La ligne peut être cliquable (`onOuvrir`), mais **jamais comme seul geste** (WCAG 2.5.7) :
 * l'appelant garde un bouton « Ouvrir » ou un `MenuActions` dans une colonne.
 */
import { isValidElement, ReactNode, useMemo, useState } from "react";
import TbodyPagine from "../TbodyPagine";
import Icone from "./Icone";

export interface Colonne<T> {
  id: string;
  libelle: ReactNode;
  /** Valeur de tri ; sans elle, la colonne ne se trie pas. */
  tri?: (ligne: T) => string | number | null | undefined;
  /** Chiffres : alignés à droite, en chiffres tabulaires. */
  numerique?: boolean;
  /** Colonne d'actions : étroite, à droite, sans tri. */
  actions?: boolean;
  className?: string;
}

interface Props<T> {
  colonnes: ReadonlyArray<Colonne<T>>;
  lignes: ReadonlyArray<T>;
  /** Rend une ligne complète (`<tr>…</tr>`), comme aujourd'hui avec `TbodyPagine`. */
  rendre: (ligne: T, index: number) => ReactNode;
  /** Résumé du tableau pour le lecteur d'écran ; visible si `legendeVisible`. */
  legende: string;
  legendeVisible?: boolean;
  vide?: ReactNode;
  taille?: number;
  testId?: string;
  /** `data-testid` du `<tbody>`, conservé pour les essais. */
  tbodyTestId?: string;
  /** Tri initial. */
  triInitial?: { colonne: string; sens: "asc" | "desc" };
  className?: string;
}

/** Une ligne de tableau : le seul enfant qu'un `<tbody>` accepte. */
const estLigne = (n: ReactNode): boolean => isValidElement(n) && n.type === "tr";

/**
 * L'état vide, **toujours rendu comme une ligne du tableau**.
 *
 * Deux formes arrivaient ici et produisaient toutes deux du HTML invalide : une **phrase** (nœud
 * de texte nu dans le `<tbody>`) et un **`<EtatVide>`** (un `<div>`, la forme la plus répandue —
 * quatre-vingt-onze appels). Le navigateur remonte l'un comme l'autre *hors* du tableau, où le
 * message s'affiche au-dessus de l'en-tête, orphelin de la liste qu'il décrit.
 *
 * Tout ce qui n'est pas un `<tr>` est donc enveloppé. Envelopper ici plutôt qu'exiger un `<tr>`
 * de chaque appelant évite de répéter le `colSpan` à quatre-vingt-onze endroits, et de l'oublier
 * au quatre-vingt-douzième.
 */
function enLigneVide(vide: ReactNode, nbColonnes: number): ReactNode {
  const contenu = vide ?? "Aucun élément.";
  if (estLigne(contenu)) return contenu;
  // Une liste de lignes passe telle quelle ; dès qu'un élément n'en est pas une, on enveloppe.
  if (Array.isArray(contenu) && contenu.length > 0 && contenu.every(estLigne)) return contenu;
  return <tr><td colSpan={nbColonnes} className="empty-cell">{contenu}</td></tr>;
}

function comparer(a: unknown, b: unknown): number {
  if (a == null && b == null) return 0;
  if (a == null) return 1;
  if (b == null) return -1;
  if (typeof a === "number" && typeof b === "number") return a - b;
  return String(a).localeCompare(String(b), "fr", { numeric: true, sensitivity: "base" });
}

export default function Tableau<T>({ colonnes, lignes, rendre, legende, legendeVisible, vide, taille = 10, testId, tbodyTestId, triInitial, className }: Props<T>) {
  const [tri, setTri] = useState<{ colonne: string; sens: "asc" | "desc" } | null>(triInitial ?? null);
  const triees = useMemo(() => {
    if (!tri) return lignes;
    const col = colonnes.find((c) => c.id === tri.colonne);
    if (!col?.tri) return lignes;
    const cle = col.tri;
    const copie = [...lignes].sort((a, b) => comparer(cle(a), cle(b)));
    return tri.sens === "asc" ? copie : copie.reverse();
  }, [lignes, tri, colonnes]);

  const basculer = (id: string) => setTri((t) => (t?.colonne === id ? { colonne: id, sens: t.sens === "asc" ? "desc" : "asc" } : { colonne: id, sens: "asc" }));

  return (
    <div className={["table-wrap", className].filter(Boolean).join(" ")} tabIndex={0} role="region" aria-label={`${legende} (zone défilable au clavier)`}>
      <table data-testid={testId} className="tableau">
        <caption className={legendeVisible ? undefined : "sr-only"}>{legende}</caption>
        <thead>
          <tr>
            {colonnes.map((c) => {
              const actif = tri?.colonne === c.id;
              const sort = c.tri ? (actif ? (tri!.sens === "asc" ? "ascending" : "descending") : "none") : undefined;
              return (
                <th key={c.id} scope="col" aria-sort={sort} className={[c.numerique ? "col-numerique" : "", c.actions ? "col-menu" : "", c.className].filter(Boolean).join(" ") || undefined}>
                  {c.tri ? (
                    <button type="button" className="tri" onClick={() => basculer(c.id)} data-testid={testId ? `${testId}-tri-${c.id}` : undefined}>
                      {c.libelle}
                      <span className="tri-icone"><Icone nom={actif ? (tri!.sens === "asc" ? "chevron-bas" : "chevron-droite") : "chevron-bas"} taille={14} /></span>
                      <span className="sr-only">{actif ? (tri!.sens === "asc" ? ", tri croissant" : ", tri décroissant") : ", trier"}</span>
                    </button>
                  ) : c.libelle}
                </th>
              );
            })}
          </tr>
        </thead>
        <TbodyPagine items={triees as T[]} rendre={rendre} taille={taille} colonnes={colonnes.length} testId={testId ? `${testId}-pager` : undefined} tbodyTestId={tbodyTestId}
                     vide={enLigneVide(vide, colonnes.length)} />
      </table>
    </div>
  );
}
