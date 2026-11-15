/**
 * **Ma feuille de temps** — la page du module `effectifs_temps` côté employé (AO 26-015-DO, lot R9 —
 * E-RF-1, E-RF-2, E-RF-3, E-RF-11, E-RF-13, E-RF-14, E-GP-3, E-GP-5).
 *
 * Un temps proposé par une source tierce seule (borne, RAO) attend que la personne le confirme ou
 * le corrige (E-RF-13). Une modification que les règles de l'organisme refusent — délai après le
 * quart, période fermée… — s'affiche avec son motif (E-GP-3).
 *
 * La feuille arrive préparée depuis l'horaire et les pointages. La personne la relit, corrige un
 * segment (la correction d'un temps pointé se motive), en ajoute, voit ses anomalies à mesure, et la
 * soumet ; soumise, elle se verrouille jusqu'à la décision.
 */
import { useCallback, useEffect, useState, type FormEvent } from "react";
import { liste } from "./formeRecue";
import { BadgeStatut, EnTeteEcran, EtatVide, Message, Tableau, Tiroir, type Colonne } from "./composants";
import { useApiEffectifs } from "./effectifs/api";
import { SansDossierEffectif, useMaRessource } from "./effectifs/maRessource";
import DetailFeuille from "./effectifs/temps/DetailFeuille";
import { champsAffiches, COMPLEMENT_VIDE, complementAEnvoyer, SaisieChamps, useChampsFeuille, valeurDe, type Complement } from "./effectifs/temps/ChampsFeuille";
import {
  duree, heureDe, hhmmDe, instantDe, origineEnMots, preuveEnMots, statutEnMots, TON_STATUT, type ChampFeuille, type Feuille, type FeuilleDetaillee, type Segment,
  peutRappeler,
} from "./effectifs/temps/types";
import "./effectifs/effectifs.css";
import PageMaquette from "./composants/PageMaquette";
import { ConfirmModal, FormulaireModal } from "./Modal";

/** Les colonnes suivent les champs de l'organisme : une feuille de paramédic et une feuille administrative n'ont pas les mêmes. */
const colonnes = (champs: ChampFeuille[]): ReadonlyArray<Colonne<Segment>> => [
  { id: "ressource", libelle: "Ressource" },
  { id: "date", libelle: "Jour", tri: (s) => s.debut },
  { id: "heures", libelle: "Heures" },
  { id: "type", libelle: "Type", tri: (s) => s.typeHeureCode },
  { id: "taches", libelle: "Tâches" },
  ...champs.map((c) => ({ id: `champ-${c.code}`, libelle: c.libelle })),
  { id: "source", libelle: "Origine", tri: (s) => s.source },
  { id: "preuve", libelle: "Preuve tierce" },
  { id: "actions", libelle: "Actions", actions: true },
];

interface Saisie { date: string; debut: string; fin: string; typeHeureCode: string; motif: string }
const VIDE: Saisie = { date: "", debut: "", fin: "", typeHeureCode: "", motif: "" };

export default function MaFeuilleTemps() {
  const { lire, envoyer, apiFetch, erreur, setErreur } = useApiEffectifs();
  /**
   * Suis-je planifiable ? La question se pose **avant** d'offrir le moindre geste : sans dossier
   * d'effectif, il n'y a pas d'horaire d'où reprendre du temps, donc rien à préparer.
   */
  const moi = useMaRessource();
  const [feuilles, setFeuilles] = useState<Feuille[] | null>(null);
  const [choisie, setChoisie] = useState("");
  const [f, setF] = useState<FeuilleDetaillee | null>(null);
  const [types, setTypes] = useState<Array<{ code: string; libelleFr: string }>>([]);
  const [periode, setPeriode] = useState({ debut: "", fin: "" });
  const [info, setInfo] = useState("");
  const [edition, setEdition] = useState<Segment | null>(null);
  const [saisie, setSaisie] = useState<Saisie>(VIDE);
  const [complement, setComplement] = useState<Complement>(COMPLEMENT_VIDE);
  const { champs } = useChampsFeuille();
  const [versions, setVersions] = useState<Segment[] | null>(null);
  const [rappel, setRappel] = useState(false);
  const voirVersions = async (s: Segment) => { setVersions(liste<Segment>(await lire<Segment[]>(`/api/effectifs/temps/feuilles/${choisie}/segments/${s.id}/historique`))); };

  const chargerListe = useCallback(async () => {
    const l = (await lire<Feuille[]>("/api/effectifs/temps/moi")) ?? [];
    setFeuilles(l);
    setChoisie((c) => c || l[0]?.id || "");
  }, [lire]);
  useEffect(() => { void chargerListe(); }, [chargerListe]);
  useEffect(() => { void lire<Array<{ code: string; libelleFr: string }>>("/api/effectifs/referentiels/types_heure").then((t) => setTypes(t ?? [])); }, [lire]);
  const chargerFeuille = useCallback(async () => { if (choisie) setF(await lire<FeuilleDetaillee>(`/api/effectifs/temps/feuilles/${choisie}`)); }, [lire, choisie]);
  useEffect(() => { void chargerFeuille(); }, [chargerFeuille]);

  const preparer = async (e: FormEvent, fermer: () => void) => {
    e.preventDefault();
    const r = await envoyer<{ creees: string[]; existantes: string[] }>("/api/effectifs/temps/feuilles/preparer", periode);
    if (!r) return;
    fermer();
    setInfo(r.creees.length ? "Feuille préparée depuis votre horaire et vos pointages." : "Votre feuille de cette période existait déjà.");
    setChoisie(r.creees[0] ?? r.existantes[0]);
    await chargerListe();
  };

  const apres = async (r: FeuilleDetaillee | null, message: string) => {
    if (!r) return false;
    setF(r); setInfo(message); setEdition(null); setSaisie(VIDE); setComplement(COMPLEMENT_VIDE);
    await chargerListe();
    return true;
  };

  const ajouter = async (e: FormEvent, fermer: () => void) => {
    e.preventDefault();
    setInfo("");
    const r = await envoyer<FeuilleDetaillee>(`/api/effectifs/temps/feuilles/${choisie}/segments`,
      { date: saisie.date, debut: instantDe(saisie.date, saisie.debut), fin: instantDe(saisie.date, saisie.fin), typeHeureCode: saisie.typeHeureCode, motif: saisie.motif || undefined, ...complementAEnvoyer(complement) });
    // Refusé, le formulaire reste ouvert avec ses valeurs : le motif du refus se corrige là où il a été saisi.
    if (await apres(r, `Segment du ${saisie.date} ajouté.`)) fermer();
  };

  const corriger = async (e: FormEvent) => {
    e.preventDefault();
    if (!edition) return;
    const r = await envoyer<FeuilleDetaillee>(`/api/effectifs/temps/feuilles/${choisie}/segments/${edition.id}`,
      { debut: instantDe(edition.date, saisie.debut), fin: instantDe(edition.date, saisie.fin), typeHeureCode: saisie.typeHeureCode, motif: saisie.motif || undefined, ...complementAEnvoyer(complement) }, "PUT");
    await apres(r, `Segment du ${edition.date} corrigé.`);
  };

  const retirer = async () => {
    if (!edition) return;
    setErreur("");
    const rep = await apiFetch(`/api/effectifs/temps/feuilles/${choisie}/segments/${edition.id}`,
      { method: "DELETE", headers: { "Content-Type": "application/json" }, body: JSON.stringify({ motif: saisie.motif || undefined }) });
    const j = await rep.json().catch(() => ({}));
    if (!rep.ok) { setErreur(j.errors?.join(", ") || `Erreur ${rep.status}`); return; }
    await apres(j, `Segment du ${edition.date} retiré.`);
  };

  /** E-RF-13 : la personne dit que l'horodatage externe dit vrai. */
  const confirmer = async (s: Segment) => {
    setInfo("");
    await apres(await envoyer<FeuilleDetaillee>(`/api/effectifs/temps/feuilles/${choisie}/segments/${s.id}/confirmer`, {}), `Temps du ${s.date} confirmé.`);
  };

  const soumettre = async () => {
    setInfo("");
    await apres(await envoyer<FeuilleDetaillee>(`/api/effectifs/temps/feuilles/${choisie}/soumettre`, {}),
      "Feuille soumise : elle attend la décision. Avant la paie, vous pouvez la rappeler pour la corriger.");
  };

  /** Le rappel : avant la paie, la feuille revient au brouillon, et la personne qui l'attendait n'a plus rien à décider. */
  const rappeler = async () => {
    setRappel(false); setInfo("");
    await apres(await envoyer<FeuilleDetaillee>(`/api/effectifs/temps/feuilles/${choisie}/rappeler`, {}),
      "Feuille rappelée : elle est revenue au brouillon. Corrigez-la, puis soumettez-la de nouveau.");
  };

  if (!feuilles) return erreur ? <Message variante="erreur" testId="mft-erreur">{erreur}</Message> : null;
  const ouverte = f && (f.statut === "BROUILLON" || f.statut === "RETOURNEE");
  const champ = (cle: keyof Saisie) => ({ value: saisie[cle], onChange: (e: { target: { value: string } }) => setSaisie((s) => ({ ...s, [cle]: e.target.value })) });
  const affiches = champsAffiches(champs, f?.segments ?? []);
  const optionsTypes = [<option key="" value="">— choisir —</option>, ...types.map((t) => <option key={t.code} value={t.code}>{t.code} — {t.libelleFr}</option>)];

  return (
    <PageMaquette module="Urgences-santé" contexte="Mon espace • feuille de temps">
      <EnTeteEcran titre="Ma feuille de temps" description="Relisez le temps repris de votre horaire et de vos pointages, corrigez-le au besoin, puis soumettez-le." testId="mft-titre" />
      {erreur && !edition && <Message variante="erreur" testId="mft-erreur">{erreur}</Message>}
      {info && <Message variante="succes" testId="mft-info">{info}</Message>}

      {/* Sans dossier d'effectif, l'écran dit ce qui manque — et n'offre aucun geste : le
          formulaire ne pouvait qu'échouer, sur un message écrit pour un planificateur. */}
      {moi === null && <SansDossierEffectif testId="mft-sans-ressource" />}

      {/* Préparer **crée** une feuille : c'est un ajout, il vit donc dans un dialogue — la page
          reste la relecture de la feuille, et non un formulaire posé au-dessus d'elle. */}
      {moi && (
        <div className="eff-actions">
          <FormulaireModal libelle="Préparer ma feuille" titre="Préparer la feuille d'une période" testId="mft-preparation" size="md">
            {(fermer) => (
              <form onSubmit={(e) => void preparer(e, fermer)} aria-label="Préparer une feuille">
                <p><label htmlFor="mft-debut">Début de la période</label><input id="mft-debut" type="date" required data-testid="mft-debut" value={periode.debut} onChange={(e) => setPeriode((p) => ({ ...p, debut: e.target.value }))} /></p>
                <p><label htmlFor="mft-fin">Fin de la période</label><input id="mft-fin" type="date" required data-testid="mft-fin" value={periode.fin} onChange={(e) => setPeriode((p) => ({ ...p, fin: e.target.value }))} /></p>
                {erreur && <Message variante="erreur" testId="mft-p-erreur">{erreur}</Message>}
                <p><button type="submit" data-testid="mft-preparer">Préparer ma feuille</button></p>
              </form>
            )}
          </FormulaireModal>
        </div>
      )}

      {moi && feuilles.length === 0
        ? <EtatVide titre="Aucune feuille de temps" compact testId="mft-vide">Préparez la feuille d'une période, ou attendez que votre planificateur la prépare.</EtatVide>
        : (
          <p>
            <label htmlFor="mft-feuille">Feuille</label>
            <select id="mft-feuille" data-testid="mft-feuille" value={choisie} onChange={(e) => { setInfo(""); setChoisie(e.target.value); }}>
              {feuilles.map((x) => <option key={x.id} value={x.id}>Du {x.debut} au {x.fin} — {statutEnMots(x)}</option>)}
            </select>
          </p>
        )}

      {f && (
        <div data-testid="mft-feuille-detail">
          <p><BadgeStatut statut={TON_STATUT[f.statut]} testId="mft-statut">{statutEnMots(f)}</BadgeStatut></p>
          {f.statut === "RETOURNEE" && f.decisions.filter((d) => d.decision === "RETOURNER" || d.decision === "ROUVRIR").slice(-1).map((d) => (
            <Message key={d.decideLe} variante="info" testId="mft-retour">{d.decision === "ROUVRIR" ? "Rouverte par l'encadrement" : "Retournée"} : {d.motif}</Message>
          ))}
          {f.statut === "PAYEE" && <Message variante="info" testId="mft-payee">Cette feuille a été payée : elle ne se modifie plus. Une erreur se corrige à la paie suivante, avec votre gestionnaire.</Message>}
          {peutRappeler(f.statut) && (
            <div className="eff-actions">
              <button type="button" className="secondary" data-testid="mft-rappeler" onClick={() => { setErreur(""); setRappel(true); }}>Rappeler ma feuille</button>
            </div>
          )}
          <ConfirmModal open={rappel} title="Rappeler ma feuille" confirmLabel="Rappeler" testId="mft-rappel"
            message={f.statut === "APPROUVEE" ? "Votre feuille est approuvée mais pas encore payée. La rappeler annule son approbation : il faudra la soumettre de nouveau."
              : "Votre feuille revient au brouillon pour que vous la corrigiez ; la personne qui devait l'approuver n'a plus rien à décider."}
            onConfirm={() => rappeler()} onCancel={() => setRappel(false)} />
          <p data-testid="mft-ressource">Ressource : <strong>{f.nom}</strong> (matricule {f.matricule}) — unité {f.uniteCode}</p>
          <DetailFeuille f={f} prefixe="mft" />
          <Tableau colonnes={colonnes(affiches)} lignes={f.segments} legende="Segments de temps de la feuille" testId="mft-segments"
            vide={<EtatVide titre="Aucun segment" compact testId="mft-segments-vide">Aucun quart publié ni pointage sur cette période.</EtatVide>}
            rendre={(s) => (
              <tr key={s.id} data-testid={`mft-s-${s.id}`}>
                <td>{f.nom} ({f.matricule})</td>
                <td>{s.date}</td>
                <td>{heureDe(s.debut)}–{heureDe(s.fin)} ({duree(Math.round((Date.parse(s.fin) - Date.parse(s.debut)) / 60_000))})</td>
                <td>{s.typeHeureCode}</td>
                <td data-testid={`mft-s-${s.id}-taches`}>{s.description || "—"}</td>
                {affiches.map((c) => <td key={c.code}>{valeurDe(s, c)}</td>)}
                <td>{origineEnMots(s)}{s.version > 1 ? ` · corrigé (v${s.version})` : ""}</td>
                <td>{preuveEnMots(s)}</td>
                <td><div className="eff-actions">
                  {ouverte && s.aConfirmer && (
                    <button type="button" data-testid={`mft-s-${s.id}-confirmer`} onClick={() => void confirmer(s)}>
                      Confirmer<span className="sr-only"> le temps du {s.date} proposé par {s.sourceExterne}</span>
                    </button>
                  )}
                  {ouverte && (
                    <button type="button" className="secondary" data-testid={`mft-s-${s.id}-modifier`}
                      onClick={() => { setErreur(""); setVersions(null); setEdition(s); setSaisie({ date: s.date, debut: hhmmDe(s.debut), fin: hhmmDe(s.fin), typeHeureCode: s.typeHeureCode, motif: "" });
                        setComplement({ description: s.description ?? "", attributs: { ...(s.attributs ?? {}) } }); }}>
                      Modifier<span className="sr-only"> le segment du {s.date} de {heureDe(s.debut)}</span>
                    </button>
                  )}
                </div></td>
              </tr>
            )} />

          {ouverte && (
            <>
              {/* L'ajout vit dans un dialogue, comme la correction vit dans son tiroir : les deux
                  écritures d'une feuille se présentaient différemment — l'une repliée dans la page,
                  l'autre en surimpression —, et l'ajout poussait le tableau qu'on était en train de relire. */}
              <div className="eff-actions">
                <FormulaireModal libelle="Ajouter un segment" titre={`Ajouter un segment — du ${f.debut} au ${f.fin}`} testId="mft-ajouter" size="md" className="secondary">
                  {(fermer) => (
                    <form onSubmit={(e) => void ajouter(e, fermer)}>
                      <p><label htmlFor="mft-a-date">Jour</label><input id="mft-a-date" type="date" required min={f.debut} max={f.fin} data-testid="mft-a-date" {...champ("date")} /></p>
                      <p><label htmlFor="mft-a-debut">De</label><input id="mft-a-debut" type="time" required data-testid="mft-a-debut" {...champ("debut")} /></p>
                      <p><label htmlFor="mft-a-fin">À</label><input id="mft-a-fin" type="time" required data-testid="mft-a-fin" {...champ("fin")} /></p>
                      <p><label htmlFor="mft-a-type">Type d'heure</label><select id="mft-a-type" required data-testid="mft-a-type" {...champ("typeHeureCode")}>{optionsTypes}</select></p>
                      <SaisieChamps champs={champs} valeur={complement} onChange={setComplement} prefixe="mft-a" />
                      {erreur && <Message variante="erreur" testId="mft-a-erreur">{erreur}</Message>}
                      <p><button type="submit" data-testid="mft-a-envoyer">Ajouter</button></p>
                    </form>
                  )}
                </FormulaireModal>
                <button type="button" data-testid="mft-soumettre" onClick={() => void soumettre()}>Soumettre ma feuille</button>
              </div>
            </>
          )}
        </div>
      )}

      <Tiroir ouvert={!!edition} onFermer={() => { setEdition(null); setComplement(COMPLEMENT_VIDE); }} titre={edition ? `Segment du ${edition.date}` : "Segment"} testId="mft-edition">
        {edition && (
          <form onSubmit={(e) => void corriger(e)}>
            <p>Origine : {origineEnMots(edition)}. Corriger un temps pointé exige un motif ; l'ancienne version reste à l'historique.{edition.source === "EXTERNE" ? " Corriger un temps proposé par une source tierce le confirme." : ""}</p>
            <p><label htmlFor="mft-e-debut">De</label><input id="mft-e-debut" type="time" required data-testid="mft-e-debut" {...champ("debut")} /></p>
            <p><label htmlFor="mft-e-fin">À</label><input id="mft-e-fin" type="time" required data-testid="mft-e-fin" {...champ("fin")} /></p>
            <p><label htmlFor="mft-e-type">Type d'heure</label><select id="mft-e-type" required data-testid="mft-e-type" {...champ("typeHeureCode")}>{optionsTypes}</select></p>
            <SaisieChamps champs={champs} valeur={complement} onChange={setComplement} prefixe="mft-e" />
            <p><label htmlFor="mft-e-motif">Motif</label><textarea id="mft-e-motif" rows={2} maxLength={500} data-testid="mft-e-motif" {...champ("motif")} /></p>
            {edition.version > 1 && (
              versions
                ? <ol aria-label="Versions du segment" data-testid="mft-e-versions">{versions.map((v) => <li key={v.id}>v{v.version} : {heureDe(v.debut)}–{heureDe(v.fin)}, {v.typeHeureCode}{v.motif ? ` — ${v.motif}` : ""}</li>)}</ol>
                : <p><button type="button" className="secondary" data-testid="mft-e-historique" onClick={() => void voirVersions(edition)}>Voir les versions précédentes</button></p>
            )}
            {erreur && <Message variante="erreur" testId="mft-e-erreur">{erreur}</Message>}
            <p className="eff-actions">
              <button type="submit" data-testid="mft-e-enregistrer">Enregistrer la correction</button>
              <button type="button" className="secondary" data-testid="mft-e-retirer" onClick={() => void retirer()}>Retirer le segment</button>
            </p>
          </form>
        )}
      </Tiroir>
    </PageMaquette>
  );
}
