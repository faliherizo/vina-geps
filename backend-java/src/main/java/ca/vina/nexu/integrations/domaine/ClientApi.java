package ca.vina.nexu.integrations.domaine;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

/**
 * Client d'API pour intégration machine-à-machine (AO-5338 #NF-08).
 *
 * <p>Le secret n'est <strong>jamais</strong> porté par cet objet : il n'existe en clair qu'à la
 * création, dans {@link AvecSecret}, et n'est plus jamais relisible ensuite. C'est la raison
 * d'être des deux types — un seul type portant un secret optionnel finirait tôt ou tard
 * sérialisé dans une liste.
 */
public record ClientApi(String id, String tenantId, String name, String clientId,
                        List<String> scopes, boolean active, String createdAt, String lastUsedAt) {

    /** Portées reconnues. Une portée inconnue est écartée, pas refusée — comme côté Node. */
    public static final List<String> PORTEES = List.of("read", "write");

    /** Le secret en clair, remis une seule fois à la création. */
    public record AvecSecret(ClientApi client, String clientSecret) {
    }

    public static class PorteeInvalideException extends IllegalArgumentException {
        public PorteeInvalideException() {
            super("at least one valid scope required (read|write)");
        }
    }

    /**
     * Portées retenues : défaut « read » si aucune n'est demandée, valeurs inconnues écartées,
     * doublons supprimés en conservant l'ordre de la demande.
     */
    public static List<String> assainirPortees(List<String> demandees) {
        List<String> source = demandees == null || demandees.isEmpty() ? List.of("read") : demandees;
        List<String> retenues = new ArrayList<>(new LinkedHashSet<>(
                source.stream().filter(PORTEES::contains).toList()));
        if (retenues.isEmpty()) {
            throw new PorteeInvalideException();
        }
        return List.copyOf(retenues);
    }
}
