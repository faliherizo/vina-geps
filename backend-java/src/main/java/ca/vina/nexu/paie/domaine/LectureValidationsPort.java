package ca.vina.nexu.paie.domaine;

import java.util.Collection;
import java.util.Map;

/** Port de lecture des validations des temps (ADR 0084). */
public interface LectureValidationsPort {

    /** La plus récente décision de la paie pour chacune de ces feuilles, par identifiant de feuille ; absente si aucune. */
    Map<String, ValidationTemps> dernieres(String tenantId, Collection<String> feuilleIds);
}
