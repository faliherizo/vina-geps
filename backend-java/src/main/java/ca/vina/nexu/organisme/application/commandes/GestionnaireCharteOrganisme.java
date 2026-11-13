package ca.vina.nexu.organisme.application.commandes;

import ca.vina.nexu.organisme.application.requetes.LectureOptionsOrganisme;
import ca.vina.nexu.organisme.domaine.Charte;
import ca.vina.nexu.organisme.domaine.CharteAppliquee;
import ca.vina.nexu.organisme.domaine.ChartesPort;
import ca.vina.nexu.organisme.domaine.Modification;
import ca.vina.nexu.organisme.domaine.Retrait;

import java.util.Map;

/** Poser ou retirer la charte de l'organisme (P-TN-9). La validation — contrastes compris — précède toute écriture. */
public class GestionnaireCharteOrganisme {

    private final ChartesPort chartes;
    private final LectureOptionsOrganisme lecture;

    public GestionnaireCharteOrganisme(ChartesPort chartes, LectureOptionsOrganisme lecture) {
        this.chartes = chartes;
        this.lecture = lecture;
    }

    /** Rend la charte relue, comme le Node : ce que l'interface appliquera, pas ce que le client a envoyé. */
    public CharteAppliquee enregistrer(String tenantId, Map<String, Object> brut, Modification modification) {
        chartes.enregistrer(tenantId, Charte.de(brut), modification);
        return lecture.charte(tenantId).orElseThrow();
    }

    public Retrait retirer(String tenantId) {
        return new Retrait(chartes.retirer(tenantId));
    }
}
