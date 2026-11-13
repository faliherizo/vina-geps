package ca.vina.nexu.partage.modules.infrastructure;

import ca.vina.nexu.SchemaEssai;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.jdbc.core.simple.JdbcClient;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Modules actifs — appartenance au tableau JSON, refus par défaut")
class DepotModulesActivesTest {

    @Test
    @DisplayName("un code est actif s'il figure au tableau, jamais par préfixe ; sans organisme, sans module ou sans quota : refusé")
    void appartenance(@TempDir Path dossier) {
        JdbcClient jdbc = SchemaEssai.base(dossier);
        jdbc.sql("INSERT INTO tenant_quotas (tenantId, maxTeachers, maxContractsPerYear, modules) VALUES ('T', 1, 1, '[\"effectifs_temps\"]'), ('V', 1, 1, '')").update();
        DepotModulesActives depot = new DepotModulesActives(jdbc);
        assertThat(depot.estActive("T", "effectifs_temps")).isTrue();
        assertThat(depot.estActive("T", "effectifs")).as("pas de correspondance par préfixe").isFalse();
        assertThat(depot.estActive(null, "effectifs_temps")).isFalse();
        assertThat(depot.estActive("T", null)).isFalse();
        assertThat(depot.estActive("inconnu", "effectifs_temps")).isFalse();
        assertThat(depot.estActive("V", "effectifs_temps")).isFalse();
    }

    @Test
    @DisplayName("un tableau illisible n'accorde rien")
    void illisible() {
        assertThat(DepotModulesActives.codes("{pas du json")).isEmpty();
        assertThat(DepotModulesActives.codes(null)).isEmpty();
        assertThat(DepotModulesActives.codes("[\"a\",\"b\"]")).containsExactly("a", "b");
    }
}
