package ca.vina.nexu.effectifs.infrastructure.configuration;

import ca.vina.nexu.effectifs.domaine.JournalEffectifsPort;
import ca.vina.nexu.effectifs.domaine.ReglagesJournalPort;
import ca.vina.nexu.effectifs.infrastructure.web.JournalDesConsultations;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.time.Clock;

/** Addenda 8, M-10 : le journal des consultations observe les lectures de l'API des effectifs. */
@Configuration
public class ConfigurationJournalConsultations implements WebMvcConfigurer {

    private final JournalDesConsultations consultations;

    public ConfigurationJournalConsultations(ReglagesJournalPort reglages, JournalEffectifsPort journal, Clock horloge) {
        this.consultations = new JournalDesConsultations(reglages, journal, horloge);
    }

    @Override
    public void addInterceptors(InterceptorRegistry registre) {
        registre.addInterceptor(consultations).addPathPatterns("/api/effectifs/**");
    }
}
