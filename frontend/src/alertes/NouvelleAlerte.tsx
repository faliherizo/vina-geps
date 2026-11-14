/**
 * **Nouvelle alerte** — la console d'envoi (CU-3 ; FX-AO-20260810, critères 1, 3, 5, 6, 7, 18).
 *
 * Un modèle remplit d'un geste le titre, le texte, la priorité et les réponses ; tout reste modifiable.
 * Les destinataires sont des groupes entiers ou des personnes choisies dans un groupe, par leur nom ;
 * le serveur les résout et les fige à l'envoi. Une alerte critique part sur tous les canaux : l'écran le
 * dit avant l'envoi, pas après.
 */
import { useCallback, useEffect, useState, type FormEvent } from "react";
import { EnTeteEcran, Message } from "../composants";
import { useApiEffectifs as useApi } from "../effectifs/api";
import { liste } from "../formeRecue";
import { lienVersPage } from "../navigation";
import { PRIORITES, libelleSemantique, type AlerteSuivie, type Groupe, type Modele, type Personne, type Priorite, type Reponse } from "./types";

export default function NouvelleAlerte() {
  const { lire, envoyer, erreur, setErreur } = useApi();
  const [modeles, setModeles] = useState<Modele[]>([]);
  const [reponses, setReponses] = useState<Reponse[]>([]);
  const [groupes, setGroupes] = useState<Groupe[]>([]);
  const [titre, setTitre] = useState("");
  const [corps, setCorps] = useState("");
  const [priorite, setPriorite] = useState<Priorite>("urgente");
  const [reponseIds, setReponseIds] = useState<string[]>([]);
  const [groupesChoisis, setGroupesChoisis] = useState<string[]>([]);
  const [personnes, setPersonnes] = useState<Personne[]>([]);
  const [envoyee, setEnvoyee] = useState<AlerteSuivie | null>(null);

  useEffect(() => {
    void (async () => {
      setModeles(liste<Modele>(await lire<Modele[]>("/api/alertes/modeles")).filter((m) => m.actif));
      setReponses(liste<Reponse>(await lire<Reponse[]>("/api/alertes/reponses")).filter((r) => r.active));
      setGroupes(liste<Groupe>(await lire<Groupe[]>("/api/alertes/groupes")));
    })();
  }, [lire]);

  const appliquerModele = (id: string) => {
    const m = modeles.find((x) => x.id === id);
    if (!m) return;
    setTitre(m.titre); setCorps(m.corps); setPriorite(m.priorite); setReponseIds(m.reponseIds.filter((r) => reponses.some((x) => x.id === r)));
  };
  const basculer = (l: string[], v: string) => (l.includes(v) ? l.filter((x) => x !== v) : [...l, v]);

  const envoyerAlerte = async (e: FormEvent) => {
    e.preventDefault();
    setErreur(""); setEnvoyee(null);
    const a = await envoyer<AlerteSuivie>("/api/alertes", {
      titre, corps, priorite, reponseIds, groupes: groupesChoisis, personnes: personnes.map((p) => p.personId),
    });
    if (a) { setEnvoyee(a); setTitre(""); setCorps(""); setGroupesChoisis([]); setPersonnes([]); }
  };

  return (
    <div data-testid="aln-ecran">
      <EnTeteEcran titre="Nouvelle alerte" testId="aln-titre"
        description="Une alerte priorisée, à des groupes ou des personnes, avec les réponses qu'ils pourront donner d'un geste." />
      {erreur && <Message variante="erreur" testId="aln-erreur">{erreur}</Message>}
      {envoyee && (
        <Message variante="succes" testId="aln-envoyee">
          Alerte « {envoyee.titre} » envoyée à {envoyee.bilan.destinataires} personne(s). <a href={lienVersPage("alsuivi")}>Suivre les réponses</a>
        </Message>
      )}
      {/* La console est le formulaire de l'écran : composer et envoyer, comme un message. */}
      <form className="card" onSubmit={(e) => void envoyerAlerte(e)} aria-label="Composer une alerte" data-formulaire="en-place" data-testid="aln-formulaire">
        {modeles.length > 0 && (
          <p><label htmlFor="aln-modele">Modèle (facultatif)</label>
            <select id="aln-modele" data-testid="aln-modele" defaultValue="" onChange={(e) => appliquerModele(e.target.value)}>
              <option value="">— Aucun —</option>
              {modeles.map((m) => <option key={m.id} value={m.id}>{m.titre}</option>)}
            </select></p>
        )}
        <p><label htmlFor="aln-titre-champ">Titre</label>
          <input id="aln-titre-champ" data-testid="aln-titre-champ" required maxLength={120} value={titre} onChange={(e) => setTitre(e.target.value)} /></p>
        <p><label htmlFor="aln-corps">Message</label>
          <textarea id="aln-corps" data-testid="aln-corps" required maxLength={2000} rows={4} value={corps} onChange={(e) => setCorps(e.target.value)} /></p>
        <fieldset>
          <legend>Priorité</legend>
          {PRIORITES.map((p) => (
            <p key={p.code} className="checkbox-line">
              <input type="radio" name="aln-priorite" id={`aln-p-${p.code}`} data-testid={`aln-p-${p.code}`} checked={priorite === p.code} onChange={() => setPriorite(p.code)} />
              <label htmlFor={`aln-p-${p.code}`}>{p.libelle}</label>
            </p>
          ))}
          {priorite === "critique" && <p className="muted" data-testid="aln-critique">Une alerte critique part sur tous les canaux — poussée, texto et courriel — quelles que soient les préférences.</p>}
        </fieldset>
        <Destinataires groupes={groupes} groupesChoisis={groupesChoisis} basculerGroupe={(g) => setGroupesChoisis((l) => basculer(l, g))}
          personnes={personnes} changerPersonnes={setPersonnes} />
        <fieldset>
          <legend>Réponses proposées</legend>
          {reponses.length === 0 && <p className="muted">Aucune réponse prédéfinie : l'alerte sera lue, sans réponse proposée.</p>}
          {reponses.map((r) => (
            <p key={r.id} className="checkbox-line">
              <input type="checkbox" id={`aln-r-${r.id}`} data-testid={`aln-r-${r.id}`} checked={reponseIds.includes(r.id)} onChange={() => setReponseIds((l) => basculer(l, r.id))} />
              <label htmlFor={`aln-r-${r.id}`}>{r.libelle} ({libelleSemantique(r.semantique)})</label>
            </p>
          ))}
        </fieldset>
        <p className="tdb-action-principale">
          <button type="submit" className="primary" data-testid="aln-envoyer" disabled={groupesChoisis.length === 0 && personnes.length === 0}>Envoyer l'alerte</button>
        </p>
      </form>
    </div>
  );
}

/** Des groupes entiers, ou des personnes choisies dans un groupe — par leur nom, jamais un identifiant. */
function Destinataires({ groupes, groupesChoisis, basculerGroupe, personnes, changerPersonnes }: {
  groupes: Groupe[]; groupesChoisis: string[]; basculerGroupe: (g: string) => void;
  personnes: Personne[]; changerPersonnes: (p: Personne[]) => void;
}) {
  const { lire } = useApi();
  const [groupeOuvert, setGroupeOuvert] = useState("");
  const [membres, setMembres] = useState<Personne[]>([]);
  const ouvrir = useCallback(async (g: string) => {
    setGroupeOuvert(g);
    setMembres(g ? liste<Personne>(await lire<Personne[]>(`/api/alertes/groupes/${encodeURIComponent(g)}/membres`)) : []);
  }, [lire]);
  const choisie = (p: Personne) => personnes.some((x) => x.personId === p.personId);
  const basculerPersonne = (p: Personne) => changerPersonnes(choisie(p) ? personnes.filter((x) => x.personId !== p.personId) : [...personnes, p]);

  return (
    <fieldset data-testid="aln-destinataires">
      <legend>Destinataires</legend>
      <p className="muted" role="status" data-testid="aln-compte">
        {groupesChoisis.length} groupe(s) et {personnes.length} personne(s) choisis{personnes.length > 0 ? ` : ${personnes.map((p) => p.nom).join(", ")}` : ""}.
      </p>
      {groupes.map((g) => (
        <p key={g.id} className="checkbox-line">
          <input type="checkbox" id={`aln-g-${g.id}`} data-testid={`aln-g-${g.id}`} checked={groupesChoisis.includes(g.id)} onChange={() => basculerGroupe(g.id)} />
          <label htmlFor={`aln-g-${g.id}`}>Tout le groupe {g.nom}</label>
        </p>
      ))}
      <p><label htmlFor="aln-groupe-personnes">Choisir des personnes dans un groupe</label>
        <select id="aln-groupe-personnes" data-testid="aln-groupe-personnes" value={groupeOuvert} onChange={(e) => void ouvrir(e.target.value)}>
          <option value="">— Choisir un groupe —</option>
          {groupes.map((g) => <option key={g.id} value={g.id}>{g.nom}</option>)}
        </select></p>
      {membres.map((p) => (
        <p key={p.personId} className="checkbox-line">
          <input type="checkbox" id={`aln-pers-${p.personId}`} data-testid={`aln-pers-${p.personId}`} checked={choisie(p)} onChange={() => basculerPersonne(p)} />
          <label htmlFor={`aln-pers-${p.personId}`}>{p.nom}</label>
        </p>
      ))}
    </fieldset>
  );
}
