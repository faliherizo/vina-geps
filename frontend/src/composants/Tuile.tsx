/**
 * **Tuile d'indicateur** — une valeur en chiffres tabulaires, un libellé, une période, une
 * tendance dite par **une flèche et un texte** (jamais la couleur seule), et un lien « Voir ».
 * Toute la tuile est cliquable quand `onOuvrir` est donné : la cible fait alors 44 px au moins.
 */
import { ReactNode } from "react";
import Icone from "./Icone";

interface Props {
  valeur: ReactNode;
  libelle: ReactNode;
  /** « ce mois », « au 30 sept. »… */
  periode?: ReactNode;
  /** Variation en pourcentage par rapport à la période précédente ; le signe donne la flèche. */
  tendance?: number | null;
  /** Ce que la tendance mesure, pour l'énoncer (« par rapport au mois précédent »). */
  tendanceLibelle?: string;
  /** Un état d'alerte (impayées, échéances dépassées) : icône et texte. */
  alerte?: string;
  onOuvrir?: () => void;
  libelleOuvrir?: string;
  testId?: string;
  /** `data-testid` porté par la valeur seule, pour les essais qui la lisent. */
  valeurTestId?: string;
  className?: string;
}

export function libelleTendance(t: number, mesure?: string): string {
  const signe = t > 0 ? "en hausse de" : t < 0 ? "en baisse de" : "stable,";
  const valeur = t === 0 ? "" : ` ${Math.abs(t).toLocaleString("fr-CA", { maximumFractionDigits: 1 })} %`;
  return `${signe}${valeur}${mesure ? ` ${mesure}` : ""}`.trim();
}

export default function Tuile({ valeur, libelle, periode, tendance, tendanceLibelle, alerte, onOuvrir, libelleOuvrir = "Voir", testId, valeurTestId, className }: Props) {
  const corps = (
    <>
      <span className="tuile-valeur" data-testid={valeurTestId}>{valeur}</span>
      <span className="tuile-libelle">{libelle}</span>
      {periode && <span className="tuile-periode">{periode}</span>}
      {typeof tendance === "number" && (
        <span className={`tuile-tendance tuile-tendance-${tendance > 0 ? "hausse" : tendance < 0 ? "baisse" : "stable"}`}>
          {tendance !== 0 && <Icone nom={tendance > 0 ? "hausse" : "baisse"} />}
          {" "}{libelleTendance(tendance, tendanceLibelle)}
        </span>
      )}
      {alerte && <span className="tuile-alerte"><Icone nom="avertissement" /> {alerte}</span>}
    </>
  );
  if (onOuvrir) {
    return (
      <button type="button" className={["tuile", "tuile-bouton", className].filter(Boolean).join(" ")} onClick={onOuvrir} data-testid={testId}>
        {corps}
        <span className="tuile-ouvrir">{libelleOuvrir} <Icone nom="chevron-droite" /></span>
      </button>
    );
  }
  return <div className={["tuile", className].filter(Boolean).join(" ")} data-testid={testId}>{corps}</div>;
}
