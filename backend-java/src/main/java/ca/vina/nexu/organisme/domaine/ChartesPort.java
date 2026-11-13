package ca.vina.nexu.organisme.domaine;

import java.util.Optional;

/** Port de persistance des chartes : une par organisme. */
public interface ChartesPort {

    Optional<Charte> lire(String tenantId);

    /** Pose ou remplace la charte de l'organisme. */
    void enregistrer(String tenantId, Charte charte, Modification modification);

    /** Vrai si une charte a été retirée. */
    boolean retirer(String tenantId);
}
