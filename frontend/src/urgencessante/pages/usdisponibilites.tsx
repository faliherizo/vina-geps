/**
 * **Disponibilités de la période** — une entrée de menu, une page (AO 26-015-DO, planches s01 à s12).
 *
 * Cette vue était un **onglet** d'un écran à douze onglets. Une page à son adresse se partage par
 * un lien, revient par le bouton « retour », et se charge seule — ce qu'un onglet ne fait jamais.
 */
import { EnTeteEcran } from "../../composants";
import PageMaquette from "../../composants/PageMaquette";
import DisponibilitesPeriode from "../../effectifs/disponibilites/DisponibilitesPeriode";
import { ChampPeriode, usePeriodeCourante } from "../periode";

export default function Usdisponibilites() {
  const periode = usePeriodeCourante();
  return (
    <PageMaquette module="Urgences-santé" contexte="Disponibilités de la période">
      <EnTeteEcran titre="Disponibilités de la période" description="Les dépôts de la période, leur conformité, et le bassin de candidats d'une place." testId="usdisponibilites-titre">
        <ChampPeriode ctx={periode} />
      </EnTeteEcran>
      <DisponibilitesPeriode periode={periode.periode} />
    </PageMaquette>
  );
}
