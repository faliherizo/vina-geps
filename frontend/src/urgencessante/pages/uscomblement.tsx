/**
 * **Comblement** — une entrée de menu, une page (AO 26-015-DO, planches s01 à s12).
 *
 * Cette vue était un **onglet** d'un écran à douze onglets. Une page à son adresse se partage par
 * un lien, revient par le bouton « retour », et se charge seule — ce qu'un onglet ne fait jamais.
 */
import { EnTeteEcran } from "../../composants";
import PageMaquette from "../../composants/PageMaquette";
import Comblement from "../../effectifs/planification/Comblement";
import { ChampPeriode, usePeriodeCourante } from "../periode";

export default function Uscomblement() {
  const periode = usePeriodeCourante();
  return (
    <PageMaquette module="Urgences-santé" contexte="Comblement">
      <EnTeteEcran titre="Comblement" description="Le tableau du matin : taux de comblement, quarts sous leur minimum, quotas manqués." testId="uscomblement-titre">
        <ChampPeriode ctx={periode} />
      </EnTeteEcran>
      <Comblement periode={periode.periode} />
    </PageMaquette>
  );
}
