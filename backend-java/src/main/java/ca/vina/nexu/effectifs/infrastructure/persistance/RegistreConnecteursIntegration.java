package ca.vina.nexu.effectifs.infrastructure.persistance;

import ca.vina.nexu.effectifs.domaine.ConnecteursIntegrationPort;
import ca.vina.nexu.effectifs.domaine.DestinationIntegrationPort;
import ca.vina.nexu.effectifs.domaine.IntegrationsEffectifs;
import ca.vina.nexu.effectifs.domaine.IntegrationsEffectifs.CodeConnecteur;
import ca.vina.nexu.effectifs.domaine.IntegrationsEffectifs.ConnecteurEnregistre;
import ca.vina.nexu.effectifs.domaine.SecretsIntegrationPort;
import ca.vina.nexu.effectifs.domaine.SourceDossiersIntegrationPort;
import ca.vina.nexu.effectifs.domaine.SourceHorodatagesIntegrationPort;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Le <strong>registre des implémentations</strong> : la configuration de l'organisme choisit le simulateur, l'adaptateur
 * HTTP ou l'adaptateur SFTP (fichiers de lot). Les simulateurs sortants sont gardés par organisme et par connecteur : la démonstration et les essais lisent ce
 * qu'ils ont reçu. Le jeton se lit au moment de l'appel, jamais avant.
 */
public class RegistreConnecteursIntegration implements ConnecteursIntegrationPort {

    private final SecretsIntegrationPort secrets;
    private final ClientHttpIntegration.Appel appel;
    private final Map<String, DestinationSimuleeIntegration> simulees = new ConcurrentHashMap<>();

    public RegistreConnecteursIntegration(SecretsIntegrationPort secrets, ClientHttpIntegration.Appel appel) {
        this.secrets = secrets;
        this.appel = appel;
    }

    /** Le simulateur d'un connecteur sortant pour un organisme : toujours le même, pour qu'on puisse lire ce qu'il a reçu. */
    public DestinationSimuleeIntegration destinationSimulee(String tenantId, CodeConnecteur code) {
        return simulees.computeIfAbsent(tenantId + "|" + code, k -> new DestinationSimuleeIntegration());
    }

    /** La clé privée, lue au moment de l'appel ; le mode SFTP l'exige, la validation de la configuration l'a vérifié. */
    private ClientSftpIntegration.ConfigSftp sftp(ConnecteurEnregistre c) {
        String cle = IntegrationsEffectifs.jetonDe(c.config().jetonRef(), secrets).orElseThrow(
                () -> new IllegalStateException("connecteur " + c.code() + " en mode SFTP sans clé privée"));
        return new ClientSftpIntegration.ConfigSftp(IntegrationsEffectifs.adresseSftp(c.config().url()), cle);
    }

    private ClientHttpIntegration.ConfigHttp http(ConnecteurEnregistre c) {
        return new ClientHttpIntegration.ConfigHttp(c.config().url(), IntegrationsEffectifs.jetonDe(c.config().jetonRef(), secrets));
    }

    @Override
    public DestinationIntegrationPort destination(String tenantId, ConnecteurEnregistre c) {
        return switch (c.config().mode()) {
            case HTTP -> new DestinationHttpIntegration(http(c), appel);
            case SFTP -> new DestinationSftpIntegration(sftp(c));
            case SIMULATEUR -> destinationSimulee(tenantId, c.code());
        };
    }

    @Override
    public SourceHorodatagesIntegrationPort horodatages(String tenantId, ConnecteurEnregistre c) {
        return switch (c.config().mode()) {
            case HTTP -> new HorodatagesHttpIntegration(http(c), appel);
            case SFTP -> LotsSftpIntegration.horodatages(sftp(c));
            case SIMULATEUR -> new HorodatagesSimulesIntegration(c.jeuEssai().stream().map(IntegrationsEffectifs::horodatageDe).toList());
        };
    }

    @Override
    public SourceDossiersIntegrationPort dossiers(String tenantId, ConnecteurEnregistre c) {
        return switch (c.config().mode()) {
            case HTTP -> new DossiersHttpIntegration(http(c), appel);
            case SFTP -> LotsSftpIntegration.dossiers(sftp(c));
            case SIMULATEUR -> new DossiersSimulesIntegration(c.jeuEssai().stream().map(IntegrationsEffectifs::dossierDe).toList());
        };
    }
}
