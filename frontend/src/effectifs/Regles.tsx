/**
 * **Règles de l'organisme** (AO 26-015-DO, écran E2 — E-GN-12, E-GN-15, E-GN-16, E-RF-10).
 *
 * Une règle se construit **par listes et boutons** : chaque groupe de conditions est un
 * `<fieldset>` titré, chaque comparaison trois champs étiquetés, l'ordre de tabulation suit
 * l'arbre. Aucun glisser-déposer (WCAG 2.5.7).
 *
 * Les faits proposés viennent du **catalogue du serveur**, par contexte : l'écran ne connaît
 * aucune règle de convention. Le simulateur évalue un dossier saisi à la main, et la décision
 * s'affiche avec ses motifs, comme l'employé la verra.
 */
import { useCallback, useEffect, useMemo, useState } from "react";
import { liste, objet } from "../formeRecue";
import { Badge, EtatVide, Message, Onglets, Tableau, Tiroir, type Colonne } from "../composants";
import { aujourdhui, lisible, useApiEffectifs } from "./api";
import type { Valeur } from "./Referentiels";

interface Fait { code: string; type: "nombre" | "texte" | "booleen" | "date"; libelle: string }
type Catalogue = Record<string, Fait[]>;

type Comparaison = { fait: string; op: string; valeur: unknown };
type Condition = { tous: Condition[] } | { un_de: Condition[] } | { non: Condition } | Comparaison;

interface Version {
  regleId: string; version: number; cle: string; contexte: string; porteeType: string; porteeCode: string | null;
  condition: Condition; action: string; message: string; priorite: number; effectifDepuis: string; effectifJusqua: string | null;
}
interface Regle { id: string; contexte: string; cle: string; porteeType: string; porteeCode: string | null; versionCourante: Version | null }
interface Verdict { action: string; motifs: Array<{ cle: string; action: string; message: string }>; versions: unknown[] }
interface Evaluation { id: string; contexte: string; objetType: string; objetId: string; action: string; motifs: Verdict["motifs"]; dateDuFait: string; horodatage: string; declencheur: string }

/** Ensembles fermés du moteur (comportements du logiciel), pas des listes de l'organisme. */
const ACTIONS = ["ACCEPTER", "AVERTIR", "EXIGER_APPROBATION", "EXCLURE_DU_BASSIN", "BLOQUER", "REFUSER"] as const;
const PORTEES: Record<string, string | null> = {
  ORGANISME: null, UNITE: "unites", CONVENTION: "conventions", STATUT_EMPLOI: "statuts_emploi", CORPS_EMPLOI: "corps_emploi", TITRE_EMPLOI: "titres_emploi",
};
const OPERATEURS: Record<string, string> = {
  "=": "est égal à", "!=": "est différent de", "<": "est inférieur à", "<=": "est au plus", ">": "est supérieur à", ">=": "est au moins",
  dans: "est l'une des valeurs", hors: "n'est aucune des valeurs",
};
const OPS_PAR_TYPE: Record<Fait["type"], string[]> = {
  nombre: ["=", "!=", "<", "<=", ">", ">=", "dans", "hors"],
  date: ["=", "!=", "<", "<=", ">", ">="],
  texte: ["=", "!=", "dans", "hors"],
  booleen: ["=", "!="],
};
const TON_ACTION: Record<string, "succes" | "info" | "avertissement" | "erreur" | "neutre"> = {
  ACCEPTER: "succes", AVERTIR: "avertissement", EXIGER_APPROBATION: "info", EXCLURE_DU_BASSIN: "erreur", BLOQUER: "erreur", REFUSER: "erreur",
};

type Vue = "regles" | "simulateur" | "decisions";

export default function Regles() {
  const { lire, envoyer, erreur, setErreur, succes, setSucces } = useApiEffectifs();
  const [catalogue, setCatalogue] = useState<Catalogue>({});
  const [contexte, setContexte] = useState("");
  const [regles, setRegles] = useState<Regle[]>([]);
  const [vue, setVue] = useState<Vue>("regles");
  const [edition, setEdition] = useState<{ regle: Regle | null } | null>(null);
  const [historique, setHistorique] = useState<{ regle: Regle; versions: Version[] | null } | null>(null);

  /** Le tiroir s'ouvre au geste, les versions arrivent ensuite : une règle garde toutes ses versions datées (E-GN-12). */
  const voirVersions = async (r: Regle) => {
    setHistorique({ regle: r, versions: null });
    const x = await lire<{ versions: Version[] }>(`/api/effectifs/regles/${r.id}`);
    setHistorique((h) => (h?.regle.id === r.id ? { ...h, versions: liste<Version>(objet<{ versions: unknown }>(x)?.versions) } : h));
  };

  useEffect(() => {
    void lire<Catalogue>("/api/effectifs/regles/faits").then((c) => {
      if (c) { setCatalogue(c); setContexte((x) => x || Object.keys(c)[0] || ""); }
    });
  }, [lire]);

  const charger = useCallback(async () => {
    if (!contexte) return;
    const r = await lire<Regle[]>(`/api/effectifs/regles?contexte=${encodeURIComponent(contexte)}`);
    if (r) setRegles(r);
  }, [lire, contexte]);
  useEffect(() => { void charger(); }, [charger]);

  const faits = catalogue[contexte] ?? [];

  const colonnes: ReadonlyArray<Colonne<Regle>> = [
    { id: "cle", libelle: "Règle", tri: (r) => r.cle },
    { id: "portee", libelle: "Portée", tri: (r) => r.porteeType },
    { id: "action", libelle: "Action" },
    { id: "message", libelle: "Message affiché" },
    { id: "vigueur", libelle: "En vigueur", tri: (r) => r.versionCourante?.effectifDepuis ?? "" },
    { id: "actions", libelle: "Actions", actions: true },
  ];

  return (
    <div data-testid="rgl-ecran">
      <div className="eff-barre">
        <p>
          <label htmlFor="rgl-contexte">Contexte</label>
          <select id="rgl-contexte" data-testid="rgl-contexte" value={contexte} onChange={(e) => { setContexte(e.target.value); setSucces(""); }}>
            {Object.keys(catalogue).map((c) => <option key={c} value={c}>{lisible(c)}</option>)}
          </select>
        </p>
        <button type="button" data-testid="rgl-nouvelle" disabled={!contexte} onClick={() => { setErreur(""); setEdition({ regle: null }); }}>Nouvelle règle</button>
      </div>
      <Onglets<Vue> secondaires libelle="Vues des règles" testId="rgl-vues" prefixeIds="rgl" actif={vue} onChange={setVue}
        onglets={[{ id: "regles", libelle: "Règles", testId: "rgl-vue-regles" }, { id: "simulateur", libelle: "Simulateur", testId: "rgl-vue-simulateur" }, { id: "decisions", libelle: "Décisions", testId: "rgl-vue-decisions" }]}>
        {erreur && !edition && <Message variante="erreur" testId="rgl-erreur">{erreur}</Message>}
        {succes && <Message variante="succes" testId="rgl-succes">{succes}</Message>}
        {vue === "regles" && (
          <Tableau
            colonnes={colonnes} lignes={regles} legende={`Règles du contexte ${lisible(contexte)}`} testId="rgl-tableau"
            vide={<EtatVide titre="Aucune règle pour ce contexte" compact testId="rgl-vide">Tout est accepté tant qu'aucune règle ne dit le contraire.</EtatVide>}
            rendre={(r) => (
              <tr key={r.id} data-testid={`rgl-ligne-${r.cle}`}>
                <td>{r.cle}</td>
                <td>{lisible(r.porteeType)}{r.porteeCode ? ` · ${r.porteeCode}` : ""}</td>
                <td>{r.versionCourante ? <Badge ton={TON_ACTION[r.versionCourante.action]}>{lisible(r.versionCourante.action)}</Badge> : <Badge ton="neutre">Retirée</Badge>}</td>
                <td>{r.versionCourante?.message ?? "—"}</td>
                <td>{r.versionCourante ? `v${r.versionCourante.version}, depuis le ${r.versionCourante.effectifDepuis}${r.versionCourante.effectifJusqua ? ` jusqu'au ${r.versionCourante.effectifJusqua}` : ""}` : "—"}</td>
                <td className="col-menu">
                  <span className="eff-actions">
                    <button type="button" className="secondary" data-testid={`rgl-versions-${r.cle}`} onClick={() => void voirVersions(r)}>
                      Versions<span className="sr-only"> de {r.cle}</span>
                    </button>
                    <button type="button" className="secondary" data-testid={`rgl-version-${r.cle}`} disabled={!r.versionCourante} onClick={() => { setErreur(""); setEdition({ regle: r }); }}>
                      Nouvelle version<span className="sr-only"> de {r.cle}</span>
                    </button>
                    {r.versionCourante && !r.versionCourante.effectifJusqua && (
                      <button type="button" className="secondary" data-testid={`rgl-retirer-${r.cle}`} onClick={async () => {
                        const x = await envoyer(`/api/effectifs/regles/${r.id}/retirer`, {});
                        if (x) { setSucces(`« ${r.cle} » ne s'applique plus à partir d'aujourd'hui.`); await charger(); }
                      }}>Retirer<span className="sr-only"> {r.cle}</span></button>
                    )}
                  </span>
                </td>
              </tr>
            )}
          />
        )}
        {vue === "simulateur" && <Simulateur contexte={contexte} faits={faits} />}
        {vue === "decisions" && <Decisions contexte={contexte} />}
      </Onglets>

      {historique && (
        <Tiroir ouvert titre={`Versions de « ${historique.regle.cle} »`} onFermer={() => setHistorique(null)} testId="rgl-historique">
          {historique.versions === null
            ? <p role="status">Chargement des versions…</p>
            : (
              <ol data-testid="rgl-historique-versions">
                {historique.versions.map((v) => (
                  <li key={v.version}>v{v.version} — {lisible(v.action)} : « {v.message} », depuis le {v.effectifDepuis}{v.effectifJusqua ? ` jusqu'au ${v.effectifJusqua} (exclu)` : ", en vigueur"}</li>
                ))}
              </ol>
            )}
        </Tiroir>
      )}
      {edition && (
        <FormulaireRegle contexte={contexte} faits={faits} regle={edition.regle} erreur={erreur} onFermer={() => setEdition(null)}
          onEnregistrer={async (corpsRegle) => {
            const chemin = edition.regle ? `/api/effectifs/regles/${edition.regle.id}/versions` : "/api/effectifs/regles";
            const r = await envoyer<Regle>(chemin, corpsRegle);
            if (r) { setEdition(null); setSucces(`Règle « ${r.cle} » publiée.`); await charger(); }
          }} />
      )}
    </div>
  );
}

// ── Constructeur de conditions ─────────────────────────────────────────────

type Groupe = { mode: "tous" | "un_de" | "aucune"; enfants: Noeud[] };
type Noeud = { groupe: Groupe } | { comparaison: { fait: string; op: string; valeur: string } };

const PROFONDEUR_MAX = 4;

/** Les faits qu'une condition lit : l'aperçu ne rejoue que les décisions qui les portent. */
function faitsDe(c: Condition): string[] {
  if ("tous" in c) return c.tous.flatMap(faitsDe);
  if ("un_de" in c) return c.un_de.flatMap(faitsDe);
  if ("non" in c) return faitsDe(c.non);
  return [c.fait];
}

function versCondition(g: Groupe, faits: Fait[]): Condition {
  const enfants = g.enfants.map((n): Condition => ("groupe" in n ? versCondition(n.groupe, faits) : versComparaison(n.comparaison, faits)));
  if (g.mode === "tous") return { tous: enfants };
  if (g.mode === "un_de") return { un_de: enfants };
  return { non: { un_de: enfants } };
}

function versComparaison(c: { fait: string; op: string; valeur: string }, faits: Fait[]): Comparaison {
  const type = faits.find((f) => f.code === c.fait)?.type ?? "texte";
  const convertir = (s: string): unknown => (type === "nombre" ? Number(s) : type === "booleen" ? s === "vrai" : s);
  const valeur = c.op === "dans" || c.op === "hors" ? c.valeur.split(",").map((s) => s.trim()).filter(Boolean).map(convertir) : convertir(c.valeur.trim());
  return { fait: c.fait, op: c.op, valeur };
}

function depuisCondition(c: Condition | undefined): Groupe {
  if (!c) return { mode: "tous", enfants: [] };
  const noeud = (x: Condition): Noeud => ("tous" in x || "un_de" in x || "non" in x ? { groupe: depuisCondition(x) } : {
    comparaison: { fait: x.fait, op: x.op, valeur: Array.isArray(x.valeur) ? x.valeur.join(", ") : typeof x.valeur === "boolean" ? (x.valeur ? "vrai" : "faux") : String(x.valeur) },
  });
  if ("tous" in c) return { mode: "tous", enfants: c.tous.map(noeud) };
  if ("un_de" in c) return { mode: "un_de", enfants: c.un_de.map(noeud) };
  if ("non" in c && "un_de" in c.non) return { mode: "aucune", enfants: c.non.un_de.map(noeud) };
  return { mode: "tous", enfants: [noeud(c)] };
}

function ConstructeurCondition({ groupe, faits, onChange, chemin, profondeur }: {
  groupe: Groupe; faits: Fait[]; onChange: (g: Groupe) => void; chemin: string; profondeur: number;
}) {
  const poser = (i: number, n: Noeud) => onChange({ ...groupe, enfants: groupe.enfants.map((x, j) => (j === i ? n : x)) });
  const retirer = (i: number) => onChange({ ...groupe, enfants: groupe.enfants.filter((_, j) => j !== i) });
  const nouveauFait = faits[0]?.code ?? "";
  return (
    <fieldset className="eff-groupe" data-testid={`rgl-groupe-${chemin}`}>
      <legend>{profondeur === 1 ? "Si" : "Groupe"} :{" "}
        <label className="sr-only" htmlFor={`rgl-mode-${chemin}`}>Mode du groupe</label>
        <select id={`rgl-mode-${chemin}`} data-testid={`rgl-mode-${chemin}`} value={groupe.mode} onChange={(e) => onChange({ ...groupe, mode: e.target.value as Groupe["mode"] })}>
          <option value="tous">toutes les conditions sont vraies</option>
          <option value="un_de">au moins une condition est vraie</option>
          <option value="aucune">aucune condition n'est vraie</option>
        </select>
      </legend>
      {groupe.enfants.length === 0 && <p className="muted">Aucune condition : ajoutez-en une.</p>}
      {groupe.enfants.map((n, i) => {
        const c = `${chemin}-${i}`;
        if ("groupe" in n) {
          return (
            <div key={c}>
              <ConstructeurCondition groupe={n.groupe} faits={faits} chemin={c} profondeur={profondeur + 1} onChange={(g) => poser(i, { groupe: g })} />
              <button type="button" className="secondary" data-testid={`rgl-retirer-${c}`} onClick={() => retirer(i)}>Retirer ce groupe</button>
            </div>
          );
        }
        const type = faits.find((f) => f.code === n.comparaison.fait)?.type ?? "texte";
        return (
          <div key={c} className="eff-barre" data-testid={`rgl-cond-${c}`}>
            <p>
              <label htmlFor={`rgl-fait-${c}`}>Fait</label>
              <select id={`rgl-fait-${c}`} data-testid={`rgl-cond-${c}-fait`} value={n.comparaison.fait} onChange={(e) => poser(i, { comparaison: { ...n.comparaison, fait: e.target.value } })}>
                {faits.map((f) => <option key={f.code} value={f.code}>{f.libelle}</option>)}
              </select>
            </p>
            <p>
              <label htmlFor={`rgl-op-${c}`}>Comparaison</label>
              <select id={`rgl-op-${c}`} data-testid={`rgl-cond-${c}-op`} value={n.comparaison.op} onChange={(e) => poser(i, { comparaison: { ...n.comparaison, op: e.target.value } })}>
                {OPS_PAR_TYPE[type].map((o) => <option key={o} value={o}>{OPERATEURS[o]}</option>)}
              </select>
            </p>
            <p>
              <label htmlFor={`rgl-val-${c}`}>{n.comparaison.op === "dans" || n.comparaison.op === "hors" ? "Valeurs (séparées par des virgules)" : type === "booleen" ? "Valeur (vrai ou faux)" : "Valeur"}</label>
              <input id={`rgl-val-${c}`} data-testid={`rgl-cond-${c}-valeur`} required inputMode={type === "nombre" ? "decimal" : undefined}
                value={n.comparaison.valeur} onChange={(e) => poser(i, { comparaison: { ...n.comparaison, valeur: e.target.value } })} />
            </p>
            <button type="button" className="secondary" data-testid={`rgl-retirer-${c}`} onClick={() => retirer(i)}>Retirer<span className="sr-only"> cette condition</span></button>
          </div>
        );
      })}
      <span className="eff-actions">
        <button type="button" className="secondary" data-testid={`rgl-ajouter-cond-${chemin}`} disabled={!nouveauFait}
          onClick={() => onChange({ ...groupe, enfants: [...groupe.enfants, { comparaison: { fait: nouveauFait, op: "=", valeur: "" } }] })}>
          Ajouter une condition
        </button>
        {profondeur < PROFONDEUR_MAX && (
          <button type="button" className="secondary" data-testid={`rgl-ajouter-groupe-${chemin}`}
            onClick={() => onChange({ ...groupe, enfants: [...groupe.enfants, { groupe: { mode: "tous", enfants: [] } }] })}>
            Ajouter un groupe
          </button>
        )}
      </span>
    </fieldset>
  );
}

// ── Formulaire d'une règle (création ou nouvelle version) ──────────────────

function FormulaireRegle({ contexte, faits, regle, erreur, onFermer, onEnregistrer }: {
  contexte: string; faits: Fait[]; regle: Regle | null; erreur: string; onFermer: () => void; onEnregistrer: (c: Record<string, unknown>) => Promise<void>;
}) {
  const { lire, envoyer, erreur: erreurApercu } = useApiEffectifs();
  const v = regle?.versionCourante ?? null;
  const [cle, setCle] = useState(regle?.cle ?? "");
  const [porteeType, setPorteeType] = useState(regle?.porteeType ?? "ORGANISME");
  const [porteeCode, setPorteeCode] = useState(regle?.porteeCode ?? "");
  const [codes, setCodes] = useState<Valeur[]>([]);
  const [groupe, setGroupe] = useState<Groupe>(() => depuisCondition(v?.condition));
  const [action, setAction] = useState(v?.action ?? "BLOQUER");
  const [message, setMessage] = useState(v?.message ?? "");
  const [priorite, setPriorite] = useState(String(v?.priorite ?? 0));
  const [date, setDate] = useState(aujourdhui());
  const [apercu, setApercu] = useState<{ total: number; parAction: Record<string, number>; exemples: Array<{ objetId: string; message: string }> } | null>(null);
  const [apercuVide, setApercuVide] = useState(false);

  /**
   * L'aperçu d'impact (P-GN-2) : la règle en brouillon rejouée sur les décisions récentes du contexte
   * qui portent les faits qu'elle lit — ce qu'elle aurait décidé, avant de la publier.
   */
  const previsualiser = async () => {
    setApercu(null); setApercuVide(false);
    const condition = versCondition(groupe, faits);
    const lus = faitsDe(condition);
    const decisions = (await lire<Array<{ objetId: string; faits: Record<string, unknown> }>>(`/api/effectifs/regles/evaluations?contexte=${encodeURIComponent(contexte)}`)) ?? [];
    const dossiers = decisions.filter((d) => d.faits && lus.every((f) => f in d.faits)).slice(0, 500).map((d) => ({ objetId: d.objetId, faits: d.faits }));
    if (!dossiers.length) { setApercuVide(true); return; }
    setApercu(await envoyer("/api/effectifs/regles/apercu", { contexte, condition, action, message, priorite, dossiers }));
  };

  useEffect(() => {
    const ref = PORTEES[porteeType];
    if (!ref) { setCodes([]); return; }
    void lire<Valeur[]>(`/api/effectifs/referentiels/${ref}`).then((x) => setCodes(x ?? []));
  }, [lire, porteeType]);

  return (
    <Tiroir ouvert titre={regle ? `Nouvelle version de « ${regle.cle} »` : `Nouvelle règle — ${lisible(contexte)}`} onFermer={onFermer} testId="rgl-formulaire">
      <form onSubmit={(e) => {
        e.preventDefault();
        const c: Record<string, unknown> = { condition: versCondition(groupe, faits), action, message, priorite };
        if (regle) c.depuis = date;
        else Object.assign(c, { contexte, cle, porteeType, porteeCode: porteeType === "ORGANISME" ? null : porteeCode, effectifDepuis: date });
        void onEnregistrer(c);
      }}>
        {!regle && (
          <>
            <p>
              <label htmlFor="rgl-cle">Clé de la règle</label>
              <input id="rgl-cle" data-testid="rgl-form-cle" required value={cle} onChange={(e) => setCle(e.target.value)} aria-describedby="rgl-cle-aide" />
              <span id="rgl-cle-aide" className="aide">Ex. « minimum-quarts-semaine ». Une règle plus particulière de même clé remplace la plus générale.</span>
            </p>
            <p>
              <label htmlFor="rgl-portee">Portée</label>
              <select id="rgl-portee" data-testid="rgl-form-portee" value={porteeType} onChange={(e) => { setPorteeType(e.target.value); setPorteeCode(""); }}>
                {Object.keys(PORTEES).map((p) => <option key={p} value={p}>{lisible(p)}</option>)}
              </select>
            </p>
            {PORTEES[porteeType] && (
              <p>
                <label htmlFor="rgl-portee-code">Valeur visée</label>
                <select id="rgl-portee-code" data-testid="rgl-form-portee-code" required value={porteeCode} onChange={(e) => setPorteeCode(e.target.value)}>
                  <option value="">Choisir…</option>
                  {codes.map((x) => <option key={x.code} value={x.code}>{x.code} — {x.libelleFr}</option>)}
                </select>
              </p>
            )}
          </>
        )}
        <ConstructeurCondition groupe={groupe} faits={faits} onChange={setGroupe} chemin="r" profondeur={1} />
        <fieldset className="eff-groupe">
          <legend>Alors</legend>
          {ACTIONS.map((a) => (
            <p key={a} className="eff-case">
              <input id={`rgl-action-${a}`} type="radio" name="rgl-action" data-testid={`rgl-action-${a}`} checked={action === a} onChange={() => setAction(a)} />
              <label htmlFor={`rgl-action-${a}`}>{lisible(a)}</label>
            </p>
          ))}
        </fieldset>
        <p>
          <label htmlFor="rgl-message">Message affiché à la personne</label>
          <textarea id="rgl-message" data-testid="rgl-form-message" required rows={2} value={message} onChange={(e) => setMessage(e.target.value)} aria-describedby="rgl-message-aide" />
          <span id="rgl-message-aide" className="aide">Dites quoi corriger. « {"{quartsParSemaine}"} » est remplacé par la valeur du fait.</span>
        </p>
        <p>
          <label htmlFor="rgl-priorite">Priorité (0 à 1 000)</label>
          <input id="rgl-priorite" type="number" data-testid="rgl-form-priorite" value={priorite} onChange={(e) => setPriorite(e.target.value)} />
        </p>
        <p>
          <label htmlFor="rgl-date">En vigueur à partir du (aujourd'hui ou plus tard)</label>
          <input id="rgl-date" type="date" required data-testid="rgl-form-date" value={date} onChange={(e) => setDate(e.target.value)} />
        </p>
        {erreur && <Message variante="erreur" testId="rgl-form-erreur">{erreur}</Message>}
        <p className="eff-actions">
          <button type="button" className="secondary" data-testid="rgl-form-apercu" onClick={() => void previsualiser()}>Aperçu d'impact</button>
          <button type="submit" data-testid="rgl-form-publier">Publier</button>
        </p>
        {erreurApercu && <Message variante="erreur" testId="rgl-apercu-erreur">{erreurApercu}</Message>}
        {apercuVide && <p role="status" data-testid="rgl-apercu-vide">Aucune décision récente de ce contexte ne porte les faits que lit cette règle : rien à rejouer.</p>}
        {apercu && (
          <div role="status" data-testid="rgl-apercu">
            <p>Sur {apercu.total} décision(s) récente(s), cette règle aurait donné : {Object.entries(apercu.parAction).filter(([, n]) => n > 0).map(([a, n]) => `${lisible(a)} ${n}`).join(", ")}.</p>
            {apercu.exemples.length > 0 && <ul>{apercu.exemples.map((x, i) => <li key={`${x.objetId}-${i}`}>{x.objetId} : {x.message}</li>)}</ul>}
          </div>
        )}
      </form>
    </Tiroir>
  );
}

// ── Simulateur et décisions ────────────────────────────────────────────────

function Simulateur({ contexte, faits }: { contexte: string; faits: Fait[] }) {
  const { envoyer, erreur } = useApiEffectifs();
  const [valeurs, setValeurs] = useState<Record<string, string>>({});
  const [unite, setUnite] = useState("");
  const [date, setDate] = useState(aujourdhui());
  const [verdict, setVerdict] = useState<Verdict | null>(null);
  useEffect(() => { setVerdict(null); setValeurs({}); }, [contexte]);
  const convertir = (f: Fait, s: string): unknown => (f.type === "nombre" ? Number(s) : f.type === "booleen" ? s === "vrai" : s);
  return (
    // Le simulateur **n'écrit rien** : il dit ce que les règles décideraient d'un cas hypothétique.
    // C'est une requête — il reste donc en ligne, à côté du verdict qu'il produit.
    <form data-testid="rgl-simulateur" data-formulaire="requete" aria-label="Simuler une décision des règles" onSubmit={async (e) => {
      e.preventDefault();
      const d = Object.fromEntries(faits.filter((f) => (valeurs[f.code] ?? "") !== "").map((f) => [f.code, convertir(f, valeurs[f.code])]));
      setVerdict(await envoyer<Verdict>("/api/effectifs/regles/simuler", { contexte, faits: d, unite: unite || null, date }));
    }}>
      <p className="aide">Saisissez un dossier : le moteur l'évalue comme il évaluera un vrai dépôt. La simulation est tracée au journal.</p>
      <div className="eff-barre">
        {faits.map((f) => (
          <p key={f.code}>
            <label htmlFor={`rgl-sim-${f.code}`}>{f.libelle}{f.type === "booleen" ? " (vrai ou faux)" : ""}</label>
            <input id={`rgl-sim-${f.code}`} data-testid={`rgl-sim-${f.code}`} value={valeurs[f.code] ?? ""} onChange={(e) => setValeurs((x) => ({ ...x, [f.code]: e.target.value }))} />
          </p>
        ))}
        <p><label htmlFor="rgl-sim-unite">Unité (facultatif)</label><input id="rgl-sim-unite" data-testid="rgl-sim-unite" value={unite} onChange={(e) => setUnite(e.target.value)} /></p>
        <p><label htmlFor="rgl-sim-date">Date du fait</label><input id="rgl-sim-date" type="date" data-testid="rgl-sim-date" value={date} onChange={(e) => setDate(e.target.value)} /></p>
      </div>
      <button type="submit" data-testid="rgl-sim-evaluer">Évaluer</button>
      {erreur && <Message variante="erreur" testId="rgl-sim-erreur">{erreur}</Message>}
      {verdict && (
        <div role="status" data-testid="rgl-sim-verdict">
          <p>Décision : <Badge ton={TON_ACTION[verdict.action]}>{lisible(verdict.action)}</Badge></p>
          {verdict.motifs.length === 0 ? <p>Aucune règle ne s'y oppose.</p> : (
            <ul>{verdict.motifs.map((m, i) => <li key={i} data-testid={`rgl-sim-motif-${i}`}>{lisible(m.action)} — {m.message}</li>)}</ul>
          )}
        </div>
      )}
    </form>
  );
}

function Decisions({ contexte }: { contexte: string }) {
  const { lire } = useApiEffectifs();
  const [evaluations, setEvaluations] = useState<Evaluation[]>([]);
  useEffect(() => {
    if (contexte) void lire<Evaluation[]>(`/api/effectifs/regles/evaluations?contexte=${encodeURIComponent(contexte)}`).then((x) => setEvaluations(x ?? []));
  }, [lire, contexte]);
  const colonnes = useMemo<ReadonlyArray<Colonne<Evaluation>>>(() => [
    { id: "quand", libelle: "Horodatage", tri: (e) => e.horodatage },
    { id: "objet", libelle: "Objet" },
    { id: "date", libelle: "Date du fait", tri: (e) => e.dateDuFait },
    { id: "decision", libelle: "Décision", tri: (e) => e.action },
    { id: "motifs", libelle: "Motifs" },
  ], []);
  return (
    <Tableau colonnes={colonnes} lignes={evaluations} legende={`Décisions automatiques — ${lisible(contexte)}`} testId="rgl-decisions"
      vide={<EtatVide titre="Aucune décision" compact testId="rgl-decisions-vide">Aucune évaluation n'a encore eu lieu dans ce contexte.</EtatVide>}
      rendre={(e) => (
        <tr key={e.id} data-testid={`rgl-decision-${e.id}`}>
          <td>{new Date(e.horodatage).toLocaleString("fr-CA")}</td>
          <td>{e.objetType} · {e.objetId}</td>
          <td>{e.dateDuFait}</td>
          <td><Badge ton={TON_ACTION[e.action]}>{lisible(e.action)}</Badge></td>
          <td>{e.motifs.map((m) => m.message).join(" · ") || "—"}</td>
        </tr>
      )} />
  );
}
