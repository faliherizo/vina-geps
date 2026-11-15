/**
 * **La cascade d'une alerte** (ST-G04 ; critères 17, 19) — où elle en est, sa trace (chaque étape, qui elle a visé,
 * qui ou quel mécanisme a agi) et la reprise manuelle à une étape choisie.
 */
import { useCallback, useEffect, useState, type FormEvent } from "react";
import { BadgeStatut, Message } from "../composants";
import { useApiEffectifs as useApi } from "../effectifs/api";
import { libelleEtatCascade, libelleEvenement, libelleResultatAppel, quand, type SuiviCascade } from "./types";

const TON: Record<string, string> = { armee: "en_cours", arretee: "approved", epuisee: "refusee" };

export default function CascadeAlerte({ alerteId }: { alerteId: string }) {
  const { lire, envoyer, erreur, succes, setSucces } = useApi();
  const [suivi, setSuivi] = useState<SuiviCascade | null>(null);
  const [etape, setEtape] = useState("1");
  const charger = useCallback(async () => setSuivi(await lire<SuiviCascade>(`/api/alertes/envoyees/${alerteId}/cascade`)), [lire, alerteId]);
  useEffect(() => { void charger(); }, [charger]);
  const reprendre = async (e: FormEvent) => {
    e.preventDefault();
    if (await envoyer(`/api/alertes/envoyees/${alerteId}/cascade/reprendre`, { etape: Number(etape) })) {
      setSucces(`Cascade reprise à l'étape ${etape} : elle part maintenant.`);
      await charger();
    }
  };
  if (!suivi) return null;
  const c = suivi.cascade;
  return (
    <section aria-labelledby="als-cascade-h" data-testid="als-cascade">
      <h3 id="als-cascade-h">Relances et escalades</h3>
      {erreur && <Message variante="erreur" testId="als-cascade-erreur">{erreur}</Message>}
      {succes && <Message variante="succes" testId="als-cascade-succes">{succes}</Message>}
      {!c ? <p className="muted" data-testid="als-cascade-aucune">Cette alerte est partie sans politique de relance.</p> : (
        <>
          <p data-testid="als-cascade-etat">« {c.politique.nom} » (version {c.politique.version}) —{" "}
            <BadgeStatut statut={TON[c.etat]}>{libelleEtatCascade(c.etat)}</BadgeStatut>
            {c.etat === "armee" && <> : étape {c.etape} sur {c.etapes}, prévue {quand(c.echeanceLe)}</>}</p>
          <table className="eff-table-compacte" data-testid="als-cascade-trace">
            <caption>Trace de la cascade, dans l'ordre</caption>
            <thead><tr><th scope="col">Quand</th><th scope="col">Événement</th><th scope="col">Étape</th><th scope="col">Personnes visées</th><th scope="col">Par</th></tr></thead>
            <tbody>{suivi.traces.map((t, i) => (
              <tr key={i}><td>{quand(t.instant)}</td><td>{libelleEvenement(t.evenement)}</td><td className="num">{t.etape}</td>
                <td>{t.personnes.join(", ") || "—"}</td><td>{t.par}</td></tr>
            ))}</tbody>
          </table>
          {(suivi.appels ?? []).length > 0 && (
            <table className="eff-table-compacte" data-testid="als-cascade-appels">
              <caption>Appels automatiques : le résultat et la durée de chacun</caption>
              <thead><tr><th scope="col">Personne</th><th scope="col">Étape</th><th scope="col">Résultat</th><th scope="col">Touche</th><th scope="col">Durée</th></tr></thead>
              <tbody>{(suivi.appels ?? []).map((a, i) => (
                <tr key={i}><td>{a.personId}</td><td className="num">{a.etape}</td><td>{libelleResultatAppel(a.resultat)}</td>
                  <td>{a.touche ?? "—"}</td><td className="num">{a.dureeSecondes == null ? "—" : `${a.dureeSecondes} s`}</td></tr>
              ))}</tbody>
            </table>
          )}
          <form className="eff-barre" aria-label="Reprendre la cascade" onSubmit={(e) => void reprendre(e)}>
            <p><label htmlFor="als-cascade-etape">Reprendre à l'étape</label>
              <select id="als-cascade-etape" data-testid="als-cascade-etape" value={etape} onChange={(e) => setEtape(e.target.value)}>
                {Array.from({ length: c.etapes }, (_, i) => String(i + 1)).map((n) => <option key={n} value={n}>{n}</option>)}
              </select></p>
            <p><button type="submit" className="secondary" data-testid="als-cascade-reprendre">Reprendre maintenant</button></p>
          </form>
        </>
      )}
    </section>
  );
}
