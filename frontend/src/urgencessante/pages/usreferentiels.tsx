/**
 * **Référentiels** — une entrée de menu, une page (AO 26-015-DO, planches s01 à s12).
 *
 * Cette vue était un **onglet** d'un écran à douze onglets. Une page à son adresse se partage par
 * un lien, revient par le bouton « retour », et se charge seule — ce qu'un onglet ne fait jamais.
 */
import { EnTeteEcran } from "../../composants";
import PageMaquette from "../../composants/PageMaquette";
import Referentiels from "../../effectifs/Referentiels";

export default function Usreferentiels() {
  return (
    <PageMaquette module="Urgences-santé" contexte="Référentiels">
      <EnTeteEcran titre="Référentiels" description="Les ensembles de l'organisme — unités, corps d'emploi, statuts, natures, types d'heure — datés." testId="usreferentiels-titre" />
      <Referentiels />
    </PageMaquette>
  );
}
