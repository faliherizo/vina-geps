/**
 * **Fil d'Ariane** — « Section › Notion › Vue », toujours affiché sous la barre du haut : c'est
 * le menu lui-même, à plat. Une page ne peut plus « être ailleurs » que là où le menu la range.
 * Chaque maillon qui mène quelque part est un bouton ; le dernier est la position courante
 * (`aria-current="page"`). Balisage WAI-ARIA « breadcrumb » : `nav` étiqueté + liste ordonnée.
 */
import Icone from "./Icone";

export interface Maillon {
  libelle: string;
  /** Sans `onClick`, le maillon est un simple repère (une section qui ne s'ouvre pas seule). */
  onClick?: () => void;
  testId?: string;
}

interface Props {
  maillons: ReadonlyArray<Maillon>;
  testId?: string;
}

export default function FilAriane({ maillons, testId = "fil-ariane" }: Props) {
  if (maillons.length === 0) return null;
  return (
    <nav aria-label="Fil d'Ariane" className="fil-ariane" data-testid={testId}>
      <ol>
        {maillons.map((m, i) => {
          const dernier = i === maillons.length - 1;
          return (
            <li key={`${i}-${m.libelle}`}>
              {i > 0 && <span className="fil-ariane-sep" aria-hidden="true"><Icone nom="chevron-droite" taille={14} /></span>}
              {dernier
                ? <span aria-current="page" className="fil-ariane-courant" data-testid={m.testId}>{m.libelle}</span>
                : m.onClick
                  ? <button type="button" className="fil-ariane-lien" onClick={m.onClick} data-testid={m.testId}>{m.libelle}</button>
                  : <span className="fil-ariane-repere" data-testid={m.testId}>{m.libelle}</span>}
            </li>
          );
        })}
      </ol>
    </nav>
  );
}
