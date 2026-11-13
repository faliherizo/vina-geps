package ca.vina.nexu.ordonnanceur.infrastructure.configuration;

import ca.vina.nexu.ordonnanceur.application.commandes.DeclencherTic;
import ca.vina.nexu.ordonnanceur.application.requetes.LectureTaches;
import ca.vina.nexu.ordonnanceur.domaine.TacheDifferee;
import ca.vina.nexu.partage.temps.domaine.Horloge;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Le câblage de l'ordonnanceur (ADR 0025) : l'identité du tic manuel nomme le processus. */
@Configuration
public class ConfigurationOrdonnanceur {

    @Bean
    public LectureTaches lectureTaches(TacheDifferee.Taches taches) {
        return new LectureTaches(taches);
    }

    @Bean
    public DeclencherTic declencherTic(TacheDifferee.Taches taches, Horloge horloge) {
        return new DeclencherTic(taches, horloge, "tic-" + ProcessHandle.current().pid());
    }
}
