/**
 * **L'aide de chaque écran** — le catalogue des sections du guide, et l'écran qui mène à chacune.
 *
 * Le bouton « Aide sur cet écran » menait auparavant à une section déduite d'une carte
 * **partielle** : vingt-cinq écrans sur soixante-seize y figuraient, et les cinquante et un
 * autres retombaient en silence sur « Premiers pas ». Pour l'utilisateur, l'aide de son écran
 * n'existait pas — il n'apprenait pas que la carte était incomplète, il concluait que le bouton
 * ne marchait pas.
 *
 * Cette carte est donc **exhaustive par construction** : `Record<Page, SectionAide>` oblige le
 * compilateur à refuser un écran sans aide, et l'essai de cohérence refuse une section qui n'est
 * pas rendue par le guide. Ajouter un écran sans son aide ne compile plus.
 *
 * Elle vit hors de `App.tsx` pour être lisible et éprouvable séparément — c'est la seule façon
 * de vérifier « chaque écran a son aide » autrement qu'à l'œil.
 */
import type { Page } from "./App";
import { PAGE_MODULE } from "./modulesParPage";

/** Les sections du guide, dans l'ordre où elles paraissent (le sommaire en découle). */
export const SECTIONS_AIDE = [
  { id: "demarrage", titre: "Premiers pas : par où commencer ?" },
  { id: "notions", titre: "Notions clés (statuts, rôles, périmètres)" },
  { id: "mon-espace", titre: "Mon espace : horaire, disponibilités, échanges, temps" },
  { id: "organisation", titre: "Organisation : accès et rôles" },
  { id: "approbations", titre: "Approbations : règles, demandes, délégation" },
  { id: "effectifs-planification", titre: "Effectifs : horaires, quarts, disponibilités" },
  { id: "communication", titre: "Communication : messages, appels, listes de diffusion" },
  { id: "alertes", titre: "Communications cliniques : alertes, réponses, suivi" },
  { id: "exploitation", titre: "Exploitation : administration, sauvegardes, journal" },
  { id: "accessibilite", titre: "Accessibilité & navigation clavier" },
  { id: "notifications", titre: "Notifications sur le téléphone (application installable)" },
  { id: "integrations", titre: "Intégrations : annuaires, textos, antivirus, API" },
  { id: "soutien", titre: "Soutien : ouvrir un billet, suivre la réponse" },
  { id: "fiches", titre: "Écran par écran : aide et exemple de remplissage" },
] as const;

export type SectionAide = (typeof SECTIONS_AIDE)[number]["id"];

/** Les identifiants de section, pour les vérifications et la mise au point. */
export const IDS_SECTIONS: ReadonlySet<string> = new Set(SECTIONS_AIDE.map((s) => s.id));

/**
 * **L'aide de chaque écran.** Le type impose l'exhaustivité : un écran ajouté sans son aide
 * fait échouer la compilation, et c'est le seul garde-fou qui tient dans le temps.
 *
 * Plusieurs écrans partagent légitimement une section — les six écrans de l'approvisionnement
 * décrivent un même déroulement, et les séparer en six pages d'aide répéterait six fois le même
 * texte, qui divergerait ensuite.
 */
export const AIDE_PAR_PAGE: Record<Page, SectionAide> = {
  // Mon espace
  dashboard: "mon-espace", account: "mon-espace", rapports: "exploitation",
  monhoraire: "mon-espace", mesdisponibilites: "mon-espace", mesechanges: "mon-espace", monagph: "mon-espace",
  monpointage: "mon-espace", matemps: "mon-espace", mesabsences: "mon-espace",

  // Urgences-santé : une page par entrée de menu, et une seule section d'aide — le module se
  // lit d'un bloc, et dix-neuf pages d'aide répéteraient dix-neuf fois le même propos.
  uscomblement: "effectifs-planification", usequipages: "effectifs-planification", usdisponibilites: "effectifs-planification", usabsences: "effectifs-planification",
  usechanges: "effectifs-planification", usagph: "effectifs-planification", uspresence: "effectifs-planification", ustemps: "effectifs-planification",
  uspostes: "effectifs-planification", usperiodes: "effectifs-planification", usressources: "effectifs-planification", usquotas: "effectifs-planification",
  usreferentiels: "effectifs-planification", usregles: "effectifs-planification", usmilieux: "effectifs-planification", usperimetres: "effectifs-planification",
  usintegrations: "effectifs-planification", usretention: "effectifs-planification", usjournal: "effectifs-planification",
  usmessages: "communication", usappels: "communication", usdiffusion: "communication",
  alnouvelle: "alertes", alsuivi: "alertes", mesalertes: "alertes", alparametres: "alertes",

  // Encadrement et exploitation
  approvals: "approbations", access: "organisation",
  admin: "exploitation", superadmin: "exploitation",

  // Intégrations, notifications, aide, soutien
  apidocs: "integrations", integrations: "integrations",
  notifications: "notifications", help: "demarrage", soutien: "soutien",
};

/**
 * **Les sections que cet établissement doit lire**, selon les modules qu'il a souscrits.
 *
 * Rien de nouveau à tenir à jour : la visibilité se **déduit** de deux tables déjà exhaustives —
 * l'aide de chaque écran (ci-dessus) et le module de chaque écran (`modulesParPage.ts`). Une
 * troisième table « section → module » aurait divergé des deux autres au premier écran ajouté,
 * et personne ne l'aurait su : un guide ne fait pas échouer de construction quand il ment.
 *
 * La règle, en deux cas :
 *
 * - une section vers laquelle **aucun écran** ne renvoie est **générale** — accessibilité,
 *   déroulements types, vue d'ensemble : elle est toujours lisible ;
 * - sinon elle est lisible dès qu'**un** des écrans qui y renvoient est ouvert. « Un », et non
 *   « tous » : les six écrans de l'approvisionnement partagent une section, et en fermer un
 *   n'efface pas l'aide des cinq autres.
 *
 * `ouvert` est le prédicat de `useModuleAllowed` : sans filtrage (compte global, modules pas
 * encore lus) il rend `true`, et le guide reste entier — on ne cache jamais plus que ce que le
 * superadmin a décidé.
 */
export function sectionsVisibles(ouvert: (code?: string) => boolean): typeof SECTIONS_AIDE[number][] {
  const ouvertes = new Set<string>();
  for (const [page, section] of Object.entries(AIDE_PAR_PAGE)) {
    if (ouvert(PAGE_MODULE[page as Page])) ouvertes.add(section);
  }
  const visees = new Set<string>(Object.values(AIDE_PAR_PAGE));
  return SECTIONS_AIDE.filter((s) => !visees.has(s.id) || ouvertes.has(s.id));
}

/**
 * **Une ancre du guide** : une section, ou la fiche d'un écran (`fiche-<page>`). Le bouton « Aide
 * sur cet écran » mène à la fiche ; une adresse partagée (`?section=`) peut viser l'une ou l'autre.
 */
export function ancreConnue(id: string): boolean {
  return IDS_SECTIONS.has(id) || (id.startsWith("fiche-") && id.slice("fiche-".length) in AIDE_PAR_PAGE);
}

/** L'intitulé d'une section, pour l'annonce du bouton d'aide (« Aide sur cet écran, section … »). */
export function titreDeSection(id: string): string {
  return SECTIONS_AIDE.find((s) => s.id === id)?.titre ?? id;
}
