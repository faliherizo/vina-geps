/**
 * **Cycles de paie** (ADR 0076) — préparer une paie à partir des seules feuilles de temps approuvées de la période,
 * la relire, la recalculer, puis la traiter : le cycle et ses feuilles sont alors figés. Une personne approuvée sans
 * dossier de rémunération est nommée, pas payée en silence.
 */
import { useCallback, useEffect, useState, type FormEvent } from "react";
import { BadgeStatut, EnTeteEcran, EtatVide, Message, Tableau, type Colonne } from "../composants";
import { ConfirmModal, FormulaireModal } from "../Modal";
import { useApiEffectifs as useApi } from "../effectifs/api";
import { liste } from "../formeRecue";
import DetailBulletin from "./DetailBulletin";
import { argent, heures, libelleEtat, statutBadge, type Bulletin, type Cycle, type Preparation, type Personne } from "./types";

const COL_CYCLES: ReadonlyArray<Colonne<Cycle>> = [
  { id: "periode", libelle: "Période", tri: (c) => c.debut }, { id: "datePaie", libelle: "Date de paie", tri: (c) => c.datePaie },
  { id: "etat", libelle: "État" }, { id: "par", libelle: "Préparé par" }, { id: "actions", libelle: "Actions", actions: true },
];
const COL_BULLETINS: ReadonlyArray<Colonne<Bulletin>> = [
  { id: "nom", libelle: "Personne", tri: (b) => b.nom }, { id: "reg", libelle: "Heures rég.", numerique: true },
  { id: "sup", libelle: "Heures sup.", numerique: true }, { id: "brut", libelle: "Brut", numerique: true, tri: (b) => b.retenues.brut },
  { id: "retenues", libelle: "Retenues", numerique: true }, { id: "net", libelle: "Net", numerique: true },
  { id: "cout", libelle: "Coût employeur", numerique: true }, { id: "actions", libelle: "Actions", actions: true },
];

type Geste = "traiter" | "annuler";

export default function CyclesPaie() {
  const { lire, envoyer, erreur, succes, setSucces } = useApi();
  const [cycles, setCycles] = useState<Cycle[]>([]);
  const [ouvert, setOuvert] = useState<Preparation | null>(null);
  const [bulletin, setBulletin] = useState<Bulletin | null>(null);
  const [geste, setGeste] = useState<Geste | null>(null);
  const charger = useCallback(async () => setCycles(liste<Cycle>(await lire<Cycle[]>("/api/paie/cycles"))), [lire]);
  useEffect(() => { void charger(); }, [charger]);

  const ouvrir = async (c: Cycle) => {
    const d = await lire<{ cycle: Cycle; bulletins: Bulletin[] }>(`/api/paie/cycles/${c.id}`);
    if (d) setOuvert({ cycle: d.cycle, bulletins: liste<Bulletin>(d.bulletins), sansDossier: [] });
  };
  const recalculer = async (c: Cycle) => {
    const p = await envoyer<Preparation>(`/api/paie/cycles/${c.id}/recalculer`, {});
    if (p) { setOuvert(p); setSucces(`Cycle recalculé : ${p.bulletins.length} bulletin(s).`); }
  };
  const confirmer = async () => {
    if (!ouvert || !geste) return;
    const c = await envoyer<Cycle>(`/api/paie/cycles/${ouvert.cycle.id}/${geste}`, {});
    setGeste(null);
    if (!c) return;
    setSucces(geste === "traiter" ? `Paie du ${c.datePaie} traitée : les feuilles payées ne se modifient plus.` : "Cycle annulé : ses feuilles restent approuvées.");
    await charger();
    await ouvrir(c);
  };

  return (
    <div data-testid="pcy-ecran">
      <EnTeteEcran titre="Cycles de paie" testId="pcy-titre"
        description="Seules les feuilles de temps approuvées entrent dans une paie. Tant que le cycle n'est pas traité, une feuille peut encore être rappelée ou rouverte ; traité, il fige ses feuilles." />
      {erreur && <Message variante="erreur" testId="pcy-erreur">{erreur}</Message>}
      {succes && <Message variante="succes" testId="pcy-succes">{succes}</Message>}

      <section className="card" aria-labelledby="pcy-liste-h">
        <h2 id="pcy-liste-h">Cycles</h2>
        <div className="eff-actions">
          <FormulaireModal libelle="Préparer un cycle" titre="Préparer un cycle de paie" testId="pcy-preparer" size="sm">
            {(fermer) => <FormulairePeriode envoyer={envoyer} apres={async (p) => {
              fermer(); setOuvert(p); setSucces(`Cycle préparé : ${p.bulletins.length} bulletin(s) à relire.`); await charger();
            }} />}
          </FormulaireModal>
        </div>
        <Tableau colonnes={COL_CYCLES} lignes={cycles} legende="Cycles de paie, le plus récent d'abord" testId="pcy-cycles"
          vide={<EtatVide titre="Aucun cycle" compact testId="pcy-vide">Préparez le premier : choisissez la période de travail et la date de paie.</EtatVide>}
          rendre={(c) => (
            <tr key={c.id} data-testid={`pcy-cycle-${c.id}`}>
              <td>du {c.debut} au {c.fin}</td><td>{c.datePaie}</td>
              <td><BadgeStatut statut={statutBadge(c.etat)}>{libelleEtat(c.etat)}</BadgeStatut></td><td>{c.preparePar}</td>
              <td><button type="button" className="secondary" data-testid={`pcy-cycle-${c.id}-voir`} onClick={() => void ouvrir(c)}>
                Ouvrir<span className="sr-only"> le cycle payé le {c.datePaie}</span></button></td>
            </tr>
          )} />
      </section>

      {ouvert && (
        <section className="card" aria-labelledby="pcy-cycle-h" data-testid="pcy-ouvert">
          <h2 id="pcy-cycle-h">Paie du {ouvert.cycle.datePaie} — {libelleEtat(ouvert.cycle.etat)}</h2>
          <SansDossier personnes={ouvert.sansDossier} />
          {ouvert.cycle.etat === "prepare" && (
            <div className="eff-actions">
              <button type="button" className="secondary" data-testid="pcy-recalculer" onClick={() => void recalculer(ouvert.cycle)}>Recalculer</button>
              <button type="button" className="primary" data-testid="pcy-traiter" onClick={() => setGeste("traiter")}>Traiter la paie</button>
              <button type="button" className="secondary" data-testid="pcy-annuler" onClick={() => setGeste("annuler")}>Annuler le cycle</button>
            </div>
          )}
          <Tableau colonnes={COL_BULLETINS} lignes={ouvert.bulletins} legende={`Bulletins de la paie du ${ouvert.cycle.datePaie}`} testId="pcy-bulletins"
            vide={<EtatVide titre="Aucun bulletin" compact testId="pcy-bulletins-vide">Aucune feuille approuvée de cette période n'a de dossier de rémunération.</EtatVide>}
            rendre={(b) => (
              <tr key={b.id} data-testid={`pcy-bulletin-${b.ressourceId}`}>
                <td>{b.nom}</td><td className="num">{heures(b.heuresRegulieres)}</td><td className="num">{heures(b.heuresSupplementaires)}</td>
                <td className="num">{argent(b.retenues.brut)}</td><td className="num">{argent(b.totalRetenues)}</td>
                <td className="num">{argent(b.net)}</td><td className="num">{argent(b.coutEmployeur)}</td>
                <td><button type="button" className="secondary" data-testid={`pcy-bulletin-${b.ressourceId}-voir`} onClick={() => setBulletin(b)}>
                  Détail<span className="sr-only"> du bulletin de {b.nom}</span></button></td>
              </tr>
            )} />
        </section>
      )}

      <FormulaireModal libelle="Bulletin" titre={bulletin ? `Bulletin de ${bulletin.nom}` : "Bulletin"} testId="pcy-bulletin" size="md"
        ouvert={bulletin !== null} onFermer={() => setBulletin(null)}>
        {bulletin && <DetailBulletin b={bulletin} prefixe="pcy-b" />}
      </FormulaireModal>
      <ConfirmModal open={geste === "traiter"} title="Traiter la paie" confirmLabel="Traiter" testId="pcy-confirmer-traiter"
        message="La paie est recalculée une dernière fois, puis figée : ses feuilles de temps passent « payées » et ne se modifient plus. Une erreur se corrigera à la paie suivante."
        onConfirm={() => confirmer()} onCancel={() => setGeste(null)} />
      <ConfirmModal open={geste === "annuler"} title="Annuler le cycle" confirmLabel="Annuler le cycle" cancelLabel="Garder le cycle" testId="pcy-confirmer-annuler"
        message="Le cycle est abandonné ; ses feuilles restent approuvées et entreront dans une prochaine paie."
        onConfirm={() => confirmer()} onCancel={() => setGeste(null)} />
    </div>
  );
}

function SansDossier({ personnes }: { personnes: Personne[] }) {
  if (personnes.length === 0) return null;
  return (
    <Message variante="avertissement" testId="pcy-sans-dossier">
      Feuilles approuvées sans dossier de rémunération, donc non payées : {personnes.map((p) => `${p.nom} (${p.matricule})`).join(", ")}.
      Créez leur dossier dans « Dossiers de rémunération », puis recalculez.
    </Message>
  );
}

type Envoyer = ReturnType<typeof useApi>["envoyer"];

function FormulairePeriode({ envoyer, apres }: { envoyer: Envoyer; apres: (p: Preparation) => Promise<void> }) {
  const [debut, setDebut] = useState("");
  const [fin, setFin] = useState("");
  const [datePaie, setDatePaie] = useState("");
  const soumettre = async (e: FormEvent) => {
    e.preventDefault();
    const p = await envoyer<Preparation>("/api/paie/cycles", { debut, fin, datePaie });
    if (p) await apres(p);
  };
  return (
    <form onSubmit={(e) => void soumettre(e)} aria-label="Préparer un cycle de paie">
      <p><label htmlFor="pcy-debut">Début de la période de travail</label>
        <input id="pcy-debut" data-testid="pcy-debut" type="date" required value={debut} onChange={(e) => setDebut(e.target.value)} /></p>
      <p><label htmlFor="pcy-fin">Fin de la période</label>
        <input id="pcy-fin" data-testid="pcy-fin" type="date" required value={fin} onChange={(e) => setFin(e.target.value)} /></p>
      <p><label htmlFor="pcy-date-paie">Date de paie</label>
        <input id="pcy-date-paie" data-testid="pcy-date-paie" type="date" required value={datePaie} onChange={(e) => setDatePaie(e.target.value)} /></p>
      <p className="muted">La période prend les feuilles dont la semaine commence entre ces deux dates. L'année de la date de paie choisit le barème.</p>
      <p><button type="submit" className="primary" data-testid="pcy-creer">Préparer</button></p>
    </form>
  );
}
