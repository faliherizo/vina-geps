package ca.vina.nexu.effectifs.infrastructure.configuration;

import ca.vina.nexu.effectifs.application.commandes.EvaluateurRegles;
import ca.vina.nexu.effectifs.application.commandes.GestionnaireEchangesEffectifs;
import ca.vina.nexu.effectifs.application.requetes.LectureEchangesEffectifs;
import ca.vina.nexu.effectifs.application.requetes.LectureEquipages;
import ca.vina.nexu.effectifs.application.requetes.LecturePerimetres;
import ca.vina.nexu.effectifs.application.requetes.LectureRessources;
import ca.vina.nexu.effectifs.domaine.DepotEchangesEffectifsPort;
import ca.vina.nexu.effectifs.domaine.JournalEffectifsPort;
import ca.vina.nexu.effectifs.domaine.SourceEchangesEffectifsPort;
import ca.vina.nexu.partage.identifiants.domaine.Sequence;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Le câblage des échanges de quarts (lot R6) : du Java nu, assemblé ici, sans rouvrir la configuration de R3. */
@Configuration
public class ConfigurationEchangesEffectifs {

    @Bean
    public LectureEchangesEffectifs lectureEchangesEffectifs(SourceEchangesEffectifsPort source, LectureRessources ressources, LecturePerimetres perimetres) {
        return new LectureEchangesEffectifs(source, ressources, perimetres);
    }

    @Bean
    public GestionnaireEchangesEffectifs gestionnaireEchangesEffectifs(LectureEchangesEffectifs echanges, LectureRessources ressources,
                                                                       LecturePerimetres perimetres, LectureEquipages equipages, EvaluateurRegles regles,
                                                                       DepotEchangesEffectifsPort depot, JournalEffectifsPort journal, Sequence sequence) {
        return new GestionnaireEchangesEffectifs(new GestionnaireEchangesEffectifs.Lectures(echanges, ressources, perimetres, equipages), regles, depot,
                journal, sequence);
    }
}
