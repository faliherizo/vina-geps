/**
 * Les valeurs en vigueur de plusieurs référentiels, chargées une fois par écran. Toute liste de
 * choix métier de la planification en vient — statuts, corps, natures, plages, unités, milieux —
 * jamais d'un tableau écrit dans l'écran.
 */
import { useEffect, useState } from "react";
import { useApiEffectifs } from "../api";
import type { Valeur } from "../Referentiels";

export interface Milieu { id: string; code: string; libelle: string; uniteCode: string }

export function useReferentiels(types: readonly string[]): { refs: Record<string, Valeur[]>; milieux: Milieu[] } {
  const { lire } = useApiEffectifs();
  const [refs, setRefs] = useState<Record<string, Valeur[]>>({});
  const [milieux, setMilieux] = useState<Milieu[]>([]);
  const cle = types.join(",");
  useEffect(() => {
    void Promise.all(cle.split(",").map(async (t) => [t, (await lire<Valeur[]>(`/api/effectifs/referentiels/${t}`)) ?? []] as const))
      .then((paires) => setRefs(Object.fromEntries(paires)));
    void lire<Milieu[]>("/api/effectifs/milieux").then((m) => setMilieux(m ?? []));
  }, [lire, cle]);
  return { refs, milieux };
}

/** Le libellé d'un code dans un référentiel chargé, ou le code lui-même s'il manque. */
export const libelleDe = (liste: readonly Valeur[] | undefined, code: string | null | undefined): string =>
  (code ? liste?.find((v) => v.code === code)?.libelleFr ?? code : "—");
