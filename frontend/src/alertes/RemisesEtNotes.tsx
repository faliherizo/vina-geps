/**
 * **Destinataires d'une alerte, et leur note** (ST-G03, ST-G12) — qui a lu, répondu, quand ; et, pour chacun, la note
 * courte et datée que les expéditeurs tiennent sur sa fiche (« joindre sur le cellulaire de garde »).
 */
import { useCallback, useEffect, useState, type FormEvent } from "react";
import { Message, Tableau, type Colonne } from "../composants";
import { FormulaireModal } from "../Modal";
import { useApiEffectifs as useApi } from "../effectifs/api";
import { liste } from "../formeRecue";
import { etatRemise, quand, type AlerteSuivie, type Remise } from "./types";

export interface NoteDestinataire { personId: string; texte: string; ecriteLe: string; ecritePar: string }

const COL: ReadonlyArray<Colonne<Remise>> = [
  { id: "nom", libelle: "Destinataire", tri: (r) => r.nom }, { id: "lue", libelle: "Lue le" },
  { id: "etat", libelle: "État" }, { id: "repondu", libelle: "Répondu le" }, { id: "note", libelle: "Note" },
  { id: "actions", libelle: "Actions", actions: true },
];

export default function RemisesEtNotes({ fiche }: { fiche: AlerteSuivie }) {
  const { lire, envoyer, supprimer, erreur, succes, setSucces } = useApi();
  const [notes, setNotes] = useState<Record<string, NoteDestinataire>>({});
  const [choisie, setChoisie] = useState<Remise | null>(null);
  const personnes = fiche.remises.map((r) => r.personId).join(",");
  const charger = useCallback(async () => {
    const l = liste<NoteDestinataire>(await lire<NoteDestinataire[]>(`/api/alertes/notes?personnes=${encodeURIComponent(personnes)}`));
    setNotes(Object.fromEntries(l.map((n) => [n.personId, n])));
  }, [lire, personnes]);
  useEffect(() => { void charger(); }, [charger]);
  const apres = async (texte: string) => { setChoisie(null); setSucces(texte); await charger(); };
  const effacer = async (r: Remise) => {
    if (await supprimer(`/api/alertes/notes/${encodeURIComponent(r.personId)}`)) await apres(`Note de ${r.nom} effacée.`);
  };

  return (
    <>
      {erreur && <Message variante="erreur" testId="als-note-erreur">{erreur}</Message>}
      {succes && <Message variante="succes" testId="als-note-succes">{succes}</Message>}
      <Tableau colonnes={COL} lignes={fiche.remises} legende={`Destinataires de l'alerte ${fiche.titre}`} legendeVisible testId="als-remises"
        rendre={(r) => {
          const n = notes[r.personId];
          return (
            <tr key={r.personId} data-testid={`als-remise-${r.personId}`}>
              <td>{r.nom}</td><td>{quand(r.lueLe)}</td><td>{etatRemise(r, fiche.reponses)}</td><td>{quand(r.reponduLe)}</td>
              <td data-testid={`als-note-${r.personId}`}>{n ? <>{n.texte} <span className="muted">({n.ecritePar}, {quand(n.ecriteLe)})</span></> : "—"}</td>
              <td><div className="eff-actions">
                <button type="button" className="secondary" data-testid={`als-note-${r.personId}-modifier`} onClick={() => setChoisie(r)}>
                  {n ? "Modifier" : "Ajouter"}<span className="sr-only"> la note de {r.nom}</span></button>
                {n && <button type="button" className="secondary" data-testid={`als-note-${r.personId}-effacer`} onClick={() => void effacer(r)}>
                  Effacer<span className="sr-only"> la note de {r.nom}</span></button>}
              </div></td>
            </tr>
          );
        }} />
      <FormulaireModal libelle="Note" titre={choisie ? `Note sur ${choisie.nom}` : "Note"} testId="als-note" size="sm" ouvert={choisie !== null} onFermer={() => setChoisie(null)}>
        {choisie && <FormulaireNote remise={choisie} initiale={notes[choisie.personId]?.texte ?? ""} envoyer={envoyer}
          apres={() => apres(`Note de ${choisie.nom} enregistrée.`)} />}
      </FormulaireModal>
    </>
  );
}

type Envoyer = ReturnType<typeof useApi>["envoyer"];

function FormulaireNote({ remise, initiale, envoyer, apres }: { remise: Remise; initiale: string; envoyer: Envoyer; apres: () => Promise<void> }) {
  const [texte, setTexte] = useState(initiale);
  const soumettre = async (e: FormEvent) => {
    e.preventDefault();
    if (await envoyer(`/api/alertes/notes/${encodeURIComponent(remise.personId)}`, { texte }, "PUT")) await apres();
  };
  return (
    <form onSubmit={(e) => void soumettre(e)} aria-label={`Note sur ${remise.nom}`}>
      <p><label htmlFor="als-note-texte">Note (280 caractères au plus)</label>
        <textarea id="als-note-texte" data-testid="als-note-texte" required maxLength={280} rows={3} value={texte} onChange={(e) => setTexte(e.target.value)}
          aria-describedby="als-note-aide" /></p>
      <p id="als-note-aide" className="muted">Visible des expéditeurs autorisés ; datée et signée. Elle ne figure jamais en clair au journal.</p>
      <p><button type="submit" className="primary" data-testid="als-note-enregistrer">Enregistrer la note</button></p>
    </form>
  );
}
