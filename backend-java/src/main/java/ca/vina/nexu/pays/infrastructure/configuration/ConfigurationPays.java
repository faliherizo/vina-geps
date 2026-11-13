package ca.vina.nexu.pays.infrastructure.configuration;

import ca.vina.nexu.pays.application.requetes.CalculsPays;
import ca.vina.nexu.pays.application.requetes.LecturePays;
import ca.vina.nexu.pays.domaine.Pays;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Le câblage des lectures et calculs par pays (ADR 0025). */
@Configuration
public class ConfigurationPays {

    @Bean
    public LecturePays lecturePays(Pays.Catalogue catalogue) {
        return new LecturePays(catalogue);
    }

    @Bean
    public CalculsPays calculsPays(Pays.Catalogue catalogue) {
        return new CalculsPays(catalogue);
    }
}
