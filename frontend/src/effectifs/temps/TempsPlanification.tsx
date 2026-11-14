/**
 * **Feuilles de temps** — onglet de la planification des effectifs (AO 26-015-DO, lot R9 — E-GP-6,
 * E-RF-1, E-RF-4, E-RF-7, E-RF-12, P-RF-1).
 *
 * L'encadrement prépare les feuilles d'une unité pour une période de paie, les relit avec leurs
 * anomalies, les approuve palier par palier ou les retourne avec un motif, produit le lot de paie
 * signé des feuilles approuvées et lit les relevés regroupés.
 */
import { useCallback, useEffect, useState, type FormEvent } from "react";
import { objet } from "../../formeRecue";
import { BadgeStatut, EtatVide, Message, Tableau, Tiroir, type Colonne } from "../../composants";
import { useApiEffectifs } from "../api";
import ExportResultats from "../ExportResultats";
import { FormulaireModal } from "../../Modal";
import { useReferentiels } from "../planification/useReferentiels";
import DetailFeuille from "./DetailFeuille";
import FluxApprobation from "./FluxApprobation";
import SuperieursHierarchiques from "./SuperieursHierarchiques";
import { champsAffiches, ParametresChamps, useChampsFeuille } from "./ChampsFeuille";
import { duree, LIBELLE_STATUT, statutEnMots, TON_STATUT, type Feuille, type FeuilleDetaillee, type StatutFeuille } from "./types";

const COLONNES: ReadonlyArray<Colonne<Feuille>> = [
  { id: "nom", libelle: "Personne", tri: (f) => f.nom },
  { id: "periode", libelle: "Période", tri: (f) => f.debut },
  { id: "statut", libelle: "Statut", tri: (f) => f.statut },
  { id: "actions", libelle: "Actions", actions: true },
];

interface LignePaie { matricule: string; date: string; codePaie: string; heures: number }
interface Lot { debut: string; feuilles: string[]; lignes: LignePaie[]; empreinte: string }
interface LigneRapport { groupe: string; feuilles: number; planifie: number; declare: number; supplementaires: number; anomalies: number }
const REGROUPEMENTS = { unite: "Unité", ressource: "Personne", typeHeure: "Type d'heure", semaine: "Semaine" } as const;

export default function TempsPlanification() {
  const { lire, envoyer, erreur, setErreur } = useApiEffectifs();
  const { refs } = useReferentiels(["unites"]);
  const [periode, setPeriode] = useState({ debut: "", fin: "", uniteCode: "" });
  const [filtre, setFiltre] = useState<"" | StatutFeuille>("SOUMISE");
  const [feuilles, setFeuilles] = useState<Feuille[]>([]);
  const [fiche, setFiche] = useState<FeuilleDetaillee | null>(null);
  const [motif, setMotif] = useState("");
  const [bilan, setBilan] = useState("");
  const [lot, setLot] = useState<Lot | null>(null);
  const [regroupement, setRegroupement] = useState<keyof typeof REGROUPEMENTS>("unite");
  const [rapport, setRapport] = useState<LigneRapport[] | null>(null);
  const { champs, recharger: rechargerChamps } = useChampsFeuille();
  const [parametres, setParametres] = useState<Record<string, number | string> | null>(null);
  const [parametresInfo, setParametresInfo] = useState("");
  useEffect(() => { void lire<Record<string, number | string>>("/api/effectifs/temps/parametres").then((p) => setParametres(objet<Record<string, number | string>>(p))); }, [lire]);
  const enregistrerParametres = async (e: FormEvent, fermer: () => void) => {
    e.preventDefault();
    setParametresInfo("");
    const r = await envoyer<Record<string, number | string>>("/api/effectifs/temps/parametres", parametres, "PUT");
    if (r) { fermer(); setParametres(r); setParametresInfo("Paramètres enregistrés : ils valent pour les feuilles soumises à partir de maintenant."); }
  };
  const [mesUnites, setMesUnites] = useState<{ toutes: boolean; planification: string[] } | null>(null);
  useEffect(() => { void lire<{ toutes: boolean; planification: string[] }>("/api/effectifs/perimetres/moi").then((m) => setMesUnites(objet<{ toutes: boolean; planification: string[] }>(m))); }, [lire]);
  // Préparer des feuilles exige le niveau PLANIFICATION : on n'offre que les unités où l'acteur l'a.
  const unites = (refs.unites ?? []).filter((u) => mesUnites?.toutes || mesUnites?.planification.includes(u.code));

  const charger = useCallback(async () => {
    const q = new URLSearchParams({ ...(filtre ? { statut: filtre } : {}), ...(periode.debut ? { debut: periode.debut } : {}) });
    setFeuilles((await lire<Feuille[]>(`/api/effectifs/temps/feuilles?${q}`)) ?? []);
  }, [lire, filtre, periode.debut]);
  useEffect(() => { void charger(); }, [charger]);

  const preparer = async (e: FormEvent) => {
    e.preventDefault();
    setBilan("");
    const r = await envoyer<{ creees: string[]; existantes: string[] }>("/api/effectifs/temps/feuilles/preparer", periode);
    if (!r) return;
    setBilan(`${r.creees.length} feuille(s) préparée(s), ${r.existantes.length} déjà présente(s).`);
    await charger();
  };

  const ouvrir = async (f: Feuille) => {
    setErreur(""); setMotif("");
    // Les champs ont pu changer depuis le dialogue « Champs de la feuille » : la fiche les relit.
    void rechargerChamps();
    setFiche({ ...f, segments: [], totaux: {}, heuresSupplementaires: { parJour: {}, total: 0 }, ecarts: [], anomalies: [], decisions: [] });
    setFiche(await lire<FeuilleDetaillee>(`/api/effectifs/temps/feuilles/${f.id}`));
  };

  const trancher = async (d: "approuver" | "retourner") => {
    if (!fiche) return;
    const r = await envoyer<FeuilleDetaillee>(`/api/effectifs/temps/feuilles/${fiche.id}/${d}`, { motif: motif || undefined });
    if (!r) return;
    setBilan(`Feuille de ${r.nom} : ${statutEnMots(r).toLowerCase()}.`);
    setFiche(null);
    await charger();
  };

  const produireLot = async () => {
    setLot(null);
    setLot(await lire<Lot>(`/api/effectifs/temps/export-paie?debut=${periode.debut}${periode.uniteCode ? `&uniteCode=${periode.uniteCode}` : ""}`));
  };
  const telecharger = () => {
    if (!lot) return;
    const csv = ["matricule;date;codePaie;heures", ...lot.lignes.map((l) => `${l.matricule};${l.date};${l.codePaie};${l.heures.toFixed(2)}`), `# sha256;${lot.empreinte}`].join("\n");
    const a = document.createElement("a");
    a.href = URL.createObjectURL(new Blob([csv], { type: "text/csv;charset=utf-8" }));
    a.download = `paie-${lot.debut}.csv`;
    a.click();
    URL.revokeObjectURL(a.href);
  };

  const lireRapport = async () => {
    setRapport(null);
    setRapport(await lire<LigneRapport[]>(`/api/effectifs/temps/rapports?debut=${periode.debut}&regroupement=${regroupement}`));
  };

  return (
    <div data-testid="pla-tps">
      {/* Le **contexte** de toute la console — la période et l'unité que lisent aussi la liste, le
          lot de paie et les relevés. Le mettre dans un dialogue cacherait de quelle période parlent
          les feuilles affichées ; « Préparer les feuilles » est une commande sur ce contexte. */}
      <form className="eff-barre" data-formulaire="requete" onSubmit={(e) => void preparer(e)} aria-label="Période de paie">
        <p><label htmlFor="pla-tps-debut">Début de la période</label><input id="pla-tps-debut" type="date" required data-testid="pla-tps-debut" value={periode.debut} onChange={(e) => setPeriode((p) => ({ ...p, debut: e.target.value }))} /></p>
        <p><label htmlFor="pla-tps-fin">Fin</label><input id="pla-tps-fin" type="date" required data-testid="pla-tps-fin" value={periode.fin} onChange={(e) => setPeriode((p) => ({ ...p, fin: e.target.value }))} /></p>
        <p>
          <label htmlFor="pla-tps-unite">Unité</label>
          <select id="pla-tps-unite" required data-testid="pla-tps-unite" value={periode.uniteCode} onChange={(e) => setPeriode((p) => ({ ...p, uniteCode: e.target.value }))}>
            <option value="">— choisir —</option>
            {unites.map((u) => <option key={u.code} value={u.code}>{u.code} — {u.libelleFr}</option>)}
          </select>
        </p>
        <p><button type="submit" data-testid="pla-tps-preparer">Préparer les feuilles</button></p>
        <p>
          <label htmlFor="pla-tps-filtre">Statut</label>
          <select id="pla-tps-filtre" data-testid="pla-tps-filtre" value={filtre} onChange={(e) => setFiltre(e.target.value as "" | StatutFeuille)}>
            <option value="">Tous</option>
            {(Object.keys(LIBELLE_STATUT) as StatutFeuille[]).map((s) => <option key={s} value={s}>{LIBELLE_STATUT[s]}</option>)}
          </select>
        </p>
      </form>
      <p>Les seuils d'heures supplémentaires, la tolérance d'écart et le nombre de paliers d'approbation se règlent dans Paramétrage des effectifs ; les anomalies par règle, dans les contextes « Feuille de temps » et « Heures supplémentaires ».</p>
      {erreur && !fiche && <Message variante="erreur" testId="pla-tps-erreur">{erreur}</Message>}
      {bilan && <Message variante="succes" testId="pla-tps-bilan">{bilan}</Message>}

      <Tableau colonnes={COLONNES} lignes={feuilles} legende="Feuilles de temps de votre périmètre" testId="pla-tps-table"
        vide={<EtatVide titre="Aucune feuille" compact testId="pla-tps-vide">Aucune feuille ne correspond à ce statut et à cette période.</EtatVide>}
        rendre={(f) => (
          <tr key={f.id} data-testid={`pla-tps-${f.id}`}>
            <td>{f.nom} ({f.matricule})</td>
            <td>{f.debut} au {f.fin}</td>
            <td><BadgeStatut statut={TON_STATUT[f.statut]} testId={`pla-tps-${f.id}-statut`}>{statutEnMots(f)}</BadgeStatut></td>
            <td>
              <button type="button" className="secondary" data-testid={`pla-tps-${f.id}-ouvrir`} onClick={() => void ouvrir(f)}>
                Ouvrir<span className="sr-only"> la feuille de {f.nom} du {f.debut}</span>
              </button>
            </td>
          </tr>
        )} />

      <Tiroir ouvert={!!fiche} onFermer={() => setFiche(null)} titre={fiche ? `Feuille de ${fiche.nom}` : "Feuille"} testId="pla-tps-fiche">
        {fiche && (
          <>
            <p>Du {fiche.debut} au {fiche.fin} — {statutEnMots(fiche)}.</p>
            <DetailFeuille f={fiche} prefixe="pla-tps-fiche" avecSegments champs={champsAffiches(champs, fiche.segments)} />
            {fiche.decisions.length > 0 && (
              <ul aria-label="Décisions">{fiche.decisions.map((d, i) => <li key={i}>Palier {d.palier} : {d.decision === "APPROUVER" ? "approuvé" : "retourné"}{d.motif ? ` — ${d.motif}` : ""}</li>)}</ul>
            )}
            {fiche.statut === "SOUMISE" && (
              <>
                <p>
                  <label htmlFor="pla-tps-motif">Motif (obligatoire pour retourner)</label>
                  <textarea id="pla-tps-motif" data-testid="pla-tps-motif" rows={2} maxLength={500} value={motif} onChange={(e) => setMotif(e.target.value)} />
                </p>
                {erreur && <Message variante="erreur" testId="pla-tps-fiche-erreur">{erreur}</Message>}
                <p className="eff-actions">
                  <button type="button" data-testid="pla-tps-approuver" onClick={() => void trancher("approuver")}>Approuver le palier {fiche.palier}{fiche.flux?.etapes.find((e) => e.palier === fiche.palier) ? ` (${fiche.flux.etapes.find((e) => e.palier === fiche.palier)!.libelle})` : ""}</button>
                  <button type="button" className="secondary" data-testid="pla-tps-retourner" onClick={() => void trancher("retourner")}>Retourner pour correction</button>
                </p>
              </>
            )}
          </>
        )}
      </Tiroir>

      <details className="eff-groupe" data-testid="pla-tps-paie">
        <summary>Lot de paie</summary>
        <p>Les feuilles approuvées de la période choisie, codées par type d'heure, heures supplémentaires reclassées, avec leur empreinte SHA-256.</p>
        <p><button type="button" data-testid="pla-tps-produire" disabled={!periode.debut} onClick={() => void produireLot()}>Produire le lot</button></p>
        {lot && (
          <div role="status" data-testid="pla-tps-lot">
            <p>{lot.feuilles.length} feuille(s), {lot.lignes.length} ligne(s). Empreinte : <code className="eff-empreinte">{lot.empreinte}</code></p>
            {lot.lignes.length > 0 && <button type="button" className="secondary" data-testid="pla-tps-telecharger" onClick={telecharger}>Télécharger le lot (CSV)</button>}
          </div>
        )}
      </details>

      {/* Paramètres et flux sont des **modifications** : chacun son dialogue. Ils étaient repliés
          ensemble dans un même `<details>`, au milieu de la console — deux objets distincts
          derrière un seul titre, et des réglages qui poussaient la liste des feuilles. */}
      <div className="eff-actions">
        {/* Préfixe **hors de `pla-tps-`** : les lignes de feuilles s'ouvrent par `pla-tps-<id>-ouvrir`,
            et les essais les comptent par ce motif — deux ouvreurs de plus le faisaient mentir. */}
        <FormulaireModal libelle="Paramètres des feuilles" titre="Paramètres des feuilles de temps" testId="feuilles-parametres" icone="⚙" className="secondary">
          {(fermer) => (parametres ? (
                <form onSubmit={(e) => void enregistrerParametres(e, fermer)}>
                  {([["seuilJourHeures", "Heures par jour avant les heures supplémentaires", 1, 24], ["seuilSemaineHeures", "Heures par semaine avant les heures supplémentaires", 1, 168],
                    ["paliersApprobation", "Paliers d'approbation par défaut (1 à 3)", 1, 3], ["toleranceEcartMinutes", "Tolérance d'écart au planifié (minutes)", 0, 240],
                    ["toleranceCorroborationMinutes", "Tolérance de corroboration par une source tierce (minutes)", 0, 60]] as const).map(([cle, libelle, min, max]) => (
                    <p key={cle}>
                      <label htmlFor={`pla-tps-p-${cle}`}>{libelle}</label>
                      <input id={`pla-tps-p-${cle}`} type="number" required min={min} max={max} step={1} data-testid={`pla-tps-p-${cle}`} value={parametres[cle] ?? ""}
                        onChange={(e) => setParametres((p) => ({ ...p, [cle]: Number(e.target.value) }))} />
                    </p>
                  ))}
                  {/* E-RF-13 : ce que la feuille fait d'un pointage qu'aucune source ne confirme, et de deux sources en désaccord. */}
                  {([["actionNonCorrobore", "Pointage non confirmé par une source tierce"], ["actionSourcesDivergentes", "Deux sources tierces en désaccord"]] as const).map(([cle, libelle]) => (
                    <p key={cle}>
                      <label htmlFor={`pla-tps-p-${cle}`}>{libelle}</label>
                      <select id={`pla-tps-p-${cle}`} data-testid={`pla-tps-p-${cle}`} value={String(parametres[cle] ?? "")}
                        onChange={(e) => setParametres((p) => ({ ...p, [cle]: e.target.value }))}>
                        <option value="IGNORER">Ignorer</option>
                        <option value="SIGNALER">Signaler à qui approuve</option>
                        <option value="BLOQUER">Bloquer la soumission</option>
                      </select>
                    </p>
                  ))}
                  {erreur && <Message variante="erreur" testId="pla-tps-p-erreur">{erreur}</Message>}
                  <p><button type="submit" data-testid="pla-tps-p-enregistrer">Enregistrer les paramètres</button></p>
                </form>
          ) : <p role="status">Chargement des paramètres…</p>)}
        </FormulaireModal>
        <FormulaireModal libelle="Flux d'approbation" titre="Flux d'approbation des heures" testId="feuilles-approbations" icone="⚙" className="secondary">
          <FluxApprobation />
        </FormulaireModal>
        <FormulaireModal libelle="Supérieurs hiérarchiques" titre="Supérieurs hiérarchiques" testId="feuilles-superieurs" icone="⚙" className="secondary">
          <SuperieursHierarchiques />
        </FormulaireModal>
        {/* Les champs propres à l'organisme : la feuille s'adapte à l'emploi sans code par emploi. */}
        <FormulaireModal libelle="Champs de la feuille" titre="Champs de la feuille de temps" testId="feuilles-champs" icone="⚙" className="secondary">
          <ParametresChamps />
        </FormulaireModal>
      </div>
      {parametresInfo && <p role="status" data-testid="pla-tps-p-info">{parametresInfo}</p>}

      <details className="eff-groupe" data-testid="pla-tps-rapports">
        <summary>Relevés</summary>
        <div className="eff-barre">
          <p>
            <label htmlFor="pla-tps-regroupement">Regrouper par</label>
            <select id="pla-tps-regroupement" data-testid="pla-tps-regroupement" value={regroupement} onChange={(e) => setRegroupement(e.target.value as keyof typeof REGROUPEMENTS)}>
              {Object.entries(REGROUPEMENTS).map(([k, v]) => <option key={k} value={k}>{v}</option>)}
            </select>
          </p>
          <p><button type="button" data-testid="pla-tps-lire-rapport" disabled={!periode.debut} onClick={() => void lireRapport()}>Afficher le relevé</button></p>
          <ExportResultats chemin="/api/effectifs/temps/rapports" parametres={new URLSearchParams({ debut: periode.debut, regroupement })}
            nomFichier={`releve-temps-${periode.debut}`} prefixe="pla-tps-rapport" desactive={!periode.debut} />
        </div>
        {rapport && (
          rapport.length === 0
            ? <p role="status" data-testid="pla-tps-rapport-vide">Aucune feuille sur cette période.</p>
            : (
              <table className="eff-table-compacte" data-testid="pla-tps-rapport">
                <caption>Relevé par {REGROUPEMENTS[regroupement].toLowerCase()}</caption>
                <thead><tr><th scope="col">{REGROUPEMENTS[regroupement]}</th><th scope="col">Planifié</th><th scope="col">Déclaré</th><th scope="col">Suppl.</th><th scope="col">Anomalies</th></tr></thead>
                <tbody>{rapport.map((l) => <tr key={l.groupe}><th scope="row">{l.groupe}</th><td>{duree(l.planifie)}</td><td>{duree(l.declare)}</td><td>{duree(l.supplementaires)}</td><td>{l.anomalies}</td></tr>)}</tbody>
              </table>
            )
        )}
      </details>
    </div>
  );
}
