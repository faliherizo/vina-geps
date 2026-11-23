/**
 * **Politiques de relance** (ST-G04 ; critères 15, 16, 17, 32) — une suite d'étapes : qui viser, par quels canaux,
 * combien de minutes après l'étape précédente, et quand s'arrêter. Modifiée, une politique fait une version nouvelle ;
 * une cascade en cours garde celle avec laquelle elle est partie.
 */
import { useCallback, useEffect, useState, type FormEvent } from "react";
import { BadgeStatut, EtatVide, Message, Tableau, type Colonne } from "../composants";
import { FormulaireModal } from "../Modal";
import { useApiEffectifs as useApi } from "../effectifs/api";
import { liste } from "../formeRecue";
import { ARRETS, CANAUX, CIBLES, etapeEnMots, libelleArret, type CanalAlerte, type CibleEtape, type CritereArret, type EtapePolitique, type Groupe,
  type Politique } from "./types";

const COL: ReadonlyArray<Colonne<Politique>> = [
  { id: "nom", libelle: "Politique", tri: (p) => p.nom }, { id: "version", libelle: "Version", numerique: true },
  { id: "etapes", libelle: "Étapes" }, { id: "arret", libelle: "Arrêt" }, { id: "etat", libelle: "État" }, { id: "actions", libelle: "Actions", actions: true },
];
const NOUVELLE_ETAPE: EtapePolitique = { cible: "initiaux", cibleId: null, canaux: ["poussee"], delaiMinutes: 5 };

export default function PolitiquesRelance({ groupes }: { groupes: Groupe[] }) {
  const { lire, envoyer, erreur, succes, setSucces } = useApi();
  const [politiques, setPolitiques] = useState<Politique[]>([]);
  const [enModification, setEnModification] = useState<Politique | null>(null);
  const charger = useCallback(async () => setPolitiques(liste<Politique>(await lire<Politique[]>("/api/alertes/politiques"))), [lire]);
  useEffect(() => { void charger(); }, [charger]);
  const basculer = async (p: Politique) => {
    if (await envoyer(`/api/alertes/politiques/${p.id}`, { actif: !p.actif }, "PATCH")) { setSucces(`« ${p.nom} » ${p.actif ? "désactivée" : "réactivée"}.`); await charger(); }
  };
  const apres = async (p: Politique, verbe: string) => { setEnModification(null); setSucces(`Politique « ${p.nom} » ${verbe} : version ${p.version}.`); await charger(); };

  return (
    <section className="card" aria-labelledby="alp-politiques-h" data-testid="alp-politiques">
      <h2 id="alp-politiques-h">Politiques de relance</h2>
      <p className="muted">Une politique relance, puis escalade, jusqu'à ce que son critère d'arrêt soit atteint. L'expéditeur la choisit à l'envoi.</p>
      {erreur && <Message variante="erreur" testId="alp-pol-erreur">{erreur}</Message>}
      {succes && <Message variante="succes" testId="alp-pol-succes">{succes}</Message>}
      <div className="eff-actions">
        <FormulaireModal libelle="Nouvelle politique" titre="Nouvelle politique de relance" testId="alp-politique" size="lg">
          {(fermer) => <FormulairePolitique groupes={groupes} envoyer={envoyer} apres={async (p) => { fermer(); await apres(p, "créée"); }} />}
        </FormulaireModal>
      </div>
      <Tableau colonnes={COL} lignes={politiques} legende="Politiques de relance" testId="alp-politiques-table"
        vide={<EtatVide titre="Aucune politique" compact testId="alp-politiques-vide">Une politique « Code bleu » : relancer après 5 minutes, puis escalader au groupe de garde.</EtatVide>}
        rendre={(p) => (
          <tr key={p.id} data-testid={`alp-pol-${p.id}`}>
            <td>{p.nom}</td><td className="num">{p.version}</td>
            <td><ol className="eff-liste-compacte">{p.etapes.map((e, i) => <li key={i}>{etapeEnMots(e)}</li>)}</ol></td>
            <td>{libelleArret(p.arret)}</td>
            <td>{p.actif ? <BadgeStatut statut="active">Active</BadgeStatut> : <BadgeStatut statut="clos">Désactivée</BadgeStatut>}</td>
            <td><div className="eff-actions">
              <button type="button" className="secondary" data-testid={`alp-pol-${p.id}-modifier`} onClick={() => setEnModification(p)}>
                Modifier<span className="sr-only"> la politique {p.nom}</span></button>
              <button type="button" className="secondary" data-testid={`alp-pol-${p.id}-basculer`} onClick={() => void basculer(p)}>
                {p.actif ? "Désactiver" : "Réactiver"}<span className="sr-only"> la politique {p.nom}</span></button>
            </div></td>
          </tr>
        )} />
      <FormulaireModal libelle="Modifier la politique" titre={enModification ? `Modifier « ${enModification.nom} » (nouvelle version)` : "Modifier"} testId="alp-pol-modification"
        size="lg" ouvert={enModification !== null} onFermer={() => setEnModification(null)}>
        {enModification && <FormulairePolitique initial={enModification} groupes={groupes} envoyer={envoyer} apres={(p) => apres(p, "modifiée")} />}
      </FormulaireModal>
    </section>
  );
}

type Envoyer = ReturnType<typeof useApi>["envoyer"];

function FormulairePolitique({ initial, groupes, envoyer, apres }: { initial?: Politique; groupes: Groupe[]; envoyer: Envoyer; apres: (p: Politique) => Promise<void> }) {
  const [nom, setNom] = useState(initial?.nom ?? "");
  const [arret, setArret] = useState<CritereArret>(initial?.arret ?? "prise_en_charge");
  const [etapes, setEtapes] = useState<EtapePolitique[]>(initial?.etapes ?? [NOUVELLE_ETAPE]);
  const changer = (i: number, e: Partial<EtapePolitique>) => setEtapes((l) => l.map((x, j) => (j === i ? { ...x, ...e } : x)));
  const soumettre = async (ev: FormEvent) => {
    ev.preventDefault();
    const corps = { nom, arret, etapes: etapes.map((e) => ({ ...e, cibleId: e.cible === "initiaux" ? null : e.cibleId })) };
    const p = initial ? await envoyer<Politique>(`/api/alertes/politiques/${initial.id}`, corps, "PUT") : await envoyer<Politique>("/api/alertes/politiques", corps);
    if (p) await apres(p);
  };
  return (
    <form onSubmit={(e) => void soumettre(e)} aria-label={initial ? "Modifier la politique de relance" : "Nouvelle politique de relance"}>
      <p><label htmlFor="alp-pol-nom">Nom</label>
        <input id="alp-pol-nom" data-testid="alp-pol-nom" required maxLength={120} value={nom} onChange={(e) => setNom(e.target.value)} /></p>
      <p><label htmlFor="alp-pol-arret">S'arrêter</label>
        <select id="alp-pol-arret" data-testid="alp-pol-arret" value={arret} onChange={(e) => setArret(e.target.value as CritereArret)}>
          {ARRETS.map((a) => <option key={a.code} value={a.code}>{a.libelle}</option>)}
        </select></p>
      {etapes.map((e, i) => <ChampsEtape key={i} rang={i} etape={e} groupes={groupes} changer={(x) => changer(i, x)}
        retirer={etapes.length > 1 ? () => setEtapes((l) => l.filter((_, j) => j !== i)) : undefined} />)}
      <div className="eff-actions">
        <button type="button" className="secondary" data-testid="alp-pol-ajouter-etape" disabled={etapes.length >= 10}
          onClick={() => setEtapes((l) => [...l, NOUVELLE_ETAPE])}>Ajouter une étape</button>
        <button type="submit" className="primary" data-testid="alp-pol-enregistrer">{initial ? "Enregistrer la nouvelle version" : "Créer la politique"}</button>
      </div>
    </form>
  );
}

function ChampsEtape({ rang, etape, groupes, changer, retirer }: {
  rang: number; etape: EtapePolitique; groupes: Groupe[]; changer: (e: Partial<EtapePolitique>) => void; retirer?: () => void;
}) {
  const p = `alp-pol-e${rang}`;
  const basculerCanal = (c: CanalAlerte) => changer({ canaux: etape.canaux.includes(c) ? etape.canaux.filter((x) => x !== c) : [...etape.canaux, c] });
  return (
    <fieldset data-testid={p}>
      <legend>Étape {rang + 1}</legend>
      <p><label htmlFor={`${p}-delai`}>Minutes après {rang === 0 ? "l'envoi" : "l'étape précédente"}</label>
        <input id={`${p}-delai`} data-testid={`${p}-delai`} type="number" min={1} max={1440} required value={etape.delaiMinutes}
          onChange={(e) => changer({ delaiMinutes: Number(e.target.value) })} /></p>
      <p><label htmlFor={`${p}-cible`}>Qui viser</label>
        <select id={`${p}-cible`} data-testid={`${p}-cible`} value={etape.cible} onChange={(e) => changer({ cible: e.target.value as CibleEtape, cibleId: null })}>
          {CIBLES.map((c) => <option key={c.code} value={c.code}>{c.libelle}</option>)}
        </select></p>
      {(etape.cible === "groupe" || etape.cible === "prochain_disponible") && (
        <p><label htmlFor={`${p}-groupe`}>Groupe</label>
          <select id={`${p}-groupe`} data-testid={`${p}-groupe`} required value={etape.cibleId ?? ""} onChange={(e) => changer({ cibleId: e.target.value })}>
            <option value="">Choisir un groupe</option>
            {groupes.map((g) => <option key={g.id} value={g.id}>{g.nom}</option>)}
          </select></p>
      )}
      {etape.cible === "personne" && (
        <p><label htmlFor={`${p}-personne`}>Personne (nom de connexion)</label>
          <input id={`${p}-personne`} data-testid={`${p}-personne`} required value={etape.cibleId ?? ""} onChange={(e) => changer({ cibleId: e.target.value })} /></p>
      )}
      <fieldset>
        <legend>Canaux</legend>
        {CANAUX.map((c) => (
          <p key={c.code} className="checkbox-line">
            <input type="checkbox" id={`${p}-${c.code}`} data-testid={`${p}-${c.code}`} checked={etape.canaux.includes(c.code)} onChange={() => basculerCanal(c.code)} />
            <label htmlFor={`${p}-${c.code}`}>{c.libelle}</label>
          </p>
        ))}
      </fieldset>
      {retirer && <p><button type="button" className="secondary" data-testid={`${p}-retirer`} onClick={retirer}>Retirer l'étape {rang + 1}</button></p>}
    </fieldset>
  );
}
