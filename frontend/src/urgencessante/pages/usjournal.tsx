/**
 * **Journal des décisions** — une entrée de menu, une page (AO 26-015-DO, planches s01 à s12).
 *
 * Cette vue était un **onglet** d'un écran à douze onglets. Une page à son adresse se partage par
 * un lien, revient par le bouton « retour », et se charge seule — ce qu'un onglet ne fait jamais.
 */
import { EnTeteEcran } from "../../composants";
import PageMaquette from "../../composants/PageMaquette";
import Journal from "../../effectifs/Journal";

export default function Usjournal() {
  return (
    <PageMaquette module="Urgences-santé" contexte="Journal des décisions">
      <EnTeteEcran titre="Journal des décisions" description="Chaque action horodatée avec son auteur ou le mécanisme qui l'a posée." testId="usjournal-titre" />
      <Journal />
    </PageMaquette>
  );
}
