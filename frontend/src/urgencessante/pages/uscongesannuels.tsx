/** **Choix des congés annuels** — une entrée de menu, une page (addenda 8, M-02). */
import { EnTeteEcran } from "../../composants";
import PageMaquette from "../../composants/PageMaquette";
import { CampagnesConges } from "../../effectifs/conges/CongesAnnuels";

export default function Uscongesannuels() {
  return (
    <PageMaquette module="Urgences-santé" contexte="Choix des congés annuels">
      <EnTeteEcran titre="Choix des congés annuels" description="Les campagnes par groupe d'employés : ouverture des choix, octroi par ancienneté sous un plafond d'absents par semaine." testId="uscongesannuels-titre" />
      <CampagnesConges />
    </PageMaquette>
  );
}
