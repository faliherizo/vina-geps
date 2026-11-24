/**
 * **Autres unités d'une personne** (AO 26-015-DO, addenda 8, M-09) : un paramédic affecté à deux secteurs, un prêt
 * temporaire. Chaque rattachement est daté ; pendant sa durée, la personne figure parmi les ressources et les candidats
 * de l'autre unité. Qui voit ses quarts reste décidé par les périmètres de chacun (profil et droits).
 */
import { useCallback, useEffect, useState, type FormEvent } from "react";
import { EtatVide, Message, Tableau, Tiroir, type Colonne } from "../../composants";
import { useApiEffectifs } from "../api";
import { liste as listeRecue } from "../../formeRecue";
import type { Ressource } from "./types";
import { libelleDe, useReferentiels } from "./useReferentiels";

interface Rattachement { uniteCode: string; depuis: string; jusqua: string | null; actif: boolean }

export function RattachementsRessource({ ressource, onFermer }: { ressource: Ressource; onFermer: () => void }) {
  const { lire, envoyer, supprimer, erreur, setErreur, succes, setSucces } = useApiEffectifs();
  const { refs } = useReferentiels(["unites"]);
  const [lignes, setLignes] = useState<Rattachement[]>([]);
  const base = `/api/effectifs/planification/ressources/${encodeURIComponent(ressource.id)}/rattachements`;
  const charger = useCallback(async () => setLignes(listeRecue<Rattachement>(await lire<Rattachement[]>(base))), [lire, base]);
  useEffect(() => { void charger(); }, [charger]);

  const ajouter = async (corps: Record<string, string>) => {
    if (await envoyer(base, corps)) { setSucces(`Rattachée à « ${libelleDe(refs.unites, corps.uniteCode)} ».`); await charger(); }
  };
  const retirer = async (r: Rattachement) => {
    if (await supprimer(`${base}/${encodeURIComponent(r.uniteCode)}?depuis=${r.depuis}`)) {
      setSucces(`Rattachement à « ${libelleDe(refs.unites, r.uniteCode)} » retiré.`);
      await charger();
    }
  };
  const colonnes: ReadonlyArray<Colonne<Rattachement>> = [
    { id: "unite", libelle: "Unité" }, { id: "depuis", libelle: "Depuis" }, { id: "jusqua", libelle: "Jusqu'au" },
    { id: "etat", libelle: "En vigueur" }, { id: "actions", libelle: "Actions", actions: true },
  ];
  return (
    <Tiroir ouvert titre={`Autres unités — ${ressource.nom}`} onFermer={onFermer} testId="pla-rattachements">
      <p className="muted">Unité principale : {libelleDe(refs.unites, ressource.uniteCode)}.</p>
      {succes && <Message variante="succes" testId="pla-rattachements-succes">{succes}</Message>}
      {erreur && <Message variante="erreur" testId="pla-rattachements-erreur">{erreur}</Message>}
      <Tableau colonnes={colonnes} lignes={lignes} legende={`Autres unités de ${ressource.nom}`} testId="pla-rattachements-tableau"
        vide={<EtatVide titre="Aucune autre unité" compact testId="pla-rattachements-vide">La personne n'appartient qu'à son unité principale.</EtatVide>}
        rendre={(r) => (
          <tr key={`${r.uniteCode}-${r.depuis}`} data-testid={`pla-rattachement-${r.uniteCode}`}>
            <td>{libelleDe(refs.unites, r.uniteCode)}</td><td>{r.depuis}</td><td>{r.jusqua ?? "Sans fin"}</td><td>{r.actif ? "Oui" : "Non"}</td>
            <td className="col-menu">
              <button type="button" className="secondary" data-testid={`pla-rattachement-retirer-${r.uniteCode}`} onClick={() => { setErreur(""); void retirer(r); }}>
                Retirer<span className="sr-only"> le rattachement à {libelleDe(refs.unites, r.uniteCode)}</span>
              </button>
            </td>
          </tr>
        )} />
      <FormulaireRattachement unites={(refs.unites ?? []).filter((u) => u.code !== ressource.uniteCode)} onAjouter={(c) => { setErreur(""); void ajouter(c); }} />
    </Tiroir>
  );
}

function FormulaireRattachement({ unites, onAjouter }: { unites: Array<{ code: string; libelleFr: string }>; onAjouter: (c: Record<string, string>) => void }) {
  const [v, setV] = useState({ uniteCode: "", depuis: "", jusqua: "" });
  const soumettre = (e: FormEvent) => { e.preventDefault(); onAjouter(v); };
  return (
    <form onSubmit={soumettre} data-testid="pla-rattachement-formulaire">
      <h3>Rattacher à une autre unité</h3>
      <p><label htmlFor="pla-rattachement-unite">Unité</label>
        <select id="pla-rattachement-unite" data-testid="pla-rattachement-unite" required value={v.uniteCode} onChange={(e) => setV({ ...v, uniteCode: e.target.value })}>
          <option value="">Choisir…</option>
          {unites.map((u) => <option key={u.code} value={u.code}>{u.libelleFr}</option>)}
        </select></p>
      <p><label htmlFor="pla-rattachement-depuis">Depuis</label>
        <input id="pla-rattachement-depuis" type="date" data-testid="pla-rattachement-depuis" required value={v.depuis} onChange={(e) => setV({ ...v, depuis: e.target.value })} /></p>
      <p><label htmlFor="pla-rattachement-jusqua">Jusqu'au (facultatif)</label>
        <input id="pla-rattachement-jusqua" type="date" data-testid="pla-rattachement-jusqua" value={v.jusqua} onChange={(e) => setV({ ...v, jusqua: e.target.value })} /></p>
      <button type="submit" data-testid="pla-rattachement-ajouter">Rattacher</button>
    </form>
  );
}
