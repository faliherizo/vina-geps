/**
 * **Demandes opérationnelles** (AO 26-015-DO, addenda 8, M-08) : au-delà des absences, des disponibilités et des
 * échanges, le formulaire couvre l'inscription à la **liste de temps supplémentaire**, les événements, la formation et
 * les autres demandes. Les types viennent du référentiel de l'organisme (`types_demande`) ; l'encadrement de l'unité
 * accepte ou refuse en motivant, et lit chaque jour la liste de temps supplémentaire.
 */
import { useCallback, useEffect, useState, type FormEvent } from "react";
import { Badge, EtatVide, Message, Tableau, type Colonne } from "../../composants";
import { useApiEffectifs } from "../api";
import { liste as listeRecue } from "../../formeRecue";

export const API_DEMANDES = "/api/effectifs/planification/demandes";

export interface TypeDemande { code: string; libelleFr: string; comportement: string }
export interface Demande {
  id: string; ressourceId: string; ressourceNom: string; uniteCode: string; typeCode: string; du: string; au: string;
  commentaire: string | null; statut: "EN_ATTENTE" | "ACCEPTEE" | "REFUSEE" | "ANNULEE"; motifDecision: string | null;
}
interface Inscrit { ressourceId: string; ressourceNom: string; uniteCode: string; anciennete: number; du: string; au: string }

const STATUTS: Record<Demande["statut"], { libelle: string; ton: "avertissement" | "succes" | "erreur" | "neutre" }> = {
  EN_ATTENTE: { libelle: "En attente", ton: "avertissement" }, ACCEPTEE: { libelle: "Acceptée", ton: "succes" },
  REFUSEE: { libelle: "Refusée", ton: "erreur" }, ANNULEE: { libelle: "Annulée", ton: "neutre" },
};

const libelleType = (types: TypeDemande[], code: string) => types.find((t) => t.code === code)?.libelleFr ?? code;

function useTypes(): TypeDemande[] {
  const { lire } = useApiEffectifs();
  const [types, setTypes] = useState<TypeDemande[]>([]);
  useEffect(() => { void lire<TypeDemande[]>(`${API_DEMANDES}/types`).then((t) => setTypes(listeRecue<TypeDemande>(t))); }, [lire]);
  return types;
}

/** **Mes demandes** : soumettre une demande, suivre sa décision, annuler celle qui attend. */
export function MesDemandes() {
  const { lire, envoyer, erreur, setErreur, succes, setSucces } = useApiEffectifs();
  const types = useTypes();
  const [demandes, setDemandes] = useState<Demande[]>([]);
  const charger = useCallback(async () => setDemandes(listeRecue<Demande>(await lire<Demande[]>(`${API_DEMANDES}/miennes`))), [lire]);
  useEffect(() => { void charger(); }, [charger]);

  const soumettre = async (corps: Record<string, string>) => {
    if (await envoyer(API_DEMANDES, corps)) { setSucces(`Demande « ${libelleType(types, corps.typeCode)} » envoyée.`); await charger(); }
  };
  const annuler = async (d: Demande) => {
    if (await envoyer(`${API_DEMANDES}/${encodeURIComponent(d.id)}/annuler`, {})) { setSucces(`Demande du ${d.du} annulée.`); await charger(); }
  };
  const colonnes: ReadonlyArray<Colonne<Demande>> = [
    { id: "type", libelle: "Demande" }, { id: "periode", libelle: "Période" }, { id: "statut", libelle: "Statut" },
    { id: "motif", libelle: "Motif de la décision" }, { id: "actions", libelle: "Actions", actions: true },
  ];
  return (
    <div data-testid="dem-miennes">
      {succes && <Message variante="succes" testId="dem-succes">{succes}</Message>}
      {erreur && <Message variante="erreur" testId="dem-erreur">{erreur}</Message>}
      <FormulaireDemande types={types} onSoumettre={(c) => { setErreur(""); void soumettre(c); }} />
      <Tableau colonnes={colonnes} lignes={demandes} legende="Mes demandes" testId="dem-table"
        vide={<EtatVide titre="Aucune demande" compact testId="dem-vide">Vos demandes et leur décision paraissent ici.</EtatVide>}
        rendre={(d) => (
          <tr key={d.id} data-testid={`dem-${d.id}`}>
            <td>{libelleType(types, d.typeCode)}{d.commentaire && <span className="muted"> — {d.commentaire}</span>}</td>
            <td>Du {d.du} au {d.au}</td>
            <td><Badge ton={STATUTS[d.statut].ton}>{STATUTS[d.statut].libelle}</Badge></td>
            <td>{d.motifDecision ?? ""}</td>
            <td className="col-menu">{d.statut === "EN_ATTENTE" && (
              <button type="button" className="secondary" data-testid={`dem-${d.id}-annuler`} onClick={() => void annuler(d)}>
                Annuler<span className="sr-only"> la demande du {d.du}</span>
              </button>
            )}</td>
          </tr>
        )} />
    </div>
  );
}

function FormulaireDemande({ types, onSoumettre }: { types: TypeDemande[]; onSoumettre: (c: Record<string, string>) => void }) {
  const [v, setV] = useState({ typeCode: "", du: "", au: "", commentaire: "" });
  const poser = (k: keyof typeof v, x: string) => setV((o) => ({ ...o, [k]: x }));
  const soumettre = (e: FormEvent) => { e.preventDefault(); onSoumettre(v); };
  return (
    <form onSubmit={soumettre} data-testid="dem-formulaire" aria-labelledby="dem-formulaire-titre">
      <h2 id="dem-formulaire-titre">Nouvelle demande</h2>
      <p><label htmlFor="dem-type">Type de demande</label>
        <select id="dem-type" data-testid="dem-type" required value={v.typeCode} onChange={(e) => poser("typeCode", e.target.value)}>
          <option value="">Choisir…</option>
          {types.map((t) => <option key={t.code} value={t.code}>{t.libelleFr}</option>)}
        </select></p>
      <p><label htmlFor="dem-du">Du</label><input id="dem-du" type="date" data-testid="dem-du" required value={v.du} onChange={(e) => poser("du", e.target.value)} /></p>
      <p><label htmlFor="dem-au">Au</label><input id="dem-au" type="date" data-testid="dem-au" required value={v.au} onChange={(e) => poser("au", e.target.value)} /></p>
      <p><label htmlFor="dem-commentaire">Précisions (facultatif)</label>
        <textarea id="dem-commentaire" data-testid="dem-commentaire" rows={2} maxLength={500} value={v.commentaire} onChange={(e) => poser("commentaire", e.target.value)} /></p>
      <button type="submit" data-testid="dem-envoyer">Envoyer la demande</button>
    </form>
  );
}

/** **Demandes opérationnelles** (encadrement) : décider en motivant ; lire la liste de temps supplémentaire d'un jour. */
export function DemandesOperationnelles() {
  const { lire, envoyer, erreur, setErreur, succes, setSucces } = useApiEffectifs();
  const types = useTypes();
  const [statut, setStatut] = useState("EN_ATTENTE");
  const [demandes, setDemandes] = useState<Demande[]>([]);
  const [motifs, setMotifs] = useState<Record<string, string>>({});
  const charger = useCallback(async () => {
    setDemandes(listeRecue<Demande>(await lire<Demande[]>(`${API_DEMANDES}${statut ? `?statut=${statut}` : ""}`)));
  }, [lire, statut]);
  useEffect(() => { void charger(); }, [charger]);

  const decider = async (d: Demande, decision: "ACCEPTER" | "REFUSER") => {
    setErreur("");
    if (await envoyer(`${API_DEMANDES}/${encodeURIComponent(d.id)}/decision`, { decision, motif: motifs[d.id] ?? "" })) {
      setSucces(`Demande de ${d.ressourceNom} ${decision === "ACCEPTER" ? "acceptée" : "refusée"}.`);
      await charger();
    }
  };
  const colonnes: ReadonlyArray<Colonne<Demande>> = [
    { id: "personne", libelle: "Personne", tri: (d) => d.ressourceNom }, { id: "type", libelle: "Demande" },
    { id: "periode", libelle: "Période", tri: (d) => d.du }, { id: "statut", libelle: "Statut" }, { id: "decision", libelle: "Décision", actions: true },
  ];
  return (
    <div data-testid="dem-gestion">
      {succes && <Message variante="succes" testId="dem-gestion-succes">{succes}</Message>}
      {erreur && <Message variante="erreur" testId="dem-gestion-erreur">{erreur}</Message>}
      <p><label htmlFor="dem-filtre">Statut</label>
        <select id="dem-filtre" data-testid="dem-filtre" value={statut} onChange={(e) => setStatut(e.target.value)}>
          <option value="">Toutes</option>
          {Object.entries(STATUTS).map(([code, s]) => <option key={code} value={code}>{s.libelle}</option>)}
        </select></p>
      <Tableau colonnes={colonnes} lignes={demandes} legende="Demandes opérationnelles de votre périmètre" testId="dem-gestion-table"
        vide={<EtatVide titre="Aucune demande" compact testId="dem-gestion-vide">Aucune demande pour ce filtre.</EtatVide>}
        rendre={(d) => (
          <tr key={d.id} data-testid={`dem-gestion-${d.id}`}>
            <td>{d.ressourceNom} <span className="muted">({d.uniteCode})</span></td>
            <td>{libelleType(types, d.typeCode)}{d.commentaire && <span className="muted"> — {d.commentaire}</span>}</td>
            <td>Du {d.du} au {d.au}</td>
            <td><Badge ton={STATUTS[d.statut].ton}>{STATUTS[d.statut].libelle}</Badge></td>
            <td className="col-menu">{d.statut === "EN_ATTENTE" ? (
              <div className="eff-barre">
                <label className="sr-only" htmlFor={`dem-motif-${d.id}`}>Motif pour {d.ressourceNom}</label>
                <input id={`dem-motif-${d.id}`} data-testid={`dem-motif-${d.id}`} placeholder="Motif (exigé pour refuser)" value={motifs[d.id] ?? ""}
                  onChange={(e) => setMotifs((m) => ({ ...m, [d.id]: e.target.value }))} />
                <button type="button" data-testid={`dem-accepter-${d.id}`} onClick={() => void decider(d, "ACCEPTER")}>
                  Accepter<span className="sr-only"> la demande de {d.ressourceNom}</span></button>
                <button type="button" className="secondary" data-testid={`dem-refuser-${d.id}`} onClick={() => void decider(d, "REFUSER")}>
                  Refuser<span className="sr-only"> la demande de {d.ressourceNom}</span></button>
              </div>
            ) : (d.motifDecision ?? "")}</td>
          </tr>
        )} />
      <ListeTempsSupplementaire />
    </div>
  );
}

/** La liste de temps supplémentaire d'un jour : les inscriptions acceptées qui le couvrent, par ancienneté. */
function ListeTempsSupplementaire() {
  const { lire } = useApiEffectifs();
  const [date, setDate] = useState(() => new Date().toLocaleDateString("en-CA", { timeZone: "America/Toronto" }));
  const [inscrits, setInscrits] = useState<Inscrit[]>([]);
  useEffect(() => {
    if (!date) return;
    void lire<Inscrit[]>(`${API_DEMANDES}/liste-ts?date=${date}`).then((r) => setInscrits(listeRecue<Inscrit>(r)));
  }, [lire, date]);
  const colonnes: ReadonlyArray<Colonne<Inscrit>> = [
    { id: "rang", libelle: "Rang", numerique: true }, { id: "personne", libelle: "Personne" }, { id: "unite", libelle: "Unité" },
    { id: "anciennete", libelle: "Ancienneté", numerique: true }, { id: "periode", libelle: "Inscrite" },
  ];
  return (
    <section aria-labelledby="dem-ts-titre" data-testid="dem-ts">
      <h2 id="dem-ts-titre">Liste de temps supplémentaire</h2>
      <p><label htmlFor="dem-ts-date">Jour</label><input id="dem-ts-date" type="date" data-testid="dem-ts-date" value={date} onChange={(e) => setDate(e.target.value)} /></p>
      <Tableau colonnes={colonnes} lignes={inscrits} legende={`Inscrits au temps supplémentaire le ${date}`} testId="dem-ts-table"
        vide={<EtatVide titre="Personne d'inscrit" compact testId="dem-ts-vide">Aucune inscription acceptée ne couvre ce jour.</EtatVide>}
        rendre={(x) => (
          <tr key={`${x.ressourceId}-${x.du}`} data-testid={`dem-ts-${x.ressourceId}`}>
            <td className="col-numerique">{inscrits.indexOf(x) + 1}</td><td>{x.ressourceNom}</td><td>{x.uniteCode}</td>
            <td className="col-numerique">{x.anciennete.toLocaleString("fr-CA")}</td><td>Du {x.du} au {x.au}</td>
          </tr>
        )} />
    </section>
  );
}
