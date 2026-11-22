/**
 * **Absences et comblement** — onglet de la planification des effectifs (AO 26-015-DO, lot R5 —
 * E-GN-5, E-GN-6, E-CH-6, E-GR-3, E-GR-4 ; lot U5 : comblement automatique, avis, remplacement long terme).
 *
 * On n'y déclare pas : la déclaration a un seul lieu, Absences › Déclarer (ADR 0034). On y décide,
 * en voyant ce que l'absence coûte au comblement : les places qu'elle videra, le taux avant et
 * après. Un refus des règles s'y renverse par une **dérogation motivée**, qui attribue un code.
 */
import { useCallback, useEffect, useState } from "react";
import { objet } from "../../formeRecue";
import { BadgeStatut, EtatVide, Message, Tableau, Tiroir, type Colonne } from "../../composants";
import { heure, useApiEffectifs } from "../api";
import { libelleDe, useReferentiels } from "../planification/useReferentiels";
import type { Motif } from "../planification/types";
import ComblementAbsence from "./ComblementAbsence";
import ParametresOffres from "../offres/ParametresOffres";
import FormulaireAbsence, { type AbsenceDeclaree } from "../../absences/FormulaireAbsence";
import { useAuth } from "../../AuthContext";

type Statut = "ACCEPTEE" | "A_APPROUVER" | "REFUSEE_AUTO" | "REFUSEE" | "ANNULEE";
interface Absence {
  id: string; ressourceId: string; ressourceNom: string; uniteCode: string; codeAbsence: string; du: string; au: string; motif: string | null;
  statut: Statut; motifsRegles: Motif[]; decideePar: string | null; motifDecision: string | null; derogationCode: string | null;
}
interface Bilan { attribuees: number; offertes: number; sansCandidat: number; reservees: number }
interface Impact { places: Array<{ id: string; date: string; debut: number; fin: number }>; tauxAvant: number; tauxApres: number }

const LIBELLES: Record<Statut, string> = {
  ACCEPTEE: "Acceptée", A_APPROUVER: "À approuver", REFUSEE_AUTO: "Refusée par les règles", REFUSEE: "Refusée", ANNULEE: "Annulée",
};
/** Le statut traduit vers les tons communs (`BadgeStatut`), pour qu'une absence se lise comme ailleurs. */
const TON: Record<Statut, string> = { ACCEPTEE: "approuvee", A_APPROUVER: "en_attente", REFUSEE_AUTO: "refusee", REFUSEE: "refusee", ANNULEE: "annulee" };

const COLONNES: ReadonlyArray<Colonne<Absence>> = [
  { id: "personne", libelle: "Personne", tri: (a) => a.ressourceNom },
  { id: "code", libelle: "Code", tri: (a) => a.codeAbsence },
  { id: "periode", libelle: "Période", tri: (a) => a.du },
  { id: "statut", libelle: "Statut", tri: (a) => a.statut },
  { id: "motifs", libelle: "Motifs des règles" },
  { id: "actions", libelle: "Actions", actions: true },
];

/** Ce que le comblement automatique a fait aussitôt la décision prise (E-GN-6). */
function resumeComblement(b: Bilan | undefined): string {
  if (!b) return "";
  const parties = [
    b.attribuees ? `${b.attribuees} attribuée(s) d'office au prioritaire` : "",
    b.offertes ? `${b.offertes} offerte(s) aux admissibles avisés` : "",
    b.sansCandidat ? `${b.sansCandidat} sans candidat, à combler dans Quarts et équipages` : "",
    b.reservees ? `${b.reservees} réservée(s) au remplacement long terme` : "",
  ].filter(Boolean);
  return parties.length ? ` ; comblement automatique : ${parties.join(", ")}` : "";
}

export default function AbsencesEffectifs() {
  const { lire, envoyer, erreur, setErreur } = useApiEffectifs();
  const { refs } = useReferentiels(["codes_absence"]);
  const [absences, setAbsences] = useState<Absence[]>([]);
  const [filtre, setFiltre] = useState<"" | Statut>("A_APPROUVER");
  const [fiche, setFiche] = useState<{ absence: Absence; impact: Impact | null } | null>(null);
  const [motif, setMotif] = useState("");
  const [code, setCode] = useState("");
  const [bilan, setBilan] = useState("");
  const [declarer, setDeclarer] = useState(false);
  const [reglage, setReglage] = useState(false);
  const { apiFetch } = useAuth();

  /** Une absence déclarée pour une ressource du périmètre : la décision automatique est dite telle quelle. */
  const declaree = async (r: AbsenceDeclaree) => {
    setDeclarer(false);
    const motifs = (r.motifs ?? []).map((m) => m.message).join(" ; ");
    setBilan(r.statut === "REFUSEE_AUTO"
      ? `Absence enregistrée, mais refusée par les règles de l'organisme. Motifs : ${motifs}. Elle peut être accordée par dérogation motivée.`
      : r.statut === "A_APPROUVER" ? "Absence enregistrée ; elle attend une décision." : "Absence accordée.");
    await charger();
  };

  const charger = useCallback(async () => {
    setAbsences((await lire<Absence[]>(`/api/effectifs/absences${filtre ? `?statut=${filtre}` : ""}`)) ?? []);
  }, [lire, filtre]);
  useEffect(() => { void charger(); }, [charger]);

  // La fiche s'ouvre tout de suite, l'impact la rejoint : un tiroir qui n'apparaît qu'au retour du
  // serveur surgit après qu'on a pu fermer ou quitter — et reste ouvert par-dessus la page.
  const ouvrir = async (a: Absence) => {
    setErreur(""); setMotif(""); setCode(a.codeAbsence); setBilan("");
    setFiche({ absence: a, impact: null });
    // La ligne de la liste a pu vieillir : la fiche relit l'absence en même temps que son impact.
    const [fraiche, impact] = await Promise.all([lire<Absence>(`/api/effectifs/absences/${a.id}`), lire<Impact>(`/api/effectifs/absences/${a.id}/impact`)]);
    setFiche((f) => (f?.absence.id === a.id ? { absence: objet<Absence>(fraiche) ?? a, impact: objet<Impact>(impact) } : f));
  };
  const decider = async (decision: "APPROUVER" | "REFUSER" | "DEROGER") => {
    if (!fiche) return;
    const corps = decision === "DEROGER" ? { decision, motif, codeAbsence: code } : { decision, motif: motif || undefined };
    const r = await envoyer<Absence & { placesLiberees: number; comblement?: Bilan }>(`/api/effectifs/absences/${fiche.absence.id}/decisions`, corps);
    if (!r) return;
    setBilan(`${fiche.absence.ressourceNom} : absence ${LIBELLES[r.statut].toLowerCase()}${r.placesLiberees ? ` — ${r.placesLiberees} place(s) rendue(s) vacante(s)${resumeComblement(r.comblement)}` : ""}.`);
    setFiche(null);
    await charger();
  };

  /** Annuler une absence accordée ou en attente. Les places déjà rendues vacantes le restent : elles se comblent dans Quarts et équipages. */
  const annuler = async () => {
    if (!fiche) return;
    const r = await envoyer<Absence>(`/api/effectifs/absences/${fiche.absence.id}/annuler`, { motif: motif || undefined });
    if (!r) return;
    setBilan(`${fiche.absence.ressourceNom} : absence ${LIBELLES[r.statut].toLowerCase()}.`);
    setFiche(null);
    await charger();
  };

  const a = fiche?.absence ?? null;
  return (
    <div data-testid="pla-abs">
      <div className="eff-barre">
        <p>
          <label htmlFor="pla-abs-filtre">Statut</label>
          <select id="pla-abs-filtre" data-testid="pla-abs-filtre" value={filtre} onChange={(e) => setFiltre(e.target.value as "" | Statut)}>
            <option value="">Tous</option>
            {(Object.keys(LIBELLES) as Statut[]).map((s) => <option key={s} value={s}>{LIBELLES[s]}</option>)}
          </select>
        </p>
        <p>
          <button type="button" className="primary" data-testid="pla-abs-declarer" onClick={() => { setBilan(""); setDeclarer(true); }}>
            Déclarer une absence pour une ressource
          </button>
        </p>
        <p>
          <button type="button" className="secondary" data-testid="pla-abs-offres-regler" onClick={() => setReglage(true)}>
            Offre des quarts libérés
          </button>
        </p>
      </div>
      <p>Une absence déclarée ici l'est pour une ressource de votre périmètre ; on décide ensuite, en voyant son effet sur le comblement.</p>
      {/* AD-07 : simultanée, ou par ancienneté et à tour de rôle — le réglage de l'organisme. */}
      <Tiroir ouvert={reglage} onFermer={() => setReglage(false)} titre="Offre des quarts libérés" testId="pla-abs-offres-tiroir">
        {reglage && <ParametresOffres />}
      </Tiroir>
      <Tiroir ouvert={declarer} onFermer={() => setDeclarer(false)} titre="Déclarer une absence" testId="pla-abs-declaration">
        <FormulaireAbsence apiFetch={apiFetch} estEmploye={false} onDeclaree={(r) => void declaree(r)} />
      </Tiroir>
      {erreur && !fiche && <Message variante="erreur" testId="pla-abs-erreur">{erreur}</Message>}
      {bilan && <Message variante="succes" testId="pla-abs-bilan">{bilan}</Message>}
      <Tableau colonnes={COLONNES} lignes={absences} legende="Absences d'effectifs de votre périmètre" testId="pla-abs-table"
        vide={<EtatVide titre="Aucune absence" compact testId="pla-abs-vide">Aucune absence ne correspond à ce statut.</EtatVide>}
        rendre={(x) => (
          <tr key={x.id} data-testid={`pla-abs-${x.id}`}>
            <td>{x.ressourceNom}</td>
            <td>{libelleDe(refs.codes_absence, x.codeAbsence)}</td>
            <td>{x.du}{x.au !== x.du ? ` → ${x.au}` : ""}</td>
            <td><BadgeStatut statut={TON[x.statut]} testId={`pla-abs-${x.id}-statut`}>{LIBELLES[x.statut]}</BadgeStatut></td>
            <td>{x.motifsRegles.length ? x.motifsRegles.map((m) => m.message).join(" ; ") : "—"}</td>
            <td>
              <button type="button" className="secondary" data-testid={`pla-abs-${x.id}-ouvrir`} onClick={() => void ouvrir(x)}>
                Impact et décision<span className="sr-only"> — {x.ressourceNom}, {x.du}</span>
              </button>
            </td>
          </tr>
        )} />

      <Tiroir ouvert={!!fiche} onFermer={() => setFiche(null)} titre={a ? `Absence de ${a.ressourceNom}` : "Absence"} testId="pla-abs-fiche">
        {a && (
          <>
            <p><BadgeStatut statut={TON[a.statut]} testId="pla-abs-fiche-statut">{LIBELLES[a.statut]}</BadgeStatut> {libelleDe(refs.codes_absence, a.codeAbsence)}, {a.du}{a.au !== a.du ? ` au ${a.au}` : ""}</p>
            {a.motifsRegles.length > 0 && (
              <Message variante={a.statut === "REFUSEE_AUTO" ? "erreur" : "avertissement"} titre="Ce que les règles ont relevé" testId="pla-abs-fiche-motifs">
                <ul>{a.motifsRegles.map((m) => <li key={m.cle}>{m.message}</li>)}</ul>
              </Message>
            )}
            {fiche && !fiche.impact && <p role="status" data-testid="pla-abs-impact-chargement">Calcul de l'impact sur le comblement…</p>}
            {fiche?.impact && (
              <section aria-labelledby="pla-abs-impact-titre" data-testid="pla-abs-impact">
                <h3 id="pla-abs-impact-titre">Impact sur le comblement</h3>
                <p data-testid="pla-abs-impact-taux">Taux de comblement : {fiche.impact.tauxAvant.toLocaleString("fr-CA")} % avant, {fiche.impact.tauxApres.toLocaleString("fr-CA")} % après</p>
                {fiche.impact.places.length === 0
                  ? <p>Aucune place tenue pendant l'absence.</p>
                  : <ul>{fiche.impact.places.map((p) => <li key={p.id}>{p.date} · {heure(p.debut)}–{heure(p.fin)}</li>)}</ul>}
              </section>
            )}
            {a.statut === "ACCEPTEE" && <ComblementAbsence key={a.id} absenceId={a.id} />}
            {erreur && <Message variante="erreur" testId="pla-abs-fiche-erreur">{erreur}</Message>}
            {(a.statut === "A_APPROUVER" || a.statut === "REFUSEE_AUTO") && (
              <p>
                <label htmlFor="pla-abs-motif">{a.statut === "REFUSEE_AUTO" ? "Motif de la dérogation (10 caractères au moins)" : "Motif de la décision (facultatif)"}</label>
                <textarea id="pla-abs-motif" data-testid="pla-abs-motif" rows={2} maxLength={500} value={motif} onChange={(e) => setMotif(e.target.value)} />
              </p>
            )}
            {a.statut === "REFUSEE_AUTO" && (
              <>
                <p>
                  <label htmlFor="pla-abs-code">Code d'absence attribué</label>
                  <select id="pla-abs-code" data-testid="pla-abs-code" value={code} onChange={(e) => setCode(e.target.value)}>
                    {(refs.codes_absence ?? []).map((k) => <option key={k.code} value={k.code}>{k.libelleFr}</option>)}
                  </select>
                </p>
                <p className="eff-actions">
                  <button type="button" data-testid="pla-abs-deroger" onClick={() => void decider("DEROGER")}>Accorder par dérogation</button>
                </p>
              </>
            )}
            {a.statut === "A_APPROUVER" && (
              <p className="eff-actions">
                <button type="button" data-testid="pla-abs-approuver" onClick={() => void decider("APPROUVER")}>Approuver</button>
                <button type="button" className="secondary" data-testid="pla-abs-refuser" onClick={() => void decider("REFUSER")}>Refuser</button>
              </p>
            )}
            {(a.statut === "ACCEPTEE" || a.statut === "A_APPROUVER") && (
              <p><button type="button" className="secondary" data-testid="pla-abs-annuler" onClick={() => void annuler()}>Annuler l'absence</button></p>
            )}
          </>
        )}
      </Tiroir>
    </div>
  );
}
