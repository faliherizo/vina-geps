package ca.vina.nexu.habilitations.infrastructure.configuration;

import ca.vina.nexu.habilitations.application.commandes.GestionnaireHabilitations;
import ca.vina.nexu.habilitations.application.requetes.LectureHabilitations;
import ca.vina.nexu.habilitations.domaine.DepotHabilitationsPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

/** Le câblage du contexte {@code habilitations} — cas d'utilisation en Java nu, sans Spring. */
@Configuration
public class ConfigurationHabilitations {

    @Bean
    public LectureHabilitations lectureHabilitations(DepotHabilitationsPort depot, Clock horloge) {
        return new LectureHabilitations(depot, horloge);
    }

    @Bean
    public GestionnaireHabilitations gestionnaireHabilitations(DepotHabilitationsPort depot,
                                                               LectureHabilitations lecture, Clock horloge) {
        return new GestionnaireHabilitations(depot, lecture, horloge);
    }
}
