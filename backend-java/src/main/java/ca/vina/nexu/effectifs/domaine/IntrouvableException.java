package ca.vina.nexu.effectifs.domaine;

/** Ce qui n'existe pas — ou n'est pas visible par l'acteur, qu'on ne distingue pas : 404. */
public class IntrouvableException extends RuntimeException {
    public IntrouvableException(String message) {
        super(message);
    }
}
