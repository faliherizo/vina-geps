package ca.vina.nexu.organisation.infrastructure.configuration;

import ca.vina.nexu.organisation.application.commandes.GestionnaireOrganisation;
import ca.vina.nexu.organisation.application.requetes.LectureOrganisation;
import ca.vina.nexu.organisation.domaine.DepotOrganisationPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Le câblage du contexte {@code organisation}.
 *
 * <p>L'identifiant est <strong>injecté</strong>, comme l'horloge : un domaine qui tire son propre
 * aléatoire n'est pas reproductible, et une réorganisation qu'on ne peut pas rejouer à l'identique
 * n'est pas vérifiable.
 */
@Configuration
public class ConfigurationOrganisation {

    @Bean
    public LectureOrganisation lectureOrganisation(DepotOrganisationPort depot, Clock horloge) {
        return new LectureOrganisation(depot, horloge);
    }

    @Bean
    public GestionnaireOrganisation gestionnaireOrganisation(DepotOrganisationPort depot,
                                                             LectureOrganisation lecture, Clock horloge) {
        Supplier<String> identifiants = () -> "orgv_" + UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        return new GestionnaireOrganisation(depot, lecture, horloge, identifiants);
    }
}
