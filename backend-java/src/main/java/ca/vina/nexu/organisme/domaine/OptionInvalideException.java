package ca.vina.nexu.organisme.domaine;

/**
 * Une option de l'organisme que le métier refuse, avec son motif : 422. Jumeau de
 * {@code OptionInvalideError} du Node — le front affiche le message tel quel, il doit donc dire quoi corriger.
 */
public class OptionInvalideException extends RuntimeException {
    public OptionInvalideException(String message) {
        super(message);
    }
}
