/** **Mes congés annuels** — une entrée de menu, une page (addenda 8, M-02). */
import { EnTeteEcran } from "../../composants";
import PageMaquette from "../../composants/PageMaquette";
import { MesCongesAnnuels } from "../../effectifs/conges/CongesAnnuels";

export default function Mescongesannuels() {
  return (
    <PageMaquette module="Urgences-santé" contexte="Mon espace • congés annuels">
      <EnTeteEcran titre="Mes congés annuels" description="Classez vos semaines de congé dans la campagne de votre groupe ; l'octroi suit l'ancienneté." testId="mescongesannuels-titre" />
      <MesCongesAnnuels />
    </PageMaquette>
  );
}
