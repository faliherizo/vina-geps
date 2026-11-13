package ca.vina.nexu.effectifs.infrastructure.configuration;

import ca.vina.nexu.effectifs.application.commandes.GestionnaireMilieux;
import ca.vina.nexu.effectifs.application.commandes.GestionnairePerimetres;
import ca.vina.nexu.effectifs.application.commandes.GestionnaireReferentiels;
import ca.vina.nexu.effectifs.application.requetes.LectureJournalEffectifs;
import ca.vina.nexu.effectifs.application.requetes.LectureMilieux;
import ca.vina.nexu.effectifs.application.requetes.LecturePerimetres;
import ca.vina.nexu.effectifs.application.requetes.LectureReferentiels;
import ca.vina.nexu.effectifs.domaine.ComptesPort;
import ca.vina.nexu.effectifs.domaine.DepotMilieuxPort;
import ca.vina.nexu.effectifs.domaine.DepotPerimetresPort;
import ca.vina.nexu.effectifs.domaine.DepotReferentielsPort;
import ca.vina.nexu.effectifs.domaine.JournalEffectifsPort;
import ca.vina.nexu.partage.identifiants.domaine.Sequence;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Le câblage du contexte {@code effectifs}. Les cas d'utilisation sont du Java nu : l'application ne connaît
 * pas Spring ({@code ArchitectureTest} le vérifie).
 */
@Configuration
public class ConfigurationEffectifs {

    @Bean
    public LectureReferentiels lectureReferentielsEffectifs(DepotReferentielsPort depot) {
        return new LectureReferentiels(depot);
    }

    @Bean
    public LecturePerimetres lecturePerimetresEffectifs(DepotPerimetresPort depot, LectureReferentiels referentiels) {
        return new LecturePerimetres(depot, referentiels);
    }

    @Bean
    public LectureMilieux lectureMilieuxEffectifs(DepotMilieuxPort depot, LecturePerimetres perimetres) {
        return new LectureMilieux(depot, perimetres);
    }

    @Bean
    public LectureJournalEffectifs lectureJournalEffectifs(JournalEffectifsPort journal, LecturePerimetres perimetres) {
        return new LectureJournalEffectifs(journal, perimetres);
    }

    @Bean
    public GestionnaireReferentiels gestionnaireReferentielsEffectifs(DepotReferentielsPort depot, LectureReferentiels lecture, JournalEffectifsPort journal, Sequence sequence) {
        return new GestionnaireReferentiels(depot, lecture, journal, sequence);
    }

    @Bean
    public GestionnairePerimetres gestionnairePerimetresEffectifs(DepotPerimetresPort depot, LectureReferentiels referentiels, ComptesPort comptes,
                                                                  JournalEffectifsPort journal, Sequence sequence) {
        return new GestionnairePerimetres(depot, referentiels, comptes, journal, sequence);
    }

    @Bean
    public GestionnaireMilieux gestionnaireMilieuxEffectifs(DepotMilieuxPort depot, LectureMilieux lecture, LectureReferentiels referentiels,
                                                            LecturePerimetres perimetres, JournalEffectifsPort journal, Sequence sequence) {
        return new GestionnaireMilieux(depot, lecture, referentiels, perimetres, journal, sequence);
    }
}
