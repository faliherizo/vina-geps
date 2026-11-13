/**
 * **Absences et comblement** — une entrée de menu, une page (AO 26-015-DO, planches s01 à s12).
 *
 * Cette vue était un **onglet** d'un écran à douze onglets. Une page à son adresse se partage par
 * un lien, revient par le bouton « retour », et se charge seule — ce qu'un onglet ne fait jamais.
 */
import { EnTeteEcran } from "../../composants";
import PageMaquette from "../../composants/PageMaquette";
import AbsencesEffectifs from "../../effectifs/absences/AbsencesEffectifs";

export default function Usabsences() {
  return (
    <PageMaquette module="Urgences-santé" contexte="Absences et comblement">
      <EnTeteEcran titre="Absences et comblement" description="Les demandes d'absence, leur impact sur le comblement, et les places qu'elles libèrent." testId="usabsences-titre" />
      <AbsencesEffectifs />
    </PageMaquette>
  );
}
