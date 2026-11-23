/**
 * **Seuils d'heures supplémentaires par convention** (AO 26-015-DO, E-RF-5 ; addenda 7, Q15 ; ADR 0087) —
 * une convention collective peut porter ses propres seuils de jour, de semaine et de période de paie. Une
 * feuille se calcule avec les seuils de la convention de sa personne ; un seuil laissé vide reprend celui de
 * l'organisme. Vider les trois seuils retire la surcharge.
 */
import { useCallback, useEffect, useState, type FormEvent } from "react";
import { liste } from "../../formeRecue";
import { Message } from "../../composants";
import { useApiEffectifs } from "../api";
import { libelleDe, useReferentiels } from "../planification/useReferentiels";

export interface SeuilsConvention {
  conventionCode: string;
  seuilJourHeures: number | null;
  seuilSemaineHeures: number | null;
  seuilPeriodePaieHeures: number | null;
}

type CleSeuil = "seuilJourHeures" | "seuilSemaineHeures" | "seuilPeriodePaieHeures";

export const CHAMPS_SEUILS: ReadonlyArray<readonly [CleSeuil, string, number, number]> = [
  ["seuilJourHeures", "Heures par jour", 1, 24],
  ["seuilSemaineHeures", "Heures par semaine", 1, 168],
  ["seuilPeriodePaieHeures", "Heures par période de paie", 1, 744],
];

const VIDE: Record<CleSeuil, string> = { seuilJourHeures: "", seuilSemaineHeures: "", seuilPeriodePaieHeures: "" };

/** Une valeur saisie : vide = reprendre le seuil de l'organisme. */
export const seuilSaisi = (v: string): number | null => (v.trim() === "" ? null : Number(v));

/** Ce qu'une ligne du tableau affiche pour un seuil : sa valeur, ou l'héritage de l'organisme. */
export const seuilEnMots = (v: number | null): string => (v === null ? "celui de l'organisme" : `${v} h`);

export default function SeuilsConventions() {
  const { lire, envoyer, erreur } = useApiEffectifs();
  const { refs } = useReferentiels(["conventions"]);
  const [seuils, setSeuils] = useState<SeuilsConvention[]>([]);
  const [convention, setConvention] = useState("");
  const [saisie, setSaisie] = useState<Record<CleSeuil, string>>(VIDE);
  const [info, setInfo] = useState("");
  const charger = useCallback(async () => {
    setSeuils(liste<SeuilsConvention>(await lire<SeuilsConvention[]>("/api/effectifs/temps/seuils-conventions")));
  }, [lire]);
  useEffect(() => { void charger(); }, [charger]);

  const choisir = (code: string) => {
    setConvention(code); setInfo("");
    const s = seuils.find((x) => x.conventionCode === code);
    setSaisie(s ? { seuilJourHeures: s.seuilJourHeures?.toString() ?? "", seuilSemaineHeures: s.seuilSemaineHeures?.toString() ?? "",
      seuilPeriodePaieHeures: s.seuilPeriodePaieHeures?.toString() ?? "" } : VIDE);
  };

  const enregistrer = async (e: FormEvent) => {
    e.preventDefault();
    setInfo("");
    const corps = Object.fromEntries(CHAMPS_SEUILS.map(([cle]) => [cle, seuilSaisi(saisie[cle])]));
    const r = await envoyer<SeuilsConvention[]>(`/api/effectifs/temps/seuils-conventions/${encodeURIComponent(convention)}`, corps, "PUT");
    if (!r) return;
    setSeuils(liste<SeuilsConvention>(r));
    const retiree = !r.some((x) => x.conventionCode === convention);
    setInfo(retiree ? `La convention ${libelleDe(refs.conventions, convention)} reprend les seuils de l'organisme.`
      : `Seuils de la convention ${libelleDe(refs.conventions, convention)} enregistrés : ils valent pour les feuilles calculées à partir de maintenant.`);
  };

  return (
    <section aria-labelledby="pla-tps-sc-titre" data-testid="pla-tps-sc">
      <h3 id="pla-tps-sc-titre">Seuils par convention</h3>
      <p>Une convention collective peut fixer ses propres seuils d'heures supplémentaires. Un seuil laissé vide reprend celui de l'organisme.</p>
      <form onSubmit={(e) => void enregistrer(e)} aria-label="Seuils d'une convention">
        <p>
          <label htmlFor="pla-tps-sc-convention">Convention collective</label>
          <select id="pla-tps-sc-convention" required data-testid="pla-tps-sc-convention" value={convention} onChange={(e) => choisir(e.target.value)}>
            <option value="">— choisir —</option>
            {(refs.conventions ?? []).map((c) => <option key={c.code} value={c.code}>{c.libelleFr}</option>)}
          </select>
        </p>
        {CHAMPS_SEUILS.map(([cle, libelle, min, max]) => (
          <p key={cle}>
            <label htmlFor={`pla-tps-sc-${cle}`}>{libelle} (vide : celui de l'organisme)</label>
            <input id={`pla-tps-sc-${cle}`} type="number" min={min} max={max} step={1} data-testid={`pla-tps-sc-${cle}`} disabled={!convention}
              value={saisie[cle]} onChange={(e) => setSaisie((s) => ({ ...s, [cle]: e.target.value }))} />
          </p>
        ))}
        {erreur && <Message variante="erreur" testId="pla-tps-sc-erreur">{erreur}</Message>}
        <p><button type="submit" data-testid="pla-tps-sc-enregistrer" disabled={!convention}>Enregistrer les seuils</button></p>
      </form>
      {info && <p role="status" data-testid="pla-tps-sc-info">{info}</p>}
      {seuils.length > 0 && (
        <table className="eff-table-compacte" data-testid="pla-tps-sc-table">
          <caption>Conventions qui ont leurs propres seuils</caption>
          <thead>
            <tr><th scope="col">Convention</th>{CHAMPS_SEUILS.map(([cle, libelle]) => <th key={cle} scope="col">{libelle}</th>)}</tr>
          </thead>
          <tbody>
            {seuils.map((s) => (
              <tr key={s.conventionCode}>
                <th scope="row">{libelleDe(refs.conventions, s.conventionCode)}</th>
                {CHAMPS_SEUILS.map(([cle]) => <td key={cle}>{seuilEnMots(s[cle])}</td>)}
              </tr>
            ))}
          </tbody>
        </table>
      )}
    </section>
  );
}
