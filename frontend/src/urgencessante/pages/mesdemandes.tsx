/** **Mes demandes** — une entrée de menu, une page (addenda 8, M-08). */
import { EnTeteEcran } from "../../composants";
import PageMaquette from "../../composants/PageMaquette";
import { MesDemandes } from "../../effectifs/demandes/Demandes";

export default function Mesdemandes() {
  return (
    <PageMaquette module="Urgences-santé" contexte="Mon espace • demandes">
      <EnTeteEcran titre="Mes demandes" description="Liste de temps supplémentaire, événement, formation ou autre demande opérationnelle : envoyez-la, suivez sa décision." testId="mesdemandes-titre" />
      <MesDemandes />
    </PageMaquette>
  );
}
