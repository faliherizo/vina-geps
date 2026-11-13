/**
 * **Pagination réutilisable** pour tous les affichages de tableau.
 *
 * `usePagination(items, pageSize)` découpe une liste en pages côté client et expose la page
 * courante + les contrôles. `<Pagination>` rend la barre de navigation (Précédent / n° de page /
 * Suivant + compteur). Idempotent : si la liste rétrécit, la page courante est bornée.
 */
import { useEffect, useMemo, useState } from "react";

export function usePagination<T>(items: T[], pageSize = 10) {
  const [page, setPage] = useState(1);
  const total = items.length;
  const pageCount = Math.max(1, Math.ceil(total / pageSize));

  // Borne la page si la liste change (filtre, rechargement…).
  useEffect(() => { if (page > pageCount) setPage(pageCount); }, [pageCount, page]);

  const pageItems = useMemo(() => items.slice((page - 1) * pageSize, page * pageSize), [items, page, pageSize]);
  return { page, setPage, pageCount, total, pageSize, pageItems };
}

export default function Pagination({
  page, pageCount, total, pageSize, onPage, testId = "pager",
}: {
  page: number; pageCount: number; total: number; pageSize: number;
  onPage: (p: number) => void; testId?: string;
}) {
  if (total <= pageSize) return null; // rien à paginer
  const from = (page - 1) * pageSize + 1;
  const to = Math.min(page * pageSize, total);
  return (
    <div className="pager" data-testid={testId} role="navigation" aria-label="Pagination"
      style={{ display: "flex", alignItems: "center", gap: 8, marginTop: 8, flexWrap: "wrap" }}>
      <button type="button" className="secondary" data-testid={`${testId}-prev`}
        disabled={page <= 1} onClick={() => onPage(page - 1)}>‹ Précédent</button>
      <span className="muted" data-testid={`${testId}-status`} style={{ fontSize: 13 }}>
        {from}–{to} sur {total} · page {page}/{pageCount}
      </span>
      <button type="button" className="secondary" data-testid={`${testId}-next`}
        disabled={page >= pageCount} onClick={() => onPage(page + 1)}>Suivant ›</button>
    </div>
  );
}
