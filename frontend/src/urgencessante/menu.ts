/**
 * **Le menu du module Urgences-santé** (AO 26-015-DO) — une page par entrée.
 *
 * La structure et l'ordre viennent des douze planches (`docs/urgences-sante/maquettes/svg/`),
 * colonne « MENU PRINCIPAL » : Mon horaire · Disponibilités · Absences · Confection AGPH ·
 * Gestion des quarts · Pointage et présence · Feuilles de temps · Demandes et échanges ·
 * Administration des effectifs.
 *
 * **Chaque entrée est une page à son adresse** (`/<base>/<clé>`), et non un onglet d'un écran
 * unique : un lien se partage, le retour arrière revient à l'écran précédent, et un écran se
 * charge seul. Les douze onglets de l'ancienne « Planification des effectifs » et les sept de
 * l'ancien « Paramétrage » sont donc devenus dix-neuf pages.
 *
 * **Chaque notion réunit la vue de l'employé et celle de l'encadrement** d'un même sujet : on
 * cherche un sujet, pas le rôle sous lequel on le regarde. Les rôles décident ensuite de ce qui
 * s'affiche — l'employée ne voit pas la console, et le menu ne montre que ce qu'elle peut ouvrir.
 *
 * Ce fichier ne contient que des identifiants, des rôles et des codes de module. Les libellés
 * viennent de la traduction (`nav.<clé>`), la page vient du registre (`index.tsx`), et la
 * visibilité réelle est décidée par `App.tsx`.
 */

/** Les rôles qui encadrent : ils planifient, comblent, approuvent. */
export const ENCADREMENT = ["admin", "superadmin", "director", "manager"] as const;
/** Tout compte planifiable de l'établissement — les écrans personnels. */
export const TOUS = ["admin", "superadmin", "director", "manager", "rh", "employee"] as const;

export interface EntreeUs {
  /** Clé de page : elle sert d'URL, d'identifiant d'essai (`nav-<clé>`) et de clé de traduction. */
  cle: string;
  /** Icône décorative : elle accompagne le libellé, elle ne le remplace pas. */
  icone: string;
  roles: readonly string[];
  /** Le module qui gouverne la page. Coupé, l'entrée disparaît et l'URL ne s'ouvre plus. */
  module: string;
}

export interface NotionUs {
  /** Clé de la notion ; `null` pour une entrée directement sous la section (un lien simple). */
  cle: string | null;
  icone: string;
  entrees: EntreeUs[];
}

/** Le socle du module : coupé, toute la section disparaît, en-tête compris. */
export const MODULE_US = "effectifs";

export const NOTIONS_US: NotionUs[] = [
  // Une entrée seule est un lien direct, pas une notion : un intertitre qui ne regroupe rien
  // n'aide pas à se repérer.
  { cle: null, icone: "📅", entrees: [
    { cle: "monhoraire", icone: "📅", roles: TOUS, module: "effectifs_planification" },
  ] },
  { cle: "usdisponibilitesnotion", icone: "🗓️", entrees: [
    { cle: "mesdisponibilites", icone: "🗓️", roles: TOUS, module: "effectifs_disponibilites" },
    { cle: "usdisponibilites", icone: "📋", roles: ENCADREMENT, module: "effectifs_disponibilites" },
  ] },
  { cle: "usabsencesnotion", icone: "🌴", entrees: [
    { cle: "mesabsences", icone: "🌴", roles: TOUS, module: "effectifs_absences" },
    { cle: "usabsences", icone: "📋", roles: ENCADREMENT, module: "effectifs_absences" },
  ] },
  // Addenda 8, M-02 : le choix annuel des congés, par formulaire, octroyé par ancienneté sous un plafond par groupe.
  { cle: "uscongesnotion", icone: "🏖️", entrees: [
    { cle: "mescongesannuels", icone: "🏖️", roles: TOUS, module: "effectifs_absences" },
    { cle: "uscongesannuels", icone: "📋", roles: ENCADREMENT, module: "effectifs_absences" },
  ] },
  { cle: "usagphnotion", icone: "🗂️", entrees: [
    { cle: "monagph", icone: "🗂️", roles: TOUS, module: "effectifs_agph" },
    { cle: "usagph", icone: "📋", roles: ENCADREMENT, module: "effectifs_agph" },
  ] },
  // Gestion des quarts : le comblement d'abord — la question du matin est « quels véhicules ne
  // sortiront pas ? ».
  { cle: "usquarts", icone: "🚑", entrees: [
    { cle: "uscomblement", icone: "🚑", roles: ENCADREMENT, module: "effectifs_planification" },
    { cle: "usequipages", icone: "🚐", roles: ENCADREMENT, module: "effectifs_planification" },
    { cle: "uspostes", icone: "📌", roles: ENCADREMENT, module: "effectifs_planification" },
    { cle: "usperiodes", icone: "📆", roles: ENCADREMENT, module: "effectifs_planification" },
    { cle: "usressources", icone: "👥", roles: ENCADREMENT, module: "effectifs_planification" },
    { cle: "usquotas", icone: "⚖️", roles: ENCADREMENT, module: "effectifs_planification" },
  ] },
  { cle: "uspointagenotion", icone: "⏱️", entrees: [
    { cle: "monpointage", icone: "⏱️", roles: TOUS, module: "effectifs_presence" },
    { cle: "uspresence", icone: "📋", roles: ENCADREMENT, module: "effectifs_presence" },
  ] },
  { cle: "ustempsnotion", icone: "🧾", entrees: [
    { cle: "matemps", icone: "🧾", roles: TOUS, module: "effectifs_temps" },
    { cle: "ustemps", icone: "📋", roles: ENCADREMENT, module: "effectifs_temps" },
  ] },
  { cle: "usechangesnotion", icone: "🔁", entrees: [
    { cle: "mesechanges", icone: "🔁", roles: TOUS, module: "effectifs_echanges" },
    { cle: "usechanges", icone: "📋", roles: ENCADREMENT, module: "effectifs_echanges" },
  ] },
  // Addenda 8, M-08 : la liste de temps supplémentaire, les événements, la formation et les autres demandes opérationnelles.
  { cle: "usdemandesnotion", icone: "📝", entrees: [
    { cle: "mesdemandes", icone: "📝", roles: TOUS, module: "effectifs_planification" },
    { cle: "usdemandes", icone: "📋", roles: ENCADREMENT, module: "effectifs_planification" },
  ] },
  // Mon équipe (comme dans Nexu VINA) : la ligne hiérarchique du gestionnaire, ses feuilles à approuver, sa suppléance.
  { cle: null, icone: "👥", entrees: [
    { cle: "monequipe", icone: "👥", roles: [...ENCADREMENT, "rh"], module: "effectifs" },
  ] },
  /**
   * **La communication du personnel** (AO 26-015-DO : se joindre entre collègues).
   *
   * La fonction vient du module Santé Québec et n'y est pas recopiée : les pages d'ici reprennent
   * les mêmes sections (`urgencessante/communication.tsx`). Son module se vend à part, et il
   * **exige le socle des horaires cliniques** au catalogue — c'est lui qui sert l'API des
   * conversations, des appels et des listes.
   */
  { cle: "uscommunication", icone: "✉️", entrees: [
    { cle: "usmessages", icone: "💬", roles: TOUS, module: "effectifs_communication" },
    { cle: "usappels", icone: "📞", roles: TOUS, module: "effectifs_communication" },
    { cle: "usdiffusion", icone: "📇", roles: TOUS, module: "effectifs_communication" },
  ] },
  // Le paramétrage : il règle la façon dont tout le reste se comporte.
  { cle: "usadministration", icone: "🛠️", entrees: [
    // Les référentiels décident pour tous : leur écran est réservé à l'administration (le serveur exige effectifs.configure).
    { cle: "usreferentiels", icone: "📚", roles: ["admin", "superadmin"], module: "effectifs" },
    // Les codes d'absence sont un référentiel ; leur page à leur nom les rend trouvables (les tenir exige effectifs.configure).
    { cle: "uscodesabsence", icone: "🌴", roles: ["admin", "superadmin"], module: "effectifs_absences" },
    { cle: "usregles", icone: "⚖️", roles: ENCADREMENT, module: "effectifs_regles" },
    { cle: "usmilieux", icone: "🏥", roles: ENCADREMENT, module: "effectifs" },
    { cle: "usperimetres", icone: "🔐", roles: ENCADREMENT, module: "effectifs" },
    { cle: "usintegrations", icone: "🔌", roles: ENCADREMENT, module: "effectifs_integrations" },
    { cle: "usretention", icone: "🗄️", roles: ENCADREMENT, module: "effectifs" },
    { cle: "usjournal", icone: "📜", roles: ENCADREMENT, module: "effectifs" },
  ] },
];

/** Toutes les entrées, à plat — l'ordre du menu. */
export const ENTREES_US: EntreeUs[] = NOTIONS_US.flatMap((n) => n.entrees);

/** Les clés de page du module. */
export const CLES_US: string[] = ENTREES_US.map((e) => e.cle);

/** Le module qui gouverne une page du module, ou `undefined` si la page n'en est pas. */
export function moduleDeLaPageUs(cle: string): string | undefined {
  return ENTREES_US.find((e) => e.cle === cle)?.module;
}
