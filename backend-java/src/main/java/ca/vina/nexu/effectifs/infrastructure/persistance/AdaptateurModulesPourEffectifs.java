package ca.vina.nexu.effectifs.infrastructure.persistance;

import ca.vina.nexu.effectifs.domaine.ModulesPort;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;

/**
 * Les modules d'un établissement, lus dans {@code tenant_quotas.modules} (un tableau JSON). L'appartenance
 * se teste au tableau désérialisé — « effectifs » ne doit pas correspondre à « effectifs_regles » — et un
 * tableau illisible n'accorde rien.
 */
@Repository("effectifsAdaptateurModulesPourEffectifs")
public class AdaptateurModulesPourEffectifs implements ModulesPort {

    private static final TypeReference<List<String>> CODES = new TypeReference<>() { };

    private final JdbcClient jdbc;

    public AdaptateurModulesPourEffectifs(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public boolean estActive(String tenantId, String module) {
        return jdbc.sql("SELECT modules FROM tenant_quotas WHERE tenantId = ?").param(tenantId).query(String.class).optional()
                .map(AdaptateurModulesPourEffectifs::codes).orElse(List.of()).contains(module);
    }

    static List<String> codes(String json) {
        try {
            return json == null ? List.of() : JsonMapper.builder().build().readValue(json, CODES);
        } catch (RuntimeException e) {
            return List.of();
        }
    }
}
