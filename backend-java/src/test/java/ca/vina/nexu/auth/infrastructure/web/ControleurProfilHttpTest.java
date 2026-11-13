package ca.vina.nexu.auth.infrastructure.web;

import ca.vina.nexu.auth.infrastructure.persistance.DepotComptes;
import ca.vina.nexu.auth.infrastructure.persistance.DepotProfil;

import ca.vina.nexu.ClientHttpEssai;
import ca.vina.nexu.SchemaEssai;
import ca.vina.nexu.auth.domaine.Totp;
import ca.vina.nexu.partage.audit.infrastructure.JournalAudit;
import ca.vina.nexu.partage.securite.ContexteRequete;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Port de la partie profil, approbateurs et amorce TOTP de {@code auth.test.ts}, mêmes codes et messages. */
@DisplayName("/api/auth — profil de la personne connectée, approbateurs, amorce TOTP")
class ControleurProfilHttpTest {

    private static final Clock HORLOGE = Clock.fixed(Instant.parse("2026-09-14T12:00:00Z"), ZoneOffset.UTC);
    private JdbcClient jdbc;
    private DepotComptes comptes;
    private ClientHttpEssai http;
    private ContexteRequete ana;
    private ContexteRequete sansFiche;
    private ContexteRequete fantome;

    @BeforeEach
    void preparer(@TempDir Path dossier) {
        jdbc = SchemaEssai.base(dossier);
        comptes = new DepotComptes(jdbc, HORLOGE);
        jdbc.sql("INSERT INTO departments (id, name, parentId, tenantId) VALUES ('d1', 'Finance', NULL, 'T1')").update();
        jdbc.sql("INSERT INTO employees (id, firstName, lastName, tenantId, sin, email, phone, address, dateOfBirth, hireDate, status, annualSalary, role, jobTitle, departmentId, managerId, weeklyCapacityHours, payType, workerType, isTeacher) VALUES "
                + "('m1', 'Marc', 'Ng', 'T1', NULL, 'marc@x.ca', NULL, NULL, NULL, '2020-01-01', 'active', 90000, 'manager', 'Directeur', 'd1', NULL, 35, 'salary', 'employee', 0), "
                + "('e1', 'Ana', 'Roy', 'T1', '123456789', 'ana@x.ca', '514', '1 rue', '1990-01-01', '2024-01-01', 'active', 50000, 'employee', 'Analyste', 'd1', 'm1', 35, 'salary', 'employee', 1), "
                + "('e2', 'Zoé', 'Lu', 'T1', NULL, 'zoe@x.ca', NULL, NULL, NULL, '2024-01-01', 'active', NULL, 'employee', NULL, NULL, 'e1', NULL, 'hourly', 'employee', 0)").update();
        var cAna = comptes.creer(new DepotComptes.Creation("ana", "mot-de-passe", "employee", "e1", "T1", false, false, "ana@x.ca", false));
        var cMarc = comptes.creer(new DepotComptes.Creation("marc", "mot-de-passe", "employee", "m1", "T1", false, false, null, false));
        comptes.creer(new DepotComptes.Creation("admin", "mot-de-passe", "admin", null, "T1", false, false, null, false));
        comptes.creer(new DepotComptes.Creation("ailleurs", "mot-de-passe", "admin", null, "T2", false, false, null, false));
        var cSans = comptes.creer(new DepotComptes.Creation("sans", "mot-de-passe", "manager", null, "T1", false, false, null, false));
        ana = new ContexteRequete(cAna.id(), "ana", "employee", "e1", "T1");
        sansFiche = new ContexteRequete(cSans.id(), "sans", "manager", null, "T1");
        fantome = new ContexteRequete("usr-absent", "fantome", "employee", null, "T1");
        assertThat(cMarc.employeeId()).isEqualTo("m1");
        http = new ClientHttpEssai(new ControleurProfil(comptes, new DepotProfil(jdbc), new JournalAudit(jdbc), new Random(9), ""));
    }

    @Test
    @DisplayName("GET /profile : le compte, ce qui se modifie, ce qui se consulte (NAS masqué), et l'encadrement réel")
    void profil() throws Exception {
        http.get("/api/auth/profile", null).andExpect(status().isUnauthorized());
        http.get("/api/auth/profile", fantome).andExpect(status().isNotFound()).andExpect(jsonPath("$.errors[0]").value("user not found"));
        http.get("/api/auth/profile", ana).andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("ana")).andExpect(jsonPath("$.role").value("employee")).andExpect(jsonPath("$.email").value("ana@x.ca"))
                .andExpect(jsonPath("$.profile.employeeId").value("e1")).andExpect(jsonPath("$.profile.firstName").value("Ana")).andExpect(jsonPath("$.profile.address").value("1 rue")).andExpect(jsonPath("$.profile.jobTitle").value("Analyste"))
                .andExpect(jsonPath("$.readOnly.function").value("employee")).andExpect(jsonPath("$.readOnly.department").value("Finance")).andExpect(jsonPath("$.readOnly.managerName").value("Marc Ng"))
                .andExpect(jsonPath("$.readOnly.sinMasked").value("***-***-789")).andExpect(jsonPath("$.readOnly.isTeacher").value(true)).andExpect(jsonPath("$.readOnly.annualSalary").value(50000.0))
                .andExpect(jsonPath("$.readOnly.hourlyRate").isEmpty()).andExpect(jsonPath("$.readOnly.weeklyCapacityHours").value(35.0))
                .andExpect(jsonPath("$.manages").value(1));
        http.get("/api/auth/profile", sansFiche).andExpect(status().isOk()).andExpect(jsonPath("$.profile").isEmpty()).andExpect(jsonPath("$.readOnly").isEmpty()).andExpect(jsonPath("$.manages").value(0));
    }

    @Test
    @DisplayName("PATCH /profile : champs admis seulement, validation, enregistrement et journal")
    void modifierProfil() throws Exception {
        http.envoyer(MockMvcRequestBuilders.patch("/api/auth/profile").contentType("application/json").content("{}"), null).andExpect(status().isUnauthorized());
        http.envoyer(MockMvcRequestBuilders.patch("/api/auth/profile").contentType("application/json").content("{}"), fantome).andExpect(status().isNotFound());
        http.envoyer(MockMvcRequestBuilders.patch("/api/auth/profile").contentType("application/json").content("{}"), sansFiche).andExpect(status().isConflict()).andExpect(jsonPath("$.errors[0]").value("no employee profile linked to this account"));
        http.envoyer(MockMvcRequestBuilders.patch("/api/auth/profile").contentType("application/json").content("{\"sin\":\"x\",\"firstName\":\" \",\"email\":\"nope\"}"), ana).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors", containsInAnyOrder("field 'sin' is not editable", "firstName required", "invalid email")));
        http.envoyer(MockMvcRequestBuilders.patch("/api/auth/profile").contentType("application/json").content("{\"firstName\":\" Anne \",\"phone\":\"438\",\"dateOfBirth\":\"\"}"), ana).andExpect(status().isOk())
                .andExpect(jsonPath("$.firstName").value("Anne")).andExpect(jsonPath("$.lastName").value("Roy")).andExpect(jsonPath("$.phone").value("438")).andExpect(jsonPath("$.dateOfBirth").value("")).andExpect(jsonPath("$.jobTitle").value("Analyste"));
        assertThat(jdbc.sql("SELECT firstName || '|' || phone || '|' || COALESCE(dateOfBirth, 'null') FROM employees WHERE id = 'e1'").query(String.class).single()).isEqualTo("Anne|438|null");
        assertThat(jdbc.sql("SELECT action FROM audit_log").query(String.class).list()).containsExactly("auth.profile.update");
        // Sans corps : rien ne change.
        http.envoyer(MockMvcRequestBuilders.patch("/api/auth/profile"), ana).andExpect(status().isOk()).andExpect(jsonPath("$.firstName").value("Anne"));
    }

    @Test
    @DisplayName("GET /approvers : l'annuaire des approbateurs de l'établissement, ou la ligne hiérarchique d'un employé")
    void approbateurs() throws Exception {
        http.get("/api/auth/approvers", null).andExpect(status().isUnauthorized());
        http.get("/api/auth/approvers", ana).andExpect(status().isOk()).andExpect(jsonPath("$[*].username").value(org.hamcrest.Matchers.contains("admin", "sans")));
        // La ligne hiérarchique de Zoé : Ana (compte « employee », mais gestionnaire selon la fiche) puis Marc.
        http.get("/api/auth/approvers?employeeId=e2", ana).andExpect(status().isOk()).andExpect(jsonPath("$[*].username").value(org.hamcrest.Matchers.contains("ana", "marc"))).andExpect(jsonPath("$[1].employeeId").value("m1"));
        http.get("/api/auth/approvers?employeeId=e1", ana).andExpect(status().isOk()).andExpect(jsonPath("$[0].username").value("marc"));
        http.get("/api/auth/approvers?employeeId=m1", ana).andExpect(status().isOk()).andExpect(jsonPath("$[*].username").value(org.hamcrest.Matchers.contains("admin")));
        // Le superadmin voit tous les établissements ; un compte sans établissement, ceux de l'en-tête.
        ContexteRequete superadmin = new ContexteRequete("sa", "sa", "superadmin", null, null);
        http.get("/api/auth/approvers", superadmin).andExpect(status().isOk()).andExpect(jsonPath("$[*].username").value(org.hamcrest.Matchers.contains("admin", "ailleurs", "sans")));
        http.envoyer(MockMvcRequestBuilders.get("/api/auth/approvers").header("X-Tenant-Id", "T2"), new ContexteRequete("x", "x", "admin", null, null)).andExpect(status().isOk()).andExpect(jsonPath("$[0].username").value("ailleurs"));
    }

    @Test
    @DisplayName("POST /security/totp/init : un secret en attente, l'URI otpauth et son QR — la méthode n'est pas encore active")
    void totp() throws Exception {
        http.post("/api/auth/security/totp/init", null, null).andExpect(status().isUnauthorized());
        http.post("/api/auth/security/totp/init", fantome, null).andExpect(status().isNotFound());
        MvcResult r = http.post("/api/auth/security/totp/init", ana, null).andExpect(status().isOk())
                .andExpect(jsonPath("$.secret").value(startsWith(""))).andExpect(jsonPath("$.otpauthUrl").value(startsWith("otpauth://totp/NexuVINA:ana%40%40T1?secret=")))
                .andExpect(jsonPath("$.qrSvg").value(startsWith("<svg xmlns=\"http://www.w3.org/2000/svg\""))).andReturn();
        String secret = r.getResponse().getContentAsString().replaceAll("^.*?\"secret\":\"([^\"]+)\".*$", "$1");
        assertThat(secret).hasSize(32).matches("[A-Z2-7]+");
        assertThat(r.getResponse().getContentAsString()).contains("\"otpauthUrl\":\"otpauth://totp/NexuVINA:ana%40%40T1?secret=" + secret + "&issuer=NexuVINA&algorithm=SHA1&digits=6&period=30\"");
        var compte = comptes.parId(ana.userId()).orElseThrow();
        assertThat(compte.pendingTotpSecret()).isEqualTo(secret);
        assertThat(compte.totpSecret()).isNull();
        assertThat(compte.securityMethod()).isNull();
        assertThat(Totp.code(secret, HORLOGE.millis())).hasSize(6);
        // Un émetteur configuré.
        ClientHttpEssai autre = new ClientHttpEssai(new ControleurProfil(comptes, new DepotProfil(jdbc), new JournalAudit(jdbc), new Random(9), "CSSDC"));
        autre.post("/api/auth/security/totp/init", ana, null).andExpect(jsonPath("$.otpauthUrl").value(startsWith("otpauth://totp/CSSDC:ana%40%40T1?secret=")));
    }
}
