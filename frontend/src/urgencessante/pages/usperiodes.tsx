/**
 * **Périodes** — une entrée de menu, une page (AO 26-015-DO, planches s01 à s12).
 *
 * Cette vue était un **onglet** d'un écran à douze onglets. Une page à son adresse se partage par
 * un lien, revient par le bouton « retour », et se charge seule — ce qu'un onglet ne fait jamais.
 */
import { EnTeteEcran } from "../../composants";
import PageMaquette from "../../composants/PageMaquette";
import Periodes from "../../effectifs/planification/Periodes";
import { usePeriodeCourante } from "../periode";

export default function Usperiodes() {
  const periode = usePeriodeCourante();
  return (
    <PageMaquette module="Urgences-santé" contexte="Périodes">
      <EnTeteEcran titre="Périodes" description="Le cycle d'une période : préparation, collecte, confection, publication, clôture." testId="usperiodes-titre" />
      <Periodes periodes={periode.periodes} recharger={periode.recharger} />
    </PageMaquette>
  );
}
