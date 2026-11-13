package ca.vina.nexu.effectifs.infrastructure.configuration;

import ca.vina.nexu.effectifs.application.commandes.GestionnaireRetentionEffectifs;
import ca.vina.nexu.effectifs.application.requetes.LectureRetentionEffectifs;
import ca.vina.nexu.effectifs.domaine.EcritureRetentionEffectifsPort;
import ca.vina.nexu.effectifs.domaine.JournalEffectifsPort;
import ca.vina.nexu.effectifs.domaine.LectureRetentionEffectifsPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Le câblage de la rétention des effectifs (lot R11) : du Java nu, assemblé ici, sans rouvrir la configuration du socle. */
@Configuration
public class ConfigurationRetentionEffectifs {

    @Bean
    public LectureRetentionEffectifs lectureRetentionEffectifs(LectureRetentionEffectifsPort depot) {
        return new LectureRetentionEffectifs(depot);
    }

    @Bean
    public GestionnaireRetentionEffectifs gestionnaireRetentionEffectifs(LectureRetentionEffectifs lecture, EcritureRetentionEffectifsPort depot,
                                                                         JournalEffectifsPort journal) {
        return new GestionnaireRetentionEffectifs(lecture, depot, journal);
    }
}
