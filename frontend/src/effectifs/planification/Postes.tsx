/**
 * **Postes** (AO 26-015-DO, écran E6 — E-CH-2 à E-CH-5).
 *
 * L'éditeur de cycle est une grille de **listes déroulantes** (semaines × jours) : pas de peinture à
 * la souris, et « Copier la semaine 1 » pour aller vite au clavier. Les paramètres fonctionnels du
 * poste se rendent depuis le référentiel `parametres_poste` : ajouter un paramètre ne demande aucun
 * changement ici.
 *
 * E-CH-3 : plusieurs postes se sélectionnent et se modifient **en lot** (tout ou rien) ; chaque
 * modification dit ce qu'elle a fait aux quarts futurs déjà générés, conflits compris.
 */
import { useCallback, useEffect, useState } from "react";
import { Badge, EtatVide, Message, Tableau, Tiroir, type Colonne } from "../../composants";
import { aujourdhui, lisible, useApiEffectifs } from "../api";
import type { Valeur } from "../Referentiels";
import type { Poste, PosteModifie, Repercussion } from "./types";
import { libelleDe, useReferentiels, type Milieu } from "./useReferentiels";

const API = "/api/effectifs/planification";
const JOURS = ["Lun", "Mar", "Mer", "Jeu", "Ven", "Sam", "Dim"];

export default function Postes() {
  const { lire, envoyer, erreur, setErreur, succes, setSucces } = useApiEffectifs();
  const { refs, milieux } = useReferentiels(["unites", "statuts_emploi", "corps_emploi", "natures_quart", "plages_horaires", "parametres_poste"]);
  const [postes, setPostes] = useState<Poste[]>([]);
  const [choisis, setChoisis] = useState<Set<string>>(new Set());
  const [fiche, setFiche] = useState<Poste | "nouveau" | null>(null);
  const [lotOuvert, setLotOuvert] = useState(false);
  const [conflits, setConflits] = useState<Repercussion["conflits"]>([]);
  const brouillonsChoisis = postes.filter((p) => choisis.has(p.id) && p.etat === "BROUILLON").map((p) => p.id);

  const charger = useCallback(async () => { const p = await lire<Poste[]>(`${API}/postes`); if (p) setPostes(p); }, [lire]);
  useEffect(() => { void charger(); }, [charger]);

  const colonnes: ReadonlyArray<Colonne<Poste>> = [
    { id: "choix", libelle: <span className="sr-only">Sélection</span> },
    { id: "code", libelle: "Poste", tri: (p) => p.code },
    { id: "statut", libelle: "Statut offert" },
    { id: "corps", libelle: "Corps d'emploi" },
    { id: "milieu", libelle: "Milieu" },
    { id: "etat", libelle: "État", tri: (p) => p.etat },
    { id: "actions", libelle: "Actions", actions: true },
  ];

  return (
    <div data-testid="pla-postes">
      <div className="eff-barre">
        <button type="button" data-testid="pla-poste-nouveau" onClick={() => { setErreur(""); setFiche("nouveau"); }}>Nouveau poste</button>
        <button type="button" className="secondary" data-testid="pla-postes-publier" disabled={brouillonsChoisis.length === 0} onClick={async () => {
          const r = await envoyer<Poste[]>(`${API}/postes/publier`, { ids: brouillonsChoisis });
          if (r) { setChoisis(new Set()); setConflits([]); setSucces(`${r.length} poste(s) publié(s).`); await charger(); }
        }}>Publier les brouillons choisis ({brouillonsChoisis.length})</button>
        <button type="button" className="secondary" data-testid="pla-postes-lot" disabled={choisis.size === 0}
          onClick={() => { setErreur(""); setLotOuvert(true); }}>Modifier la sélection ({choisis.size})</button>
      </div>
      {erreur && !fiche && !lotOuvert && <Message variante="erreur" testId="pla-postes-erreur">{erreur}</Message>}
      {succes && <Message variante="succes" testId="pla-postes-succes">{succes}</Message>}
      {conflits.length > 0 && (
        <Message variante="avertissement" testId="pla-postes-conflits">
          <p>{conflits.length} titulaire(s) libéré(s) : la place ne leur convient plus.</p>
          <ul>{conflits.map((c) => <li key={c.placeId}>Quart du {c.date} : {c.motif}.</li>)}</ul>
        </Message>
      )}
      <Tableau colonnes={colonnes} lignes={postes} legende="Postes (lignes de quarts)" testId="pla-postes-tableau"
        vide={<EtatVide titre="Aucun poste" compact testId="pla-postes-vide">Un poste est une ligne de quarts attribuable : créez-en un.</EtatVide>}
        rendre={(p) => (
          <tr key={p.id} data-testid={`pla-poste-${p.code}`}>
            <td className="eff-case">
              <input type="checkbox" id={`pla-poste-choix-${p.id}`} data-testid={`pla-poste-choix-${p.code}`} checked={choisis.has(p.id)}
                onChange={(e) => setChoisis((s) => { const n = new Set(s); if (e.target.checked) n.add(p.id); else n.delete(p.id); return n; })} />
              <label htmlFor={`pla-poste-choix-${p.id}`} className="sr-only">Sélectionner le poste {p.code}</label>
            </td>
            <td>{p.code} — {p.libelle}</td>
            <td>{libelleDe(refs.statuts_emploi, p.statutEmploiCode)}</td>
            <td>{libelleDe(refs.corps_emploi, p.corpsEmploiCode)}</td>
            <td>{milieux.find((m) => m.id === p.milieuId)?.libelle ?? p.milieuId}</td>
            <td><Badge ton={p.etat === "ATTRIBUE" ? "succes" : p.etat === "VACANT" ? "avertissement" : p.etat === "PUBLIE" ? "info" : "neutre"}>{lisible(p.etat)}</Badge></td>
            <td className="col-menu">
              <button type="button" className="secondary" data-testid={`pla-poste-modifier-${p.code}`} onClick={() => { setErreur(""); setFiche(p); }}>
                Modifier<span className="sr-only"> {p.code}</span>
              </button>
            </td>
          </tr>
        )} />
      {fiche && (
        <FichePoste poste={fiche === "nouveau" ? null : fiche} refs={refs} milieux={milieux} erreur={erreur} onFermer={() => setFiche(null)}
          onEnregistrer={async (c) => {
            if (fiche === "nouveau") {
              const r = await envoyer<Poste>(`${API}/postes`, c);
              if (r) { setFiche(null); setConflits([]); setSucces(`Poste « ${r.code} » enregistré.`); await charger(); }
              return;
            }
            const r = await envoyer<PosteModifie>(`${API}/postes/${fiche.id}`, c, "PUT");
            if (r) { setFiche(null); setConflits(r.repercussion?.conflits ?? []); setSucces(`Poste « ${r.code} » enregistré. ${resume([r])}`); await charger(); }
          }} />
      )}
      {lotOuvert && (
        <FicheLot nombre={choisis.size} refs={refs} milieux={milieux} erreur={erreur} onFermer={() => setLotOuvert(false)}
          onEnregistrer={async (changements) => {
            const r = await envoyer<PosteModifie[]>(`${API}/postes/lot`, { ids: [...choisis], changements });
            if (!r) return;
            const liste = Array.isArray(r) ? r : [];
            setLotOuvert(false);
            setChoisis(new Set());
            setConflits(liste.flatMap((p) => p.repercussion?.conflits ?? []));
            setSucces(`${liste.length} poste(s) modifié(s) d'un seul geste. ${resume(liste)}`);
            await charger();
          }} />
      )}
    </div>
  );
}

/** « Quarts futurs : 2 déplacé(s), 1 retiré(s)… » — ce que la modification a fait aux quarts déjà générés. */
function resume(postes: readonly PosteModifie[]): string {
  const t = postes.reduce((a, p) => {
    const r = p.repercussion;
    return r ? { d: a.d + r.deplacees, q: a.q + r.requalifiees, r: a.r + r.retirees, a: a.a + r.ajoutees, p: a.p + r.pointeesIgnorees, c: a.c + r.conflits.length } : a;
  }, { d: 0, q: 0, r: 0, a: 0, p: 0, c: 0 });
  if (t.d + t.q + t.r + t.a + t.p + t.c === 0) return "Aucun quart futur à ajuster.";
  return `Quarts futurs : ${t.d} déplacé(s), ${t.q} requalifié(s), ${t.r} retiré(s), ${t.a} ajouté(s) ; ${t.p} pointé(s) laissé(s) tel(s) ; ${t.c} conflit(s).`;
}

/**
 * Les changements communs d'un lot de postes : chaque champ laissé à « Ne pas changer » garde la
 * valeur propre à chaque poste. Le code et le libellé, propres à chaque poste, ne s'y modifient pas.
 */
function FicheLot({ nombre, refs, milieux, erreur, onFermer, onEnregistrer }: {
  nombre: number; refs: Record<string, Valeur[]>; milieux: Milieu[]; erreur: string; onFermer: () => void;
  onEnregistrer: (changements: Record<string, unknown>) => Promise<void>;
}) {
  const [valeurs, setValeurs] = useState<Record<string, string>>({});
  const [remplacerCycle, setRemplacerCycle] = useState(false);
  const [cycle, setCycle] = useState<Array<string | null>>(Array(7).fill(null));
  const [local, setLocal] = useState("");
  const poser = (cle: string, v: string) => setValeurs((x) => ({ ...x, [cle]: v }));
  const depuisRef = (t: string) => (refs[t] ?? []).map((v) => ({ code: v.code, libelle: v.libelleFr }));
  const choix = (cle: string, libelle: string, liste: Array<{ code: string; libelle: string }>) => (
    <p>
      <label htmlFor={`pla-lot-${cle}`}>{libelle}</label>
      <select id={`pla-lot-${cle}`} data-testid={`pla-lot-${cle}`} value={valeurs[cle] ?? ""} onChange={(e) => poser(cle, e.target.value)}>
        <option value="">Ne pas changer</option>
        {liste.map((o) => <option key={o.code} value={o.code}>{o.libelle}</option>)}
      </select>
    </p>
  );
  return (
    <Tiroir ouvert titre={`Modifier ${nombre} poste(s)`} onFermer={onFermer} testId="pla-lot-fiche">
      <form onSubmit={(e) => {
        e.preventDefault();
        const changements: Record<string, unknown> = Object.fromEntries(Object.entries(valeurs).filter(([, v]) => v !== ""));
        if (remplacerCycle) changements.cycle = [cycle];
        if (Object.keys(changements).length === 0) { setLocal("Choisissez au moins un changement : chaque champ laissé à « Ne pas changer » garde la valeur de chaque poste."); return; }
        setLocal("");
        void onEnregistrer(changements);
      }}>
        <p className="aide">Les changements s'appliquent à tous les postes choisis, ou à aucun. Les quarts futurs déjà générés suivent ; les quarts passés, pointés ou d'une période close ne bougent pas.</p>
        {choix("uniteCode", "Unité", depuisRef("unites"))}
        {choix("statutEmploiCode", "Statut d'emploi offert", depuisRef("statuts_emploi"))}
        {choix("corpsEmploiCode", "Corps d'emploi", depuisRef("corps_emploi"))}
        {choix("milieuId", "Milieu", milieux.map((m) => ({ code: m.id, libelle: `${m.code} — ${m.libelle}` })))}
        {choix("natureCode", "Nature des quarts", depuisRef("natures_quart"))}
        <p>
          <label htmlFor="pla-lot-debutCycle">Premier jour du cycle (vide : ne pas changer)</label>
          <input id="pla-lot-debutCycle" type="date" data-testid="pla-lot-debutCycle" value={valeurs.debutCycle ?? ""} onChange={(e) => poser("debutCycle", e.target.value)} />
        </p>
        <p className="eff-case">
          <input id="pla-lot-remplacer-cycle" type="checkbox" data-testid="pla-lot-remplacer-cycle" checked={remplacerCycle} onChange={(e) => setRemplacerCycle(e.target.checked)} />
          <label htmlFor="pla-lot-remplacer-cycle">Remplacer le cycle par une semaine commune</label>
        </p>
        {remplacerCycle && (
          <fieldset className="eff-groupe">
            <legend>Nouveau cycle d'une semaine (vide = congé)</legend>
            <div className="eff-barre">
              {cycle.map((jour, j) => (
                <p key={j}>
                  <label htmlFor={`pla-lot-cycle-${j}`}>{JOURS[j]}</label>
                  <select id={`pla-lot-cycle-${j}`} data-testid={`pla-lot-cycle-${j}`} value={jour ?? ""}
                    onChange={(e) => setCycle((c) => c.map((x, k) => (k === j ? (e.target.value || null) : x)))}>
                    <option value="">Congé</option>
                    {(refs.plages_horaires ?? []).map((v) => <option key={v.code} value={v.code}>{v.libelleFr}</option>)}
                  </select>
                </p>
              ))}
            </div>
          </fieldset>
        )}
        {local && <Message variante="erreur" testId="pla-lot-vide">{local}</Message>}
        {erreur && <Message variante="erreur" testId="pla-lot-erreur">{erreur}</Message>}
        <button type="submit" data-testid="pla-lot-enregistrer">Appliquer aux {nombre} poste(s)</button>
      </form>
    </Tiroir>
  );
}

function FichePoste({ poste, refs, milieux, erreur, onFermer, onEnregistrer }: {
  poste: Poste | null; refs: Record<string, Valeur[]>; milieux: Milieu[]; erreur: string; onFermer: () => void; onEnregistrer: (c: Record<string, unknown>) => Promise<void>;
}) {
  const [code, setCode] = useState(poste?.code ?? "");
  const [libelle, setLibelle] = useState(poste?.libelle ?? "");
  const [uniteCode, setUniteCode] = useState(poste?.uniteCode ?? "");
  const [statut, setStatut] = useState(poste?.statutEmploiCode ?? "");
  const [corps, setCorps] = useState(poste?.corpsEmploiCode ?? "");
  const [milieuId, setMilieuId] = useState(poste?.milieuId ?? "");
  const [nature, setNature] = useState(poste?.natureCode ?? "");
  const [debutCycle, setDebutCycle] = useState(poste?.debutCycle ?? aujourdhui());
  const [cycle, setCycle] = useState<Array<Array<string | null>>>(poste?.cycle ?? [Array(7).fill(null)]);
  const [parametres, setParametres] = useState<Record<string, string | number | boolean>>(poste?.parametres ?? {});
  const definitions = refs.parametres_poste ?? [];

  const poserJour = (s: number, j: number, v: string) => setCycle((c) => c.map((sem, i) => (i === s ? sem.map((x, k) => (k === j ? (v || null) : x)) : sem)));
  const choix = (id: string, libelleChamp: string, valeur: string, set: (v: string) => void, liste: Array<{ code: string; libelle: string }>) => (
    <p>
      <label htmlFor={`${id}`}>{libelleChamp}</label>
      <select id={`${id}`} data-testid={id} required value={valeur} onChange={(e) => set(e.target.value)}>
        <option value="">Choisir…</option>
        {liste.map((o) => <option key={o.code} value={o.code}>{o.libelle}</option>)}
      </select>
    </p>
  );
  const depuisRef = (t: string) => (refs[t] ?? []).map((v) => ({ code: v.code, libelle: v.libelleFr }));

  return (
    <Tiroir ouvert titre={poste ? `Poste ${poste.code}` : "Nouveau poste"} onFermer={onFermer} testId="pla-poste-fiche">
      <form onSubmit={(e) => {
        e.preventDefault();
        void onEnregistrer({ code, libelle, uniteCode, statutEmploiCode: statut, corpsEmploiCode: corps, milieuId, natureCode: nature, debutCycle, cycle, parametres });
      }}>
        {!poste && <p><label htmlFor="pla-pos-code">Code</label><input id="pla-pos-code" data-testid="pla-pos-code" required value={code} onChange={(e) => setCode(e.target.value)} /></p>}
        <p><label htmlFor="pla-pos-libelle">Libellé</label><input id="pla-pos-libelle" data-testid="pla-pos-libelle" required value={libelle} onChange={(e) => setLibelle(e.target.value)} /></p>
        {choix("pla-pos-unite", "Unité", uniteCode, setUniteCode, depuisRef("unites"))}
        {choix("pla-pos-statut", "Statut d'emploi offert", statut, setStatut, depuisRef("statuts_emploi"))}
        {choix("pla-pos-corps", "Corps d'emploi", corps, setCorps, depuisRef("corps_emploi"))}
        {choix("pla-pos-milieu", "Milieu", milieuId, setMilieuId, milieux.map((m) => ({ code: m.id, libelle: `${m.code} — ${m.libelle}` })))}
        {choix("pla-pos-nature", "Nature des quarts", nature, setNature, depuisRef("natures_quart"))}
        <p><label htmlFor="pla-pos-debut">Premier jour du cycle</label><input id="pla-pos-debut" type="date" data-testid="pla-pos-debut" required value={debutCycle} onChange={(e) => setDebutCycle(e.target.value)} /></p>
        <fieldset className="eff-groupe eff-cycle">
          <legend>Cycle : une plage par jour (vide = congé)</legend>
          {cycle.map((semaine, s) => (
            <fieldset key={s} className="eff-groupe">
              <legend>Semaine {s + 1}</legend>
              <div className="eff-barre">
                {semaine.map((jour, j) => (
                  <p key={j}>
                    <label htmlFor={`pla-pos-cycle-${s}-${j}`}>{JOURS[j]}</label>
                    <select id={`pla-pos-cycle-${s}-${j}`} data-testid={`pla-pos-cycle-${s}-${j}`} value={jour ?? ""} onChange={(e) => poserJour(s, j, e.target.value)}>
                      <option value="">Congé</option>
                      {(refs.plages_horaires ?? []).map((v) => <option key={v.code} value={v.code}>{v.libelleFr}</option>)}
                    </select>
                  </p>
                ))}
              </div>
            </fieldset>
          ))}
          <span className="eff-actions">
            <button type="button" className="secondary" data-testid="pla-pos-cycle-ajouter" disabled={cycle.length >= 12} onClick={() => setCycle((c) => [...c, [...c[0]]])}>
              Ajouter une semaine (copie de la semaine 1)
            </button>
            {cycle.length > 1 && <button type="button" className="secondary" onClick={() => setCycle((c) => c.slice(0, -1))}>Retirer la dernière semaine</button>}
          </span>
        </fieldset>
        {definitions.length > 0 && (
          <fieldset className="eff-groupe">
            <legend>Paramètres fonctionnels</legend>
            {definitions.map((d) => {
              const type = String(d.attributs.typeValeur);
              const id = `pla-pos-param-${d.code}`;
              const v = parametres[d.code];
              if (type === "BOOLEEN") {
                return (
                  <p key={d.code} className="eff-case">
                    <input id={`${id}`} type="checkbox" data-testid={id} checked={v === true} onChange={(e) => setParametres((p) => ({ ...p, [d.code]: e.target.checked }))} />
                    <label htmlFor={`${id}`}>{d.libelleFr}</label>
                  </p>
                );
              }
              return (
                <p key={d.code}>
                  <label htmlFor={`${id}`}>{d.libelleFr}</label>
                  <input id={`${id}`} data-testid={id} type={type === "NOMBRE" ? "number" : "text"} value={v === undefined ? "" : String(v)}
                    onChange={(e) => setParametres((p) => {
                      const n = { ...p };
                      if (e.target.value === "") delete n[d.code]; else n[d.code] = type === "NOMBRE" ? Number(e.target.value) : e.target.value;
                      return n;
                    })} />
                </p>
              );
            })}
          </fieldset>
        )}
        {erreur && <Message variante="erreur" testId="pla-pos-erreur">{erreur}</Message>}
        <button type="submit" data-testid="pla-pos-enregistrer">Enregistrer</button>
      </form>
    </Tiroir>
  );
}
