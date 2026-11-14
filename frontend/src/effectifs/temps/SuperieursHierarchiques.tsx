/**
 * **Supérieurs hiérarchiques** — qui relève de qui. C'est ce que lit l'approbateur « Supérieur
 * hiérarchique » du flux des feuilles de temps : la feuille d'une personne va à son supérieur,
 * désigné ici. Sans supérieur, la feuille revient au niveau GESTION de son unité.
 */
import { useCallback, useEffect, useState, type FormEvent } from "react";
import { liste } from "../../formeRecue";
import { Message } from "../../composants";
import { useApiEffectifs } from "../api";

export interface LienHierarchique { id: string; nom: string; superieurId: string | null }

export default function SuperieursHierarchiques() {
  const { lire, envoyer, erreur, setErreur } = useApiEffectifs();
  const [liens, setLiens] = useState<LienHierarchique[]>([]);
  const [personne, setPersonne] = useState("");
  const [superieur, setSuperieur] = useState("");
  const [info, setInfo] = useState("");
  const charger = useCallback(async () => { setLiens(liste<LienHierarchique>(await lire<LienHierarchique[]>("/api/personnel/hierarchie"))); }, [lire]);
  useEffect(() => { void charger(); }, [charger]);
  const nomDe = (id: string | null) => liens.find((l) => l.id === id)?.nom ?? "—";

  const choisir = (id: string) => {
    setPersonne(id); setInfo(""); setErreur("");
    setSuperieur(liens.find((l) => l.id === id)?.superieurId ?? "");
  };

  const enregistrer = async (e: FormEvent) => {
    e.preventDefault();
    setInfo("");
    const r = await envoyer<LienHierarchique>(`/api/personnel/employes/${personne}/superieur`, { superieurId: superieur || null }, "PUT");
    if (!r) return;
    setInfo(r.superieurId ? `${r.nom} relève maintenant de ${nomDe(r.superieurId)}.` : `${r.nom} n'a plus de supérieur désigné : ses feuilles iront au niveau GESTION de son unité.`);
    await charger();
  };

  return (
    <section aria-labelledby="pla-tps-sup-titre" data-testid="pla-tps-sup">
      <h3 id="pla-tps-sup-titre">Supérieurs hiérarchiques</h3>
      <p>Le flux d'approbation peut envoyer une feuille au supérieur de la personne qui la soumet. Désignez ici qui relève de qui.</p>
      <form onSubmit={(e) => void enregistrer(e)} aria-label="Désigner un supérieur">
        <p>
          <label htmlFor="pla-tps-sup-personne">Personne</label>
          <select id="pla-tps-sup-personne" required data-testid="pla-tps-sup-personne" value={personne} onChange={(e) => choisir(e.target.value)}>
            <option value="">— choisir —</option>
            {liens.map((l) => <option key={l.id} value={l.id}>{l.nom}</option>)}
          </select>
        </p>
        <p>
          <label htmlFor="pla-tps-sup-superieur">Son supérieur hiérarchique</label>
          <select id="pla-tps-sup-superieur" data-testid="pla-tps-sup-superieur" value={superieur} disabled={!personne} onChange={(e) => setSuperieur(e.target.value)}>
            <option value="">— aucun (niveau GESTION de l'unité) —</option>
            {liens.filter((l) => l.id !== personne).map((l) => <option key={l.id} value={l.id}>{l.nom}</option>)}
          </select>
        </p>
        {erreur && <Message variante="erreur" testId="pla-tps-sup-erreur">{erreur}</Message>}
        <p><button type="submit" data-testid="pla-tps-sup-enregistrer" disabled={!personne}>Enregistrer</button></p>
      </form>
      {info && <p role="status" data-testid="pla-tps-sup-info">{info}</p>}
      {liens.some((l) => l.superieurId) && (
        <table className="eff-table-compacte" data-testid="pla-tps-sup-table">
          <caption>Qui relève de qui</caption>
          <thead><tr><th scope="col">Personne</th><th scope="col">Supérieur hiérarchique</th></tr></thead>
          <tbody>{liens.filter((l) => l.superieurId).map((l) => <tr key={l.id}><th scope="row">{l.nom}</th><td>{nomDe(l.superieurId)}</td></tr>)}</tbody>
        </table>
      )}
    </section>
  );
}
