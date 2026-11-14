/**
 * **Correspondance page → module** — ce que l'abonnement d'un établissement ouvre.
 *
 * Elle vit dans son propre fichier, et non dans `App.tsx`, parce que **deux écrans en dépendent**
 * désormais : le menu, et le guide d'utilisation — un guide qui décrit les écrans d'un module
 * fermé enseigne des gestes que l'utilisateur ne peut pas faire, et lui fait chercher une entrée
 * de menu qui n'existe pas chez lui. La laisser dans `App.tsx` aurait obligé `aideParEcran.ts` à
 * en importer une **valeur**, alors qu'`App.tsx` importe déjà ce fichier : le cycle se serait
 * résolu à `undefined` au chargement, de façon silencieuse et dépendante de l'ordre des imports.
 *
 * Le type `Page` reste importé d'`App.tsx`, mais **en type seul** : il disparaît à la compilation
 * et ne crée donc aucun cycle à l'exécution.
 */
import type { Page } from "./App";

/**
 * Une page absente de cette table n'est **jamais** filtrée par module (menus « cœur » : tableau de
 * bord, mon espace, compte, aide, administration, superadmin…). Un menu dont le module est désactivé
 * pour le tenant (ou hors abonnement) **ne s'affiche pas**.
 */
export const PAGE_MODULE: Partial<Record<Page, string>> = {
  // Les dix-neuf pages du module (une par entrée de menu) : chacune porte le module qui la
  // gouverne, et une page dont le module est coupé ne s'ouvre pas même par son URL.
  uscomblement: "effectifs_planification",
  usequipages: "effectifs_planification",
  usdisponibilites: "effectifs_disponibilites",
  usabsences: "effectifs_absences",
  usechanges: "effectifs_echanges",
  usagph: "effectifs_agph",
  uspresence: "effectifs_presence",
  ustemps: "effectifs_temps",
  uspostes: "effectifs_planification",
  usperiodes: "effectifs_planification",
  usressources: "effectifs_planification",
  usquotas: "effectifs_planification",
  usreferentiels: "effectifs",
  usregles: "effectifs_regles",
  usmilieux: "effectifs",
  usperimetres: "effectifs",
  usintegrations: "effectifs_integrations",
  usretention: "effectifs",
  usjournal: "effectifs",
  usmessages: "effectifs_communication",
  usappels: "effectifs_communication",
  usdiffusion: "effectifs_communication",
  // L'horaire existe parce que la planification existe : c'est elle qui le gouverne, et non
  // les échanges — un employé a un horaire même si son organisme n'achète pas les échanges.
  monhoraire: "effectifs_planification",
  // Mon équipe vit de l'organigramme : le socle des effectifs suffit.
  monequipe: "effectifs",
  // Communications cliniques (ADR 0074) : un module à part, qui n'exige pas les effectifs.
  alnouvelle: "alertes", alsuivi: "alertes", mesalertes: "alertes", alparametres: "alertes",
  mesdisponibilites: "effectifs_disponibilites",
  mesechanges: "effectifs_echanges",
  monagph: "effectifs_agph",
  monpointage: "effectifs_presence",
  matemps: "effectifs_temps",
  mesabsences: "effectifs_absences",
  integrations: "integrations",
  approvals: "approvals",
  notifications: "notifications",
};
