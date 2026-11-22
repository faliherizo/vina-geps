package ca.vina.nexu.auth.infrastructure.configuration;

import ca.vina.nexu.auth.application.ServiceApprobationsLiaison;
import ca.vina.nexu.auth.application.ServiceResolutionIdentite;
import ca.vina.nexu.auth.infrastructure.persistance.AdaptateurComptesAnnuaire;
import ca.vina.nexu.auth.infrastructure.persistance.AdaptateurInvitationsLiaison;
import ca.vina.nexu.auth.infrastructure.persistance.DepotComptes;
import ca.vina.nexu.auth.infrastructure.persistance.DepotDemandesLiaison;
import ca.vina.nexu.auth.infrastructure.persistance.DepotIdentitesExternes;
import ca.vina.nexu.auth.infrastructure.persistance.DepotInvitations;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.security.SecureRandom;
import java.time.Clock;

/**
 * Le câblage de la résolution d'identité (ADR 0093). Le service est <strong>un seul</strong> pour le processus : la
 * liaison en cours, posée au retour d'un annuaire ou du SSO d'instance, se prouve ensuite par d'autres routes.
 */
@Configuration
public class ConfigurationIdentitesExternes {

    @Bean
    public ServiceResolutionIdentite.Ports portsIdentitesExternes(DepotIdentitesExternes identites, DepotComptes comptes, DepotInvitations invitations,
                                                                  DepotDemandesLiaison demandes, Clock horloge) {
        SecureRandom alea = new SecureRandom();
        return new ServiceResolutionIdentite.Ports(identites, identites, new AdaptateurComptesAnnuaire(comptes, alea),
                new AdaptateurInvitationsLiaison(invitations, comptes, horloge, alea), demandes, demandes);
    }

    @Bean
    public ServiceResolutionIdentite serviceResolutionIdentite(ServiceResolutionIdentite.Ports ports, Clock horloge) {
        return new ServiceResolutionIdentite(ports, horloge, new SecureRandom());
    }

    @Bean
    public ServiceApprobationsLiaison serviceApprobationsLiaison(ServiceResolutionIdentite.Ports ports, Clock horloge) {
        return new ServiceApprobationsLiaison(ports, horloge);
    }
}
