/**
 * **Validation des temps** (ADR 0084) — le contrôle de la paie avant de payer : chaque feuille de la période, son état,
 * ses heures et ses anomalies. Valider ne réapprouve pas : c'est le regard de la paie, distinct de l'approbation du
 * gestionnaire. Une feuille fautive se retourne à la personne, avec ce qu'il faut corriger.
 */
import { useCallback, useEffect, useState, type FormEvent } from "react";
import { BadgeStatut, EnTeteEcran, EtatVide, Message, Tableau, type Colonne } from "../composants";
import { FormulaireModal } from "../Modal";
import { useApiEffectifs as useApi } from "../effectifs/api";
import { objet } from "../formeRecue";
import { ANOMALIES, heures, libelleAnomalie, libelleEtatFeuille, type AnomalieTemps, type Controle, type LigneControle, type Validation } from "./types";

const COL: ReadonlyArray<Colonne<LigneControle>> = [
  { id: "nom", libelle: "Personne", tri: (l) => l.nom }, { id: "semaine", libelle: "Semaine du", tri: (l) => l.debut },
  { id: "etat", libelle: "État" }, { id: "reg", libelle: "Heures rég.", numerique: true }, { id: "sup", libelle: "Heures sup.", numerique: true },
  { id: "anomalies", libelle: "Anomalies" }, { id: "validation", libelle: "Validation" }, { id: "actions", libelle: "Actions", actions: true },
];

const iso = (d: Date) => d.toISOString().slice(0, 10);

/** Par défaut, les deux dernières semaines complètes ou en cours : du lundi d'il y a deux semaines à dimanche. */
export function periodeParDefaut(aujourdhui: Date): { debut: string; fin: string } {
  const lundi = new Date(aujourdhui);
  lundi.setUTCDate(lundi.getUTCDate() - ((lundi.getUTCDay() + 6) % 7) - 7);
  const dimanche = new Date(lundi);
  dimanche.setUTCDate(lundi.getUTCDate() + 13);
  return { debut: iso(lundi), fin: iso(dimanche) };
}

const BADGE_ETAT: Record<LigneControle["etat"], string> = { brouillon: "brouillon", soumise: "submitted", retournee: "pending", approuvee: "approuvee", payee: "paid" };

export default function ValidationTemps() {
  const { lire, envoyer, erreur, succes, setSucces } = useApi();
  const defaut = periodeParDefaut(new Date());
  const [debut, setDebut] = useState(defaut.debut);
  const [fin, setFin] = useState(defaut.fin);
  const [controle, setControle] = useState<Controle | null>(null);
  const [aRetourner, setARetourner] = useState<LigneControle | null>(null);
  const charger = useCallback(async () => {
    setControle(objet<Controle>(await lire<Controle>(`/api/paie/temps?debut=${debut}&fin=${fin}`)));
  }, [lire, debut, fin]);
  useEffect(() => { void charger(); }, [charger]);
  const feuilles = controle?.feuilles ?? [];
  const sansAnomalie = feuilles.filter((l) => l.validable && l.anomalies.length === 0 && !l.validee).length;

  const valider = async (l: LigneControle) => {
    if (await envoyer<Validation>(`/api/paie/temps/${l.feuilleId}/valider`, {})) {
      setSucces(`Feuille de ${l.nom} (semaine du ${l.debut}) validée pour la paie.`);
      await charger();
    }
  };
  const validerLot = async () => {
    const r = await envoyer<{ validees: Validation[] }>("/api/paie/temps/valider-sans-anomalie", { debut, fin });
    if (r) { setSucces(`${r.validees.length} feuille(s) sans anomalie validée(s) pour la paie.`); await charger(); }
  };

  return (
    <div data-testid="pvt-ecran">
      <EnTeteEcran titre="Validation des temps" testId="pvt-titre"
        description="Avant de payer, la paie relit les feuilles approuvées : elle les valide, ou les retourne à la personne avec ce qu'il faut corriger." />
      {erreur && <Message variante="erreur" testId="pvt-erreur">{erreur}</Message>}
      {succes && <Message variante="succes" testId="pvt-succes">{succes}</Message>}
      <section className="card" aria-labelledby="pvt-periode-h">
        <h2 id="pvt-periode-h">Période</h2>
        <form className="eff-actions" aria-label="Période à contrôler" onSubmit={(e: FormEvent) => { e.preventDefault(); void charger(); }}>
          <span><label htmlFor="pvt-debut">Du</label>{" "}
            <input id="pvt-debut" data-testid="pvt-debut" type="date" required value={debut} onChange={(e) => setDebut(e.target.value)} /></span>
          <span><label htmlFor="pvt-fin">Au</label>{" "}
            <input id="pvt-fin" data-testid="pvt-fin" type="date" required value={fin} onChange={(e) => setFin(e.target.value)} /></span>
          <button type="submit" className="secondary" data-testid="pvt-afficher">Afficher</button>
          <button type="button" className="primary" data-testid="pvt-valider-lot" disabled={sansAnomalie === 0} onClick={() => void validerLot()}>
            Valider les {sansAnomalie} feuille(s) sans anomalie</button>
        </form>
        <p className="muted" data-testid="pvt-bilan" role="status">
          {feuilles.length} feuille(s) ; {feuilles.filter((l) => l.validee).length} validée(s) ; {feuilles.filter((l) => l.anomalies.length > 0).length} avec
          anomalie ; {(controle?.sansFeuille ?? []).length} personne(s) sans feuille.</p>
      </section>
      <section className="card" aria-labelledby="pvt-feuilles-h">
        <h2 id="pvt-feuilles-h">Feuilles de la période</h2>
        <Tableau colonnes={COL} lignes={feuilles} legende={`Feuilles de temps du ${debut} au ${fin}`} testId="pvt-feuilles"
          vide={<EtatVide titre="Aucune feuille" compact testId="pvt-vide">Aucune feuille de temps dont la semaine commence dans cette période.</EtatVide>}
          rendre={(l) => (
            <tr key={l.feuilleId} data-testid={`pvt-${l.feuilleId}`}>
              <td>{l.nom} <span className="muted">({l.matricule})</span></td><td>{l.debut}</td>
              <td><BadgeStatut statut={BADGE_ETAT[l.etat]}>{libelleEtatFeuille(l.etat)}</BadgeStatut></td>
              <td className="num">{heures(l.heuresRegulieres)}</td><td className="num">{heures(l.heuresSupplementaires)}</td>
              <td><Anomalies codes={l.anomalies} detail={l.incalculable} /></td>
              <td data-testid={`pvt-${l.feuilleId}-validation`}>{etatValidation(l)}</td>
              <td><div className="eff-actions">
                {l.validable && !l.validee && (
                  <button type="button" className="primary" data-testid={`pvt-${l.feuilleId}-valider`} onClick={() => void valider(l)}>
                    Valider<span className="sr-only"> la feuille de {l.nom}, semaine du {l.debut}</span></button>)}
                {l.etat === "approuvee" && (
                  <button type="button" className="secondary" data-testid={`pvt-${l.feuilleId}-retourner`} onClick={() => setARetourner(l)}>
                    Retourner<span className="sr-only"> la feuille de {l.nom}, semaine du {l.debut}</span></button>)}
              </div></td>
            </tr>
          )} />
      </section>
      {(controle?.sansFeuille ?? []).length > 0 && (
        <section className="card" aria-labelledby="pvt-sans-h" data-testid="pvt-sans-feuille">
          <h2 id="pvt-sans-h">Personnes actives sans feuille dans la période</h2>
          <ul>{(controle?.sansFeuille ?? []).map((p) => <li key={p.ressourceId}>{p.nom} ({p.matricule})</li>)}</ul>
        </section>
      )}
      <FormulaireModal libelle="Retourner" titre={aRetourner ? `Retourner la feuille de ${aRetourner.nom}` : "Retourner"} testId="pvt-retour" size="sm"
        ouvert={aRetourner !== null} onFermer={() => setARetourner(null)}>
        {aRetourner && <FormulaireRetour ligne={aRetourner} envoyer={envoyer} erreur={erreur}
          apres={async () => { setSucces(`Feuille de ${aRetourner.nom} retournée : elle revient à la personne, avec votre motif.`); setARetourner(null); await charger(); }} />}
      </FormulaireModal>
    </div>
  );
}

function etatValidation(l: LigneControle): string {
  if (l.validee) return `Validée par ${l.validation?.acteur ?? "—"}`;
  if (l.validation?.decision === "retournee") return `Retournée : ${l.validation.motif ?? ""}`;
  return "—";
}

/** Une anomalie se lit par son texte, et une bloquante porte en plus un signe : la couleur ne dit rien seule (1.4.1). */
function Anomalies({ codes, detail }: { codes: string[]; detail: string | null }) {
  if (codes.length === 0) return <span className="muted">Aucune</span>;
  return (
    <ul className="liste-compacte">
      {codes.map((c) => (
        <li key={c} data-testid={`pvt-anomalie-${c}`}>
          {ANOMALIES[c as AnomalieTemps]?.bloquante ? <span aria-hidden="true">⛔ </span> : <span aria-hidden="true">⚠️ </span>}
          {libelleAnomalie(c)}{c === "incalculable" && detail ? ` — ${detail}` : ""}
        </li>
      ))}
    </ul>
  );
}

type Envoyer = ReturnType<typeof useApi>["envoyer"];

function FormulaireRetour({ ligne, envoyer, erreur, apres }: { ligne: LigneControle; envoyer: Envoyer; erreur: string | null; apres: () => Promise<void> }) {
  const [motif, setMotif] = useState("");
  const soumettre = async (e: FormEvent) => {
    e.preventDefault();
    if (await envoyer<Validation>(`/api/paie/temps/${ligne.feuilleId}/retourner`, { motif })) await apres();
  };
  return (
    <form onSubmit={(e) => void soumettre(e)} aria-label={`Retourner la feuille de ${ligne.nom}`}>
      {erreur && <Message variante="erreur" testId="pvt-retour-erreur">{erreur}</Message>}
      <p><label htmlFor="pvt-motif">Ce qu'il faut corriger</label>
        <textarea id="pvt-motif" data-testid="pvt-motif" required maxLength={500} rows={3} value={motif} onChange={(e) => setMotif(e.target.value)} /></p>
      <p className="muted">La feuille revient à {ligne.nom}, retournée. Une fois corrigée, resoumise et réapprouvée, elle se valide de nouveau.</p>
      <p><button type="submit" className="primary" data-testid="pvt-retour-confirmer">Retourner la feuille</button></p>
    </form>
  );
}
