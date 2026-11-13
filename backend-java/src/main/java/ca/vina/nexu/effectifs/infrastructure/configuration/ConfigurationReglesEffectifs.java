package ca.vina.nexu.effectifs.infrastructure.configuration;

import ca.vina.nexu.effectifs.application.commandes.EvaluateurRegles;
import ca.vina.nexu.effectifs.application.commandes.GestionnaireReferentiels;
import ca.vina.nexu.effectifs.application.commandes.GestionnaireRegles;
import ca.vina.nexu.effectifs.application.commandes.PromotionConfiguration;
import ca.vina.nexu.effectifs.application.requetes.LectureReferentiels;
import ca.vina.nexu.effectifs.application.requetes.LectureRegles;
import ca.vina.nexu.effectifs.domaine.DepotReglesPort;
import ca.vina.nexu.effectifs.domaine.JournalEffectifsPort;
import ca.vina.nexu.partage.identifiants.domaine.Sequence;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Le câblage du moteur de règles des effectifs (lot R2). À part du socle : les lots suivants dépendent de
 * {@link EvaluateurRegles}, et chacun ajoute sa configuration sans rouvrir celle des autres.
 */
@Configuration
public class ConfigurationReglesEffectifs {

    @Bean
    public LectureRegles lectureReglesEffectifs(DepotReglesPort depot) {
        return new LectureRegles(depot);
    }

    @Bean
    public GestionnaireRegles gestionnaireReglesEffectifs(DepotReglesPort depot, LectureRegles lecture, LectureReferentiels referentiels,
                                                          JournalEffectifsPort journal, Sequence sequence) {
        return new GestionnaireRegles(depot, lecture, referentiels, journal, sequence);
    }

    @Bean
    public EvaluateurRegles evaluateurReglesEffectifs(DepotReglesPort depot, LectureReferentiels referentiels, JournalEffectifsPort journal, Sequence sequence) {
        return new EvaluateurRegles(depot, referentiels, journal, sequence);
    }

    /** P-TN-1 : la promotion de la configuration (référentiels et règles) d'un environnement à l'autre. */
    @Bean
    public PromotionConfiguration promotionConfigurationEffectifs(LectureReferentiels lectureReferentiels, GestionnaireReferentiels referentiels,
                                                                  LectureRegles lectureRegles, GestionnaireRegles regles) {
        return new PromotionConfiguration(lectureReferentiels, referentiels, lectureRegles, regles);
    }
}
