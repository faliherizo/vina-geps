package ca.vina.nexu.integrations.domaine;

import java.util.Map;

/**
 * Port : l'envoi HTTP d'une livraison de webhook. L'adaptateur réel fait un POST avec délai ; les
 * essais branchent un faux récepteur. Un échec de transport est une exception nommée, jamais un
 * code d'erreur.
 */
@FunctionalInterface
public interface Transport {

    final class EchecTransportException extends Exception {
        public EchecTransportException(String message) {
            super(message);
        }
    }

    /** Retourne le code HTTP du récepteur, ou lève un {@link EchecTransportException} (délai, connexion refusée…). */
    int envoyer(String url, String corps, Map<String, String> enTetes) throws EchecTransportException;
}
