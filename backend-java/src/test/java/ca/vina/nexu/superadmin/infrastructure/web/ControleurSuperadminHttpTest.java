package ca.vina.nexu.superadmin.infrastructure.web;

import ca.vina.nexu.superadmin.infrastructure.persistance.AdaptateurEtablissements;
import ca.vina.nexu.superadmin.infrastructure.configuration.ConfigurationSuperadmin;
import ca.vina.nexu.superadmin.infrastructure.persistance.DepotAdminsEtablissement;
import ca.vina.nexu.superadmin.infrastructure.persistance.TransactionsSpring;

import ca.vina.nexu.ClientHttpEssai;
import ca.vina.nexu.SchemaEssai;
import ca.vina.nexu.acces.infrastructure.persistance.RegistrePolitiqueAcces;
import ca.vina.nexu.auth.infrastructure.persistance.DepotComptes;
import ca.vina.nexu.partage.audit.infrastructure.JournalAudit;
import ca.vina.nexu.partage.courrier.infrastructure.CourrierJournal;
import ca.vina.nexu.partage.securite.ContexteRequete;
import ca.vina.nexu.partage.web.GardeAcces;
import ca.vina.nexu.pays.infrastructure.persistance.RegistrePays;
import ca.vina.nexu.saas.infrastructure.persistance.DepotLocataires;
import ca.vina.nexu.saas.infrastructure.persistance.DepotQuotas;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.support.TransactionTemplate;

import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Port de `superadmin.test.ts` et `tenant-create-atomic` : établissements et admins et réinitialisation. */
@DisplayName("API superadmin — établissements, administrateurs")
class ControleurSuperadminHttpTest {

    private static final Clock HORLOGE = Clock.fixed(Instant.parse("2026-09-13T12:00:00Z"), ZoneOffset.UTC);
    private static final ContexteRequete SA = ClientHttpEssai.superadmin();
    private static final ContexteRequete ADMIN = ClientHttpEssai.session("admin", "t-1");

    private JdbcClient jdbc;
    private ClientHttpEssai http;
    private DepotComptes comptes;
    private CourrierJournal courrier;
    private final AtomicInteger tirages = new AtomicInteger();

    @BeforeEach
    void preparer(@TempDir Path dossier) {
        var source = SchemaEssai.source(dossier);
        jdbc = SchemaEssai.base(source);
        comptes = new DepotComptes(jdbc, HORLOGE);
        courrier = new CourrierJournal();
        DepotLocataires locataires = new DepotLocataires(jdbc, HORLOGE, new RegistrePays(), "ca-central-1");
        DepotAdminsEtablissement admins = new DepotAdminsEtablissement(jdbc, HORLOGE, comptes, locataires, new JournalAudit(jdbc), courrier,
                () -> "Provisoire-" + tirages.incrementAndGet() + "!", "https://app.exemple.qc.ca/");
        ConfigurationSuperadmin config = new ConfigurationSuperadmin();
        AdaptateurEtablissements etablissements = new AdaptateurEtablissements(locataires, new DepotQuotas(jdbc));
        TransactionsSpring transactions = new TransactionsSpring(new TransactionTemplate(new DataSourceTransactionManager(source)));
        http = new ClientHttpEssai(new ControleurSuperadmin(config.lectureAdministration(etablissements, admins),
                config.gestionnaireAdministration(etablissements, admins, transactions), new GardeAcces(new RegistrePolitiqueAcces())));
    }

    private static String champ(MvcResult r, String nom) throws Exception {
        return r.getResponse().getContentAsString().replaceAll("^.*?\"" + nom + "\":\"([^\"]+)\".*$", "$1");
    }

    @Test
    void unEtablissementNaitAvecSonAdministrateurDansUneSeuleTransaction() throws Exception {
        http.get("/api/superadmin/tenants", ADMIN).andExpect(status().isForbidden()).andExpect(jsonPath("$.errors[0]").value("insufficient role"));
        http.get("/api/superadmin/tenants", null).andExpect(status().isUnauthorized());
        http.get("/api/superadmin/tenants", SA).andExpect(jsonPath("$", hasSize(0)));

        // Un mot de passe permanent trop faible refuse l'admin : l'établissement n'est pas créé non plus.
        http.post("/api/superadmin/tenants", SA, "{\"name\":\"CSS des Chênes\",\"type\":\"public_institution\",\"country\":\"CA\",\"planCode\":\"enterprise\",\"adminPassword\":\"faible\"}")
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors[0]", org.hamcrest.Matchers.startsWith("mot de passe trop faible")));
        assertThat(jdbc.sql("SELECT COUNT(*) FROM tenants").query(Integer.class).single()).isZero();
        http.post("/api/superadmin/tenants", SA, "{\"name\":\" \",\"type\":\"x\",\"country\":\"CA\",\"planCode\":\"starter\"}").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors", hasSize(2)));

        MvcResult cree = http.post("/api/superadmin/tenants", SA, "{\"name\":\"CSS des Chênes\",\"type\":\"public_institution\",\"country\":\"CA\",\"planCode\":\"enterprise\",\"slug\":\"chenes\"}")
                .andExpect(status().isCreated()).andExpect(jsonPath("$.tenant.status").value("trial")).andExpect(jsonPath("$.admin.username").value("admin"))
                .andExpect(jsonPath("$.temporaryPassword").value("Provisoire-2!")).andReturn();   // le tirage précède la validation, comme au Node
        String tenantId = champ(cree, "id");
        assertThat(comptes.parNom("admin", tenantId).orElseThrow().mustChangePassword()).isTrue();
        http.get("/api/superadmin/tenants", SA).andExpect(jsonPath("$", hasSize(1))).andExpect(jsonPath("$[0].admins[0].username").value("admin"))
                .andExpect(jsonPath("$[0].admins[0].active").value(true)).andExpect(jsonPath("$[0].modules", org.hamcrest.Matchers.hasItem("effectifs_temps")));
        // Un mot de passe permanent fourni : pas de provisoire, changement non imposé.
        http.post("/api/superadmin/tenants", SA, "{\"name\":\"Autre\",\"type\":\"corporate\",\"country\":\"CA\",\"planCode\":\"starter\",\"adminUsername\":\" direction \",\"adminPassword\":\"Tres-Solide-2026!\"}")
                .andExpect(status().isCreated()).andExpect(jsonPath("$.temporaryPassword").doesNotExist()).andExpect(jsonPath("$.admin.username").value("direction"));
        http.post("/api/superadmin/tenants", SA, "{\"name\":\"Encore\",\"type\":\"corporate\",\"country\":\"CA\",\"planCode\":\"starter\",\"slug\":\"chenes\"}").andExpect(status().isConflict());

        // Modification des coordonnées ; le nom vide et le courriel invalide sont nommés ; le slug pris est un conflit.
        http.patch("/api/superadmin/tenants/" + tenantId, SA, "{\"name\":\" \"}").andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors[0]").value("le nom de l'établissement ne peut pas être vide"));
        http.patch("/api/superadmin/tenants/" + tenantId, SA, "{\"email\":\"pas-un-courriel\"}").andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors[0]").value("courriel invalide « pas-un-courriel »"));
        http.patch("/api/superadmin/tenants/" + tenantId, SA, "{\"name\":\" CSS des Chênes-Nord \",\"city\":\"Québec\",\"phone\":\"418 555-0100\",\"email\":\"a@b.qc\",\"region\":\"QC\",\"address\":\"1 rue X\",\"postalCode\":\"G1A\",\"slug\":\"Chênes Nord\"}")
                .andExpect(jsonPath("$.name").value("CSS des Chênes-Nord")).andExpect(jsonPath("$.slug").value("ch-nes-nord")).andExpect(jsonPath("$.city").value("Québec"));
        http.patch("/api/superadmin/tenants/" + tenantId, SA, "{}").andExpect(jsonPath("$.name").value("CSS des Chênes-Nord"));
        http.patch("/api/superadmin/tenants/" + tenantId, SA, "{\"slug\":\"ch-nes-nord\"}").andExpect(status().isOk());
        String autre = jdbc.sql("SELECT id FROM tenants WHERE name = 'Autre'").query(String.class).single();
        http.patch("/api/superadmin/tenants/" + autre, SA, "{\"slug\":\"ch-nes-nord\"}").andExpect(status().isConflict());
        http.patch("/api/superadmin/tenants/absent", SA, "{\"name\":\"x\"}").andExpect(status().isNotFound());

        http.post("/api/superadmin/tenants/" + tenantId + "/activate", SA, null).andExpect(jsonPath("$.status").value("active")).andExpect(jsonPath("$.subscriptionEndsAt").value("2027-09-08T12:00:00.000Z"));
        http.post("/api/superadmin/tenants/" + tenantId + "/suspend", SA, null).andExpect(jsonPath("$.status").value("suspended"));
        http.post("/api/superadmin/tenants/" + tenantId + "/terminate", SA, null).andExpect(jsonPath("$.status").value("terminated"));
        http.post("/api/superadmin/tenants/absent/activate", SA, null).andExpect(status().isNotFound()).andExpect(jsonPath("$.errors[0]").value("tenant not found"));
    }

    @Test
    void lesAdministrateursSeCreentSeReinitialisentEtSeDesactiventSaufLeDernier() throws Exception {
        String tenantId = champ(http.post("/api/superadmin/tenants", SA, "{\"name\":\"CSS\",\"type\":\"corporate\",\"country\":\"CA\",\"planCode\":\"starter\"}").andReturn(), "id");
        http.post("/api/superadmin/tenants/absent/admins", SA, "{\"username\":\"x\",\"password\":\"Passe-Tres-Solide-1!\"}").andExpect(status().isNotFound());
        http.post("/api/superadmin/tenants/" + tenantId + "/admins", SA, "{\"password\":\"Passe-Tres-Solide-1!\"}").andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors[0]").value("username required"));
        http.post("/api/superadmin/tenants/" + tenantId + "/admins", SA, "{\"username\":\"second\",\"password\":\"court\",\"mustChangePassword\":true}").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0]").value("temporary password must be at least 8 characters"));
        http.post("/api/superadmin/tenants/" + tenantId + "/admins", SA, "{\"username\":\"admin\",\"password\":\"Passe-Tres-Solide-1!\"}").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0]").value("username already exists for this tenant"));
        MvcResult second = http.post("/api/superadmin/tenants/" + tenantId + "/admins", SA, "{\"username\":\"second\",\"password\":\"provisoire8\",\"mustChangePassword\":true}")
                .andExpect(status().isCreated()).andExpect(jsonPath("$.username").value("second")).andReturn();
        String secondId = champ(second, "userId");
        http.get("/api/superadmin/tenants/" + tenantId + "/admins", SA).andExpect(jsonPath("$[*].username", contains("admin", "second"))).andExpect(jsonPath("$[1].active").value(true));

        // Réinitialisation : mot de passe provisoire, changement imposé, courriel s'il y a une adresse.
        http.post("/api/superadmin/tenants/" + tenantId + "/admins/absent/reset-password", SA, null).andExpect(status().isNotFound()).andExpect(jsonPath("$.errors[0]").value("admin not found"));
        http.post("/api/superadmin/tenants/" + tenantId + "/admins/" + secondId + "/reset-password", SA, null).andExpect(jsonPath("$.temporaryPassword").value("Provisoire-2!"))
                .andExpect(jsonPath("$.emailSent").value(false)).andExpect(jsonPath("$.username").value("second"));
        assertThat(comptes.verifierMotDePasse(comptes.parId(secondId).orElseThrow(), "Provisoire-2!")).isTrue();
        assertThat(comptes.parId(secondId).orElseThrow().mustChangePassword()).isTrue();
        comptes.modifier(secondId, new DepotComptes.Modification(null, null, null, null, null, null, java.util.Optional.of("second@css.qc.ca")));
        http.post("/api/superadmin/tenants/" + tenantId + "/admins/" + secondId + "/reset-password", SA, null).andExpect(jsonPath("$.emailSent").value(true));
        assertThat(courrier.envoyes()).hasSize(1);
        assertThat(courrier.envoyes().get(0).to()).isEqualTo("second@css.qc.ca");
        assertThat(courrier.envoyes().get(0).subject()).isEqualTo("Réinitialisation de votre mot de passe — Nexu VINA");
        assertThat(courrier.envoyes().get(0).text()).contains("  établissement : CSS").contains("  mot de passe temporaire : Provisoire-3!").contains("  connexion : https://app.exemple.qc.ca/login");
        assertThat(jdbc.sql("SELECT COUNT(*) FROM audit_log WHERE action = 'superadmin.admin.reset-password'").query(Integer.class).single()).isEqualTo(2);

        // Désactiver : jamais le dernier actif.
        http.post("/api/superadmin/tenants/" + tenantId + "/admins/" + secondId + "/deactivate", SA, null).andExpect(jsonPath("$.active").value(false));
        assertThat(comptes.parId(secondId).orElseThrow().active()).isFalse();
        String premier = comptes.parNom("admin", tenantId).orElseThrow().id();
        http.post("/api/superadmin/tenants/" + tenantId + "/admins/" + premier + "/deactivate", SA, null).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0]").value("dernier administrateur actif : l'établissement deviendrait inadministrable"));
        http.post("/api/superadmin/tenants/" + tenantId + "/admins/" + secondId + "/activate", SA, null).andExpect(jsonPath("$.active").value(true));
        http.post("/api/superadmin/tenants/" + tenantId + "/admins/absent/activate", SA, null).andExpect(status().isNotFound());
        http.get("/api/superadmin/tenants/" + tenantId + "/admins", SA).andExpect(jsonPath("$[1].active").value(true));
    }

}
