/**
 * **L'autocontrôle d'un organisme par défaut** — la liste, pure, de ce que chaque rôle doit pouvoir (et ne pas
 * pouvoir) ouvrir dès sa première connexion. Le script l'exécute ; l'essai la relit.
 *
 * Les écrans personnels (Mes absences, Mon horaire, Mes disponibilités, Ma feuille de temps) refusent un compte sans
 * ressource par « votre compte n'est relié à aucune ressource planifiable » : `GET /api/effectifs/ma-ressource` lit
 * exactement la liaison que la déclaration d'absence exige (LectureRessources.deLActeur), d'où le contrôle
 * `ressource: true`.
 */

const SANS_RESSOURCE = "aucune ressource planifiable";

/** Les routes personnelles derrière les écrans de « Mon espace ». */
function routesPersonnelles(du, au) {
  return [
    { quoi: "Mes absences", chemin: "/api/effectifs/absences", attendu: 200 },
    { quoi: "Mon horaire", chemin: `/api/effectifs/mon-horaire?du=${du}&au=${au}`, attendu: 200 },
    { quoi: "Mes disponibilités", chemin: "/api/effectifs/disponibilites/moi", attendu: 200 },
    { quoi: "Ma feuille de temps", chemin: "/api/effectifs/temps/moi", attendu: 200 },
    { quoi: "Ma ressource", chemin: "/api/effectifs/ma-ressource", attendu: 200, ressource: true },
  ];
}

/** Une route réservée à l'administration : un employé doit y recevoir 403. */
const ADMIN_SEULEMENT = { quoi: "Comptes (administration seulement)", chemin: "/api/auth/users", attendu: 403 };

/** Ce que chaque rôle doit ouvrir, en plus de ses écrans personnels. */
const PAR_ROLE = {
  admin: [
    { quoi: "Comptes", chemin: "/api/auth/users", attendu: 200 },
    { quoi: "Règles", chemin: "/api/effectifs/regles", attendu: 200 },
    { quoi: "Paquet de configuration", chemin: "/api/effectifs/configuration/paquet", attendu: 200 },
  ],
  director: [
    { quoi: "Ressources (périmètre de l'organisme)", chemin: "/api/effectifs/planification/ressources", attendu: 200, auMoins: 7 },
    { quoi: "Feuilles de temps", chemin: "/api/effectifs/temps/feuilles", attendu: 200 },
    { quoi: "Comptes", chemin: "/api/auth/users", attendu: 403 },
  ],
  manager: [
    { quoi: "Ressources (périmètre de l'unité)", chemin: "/api/effectifs/planification/ressources?uniteCode=OPS", attendu: 200, auMoins: 4 },
    { quoi: "Périodes", chemin: "/api/effectifs/planification/periodes", attendu: 200 },
    { quoi: "Comptes", chemin: "/api/auth/users", attendu: 403 },
  ],
  rh: [
    { quoi: "Dossiers de rémunération", chemin: "/api/paie/dossiers", attendu: 200 },
    { quoi: "Paramètres de la paie", chemin: "/api/paie/reglages", attendu: 200 },
    { quoi: "Comptes", chemin: "/api/auth/users", attendu: 403 },
  ],
  accountant: [
    { quoi: "Cycles de paie", chemin: "/api/paie/cycles", attendu: 200 },
    { quoi: "Mes bulletins", chemin: "/api/paie/mes-bulletins", attendu: 200 },
    { quoi: "Comptes", chemin: "/api/auth/users", attendu: 403 },
  ],
  employee: [ADMIN_SEULEMENT, { quoi: "Paramétrage des règles", chemin: "/api/effectifs/configuration/paquet", attendu: 403 }],
};

/** Les contrôles d'un compte : ses routes de rôle, et ses routes personnelles s'il a une ressource. */
function controlesDe(compte, { du, au }) {
  const propres = PAR_ROLE[compte.role] ?? [];
  return compte.aRessource ? [...routesPersonnelles(du, au), ...propres] : propres;
}

/**
 * Le verdict d'un contrôle : le statut attendu, aucune mention « aucune ressource planifiable », et, s'il le faut,
 * une ressource présente ou un nombre minimal d'éléments. Rend `null` si tout va, sinon le motif.
 */
function verdict(controle, statut, corps) {
  if (statut !== controle.attendu) return `statut ${statut}, attendu ${controle.attendu}`;
  const texte = typeof corps === "string" ? corps : JSON.stringify(corps ?? "");
  if (texte.includes(SANS_RESSOURCE)) return `« ${SANS_RESSOURCE} »`;
  if (controle.ressource && !corps?.ressource) return "aucune ressource reliée au compte";
  if (controle.auMoins && (!Array.isArray(corps) || corps.length < controle.auMoins)) {
    return `${Array.isArray(corps) ? corps.length : 0} élément(s), au moins ${controle.auMoins} attendus`;
  }
  return null;
}

module.exports = { SANS_RESSOURCE, routesPersonnelles, PAR_ROLE, controlesDe, verdict };
