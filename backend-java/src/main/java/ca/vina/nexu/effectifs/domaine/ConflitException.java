package ca.vina.nexu.effectifs.domaine;

/** Une demande valide qui heurte l'état présent (doublon, statut déjà décidé) : 409. */
public class ConflitException extends RuntimeException {
    public ConflitException(String message) {
        super(message);
    }
}
