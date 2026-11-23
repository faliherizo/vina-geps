/**
 * **Mes bulletins de paie** (ADR 0076) — chacun lit les bulletins des paies traitées qui le concernent, et seulement
 * les siens : le serveur les trouve par la ressource reliée au compte.
 */
import { useCallback, useEffect, useState } from "react";
import { EnTeteEcran, EtatVide, Message, Tableau, type Colonne } from "../composants";
import { FormulaireModal } from "../Modal";
import { useApiEffectifs as useApi } from "../effectifs/api";
import { liste } from "../formeRecue";
import DetailBulletin from "./DetailBulletin";
import { useTelechargementPdf } from "./telecharger";
import { argent, heures, type Bulletin } from "./types";

const COL: ReadonlyArray<Colonne<Bulletin>> = [
  { id: "date", libelle: "Date de paie", tri: (b) => b.datePaie }, { id: "heures", libelle: "Heures", numerique: true },
  { id: "brut", libelle: "Brut", numerique: true }, { id: "retenues", libelle: "Retenues", numerique: true },
  { id: "net", libelle: "Net", numerique: true }, { id: "actions", libelle: "Actions", actions: true },
];

export default function MesBulletins() {
  const { lire, erreur } = useApi();
  const { telecharger, erreurPdf } = useTelechargementPdf();
  const [bulletins, setBulletins] = useState<Bulletin[]>([]);
  const [ouvert, setOuvert] = useState<Bulletin | null>(null);
  const charger = useCallback(async () => setBulletins(liste<Bulletin>(await lire<Bulletin[]>("/api/paie/mes-bulletins"))), [lire]);
  useEffect(() => { void charger(); }, [charger]);
  return (
    <div data-testid="mbu-ecran">
      <EnTeteEcran titre="Mes bulletins de paie" testId="mbu-titre"
        description="Vos paies traitées, la plus récente d'abord : le détail du brut au net, et la fiche de paie en PDF à conserver." />
      {erreur && <Message variante="erreur" testId="mbu-erreur">{erreur}</Message>}
      {erreurPdf && <Message variante="erreur" testId="mbu-erreur-pdf">{erreurPdf}</Message>}
      <section className="card" aria-labelledby="mbu-h">
        <h2 id="mbu-h">Bulletins</h2>
        <Tableau colonnes={COL} lignes={bulletins} legende="Mes bulletins de paie" testId="mbu-liste"
          vide={<EtatVide titre="Aucun bulletin" compact testId="mbu-vide">Vos bulletins apparaissent ici une fois la paie traitée.</EtatVide>}
          rendre={(b) => (
            <tr key={b.id} data-testid={`mbu-${b.id}`}>
              <td>{b.datePaie}</td><td className="num">{heures(b.heuresRegulieres + b.heuresSupplementaires)}</td>
              <td className="num">{argent(b.retenues.brut)}</td><td className="num">{argent(b.totalRetenues)}</td><td className="num">{argent(b.net)}</td>
              <td><div className="eff-actions">
                <button type="button" className="secondary" data-testid={`mbu-${b.id}-voir`} onClick={() => setOuvert(b)}>
                  Détail<span className="sr-only"> du bulletin du {b.datePaie}</span></button>
                <button type="button" className="secondary" data-testid={`mbu-${b.id}-pdf`}
                  onClick={() => void telecharger(`/api/paie/mes-bulletins/${b.id}/pdf`, `fiche-paie-${b.datePaie}.pdf`)}>
                  Fiche PDF<span className="sr-only"> du {b.datePaie}</span></button>
              </div></td>
            </tr>
          )} />
      </section>
      <FormulaireModal libelle="Bulletin" titre={ouvert ? `Bulletin du ${ouvert.datePaie}` : "Bulletin"} testId="mbu-bulletin" size="md"
        ouvert={ouvert !== null} onFermer={() => setOuvert(null)}>
        {ouvert && <DetailBulletin b={ouvert} prefixe="mbu-b" />}
      </FormulaireModal>
    </div>
  );
}
