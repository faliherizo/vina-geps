/**
 * **Échanges** — onglet de la planification des effectifs (AO 26-015-DO, lot R6 — E-GR-7, E-GR-12).
 *
 * Le planificateur voit les échanges de son périmètre et tranche ceux que les règles veulent
 * validés (un échange entre périodes « restreint », par exemple) : valider applique l'échange,
 * rejeter le clôt. La validation rejuge l'échange sur l'horaire du moment.
 */
import { useCallback, useEffect, useState } from "react";
import { objet } from "../../formeRecue";
import { BadgeStatut, EtatVide, Message, Tableau, Tiroir, type Colonne } from "../../composants";
import { heure, useApiEffectifs } from "../api";
import ProposerPourAutrui from "./ProposerPourAutrui";
import { FormulaireModal } from "../../Modal";
import { LIBELLE_STATUT, TON_STATUT, type Echange, type Quart, type StatutEchange } from "./types";

const quart = (q: Quart) => `${q.date} · ${heure(q.debut)}–${heure(q.fin)}`;

const COLONNES: ReadonlyArray<Colonne<Echange>> = [
  { id: "parties", libelle: "Entre", tri: (x) => x.nomA },
  { id: "cede", libelle: "Quart cédé", tri: (x) => x.quartCede.date },
  { id: "voulu", libelle: "Quart en retour", tri: (x) => x.quartVoulu.date },
  { id: "statut", libelle: "Statut", tri: (x) => x.statut },
  { id: "motifs", libelle: "Ce que les règles ont relevé" },
  { id: "actions", libelle: "Actions", actions: true },
];

export default function EchangesPlanification() {
  const { lire, envoyer, erreur, setErreur } = useApiEffectifs();
  const [filtre, setFiltre] = useState<"" | StatutEchange>("A_VALIDER");
  const [echanges, setEchanges] = useState<Echange[]>([]);
  const [fiche, setFiche] = useState<Echange | null>(null);
  const [motif, setMotif] = useState("");
  const [bilan, setBilan] = useState("");

  const charger = useCallback(async () => {
    setEchanges((await lire<Echange[]>(`/api/effectifs/echanges${filtre ? `?statut=${filtre}` : ""}`)) ?? []);
  }, [lire, filtre]);
  useEffect(() => { void charger(); }, [charger]);

  /** La fiche s'ouvre au geste et se relit : l'échange a pu être accepté ou annulé depuis l'affichage de la liste. */
  const ouvrir = async (x: Echange) => {
    setErreur(""); setMotif(""); setFiche(x);
    const frais = objet<Echange>(await lire<Echange>(`/api/effectifs/echanges/${x.id}`));
    if (frais) setFiche((f) => (f?.id === x.id ? frais : f));
  };

  const trancher = async (g: "valider" | "rejeter") => {
    if (!fiche) return;
    const r = await envoyer<Echange>(`/api/effectifs/echanges/${fiche.id}/${g}`, { motif: motif || undefined });
    if (!r) return;
    setBilan(`Échange entre ${r.nomA} et ${r.nomB} : ${LIBELLE_STATUT[r.statut].toLowerCase()}.`);
    setFiche(null);
    await charger();
  };

  return (
    <div data-testid="pla-ech">
      {/* Proposer **crée** un échange : il vit dans un dialogue, et non replié au-dessus de la
          liste qu'il vient allonger. */}
      <div className="eff-actions">
        <FormulaireModal libelle="Proposer un échange pour une personne" titre="Proposer un échange" testId="pla-ech-pour" size="md">
          {(fermer) => (
            <ProposerPourAutrui onPropose={(x) => { fermer(); setBilan(`Échange proposé pour ${x.nomA} avec ${x.nomB} : ${x.nomB} doit l'accepter.`); void charger(); }} />
          )}
        </FormulaireModal>
      </div>
      <div className="eff-barre">
        <p>
          <label htmlFor="pla-ech-filtre">Statut</label>
          <select id="pla-ech-filtre" data-testid="pla-ech-filtre" value={filtre} onChange={(e) => setFiltre(e.target.value as "" | StatutEchange)}>
            <option value="">Tous</option>
            {(Object.keys(LIBELLE_STATUT) as StatutEchange[]).map((s) => <option key={s} value={s}>{LIBELLE_STATUT[s]}</option>)}
          </select>
        </p>
      </div>
      <p>La politique d'échange entre périodes (autorisé, restreint, interdit) se règle dans Paramétrage des effectifs › Règles, contexte « Échange », fait « Échange dans la même période ».</p>
      {erreur && !fiche && <Message variante="erreur" testId="pla-ech-erreur">{erreur}</Message>}
      {bilan && <Message variante="succes" testId="pla-ech-bilan">{bilan}</Message>}
      <Tableau colonnes={COLONNES} lignes={echanges} legende="Échanges de quarts de votre périmètre" testId="pla-ech-table"
        vide={<EtatVide titre="Aucun échange" compact testId="pla-ech-vide">Aucun échange ne correspond à ce statut.</EtatVide>}
        rendre={(x) => (
          <tr key={x.id} data-testid={`pla-ech-${x.id}`}>
            <td>{x.nomA} → {x.nomB}</td>
            <td>{quart(x.quartCede)}</td>
            <td>{quart(x.quartVoulu)}</td>
            <td><BadgeStatut statut={TON_STATUT[x.statut]} testId={`pla-ech-${x.id}-statut`}>{LIBELLE_STATUT[x.statut]}</BadgeStatut></td>
            <td>{x.motifsRegles.length ? x.motifsRegles.map((m) => m.message).join(" ; ") : "—"}</td>
            <td>
              {x.statut === "A_VALIDER" && (
                <button type="button" className="secondary" data-testid={`pla-ech-${x.id}-trancher`} onClick={() => void ouvrir(x)}>
                  Trancher<span className="sr-only"> l'échange entre {x.nomA} et {x.nomB}</span>
                </button>
              )}
            </td>
          </tr>
        )} />

      <Tiroir ouvert={!!fiche} onFermer={() => setFiche(null)} titre={fiche ? `Échange entre ${fiche.nomA} et ${fiche.nomB}` : "Échange"} testId="pla-ech-fiche">
        {fiche && (
          <>
            <p>{fiche.nomA} cède le {quart(fiche.quartCede)} et reçoit le {quart(fiche.quartVoulu)} de {fiche.nomB}.</p>
            {fiche.motifsRegles.length > 0 && (
              <Message variante="avertissement" titre="Ce que les règles ont relevé" testId="pla-ech-fiche-motifs">
                <ul>{fiche.motifsRegles.map((m, i) => <li key={`${m.cle}-${i}`}>{m.message}</li>)}</ul>
              </Message>
            )}
            <p>
              <label htmlFor="pla-ech-motif">Motif (facultatif)</label>
              <textarea id="pla-ech-motif" data-testid="pla-ech-motif" rows={2} maxLength={500} value={motif} onChange={(e) => setMotif(e.target.value)} />
            </p>
            {erreur && <Message variante="erreur" testId="pla-ech-fiche-erreur">{erreur}</Message>}
            <p className="eff-actions">
              <button type="button" data-testid="pla-ech-valider" onClick={() => void trancher("valider")}>Valider et appliquer</button>
              <button type="button" className="secondary" data-testid="pla-ech-rejeter" onClick={() => void trancher("rejeter")}>Rejeter</button>
            </p>
          </>
        )}
      </Tiroir>
    </div>
  );
}
