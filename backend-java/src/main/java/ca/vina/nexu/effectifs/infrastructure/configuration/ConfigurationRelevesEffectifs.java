package ca.vina.nexu.effectifs.infrastructure.configuration;

import ca.vina.nexu.effectifs.application.requetes.CalculFeuillesTemps;
import ca.vina.nexu.effectifs.application.requetes.LectureFeuillesTemps;
import ca.vina.nexu.effectifs.application.requetes.LecturePerimetres;
import ca.vina.nexu.effectifs.application.requetes.LectureReferentiels;
import ca.vina.nexu.effectifs.infrastructure.persistance.FaitsRelevesEffectifs;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Le câblage des relevés statistiques d'effectifs (P-RF-1) : les lectures que le fournisseur de faits réutilise. */
@Configuration
public class ConfigurationRelevesEffectifs {

    @Bean
    public FaitsRelevesEffectifs.Lectures lecturesRelevesEffectifs(LectureFeuillesTemps feuilles, CalculFeuillesTemps calcul,
                                                                   LecturePerimetres perimetres, LectureReferentiels referentiels) {
        return new FaitsRelevesEffectifs.Lectures(feuilles, calcul, perimetres, referentiels);
    }
}
