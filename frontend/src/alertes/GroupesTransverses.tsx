/**
 * **Groupes transverses** (FX-AO-20260810, ST-G09, critère 27) — des groupes de spécialité, de garde ou de
 * comité, hors des unités, que l'organisme forme lui-même. Ils paraissent dans la messagerie, les listes de
 * distribution et les alertes comme les unités. L'appartenance est datée (une garde commence et finit) ; une
 * modératrice tient la liste de son groupe. Un groupe se désactive, jamais ne s'efface.
 */
import { useCallback, useEffect, useState, type FormEvent } from "react";
import ChampPersonne, { type PersonneSuggeree } from "../ChampPersonne";
import { BadgeStatut, EtatVide, Message, Tableau, type Colonne } from "../composants";
import { FormulaireModal } from "../Modal";
import { useApiEffectifs as useApi } from "../effectifs/api";
import { liste } from "../formeRecue";

export type NatureGroupe = "specialite" | "garde" | "comite";
export const NATURES: ReadonlyArray<{ code: NatureGroupe; libelle: string }> = [
  { code: "garde", libelle: "Garde" }, { code: "specialite", libelle: "Spécialité" }, { code: "comite", libelle: "Comité" },
];
export const libelleNature = (n: string) => NATURES.find((x) => x.code === n)?.libelle ?? n;

export interface Membre { personId: string; nom: string; depuis: string | null; jusqua: string | null; moderateur: boolean }
export interface GroupeTransverse { id: string; nom: string; nature: NatureGroupe; actif: boolean; membres: Membre[] }

const RACINE = "/api/communication/groupes-transverses";

/** La recherche des personnes à inscrire rend { id, nom } : le champ « personne » attend sa propre forme. */
export const adapterPersonnes = (brut: unknown): PersonneSuggeree[] =>
  (Array.isArray(brut) ? brut : []).map((p: { id: string; nom: string }) => ({ id: p.id, nom: p.nom, titre: null, courriel: null, statut: "active" }));

/** « du 2031-03-01 au 2031-03-07 », « depuis le … », « jusqu'au … », ou « sans date ». */
export function periode(m: Pick<Membre, "depuis" | "jusqua">): string {
  if (m.depuis && m.jusqua) return `du ${m.depuis} au ${m.jusqua}`;
  if (m.depuis) return `depuis le ${m.depuis}`;
  if (m.jusqua) return `jusqu'au ${m.jusqua}`;
  return "sans date";
}

const COLONNES: ReadonlyArray<Colonne<GroupeTransverse>> = [
  { id: "nom", libelle: "Groupe", tri: (g) => g.nom }, { id: "nature", libelle: "Nature" },
  { id: "membres", libelle: "Membres", numerique: true }, { id: "etat", libelle: "État" }, { id: "actions", libelle: "Actions", actions: true },
];

export default function GroupesTransverses() {
  const { lire, envoyer, erreur, setErreur } = useApi();
  const [groupes, setGroupes] = useState<GroupeTransverse[]>([]);
  const [info, setInfo] = useState("");
  const [gere, setGere] = useState<GroupeTransverse | null>(null);
  const charger = useCallback(async () => setGroupes(liste<GroupeTransverse>(await lire<GroupeTransverse[]>(RACINE))), [lire]);
  useEffect(() => { void charger(); }, [charger]);

  /** Le serveur rend le groupe à jour : il remplace celui de la liste et celui du dialogue. */
  const apres = async (g: GroupeTransverse | null, message: string) => {
    if (!g) return false;
    setInfo(message);
    setGere((x) => (x && x.id === g.id ? g : x));
    await charger();
    return true;
  };

  return (
    <section className="card" aria-labelledby="alp-gt-h" data-testid="alp-gt">
      <h2 id="alp-gt-h">Groupes transverses</h2>
      <p className="muted">Gardes, spécialités, comités : des groupes hors des unités, que la messagerie, les listes et les alertes proposent comme les unités.</p>
      {info && <Message variante="succes" testId="alp-gt-info">{info}</Message>}
      {erreur && !gere && <Message variante="erreur" testId="alp-gt-erreur">{erreur}</Message>}
      <div className="eff-actions">
        <FormulaireModal libelle="Nouveau groupe" titre="Nouveau groupe transverse" testId="alp-gt-nouveau" size="sm">
          {(fermer) => <NouveauGroupe envoyer={envoyer} apres={async (g) => { if (await apres(g, `Groupe « ${g?.nom} » créé.`)) fermer(); }} />}
        </FormulaireModal>
      </div>
      <Tableau colonnes={COLONNES} lignes={groupes} legende="Groupes transverses" testId="alp-gt-table"
        vide={<EtatVide titre="Aucun groupe transverse" compact testId="alp-gt-vide">Créez par exemple « Garde de nuit » ou « Comité de soins palliatifs ».</EtatVide>}
        rendre={(g) => (
          <tr key={g.id} data-testid={`alp-gt-${g.id}`}>
            <td>{g.nom}</td><td>{libelleNature(g.nature)}</td><td className="num">{g.membres.length}</td>
            <td>{g.actif ? <BadgeStatut statut="active">Actif</BadgeStatut> : <BadgeStatut statut="clos">Désactivé</BadgeStatut>}</td>
            <td><button type="button" className="secondary" data-testid={`alp-gt-${g.id}-gerer`} onClick={() => { setErreur(""); setGere(g); }}>
              Gérer<span className="sr-only"> le groupe {g.nom}</span></button></td>
          </tr>
        )} />
      <FormulaireModal libelle="Gérer le groupe" titre={gere ? `Groupe « ${gere.nom} »` : "Groupe"} testId="alp-gt-gestion" size="md"
        ouvert={gere !== null} onFermer={() => setGere(null)}>
        {gere && <GestionGroupe groupe={gere} apres={apres} />}
      </FormulaireModal>
    </section>
  );
}

type Envoyer = ReturnType<typeof useApi>["envoyer"];

function NouveauGroupe({ envoyer, apres }: { envoyer: Envoyer; apres: (g: GroupeTransverse | null) => Promise<void> }) {
  const [nom, setNom] = useState("");
  const [nature, setNature] = useState<NatureGroupe>("garde");
  const soumettre = async (e: FormEvent) => {
    e.preventDefault();
    await apres(await envoyer<GroupeTransverse>(RACINE, { nom, nature }));
  };
  return (
    <form onSubmit={(e) => void soumettre(e)} aria-label="Nouveau groupe transverse">
      <p><label htmlFor="alp-gt-nom">Nom</label>
        <input id="alp-gt-nom" data-testid="alp-gt-nom" required maxLength={80} value={nom} onChange={(e) => setNom(e.target.value)} /></p>
      <p><label htmlFor="alp-gt-nature">Nature</label>
        <select id="alp-gt-nature" data-testid="alp-gt-nature" value={nature} onChange={(e) => setNature(e.target.value as NatureGroupe)}>
          {NATURES.map((n) => <option key={n.code} value={n.code}>{n.libelle}</option>)}
        </select></p>
      <p><button type="submit" className="primary" data-testid="alp-gt-creer">Créer le groupe</button></p>
    </form>
  );
}

/** Renommer, désactiver ; inscrire une personne pour une période ; la retirer. */
function GestionGroupe({ groupe, apres }: { groupe: GroupeTransverse; apres: (g: GroupeTransverse | null, message: string) => Promise<boolean> }) {
  const { envoyer, apiFetch, erreur, setErreur } = useApi();
  const [nom, setNom] = useState(groupe.nom);
  const [inscription, setInscription] = useState({ personId: "", depuis: "", jusqua: "", moderateur: false });
  const chemin = `${RACINE}/${encodeURIComponent(groupe.id)}`;

  const renommer = async (e: FormEvent) => {
    e.preventDefault();
    await apres(await envoyer<GroupeTransverse>(chemin, { nom, actif: groupe.actif }, "PATCH"), `Groupe « ${nom} » enregistré.`);
  };
  const basculer = async () => {
    await apres(await envoyer<GroupeTransverse>(chemin, { nom: groupe.nom, actif: !groupe.actif }, "PATCH"),
      groupe.actif ? `Groupe « ${groupe.nom} » désactivé : il n'est plus proposé.` : `Groupe « ${groupe.nom} » réactivé.`);
  };
  const inscrire = async (e: FormEvent) => {
    e.preventDefault();
    const { personId, ...periodeEtRole } = inscription;
    const g = await envoyer<GroupeTransverse>(`${chemin}/membres/${encodeURIComponent(personId)}`, periodeEtRole, "PUT");
    if (await apres(g, "Inscription enregistrée.")) setInscription({ personId: "", depuis: "", jusqua: "", moderateur: false });
  };
  const retirer = async (m: Membre) => {
    setErreur("");
    const r = await apiFetch(`${chemin}/membres/${encodeURIComponent(m.personId)}`, { method: "DELETE" });
    const j = await r.json().catch(() => null);
    if (!r.ok) { setErreur(j?.errors?.join(", ") || `Erreur ${r.status}`); return; }
    await apres(j as GroupeTransverse, `${m.nom} retiré du groupe.`);
  };

  return (
    <div data-testid="alp-gt-fiche">
      {erreur && <Message variante="erreur" testId="alp-gt-fiche-erreur">{erreur}</Message>}
      <form onSubmit={(e) => void renommer(e)} aria-label="Renommer le groupe" className="eff-barre">
        <p><label htmlFor="alp-gt-renommer">Nom du groupe</label>
          <input id="alp-gt-renommer" data-testid="alp-gt-renommer" required maxLength={80} value={nom} onChange={(e) => setNom(e.target.value)} /></p>
        <p><button type="submit" className="secondary" data-testid="alp-gt-enregistrer">Enregistrer le nom</button></p>
        <p><button type="button" className="secondary" data-testid="alp-gt-basculer" onClick={() => void basculer()}>
          {groupe.actif ? "Désactiver le groupe" : "Réactiver le groupe"}</button></p>
      </form>
      {groupe.membres.length === 0
        ? <p data-testid="alp-gt-membres-vide">Personne n'est encore inscrit.</p>
        : (
          <table className="eff-table-compacte" data-testid="alp-gt-membres">
            <caption>Membres du groupe {groupe.nom}</caption>
            <thead><tr><th scope="col">Personne</th><th scope="col">Période</th><th scope="col">Rôle</th><th scope="col">Action</th></tr></thead>
            <tbody>{groupe.membres.map((m) => (
              <tr key={m.personId} data-testid={`alp-gt-membre-${m.personId}`}>
                <th scope="row">{m.nom}</th><td>{periode(m)}</td><td>{m.moderateur ? "Modératrice ou modérateur" : "Membre"}</td>
                <td><button type="button" className="secondary" data-testid={`alp-gt-membre-${m.personId}-retirer`} onClick={() => void retirer(m)}>
                  Retirer<span className="sr-only"> {m.nom} du groupe</span></button></td>
              </tr>
            ))}</tbody>
          </table>
        )}
      <form onSubmit={(e) => void inscrire(e)} aria-label="Inscrire une personne" className="grid-form">
        <div className="field field-full">
          <ChampPersonne libelle="Personne à inscrire" testId="alp-gt-personne" requis placeholder="Nom (3 caractères minimum)" valeur={inscription.personId}
            onChoisir={(id) => setInscription((s) => ({ ...s, personId: id }))} appel={apiFetch}
            source={{ suggerer: `${RACINE}/personnes`, adapter: adapterPersonnes }} />
        </div>
        <label className="field" htmlFor="alp-gt-depuis"><span>Du (facultatif)</span>
          <input id="alp-gt-depuis" type="date" data-testid="alp-gt-depuis" value={inscription.depuis} onChange={(e) => setInscription((s) => ({ ...s, depuis: e.target.value }))} /></label>
        <label className="field" htmlFor="alp-gt-jusqua"><span>Au (facultatif)</span>
          <input id="alp-gt-jusqua" type="date" data-testid="alp-gt-jusqua" value={inscription.jusqua} onChange={(e) => setInscription((s) => ({ ...s, jusqua: e.target.value }))} /></label>
        <p className="checkbox-line field-full">
          <input type="checkbox" id="alp-gt-moderateur" data-testid="alp-gt-moderateur" checked={inscription.moderateur}
            onChange={(e) => setInscription((s) => ({ ...s, moderateur: e.target.checked }))} />
          <label htmlFor="alp-gt-moderateur">Modératrice ou modérateur du groupe</label>
        </p>
        <div className="field field-actions"><button type="submit" className="primary" data-testid="alp-gt-inscrire" disabled={!inscription.personId}>Inscrire</button></div>
      </form>
    </div>
  );
}
