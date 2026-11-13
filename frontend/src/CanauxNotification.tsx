/**
 * « Mes notifications » (devis 2.6 ; ADR 0029) : la personne choisit par où les avis lui
 * parviennent — courriel, texto, poussée — en général **et événement par événement**. Un canal
 * fermé ici n'est plus emprunté par l'ordonnanceur, les avis de la paie ni les envois de
 * l'encadrement ; au moins un canal écrit reste ouvert, en général et pour chaque avis essentiel.
 * Fermer un canal vaut retrait immédiat (LCAP, Loi 25).
 */
import { Fragment, useEffect, useMemo, useState } from "react";
import { Message } from "./composants";
import { Canaux, EvenementNotifiable, PreferencesAvis, canauxEffectifs, refusMatrice } from "./reglesTextos";

interface Props { call: (chemin: string, init?: RequestInit) => Promise<Response>; signaler: (erreur: string) => void; confirmer: (message: string) => void }

const CANAUX: Array<{ cle: keyof Canaux; libelle: string; aide: string }> = [
  { cle: "courriel", libelle: "Courriel", aide: "à l'adresse de votre dossier" },
  { cle: "texto", libelle: "Texto (SMS)", aide: "au numéro de téléphone de votre dossier" },
  { cle: "poussee", libelle: "Poussée", aide: "sur les appareils et navigateurs où vous l'avez activée" },
];

export default function CanauxNotification({ call, signaler, confirmer }: Props) {
  const [prefs, setPrefs] = useState<PreferencesAvis | null>(null);
  const [evenements, setEvenements] = useState<EvenementNotifiable[]>([]);
  const [refus, setRefus] = useState("");

  useEffect(() => {
    let vivant = true;
    void Promise.all([
      call("/api/notifications/preferences").then((r) => (r.ok ? r.json() : null)),
      call("/api/notifications/evenements").then((r) => (r.ok ? r.json() : [])),
    ]).then(([p, ev]) => {
      if (!vivant) return;
      if (p && typeof p.courriel === "boolean") setPrefs({ ...p, evenements: p.evenements && typeof p.evenements === "object" ? p.evenements : {} });
      if (Array.isArray(ev)) setEvenements(ev.filter((e) => e && typeof e.code === "string" && e.defauts));
    });
    return () => { vivant = false; };
  }, [call]);

  const parModule = useMemo(() => {
    const groupes = new Map<string, EvenementNotifiable[]>();
    for (const e of evenements) groupes.set(e.moduleLibelle, [...(groupes.get(e.moduleLibelle) ?? []), e]);
    return [...groupes.entries()];
  }, [evenements]);

  function reglerEvenement(e: EvenementNotifiable, canal: keyof Canaux, valeur: boolean) {
    if (!prefs) return;
    setPrefs({ ...prefs, evenements: { ...prefs.evenements, [e.code]: { ...canauxEffectifs(e, prefs), [canal]: valeur } } });
  }

  async function enregistrer(ev: React.FormEvent) {
    ev.preventDefault();
    if (!prefs) return;
    const motif = refusMatrice(prefs, evenements);
    setRefus(motif ?? "");
    if (motif) return;
    const r = await call("/api/notifications/preferences", { method: "PUT", body: JSON.stringify({ courriel: prefs.courriel, texto: prefs.texto, poussee: prefs.poussee, evenements: prefs.evenements, courrielPersonnel: prefs.courrielPersonnel?.trim() || null }) });
    if (!r.ok) { const j = await r.json().catch(() => ({})); signaler(j.errors?.join(", ") || `Erreur ${r.status}`); return; }
    setPrefs(await r.json());
    confirmer("Vos notifications sont enregistrées.");
  }

  return (
    <section className="card" aria-labelledby="acc-canaux-title" data-testid="account-canaux">
      <h2 id="acc-canaux-title">Mes notifications</h2>
      <p className="muted">Choisissez, événement par événement, par où les avis vous parviennent. Fermer un canal vaut retrait immédiat. Les avis <strong>essentiels</strong> gardent au moins le courriel ou le texto.</p>
      {prefs && (
        <form onSubmit={enregistrer} data-testid="account-canaux-form">
          <div className="table-wrap" tabIndex={0} role="region" aria-label="Mes notifications par événement et par canal (zone défilable au clavier)">
            <table data-testid="account-canaux-matrice">
              <caption className="sr-only">Mes notifications : une ligne par événement, une colonne par canal</caption>
              <thead>
                <tr>
                  <th scope="col">Avis</th>
                  {CANAUX.map((c) => <th key={c.cle} scope="col">{c.libelle} <span className="muted" style={{ fontWeight: 400 }}>({c.aide})</span></th>)}
                </tr>
              </thead>
              <tbody>
                <tr data-testid="account-canaux-general">
                  <th scope="row">Tous les avis <span className="muted">(interrupteur général)</span></th>
                  {CANAUX.map((c) => (
                    <td key={c.cle}>
                      <input type="checkbox" data-testid={`account-canal-${c.cle}`} checked={prefs[c.cle]} aria-label={`Tous les avis par ${c.libelle}`}
                             onChange={(e) => setPrefs({ ...prefs, [c.cle]: e.target.checked })} />
                    </td>
                  ))}
                </tr>
                {parModule.map(([module, liste]) => (
                  <Fragment key={module}>
                    <tr className="row-group"><th scope="rowgroup" colSpan={CANAUX.length + 1}>{module}</th></tr>
                    {liste.map((e) => {
                      const canaux = canauxEffectifs(e, prefs);
                      return (
                        <tr key={e.code} data-testid={`account-avis-${e.code}`}>
                          <th scope="row">{e.libelle}{e.essentiel && <> <span className="badge badge-rule" data-testid={`account-avis-${e.code}-essentiel`}>essentiel</span></>}</th>
                          {CANAUX.map((c) => (
                            <td key={c.cle}>
                              <input type="checkbox" data-testid={`account-avis-${e.code}-${c.cle}`} checked={canaux[c.cle]} disabled={!prefs[c.cle]}
                                     aria-label={`${e.libelle} par ${c.libelle}${prefs[c.cle] ? "" : " (canal fermé par l'interrupteur général)"}`}
                                     onChange={(ev) => reglerEvenement(e, c.cle, ev.target.checked)} />
                            </td>
                          ))}
                        </tr>
                      );
                    })}
                  </Fragment>
                ))}
              </tbody>
            </table>
          </div>
          {/* CSSMB 21 : l'avis par courriel part aussi à l'adresse personnelle que la personne donne — elle seule la donne, et peut la retirer. */}
          <label className="field" style={{ marginTop: 12 }}><span>Courriel personnel (les avis y partent aussi)</span>
            <input type="email" data-testid="account-courriel-personnel" value={prefs.courrielPersonnel ?? ""} onChange={(e) => setPrefs({ ...prefs, courrielPersonnel: e.target.value })} aria-describedby="account-courriel-personnel-aide" autoComplete="email" />
            <span className="aide" id="account-courriel-personnel-aide">Facultatif. Un remplaçant ne lit pas toujours sa boîte institutionnelle le soir : chaque avis par courriel part aussi ici. Videz le champ pour ne recevoir que l'institutionnel.</span></label>
          {refus && <Message variante="erreur" testId="account-canaux-refus">{refus}</Message>}
          <div className="field-actions" style={{ marginTop: 12 }}>
            <button type="submit" className="primary" data-testid="account-canaux-save">Enregistrer mes notifications</button>
          </div>
        </form>
      )}
    </section>
  );
}
