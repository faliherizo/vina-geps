/**
 * **Suivi des communications** (CU-6 ; FX-AO-20260810, critères 9, 11, 12, 31) — partagé entre les
 * expéditeurs autorisés de l'organisme. Par alerte : combien l'ont lue, combien ont répondu, et qui a
 * pris en charge ; par destinataire : quand l'alerte lui a été envoyée, lue, et ce qu'il a répondu.
 * Le tableau se filtre (période, priorité, état, expéditeur) et s'exporte au même filtre (ST-G03).
 */
import { useCallback, useEffect, useMemo, useState, type FormEvent } from "react";
import CascadeAlerte from "./CascadeAlerte";
import RemisesEtNotes from "./RemisesEtNotes";
import ExportResultats from "../effectifs/ExportResultats";
import { BadgeStatut, EnTeteEcran, EtatVide, Message, Tableau, type Colonne } from "../composants";
import { useApiEffectifs as useApi } from "../effectifs/api";
import { liste } from "../formeRecue";
import { ETATS, PRIORITES, etatRemise, libellePriorite, quand, tonPriorite, type AlerteSuivie, type Remise } from "./types";

interface Filtre { du: string; au: string; priorite: string; etat: string; expediteur: string }
const AUCUN: Filtre = { du: "", au: "", priorite: "", etat: "", expediteur: "" };

/** La requête du filtre : seules les bornes remplies partent. */
export function parametresDe(f: Filtre): URLSearchParams {
  const p = new URLSearchParams();
  (Object.keys(f) as Array<keyof Filtre>).forEach((k) => { if (f[k].trim()) p.set(k, f[k].trim()); });
  return p;
}

const COL_ALERTES: ReadonlyArray<Colonne<AlerteSuivie>> = [
  { id: "envoi", libelle: "Envoyée", tri: (a) => a.envoyeeLe }, { id: "titre", libelle: "Alerte" },
  { id: "priorite", libelle: "Priorité" }, { id: "lues", libelle: "Lues", numerique: true },
  { id: "reponses", libelle: "Réponses", numerique: true }, { id: "prise", libelle: "Prise en charge" },
  { id: "actions", libelle: "Actions", actions: true },
];

export default function SuiviAlertes() {
  const { lire, erreur } = useApi();
  const [alertes, setAlertes] = useState<AlerteSuivie[]>([]);
  const [ouverte, setOuverte] = useState<string | null>(null);
  const [saisie, setSaisie] = useState<Filtre>(AUCUN);
  const [filtre, setFiltre] = useState<Filtre>(AUCUN);
  const parametres = useMemo(() => parametresDe(filtre), [filtre]);
  const charger = useCallback(async () => {
    const q = parametres.toString();
    setAlertes(liste<AlerteSuivie>(await lire<AlerteSuivie[]>(`/api/alertes/envoyees${q ? `?${q}` : ""}`)));
  }, [lire, parametres]);
  useEffect(() => { void charger(); }, [charger]);
  const appliquer = (e: FormEvent) => { e.preventDefault(); setOuverte(null); setFiltre(saisie); };
  const champ = (k: keyof Filtre) => ({ value: saisie[k], onChange: (e: { target: { value: string } }) => setSaisie((s) => ({ ...s, [k]: e.target.value })) });
  const fiche = alertes.find((a) => a.id === ouverte) ?? null;

  return (
    <div data-testid="als-ecran">
      <EnTeteEcran titre="Suivi des communications" testId="als-titre"
        description="Les alertes de l'organisme, les plus récentes d'abord : qui les a lues, qui a répondu, qui a pris en charge." />
      {erreur && <Message variante="erreur" testId="als-erreur">{erreur}</Message>}
      <form className="eff-barre" data-formulaire="requete" aria-label="Filtrer le suivi" onSubmit={appliquer} data-testid="als-filtres">
        <p><label htmlFor="als-du">Du</label><input id="als-du" type="date" data-testid="als-du" {...champ("du")} /></p>
        <p><label htmlFor="als-au">Au</label><input id="als-au" type="date" data-testid="als-au" {...champ("au")} /></p>
        <p><label htmlFor="als-priorite">Priorité</label>
          <select id="als-priorite" data-testid="als-priorite" {...champ("priorite")}>
            <option value="">Toutes</option>
            {PRIORITES.map((p) => <option key={p.code} value={p.code}>{p.libelle}</option>)}
          </select></p>
        <p><label htmlFor="als-etat">État</label>
          <select id="als-etat" data-testid="als-etat" {...champ("etat")}>
            <option value="">Tous</option>
            {ETATS.map((x) => <option key={x.code} value={x.code}>{x.libelle}</option>)}
          </select></p>
        <p><label htmlFor="als-expediteur">Expéditeur (nom de connexion)</label><input id="als-expediteur" data-testid="als-expediteur" {...champ("expediteur")} /></p>
        <p><button type="submit" data-testid="als-filtrer">Filtrer</button></p>
      </form>
      <div className="eff-barre">
        <p><button type="button" className="secondary" data-testid="als-actualiser" onClick={() => void charger()}>Actualiser</button></p>
        <ExportResultats chemin="/api/alertes/envoyees/export" parametres={parametres} nomFichier="suivi-alertes" prefixe="als-export" />
      </div>
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
          <RemisesEtNotes key={`remises-${fiche.id}`} fiche={fiche} />
          <CascadeAlerte key={fiche.id} alerteId={fiche.id} />
        </section>
      )}
    </div>
  );
}
