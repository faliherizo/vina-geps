/**
 * **Demandes et échanges** — une entrée de menu, une page (AO 26-015-DO, planches s01 à s12).
 *
 * Cette vue était un **onglet** d'un écran à douze onglets. Une page à son adresse se partage par
 * un lien, revient par le bouton « retour », et se charge seule — ce qu'un onglet ne fait jamais.
 */
import { EnTeteEcran } from "../../composants";
import PageMaquette from "../../composants/PageMaquette";
import EchangesPlanification from "../../effectifs/echanges/EchangesPlanification";

export default function Usechanges() {
  return (
    <PageMaquette module="Urgences-santé" contexte="Demandes et échanges">
      <EnTeteEcran titre="Demandes et échanges" description="Les échanges de quarts proposés : admissibilité, contiguïté, repos, et la décision." testId="usechanges-titre" />
      <EchangesPlanification />
    </PageMaquette>
  );
}
