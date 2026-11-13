/**
 * **Ressources** — une entrée de menu, une page (AO 26-015-DO, planches s01 à s12).
 *
 * Cette vue était un **onglet** d'un écran à douze onglets. Une page à son adresse se partage par
 * un lien, revient par le bouton « retour », et se charge seule — ce qu'un onglet ne fait jamais.
 */
import { EnTeteEcran } from "../../composants";
import PageMaquette from "../../composants/PageMaquette";
import Ressources from "../../effectifs/planification/Ressources";

export default function Usressources() {
  return (
    <PageMaquette module="Urgences-santé" contexte="Ressources">
      <EnTeteEcran titre="Ressources" description="Les personnes planifiables de votre périmètre, leur statut et leur ancienneté." testId="usressources-titre" />
      <Ressources />
    </PageMaquette>
  );
}
