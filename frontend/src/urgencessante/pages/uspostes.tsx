/**
 * **Postes** — une entrée de menu, une page (AO 26-015-DO, planches s01 à s12).
 *
 * Cette vue était un **onglet** d'un écran à douze onglets. Une page à son adresse se partage par
 * un lien, revient par le bouton « retour », et se charge seule — ce qu'un onglet ne fait jamais.
 */
import { EnTeteEcran } from "../../composants";
import PageMaquette from "../../composants/PageMaquette";
import Postes from "../../effectifs/planification/Postes";

export default function Uspostes() {
  return (
    <PageMaquette module="Urgences-santé" contexte="Postes">
      <EnTeteEcran titre="Postes" description="Les lignes de quarts à cycle, leurs paramètres, et leur publication." testId="uspostes-titre" />
      <Postes />
    </PageMaquette>
  );
}
