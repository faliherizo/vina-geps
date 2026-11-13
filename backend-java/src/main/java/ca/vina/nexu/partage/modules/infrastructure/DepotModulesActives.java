package ca.vina.nexu.partage.modules.infrastructure;

import ca.vina.nexu.partage.modules.ModulesActives;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;
import java.util.Optional;

/**
 * Adaptateur du port {@link ModulesActives} sur {@code tenant_quotas.modules}.
 *
 * <p>La colonne porte un tableau JSON, et le Node teste l'appartenance <strong>au tableau
 * désérialisé</strong> : « payroll » ne doit pas correspondre à « payroll_avance ». Le tableau
 * est donc lu comme du JSON, pas cherché dans le texte.
 */
@Repository
public class DepotModulesActives implements ModulesActives {

    private static final ObjectMapper JSON = JsonMapper.builder().build();
    private static final TypeReference<List<String>> CODES = new TypeReference<>() { };

    private final JdbcClient jdbc;

    public DepotModulesActives(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public boolean estActive(String tenantId, String module) {
        if (tenantId == null || module == null) {
            return false;
        }
        Optional<String> json = jdbc.sql("SELECT modules FROM tenant_quotas WHERE tenantId = ?")
                .param(tenantId).query(String.class).optional();
        return json.map(DepotModulesActives::codes).orElse(List.of()).contains(module);
    }

    public static List<String> codes(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return JSON.readValue(json, CODES);
        } catch (RuntimeException e) {
            // Un tableau illisible n'accorde rien : refus par défaut.
            return List.of();
        }
    }
}
