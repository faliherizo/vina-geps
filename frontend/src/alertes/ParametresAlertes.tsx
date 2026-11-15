/**
 * **Paramètres des alertes** (CU-1, CU-2 ; FX-AO-20260810, critères 6, 10, 32) — les réponses
 * prédéfinies et les modèles de l'organisme. Un ajout vit dans un dialogue ; rien ne s'efface, tout se
 * désactive : une alerte passée garde ce qu'elle a proposé (R9). Un modèle se modifie en versions datées
 * et signées, et son périmètre borne les expéditeurs qui le voient (ST-G02). Les réglages de l'organisme
 * (copie par courriel, critère 8) et les groupes transverses (ST-G09) se tiennent ici aussi.
 */
import { useCallback, useEffect, useState, type FormEvent } from "react";
import { BadgeStatut, EnTeteEcran, EtatVide, Message, Tableau, type Colonne } from "../composants";
import { FormulaireModal } from "../Modal";
import { useApiEffectifs as useApi } from "../effectifs/api";
import { liste } from "../formeRecue";
import GroupesTransverses from "./GroupesTransverses";
import PolitiquesRelance from "./PolitiquesRelance";
import { PRIORITES, SEMANTIQUES, libellePriorite, libelleSemantique, quand, type Groupe, type Modele, type Priorite, type Reglages, type Reponse, type Semantique,
  type VersionModele } from "./types";

const COL_REPONSES: ReadonlyArray<Colonne<Reponse>> = [
  { id: "libelle", libelle: "Réponse", tri: (r) => r.libelle }, { id: "semantique", libelle: "Sens" },
  { id: "etat", libelle: "État" }, { id: "actions", libelle: "Actions", actions: true },
];
const COL_MODELES: ReadonlyArray<Colonne<Modele>> = [
  { id: "titre", libelle: "Modèle", tri: (m) => m.titre }, { id: "version", libelle: "Version", numerique: true }, { id: "priorite", libelle: "Priorité" },
  { id: "reponses", libelle: "Réponses proposées" }, { id: "perimetre", libelle: "Périmètre" }, { id: "etat", libelle: "État" }, { id: "actions", libelle: "Actions", actions: true },
];

export default function ParametresAlertes() {
  const { lire, envoyer, erreur, succes, setSucces } = useApi();
  const [reponses, setReponses] = useState<Reponse[]>([]);
  const [modeles, setModeles] = useState<Modele[]>([]);
  const [groupes, setGroupes] = useState<Groupe[]>([]);
  const [reglages, setReglages] = useState<Reglages | null>(null);
  const [enModification, setEnModification] = useState<Modele | null>(null);
  const [historique, setHistorique] = useState<{ modele: Modele; versions: VersionModele[] } | null>(null);
  const charger = useCallback(async () => {
    setReponses(liste<Reponse>(await lire<Reponse[]>("/api/alertes/reponses")));
    setModeles(liste<Modele>(await lire<Modele[]>("/api/alertes/modeles")));
    setGroupes(liste<Groupe>(await lire<Groupe[]>("/api/alertes/groupes")));
    setReglages(await lire<Reglages>("/api/alertes/reglages"));
  }, [lire]);
  useEffect(() => { void charger(); }, [charger]);

  const basculer = async (chemin: string, champ: "active" | "actif", valeur: boolean, nom: string) => {
    if (await envoyer(chemin, { [champ]: valeur }, "PATCH")) { setSucces(`« ${nom} » ${valeur ? "réactivé" : "désactivé"}.`); await charger(); }
  };
  const libelleDe = (id: string) => reponses.find((r) => r.id === id)?.libelle ?? id;
  const groupeDe = (id: string) => groupes.find((g) => g.id === id)?.nom ?? id;
  const voirVersions = async (m: Modele) => setHistorique({ modele: m, versions: liste<VersionModele>(await lire<VersionModele[]>(`/api/alertes/modeles/${m.id}/versions`)) });
  const enregistrerReglages = async (copieCourriel: boolean) => {
    const r = await envoyer<Reglages>("/api/alertes/reglages", { copieCourriel }, "PUT");
    if (r) { setReglages(r); setSucces(copieCourriel ? "Chaque destinataire recevra aussi une copie par courriel." : "La copie par courriel est retirée."); }
  };

  return (
    <div data-testid="alp-ecran">
      <EnTeteEcran titre="Paramètres des alertes" testId="alp-titre"
        description="Les réponses proposées aux destinataires et les modèles d'alerte de l'organisme. Rien ne s'efface : une alerte passée garde ce qu'elle a proposé." />
      {erreur && <Message variante="erreur" testId="alp-erreur">{erreur}</Message>}
      {succes && <Message variante="succes" testId="alp-succes">{succes}</Message>}

      <section className="card" aria-labelledby="alp-reponses-h">
        <h2 id="alp-reponses-h">Réponses prédéfinies</h2>
        <div className="eff-actions">
          <FormulaireModal libelle="Nouvelle réponse" titre="Nouvelle réponse prédéfinie" testId="alp-reponse" size="sm">
            {(fermer) => <FormulaireReponse envoyer={envoyer} apres={async (r) => { fermer(); setSucces(`Réponse « ${r.libelle} » créée.`); await charger(); }} />}
          </FormulaireModal>
        </div>
        <Tableau colonnes={COL_REPONSES} lignes={reponses} legende="Réponses prédéfinies" testId="alp-reponses"
          vide={<EtatVide titre="Aucune réponse prédéfinie" compact testId="alp-reponses-vide">Créez « J'arrive » (prise en charge) et « Indisponible » (refus) pour commencer.</EtatVide>}
          rendre={(r) => (
            <tr key={r.id} data-testid={`alp-reponse-${r.id}`}>
              <td>{r.libelle}</td><td>{libelleSemantique(r.semantique)}</td>
              <td>{r.active ? <BadgeStatut statut="active">Active</BadgeStatut> : <BadgeStatut statut="clos">Désactivée</BadgeStatut>}</td>
              <td><button type="button" className="secondary" data-testid={`alp-reponse-${r.id}-basculer`}
                onClick={() => void basculer(`/api/alertes/reponses/${r.id}`, "active", !r.active, r.libelle)}>
                {r.active ? "Désactiver" : "Réactiver"}<span className="sr-only"> la réponse {r.libelle}</span></button></td>
            </tr>
          )} />
      </section>

      <section className="card" aria-labelledby="alp-modeles-h">
        <h2 id="alp-modeles-h">Modèles d'alerte</h2>
        <div className="eff-actions">
          <FormulaireModal libelle="Nouveau modèle" titre="Nouveau modèle d'alerte" testId="alp-modele" size="md">
            {(fermer) => <FormulaireModele reponses={reponses.filter((r) => r.active)} groupes={groupes} envoyer={envoyer}
              apres={async (m) => { fermer(); setSucces(`Modèle « ${m.titre} » créé.`); await charger(); }} />}
          </FormulaireModal>
        </div>
        <Tableau colonnes={COL_MODELES} lignes={modeles} legende="Modèles d'alerte" testId="alp-modeles"
          vide={<EtatVide titre="Aucun modèle" compact testId="alp-modeles-vide">Un modèle prépare le titre, le texte, la priorité et les réponses d'une alerte fréquente.</EtatVide>}
          rendre={(m) => (
            <tr key={m.id} data-testid={`alp-modele-${m.id}`}>
              <td>{m.titre}</td><td className="num">{m.version ?? 1}</td><td>{libellePriorite(m.priorite)}</td><td>{m.reponseIds.map(libelleDe).join(" · ") || "—"}</td>
              <td>{(m.groupeIds ?? []).length === 0 ? "Tout l'organisme" : (m.groupeIds ?? []).map(groupeDe).join(" · ")}</td>
              <td>{m.actif ? <BadgeStatut statut="active">Actif</BadgeStatut> : <BadgeStatut statut="clos">Désactivé</BadgeStatut>}</td>
              <td><div className="eff-actions">
                <button type="button" className="secondary" data-testid={`alp-modele-${m.id}-modifier`} onClick={() => setEnModification(m)}>
                  Modifier<span className="sr-only"> le modèle {m.titre}</span></button>
                <button type="button" className="secondary" data-testid={`alp-modele-${m.id}-versions`} onClick={() => void voirVersions(m)}>
                  Versions<span className="sr-only"> du modèle {m.titre}</span></button>
                <button type="button" className="secondary" data-testid={`alp-modele-${m.id}-basculer`}
                  onClick={() => void basculer(`/api/alertes/modeles/${m.id}`, "actif", !m.actif, m.titre)}>
                  {m.actif ? "Désactiver" : "Réactiver"}<span className="sr-only"> le modèle {m.titre}</span></button>
              </div></td>
            </tr>
          )} />
        <FormulaireModal libelle="Modifier le modèle" titre={enModification ? `Modifier « ${enModification.titre} » (nouvelle version)` : "Modifier le modèle"}
          testId="alp-modification" size="md" ouvert={enModification !== null} onFermer={() => setEnModification(null)}>
          {enModification && <FormulaireModele initial={enModification} reponses={reponses.filter((r) => r.active)} groupes={groupes} envoyer={envoyer}
            apres={async (m) => { setEnModification(null); setSucces(`Modèle « ${m.titre} » modifié : version ${m.version ?? ""}.`); await charger(); }} />}
        </FormulaireModal>
        <FormulaireModal libelle="Versions du modèle" titre={historique ? `Versions de « ${historique.modele.titre} »` : "Versions"} testId="alp-versions" size="md"
          ouvert={historique !== null} onFermer={() => setHistorique(null)}>
          {historique && (
            <table className="eff-table-compacte" data-testid="alp-versions-table">
              <caption>Chaque version, la plus récente d'abord : une alerte garde celle dont elle est partie</caption>
              <thead><tr><th scope="col">Version</th><th scope="col">Titre</th><th scope="col">Priorité</th><th scope="col">Faite le</th><th scope="col">Par</th></tr></thead>
              <tbody>{historique.versions.map((v) => (
                <tr key={v.version}><th scope="row">{v.version}</th><td>{v.titre}</td><td>{libellePriorite(v.priorite)}</td><td>{quand(v.creeLe)}</td><td>{v.creePar}</td></tr>
              ))}</tbody>
            </table>
          )}
        </FormulaireModal>
      </section>

      <section className="card" aria-labelledby="alp-reglages-h" data-testid="alp-reglages">
        <h2 id="alp-reglages-h">Réglages de l'organisme</h2>
        {reglages && (
          <p className="checkbox-line">
            <input type="checkbox" id="alp-copie" data-testid="alp-copie" checked={reglages.copieCourriel} onChange={(e) => void enregistrerReglages(e.target.checked)} />
            <label htmlFor="alp-copie">Envoyer aussi une copie par courriel à chaque destinataire (sauf s'il l'a déjà reçue par courriel)</label>
          </p>
        )}
      </section>

      <PolitiquesRelance groupes={groupes} />

      <GroupesTransverses />
    </div>
  );
}

type Envoyer = ReturnType<typeof useApi>["envoyer"];

function FormulaireReponse({ envoyer, apres }: { envoyer: Envoyer; apres: (r: Reponse) => Promise<void> }) {
  const [libelle, setLibelle] = useState("");
  const [semantique, setSemantique] = useState<Semantique>("prise_en_charge");
  const soumettre = async (e: FormEvent) => {
    e.preventDefault();
    const r = await envoyer<Reponse>("/api/alertes/reponses", { libelle, semantique });
    if (r) await apres(r);
  };
  return (
    <form onSubmit={(e) => void soumettre(e)} aria-label="Nouvelle réponse prédéfinie">
      <p><label htmlFor="alp-r-libelle">Libellé</label>
        <input id="alp-r-libelle" data-testid="alp-r-libelle" required maxLength={60} value={libelle} onChange={(e) => setLibelle(e.target.value)} /></p>
      <p><label htmlFor="alp-r-semantique">Ce qu'elle veut dire</label>
        <select id="alp-r-semantique" data-testid="alp-r-semantique" value={semantique} onChange={(e) => setSemantique(e.target.value as Semantique)}>
          {SEMANTIQUES.map((s) => <option key={s.code} value={s.code}>{s.libelle}</option>)}
        </select></p>
      <p><button type="submit" className="primary" data-testid="alp-r-creer">Créer la réponse</button></p>
    </form>
  );
}

/** Créer un modèle, ou le modifier ({@code initial}) : la modification fait une version nouvelle. */
function FormulaireModele({ initial, reponses, groupes, envoyer, apres }: {
  initial?: Modele; reponses: Reponse[]; groupes: Groupe[]; envoyer: Envoyer; apres: (m: Modele) => Promise<void>;
}) {
  const [titre, setTitre] = useState(initial?.titre ?? "");
  const [corps, setCorps] = useState(initial?.corps ?? "");
  const [priorite, setPriorite] = useState<Priorite>(initial?.priorite ?? "urgente");
  const [choisies, setChoisies] = useState<string[]>(initial?.reponseIds ?? []);
  const [perimetre, setPerimetre] = useState<string[]>(initial?.groupeIds ?? []);
  const basculer = (id: string) => setChoisies((l) => (l.includes(id) ? l.filter((x) => x !== id) : [...l, id]));
  const basculerGroupe = (id: string) => setPerimetre((l) => (l.includes(id) ? l.filter((x) => x !== id) : [...l, id]));
  const soumettre = async (e: FormEvent) => {
    e.preventDefault();
    const corpsRequete = { titre, corps, priorite, reponseIds: choisies, groupeIds: perimetre };
    const m = initial ? await envoyer<Modele>(`/api/alertes/modeles/${initial.id}`, corpsRequete, "PUT") : await envoyer<Modele>("/api/alertes/modeles", corpsRequete);
    if (m) await apres(m);
  };
  return (
    <form onSubmit={(e) => void soumettre(e)} aria-label={initial ? "Modifier le modèle d'alerte" : "Nouveau modèle d'alerte"}>
      <p><label htmlFor="alp-m-titre">Titre</label>
        <input id="alp-m-titre" data-testid="alp-m-titre" required maxLength={120} value={titre} onChange={(e) => setTitre(e.target.value)} /></p>
      <p><label htmlFor="alp-m-corps">Texte</label>
        <textarea id="alp-m-corps" data-testid="alp-m-corps" required maxLength={2000} rows={4} value={corps} onChange={(e) => setCorps(e.target.value)} /></p>
      <p><label htmlFor="alp-m-priorite">Priorité</label>
        <select id="alp-m-priorite" data-testid="alp-m-priorite" value={priorite} onChange={(e) => setPriorite(e.target.value as Priorite)}>
          {PRIORITES.map((p) => <option key={p.code} value={p.code}>{p.libelle}</option>)}
        </select></p>
      <fieldset>
        <legend>Réponses proposées</legend>
        {reponses.length === 0 && <p className="muted">Aucune réponse active : créez-en d'abord.</p>}
        {reponses.map((r) => (
          <p key={r.id} className="checkbox-line">
            <input type="checkbox" id={`alp-m-r-${r.id}`} data-testid={`alp-m-r-${r.id}`} checked={choisies.includes(r.id)} onChange={() => basculer(r.id)} />
            <label htmlFor={`alp-m-r-${r.id}`}>{r.libelle} ({libelleSemantique(r.semantique)})</label>
          </p>
        ))}
      </fieldset>
      <fieldset>
        <legend>Périmètre : qui voit ce modèle</legend>
        <p className="muted">Aucun groupe coché : tous les expéditeurs de l'organisme le voient.</p>
        {groupes.map((g) => (
          <p key={g.id} className="checkbox-line">
            <input type="checkbox" id={`alp-m-g-${g.id}`} data-testid={`alp-m-g-${g.id}`} checked={perimetre.includes(g.id)} onChange={() => basculerGroupe(g.id)} />
            <label htmlFor={`alp-m-g-${g.id}`}>{g.nom}</label>
          </p>
        ))}
      </fieldset>
      <p><button type="submit" className="primary" data-testid="alp-m-creer">{initial ? "Enregistrer une nouvelle version" : "Créer le modèle"}</button></p>
    </form>
  );
}
