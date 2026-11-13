/**
 * **Équipages et quarts** (AO 26-015-DO, écran E5 — E-GN-1 à E-GN-4, E-GN-11, E-CH-3, E-GR-5, E-GR-6).
 *
 * Trois décisions d'interface qu'aucun outil d'audit ne vérifie :
 *
 * - **L'état opérationnel est un texte et une icône**, pas une couleur : « Sous le minimum (1/2) ».
 * - **Un refus par règle montre ses motifs** avant de proposer quoi que ce soit ; le bouton
 *   « Forcer » n'apparaît qu'après, avec une justification obligatoire et reliée à son erreur.
 * - **La sélection pour une modification en lot se fait par cases de 24 px**, jamais par glisser.
 */
import { useCallback, useEffect, useMemo, useState } from "react";
import { Badge, EtatVide, Message, Tableau, Tiroir, type Colonne } from "../../composants";
import { heure, lisible, minutes, useApiEffectifs } from "../api";
import type { Valeur } from "../Referentiels";
import type { Equipage, Periode, Place, Ressource, Verdict } from "./types";
import { libelleDe, useReferentiels, type Milieu } from "./useReferentiels";

const API = "/api/effectifs/planification";

export default function Equipages({ periode }: { periode: Periode | null }) {
  const { lire, envoyer, erreur, setErreur, succes, setSucces } = useApiEffectifs();
  const { refs, milieux } = useReferentiels(["corps_emploi", "natures_quart", "plages_horaires"]);
  const [equipages, setEquipages] = useState<Equipage[]>([]);
  const [ressources, setRessources] = useState<Ressource[]>([]);
  const [date, setDate] = useState("");
  const [choisis, setChoisis] = useState<Set<string>>(new Set());
  const [attribution, setAttribution] = useState<{ equipage: Equipage; place: Place } | null>(null);
  const [liberation, setLiberation] = useState<{ equipage: Equipage; place: Place } | null>(null);
  const [ajustement, setAjustement] = useState<Equipage | "lot" | null>(null);
  const [adhoc, setAdhoc] = useState(false);

  const charger = useCallback(async () => {
    if (!periode) { setEquipages([]); return; }
    const e = await lire<Equipage[]>(`${API}/periodes/${periode.id}/equipages${date ? `?date=${date}` : ""}`);
    if (e) setEquipages(e);
  }, [lire, periode, date]);
  useEffect(() => { void charger(); }, [charger]);
  useEffect(() => { void lire<Ressource[]>(`${API}/ressources`).then((r) => setRessources(r ?? [])); }, [lire]);

  const milieu = (id: string) => milieux.find((m) => m.id === id)?.libelle ?? id;
  const basculer = (id: string, oui: boolean) => setChoisis((s) => { const n = new Set(s); if (oui) n.add(id); else n.delete(id); return n; });

  const colonnes: ReadonlyArray<Colonne<Equipage>> = [
    { id: "choix", libelle: <span className="sr-only">Sélection</span> },
    { id: "quand", libelle: "Quart", tri: (e) => `${e.date}${String(e.debut).padStart(4, "0")}` },
    { id: "milieu", libelle: "Milieu" },
    { id: "nature", libelle: "Nature" },
    { id: "places", libelle: "Places" },
    { id: "etat", libelle: "État", tri: (e) => e.statut },
    { id: "actions", libelle: "Actions", actions: true },
  ];

  if (!periode) return <EtatVide titre="Choisissez une période" compact testId="pla-equipages-sans-periode">Les quarts se consultent période par période.</EtatVide>;

  return (
    <div data-testid="pla-equipages">
      <div className="eff-barre">
        <p>
          <label htmlFor="pla-eq-date">Jour (facultatif)</label>
          <input id="pla-eq-date" type="date" data-testid="pla-eq-date" min={periode.debut} max={periode.fin} value={date} onChange={(e) => setDate(e.target.value)} />
        </p>
        <button type="button" data-testid="pla-eq-adhoc" disabled={periode.etat === "CLOSE"} onClick={() => { setErreur(""); setAdhoc(true); }}>Quart ad hoc</button>
        <button type="button" className="secondary" data-testid="pla-eq-lot" disabled={choisis.size === 0} onClick={() => { setErreur(""); setAjustement("lot"); }}>
          Modifier la sélection ({choisis.size})
        </button>
      </div>
      {erreur && !attribution && !ajustement && !adhoc && <Message variante="erreur" testId="pla-eq-erreur">{erreur}</Message>}
      {succes && <Message variante="succes" testId="pla-eq-succes">{succes}</Message>}
      <Tableau colonnes={colonnes} lignes={equipages} legende={`Quarts de la période ${periode.nom}`} testId="pla-eq-tableau" taille={20}
        vide={<EtatVide titre="Aucun quart" compact testId="pla-eq-vide">Générez les quarts depuis les postes, ou créez un quart ad hoc.</EtatVide>}
        rendre={(e) => (
          <tr key={e.id} data-testid={`pla-eq-${e.id}`}>
            <td className="eff-case">
              <input type="checkbox" id={`pla-eq-choix-${e.id}`} data-testid={`pla-eq-choix-${e.id}`} checked={choisis.has(e.id)} onChange={(x) => basculer(e.id, x.target.checked)} />
              <label htmlFor={`pla-eq-choix-${e.id}`} className="sr-only">Sélectionner le quart du {e.date} à {heure(e.debut)}</label>
            </td>
            <td>{e.date} · {heure(e.debut)}–{heure(e.fin)}{e.note ? <span className="muted"> · {e.note}</span> : null}</td>
            <td>{milieu(e.milieuId)}</td>
            <td>{libelleDe(refs.natures_quart, e.natureCode)}{e.plageCode ? ` · ${libelleDe(refs.plages_horaires, e.plageCode)}` : ""} · {e.modeAttribution === "AUTOMATISE" ? "attribution automatisée" : "attribution manuelle"}</td>
            <td>
              <ul className="eff-places">
                {e.places.map((p) => (
                  <li key={p.id} data-testid={`pla-place-${p.id}`}>
                    {libelleDe(refs.corps_emploi, p.corpsEmploiCode)} : {p.ressourceNom ?? <em>vacante</em>}
                    {p.justificationForcage && <Badge ton="avertissement" title={p.justificationForcage}>forcée</Badge>}{" "}
                    {!p.ressourceId && periode.etat !== "CLOSE" && (
                      <button type="button" className="secondary" data-testid={`pla-place-attribuer-${p.id}`} onClick={() => { setErreur(""); setAttribution({ equipage: e, place: p }); }}>
                        Attribuer<span className="sr-only"> la place de {p.corpsEmploiCode} du {e.date}</span>
                      </button>
                    )}
                    {p.ressourceId && periode.etat !== "CLOSE" && (
                      <button type="button" className="secondary" data-testid={`pla-place-liberer-${p.id}`} onClick={() => { setErreur(""); setLiberation({ equipage: e, place: p }); }}>
                        Libérer<span className="sr-only"> la place de {p.ressourceNom} du {e.date}</span>
                      </button>
                    )}
                  </li>
                ))}
              </ul>
            </td>
            <td data-testid={`pla-eq-etat-${e.id}`}>
              {e.statut === "OPERATIONNEL"
                ? <Badge ton="succes">✓ Opérationnel ({e.pourvues}/{e.places.length})</Badge>
                : <Badge ton="erreur">⚠ Sous le minimum ({e.pourvues}/{e.minimumOperationnel} requis)</Badge>}
            </td>
            <td className="col-menu">
              {periode.etat !== "CLOSE" && (
                <button type="button" className="secondary" data-testid={`pla-eq-ajuster-${e.id}`} onClick={() => { setErreur(""); setAjustement(e); }}>
                  Ajuster<span className="sr-only"> le quart du {e.date}</span>
                </button>
              )}
            </td>
          </tr>
        )} />

      {attribution && (
        <TiroirAttribution attribution={attribution} ressources={ressources.filter((r) => r.actif && r.corpsEmploiCode === attribution.place.corpsEmploiCode)}
          onFermer={() => setAttribution(null)} onFait={async (message) => { setAttribution(null); setSucces(message); await charger(); }} />
      )}
      {liberation && (
        <TiroirLiberation liberation={liberation} onFermer={() => setLiberation(null)} onFait={async (message) => { setLiberation(null); setSucces(message); await charger(); }} />
      )}
      {ajustement && (
        <TiroirAjustement cible={ajustement} milieux={milieux} erreur={erreur} onFermer={() => setAjustement(null)} onEnregistrer={async (changements) => {
          const r = ajustement === "lot"
            ? await envoyer(`${API}/equipages/lot`, { ids: [...choisis], changements })
            : await envoyer(`${API}/equipages/${ajustement.id}`, changements, "PATCH");
          if (r) { setAjustement(null); setChoisis(new Set()); setSucces(ajustement === "lot" ? `${choisis.size} quart(s) modifié(s).` : "Quart ajusté."); await charger(); }
        }} />
      )}
      {adhoc && (
        <TiroirAdhoc periode={periode} refs={refs} milieux={milieux} erreur={erreur} onFermer={() => setAdhoc(false)} onEnregistrer={async (c) => {
          const r = await envoyer<Equipage>(`${API}/equipages`, c);
          if (r) { setAdhoc(false); setSucces(`Quart ad hoc du ${r.date} créé (${r.places.length} place(s)).`); await charger(); }
        }} />
      )}
    </div>
  );
}

/** Libérer une place tenue : elle redevient vacante, à combler ; le motif est consigné au journal. */
function TiroirLiberation({ liberation, onFermer, onFait }: { liberation: { equipage: Equipage; place: Place }; onFermer: () => void; onFait: (m: string) => Promise<void> }) {
  const { envoyer, erreur } = useApiEffectifs();
  const [motif, setMotif] = useState("");
  const { equipage, place } = liberation;
  return (
    <Tiroir ouvert titre={`Libérer — ${equipage.date} ${heure(equipage.debut)}–${heure(equipage.fin)}`} onFermer={onFermer} testId="pla-liberation">
      <form onSubmit={async (e) => {
        e.preventDefault();
        if (await envoyer(`${API}/places/${place.id}/liberer`, { motif })) await onFait(`Place de ${place.ressourceNom} libérée : elle est vacante, à combler.`);
      }}>
        <p>
          <label htmlFor="pla-lib-motif">Pourquoi libérer la place de {place.ressourceNom} ?</label>
          <textarea id="pla-lib-motif" data-testid="pla-lib-motif" rows={2} required maxLength={500} value={motif} onChange={(e) => setMotif(e.target.value)} />
        </p>
        {erreur && <Message variante="erreur" testId="pla-lib-erreur">{erreur}</Message>}
        <button type="submit" data-testid="pla-lib-liberer">Libérer la place</button>
      </form>
    </Tiroir>
  );
}

/**
 * Le tiroir d'attribution d'une place — **exporté** pour que le tableau de comblement l'ouvre
 * depuis la ligne d'un quart sous minimum (planches s01 et s05 : « Combler »). Un seul formulaire,
 * deux portes : le recopier aurait fait deux attributions qui divergent au premier correctif.
 */
export function TiroirAttribution({ attribution, ressources, onFermer, onFait }: {
  attribution: { equipage: Equipage; place: Place }; ressources: Ressource[]; onFermer: () => void; onFait: (m: string) => Promise<void>;
}) {
  const { envoyerBrut, erreur } = useApiEffectifs();
  const [ressourceId, setRessourceId] = useState("");
  const [refus, setRefus] = useState<Verdict | null>(null);
  const [justification, setJustification] = useState("");
  const [avertissements, setAvertissements] = useState<Verdict | null>(null);
  const { equipage, place } = attribution;

  async function attribuer(forcer: boolean) {
    const corps = forcer ? { ressourceId, forcer: true, justification } : { ressourceId };
    const r = await envoyerBrut(`${API}/places/${place.id}/attribuer`, corps);
    if (r.ok) {
      const v = r.corps.verdict as Verdict;
      if (v.action === "AVERTIR" && !avertissements) { setAvertissements(v); return; }
      await onFait(r.corps.force ? "Attribution forcée, justification consignée au journal." : "Place attribuée.");
    } else if (r.statut === 409 && r.corps.verdict) {
      setRefus(r.corps.verdict as Verdict);
    }
  }

  return (
    <Tiroir ouvert titre={`Attribuer — ${equipage.date} ${heure(equipage.debut)}–${heure(equipage.fin)}`} onFermer={onFermer} testId="pla-attribution">
      <form onSubmit={(e) => { e.preventDefault(); void attribuer(refus !== null); }}>
        <p>
          <label htmlFor="pla-att-ressource">Personne ({place.corpsEmploiCode})</label>
          <select id="pla-att-ressource" data-testid="pla-att-ressource" required value={ressourceId} onChange={(e) => { setRessourceId(e.target.value); setRefus(null); setAvertissements(null); }}>
            <option value="">Choisir…</option>
            {ressources.map((r) => <option key={r.id} value={r.id}>{r.nom} ({r.matricule})</option>)}
          </select>
        </p>
        {avertissements && (
          <div role="status" data-testid="pla-att-avertissements">
            <p>Les règles avertissent, sans empêcher :</p>
            <ul>{avertissements.motifs.map((m, i) => <li key={i}>{m.message}</li>)}</ul>
            <button type="button" data-testid="pla-att-confirmer" onClick={() => void onFait("Place attribuée (avec avertissement).")}>Compris</button>
          </div>
        )}
        {refus && (
          <div role="alert" data-testid="pla-att-refus">
            <p><strong>Les règles s'opposent à cette attribution :</strong></p>
            <ul>{refus.motifs.map((m, i) => <li key={i} data-testid={`pla-att-motif-${i}`}>{lisible(m.action)} — {m.message}</li>)}</ul>
            <p>
              <label htmlFor="pla-att-justification">Justification pour forcer (10 caractères au moins, consignée au journal)</label>
              <textarea id="pla-att-justification" data-testid="pla-att-justification" rows={2} required minLength={10} value={justification}
                aria-describedby={erreur ? "pla-att-erreur" : undefined} onChange={(e) => setJustification(e.target.value)} />
            </p>
          </div>
        )}
        {erreur && !refus && <Message variante="erreur" testId="pla-att-erreur">{erreur}</Message>}
        {erreur && refus && <p id="pla-att-erreur" className="aide" data-testid="pla-att-erreur-forcer">{erreur}</p>}
        {!avertissements && (
          <button type="submit" data-testid={refus ? "pla-att-forcer" : "pla-att-attribuer"}>{refus ? "Forcer l'attribution" : "Attribuer"}</button>
        )}
      </form>
    </Tiroir>
  );
}

function TiroirAjustement({ cible, milieux, erreur, onFermer, onEnregistrer }: {
  cible: Equipage | "lot"; milieux: Milieu[]; erreur: string; onFermer: () => void; onEnregistrer: (c: Record<string, unknown>) => Promise<void>;
}) {
  const seul = cible === "lot" ? null : cible;
  const [debut, setDebut] = useState(seul ? heure(seul.debut) : "");
  const [fin, setFin] = useState(seul ? heure(seul.fin) : "");
  const [milieuId, setMilieuId] = useState(seul?.milieuId ?? "");
  const [mode, setMode] = useState(seul?.modeAttribution ?? "");
  const [minimum, setMinimum] = useState(seul ? String(seul.minimumOperationnel) : "");
  const [note, setNote] = useState(seul?.note ?? "");
  return (
    <Tiroir ouvert titre={seul ? `Ajuster le quart du ${seul.date}` : "Modifier les quarts sélectionnés"} onFermer={onFermer} testId="pla-ajustement">
      <form onSubmit={(e) => {
        e.preventDefault();
        const c: Record<string, unknown> = {};
        if (debut && fin && (!seul || debut !== heure(seul.debut) || fin !== heure(seul.fin))) { c.debut = minutes(debut); c.fin = minutes(fin, true); }
        if (milieuId && milieuId !== seul?.milieuId) c.milieuId = milieuId;
        if (mode && mode !== seul?.modeAttribution) c.modeAttribution = mode;
        if (seul && minimum && Number(minimum) !== seul.minimumOperationnel) c.minimumOperationnel = Number(minimum);
        if (seul && note !== (seul.note ?? "")) c.note = note;
        void onEnregistrer(c);
      }}>
        {!seul && <p className="aide">Seuls les champs remplis sont appliqués, à tous les quarts sélectionnés ; si l'un échoue, aucun n'est modifié.</p>}
        <p><label htmlFor="pla-aj-debut">Début</label><input id="pla-aj-debut" type="time" data-testid="pla-aj-debut" value={debut} onChange={(e) => setDebut(e.target.value)} /></p>
        <p><label htmlFor="pla-aj-fin">Fin (avant le début = passe minuit)</label><input id="pla-aj-fin" type="time" data-testid="pla-aj-fin" value={fin} onChange={(e) => setFin(e.target.value)} /></p>
        <p>
          <label htmlFor="pla-aj-milieu">Milieu</label>
          <select id="pla-aj-milieu" data-testid="pla-aj-milieu" value={milieuId} onChange={(e) => setMilieuId(e.target.value)}>
            <option value="">{seul ? "Inchangé" : "Ne pas changer"}</option>
            {milieux.map((m) => <option key={m.id} value={m.id}>{m.code} — {m.libelle}</option>)}
          </select>
        </p>
        <p>
          <label htmlFor="pla-aj-mode">Mode d'attribution</label>
          <select id="pla-aj-mode" data-testid="pla-aj-mode" value={mode} onChange={(e) => setMode(e.target.value as Equipage["modeAttribution"])}>
            <option value="">{seul ? "Inchangé" : "Ne pas changer"}</option>
            <option value="AUTOMATISE">Automatisée</option>
            <option value="MANUEL">Manuelle</option>
          </select>
        </p>
        {seul && (
          <>
            <p><label htmlFor="pla-aj-minimum">Minimum opérationnel (1 à {seul.places.length})</label>
              <input id="pla-aj-minimum" type="number" min={1} max={seul.places.length} data-testid="pla-aj-minimum" value={minimum} onChange={(e) => setMinimum(e.target.value)} /></p>
            <p><label htmlFor="pla-aj-note">Note</label><input id="pla-aj-note" data-testid="pla-aj-note" value={note} onChange={(e) => setNote(e.target.value)} /></p>
          </>
        )}
        {erreur && <Message variante="erreur" testId="pla-aj-erreur">{erreur}</Message>}
        <button type="submit" data-testid="pla-aj-enregistrer">Enregistrer</button>
      </form>
    </Tiroir>
  );
}

function TiroirAdhoc({ periode, refs, milieux, erreur, onFermer, onEnregistrer }: {
  periode: Periode; refs: Record<string, Valeur[]>; milieux: Milieu[]; erreur: string; onFermer: () => void; onEnregistrer: (c: Record<string, unknown>) => Promise<void>;
}) {
  const [date, setDate] = useState(periode.debut);
  const [debut, setDebut] = useState("08:00");
  const [fin, setFin] = useState("12:00");
  const [natureCode, setNatureCode] = useState("");
  const [plageCode, setPlageCode] = useState("");
  const [milieuId, setMilieuId] = useState("");
  const [mode, setMode] = useState("MANUEL");
  const [minimum, setMinimum] = useState("");
  const [composition, setComposition] = useState<Array<{ corpsEmploiCode: string; nombre: string }>>([{ corpsEmploiCode: "", nombre: "1" }]);
  const total = useMemo(() => composition.reduce((t, l) => t + (Number(l.nombre) || 0), 0), [composition]);
  const poser = (i: number, cle: "corpsEmploiCode" | "nombre", v: string) => setComposition((c) => c.map((l, j) => (j === i ? { ...l, [cle]: v } : l)));
  return (
    <Tiroir ouvert titre="Nouveau quart ad hoc" onFermer={onFermer} testId="pla-adhoc">
      <form onSubmit={(e) => {
        e.preventDefault();
        void onEnregistrer({
          periodeId: periode.id, date, debut: minutes(debut), fin: minutes(fin, true), natureCode, plageCode: plageCode || null, milieuId,
          modeAttribution: mode, minimumOperationnel: minimum ? Number(minimum) : undefined,
          composition: composition.map((l) => ({ corpsEmploiCode: l.corpsEmploiCode, nombre: Number(l.nombre) })),
        });
      }}>
        <p><label htmlFor="pla-ah-date">Jour</label><input id="pla-ah-date" type="date" data-testid="pla-ah-date" required min={periode.debut} max={periode.fin} value={date} onChange={(e) => setDate(e.target.value)} /></p>
        <p><label htmlFor="pla-ah-debut">Début</label><input id="pla-ah-debut" type="time" data-testid="pla-ah-debut" required value={debut} onChange={(e) => setDebut(e.target.value)} /></p>
        <p><label htmlFor="pla-ah-fin">Fin (avant le début = passe minuit)</label><input id="pla-ah-fin" type="time" data-testid="pla-ah-fin" required value={fin} onChange={(e) => setFin(e.target.value)} /></p>
        <p>
          <label htmlFor="pla-ah-nature">Nature du quart</label>
          <select id="pla-ah-nature" data-testid="pla-ah-nature" required value={natureCode} onChange={(e) => setNatureCode(e.target.value)}>
            <option value="">Choisir…</option>
            {(refs.natures_quart ?? []).map((v) => <option key={v.code} value={v.code}>{v.libelleFr}</option>)}
          </select>
        </p>
        <p>
          <label htmlFor="pla-ah-plage">Plage horaire (facultatif, pour les quotas)</label>
          <select id="pla-ah-plage" data-testid="pla-ah-plage" value={plageCode} onChange={(e) => setPlageCode(e.target.value)}>
            <option value="">Aucune</option>
            {(refs.plages_horaires ?? []).map((v) => <option key={v.code} value={v.code}>{v.libelleFr}</option>)}
          </select>
        </p>
        <p>
          <label htmlFor="pla-ah-milieu">Milieu</label>
          <select id="pla-ah-milieu" data-testid="pla-ah-milieu" required value={milieuId} onChange={(e) => setMilieuId(e.target.value)}>
            <option value="">Choisir…</option>
            {milieux.map((m) => <option key={m.id} value={m.id}>{m.code} — {m.libelle}</option>)}
          </select>
        </p>
        <fieldset className="eff-groupe">
          <legend>Composition de l'équipage</legend>
          {composition.map((l, i) => (
            <div key={i} className="eff-barre">
              <p>
                <label htmlFor={`pla-ah-corps-${i}`}>Corps d'emploi</label>
                <select id={`pla-ah-corps-${i}`} data-testid={`pla-ah-corps-${i}`} required value={l.corpsEmploiCode} onChange={(e) => poser(i, "corpsEmploiCode", e.target.value)}>
                  <option value="">Choisir…</option>
                  {(refs.corps_emploi ?? []).map((v) => <option key={v.code} value={v.code}>{v.libelleFr}</option>)}
                </select>
              </p>
              <p>
                <label htmlFor={`pla-ah-nombre-${i}`}>Nombre</label>
                <input id={`pla-ah-nombre-${i}`} type="number" min={1} data-testid={`pla-ah-nombre-${i}`} required value={l.nombre} onChange={(e) => poser(i, "nombre", e.target.value)} />
              </p>
              {composition.length > 1 && (
                <button type="button" className="secondary" onClick={() => setComposition((c) => c.filter((_, j) => j !== i))}>Retirer<span className="sr-only"> la ligne {i + 1}</span></button>
              )}
            </div>
          ))}
          <button type="button" className="secondary" data-testid="pla-ah-ajouter-corps" onClick={() => setComposition((c) => [...c, { corpsEmploiCode: "", nombre: "1" }])}>Ajouter un corps d'emploi</button>
        </fieldset>
        <p>
          <label htmlFor="pla-ah-mode">Mode d'attribution</label>
          <select id="pla-ah-mode" data-testid="pla-ah-mode" value={mode} onChange={(e) => setMode(e.target.value)}>
            <option value="MANUEL">Manuelle</option>
            <option value="AUTOMATISE">Automatisée</option>
          </select>
        </p>
        <p><label htmlFor="pla-ah-minimum">Minimum opérationnel (par défaut : {total} place(s))</label>
          <input id="pla-ah-minimum" type="number" min={1} max={total || 1} data-testid="pla-ah-minimum" value={minimum} onChange={(e) => setMinimum(e.target.value)} /></p>
        {erreur && <Message variante="erreur" testId="pla-ah-erreur">{erreur}</Message>}
        <button type="submit" data-testid="pla-ah-creer">Créer le quart</button>
      </form>
    </Tiroir>
  );
}
