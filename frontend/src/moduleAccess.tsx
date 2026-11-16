/**
 * Accès aux **modules activés** pour l'établissement courant.
 *
 * Les modules sont pilotés par le superadmin (`tenant_quotas.modules`) et déterminent ce que
 * l'établissement voit. Le calcul était jusqu'ici confiné à `App.tsx`, qui s'en servait pour
 * les menus : le tableau de bord, lui, affichait ses tuiles d'accès rapide sans rien vérifier.
 * Un module désactivé disparaissait donc du menu mais restait accessible depuis l'accueil.
 *
 * Ce contexte expose la **même** source à tout l'arbre, sans requête supplémentaire :
 * `App.tsx` fournit l'ensemble qu'il a déjà chargé, les pages le consomment.
 */
import { createContext, useCallback, useContext, useMemo, type ReactNode } from "react";

/** `null` = aucun filtrage — utilisateur global (superadmin) ou modules pas encore chargés. */
export type EnabledModules = Set<string> | null;

const Ctx = createContext<EnabledModules>(null);

/** Les pages dont **toutes** les vues de menu sont masquées pour l'établissement (Superadmin › Modules et accès). */
const CtxMasquees = createContext<ReadonlySet<string>>(new Set());

export function ModuleAccessProvider({ value, pagesMasquees, children }: { value: EnabledModules; pagesMasquees?: ReadonlySet<string>; children: ReactNode }) {
  const masquees = useMemo(() => pagesMasquees ?? new Set<string>(), [pagesMasquees]);
  return <Ctx.Provider value={value}><CtxMasquees.Provider value={masquees}>{children}</CtxMasquees.Provider></Ctx.Provider>;
}

/**
 * **La page est-elle ouverte à l'établissement ?** Son module est activé, et le superadmin n'a pas masqué toutes ses
 * vues du menu. C'est la règle du menu, lue telle quelle : l'aide s'en sert pour ne décrire que ce que l'organisme a.
 */
export function usePageMasquee(): (page: string) => boolean {
  const masquees = useContext(CtxMasquees);
  return useCallback((page: string) => masquees.has(page), [masquees]);
}

/**
 * Prédicat d'autorisation d'un module.
 *
 * Un code absent (fonctionnalité « cœur », toujours disponible) et l'absence de filtrage
 * renvoient `true` : le gating ne doit jamais masquer plus que ce que le superadmin a décidé.
 */
export function useModuleAllowed(): (moduleCode?: string) => boolean {
  const enabled = useContext(Ctx);
  // Même identité tant que l'ensemble ne change pas : le prédicat entre dans des dépendances
  // d'effets (`useAccesEnseignement`) — une fonction neuve à chaque rendu y relançait la lecture
  // des permissions à l'infini, et l'écran clignotait (onglets d'Absences, 16 sept. 2026).
  return useCallback((moduleCode?: string) => !moduleCode || enabled === null || enabled.has(moduleCode), [enabled]);
}
