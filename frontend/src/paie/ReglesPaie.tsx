/**
 * **Règles de paie variable** (ADR 0084) — l'échelle salariale (taux de base par titre d'emploi et échelon) et les
 * primes de l'organisme : soir, nuit, fin de semaine, férié, forfait. Chaque règle a une période de validité ; on la
 * désactive plutôt que de l'effacer, pour que l'historique reste lisible. Une paie traitée garde ses gains figés.
 */
import { useCallback, useEffect, useState, type FormEvent } from "react";
import { BadgeStatut, EnTeteEcran, EtatVide, Message, Tableau, type Colonne } from "../composants";
import { FormulaireModal } from "../Modal";
import { useApiEffectifs as useApi } from "../effectifs/api";
import { liste } from "../formeRecue";
import { argent, JOURS, libelleTypeRegle, TYPES_REGLE, type Regle, type TypeRegle } from "./types";

const COL: ReadonlyArray<Colonne<Regle>> = [
  { id: "code", libelle: "Code", tri: (r) => r.code }, { id: "libelle", libelle: "Libellé" }, { id: "valeur", libelle: "Valeur", numerique: true },
  { id: "vise", libelle: "S'applique à" }, { id: "validite", libelle: "Validité", tri: (r) => r.valideDu }, { id: "etat", libelle: "État" },
  { id: "actions", libelle: "Actions", actions: true },
];

/** La valeur dans son unité : 12 % pour une prime en pourcentage, 34,50 $ pour le reste. */
export function valeurLisible(r: Pick<Regle, "type" | "valeur">): string {
  return r.type === "prime-pourcentage" ? `${Math.round(r.valeur * 100000) / 1000} %` : argent(r.valeur);
}

/** Les conditions, en une phrase : qui, quand, quelles heures. */
export function conditionsLisibles(r: Regle): string {
  const parts: string[] = [];
  if (r.titreEmploi) parts.push(`titre ${r.titreEmploi}`);
  if (r.convention) parts.push(`convention ${r.convention}`);
  if (r.echelon != null) parts.push(`échelon ${r.echelon}`);
  if (r.heureDebut && r.heureFin) parts.push(`de ${r.heureDebut} à ${r.heureFin}`);
  if (r.jours.length > 0) parts.push(r.jours.map((j) => JOURS.find((x) => x.numero === j)?.court ?? j).join(", "));
  if (r.feriesSeulement) parts.push("jours fériés");
  if (r.codesPaie.length > 0) parts.push(`heures ${r.codesPaie.join(", ")}`);
  return parts.length > 0 ? parts.join(" · ") : "tout le personnel, toutes les heures";
}

export default function ReglesPaie() {
  const { lire, envoyer, erreur, succes, setSucces } = useApi();
  const [regles, setRegles] = useState<Regle[]>([]);
  const [choisie, setChoisie] = useState<Regle | null>(null);
  const charger = useCallback(async () => setRegles(liste<Regle>(await lire<Regle[]>("/api/paie/regles"))), [lire]);
  useEffect(() => { void charger(); }, [charger]);
  const echelle = regles.filter((r) => r.type === "taux-de-base");
  const primes = regles.filter((r) => r.type !== "taux-de-base");
  const basculer = async (r: Regle) => {
    const { id: _id, ...corps } = r;
    if (await envoyer<Regle>(`/api/paie/regles/${r.id}`, { ...corps, actif: !r.actif }, "PUT")) {
      setSucces(`Règle ${r.code} ${r.actif ? "désactivée" : "réactivée"} : elle s'applique aux prochains calculs.`);
      await charger();
    }
  };
  const apres = async (message: string) => { setSucces(message); setChoisie(null); await charger(); };
  const rendre = (r: Regle) => (
    <tr key={r.id} data-testid={`prg-${r.id}`}>
      <td><code>{r.code}</code></td><td>{r.libelle}<br /><span className="muted">{libelleTypeRegle(r.type)}</span></td>
      <td className="num">{valeurLisible(r)}</td><td>{conditionsLisibles(r)}</td>
      <td>du {r.valideDu}{r.valideAu ? ` au ${r.valideAu}` : ", sans fin"}</td>
      <td><BadgeStatut statut={r.actif ? "actif" : "clos"}>{r.actif ? "Active" : "Inactive"}</BadgeStatut></td>
      <td><div className="eff-actions">
        <button type="button" className="secondary" data-testid={`prg-${r.id}-modifier`} onClick={() => setChoisie(r)}>
          Modifier<span className="sr-only"> la règle {r.code}</span></button>
        <button type="button" className="secondary" data-testid={`prg-${r.id}-basculer`} onClick={() => void basculer(r)}>
          {r.actif ? "Désactiver" : "Réactiver"}<span className="sr-only"> la règle {r.code}</span></button>
      </div></td>
    </tr>
  );

  return (
    <div data-testid="prg-ecran">
      <EnTeteEcran titre="Règles de paie variable" testId="prg-titre"
        description="L'échelle salariale et les primes de l'organisme. La paie applique, à chaque heure, les règles en vigueur ce jour-là qui visent la personne." />
      {erreur && <Message variante="erreur" testId="prg-erreur">{erreur}</Message>}
      {succes && <Message variante="succes" testId="prg-succes">{succes}</Message>}
      <div className="eff-actions">
        <FormulaireModal libelle="Nouvelle règle" titre="Nouvelle règle de paie" testId="prg-nouvelle" size="lg">
          {(fermer) => <FormulaireRegle envoyer={envoyer} erreur={erreur}
            apres={async (r) => { fermer(); await apres(`Règle ${r.code} créée.`); }} />}
        </FormulaireModal>
      </div>
      <section className="card" aria-labelledby="prg-echelle-h">
        <h2 id="prg-echelle-h">Échelle salariale (taux de base)</h2>
        <Tableau colonnes={COL} lignes={echelle} legende="Taux de base par titre d'emploi et échelon" testId="prg-echelle"
          vide={<EtatVide titre="Aucun taux de base" compact testId="prg-echelle-vide">
            Sans échelle, chaque dossier à l'heure doit porter son propre taux. Ajoutez une règle « Taux de base » par titre d'emploi et échelon.</EtatVide>}
          rendre={rendre} />
      </section>
      <section className="card" aria-labelledby="prg-primes-h">
        <h2 id="prg-primes-h">Primes et forfaits</h2>
        <Tableau colonnes={COL} lignes={primes} legende="Primes de quart, de fin de semaine, de férié, et forfaits" testId="prg-primes"
          vide={<EtatVide titre="Aucune prime" compact testId="prg-primes-vide">Ajoutez la prime de soir, de nuit ou de fin de semaine de votre convention.</EtatVide>}
          rendre={rendre} />
      </section>
      <FormulaireModal libelle="Règle" titre={choisie ? `Modifier la règle ${choisie.code}` : "Règle"} testId="prg-regle" size="lg"
        ouvert={choisie !== null} onFermer={() => setChoisie(null)}>
        {choisie && <FormulaireRegle regle={choisie} envoyer={envoyer} erreur={erreur} apres={async (r) => apres(`Règle ${r.code} enregistrée.`)} />}
      </FormulaireModal>
    </div>
  );
}

type Envoyer = ReturnType<typeof useApi>["envoyer"];
const texte = (v: number | string | null | undefined) => (v == null ? "" : String(v));

/** Une prime en pourcentage se saisit en %, et voyage en fraction (12 → 0,12). */
const versSaisie = (r: Regle | undefined) => (r == null ? "" : r.type === "prime-pourcentage" ? String(Math.round(r.valeur * 100000) / 1000) : String(r.valeur));

function FormulaireRegle({ regle, envoyer, erreur, apres }: { regle?: Regle; envoyer: Envoyer; erreur: string | null; apres: (r: Regle) => Promise<void> }) {
  const [code, setCode] = useState(regle?.code ?? "");
  const [libelle, setLibelle] = useState(regle?.libelle ?? "");
  const [type, setType] = useState<TypeRegle>(regle?.type ?? "prime-pourcentage");
  const [valeur, setValeur] = useState(versSaisie(regle));
  const [titre, setTitre] = useState(regle?.titreEmploi ?? "");
  const [convention, setConvention] = useState(regle?.convention ?? "");
  const [echelon, setEchelon] = useState(texte(regle?.echelon));
  const [debut, setDebut] = useState(regle?.heureDebut ?? "");
  const [fin, setFin] = useState(regle?.heureFin ?? "");
  const [jours, setJours] = useState<number[]>(regle?.jours ?? []);
  const [feries, setFeries] = useState(regle?.feriesSeulement ?? false);
  const [codes, setCodes] = useState((regle?.codesPaie ?? []).join(", "));
  const [du, setDu] = useState(regle?.valideDu ?? "");
  const [au, setAu] = useState(regle?.valideAu ?? "");
  const prime = type === "prime-pourcentage" || type === "prime-horaire";
  const unite = TYPES_REGLE.find((t) => t.code === type)?.unite ?? "";
  const basculerJour = (n: number) => setJours((j) => (j.includes(n) ? j.filter((x) => x !== n) : [...j, n].sort()));
  const soumettre = async (e: FormEvent) => {
    e.preventDefault();
    const corps = {
      code: code.trim().toUpperCase(), libelle, type, valeur: type === "prime-pourcentage" ? Number(valeur) / 100 : Number(valeur),
      titreEmploi: titre, convention, echelon: echelon === "" ? null : Number(echelon), valideDu: du, valideAu: au, actif: regle?.actif ?? true,
      heureDebut: prime ? debut : "", heureFin: prime ? fin : "", jours: prime ? jours : [], feriesSeulement: prime && feries,
      codesPaie: prime ? codes.split(",").map((c) => c.trim()).filter(Boolean) : [],
    };
    const r = regle ? await envoyer<Regle>(`/api/paie/regles/${regle.id}`, corps, "PUT") : await envoyer<Regle>("/api/paie/regles", corps);
    if (r) await apres(r);
  };
  return (
    <form onSubmit={(e) => void soumettre(e)} aria-label={regle ? `Modifier la règle ${regle.code}` : "Nouvelle règle de paie"}>
      {erreur && <Message variante="erreur" testId="prg-form-erreur">{erreur}</Message>}
      <p><label htmlFor="prg-type">Type de règle</label>
        <select id="prg-type" data-testid="prg-type" value={type} onChange={(e) => setType(e.target.value as TypeRegle)}>
          {TYPES_REGLE.map((t) => <option key={t.code} value={t.code}>{t.libelle}</option>)}
        </select></p>
      <p><label htmlFor="prg-code">Code du gain (A-Z, 0-9, _)</label>
        <input id="prg-code" data-testid="prg-code" required pattern="[A-Za-z0-9_]{2,20}" value={code} onChange={(e) => setCode(e.target.value)} /></p>
      <p><label htmlFor="prg-libelle">Libellé sur la fiche de paie</label>
        <input id="prg-libelle" data-testid="prg-libelle" required minLength={2} maxLength={80} value={libelle} onChange={(e) => setLibelle(e.target.value)} /></p>
      <p><label htmlFor="prg-valeur">Valeur ({unite})</label>
        <input id="prg-valeur" data-testid="prg-valeur" type="number" min="0" step="0.001" inputMode="decimal" required value={valeur}
          onChange={(e) => setValeur(e.target.value)} /></p>
      <fieldset>
        <legend>Qui est visé (vide : tout le personnel)</legend>
        <p><label htmlFor="prg-titre-emploi">Titre d'emploi (code des effectifs)</label>
          <input id="prg-titre-emploi" data-testid="prg-titre-emploi" value={titre} onChange={(e) => setTitre(e.target.value)} /></p>
        <p><label htmlFor="prg-convention">Convention collective (code)</label>
          <input id="prg-convention" data-testid="prg-convention" value={convention} onChange={(e) => setConvention(e.target.value)} /></p>
        <p><label htmlFor="prg-echelon">Échelon (1 à 30)</label>
          <input id="prg-echelon" data-testid="prg-echelon" type="number" min="1" max="30" step="1" value={echelon} onChange={(e) => setEchelon(e.target.value)} /></p>
      </fieldset>
      {prime && (
        <fieldset data-testid="prg-heures">
          <legend>Quelles heures (vide : toutes les heures payées)</legend>
          <p><label htmlFor="prg-debut">De (heure de Montréal)</label>
            <input id="prg-debut" data-testid="prg-debut" type="time" value={debut} onChange={(e) => setDebut(e.target.value)} /></p>
          <p><label htmlFor="prg-fin">À (une fin avant le début passe minuit)</label>
            <input id="prg-fin" data-testid="prg-fin" type="time" value={fin} onChange={(e) => setFin(e.target.value)} /></p>
          <div className="eff-actions" role="group" aria-label="Jours de la semaine">
            {JOURS.map((j) => (
              <span key={j.numero} className="checkbox-line">
                <input type="checkbox" id={`prg-jour-${j.numero}`} data-testid={`prg-jour-${j.numero}`} checked={jours.includes(j.numero)}
                  onChange={() => basculerJour(j.numero)} />
                <label htmlFor={`prg-jour-${j.numero}`}>{j.long}</label>
              </span>
            ))}
          </div>
          <p className="checkbox-line"><input type="checkbox" id="prg-feries" data-testid="prg-feries" checked={feries} onChange={(e) => setFeries(e.target.checked)} />
            <label htmlFor="prg-feries">Seulement les jours fériés de l'organisme</label></p>
          <p><label htmlFor="prg-codes">Codes de paie visés, séparés par des virgules (par exemple REG)</label>
            <input id="prg-codes" data-testid="prg-codes" value={codes} onChange={(e) => setCodes(e.target.value)} /></p>
        </fieldset>
      )}
      <p><label htmlFor="prg-du">En vigueur à partir du</label>
        <input id="prg-du" data-testid="prg-du" type="date" required value={du} onChange={(e) => setDu(e.target.value)} /></p>
      <p><label htmlFor="prg-au">Jusqu'au (vide : sans fin)</label>
        <input id="prg-au" data-testid="prg-au" type="date" value={au} onChange={(e) => setAu(e.target.value)} /></p>
      <p><button type="submit" className="primary" data-testid="prg-enregistrer">Enregistrer la règle</button></p>
    </form>
  );
}
