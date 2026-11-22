/**
 * **Une fiche d'aide par écran** : à quoi il sert, qui s'en sert, comment s'y prendre, et **un
 * exemple de remplissage** champ par champ, avec les libellés réels de l'écran.
 *
 * C'est la source unique : le guide (`Help.tsx`) la rend, le bouton « Aide sur cet écran » y mène
 * directement (`?section=fiche-<page>`), et `scripts/generer-guide-ecrans.cjs` en tire le document
 * `docs/urgences-sante/GUIDE-ECRANS.md`. Le type `Record<Page, Fiche>` fait échouer la compilation
 * si un écran est ajouté sans sa fiche — le seul garde-fou qui tienne dans le temps.
 *
 * Les exemples sont ceux de l'organisme de démonstration (unité OPS, Caserne 12, Julie Roy, Marc
 * Dubé, Claire Nadeau) : on peut les rejouer tels quels.
 */
import type { Page } from "../App";

/**
 * Une chose à mettre en place **avant** que l'écran serve : qui le fait, dans quel écran, avec quelles
 * données. `ecran` désigne l'écran où cela se fait, quand il est dans l'application : le guide y mène.
 */
export interface Prealable {
  texte: string;
  ecran?: Page;
}

export interface Fiche {
  titre: string;
  /** À quoi sert l'écran, en une phrase. */
  objet: string;
  /** Qui l'ouvre (rôle ou profil). */
  qui: string;
  /**
   * **Avant de commencer**, dans l'ordre où cela se fait. Tirés des gardes du serveur (module, capacité,
   * périmètre, ressource de l'acteur) et de l'ordre de semis des essais : un écran dont un préalable
   * manque répond par un refus que l'utilisateur ne sait pas lever seul (« votre compte n'est relié à
   * aucune ressource planifiable ») — la fiche doit dire d'avance qui doit faire quoi, et où.
   */
  prealables: readonly Prealable[];
  /** Le déroulement, dans l'ordre des gestes. */
  etapes: readonly string[];
  /** Un remplissage type : [libellé du champ tel qu'à l'écran, valeur saisie]. */
  exemple: ReadonlyArray<readonly [string, string]>;
  /** Ce qui se passe une fois le geste fait. */
  resultat: string;
  /** Le piège courant, ou la règle qu'on oublie. */
  attention?: string;
}

/** L'identifiant d'ancre d'une fiche dans le guide. */
export const idFiche = (page: string): string => `fiche-${page}`;

const p = (texte: string, ecran?: Page): Prealable => (ecran ? { texte, ecran } : { texte });

/**
 * Les modules d'un écran : ceux que la garde du serveur exige, plus le socle « effectifs » pour les
 * écrans du menu des effectifs (le menu les range sous ce module). Le superadmin les accorde ; l'écran
 * Organismes n'est pas lié, l'administration d'un organisme ne l'ouvre pas.
 */
const modules = (...codes: string[]): Prealable => {
  const noms = codes.map((c) => "« " + c + " »").join(" et ");
  return p(
    `L'exploitant de la plateforme (superadmin) accorde à l'organisme ${codes.length > 1 ? "les modules" : "le module"} ${noms} `
    + "(Administration › Superadmin, modules de l'organisme) et laisse cette vue au menu : sans cela, l'entrée n'apparaît pas au menu et le serveur refuse l'accès.",
  );
};

const compte = (roles: string): Prealable => p(
  `L'administration de l'organisme crée le compte de chaque personne dans Administration › Administration, onglet « Comptes et rôles ». Rôle attendu : ${roles}.`,
  "admin",
);

const REFERENTIELS_SOCLE = p(
  "L'administration crée les référentiels en vigueur à la date voulue (Administration › Paramètres des effectifs › Référentiels) : "
  + "l'unité et le corps d'emploi, obligatoires pour toute ressource ; au besoin le titre d'emploi (rattaché à son corps) et le statut d'emploi.",
  "usreferentiels",
);

/** Ce que veut dire « votre compte n'est relié à aucune ressource planifiable » (`LectureRessources.exigerDeLActeur`). */
const RESSOURCE_LIEE = p(
  "Un planificateur (niveau Gestion sur l'unité) ou l'administration crée la ressource de la personne dans Menu principal › Gestion des quarts › Ressources "
  + "— matricule, nom, unité, corps d'emploi — et choisit son « Compte utilisateur ». C'est ce lien qui manque quand l'écran répond "
  + "« votre compte n'est relié à aucune ressource planifiable : demandez-le à votre planificateur ».",
  "usressources",
);

const perimetre = (niveau: string, effet: string): Prealable => p(
  `L'administration donne au compte de l'encadrement un périmètre sur l'unité, au niveau ${niveau} (Administration › Paramètres des effectifs › Périmètres) : ${effet} `
  + "Un compte d'administration voit toutes les unités sans périmètre.",
  "usperimetres",
);

const PERIODE_PUBLIEE = p(
  "Un planificateur publie les postes de l'unité (Gestion des quarts › Postes), crée la période (Gestion des quarts › Périodes), la passe en confection, "
  + "génère ses quarts, attribue les places aux personnes (Quarts et équipages ou Comblement), puis publie la période : seuls les quarts d'une période publiée comptent ici.",
  "usperiodes",
);

const ENCADREMENT_EFFECTIFS = "administration, direction ou gestionnaire (capacité « effectifs.manage »)";
const CONFIGURATION_EFFECTIFS = "administration de l'organisme (capacité « effectifs.configure »)";

export const FICHES: Record<Page, Fiche> = {
  // ── Menu principal ────────────────────────────────────────────────────────────────────────
  dashboard: {
    titre: "Tableau de bord",
    objet: "Lire sa journée : pour l'encadrement, la couverture des quarts, les absences à traiter, les demandes en attente et les pointages à approuver ; pour l'employé, ses sept prochains jours.",
    qui: "Tous les comptes ; la vue gestionnaire s'ouvre à l'administration, à la direction et aux planificateurs.",
    prealables: [
      compte("tout rôle pour la vue personnelle ; administration, direction ou gestionnaire pour la vue d'encadrement"),
      p("Les tuiles lisent les modules des effectifs accordés à l'organisme (« effectifs_planification », « effectifs_absences », « effectifs_temps »…) : un module absent affiche « — », jamais zéro."),
      perimetre("Lecture au moins", "les chiffres d'un gestionnaire ne comptent que les unités de son périmètre."),
      RESSOURCE_LIEE,
      PERIODE_PUBLIEE,
    ],
    etapes: [
      "Lire les quatre tuiles : chacune est un bouton qui ouvre l'onglet correspondant.",
      "Dans « Couverture des quarts », les quarts à combler sont en tête ; « Combler » mène à l'écran de comblement.",
      "Dans les autres onglets, « Décider », « Valider » ou « Examiner » mène à l'écran où l'on agit.",
    ],
    exemple: [["Tuile « Quarts aujourd'hui »", "24 — dont « 8 place(s) à combler »"], ["Onglet", "Couverture des quarts"], ["Ligne", "14:00–22:00 · Nord · 1 / 3 · Sous le minimum → Combler"]],
    resultat: "Rien ne s'écrit ici : le tableau de bord montre et oriente, l'action se fait sur l'écran visé.",
    attention: "Un chiffre « — » veut dire « non lu » (module non souscrit, ou rôle qui ne le consulte pas) : ce n'est pas zéro.",
  },
  monhoraire: {
    titre: "Mon horaire",
    objet: "Consulter ses quarts publiés : le mois en calendrier, ou la semaine en liste.",
    qui: "Toute personne planifiable (compte relié à une ressource).",
    prealables: [
      modules("effectifs", "effectifs_planification"),
      REFERENTIELS_SOCLE,
      compte("n'importe lequel"),
      RESSOURCE_LIEE,
      PERIODE_PUBLIEE,
    ],
    etapes: [
      "Le calendrier du mois s'ouvre par défaut ; « Mois suivant » et « Mois précédent » changent le mois.",
      "« Liste de la semaine » donne la table jour, quart, site, poste et statut ; « Semaine précédente » sert à vérifier sa feuille de temps.",
      "Un quart visé par un échange ouvert porte la mention « Échange en cours ».",
    ],
    exemple: [["Affichage", "Calendrier du mois"], ["Case du lundi 16", "06:00–14:00 Caserne 12 · PARA"]],
    resultat: "Lecture seule. Pour céder un quart : « Proposer un échange », qui mène à Mes échanges.",
    attention: "« Votre compte n'est relié à aucune ressource planifiable » : demander au planificateur de relier le compte dans Ressources.",
  },
  mesdisponibilites: {
    titre: "Mes disponibilités",
    objet: "Déposer ses disponibilités pour une période dont la collecte est ouverte.",
    qui: "Toute personne planifiable.",
    prealables: [
      modules("effectifs", "effectifs_disponibilites"),
      REFERENTIELS_SOCLE,
      compte("n'importe lequel"),
      RESSOURCE_LIEE,
      p("Un planificateur crée la période de l'unité de la personne (Gestion des quarts › Périodes) puis « Ouvre la collecte » avec sa fin : hors collecte, le serveur répond « la collecte de cette période n'est pas ouverte : les disponibilités ne se déposent plus ».", "usperiodes"),
      p("Facultatif : l'administration publie des règles du contexte DISPONIBILITE (Règles des effectifs, module « effectifs_regles ») ; sans règle, tout dépôt est accepté.", "usregles"),
    ],
    etapes: [
      "Choisir la « Période » (seules celles en collecte apparaissent).",
      "« Ajouter une plage » pour chaque jour : jour, début, fin, et le type « Disponibilité ».",
      "Enregistrer : les règles jugent le dépôt aussitôt ; un refus nomme ce qu'il faut corriger.",
    ],
    exemple: [["Période", "Semaine du 16 novembre"], ["Jour", "lundi 16"], ["Début", "06:00"], ["Fin (avant le début = passe minuit)", "18:00"], ["Disponibilité", "Disponible"]],
    resultat: "Le dépôt rejoint le bassin de candidats de la période ; « À surveiller dans votre dépôt » liste les avertissements.",
    attention: "Une fin avant le début (22:00 → 06:00) veut dire que la plage passe minuit.",
  },
  mesabsences: {
    titre: "Mes absences d'effectifs",
    objet: "Déclarer une absence sur des quarts prévus, et suivre sa décision.",
    qui: "Toute personne planifiable.",
    prealables: [
      modules("effectifs", "effectifs_absences"),
      REFERENTIELS_SOCLE,
      p("L'administration crée les codes d'absence (Administration › Paramètres des effectifs › Codes d'absence) avec leurs attributs : comportement (payée ou non), catégorie de durée, « Peut être planifiée d'avance », titres d'emploi admissibles. Le code doit être en vigueur au premier jour ; un code non planifiable ne se déclare pas d'avance ; un code réservé à certains titres est refusé aux autres.", "uscodesabsence"),
      compte("n'importe lequel"),
      RESSOURCE_LIEE,
      p("Pour qu'une absence « à approuver » soit décidée : l'administration donne à un gestionnaire un périmètre de niveau Gestion sur l'unité de la personne (Paramètres des effectifs › Périmètres) ; un compte d'administration décide partout.", "usperimetres"),
      p("Facultatif : l'administration publie des règles du contexte ABSENCE (Règles des effectifs, module « effectifs_regles ») ; sans règle, l'absence est acceptée d'office.", "usregles"),
      p("Pour que des places soient libérées et offertes au comblement : la personne tient des quarts attribués dans une période publiée (Gestion des quarts › Périodes, puis Quarts et équipages).", "usperiodes"),
    ],
    etapes: [
      "« Déclarer une absence » ouvre le formulaire.",
      "Choisir le code d'absence, la période (du, au) et ajouter un motif au besoin.",
      "Envoyer : l'absence est acceptée d'office si toutes les règles sont respectées, sinon elle attend une décision.",
    ],
    exemple: [["Code", "MAL — Maladie"], ["Du", "2026-11-19"], ["Au", "2026-11-20"], ["Motif", "Fièvre, certificat à suivre"]],
    resultat: "Les places libérées sont offertes au comblement ; le statut (accordée, à approuver, refusée) s'affiche avec ses motifs.",
    attention: "Une indisponibilité (« je ne peux pas ce soir-là ») se dépose dans Mes disponibilités, pas ici.",
  },
  monagph: {
    titre: "Mon AGPH",
    objet: "Suivre son tour de rôle pour le choix annuel des postes (AGPH), choisir son poste quand c'est son tour, et manifester son intérêt pour un remplacement long terme ouvert.",
    qui: "Toute personne planifiable visée par une campagne de son unité et de son corps d'emploi.",
    prealables: [
      modules("effectifs", "effectifs_agph"),
      REFERENTIELS_SOCLE,
      compte("n'importe lequel"),
      p("La ressource de la personne est reliée à son compte (Gestion des quarts › Ressources, « Compte utilisateur »), active, avec son « Ancienneté (selon la convention) » : le rang en découle. Sans ce lien : « votre compte n'est relié à aucune ressource planifiable ».", "usressources"),
      p("Un planificateur publie les postes de l'unité et du corps d'emploi (Gestion des quarts › Postes) : sans poste publié et libre, la campagne n'a « rien à offrir ».", "uspostes"),
      p("Un planificateur (niveau Gestion sur l'unité) crée la campagne de l'année pour l'unité et le corps d'emploi de la personne, puis démarre le tour de rôle (Confection annuelle (AGPH)).", "usagph"),
      p("Pour manifester son intérêt : un planificateur ouvre un remplacement long terme du même corps d'emploi, avec sa date de fin des manifestations (Confection annuelle (AGPH)).", "usagph"),
    ],
    etapes: [
      "Lire son rang et l'état de la campagne : « Votre tour de rôle » dit combien de personnes vous précèdent.",
      "Quand c'est votre tour, choisir un poste parmi ceux qui restent ; le délai de choix est affiché.",
      "Dans « Remplacements long terme ouverts », « Manifester mon intérêt » sur un remplacement de votre corps d'emploi, avant la date de fin des manifestations.",
    ],
    exemple: [["Campagne", "AGPH 2027 — Opérations Est, Paramédics"], ["Rang", "3 (ancienneté figée : 120)"], ["Poste choisi", "B-TC — Caserne 12, cycle 4/4"], ["Remplacement", "Poste C-TPR, du 2027-06-02 au 2027-06-30 → Manifester mon intérêt"]],
    resultat: "Le choix est enregistré et le rang suivant est appelé ; l'intérêt pour un remplacement est enregistré et le planificateur l'attribue à l'ancienneté.",
    attention: "Si vous ne choisissez pas dans le délai, votre tour passe : le planificateur peut saisir votre choix ensuite.",
  },
  monpointage: {
    titre: "Mon pointage",
    objet: "Pointer l'entrée et la sortie d'un quart, avec la preuve exigée par le milieu, ou déclarer après coup un quart non pointé.",
    qui: "Toute personne planifiable qui a un quart en cours.",
    prealables: [
      modules("effectifs", "effectifs_presence"),
      REFERENTIELS_SOCLE,
      p("L'encadrement crée le milieu (Paramètres des effectifs › Milieux physiques) — type de milieu et unité en vigueur — et coche les preuves de présence admises et exigées ; le géorepérage exige latitude, longitude et rayon.", "usmilieux"),
      compte("n'importe lequel"),
      RESSOURCE_LIEE,
      p("Un planificateur attribue à la personne un quart d'une période publiée qui couvre l'heure du pointage ; sinon : « aucun quart publié ne vous attend en ce moment : un quart non pointé se déclare en saisie a posteriori ».", "usequipages"),
      p("Facultatif : un profil de pointage (Pointage et présence (console)) fixe le mode — temps réel ou après coup — et la fenêtre de saisie ; un profil « après coup » refuse le pointage en temps réel. Les activités proposées viennent du référentiel « Activités de présence ».", "uspresence"),
    ],
    etapes: [
      "Choisir « Où êtes-vous ? » (le milieu du quart) et, si demandé, saisir le « Code affiché au milieu » ou joindre sa position.",
      "« Entrée » au début du quart, « Sortie » à la fin ; une pause se déclare par « Début de pause » et « Fin de pause ».",
      "Un oubli : « Déclarer un quart non pointé », avec la date, les heures et « Pourquoi ce quart n'a-t-il pas été pointé ? ».",
    ],
    exemple: [["Où êtes-vous ?", "Caserne 12"], ["Code affiché au milieu (si demandé)", "4821"], ["Que commencez-vous à faire ?", "Quart régulier"]],
    resultat: "Le pointage est jugé (conforme, écart de lieu, preuve absente) ; un écart part à l'approbation du planificateur.",
    attention: "La position n'est lue qu'une fois, au geste, et seulement si vous cochez « Joindre ma position ».",
  },
  matemps: {
    titre: "Ma feuille de temps",
    objet: "Préparer sa feuille d'une période à partir de ses quarts, la corriger segment par segment, puis la soumettre.",
    qui: "Toute personne planifiable.",
    prealables: [
      modules("effectifs", "effectifs_temps"),
      REFERENTIELS_SOCLE,
      p("L'administration crée les types d'heures (Référentiels › Types d'heures) avec leur code de paie : au moins un de comportement TRAVAILLEE — sinon « aucun type d'heure de comportement TRAVAILLEE n'est en vigueur » — et un de comportement SUPPLEMENTAIRE pour les heures supplémentaires.", "usreferentiels"),
      compte("n'importe lequel"),
      RESSOURCE_LIEE,
      p("Les segments viennent des quarts attribués dans une période publiée et des pointages : sans eux, la feuille est vide et tout se saisit à la main.", "usperiodes"),
      perimetre("Gestion", "par défaut, chaque palier d'approbation va au niveau Gestion de l'unité de la personne ; sans personne à ce niveau, la feuille soumise n'a pas d'approbateur."),
      p("Facultatif : le nombre de paliers, le flux d'approbation, les supérieurs hiérarchiques et les champs propres à l'organisme se règlent dans Feuilles de temps (console).", "ustemps"),
    ],
    etapes: [
      "« Préparer ma feuille » avec le « Début de la période » et la « Fin de la période » : les segments viennent des quarts et des pointages.",
      "Vérifier chaque segment : la ressource (nom et matricule), les heures, le type d'heure, les « Tâches » et les champs propres à votre organisme.",
      "« Ajouter un segment » pour un temps manquant (jour, de, à, type d'heure), avec la « Description des tâches » et les champs demandés (ex. Véhicule) ; ou modifier un segment et « Enregistrer la correction ».",
      "« Soumettre ma feuille » : elle part au premier palier d'approbation — souvent votre supérieur hiérarchique. « En attente de » dit chez qui elle est, et, s'il est absent, qui le remplace (« en son absence : … »).",
      "Des heures supplémentaires ? Ajoutez un segment du type d'heure « Heures supplémentaires » : elles comptent même sous le seuil du jour, et se paient majorées, une seule fois. Au-delà des seuils de l'organisme, l'excédent devient supplémentaire de lui-même.",
      "Une erreur après la soumission ? Tant que la paie n'est pas faite, « Rappeler ma feuille » la ramène au brouillon : corrigez-la, puis soumettez-la de nouveau.",
    ],
    exemple: [["Début de la période", "2026-11-09"], ["Fin de la période", "2026-11-15"], ["Ajouter un segment — Jour", "2026-11-12"], ["De", "18:00"], ["À", "19:30"], ["Type d'heure", "SUPP — Supplémentaire"],
      ["Description des tâches", "Transport prolongé, appel 412"], ["Véhicule (obligatoire)", "A-12"]],
    resultat: "La feuille passe à « Soumise » et affiche « En attente de : … » ; chaque correction garde ses versions, sa description et ses champs, et une anomalie est nommée avec ce qu'il faut corriger.",
    attention: "Une feuille retournée ou rouverte porte son motif : corriger puis soumettre à nouveau. Une feuille payée ne se modifie plus : une erreur se corrige à la paie suivante. Un champ marqué « obligatoire » doit être rempli pour que le segment soit accepté.",
  },
  monequipe: {
    titre: "Mon équipe",
    objet: "Voir les personnes qui relèvent de soi, réorganiser son équipe, suivre les feuilles à approuver et désigner un suppléant pendant son absence.",
    qui: "Gestionnaires, direction, RH et administration ; réorganiser exige le droit de corriger une fiche (gestion, RH, administration).",
    prealables: [
      modules("effectifs"),
      compte("gestionnaire, direction, RH ou administration"),
      p("Le compte du gestionnaire est rattaché à une fiche du personnel : créer sa ressource reliée à son compte (Gestion des quarts › Ressources) crée et rattache cette fiche ; l'administration peut aussi lier le compte à l'employé (Comptes et rôles). Sans fiche, l'équipe reste vide et réorganiser répond « votre compte n'est relié à aucune fiche du personnel ».", "usressources"),
      p("Chaque membre a sa fiche (même geste) et son supérieur direct : « Supérieurs hiérarchiques » dans Feuilles de temps (console), ou « Rattacher une personne » ici.", "ustemps"),
      p("Pour désigner un suppléant : il a un rôle qui approuve des feuilles (gestion, direction ou administration)."),
    ],
    etapes: [
      "Lire « Les membres de mon équipe » : chaque personne, son poste, son supérieur direct et son lien (direct ou indirect).",
      "« Rattacher une personne » : choisir une personne encore sans supérieur, puis « Rattacher à mon équipe ».",
      "« Changer de supérieur » sur une ligne : choisir le « Nouveau supérieur direct » (vous ou un membre de votre équipe), puis « Enregistrer » ; « Détacher » retire un subordonné direct.",
      "« Feuilles de temps à approuver » : les feuilles qui attendent votre décision, puis « Ouvrir la console des feuilles pour décider ».",
      "Avant une absence : choisir le « Suppléant » (3 lettres de son nom), les dates « Du » et « Au », puis « Désigner ce suppléant » ; au retour, « Mettre fin ».",
    ],
    exemple: [["Personne à rattacher", "Léa Morin — Paramédic"], ["Nouveau supérieur direct de Luc Bouchard", "Julie Tremblay"],
      ["Suppléant", "Diane Roy"], ["Du", "2026-12-21"], ["Au", "2027-01-04"]],
    resultat: "« Luc Bouchard relève maintenant de Julie Tremblay. » ; pendant la suppléance, les feuilles de votre équipe parviennent aussi à Diane Roy, et l'employé lit « en son absence : Diane Roy (jusqu'au 2027-01-04) ».",
    attention: "Vous ne voyez et ne changez que votre propre équipe : une personne d'une autre branche se demande à son gestionnaire. Le suppléant doit pouvoir approuver des feuilles (gestion, direction ou administration).",
  },
  mesechanges: {
    titre: "Mes échanges",
    objet: "Proposer d'échanger un de ses quarts contre celui d'un collègue, et accepter ou refuser ceux qu'on vous propose.",
    qui: "Toute personne planifiable.",
    prealables: [
      modules("effectifs", "effectifs_echanges"),
      REFERENTIELS_SOCLE,
      compte("n'importe lequel"),
      RESSOURCE_LIEE,
      p("Les deux personnes tiennent chacune un quart à venir d'une période publiée : un quart d'une période non publiée ou une place vacante ne s'échange pas.", "usperiodes"),
      p("Facultatif : des règles du contexte ECHANGE (Règles des effectifs), par exemple « le chef valide » ; le chef qui valide a un périmètre de niveau Gestion sur les deux unités.", "usregles"),
    ],
    etapes: [
      "Choisir « Mon quart » (celui que vous cédez) puis « Contre le quart de » (le quart d'un collègue).",
      "« Proposer l'échange » : les règles le jugent dans les deux sens ; un refus dit pourquoi.",
      "Le collègue l'accepte dans son propre écran ; si la règle l'exige, le chef le valide ensuite.",
    ],
    exemple: [["Mon quart", "lun. 2026-11-16 · 06:00–18:00"], ["Contre le quart de", "Marc Dubé — lun. 2026-11-23 · 06:00–18:00"]],
    resultat: "« Échange proposé à Marc Dubé. Une fois accepté, il devra être validé par le chef. » ; appliqué, les quarts changent de mains.",
  },
  usdisponibilites: {
    titre: "Disponibilités de la période",
    objet: "Lire les dépôts de disponibilités d'une période, les approuver, et voir le bassin de candidats d'une place vacante.",
    qui: "Planificateurs et chefs, dans leur périmètre.",
    prealables: [
      modules("effectifs", "effectifs_disponibilites"),
      compte(ENCADREMENT_EFFECTIFS),
      perimetre("Lecture pour lire, Gestion pour approuver un dépôt", "hors périmètre, l'unité n'apparaît pas."),
      p("Une période de l'unité en collecte (Périodes › « Ouvrir la collecte ») et des personnes planifiables, reliées à leur compte, qui y déposent (Mes disponibilités).", "usperiodes"),
      p("Pour « Voir les candidats » : des quarts générés avec des places vacantes dans la période (Postes publiés, puis génération en confection).", "usequipages"),
    ],
    etapes: [
      "Choisir la « Période ».",
      "Ouvrir un dépôt pour lire ses plages et les « Motifs relevés par les règles au dépôt » ; « Approuver ce dépôt » au besoin.",
      "Choisir une « Place vacante » puis « Voir les candidats » : chaque exclusion porte son motif.",
    ],
    exemple: [["Période", "Semaine du 16 novembre"], ["Place vacante", "2026-11-17 · 06:00–18:00 · Caserne 12"], ["Geste", "Voir les candidats → Marc Dubé (admissible)"]],
    resultat: "À la fin de la collecte, l'attribution automatique se fait sans geste ; l'écran le rappelle.",
  },
  usabsences: {
    titre: "Absences et comblement",
    objet: "Décider des absences de son périmètre en voyant leur impact sur le comblement, et suivre l'offre des places libérées.",
    qui: "Planificateurs, chefs, direction.",
    prealables: [
      modules("effectifs", "effectifs_absences"),
      compte(ENCADREMENT_EFFECTIFS),
      perimetre("Gestion", "approuver, refuser ou déroger l'exige sur l'unité de la personne ; sinon « unité … hors de votre périmètre (niveau GESTION requis) »."),
      p("Les codes d'absence existent (Codes d'absence) : une dérogation attribue un code en vigueur.", "uscodesabsence"),
      p("Des ressources reliées à leur compte, qui déclarent dans Mes absences d'effectifs, ou pour qui le planificateur déclare.", "usressources"),
      p("Pour mesurer l'impact et offrir les places : des quarts attribués dans une période publiée ; les règles ABSENCE sont facultatives (Règles des effectifs).", "usperiodes"),
    ],
    etapes: [
      "Filtrer par statut (« À approuver »), puis ouvrir une absence.",
      "Lire l'impact : places libérées et taux de comblement avant/après.",
      "« Approuver » ou « Refuser » ; une dérogation à un refus automatique exige un motif et un code d'absence attribué.",
    ],
    exemple: [["Personne", "Julie Roy"], ["Code", "MAL"], ["Période", "2026-11-19 au 2026-11-20"], ["Décision", "Approuver"]],
    resultat: "Les places libérées sont offertes aux admissibles, attribuées d'office ou réservées au remplacement long terme ; le bilan s'affiche.",
  },
  usagph: {
    titre: "Confection annuelle (AGPH)",
    objet: "Conduire la campagne annuelle de choix des postes : rangs, budget d'heures, tours de rôle, révision, clôture ; et ouvrir les remplacements long terme.",
    qui: "Planificateurs et administration.",
    prealables: [
      modules("effectifs", "effectifs_agph"),
      compte(ENCADREMENT_EFFECTIFS),
      perimetre("Gestion", "créer et conduire une campagne l'exige sur l'unité."),
      REFERENTIELS_SOCLE,
      p("Les ressources actives de l'unité et du corps d'emploi, avec leur ancienneté (Ressources) ; sans elles : « aucune ressource active de … : rien à ordonner ».", "usressources"),
      p("Les postes de l'unité et du corps, publiés et libres (Postes) ; sans eux : « aucun poste publié et libre de … : rien à offrir ».", "uspostes"),
    ],
    etapes: [
      "« Nouvelle campagne AGPH » : année, unité, corps d'emploi, délai de choix (minutes).",
      "Fixer le budget d'heures par semaine ; lire les disponibilités déposées dans la campagne.",
      "Démarrer le tour de rôle ; saisir le choix d'une personne absente ; réviser une attribution avec un motif ; clore.",
      "« Ouvrir un remplacement long terme » : poste, origine, du/au, « Manifestations jusqu'au ».",
    ],
    exemple: [["Année", "2027"], ["Unité", "OPS"], ["Corps d'emploi", "PARA"], ["Délai de choix (minutes)", "1440"], ["Budget d'heures par semaine (facultatif)", "36"]],
    resultat: "À la clôture, les postes restés libres passent à la gestion régulière et sont listés ; un remplacement non comblé transfère ses quarts.",
  },
  uscomblement: {
    titre: "Comblement",
    objet: "Le tableau du matin : taux de comblement, quarts sous leur minimum opérationnel, jours sous le quota.",
    qui: "Planificateurs, chefs, direction.",
    prealables: [
      modules("effectifs", "effectifs_planification"),
      compte(ENCADREMENT_EFFECTIFS),
      perimetre("Planification", "le tableau ne montre que les unités du périmètre ; forcer une attribution qu'une règle écarte exige le niveau Gestion."),
      p("Une période dont les quarts sont générés depuis des postes publiés (Postes, puis Périodes : confection, générer), et des ressources actives du bon corps d'emploi (Ressources).", "usperiodes"),
      p("Facultatif : les quotas d'équipages par jour (Quotas), pour lire les jours sous le quota.", "usquotas"),
    ],
    etapes: [
      "Choisir la « Période ».",
      "Lire « Quarts sous leur minimum opérationnel » ; « Combler » ouvre la place à pourvoir.",
      "Choisir la personne ; forcer une attribution qu'une règle écarte exige une justification (10 caractères au moins), consignée au journal.",
    ],
    exemple: [["Période", "Semaine du 16 novembre"], ["Quart", "2026-11-17 · 14:00–22:00 · Nord · 1 / 3"], ["Personne", "Marc Dubé"], ["Justification pour forcer", "Seul paramédic disponible, accord du chef"]],
    resultat: "Le quart remonte vers « opérationnel » et le taux de comblement se met à jour.",
  },
  usequipages: {
    titre: "Quarts et équipages",
    objet: "Gérer les quarts d'une période : leurs places, le minimum opérationnel, le mode d'attribution, et les quarts ad hoc hors cycle.",
    qui: "Planificateurs.",
    prealables: [
      modules("effectifs", "effectifs_planification"),
      compte(ENCADREMENT_EFFECTIFS),
      perimetre("Planification", "les quarts d'une unité hors périmètre ne s'ouvrent pas."),
      p("Les référentiels en vigueur : natures de quart, plages horaires, corps d'emploi (Référentiels), et les milieux (Milieux physiques).", "usreferentiels"),
      p("Une période de l'unité qui n'est pas close (Périodes) ; ses quarts réguliers viennent des postes publiés par « générer ».", "usperiodes"),
      p("Des ressources actives du corps d'emploi de la place, pour l'occuper (Ressources).", "usressources"),
    ],
    etapes: [
      "Choisir la « Période », puis un quart (« Sélectionner le quart du … »).",
      "Modifier le minimum opérationnel, le mode d'attribution ou la note ; « Libérer la place » d'une personne avec un motif.",
      "« Nouveau quart ad hoc » : jour, début, fin, nature, milieu, corps d'emploi et nombre de places.",
    ],
    exemple: [["Jour", "2026-11-21"], ["Début", "10:00"], ["Fin (avant le début = passe minuit)", "16:00"], ["Nature du quart", "REG"], ["Milieu", "Caserne 12"], ["Corps d'emploi", "PARA"], ["Nombre", "2"], ["Mode d'attribution", "Manuel"]],
    resultat: "Le quart est créé avec ses places ; il apparaît au comblement et dans l'horaire des personnes attribuées.",
  },
  uspostes: {
    titre: "Postes",
    objet: "Définir les postes (lignes de quarts) : code, libellé, milieu, corps d'emploi, statut offert et cycle ; les publier, les modifier en lot.",
    qui: "Planificateurs et administration.",
    prealables: [
      modules("effectifs", "effectifs_planification"),
      compte(ENCADREMENT_EFFECTIFS),
      perimetre("Planification", "créer un poste l'exige sur son unité."),
      p("Les référentiels en vigueur : unité, statut d'emploi, corps d'emploi, nature de quart et chaque plage horaire nommée dans le cycle ; au besoin, les « Paramètres de poste » (Référentiels).", "usreferentiels"),
      p("Le milieu où le poste travaille (Milieux physiques).", "usmilieux"),
    ],
    etapes: [
      "Créer un poste avec son code, son libellé, son milieu et son « Premier jour du cycle ».",
      "Décrire le cycle (les quarts de chaque jour) ; publier pour qu'il entre dans la planification.",
      "Modifier plusieurs postes d'un geste (« Remplacer le cycle par une semaine commune ») : les conflits sont listés avant d'écrire.",
    ],
    exemple: [["Code", "B-TC"], ["Libellé", "Caserne 12 — jour, temps complet"], ["Milieu", "Caserne 12"], ["Corps d'emploi", "PARA"], ["Statut offert", "TC"], ["Premier jour du cycle", "2026-11-16"]],
    resultat: "Les quarts futurs déjà générés suivent la modification ; le bilan dit combien ont été déplacés, requalifiés ou retirés.",
  },
  usperiodes: {
    titre: "Périodes",
    objet: "Ouvrir les périodes de planification et les faire avancer : préparation, collecte, confection, publication, clôture.",
    qui: "Planificateurs.",
    prealables: [
      modules("effectifs", "effectifs_planification"),
      compte(ENCADREMENT_EFFECTIFS),
      perimetre("Planification", "créer une période ou la faire avancer l'exige sur son unité."),
      p("L'unité en vigueur (Référentiels).", "usreferentiels"),
      p("Pour générer les quarts : des postes publiés de l'unité (Postes) ; pour les occuper : des ressources (Ressources).", "uspostes"),
    ],
    etapes: [
      "« Nouvelle période » : nom, unité, premier et dernier jour.",
      "« Ouvrir la collecte » avec la « Fin de la collecte » : les employés déposent leurs disponibilités.",
      "Confectionner, puis publier : les horaires deviennent visibles dans Mon horaire.",
    ],
    exemple: [["Nom", "Semaine du 16 novembre"], ["Unité", "OPS"], ["Premier jour", "2026-11-16"], ["Dernier jour", "2026-11-22"], ["Fin de la collecte", "2026-11-12"]],
    resultat: "La période change d'état ; une période publiée ne redevient jamais brouillon.",
  },
  usressources: {
    titre: "Ressources",
    objet: "Tenir les personnes planifiables : matricule, nom, unité, corps et statut d'emploi, ancienneté, et le compte auquel elles sont reliées.",
    qui: "Planificateurs, ressources humaines, administration.",
    prealables: [
      modules("effectifs", "effectifs_planification"),
      compte(ENCADREMENT_EFFECTIFS + " pour tenir les ressources ; et, pour chaque personne, son propre compte de connexion"),
      perimetre("Gestion", "créer ou modifier une ressource l'exige sur son unité."),
      p("Les référentiels en vigueur : unité et corps d'emploi (obligatoires) ; titre d'emploi, statut d'emploi et convention collective (facultatifs).", "usreferentiels"),
      p("Le compte de la personne existe avant d'être relié (Comptes et rôles) ; seul un compte d'administration voit la liste des comptes à relier.", "admin"),
    ],
    etapes: [
      "Ajouter une ressource avec son matricule, son nom, son unité, son corps d'emploi et son statut.",
      "Renseigner l'« Ancienneté (selon la convention) » : elle ordonne l'AGPH et les remplacements.",
      "Relier la ressource au compte de la personne (« Compte utilisateur », proposé à l'administration) : sans ce lien, ses écrans personnels répondent « votre compte n'est relié à aucune ressource planifiable ».",
    ],
    exemple: [["Matricule", "P100"], ["Nom", "Julie Roy"], ["Unité", "OPS"], ["Corps d'emploi", "PARA"], ["Statut", "TC — Temps complet"], ["Ancienneté (selon la convention)", "120"], ["Compte utilisateur (facultatif)", "julie.roy"]],
    resultat: "La personne devient planifiable et voit son horaire, ses disponibilités et sa feuille de temps.",
  },
  usquotas: {
    titre: "Quotas",
    objet: "Fixer le nombre d'équipages opérationnels visés par jour, par unité, plage ou nature de quart, et poser une surcharge temporaire.",
    qui: "Planificateurs et direction.",
    prealables: [
      modules("effectifs", "effectifs_planification"),
      compte(ENCADREMENT_EFFECTIFS),
      perimetre("Planification", "poser un quota l'exige sur son unité."),
      p("Les référentiels : l'unité, et au besoin la plage horaire et la nature de quart visées (Référentiels).", "usreferentiels"),
    ],
    etapes: [
      "« Définir un quota » : unité, plage, nature, valeur et « En vigueur à partir du ».",
      "Pour une période exceptionnelle, « Enregistrer la surcharge » avec ses dates et son motif.",
    ],
    exemple: [["Unité", "OPS"], ["Nature", "REG"], ["Équipages opérationnels visés par jour", "6"], ["En vigueur à partir du (aujourd'hui ou plus tard)", "2026-11-16"], ["Valeur temporaire", "8"], ["Motif", "Festival d'été"]],
    resultat: "Le comblement compare chaque jour à son quota ; une surcharge prend effet « à partir de », jamais en arrière.",
  },
  uspresence: {
    titre: "Pointage et présence (console)",
    objet: "Contrôler les pointages de son périmètre, approuver ou rejeter les écarts, importer des horodatages externes et tenir les profils de pointage.",
    qui: "Planificateurs et chefs.",
    prealables: [
      modules("effectifs", "effectifs_presence"),
      compte(ENCADREMENT_EFFECTIFS),
      perimetre("Lecture au moins", "la console ne montre que les pointages des unités du périmètre."),
      p("Les milieux et leurs preuves de présence (Milieux physiques).", "usmilieux"),
      p("Des personnes qui pointent (Mon pointage) : une ressource reliée à leur compte et un quart publié en cours. Pour l'import, chaque matricule est celui d'une ressource.", "usressources"),
    ],
    etapes: [
      "Filtrer par site, statut, ou cocher « N'afficher que les écarts de lieu ».",
      "Ouvrir un pointage, lire le verdict et la preuve, puis approuver, ou rejeter avec un motif obligatoire.",
      "« Importer des horodatages externes » : une ligne « clé;matricule;sens;horodatage ISO » par pointage de borne.",
    ],
    exemple: [["Source (borne, système)", "Borne Caserne 12"], ["Lignes « clé;matricule;sens;horodatage ISO »", "B12-001;P100;ENTREE;2026-11-16T06:02:00-05:00"]],
    resultat: "Le pointage approuvé alimente la feuille de temps ; un rejet est motivé et journalisé.",
  },
  ustemps: {
    titre: "Feuilles de temps (console)",
    objet: "Préparer les feuilles d'une période, les approuver palier par palier, lire les relevés et produire le lot de paie.",
    qui: "Planificateurs, chefs, paie.",
    prealables: [
      modules("effectifs", "effectifs_temps"),
      compte(ENCADREMENT_EFFECTIFS + " ; les champs de la feuille et les seuils relèvent de l'" + CONFIGURATION_EFFECTIFS),
      perimetre("Planification pour préparer, Gestion pour approuver au palier par défaut", "hors périmètre, une feuille n'apparaît pas, sauf celles routées à son supérieur hiérarchique."),
      p("Les types d'heures (Référentiels › Types d'heures) : un de comportement TRAVAILLEE pour préparer ; un de comportement SUPPLEMENTAIRE aussi pour produire le lot de paie, chacun avec son code de paie.", "usreferentiels"),
      p("Des ressources reliées à leur compte et des quarts attribués dans une période publiée (Périodes).", "usperiodes"),
      p("Pour transmettre le lot à un système de paie : le connecteur de paie (Intégrations des effectifs).", "usintegrations"),
    ],
    etapes: [
      "« Préparer les feuilles » pour une unité et une période.",
      "Ouvrir une feuille soumise : approuver, ou retourner avec un motif obligatoire. Une feuille approuvée mais pas encore payée se « Rouvre pour correction », avec un motif.",
      "« Afficher le relevé » (regroupé par personne, unité ou type d'heure) et l'exporter ; « Produire le lot » puis « Télécharger le lot (CSV) ».",
      "Régler le « Flux d'approbation des heures » : paliers, conditions et approbateur — niveau de l'unité, rôle, personne désignée ou « Supérieur hiérarchique ».",
      "« Supérieurs hiérarchiques » : choisir une « Personne » puis « Son supérieur hiérarchique », et « Enregistrer ». C'est ce que lit l'approbateur « Supérieur hiérarchique ».",
      "« Champs de la feuille » (administration) : ajouter ce que chaque segment doit dire selon vos emplois — libellé, code, type (texte, nombre, oui ou non, liste), obligatoire ; « Désactiver » un champ qui ne sert plus.",
    ],
    exemple: [["Unité", "OPS"], ["Début de la période", "2026-11-09"], ["Fin", "2026-11-15"], ["Motif (obligatoire pour retourner)", "Segment du 12 sans preuve de transport"],
      ["Champ — Libellé", "Véhicule"], ["Code", "vehicule"], ["Type", "Liste de choix"], ["Choix offerts", "A-12, B-07"], ["Personne", "Julie Roy"], ["Son supérieur hiérarchique", "Marc Lavoie"]],
    resultat: "Les feuilles approuvées entrent au lot de paie ; une feuille routée au supérieur lui apparaît même hors de son périmètre, avec « En attente de ».",
    attention: "Sans supérieur désigné, une feuille routée au « Supérieur hiérarchique » revient au niveau GESTION de l'unité. Un champ se désactive, jamais ne s'efface : les valeurs déjà saisies restent lisibles.",
  },
  usechanges: {
    titre: "Demandes et échanges (console)",
    objet: "Suivre les échanges de quarts de son périmètre, en créer un pour une personne, et valider ou rejeter ceux que la règle soumet au chef.",
    qui: "Planificateurs et chefs.",
    prealables: [
      modules("effectifs", "effectifs_echanges"),
      compte(ENCADREMENT_EFFECTIFS),
      perimetre("Planification pour proposer pour une personne, Gestion sur les deux unités pour valider ou rejeter", "sinon la console refuse le geste."),
      p("Deux personnes reliées à leur compte, qui tiennent chacune un quart à venir d'une période publiée (Périodes).", "usperiodes"),
      p("Facultatif : des règles du contexte ECHANGE qui exigent la validation du chef (Règles des effectifs).", "usregles"),
    ],
    etapes: [
      "Créer : « Proposer un échange pour une personne », choisir la « Personne », le « Quart qu'elle cède » et le « Quart demandé en retour », puis « Proposer l'échange ».",
      "La destinataire accepte l'échange dans Mes échanges.",
      "Trancher : ouvrir l'échange « à valider », lire « Ce que les règles ont relevé », saisir un motif, puis « Valider » ou « Rejeter ».",
    ],
    exemple: [["Personne", "Julie Roy"], ["Quart qu'elle cède", "2026-11-16 · 06:00–18:00"], ["Quart demandé en retour", "Marc Dubé — 2026-11-23 · 06:00–18:00"], ["Motif (facultatif)", "Remplacement couvert"]],
    resultat: "« Échange proposé pour Julie Roy avec Marc Dubé : Marc Dubé doit l'accepter. » ; validé, les quarts changent de mains ; rejeté, chacun garde le sien.",
    attention: "L'échange est jugé dans les deux sens : un quart qui créerait un conflit chez l'un ou l'autre est refusé, avec la règle nommée.",
  },
  usmessages: {
    titre: "Messagerie du personnel",
    objet: "Écrire à une personne ou à un groupe, répondre en privé, réagir, joindre un fichier, et appeler depuis un fil.",
    qui: "Tous les comptes de l'organisme qui ont le module de communication.",
    prealables: [
      modules("effectifs", "effectifs_communication"),
      compte("n'importe lequel"),
      p("Les personnes joignables sont les membres des groupes de travail : chaque groupe est une unité en vigueur (Référentiels), et ses membres sont les ressources actives reliées à un compte (Ressources).", "usressources"),
    ],
    etapes: [
      "« Écrire à une personne » : choisir la « Personne » puis « Écrire à cette personne » ; le fil s'ouvre dans « Lire et écrire ».",
      "Écrire le « Message », joindre une pièce au besoin, puis « Envoyer » ; l'auteur voit Envoyé, Reçu par N, puis Lu par N.",
      "Appeler : « Appeler Marc Dubé » en tête du fil. Le bandeau « ça sonne » paraît en haut de page, chez vous comme chez Marc, qui décroche ou refuse ; « Raccrocher » termine l'appel.",
      "Un groupe : « Ouvrir une conversation de groupe » (sujet, premier participant), puis « Appeler le groupe » : chacun rejoint ou ignore.",
    ],
    exemple: [["Personne", "Marc Dubé"], ["Message", "Peux-tu prendre mon quart de jeudi ?"], ["Pièce jointe", "consigne.pdf (≤ 50 Mo, analysée par l'antivirus)"]],
    resultat: "Le message est remis, ses réactions et son historique se lisent dans le fil ; l'appel figure à l'historique des appels.",
    attention: "La voix passe par votre téléphone habituel : l'application ouvre et trace la ligne, elle ne transporte pas encore la voix.",
  },
  usappels: {
    titre: "Appeler un collègue",
    objet: "Joindre directement une personne de son groupe, sans passer par un fil, et relire l'historique de ses appels.",
    qui: "Tous les comptes qui ont le module de communication.",
    prealables: [
      modules("effectifs", "effectifs_communication"),
      compte("n'importe lequel"),
      p("Les groupes sont les unités en vigueur, et les personnes à appeler les ressources actives reliées à un compte (Ressources) : une personne sans ressource n'apparaît dans aucun groupe.", "usressources"),
    ],
    etapes: [
      "Choisir le groupe (périmètre de travail), puis la « Personne à appeler ».",
      "« Appeler » : le bandeau « ça sonne » paraît aussitôt ; la personne décroche ou refuse.",
      "L'historique liste les appels passés et reçus, avec leur durée.",
    ],
    exemple: [["Personne à appeler", "Marc Dubé"]],
    resultat: "Une sonnerie sans réponse expire au bout de 45 secondes et devient un appel manqué.",
  },
  usdiffusion: {
    titre: "Listes de diffusion du personnel",
    objet: "Préparer des listes de destinataires (personnes ou groupes entiers) pour écrire à plusieurs d'un seul envoi.",
    qui: "Encadrement et personnes qui coordonnent.",
    prealables: [
      modules("effectifs", "effectifs_communication"),
      compte("n'importe lequel"),
      p("Les membres possibles : les groupes de travail (unités en vigueur) et leurs ressources actives reliées à un compte (Ressources).", "usressources"),
    ],
    etapes: [
      "Créer une liste : nom, partagée ou non.",
      "Ajouter des membres : une personne, ou un groupe entier (il se recalcule à chaque envoi).",
      "Écrire à la liste depuis la messagerie.",
    ],
    exemple: [["Nom", "Équipe de nuit — Est"], ["Partagée", "Oui"], ["Membre (groupe)", "OPS — Opérations préhospitalières"]],
    resultat: "Une arrivée dans l'unité est jointe à la liste sans que personne ait à la modifier.",
  },
  alnouvelle: {
    titre: "Nouvelle alerte",
    objet: "Envoyer une alerte priorisée à des groupes ou à des personnes, avec les réponses qu'elles pourront donner d'un geste.",
    qui: "Administration, direction et planificateurs (capacité « alertes.envoyer »).",
    prealables: [
      modules("alertes"),
      compte("administration, direction ou gestionnaire (capacité « alertes.envoyer ») ; la priorité critique exige « alertes.critique » : administration, direction, ou droit accordé par l'organisme"),
      p("Les destinataires : les groupes sont les unités en vigueur des effectifs, et leurs membres les ressources actives reliées à un compte (Ressources) ; les listes viennent des Listes de diffusion du personnel ; les groupes transverses, des Paramètres des alertes.", "usressources"),
      p("Facultatif : réponses prédéfinies, modèles et politiques de relance (Paramètres des alertes).", "alparametres"),
      p("Pour les textos : le fournisseur de textos de l'organisme (Intégrations) et un numéro de téléphone au dossier de chaque personne (Mon compte).", "integrations"),
    ],
    etapes: [
      "Choisir un « Modèle » au besoin : il remplit le titre, le message, la priorité et les réponses ; tout reste modifiable.",
      "Écrire le « Titre » et le « Message », puis choisir la « Priorité ». Une alerte critique part sur tous les canaux, quelles que soient les préférences ; elle est réservée à qui en a le droit (direction, administration, ou un droit accordé par l'organisme).",
      "Choisir les destinataires : « Tout le groupe … » (une unité ou un groupe transverse, comme une garde), « La liste … » (une liste de distribution), ou des personnes, par leur nom, dans « Choisir des personnes dans un groupe ».",
      "Au besoin, choisir des « Relances » : sans réaction, l'alerte est relancée puis escaladée selon la politique, jusqu'à son critère d'arrêt.",
      "Cocher les « Réponses proposées », puis « Envoyer l'alerte ».",
    ],
    exemple: [["Modèle", "Code bleu"], ["Titre", "Code bleu chambre 512"], ["Message", "5e étage, répondez à la prise en charge"], ["Priorité", "Critique"],
      ["Destinataires", "Tout le groupe Opérations Est ; Julie Roy"], ["Réponses proposées", "J'arrive (prise en charge) · Indisponible (refus)"]],
    resultat: "« Alerte … envoyée à N personne(s) » ; les destinataires sont figés à l'envoi, et « Suivre les réponses » ouvre le suivi.",
    attention: "Vous n'êtes jamais destinataire de votre propre alerte, même si vous êtes dans le groupe visé. Sans le droit du critique, la priorité « Critique » est grisée, et les modèles critiques ne vous sont pas proposés.",
  },
  alsuivi: {
    titre: "Suivi des alertes",
    objet: "Voir, pour chaque alerte de l'organisme, qui l'a lue, qui a répondu et qui a pris en charge.",
    qui: "Qui détient la capacité « alertes.suivre » : expéditeurs, gestionnaires et pilotes par défaut. Le suivi est la preuve de livraison, partagée ; il n'exige pas le droit d'envoyer.",
    prealables: [
      modules("alertes"),
      compte("administration, direction ou gestionnaire (capacité « alertes.suivre »)"),
      p("Des alertes envoyées (Nouvelle alerte).", "alnouvelle"),
    ],
    etapes: ["Filtrer au besoin : « Du », « Au », « Priorité », « État » (prise en charge, sans prise en charge, pas encore lue de tous), « Expéditeur », puis « Filtrer ».",
      "Lire la table : lues sur destinataires, nombre de réponses, première prise en charge.", "« Détail » ouvre les destinataires : lue le, état, répondu le.",
      "« Exporter » le même suivi, au même filtre : CSV, Excel, PDF ou JSON. « Actualiser » relit les réponses arrivées depuis.",
      "Dans le détail, « Relances et escalades » montre la cascade : l'étape attendue et son heure, puis la trace de chaque étape (qui a été visé, par qui). « Reprendre à l'étape … » la relance aussitôt.",
      "Dans le détail, la colonne « Note » montre la note de chaque destinataire ; « Ajouter » ou « Modifier » l'écrit (280 caractères, datée et signée), « Effacer » la retire. Elle ne figure jamais en clair au journal."],
    exemple: [["Du", "2026-11-01"], ["État", "Sans prise en charge"], ["Alerte", "Code bleu chambre 512"], ["Lues", "12 / 15"], ["Prise en charge", "Julie Roy, 2026-11-14 14:03"],
      ["Format d'export", "Excel (XLSX)"]],
    resultat: "Lecture seule : le suivi ne change rien aux alertes ; l'export est une pièce datée, à archiver.",
  },
  mesalertes: {
    titre: "Mes alertes",
    objet: "Lire les alertes qui vous visent et y répondre d'un geste.",
    qui: "Tout compte de l'organisme.",
    prealables: [
      modules("alertes"),
      compte("n'importe lequel"),
      p("Être destinataire : membre d'un groupe visé (ressource active de l'unité reliée à son compte), d'une liste de diffusion ou d'un groupe transverse, ou nommé personnellement par l'expéditeur.", "usressources"),
      p("Pour recevoir aussi le texto : un numéro de téléphone au dossier (Mon compte) et le fournisseur de textos de l'organisme (Intégrations).", "account"),
    ],
    etapes: ["Les non lues sont en tête, marquées « Non lue ».", "Ouvrir une alerte la marque lue.", "Répondre par un bouton (« J'arrive », « Indisponible ») : une seule réponse par alerte."],
    exemple: [["Alerte", "Critique — Code bleu chambre 512"], ["Réponse", "J'arrive"]],
    resultat: "« Réponse … envoyée » ; l'expéditeur voit votre réponse dans son suivi.",
    attention: "Une alerte critique vous arrive aussi par poussée, texto et courriel, même si vous avez fermé ces canaux pour les autres avis. Sans l'application, répondez au texto reçu : « 1 » (ou « OK ») pour prendre en charge, « 2 » (ou « NON ») pour refuser ; tout autre mot reçoit l'aide et ne compte pas.",
  },
  alparametres: {
    titre: "Paramètres des alertes",
    objet: "Tenir les réponses prédéfinies et les modèles d'alerte de l'organisme.",
    qui: "Administration (capacité « alertes.configure »).",
    prealables: [
      modules("alertes"),
      compte("administration (capacité « alertes.configure »)"),
      p("Pour le « Périmètre » d'un modèle et pour les étapes qui visent un groupe : les unités en vigueur des effectifs (Référentiels).", "usreferentiels"),
      p("Pour inscrire une personne à un groupe transverse : son compte existe (Comptes et rôles).", "admin"),
    ],
    etapes: ["« Nouvelle réponse » : un libellé et ce qu'il veut dire (prise en charge, refus, information).",
      "« Nouveau modèle » : titre, texte, priorité, réponses proposées, et son « Périmètre » (les groupes dont les expéditeurs le voient ; aucun coché : tout l'organisme).",
      "« Modifier » un modèle en fait une nouvelle version ; « Versions » montre chaque version, qui l'a faite et quand.",
      "« Désactiver » retire une réponse ou un modèle des choix ; rien ne s'efface.",
      "Réglages : cocher « Envoyer aussi une copie par courriel » pour que chacun reçoive l'alerte par courriel en plus de ses canaux.",
      "Politiques de relance : « Nouvelle politique », un nom, quand s'arrêter (une prise en charge, ou tous ont lu), puis chaque étape — combien de minutes après la précédente, qui viser (relancer ceux qui n'ont pas réagi, tout un groupe, une personne, ou le prochain disponible d'un groupe), par quels canaux — « Appel automatique » fait sonner la personne, qui confirme en appuyant sur 1. « Modifier » fait une version nouvelle.",
      "Utilisateurs actifs : choisir le « Mois » ; chaque personne qu'une alerte, une relance ou un appel a visée compte une fois, avec sa première tentative et l'alerte en cause. « Exporter » produit le justificatif du bordereau.",
      "Groupes transverses : « Nouveau groupe » (nom, nature : garde, spécialité, comité), puis « Gérer » pour inscrire une personne par son nom, pour une période, comme membre ou modératrice."],
    exemple: [["Libellé", "J'arrive"], ["Ce qu'elle veut dire", "Prise en charge"], ["Modèle — Titre", "Code bleu"], ["Priorité", "Critique"], ["Périmètre", "Garde de nuit"],
      ["Groupe transverse", "Garde de nuit (garde)"], ["Personne à inscrire", "Julie Roy, du 2026-12-21 au 2026-12-27"]],
    resultat: "La réponse, le modèle ou le groupe est proposé dès la prochaine alerte ; les alertes passées gardent ce qu'elles ont proposé, et la version du modèle dont elles sont parties.",
  },
  paiecycles: {
    titre: "Cycles de paie",
    objet: "Préparer une paie à partir des feuilles de temps approuvées, la relire, puis la traiter.",
    qui: "Administration, RH et comptabilité (capacité « paie.gerer »).",
    prealables: [
      modules("paie"),
      compte("administration, RH ou comptabilité (capacité « paie.gerer »)"),
      p("Les feuilles de temps approuvées de la période (module « effectifs_temps », Feuilles de temps (console)) ; si l'organisme exige la validation, validées dans Validation des temps.", "ustemps"),
      p("Un dossier de rémunération par personne (Dossiers de rémunération).", "paiedossiers"),
      p("Un taux pour chacun : l'échelle salariale et les primes (Règles de paie variable), ou un taux hors échelle au dossier.", "paieregles"),
      p("Les taux de l'organisme : FSS, CNESST, majoration des heures supplémentaires (Paramètres de la paie).", "paieparametres"),
      p("Le barème fiscal de l'année de paie, livré avec le produit ; sans lui : « aucun barème de paie publié pour <année> » — à demander au soutien.", "soutien"),
    ],
    etapes: ["« Préparer un cycle » : le début et la fin de la période de travail, puis la date de paie. Seules les feuilles approuvées dont la semaine commence dans la période entrent.",
      "Relire les bulletins : heures régulières et supplémentaires, brut, retenues, net, coût pour l'employeur ; « Détail » montre chaque gain (heures, primes, forfaits) et chaque retenue.",
      "« Registre de paie (PDF) » : une ligne par personne et les totaux, à relire avant de traiter ; « Fiche PDF » produit la fiche de paie d'une personne.",
      "Les avertissements nomment qui n'est pas payé : sans dossier, sans taux (ni au dossier, ni à l'échelle), ou feuilles que la paie n'a pas validées quand l'organisme l'exige. Corrigez, puis « Recalculer ».",
      "« Traiter la paie » : le cycle est recalculé une dernière fois, puis figé ; ses feuilles passent « payées ».",
      "« Annuler le cycle » abandonne un cycle préparé ; ses feuilles restent approuvées."],
    exemple: [["Début de la période", "2026-02-15"], ["Fin de la période", "2026-02-28"], ["Date de paie", "2026-03-05"]],
    resultat: "Les bulletins sont lisibles par chacun dans « Mes bulletins de paie », et les montants entrent dans les remises du mois.",
    attention: "Si une feuille a été rappelée ou rouverte entre la préparation et le traitement, le traitement est refusé en entier : recalculez, puis traitez de nouveau. Une feuille payée ne se corrige plus ; une erreur se règle à la paie suivante.",
  },
  paiedossiers: {
    titre: "Dossiers de rémunération",
    objet: "Dire, pour chaque personne des effectifs, comment elle est payée.",
    qui: "Administration et RH (capacité « paie.configurer »).",
    prealables: [
      modules("paie"),
      compte("administration ou RH (capacité « paie.configurer »)"),
      p("Les personnes viennent des ressources des effectifs : un dossier ne se tient que pour une ressource de l'organisme (Ressources).", "usressources"),
      p("Pour un taux tiré de l'échelle : l'échelle salariale (Règles de paie variable).", "paieregles"),
    ],
    etapes: ["« Créer » ou « Modifier » sur la ligne de la personne.",
      "Choisir la rémunération (à l'heure ou au salaire annuel) et la fréquence de paie.",
      "À l'heure, saisir l'échelon : le taux vient alors de l'échelle salariale (« Règles de paie variable »). Un taux horaire saisi est un taux hors échelle, qui l'emporte. Au salaire, saisir le salaire annuel.",
      "Au besoin, les montants personnels déclarés sur les formulaires TP-1015.3-V (Québec) et TD1 (fédéral) ; laissés vides, ce sont ceux de base de l'année."],
    exemple: [["Rémunération", "À l'heure"], ["Fréquence de paie", "Aux deux semaines (26 paies)"], ["Échelon", "3"]],
    resultat: "La prochaine paie préparée ou recalculée applique ce dossier.",
  },
  paiedeclarations: {
    titre: "Remises et relevés",
    objet: "Lire ce qu'il faut verser chaque mois et les cases des relevés de l'année.",
    qui: "Administration, RH et comptabilité (capacité « paie.gerer »).",
    prealables: [
      modules("paie"),
      compte("administration, RH ou comptabilité (capacité « paie.gerer »)"),
      p("Des cycles de paie traités (Cycles de paie) : un cycle préparé ou annulé n'y figure pas.", "paiecycles"),
    ],
    etapes: ["Choisir l'année.",
      "Remises mensuelles : le montant à verser à Revenu Québec (impôt du Québec, RRQ, RQAP, FSS) et à l'ARC (impôt fédéral, assurance-emploi), avec l'échéance du 15 du mois suivant.",
      "Relevés : pour chaque personne, les cases du RL-1 et du T4 à reporter."],
    exemple: [["Année", "2026"]],
    resultat: "Les montants ne comptent que les paies traitées ; un cycle préparé ou annulé n'y figure pas.",
    attention: "La transmission électronique des relevés et des remises se fait hors du produit.",
  },
  mesbulletins: {
    titre: "Mes bulletins de paie",
    objet: "Lire ses propres bulletins de paie, du brut au net.",
    qui: "Toute personne dont le compte est relié à une ressource des effectifs.",
    prealables: [
      modules("paie"),
      compte("n'importe lequel"),
      p("La ressource des effectifs de la personne est reliée à son compte (Gestion des quarts › Ressources, « Compte utilisateur ») : les bulletins sont ceux de cette ressource ; sans elle, aucun.", "usressources"),
      p("Un cycle de paie traité qui la comprend (Cycles de paie).", "paiecycles"),
    ],
    etapes: ["Ouvrir « Mes bulletins de paie » : la paie la plus récente d'abord.", "« Détail » montre les gains (heures, primes), chaque retenue, le net et ce que paie l'employeur.",
      "« Fiche PDF » télécharge la fiche de paie, avec le cumul de l'année, à conserver."],
    exemple: [["Bulletin", "Paie du 2026-03-05"]],
    resultat: "Vous voyez seulement vos bulletins, une fois la paie traitée.",
  },
  paieparametres: {
    titre: "Paramètres de la paie",
    objet: "Régler les taux propres à l'organisme et lire le barème de l'année.",
    qui: "Administration et RH (capacité « paie.configurer »).",
    prealables: [
      modules("paie"),
      compte("administration ou RH (capacité « paie.configurer »)"),
      p("Les taux à reporter : celui du FSS selon la masse salariale de l'organisme, et celui de l'avis de cotisation de la CNESST."),
    ],
    etapes: ["Saisir le taux du Fonds des services de santé (selon la masse salariale et le secteur ; 4,26 % au secteur public).",
      "Saisir le taux de la CNESST indiqué sur l'avis de cotisation de l'organisme.",
      "Saisir la majoration des heures supplémentaires : au moins 50 % (Loi sur les normes du travail), davantage si la convention le prévoit.",
      "Cocher « Exiger la validation des temps par la paie » pour que seules les feuilles validées entrent dans un cycle.",
      "« Enregistrer les réglages »."],
    exemple: [["Taux du FSS", "4,26"], ["Taux de la CNESST", "1,78"], ["Majoration des heures supplémentaires", "50"]],
    resultat: "Les prochains calculs appliquent ces taux ; les paies déjà traitées ne changent pas.",
  },
  paieregles: {
    titre: "Règles de paie variable",
    objet: "Tenir l'échelle salariale et les primes de l'organisme, sans livraison du produit.",
    qui: "Administration et RH (capacité « paie.configurer ») ; la comptabilité paie selon ces règles sans les modifier.",
    prealables: [
      modules("paie"),
      compte("administration ou RH (capacité « paie.configurer »)"),
      p("Pour cibler une règle : les titres d'emploi et les conventions des référentiels des effectifs (Référentiels).", "usreferentiels"),
      p("Pour qu'un échelon serve : le dossier de rémunération de la personne le porte (Dossiers de rémunération).", "paiedossiers"),
    ],
    etapes: ["« Nouvelle règle » : choisir le type — taux de base (échelle), prime en pourcentage du taux, prime horaire, ou forfait par paie.",
      "Donner le code du gain (il paraît sur la fiche de paie), le libellé et la valeur.",
      "Dire qui est visé : titre d'emploi, convention, échelon ; vides, la règle vise tout le personnel.",
      "Pour une prime, dire quelles heures : la plage (heure de Montréal ; une fin avant le début passe minuit), les jours, les fériés, les codes de paie.",
      "Donner la période de validité. Une augmentation se saisit comme une nouvelle règle qui commence à sa date ; l'ancienne se termine la veille.",
      "« Désactiver » retire une règle des prochains calculs, sans l'effacer."],
    exemple: [["Type", "Prime en pourcentage du taux"], ["Code", "PRIME_NUIT"], ["Libellé", "Prime de nuit"], ["Valeur", "12 %"],
      ["De", "00:00"], ["À", "08:00"], ["En vigueur à partir du", "2026-01-01"]],
    resultat: "Le prochain cycle préparé ou recalculé paie la prime ; une paie déjà traitée garde ses gains.",
    attention: "Une heure se paie au taux de base du jour où elle est travaillée : un cycle à cheval sur une augmentation montre deux lignes d'heures, une par taux.",
  },
  paietemps: {
    titre: "Validation des temps",
    objet: "Relire, avant la paie, les feuilles de la période et leurs anomalies, puis les valider ou les retourner.",
    qui: "Administration, RH et comptabilité (capacité « paie.gerer »). Valider ne remplace pas l'approbation du gestionnaire.",
    prealables: [
      modules("paie"),
      compte("administration, RH ou comptabilité (capacité « paie.gerer »)"),
      p("Des feuilles approuvées par l'encadrement (Feuilles de temps (console)).", "ustemps"),
      p("Un dossier de rémunération et un taux pour chaque personne, sinon l'anomalie « sans dossier » ou « sans taux » (Dossiers de rémunération).", "paiedossiers"),
      p("Pour que la validation conditionne la paie : cocher « Exiger la validation des temps par la paie » (Paramètres de la paie).", "paieparametres"),
    ],
    etapes: ["Choisir la période, puis « Afficher ».",
      "Lire les anomalies de chaque feuille : non approuvée, incalculable, sans dossier, sans taux, plus de 60 heures, validation périmée ; et les personnes sans feuille.",
      "« Valider les feuilles sans anomalie » d'un coup, puis « Valider » les autres une à une après vérification.",
      "« Retourner » une feuille approuvée qui contient une erreur : dire ce qu'il faut corriger ; elle revient à la personne."],
    exemple: [["Du", "2026-02-15"], ["Au", "2026-02-28"], ["Motif d'un retour", "Le quart du 23 février manque"]],
    resultat: "Quand l'organisme exige la validation, seules les feuilles validées entrent dans le prochain cycle de paie.",
    attention: "Une feuille validée dont les heures changent ensuite (rouverte, corrigée, réapprouvée) perd sa validation : elle est à revalider.",
  },
  rapports: {
    titre: "Constructeur de rapports",
    objet: "Composer un rapport sur les données d'effectifs : source, colonnes, filtres, tri, agrégat ; l'exécuter, l'enregistrer et l'exporter.",
    qui: "Administration et planificateurs.",
    prealables: [
      compte("administration ou gestionnaire ; chaque source exige sa capacité, « effectifs.manage » pour les sources d'effectifs"),
      p("Les sources proposées sont celles des modules accordés à l'organisme (par l'exploitant, Administration › Superadmin)."),
      perimetre("Lecture au moins", "un gestionnaire ne compte que les unités de son périmètre."),
      p("Des données à compter : périodes publiées, absences, feuilles de temps… (Périodes).", "usperiodes"),
    ],
    etapes: [
      "Choisir la « Source de données », puis cocher les colonnes.",
      "« Ajouter un filtre » : champ, opérateur, valeur (et seconde borne pour « entre »).",
      "Trier, agréger au besoin, puis exécuter ; nommer le rapport pour l'enregistrer et choisir le « Format » d'export.",
    ],
    exemple: [["Source de données", "Quarts et places"], ["Champ du filtre", "Date"], ["Opérateur du filtre", "entre"], ["Du", "2026-11-01"], ["Au", "2026-11-30"], ["Trier par", "Milieu"], ["Nom du rapport", "Taux de comblement — novembre"], ["Format", "Excel (XLSX)"]],
    resultat: "Le rapport enregistré se relance tel quel ; l'export porte les mêmes colonnes et filtres.",
  },
  approvals: {
    titre: "Approbations",
    objet: "Trancher les demandes qui vous sont adressées, régler les règles d'approbation et déléguer vos approbations pendant une absence.",
    qui: "Toute personne qui encadre ou a reçu une délégation.",
    prealables: [
      modules("approvals"),
      compte("administration, gestionnaire ou direction pour décider ; administration ou gestionnaire pour les règles"),
      p("Les types de document, tenus par l'administration, puis les règles d'approbation (onglet « Règles ») : sans règle, aucune demande n'est adressée."),
      p("Pour déléguer : le délégataire a un compte qui peut approuver (Comptes et rôles).", "admin"),
    ],
    etapes: [
      "Onglet « Demandes » : ouvrir une demande, puis « Approuver » ou « Rejeter » avec un commentaire.",
      "Onglet « Règles » : type de document, champ, opérateur, valeur, niveau et rôle approbateur.",
      "« Déléguer mes approbations » : choisir le délégataire et la période.",
    ],
    exemple: [["Délégataire", "Denis Lavoie"], ["Du", "2026-12-21"], ["Au", "2027-01-04"]],
    resultat: "La demande avance au palier suivant ou se clôt ; la délégation s'arrête d'elle-même à son terme.",
    attention: "On n'approuve jamais sa propre demande : c'est la séparation des tâches.",
  },

  // ── Administration ────────────────────────────────────────────────────────────────────────
  usreferentiels: {
    titre: "Référentiels",
    objet: "Tenir les listes de valeurs datées dont tout dépend : unités, corps d'emploi, statuts d'emploi, natures de quart, types d'heures, codes d'absence, types de milieu.",
    qui: "Administration.",
    prealables: [
      modules("effectifs"),
      compte(CONFIGURATION_EFFECTIFS + " ; les autres rôles lisent"),
      p("Dans l'ordre : les unités (une unité peut avoir une parente), puis les corps d'emploi avant les titres d'emploi qui s'y rattachent ; chaque valeur en vigueur à la date où l'on s'en servira."),
    ],
    etapes: [
      "Choisir le « Référentiel », puis ajouter une valeur : code, libellé (français, anglais facultatif), comportement et date d'entrée en vigueur.",
      "« Fermer la valeur » à une date plutôt que la supprimer : l'historique reste juste.",
      "Promouvoir la configuration d'un environnement à l'autre par un paquet JSON (exporter, puis appliquer).",
    ],
    exemple: [["Référentiel", "Codes d'absence"], ["Code", "MAL"], ["Libellé (français)", "Maladie"], ["Comportement dans le logiciel", "ABSENCE_PAYEE"], ["En vigueur le", "2026-01-01"]],
    resultat: "La valeur devient choisissable à partir de sa date ; une valeur fermée ne s'offre plus, mais les données passées la gardent.",
  },
  uscodesabsence: {
    titre: "Codes d'absence",
    objet: "Tenir la liste des motifs qu'une personne choisit en déclarant une absence : maladie, vacances, formation, congé parental…",
    qui: "Administration (capacité « effectifs.configure »).",
    prealables: [
      modules("effectifs", "effectifs_absences"),
      compte(CONFIGURATION_EFFECTIFS),
      p("Pour réserver un code à certains titres d'emploi : ces titres existent (Référentiels).", "usreferentiels"),
    ],
    etapes: [
      "« Ajouter une valeur » : le code, le libellé en français (et en anglais au besoin), le comportement dans le logiciel (payée ou non), la durée et la planification, puis la date d'entrée en vigueur.",
      "« Remplacer » pour modifier un code à partir d'une date : le libellé, la durée ou le comportement changent pour l'avenir, le passé reste tel qu'il était.",
      "« Fermer » pour retirer un code à une date : il ne s'offre plus dans « Mes absences », mais les absences déjà déclarées le gardent.",
      "« Historique » montre toutes les versions d'un code."],
    exemple: [["Code", "FORM"], ["Libellé (français)", "Formation"], ["Comportement dans le logiciel", "Absence payée"], ["En vigueur le", "2026-12-01"]],
    resultat: "Le code apparaît (ou disparaît) dans la liste « Code d'absence » de « Mes absences » et de « Absences » à la date choisie.",
    attention: "On ne supprime pas un code : on le ferme. Une absence passée doit toujours pouvoir dire sous quel code elle a été déclarée.",
  },
  usregles: {
    titre: "Règles des effectifs",
    objet: "Écrire les règles de l'organisme par contexte (disponibilités, absences, comblement, échanges, feuille de temps), les simuler, puis les publier.",
    qui: "Administration.",
    prealables: [
      modules("effectifs", "effectifs_regles"),
      compte(CONFIGURATION_EFFECTIFS + " ; la lecture des décisions demande « effectifs.manage »"),
      p("Pour une règle à portée : les conventions, statuts, corps ou titres d'emploi visés (Référentiels).", "usreferentiels"),
      p("Une date d'entrée en vigueur aujourd'hui ou plus tard : « une règle ne peut pas prendre effet dans le passé »."),
    ],
    etapes: [
      "Choisir le « Contexte », puis composer la condition : fait, comparaison, valeur visée.",
      "Choisir l'« Action » (accepter, avertir, exiger une approbation, refuser) et le « Message affiché à la personne ».",
      "« Aperçu d'impact » puis « Simulateur » pour juger un cas ; « Publier » avec la date d'entrée en vigueur.",
    ],
    exemple: [["Contexte", "ECHANGE"], ["Clé de la règle", "entre-periodes"], ["Fait", "memePeriode"], ["Comparaison", "="], ["Valeur visée", "faux"], ["Action", "EXIGER_APPROBATION"], ["Message affiché à la personne", "Échange entre deux périodes : le chef valide"]],
    resultat: "La règle s'applique aux gestes faits à partir de sa date ; l'onglet « Décisions » montre chaque décision et ses motifs.",
  },
  usmilieux: {
    titre: "Milieux physiques",
    objet: "Déclarer les lieux de travail (casernes, centres) avec leur position et leur rayon, et les preuves de présence admises.",
    qui: "Administration.",
    prealables: [
      modules("effectifs"),
      compte(ENCADREMENT_EFFECTIFS),
      perimetre("Gestion", "créer ou modifier un milieu l'exige sur son unité."),
      p("Les référentiels : le type de milieu et l'unité (Référentiels).", "usreferentiels"),
    ],
    etapes: [
      "Créer un milieu : code, libellé, type, unité, adresse, latitude, longitude et rayon.",
      "Cocher les « Preuves de présence » acceptées (code sur place, code QR, position, réseau) et celles qui sont exigées.",
    ],
    exemple: [["Code", "C12"], ["Libellé", "Caserne 12"], ["Type", "CASERNE"], ["Unité", "OPS"], ["Latitude", "45.5225"], ["Longitude", "-73.5960"], ["Rayon en mètres (10 à 5 000)", "150"], ["Plages réseau", "10.12.0.0/16"]],
    resultat: "Le pointage juge la distance et la preuve selon ce milieu.",
  },
  usperimetres: {
    titre: "Périmètres",
    objet: "Dire qui gère quelle unité, à quel niveau et depuis quand : sans périmètre, une console s'ouvre sur des listes vides.",
    qui: "Administration.",
    prealables: [
      modules("effectifs"),
      compte(CONFIGURATION_EFFECTIFS),
      p("Le compte à qui l'on donne l'accès existe (Comptes et rôles).", "admin"),
      p("L'unité en vigueur à la date « À partir du » (Référentiels).", "usreferentiels"),
    ],
    etapes: [
      "Choisir le « Compte ».",
      "« Ajouter un accès » : unité, étendue (l'unité seule ou son sous-arbre), niveau (lecture, planification, gestion), « À partir du ».",
    ],
    exemple: [["Compte", "claire.nadeau"], ["Unité", "OPS"], ["Étendue", "Sous-arbre"], ["Niveau", "Planification"], ["À partir du", "2026-11-14"]],
    resultat: "Les consoles de ce compte montrent désormais les personnes et les quarts de l'unité.",
    attention: "Le refus est la règle : un accès oublié n'ouvre rien, et c'est voulu.",
  },
  usintegrations: {
    titre: "Intégrations des effectifs",
    objet: "Régler les connecteurs (paie, bus d'entreprise, entrepôt de données, horodateurs) et suivre leur boîte d'envoi.",
    qui: "Administration.",
    prealables: [
      modules("effectifs", "effectifs_integrations"),
      compte(CONFIGURATION_EFFECTIFS + " pour régler les connecteurs ; " + ENCADREMENT_EFFECTIFS + ", dans son périmètre, pour suivre la boîte d'envoi"),
      p("Les coordonnées du système visé (dépôt SFTP, bus, entrepôt), fournies par son exploitant."),
    ],
    etapes: [
      "Activer un connecteur et choisir son mode.",
      "Suivre la « Boîte d'envoi » : état de chaque message, reprise d'un envoi en échec.",
    ],
    exemple: [["Connecteur", "Paie — lot CSV"], ["Connecteur activé", "Oui"], ["Mode", "Dépôt SFTP"]],
    resultat: "Les lots de paie et les équipages partent selon le connecteur ; un échec reste en boîte jusqu'à sa reprise.",
  },
  usretention: {
    titre: "Rétention des effectifs",
    objet: "Fixer combien de temps chaque classe de données est gardée, et purger ce qui a dépassé sa durée.",
    qui: "Administration (responsable de la protection des renseignements personnels).",
    prealables: [
      modules("effectifs"),
      compte(CONFIGURATION_EFFECTIFS),
      p("La politique de conservation de l'organisme, fixée par la personne responsable de la protection des renseignements personnels."),
    ],
    etapes: ["« Modifier la politique » d'une classe : durée et motif.", "Appliquer la purge : seules les données échues sont touchées."],
    exemple: [["Classe", "Pointages et localisations"], ["Durée", "24 mois"]],
    resultat: "La purge est journalisée ; rien de plus récent que la durée n'est retiré.",
  },
  usjournal: {
    titre: "Journal des décisions",
    objet: "Retrouver qui a fait quoi, quand, et par quel mécanisme : chaque décision des règles et chaque geste de l'encadrement.",
    qui: "Administration, direction, vérification.",
    prealables: [
      modules("effectifs"),
      compte(ENCADREMENT_EFFECTIFS),
      perimetre("Lecture au moins", "un gestionnaire ne lit que les entrées des unités de son périmètre."),
    ],
    etapes: ["Filtrer par période (du, au exclu), unité, acteur ou action.", "Ouvrir le « Détail » d'une entrée ; exporter au format voulu."],
    exemple: [["Du", "2026-11-01"], ["Au (exclu)", "2026-12-01"], ["Action", "attribution forcée"], ["Format d'export", "PDF"]],
    resultat: "L'export porte les mêmes filtres que l'écran.",
  },
  admin: {
    titre: "Utilisateurs et rôles",
    objet: "Créer les comptes de l'organisme, leur donner un rôle, tenir les clés d'API, l'audit, la confidentialité et les tâches différées.",
    qui: "Administration de l'organisme.",
    prealables: [
      p("L'exploitant crée l'organisme avec son compte d'administration (Administration › Superadmin : identifiant et mot de passe de l'administrateur)."),
      p("Se connecter avec le rôle administration de l'organisme."),
      p("Pour lier un compte à un employé : sa fiche du personnel existe ; elle se crée aussi avec la ressource des effectifs (Ressources).", "usressources"),
      p("Avec Entra ID : l'annuaire déclaré et sa table groupes → rôles (Intégrations).", "integrations"),
    ],
    etapes: [
      "« Comptes et rôles » : identifiant, rôle, mot de passe provisoire, lien facultatif à l'employé.",
      "« Rôles et accès » : composer un rôle propre à l'organisme à partir des capacités.",
      "« Journal d'audit », « Confidentialité » (droit à l'oubli), « Clés d'API ».",
    ],
    exemple: [["Identifiant", "claire.nadeau"], ["Rôle", "manager"], ["Mot de passe provisoire", "(généré, changé à la première connexion)"]],
    resultat: "La personne se connecte, change son mot de passe et configure son second facteur ; avec Entra ID, le compte se crée à la première connexion.",
    attention: "Avec l'authentification Entra ID, le rôle vient des groupes de l'annuaire : voir UTILISATEURS-PROFILS-ACCES.md.",
  },
  access: {
    titre: "Accès",
    objet: "Restreindre un objet au-delà du rôle, accorder un droit daté à une personne, et revoir qui accède à quoi.",
    qui: "Administration.",
    prealables: [
      compte("administration"),
      p("Les comptes visés par un octroi existent (Comptes et rôles)."),
    ],
    etapes: [
      "« Objets protégés » : code de l'objet, libellé, permission.",
      "« Octrois datés » : sujet, objet, permission, « À partir du », « Jusqu'au », pourquoi.",
      "« Qui accède à quoi » : choisir un objet et une date d'observation ; exporter pour la revue trimestrielle.",
    ],
    exemple: [["Type de sujet", "Personne"], ["Identifiant du sujet", "denis.lavoie"], ["Objet", "Feuilles de temps — Est"], ["Permission", "approuver"], ["À partir du", "2026-12-21"], ["Jusqu'au (facultatif)", "2027-01-04"], ["Pourquoi", "Intérim de Claire Nadeau"]],
    resultat: "À l'échéance, l'octroi tombe de lui-même.",
  },
  notifications: {
    titre: "Avis",
    objet: "Régler les avis envoyés au personnel (courriel, texto, poussée) événement par événement, et suivre leur consommation.",
    qui: "Administration, ressources humaines, planificateurs.",
    prealables: [
      modules("notifications"),
      compte("administration, gestionnaire ou RH"),
      p("Pour les textos : le fournisseur de textos de l'organisme (Intégrations) ; chacun choisit ensuite ses canaux dans Mon compte.", "integrations"),
    ],
    etapes: ["Choisir un événement, puis les canaux et le gabarit.", "Lire la consommation du mois (textos en segments, courriels)."],
    exemple: [["Événement", "Quart offert"], ["Canaux", "Texto, poussée"]],
    resultat: "Les personnes reçoivent l'avis selon leurs propres préférences, qu'elles règlent dans Mon compte.",
  },
  integrations: {
    titre: "Intégrations",
    objet: "Brancher l'organisme à ses systèmes : annuaires (Entra ID, SAML), webhooks, fournisseur de textos, antivirus.",
    qui: "Administration.",
    prealables: [
      modules("integrations"),
      compte("administration"),
      p("Pour Entra ID : l'application inscrite dans l'annuaire de l'organisme (ID du locataire, ID d'application), et son secret posé par l'exploitation dans une variable d'environnement du serveur."),
    ],
    etapes: ["Déclarer un annuaire Entra ID : ID du locataire, ID d'application, variable du secret, domaines admis, table groupes → rôles.", "Ajouter un webhook : URL, événements."],
    exemple: [["Annuaire", "Urgences-santé — Entra ID"], ["Domaines admis", "urgences-sante.qc.ca"], ["Groupe → rôle", "GEPS-Planification → manager"]],
    resultat: "Le bouton de connexion de l'annuaire paraît sur la page de connexion de l'organisme.",
  },
  apidocs: {
    titre: "API (OpenAPI)",
    objet: "Lire la description OpenAPI de toutes les routes et les essayer avec son propre compte.",
    qui: "Administration, direction, intégrateurs.",
    prealables: [
      compte("administration ou direction"),
      p("Les routes essayées répondent selon vos droits et les modules accordés à l'organisme."),
    ],
    etapes: ["Ouvrir une route, lire ses paramètres et ses réponses.", "« Essayer » : l'appel part avec vos droits, pas plus."],
    exemple: [["Route", "GET /api/effectifs/mon-horaire?du=2026-11-16&au=2026-11-22"]],
    resultat: "La réponse réelle s'affiche ; un refus dit quel droit manque.",
  },
  superadmin: {
    titre: "Organismes (exploitation)",
    objet: "Créer un organisme, lui accorder ses modules, régler ses sous-menus et ses sauvegardes.",
    qui: "Exploitant de la plateforme seulement.",
    prealables: [
      p("Un compte superadmin de la plateforme, remis par l'exploitation."),
      p("Le plan d'abonnement de l'organisme : un module hors abonnement reste fermé."),
    ],
    etapes: ["Créer l'organisme, puis cocher ses modules.", "Masquer au besoin des vues de menu ; planifier les sauvegardes."],
    exemple: [["Organisme", "Urgences-santé"], ["Modules", "effectifs, effectifs_planification, effectifs_communication…"]],
    resultat: "Le menu de l'organisme suit ses modules ; un module coupé ferme aussi ses routes.",
  },

  // ── Aide et compte ────────────────────────────────────────────────────────────────────────
  account: {
    titre: "Mon compte",
    objet: "Tenir son identité, sa photo, son mot de passe, son second facteur, ses clés d'accès et ses préférences d'avis.",
    qui: "Tous les comptes.",
    prealables: [
      compte("n'importe lequel"),
      p("À la première connexion : changer le mot de passe provisoire et configurer le second facteur."),
    ],
    etapes: ["Changer son mot de passe (le collage est accepté).", "Ajouter une clé d'accès (passkey) ou régler l'application d'authentification.", "Choisir ses canaux d'avis : au moins un canal écrit reste ouvert."],
    exemple: [["Canaux", "Courriel et poussée"]],
    resultat: "Les préférences s'appliquent au prochain avis.",
  },
  help: {
    titre: "Guide",
    objet: "Le présent guide : notions, déroulements, et une fiche par écran avec un exemple de remplissage.",
    qui: "Tous les comptes.",
    prealables: [
      p("Un compte actif de l'organisme : le guide ne montre que les fiches des modules accordés et des vues gardées au menu."),
    ],
    etapes: ["Le bouton « Aide sur cet écran », en haut de chaque page, ouvre directement la fiche de l'écran."],
    exemple: [["Lien", "/admin/help?section=fiche-matemps"]],
    resultat: "La fiche s'ouvre et reçoit le focus.",
  },
  soutien: {
    titre: "Soutien",
    objet: "Ouvrir un billet auprès du soutien, suivre sa réponse et lire les niveaux et délais de service.",
    qui: "Tous les comptes.",
    prealables: [
      compte("n'importe lequel"),
      p("Rien d'autre à configurer : le niveau et les délais de service suivent l'entente de l'organisme."),
    ],
    etapes: ["« Ouvrir un billet » : sujet, gravité, description, capture au besoin.", "Suivre « Mes billets » et donner son avis à la fermeture."],
    exemple: [["Sujet", "Le bouton Appeler ne sonne pas chez mon collègue"], ["Gravité", "Élevée"]],
    resultat: "Le billet reçoit un numéro ; le délai de réponse dépend de sa gravité.",
  },
};
