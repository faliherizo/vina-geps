/**
 * **Tiroir** — la fiche d'un élément, ouverte à droite de la liste sans la quitter (gabarit
 * G2). C'est le `Modal` existant en variante latérale : même dialogue titré, même piège de
 * focus, même retour du focus à l'ouvreur, Échap ferme. Sous 900 px, il occupe l'écran.
 */
import { ReactNode } from "react";
import { Modal } from "../Modal";

interface Props {
  ouvert: boolean;
  onFermer: () => void;
  titre: string;
  children: ReactNode;
  testId?: string;
  /** Actions de la fiche (menu `⋯`, boutons), à droite du titre. */
  actions?: ReactNode;
}

export default function Tiroir({ ouvert, onFermer, titre, children, testId = "tiroir", actions }: Props) {
  return (
    <Modal open={ouvert} onClose={onFermer} title={titre} testId={testId} variante="tiroir" actions={actions}>
      {children}
    </Modal>
  );
}
