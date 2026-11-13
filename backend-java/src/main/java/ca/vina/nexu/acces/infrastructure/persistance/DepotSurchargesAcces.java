package ca.vina.nexu.acces.infrastructure.persistance;

import ca.vina.nexu.acces.domaine.SurchargesAcces;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.util.HashMap;
import java.util.Map;

/** La table {@code tenant_role_capabilities}, lue par la garde d'accès pour chaque capacité exigée. */
@Repository
public class DepotSurchargesAcces implements SurchargesAcces {

    private final JdbcClient jdbc;

    public DepotSurchargesAcces(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Map<String, Boolean> pour(String tenantId, String role) {
        Map<String, Boolean> s = new HashMap<>();
        if (tenantId == null || role == null) {
            return s;
        }
        jdbc.sql("SELECT capability, allowed FROM tenant_role_capabilities WHERE tenantId = ? AND role = ?")
                .params(tenantId, role).query((rs, i) -> s.put(rs.getString("capability"), rs.getInt("allowed") == 1)).list();
        return s;
    }
}
