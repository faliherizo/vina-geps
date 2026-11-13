/**
 * Le détail d'une feuille, commun à l'employé et à l'encadrement : anomalies (bloquantes nommées
 * comme telles, jamais par la seule couleur), totaux par type d'heure, heures supplémentaires et
 * écart planifié / déclaré (E-GP-8, E-RF-11), corrélation des sources tierces entre elles
 * (E-RF-13) et circuit d'approbation de la dernière soumission (E-GP-6).
 */
import { Message } from "../../composants";
import { duree, heureDe, LIBELLE_ETAPE, origineEnMots, preuveEnMots, type FeuilleDetaillee } from "./types";

/** `avecSegments` : l'encadrement, qui n'a pas la liste des segments de l'employé, les lit ici avec leur preuve. */
export default function DetailFeuille({ f, prefixe, avecSegments = false }: { f: FeuilleDetaillee; prefixe: string; avecSegments?: boolean }) {
  return (
    <>
      {f.anomalies.length > 0
        ? (
          <Message variante="avertissement" titre={`${f.anomalies.length} anomalie${f.anomalies.length > 1 ? "s" : ""}`} testId={`${prefixe}-anomalies`}>
            <ul>{f.anomalies.map((a, i) => <li key={`${a.code}-${a.date}-${i}`}>{a.date} — {a.message}{a.bloquante ? " (à corriger avant de soumettre)" : ""}</li>)}</ul>
          </Message>
        )
        : <p data-testid={`${prefixe}-sans-anomalie`}>Aucune anomalie.</p>}
      <table className="eff-table-compacte" data-testid={`${prefixe}-totaux`}>
        <caption>Totaux de la période</caption>
        <thead><tr><th scope="col">Type d'heure</th><th scope="col">Temps</th></tr></thead>
        <tbody>
          {Object.entries(f.totaux).map(([code, m]) => <tr key={code}><th scope="row">{code}</th><td>{duree(m)}</td></tr>)}
          <tr><th scope="row">dont heures supplémentaires</th><td data-testid={`${prefixe}-hs`}>{duree(f.heuresSupplementaires.total)}</td></tr>
        </tbody>
      </table>
      {f.ecarts.length > 0 && (
        <table className="eff-table-compacte" data-testid={`${prefixe}-ecarts`}>
          <caption>Planifié et déclaré, quart par quart</caption>
          <thead><tr><th scope="col">Jour</th><th scope="col">Planifié</th><th scope="col">Déclaré</th><th scope="col">Écart</th></tr></thead>
          <tbody>{f.ecarts.map((e) => <tr key={e.placeId}><th scope="row">{e.date}</th><td>{duree(e.planifie)}</td><td>{duree(e.declare)}</td><td>{duree(e.ecart)}</td></tr>)}</tbody>
        </table>
      )}
      {avecSegments && f.segments.length > 0 && (
        <table className="eff-table-compacte" data-testid={`${prefixe}-segments`}>
          <caption>Temps et preuves</caption>
          <thead><tr><th scope="col">Jour</th><th scope="col">Heures</th><th scope="col">Origine</th><th scope="col">Preuve tierce</th></tr></thead>
          <tbody>
            {f.segments.map((s) => (
              <tr key={s.id}><th scope="row">{s.date}</th><td>{heureDe(s.debut)}–{heureDe(s.fin)}</td><td>{origineEnMots(s)}</td><td>{preuveEnMots(s)}</td></tr>
            ))}
          </tbody>
        </table>
      )}
      {(f.correlation ?? []).length > 0 && (
        <table className="eff-table-compacte" data-testid={`${prefixe}-correlation`}>
          <caption>Sources tierces comparées entre elles</caption>
          <thead><tr><th scope="col">Jour</th><th scope="col">Sources</th><th scope="col">Concordants</th><th scope="col">Divergents</th></tr></thead>
          <tbody>
            {(f.correlation ?? []).map((c) => (
              <tr key={`${c.date}-${c.sources.join("-")}`}>
                <th scope="row">{c.date}</th><td>{c.sources.join(" et ")}</td><td>{c.concordants}</td>
                <td>{c.divergents}{c.divergents > 0 ? " (en désaccord)" : ""}</td>
              </tr>
            ))}
          </tbody>
        </table>
      )}
      {f.flux && (
        <ol aria-label="Circuit d'approbation" data-testid={`${prefixe}-circuit`}>
          {f.flux.etapes.map((e) => (
            <li key={e.id}>
              Palier {e.palier} : {e.libelle} — {LIBELLE_ETAPE[e.statut]}{e.palier === f.flux?.palierCourant ? " (palier courant)" : ""}
            </li>
          ))}
        </ol>
      )}
    </>
  );
}
