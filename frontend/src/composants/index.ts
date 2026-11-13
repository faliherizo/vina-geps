/**
 * **Bibliothèque de composants** de la refonte visuelle (docs/refonte-visuelle/REFONTE-VISUELLE.md,
 * partie B.3). Un écran migré n'ajoute pas de logique : il en retire, en confiant sa forme à ces
 * composants. Les `data-testid` restent ceux de l'écran.
 */
export { default as Icone } from "./Icone";
export type { NomIcone } from "./Icone";
export { default as Message } from "./Message";
export type { VarianteMessage } from "./Message";
export { default as Badge, BadgeStatut, tonDuStatut } from "./Badge";
export type { TonBadge, FormeBadge } from "./Badge";
export { default as EtatVide } from "./EtatVide";
export { default as EnTeteEcran } from "./EnTeteEcran";
export { default as Onglets } from "./Onglets";
export type { Onglet } from "./Onglets";
export { default as BarreFiltres, libelleResultats } from "./BarreFiltres";
export { default as Tuile, libelleTendance } from "./Tuile";
export { default as Etapes } from "./Etapes";
export type { Etape } from "./Etapes";
export { default as MenuActions } from "./MenuActions";
export type { ActionMenu } from "./MenuActions";
export { default as Tableau } from "./Tableau";
export type { Colonne } from "./Tableau";
export { default as Tiroir } from "./Tiroir";
export { default as FilAriane } from "./FilAriane";
export { default as Avatar, initialesDe, teinteDe } from "./Avatar";
export { default as PageMaquette } from "./PageMaquette";
export { default as SommaireSections, allerALaSection } from "./SommaireSections";
export type { SectionSommaire } from "./SommaireSections";
export { default as Calendrier, casesDuMois, ymd as dateIso, JOURS_SEMAINE } from "./Calendrier";
export type { JourCalendrier } from "./Calendrier";
