package ca.vina.nexu.alertes.application.requetes;

import ca.vina.nexu.alertes.domaine.Cascades.Cascade;
import ca.vina.nexu.alertes.domaine.Cascades.Politique;
import ca.vina.nexu.alertes.domaine.Cascades.Trace;
import ca.vina.nexu.alertes.domaine.LectureAlertesPort;
import ca.vina.nexu.alertes.domaine.LectureCascadesPort;
import ca.vina.nexu.alertes.domaine.RefusAlerte;

import java.util.List;
import java.util.Optional;

/** Les lectures des cascades : les politiques, et la cascade d'une alerte avec sa trace (critère 19). */
public class LectureCascades {

    /** La cascade d'une alerte (absente si l'alerte est partie sans politique) et sa trace. */
    public record Suivi(Optional<Cascade> cascade, List<Trace> traces) { }

    private final LectureCascadesPort cascades;
    private final LectureAlertesPort alertes;

    public LectureCascades(LectureCascadesPort cascades, LectureAlertesPort alertes) {
        this.cascades = cascades;
        this.alertes = alertes;
    }

    /** Toutes pour l'administration ; les actives seulement pour qui compose une alerte. */
    public List<Politique> politiques(String tenantId, boolean toutes) {
        return cascades.politiques(tenantId).stream().filter(p -> toutes || p.actif()).toList();
    }

    public Suivi suivi(String tenantId, String alerteId) {
        alertes.alerte(tenantId, alerteId).orElseThrow(() -> RefusAlerte.introuvable("alerte introuvable"));
        return new Suivi(cascades.cascade(tenantId, alerteId), cascades.traces(tenantId, alerteId));
    }
}
