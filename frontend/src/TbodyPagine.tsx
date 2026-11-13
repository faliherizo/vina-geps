/**
 * **Corps de tableau paginé** — dix lignes par page, partout où une liste peut dépasser dix
 * (demande du 13 sept. 2026). Le composant rend le `<tbody>` **et** la barre de pagination dans
 * un `<tfoot>` : la modification reste locale au tableau, et la pagination disparaît d'elle-même
 * quand la liste tient sur une page (`<Pagination>` ne rend rien sous le seuil).
 *
 * Les lignes gardent leur `key` et leur `data-testid` : ce qui change, c'est seulement combien
 * de lignes sont rendues à la fois.
 */
import { ReactNode } from "react";
import Pagination, { usePagination } from "./Pagination";

interface Props<T> {
  items: T[];
  rendre: (item: T, index: number) => ReactNode;
  taille?: number;
  testId?: string;
  /** Nombre de colonnes du tableau, pour la cellule du pied qui porte la pagination. */
  colonnes?: number;
  /** Ce qui est rendu dans le corps quand la liste est vide (une ligne « aucun élément », le plus souvent). */
  vide?: ReactNode;
  /** Le `data-testid` que portait le `<tbody>` d'origine : les essais le retrouvent tel quel. */
  tbodyTestId?: string;
}

export default function TbodyPagine<T>({ items, rendre, taille = 10, testId = "pager", colonnes = 99, vide, tbodyTestId }: Props<T>) {
  const pager = usePagination(items, taille);
  const decalage = (pager.page - 1) * pager.pageSize;
  return (
    <>
      <tbody data-testid={tbodyTestId}>
        {items.length === 0 && vide}
        {pager.pageItems.map((item, i) => rendre(item, decalage + i))}
      </tbody>
      {pager.total > pager.pageSize && (
        <tfoot>
          <tr>
            <td colSpan={colonnes}>
              <Pagination page={pager.page} pageCount={pager.pageCount} total={pager.total} pageSize={pager.pageSize} onPage={pager.setPage} testId={testId} />
            </td>
          </tr>
        </tfoot>
      )}
    </>
  );
}
