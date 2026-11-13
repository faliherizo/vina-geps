package ca.vina.nexu.partage.courrier.infrastructure;

import ca.vina.nexu.partage.courrier.Courrier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import java.util.Map;

/** Le transport du courrier se choisit à la configuration, jamais dans le code métier : SMTP si {@code SMTP_HOST} est réglé, sinon le journal. */
@Configuration
public class ConfigurationCourrier {

    @Bean
    @Primary
    public Courrier courrier(CourrierJournal journal) {
        return choisir(System.getenv(), journal);
    }

    static Courrier choisir(Map<String, String> env, CourrierJournal journal) {
        return env.getOrDefault("SMTP_HOST", "").isBlank() ? journal : CourrierSmtp.depuis(env);
    }
}
