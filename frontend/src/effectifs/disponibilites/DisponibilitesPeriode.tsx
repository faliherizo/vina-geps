/**
 * **Disponibilités d'une période, côté planificateur** (AO 26-015-DO, lot R4 — E-GN-14, E-GN-16,
 * P-GH-4).
 *
 * Trois questions, dans l'ordre où elles se posent : qui a déposé et qui attend une approbation ;
 * qui peut tenir tel quart — **et pourquoi pas les autres** ; puis, collecte fermée, attribuer les
 * places vacantes des quarts automatisés au premier candidat que les règles acceptent — ce que
 * l'ordonnanceur fait de lui-même au lendemain de la fin de collecte (P-GH-4) ; le bouton reste pour
 * relancer après une correction.
 */
import { useCallback, useEffect, useState } from "react";
import { Badge, EtatVide, Message, Tableau, Tiroir, type Colonne } from "../../composants";
import { heure, useApiEffectifs } from "../api";
import type { Equipage, Periode } from "../planification/types";
import { useReferentiels } from "../planification/useReferentiels";
import EditeurPlages, { joursDe, lignesDe } from "./EditeurPlages";
import Motifs, { BadgeConformite } from "./Motifs";
import { objet } from "../../formeRecue";
import type { AttributionAutomatique, Candidats, Depot, LigneDepot } from "./types";

const API = "/api/effectifs/disponibilites";

export default function DisponibilitesPeriode({ periode }: { periode: Periode | null }) {
  const { lire, envoyer, erreur, setErreur } = useApiEffectifs();
  const { milieux } = useReferentiels([]);
  const [depots, setDepots] = useState<LigneDepot[]>([]);
  const [equipages, setEquipages] = useState<Equipage[]>([]);
  const [fiche, setFiche] = useState<{ ligne: LigneDepot; depot: Depot | null } | null>(null);
  const [placeId, setPlaceId] = useState("");
  const [candidats, setCandidats] = useState<Candidats | null>(null);
  const [bilan, setBilan] = useState("");
  const [auto, setAuto] = useState<AttributionAutomatique | null>(null);

  const charger = useCallback(async () => {
    if (!periode) return;
    setDepots((await lire<LigneDepot[]>(`${API}/periodes/${periode.id}`)) ?? []);
    setEquipages((await lire<Equipage[]>(`/api/effectifs/planification/periodes/${periode.id}/equipages`)) ?? []);
    setAuto(objet<AttributionAutomatique>(await lire<AttributionAutomatique>(`${API}/periodes/${periode.id}/attribution-automatique`)));
  }, [lire, periode]);
  useEffect(() => { setCandidats(null); setPlaceId(""); setBilan(""); void charger(); }, [charger]);

  if (!periode) return <EtatVide titre="Choisissez une période" compact testId="pla-dispo-sans-periode">Les disponibilités se déposent période par période.</EtatVide>;

  const milieu = (id: string) => milieux.find((m) => m.id === id)?.libelle ?? id;
  const vacantes = equipages.flatMap((e) => e.places.filter((p) => !p.ressourceId).map((p) => ({
    id: p.id, libelle: `${e.date} ${heure(e.debut)}–${heure(e.fin)} · ${milieu(e.milieuId)} · place ${p.rang} (${p.corpsEmploiCode})`,
  })));
  const attente = depots.filter((d) => d.depot?.conformite === "APPROBATION_REQUISE").length;
  const sansDepot = depots.filter((d) => !d.depot).length;

  // Même règle que partout : le tiroir s'ouvre d'abord, le dépôt le rejoint.
  const ouvrir = async (ligne: LigneDepot) => {
    setErreur("");
    setFiche({ ligne, depot: null });
    const depot = await lire<Depot | null>(`${API}/periodes/${periode.id}/ressources/${ligne.ressourceId}`);
    setFiche((f) => (f?.ligne.ressourceId === ligne.ressourceId ? { ligne, depot } : f));
  };
  const approuver = async () => {
    if (!fiche) return;
    const d = await envoyer<Depot>(`${API}/periodes/${periode.id}/ressources/${fiche.ligne.ressourceId}/approuver`, {});
    if (!d) return;
    setFiche(null);
    await charger();
  };
  const voirCandidats = async () => {
    setCandidats(placeId ? await lire<Candidats>(`${API}/places/${placeId}/candidats`) : null);
  };
  const attribuer = async () => {
    const r = await envoyer<{ attribuees: number; restees: number }>(`${API}/periodes/${periode.id}/attribuer`, {});
    if (!r) return;
    setBilan(`${r.attribuees} place(s) attribuée(s) depuis les disponibilités ; ${r.restees} restée(s) vacante(s), à combler par la gestion régulière.`);
    setCandidats(null);
    await charger();
  };

  const colonnes: ReadonlyArray<Colonne<LigneDepot>> = [
    { id: "matricule", libelle: "Matricule", tri: (d) => d.matricule },
    { id: "nom", libelle: "Nom", tri: (d) => d.nom },
    { id: "conformite", libelle: "Dépôt", tri: (d) => d.depot?.conformite ?? "" },
    { id: "plages", libelle: "Plages", numerique: true },
    { id: "le", libelle: "Déposé le" },
    { id: "actions", libelle: "Actions", actions: true },
  ];

  return (
    <div data-testid="pla-dispo">
      {erreur && !fiche && <Message variante="erreur" testId="pla-dispo-erreur">{erreur}</Message>}
      <p data-testid="pla-dispo-resume">
        {depots.length - sansDepot} dépôt(s) sur {depots.length} ressource(s) · {attente} en attente d'approbation · {sansDepot} sans dépôt
      </p>
      <Tableau colonnes={colonnes} lignes={depots} legende="Dépôts de disponibilités de la période" testId="pla-dispo-depots"
        vide={<EtatVide titre="Aucune ressource dans cette unité" compact testId="pla-dispo-vide">Ajoutez des ressources dans l'onglet Ressources.</EtatVide>}
        rendre={(d) => (
          <tr key={d.ressourceId} data-testid={`pla-dispo-${d.matricule}`}>
            <td>{d.matricule}</td>
            <td>{d.nom}</td>
            <td><BadgeConformite conformite={d.depot?.conformite ?? null} testId={`pla-dispo-${d.matricule}-conformite`} /></td>
            <td className="col-numerique">{d.depot?.plages ?? 0}</td>
            <td>{d.depot ? d.depot.deposeLe.slice(0, 10) : "—"}</td>
            <td>
              <button type="button" className="secondary" data-testid={`pla-dispo-${d.matricule}-voir`} disabled={!d.depot} onClick={() => void ouvrir(d)}>
                Consulter<span className="sr-only"> le dépôt de {d.nom}</span>
              </button>
            </td>
          </tr>
        )} />

      <h3>Bassin d'un quart</h3>
      <div className="eff-barre">
        <p>
          <label htmlFor="pla-dispo-place">Place vacante</label>
          <select id="pla-dispo-place" data-testid="pla-dispo-place" value={placeId} onChange={(e) => { setPlaceId(e.target.value); setCandidats(null); }}>
            <option value="">{vacantes.length ? "Choisir une place" : "Aucune place vacante"}</option>
            {vacantes.map((v) => <option key={v.id} value={v.id}>{v.libelle}</option>)}
          </select>
        </p>
        <p className="eff-actions">
          <button type="button" className="secondary" data-testid="pla-dispo-candidats" disabled={!placeId} onClick={() => void voirCandidats()}>Voir les candidats</button>
        </p>
      </div>
      {candidats && (
        <div data-testid="pla-dispo-bassin">
          <h4>Admissibles, dans l'ordre d'attribution</h4>
          {candidats.admissibles.length === 0
            ? <EtatVide titre="Personne ne peut tenir ce quart" compact testId="pla-dispo-aucun">Les motifs d'exclusion sont listés ci-dessous.</EtatVide>
            : (
              <ol data-testid="pla-dispo-admissibles">
                {candidats.admissibles.map((c) => (
                  <li key={c.ressourceId}>
                    {c.nom} — {c.anciennete} an(s) d'ancienneté, {c.heuresPlanifiees} h déjà planifiées la même semaine
                    {c.preference && <> <Badge ton="info">Préférence exprimée</Badge></>}
                  </li>
                ))}
              </ol>
            )}
          {candidats.exclus.length > 0 && (
            <>
              <h4>Exclus, et pourquoi</h4>
              <ul data-testid="pla-dispo-exclus">
                {candidats.exclus.map((x) => <li key={x.ressourceId}><strong>{x.nom}</strong> : {x.motif}</li>)}
              </ul>
            </>
          )}
        </div>
      )}

      <h3>Attribution de fin de collecte</h3>
      <p>
        Chaque place vacante d'un quart en attribution automatisée va au premier candidat que les règles acceptent :
        la préférence exprimée d'abord, puis l'ancienneté, puis le moins d'heures planifiées.
      </p>
      <p data-testid="pla-dispo-auto" role="status">
        {!auto?.echeanceLe && "Aucune attribution automatique planifiée : elle se planifie à l'ouverture de la collecte."}
        {auto?.echeanceLe && auto.statut === "en_attente" && `Attribution automatique planifiée le ${auto.echeanceLe.slice(0, 10)}, au lendemain de la fin de collecte (${auto.finCollecte}) : aucun geste n'est requis.`}
        {auto?.echeanceLe && auto.statut === "faite" && `Attribution automatique faite à la fin de la collecte (${auto.finCollecte}).`}
        {auto?.echeanceLe && auto.statut === "annulee" && "Attribution automatique annulée : la période a été publiée avant la fin de la collecte."}
        {auto?.echeanceLe && (auto.statut === "echouee" || auto.statut === "en_cours") && `Attribution automatique : ${auto.statut === "echouee" ? "en échec, à relancer" : "en cours"}.`}
        {auto?.derniere && ` Dernière attribution le ${auto.derniere.le.slice(0, 10)} : ${auto.derniere.attribuees} place(s) attribuée(s), ${auto.derniere.restees} restée(s) vacante(s).`}
      </p>
      {periode.etat !== "CONFECTION" && (
        <p data-testid="pla-dispo-attente-confection">Disponible une fois la collecte fermée, quand la période passe en confection.</p>
      )}
      <p className="eff-actions">
        <button type="button" data-testid="pla-dispo-attribuer" disabled={periode.etat !== "CONFECTION"} onClick={() => void attribuer()}>
          Attribuer depuis les disponibilités
        </button>
      </p>
      {bilan && <Message variante="succes" testId="pla-dispo-bilan">{bilan}</Message>}

      <Tiroir ouvert={!!fiche} onFermer={() => setFiche(null)} titre={fiche ? `Dépôt de ${fiche.ligne.nom}` : "Dépôt"} testId="pla-dispo-fiche">
        {fiche && !fiche.depot && <p role="status" data-testid="pla-dispo-fiche-chargement">Chargement du dépôt…</p>}
        {fiche?.depot && (
          <>
            <p><BadgeConformite conformite={fiche.depot.conformite} testId="pla-dispo-fiche-conformite" /></p>
            <Motifs motifs={fiche.depot.motifs} titre="Motifs relevés par les règles au dépôt" testId="pla-dispo-fiche-motifs" />
            {fiche.depot.approuveLe && <p>Approuvé le {fiche.depot.approuveLe.slice(0, 10)}.</p>}
            <EditeurPlages prefixe="pla-dispo-fiche" jours={joursDe(periode.debut, periode.fin)} lignes={lignesDe(fiche.depot.plages)} onChange={() => undefined} desactive />
            {erreur && <Message variante="erreur" testId="pla-dispo-fiche-erreur">{erreur}</Message>}
            {fiche.depot.conformite === "APPROBATION_REQUISE" && (
              <p className="eff-actions">
                <button type="button" data-testid="pla-dispo-approuver" onClick={() => void approuver()}>Approuver ce dépôt</button>
              </p>
            )}
          </>
        )}
      </Tiroir>
    </div>
  );
}
