/**
 * **Quarts et équipages** — une entrée de menu, une page (AO 26-015-DO, planches s01 à s12).
 *
 * Cette vue était un **onglet** d'un écran à douze onglets. Une page à son adresse se partage par
 * un lien, revient par le bouton « retour », et se charge seule — ce qu'un onglet ne fait jamais.
 */
import { EnTeteEcran } from "../../composants";
import PageMaquette from "../../composants/PageMaquette";
import Equipages from "../../effectifs/planification/Equipages";
import { ChampPeriode, usePeriodeCourante } from "../periode";

export default function Usequipages() {
  const periode = usePeriodeCourante();
  return (
    <PageMaquette module="Urgences-santé" contexte="Quarts et équipages">
      <EnTeteEcran titre="Quarts et équipages" description="Les quarts de la période, leur composition par corps d'emploi et l'attribution des places." testId="usequipages-titre">
        <ChampPeriode ctx={periode} />
      </EnTeteEcran>
      <Equipages periode={periode.periode} />
    </PageMaquette>
  );
}
