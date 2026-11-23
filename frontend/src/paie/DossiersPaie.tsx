/**
 * **Dossiers de rémunération** (ADR 0076) — pour chaque personne des effectifs : comment elle est payée, à quelle
 * fréquence, à quel taux, et les montants personnels qu'elle a déclarés (TP-1015.3-V, TD1). Sans dossier, ses
 * feuilles approuvées ne sont pas payées : la liste montre aussi qui en manque.
 */
import { useCallback, useEffect, useState, type FormEvent } from "react";
import { BadgeStatut, EnTeteEcran, EtatVide, Message, Tableau, type Colonne } from "../composants";
import { FormulaireModal } from "../Modal";
import { useApiEffectifs as useApi } from "../effectifs/api";
import { liste } from "../formeRecue";
import { argent, FREQUENCES, libelleFrequence, MODES, type Dossier, type Frequence, type Mode, type PersonneDossier } from "./types";

const COL: ReadonlyArray<Colonne<PersonneDossier>> = [
  { id: "nom", libelle: "Personne", tri: (p) => p.nom }, { id: "matricule", libelle: "Matricule" }, { id: "titre", libelle: "Titre d'emploi" },
  { id: "mode", libelle: "Rémunération" }, { id: "frequence", libelle: "Fréquence" }, { id: "actions", libelle: "Actions", actions: true },
];

export default function DossiersPaie() {
  const { lire, envoyer, erreur, succes, setSucces } = useApi();
  const [personnes, setPersonnes] = useState<PersonneDossier[]>([]);
  const [choisie, setChoisie] = useState<PersonneDossier | null>(null);
  const charger = useCallback(async () => setPersonnes(liste<PersonneDossier>(await lire<PersonneDossier[]>("/api/paie/dossiers"))), [lire]);
  useEffect(() => { void charger(); }, [charger]);
  const sansDossier = personnes.filter((p) => !p.dossier).length;

  return (
    <div data-testid="pdo-ecran">
      <EnTeteEcran titre="Dossiers de rémunération" testId="pdo-titre"
        description="Le taux ou le salaire, la fréquence de paie et les montants personnels déclarés de chacun. Sans dossier, une feuille approuvée n'est pas payée." />
      {erreur && <Message variante="erreur" testId="pdo-erreur">{erreur}</Message>}
      {succes && <Message variante="succes" testId="pdo-succes">{succes}</Message>}
      {sansDossier > 0 && <Message variante="avertissement" testId="pdo-manquants">{sansDossier} personne(s) sans dossier de rémunération.</Message>}
      <section className="card" aria-labelledby="pdo-h">
        <h2 id="pdo-h">Personnes des effectifs</h2>
        <Tableau colonnes={COL} lignes={personnes} legende="Personnes des effectifs et leur dossier de rémunération" testId="pdo-liste"
          vide={<EtatVide titre="Aucune personne" compact testId="pdo-vide">Les ressources actives des effectifs apparaissent ici.</EtatVide>}
          rendre={(p) => (
            <tr key={p.ressourceId} data-testid={`pdo-${p.ressourceId}`}>
              <td>{p.nom}</td><td>{p.matricule}</td><td>{p.titreEmploi ?? "—"}</td>
              <td>{p.dossier ? remuneration(p.dossier) : <BadgeStatut statut="en_attente">Sans dossier</BadgeStatut>}</td>
              <td>{p.dossier ? libelleFrequence(p.dossier.frequence) : "—"}</td>
              <td><button type="button" className="secondary" data-testid={`pdo-${p.ressourceId}-modifier`} onClick={() => setChoisie(p)}>
                {p.dossier ? "Modifier" : "Créer"}<span className="sr-only"> le dossier de {p.nom}</span></button></td>
            </tr>
          )} />
      </section>
      <FormulaireModal libelle="Dossier" titre={choisie ? `Dossier de rémunération — ${choisie.nom}` : "Dossier"} testId="pdo-dossier" size="md"
        ouvert={choisie !== null} onFermer={() => setChoisie(null)}>
        {choisie && <FormulaireDossier personne={choisie} envoyer={envoyer}
          apres={async () => { setSucces(`Dossier de ${choisie.nom} enregistré.`); setChoisie(null); await charger(); }} />}
      </FormulaireModal>
    </div>
  );
}

/** À l'heure : le taux hors échelle s'il est saisi, sinon l'échelon de l'échelle salariale (ADR 0084). */
function remuneration(d: Dossier): string {
  if (d.mode === "salaire") return `${argent(d.salaireAnnuel)} par année`;
  return d.tauxHoraire != null ? `${argent(d.tauxHoraire)} l'heure (hors échelle)` : `Échelle, échelon ${d.echelon ?? "—"}`;
}

type Envoyer = ReturnType<typeof useApi>["envoyer"];
const texte = (v: number | null | undefined) => (v == null ? "" : String(v));

function FormulaireDossier({ personne, envoyer, apres }: { personne: PersonneDossier; envoyer: Envoyer; apres: () => Promise<void> }) {
  const d = personne.dossier;
  const [mode, setMode] = useState<Mode>(d?.mode ?? "horaire");
  const [frequence, setFrequence] = useState<Frequence>(d?.frequence ?? "aux-deux-semaines");
  const [tauxHoraire, setTauxHoraire] = useState(texte(d?.tauxHoraire));
  const [salaireAnnuel, setSalaireAnnuel] = useState(texte(d?.salaireAnnuel));
  const [echelon, setEchelon] = useState(texte(d?.echelon));
  const [federal, setFederal] = useState(texte(d?.montantPersonnelFederal));
  const [quebec, setQuebec] = useState(texte(d?.montantPersonnelQuebec));
  const soumettre = async (e: FormEvent) => {
    e.preventDefault();
    const corps = { mode, frequence, tauxHoraire, salaireAnnuel, montantPersonnelFederal: federal, montantPersonnelQuebec: quebec,
      echelon: echelon === "" ? null : Number(echelon) };
    if (await envoyer<Dossier>(`/api/paie/dossiers/${personne.ressourceId}`, corps, "PUT")) await apres();
  };
  return (
    <form onSubmit={(e) => void soumettre(e)} aria-label={`Dossier de rémunération de ${personne.nom}`}>
      <p><label htmlFor="pdo-mode">Rémunération</label>
        <select id="pdo-mode" data-testid="pdo-mode" value={mode} onChange={(e) => setMode(e.target.value as Mode)}>
          {MODES.map((m) => <option key={m.code} value={m.code}>{m.libelle}</option>)}
        </select></p>
      <p><label htmlFor="pdo-frequence">Fréquence de paie</label>
        <select id="pdo-frequence" data-testid="pdo-frequence" value={frequence} onChange={(e) => setFrequence(e.target.value as Frequence)}>
          {FREQUENCES.map((f) => <option key={f.code} value={f.code}>{f.libelle}</option>)}
        </select></p>
      <p><label htmlFor="pdo-echelon">Échelon dans l'échelle salariale{personne.titreEmploi ? ` (${personne.titreEmploi})` : ""}</label>
        <input id="pdo-echelon" data-testid="pdo-echelon" type="number" min="1" max="30" step="1" inputMode="numeric"
          aria-describedby="pdo-taux-aide" value={echelon} onChange={(e) => setEchelon(e.target.value)} /></p>
      <p><label htmlFor="pdo-taux">Taux horaire hors échelle ($){mode === "salaire" ? " — paie les heures supplémentaires" : ""}</label>
        <input id="pdo-taux" data-testid="pdo-taux" type="number" min="0" step="0.01" inputMode="decimal"
          required={mode === "horaire" && echelon === ""} aria-describedby="pdo-taux-aide"
          value={tauxHoraire} onChange={(e) => setTauxHoraire(e.target.value)} /></p>
      <p id="pdo-taux-aide" className="muted">À l'heure, un échelon suffit : le taux vient alors des règles « Taux de base ». Un taux saisi ici l'emporte sur l'échelle.</p>
      {mode === "salaire" && (
        <p><label htmlFor="pdo-salaire">Salaire annuel ($)</label>
          <input id="pdo-salaire" data-testid="pdo-salaire" type="number" min="0" step="0.01" inputMode="decimal" required
            value={salaireAnnuel} onChange={(e) => setSalaireAnnuel(e.target.value)} /></p>
      )}
      <p><label htmlFor="pdo-quebec">Montant personnel déclaré au Québec ($, TP-1015.3-V)</label>
        <input id="pdo-quebec" data-testid="pdo-quebec" type="number" min="0" step="1" inputMode="decimal" value={quebec}
          aria-describedby="pdo-montants-aide" onChange={(e) => setQuebec(e.target.value)} /></p>
      <p><label htmlFor="pdo-federal">Montant personnel déclaré au fédéral ($, TD1)</label>
        <input id="pdo-federal" data-testid="pdo-federal" type="number" min="0" step="1" inputMode="decimal" value={federal}
          aria-describedby="pdo-montants-aide" onChange={(e) => setFederal(e.target.value)} /></p>
      <p id="pdo-montants-aide" className="muted">Laissés vides, ce sont les montants personnels de base de l'année.</p>
      <p><button type="submit" className="primary" data-testid="pdo-enregistrer">Enregistrer le dossier</button></p>
    </form>
  );
}
