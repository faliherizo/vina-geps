package ca.vina.nexu.auth.infrastructure.persistance;

import ca.vina.nexu.SchemaEssai;
import ca.vina.nexu.partage.audit.infrastructure.JournalAudit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.mock.env.MockEnvironment;

import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

/** PT-03 : un compte amorcé avec le mot de passe de son rôle doit le changer à la première connexion, hors essai. */
@DisplayName("Comptes amorcés — mot de passe par défaut à changer")
class ProvisionnementComptesTest {

    private static final Clock HORLOGE = Clock.fixed(Instant.parse("2026-11-22T12:00:00Z"), ZoneOffset.UTC);

    private static JdbcClient amorcer(Path dossier, MockEnvironment env) {
        JdbcClient jdbc = SchemaEssai.base(dossier);
        new ProvisionnementComptes(new DepotComptes(jdbc, HORLOGE), new JournalAudit(jdbc), env).assurer();
        return jdbc;
    }

    private static int aChanger(JdbcClient jdbc, String nom) {
        return jdbc.sql("SELECT mustChangePassword FROM users WHERE username = ?").params(nom).query(Integer.class).single();
    }

    @Test
    @DisplayName("sans secrets, hors essai : admin et superadmin doivent changer leur mot de passe")
    void parDefautHorsEssai(@TempDir Path dossier) {
        JdbcClient jdbc = amorcer(dossier, new MockEnvironment());
        assertThat(aChanger(jdbc, "admin")).isEqualTo(1);
        assertThat(aChanger(jdbc, "superadmin")).isEqualTo(1);
    }

    @Test
    @DisplayName("un secret égal au rôle ou vide compte comme défaut ; en essai, rien à changer")
    void essaiEtSecretsTriviaux(@TempDir Path dossier) {
        JdbcClient jdbc = amorcer(dossier, new MockEnvironment().withProperty("nexu.essai", "true").withProperty("ADMIN_PASSWORD", " "));
        assertThat(aChanger(jdbc, "admin")).isZero();
        assertThat(aChanger(jdbc, "superadmin")).isZero();
    }

    @Test
    @DisplayName("des secrets fournis : aucun changement imposé")
    void secretsFournis(@TempDir Path dossier) {
        JdbcClient jdbc = amorcer(dossier, new MockEnvironment().withProperty("ADMIN_PASSWORD", "Un-Secret-Solide-2026")
                .withProperty("SUPERADMIN_PASSWORD", "superadmin").withProperty("SUPERADMIN_USERNAME", "racine"));
        assertThat(aChanger(jdbc, "admin")).isZero();
        assertThat(aChanger(jdbc, "racine")).isEqualTo(1);
    }
}
