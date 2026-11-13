/**
 * **Feuilles de temps** — une entrée de menu, une page (AO 26-015-DO, planches s01 à s12).
 *
 * Cette vue était un **onglet** d'un écran à douze onglets. Une page à son adresse se partage par
 * un lien, revient par le bouton « retour », et se charge seule — ce qu'un onglet ne fait jamais.
 */
import { EnTeteEcran } from "../../composants";
import PageMaquette from "../../composants/PageMaquette";
import TempsPlanification from "../../effectifs/temps/TempsPlanification";

export default function Ustemps() {
  return (
    <PageMaquette module="Urgences-santé" contexte="Feuilles de temps">
      <EnTeteEcran titre="Feuilles de temps" description="Préparer, approuver et verrouiller les feuilles, produire le lot de paie et les relevés." testId="ustemps-titre" />
      <TempsPlanification />
    </PageMaquette>
  );
}
