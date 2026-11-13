package ca.vina.nexu.rapports.infrastructure.persistance;

import ca.vina.nexu.SchemaEssai;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.jdbc.core.simple.JdbcClient;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/** Une base réduite, sans journal d'audit ni invitations : aucune activité connue, jamais une panne. */
@DisplayName("Utilisation — une base sans journal ni invitations se lit quand même")
class DepotUtilisationTest {

    @Test
    @DisplayName("les comptes sont rendus sans activité et sans invité quand les tables manquent")
    void baseReduite(@TempDir Path dossier) {
        JdbcClient jdbc = SchemaEssai.base(dossier);
        jdbc.sql("CREATE TABLE IF NOT EXISTS horaire_periodes (id TEXT PRIMARY KEY, tenantId TEXT NOT NULL, statut TEXT NOT NULL)").update();
        jdbc.sql("DROP TABLE audit_log").update();
        jdbc.sql("DROP TABLE auth_invitations").update();
        jdbc.sql("INSERT INTO users (id, username, tenantId, passwordHash, role, createdAt, active) VALUES ('u1', 'alice', 't-1', 'x', 'employee', 'x', 1)").update();

        DepotUtilisation.Listes l = new DepotUtilisation(jdbc).lire("t-1", "2027-03-01", "2027-03-31", "2027-04-01T00:00:00.000Z");

        assertThat(l.quarts().publies()).isZero();
        assertThat(l.comptes()).singleElement().satisfies(c -> {
            assertThat(c.identifiant()).isEqualTo("alice");
            assertThat(c.derniereActiviteLe()).isNull();
            assertThat(c.invitationEnCours()).isFalse();
        });
    }
}
