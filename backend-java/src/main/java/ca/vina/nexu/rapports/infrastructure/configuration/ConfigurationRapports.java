package ca.vina.nexu.rapports.infrastructure.configuration;

import ca.vina.nexu.partage.identifiants.domaine.Sequence;
import ca.vina.nexu.rapports.infrastructure.persistance.DepotIndicateurs;
import ca.vina.nexu.rapports.infrastructure.persistance.DepotRapportsEnregistres;
import ca.vina.nexu.rapports.infrastructure.persistance.DepotUtilisation;
import ca.vina.nexu.rapports.infrastructure.web.ControleurAnalyseRapports;
import ca.vina.nexu.rapports.infrastructure.web.ControleurRapports;
import ca.vina.nexu.saas.infrastructure.persistance.DepotQuotas;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

/** Le câblage du constructeur de rapports : les ports que le contrôleur reçoit au-delà du moteur et de la garde. */
@Configuration
public class ConfigurationRapports {

    @Bean
    public ControleurRapports.Ports portsRapports(DepotQuotas quotas, DepotRapportsEnregistres enregistres, Clock horloge, Sequence sequence) {
        return new ControleurRapports.Ports(quotas, enregistres, horloge, sequence);
    }

    @Bean
    public ControleurAnalyseRapports.Ports portsAnalyseRapports(DepotQuotas quotas, DepotIndicateurs indicateurs, DepotUtilisation utilisation, Clock horloge,
                                                               Sequence sequence) {
        return new ControleurAnalyseRapports.Ports(quotas, indicateurs, utilisation, horloge, sequence);
    }
}
