/**
 * **Badge de statut** — un texte, une couleur **et** une forme (● ○ ◐) : un daltonien ou un
 * écran monochrome lit le même statut que les autres (WCAG 1.4.1). Les classes `.badge`
 * historiques (`badge-active`, `badge.draft`…) restent valables : ce composant les normalise.
 */
import { ReactNode } from "react";

export type TonBadge = "succes" | "avertissement" | "erreur" | "info" | "neutre";
/** La forme accompagne le ton : plein = fait/actif, vide = à venir/brouillon, demi = en cours. */
export type FormeBadge = "plein" | "vide" | "demi" | "aucune";

interface Props {
  ton?: TonBadge;
  forme?: FormeBadge;
  children: ReactNode;
  testId?: string;
  className?: string;
  title?: string;
}

const FORMES: Record<FormeBadge, string> = { plein: "●", vide: "○", demi: "◐", aucune: "" };

export default function Badge({ ton = "neutre", forme = "aucune", children, testId, className, title }: Props) {
  return (
    <span className={["badge", `badge-ton-${ton}`, className].filter(Boolean).join(" ")} data-testid={testId} title={title}>
      {forme !== "aucune" && <span className="badge-forme" aria-hidden="true">{FORMES[forme]} </span>}
      {children}
    </span>
  );
}

/**
 * Le ton et la forme d'un statut métier connu du produit — une seule table pour les factures,
 * contrats, feuilles, demandes, billets, besoins… Un statut inconnu reste neutre, sans forme.
 */
const STATUTS: Record<string, [TonBadge, FormeBadge]> = {
  draft: ["neutre", "vide"], brouillon: ["neutre", "vide"], planned: ["info", "vide"], ouvert: ["info", "vide"], open: ["info", "vide"], pending: ["avertissement", "demi"], en_attente: ["avertissement", "demi"],
  sent: ["info", "demi"], submitted: ["info", "demi"], in_progress: ["avertissement", "demi"], en_cours: ["avertissement", "demi"], recherche: ["avertissement", "demi"], on_hold: ["avertissement", "demi"], on_leave: ["avertissement", "demi"], trial: ["avertissement", "demi"],
  active: ["succes", "plein"], actif: ["succes", "plein"], paid: ["succes", "plein"], approved: ["succes", "plein"], approuvee: ["succes", "plein"], done: ["succes", "plein"], completed: ["succes", "plein"], attribue: ["succes", "plein"], resolu: ["succes", "plein"], posted: ["succes", "plein"], signed: ["succes", "plein"],
  rejected: ["erreur", "plein"], refusee: ["erreur", "plein"], cancelled: ["erreur", "plein"], annule: ["erreur", "plein"], blocked: ["erreur", "plein"], echec: ["erreur", "plein"], terminated: ["erreur", "plein"], closed: ["neutre", "plein"], clos: ["neutre", "plein"], suspended: ["erreur", "demi"], overdue: ["erreur", "plein"],
};

export function tonDuStatut(statut: string | null | undefined): [TonBadge, FormeBadge] {
  return STATUTS[String(statut ?? "").toLowerCase()] ?? ["neutre", "aucune"];
}

/** Un badge dont le ton découle du statut métier ; le libellé reste celui de l'appelant (traduit). */
export function BadgeStatut({ statut, children, testId, className }: { statut: string | null | undefined; children?: ReactNode; testId?: string; className?: string }) {
  const [ton, forme] = tonDuStatut(statut);
  return <Badge ton={ton} forme={forme} testId={testId} className={className}>{children ?? statut ?? "—"}</Badge>;
}
