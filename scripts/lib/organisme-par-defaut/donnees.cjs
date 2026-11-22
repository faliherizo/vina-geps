/**
 * **Ce que contient un organisme par défaut** — pur : aucune entrée-sortie, aucune horloge, aucun hasard.
 *
 * Le script `scripts/creer-organisme-par-defaut.cjs` joue ces données contre l'API ; l'essai
 * `organisme-par-defaut.test.cjs` les relit. Les dates et les mots de passe sont reçus en paramètre : ainsi
 * l'essai fixe le jour et le tirage, et la même entrée donne toujours le même organisme.
 *
 * L'ordre des constantes est l'ordre du paramétrage, celui que suivent les fiches d'aide
 * (docs/ORGANISME-PAR-DEFAUT.md) : une valeur ne vise qu'une valeur déjà en vigueur.
 */

/** Premier jour d'effet des référentiels : antérieur à tout ce que l'organisme planifie. */
const EFFECTIF_DEPUIS = "2026-01-01";

/**
 * Familles du catalogue de modules qui ne sont pas des écrans du produit : options d'hébergement (BYOK, infrastructure
 * dédiée) qui se règlent au déploiement. Toutes les autres familles sont activées.
 */
const FAMILLES_HORS_PRODUIT = new Set(["infrastructure"]);

/**
 * Repli si le catalogue `/api/saas/modules` ne répond pas : la liste du serveur (CatalogueModules), sans les options
 * d'hébergement. Le catalogue lu au serveur l'emporte toujours : une liste figée resterait muette sur un module ajouté.
 */
const MODULES_DU_PRODUIT = [
  "approvals", "notifications", "integrations", "confidentialite",
  "effectifs", "effectifs_regles", "effectifs_planification", "effectifs_disponibilites", "effectifs_absences",
  "effectifs_echanges", "effectifs_agph", "effectifs_presence", "effectifs_temps", "effectifs_communication",
  "effectifs_integrations", "alertes", "paie",
];

/** Les unités : une racine, deux branches — un gestionnaire gère une branche, la direction gère la racine. */
const UNITES = [
  { code: "ORG", libelleFr: "Organisme" },
  { code: "OPS", libelleFr: "Opérations préhospitalières", attributs: { parent: "ORG" } },
  { code: "SOUT", libelleFr: "Soutien et administration", attributs: { parent: "ORG" } },
];

const CORPS_EMPLOI = [
  { code: "PARA", libelleFr: "Paramédics" },
  { code: "REPA", libelleFr: "Répartition médicale d'urgence" },
  { code: "ADM", libelleFr: "Personnel administratif" },
];

const TITRES_EMPLOI = [
  { code: "PSP", libelleFr: "Paramédic en soins primaires", attributs: { corps: "PARA" } },
  { code: "PSA", libelleFr: "Paramédic en soins avancés", attributs: { corps: "PARA" } },
  { code: "RMU", libelleFr: "Répartiteur médical d'urgence", attributs: { corps: "REPA" } },
  { code: "AGA", libelleFr: "Agent administratif", attributs: { corps: "ADM" } },
];

const STATUTS_EMPLOI = [
  { code: "TC", libelleFr: "Temps complet", comportement: "TEMPS_COMPLET" },
  { code: "TPR", libelleFr: "Temps partiel régulier", comportement: "TEMPS_PARTIEL_REGULIER" },
  { code: "TP", libelleFr: "Temps partiel occasionnel", comportement: "TEMPS_PARTIEL_OCCASIONNEL" },
];

const CONVENTIONS = [
  { code: "CONV-PARA", libelleFr: "Convention collective des paramédics" },
  { code: "CONV-SOUT", libelleFr: "Convention collective du personnel de soutien" },
];

const NATURES_QUART = [
  { code: "REG", libelleFr: "Quart régulier", comportement: "REGULIER", attributs: { compteQuota: true } },
  { code: "ADH", libelleFr: "Quart ad hoc", comportement: "AD_HOC" },
  { code: "REMP", libelleFr: "Remplacement", comportement: "REMPLACEMENT" },
  { code: "HS", libelleFr: "Heures supplémentaires offertes", comportement: "HEURES_SUPPLEMENTAIRES" },
];

/** Minutes depuis minuit ; une fin qui précède le début passe minuit (Planification.creneauDe). */
const PLAGES_HORAIRES = [
  { code: "J", libelleFr: "Jour (7 h – 15 h)", attributs: { debut: 420, fin: 900 } },
  { code: "S", libelleFr: "Soir (15 h – 23 h)", attributs: { debut: 900, fin: 1380 } },
  { code: "N", libelleFr: "Nuit (23 h – 7 h)", attributs: { debut: 1380, fin: 420 } },
];

const TYPES_MILIEU = [
  { code: "CASERNE", libelleFr: "Caserne", comportement: "CASERNE" },
  { code: "CCS", libelleFr: "Centre de communication santé", comportement: "CENTRE_OPERATIONNEL" },
  { code: "PS", libelleFr: "Point de service", comportement: "POINT_DE_SERVICE" },
];

/**
 * Les codes d'absence et leurs attributs (TypeReferentiel.CODES_ABSENCE) : `duree` est exigée ; `banque` et
 * `heuresParJour` relient le code au solde transmis par l'organisme (AD-06b) ; `seuilJoursLongTerme` fait passer une
 * longue absence en remplacement long terme (AD-02).
 */
const CODES_ABSENCE = [
  { code: "MAL", libelleFr: "Maladie", comportement: "PAYE",
    attributs: { duree: "PONCTUELLE", planifiable: false, banque: "MAL", heuresParJour: 8, seuilJoursLongTerme: 14 } },
  { code: "VAC", libelleFr: "Vacances annuelles", comportement: "BANQUE",
    attributs: { duree: "PONCTUELLE", planifiable: true, banque: "VAC", heuresParJour: 8, seuilJoursLongTerme: 14 } },
  { code: "FERM", libelleFr: "Férié mobile", comportement: "BANQUE",
    attributs: { duree: "PONCTUELLE", planifiable: true, banque: "FERM", heuresParJour: 8 } },
  { code: "FORM", libelleFr: "Formation", comportement: "PAYE", attributs: { duree: "PONCTUELLE", planifiable: true } },
  { code: "CSS", libelleFr: "Congé sans solde", comportement: "NON_PAYE",
    attributs: { duree: "MOYEN_TERME", planifiable: true, seuilJoursLongTerme: 14 } },
  { code: "INV", libelleFr: "Invalidité de longue durée", comportement: "PAYE", attributs: { duree: "LONG_TERME", planifiable: false } },
];

const TYPES_HEURE = [
  { code: "REG", libelleFr: "Travail régulier", comportement: "TRAVAILLEE", attributs: { codePaie: "001" } },
  { code: "PREA", libelleFr: "Présence préalable", comportement: "PRESENCE_PREALABLE", attributs: { codePaie: "002" } },
  { code: "REPAS", libelleFr: "Repas non rémunéré", comportement: "PAUSE_NON_PAYEE" },
  { code: "ABS", libelleFr: "Absence rémunérée", comportement: "ABSENCE_PAYEE", attributs: { codePaie: "010" } },
  { code: "HS", libelleFr: "Heures supplémentaires (150 %)", comportement: "SUPPLEMENTAIRE", attributs: { codePaie: "050", majorationPourcent: 50 } },
];

const ACTIVITES_PRESENCE = [
  { code: "INTER", libelleFr: "Intervention", comportement: "INTERVENTION", attributs: { facturable: false } },
  { code: "DISPO", libelleFr: "Disponibilité", comportement: "DISPONIBILITE", attributs: { facturable: false } },
  { code: "FORMA", libelleFr: "Formation", comportement: "FORMATION", attributs: { facturable: false } },
];

/** Les fériés de l'année : le code d'un férié est sa date (TypeReferentiel.formatCode). */
function joursFeries(annee) {
  return [
    { code: `${annee}-06-24`, libelleFr: "Fête nationale du Québec" },
    { code: `${annee}-07-01`, libelleFr: "Fête du Canada" },
    { code: `${annee}-12-25`, libelleFr: "Noël" },
  ];
}

/** Les référentiels, dans l'ordre où ils doivent passer (une référence vise une valeur déjà posée). */
function referentiels(annee) {
  return [
    ["unites", UNITES], ["corps_emploi", CORPS_EMPLOI], ["titres_emploi", TITRES_EMPLOI],
    ["statuts_emploi", STATUTS_EMPLOI], ["conventions", CONVENTIONS], ["natures_quart", NATURES_QUART],
    ["plages_horaires", PLAGES_HORAIRES], ["types_milieu", TYPES_MILIEU], ["codes_absence", CODES_ABSENCE],
    ["types_heure", TYPES_HEURE], ["activites_presence", ACTIVITES_PRESENCE], ["jours_feries", joursFeries(annee)],
  ].flatMap(([type, valeurs]) => valeurs.map((v) => ({ type, corps: { effectifDepuis: EFFECTIF_DEPUIS, ...v } })));
}

const MILIEUX = [
  { code: "CAS-01", libelle: "Caserne 1", typeCode: "CASERNE", uniteCode: "OPS" },
  { code: "CAS-02", libelle: "Caserne 2", typeCode: "CASERNE", uniteCode: "OPS" },
  { code: "CCS", libelle: "Centre de communication santé", typeCode: "CCS", uniteCode: "SOUT" },
];

/**
 * Les règles de base, à portée de l'organisme. Sans date d'effet, le serveur prend aujourd'hui : une règle ne
 * s'applique jamais au passé.
 */
const REGLES = [
  { cle: "repos-minimum-attribution", contexte: "ATTRIBUTION", action: "BLOQUER",
    condition: { tous: [{ fait: "reposHeures", op: ">", valeur: 0 }, { fait: "reposHeures", op: "<", valeur: 8 }] },
    message: "Repos de {reposHeures} h entre deux quarts : il en faut au moins 8" },
  { cle: "seize-heures-attribution", contexte: "ATTRIBUTION", action: "BLOQUER",
    condition: { fait: "heures24h", op: ">", valeur: 16 },
    message: "{heures24h} h sur 24 h : au plus 16" },
  { cle: "semaine-chargee-attribution", contexte: "ATTRIBUTION", action: "AVERTIR",
    condition: { fait: "heuresSemaine", op: ">", valeur: 48 },
    message: "{heuresSemaine} h déjà planifiées dans la semaine : au-delà de 48, vérifier la fatigue" },
  { cle: "solde-insuffisant-absence", contexte: "ABSENCE", action: "REFUSER",
    condition: { tous: [{ fait: "soldeBanqueConnu", op: "=", valeur: true }, { fait: "soldeBanqueApresHeures", op: "<", valeur: 0 }] },
    message: "Solde de la banque insuffisant : il resterait {soldeBanqueApresHeures} h" },
  { cle: "preavis-court-absence", contexte: "ABSENCE", action: "EXIGER_APPROBATION",
    condition: { tous: [{ fait: "planifiable", op: "=", valeur: true }, { fait: "joursPreavis", op: "<", valeur: 14 }] },
    message: "Absence planifiable déclarée {joursPreavis} jour(s) d'avance : moins de 14, l'approbation du gestionnaire est requise" },
  { cle: "absence-longue", contexte: "ABSENCE", action: "EXIGER_APPROBATION",
    condition: { fait: "joursDemandes", op: ">", valeur: 5 },
    message: "Absence de {joursDemandes} jours : au-delà de 5, l'approbation du gestionnaire est requise" },
].map((r) => ({ ...r, porteeType: "ORGANISME" }));

/** Paramètres du temps (PUT /api/effectifs/temps/parametres) et seuils d'heures supplémentaires par convention. */
const PARAMETRES_TEMPS = { seuilJourHeures: 12, seuilSemaineHeures: 40, paliersApprobation: 1, toleranceEcartMinutes: 15 };
const SEUILS_CONVENTIONS = [
  { code: "CONV-PARA", corps: { seuilPeriodePaieHeures: 80 } },
  { code: "CONV-SOUT", corps: { seuilSemaineHeures: 35 } },
];

/**
 * Les comptes par défaut, un par rôle intégré (partage.securite.Role), plus trois employés. Chacun — sauf
 * l'administration, compte technique — est relié à une ressource : ses écrans personnels (Mes absences, Mon horaire,
 * Mes disponibilités, Ma feuille de temps) s'ouvrent dès la première connexion. Le personnel de soutien est placé dans
 * l'unité SOUT, corps ADM : il ne paraît jamais dans les bassins de candidats des quarts de paramédics.
 */
const COMPTES = [
  { cle: "direction", role: "director", nom: "Dominique Direction",
    ressource: { matricule: "D0001", uniteCode: "SOUT", corpsEmploiCode: "ADM", titreEmploiCode: "AGA", statutEmploiCode: "TC", conventionCode: "CONV-SOUT", anciennete: 20 },
    perimetre: { uniteCode: "ORG", etendue: "SOUS_ARBRE", niveau: "GESTION" } },
  { cle: "chef-ops", role: "manager", nom: "Camille Chef des opérations",
    ressource: { matricule: "G0001", uniteCode: "OPS", corpsEmploiCode: "PARA", titreEmploiCode: "PSA", statutEmploiCode: "TC", conventionCode: "CONV-PARA", anciennete: 15 },
    perimetre: { uniteCode: "OPS", etendue: "SOUS_ARBRE", niveau: "GESTION" } },
  { cle: "rh", role: "rh", nom: "Rosalie Ressources humaines",
    ressource: { matricule: "R0001", uniteCode: "SOUT", corpsEmploiCode: "ADM", titreEmploiCode: "AGA", statutEmploiCode: "TC", conventionCode: "CONV-SOUT", anciennete: 8 },
    perimetre: { uniteCode: "ORG", etendue: "SOUS_ARBRE", niveau: "LECTURE" } },
  { cle: "comptable", role: "accountant", nom: "Charles Comptable",
    ressource: { matricule: "C0001", uniteCode: "SOUT", corpsEmploiCode: "ADM", titreEmploiCode: "AGA", statutEmploiCode: "TC", conventionCode: "CONV-SOUT", anciennete: 6 } },
  { cle: "employe1", role: "employee", nom: "Émilie Paramédic",
    ressource: { matricule: "E0001", uniteCode: "OPS", corpsEmploiCode: "PARA", titreEmploiCode: "PSA", statutEmploiCode: "TC", conventionCode: "CONV-PARA", anciennete: 9 } },
  { cle: "employe2", role: "employee", nom: "Étienne Paramédic",
    ressource: { matricule: "E0002", uniteCode: "OPS", corpsEmploiCode: "PARA", titreEmploiCode: "PSP", statutEmploiCode: "TPR", conventionCode: "CONV-PARA", anciennete: 4 } },
  { cle: "employe3", role: "employee", nom: "Éloïse Paramédic",
    ressource: { matricule: "E0003", uniteCode: "OPS", corpsEmploiCode: "PARA", titreEmploiCode: "PSP", statutEmploiCode: "TP", conventionCode: "CONV-PARA", anciennete: 1 } },
];

/** Un jour décalé d'une date `AAAA-MM-JJ`, calculé en UTC pour ne dépendre d'aucun fuseau du poste. */
function decaler(date, jours) {
  const d = new Date(`${date}T12:00:00Z`);
  d.setUTCDate(d.getUTCDate() + jours);
  return d.toISOString().slice(0, 10);
}

/** Le lundi de la semaine d'une date. */
function lundiDe(date) {
  const jourSemaine = new Date(`${date}T12:00:00Z`).getUTCDay();
  return decaler(date, -((jourSemaine + 6) % 7));
}

/**
 * Les deux périodes : la courante (deux semaines depuis le lundi, publiée, avec ses équipages) et la suivante, en
 * collecte de disponibilités — c'est elle que « Mes disponibilités » ouvre. Les noms sont fixes : ils servent de clé
 * d'idempotence.
 */
function periodes(aujourdhui) {
  const debut = lundiDe(aujourdhui);
  return {
    courante: { nom: "Période de démarrage", uniteCode: "OPS", debut, fin: decaler(debut, 13) },
    collecte: { nom: "Période suivante — collecte des disponibilités", uniteCode: "OPS", debut: decaler(debut, 14), fin: decaler(debut, 27),
      finCollecte: decaler(debut, 13) },
  };
}

/**
 * Les équipages de la période courante : chaque jour, un quart de jour et un quart de nuit à la caserne 1, deux
 * paramédics chacun. Ils commencent aujourd'hui, pour que « Mon horaire » montre ses prochains quarts.
 */
function equipages(periode, aujourdhui) {
  const premier = aujourdhui > periode.debut ? aujourdhui : periode.debut;
  const liste = [];
  for (let date = premier; date <= periode.fin; date = decaler(date, 1)) {
    for (const plageCode of ["J", "N"]) {
      const plage = PLAGES_HORAIRES.find((p) => p.code === plageCode).attributs;
      liste.push({ date, plageCode, uniteCode: "OPS", milieuCode: "CAS-01", natureCode: "REG", debut: plage.debut, fin: plage.fin,
        minimumOperationnel: 2, composition: [{ corpsEmploiCode: "PARA", nombre: 2 }] });
    }
  }
  return liste;
}

/**
 * Les données de démonstration (absentes avec `--sans-donnees-de-demo`) : chaque employé reçoit un quart de jour, à
 * deux jours d'écart, et la première employée déclare des vacances dans trois semaines.
 */
function attributionsDeDemo(listeEquipages) {
  const jours = listeEquipages.filter((e) => e.plageCode === "J");
  const employes = COMPTES.filter((c) => c.role === "employee");
  return employes.map((c, i) => ({ cle: c.cle, equipage: jours[i * 2] })).filter((a) => a.equipage);
}

function absenceDeDemo(aujourdhui) {
  return { cle: "employe1", corps: { motif: "VAC", du: decaler(aujourdhui, 21), au: decaler(aujourdhui, 22), commentaire: "Absence de démonstration" } };
}

module.exports = {
  EFFECTIF_DEPUIS, FAMILLES_HORS_PRODUIT, MODULES_DU_PRODUIT, UNITES, CORPS_EMPLOI, TITRES_EMPLOI, STATUTS_EMPLOI, CONVENTIONS,
  NATURES_QUART, PLAGES_HORAIRES, TYPES_MILIEU, CODES_ABSENCE, TYPES_HEURE, ACTIVITES_PRESENCE, MILIEUX, REGLES, PARAMETRES_TEMPS,
  SEUILS_CONVENTIONS, COMPTES, joursFeries, referentiels, decaler, lundiDe, periodes, equipages, attributionsDeDemo, absenceDeDemo,
};
