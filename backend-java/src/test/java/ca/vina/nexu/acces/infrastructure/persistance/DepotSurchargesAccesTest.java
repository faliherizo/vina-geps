package ca.vina.nexu.acces.infrastructure.persistance;

import ca.vina.nexu.SchemaEssai;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.jdbc.core.simple.JdbcClient;

import java.nio.file.Path;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Surcharges d'accès — lues par établissement et rôle")
class DepotSurchargesAccesTest {

    @Test
    void litLesDecisionsDeLEtablissement(@TempDir Path dossier) {
        JdbcClient jdbc = SchemaEssai.base(dossier);
        jdbc.sql("INSERT INTO tenant_role_capabilities (tenantId, role, capability, allowed) VALUES ('t-1', 'employee', 'payroll.run', 1), ('t-1', 'employee', 'employee.create', 0)").update();
        DepotSurchargesAcces depot = new DepotSurchargesAcces(jdbc);
        assertThat(depot.pour("t-1", "employee")).isEqualTo(Map.of("payroll.run", true, "employee.create", false));
        assertThat(depot.pour("t-2", "employee")).isEmpty();
        assertThat(depot.pour(null, "employee")).isEmpty();
        assertThat(depot.pour("t-1", null)).isEmpty();
    }
}
