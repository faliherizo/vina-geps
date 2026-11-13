package ca.vina.nexu.communication.domaine;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Port : par quels canaux joindre une personne pour un événement de la communication, dans
 * l'ordre — jamais vide. Le fil d'une conversation le montre à côté de chaque participant.
 */
@FunctionalInterface
public interface AcheminementPort {

    List<Canal> acheminement(String tenantId, String personId, String evenement);

    record Canal(String canal, String mode, int ordrePriorite, boolean parDefaut) {
        public Map<String, Object> enCarte() {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("canal", canal);
            m.put("mode", mode);
            m.put("ordrePriorite", ordrePriorite);
            m.put("parDefaut", parDefaut);
            return m;
        }
    }
}
