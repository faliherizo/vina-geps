package ca.vina.nexu.paie.domaine;

import java.util.List;
import java.util.Optional;

/** Port de lecture des règles de paie variable de l'organisme (ADR 0084). */
public interface LectureReglesPaiePort {

    /** Toutes les règles, actives ou non, par type puis par code. */
    List<ReglePaie> regles(String tenantId);

    Optional<ReglePaie> regle(String tenantId, String id);
}
