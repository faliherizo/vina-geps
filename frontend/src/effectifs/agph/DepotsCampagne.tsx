/**
 * **Disponibilités et budget d'une campagne AGPH, côté planification** (E-CH-1).
 *
 * Le service de planification fixe le budget d'heures hebdomadaires de la campagne, voit qui a
 * déposé sa semaine type et ce que les règles en ont dit, et le total des heures offertes rapporté
 * au budget. Il saisit au besoin la semaine type d'une personne qui n'a pas d'accès (reçue au
 * téléphone) : le même éditeur, les mêmes règles.
 */
import { useCallback, useEffect, useState } from "react";
import { Badge, EtatVide, Message, Tableau, Tiroir, type Colonne } from "../../composants";
import { liste, objet } from "../../formeRecue";
import { useApiEffectifs } from "../api";
import { FormulaireModal } from "../../Modal";
import { BadgeConformite } from "../disponibilites/Motifs";
import SemaineType from "./SemaineType";
import { depotOuvert, type DepotCampagne, type DepotsCampagne, type PlageType, type VueCampagne } from "./types";

type Ligne = DepotsCampagne["lignes"][number];

const COLONNES: ReadonlyArray<Colonne<Ligne>> = [
  { id: "rang", libelle: "Rang", numerique: true, tri: (l) => l.rang }, { id: "nom", libelle: "Personne", tri: (l) => l.nom },
  { id: "depot", libelle: "Dépôt" }, { id: "heures", libelle: "Heures par semaine", numerique: true }, { id: "motifs", libelle: "Motifs relevés" },
  { id: "actions", libelle: "Actions", actions: true },
];

export default function DepotsCampagnePanneau({ campagne, onParametree }: { campagne: VueCampagne; onParametree: () => Promise<void> }) {
  const { lire, envoyer, envoyerBrut, erreur, setErreur } = useApiEffectifs();
  const [donnees, setDonnees] = useState<DepotsCampagne | null>(null);
  const [budget, setBudget] = useState(campagne.budgetHeuresSemaine ? String(campagne.budgetHeuresSemaine) : "");
  const [info, setInfo] = useState("");
  const [saisie, setSaisie] = useState<Ligne | null>(null);
  const [erreurSaisie, setErreurSaisie] = useState("");

  const charger = useCallback(async () => {
    setDonnees(objet<DepotsCampagne>(await lire<DepotsCampagne>(`/api/effectifs/agph/campagnes/${campagne.id}/disponibilites`)));
  }, [campagne.id, lire]);
  useEffect(() => { void charger(); }, [charger, campagne.etat]);

  const fixerBudget = async (e: React.FormEvent<HTMLFormElement>, fermer: () => void) => {
    e.preventDefault();
    setInfo("");
    const c = await envoyer(`/api/effectifs/agph/campagnes/${campagne.id}/parametres`, { budgetHeuresSemaine: budget === "" ? null : Number(budget) }, "PUT");
    if (c) { fermer(); setInfo(budget === "" ? "Budget retiré : aucune règle budgétaire ne s'applique." : `Budget fixé à ${budget} h par semaine.`); await charger(); await onParametree(); }
  };
  const deposerPour = async (plages: PlageType[]): Promise<DepotCampagne | null> => {
    if (!saisie) return null;
    const r = await envoyerBrut(`/api/effectifs/agph/campagnes/${campagne.id}/disponibilites/${saisie.ressourceId}`, { plages }, "PUT");
    setErreur("");
    setErreurSaisie(r.ok ? "" : r.corps.errors?.join(", ") || `Erreur ${r.statut}`);
    if (!r.ok) return null;
    setInfo(`Disponibilités de ${saisie.nom} enregistrées.`);
    await charger();
    return r.corps as DepotCampagne;
  };

  const lignes = liste<Ligne>(donnees?.lignes);
  const depasse = donnees?.tauxBudget !== null && (donnees?.tauxBudget ?? 0) > 100;
  return (
    <section aria-labelledby="pla-agph-dispo-titre" data-testid="pla-agph-dispo">
      <h4 id="pla-agph-dispo-titre">Disponibilités déposées et budget</h4>
      {erreur && !saisie && <Message variante="erreur" testId="pla-agph-dispo-erreur">{erreur}</Message>}
      {info && <Message variante="succes" testId="pla-agph-dispo-info">{info}</Message>}
      {/* Le budget **modifie** la campagne : un dialogue. Le résumé juste en dessous dit le budget
          en vigueur et son taux ; un champ éditable posé au-dessus montrait une valeur qui n'était
          peut-être pas encore enregistrée, à côté de celle qui l'était. */}
      {campagne.etat !== "CLOSE" && (
        <div className="eff-actions">
          <FormulaireModal libelle={donnees?.budgetHeuresSemaine ? "Modifier le budget" : "Fixer le budget"} titre={`Budget d'heures — ${campagne.nom}`} testId="pla-agph-budget-edition" size="sm" icone="✎" className="secondary">
            {(fermer) => (
              <form onSubmit={(e) => void fixerBudget(e, fermer)}>
                <p>
                  <label htmlFor="pla-agph-budget">Budget d'heures par semaine (vide = non fixé)</label>
                  <input id="pla-agph-budget" type="number" min={1} step="0.5" data-testid="pla-agph-budget" value={budget} onChange={(e) => setBudget(e.target.value)} />
                </p>
                {erreur && <Message variante="erreur" testId="pla-agph-budget-erreur">{erreur}</Message>}
                <p className="eff-actions"><button type="submit" data-testid="pla-agph-budget-fixer">Fixer le budget</button></p>
              </form>
            )}
          </FormulaireModal>
        </div>
      )}
      {donnees && (
        <>
          <p data-testid="pla-agph-dispo-resume">
            {donnees.deposes} dépôt(s) sur {lignes.length} personne(s) · {Number(donnees.heuresCampagne ?? 0).toLocaleString("fr-CA")} h offertes par semaine
            {donnees.budgetHeuresSemaine ? ` · budget ${donnees.budgetHeuresSemaine.toLocaleString("fr-CA")} h (${(donnees.tauxBudget ?? 0).toLocaleString("fr-CA")} %)` : " · budget non fixé"}
            {depasse && <> <Badge ton="avertissement">Au-delà du budget</Badge></>}
          </p>
          <Tableau colonnes={COLONNES} lignes={lignes} legende="Disponibilités déposées dans la campagne" testId="pla-agph-dispo-table"
            vide={<EtatVide titre="Rangs non encore figés" compact>Les personnes déposent leurs disponibilités une fois la campagne ouverte.</EtatVide>}
            rendre={(l) => (
              <tr key={l.ressourceId} data-testid={`pla-agph-dispo-${l.rang}`}>
                <td className="col-numerique">{l.rang}</td>
                <td>{l.nom} ({l.matricule})</td>
                <td><BadgeConformite conformite={l.depot?.conformite ?? null} /></td>
                <td className="col-numerique">{l.depot ? l.depot.heures.toLocaleString("fr-CA") : "—"}</td>
                <td>{liste<{ message: string }>(l.depot?.motifs).map((m) => m.message).join(" ; ") || "—"}</td>
                <td>
                  {depotOuvert(campagne.etat) && (
                    <button type="button" className="secondary" data-testid={`pla-agph-dispo-saisir-${l.rang}`} onClick={() => { setErreurSaisie(""); setSaisie(l); }}>
                      Saisir ses disponibilités<span className="sr-only"> — {l.nom}</span>
                    </button>
                  )}
                </td>
              </tr>
            )} />
        </>
      )}
      <Tiroir ouvert={!!saisie} onFermer={() => setSaisie(null)} titre={saisie ? `Semaine type de ${saisie.nom}` : "Semaine type"} testId="pla-agph-dispo-saisie">
        {saisie && (
          <SemaineType key={saisie.ressourceId} prefixe="pla-agph-dispo-saisie" depot={saisie.depot} erreur={erreurSaisie} libelleBouton="Enregistrer pour cette personne"
            onEnregistrer={deposerPour} />
        )}
      </Tiroir>
    </section>
  );
}
