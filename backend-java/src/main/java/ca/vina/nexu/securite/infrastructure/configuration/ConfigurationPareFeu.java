package ca.vina.nexu.securite.infrastructure.configuration;

import ca.vina.nexu.partage.audit.infrastructure.JournalAudit;
import ca.vina.nexu.securite.domaine.PareFeu;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;
import java.util.HashMap;
import java.util.Map;

/**
 * L'unique pare-feu du processus, réglé par l'environnement ({@code WAF_MODE},
 * {@code WAF_REQUETES_PAR_MINUTE}, {@code WAF_ECHECS_CONNEXION}, {@code WAF_VERROU_MINUTES}) :
 * le filtre HTTP l'interroge, la connexion lui rapporte ses échecs, l'Exploitation lit son état.
 * Ses blocages vont au journal d'audit ; un journal indisponible n'est pas une raison de laisser passer.
 */
@Configuration
public class ConfigurationPareFeu {

    @Bean
    public PareFeu pareFeu(JournalAudit audit, Clock horloge, @Value("${nexu.waf.mode:}") String mode, @Value("${nexu.waf.requetes-par-minute:}") String parMinute,
                           @Value("${nexu.waf.echecs-connexion:}") String echecs, @Value("${nexu.waf.verrou-minutes:}") String verrou) {
        Map<String, String> env = new HashMap<>();
        env.put("WAF_MODE", mode);
        env.put("WAF_REQUETES_PAR_MINUTE", parMinute);
        env.put("WAF_ECHECS_CONNEXION", echecs);
        env.put("WAF_VERROU_MINUTES", verrou);
        return new PareFeu(PareFeu.reglages(env), horloge::millis, (action, d) -> journaliser(audit, action, d));
    }

    @SuppressWarnings("unchecked")
    static void journaliser(JournalAudit audit, String action, Map<String, Object> d) {
        try {
            audit.consigner(null, null, action, (String) d.get("target"), (Map<String, Object>) d.get("details"));
        } catch (RuntimeException e) {
            // le journal n'est pas une raison de laisser passer
        }
    }
}
