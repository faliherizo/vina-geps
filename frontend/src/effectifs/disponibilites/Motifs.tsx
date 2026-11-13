/**
 * **Les motifs d'une décision des règles** (E-GN-16) : ce que la personne doit corriger, rendu
 * avec les valeurs de son propre dépôt. Un refus est une alerte ; un avertissement, un statut.
 */
import { Badge, Message, type TonBadge } from "../../composants";
import type { Motif } from "../planification/types";
import { LIBELLE_CONFORMITE, type Conformite } from "./types";

export const TON_CONFORMITE: Record<Conformite, TonBadge> = { CONFORME: "succes", AVERTIE: "avertissement", APPROBATION_REQUISE: "info" };

export function BadgeConformite({ conformite, testId }: { conformite: Conformite | null; testId?: string }) {
  if (!conformite) return <Badge ton="neutre" testId={testId}>Aucun dépôt</Badge>;
  return <Badge ton={TON_CONFORMITE[conformite]} testId={testId}>{LIBELLE_CONFORMITE[conformite]}</Badge>;
}

export default function Motifs({ motifs, refus, titre, testId }: { motifs: readonly Motif[]; refus?: boolean; titre: string; testId: string }) {
  if (motifs.length === 0) return null;
  return (
    <Message variante={refus ? "erreur" : "avertissement"} titre={titre} testId={testId}>
      <ul>
        {motifs.map((m) => <li key={`${m.cle}-${m.action}`} data-testid={`${testId}-${m.cle}`}>{m.message}</li>)}
      </ul>
    </Message>
  );
}
