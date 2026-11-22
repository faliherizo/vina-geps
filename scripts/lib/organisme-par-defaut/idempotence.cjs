/**
 * **Retrouver avant de créer** — les décisions pures qui rendent `creer-organisme-par-defaut.cjs` rejouable.
 *
 * Chaque objet se retrouve par sa clé métier (nom, code, matricule, nom de connexion, clé de règle) : relancer le
 * script ne double rien, et complète ce qui manque. Aucune fonction ici ne touche le réseau.
 */
const crypto = require("crypto");

/** Le serveur dit « existe déjà » par un 409 : la création concurrente ou répétée n'est pas une faute. */
const CONFLIT = 409;

/** La première entrée d'une liste dont le champ vaut la valeur ; une liste absente vaut une liste vide. */
function trouver(liste, champ, valeur) {
  return (Array.isArray(liste) ? liste : []).find((e) => e && e[champ] === valeur) ?? null;
}

/** Une règle se retrouve par sa clé, son contexte et sa portée : la même clé peut viser deux portées. */
function regleExistante(existantes, r) {
  return (Array.isArray(existantes) ? existantes : []).find((e) => e.cle === r.cle && e.contexte === r.contexte
    && (e.porteeType ?? "ORGANISME") === r.porteeType && (e.porteeCode ?? null) === (r.porteeCode ?? null)) ?? null;
}

/** Un périmètre ouvert de cette personne sur cette unité (un périmètre fermé ne compte plus). */
function perimetreExistant(existants, utilisateurId, uniteCode) {
  return (Array.isArray(existants) ? existants : []).find((p) => p.utilisateurId === utilisateurId && p.uniteCode === uniteCode
    && !p.jusqua && !p.fin) ?? null;
}

/** Une erreur du client d'API levée sur un 409. */
function estConflit(erreur) {
  return erreur?.statut === CONFLIT;
}

/** Le nom de fichier local d'un organisme : minuscules, sans accent, tirets. */
function slug(nom) {
  return nom.normalize("NFD").replace(/[̀-ͯ]/g, "").toLowerCase().replace(/[^a-z0-9]+/g, "-").replace(/^-+|-+$/g, "") || "organisme";
}

/**
 * Un mot de passe fort : 24 caractères tirés au hasard, plus une minuscule, une majuscule, un chiffre et un symbole
 * garantis — la politique du serveur exige au moins 12 caractères. `octets` est injecté pour l'essai.
 */
function motDePasse(octets = crypto.randomBytes) {
  return `${octets(18).toString("base64url")}aZ7!`;
}

/**
 * Les identifiants connus, fusionnés avec ceux de cette exécution : un compte déjà consigné garde son mot de passe,
 * sauf si l'exécution l'a remplacé (réinitialisation). Rien ne se perd d'une exécution à l'autre.
 */
function fusionnerComptes(anciens, nouveaux) {
  const parNom = new Map((anciens ?? []).map((c) => [c.username, c]));
  for (const c of nouveaux) parNom.set(c.username, { ...parNom.get(c.username), ...c });
  return [...parNom.values()];
}

/** Le cycle de vie d'une période (Periode.Etat), dans l'ordre. */
const ETATS_PERIODE = ["PREPARATION", "COLLECTE", "CONFECTION", "PUBLIEE", "CLOSE"];

/**
 * Les états à franchir pour amener une période de son état actuel à la cible : la cible seule si elle est en avant,
 * rien si la période y est déjà ou plus loin — rejouer ne fait jamais reculer une période publiée.
 */
function etatsAFranchir(actuel, cible) {
  const de = ETATS_PERIODE.indexOf(actuel ?? "PREPARATION");
  const vers = ETATS_PERIODE.indexOf(cible);
  if (vers < 0) throw new Error(`état de période inconnu : ${cible}`);
  return vers > de ? [cible] : [];
}

module.exports = { CONFLIT, ETATS_PERIODE, etatsAFranchir, trouver, regleExistante, perimetreExistant, estConflit, slug, motDePasse, fusionnerComptes };
