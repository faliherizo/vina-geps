/**
 * **Référentiels de l'organisme** (AO 26-015-DO, écran E1).
 *
 * Un seul écran pour tous les référentiels : le formulaire est **rendu depuis le descripteur**
 * envoyé par le serveur (champs, types, comportements admis, références). Ajouter un attribut à un
 * référentiel ne demande donc aucune modification ici.
 *
 * Une valeur ne se modifie pas en place : elle se **remplace à partir d'une date** (jamais passée),
 * et la date du filtre montre ce qui était en vigueur à ce jour-là — le passé reste lisible tel
 * qu'il était.
 */
import { useCallback, useEffect, useMemo, useState } from "react";
import { liste } from "../formeRecue";
import AlignementOrganigramme from "./AlignementOrganigramme";
import PromotionConfiguration from "./PromotionConfiguration";
import { EtatVide, Message, Tableau, Tiroir, type Colonne } from "../composants";
import { aujourdhui, lisible, useApiEffectifs } from "./api";

export interface DescripteurAttribut {
  nom: string; libelle: string; type: "texte" | "nombre" | "booleen" | "reference" | "references" | "choix";
  obligatoire?: boolean; reference?: string; valeurs?: string[]; min?: number; max?: number;
}
export interface Descripteur { type: string; libelle: string; comportements?: string[]; attributs: DescripteurAttribut[] }
export interface Valeur {
  id: string; type: string; code: string; libelleFr: string; libelleEn: string | null; comportement: string | null;
  ordre: number; attributs: Record<string, unknown>; effectifDepuis: string; effectifJusqua: string | null;
}

type Mode = { nature: "creer" } | { nature: "remplacer"; valeur: Valeur } | { nature: "fermer"; valeur: Valeur } | { nature: "historique"; valeur: Valeur; versions: Valeur[] | null };

export default function Referentiels() {
  const { lire, envoyer, erreur, setErreur, succes, setSucces } = useApiEffectifs();
  const [descripteurs, setDescripteurs] = useState<Descripteur[]>([]);
  const [type, setType] = useState("");
  const [date, setDate] = useState(aujourdhui());
  const [valeurs, setValeurs] = useState<Valeur[]>([]);
  const [mode, setMode] = useState<Mode | null>(null);

  useEffect(() => {
    void lire<Descripteur[]>("/api/effectifs/referentiels").then((d) => {
      if (d) { setDescripteurs(d); setType((t) => t || d[0]?.type || ""); }
    });
  }, [lire]);

  const charger = useCallback(async () => {
    if (!type) return;
    const v = await lire<Valeur[]>(`/api/effectifs/referentiels/${type}?date=${encodeURIComponent(date)}`);
    if (v) setValeurs(v);
  }, [lire, type, date]);

  useEffect(() => { void charger(); }, [charger]);

  const descripteur = useMemo(() => descripteurs.find((d) => d.type === type), [descripteurs, type]);

  /** Le tiroir s'ouvre au geste ; les versions arrivent ensuite (E-GN-7 : une valeur datée garde toute son histoire). */
  const voirHistorique = async (v: Valeur) => {
    setErreur("");
    setMode({ nature: "historique", valeur: v, versions: null });
    const versions = await lire<Valeur[]>(`/api/effectifs/referentiels/${type}/${encodeURIComponent(v.code)}/historique`);
    setMode((m) => (m?.nature === "historique" && m.valeur.id === v.id ? { ...m, versions: liste<Valeur>(versions) } : m));
  };

  const colonnes: ReadonlyArray<Colonne<Valeur>> = [
    { id: "code", libelle: "Code", tri: (v) => v.code },
    { id: "libelle", libelle: "Libellé", tri: (v) => v.libelleFr },
    ...(descripteur?.comportements ? [{ id: "comportement", libelle: "Comportement", tri: (v: Valeur) => v.comportement ?? "" }] : []),
    { id: "ordre", libelle: "Ordre", numerique: true, tri: (v) => v.ordre },
    { id: "vigueur", libelle: "En vigueur", tri: (v) => v.effectifDepuis },
    { id: "actions", libelle: "Actions", actions: true },
  ];

  return (
    <div data-testid="ref-ecran">
      <div className="eff-barre">
        <p>
          <label htmlFor="ref-type">Référentiel</label>
          <select id="ref-type" data-testid="ref-type" value={type} onChange={(e) => { setType(e.target.value); setSucces(""); }}>
            {descripteurs.map((d) => <option key={d.type} value={d.type}>{d.libelle}</option>)}
          </select>
        </p>
        <p>
          <label htmlFor="ref-date">En vigueur le</label>
          <input id="ref-date" type="date" data-testid="ref-filtre-vigueur" value={date} onChange={(e) => setDate(e.target.value)} />
        </p>
        <button type="button" data-testid="ref-ajouter" onClick={() => { setErreur(""); setMode({ nature: "creer" }); }} disabled={!descripteur}>
          Ajouter une valeur
        </button>
        {type === "unites" && <AlignementOrganigramme onAligne={(m) => { setSucces(m); void charger(); }} />}
        <PromotionConfiguration onApplique={(m) => { setSucces(m); void charger(); }} />
      </div>

      {erreur && !mode && <Message variante="erreur" testId="ref-erreur">{erreur}</Message>}
      {succes && <Message variante="succes" testId="ref-succes">{succes}</Message>}

      <Tableau
        colonnes={colonnes}
        lignes={valeurs}
        legende={`${descripteur?.libelle ?? "Référentiel"} en vigueur le ${date}`}
        testId="ref-tableau"
        vide={<EtatVide titre="Aucune valeur en vigueur à cette date" compact testId="ref-vide">Ajoutez-en une, ou changez la date.</EtatVide>}
        rendre={(v) => (
          <tr key={v.id} data-testid={`ref-ligne-${v.code}`}>
            <td>{v.code}</td>
            <td>{v.libelleFr}{v.libelleEn ? <span className="muted"> · {v.libelleEn}</span> : null}</td>
            {descripteur?.comportements && <td>{v.comportement ? lisible(v.comportement) : "—"}</td>}
            <td className="col-numerique">{v.ordre}</td>
            <td>depuis le {v.effectifDepuis}{v.effectifJusqua ? `, jusqu'au ${v.effectifJusqua} (exclu)` : ""}</td>
            <td className="col-menu">
              <button type="button" className="secondary" data-testid={`ref-remplacer-${v.code}`} onClick={() => { setErreur(""); setMode({ nature: "remplacer", valeur: v }); }}>
                Remplacer<span className="sr-only"> {v.code}</span>
              </button>{" "}
              <button type="button" className="secondary" data-testid={`ref-historique-${v.code}`} onClick={() => void voirHistorique(v)}>
                Historique<span className="sr-only"> de {v.code}</span>
              </button>{" "}
              {!v.effectifJusqua && (
                <button type="button" className="secondary" data-testid={`ref-fermer-${v.code}`} onClick={() => { setErreur(""); setMode({ nature: "fermer", valeur: v }); }}>
                  Fermer<span className="sr-only"> {v.code}</span>
                </button>
              )}
            </td>
          </tr>
        )}
      />

      {mode?.nature === "historique" && (
        <Tiroir ouvert titre={`Historique de « ${mode.valeur.code} »`} onFermer={() => setMode(null)} testId="ref-historique">
          {mode.versions === null
            ? <p role="status">Chargement de l'historique…</p>
            : (
              <ol data-testid="ref-historique-versions">
                {mode.versions.map((x) => (
                  <li key={x.id}>{x.libelleFr}{x.comportement ? ` (${lisible(x.comportement)})` : ""} — depuis le {x.effectifDepuis}{x.effectifJusqua ? `, jusqu'au ${x.effectifJusqua} (exclu)` : ", toujours en vigueur"}</li>
                ))}
              </ol>
            )}
        </Tiroir>
      )}
      {mode && mode.nature !== "historique" && descripteur && (
        mode.nature === "fermer"
          ? <TiroirFermeture valeur={mode.valeur} erreur={erreur} onFermer={() => setMode(null)}
              onEnregistrer={async (d) => {
                const r = await envoyer(`/api/effectifs/referentiels/${type}/${encodeURIComponent(mode.valeur.code)}/fermer`, { date: d });
                if (r) { setMode(null); setSucces(`« ${mode.valeur.code} » cessera d'être en vigueur le ${d}.`); await charger(); }
              }} />
          : <FormulaireReferentiel descripteur={descripteur} valeur={mode.nature === "remplacer" ? mode.valeur : null} erreur={erreur}
              onFermer={() => setMode(null)}
              onEnregistrer={async (corps) => {
                const chemin = mode.nature === "remplacer"
                  ? `/api/effectifs/referentiels/${type}/${encodeURIComponent(mode.valeur.code)}/versions`
                  : `/api/effectifs/referentiels/${type}`;
                const r = await envoyer<Valeur>(chemin, corps);
                if (r) { setMode(null); setSucces(`« ${r.code} » enregistré, en vigueur à partir du ${r.effectifDepuis}.`); await charger(); }
              }} />
      )}
    </div>
  );
}

function TiroirFermeture({ valeur, erreur, onFermer, onEnregistrer }: { valeur: Valeur; erreur: string; onFermer: () => void; onEnregistrer: (d: string) => Promise<void> }) {
  const [date, setDate] = useState(aujourdhui());
  return (
    <Tiroir ouvert titre={`Fermer « ${valeur.code} »`} onFermer={onFermer} testId="ref-fermeture">
      <form onSubmit={(e) => { e.preventDefault(); void onEnregistrer(date); }}>
        <p className="aide" id="ref-fermeture-aide">La valeur ne sera plus proposée à partir de cette date. Les faits antérieurs la conservent. Une date passée est refusée.</p>
        <p>
          <label htmlFor="ref-fermeture-date">Dernier jour exclu</label>
          <input id="ref-fermeture-date" type="date" required data-testid="ref-fermeture-date" aria-describedby="ref-fermeture-aide" value={date} onChange={(e) => setDate(e.target.value)} />
        </p>
        {erreur && <Message variante="erreur" testId="ref-form-erreur">{erreur}</Message>}
        <button type="submit" data-testid="ref-fermeture-confirmer">Fermer la valeur</button>
      </form>
    </Tiroir>
  );
}

/**
 * **Le formulaire générique d'un référentiel**, construit depuis son descripteur. Les références
 * (unité parente, corps d'emploi…) sont des listes chargées du référentiel visé, jamais saisies.
 */
export function FormulaireReferentiel({ descripteur, valeur, erreur, onFermer, onEnregistrer }: {
  descripteur: Descripteur; valeur: Valeur | null; erreur: string; onFermer: () => void; onEnregistrer: (corps: Record<string, unknown>) => Promise<void>;
}) {
  const { lire } = useApiEffectifs();
  const [code, setCode] = useState(valeur?.code ?? "");
  const [libelleFr, setLibelleFr] = useState(valeur?.libelleFr ?? "");
  const [libelleEn, setLibelleEn] = useState(valeur?.libelleEn ?? "");
  const [comportement, setComportement] = useState(valeur?.comportement ?? descripteur.comportements?.[0] ?? "");
  const [ordre, setOrdre] = useState(String(valeur?.ordre ?? 0));
  const [date, setDate] = useState(aujourdhui());
  const [attributs, setAttributs] = useState<Record<string, unknown>>(valeur?.attributs ?? {});
  const [options, setOptions] = useState<Record<string, Valeur[]>>({});

  useEffect(() => {
    const types = [...new Set(descripteur.attributs.filter((a) => a.reference).map((a) => a.reference!))];
    void Promise.all(types.map(async (t) => [t, (await lire<Valeur[]>(`/api/effectifs/referentiels/${t}`)) ?? []] as const))
      .then((paires) => setOptions(Object.fromEntries(paires)));
  }, [descripteur, lire]);

  const poser = (nom: string, v: unknown) => setAttributs((a) => ({ ...a, [nom]: v }));
  const remplacement = valeur !== null;

  return (
    <Tiroir ouvert titre={remplacement ? `Remplacer « ${valeur!.code} » à partir d'une date` : `Nouvelle valeur — ${descripteur.libelle}`} onFermer={onFermer} testId="ref-formulaire">
      <form onSubmit={(e) => {
        e.preventDefault();
        const corps: Record<string, unknown> = { libelleFr, libelleEn, ordre, attributs };
        if (descripteur.comportements) corps.comportement = comportement;
        if (remplacement) corps.depuis = date; else { corps.code = code; corps.effectifDepuis = date; }
        void onEnregistrer(corps);
      }}>
        {!remplacement && (
          <p>
            <label htmlFor="ref-form-code">Code</label>
            <input id="ref-form-code" data-testid="ref-form-code" required value={code} onChange={(e) => setCode(e.target.value)} aria-describedby="ref-form-code-aide" />
            <span id="ref-form-code-aide" className="aide">Lettres, chiffres, tirets ou soulignés (40 au plus). Il ne change plus ensuite.</span>
          </p>
        )}
        <p>
          <label htmlFor="ref-form-libelleFr">Libellé (français)</label>
          <input id="ref-form-libelleFr" data-testid="ref-form-libelleFr" required value={libelleFr} onChange={(e) => setLibelleFr(e.target.value)} />
        </p>
        <p>
          <label htmlFor="ref-form-libelleEn">Libellé (anglais, facultatif)</label>
          <input id="ref-form-libelleEn" data-testid="ref-form-libelleEn" value={libelleEn} onChange={(e) => setLibelleEn(e.target.value)} />
        </p>
        {descripteur.comportements && (
          <p>
            <label htmlFor="ref-form-comportement">Comportement dans le logiciel</label>
            <select id="ref-form-comportement" data-testid="ref-form-comportement" value={comportement} onChange={(e) => setComportement(e.target.value)}>
              {descripteur.comportements.map((c) => <option key={c} value={c}>{lisible(c)}</option>)}
            </select>
          </p>
        )}
        <p>
          <label htmlFor="ref-form-ordre">Ordre d'affichage</label>
          <input id="ref-form-ordre" type="number" step={1} data-testid="ref-form-ordre" value={ordre} onChange={(e) => setOrdre(e.target.value)} />
        </p>
        {descripteur.attributs.map((a) => (
          <ChampAttribut key={a.nom} attribut={a} valeur={attributs[a.nom]} options={options[a.reference ?? ""] ?? []} onChange={(v) => poser(a.nom, v)} />
        ))}
        <p>
          <label htmlFor="ref-form-date">{remplacement ? "En vigueur à partir du (aujourd'hui ou plus tard)" : "En vigueur à partir du"}</label>
          <input id="ref-form-date" type="date" required data-testid="ref-form-date" value={date} onChange={(e) => setDate(e.target.value)} />
        </p>
        {erreur && <Message variante="erreur" testId="ref-form-erreur">{erreur}</Message>}
        <button type="submit" data-testid="ref-form-enregistrer">Enregistrer</button>
      </form>
    </Tiroir>
  );
}

function ChampAttribut({ attribut: a, valeur, options, onChange }: {
  attribut: DescripteurAttribut; valeur: unknown; options: Valeur[]; onChange: (v: unknown) => void;
}) {
  const id = `ref-form-attr-${a.nom}`;
  const libelle = `${a.libelle}${a.obligatoire ? "" : " (facultatif)"}`;
  if (a.type === "booleen") {
    return (
      <p className="eff-case">
        <input id={`${id}`} data-testid={id} type="checkbox" checked={valeur === true} onChange={(e) => onChange(e.target.checked)} />
        <label htmlFor={`${id}`}>{a.libelle}</label>
      </p>
    );
  }
  if (a.type === "references") {
    const choisis = new Set(Array.isArray(valeur) ? valeur.map(String) : []);
    return (
      <fieldset className="eff-groupe" data-testid={id}>
        <legend>{libelle}</legend>
        {options.length === 0 && <p className="muted">Aucune valeur disponible.</p>}
        {options.map((o) => (
          <p key={o.code} className="eff-case">
            <input id={`${id}-${o.code}`} type="checkbox" checked={choisis.has(o.code)}
              onChange={(e) => { const s = new Set(choisis); if (e.target.checked) s.add(o.code); else s.delete(o.code); onChange([...s]); }} />
            <label htmlFor={`${id}-${o.code}`}>{o.code} — {o.libelleFr}</label>
          </p>
        ))}
      </fieldset>
    );
  }
  return (
    <p>
      <label htmlFor={`${id}`}>{libelle}</label>
      {a.type === "choix" || a.type === "reference" ? (
        <select id={`${id}`} data-testid={id} required={a.obligatoire} value={valeur === undefined ? "" : String(valeur)} onChange={(e) => onChange(e.target.value || undefined)}>
          <option value="">{a.obligatoire ? "Choisir…" : "Aucune"}</option>
          {a.type === "choix"
            ? a.valeurs!.map((v) => <option key={v} value={v}>{lisible(v)}</option>)
            : options.map((o) => <option key={o.code} value={o.code}>{o.code} — {o.libelleFr}</option>)}
        </select>
      ) : (
        <input id={`${id}`} data-testid={id} type={a.type === "nombre" ? "number" : "text"} required={a.obligatoire} min={a.min} max={a.max}
          value={valeur === undefined ? "" : String(valeur)} onChange={(e) => onChange(e.target.value === "" ? undefined : a.type === "nombre" ? Number(e.target.value) : e.target.value)} />
      )}
    </p>
  );
}
