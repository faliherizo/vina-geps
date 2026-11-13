/**
 * **Mes absences** — Mon espace, module `effectifs_absences` (AO 26-015-DO, lot R12, décision D-12).
 *
 * L'employé déclare son absence dans **le** formulaire unique des absences (ADR 0034 : un seul
 * formulaire, pas un second), en mode libre-service : la personne vient de la session. Il voit ses
 * absences d'effectifs, la décision automatique et ses motifs, et annule celle qui n'est pas encore
 * passée ou qui attend une décision.
 */
import { useCallback, useEffect, useState } from "react";
import { BadgeStatut, type Colonne, EnTeteEcran, EtatVide, Message, Tableau, Tiroir } from "./composants";
import { useAuth } from "./AuthContext";
import FormulaireAbsence from "./absences/FormulaireAbsence";
import { liste, objet } from "./formeRecue";
import "./effectifs/effectifs.css";
import PageMaquette from "./composants/PageMaquette";

interface Absence {
  id: string; codeAbsence: string; du: string; au: string; statut: "ACCEPTEE" | "A_APPROUVER" | "REFUSEE_AUTO" | "REFUSEE" | "ANNULEE";
  motifsRegles: Array<{ message: string }>; motifDecision: string | null;
}

const LIBELLE: Record<Absence["statut"], string> = { ACCEPTEE: "Acceptée", A_APPROUVER: "À approuver", REFUSEE_AUTO: "Refusée par les règles", REFUSEE: "Refusée", ANNULEE: "Annulée" };
const TON: Record<Absence["statut"], string> = { ACCEPTEE: "approuvee", A_APPROUVER: "en_attente", REFUSEE_AUTO: "refusee", REFUSEE: "refusee", ANNULEE: "annule" };
const COLONNES: ReadonlyArray<Colonne<Absence>> = [
  { id: "periode", libelle: "Période", tri: (a) => a.du }, { id: "code", libelle: "Code", tri: (a) => a.codeAbsence },
  { id: "statut", libelle: "Statut", tri: (a) => a.statut }, { id: "motifs", libelle: "Motifs" }, { id: "actions", libelle: "Actions", actions: true },
];

export default function MesAbsences() {
  const { apiFetch } = useAuth();
  const [absences, setAbsences] = useState<Absence[] | null>(null);
  const [info, setInfo] = useState("");
  const [erreur, setErreur] = useState("");
  const [declarer, setDeclarer] = useState(false);

  const charger = useCallback(async () => {
    const r = await apiFetch("/api/effectifs/absences");
    setAbsences(r.ok ? liste<Absence>(await r.json()) : []);
  }, [apiFetch]);
  useEffect(() => { void charger(); }, [charger]);

  const annuler = async (a: Absence) => {
    setInfo(""); setErreur("");
    const r = await apiFetch(`/api/effectifs/absences/${a.id}/annuler`, { method: "POST", headers: { "Content-Type": "application/json" }, body: "{}" });
    const j = await r.json().catch(() => ({}));
    if (!r.ok) { setErreur(j.errors?.join(", ") || `Erreur ${r.status}`); return; }
    setInfo(`Absence du ${a.du} au ${a.au} annulée.`);
    await charger();
  };

  const aujourdhui = new Date().toISOString().slice(0, 10);

  return (
    <PageMaquette module="Urgences-santé" contexte="Mon espace • absences">
      <EnTeteEcran titre="Mes absences" description="Déclarez une absence, suivez la décision et annulez-la au besoin." testId="mab-titre" />
      {erreur && <Message variante="erreur" testId="mab-erreur">{erreur}</Message>}
      {info && <Message variante="succes" testId="mab-info">{info}</Message>}
      {/* **Le même formulaire, dans le même conteneur que la page « Absences »** : un tiroir.
          Ici, il s'ouvrait en ligne par un bouton à bascule — deux présentations du même geste
          selon la porte par laquelle on entrait, et un formulaire qui repoussait la liste qu'il
          venait compléter. */}
      <p><button type="button" data-testid="mab-declarer" onClick={() => setDeclarer(true)}>Déclarer une absence</button></p>
      <Tiroir ouvert={declarer} onFermer={() => setDeclarer(false)} titre="Déclarer une absence" testId="mab-formulaire">
        <FormulaireAbsence apiFetch={apiFetch} estEmploye onDeclaree={(r) => {
          const d = objet<{ statut?: string; motifs?: Array<{ message: string }> }>(r);
          setDeclarer(false);
          setInfo(d?.statut === "REFUSEE_AUTO" ? `Absence refusée par les règles : ${(d.motifs ?? []).map((m) => m.message).join(" ; ")}. Votre gestionnaire peut l'accorder par dérogation.` : "Absence déclarée.");
          void charger();
        }} />
      </Tiroir>
      <h2>Mes absences d'effectifs</h2>
      {absences === null ? null : (
        <Tableau colonnes={COLONNES} lignes={absences} legende="Mes absences d'effectifs" testId="mab-table"
          vide={<EtatVide titre="Aucune absence" compact testId="mab-vide">Vos absences déclarées apparaîtront ici.</EtatVide>}
          rendre={(a) => (
            <tr key={a.id} data-testid={`mab-${a.id}`}>
              <td>Du {a.du} au {a.au}</td>
              <td>{a.codeAbsence}</td>
              <td><BadgeStatut statut={TON[a.statut]}>{LIBELLE[a.statut]}</BadgeStatut></td>
              <td>{[...a.motifsRegles.map((m) => m.message), ...(a.motifDecision ? [a.motifDecision] : [])].join(" ; ") || "—"}</td>
              <td>
                {(a.statut === "A_APPROUVER" || (a.statut === "ACCEPTEE" && a.du > aujourdhui)) && (
                  <button type="button" className="secondary" data-testid={`mab-${a.id}-annuler`} onClick={() => void annuler(a)}>
                    Annuler<span className="sr-only"> l'absence du {a.du}</span>
                  </button>
                )}
              </td>
            </tr>
          )} />
      )}
    </PageMaquette>
  );
}
