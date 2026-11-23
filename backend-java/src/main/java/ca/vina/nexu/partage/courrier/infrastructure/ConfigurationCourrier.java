package ca.vina.nexu.partage.courrier.infrastructure;

import ca.vina.nexu.partage.courrier.Courrier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import java.util.Map;

/**
 * Le transport du courrier se choisit à la configuration, jamais dans le code métier : Microsoft Graph si
 * {@code GRAPH_CLIENT_ID} est réglé, SMTP si {@code SMTP_HOST} l'est, sinon le journal.
 */
@Configuration
public class ConfigurationCourrier {

    @Bean
    @Primary
    public Courrier courrier(CourrierJournal journal) {
        return choisir(System.getenv(), journal);
    }

    /** Microsoft Graph si une application Entra est réglée (ST-G11), sinon SMTP si un hôte l'est, sinon le journal. */
    static Courrier choisir(Map<String, String> env, CourrierJournal journal) {
        if (!env.getOrDefault("GRAPH_CLIENT_ID", "").isBlank()) {
            return CourrierGraph.depuis(env, java.time.Clock.systemUTC());
        }
        return env.getOrDefault("SMTP_HOST", "").isBlank() ? journal : CourrierSmtp.depuis(env);
    }
}
