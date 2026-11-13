/**
 * **Périmètres** — une entrée de menu, une page (AO 26-015-DO, planches s01 à s12).
 *
 * Cette vue était un **onglet** d'un écran à douze onglets. Une page à son adresse se partage par
 * un lien, revient par le bouton « retour », et se charge seule — ce qu'un onglet ne fait jamais.
 */
import { EnTeteEcran } from "../../composants";
import PageMaquette from "../../composants/PageMaquette";
import Perimetres from "../../effectifs/Perimetres";

export default function Usperimetres() {
  return (
    <PageMaquette module="Urgences-santé" contexte="Périmètres">
      <EnTeteEcran titre="Périmètres" description="Qui voit quoi : l'unité, le niveau et l'étendue accordés à chaque compte." testId="usperimetres-titre" />
      <Perimetres />
    </PageMaquette>
  );
}
