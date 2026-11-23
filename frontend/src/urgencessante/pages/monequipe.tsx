/**
 * **Mon équipe** — une entrée de menu, une page : la ligne hiérarchique du gestionnaire, ses feuilles
 * à approuver et sa suppléance (comme dans Nexu VINA).
 */
import { EnTeteEcran } from "../../composants";
import PageMaquette from "../../composants/PageMaquette";
import MonEquipe from "../../effectifs/equipe/MonEquipe";

export default function Monequipe() {
  return (
    <PageMaquette module="Urgences-santé" contexte="Mon équipe">
      <EnTeteEcran titre="Mon équipe" description="Les personnes qui relèvent de vous, les feuilles qui attendent votre décision, et qui vous remplace pendant votre absence." testId="monequipe-titre" />
      <MonEquipe />
    </PageMaquette>
  );
}
