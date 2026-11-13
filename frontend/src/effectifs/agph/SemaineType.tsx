/**
 * **Semaine type de disponibilités d'une campagne AGPH** (E-CH-1).
 *
 * La personne dit, pour l'année, quels jours et à quelles heures elle peut travailler. L'éditeur est
 * celui des dépôts de période (une ligne par plage, aucun glisser-déposer), posé sur les sept jours
 * de la semaine plutôt que sur des dates. Le verdict des règles — conformité et motifs — s'affiche
 * sous l'éditeur ; un refus garde la saisie pour qu'on la corrige.
 */
import { useState } from "react";
import { Message } from "../../composants";
import { liste } from "../../formeRecue";
import EditeurPlages, { lignesDe, nouvelleLigne, plagesDe, type LignePlage } from "../disponibilites/EditeurPlages";
import Motifs, { BadgeConformite } from "../disponibilites/Motifs";
import { libelleJourType, SEMAINE_TYPE, versDates, versJours, type DepotCampagne, type PlageType } from "./types";

interface Props {
  prefixe: string;
  depot: DepotCampagne | null;
  /** Enregistre ; rend le dépôt accepté, ou null (le motif du refus est alors dans `erreur`). */
  onEnregistrer: (plages: PlageType[]) => Promise<DepotCampagne | null>;
  erreur: string;
  libelleBouton: string;
}

export default function SemaineType({ prefixe, depot, onEnregistrer, erreur, libelleBouton }: Props) {
  const [lignes, setLignes] = useState<LignePlage[]>(() => (depot ? lignesDe(versDates(liste<PlageType>(depot.plages))) : [nouvelleLigne(SEMAINE_TYPE[0])]));
  const [accepte, setAccepte] = useState<DepotCampagne | null>(depot);
  const [envoi, setEnvoi] = useState(false);

  const enregistrer = async (e: React.FormEvent<HTMLFormElement>) => {
    e.preventDefault();
    setEnvoi(true);
    const d = await onEnregistrer(versJours(plagesDe(lignes)));
    setEnvoi(false);
    if (d) setAccepte(d);
  };

  return (
    // Une **grille éditée là où elle se lit** : la semaine type est la donnée elle-même, et la
    // mettre dans un dialogue cacherait la semaine qu'on est en train de composer. Côté
    // planification, c'est la saisie *pour une autre personne*, choisie dans une liste : elle vit
    // alors dans un tiroir, autour de ce même composant.
    <form onSubmit={(e) => void enregistrer(e)} data-formulaire="en-place" aria-label="Ma semaine type" data-testid={`${prefixe}-semaine`}>
      <EditeurPlages prefixe={prefixe} jours={SEMAINE_TYPE} lignes={lignes} onChange={setLignes} libelleDuJour={libelleJourType} />
      {erreur && <Message variante="erreur" testId={`${prefixe}-erreur`}>{erreur}</Message>}
      {accepte && !erreur && (
        <div role="status" data-testid={`${prefixe}-verdict`}>
          <p>
            <BadgeConformite conformite={accepte.conformite} testId={`${prefixe}-conformite`} /> {accepte.heures.toLocaleString("fr-CA")} h offertes par semaine,
            déposées le {accepte.deposeLe.slice(0, 10)}.
          </p>
          <Motifs motifs={liste(accepte.motifs)} titre="Ce que les règles ont relevé" testId={`${prefixe}-motifs`} />
        </div>
      )}
      <p className="eff-actions">
        <button type="submit" data-testid={`${prefixe}-enregistrer`} disabled={envoi}>{libelleBouton}</button>
      </p>
    </form>
  );
}
