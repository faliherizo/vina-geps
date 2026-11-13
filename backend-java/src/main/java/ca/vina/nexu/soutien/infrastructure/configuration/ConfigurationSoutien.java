package ca.vina.nexu.soutien.infrastructure.configuration;

import ca.vina.nexu.partage.temps.domaine.Horloge;
import ca.vina.nexu.soutien.application.commandes.FixerEngagement;
import ca.vina.nexu.soutien.application.commandes.GestionnaireSoutien;
import ca.vina.nexu.soutien.application.requetes.LectureEngagements;
import ca.vina.nexu.soutien.application.requetes.LectureSoutien;
import ca.vina.nexu.soutien.domaine.EngagementsDeService;
import ca.vina.nexu.soutien.domaine.Soutien;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

/** Le câblage du soutien (ADR 0025). */
@Configuration
public class ConfigurationSoutien {

    @Bean
    public LectureSoutien lectureSoutien(Soutien.Depot depot, Horloge horloge, EngagementsDeService.Registre engagements) {
        return new LectureSoutien(depot, horloge, engagements);
    }

    @Bean
    public LectureEngagements lectureEngagements(EngagementsDeService.Repertoire repertoire) {
        return new LectureEngagements(repertoire);
    }

    @Bean
    public FixerEngagement fixerEngagement(EngagementsDeService.Ecriture ecriture, Clock horloge) {
        return new FixerEngagement(ecriture, horloge);
    }

    @Bean
    public GestionnaireSoutien gestionnaireSoutien(Soutien.Depot depot) {
        return new GestionnaireSoutien(depot);
    }
}
