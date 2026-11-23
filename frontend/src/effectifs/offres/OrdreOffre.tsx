/**
 * **L'ordre d'offre d'une place** (AD-07 ; art. 16.02, 16.03, 16.04 ; ADR 0089) — dans le comblement d'une absence.
 *
 * L'encadrement relit l'ordre tel qu'il a été annoncé : rang, personne, heures déjà comptées dans la fenêtre de
 * répartition, ancienneté, ce que chacun a fait de l'offre et quand. C'est la trace que la convention suppose quand
 * elle compte un refus comme des heures faites ; un tableau légendé, chaque état dit en mots.
 */
import { BadgeStatut } from "../../composants";
import { ETATS_OFFRE, ETATS_RANG, heureLocale, type OffreQuart } from "./types";

const heures = (h: number) => `${h.toLocaleString("fr-CA", { maximumFractionDigits: 2 })} h`;

export default function OrdreOffre({ offre }: { offre: OffreQuart }) {
  const id = `pla-abs-ordre-${offre.id}`;
  return (
    <div data-testid={`pla-abs-ordre-${offre.quart.date}`}>
      <div className="table-wrap" tabIndex={0} role="region" aria-label={`Ordre d'offre du quart du ${offre.quart.date} (zone défilable au clavier)`}>
      <table className="eff-table-compacte" aria-describedby={`${id}-etat`}>
        <caption>
          Ordre d'offre du quart du {offre.quart.date}{offre.urgente ? " — urgent : offert à tous jusqu'au début du quart" : " — par ancienneté et à tour de rôle"}
        </caption>
        <thead>
          <tr>
            <th scope="col">Rang</th><th scope="col">Personne</th><th scope="col">Heures comptées</th><th scope="col">Ancienneté</th>
            <th scope="col">État</th><th scope="col">Réponse attendue avant</th><th scope="col">Répondu le</th>
          </tr>
        </thead>
        <tbody>
          {offre.rangs.map((r) => (
            <tr key={r.ressourceId}>
              <th scope="row">{r.rang}</th>
              <td>{r.nom ?? r.ressourceId}</td>
              <td>{heures(r.heuresComptees)}</td>
              <td>{r.anciennete.toLocaleString("fr-CA")} ans</td>
              <td><BadgeStatut statut={ETATS_RANG[r.etat].ton}>{ETATS_RANG[r.etat].libelle}</BadgeStatut>{r.motif ? ` : ${r.motif}` : ""}</td>
              <td>{heureLocale(r.echeance)}</td>
              <td>{heureLocale(r.reponduLe)}</td>
            </tr>
          ))}
        </tbody>
      </table>
      </div>
      <p id={`${id}-etat`} data-testid={`pla-abs-ordre-etat-${offre.quart.date}`}>Offre {ETATS_OFFRE[offre.etat]}.</p>
    </div>
  );
}
