/**
 * Le détail d'un bulletin : le brut, chaque retenue, le net, puis ce que paie l'employeur. Un tableau, pas une
 * grille de cartes : un lecteur d'écran l'annonce ligne par ligne, montant à côté de son libellé.
 */
import { argent, heures, type Bulletin, type Gain } from "./types";

/** Le taux d'une ligne de gains : une prime en pourcentage se lit en %, le reste en dollars. */
function taux(g: Gain): string {
  return g.nature === "prime-pourcentage" ? `${Math.round(g.taux * 100000) / 1000} %` : argent(g.taux);
}

const quantite = (g: Gain): string => (g.nature === "salaire" || g.nature === "forfait" ? "1" : heures(g.quantite));

export default function DetailBulletin({ b, prefixe }: { b: Bulletin; prefixe: string }) {
  const r = b.retenues;
  const e = r.employeur;
  const lignes: Array<[string, number, string]> = [
    ["Impôt fédéral", r.impotFederal, "federal"], ["Impôt du Québec", r.impotQuebec, "quebec"], ["RRQ", r.rrq, "rrq"],
    ["RRQ supplémentaire (RRQ2)", r.rrq2, "rrq2"], ["Assurance-emploi", r.ae, "ae"], ["RQAP", r.rqap, "rqap"],
  ];
  const employeur: Array<[string, number]> = [
    ["RRQ et RRQ2", e.rrq + e.rrq2], ["Assurance-emploi (1,4 fois)", e.ae], ["RQAP", e.rqap],
    ["Fonds des services de santé", e.fss], ["Normes du travail (CNT)", e.cnt], ["CNESST", e.cnesst],
  ];
  return (
    <div data-testid={`${prefixe}-detail`}>
      <p>{b.nom} (matricule {b.matricule}) — paie du {b.datePaie} : {heures(b.heuresRegulieres)} régulières,{" "}
        {heures(b.heuresSupplementaires)} supplémentaires, {b.feuilles.length} feuille{b.feuilles.length > 1 ? "s" : ""} de temps.</p>
      {(b.gains ?? []).length > 0 && (
        <table className="eff-table-compacte" data-testid={`${prefixe}-gains`}>
          <caption>Gains : heures, primes et forfaits</caption>
          <thead><tr><th scope="col">Gain</th><th scope="col" className="num">Quantité</th><th scope="col" className="num">Taux</th>
            <th scope="col" className="num">Montant</th></tr></thead>
          <tbody>
            {(b.gains ?? []).map((g, i) => (
              <tr key={`${g.code}-${i}`} data-testid={`${prefixe}-gain-${g.code}`}>
                <th scope="row">{g.libelle} <span className="muted">({g.code})</span></th>
                <td className="num">{quantite(g)}</td><td className="num">{taux(g)}</td><td className="num">{argent(g.montant)}</td>
              </tr>
            ))}
          </tbody>
        </table>
      )}
      <table className="eff-table-compacte" data-testid={`${prefixe}-retenues`}>
        <caption>Du brut au net</caption>
        <thead><tr><th scope="col">Ligne</th><th scope="col" className="num">Montant</th></tr></thead>
        <tbody>
          <tr><th scope="row">Brut</th><td className="num" data-testid={`${prefixe}-brut`}>{argent(r.brut)}</td></tr>
          {lignes.map(([libelle, montant, cle]) => (
            <tr key={cle}><th scope="row">{libelle}</th><td className="num" data-testid={`${prefixe}-${cle}`}>− {argent(montant)}</td></tr>
          ))}
          <tr><th scope="row">Net à verser</th><td className="num" data-testid={`${prefixe}-net`}><strong>{argent(b.net)}</strong></td></tr>
        </tbody>
      </table>
      <table className="eff-table-compacte" data-testid={`${prefixe}-employeur`}>
        <caption>Cotisations de l'employeur, en plus du brut</caption>
        <thead><tr><th scope="col">Cotisation</th><th scope="col" className="num">Montant</th></tr></thead>
        <tbody>
          {employeur.map(([libelle, montant]) => <tr key={libelle}><th scope="row">{libelle}</th><td className="num">{argent(montant)}</td></tr>)}
          <tr><th scope="row">Coût total pour l'employeur</th><td className="num"><strong>{argent(b.coutEmployeur)}</strong></td></tr>
        </tbody>
      </table>
    </div>
  );
}
