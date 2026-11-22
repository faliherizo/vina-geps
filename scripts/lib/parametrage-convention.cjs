/**
 * **Le paramétrage d'une convention collective, traduit en gestes de l'application** — des appels d'API, rien d'autre.
 *
 * Le fichier de données (p. ex. `e2e/donnees/urgences-sante/convention-sp-csn.json`) cite l'article de chaque valeur ;
 * ces citations documentent et ne se postent pas. Ce module rend la liste ordonnée des appels qu'une administratrice
 * ferait à l'écran : référentiels d'abord (une règle ne vise qu'une convention ou un statut déjà en vigueur), règles
 * ensuite, seuils d'heures supplémentaires en dernier. Il est pur : le semis l'exécute, l'essai le relit.
 */

/** Le premier jour d'effet des référentiels : antérieur à tout ce que la démonstration planifie. */
const EFFECTIF_DEPUIS = "2026-01-01";

/** Les clés qui documentent une valeur sans faire partie de ce que l'API reçoit. */
const DOCUMENTATION = new Set(["article", "lecture", "pourquoi", "nonPoses"]);

const sansDocumentation = (o) => Object.fromEntries(Object.entries(o).filter(([k]) => !DOCUMENTATION.has(k)));

/** Une valeur de référentiel : son type, ses champs postés, et ce qu'en dit la convention. */
function referentiel(type, valeur) {
  return {
    methode: "POST", chemin: `/api/effectifs/referentiels/${type}`, corps: { effectifDepuis: EFFECTIF_DEPUIS, ...sansDocumentation(valeur) },
    quoi: `${type} ${valeur.code} (art. ${valeur.article})`,
  };
}

/**
 * Une règle. Sans portée déclarée, elle vise la convention : c'est la convention qui la fonde. Sans date d'effet, le
 * serveur prend aujourd'hui — une règle ne s'applique jamais au passé.
 */
function regle(r, convention) {
  const portee = r.portee ?? { type: "CONVENTION", code: convention };
  return {
    methode: "POST", chemin: "/api/effectifs/regles",
    corps: { cle: r.cle, contexte: r.contexte, porteeType: portee.type, porteeCode: portee.code, condition: r.condition, action: r.action, message: r.message },
    quoi: `règle ${r.cle} (art. ${r.article})`,
  };
}

/** Les appels, dans l'ordre où ils doivent passer. */
function etapesDuParametrage(donnees) {
  const code = donnees.convention.code;
  const seuils = donnees.seuilsHeuresSupplementaires;
  return [
    referentiel("conventions", donnees.convention),
    ...donnees.statutsEmploi.map((s) => referentiel("statuts_emploi", s)),
    ...donnees.naturesQuart.map((n) => referentiel("natures_quart", n)),
    ...donnees.typesHeure.map((t) => referentiel("types_heure", t)),
    ...donnees.codesAbsence.codes.map((c) => referentiel("codes_absence", c)),
    ...donnees.regles.map((r) => regle(r, code)),
    { methode: "PUT", chemin: `/api/effectifs/temps/seuils-conventions/${code}`, corps: seuils.corps, quoi: `seuils de ${code} (art. ${seuils.article})` },
  ];
}

module.exports = { etapesDuParametrage, EFFECTIF_DEPUIS };
