package ca.vina.nexu.effectifs.domaine;

/** L'acteur n'a pas le niveau voulu sur l'unité : 403. Refus par défaut (E-GN-8). */
public class HorsPerimetreException extends RuntimeException {
    public HorsPerimetreException(String message) {
        super(message);
    }
}
