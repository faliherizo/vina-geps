/**
 * **Milieux physiques** — une entrée de menu, une page (AO 26-015-DO, planches s01 à s12).
 *
 * Cette vue était un **onglet** d'un écran à douze onglets. Une page à son adresse se partage par
 * un lien, revient par le bouton « retour », et se charge seule — ce qu'un onglet ne fait jamais.
 */
import { EnTeteEcran } from "../../composants";
import PageMaquette from "../../composants/PageMaquette";
import Milieux from "../../effectifs/Milieux";

export default function Usmilieux() {
  return (
    <PageMaquette module="Urgences-santé" contexte="Milieux physiques">
      <EnTeteEcran titre="Milieux physiques" description="Les sites, leurs coordonnées et les preuves de présence qu'ils exigent." testId="usmilieux-titre" />
      <Milieux />
    </PageMaquette>
  );
}
