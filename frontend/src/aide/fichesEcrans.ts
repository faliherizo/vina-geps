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

export interface Fiche {
  titre: string;
  /** À quoi sert l'écran, en une phrase. */
  objet: string;
  /** Qui l'ouvre (rôle ou profil). */
  qui: string;
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

export const FICHES: Record<Page, Fiche> = {
  // ── Menu principal ────────────────────────────────────────────────────────────────────────
  dashboard: {
    titre: "Tableau de bord",
    objet: "Lire sa journée : pour l'encadrement, la couverture des quarts, les absences à traiter, les demandes en attente et les pointages à approuver ; pour l'employé, ses sept prochains jours.",
    qui: "Tous les comptes ; la vue gestionnaire s'ouvre à l'administration, à la direction et aux planificateurs.",
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
    etapes: [
      "« Préparer ma feuille » avec le « Début de la période » et la « Fin de la période » : les segments viennent des quarts et des pointages.",
      "Vérifier chaque segment : la ressource (nom et matricule), les heures, le type d'heure, les « Tâches » et les champs propres à votre organisme.",
      "« Ajouter un segment » pour un temps manquant (jour, de, à, type d'heure), avec la « Description des tâches » et les champs demandés (ex. Véhicule) ; ou modifier un segment et « Enregistrer la correction ».",
      "« Soumettre ma feuille » : elle part au premier palier d'approbation — souvent votre supérieur hiérarchique. « En attente de » dit chez qui elle est, et, s'il est absent, qui le remplace (« en son absence : … »).",
    ],
    exemple: [["Début de la période", "2026-11-09"], ["Fin de la période", "2026-11-15"], ["Ajouter un segment — Jour", "2026-11-12"], ["De", "18:00"], ["À", "19:30"], ["Type d'heure", "SUPP — Supplémentaire"],
      ["Description des tâches", "Transport prolongé, appel 412"], ["Véhicule (obligatoire)", "A-12"]],
    resultat: "La feuille passe à « Soumise » et affiche « En attente de : … » ; chaque correction garde ses versions, sa description et ses champs, et une anomalie est nommée avec ce qu'il faut corriger.",
    attention: "Une feuille retournée porte son motif : corriger puis soumettre à nouveau. Un champ marqué « obligatoire » doit être rempli pour que le segment soit accepté.",
  },
  monequipe: {
    titre: "Mon équipe",
    objet: "Voir les personnes qui relèvent de soi, réorganiser son équipe, suivre les feuilles à approuver et désigner un suppléant pendant son absence.",
    qui: "Gestionnaires, direction, RH et administration ; réorganiser exige le droit de corriger une fiche (gestion, RH, administration).",
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
    etapes: [
      "Ajouter une ressource avec son matricule, son nom, son unité, son corps d'emploi et son statut.",
      "Renseigner l'« Ancienneté (selon la convention) » : elle ordonne l'AGPH et les remplacements.",
      "Relier la ressource au compte de la personne (« Personne ») : sans ce lien, ses écrans personnels restent vides.",
    ],
    exemple: [["Matricule", "P100"], ["Nom", "Julie Roy"], ["Unité", "OPS"], ["Corps d'emploi", "PARA"], ["Statut", "TC — Temps complet"], ["Ancienneté (selon la convention)", "120"], ["Personne", "julie.roy"]],
    resultat: "La personne devient planifiable et voit son horaire, ses disponibilités et sa feuille de temps.",
  },
  usquotas: {
    titre: "Quotas",
    objet: "Fixer le nombre d'équipages opérationnels visés par jour, par unité, plage ou nature de quart, et poser une surcharge temporaire.",
    qui: "Planificateurs et direction.",
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
    etapes: [
      "« Préparer les feuilles » pour une unité et une période.",
      "Ouvrir une feuille soumise : approuver, ou retourner avec un motif obligatoire.",
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
    etapes: [
      "Choisir un « Modèle » au besoin : il remplit le titre, le message, la priorité et les réponses ; tout reste modifiable.",
      "Écrire le « Titre » et le « Message », puis choisir la « Priorité ». Une alerte critique part sur tous les canaux, quelles que soient les préférences ; elle est réservée à qui en a le droit (direction, administration, ou un droit accordé par l'organisme).",
      "Choisir les destinataires : « Tout le groupe … » (une unité ou un groupe transverse, comme une garde), « La liste … » (une liste de distribution), ou des personnes, par leur nom, dans « Choisir des personnes dans un groupe ».",
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
    qui: "Administration, direction et planificateurs : le suivi est partagé entre expéditeurs.",
    etapes: ["Filtrer au besoin : « Du », « Au », « Priorité », « État » (prise en charge, sans prise en charge, pas encore lue de tous), « Expéditeur », puis « Filtrer ».",
      "Lire la table : lues sur destinataires, nombre de réponses, première prise en charge.", "« Détail » ouvre les destinataires : lue le, état, répondu le.",
      "« Exporter » le même suivi, au même filtre : CSV, Excel, PDF ou JSON. « Actualiser » relit les réponses arrivées depuis."],
    exemple: [["Du", "2026-11-01"], ["État", "Sans prise en charge"], ["Alerte", "Code bleu chambre 512"], ["Lues", "12 / 15"], ["Prise en charge", "Julie Roy, 2026-11-14 14:03"],
      ["Format d'export", "Excel (XLSX)"]],
    resultat: "Lecture seule : le suivi ne change rien aux alertes ; l'export est une pièce datée, à archiver.",
  },
  mesalertes: {
    titre: "Mes alertes",
    objet: "Lire les alertes qui vous visent et y répondre d'un geste.",
    qui: "Tout compte de l'organisme.",
    etapes: ["Les non lues sont en tête, marquées « Non lue ».", "Ouvrir une alerte la marque lue.", "Répondre par un bouton (« J'arrive », « Indisponible ») : une seule réponse par alerte."],
    exemple: [["Alerte", "Critique — Code bleu chambre 512"], ["Réponse", "J'arrive"]],
    resultat: "« Réponse … envoyée » ; l'expéditeur voit votre réponse dans son suivi.",
    attention: "Une alerte critique vous arrive aussi par poussée, texto et courriel, même si vous avez fermé ces canaux pour les autres avis.",
  },
  alparametres: {
    titre: "Paramètres des alertes",
    objet: "Tenir les réponses prédéfinies et les modèles d'alerte de l'organisme.",
    qui: "Administration (capacité « alertes.configure »).",
    etapes: ["« Nouvelle réponse » : un libellé et ce qu'il veut dire (prise en charge, refus, information).",
      "« Nouveau modèle » : titre, texte, priorité, réponses proposées, et son « Périmètre » (les groupes dont les expéditeurs le voient ; aucun coché : tout l'organisme).",
      "« Modifier » un modèle en fait une nouvelle version ; « Versions » montre chaque version, qui l'a faite et quand.",
      "« Désactiver » retire une réponse ou un modèle des choix ; rien ne s'efface.",
      "Réglages : cocher « Envoyer aussi une copie par courriel » pour que chacun reçoive l'alerte par courriel en plus de ses canaux.",
      "Groupes transverses : « Nouveau groupe » (nom, nature : garde, spécialité, comité), puis « Gérer » pour inscrire une personne par son nom, pour une période, comme membre ou modératrice."],
    exemple: [["Libellé", "J'arrive"], ["Ce qu'elle veut dire", "Prise en charge"], ["Modèle — Titre", "Code bleu"], ["Priorité", "Critique"], ["Périmètre", "Garde de nuit"],
      ["Groupe transverse", "Garde de nuit (garde)"], ["Personne à inscrire", "Julie Roy, du 2026-12-21 au 2026-12-27"]],
    resultat: "La réponse, le modèle ou le groupe est proposé dès la prochaine alerte ; les alertes passées gardent ce qu'elles ont proposé, et la version du modèle dont elles sont parties.",
  },
  rapports: {
    titre: "Constructeur de rapports",
    objet: "Composer un rapport sur les données d'effectifs : source, colonnes, filtres, tri, agrégat ; l'exécuter, l'enregistrer et l'exporter.",
    qui: "Administration et planificateurs.",
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
    etapes: [
      "Choisir le « Référentiel », puis ajouter une valeur : code, libellé (français, anglais facultatif), comportement et date d'entrée en vigueur.",
      "« Fermer la valeur » à une date plutôt que la supprimer : l'historique reste juste.",
      "Promouvoir la configuration d'un environnement à l'autre par un paquet JSON (exporter, puis appliquer).",
    ],
    exemple: [["Référentiel", "Codes d'absence"], ["Code", "MAL"], ["Libellé (français)", "Maladie"], ["Comportement dans le logiciel", "ABSENCE_PAYEE"], ["En vigueur le", "2026-01-01"]],
    resultat: "La valeur devient choisissable à partir de sa date ; une valeur fermée ne s'offre plus, mais les données passées la gardent.",
  },
  usregles: {
    titre: "Règles des effectifs",
    objet: "Écrire les règles de l'organisme par contexte (disponibilités, absences, comblement, échanges, feuille de temps), les simuler, puis les publier.",
    qui: "Administration.",
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
    etapes: ["« Modifier la politique » d'une classe : durée et motif.", "Appliquer la purge : seules les données échues sont touchées."],
    exemple: [["Classe", "Pointages et localisations"], ["Durée", "24 mois"]],
    resultat: "La purge est journalisée ; rien de plus récent que la durée n'est retiré.",
  },
  usjournal: {
    titre: "Journal des décisions",
    objet: "Retrouver qui a fait quoi, quand, et par quel mécanisme : chaque décision des règles et chaque geste de l'encadrement.",
    qui: "Administration, direction, vérification.",
    etapes: ["Filtrer par période (du, au exclu), unité, acteur ou action.", "Ouvrir le « Détail » d'une entrée ; exporter au format voulu."],
    exemple: [["Du", "2026-11-01"], ["Au (exclu)", "2026-12-01"], ["Action", "attribution forcée"], ["Format d'export", "PDF"]],
    resultat: "L'export porte les mêmes filtres que l'écran.",
  },
  admin: {
    titre: "Utilisateurs et rôles",
    objet: "Créer les comptes de l'organisme, leur donner un rôle, tenir les clés d'API, l'audit, la confidentialité et les tâches différées.",
    qui: "Administration de l'organisme.",
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
    etapes: ["Choisir un événement, puis les canaux et le gabarit.", "Lire la consommation du mois (textos en segments, courriels)."],
    exemple: [["Événement", "Quart offert"], ["Canaux", "Texto, poussée"]],
    resultat: "Les personnes reçoivent l'avis selon leurs propres préférences, qu'elles règlent dans Mon compte.",
  },
  integrations: {
    titre: "Intégrations",
    objet: "Brancher l'organisme à ses systèmes : annuaires (Entra ID, SAML), webhooks, fournisseur de textos, antivirus.",
    qui: "Administration.",
    etapes: ["Déclarer un annuaire Entra ID : ID du locataire, ID d'application, variable du secret, domaines admis, table groupes → rôles.", "Ajouter un webhook : URL, événements."],
    exemple: [["Annuaire", "Urgences-santé — Entra ID"], ["Domaines admis", "urgences-sante.qc.ca"], ["Groupe → rôle", "GEPS-Planification → manager"]],
    resultat: "Le bouton de connexion de l'annuaire paraît sur la page de connexion de l'organisme.",
  },
  apidocs: {
    titre: "API (OpenAPI)",
    objet: "Lire la description OpenAPI de toutes les routes et les essayer avec son propre compte.",
    qui: "Administration, direction, intégrateurs.",
    etapes: ["Ouvrir une route, lire ses paramètres et ses réponses.", "« Essayer » : l'appel part avec vos droits, pas plus."],
    exemple: [["Route", "GET /api/effectifs/mon-horaire?du=2026-11-16&au=2026-11-22"]],
    resultat: "La réponse réelle s'affiche ; un refus dit quel droit manque.",
  },
  superadmin: {
    titre: "Organismes (exploitation)",
    objet: "Créer un organisme, lui accorder ses modules, régler ses sous-menus et ses sauvegardes.",
    qui: "Exploitant de la plateforme seulement.",
    etapes: ["Créer l'organisme, puis cocher ses modules.", "Masquer au besoin des vues de menu ; planifier les sauvegardes."],
    exemple: [["Organisme", "Urgences-santé"], ["Modules", "effectifs, effectifs_planification, effectifs_communication…"]],
    resultat: "Le menu de l'organisme suit ses modules ; un module coupé ferme aussi ses routes.",
  },

  // ── Aide et compte ────────────────────────────────────────────────────────────────────────
  account: {
    titre: "Mon compte",
    objet: "Tenir son identité, sa photo, son mot de passe, son second facteur, ses clés d'accès et ses préférences d'avis.",
    qui: "Tous les comptes.",
    etapes: ["Changer son mot de passe (le collage est accepté).", "Ajouter une clé d'accès (passkey) ou régler l'application d'authentification.", "Choisir ses canaux d'avis : au moins un canal écrit reste ouvert."],
    exemple: [["Canaux", "Courriel et poussée"]],
    resultat: "Les préférences s'appliquent au prochain avis.",
  },
  help: {
    titre: "Guide",
    objet: "Le présent guide : notions, déroulements, et une fiche par écran avec un exemple de remplissage.",
    qui: "Tous les comptes.",
    etapes: ["Le bouton « Aide sur cet écran », en haut de chaque page, ouvre directement la fiche de l'écran."],
    exemple: [["Lien", "/admin/help?section=fiche-matemps"]],
    resultat: "La fiche s'ouvre et reçoit le focus.",
  },
  soutien: {
    titre: "Soutien",
    objet: "Ouvrir un billet auprès du soutien, suivre sa réponse et lire les niveaux et délais de service.",
    qui: "Tous les comptes.",
    etapes: ["« Ouvrir un billet » : sujet, gravité, description, capture au besoin.", "Suivre « Mes billets » et donner son avis à la fermeture."],
    exemple: [["Sujet", "Le bouton Appeler ne sonne pas chez mon collègue"], ["Gravité", "Élevée"]],
    resultat: "Le billet reçoit un numéro ; le délai de réponse dépend de sa gravité.",
  },
};
