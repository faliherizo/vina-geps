/**
 * **Périodes de planification** (E-GR-10, E-GR-11) : créer, faire avancer dans le cycle
 * préparation → collecte → confection → publiée → close, générer les quarts depuis les postes.
 *
 * Le cycle est rendu par `Etapes` (l'étape courante porte `aria-current="step"`) ; seules les
 * transitions admises sont proposées, et le serveur refuse les autres de toute façon.
 */
import { useState } from "react";
import { Badge, EtatVide, Etapes, Message, Tableau, Tiroir, type Colonne } from "../../composants";
import { aujourdhui, lisible, useApiEffectifs } from "../api";
import type { Periode } from "./types";
import { useReferentiels } from "./useReferentiels";

const CYCLE = ["PREPARATION", "COLLECTE", "CONFECTION", "PUBLIEE", "CLOSE"] as const;
const SUIVANTS: Record<Periode["etat"], Periode["etat"][]> = {
  PREPARATION: ["COLLECTE", "CONFECTION"], COLLECTE: ["CONFECTION"], CONFECTION: ["PUBLIEE"], PUBLIEE: ["CLOSE"], CLOSE: [],
};

export default function Periodes({ periodes, recharger }: { periodes: Periode[]; recharger: () => Promise<void> }) {
  const { envoyer, erreur, setErreur, succes, setSucces } = useApiEffectifs();
  const { refs } = useReferentiels(["unites"]);
  const [creation, setCreation] = useState(false);
  const [collecte, setCollecte] = useState<Periode | null>(null);

  async function avancer(p: Periode, etat: Periode["etat"], finCollecte?: string) {
    const r = await envoyer(`/api/effectifs/planification/periodes/${p.id}/etat`, { etat, finCollecte });
    if (r) { setCollecte(null); setSucces(`« ${p.nom} » : ${lisible(etat)}.`); await recharger(); }
  }

  async function generer(p: Periode) {
    const r = await envoyer<{ equipagesCrees: number; placesCreees: number }>(`/api/effectifs/planification/periodes/${p.id}/generer`, {});
    if (r) setSucces(`${r.equipagesCrees} quart(s) et ${r.placesCreees} place(s) générés pour « ${p.nom} ».`);
  }

  const colonnes: ReadonlyArray<Colonne<Periode>> = [
    { id: "nom", libelle: "Période", tri: (p) => p.nom },
    { id: "unite", libelle: "Unité", tri: (p) => p.uniteCode },
    { id: "dates", libelle: "Dates", tri: (p) => p.debut },
    { id: "cycle", libelle: "Cycle" },
    { id: "actions", libelle: "Actions", actions: true },
  ];

  return (
    <div data-testid="pla-periodes">
      <div className="eff-barre">
        <button type="button" data-testid="pla-periode-nouvelle" onClick={() => { setErreur(""); setCreation(true); }}>Nouvelle période</button>
      </div>
      {erreur && !creation && !collecte && <Message variante="erreur" testId="pla-periodes-erreur">{erreur}</Message>}
      {succes && <Message variante="succes" testId="pla-periodes-succes">{succes}</Message>}
      <Tableau colonnes={colonnes} lignes={periodes} legende="Périodes de planification" testId="pla-periodes-tableau"
        vide={<EtatVide titre="Aucune période" compact testId="pla-periodes-vide">Créez une période pour y planifier des quarts.</EtatVide>}
        rendre={(p) => (
          <tr key={p.id} data-testid={`pla-periode-${p.id}`}>
            <td>{p.nom}</td>
            <td>{p.uniteCode}</td>
            <td>{p.debut} au {p.fin}{p.finCollecte ? <span className="muted"> · collecte jusqu'au {p.finCollecte}</span> : null}</td>
            <td>
              <Etapes etapes={CYCLE.map((e) => ({ id: e, libelle: lisible(e) }))} courante={p.etat} libelle={`Cycle de la période ${p.nom}`} testId={`pla-periode-etapes-${p.id}`} />
            </td>
            <td className="col-menu">
              <span className="eff-actions">
                {SUIVANTS[p.etat].map((e) => (
                  <button key={e} type="button" className="secondary" data-testid={`pla-periode-${e.toLowerCase()}-${p.id}`}
                    onClick={() => (e === "COLLECTE" ? (setErreur(""), setCollecte(p)) : void avancer(p, e))}>
                    {e === "COLLECTE" ? "Ouvrir la collecte" : e === "CLOSE" ? "Clore" : `Passer à : ${lisible(e)}`}<span className="sr-only"> — {p.nom}</span>
                  </button>
                ))}
                {p.etat !== "CLOSE" && (
                  <button type="button" className="secondary" data-testid={`pla-periode-generer-${p.id}`} onClick={() => void generer(p)}>
                    Générer les quarts<span className="sr-only"> de {p.nom}</span>
                  </button>
                )}
                {p.etat === "CLOSE" && <Badge ton="neutre">Lecture seule</Badge>}
              </span>
            </td>
          </tr>
        )} />

      {creation && (
        <TiroirPeriode unites={refs.unites ?? []} erreur={erreur} onFermer={() => setCreation(false)} onEnregistrer={async (c) => {
          const r = await envoyer<Periode>("/api/effectifs/planification/periodes", c);
          if (r) { setCreation(false); setSucces(`Période « ${r.nom} » créée.`); await recharger(); }
        }} />
      )}
      {collecte && (
        <TiroirCollecte periode={collecte} erreur={erreur} onFermer={() => setCollecte(null)} onEnregistrer={(fin) => avancer(collecte, "COLLECTE", fin)} />
      )}
    </div>
  );
}

function TiroirPeriode({ unites, erreur, onFermer, onEnregistrer }: {
  unites: Array<{ code: string; libelleFr: string }>; erreur: string; onFermer: () => void; onEnregistrer: (c: Record<string, unknown>) => Promise<void>;
}) {
  const [nom, setNom] = useState("");
  const [uniteCode, setUniteCode] = useState("");
  const [debut, setDebut] = useState(aujourdhui());
  const [fin, setFin] = useState(aujourdhui());
  return (
    <Tiroir ouvert titre="Nouvelle période" onFermer={onFermer} testId="pla-periode-tiroir">
      <form onSubmit={(e) => { e.preventDefault(); void onEnregistrer({ nom, uniteCode, debut, fin }); }}>
        <p><label htmlFor="pla-per-nom">Nom</label><input id="pla-per-nom" data-testid="pla-per-nom" required value={nom} onChange={(e) => setNom(e.target.value)} /></p>
        <p>
          <label htmlFor="pla-per-unite">Unité</label>
          <select id="pla-per-unite" data-testid="pla-per-unite" required value={uniteCode} onChange={(e) => setUniteCode(e.target.value)}>
            <option value="">Choisir…</option>
            {unites.map((u) => <option key={u.code} value={u.code}>{u.code} — {u.libelleFr}</option>)}
          </select>
        </p>
        <p><label htmlFor="pla-per-debut">Premier jour</label><input id="pla-per-debut" type="date" data-testid="pla-per-debut" required value={debut} onChange={(e) => setDebut(e.target.value)} /></p>
        <p><label htmlFor="pla-per-fin">Dernier jour</label><input id="pla-per-fin" type="date" data-testid="pla-per-fin" required value={fin} onChange={(e) => setFin(e.target.value)} /></p>
        {erreur && <Message variante="erreur" testId="pla-per-erreur">{erreur}</Message>}
        <button type="submit" data-testid="pla-per-enregistrer">Créer la période</button>
      </form>
    </Tiroir>
  );
}

function TiroirCollecte({ periode, erreur, onFermer, onEnregistrer }: { periode: Periode; erreur: string; onFermer: () => void; onEnregistrer: (fin: string) => Promise<void> }) {
  const [fin, setFin] = useState(aujourdhui());
  return (
    <Tiroir ouvert titre={`Ouvrir la collecte — ${periode.nom}`} onFermer={onFermer} testId="pla-collecte-tiroir">
      <form onSubmit={(e) => { e.preventDefault(); void onEnregistrer(fin); }}>
        <p className="aide" id="pla-collecte-aide">Les employés déposeront leurs disponibilités jusqu'à cette date, au plus tard le {periode.debut}.</p>
        <p>
          <label htmlFor="pla-collecte-fin">Fin de la collecte</label>
          <input id="pla-collecte-fin" type="date" data-testid="pla-collecte-fin" required aria-describedby="pla-collecte-aide" value={fin} onChange={(e) => setFin(e.target.value)} />
        </p>
        {erreur && <Message variante="erreur" testId="pla-collecte-erreur">{erreur}</Message>}
        <button type="submit" data-testid="pla-collecte-ouvrir">Ouvrir la collecte</button>
      </form>
    </Tiroir>
  );
}
