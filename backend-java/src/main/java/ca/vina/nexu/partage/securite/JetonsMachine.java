package ca.vina.nexu.partage.securite;

import java.util.Optional;
import java.util.Set;

/**
 * Port : la lecture d'un jeton <em>machine</em> ({@code client_credentials}) — celui d'une application de l'organisme, sans
 * identité humaine. Il vit dans {@code partage} pour que la garde des routes machine ne dépende pas du contexte qui
 * émet le jeton (les intégrations).
 */
public interface JetonsMachine {

    /** Le jeton lu, ou vide s'il est invalide, expiré, ou n'est pas un jeton machine (une session, par exemple). */
    Optional<Application> lire(String jeton);

    /** L'application authentifiée : son identifiant client, son organisme, ses portées. */
    record Application(String clientId, String tenantId, Set<Portee> portees) {

        public Application {
            portees = Set.copyOf(portees);
        }

        public boolean peut(Portee portee) {
            return portees.contains(portee);
        }
    }

    /** Les portées d'un client d'API, ensemble fermé ; {@code code} est la valeur portée par le jeton (OAuth2 {@code scope}). */
    enum Portee {
        LECTURE("read"), ECRITURE("write");

        private final String code;

        Portee(String code) {
            this.code = code;
        }

        public String code() {
            return code;
        }

        /** Une portée inconnue du jeton est ignorée, comme à l'émission (voir {@code ClientApi.assainirPortees}). */
        public static Optional<Portee> de(String code) {
            for (Portee p : values()) {
                if (p.code.equals(code)) {
                    return Optional.of(p);
                }
            }
            return Optional.empty();
        }
    }
}
