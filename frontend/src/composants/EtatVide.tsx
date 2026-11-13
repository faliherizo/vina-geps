/**
 * **État vide** — une liste sans élément n'est pas un tableau sans ligne : c'est une phrase et
 * l'action qui fait exister le premier élément (« Aucun contrat pour l'instant — Créer un
 * contrat »). Dans un tableau, on le pose dans la cellule `vide` de `TbodyPagine`.
 */
import { ReactNode } from "react";
import Icone, { type NomIcone } from "./Icone";

interface Props {
  titre: string;
  /** Une phrase de contexte ; facultative. */
  children?: ReactNode;
  /** Le bouton (ou lien) qui crée le premier élément. */
  action?: ReactNode;
  icone?: NomIcone;
  testId?: string;
  /** Rendu compact, pour une cellule de tableau ou un panneau étroit. */
  compact?: boolean;
}

export default function EtatVide({ titre, children, action, icone = "boite-vide", testId, compact }: Props) {
  return (
    <div className={compact ? "etat-vide etat-vide-compact" : "etat-vide"} data-testid={testId}>
      <span className="etat-vide-icone"><Icone nom={icone} taille={compact ? 24 : 40} /></span>
      <p className="etat-vide-titre">{titre}</p>
      {children && <p className="etat-vide-texte">{children}</p>}
      {action && <div className="etat-vide-action">{action}</div>}
    </div>
  );
}
