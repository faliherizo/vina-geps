package ca.vina.nexu.effectifs.domaine;

/**
 * Port du <strong>registre des implémentations</strong> : la configuration de l'organisme choisit le simulateur ou
 * l'adaptateur HTTP (DIP — le métier ne sait pas lequel il appelle).
 */
public interface ConnecteursIntegrationPort {

    DestinationIntegrationPort destination(String tenantId, IntegrationsEffectifs.ConnecteurEnregistre c);

    SourceHorodatagesIntegrationPort horodatages(String tenantId, IntegrationsEffectifs.ConnecteurEnregistre c);

    SourceDossiersIntegrationPort dossiers(String tenantId, IntegrationsEffectifs.ConnecteurEnregistre c);
}
