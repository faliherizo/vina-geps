package ca.vina.nexu.effectifs.infrastructure.persistance;

import ca.vina.nexu.effectifs.domaine.SecretsIntegrationPort;
import org.springframework.stereotype.Component;

import java.util.Optional;

/** Les variables d'environnement du serveur : le seul endroit où le jeton d'un connecteur existe, lu au moment de l'appel. */
@Component("effectifsSecretsIntegration")
public class AdaptateurSecretsIntegration implements SecretsIntegrationPort {

    @Override
    public Optional<String> variable(String nom) {
        return Optional.ofNullable(System.getenv(nom));
    }
}
