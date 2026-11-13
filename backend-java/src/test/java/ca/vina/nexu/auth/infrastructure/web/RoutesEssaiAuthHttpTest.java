package ca.vina.nexu.auth.infrastructure.web;

import ca.vina.nexu.ClientHttpEssai;
import ca.vina.nexu.SchemaEssai;
import ca.vina.nexu.auth.infrastructure.persistance.DepotComptes;
import ca.vina.nexu.auth.infrastructure.persistance.ProvisionnementComptes;
import ca.vina.nexu.auth.infrastructure.persistance.RemiseAZeroComptes;
import ca.vina.nexu.partage.audit.infrastructure.JournalAudit;
import ca.vina.nexu.partage.courrier.infrastructure.CourrierJournal;
import org.junit.jupiter.api.BeforeEach;
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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Les deux routes que la campagne e2e emploie, et qui n'existent qu'en environnement d'essai. */
@DisplayName("Routes d'essai de l'authentification — remise à zéro et boîte d'envoi, fermées hors essai")
class RoutesEssaiAuthHttpTest {

    private static final Clock HORLOGE = Clock.fixed(Instant.parse("2026-11-13T10:00:00Z"), ZoneOffset.UTC);

    private JdbcClient jdbc;
    private ProvisionnementComptes provisionnement;

    @BeforeEach
    void preparer(@TempDir Path dossier) {
        jdbc = SchemaEssai.base(dossier);
        provisionnement = new ProvisionnementComptes(new DepotComptes(jdbc, HORLOGE), new JournalAudit(jdbc), new MockEnvironment());
        provisionnement.assurer();
    }

    private ClientHttpEssai http(boolean essai) {
        return new ClientHttpEssai(new ControleurRemiseAZeroComptes(new RemiseAZeroComptes(jdbc), provisionnement, essai),
                new ControleurBoiteDEnvoi(new CourrierJournal(HORLOGE), essai));
    }

    private int comptes() {
        return jdbc.sql("SELECT COUNT(*) FROM users").query(Integer.class).single();
    }

    @Test
    @DisplayName("en essai : la remise à zéro garde admin, recrée le superadmin et vide le journal ; la boîte d'envoi se lit")
    void enEssai() throws Exception {
        jdbc.sql("INSERT INTO users (id, username, tenantId, passwordHash, role, createdAt) VALUES ('u-lea', 'lea', 't-1', 'x', 'employee', 'x')").update();
        jdbc.sql("DELETE FROM users WHERE username = 'superadmin'").update();
        jdbc.sql("INSERT INTO audit_log (id, at, action) VALUES ('a-ancien', 'x', 'login')").update();
        assertThat(comptes()).isEqualTo(2);
        http(true).post("/api/auth/_reset", null, "{}").andExpect(status().isNoContent());
        assertThat(jdbc.sql("SELECT username FROM users ORDER BY username").query(String.class).list()).containsExactly("admin", "superadmin");
        // Le journal est vidé ; ne reste que la trace de la recréation du superadmin.
        assertThat(jdbc.sql("SELECT COUNT(*) FROM audit_log WHERE id = 'a-ancien'").query(Integer.class).single()).isZero();
        http(true).get("/api/auth/_outbox", null).andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    @DisplayName("hors essai : la remise à zéro est refusée nommément et ne touche à rien ; la boîte d'envoi est introuvable")
    void horsEssai() throws Exception {
        int avant = comptes();
        http(false).post("/api/auth/_reset", null, "{}").andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errors[0]").value(ControleurRemiseAZeroComptes.REFUS));
        assertThat(comptes()).isEqualTo(avant);
        http(false).get("/api/auth/_outbox", null).andExpect(status().isNotFound());
    }
}
