package ca.vina.nexu.partage.erreurs.domaine;

/** Une entité que rien ne désigne, dans n'importe quel contexte ; le bord HTTP la rend 404. */
public class IntrouvableException extends RuntimeException {
    public IntrouvableException(String message) {
        super(message);
    }
}
