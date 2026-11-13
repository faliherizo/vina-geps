/**
 * **Onglets** — les vues d'une notion, dans l'ordre du menu. Motif ARIA « tabs » à activation
 * automatique : `role="tablist"`, un `role="tab"` par onglet avec `aria-selected` et
 * `aria-controls`, tabindex tournant (seul l'onglet actif est dans l'ordre de Tab), flèches
 * ← → Début Fin prises en charge par le délégué global (`installerNavigationOnglets`).
 *
 * Les `data-testid` sont ceux de l'appelant (`admin-tab-comptes`, `finance-tab-accounting`…) :
 * un écran migré garde ses identifiants d'essai. Le panneau porte `role="tabpanel"` et
 * `aria-labelledby` vers son onglet.
 */
import { ReactNode, useId } from "react";
import Icone, { type NomIcone } from "./Icone";

export interface Onglet<T extends string> {
  id: T;
  libelle: ReactNode;
  testId?: string;
  icone?: NomIcone;
  /** Un compteur (à traiter, en attente) affiché en badge après le libellé. */
  compteur?: number;
  /** Ce que le compteur annonce au lecteur d'écran (« 3 billets ouverts, dont 1 en retard ») ; sinon « n à traiter ». */
  compteurLibelle?: string;
  compteurTestId?: string;
  desactive?: boolean;
  /** Raison lue par le lecteur d'écran quand l'onglet est désactivé. */
  raison?: string;
}

interface Props<T extends string> {
  onglets: ReadonlyArray<Onglet<T>>;
  actif: T;
  onChange: (id: T) => void;
  /** Nom de la barre pour le lecteur d'écran (« Vues des absences »). */
  libelle: string;
  /** Onglets secondaires (dans un onglet, dans une fiche) : trait plus fin. */
  secondaires?: boolean;
  testId?: string;
  /**
   * Préfixe des identifiants (`<prefixe>-tabbtn-<id>` pour l'onglet, `<prefixe>-tabpanel-<id>` pour
   * le panneau) : un écran qui rend ses propres panneaux garde ainsi des `id` stables et prévisibles.
   */
  prefixeIds?: string;
  /** Le panneau : rendu sous la barre, lié à l'onglet actif. Sans enfants, l'appelant rend le sien. */
  children?: ReactNode;
  className?: string;
}

export default function Onglets<T extends string>({ onglets, actif, onChange, libelle, secondaires, testId, prefixeIds, children, className }: Props<T>) {
  const genere = useId();
  const prefixe = prefixeIds ?? genere;
  const idOnglet = (id: T) => `${prefixe}-tabbtn-${id}`;
  const idPanneau = (id: T) => `${prefixe}-tabpanel-${id}`;
  return (
    <>
      <div className={["tabs", secondaires ? "tabs-secondaires" : "", className].filter(Boolean).join(" ")} role="tablist" aria-label={libelle} data-testid={testId}>
        {onglets.map((o, i) => {
          const selectionne = o.id === actif;
          // Une barre dont aucun onglet n'est actif (plusieurs barres pour une même sélection, comme les
          // familles du dossier d'employé) reste atteignable par Tab : son premier onglet entre dans l'ordre.
          const atteignable = selectionne || (i === 0 && !onglets.some((x) => x.id === actif));
          return (
            /* `aria-controls` ne vise qu'un panneau présent dans le document : celui que ce composant rend,
               ou celui que l'hôte promet par `prefixeIds` — et seulement pour l'onglet actif, un onglet
               inactif n'ayant pas de panneau. Une référence vers un id absent est une valeur ARIA invalide
               (axe `aria-valid-attr-value`, WCAG 4.1.2). */
            <button key={o.id} type="button" role="tab" id={idOnglet(o.id)} aria-selected={selectionne}
                    aria-controls={selectionne && (children !== undefined || prefixeIds) ? idPanneau(o.id) : undefined}
                    tabIndex={atteignable ? 0 : -1} data-testid={o.testId} disabled={o.desactive} aria-disabled={o.desactive || undefined}
                    title={o.desactive ? o.raison : undefined} onClick={() => onChange(o.id)}>
              {o.icone && <Icone nom={o.icone} />}
              <span>{o.libelle}</span>
              {typeof o.compteur === "number" && o.compteur > 0 && (
                <span className="onglet-compteur" aria-label={o.compteurLibelle ?? `${o.compteur} à traiter`} data-testid={o.compteurTestId}>{o.compteur}</span>
              )}
              {o.desactive && o.raison && <span className="sr-only"> — {o.raison}</span>}
            </button>
          );
        })}
      </div>
      {children !== undefined && (
        <div role="tabpanel" id={idPanneau(actif)} aria-labelledby={idOnglet(actif)} tabIndex={0} className="tabpanel">
          {children}
        </div>
      )}
    </>
  );
}
