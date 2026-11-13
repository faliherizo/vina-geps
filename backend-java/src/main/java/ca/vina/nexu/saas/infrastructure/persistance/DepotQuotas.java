package ca.vina.nexu.saas.infrastructure.persistance;

import ca.vina.nexu.saas.domaine.CatalogueModules;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import tools.jackson.databind.json.JsonMapper;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Quotas et modules d'un établissement ({@code tenant_quotas}, {@code tenants.planCode}) — SQL repris de {@code saas/tenants.ts}. */
@Repository
public class DepotQuotas {

    public static final class IntrouvableException extends RuntimeException {
        public IntrouvableException(String message) {
            super(message);
        }
    }

    private static final JsonMapper JSON = JsonMapper.builder().build();
    private final JdbcClient jdbc;

    public DepotQuotas(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public record Quota(String tenantId, int maxTeachers, int maxContractsPerYear, List<String> modules) { }

    public Optional<Quota> quota(String tenantId) {
        return jdbc.sql("SELECT tenantId, maxTeachers, maxContractsPerYear, modules FROM tenant_quotas WHERE tenantId = ?").params(tenantId)
                .query((rs, i) -> new Quota(rs.getString("tenantId"), rs.getInt("maxTeachers"), rs.getInt("maxContractsPerYear"),
                        JSON.readValue(rs.getString("modules"), JSON.getTypeFactory().constructCollectionType(List.class, String.class)))).optional();
    }

    public Optional<String> planCode(String tenantId) {
        return jdbc.sql("SELECT planCode FROM tenants WHERE id = ?").params(tenantId).query(String.class).optional();
    }

    /** L'état précédent est ce qui distingue un ajout d'un retrait ; la réponse dit ce qui a changé d'office. */
    public Map<String, Object> reglerModules(String tenantId, List<String> modules) {
        List<String> demandes = modules.stream().map(String::trim).filter(m -> !m.isEmpty()).toList();
        Quota existant = quota(tenantId).orElseThrow(() -> new IntrouvableException("tenant quota not found"));
        CatalogueModules.Resolution resolution = CatalogueModules.resoudreDependances(demandes, existant.modules());
        jdbc.sql("UPDATE tenant_quotas SET modules = ? WHERE tenantId = ?").params(JSON.writeValueAsString(resolution.modules()), tenantId).update();
        Quota apres = quota(tenantId).orElseThrow();
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("tenantId", apres.tenantId());
        r.put("maxTeachers", apres.maxTeachers());
        r.put("maxContractsPerYear", apres.maxContractsPerYear());
        r.put("modules", apres.modules());
        r.put("ajoutes", resolution.ajoutes());
        r.put("retires", resolution.retires());
        return r;
    }
}
