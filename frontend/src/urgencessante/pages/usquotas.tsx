/**
 * **Quotas** — une entrée de menu, une page (AO 26-015-DO, planches s01 à s12).
 *
 * Cette vue était un **onglet** d'un écran à douze onglets. Une page à son adresse se partage par
 * un lien, revient par le bouton « retour », et se charge seule — ce qu'un onglet ne fait jamais.
 */
import { EnTeteEcran } from "../../composants";
import PageMaquette from "../../composants/PageMaquette";
import Quotas from "../../effectifs/planification/Quotas";

export default function Usquotas() {
  return (
    <PageMaquette module="Urgences-santé" contexte="Quotas">
      <EnTeteEcran titre="Quotas" description="Les quotas de comblement par unité, plage ou nature, et leurs surcharges datées." testId="usquotas-titre" />
      <Quotas />
    </PageMaquette>
  );
}
