/**
 * **Pointage et présence** — une entrée de menu, une page (AO 26-015-DO, planches s01 à s12).
 *
 * Cette vue était un **onglet** d'un écran à douze onglets. Une page à son adresse se partage par
 * un lien, revient par le bouton « retour », et se charge seule — ce qu'un onglet ne fait jamais.
 */
import { EnTeteEcran } from "../../composants";
import PageMaquette from "../../composants/PageMaquette";
import PresencePlanification from "../../effectifs/presence/PresencePlanification";

export default function Uspresence() {
  return (
    <PageMaquette module="Urgences-santé" contexte="Pointage et présence">
      <EnTeteEcran titre="Pointage et présence" description="Les pointages de votre périmètre, leurs écarts, les profils et l'audit des localisations." testId="uspresence-titre" />
      <PresencePlanification />
    </PageMaquette>
  );
}
