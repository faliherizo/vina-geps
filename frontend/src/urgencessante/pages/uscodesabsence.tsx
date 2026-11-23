/**
 * **Codes d'absence** — la liste que « Mes absences » et « Absences » proposent, tenue par l'administration de
 * l'organisme : ajouter un code, le remplacer à partir d'une date (libellé, durée, planification), ou le fermer.
 *
 * Les codes étaient déjà un référentiel, mais un administrateur ne les trouvait qu'en sachant qu'il fallait ouvrir
 * « Référentiels », puis choisir « Codes d'absence » dans une liste. Une page à leur nom les rend trouvables. Fermer un
 * code ne l'efface pas : les absences déjà déclarées gardent le leur, et l'historique reste lisible.
 */
import { EnTeteEcran } from "../../composants";
import PageMaquette from "../../composants/PageMaquette";
import Referentiels from "../../effectifs/Referentiels";

export default function Uscodesabsence() {
  return (
    <PageMaquette module="Urgences-santé" contexte="Codes d'absence">
      <EnTeteEcran titre="Codes d'absence" testId="uscodesabsence-titre"
        description="Les motifs qu'une personne choisit en déclarant une absence. Ajoutez, remplacez à partir d'une date, ou fermez un code : il disparaît des formulaires sans effacer les absences passées." />
      <Referentiels typeFixe="codes_absence" />
    </PageMaquette>
  );
}
