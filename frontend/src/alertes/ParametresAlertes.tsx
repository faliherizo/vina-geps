/**
 * **Paramètres des alertes** (CU-1, CU-2 ; FX-AO-20260810, critères 6, 10, 32) — les réponses
 * prédéfinies et les modèles de l'organisme. Un ajout vit dans un dialogue ; rien ne s'efface, tout se
 * désactive : une alerte passée garde ce qu'elle a proposé (R9).
 */
import { useCallback, useEffect, useState, type FormEvent } from "react";
import { BadgeStatut, EnTeteEcran, EtatVide, Message, Tableau, type Colonne } from "../composants";
import { FormulaireModal } from "../Modal";
import { useApiEffectifs as useApi } from "../effectifs/api";
import { liste } from "../formeRecue";
import { PRIORITES, SEMANTIQUES, libellePriorite, libelleSemantique, type Modele, type Priorite, type Reponse, type Semantique } from "./types";

const COL_REPONSES: ReadonlyArray<Colonne<Reponse>> = [
  { id: "libelle", libelle: "Réponse", tri: (r) => r.libelle }, { id: "semantique", libelle: "Sens" },
  { id: "etat", libelle: "État" }, { id: "actions", libelle: "Actions", actions: true },
];
const COL_MODELES: ReadonlyArray<Colonne<Modele>> = [
  { id: "titre", libelle: "Modèle", tri: (m) => m.titre }, { id: "priorite", libelle: "Priorité" },
  { id: "reponses", libelle: "Réponses proposées" }, { id: "etat", libelle: "État" }, { id: "actions", libelle: "Actions", actions: true },
];

export default function ParametresAlertes() {
  const { lire, envoyer, erreur, succes, setSucces } = useApi();
  const [reponses, setReponses] = useState<Reponse[]>([]);
  const [modeles, setModeles] = useState<Modele[]>([]);
  const charger = useCallback(async () => {
    setReponses(liste<Reponse>(await lire<Reponse[]>("/api/alertes/reponses")));
    setModeles(liste<Modele>(await lire<Modele[]>("/api/alertes/modeles")));
  }, [lire]);
  useEffect(() => { void charger(); }, [charger]);

  const basculer = async (chemin: string, champ: "active" | "actif", valeur: boolean, nom: string) => {
    if (await envoyer(chemin, { [champ]: valeur }, "PATCH")) { setSucces(`« ${nom} » ${valeur ? "réactivé" : "désactivé"}.`); await charger(); }
  };
  const libelleDe = (id: string) => reponses.find((r) => r.id === id)?.libelle ?? id;

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
            {(fermer) => <FormulaireModele reponses={reponses.filter((r) => r.active)} envoyer={envoyer}
              apres={async (m) => { fermer(); setSucces(`Modèle « ${m.titre} » créé.`); await charger(); }} />}
          </FormulaireModal>
        </div>
        <Tableau colonnes={COL_MODELES} lignes={modeles} legende="Modèles d'alerte" testId="alp-modeles"
          vide={<EtatVide titre="Aucun modèle" compact testId="alp-modeles-vide">Un modèle prépare le titre, le texte, la priorité et les réponses d'une alerte fréquente.</EtatVide>}
          rendre={(m) => (
            <tr key={m.id} data-testid={`alp-modele-${m.id}`}>
              <td>{m.titre}</td><td>{libellePriorite(m.priorite)}</td><td>{m.reponseIds.map(libelleDe).join(" · ") || "—"}</td>
              <td>{m.actif ? <BadgeStatut statut="active">Actif</BadgeStatut> : <BadgeStatut statut="clos">Désactivé</BadgeStatut>}</td>
              <td><button type="button" className="secondary" data-testid={`alp-modele-${m.id}-basculer`}
                onClick={() => void basculer(`/api/alertes/modeles/${m.id}`, "actif", !m.actif, m.titre)}>
                {m.actif ? "Désactiver" : "Réactiver"}<span className="sr-only"> le modèle {m.titre}</span></button></td>
            </tr>
          )} />
      </section>
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

function FormulaireModele({ reponses, envoyer, apres }: { reponses: Reponse[]; envoyer: Envoyer; apres: (m: Modele) => Promise<void> }) {
  const [titre, setTitre] = useState("");
  const [corps, setCorps] = useState("");
  const [priorite, setPriorite] = useState<Priorite>("urgente");
  const [choisies, setChoisies] = useState<string[]>([]);
  const basculer = (id: string) => setChoisies((l) => (l.includes(id) ? l.filter((x) => x !== id) : [...l, id]));
  const soumettre = async (e: FormEvent) => {
    e.preventDefault();
    const m = await envoyer<Modele>("/api/alertes/modeles", { titre, corps, priorite, reponseIds: choisies });
    if (m) await apres(m);
  };
  return (
    <form onSubmit={(e) => void soumettre(e)} aria-label="Nouveau modèle d'alerte">
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
      <p><button type="submit" className="primary" data-testid="alp-m-creer">Créer le modèle</button></p>
    </form>
  );
}
