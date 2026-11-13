/**
 * **Intégrations** — une entrée de menu, une page (AO 26-015-DO, planches s01 à s12).
 *
 * Cette vue était un **onglet** d'un écran à douze onglets. Une page à son adresse se partage par
 * un lien, revient par le bouton « retour », et se charge seule — ce qu'un onglet ne fait jamais.
 */
import { EnTeteEcran } from "../../composants";
import PageMaquette from "../../composants/PageMaquette";
import Integrations from "../../effectifs/Integrations";

export default function Usintegrations() {
  return (
    <PageMaquette module="Urgences-santé" contexte="Intégrations">
      <EnTeteEcran titre="Intégrations" description="Les connecteurs sortants, leur mode, et la boîte d'envoi retentée." testId="usintegrations-titre" />
      <Integrations />
    </PageMaquette>
  );
}
