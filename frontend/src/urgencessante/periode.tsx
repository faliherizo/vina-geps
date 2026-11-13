/**
 * **La période courante**, partagée par les trois écrans qui en dépendent.
 *
 * Comblement, quarts et disponibilités se lisent **dans une période** : sans elle, ils n'ont rien
 * à montrer. Quand ces trois vues étaient des onglets d'un même écran, le sélecteur vivait dans
 * l'écran hôte et les onglets se le partageaient sans y penser. Une page par entrée de menu
 * supprime l'hôte — il fallait donc décider où va le sélecteur.
 *
 * Il va **ici**, et non dans chaque page : trois copies d'un même chargement finiraient par
 * diverger (l'une filtrant les périodes closes, l'autre non), et l'on ne verrait pas laquelle a
 * raison. Chaque page l'appelle et rend le champ là où elle le veut.
 */
import { useCallback, useEffect, useState } from "react";
import { useApiEffectifs } from "../effectifs/api";
import type { Periode } from "../effectifs/planification/types";

export interface PeriodeCourante {
  periodes: Periode[];
  periodeId: string;
  periode: Periode | null;
  choisir: (id: string) => void;
  recharger: () => Promise<void>;
}

export function usePeriodeCourante(): PeriodeCourante {
  const { lire } = useApiEffectifs();
  const [periodes, setPeriodes] = useState<Periode[]>([]);
  const [periodeId, setPeriodeId] = useState("");

  const recharger = useCallback(async () => {
    const p = await lire<Periode[]>("/api/effectifs/planification/periodes");
    if (!p) return;
    setPeriodes(p);
    // La période retenue d'abord, sinon la première qui n'est pas close : on arrive sur celle où
    // il y a quelque chose à faire, et non sur la plus ancienne.
    setPeriodeId((id) => (id && p.some((x) => x.id === id) ? id : p.find((x) => x.etat !== "CLOSE")?.id ?? p[0]?.id ?? ""));
  }, [lire]);
  useEffect(() => { void recharger(); }, [recharger]);

  return {
    periodes, periodeId, periode: periodes.find((p) => p.id === periodeId) ?? null,
    choisir: setPeriodeId, recharger,
  };
}

/** Le champ de choix de période, tel que les écrans le posent sous leur titre. */
export function ChampPeriode({ ctx }: { ctx: PeriodeCourante }) {
  return (
    <p>
      <label htmlFor="pla-periode">Période</label>{" "}
      <select id="pla-periode" data-testid="pla-periode" value={ctx.periodeId}
              onChange={(e) => ctx.choisir(e.target.value)}>
        {ctx.periodes.length === 0 && <option value="">Aucune période</option>}
        {ctx.periodes.map((p) => <option key={p.id} value={p.id}>{p.nom} ({p.debut} au {p.fin})</option>)}
      </select>
    </p>
  );
}
