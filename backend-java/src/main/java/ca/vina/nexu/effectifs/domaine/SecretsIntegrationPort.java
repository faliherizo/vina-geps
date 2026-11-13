package ca.vina.nexu.effectifs.domaine;

import java.util.Optional;

/**
 * Port : la valeur d'une variable d'environnement du serveur, lue <strong>au moment de l'appel</strong>. La base ne garde
 * que le nom de la variable : un secret qui n'est jamais écrit ne fuit pas par une sauvegarde ou un export.
 */
@FunctionalInterface
public interface SecretsIntegrationPort {
    Optional<String> variable(String nom);
}
