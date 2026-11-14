/**
 * **Registre des pages du module Urgences-santé.**
 *
 * `App.tsx` ne connaît qu'une ligne de ce module : ce registre. Vingt-neuf `import` et vingt-neuf
 * conditions dans l'écran racine auraient fait de chaque ajout de page une modification de
 * l'ossature de l'application — et l'ossature se relit mal quand elle énumère.
 *
 * Les sept écrans **personnels** vivaient déjà à la racine de `src/` : ils y restent, et le
 * registre se contente de les nommer. Les déplacer n'aurait servi qu'à faire bouger des fichiers.
 *
 * L'essai `frontend/tests/urgences-sante-menu.test.ts` refuse qu'une entrée de menu n'ait pas sa
 * page, et l'inverse.
 */
import type { ComponentType } from "react";
import Monhoraire from "../MonHoraire";
import Mesdisponibilites from "../MesDisponibilites";
import Mesabsences from "../MesAbsences";
import Monagph from "../MonAgph";
import Monpointage from "../MonPointage";
import Matemps from "../MaFeuilleTemps";
import Mesechanges from "../MesEchanges";
import Uscomblement from "./pages/uscomblement";
import Usequipages from "./pages/usequipages";
import Usdisponibilites from "./pages/usdisponibilites";
import Usabsences from "./pages/usabsences";
import Usechanges from "./pages/usechanges";
import Usagph from "./pages/usagph";
import Uspresence from "./pages/uspresence";
import Ustemps from "./pages/ustemps";
import Uspostes from "./pages/uspostes";
import Usperiodes from "./pages/usperiodes";
import Usressources from "./pages/usressources";
import Usquotas from "./pages/usquotas";
import Usreferentiels from "./pages/usreferentiels";
import Usregles from "./pages/usregles";
import Usmilieux from "./pages/usmilieux";
import Usperimetres from "./pages/usperimetres";
import Usintegrations from "./pages/usintegrations";
import Usretention from "./pages/usretention";
import Usjournal from "./pages/usjournal";
// La communication du personnel : les pages reprennent les sections du module Santé Québec.
import { Usappels, Usdiffusion, Usmessages } from "./communication";

/** La page de chaque clé du menu. */
export const PAGES_US: Record<string, ComponentType> = {
  monhoraire: Monhoraire,
  mesdisponibilites: Mesdisponibilites,
  mesabsences: Mesabsences,
  monagph: Monagph,
  monpointage: Monpointage,
  matemps: Matemps,
  mesechanges: Mesechanges,
  uscomblement: Uscomblement,
  usequipages: Usequipages,
  usdisponibilites: Usdisponibilites,
  usabsences: Usabsences,
  usechanges: Usechanges,
  usagph: Usagph,
  uspresence: Uspresence,
  ustemps: Ustemps,
  uspostes: Uspostes,
  usperiodes: Usperiodes,
  usressources: Usressources,
  usquotas: Usquotas,
  usreferentiels: Usreferentiels,
  usregles: Usregles,
  usmilieux: Usmilieux,
  usperimetres: Usperimetres,
  usintegrations: Usintegrations,
  usretention: Usretention,
  usjournal: Usjournal,
  usmessages: Usmessages,
  usappels: Usappels,
  usdiffusion: Usdiffusion,
};

/** La page d'une clé du module, ou rien si la clé n'en est pas une. */
export function PageUrgencesSante({ cle }: { cle: string }) {
  const Page = PAGES_US[cle];
  return Page ? <Page /> : null;
}

export { CLES_US, ENCADREMENT, ENTREES_US, MODULE_US, NOTIONS_US, moduleDeLaPageUs } from "./menu";
export type { EntreeUs, NotionUs } from "./menu";
