/**
 * **Barre de filtres** — en tête de tout tableau : des champs étiquetés, le nombre de
 * résultats annoncé (`role="status"`), et « Réinitialiser » quand un filtre est posé. Le
 * `<label>` de chaque champ est fourni par l'appelant ; ce composant n'apporte que la mise en
 * page et l'annonce du décompte (WCAG 4.1.3).
 */
import { ReactNode } from "react";
import Icone from "./Icone";

interface Props {
  children: ReactNode;
  /** Le nombre de lignes après filtrage ; annoncé comme « 12 résultats ». */
  total?: number;
  /** Le nombre avant filtrage, pour dire « 12 résultats sur 42 ». */
  totalSansFiltre?: number;
  /** Rendu quand au moins un filtre est actif : le bouton « Réinitialiser » l'appelle. */
  onReinitialiser?: () => void;
  filtresActifs?: boolean;
  /** Ce qui se place à droite : « Vue enregistrée ▾ », export. */
  aDroite?: ReactNode;
  testId?: string;
  libelle?: string;
}

export function libelleResultats(total: number, sansFiltre?: number): string {
  const base = total === 1 ? "1 résultat" : `${total} résultats`;
  return sansFiltre !== undefined && sansFiltre !== total ? `${base} sur ${sansFiltre}` : base;
}

export default function BarreFiltres({ children, total, totalSansFiltre, onReinitialiser, filtresActifs, aDroite, testId, libelle = "Filtres" }: Props) {
  return (
    <div className="barre-filtres" role="group" aria-label={libelle} data-testid={testId}>
      <span className="barre-filtres-icone"><Icone nom="filtre" /></span>
      <div className="barre-filtres-champs">{children}</div>
      <div className="barre-filtres-fin">
        {typeof total === "number" && (
          <span className="barre-filtres-total" role="status" data-testid={testId ? `${testId}-total` : undefined}>{libelleResultats(total, totalSansFiltre)}</span>
        )}
        {onReinitialiser && filtresActifs && (
          <button type="button" className="lien" onClick={onReinitialiser} data-testid={testId ? `${testId}-reinitialiser` : undefined}>
            <Icone nom="fermer" /> Réinitialiser
          </button>
        )}
        {aDroite}
      </div>
    </div>
  );
}
