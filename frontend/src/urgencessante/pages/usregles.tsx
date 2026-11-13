/**
 * **Règles** — une entrée de menu, une page (AO 26-015-DO, planches s01 à s12).
 *
 * Cette vue était un **onglet** d'un écran à douze onglets. Une page à son adresse se partage par
 * un lien, revient par le bouton « retour », et se charge seule — ce qu'un onglet ne fait jamais.
 */
import { EnTeteEcran } from "../../composants";
import PageMaquette from "../../composants/PageMaquette";
import Regles from "../../effectifs/Regles";

export default function Usregles() {
  return (
    <PageMaquette module="Urgences-santé" contexte="Règles">
      <EnTeteEcran titre="Règles" description="Les règles versionnées par contexte et par portée, leur simulateur et leurs décisions." testId="usregles-titre" />
      <Regles />
    </PageMaquette>
  );
}
