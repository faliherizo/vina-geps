package ca.vina.nexu.soutien.application.commandes;

import ca.vina.nexu.soutien.application.requetes.LectureEngagements;
import ca.vina.nexu.soutien.domaine.EngagementsDeService;

import java.time.Clock;

/** Le soutien attribue à un organisme la politique de son contrat ; le changement est daté et signé. */
public final class FixerEngagement {

    private final EngagementsDeService.Ecriture ecriture;
    private final Clock horloge;

    public FixerEngagement(EngagementsDeService.Ecriture ecriture, Clock horloge) {
        this.ecriture = ecriture;
        this.horloge = horloge;
    }

    public LectureEngagements.AffectationVue fixer(String tenantId, EngagementsDeService.Politique politique, String acteurId) {
        return LectureEngagements.AffectationVue.de(ecriture.fixer(tenantId, politique, acteurId, horloge.instant()));
    }
}
