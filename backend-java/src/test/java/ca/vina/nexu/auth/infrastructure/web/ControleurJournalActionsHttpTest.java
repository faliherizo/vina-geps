package ca.vina.nexu.auth.infrastructure.web;

import ca.vina.nexu.ClientHttpEssai;
import ca.vina.nexu.SchemaEssai;
import ca.vina.nexu.acces.infrastructure.persistance.RegistrePolitiqueAcces;
import ca.vina.nexu.auth.infrastructure.persistance.DepotComptes;
import ca.vina.nexu.partage.journal.infrastructure.DepotJournalActions;
import ca.vina.nexu.partage.securite.ContexteRequete;
import ca.vina.nexu.partage.web.GardeAcces;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.jdbc.core.simple.JdbcClient;

import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Port de `journal-actions.integration.test.ts` : l'administrateur lit son organisme, le superadmin tout ; filtres ; refus. */
@DisplayName("API auth — journal des actions des utilisateurs")
class ControleurJournalActionsHttpTest {

    private static final Clock HORLOGE = Clock.fixed(Instant.parse("2026-09-19T12:00:00Z"), ZoneOffset.UTC);
    private ClientHttpEssai http;

    @BeforeEach
    void preparer(@TempDir Path dossier) {
        JdbcClient jdbc = SchemaEssai.base(dossier);
        jdbc.sql("INSERT INTO users (id, username, loginName, tenantId, passwordHash, role, employeeId, createdAt, active) VALUES "
                + "('u-admin','admin-t1@@t-1','admin-t1','t-1','x','admin',NULL,'x',1), ('u-lea','lea@@t-1','lea','t-1','x','employee','emp-1','x',1)").update();
        for (String[] l : new String[][] {{"a", "t-1", "u-lea", null, "PUT", "/api/notifications/preferences", "200"}, {"b", "t-1", "u-lea", "u-admin", "POST", "/api/scolaire/absences", "201"}, {"c", "t-2", "u-x", null, "DELETE", "/api/c", "204"}}) {
            jdbc.sql("INSERT INTO journal_actions (id, instant, tenantId, userId, acteurReel, methode, chemin, statut, dureeMs, requeteId) VALUES (?, '2026-09-19T10:00:0" + l[0].charAt(0) % 10 + "Z', ?, ?, ?, ?, ?, ?, 3, 'req-' || ?)")
                    .params(l[0], l[1], l[2], l[3], l[4], l[5], Integer.parseInt(l[6]), l[0]).update();
        }
        http = new ClientHttpEssai(new ControleurJournalActions(new DepotJournalActions(jdbc, HORLOGE), new DepotComptes(jdbc, HORLOGE), new GardeAcces(new RegistrePolitiqueAcces())));
    }

    @Test
    void perimetreFiltresEtRefus() throws Exception {
        ContexteRequete admin = new ContexteRequete("u-admin", "admin-t1", "admin", null, "t-1");
        http.get("/api/auth/journal-actions", null).andExpect(status().isUnauthorized());
        http.get("/api/auth/journal-actions", new ContexteRequete("u-lea", "lea", "employee", "emp-1", "t-1")).andExpect(status().isForbidden());
        http.get("/api/auth/journal-actions", admin).andExpect(status().isOk()).andExpect(jsonPath("$.total").value(2))
                .andExpect(jsonPath("$.items[0].utilisateur").value("lea")).andExpect(jsonPath("$.items[0].acteurReelNom").value("admin-t1"))
                .andExpect(jsonPath("$.items[1].acteurReelNom").isEmpty()).andExpect(jsonPath("$.items[0].requeteId").value("req-b"));
        http.get("/api/auth/journal-actions?organisme=t-2", admin).andExpect(status().isOk()).andExpect(jsonPath("$.total").value(2));
        http.get("/api/auth/journal-actions?q=preferences&utilisateur=u-lea", admin).andExpect(jsonPath("$.total").value(1));
        http.get("/api/auth/journal-actions?depuis=2099-01-01", admin).andExpect(jsonPath("$.total").value(0));
        http.get("/api/auth/journal-actions?depuis=hier", admin).andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors[0]").value("depuis : date AAAA-MM-JJ"));
        ContexteRequete global = new ContexteRequete("u-root", "root", "superadmin", null, null);
        http.get("/api/auth/journal-actions", global).andExpect(jsonPath("$.total").value(3)).andExpect(jsonPath("$.items", hasSize(3)));
        http.get("/api/auth/journal-actions?organisme=t-2&pageSize=1", global).andExpect(jsonPath("$.total").value(1)).andExpect(jsonPath("$.items[0].utilisateur").value("u-x"));
    }
}
