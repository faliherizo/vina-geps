/**
 * **Aligner les unités sur l'organigramme** — lot R12 (décision D-3). L'organigramme de l'organisme
 * fait foi pour la structure : l'écran montre d'abord le plan (à créer, à modifier, identiques,
 * ignorées et pourquoi, absentes de l'organigramme), puis l'applique. Rien n'est fermé d'office.
 */
import { useState } from "react";
import { Message, Tiroir } from "../composants";
import { objet } from "../formeRecue";
import { useApiEffectifs } from "./api";

interface Plan {
  date: string; creer: Array<{ code: string; libelle: string; parent: string | null }>;
  modifier: Array<{ code: string; libelle: string; parent: string | null; avant: { libelle: string; parent: string | null } }>;
  identiques: string[]; ignorees: Array<{ code: string; motif: string }>; horsOrganigramme: string[];
}

export default function AlignementOrganigramme({ onAligne }: { onAligne: (message: string) => void }) {
  const { lire, envoyer, erreur } = useApiEffectifs();
  const [ouvert, setOuvert] = useState(false);
  const [plan, setPlan] = useState<Plan | null>(null);

  const ouvrir = async () => {
    setOuvert(true); setPlan(null);
    setPlan(objet<Plan>(await lire("/api/effectifs/liaisons/organisation")));
  };
  const appliquer = async () => {
    const r = objet<{ creees: number; modifiees: number }>(await envoyer("/api/effectifs/liaisons/organisation", {}));
    if (r) { setOuvert(false); onAligne(`Unités alignées sur l'organigramme : ${r.creees} créée(s), ${r.modifiees} modifiée(s).`); }
  };
  const vide = plan && !plan.creer.length && !plan.modifier.length;

  return (
    <>
      <button type="button" className="secondary" data-testid="ref-aligner" onClick={() => void ouvrir()}>Aligner sur l'organigramme</button>
      <Tiroir ouvert={ouvert} onFermer={() => setOuvert(false)} titre="Aligner les unités sur l'organigramme" testId="ref-alignement">
        {!plan
          ? (erreur ? <Message variante="erreur" testId="ref-alignement-erreur">{erreur}</Message> : <p role="status">Lecture de l'organigramme…</p>)
          : (
            <div data-testid="ref-alignement-plan">
              <p>Au {plan.date} : {plan.creer.length} unité(s) à créer, {plan.modifier.length} à modifier, {plan.identiques.length} identique(s).</p>
              {plan.creer.length > 0 && <><h3>À créer</h3><ul>{plan.creer.map((u) => <li key={u.code}>{u.code} — {u.libelle}{u.parent ? ` (sous ${u.parent})` : ""}</li>)}</ul></>}
              {plan.modifier.length > 0 && <><h3>À modifier</h3><ul>{plan.modifier.map((u) => <li key={u.code}>{u.code} : « {u.avant.libelle} » → « {u.libelle} »{u.avant.parent !== u.parent ? `, parent ${u.avant.parent ?? "aucun"} → ${u.parent ?? "aucun"}` : ""}</li>)}</ul></>}
              {plan.ignorees.length > 0 && <><h3>Ignorées</h3><ul>{plan.ignorees.map((u) => <li key={u.code}>{u.code} : {u.motif}</li>)}</ul></>}
              {plan.horsOrganigramme.length > 0 && <p>Absentes de l'organigramme (conservées, à fermer au besoin) : {plan.horsOrganigramme.join(", ")}.</p>}
              {erreur && <Message variante="erreur" testId="ref-alignement-erreur">{erreur}</Message>}
              {vide
                ? <p role="status" data-testid="ref-alignement-rien">Les unités suivent déjà l'organigramme : rien à appliquer.</p>
                : <button type="button" data-testid="ref-alignement-appliquer" onClick={() => void appliquer()}>Appliquer l'alignement</button>}
            </div>
          )}
      </Tiroir>
    </>
  );
}
