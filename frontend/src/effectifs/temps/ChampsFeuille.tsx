/**
 * **Les champs de la feuille propres à l'organisme** — ce qui rend la feuille de temps adaptable à
 * n'importe quel emploi sans code par emploi (docs/urgences-sante/ETUDE-FEUILLE-DE-TEMPS-DYNAMIQUE.md).
 *
 * `SaisieChamps` rend, dans un formulaire de segment, la description des tâches puis un contrôle par
 * champ actif. `ParametresChamps` est le dialogue de l'administration : définir, activer, désactiver.
 * Un champ se désactive, jamais ne s'efface : les valeurs déjà saisies restent lisibles.
 */
import { useCallback, useEffect, useState, type FormEvent } from "react";
import { liste } from "../../formeRecue";
import { Message } from "../../composants";
import { useApiEffectifs } from "../api";
import { typeDe, valeurEnMots, type ChampFeuille, type TypeChamp, type ValeurChamp } from "./types";

const LIBELLE_TYPE: Record<TypeChamp, string> = { texte: "Texte", nombre: "Nombre", booleen: "Oui ou non", liste: "Liste de choix" };
const DESCRIPTION_MAX = 500;

/** Les champs de l'organisme, lus une fois par écran. */
export function useChampsFeuille() {
  const { lire } = useApiEffectifs();
  const [champs, setChamps] = useState<ChampFeuille[]>([]);
  const recharger = useCallback(async () => { setChamps(liste<ChampFeuille>(await lire<ChampFeuille[]>("/api/effectifs/temps/champs"))); }, [lire]);
  useEffect(() => { void recharger(); }, [recharger]);
  return { champs, recharger };
}

/** Ce que le formulaire d'un segment tient en plus des heures. */
export interface Complement { description: string; attributs: Record<string, ValeurChamp> }
export const COMPLEMENT_VIDE: Complement = { description: "", attributs: {} };

/** Le corps envoyé au serveur : les valeurs vides ne partent pas (un champ facultatif vide n'est pas une valeur). */
export function complementAEnvoyer(c: Complement) {
  const attributs = Object.fromEntries(Object.entries(c.attributs).filter(([, v]) => v !== ""));
  return { description: c.description, attributs };
}

/** La description des tâches et les champs actifs, pour l'ajout comme pour la correction. */
export function SaisieChamps({ champs, valeur, onChange, prefixe }: {
  champs: ChampFeuille[]; valeur: Complement; onChange: (c: Complement) => void; prefixe: string;
}) {
  const poser = (code: string, v: ValeurChamp) => onChange({ ...valeur, attributs: { ...valeur.attributs, [code]: v } });
  return (
    <>
      <p>
        <label htmlFor={`${prefixe}-description`}>Description des tâches (facultatif)</label>
        <textarea id={`${prefixe}-description`} rows={3} maxLength={DESCRIPTION_MAX} data-testid={`${prefixe}-description`}
          aria-describedby={`${prefixe}-description-aide`} value={valeur.description} onChange={(e) => onChange({ ...valeur, description: e.target.value })} />
        <span id={`${prefixe}-description-aide`} className="aide eff-aide">Ce que vous avez fait pendant ce temps, {DESCRIPTION_MAX} caractères au plus.</span>
      </p>
      {champs.filter((c) => c.actif).map((c) => <ControleChamp key={c.code} champ={c} valeur={valeur.attributs[c.code]} poser={poser} prefixe={prefixe} />)}
    </>
  );
}

function ControleChamp({ champ: c, valeur, poser, prefixe }: { champ: ChampFeuille; valeur: ValeurChamp | undefined; poser: (code: string, v: ValeurChamp) => void; prefixe: string }) {
  const id = `${prefixe}-champ-${c.code}`;
  const libelleId = `${id}-libelle`;
  const libelle = <label id={libelleId} htmlFor={id}>{`${c.libelle}${c.requis ? " (obligatoire)" : ""}`}</label>;
  // Un seul jeu d'attributs pour les quatre contrôles : l'identifiant ne se déclare qu'une fois.
  const commun = { id, "data-testid": id, required: c.requis, value: String(valeur ?? "") };
  const type = typeDe(c);
  if (type === "booleen") {
    return <p className="eff-case"><input {...commun} aria-labelledby={libelleId} required={false} type="checkbox" checked={valeur === true} onChange={(e) => poser(c.code, e.target.checked)} />{libelle}</p>;
  }
  const controle = type === "liste"
    ? (
      <select {...commun} aria-labelledby={libelleId} onChange={(e) => poser(c.code, e.target.value)}>
        <option value="">— choisir —</option>
        {c.valeurs.map((v) => <option key={v} value={v}>{v}</option>)}
      </select>
    )
    : type === "nombre"
      ? <input {...commun} aria-labelledby={libelleId} type="number" step="any" inputMode="decimal" onChange={(e) => poser(c.code, e.target.value === "" ? "" : Number(e.target.value))} />
      : <input {...commun} aria-labelledby={libelleId} type="text" maxLength={200} onChange={(e) => poser(c.code, e.target.value)} />;
  return <p>{libelle}{controle}</p>;
}

/** Les colonnes à montrer : les champs actifs, et ceux désactivés qui portent encore une valeur sur la feuille. */
export function champsAffiches(champs: ChampFeuille[], segments: Array<{ attributs?: Record<string, ValeurChamp> | null }>) {
  return champs.filter((c) => c.actif || segments.some((s) => s.attributs?.[c.code] !== undefined));
}

export const valeurDe = (s: { attributs?: Record<string, ValeurChamp> | null }, c: ChampFeuille) => valeurEnMots(s.attributs?.[c.code]);

interface Definition { code: string; libelle: string; type: TypeChamp; valeurs: string; requis: boolean }
const DEFINITION_VIDE: Definition = { code: "", libelle: "", type: "texte", valeurs: "", requis: false };

/** Le dialogue de l'administration : la liste des champs, leur état, et la définition d'un nouveau. */
export function ParametresChamps() {
  const { envoyer, erreur, setErreur } = useApiEffectifs();
  const { champs, recharger } = useChampsFeuille();
  const [d, setD] = useState<Definition>(DEFINITION_VIDE);
  const [info, setInfo] = useState("");

  const definir = async (e: FormEvent) => {
    e.preventDefault();
    setInfo("");
    const valeurs = d.type === "liste" ? d.valeurs.split("\n").map((v) => v.trim()).filter(Boolean) : undefined;
    const r = await envoyer<ChampFeuille>("/api/effectifs/temps/champs", { code: d.code.trim(), libelle: d.libelle, type: d.type, valeurs, requis: d.requis });
    if (!r) return;
    setInfo(`Champ « ${r.libelle} » ajouté à la feuille.`);
    setD(DEFINITION_VIDE);
    await recharger();
  };

  const basculer = async (c: ChampFeuille) => {
    setInfo(""); setErreur("");
    const r = await envoyer<ChampFeuille>(`/api/effectifs/temps/champs/${c.code}`, { actif: !c.actif }, "PATCH");
    if (!r) return;
    setInfo(`Champ « ${r.libelle} » ${r.actif ? "réactivé" : "désactivé : les valeurs déjà saisies restent lisibles"}.`);
    await recharger();
  };

  const poser = <K extends keyof Definition>(cle: K, v: Definition[K]) => setD((x) => ({ ...x, [cle]: v }));

  return (
    <section aria-labelledby="pla-tps-champs-titre" data-testid="pla-tps-champs">
      <h3 id="pla-tps-champs-titre">Champs de la feuille</h3>
      <p>Ce que chaque segment de temps doit dire en plus des heures, selon vos emplois : un véhicule, un projet, un patient transporté… La description des tâches est toujours offerte.</p>
      {champs.length === 0
        ? <p data-testid="pla-tps-champs-vide">Aucun champ propre à l'organisme : la feuille offre les heures, le type d'heure et la description des tâches.</p>
        : (
          <table className="eff-table-compacte" data-testid="pla-tps-champs-table">
            <caption>Champs définis par l'organisme</caption>
            <thead><tr><th scope="col">Libellé</th><th scope="col">Code</th><th scope="col">Type</th><th scope="col">Obligatoire</th><th scope="col">État</th><th scope="col">Action</th></tr></thead>
            <tbody>
              {champs.map((c) => (
                <tr key={c.code} data-testid={`pla-tps-champ-${c.code}`}>
                  <th scope="row">{c.libelle}</th>
                  <td><code>{c.code}</code></td>
                  <td>{LIBELLE_TYPE[typeDe(c)]}{typeDe(c) === "liste" ? ` : ${c.valeurs.join(", ")}` : ""}</td>
                  <td>{c.requis ? "Oui" : "Non"}</td>
                  <td>{c.actif ? "Actif" : "Désactivé"}</td>
                  <td>
                    <button type="button" className="secondary" data-testid={`pla-tps-champ-${c.code}-basculer`} onClick={() => void basculer(c)}>
                      {c.actif ? "Désactiver" : "Réactiver"}<span className="sr-only"> le champ {c.libelle}</span>
                    </button>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        )}

      <form onSubmit={(e) => void definir(e)} aria-label="Définir un champ">
        <h4>Nouveau champ</h4>
        <p>
          <label htmlFor="pla-tps-champ-libelle">Libellé</label>
          <input id="pla-tps-champ-libelle" required maxLength={60} data-testid="pla-tps-champ-libelle" value={d.libelle} onChange={(e) => poser("libelle", e.target.value)} />
        </p>
        <p>
          <label htmlFor="pla-tps-champ-code">Code</label>
          <input id="pla-tps-champ-code" required pattern="[a-z][a-z0-9_]{1,39}" maxLength={40} data-testid="pla-tps-champ-code" aria-describedby="pla-tps-champ-code-aide"
            value={d.code} onChange={(e) => poser("code", e.target.value)} />
          <span id="pla-tps-champ-code-aide" className="aide eff-aide">Minuscules, chiffres et _ (ex. vehicule, km_parcourus). Il ne change plus ensuite.</span>
        </p>
        <p>
          <label htmlFor="pla-tps-champ-type">Type</label>
          <select id="pla-tps-champ-type" data-testid="pla-tps-champ-type" value={d.type} onChange={(e) => poser("type", e.target.value as TypeChamp)}>
            {(Object.keys(LIBELLE_TYPE) as TypeChamp[]).map((t) => <option key={t} value={t}>{LIBELLE_TYPE[t]}</option>)}
          </select>
        </p>
        {d.type === "liste" && (
          <p>
            <label htmlFor="pla-tps-champ-valeurs">Choix offerts, un par ligne</label>
            <textarea id="pla-tps-champ-valeurs" required rows={4} data-testid="pla-tps-champ-valeurs" value={d.valeurs} onChange={(e) => poser("valeurs", e.target.value)} />
          </p>
        )}
        <p className="eff-case">
          <input id="pla-tps-champ-requis" type="checkbox" data-testid="pla-tps-champ-requis" checked={d.requis} onChange={(e) => poser("requis", e.target.checked)} />
          <label htmlFor="pla-tps-champ-requis">Obligatoire sur chaque segment</label>
        </p>
        {erreur && <Message variante="erreur" testId="pla-tps-champs-erreur">{erreur}</Message>}
        <p><button type="submit" data-testid="pla-tps-champ-definir">Ajouter le champ</button></p>
      </form>
      {info && <p role="status" data-testid="pla-tps-champs-info">{info}</p>}
    </section>
  );
}
