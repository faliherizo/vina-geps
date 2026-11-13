/**
 * **Les règles qui jugeront le dépôt** — annoncées avant, pas seulement au refus (E-GN-16, et le
 * panneau « Règles applicables » de la planche s02).
 *
 * L'employée ne les connaissait qu'en se faisant refuser : elle déposait, lisait le motif,
 * corrigeait. Ce panneau dit la même chose **dans l'autre sens** — ce qu'il faut respecter, et ce
 * qui arrive sinon — avec la liste exacte que le serveur emploiera pour décider.
 *
 * Deux choix d'affichage, l'un et l'autre contre une fausse impression :
 *
 * - **la conséquence est en mots**, jamais une couleur seule (WCAG 1.4.1) : « refuse le dépôt » et
 *   « demande une approbation » ne se distinguent pas d'un rouge et d'un bleu quand on ne voit pas
 *   les couleurs, et la différence est tout l'enjeu ;
 * - **la portée est dite** quand elle n'est pas celle de l'organisme : une règle qui ne vise que
 *   son statut d'emploi s'explique, sinon elle a l'air d'une règle maison.
 */
import { Badge, type TonBadge } from "../../composants";

export interface RegleApplicable {
  cle: string;
  action: string;
  message: string | null;
  porteeType: string;
  porteeCode: string | null;
}

/** Ce qu'une action **fait au dépôt**, du point de vue de qui dépose. */
const CONSEQUENCE: Record<string, string> = {
  ACCEPTER: "accepte le dépôt",
  AVERTIR: "accepte, avec un avertissement",
  EXIGER_APPROBATION: "demande l'approbation de votre gestionnaire",
  EXCLURE_DU_BASSIN: "vous écarte du bassin d'attribution",
  BLOQUER: "refuse le dépôt",
  REFUSER: "refuse le dépôt",
};

const TON: Record<string, TonBadge> = {
  ACCEPTER: "succes", AVERTIR: "avertissement", EXIGER_APPROBATION: "info",
  EXCLURE_DU_BASSIN: "erreur", BLOQUER: "erreur", REFUSER: "erreur",
};

/** « selon votre statut d'emploi (TPR) » — pourquoi cette règle vous vise, vous. */
const PORTEE: Record<string, string> = {
  UNITE: "votre unité",
  CONVENTION: "votre convention",
  STATUT_EMPLOI: "votre statut d'emploi",
  CORPS_EMPLOI: "votre corps d'emploi",
  TITRE_EMPLOI: "votre titre d'emploi",
};

export default function ReglesApplicables({ regles, testId }: { regles: readonly RegleApplicable[]; testId: string }) {
  if (regles.length === 0) {
    return (
      <p data-testid={`${testId}-aucune`}>
        Aucune règle ne restreint votre dépôt pour cette période : déposez les plages qui vous conviennent.
      </p>
    );
  }
  return (
    <details className="eff-groupe" data-testid={testId}>
      <summary>Règles applicables à votre dépôt ({regles.length})</summary>
      <ul aria-label="Règles applicables à votre dépôt">
        {regles.map((r) => (
          <li key={`${r.cle}-${r.porteeType}-${r.porteeCode ?? ""}`} data-testid={`${testId}-${r.cle}`}>
            <strong>{r.cle}</strong>{" "}
            <Badge ton={TON[r.action] ?? "neutre"}>{CONSEQUENCE[r.action] ?? r.action}</Badge>
            {r.message ? <> — {r.message}</> : null}
            {r.porteeType !== "ORGANISME" && PORTEE[r.porteeType]
              ? <> <span className="aide">(selon {PORTEE[r.porteeType]}{r.porteeCode ? ` : ${r.porteeCode}` : ""})</span></>
              : null}
          </li>
        ))}
      </ul>
    </details>
  );
}
