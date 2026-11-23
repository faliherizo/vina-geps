/**
 * **Mon équipe** — ce que Nexu VINA donnait au gestionnaire, dans VINA GePS : la liste des personnes
 * qui relèvent de lui (directement ou non), leur réorganisation dans ses bornes, les feuilles de
 * temps qui attendent sa décision, et sa **suppléance** quand il s'absente.
 *
 * Le cloisonnement est au serveur (`/api/personnel/equipe`) : un gestionnaire ne reçoit jamais une
 * personne hors de sa chaîne, et ne peut rattacher, réaffecter ou détacher qu'à l'intérieur.
 * Pendant l'absence déclarée, les feuilles qui lui reviennent partent vers son suppléant, et
 * l'employé le lit sur sa feuille (« en son absence : … »).
 */
import { useCallback, useEffect, useState, type FormEvent } from "react";
import { useAuth } from "../../AuthContext";
import ChampPersonne from "../../ChampPersonne";
import { EtatVide, Message, Tableau, type Colonne } from "../../composants";
import { liste, objet } from "../../formeRecue";
import { ConfirmModal, FormulaireModal } from "../../Modal";
import { lienVersPage } from "../../navigation";
import { useApiEffectifs } from "../api";
import { statutEnMots, type Feuille } from "../temps/types";

export interface Membre { id: string; nom: string; titre: string | null; courriel: string | null; superieurId: string | null; superieurNom: string | null; niveau: number }
export interface Equipe { moiId: string | null; moiNom: string | null; membres: Membre[]; sansSuperieur: Membre[] }
export interface Delegation { id: string; toUserId: string; toNom?: string; fromDate: string | null; toDate: string | null }

/** Les rôles qui corrigent une fiche (`employee.update`) : eux seuls réorganisent leur équipe. */
const GERENT = ["admin", "rh", "manager"];

/** Où en est une délégation aujourd'hui : dit en mots, jamais par la seule couleur. */
export function etatDelegation(d: Pick<Delegation, "fromDate" | "toDate">, aujourdhui: string): "à venir" | "en cours" | "terminée" {
  if (d.fromDate && aujourdhui < d.fromDate) return "à venir";
  if (d.toDate && aujourdhui > d.toDate) return "terminée";
  return "en cours";
}

const niveauEnMots = (m: Membre) => (m.niveau === 1 ? "Direct" : `Indirect (niveau ${m.niveau})`);

const COLONNES: ReadonlyArray<Colonne<Membre>> = [
  { id: "nom", libelle: "Personne", tri: (m) => m.nom },
  { id: "titre", libelle: "Poste" },
  { id: "superieur", libelle: "Supérieur direct", tri: (m) => m.superieurNom ?? "" },
  { id: "niveau", libelle: "Lien", tri: (m) => m.niveau },
  { id: "actions", libelle: "Actions", actions: true },
];

export default function MonEquipe() {
  const { user } = useAuth();
  const { lire, envoyer, erreur, setErreur, apiFetch } = useApiEffectifs();
  const [equipe, setEquipe] = useState<Equipe | null>(null);
  const [info, setInfo] = useState("");
  const peutGerer = GERENT.includes(user?.role ?? "");

  const charger = useCallback(async () => { setEquipe(objet<Equipe>(await lire<Equipe>("/api/personnel/equipe"))); }, [lire]);
  useEffect(() => { void charger(); }, [charger]);

  /** Rattacher, réaffecter ou détacher : le serveur rend l'équipe à jour. */
  const designer = async (m: Membre, superieurId: string | null, message: string) => {
    setInfo("");
    const r = await envoyer<Equipe>(`/api/personnel/equipe/${encodeURIComponent(m.id)}/superieur`, { superieurId }, "PUT");
    if (!r) return false;
    setEquipe(r);
    setInfo(message);
    return true;
  };

  if (!equipe) return erreur ? <Message variante="erreur" testId="meq-erreur">{erreur}</Message> : <p role="status">Chargement de votre équipe…</p>;

  return (
    <>
      {info && <Message variante="succes" testId="meq-info">{info}</Message>}
      {erreur && <Message variante="erreur" testId="meq-erreur">{erreur}</Message>}
      {equipe.moiId === null
        ? <EtatVide titre="Votre compte n'est relié à aucune fiche du personnel" testId="meq-sans-fiche">Demandez à l'administration de relier votre compte à votre fiche : votre équipe se lit dans l'organigramme.</EtatVide>
        : <Membres equipe={equipe} peutGerer={peutGerer} designer={designer} effacer={() => setErreur("")} />}
      <FeuillesAApprouver apiFetch={apiFetch} />
      <Suppleance />
    </>
  );
}

function Membres({ equipe, peutGerer, designer, effacer }: {
  equipe: Equipe; peutGerer: boolean; designer: (m: Membre, s: string | null, message: string) => Promise<boolean>; effacer: () => void;
}) {
  const [changement, setChangement] = useState<Membre | null>(null);
  const [nouveau, setNouveau] = useState("");
  const [aDetacher, setADetacher] = useState<Membre | null>(null);
  const [aRattacher, setARattacher] = useState("");
  const moi = { id: equipe.moiId!, nom: `${equipe.moiNom} (moi)` };
  // Le nouveau supérieur : moi, ou un membre — jamais la personne elle-même.
  const choix = (m: Membre | null) => [moi, ...equipe.membres.filter((x) => x.id !== m?.id)];
  const nomDe = (id: string) => choix(null).find((c) => c.id === id)?.nom ?? id;

  const changer = async (e: FormEvent) => {
    e.preventDefault();
    if (changement && await designer(changement, nouveau, `${changement.nom} relève maintenant de ${nomDe(nouveau)}.`)) setChangement(null);
  };
  const rattacher = async (e: FormEvent, fermer: () => void) => {
    e.preventDefault();
    const m = equipe.sansSuperieur.find((x) => x.id === aRattacher);
    if (m && await designer(m, equipe.moiId, `${m.nom} rejoint votre équipe.`)) { setARattacher(""); fermer(); }
  };

  return (
    <section aria-labelledby="meq-membres-titre" data-testid="meq-membres">
      <h2 id="meq-membres-titre">Les membres de mon équipe ({equipe.membres.length})</h2>
      <p>Les personnes qui relèvent de vous, directement ou par un de vos subordonnés, d'après l'organigramme.</p>
      {peutGerer && (
        <div className="eff-actions">
          <FormulaireModal libelle="Rattacher une personne" titre="Rattacher une personne à mon équipe" testId="meq-rattacher" size="md" className="secondary"
            desactive={equipe.sansSuperieur.length === 0} raison="Aucune personne sans supérieur à rattacher">
            {(fermer) => (
              <form onSubmit={(e) => void rattacher(e, fermer)} aria-label="Rattacher une personne">
                <p>Seules les personnes qui n'ont encore aucun supérieur se rattachent ici ; les autres se demandent à leur gestionnaire actuel.</p>
                <p>
                  <label htmlFor="meq-rattacher-personne">Personne</label>
                  <select id="meq-rattacher-personne" required data-testid="meq-rattacher-personne" value={aRattacher} onChange={(e) => { effacer(); setARattacher(e.target.value); }}>
                    <option value="">— choisir —</option>
                    {equipe.sansSuperieur.map((m) => <option key={m.id} value={m.id}>{m.nom}{m.titre ? ` — ${m.titre}` : ""}</option>)}
                  </select>
                </p>
                <p><button type="submit" data-testid="meq-rattacher-enregistrer">Rattacher à mon équipe</button></p>
              </form>
            )}
          </FormulaireModal>
        </div>
      )}
      <Tableau colonnes={COLONNES} lignes={equipe.membres} legende="Membres de mon équipe" testId="meq-table"
        vide={<EtatVide titre="Personne ne relève de vous" compact testId="meq-vide">L'organigramme ne vous donne encore aucun subordonné.</EtatVide>}
        rendre={(m) => (
          <tr key={m.id} data-testid={`meq-membre-${m.id}`}>
            <td>{m.nom}{m.courriel ? <><br /><small>{m.courriel}</small></> : null}</td>
            <td>{m.titre ?? "—"}</td>
            <td>{m.superieurNom ?? "—"}</td>
            <td>{niveauEnMots(m)}</td>
            <td>
              {peutGerer && (
                <div className="eff-actions">
                  <button type="button" className="secondary" data-testid={`meq-${m.id}-changer`} onClick={() => { effacer(); setNouveau(m.superieurId ?? ""); setChangement(m); }}>
                    Changer de supérieur<span className="sr-only"> pour {m.nom}</span>
                  </button>
                  {m.niveau === 1 && (
                    <button type="button" className="secondary" data-testid={`meq-${m.id}-detacher`} onClick={() => { effacer(); setADetacher(m); }}>
                      Détacher<span className="sr-only"> {m.nom} de mon équipe</span>
                    </button>
                  )}
                </div>
              )}
            </td>
          </tr>
        )} />
      <FormulaireModal libelle="Changer de supérieur" titre={changement ? `Nouveau supérieur de ${changement.nom}` : "Changer de supérieur"} testId="meq-changement"
        ouvert={changement !== null} onFermer={() => setChangement(null)} size="md">
        {changement && (
          <form onSubmit={(e) => void changer(e)} aria-label="Changer de supérieur">
            <p>
              <label htmlFor="meq-nouveau">Nouveau supérieur direct</label>
              <select id="meq-nouveau" required data-testid="meq-nouveau" value={nouveau} onChange={(e) => setNouveau(e.target.value)}>
                {choix(changement).map((c) => <option key={c.id} value={c.id}>{c.nom}</option>)}
              </select>
            </p>
            <p><button type="submit" data-testid="meq-changer-enregistrer">Enregistrer</button></p>
          </form>
        )}
      </FormulaireModal>
      <ConfirmModal open={aDetacher !== null} title="Détacher de mon équipe" variant="danger" confirmLabel="Détacher" testId="meq-detacher"
        message={`${aDetacher?.nom ?? ""} n'aura plus de supérieur : ses feuilles iront au niveau GESTION de son unité jusqu'à un nouveau rattachement.`}
        onConfirm={async () => { if (aDetacher && await designer(aDetacher, null, `${aDetacher.nom} n'est plus dans votre équipe.`)) setADetacher(null); }}
        onCancel={() => setADetacher(null)} />
    </section>
  );
}

/** Les feuilles qui attendent ma décision : celles de mon équipe, et celles que je reçois en suppléance. */
function FeuillesAApprouver({ apiFetch }: { apiFetch: (chemin: string, init?: RequestInit) => Promise<Response> }) {
  const [feuilles, setFeuilles] = useState<Feuille[] | null>(null);
  useEffect(() => {
    // Sans le module des feuilles, ou sans le droit de les décider, la section n'a rien à dire : elle se tait.
    apiFetch("/api/effectifs/temps/feuilles?statut=SOUMISE").then(async (r) => setFeuilles(r.ok ? liste<Feuille>(await r.json()) : null)).catch(() => setFeuilles(null));
  }, [apiFetch]);
  if (feuilles === null) return null;
  return (
    <section aria-labelledby="meq-feuilles-titre" data-testid="meq-feuilles">
      <h2 id="meq-feuilles-titre">Feuilles de temps à approuver ({feuilles.length})</h2>
      {feuilles.length === 0
        ? <p data-testid="meq-feuilles-vide">Aucune feuille n'attend votre décision.</p>
        : (
          <table className="eff-table-compacte" data-testid="meq-feuilles-table">
            <caption>Feuilles soumises qui attendent votre décision</caption>
            <thead><tr><th scope="col">Personne</th><th scope="col">Période</th><th scope="col">Statut</th></tr></thead>
            <tbody>{feuilles.map((f) => <tr key={f.id}><th scope="row">{f.nom} ({f.matricule})</th><td>{f.debut} au {f.fin}</td><td>{statutEnMots(f)}</td></tr>)}</tbody>
          </table>
        )}
      <p><a className="btn secondary" href={lienVersPage("ustemps")} data-testid="meq-vers-console">Ouvrir la console des feuilles pour décider</a></p>
    </section>
  );
}

/** Mon absence : à qui je confie mes approbations, pour quelle période, et y mettre fin à mon retour. */
function Suppleance() {
  const { lire, supprimer, apiFetch } = useApiEffectifs();
  const [delegations, setDelegations] = useState<Delegation[]>([]);
  const [saisie, setSaisie] = useState({ toUserId: "", fromDate: "", toDate: "" });
  const [info, setInfo] = useState("");
  const [erreur, setErreur] = useState("");
  const [aTerminer, setATerminer] = useState<Delegation | null>(null);
  const aujourdhui = new Date().toISOString().slice(0, 10);
  const charger = useCallback(async () => { setDelegations(liste<Delegation>(await lire<Delegation[]>("/api/approvals/delegations?mine=1"))); }, [lire]);
  useEffect(() => { void charger(); }, [charger]);

  const deleguer = async (e: FormEvent, fermer: () => void) => {
    e.preventDefault();
    setInfo(""); setErreur("");
    const corps = { toUserId: saisie.toUserId, fromDate: saisie.fromDate || undefined, toDate: saisie.toDate || undefined };
    const r = await apiFetch("/api/approvals/delegations", { method: "POST", headers: { "Content-Type": "application/json" }, body: JSON.stringify(corps) });
    if (!r.ok) { const j = await r.json().catch(() => ({})); setErreur(j.errors?.join(", ") || `Erreur ${r.status}`); return; }
    setInfo("Suppléance enregistrée : pendant cette période, les feuilles qui vous reviennent partent aussi vers votre suppléant, qui en est avisé.");
    setSaisie({ toUserId: "", fromDate: "", toDate: "" });
    fermer();
    await charger();
  };
  const terminer = async (d: Delegation) => {
    setATerminer(null);
    if (await supprimer(`/api/approvals/delegations/${encodeURIComponent(d.id)}`)) { setInfo(`Suppléance de ${d.toNom || d.toUserId} terminée : vos feuilles vous reviennent.`); await charger(); }
  };

  return (
    <section aria-labelledby="meq-suppleance-titre" data-testid="meq-suppleance-section">
      <h2 id="meq-suppleance-titre">Mon absence : désigner un suppléant</h2>
      <p>Vacances, formation, congé : confiez vos approbations à un collègue pour une période. Les feuilles de votre équipe lui parviennent, l'employé voit vers qui sa feuille est partie, et chaque décision est tracée « pour le compte de » vous. Le suppléant doit pouvoir approuver des feuilles (gestion, direction ou administration).</p>
      {info && <Message variante="succes" testId="meq-suppleance-info">{info}</Message>}
      <div className="eff-actions">
        <FormulaireModal libelle="Désigner un suppléant" titre="Désigner un suppléant pendant mon absence" testId="meq-suppleance" size="md" className="secondary">
          {(fermer) => (
            <form onSubmit={(e) => void deleguer(e, fermer)} aria-label="Désigner un suppléant" className="grid-form">
              <div className="field field-full">
                <ChampPersonne libelle="Suppléant" testId="meq-suppleant" requis placeholder="Nom du collègue (3 caractères minimum)" valeur={saisie.toUserId}
                  onChoisir={(id) => setSaisie((s) => ({ ...s, toUserId: id }))} appel={apiFetch} source={{ suggerer: "/api/approvals/delegataires" }} />
              </div>
              <label className="field" htmlFor="meq-du"><span>Du (facultatif)</span>
                <input id="meq-du" type="date" data-testid="meq-du" value={saisie.fromDate} onChange={(e) => setSaisie((s) => ({ ...s, fromDate: e.target.value }))} /></label>
              <label className="field" htmlFor="meq-au"><span>Au (facultatif)</span>
                <input id="meq-au" type="date" data-testid="meq-au" value={saisie.toDate} onChange={(e) => setSaisie((s) => ({ ...s, toDate: e.target.value }))} /></label>
              {erreur && <div className="field field-full"><Message variante="erreur" testId="meq-suppleance-erreur">{erreur}</Message></div>}
              <div className="field field-actions"><button type="submit" className="primary" data-testid="meq-deleguer" disabled={!saisie.toUserId}>Désigner ce suppléant</button></div>
            </form>
          )}
        </FormulaireModal>
      </div>
      {delegations.length > 0 && (
        <table className="eff-table-compacte" data-testid="meq-delegations">
          <caption>Mes suppléances</caption>
          <thead><tr><th scope="col">Suppléant</th><th scope="col">Du</th><th scope="col">Au</th><th scope="col">État</th><th scope="col">Action</th></tr></thead>
          <tbody>
            {delegations.map((d) => (
              <tr key={d.id} data-testid={`meq-deleg-${d.id}`}>
                <th scope="row">{d.toNom || d.toUserId}</th><td>{d.fromDate ?? "—"}</td><td>{d.toDate ?? "sans date de fin"}</td><td>{etatDelegation(d, aujourdhui)}</td>
                <td>
                  {etatDelegation(d, aujourdhui) !== "terminée" && (
                    <button type="button" className="secondary" data-testid={`meq-deleg-${d.id}-terminer`} onClick={() => setATerminer(d)}>
                      Mettre fin<span className="sr-only"> à la suppléance de {d.toNom || d.toUserId}</span>
                    </button>
                  )}
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      )}
      <ConfirmModal open={aTerminer !== null} title="Mettre fin à la suppléance" confirmLabel="Mettre fin" testId="meq-fin"
        message={`Les feuilles de votre équipe ne parviendront plus à ${aTerminer?.toNom || aTerminer?.toUserId || ""}.`}
        onConfirm={() => (aTerminer ? terminer(aTerminer) : undefined)} onCancel={() => setATerminer(null)} />
    </section>
  );
}
