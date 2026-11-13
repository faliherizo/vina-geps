package ca.vina.nexu.effectifs.infrastructure.configuration;

import ca.vina.nexu.effectifs.application.commandes.EvaluateurRegles;
import ca.vina.nexu.effectifs.application.commandes.GestionnaireFluxApprobations;
import ca.vina.nexu.effectifs.application.commandes.RestrictionModificationHeures;
import ca.vina.nexu.effectifs.application.requetes.LectureFluxApprobations;
import ca.vina.nexu.effectifs.application.requetes.LecturePerimetres;
import ca.vina.nexu.effectifs.domaine.ComptesPort;
import ca.vina.nexu.effectifs.domaine.FluxApprobationsPort;
import ca.vina.nexu.effectifs.domaine.JournalEffectifsPort;
import ca.vina.nexu.effectifs.domaine.SourcesTempsEffectifsPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Le câblage des flux d'approbation des heures (E-GP-6) et de la restriction de leur modification (E-GP-3), que les feuilles
 * de temps et la présence partagent.
 */
@Configuration
public class ConfigurationFluxApprobationsEffectifs {

    @Bean
    public LectureFluxApprobations lectureFluxApprobationsEffectifs(FluxApprobationsPort moteur, ComptesPort comptes, LecturePerimetres perimetres) {
        return new LectureFluxApprobations(moteur, comptes, perimetres);
    }

    @Bean
    public GestionnaireFluxApprobations gestionnaireFluxApprobationsEffectifs(LectureFluxApprobations lecture, ComptesPort comptes, JournalEffectifsPort journal) {
        return new GestionnaireFluxApprobations(lecture, comptes, journal);
    }

    @Bean
    public RestrictionModificationHeures restrictionModificationHeuresEffectifs(EvaluateurRegles regles, LecturePerimetres perimetres,
                                                                                SourcesTempsEffectifsPort sources) {
        return new RestrictionModificationHeures(regles, perimetres, sources);
    }
}
