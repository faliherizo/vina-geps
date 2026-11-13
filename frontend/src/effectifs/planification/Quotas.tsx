/**
 * **Quotas de comblement** (AO 26-015-DO, écran E9 — E-GR-8, E-GR-9).
 *
 * « Surcharger temporairement » refuse un début passé, **et le dit** : « une surcharge ne peut pas
 * être rétroactive ». La colonne « En vigueur maintenant » dit d'où vient la valeur — version datée
 * ou surcharge — pour qu'on ne s'étonne pas d'un chiffre qu'on n'a pas saisi.
 */
import { useCallback, useEffect, useState } from "react";
import { Badge, EtatVide, Message, Tableau, Tiroir, type Colonne } from "../../composants";
import { aujourdhui, useApiEffectifs } from "../api";
import type { Quota } from "./types";
import { libelleDe, useReferentiels } from "./useReferentiels";

const API = "/api/effectifs/planification/quotas";

export default function Quotas() {
  const { lire, envoyer, erreur, setErreur, succes, setSucces } = useApiEffectifs();
  const { refs } = useReferentiels(["unites", "plages_horaires", "natures_quart"]);
  const [quotas, setQuotas] = useState<Quota[]>([]);
  const [definition, setDefinition] = useState(false);
  const [surcharge, setSurcharge] = useState<Quota | null>(null);
  const charger = useCallback(async () => { const q = await lire<Quota[]>(API); if (q) setQuotas(q); }, [lire]);
  useEffect(() => { void charger(); }, [charger]);

  const colonnes: ReadonlyArray<Colonne<Quota>> = [
    { id: "unite", libelle: "Unité", tri: (q) => q.uniteCode },
    { id: "plage", libelle: "Plage" },
    { id: "nature", libelle: "Nature" },
    { id: "maintenant", libelle: "En vigueur maintenant", numerique: true },
    { id: "versions", libelle: "Versions" },
    { id: "actions", libelle: "Actions", actions: true },
  ];

  return (
    <div data-testid="pla-quotas">
      <p className="aide">Un quota fixe le nombre d'équipages opérationnels visé par jour, pour une unité et, au besoin, une plage ou une nature de quart.</p>
      <div className="eff-barre"><button type="button" data-testid="pla-quota-definir" onClick={() => { setErreur(""); setDefinition(true); }}>Définir un quota</button></div>
      {erreur && !definition && !surcharge && <Message variante="erreur" testId="pla-quotas-erreur">{erreur}</Message>}
      {succes && <Message variante="succes" testId="pla-quotas-succes">{succes}</Message>}
      <Tableau colonnes={colonnes} lignes={quotas} legende="Quotas de comblement" testId="pla-quotas-tableau"
        vide={<EtatVide titre="Aucun quota" compact testId="pla-quotas-vide">Aucun quota n'est défini.</EtatVide>}
        rendre={(q) => (
          <tr key={q.cle} data-testid={`pla-quota-${q.cle}`}>
            <td>{q.uniteCode}</td>
            <td>{q.plageCode ? libelleDe(refs.plages_horaires, q.plageCode) : "Toutes"}</td>
            <td>{q.natureCode ? libelleDe(refs.natures_quart, q.natureCode) : "Toutes"}</td>
            <td className="col-numerique">
              {q.enVigueur ? <>{q.enVigueur.quota} {q.enVigueur.origine === "SURCHARGE" && <Badge ton="info">surcharge</Badge>}</> : "—"}
            </td>
            <td>{q.versions.map((v) => `${v.quota} depuis le ${v.effectifDepuis}`).join(" · ")}</td>
            <td className="col-menu">
              <button type="button" className="secondary" data-testid={`pla-quota-surcharger-${q.cle}`} onClick={() => { setErreur(""); setSurcharge(q); }}>
                Surcharger temporairement<span className="sr-only"> le quota {q.cle}</span>
              </button>
            </td>
          </tr>
        )} />
      {definition && <TiroirDefinition refs={refs} erreur={erreur} onFermer={() => setDefinition(false)} onEnregistrer={async (c) => {
        const r = await envoyer<Quota>(API, c);
        if (r) { setDefinition(false); setSucces("Quota enregistré."); await charger(); }
      }} />}
      {surcharge && <TiroirSurcharge quota={surcharge} erreur={erreur} onFermer={() => setSurcharge(null)} onEnregistrer={async (c) => {
        const version = surcharge.versions[surcharge.versions.length - 1];
        const r = await envoyer<Quota>(`${API}/${version.id}/surcharges`, c);
        if (r) { setSurcharge(null); setSucces("Surcharge temporaire enregistrée."); await charger(); }
      }} />}
    </div>
  );
}

function TiroirDefinition({ refs, erreur, onFermer, onEnregistrer }: { refs: Record<string, Array<{ code: string; libelleFr: string }>>; erreur: string; onFermer: () => void; onEnregistrer: (c: Record<string, unknown>) => Promise<void> }) {
  const [v, setV] = useState({ uniteCode: "", plageCode: "", natureCode: "", quota: "1", depuis: aujourdhui() });
  const poser = (k: keyof typeof v, x: string) => setV((o) => ({ ...o, [k]: x }));
  const liste = (k: "uniteCode" | "plageCode" | "natureCode", libelle: string, type: string, requis: boolean) => (
    <p>
      <label htmlFor={`pla-quo-${k}`}>{libelle}</label>
      <select id={`pla-quo-${k}`} data-testid={`pla-quo-${k}`} required={requis} value={v[k]} onChange={(e) => poser(k, e.target.value)}>
        <option value="">{requis ? "Choisir…" : "Toutes"}</option>
        {(refs[type] ?? []).map((o) => <option key={o.code} value={o.code}>{o.libelleFr}</option>)}
      </select>
    </p>
  );
  return (
    <Tiroir ouvert titre="Définir un quota" onFermer={onFermer} testId="pla-quota-tiroir">
      <form onSubmit={(e) => { e.preventDefault(); void onEnregistrer({ ...v, quota: Number(v.quota), plageCode: v.plageCode || null, natureCode: v.natureCode || null }); }}>
        {liste("uniteCode", "Unité", "unites", true)}
        {liste("plageCode", "Plage horaire", "plages_horaires", false)}
        {liste("natureCode", "Nature de quart", "natures_quart", false)}
        <p><label htmlFor="pla-quo-quota">Équipages opérationnels visés par jour</label><input id="pla-quo-quota" type="number" min={0} data-testid="pla-quo-quota" required value={v.quota} onChange={(e) => poser("quota", e.target.value)} /></p>
        <p><label htmlFor="pla-quo-depuis">En vigueur à partir du (aujourd'hui ou plus tard)</label><input id="pla-quo-depuis" type="date" data-testid="pla-quo-depuis" required value={v.depuis} onChange={(e) => poser("depuis", e.target.value)} /></p>
        {erreur && <Message variante="erreur" testId="pla-quo-erreur">{erreur}</Message>}
        <button type="submit" data-testid="pla-quo-enregistrer">Enregistrer</button>
      </form>
    </Tiroir>
  );
}

function TiroirSurcharge({ quota, erreur, onFermer, onEnregistrer }: { quota: Quota; erreur: string; onFermer: () => void; onEnregistrer: (c: Record<string, unknown>) => Promise<void> }) {
  const [valeur, setValeur] = useState(String(quota.enVigueur?.quota ?? 0));
  const [depuis, setDepuis] = useState("");
  const [jusqua, setJusqua] = useState("");
  const [motif, setMotif] = useState("");
  return (
    <Tiroir ouvert titre={`Surcharger le quota ${quota.cle}`} onFermer={onFermer} testId="pla-surcharge-tiroir">
      <form onSubmit={(e) => {
        e.preventDefault();
        void onEnregistrer({ valeur: Number(valeur), depuis: new Date(depuis).toISOString(), jusqua: new Date(jusqua).toISOString(), motif });
      }}>
        <p className="aide" id="pla-sur-aide">Une surcharge commence maintenant ou plus tard, dure 90 jours au plus, et ne change jamais un jour déjà passé.</p>
        <p><label htmlFor="pla-sur-valeur">Valeur temporaire</label><input id="pla-sur-valeur" type="number" min={0} data-testid="pla-sur-valeur" required value={valeur} onChange={(e) => setValeur(e.target.value)} /></p>
        <p><label htmlFor="pla-sur-depuis">À partir de</label><input id="pla-sur-depuis" type="datetime-local" data-testid="pla-sur-depuis" required aria-describedby="pla-sur-aide" value={depuis} onChange={(e) => setDepuis(e.target.value)} /></p>
        <p><label htmlFor="pla-sur-jusqua">Jusqu'à (exclu)</label><input id="pla-sur-jusqua" type="datetime-local" data-testid="pla-sur-jusqua" required value={jusqua} onChange={(e) => setJusqua(e.target.value)} /></p>
        <p><label htmlFor="pla-sur-motif">Motif</label><input id="pla-sur-motif" data-testid="pla-sur-motif" required value={motif} onChange={(e) => setMotif(e.target.value)} /></p>
        {erreur && <Message variante="erreur" testId="pla-sur-erreur">{erreur}</Message>}
        <button type="submit" data-testid="pla-sur-enregistrer">Enregistrer la surcharge</button>
      </form>
    </Tiroir>
  );
}
