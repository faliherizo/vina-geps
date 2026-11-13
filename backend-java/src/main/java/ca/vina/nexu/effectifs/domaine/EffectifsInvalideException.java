package ca.vina.nexu.effectifs.domaine;

/** Une demande que le métier refuse, avec son motif : 422. Jumeau de {@code EffectifsInvalideError} du Node. */
public class EffectifsInvalideException extends RuntimeException {
    public EffectifsInvalideException(String message) {
        super(message);
    }
}
