package ca.vina.nexu.paie.infrastructure.configuration;

import ca.vina.nexu.paie.application.commandes.GestionnaireCyclesPaie;
import ca.vina.nexu.paie.application.commandes.GestionnaireDossiersPaie;
import ca.vina.nexu.paie.application.requetes.LecturePaie;
import ca.vina.nexu.paie.infrastructure.persistance.BaremesFichiers;
import ca.vina.nexu.paie.infrastructure.persistance.DepotPaie;
import ca.vina.nexu.paie.infrastructure.persistance.UniteDeTravailSpring;
import ca.vina.nexu.paie.infrastructure.web.ControleurPaie;
import ca.vina.nexu.paie.infrastructure.web.GardePaie;
import ca.vina.nexu.partage.identifiants.domaine.Sequence;
import ca.vina.nexu.partage.modules.ModulesActives;
import ca.vina.nexu.partage.paie.HeuresApprouveesPort;
import ca.vina.nexu.partage.paie.PersonnesPourPaiePort;
import ca.vina.nexu.partage.web.GardeAcces;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;

/** Le câblage du contexte de la paie (ADR 0076), en un seul endroit. */
@Configuration
public class ConfigurationPaie {

    @Bean
    public DepotPaie depotPaie(JdbcClient jdbc) {
        return new DepotPaie(jdbc);
    }

    @Bean
    public GardePaie gardePaie(GardeAcces garde, ModulesActives modules, Clock horloge) {
        return new GardePaie(garde, modules, horloge);
    }

    @Bean
    public ControleurPaie.Cas casPaie(DepotPaie depot, HeuresApprouveesPort heures, PersonnesPourPaiePort personnes,
                                      TransactionTemplate transaction, Sequence sequence) {
        LecturePaie lecture = new LecturePaie(depot, new BaremesFichiers(), personnes);
        return new ControleurPaie.Cas(new GestionnaireDossiersPaie(depot, personnes),
                new GestionnaireCyclesPaie(new GestionnaireCyclesPaie.Dependances(lecture, depot, heures, new UniteDeTravailSpring(transaction), sequence)),
                lecture);
    }
}
