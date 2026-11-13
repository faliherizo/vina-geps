package ca.vina.nexu.ordonnanceur.domaine;

/** Ce qu'un tic a fait : prises, faites, reportées, retentées, échouées, sans exécuteur. */
public record BilanTic(int prises, int faites, int reportees, int retentees, int echouees, int sansExecuteur) {
}
