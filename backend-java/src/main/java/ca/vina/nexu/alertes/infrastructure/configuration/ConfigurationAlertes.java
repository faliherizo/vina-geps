package ca.vina.nexu.alertes.infrastructure.configuration;

import ca.vina.nexu.alertes.application.commandes.GestionnaireEnvoiAlertes;
import ca.vina.nexu.alertes.application.commandes.GestionnaireParametresAlertes;
import ca.vina.nexu.alertes.application.commandes.GestionnaireReceptionAlertes;
import ca.vina.nexu.alertes.application.requetes.LectureAlertes;
import ca.vina.nexu.alertes.infrastructure.persistance.AdaptateurAnnuaireAlertes;
import ca.vina.nexu.alertes.infrastructure.persistance.DepotAlertes;
import ca.vina.nexu.alertes.infrastructure.web.ControleurAlertes;
import ca.vina.nexu.alertes.infrastructure.web.GardeAlertes;
import ca.vina.nexu.communication.domaine.GroupesDeTravailPort;
import ca.vina.nexu.communication.domaine.LectureListesDistributionPort;
import ca.vina.nexu.partage.avis.domaine.PublicationAvis;
import ca.vina.nexu.partage.modules.ModulesActives;
import ca.vina.nexu.partage.web.GardeAcces;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.simple.JdbcClient;

import java.time.Clock;
import java.util.UUID;

/** Le câblage du contexte des alertes (ADR 0074), en un seul endroit. */
@Configuration
public class ConfigurationAlertes {

    @Bean
    public DepotAlertes depotAlertes(JdbcClient jdbc) {
        return new DepotAlertes(jdbc);
    }

    @Bean
    public AdaptateurAnnuaireAlertes annuaireAlertes(GroupesDeTravailPort groupes, LectureListesDistributionPort listes, JdbcClient jdbc) {
        return new AdaptateurAnnuaireAlertes(groupes, listes, jdbc);
    }

    @Bean
    public GardeAlertes gardeAlertes(GardeAcces garde, ModulesActives modules) {
        return new GardeAlertes(garde, modules);
    }

    @Bean
    public ControleurAlertes.Cas casAlertes(DepotAlertes depot, AdaptateurAnnuaireAlertes annuaire, PublicationAvis avis, Clock horloge) {
        // L'identifiant est tiré ici, à la frontière : le domaine le reçoit, il ne le tire jamais.
        java.util.function.Supplier<String> ids = () -> UUID.randomUUID().toString();
        return new ControleurAlertes.Cas(new GestionnaireParametresAlertes(new GestionnaireParametresAlertes.Dependances(depot, depot, annuaire, horloge), ids),
                new GestionnaireEnvoiAlertes(new GestionnaireEnvoiAlertes.Dependances(depot, depot, annuaire, avis), ids, horloge),
                new GestionnaireReceptionAlertes(depot, depot, horloge), new LectureAlertes(depot, annuaire));
    }
}
