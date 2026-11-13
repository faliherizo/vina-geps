package ca.vina.nexu.effectifs.domaine;

/**
 * Un refus rendu par le moteur de règles : 409, et le <strong>verdict</strong> voyage avec l'erreur — l'écran
 * doit pouvoir afficher chaque motif et proposer un forçage justifié (E-GN-11, E-GN-16).
 */
public class RefusParRegleException extends ConflitException {

    private final transient Regles.Verdict verdict;

    public RefusParRegleException(String message, Regles.Verdict verdict) {
        super(message);
        this.verdict = verdict;
    }

    public Regles.Verdict verdict() {
        return verdict;
    }
}
