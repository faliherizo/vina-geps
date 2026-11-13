/**
 * **Confection annuelle (AGPH)** — une entrée de menu, une page (AO 26-015-DO, planches s01 à s12).
 *
 * Cette vue était un **onglet** d'un écran à douze onglets. Une page à son adresse se partage par
 * un lien, revient par le bouton « retour », et se charge seule — ce qu'un onglet ne fait jamais.
 */
import { EnTeteEcran } from "../../composants";
import PageMaquette from "../../composants/PageMaquette";
import AgphPlanification from "../../effectifs/agph/AgphPlanification";

export default function Usagph() {
  return (
    <PageMaquette module="Urgences-santé" contexte="Confection annuelle (AGPH)">
      <EnTeteEcran titre="Confection annuelle (AGPH)" description="La campagne annuelle, son tour de rôle, et les remplacements long terme." testId="usagph-titre" />
      <AgphPlanification />
    </PageMaquette>
  );
}
