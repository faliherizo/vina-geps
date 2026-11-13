/**
 * **Milieux physiques** (AO 26-015-DO, écran E3 — E-GN-17, E-GP-7, E-GP-11, E-GP-12).
 *
 * Latitude, longitude et rayon se **saisissent en champs** : une carte peut situer un milieu,
 * elle ne peut pas être le seul moyen de le saisir (WCAG 2.5.7). Les preuves de présence exigées
 * sont des cases de 24 px ; le secret du code QR n'est jamais affiché — le serveur le garde.
 */
import { useCallback, useEffect, useState } from "react";
import { Badge, EtatVide, Message, Tableau, Tiroir, type Colonne } from "../composants";
import { lisible, useApiEffectifs } from "./api";
import type { Valeur } from "./Referentiels";

interface Preuve { mecanisme: string; exige: boolean; parametres: { plages?: string[] } }
interface Milieu {
  id: string; code: string; libelle: string; typeCode: string; uniteCode: string; adresse: string | null;
  latitude: number | null; longitude: number | null; rayonMetres: number | null; actif: boolean; preuves: Preuve[];
}

/** Les mécanismes que le logiciel sait vérifier : un ensemble fermé du code, pas un paramétrage. */
const MECANISMES = ["GEOREPERAGE", "CODE_QR_DE_LIEU", "RESEAU", "BORNE", "SOURCE_EXTERNE"] as const;

export default function Milieux() {
  const { lire, envoyer, erreur, setErreur, succes, setSucces } = useApiEffectifs();
  const [milieux, setMilieux] = useState<Milieu[]>([]);
  const [types, setTypes] = useState<Valeur[]>([]);
  const [unites, setUnites] = useState<Valeur[]>([]);
  const [fiche, setFiche] = useState<Milieu | "nouveau" | null>(null);
  const [preuves, setPreuves] = useState<Milieu | null>(null);

  const charger = useCallback(async () => {
    const [m, t, u] = await Promise.all([
      lire<Milieu[]>("/api/effectifs/milieux"),
      lire<Valeur[]>("/api/effectifs/referentiels/types_milieu"),
      lire<Valeur[]>("/api/effectifs/referentiels/unites"),
    ]);
    if (m) setMilieux(m);
    if (t) setTypes(t);
    if (u) setUnites(u);
  }, [lire]);

  useEffect(() => { void charger(); }, [charger]);

  const colonnes: ReadonlyArray<Colonne<Milieu>> = [
    { id: "code", libelle: "Code", tri: (m) => m.code },
    { id: "libelle", libelle: "Libellé", tri: (m) => m.libelle },
    { id: "type", libelle: "Type", tri: (m) => m.typeCode },
    { id: "unite", libelle: "Unité", tri: (m) => m.uniteCode },
    { id: "preuves", libelle: "Preuves de présence" },
    { id: "etat", libelle: "État" },
    { id: "actions", libelle: "Actions", actions: true },
  ];

  return (
    <div data-testid="mil-ecran">
      <div className="eff-barre">
        <button type="button" data-testid="mil-ajouter" onClick={() => { setErreur(""); setFiche("nouveau"); }}>Ajouter un milieu</button>
      </div>
      {erreur && !fiche && !preuves && <Message variante="erreur" testId="mil-erreur">{erreur}</Message>}
      {succes && <Message variante="succes" testId="mil-succes">{succes}</Message>}
      <Tableau
        colonnes={colonnes} lignes={milieux} legende="Milieux physiques de votre périmètre" testId="mil-tableau"
        vide={<EtatVide titre="Aucun milieu dans votre périmètre" compact testId="mil-vide">Un milieu appartient à une unité : il n'apparaît qu'à ceux qui ont cette unité dans leur périmètre.</EtatVide>}
        rendre={(m) => (
          <tr key={m.id} data-testid={`mil-ligne-${m.code}`}>
            <td>{m.code}</td>
            <td>{m.libelle}{m.adresse ? <span className="muted"> · {m.adresse}</span> : null}</td>
            <td>{types.find((t) => t.code === m.typeCode)?.libelleFr ?? m.typeCode}</td>
            <td>{m.uniteCode}</td>
            <td>{m.preuves.length ? m.preuves.map((p) => `${lisible(p.mecanisme)}${p.exige ? " (exigée)" : ""}`).join(", ") : "Aucune"}</td>
            <td>{m.actif ? <Badge ton="succes">Actif</Badge> : <Badge ton="neutre">Inactif</Badge>}</td>
            <td className="col-menu">
              <span className="eff-actions">
                <button type="button" className="secondary" data-testid={`mil-modifier-${m.code}`} onClick={() => { setErreur(""); setFiche(m); }}>
                  Modifier<span className="sr-only"> {m.code}</span>
                </button>
                <button type="button" className="secondary" data-testid={`mil-preuves-${m.code}`} onClick={() => { setErreur(""); setPreuves(m); }}>
                  Preuves<span className="sr-only"> de {m.code}</span>
                </button>
              </span>
            </td>
          </tr>
        )}
      />

      {fiche && (
        <FicheMilieu milieu={fiche === "nouveau" ? null : fiche} types={types} unites={unites} erreur={erreur} onFermer={() => setFiche(null)}
          onEnregistrer={async (corps) => {
            const r = fiche === "nouveau"
              ? await envoyer<Milieu>("/api/effectifs/milieux", corps)
              : await envoyer<Milieu>(`/api/effectifs/milieux/${fiche.id}`, corps, "PUT");
            if (r) { setFiche(null); setSucces(`Milieu « ${r.code} » enregistré.`); await charger(); }
          }} />
      )}
      {preuves && (
        <TiroirPreuves milieu={preuves} erreur={erreur} onFermer={() => setPreuves(null)}
          onEnregistrer={async (liste) => {
            const r = await envoyer<Milieu>(`/api/effectifs/milieux/${preuves.id}/preuves`, { preuves: liste }, "PUT");
            if (r) { setPreuves(null); setSucces(`Preuves de présence de « ${r.code} » enregistrées.`); await charger(); }
          }} />
      )}
    </div>
  );
}

function FicheMilieu({ milieu, types, unites, erreur, onFermer, onEnregistrer }: {
  milieu: Milieu | null; types: Valeur[]; unites: Valeur[]; erreur: string; onFermer: () => void; onEnregistrer: (c: Record<string, unknown>) => Promise<void>;
}) {
  const [code, setCode] = useState(milieu?.code ?? "");
  const [libelle, setLibelle] = useState(milieu?.libelle ?? "");
  const [typeCode, setTypeCode] = useState(milieu?.typeCode ?? "");
  const [uniteCode, setUniteCode] = useState(milieu?.uniteCode ?? "");
  const [adresse, setAdresse] = useState(milieu?.adresse ?? "");
  const [latitude, setLatitude] = useState(milieu?.latitude?.toString() ?? "");
  const [longitude, setLongitude] = useState(milieu?.longitude?.toString() ?? "");
  const [rayon, setRayon] = useState(milieu?.rayonMetres?.toString() ?? "");
  const [actif, setActif] = useState(milieu?.actif ?? true);
  return (
    <Tiroir ouvert titre={milieu ? `Milieu ${milieu.code}` : "Nouveau milieu"} onFermer={onFermer} testId="mil-fiche">
      <form onSubmit={(e) => {
        e.preventDefault();
        const corps: Record<string, unknown> = { libelle, typeCode, uniteCode, adresse, latitude, longitude, rayonMetres: rayon };
        if (milieu) corps.actif = actif; else corps.code = code;
        void onEnregistrer(corps);
      }}>
        {!milieu && (
          <p><label htmlFor="mil-code">Code</label><input id="mil-code" data-testid="mil-form-code" required value={code} onChange={(e) => setCode(e.target.value)} /></p>
        )}
        <p><label htmlFor="mil-libelle">Libellé</label><input id="mil-libelle" data-testid="mil-form-libelle" required value={libelle} onChange={(e) => setLibelle(e.target.value)} /></p>
        <p>
          <label htmlFor="mil-type">Type</label>
          <select id="mil-type" data-testid="mil-form-type" required value={typeCode} onChange={(e) => setTypeCode(e.target.value)}>
            <option value="">Choisir…</option>
            {types.map((t) => <option key={t.code} value={t.code}>{t.libelleFr}</option>)}
          </select>
        </p>
        <p>
          <label htmlFor="mil-unite">Unité</label>
          <select id="mil-unite" data-testid="mil-form-unite" required value={uniteCode} onChange={(e) => setUniteCode(e.target.value)}>
            <option value="">Choisir…</option>
            {unites.map((u) => <option key={u.code} value={u.code}>{u.code} — {u.libelleFr}</option>)}
          </select>
        </p>
        <p><label htmlFor="mil-adresse">Adresse (facultatif)</label><input id="mil-adresse" data-testid="mil-form-adresse" value={adresse} onChange={(e) => setAdresse(e.target.value)} /></p>
        <fieldset className="eff-groupe">
          <legend>Position (pour le géorepérage ; les trois champs ensemble ou aucun)</legend>
          <p><label htmlFor="mil-lat">Latitude</label><input id="mil-lat" data-testid="mil-form-latitude" inputMode="decimal" value={latitude} onChange={(e) => setLatitude(e.target.value)} /></p>
          <p><label htmlFor="mil-lon">Longitude</label><input id="mil-lon" data-testid="mil-form-longitude" inputMode="decimal" value={longitude} onChange={(e) => setLongitude(e.target.value)} /></p>
          <p><label htmlFor="mil-rayon">Rayon en mètres (10 à 5 000)</label><input id="mil-rayon" data-testid="mil-form-rayon" inputMode="numeric" value={rayon} onChange={(e) => setRayon(e.target.value)} /></p>
        </fieldset>
        {milieu && (
          <p className="eff-case"><input id="mil-actif" type="checkbox" data-testid="mil-form-actif" checked={actif} onChange={(e) => setActif(e.target.checked)} /><label htmlFor="mil-actif">Milieu actif</label></p>
        )}
        {erreur && <Message variante="erreur" testId="mil-form-erreur">{erreur}</Message>}
        <button type="submit" data-testid="mil-form-enregistrer">Enregistrer</button>
      </form>
    </Tiroir>
  );
}

function TiroirPreuves({ milieu, erreur, onFermer, onEnregistrer }: {
  milieu: Milieu; erreur: string; onFermer: () => void; onEnregistrer: (p: Preuve[]) => Promise<void>;
}) {
  const initial = new Map(milieu.preuves.map((p) => [p.mecanisme, p]));
  const [choix, setChoix] = useState<Record<string, { actif: boolean; exige: boolean }>>(
    Object.fromEntries(MECANISMES.map((m) => [m, { actif: initial.has(m), exige: initial.get(m)?.exige ?? false }])));
  const [plages, setPlages] = useState((initial.get("RESEAU")?.parametres.plages ?? []).join(", "));
  const basculer = (m: string, cle: "actif" | "exige", v: boolean) => setChoix((c) => ({ ...c, [m]: { ...c[m], [cle]: v } }));
  return (
    <Tiroir ouvert titre={`Preuves de présence — ${milieu.code}`} onFermer={onFermer} testId="mil-preuves">
      <form onSubmit={(e) => {
        e.preventDefault();
        const liste: Preuve[] = MECANISMES.filter((m) => choix[m].actif).map((m) => ({
          mecanisme: m, exige: choix[m].exige,
          parametres: m === "RESEAU" ? { plages: plages.split(",").map((s) => s.trim()).filter(Boolean) } : {},
        }));
        void onEnregistrer(liste);
      }}>
        <p className="aide">Le pointage acceptera ces preuves ; une preuve « exigée » doit être présente pour qu'un pointage soit conforme.</p>
        {MECANISMES.map((m) => (
          <fieldset key={m} className="eff-groupe">
            <legend>{lisible(m)}</legend>
            <p className="eff-case">
              <input id={`mil-p-${m}`} type="checkbox" data-testid={`mil-preuve-${m}`} checked={choix[m].actif} onChange={(e) => basculer(m, "actif", e.target.checked)} />
              <label htmlFor={`mil-p-${m}`}>Acceptée pour ce milieu</label>
            </p>
            <p className="eff-case">
              <input id={`mil-e-${m}`} type="checkbox" data-testid={`mil-exige-${m}`} disabled={!choix[m].actif} checked={choix[m].exige} onChange={(e) => basculer(m, "exige", e.target.checked)} />
              <label htmlFor={`mil-e-${m}`}>Exigée</label>
            </p>
            {m === "RESEAU" && choix[m].actif && (
              <p>
                <label htmlFor="mil-plages">Plages réseau (a.b.c.d/nn, séparées par des virgules)</label>
                <input id="mil-plages" data-testid="mil-preuve-plages" value={plages} onChange={(e) => setPlages(e.target.value)} />
              </p>
            )}
          </fieldset>
        ))}
        {erreur && <Message variante="erreur" testId="mil-preuves-erreur">{erreur}</Message>}
        <button type="submit" data-testid="mil-preuves-enregistrer">Enregistrer les preuves</button>
      </form>
    </Tiroir>
  );
}
