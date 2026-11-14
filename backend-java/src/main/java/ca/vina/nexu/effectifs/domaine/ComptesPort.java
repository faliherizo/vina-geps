package ca.vina.nexu.effectifs.domaine;

import java.util.Optional;

/** Le compte d'un utilisateur, vu des effectifs : seulement son établissement (vide = compte inconnu ou global). */
public interface ComptesPort {
    Optional<String> etablissementDe(String utilisateurId);

    /** Le nom de connexion d'un compte, pour qu'un écran n'affiche pas un identifiant ; vide pour un compte inconnu. */
    Optional<String> nomDe(String utilisateurId);

    /**
     * Le compte du <strong>supérieur hiérarchique</strong> de ce compte, d'après l'organigramme de l'organisme
     * (dossier d'employé → gestionnaire → son compte) ; vide si un maillon manque.
     */
    Optional<String> superieurDe(String tenantId, String utilisateurId);
}
