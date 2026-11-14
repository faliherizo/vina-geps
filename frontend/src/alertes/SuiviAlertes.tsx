/**
 * **Suivi des communications** (CU-6 ; FX-AO-20260810, critères 9, 11, 12, 31) — partagé entre les
 * expéditeurs autorisés de l'organisme. Par alerte : combien l'ont lue, combien ont répondu, et qui a
 * pris en charge ; par destinataire : quand l'alerte lui a été envoyée, lue, et ce qu'il a répondu.
 */
import { useCallback, useEffect, useState } from "react";
import { BadgeStatut, EnTeteEcran, EtatVide, Message, Tableau, type Colonne } from "../composants";
import { useApiEffectifs as useApi } from "../effectifs/api";
import { liste } from "../formeRecue";
import { etatRemise, libellePriorite, quand, tonPriorite, type AlerteSuivie, type Remise } from "./types";

const COL_ALERTES: ReadonlyArray<Colonne<AlerteSuivie>> = [
  { id: "envoi", libelle: "Envoyée", tri: (a) => a.envoyeeLe }, { id: "titre", libelle: "Alerte" },
  { id: "priorite", libelle: "Priorité" }, { id: "lues", libelle: "Lues", numerique: true },
  { id: "reponses", libelle: "Réponses", numerique: true }, { id: "prise", libelle: "Prise en charge" },
  { id: "actions", libelle: "Actions", actions: true },
];
const COL_REMISES: ReadonlyArray<Colonne<Remise>> = [
  { id: "nom", libelle: "Destinataire", tri: (r) => r.nom }, { id: "lue", libelle: "Lue le" },
  { id: "etat", libelle: "État" }, { id: "repondu", libelle: "Répondu le" },
];

export default function SuiviAlertes() {
  const { lire, erreur } = useApi();
  const [alertes, setAlertes] = useState<AlerteSuivie[]>([]);
  const [ouverte, setOuverte] = useState<string | null>(null);
  const charger = useCallback(async () => setAlertes(liste<AlerteSuivie>(await lire<AlerteSuivie[]>("/api/alertes/envoyees"))), [lire]);
  useEffect(() => { void charger(); }, [charger]);
  const fiche = alertes.find((a) => a.id === ouverte) ?? null;

  return (
    <div data-testid="als-ecran">
      <EnTeteEcran titre="Suivi des communications" testId="als-titre"
        description="Les alertes de l'organisme, les plus récentes d'abord : qui les a lues, qui a répondu, qui a pris en charge." />
      {erreur && <Message variante="erreur" testId="als-erreur">{erreur}</Message>}
      <p className="eff-actions"><button type="button" className="secondary" data-testid="als-actualiser" onClick={() => void charger()}>Actualiser</button></p>
      <Tableau colonnes={COL_ALERTES} lignes={alertes} legende="Alertes envoyées" testId="als-table"
        vide={<EtatVide titre="Aucune alerte envoyée" compact testId="als-vide">Les alertes envoyées depuis « Nouvelle alerte » paraissent ici.</EtatVide>}
        rendre={(a) => (
          <tr key={a.id} data-testid={`als-alerte-${a.id}`}>
            <td>{quand(a.envoyeeLe)}</td><td>{a.titre}</td>
            <td><BadgeStatut statut={tonPriorite(a.priorite)}>{libellePriorite(a.priorite)}</BadgeStatut></td>
            <td className="num">{a.bilan.lues} / {a.bilan.destinataires}</td>
            <td className="num">{a.bilan.repondues}</td>
            <td>{a.bilan.priseEnChargePar ? `${a.bilan.priseEnChargePar}, ${quand(a.bilan.priseEnChargeLe)}` : "Personne encore"}</td>
            <td><button type="button" className="secondary" data-testid={`als-alerte-${a.id}-detail`} aria-expanded={ouverte === a.id}
              onClick={() => setOuverte(ouverte === a.id ? null : a.id)}>Détail<span className="sr-only"> de l'alerte {a.titre}</span></button></td>
          </tr>
        )} />
      {fiche && (
        <section className="card tdb-panneau" aria-labelledby="als-fiche-h" data-testid="als-fiche">
          <h2 id="als-fiche-h">{fiche.titre}</h2>
          <p>{fiche.corps}</p>
          <Tableau colonnes={COL_REMISES} lignes={fiche.remises} legende={`Destinataires de l'alerte ${fiche.titre}`} legendeVisible testId="als-remises"
            rendre={(r) => (
              <tr key={r.personId} data-testid={`als-remise-${r.personId}`}>
                <td>{r.nom}</td><td>{quand(r.lueLe)}</td><td>{etatRemise(r, fiche.reponses)}</td><td>{quand(r.reponduLe)}</td>
              </tr>
            )} />
        </section>
      )}
    </div>
  );
}
