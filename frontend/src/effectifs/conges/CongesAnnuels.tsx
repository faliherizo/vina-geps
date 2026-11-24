/**
 * **Le choix annuel des congés** (AO 26-015-DO, addenda 8, M-02) : le choix des congés annuels et hors saison se fait
 * **par formulaire** ; l'octroi est paramétrable par groupe d'employés et selon les règles conventionnées. L'encadrement
 * ouvre une campagne (unité, corps d'emploi, saison, code d'absence, plafond d'absents par semaine) ; chacun classe ses
 * semaines ; l'octroi suit l'ancienneté, sous le plafond, et chaque semaine octroyée devient une absence jugée par les
 * règles de l'organisme (soldes de banque compris).
 */
import { useCallback, useEffect, useState, type FormEvent } from "react";
import { Badge, EtatVide, Message, Tableau, type Colonne } from "../../composants";
import { useApiEffectifs } from "../api";
import { liste as listeRecue } from "../../formeRecue";
import { useReferentiels } from "../planification/useReferentiels";

export const API_CONGES = "/api/effectifs/absences/conges";

export interface Campagne {
  id: string; nom: string; uniteCode: string; corpsEmploiCode: string; annee: number; du: string; au: string;
  codeAbsence: string; plafondParSemaine: number; etat: "BROUILLON" | "OUVERTE" | "FERMEE" | "OCTROYEE";
}
interface Choix { semaine: string; rang: number; statut: "DEMANDE" | "OCTROYE" | "REFUSE"; motif: string | null }
interface MaCampagne { campagne: Campagne; semaines: string[]; choix: Choix[] }
interface Resultat { ressourceNom: string; anciennete: number; semaine: string; rang: number; statut: Choix["statut"]; motif: string | null }

const ETATS: Record<Campagne["etat"], string> = { BROUILLON: "Brouillon", OUVERTE: "Choix ouverts", FERMEE: "Choix fermés", OCTROYEE: "Octroyée" };
const STATUTS: Record<Choix["statut"], { libelle: string; ton: "neutre" | "succes" | "erreur" }> = {
  DEMANDE: { libelle: "Demandée", ton: "neutre" }, OCTROYE: { libelle: "Octroyée", ton: "succes" }, REFUSE: { libelle: "Refusée", ton: "erreur" },
};
const semaineLisible = (lundi: string) => {
  const fin = new Date(`${lundi}T12:00:00`);
  fin.setDate(fin.getDate() + 6);
  return `${lundi} au ${fin.toISOString().slice(0, 10)}`;
};

/** **Mes congés annuels** : classer ses semaines dans chaque campagne ouverte, puis lire l'octroi. */
export function MesCongesAnnuels() {
  const { lire, envoyer, erreur, setErreur, succes, setSucces } = useApiEffectifs();
  const [miennes, setMiennes] = useState<MaCampagne[]>([]);
  const charger = useCallback(async () => setMiennes(listeRecue<MaCampagne>(await lire<MaCampagne[]>(`${API_CONGES}/miennes`))), [lire]);
  useEffect(() => { void charger(); }, [charger]);
  const enregistrer = async (c: Campagne, semaines: string[]) => {
    setErreur("");
    if (await envoyer(`${API_CONGES}/${encodeURIComponent(c.id)}/choix`, { semaines }, "PUT")) { setSucces(`Vos ${semaines.length} choix sont enregistrés pour « ${c.nom} ».`); await charger(); }
  };
  return (
    <div data-testid="cng-miennes">
      {succes && <Message variante="succes" testId="cng-succes">{succes}</Message>}
      {erreur && <Message variante="erreur" testId="cng-erreur">{erreur}</Message>}
      {miennes.length === 0 && <EtatVide titre="Aucune campagne" testId="cng-vide">Aucune campagne de choix des congés ne vise votre unité en ce moment.</EtatVide>}
      {miennes.map((m) => <CampagneDeLEmploye key={m.campagne.id} m={m} onEnregistrer={(s) => void enregistrer(m.campagne, s)} />)}
    </div>
  );
}

function CampagneDeLEmploye({ m, onEnregistrer }: { m: MaCampagne; onEnregistrer: (semaines: string[]) => void }) {
  const [ordre, setOrdre] = useState<string[]>(() => [...m.choix].sort((a, b) => a.rang - b.rang).map((c) => c.semaine));
  const ouverte = m.campagne.etat === "OUVERTE";
  const basculer = (s: string) => setOrdre((o) => (o.includes(s) ? o.filter((x) => x !== s) : [...o, s]));
  const statut = (s: string) => m.choix.find((c) => c.semaine === s);
  const soumettre = (e: FormEvent) => { e.preventDefault(); onEnregistrer(ordre); };
  return (
    <section aria-labelledby={`cng-${m.campagne.id}-titre`} data-testid={`cng-campagne-${m.campagne.id}`}>
      <h2 id={`cng-${m.campagne.id}-titre`}>{m.campagne.nom}</h2>
      <p className="muted">{ETATS[m.campagne.etat]} — saison du {m.campagne.du} au {m.campagne.au} ; au plus {m.campagne.plafondParSemaine} absent(s) par semaine.</p>
      <form onSubmit={soumettre}>
        <fieldset disabled={!ouverte}>
          <legend>Vos semaines, dans l'ordre de préférence (cochez-les dans cet ordre)</legend>
          {m.semaines.map((s) => {
            const st = statut(s);
            return (
              <p key={s} className="eff-case">
                <input type="checkbox" id={`cng-${m.campagne.id}-${s}`} data-testid={`cng-semaine-${s}`} checked={ordre.includes(s)} onChange={() => basculer(s)} />
                <label htmlFor={`cng-${m.campagne.id}-${s}`}>
                  {ordre.includes(s) ? `${ordre.indexOf(s) + 1}ᵉ choix — ` : ""}Semaine du {semaineLisible(s)}
                </label>{" "}
                {st && st.statut !== "DEMANDE" && <Badge ton={STATUTS[st.statut].ton}>{STATUTS[st.statut].libelle}{st.motif ? ` : ${st.motif}` : ""}</Badge>}
              </p>
            );
          })}
        </fieldset>
        {ouverte && <button type="submit" data-testid={`cng-enregistrer-${m.campagne.id}`}>Enregistrer mes choix</button>}
      </form>
    </section>
  );
}

/** **Choix des congés annuels** (encadrement) : créer, ouvrir et fermer une campagne, octroyer, lire les résultats. */
export function CampagnesConges() {
  const { lire, envoyer, erreur, setErreur, succes, setSucces } = useApiEffectifs();
  const { refs } = useReferentiels(["unites", "corps_emploi", "codes_absence"]);
  const [campagnes, setCampagnes] = useState<Campagne[]>([]);
  const [resultats, setResultats] = useState<{ campagne: Campagne; lignes: Resultat[] } | null>(null);
  const charger = useCallback(async () => setCampagnes(listeRecue<Campagne>(await lire<Campagne[]>(API_CONGES))), [lire]);
  useEffect(() => { void charger(); }, [charger]);

  const geste = async (c: Campagne, chemin: string, corps: unknown, message: string) => {
    setErreur("");
    if (await envoyer(`${API_CONGES}/${encodeURIComponent(c.id)}/${chemin}`, corps)) { setSucces(message); await charger(); }
  };
  const voir = async (c: Campagne) => setResultats({ campagne: c, lignes: listeRecue<Resultat>(await lire<Resultat[]>(`${API_CONGES}/${encodeURIComponent(c.id)}/resultats`)) });
  const colonnes: ReadonlyArray<Colonne<Campagne>> = [
    { id: "nom", libelle: "Campagne" }, { id: "groupe", libelle: "Groupe" }, { id: "saison", libelle: "Saison" },
    { id: "plafond", libelle: "Plafond / semaine", numerique: true }, { id: "etat", libelle: "État" }, { id: "actions", libelle: "Actions", actions: true },
  ];
  return (
    <div data-testid="cng-gestion">
      {succes && <Message variante="succes" testId="cng-gestion-succes">{succes}</Message>}
      {erreur && <Message variante="erreur" testId="cng-gestion-erreur">{erreur}</Message>}
      <Tableau colonnes={colonnes} lignes={campagnes} legende="Campagnes de choix des congés" testId="cng-table"
        vide={<EtatVide titre="Aucune campagne" compact testId="cng-gestion-vide">Créez la campagne de l'année pour un groupe d'employés.</EtatVide>}
        rendre={(c) => (
          <tr key={c.id} data-testid={`cng-${c.id}`}>
            <td>{c.nom} ({c.annee})</td><td>{c.uniteCode} · {c.corpsEmploiCode}</td><td>{c.du} au {c.au} · {c.codeAbsence}</td>
            <td className="col-numerique">{c.plafondParSemaine}</td><td>{ETATS[c.etat]}</td>
            <td className="col-menu">
              {c.etat === "BROUILLON" && <button type="button" data-testid={`cng-ouvrir-${c.id}`} onClick={() => void geste(c, "etat", { etat: "OUVERTE" }, `« ${c.nom} » est ouverte aux choix.`)}>Ouvrir les choix<span className="sr-only"> de {c.nom}</span></button>}
              {c.etat === "OUVERTE" && <button type="button" data-testid={`cng-fermer-${c.id}`} onClick={() => void geste(c, "etat", { etat: "FERMEE" }, `Les choix de « ${c.nom} » sont fermés.`)}>Fermer les choix<span className="sr-only"> de {c.nom}</span></button>}
              {c.etat === "FERMEE" && <button type="button" data-testid={`cng-octroyer-${c.id}`} onClick={() => void geste(c, "octroyer", {}, `« ${c.nom} » est octroyée.`)}>Octroyer par ancienneté<span className="sr-only"> pour {c.nom}</span></button>}
              <button type="button" className="secondary" data-testid={`cng-resultats-${c.id}`} onClick={() => void voir(c)}>Résultats<span className="sr-only"> de {c.nom}</span></button>
            </td>
          </tr>
        )} />
      {resultats && <Resultats campagne={resultats.campagne} lignes={resultats.lignes} />}
      <FormulaireCampagne refs={refs} onCreer={async (c) => {
        setErreur("");
        if (await envoyer(API_CONGES, c)) { setSucces(`Campagne « ${String(c.nom)} » créée.`); await charger(); }
      }} />
    </div>
  );
}

function Resultats({ campagne, lignes }: { campagne: Campagne; lignes: Resultat[] }) {
  const colonnes: ReadonlyArray<Colonne<Resultat>> = [
    { id: "personne", libelle: "Personne" }, { id: "anciennete", libelle: "Ancienneté", numerique: true },
    { id: "rang", libelle: "Choix", numerique: true }, { id: "semaine", libelle: "Semaine" }, { id: "statut", libelle: "Octroi" },
  ];
  return (
    <section aria-labelledby="cng-resultats-titre" data-testid="cng-resultats">
      <h2 id="cng-resultats-titre">Résultats — {campagne.nom}</h2>
      <Tableau colonnes={colonnes} lignes={lignes} legende={`Choix et octroi de ${campagne.nom}`} testId="cng-resultats-table"
        vide={<EtatVide titre="Aucun choix" compact testId="cng-resultats-vide">Personne n'a encore classé de semaine.</EtatVide>}
        rendre={(r) => (
          <tr key={`${r.ressourceNom}-${r.semaine}`}>
            <td>{r.ressourceNom}</td><td className="col-numerique">{r.anciennete.toLocaleString("fr-CA")}</td><td className="col-numerique">{r.rang}</td>
            <td>{semaineLisible(r.semaine)}</td><td><Badge ton={STATUTS[r.statut].ton}>{STATUTS[r.statut].libelle}</Badge>{r.motif ? ` ${r.motif}` : ""}</td>
          </tr>
        )} />
    </section>
  );
}

function FormulaireCampagne({ refs, onCreer }: { refs: Record<string, Array<{ code: string; libelleFr: string }>>; onCreer: (c: Record<string, unknown>) => Promise<void> }) {
  const annee = new Date().getFullYear() + 1;
  const [v, setV] = useState<Record<string, string>>({ nom: "", uniteCode: "", corpsEmploiCode: "", annee: String(annee), du: "", au: "", codeAbsence: "", plafondParSemaine: "2" });
  const poser = (k: string, x: string) => setV((o) => ({ ...o, [k]: x }));
  const liste = (cle: string, libelle: string, type: string) => (
    <p><label htmlFor={`cng-f-${cle}`}>{libelle}</label>
      <select id={`cng-f-${cle}`} data-testid={`cng-f-${cle}`} required value={v[cle]} onChange={(e) => poser(cle, e.target.value)}>
        <option value="">Choisir…</option>
        {(refs[type] ?? []).map((o) => <option key={o.code} value={o.code}>{o.libelleFr}</option>)}
      </select></p>
  );
  const soumettre = (e: FormEvent) => { e.preventDefault(); void onCreer({ ...v, annee: Number(v.annee), plafondParSemaine: Number(v.plafondParSemaine) }); };
  return (
    <form onSubmit={soumettre} data-testid="cng-formulaire" aria-labelledby="cng-formulaire-titre">
      <h2 id="cng-formulaire-titre">Nouvelle campagne</h2>
      <p><label htmlFor="cng-f-nom">Nom</label><input id="cng-f-nom" data-testid="cng-f-nom" required value={v.nom} onChange={(e) => poser("nom", e.target.value)} /></p>
      {liste("uniteCode", "Unité", "unites")}
      {liste("corpsEmploiCode", "Corps d'emploi", "corps_emploi")}
      {liste("codeAbsence", "Code d'absence octroyé", "codes_absence")}
      <p><label htmlFor="cng-f-annee">Année</label><input id="cng-f-annee" type="number" min={2000} max={2100} data-testid="cng-f-annee" required value={v.annee} onChange={(e) => poser("annee", e.target.value)} /></p>
      <p><label htmlFor="cng-f-du">Saison : du</label><input id="cng-f-du" type="date" data-testid="cng-f-du" required value={v.du} onChange={(e) => poser("du", e.target.value)} /></p>
      <p><label htmlFor="cng-f-au">au</label><input id="cng-f-au" type="date" data-testid="cng-f-au" required value={v.au} onChange={(e) => poser("au", e.target.value)} /></p>
      <p><label htmlFor="cng-f-plafond">Plafond d'absents par semaine</label>
        <input id="cng-f-plafond" type="number" min={1} max={500} data-testid="cng-f-plafond" required value={v.plafondParSemaine} onChange={(e) => poser("plafondParSemaine", e.target.value)} /></p>
      <button type="submit" data-testid="cng-creer">Créer la campagne</button>
    </form>
  );
}
