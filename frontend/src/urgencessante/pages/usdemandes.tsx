/** **Demandes opérationnelles** — une entrée de menu, une page (addenda 8, M-08). */
import { EnTeteEcran } from "../../composants";
import PageMaquette from "../../composants/PageMaquette";
import { DemandesOperationnelles } from "../../effectifs/demandes/Demandes";

export default function Usdemandes() {
  return (
    <PageMaquette module="Urgences-santé" contexte="Demandes opérationnelles">
      <EnTeteEcran titre="Demandes opérationnelles" description="Les demandes de votre périmètre, à accepter ou refuser en motivant, et la liste de temps supplémentaire du jour." testId="usdemandes-titre" />
      <DemandesOperationnelles />
    </PageMaquette>
  );
}
