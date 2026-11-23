/**
 * **Le droit de voir une entrée de menu.** Une seule décision, trois sources possibles.
 *
 * Elle vit ici, hors de `App.tsx`, pour une raison précise : c'est la règle qui, recopiée,
 * produit le défaut le plus pénible du produit — un écran ouvert dont tous les boutons
 * échouent en 403, ou un écran caché à quelqu'un que le serveur sert volontiers. Le serveur
 * dit déjà qui a le droit ; le menu ne fait que lire sa réponse, et cette lecture s'éprouve.
 */

/** Ce que le profil revenu du serveur nous apprend sur la personne qui regarde. */
export interface RegardDuMenu {
  role: string;
  /** Capacités calculées par le serveur ; vide tant que `/me` n'a pas répondu. */
  capabilities?: readonly string[];
}

/** Ce que le catalogue déclare sur une entrée. */
export interface EntreeDuMenu {
  /** Les rôles livrés avec le produit — le repli, jamais la règle. */
  roles: readonly string[];
  /** Capacités dont une seule suffit ; absent = l'entrée ne correspond à aucun droit nommé. */
  capacitesRequises?: readonly string[];
  /** Vrai si l'entrée délègue son droit au serveur : distingue « pas de délégation » de « droit inconnu ». */
  droitDelegue?: boolean;
  /** Le droit calculé par le serveur ; `undefined` = le profil n'a pas encore répondu. */
  droitServeur?: boolean;
  /**
   * **L'espace personnel** — mes alertes, mon compte, l'aide, le soutien : à tout compte d'organisme, quel que soit son
   * rôle. Une liste de rôles l'aurait fermé aux rôles propres (ADR 0085) : l'expéditeur de l'avis FX-AO reçoit des
   * alertes que le serveur lui sert, mais ne voyait ni « Mes alertes » ni « Mon compte » (constaté le 23 novembre 2026).
   */
  espacePersonnel?: boolean;
}

/** Le compte global d'exploitation : il n'appartient à aucun organisme, il n'a pas d'espace personnel. */
const ROLE_GLOBAL = "superadmin";

/**
 * Décide si l'entrée doit apparaître, **sans** se prononcer sur les modules : qu'un organisme
 * ait ou non souscrit au module est une autre question, tranchée ailleurs.
 *
 * Trois régimes, du plus précis au plus grossier :
 *
 * 1. **Droit calculé par le serveur** (`droitDelegue`). Une capacité ne connaît que le rôle ;
 *    approuver dépend aussi de l'organigramme — quiconque encadre réellement quelqu'un approuve
 *    son travail, même si son compte est resté « employé ». Seul le serveur sait cela.
 * 2. **Capacité**. Depuis que les établissements définissent leurs propres rôles — « Doyen »,
 *    « Coordonnateur » —, une liste de rôles écrite dans le code ne peut plus être exhaustive.
 * 3. **Rôle**. Pour les entrées qui ne correspondent à aucun droit nommé : tableau de bord,
 *    aide, espace personnel. Elles doivent rester ouvertes.
 *
 * Dans les deux premiers régimes, **tant que le serveur n'a pas répondu on retombe sur les
 * rôles** plutôt que de masquer l'application : un menu vide au chargement se lit comme une
 * perte d'accès, et c'est un appel au soutien pour un écran qui allait apparaître.
 */
export function droitDeVoir(entree: EntreeDuMenu, regard: RegardDuMenu): boolean {
  if (entree.espacePersonnel) return regard.role !== ROLE_GLOBAL;
  const parRole = entree.roles.includes(regard.role);

  if (entree.droitDelegue) return entree.droitServeur === undefined ? parRole : entree.droitServeur;

  const requises = entree.capacitesRequises;
  if (!requises || requises.length === 0) return parRole;

  const detenues = regard.capabilities ?? [];
  return detenues.length === 0 ? parRole : requises.some((c) => detenues.includes(c));
}
