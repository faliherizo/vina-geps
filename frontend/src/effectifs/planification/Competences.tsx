/**
 * **Compétences et échéances** (AO 26-015-DO, addenda 8, M-04) : permis, accréditations, formations obligatoires, tenus
 * dans la Solution avec leur date d'échéance. Une compétence exigée du corps d'emploi et échue au jour du quart rend la
 * personne inadmissible selon une règle de l'organisme (fait `competencesManquantes`). Le relevé liste ce qui échoit.
 */
import { useCallback, useEffect, useState, type FormEvent } from "react";
import { Badge, EtatVide, Message, Tableau, Tiroir, type Colonne } from "../../composants";
import { useApiEffectifs } from "../api";
import { liste as listeRecue } from "../../formeRecue";
import type { Valeur } from "../Referentiels";
import type { Ressource } from "./types";
import { libelleDe, useReferentiels } from "./useReferentiels";

const API = "/api/effectifs/planification";

interface Detenue { code: string; obtenueLe: string; echeance: string | null; valide: boolean }
interface CompetencesDeLaRessource { detenues: Detenue[]; manquantes: string[] }
interface LigneEcheance { ressourceId: string; ressourceNom: string; uniteCode: string; code: string; echeance: string; etat: "echue" | "a_echoir"; jours: number }

/** Les horizons offerts au relevé, en jours. */
const HORIZONS = [30, 60, 90, 365];

/** Les compétences d'une personne : ce qu'elle détient, ce que son corps exige et qui manque ; poser, renouveler, retirer. */
export function CompetencesRessource({ ressource, onFermer }: { ressource: Ressource; onFermer: () => void }) {
  const { lire, envoyer, supprimer, erreur, setErreur, succes, setSucces } = useApiEffectifs();
  const { refs } = useReferentiels(["competences"]);
  const [etat, setEtat] = useState<CompetencesDeLaRessource | null>(null);
  const base = `${API}/ressources/${encodeURIComponent(ressource.id)}/competences`;
  const charger = useCallback(async () => setEtat(await lire<CompetencesDeLaRessource>(base)), [lire, base]);
  useEffect(() => { void charger(); }, [charger]);

  const retirer = async (code: string) => {
    if (await supprimer(`${base}/${encodeURIComponent(code)}`)) { setSucces(`Compétence « ${libelleDe(refs.competences, code)} » retirée.`); await charger(); }
  };
  const poser = async (code: string, obtenueLe: string, echeance: string) => {
    if (await envoyer(`${base}/${encodeURIComponent(code)}`, { obtenueLe, echeance }, "PUT")) {
      setSucces(`Compétence « ${libelleDe(refs.competences, code)} » enregistrée.`);
      await charger();
    }
  };

  const colonnes: ReadonlyArray<Colonne<Detenue>> = [
    { id: "competence", libelle: "Compétence" }, { id: "obtenue", libelle: "Obtenue le" },
    { id: "echeance", libelle: "Échéance" }, { id: "etat", libelle: "État" }, { id: "actions", libelle: "Actions", actions: true },
  ];
  return (
    <Tiroir ouvert titre={`Compétences — ${ressource.nom}`} onFermer={onFermer} testId="pla-competences">
      {succes && <Message variante="succes" testId="pla-competences-succes">{succes}</Message>}
      {erreur && <Message variante="erreur" testId="pla-competences-erreur">{erreur}</Message>}
      {etat && etat.manquantes.length > 0 && (
        <Message variante="avertissement" testId="pla-competences-manquantes">
          Exigées de son corps d'emploi et manquantes ou échues : {etat.manquantes.map((c) => libelleDe(refs.competences, c)).join(", ")}.
        </Message>
      )}
      <Tableau colonnes={colonnes} lignes={etat?.detenues ?? []} legende={`Compétences de ${ressource.nom}`} testId="pla-competences-tableau"
        vide={<EtatVide titre="Aucune compétence" compact testId="pla-competences-vide">Aucune compétence n'est encore entrée pour cette personne.</EtatVide>}
        rendre={(d) => (
          <tr key={d.code} data-testid={`pla-competence-${d.code}`}>
            <td>{libelleDe(refs.competences, d.code)}</td>
            <td>{d.obtenueLe}</td>
            <td>{d.echeance ?? "Sans échéance"}</td>
            <td>{d.valide ? <Badge ton="succes">Valide</Badge> : <Badge ton="erreur">Échue</Badge>}</td>
            <td className="col-menu">
              <button type="button" className="secondary" data-testid={`pla-competence-retirer-${d.code}`} onClick={() => { setErreur(""); void retirer(d.code); }}>
                Retirer<span className="sr-only"> {libelleDe(refs.competences, d.code)}</span>
              </button>
            </td>
          </tr>
        )} />
      <FormulaireCompetence competences={refs.competences ?? []} onPoser={poser} />
    </Tiroir>
  );
}

function FormulaireCompetence({ competences, onPoser }: { competences: Valeur[]; onPoser: (code: string, obtenueLe: string, echeance: string) => Promise<void> }) {
  const [code, setCode] = useState("");
  const [obtenueLe, setObtenueLe] = useState("");
  const [echeance, setEcheance] = useState("");
  const soumettre = (e: FormEvent) => { e.preventDefault(); void onPoser(code, obtenueLe, echeance); };
  return (
    <form onSubmit={soumettre} data-testid="pla-competence-formulaire">
      <h3>Entrer ou renouveler une compétence</h3>
      <p><label htmlFor="pla-competence-code">Compétence</label>
        <select id="pla-competence-code" data-testid="pla-competence-code" required value={code} onChange={(e) => setCode(e.target.value)}>
          <option value="">Choisir…</option>
          {competences.map((c) => <option key={c.code} value={c.code}>{c.libelleFr}</option>)}
        </select></p>
      <p><label htmlFor="pla-competence-obtenue">Obtenue le</label>
        <input id="pla-competence-obtenue" type="date" data-testid="pla-competence-obtenue" required value={obtenueLe} onChange={(e) => setObtenueLe(e.target.value)} /></p>
      <p><label htmlFor="pla-competence-echeance">Échéance (facultative)</label>
        <input id="pla-competence-echeance" type="date" data-testid="pla-competence-echeance" aria-describedby="pla-competence-echeance-aide" value={echeance}
          onChange={(e) => setEcheance(e.target.value)} />
        <span className="aide" id="pla-competence-echeance-aide">Après cette date, la personne n'est plus admissible aux quarts qui l'exigent.</span></p>
      <button type="submit" data-testid="pla-competence-enregistrer">Enregistrer la compétence</button>
    </form>
  );
}

/** Le relevé des échéances : les compétences échues ou qui échoient dans l'horizon choisi, exportable en CSV. */
export function EcheancesCompetences() {
  const { lire, erreur } = useApiEffectifs();
  const { refs } = useReferentiels(["competences"]);
  const [horizon, setHorizon] = useState(60);
  const [lignes, setLignes] = useState<LigneEcheance[]>([]);
  useEffect(() => {
    void lire<LigneEcheance[]>(`${API}/competences/echeances?dans=${horizon}`).then((r) => setLignes(listeRecue<LigneEcheance>(r)));
  }, [lire, horizon]);

  const colonnes: ReadonlyArray<Colonne<LigneEcheance>> = [
    { id: "personne", libelle: "Personne", tri: (l) => l.ressourceNom }, { id: "unite", libelle: "Unité", tri: (l) => l.uniteCode },
    { id: "competence", libelle: "Compétence" }, { id: "echeance", libelle: "Échéance", tri: (l) => l.echeance },
    { id: "etat", libelle: "État" }, { id: "jours", libelle: "Jours", numerique: true, tri: (l) => l.jours },
  ];
  return (
    <section aria-labelledby="pla-echeances-titre" data-testid="pla-echeances">
      <h2 id="pla-echeances-titre">Échéances des compétences</h2>
      <div className="eff-barre">
        <p><label htmlFor="pla-echeances-horizon">Échéant dans</label>
          <select id="pla-echeances-horizon" data-testid="pla-echeances-horizon" value={horizon} onChange={(e) => setHorizon(Number(e.target.value))}>
            {HORIZONS.map((h) => <option key={h} value={h}>{h} jours</option>)}
          </select></p>
        <a className="bouton secondary" data-testid="pla-echeances-csv" download="echeances-competences.csv" href={csv(lignes, refs.competences ?? [])}>Exporter en CSV</a>
      </div>
      {erreur && <Message variante="erreur" testId="pla-echeances-erreur">{erreur}</Message>}
      <Tableau colonnes={colonnes} lignes={lignes} legende="Compétences échues ou à échoir" testId="pla-echeances-tableau"
        vide={<EtatVide titre="Aucune échéance proche" compact testId="pla-echeances-vide">Aucune compétence échue ni à échoir dans cet horizon.</EtatVide>}
        rendre={(l) => (
          <tr key={`${l.ressourceId}-${l.code}`} data-testid={`pla-echeance-${l.ressourceId}-${l.code}`}>
            <td>{l.ressourceNom}</td><td>{l.uniteCode}</td><td>{libelleDe(refs.competences, l.code)}</td><td>{l.echeance}</td>
            <td>{l.etat === "echue" ? <Badge ton="erreur">Échue</Badge> : <Badge ton="avertissement">À échoir</Badge>}</td>
            <td className="col-numerique">{l.jours}</td>
          </tr>
        )} />
    </section>
  );
}

/** Le relevé en CSV (séparateur « ; », guillemets doublés), pour Excel. */
export function csv(lignes: LigneEcheance[], competences: Valeur[]): string {
  const cellule = (x: string | number) => `"${String(x).replace(/"/g, '""')}"`;
  const contenu = [["Personne", "Unité", "Compétence", "Échéance", "État", "Jours"],
    ...lignes.map((l) => [l.ressourceNom, l.uniteCode, libelleDe(competences, l.code), l.echeance, l.etat === "echue" ? "Échue" : "À échoir", l.jours])]
    .map((ligne) => ligne.map(cellule).join(";")).join("\r\n");
  return `data:text/csv;charset=utf-8,${encodeURIComponent(`﻿${contenu}`)}`;
}
