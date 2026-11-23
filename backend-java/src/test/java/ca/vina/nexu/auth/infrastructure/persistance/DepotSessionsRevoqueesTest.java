package ca.vina.nexu.auth.infrastructure.persistance;

import ca.vina.nexu.SchemaEssai;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.jdbc.core.simple.JdbcClient;

import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

/** PT-02 : une session révoquée le reste jusqu'à l'échéance de son jeton, puis l'entrée disparaît. */
@DisplayName("Sessions révoquées — inscription, lecture, purge")
class DepotSessionsRevoqueesTest {

    private static final Instant MAINTENANT = Instant.parse("2026-11-22T12:00:00Z");

    @Test
    @DisplayName("révoquer, relire, réinscrire sans doublon ; une entrée échue est purgée à la révocation suivante")
    void revoquerEtPurger(@TempDir Path dossier) {
        JdbcClient jdbc = SchemaEssai.base(dossier);
        DepotSessionsRevoquees depot = new DepotSessionsRevoquees(jdbc, Clock.fixed(MAINTENANT, ZoneOffset.UTC));
        assertThat(depot.revoquee("j-1")).isFalse();
        depot.revoquer("j-1", MAINTENANT.plusSeconds(3600));
        depot.revoquer("j-1", MAINTENANT.plusSeconds(3600));
        assertThat(depot.revoquee("j-1")).isTrue();
        jdbc.sql("INSERT INTO auth_sessions_revoquees (jti, expireLe, revoqueeLe) VALUES ('j-echu', '2026-11-22T11:59:59.000Z', 'x')").update();
        depot.revoquer("j-2", null);
        assertThat(depot.revoquee("j-echu")).isFalse();
        assertThat(jdbc.sql("SELECT expireLe FROM auth_sessions_revoquees WHERE jti = 'j-2'").query(String.class).single()).isEqualTo("2026-11-22T20:00:00.000Z");
        assertThat(jdbc.sql("SELECT COUNT(*) FROM auth_sessions_revoquees").query(Integer.class).single()).isEqualTo(2);
    }
}
