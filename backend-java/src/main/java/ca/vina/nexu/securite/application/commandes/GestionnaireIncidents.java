package ca.vina.nexu.securite.application.commandes;

import ca.vina.nexu.partage.temps.domaine.Horloge;
import ca.vina.nexu.securite.domaine.Incidents;

import java.util.Map;

/** Consigner, corriger et clore un incident de confidentialité. */
public final class GestionnaireIncidents {

    private final Incidents.Registre registre;
    private final Horloge horloge;

    public GestionnaireIncidents(Incidents.Registre registre, Horloge horloge) {
        this.registre = registre;
        this.horloge = horloge;
    }

    public Incidents.Incident consigner(String tenantId, String acteurId, Map<String, Object> saisie) {
        return registre.consigner(tenantId, acteurId, saisie, horloge.maintenant());
    }

    public Incidents.Incident modifier(String tenantId, String id, Map<String, Object> saisie) {
        return registre.modifier(tenantId, id, saisie);
    }

    public Incidents.Incident clore(String tenantId, String id) {
        return registre.clore(tenantId, id, horloge.maintenant());
    }
}
