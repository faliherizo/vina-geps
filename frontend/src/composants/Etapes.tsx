/**
 * **Barre d'étapes** d'un assistant (gabarit G3) : une liste ordonnée, l'étape courante en
 * `aria-current="step"`, les étapes faites cochées **et** dites « terminée ». Les étapes déjà
 * faites sont des boutons quand l'appelant permet d'y revenir. Modèle : `ImportScolago`.
 */
export interface Etape {
  id: string;
  libelle: string;
  testId?: string;
}

interface Props {
  etapes: ReadonlyArray<Etape>;
  courante: string;
  /** Revenir à une étape faite ; sans elle, les étapes ne sont pas cliquables. */
  onAller?: (id: string) => void;
  /** Toute étape est atteignable directement (un formulaire dont l'ordre est un guide, pas une contrainte). */
  libre?: boolean;
  libelle?: string;
  testId?: string;
}

export default function Etapes({ etapes, courante, onAller, libre, libelle = "Étapes", testId }: Props) {
  const indexCourante = etapes.findIndex((e) => e.id === courante);
  return (
    <ol className="etapes" aria-label={libelle} data-testid={testId}>
      {etapes.map((e, i) => {
        const etat = i < indexCourante ? "faite" : i === indexCourante ? "courante" : "a-venir";
        const contenu = (
          <>
            <span className="numero" aria-hidden="true">{i + 1}</span>
            <span className="etape-libelle">{e.libelle}</span>
            {etat === "faite" && <span className="sr-only"> (terminée)</span>}
          </>
        );
        return (
          <li key={e.id} className={etat === "faite" ? "faite" : undefined} aria-current={etat === "courante" ? "step" : undefined} data-testid={e.testId} data-etape={e.id}>
            {onAller && (etat === "faite" || (libre && etat !== "courante"))
              ? <button type="button" className="etape-retour" onClick={() => onAller(e.id)}>{contenu}</button>
              : contenu}
          </li>
        );
      })}
    </ol>
  );
}
