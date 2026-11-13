package ca.vina.nexu.effectifs.domaine;

import java.util.Optional;

/** Le compte d'un utilisateur, vu des effectifs : seulement son établissement (vide = compte inconnu ou global). */
public interface ComptesPort {
    Optional<String> etablissementDe(String utilisateurId);

    /** Le nom de connexion d'un compte, pour qu'un écran n'affiche pas un identifiant ; vide pour un compte inconnu. */
    Optional<String> nomDe(String utilisateurId);
}
