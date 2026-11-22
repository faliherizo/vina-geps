/**
 * **« Voir ce qu'il faut configurer »** — posé à côté d'un refus que l'utilisateur ne peut pas lever
 * seul (« votre compte n'est relié à aucune ressource planifiable »). Le message du serveur reste tel
 * quel ; le lien mène à la fiche de l'écran, dont la rubrique « Avant de commencer » dit qui doit faire
 * quoi, et où.
 */
import type { Page } from "../App";
import { FICHES } from "./fichesEcrans";
import { cheminFiche } from "./liensAide";

/** Un refus qui tient à la configuration du compte, non à la saisie : il appelle ce lien. */
export const estSansRessource = (message: string): boolean => /aucune ressource planifiable/i.test(message);

export default function LienPrealables({ page, testId }: { page: Page; testId: string }) {
  return (
    <a href={cheminFiche(page)} data-testid={testId}>
      Voir ce qu'il faut configurer<span className="sr-only"> avant d'utiliser l'écran « {FICHES[page].titre} »</span>
    </a>
  );
}
