package ca.vina.nexu.partage.erreurs.domaine;

import java.util.List;

/** Plusieurs erreurs de saisie, toutes dites ensemble ({@code errors: [...]}) ; le bord HTTP la rend 400. */
public class RefusException extends IllegalArgumentException {
    private final List<String> erreurs;

    public RefusException(List<String> erreurs) {
        super(String.join("; ", erreurs));
        this.erreurs = List.copyOf(erreurs);
    }

    public List<String> erreurs() {
        return erreurs;
    }
}
