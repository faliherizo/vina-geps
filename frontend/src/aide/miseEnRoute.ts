/**
 * **La mise en route** : tout ce qu'il faut configurer, dans l'ordre, avant qu'une personne puisse se servir de
 * l'application — déclarer une absence, lire son horaire, pointer. Sans ces étapes, l'écran répond par un refus que
 * la personne ne sait pas lever seule (« votre compte n'est relié à aucune ressource planifiable »).
 *
 * Source unique : la section « Premiers pas » du guide la rend, et `scripts/tourner-mise-en-route.mjs` en tire la
 * vidéo (un chapitre par étape, filmé avec le compte qui la fait). L'ordre est celui du script de l'organisme par
 * défaut (`docs/ORGANISME-PAR-DEFAUT.md`) : une valeur ne vise qu'une valeur déjà en vigueur.
 */
import type { Page } from "../App";

/** Qui fait l'étape : il en découle le compte avec lequel la vidéo la filme. */
export type Acteur = "exploitation" | "administration" | "planification" | "personne";

export const LIBELLE_ACTEUR: Record<Acteur, string> = {
  exploitation: "L'exploitant de la plateforme (superadmin)",
  administration: "L'administration de l'organisme",
  planification: "Un planificateur de l'unité, ou l'administration",
  personne: "La personne elle-même",
};

export interface EtapeMiseEnRoute {
  /** Identifiant stable : ancre du guide, chapitre de la vidéo. */
  id: string;
  /** L'écran où l'étape se fait. */
  ecran: Page;
  titre: string;
  acteur: Acteur;
  /** Ce qu'on y fait, dans l'ordre des gestes ; la vidéo les dit tels quels. */
  gestes: readonly string[];
  /** Une étape qu'on peut sauter : l'application fonctionne sans elle, avec un comportement par défaut. */
  facultative?: boolean;
}

export const MISE_EN_ROUTE: readonly EtapeMiseEnRoute[] = [
  {
    id: "organisme",
    ecran: "superadmin",
    titre: "Créer l'organisme et lui accorder ses modules",
    acteur: "exploitation",
    gestes: [
      "Créer l'organisme avec son compte d'administration.",
      "Accorder les modules voulus : le socle effectifs, puis absences, planification, disponibilités, présence, temps, échanges.",
      "Un module non accordé n'apparaît pas au menu, et le serveur en refuse l'accès.",
    ],
  },
  {
    id: "referentiels",
    ecran: "usreferentiels",
    titre: "Créer les référentiels",
    acteur: "administration",
    gestes: [
      "Les unités d'abord, puis les corps d'emploi, et les titres d'emploi rattachés à leur corps.",
      "Ensuite les statuts d'emploi, les conventions, les natures de quart, les plages horaires et les types d'heures.",
      "Chaque valeur doit être en vigueur à la date où l'on s'en servira.",
    ],
  },
  {
    id: "codes-absence",
    ecran: "uscodesabsence",
    titre: "Créer les codes d'absence",
    acteur: "administration",
    gestes: [
      "Pour chaque code : payé ou non, catégorie de durée, et s'il peut être planifié d'avance.",
      "Au besoin, les titres d'emploi admissibles : un code réservé est refusé aux autres titres.",
    ],
  },
  {
    id: "milieux",
    ecran: "usmilieux",
    titre: "Créer les milieux physiques",
    acteur: "administration",
    gestes: ["Les casernes, centres et points de service, chacun rattaché à son unité : les quarts s'y déroulent."],
  },
  {
    id: "comptes",
    ecran: "admin",
    titre: "Créer les comptes et leurs rôles",
    acteur: "administration",
    gestes: [
      "Un compte par personne, dans l'onglet Comptes et rôles : employé, gestionnaire, direction, ressources humaines ou comptable.",
      "Le compte permet de se connecter ; il ne suffit pas encore pour être planifié.",
    ],
  },
  {
    id: "ressources",
    ecran: "usressources",
    titre: "Créer les ressources et les relier à leur compte",
    acteur: "planification",
    gestes: [
      "Pour chaque personne planifiable : matricule, nom, unité et corps d'emploi.",
      "Choisir son Compte utilisateur. C'est ce lien qui manque quand Mes absences répond : votre compte n'est relié à aucune ressource planifiable.",
    ],
  },
  {
    id: "perimetres",
    ecran: "usperimetres",
    titre: "Donner les périmètres de gestion",
    acteur: "administration",
    gestes: [
      "Donner à chaque gestionnaire le niveau Gestion sur son unité.",
      "Sans périmètre, les consoles s'ouvrent sur des listes vides, et les absences à approuver n'ont personne pour les décider.",
    ],
  },
  {
    id: "regles",
    ecran: "usregles",
    titre: "Publier les règles des effectifs",
    acteur: "administration",
    facultative: true,
    gestes: [
      "Par exemple : un solde insuffisant refuse l'absence, un préavis trop court exige l'approbation.",
      "Sans règle, une absence est acceptée d'office.",
    ],
  },
  {
    id: "periodes",
    ecran: "usperiodes",
    titre: "Ouvrir et publier une période",
    acteur: "planification",
    gestes: ["Créer la période de l'unité, collecter les disponibilités, puis la publier : les horaires deviennent visibles."],
  },
  {
    id: "equipages",
    ecran: "usequipages",
    titre: "Créer les quarts et attribuer les places",
    acteur: "planification",
    gestes: ["Créer les équipages de chaque jour, puis attribuer les places aux ressources : une absence libère ensuite ces places."],
  },
  {
    id: "absence",
    ecran: "mesabsences",
    titre: "La personne déclare son absence",
    acteur: "personne",
    gestes: [
      "Tout est en place : la personne ouvre Mes absences et déclare son absence.",
      "Elle est acceptée d'office, ou attend la décision de son gestionnaire selon les règles.",
    ],
  },
];

/** Le chapitre de la vidéo où commence chaque étape, en secondes ; écrit par le tournage à côté de la vidéo. */
export type ChapitresMiseEnRoute = Readonly<Record<string, number>>;

export const VIDEO_MISE_EN_ROUTE = {
  video: "/aide/videos/mise-en-route.webm",
  sousTitres: "/aide/videos/mise-en-route.fr.vtt",
  chapitres: "/aide/videos/mise-en-route.chapitres.json",
} as const;
