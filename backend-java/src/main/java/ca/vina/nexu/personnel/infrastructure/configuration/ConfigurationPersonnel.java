package ca.vina.nexu.personnel.infrastructure.configuration;

import ca.vina.nexu.personnel.application.requetes.LecturePersonnes;
import ca.vina.nexu.personnel.domaine.RepertoirePersonnes;
import ca.vina.nexu.partage.identifiants.domaine.Sequence;
import ca.vina.nexu.partage.personnel.FichesPersonnelPort;
import ca.vina.nexu.personnel.application.commandes.GestionnairePersonnes;
import ca.vina.nexu.personnel.application.commandes.InscriptionDepuisEffectifs;
import ca.vina.nexu.personnel.domaine.ComptesPersonnelPort;
import ca.vina.nexu.personnel.infrastructure.persistance.DepotRepertoirePersonnes;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

/** Le câblage du contexte {@code personnel} — cas d'utilisation en Java nu, sans Spring. */
@Configuration
public class ConfigurationPersonnel {

    @Bean
    public GestionnairePersonnes gestionnairePersonnes(DepotRepertoirePersonnes depot, Sequence sequence, Clock horloge) {
        return new GestionnairePersonnes(depot, depot, sequence, horloge);
    }

    /** Ce qu'effectifs appelle quand une ressource reliée à un compte est créée ou modifiée. */
    @Bean
    public FichesPersonnelPort fichesPersonnel(ComptesPersonnelPort comptes, GestionnairePersonnes gestionnaire) {
        return new InscriptionDepuisEffectifs(comptes, gestionnaire);
    }

    @Bean
    public LecturePersonnes lecturePersonnes(RepertoirePersonnes repertoire) {
        return new LecturePersonnes(repertoire);
    }
}
