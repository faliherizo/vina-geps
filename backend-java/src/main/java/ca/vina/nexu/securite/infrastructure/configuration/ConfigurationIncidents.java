package ca.vina.nexu.securite.infrastructure.configuration;

import ca.vina.nexu.partage.temps.domaine.Horloge;
import ca.vina.nexu.securite.application.commandes.GestionnaireIncidents;
import ca.vina.nexu.securite.application.requetes.LectureIncidents;
import ca.vina.nexu.securite.domaine.Incidents;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Le câblage du registre des incidents (ADR 0025). */
@Configuration
public class ConfigurationIncidents {

    @Bean
    public LectureIncidents lectureIncidents(Incidents.Registre registre, LectureIncidents.RenduPdf pdf, Horloge horloge) {
        return new LectureIncidents(registre, pdf, horloge);
    }

    @Bean
    public GestionnaireIncidents gestionnaireIncidents(Incidents.Registre registre, Horloge horloge) {
        return new GestionnaireIncidents(registre, horloge);
    }
}
