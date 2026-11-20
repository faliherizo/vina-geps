package ca.vina.nexu.saas.infrastructure.configuration;

import ca.vina.nexu.saas.domaine.AnnexeDestruction;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Le signataire autorisé de l'annexe 10.18.04 C, lu en ce seul endroit (`saas.attestation.*`). */
@Configuration
public class ConfigurationAttestation {

    @Bean
    public AnnexeDestruction.Signataire signataireAttestation(@Value("${saas.attestation.signataire}") String nom,
                                                             @Value("${saas.attestation.fournisseur}") String fournisseur,
                                                             @Value("${saas.attestation.adresse}") String adresse) {
        return new AnnexeDestruction.Signataire(nom, fournisseur, adresse);
    }
}
