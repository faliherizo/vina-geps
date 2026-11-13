package ca.vina.nexu.partage.erreurs.domaine;

/** Une semaine soumise ou approuvée ne se touche plus ; le bord HTTP le rend 409. */
public class VerrouException extends IllegalStateException {
    public VerrouException(String message) {
        super(message);
    }
}
