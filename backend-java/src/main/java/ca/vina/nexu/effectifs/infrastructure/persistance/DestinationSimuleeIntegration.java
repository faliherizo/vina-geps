package ca.vina.nexu.effectifs.infrastructure.persistance;

import ca.vina.nexu.effectifs.domaine.DestinationIntegrationPort;
import ca.vina.nexu.effectifs.domaine.IntegrationsEffectifs.Acceptee;
import ca.vina.nexu.effectifs.domaine.IntegrationsEffectifs.Issue;
import ca.vina.nexu.effectifs.domaine.IntegrationsEffectifs.MessageALivrer;
import ca.vina.nexu.effectifs.domaine.IntegrationsEffectifs.Rejetee;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Le destinataire simulé : accepte tout, sauf une charge qui demande le rejet ; un message déjà reçu rend la même référence
 * (idempotence). La démonstration et les essais lisent ce qu'il a reçu.
 */
public class DestinationSimuleeIntegration implements DestinationIntegrationPort {

    /** Une valeur de charge qui commence par ce préfixe fait rejeter le message : l'essai et la démonstration jouent ainsi le rejet et son rejeu. */
    public static final String PREFIXE_REJET_SIMULE = "REJ-";

    private final Map<String, MessageALivrer> recus = new LinkedHashMap<>();

    static boolean contientRejet(Object v) {
        return switch (v) {
            case String s -> s.startsWith(PREFIXE_REJET_SIMULE);
            case Collection<?> l -> l.stream().anyMatch(DestinationSimuleeIntegration::contientRejet);
            case Map<?, ?> m -> m.values().stream().anyMatch(DestinationSimuleeIntegration::contientRejet);
            case null, default -> false;
        };
    }

    @Override
    public synchronized Issue livrer(MessageALivrer m) {
        if (contientRejet(m.charge())) {
            return new Rejetee(m.connecteur() + " : valeur refusée par le destinataire (simulation)");
        }
        recus.put(m.cle(), m);
        return new Acceptee(m.connecteur() + "-SIM-" + (new ArrayList<>(recus.keySet()).indexOf(m.cle()) + 1));
    }

    /** Ce que le destinataire a reçu, par clé. */
    public synchronized Map<String, MessageALivrer> recus() {
        return Map.copyOf(recus);
    }
}
