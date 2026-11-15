package ca.vina.nexu.alertes.domaine;

import ca.vina.nexu.alertes.domaine.Cascades.Cascade;
import ca.vina.nexu.alertes.domaine.Cascades.Politique;
import ca.vina.nexu.alertes.domaine.Cascades.Trace;

import java.util.List;
import java.util.Optional;

/** La lecture des politiques, des cascades et de leur trace. */
public interface LectureCascadesPort {

    List<Politique> politiques(String tenantId);

    Optional<Politique> politique(String tenantId, String id);

    Optional<Cascade> cascade(String tenantId, String alerteId);

    /** La trace d'une cascade, dans l'ordre où elle s'est faite. */
    List<Trace> traces(String tenantId, String alerteId);
}
