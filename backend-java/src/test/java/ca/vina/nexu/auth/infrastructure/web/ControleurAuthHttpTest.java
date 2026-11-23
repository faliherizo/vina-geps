package ca.vina.nexu.auth.infrastructure.web;

import ca.vina.nexu.auth.infrastructure.persistance.DepotAudit;
import ca.vina.nexu.auth.infrastructure.persistance.DepotCatalogueQuestions;
import ca.vina.nexu.auth.infrastructure.persistance.DepotComptes;
import ca.vina.nexu.auth.infrastructure.persistance.DepotEmprunts;
import ca.vina.nexu.auth.infrastructure.persistance.DepotEtablissementsConnexion;
import ca.vina.nexu.auth.infrastructure.persistance.DepotQuestionsSecretes;
import ca.vina.nexu.auth.infrastructure.persistance.DepotRolesEtablissement;
import ca.vina.nexu.auth.infrastructure.services.ServiceConnexion;

import ca.vina.nexu.ClientHttpEssai;
import ca.vina.nexu.SchemaEssai;
import ca.vina.nexu.acces.infrastructure.persistance.RegistrePolitiqueAcces;
import ca.vina.nexu.auth.domaine.Compte;
import ca.vina.nexu.auth.domaine.DroitDApprouver;
import ca.vina.nexu.auth.domaine.RestrictionPaysConnexion;
import ca.vina.nexu.auth.domaine.Totp;
import ca.vina.nexu.partage.audit.infrastructure.JournalAudit;
import ca.vina.nexu.partage.securite.ContexteRequete;
import ca.vina.nexu.partage.securite.HoteEtablissement;
import ca.vina.nexu.partage.securite.JetonDeSession;
import ca.vina.nexu.partage.securite.FiltreSession;
import ca.vina.nexu.partage.securite.SessionsEmpruntees;
import ca.vina.nexu.partage.securite.SessionsRevoquees;
import ca.vina.nexu.auth.infrastructure.persistance.DepotSessionsRevoquees;
import ca.vina.nexu.partage.web.GardeAcces;
import ca.vina.nexu.saas.infrastructure.persistance.DepotQuotas;
import ca.vina.nexu.securite.domaine.PareFeu;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.MvcResult;
import ca.vina.nexu.auth.infrastructure.persistance.ResolveurTenantHote;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import jakarta.servlet.http.Cookie;

import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Port de `auth.test.ts` (connexion, comptes, cookies, second facteur) route par route, mêmes codes et messages. */
@DisplayName("API auth — sessions, comptes, politique, second facteur")
class ControleurAuthHttpTest {

    private static final String SECRET = "secret-d-essai-de-trente-deux-octets-au-moins";
    private static final Clock HORLOGE = Clock.fixed(Instant.parse("2026-09-13T12:00:00Z"), ZoneOffset.UTC);
    private static final Pattern JETON = Pattern.compile("\"token\":\"([^\"]+)\"");

    private JdbcClient jdbc;
    private DepotComptes comptes;
    private JetonDeSession jetons;
    private ClientHttpEssai http;
    private ControleurAuth controleur;

    private PareFeu pareFeu;
    private final List<String> journalWaf = new java.util.ArrayList<>();

    @BeforeEach
    void preparer(@TempDir Path dossier) {
        jdbc = SchemaEssai.base(dossier);
        comptes = new DepotComptes(jdbc, HORLOGE);
        jetons = new JetonDeSession(SECRET);
        DepotQuestionsSecretes questions = new DepotQuestionsSecretes(jdbc, HORLOGE);
        RegistrePolitiqueAcces acces = new RegistrePolitiqueAcces();
        pareFeu = new PareFeu(new PareFeu.Reglages(PareFeu.Mode.BLOQUER, 0, 3, 15), HORLOGE::millis, (a, d) -> journalWaf.add(a));
        controleur = new ControleurAuth(comptes, questions, new DepotRolesEtablissement(jdbc, acces, HORLOGE), new DepotCatalogueQuestions(jdbc, HORLOGE), new DepotAudit(jdbc, new JournalAudit(jdbc), HORLOGE), new DepotEtablissementsConnexion(jdbc),
                new DepotQuotas(jdbc), new JournalAudit(jdbc), new GardeAcces(acces), HORLOGE, pareFeu,
                new ServiceConnexion(comptes, questions, jetons, HORLOGE), false, HoteEtablissement.selecteur(), new DepotEmprunts(jdbc, HORLOGE, comptes, new JournalAudit(jdbc), jetons));
        http = new ClientHttpEssai(controleur);
        comptes.creer(new DepotComptes.Creation("admin", "admin", "admin", null, null, false, false, null, false));
        comptes.creer(new DepotComptes.Creation("superadmin", "superadmin", "superadmin", null, null, false, false, null, false));
        jdbc.sql("INSERT INTO tenants (id, name, slug, country, status, createdAt) VALUES "
                + "('T1', 'CSS des Chênes', 'chenes', 'CA', 'active', '2026-02-01T00:00:00.000Z'), "
                + "('T2', 'CSS Résiliée', 'resiliee', 'CA', 'terminated', '2026-03-01T00:00:00.000Z'), "
                + "('T3', 'CSS à l''essai', 'essai', 'FR', 'trial', '2026-04-01T00:00:00.000Z')").update();
    }

    private MvcResult login(String username, String password, String tenantId) throws Exception {
        var b = MockMvcRequestBuilders.post("/api/auth/login")
                .contentType("application/json").content("{\"username\":\"" + username + "\",\"password\":\"" + password + "\"}");
        if (tenantId != null) {
            b.header("X-Tenant-Id", tenantId);
        }
        return MockMvcBuilders.standaloneSetup(controleur).build().perform(b).andReturn();
    }

    /** La session que le filtre poserait à partir du jeton reçu. */
    private ContexteRequete session(MvcResult r) throws Exception {
        Matcher m = JETON.matcher(r.getResponse().getContentAsString());
        assertThat(m.find()).as("jeton dans " + r.getResponse().getContentAsString()).isTrue();
        JetonDeSession.Revendications c = jetons.lire(m.group(1));
        return new ContexteRequete(c.sub(), c.username(), c.role(), c.employeeId(), c.tenantId());
    }

    private ContexteRequete admin() throws Exception {
        return session(login("admin", "admin", null));
    }

    private int nombreAudit(String action) {
        return jdbc.sql("SELECT COUNT(*) FROM audit_log WHERE action = ?").params(action).query(Integer.class).single();
    }

    // ── Public ─────────────────────────────────────────────────────────────────────────────────

    @Test
    void leSelecteurNeProposeQueLesEtablissementsActifsOuALEssaiDuPlusRecent() throws Exception {
        http.get("/api/auth/tenants", null).andExpect(status().isOk())
                .andExpect(jsonPath("$[*].id", contains("T3", "T1")))
                .andExpect(jsonPath("$[0].country").value("FR"))
                .andExpect(jsonPath("$[0].status").doesNotExist());
        http.get("/api/auth/tenant-by-slug/CHENES", null).andExpect(status().isOk()).andExpect(jsonPath("$.id").value("T1"));
        http.get("/api/auth/tenant-by-slug/nulle-part", null).andExpect(status().isNotFound()).andExpect(jsonPath("$.errors[0]").value("tenant not found"));
        http.get("/api/auth/password-policy", null).andExpect(jsonPath("$.minLength").value(12));
        http.post("/api/auth/password-policy/check", null, "{\"password\":\"Alice-Tremblay-2026!\",\"username\":\"alice-tremblay\"}")
                .andExpect(jsonPath("$.issues", contains("ne pas contenir le nom d'utilisateur")));
        http.post("/api/auth/password-policy/check", null, null).andExpect(jsonPath("$.issues", hasSize(5)));
    }

    // ── Déploiement par sous-domaine (ADR 0033) ─────────────────────────────────────────────

    /** Le même contrôleur, branché sur un déploiement par sous-domaine dont la racine est nexus.example. */
    private ControleurAuth surSousDomaine() {
        ResolveurTenantHote hote = new ResolveurTenantHote(jdbc, "sous_domaine", "nexus.example", "", "");
        return new ControleurAuth(comptes, new DepotQuestionsSecretes(jdbc, HORLOGE), new DepotRolesEtablissement(jdbc, new RegistrePolitiqueAcces(), HORLOGE), new DepotCatalogueQuestions(jdbc, HORLOGE),
                new DepotAudit(jdbc, new JournalAudit(jdbc), HORLOGE), new DepotEtablissementsConnexion(jdbc), new DepotQuotas(jdbc), new JournalAudit(jdbc), new GardeAcces(new RegistrePolitiqueAcces()), HORLOGE, pareFeu,
                new ServiceConnexion(comptes, new DepotQuestionsSecretes(jdbc, HORLOGE), jetons, HORLOGE), false, hote, new DepotEmprunts(jdbc, HORLOGE, comptes, new JournalAudit(jdbc), jetons));
    }

    private static MvcResult sur(ControleurAuth c, MockHttpServletRequestBuilder b, String hote, ContexteRequete session) throws Exception {
        b.header("X-Forwarded-Host", hote);
        if (session != null) {
            b.requestAttr(ContexteRequete.ATTRIBUT, session);
        }
        return MockMvcBuilders.standaloneSetup(c).build().perform(b).andReturn();
    }

    private static MockHttpServletRequestBuilder connexion(String username, String password, String tenantId) {
        return MockMvcRequestBuilders.post("/api/auth/login").contentType("application/json")
                .content("{\"username\":\"" + username + "\",\"password\":\"" + password + "\"" + (tenantId == null ? "" : ",\"tenantId\":\"" + tenantId + "\"") + "}");
    }

    @Test
    void leDeploiementDitCeQueLHoteDesigne() throws Exception {
        http.get("/api/auth/deploiement", null).andExpect(status().isOk())
                .andExpect(jsonPath("$.resolution").value("selecteur")).andExpect(jsonPath("$.hote").value("selecteur")).andExpect(jsonPath("$.etablissement").doesNotExist());
        ControleurAuth c = surSousDomaine();
        MvcResult etab = sur(c, MockMvcRequestBuilders.get("/api/auth/deploiement"), "chenes.nexus.example", null);
        assertThat(etab.getResponse().getStatus()).isEqualTo(200);
        assertThat(etab.getResponse().getContentAsString()).contains("\"resolution\":\"sous_domaine\"").contains("\"domaineRacine\":\"nexus.example\"")
                .contains("\"hote\":\"etablissement\"").contains("\"id\":\"T1\"").contains("\"slug\":\"chenes\"");
        assertThat(sur(c, MockMvcRequestBuilders.get("/api/auth/deploiement"), "admin.nexus.example", null).getResponse().getContentAsString()).contains("\"hote\":\"global\"");
        MvcResult inconnu = sur(c, MockMvcRequestBuilders.get("/api/auth/deploiement"), "resiliee.nexus.example", null);
        assertThat(inconnu.getResponse().getStatus()).isEqualTo(404);
        assertThat(inconnu.getResponse().getContentAsString()).contains("\"hote\":\"inconnu\"").contains("organisme inconnu ou fermé");
        assertThat(sur(c, MockMvcRequestBuilders.get("/api/auth/deploiement"), "localhost", null).getResponse().getContentAsString()).contains("\"hote\":\"hors_domaine\"");
    }

    @Test
    void enSousDomaineLaListeDesEtablissementsNeSeDonnePlusAUnAnonyme() throws Exception {
        ControleurAuth c = surSousDomaine();
        assertThat(sur(c, MockMvcRequestBuilders.get("/api/auth/tenants"), "chenes.nexus.example", null).getResponse().getStatus()).isEqualTo(404);
        // Un compte rattaché ne voit que le sien ; un compte global les voit tous.
        assertThat(sur(c, MockMvcRequestBuilders.get("/api/auth/tenants"), "chenes.nexus.example", ClientHttpEssai.session("admin", "T1")).getResponse().getContentAsString())
                .contains("\"id\":\"T1\"").doesNotContain("\"id\":\"T3\"");
        assertThat(sur(c, MockMvcRequestBuilders.get("/api/auth/tenants"), "admin.nexus.example", ClientHttpEssai.superadmin()).getResponse().getContentAsString())
                .contains("\"id\":\"T1\"").contains("\"id\":\"T3\"");
    }

    @Test
    @DisplayName("le choix de la vitrine : organismes ouverts avec sous-domaine, nom et adresse de connexion seulement")
    void leChoixDeLaVitrineNeDonneQueLesOrganismesJoignables() throws Exception {
        http.get("/api/auth/organismes", null).andExpect(status().isNotFound());
        jdbc.sql("INSERT INTO tenants (id, name, slug, country, status, createdAt) VALUES "
                + "('T4', 'Agence sans adresse', NULL, 'CA', 'active', '2026-05-01T00:00:00.000Z'), "
                + "('T5', 'Agence suspendue', 'suspendue', 'CA', 'suspended', '2026-05-01T00:00:00.000Z')").update();
        MvcResult r = sur(surSousDomaine(), MockMvcRequestBuilders.get("/api/auth/organismes"), "nexus.example", null);
        assertThat(r.getResponse().getStatus()).isEqualTo(200);
        String corps = r.getResponse().getContentAsString();
        // Triés par nom : « CSS à l'essai » avant « CSS des Chênes ».
        assertThat(corps).contains("\"adresse\":\"https://chenes.nexus.example/admin/login\"").contains("\"nom\":\"CSS des Chênes\"")
                .contains("\"slug\":\"essai\"").doesNotContain("resiliee").doesNotContain("suspendue").doesNotContain("sans adresse")
                .doesNotContain("\"id\"").doesNotContain("status");
        assertThat(corps.indexOf("essai")).isLessThan(corps.indexOf("chenes"));
    }

    @Test
    void enSousDomaineLHoteEstLaSeuleAutoriteDeLaConnexion() throws Exception {
        comptes.creer(new DepotComptes.Creation("s.tremblay", "Mot-de-passe-1", "employee", null, "T1", false, false, null, false));
        comptes.creer(new DepotComptes.Creation("s.tremblay", "Mot-de-passe-1", "employee", null, "T3", false, false, null, false));
        ControleurAuth c = surSousDomaine();
        // Sur chenes., le compte de T1 entre sans rien choisir — même s'il existe aussi à T3.
        MvcResult ok = sur(c, connexion("s.tremblay", "Mot-de-passe-1", null), "chenes.nexus.example", null);
        assertThat(ok.getResponse().getStatus()).isEqualTo(200);
        assertThat(ok.getResponse().getContentAsString()).contains("\"tenantId\":\"T1\"").doesNotContain("tenantChoiceRequired");
        // Un tenantId qui contredit l'hôte est refusé ; un slug inconnu ne connaît personne.
        assertThat(sur(c, connexion("s.tremblay", "Mot-de-passe-1", "T3"), "chenes.nexus.example", null).getResponse().getStatus()).isEqualTo(403);
        assertThat(sur(c, connexion("s.tremblay", "Mot-de-passe-1", null), "csspi.nexus.example", null).getResponse().getStatus()).isEqualTo(404);
        assertThat(sur(c, connexion("admin", "admin", null), "localhost", null).getResponse().getStatus()).isEqualTo(404);
        // admin. n'ouvre qu'aux comptes globaux : le compte d'établissement y est refusé à identifiants exacts ; l'exploitant entre aussi sur l'hôte d'un établissement.
        assertThat(sur(c, connexion("s.tremblay", "Mot-de-passe-1", null), "admin.nexus.example", null).getResponse().getStatus()).isEqualTo(401);
        assertThat(sur(c, connexion("admin", "admin", null), "admin.nexus.example", null).getResponse().getStatus()).isEqualTo(200);
        assertThat(sur(c, connexion("admin", "admin", null), "chenes.nexus.example", null).getResponse().getStatus()).isEqualTo(200);
    }

    // ── Connexion ──────────────────────────────────────────────────────────────────────────────

    @Test
    void laConnexionPoseLesCookiesEtRendLeJeton() throws Exception {
        MvcResult r = login("admin", "admin", null);
        assertThat(r.getResponse().getStatus()).isEqualTo(200);
        List<String> cookies = r.getResponse().getHeaders("Set-Cookie");
        assertThat(cookies).anyMatch(c -> c.startsWith("session=") && c.contains("HttpOnly") && c.contains("SameSite=Lax"));
        assertThat(cookies).anyMatch(c -> c.startsWith("csrf=") && !c.contains("HttpOnly"));
        assertThat(r.getResponse().getContentAsString()).contains("\"csrf\":\"").contains("\"user\":{\"id\":\"usr-").contains("\"role\":\"admin\"")
                .contains("\"capabilities\":[").contains("\"peutApprouver\":false");
        assertThat(session(r).role()).isEqualTo("admin");
        assertThat(nombreAudit("auth.login.ok")).isEqualTo(1);
    }

    @Test
    void lesRefusDeConnexionOntLeursCodes() throws Exception {
        assertThat(login("admin", "wrong", null).getResponse().getStatus()).isEqualTo(401);
        assertThat(login("admin", "wrong", null).getResponse().getContentAsString()).isEqualTo("{\"errors\":[\"invalid credentials\"]}");
        assertThat(nombreAudit("auth.login.fail")).isEqualTo(2);
        http.post("/api/auth/login", null, "{\"username\":\"admin\"}").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0]").value("username and password required"));
        http.post("/api/auth/login", null, null).andExpect(status().isBadRequest());
        // Un compte global se connecte malgré un en-tête résiduel ; un compte local ne traverse pas.
        MvcResult global = login("admin", "admin", "un-tenant-quelconque");
        assertThat(global.getResponse().getStatus()).isEqualTo(200);
        assertThat(global.getResponse().getContentAsString()).contains("\"tenantId\":null");
        http.post("/api/auth/users", ClientHttpEssai.superadmin(), "{\"username\":\"locale\",\"password\":\"p4ssword\",\"role\":\"admin\",\"tenantId\":\"T1\"}")
                .andExpect(status().isCreated());
        assertThat(login("locale", "p4ssword", "T1").getResponse().getStatus()).isEqualTo(200);
        assertThat(login("locale", "p4ssword", "T2").getResponse().getStatus()).isEqualTo(401);
    }

    @Test
    void unOrphelinEstNommeEtUnHomonymeDoitChoisir() throws Exception {
        jdbc.sql("INSERT INTO employees (id, firstName, lastName, tenantId) VALUES ('emp-1', 'P', 'Q', 'T1')").update();
        comptes.creer(new DepotComptes.Creation("prof", "Mot-de-passe-1", "employee", "emp-1", null, false, false, null, false));
        MvcResult orphelin = login("prof", "Mot-de-passe-1", null);
        assertThat(orphelin.getResponse().getStatus()).isEqualTo(403);
        assertThat(orphelin.getResponse().getContentAsString()).contains("\"orphanAccount\":true").contains("aucun établissement");
        assertThat(nombreAudit("auth.login.orphan")).isEqualTo(1);

        comptes.creer(new DepotComptes.Creation("s.tremblay", "Mot-de-passe-1", "employee", null, "T1", false, false, null, false));
        comptes.creer(new DepotComptes.Creation("s.tremblay", "Mot-de-passe-1", "employee", null, "T9", false, false, null, false));
        MvcResult choix = login("s.tremblay", "Mot-de-passe-1", null);
        assertThat(choix.getResponse().getContentAsString())
                .isEqualTo("{\"tenantChoiceRequired\":true,\"tenants\":[{\"id\":\"T1\",\"name\":\"CSS des Chênes\"},{\"id\":\"T9\",\"name\":\"T9\"}]}");
        assertThat(nombreAudit("auth.login.tenant_choice")).isEqualTo(1);
    }

    @Test
    void laDeconnexionEffaceLesCookiesEtJournaliseSiUneSessionExiste() throws Exception {
        MvcResult r = http.post("/api/auth/logout", admin(), null).andExpect(status().isOk()).andExpect(jsonPath("$.ok").value(true)).andReturn();
        assertThat(r.getResponse().getHeaders("Set-Cookie")).anyMatch(c -> c.startsWith("session=;") && c.contains("Max-Age=0"));
        assertThat(nombreAudit("auth.logout")).isEqualTo(1);
        http.post("/api/auth/logout", null, null).andExpect(status().isOk());
        assertThat(nombreAudit("auth.logout")).isEqualTo(1);
    }

    // ── Identité ───────────────────────────────────────────────────────────────────────────────

    @Test
    void meEtWhoamiPortentLesCapacitesEtLEtablissement() throws Exception {
        http.get("/api/auth/me", null).andExpect(status().isUnauthorized());
        http.get("/api/auth/me", admin()).andExpect(status().isOk()).andExpect(jsonPath("$.username").value("admin"))
                .andExpect(jsonPath("$.capabilities[0]").exists()).andExpect(jsonPath("$.peutApprouver").value(false))
                .andExpect(jsonPath("$.passwordHash").doesNotExist());
        http.get("/api/auth/me", new ContexteRequete("usr-fantome", "f", "admin", null, null)).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errors[0]").value("user not found"));
        // Sous emprunt (ADR 0038), les portes de première connexion de la cible restent fermées ; pour elle-même, elles s'ouvrent.
        Compte premiere = comptes.creer(new DepotComptes.Creation("premiere", "Passe!Test2026", "employee", null, null, true, true, null, false));
        http.get("/api/auth/me", new ContexteRequete(premiere.id(), "premiere", "employee", null, null)).andExpect(status().isOk())
                .andExpect(jsonPath("$.mustChangePassword").value(true)).andExpect(jsonPath("$.securitySetupRequired").value(true));
        http.get("/api/auth/me", new ContexteRequete(premiere.id(), "premiere", "employee", null, null, "usr-admin", "empr-1")).andExpect(status().isOk())
                .andExpect(jsonPath("$.mustChangePassword").value(false)).andExpect(jsonPath("$.securitySetupRequired").value(false));

        jdbc.sql("INSERT INTO tenant_quotas (tenantId, maxTeachers, maxContractsPerYear, modules) VALUES ('T1', 5, 5, '[\"payroll\",\"rh\"]')").update();
        jdbc.sql("INSERT INTO tenant_role_capabilities (tenantId, role, capability, allowed) VALUES ('T1', 'employee', 'payroll.run', 1)").update();
        Compte local = comptes.creer(new DepotComptes.Creation("locale", "p4ssword", "employee", "emp-7", "T1", false, false, null, false));
        ContexteRequete session = new ContexteRequete(local.id(), "locale", "employee", "emp-7", "T1");
        MvcResult r = MockMvcBuilders.standaloneSetup(controleur).build()
                .perform(MockMvcRequestBuilders.get("/api/auth/whoami").requestAttr(ContexteRequete.ATTRIBUT, session).cookie(new Cookie("session", "j")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.authentifie").value(true)).andExpect(jsonPath("$.methode").value("cookie"))
                .andExpect(jsonPath("$.utilisateur.tenantId").value("T1")).andExpect(jsonPath("$.utilisateur.employeeId").value("emp-7"))
                .andExpect(jsonPath("$.locataire.name").value("CSS des Chênes")).andExpect(jsonPath("$.locataire.modules", contains("payroll", "rh")))
                .andExpect(jsonPath("$.instant").value("2026-09-13T12:00:00.000Z")).andReturn();
        assertThat(r.getResponse().getContentAsString()).contains("\"langue\":null").contains("payroll.run");

        // Un compte global sans en-tête : pas de locataire, méthode bearer ; avec l'en-tête, le locataire nommé.
        http.get("/api/auth/whoami", admin()).andExpect(jsonPath("$.methode").value("bearer")).andExpect(jsonPath("$.locataire").isEmpty());
        MockMvcBuilders.standaloneSetup(controleur).build()
                .perform(MockMvcRequestBuilders.get("/api/auth/whoami").requestAttr(ContexteRequete.ATTRIBUT, admin()).header("X-Tenant-Id", " T1 "))
                .andExpect(jsonPath("$.locataire.id").value("T1"));
        MockMvcBuilders.standaloneSetup(controleur).build()
                .perform(MockMvcRequestBuilders.get("/api/auth/whoami").requestAttr(ContexteRequete.ATTRIBUT, admin()).header("X-Tenant-Id", "inconnu"))
                .andExpect(jsonPath("$.locataire").isEmpty());
        http.get("/api/auth/whoami", new ContexteRequete("usr-fantome", "f", "admin", null, null)).andExpect(jsonPath("$.capacites", hasSize(0)));
    }

    // ── Mot de passe ───────────────────────────────────────────────────────────────────────────

    @Test
    void changerSonMotDePasseExigeLAncienEtLaPolitique() throws Exception {
        ContexteRequete a = admin();
        http.post("/api/auth/change-password", a, "{\"oldPassword\":\"admin\"}").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0]").value("oldPassword and newPassword required"));
        http.post("/api/auth/change-password", a, "{\"oldPassword\":\"wrong\",\"newPassword\":\"ne1\"}").andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errors[0]").value("wrong password"));
        http.post("/api/auth/change-password", a, "{\"oldPassword\":\"admin\",\"newPassword\":\"faible\"}").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0]", startsWith("mot de passe trop faible")));
        http.post("/api/auth/change-password", a, "{\"oldPassword\":\"admin\",\"newPassword\":\"Str0ng!Passw0rd\"}").andExpect(status().isOk());
        assertThat(login("admin", "admin", null).getResponse().getStatus()).isEqualTo(401);
        assertThat(login("admin", "Str0ng!Passw0rd", null).getResponse().getStatus()).isEqualTo(200);
        assertThat(nombreAudit("auth.password.change")).isEqualTo(1);
    }

    @Test
    void lePremierMotDePasseNEstAcceptableQueSiLeChangementEstImpose() throws Exception {
        ContexteRequete a = admin();
        http.post("/api/auth/first-login/change-password", a, "{\"newPassword\":\"Str0ng!Passw0rd\"}").andExpect(status().isConflict())
                .andExpect(jsonPath("$.errors[0]").value("password change not required"));
        Compte neuf = comptes.creer(new DepotComptes.Creation("neuve", "TempPass12", "employee", null, null, true, true, null, false));
        ContexteRequete s = new ContexteRequete(neuf.id(), "neuve", "employee", null, null);
        http.post("/api/auth/first-login/change-password", s, "{\"newPassword\":\"neuve-1\"}").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.passwordIssues", contains("au moins 12 caractères", "une lettre majuscule", "ne pas contenir le nom d'utilisateur")));
        http.post("/api/auth/first-login/change-password", s, "{\"newPassword\":\"Str0ng!Passw0rd\"}").andExpect(status().isOk());
        assertThat(comptes.parId(neuf.id()).orElseThrow().mustChangePassword()).isFalse();
        http.post("/api/auth/first-login/change-password", new ContexteRequete("usr-fantome", "f", "employee", null, null), "{}")
                .andExpect(status().isNotFound());
    }

    // ── Comptes ────────────────────────────────────────────────────────────────────────────────

    @Test
    void creerUnCompteExigeUnAdminUnRoleConnuEtUnMotDePasseProvisoireDeHuit() throws Exception {
        http.post("/api/auth/users", null, "{}").andExpect(status().isUnauthorized());
        ContexteRequete a = admin();
        http.post("/api/auth/users", a, "{\"username\":\"marie\"}").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0]").value("username, password, role required"));
        http.post("/api/auth/users", a, "{\"username\":\"marie\",\"password\":\"TempSecret1\",\"role\":\"doyen\"}").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0]").value("invalid role (attendus : admin, manager, rh, director, accountant, employee)"));
        http.post("/api/auth/users", a, "{\"username\":\"marie\",\"password\":\"court\",\"role\":\"manager\"}").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0]").value("temporary password must be at least 8 characters"));
        http.post("/api/auth/users", a, "{\"username\":\"marie\",\"password\":\"TempSecret1\",\"role\":\"manager\"}").andExpect(status().isCreated())
                .andExpect(jsonPath("$.role").value("manager")).andExpect(jsonPath("$.mustChangePassword").value(true))
                .andExpect(jsonPath("$.securitySetupRequired").value(true));
        assertThat(nombreAudit("auth.user.create")).isEqualTo(1);
        MvcResult lr = login("marie", "TempSecret1", null);
        assertThat(lr.getResponse().getStatus()).isEqualTo(200);
        // Un employé ne crée personne.
        http.post("/api/auth/users", a, "{\"username\":\"bob\",\"password\":\"TempPass12\",\"role\":\"employee\"}").andExpect(status().isCreated());
        http.post("/api/auth/users", session(login("bob", "TempPass12", null)), "{\"username\":\"x\",\"password\":\"TempPass12\",\"role\":\"employee\"}")
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.errors[0]").value("insufficient role"));
        // Un mot de passe permanent est soumis à la politique élevée ; un doublon est refusé.
        http.post("/api/auth/users", a, "{\"username\":\"perm\",\"password\":\"TempSecret1\",\"role\":\"employee\",\"mustChangePassword\":false}")
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors[0]", startsWith("mot de passe trop faible")));
        http.post("/api/auth/users", a, "{\"username\":\"marie\",\"password\":\"TempSecret1\",\"role\":\"manager\"}").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0]").value("username already exists"));
        // Un rôle propre à l'établissement est attribuable chez lui.
        jdbc.sql("INSERT INTO tenant_roles (tenantId, code, label, active, createdAt) VALUES ('T1', 'doyen', 'Doyen', 1, '2026-01-01')").update();
        http.post("/api/auth/users", ClientHttpEssai.session("admin", "T1"), "{\"username\":\"d\",\"password\":\"TempSecret1\",\"role\":\"doyen\"}")
                .andExpect(status().isCreated()).andExpect(jsonPath("$.tenantId").value("T1"));
    }

    @Test
    void listerModifierReinitialiserEtSupprimerRestentDansLEtablissement() throws Exception {
        ContexteRequete a = admin();
        ContexteRequete adminT1 = ClientHttpEssai.session("admin", "T1");
        http.post("/api/auth/users", adminT1, "{\"username\":\"carole\",\"password\":\"TempPass12\",\"role\":\"employee\"}").andExpect(status().isCreated());
        String carole = comptes.parNom("carole", "T1").orElseThrow().id();

        http.get("/api/auth/users", null).andExpect(status().isUnauthorized());
        http.get("/api/auth/users", adminT1).andExpect(jsonPath("$", hasSize(1))).andExpect(jsonPath("$[0].username").value("carole"));
        http.get("/api/auth/users", a).andExpect(jsonPath("$[*].username", contains("admin")));
        http.get("/api/auth/users", ClientHttpEssai.superadmin()).andExpect(jsonPath("$", hasSize(3)))
                .andExpect(jsonPath("$[*].username", contains("admin", "carole", "superadmin")));

        // Hors de portée : 404 sans rien divulguer ; le superadmin, lui, voit tout.
        http.patch("/api/auth/users/" + carole, a, "{\"role\":\"manager\"}").andExpect(status().isNotFound());
        http.patch("/api/auth/users/" + comptes.parNom("superadmin", null).orElseThrow().id(), a, "{\"active\":false}").andExpect(status().isNotFound());
        http.patch("/api/auth/users/" + carole, adminT1, "{\"role\":\"manager\",\"active\":false,\"employeeId\":null,\"email\":\"c@d.qc\"}")
                .andExpect(status().isOk()).andExpect(jsonPath("$.role").value("manager")).andExpect(jsonPath("$.active").value(false))
                .andExpect(jsonPath("$.email").value("c@d.qc"));
        assertThat(login("carole", "TempPass12", "T1").getResponse().getStatus()).isEqualTo(401);
        http.patch("/api/auth/users/absent", ClientHttpEssai.superadmin(), "{}").andExpect(status().isNotFound());
        http.patch("/api/auth/users/" + carole, adminT1, "{\"password\":\"abc\"}").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0]").value("password too short"));

        http.post("/api/auth/users/" + carole + "/reset-security", a, null).andExpect(status().isNotFound());
        http.post("/api/auth/users/absent/reset-security", adminT1, null).andExpect(status().isNotFound());
        http.post("/api/auth/users/" + carole + "/reset-security", adminT1, null).andExpect(status().isOk())
                .andExpect(jsonPath("$.securitySetupRequired").value(true)).andExpect(jsonPath("$.userId").value(carole));
        assertThat(nombreAudit("auth.security.reset")).isEqualTo(1);

        http.delete("/api/auth/users/" + adminT1.userId(), adminT1).andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors[0]").value("cannot delete self"));
        http.delete("/api/auth/users/" + carole, a).andExpect(status().isNotFound());
        http.delete("/api/auth/users/" + carole, adminT1).andExpect(status().isNoContent());
        http.delete("/api/auth/users/" + carole, adminT1).andExpect(status().isNotFound());
        assertThat(nombreAudit("auth.user.delete")).isEqualTo(1);
    }

    // ── Rôles ──────────────────────────────────────────────────────────────────────────────────

    @Test
    void unEtablissementDefinitSesRolesEtAjusteLesAcces() throws Exception {
        ContexteRequete adminT1 = ClientHttpEssai.session("admin", "T1");
        http.get("/api/auth/roles", ClientHttpEssai.session("employee", "T1")).andExpect(status().isForbidden());
        http.get("/api/auth/roles", adminT1).andExpect(jsonPath("$.roles", hasSize(6))).andExpect(jsonPath("$.roles[0].code").value("admin"))
                .andExpect(jsonPath("$.roles[0].builtin").value(true)).andExpect(jsonPath("$.roles[0].label").value("Administrateur"))
                .andExpect(jsonPath("$.catalog[0].capability").exists()).andExpect(jsonPath("$.catalog[0].roles").doesNotExist());

        // Un admin système sans établissement n'en définit pas.
        http.post("/api/auth/roles", admin(), "{\"code\":\"doyen\",\"label\":\"Doyen\"}").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0]").value("un rôle appartient à un établissement"));
        http.patch("/api/auth/roles/doyen", admin(), "{}").andExpect(status().isBadRequest());
        http.delete("/api/auth/roles/doyen", admin()).andExpect(status().isBadRequest());
        http.put("/api/auth/roles/doyen/capabilities/payroll.run", admin(), "{}").andExpect(status().isBadRequest());

        http.post("/api/auth/roles", adminT1, "{\"code\":\"Admin\",\"label\":\"X\"}").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0]").value("'admin' is a built-in role"));
        http.post("/api/auth/roles", adminT1, "{\"code\":\"doyen\",\"label\":\"Doyen\",\"capabilities\":[\"payroll.run\"]}")
                .andExpect(status().isCreated()).andExpect(jsonPath("$.code").value("doyen")).andExpect(jsonPath("$.builtin").value(false))
                .andExpect(jsonPath("$.capabilities", contains("payroll.run")));
        http.post("/api/auth/roles", adminT1, "{\"code\":\"doyen\",\"label\":\"Doyen\"}").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0]").value("role code already exists"));
        http.post("/api/auth/roles", adminT1, "{\"code\":\"fantome\",\"label\":\"F\",\"capabilities\":[\"inconnue\"]}")
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors[0]").value("unknown capability"));

        http.get("/api/auth/assignable-roles", ClientHttpEssai.session("employee", "T1")).andExpect(jsonPath("$", hasSize(7)))
                .andExpect(jsonPath("$[6].role").value("doyen")).andExpect(jsonPath("$[6].label").value("Doyen"));
        // Un rôle personnalisé part de rien ; une surcharge se pose et se retire, sauf à l'admin.
        http.put("/api/auth/roles/doyen/capabilities/payroll.run", adminT1, "{\"allowed\":false}").andExpect(status().isOk())
                .andExpect(jsonPath("$.capabilities", hasSize(0)));
        http.put("/api/auth/roles/employee/capabilities/payroll.run", adminT1, null).andExpect(jsonPath("$.capabilities", contains("payroll.run")));
        http.put("/api/auth/roles/admin/capabilities/payroll.run", adminT1, "{\"allowed\":false}").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0]", startsWith("les droits de l'administrateur")));
        http.put("/api/auth/roles/superadmin/capabilities/payroll.run", adminT1, "{}").andExpect(status().isBadRequest());
        http.put("/api/auth/roles/absent/capabilities/payroll.run", adminT1, "{}").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0]").value("unknown role"));
        http.get("/api/auth/capabilities", ClientHttpEssai.session("employee", "T1")).andExpect(jsonPath("$.role").value("employee"))
                .andExpect(jsonPath("$.mine", contains("payroll.run"))).andExpect(jsonPath("$.catalog[0].roles").isArray());

        http.patch("/api/auth/roles/doyen", adminT1, "{\"label\":\"Doyenne\",\"active\":false}").andExpect(jsonPath("$.label").value("Doyenne"))
                .andExpect(jsonPath("$.active").value(false));
        http.patch("/api/auth/roles/doyen", adminT1, "{\"label\":\" \"}").andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors[0]").value("label required"));
        http.patch("/api/auth/roles/rh", adminT1, "{\"label\":\"X\"}").andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors[0]").value("built-in role cannot be renamed"));
        http.patch("/api/auth/roles/absent", adminT1, "{}").andExpect(status().isNotFound()).andExpect(jsonPath("$.errors[0]").value("role not found"));
        http.get("/api/auth/assignable-roles", adminT1).andExpect(jsonPath("$", hasSize(6)));

        jdbc.sql("UPDATE tenant_roles SET active = 1 WHERE code = 'doyen'").update();
        http.post("/api/auth/users", adminT1, "{\"username\":\"d\",\"password\":\"TempSecret1\",\"role\":\"doyen\"}").andExpect(status().isCreated());
        http.delete("/api/auth/roles/doyen", adminT1).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0]").value("1 compte(s) portent encore ce rôle : reclassez-les d'abord"));
        http.delete("/api/auth/roles/rh", adminT1).andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors[0]").value("built-in role cannot be deleted"));
        jdbc.sql("DELETE FROM users WHERE role = 'doyen'").update();
        http.delete("/api/auth/roles/doyen", adminT1).andExpect(status().isNoContent());
        http.get("/api/auth/roles", adminT1).andExpect(jsonPath("$.roles", hasSize(6)));
    }

    // ── Catalogue ──────────────────────────────────────────────────────────────────────────────

    @Test
    void leCatalogueDesQuestionsAUnePorteeGlobaleEtUnePorteeParEtablissement() throws Exception {
        ContexteRequete sa = ClientHttpEssai.superadmin();
        ContexteRequete adminT1 = ClientHttpEssai.session("admin", "T1");
        // Semis des six questions de secours à la première lecture globale.
        http.get("/api/auth/security/catalog", sa).andExpect(jsonPath("$.scope").value("global")).andExpect(jsonPath("$.questions", hasSize(6)))
                .andExpect(jsonPath("$.questions[0].sortOrder").value(0)).andExpect(jsonPath("$.questions[0].tenantId").isEmpty());
        http.get("/api/auth/security/catalog", admin()).andExpect(status().isForbidden()).andExpect(jsonPath("$.errors[0]").value("aucun établissement associé"));
        http.post("/api/auth/security/catalog", admin(), "{\"question\":\"Votre rue ?\"}").andExpect(status().isForbidden());
        http.get("/api/auth/security/catalog", ClientHttpEssai.session("employee", "T1")).andExpect(status().isForbidden());

        http.post("/api/auth/security/catalog", adminT1, "{\"question\":\"abc\"}").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0]").value("la question doit comporter au moins 5 caractères"));
        MvcResult ajout = http.post("/api/auth/security/catalog", adminT1, "{\"question\":\"Votre rue d'enfance ?\"}").andExpect(status().isCreated())
                .andExpect(jsonPath("$.tenantId").value("T1")).andExpect(jsonPath("$.sortOrder").value(0)).andReturn();
        String id = ajout.getResponse().getContentAsString().replaceAll("^.*?\"id\":\"([^\"]+)\".*$", "$1");
        http.post("/api/auth/security/catalog", adminT1, "{\"question\":\"votre RUE d'enfance ?\"}").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0]").value("cette question existe déjà dans ce catalogue"));
        http.post("/api/auth/security/catalog", adminT1, "{\"question\":\"Votre plat préféré ?\"}").andExpect(jsonPath("$.sortOrder").value(1));
        assertThat(nombreAudit("auth.security.catalog.add")).isEqualTo(2);
        // Le superadmin cible un établissement par ?tenantId= ou par le corps ; « global » = la portée globale.
        MockMvcBuilders.standaloneSetup(controleur).build()
                .perform(MockMvcRequestBuilders.get("/api/auth/security/catalog").param("tenantId", "T1").requestAttr(ContexteRequete.ATTRIBUT, sa))
                .andExpect(jsonPath("$.scope").value("T1")).andExpect(jsonPath("$.questions", hasSize(2)));
        MockMvcBuilders.standaloneSetup(controleur).build()
                .perform(MockMvcRequestBuilders.get("/api/auth/security/catalog").param("tenantId", "global").requestAttr(ContexteRequete.ATTRIBUT, sa))
                .andExpect(jsonPath("$.scope").value("global"));
        http.post("/api/auth/security/catalog", sa, "{\"tenantId\":\"T1\",\"question\":\"Votre premier emploi ?\"}").andExpect(jsonPath("$.tenantId").value("T1"));
        http.post("/api/auth/security/catalog", sa, "{\"question\":\"Votre premier emploi ?\"}").andExpect(jsonPath("$.tenantId").isEmpty()).andExpect(jsonPath("$.sortOrder").value(6));

        // La liste effective : globales puis celles de l'établissement, sans doublon.
        http.get("/api/auth/security/questions", ClientHttpEssai.session("employee", "T1")).andExpect(jsonPath("$.questions", hasSize(9)))
                .andExpect(jsonPath("$.questions[6]").value("Votre premier emploi ?")).andExpect(jsonPath("$.questions[7]").value("Votre rue d'enfance ?"));
        http.get("/api/auth/security/questions", admin()).andExpect(jsonPath("$.questions", hasSize(7)));

        http.patch("/api/auth/security/catalog/absent", adminT1, "{}").andExpect(status().isNotFound()).andExpect(jsonPath("$.errors[0]").value("question introuvable"));
        http.patch("/api/auth/security/catalog/" + id, ClientHttpEssai.session("admin", "T2"), "{\"active\":false}").andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errors[0]").value("hors de votre établissement"));
        http.patch("/api/auth/security/catalog/" + id, adminT1, "{\"active\":false,\"question\":\"Votre rue natale ?\"}").andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(false)).andExpect(jsonPath("$.question").value("Votre rue natale ?"));
        http.patch("/api/auth/security/catalog/" + id, adminT1, "{\"question\":\"ab\"}").andExpect(status().isBadRequest());
        http.patch("/api/auth/security/catalog/" + id, adminT1, "{}").andExpect(jsonPath("$.active").value(false));
        http.get("/api/auth/security/questions", ClientHttpEssai.session("employee", "T1")).andExpect(jsonPath("$.questions", hasSize(8)));

        String globale = catalogueGlobalId();
        http.delete("/api/auth/security/catalog/" + globale, adminT1).andExpect(status().isForbidden());
        http.delete("/api/auth/security/catalog/absent", adminT1).andExpect(status().isNotFound());
        http.delete("/api/auth/security/catalog/" + id, adminT1).andExpect(status().isNoContent());
        http.delete("/api/auth/security/catalog/" + globale, sa).andExpect(status().isNoContent());
        // Catalogue global vidé : la liste effective retombe sur les questions de secours.
        jdbc.sql("DELETE FROM security_question_catalog").update();
        http.get("/api/auth/security/questions", ClientHttpEssai.session("employee", "T9")).andExpect(jsonPath("$.questions", hasSize(6)));
    }

    private String catalogueGlobalId() {
        return jdbc.sql("SELECT id FROM security_question_catalog WHERE tenantId IS NULL ORDER BY sortOrder LIMIT 1").query(String.class).single();
    }

    // ── Journal d'audit ────────────────────────────────────────────────────────────────────────

    /** L'admin système sans passer par la connexion : celle-ci écrirait dans le journal qu'on mesure. */
    private static ContexteRequete adminSysteme() {
        return new ContexteRequete("usr-admin-sys", "admin", "admin", null, null);
    }

    private void entree(String id, String at, String userId, String username, String action, String target, String details, String empreinte) {
        jdbc.sql("INSERT INTO audit_log (id, at, userId, username, action, target, details, empreinte) VALUES (?, ?, ?, ?, ?, ?, ?, ?)")
                .params(id, at, userId, username, action, target, details, empreinte).update();
    }

    @Test
    void leJournalEstCloisonneEnrichiFiltreEtPagine() throws Exception {
        Compte locale = comptes.creer(new DepotComptes.Creation("locale", "p4ssword", "employee", null, "T1", false, false, null, false));
        entree("a1", "2026-09-01T10:00:00.000Z", locale.id(), "locale", "auth.login.ok", null, null, null);
        entree("a2", "2026-09-02T10:00:00.000Z", null, "locale", "rh.dossier.lecture", "emp-1", null, null);
        entree("a3", "2026-09-03T10:00:00.000Z", null, "inconnu", "auth.login.fail", "inconnu", null, null);
        entree("a4", "2026-09-04T10:00:00.000Z", comptes.parNom("admin", null).orElseThrow().id(), "admin", "auth.login.ok", null, null, null);

        http.get("/api/auth/audit", ClientHttpEssai.session("employee", "T1")).andExpect(status().isForbidden());
        http.get("/api/auth/audit", ClientHttpEssai.session("admin", "T1")).andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].id").value("a2")).andExpect(jsonPath("$[0].tenantId").value("T1")).andExpect(jsonPath("$[0].tenantName").value("CSS des Chênes"));
        // L'admin système sans établissement lit les comptes globaux et les événements sans compte.
        http.get("/api/auth/audit", adminSysteme()).andExpect(jsonPath("$[*].id", contains("a4", "a3")));
        http.get("/api/auth/audit", ClientHttpEssai.superadmin()).andExpect(jsonPath("$", hasSize(4)));
        MockMvcBuilders.standaloneSetup(controleur).build().perform(MockMvcRequestBuilders.get("/api/auth/audit").param("q", "chênes").param("limit", "1")
                .requestAttr(ContexteRequete.ATTRIBUT, ClientHttpEssai.superadmin())).andExpect(jsonPath("$", hasSize(1))).andExpect(jsonPath("$[0].id").value("a2"));
        MockMvcBuilders.standaloneSetup(controleur).build().perform(MockMvcRequestBuilders.get("/api/auth/audit").param("action", "auth.login.ok")
                .requestAttr(ContexteRequete.ATTRIBUT, ClientHttpEssai.superadmin())).andExpect(jsonPath("$[*].id", contains("a4", "a1")));
        MockMvcBuilders.standaloneSetup(controleur).build().perform(MockMvcRequestBuilders.get("/api/auth/audit").param("page", "1").param("pageSize", "3")
                .requestAttr(ContexteRequete.ATTRIBUT, ClientHttpEssai.superadmin()))
                .andExpect(jsonPath("$.total").value(4)).andExpect(jsonPath("$.page").value(1)).andExpect(jsonPath("$.pageSize").value(3))
                .andExpect(jsonPath("$.items", hasSize(1))).andExpect(jsonPath("$.items[0].id").value("a1"));
        MockMvcBuilders.standaloneSetup(controleur).build().perform(MockMvcRequestBuilders.get("/api/auth/audit").param("page", "x").param("pageSize", "900")
                .requestAttr(ContexteRequete.ATTRIBUT, ClientHttpEssai.superadmin())).andExpect(jsonPath("$.page").value(0)).andExpect(jsonPath("$.pageSize").value(200));
    }

    @Test
    void leSiemExporteEtLaConsoleSynthetise() throws Exception {
        entree("a1", "2026-09-01T10:00:00.000Z", "u1", "alice", "auth.login.fail", "alice", null, null);
        entree("a2", "2026-09-02T10:00:00.000Z", "u1", "alice", "rh.dossier.lecture", null, "{\"x\":1}", null);
        http.get("/api/auth/audit/siem-export", ClientHttpEssai.session("rh", "T1")).andExpect(status().isForbidden());
        MvcResult cef = http.get("/api/auth/audit/siem-export", adminSysteme()).andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", "attachment; filename=\"audit-siem.cef\"")).andReturn();
        assertThat(cef.getResponse().getContentType()).startsWith("text/plain");
        assertThat(cef.getResponse().getContentAsString()).startsWith("CEF:0|NexuVINA|Finance|1.0|rh.dossier.lecture|").contains("\nCEF:0|NexuVINA|Finance|1.0|auth.login.fail|auth.login.fail|7|");
        MvcResult nd = MockMvcBuilders.standaloneSetup(controleur).build().perform(MockMvcRequestBuilders.get("/api/auth/audit/siem-export").param("format", "ndjson").param("action", "auth.login.fail")
                .requestAttr(ContexteRequete.ATTRIBUT, adminSysteme())).andExpect(header().string("Content-Disposition", "attachment; filename=\"audit-siem.ndjson\"")).andReturn();
        assertThat(nd.getResponse().getContentAsString()).isEqualTo("{\"id\":\"a1\",\"at\":\"2026-09-01T10:00:00.000Z\",\"userId\":\"u1\",\"username\":\"alice\",\"action\":\"auth.login.fail\",\"target\":\"alice\",\"details\":null}\n");
        http.get("/api/auth/security/siem-console", adminSysteme()).andExpect(jsonPath("$.summary.high").value(1)).andExpect(jsonPath("$.summary.total").value(2))
                .andExpect(jsonPath("$.events[0].level").value("low"));
        MockMvcBuilders.standaloneSetup(controleur).build().perform(MockMvcRequestBuilders.get("/api/auth/security/siem-console").param("minLevel", "high")
                .requestAttr(ContexteRequete.ATTRIBUT, adminSysteme())).andExpect(jsonPath("$.events", hasSize(1))).andExpect(jsonPath("$.events[0].id").value("a1"));
    }

    @Test
    void laRetentionPurgeEtLEffacementAnonymise() throws Exception {
        entree("vieille", "2026-05-01T10:00:00.000Z", "u1", "alice", "auth.login.ok", null, "{\"ip\":\"1\"}", null);
        entree("recente", "2026-09-10T10:00:00.000Z", "u1", "alice", "auth.login.ok", null, "{\"ip\":\"2\"}", null);
        http.post("/api/auth/security/retention/purge", adminSysteme(), "{\"retentionDays\":0}").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0]").value("retentionDays must be > 0"));
        http.post("/api/auth/security/retention/purge", adminSysteme(), "{\"retentionDays\":\"abc\"}").andExpect(status().isBadRequest());
        http.post("/api/auth/security/retention/purge", adminSysteme(), "{}").andExpect(status().isBadRequest());
        http.post("/api/auth/security/retention/purge", adminSysteme(), "{\"retentionDays\":\"30\"}").andExpect(status().isOk())
                .andExpect(jsonPath("$.cutoff").value("2026-08-14T12:00:00.000Z")).andExpect(jsonPath("$.purged").value(1)).andExpect(jsonPath("$.retentionDays").value(30.0));
        assertThat(nombreAudit("security.retention.purge")).isEqualTo(1);
        http.post("/api/auth/security/retention/erase-user", adminSysteme(), "{\"username\":\" \"}").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0]").value("username required"));
        http.post("/api/auth/security/retention/erase-user", adminSysteme(), "{\"username\":\" alice \"}").andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("alice")).andExpect(jsonPath("$.anonymized").value(1));
        assertThat(jdbc.sql("SELECT username FROM audit_log WHERE id = 'recente'").query(String.class).single()).isEqualTo("ERASED");
        assertThat(nombreAudit("security.retention.erase")).isEqualTo(1);
    }

    @Test
    void lIntegriteEtLaRevueSontVerifiablesEtConsigneesParLeSuperadmin() throws Exception {
        http.get("/api/auth/audit/integrite", adminSysteme()).andExpect(jsonPath("$.entrees").value(0)).andExpect(jsonPath("$.intacte").value(true))
                .andExpect(jsonPath("$.revue.derniere").isEmpty()).andExpect(jsonPath("$.revue.enRetard").value(false));
        JournalAudit journal = new JournalAudit(jdbc);
        journal.consigner("u1", "alice", "auth.login.ok", null, null);
        journal.consigner("u1", "alice", "rh.dossier.lecture", "emp-1", Map.of("k", "v"));
        jdbc.sql("UPDATE audit_log SET at = '2026-06-10T10:00:00.000Z' WHERE action = 'auth.login.ok'").update();
        jdbc.sql("UPDATE audit_log SET at = '2026-06-20T10:00:00.000Z' WHERE action = 'rh.dossier.lecture'").update();
        http.get("/api/auth/audit/integrite", adminSysteme()).andExpect(jsonPath("$.chainees").value(2)).andExpect(jsonPath("$.intacte").value(false))
                .andExpect(jsonPath("$.premiereRupture.id").exists()).andExpect(jsonPath("$.revue.enRetard").value(true))
                .andExpect(jsonPath("$.revue.prochaineAvant").value("2026-09-08T10:00:00.000Z"));

        http.post("/api/auth/audit/revues", adminSysteme(), "{}").andExpect(status().isForbidden()).andExpect(jsonPath("$.errors[0]").value("insufficient role"));
        http.post("/api/auth/audit/revues", null, "{}").andExpect(status().isUnauthorized());
        http.post("/api/auth/audit/revues", ClientHttpEssai.superadmin(), "{\"du\":\"2026-06-01\"}").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0]", startsWith("au : date AAAA-MM-JJ attendue; constat requis")));
        http.post("/api/auth/audit/revues", ClientHttpEssai.superadmin(), "{\"du\":\"2026-06-01\",\"au\":\"2026-06-15\",\"constat\":\"Registre lu, chaîne intacte sur la période.\"}")
                .andExpect(status().isCreated()).andExpect(jsonPath("$.entrees").value(1)).andExpect(jsonPath("$.reviseurNom").value("sa"))
                .andExpect(jsonPath("$.faiteLe").value("2026-09-13T12:00:00.000Z")).andExpect(jsonPath("$.empreinteFin").isString());
        http.get("/api/auth/audit/revues", adminSysteme()).andExpect(jsonPath("$", hasSize(1))).andExpect(jsonPath("$[0].du").value("2026-06-01"));
        http.get("/api/auth/audit/revues", ClientHttpEssai.session("employee", "T1")).andExpect(status().isForbidden());
        assertThat(nombreAudit("audit.revue")).isEqualTo(1);
        http.get("/api/auth/audit/integrite", adminSysteme()).andExpect(jsonPath("$.revue.derniere").value("2026-09-13T12:00:00.000Z")).andExpect(jsonPath("$.revue.enRetard").value(false));
    }

    // ── Second facteur ─────────────────────────────────────────────────────────────────────────

    @Test
    void lesQuestionsSecretesSontExigeesAuLoginUneFoisConfigurees() throws Exception {
        http.post("/api/auth/users", admin(), "{\"username\":\"qauser\",\"password\":\"TempSecret1\",\"role\":\"employee\"}").andExpect(status().isCreated());
        ContexteRequete q = session(login("qauser", "TempSecret1", null));
        http.get("/api/auth/security/status", q).andExpect(jsonPath("$.securityMethod").isEmpty()).andExpect(jsonPath("$.hasSecurityQuestion").value(false))
                .andExpect(jsonPath("$.minSecurityQuestions").value(3)).andExpect(jsonPath("$.securityQuestionsCount").value(0));
        http.get("/api/auth/security/question", q).andExpect(status().isNotFound()).andExpect(jsonPath("$.errors[0]").value("no question configured"));
        http.post("/api/auth/security/verify", q, "{}").andExpect(status().isConflict()).andExpect(jsonPath("$.errors[0]").value("security method not configured"));
        http.post("/api/auth/security/setup", q, "{\"method\":\"autre\"}").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0]").value("method must be 'question' or 'totp'"));
        http.post("/api/auth/security/setup", q, "{\"method\":\"question\",\"questions\":[{\"question\":\"Ville ?\",\"answer\":\"Lyon\"}]}")
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors[0]").value("at least 3 security questions are required"));
        http.post("/api/auth/security/setup", q, "{\"method\":\"question\",\"questions\":[{\"question\":\"Ville ?\",\"answer\":\"Lyon\"},"
                + "{\"question\":\"ville ?\",\"answer\":\"Marie\"},{\"question\":\"Animal ?\",\"answer\":\"Rex\"}]}")
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors[0]").value("security questions must be distinct"));
        http.post("/api/auth/security/setup", q, "{\"method\":\"question\",\"questions\":[{\"question\":\"Ville de naissance ?\",\"answer\":\"Lyon\"},"
                + "{\"question\":\"Prénom de votre mère ?\",\"answer\":\"Marie\"},{\"question\":\"Premier animal ?\",\"answer\":\"Rex\"}, \"pas-un-objet\"]}")
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors[0]").value("question #4 must be at least 5 characters"));
        http.post("/api/auth/security/setup", q, "{\"method\":\"question\",\"questions\":[{\"question\":\"Ville de naissance ?\",\"answer\":\"Lyon\"},"
                + "{\"question\":\"Prénom de votre mère ?\",\"answer\":\"Marie\"},{\"question\":\"Premier animal ?\",\"answer\":\"Rex\"}]}")
                .andExpect(status().isOk()).andExpect(jsonPath("$.count").value(3));
        http.get("/api/auth/security/status", q).andExpect(jsonPath("$.securityMethod").value("question")).andExpect(jsonPath("$.hasSecurityQuestion").value(true));
        http.get("/api/auth/security/question", q).andExpect(jsonPath("$.questions", contains("Ville de naissance ?", "Prénom de votre mère ?", "Premier animal ?")));

        MvcResult etape1 = login("qauser", "TempSecret1", null);
        assertThat(etape1.getResponse().getContentAsString())
                .isEqualTo("{\"mfaRequired\":true,\"securityMethod\":\"question\",\"securityQuestions\":[\"Ville de naissance ?\",\"Prénom de votre mère ?\",\"Premier animal ?\"],"
                        + "\"mustChangePassword\":true,\"securitySetupRequired\":false}");
        MvcResult etape2 = MockMvcBuilders.standaloneSetup(controleur).build().perform(MockMvcRequestBuilders.post("/api/auth/login")
                .contentType("application/json").content("{\"username\":\"qauser\",\"password\":\"TempSecret1\",\"securityAnswers\":[\"Lyon\",\"Marie\",\"Rex\"]}")).andReturn();
        assertThat(etape2.getResponse().getContentAsString()).contains("\"token\":\"");
        MvcResult unique = MockMvcBuilders.standaloneSetup(controleur).build().perform(MockMvcRequestBuilders.post("/api/auth/login")
                .contentType("application/json").content("{\"username\":\"qauser\",\"password\":\"TempSecret1\",\"securityAnswer\":\"Lyon\"}")).andReturn();
        assertThat(unique.getResponse().getContentAsString()).contains("\"mfaRequired\":true");

        http.post("/api/auth/security/verify", q, "{\"answers\":[\"Lyon\",\"Marie\",\"Rex\"]}").andExpect(jsonPath("$.ok").value(true));
        http.post("/api/auth/security/verify", q, "{\"answer\":\"Lyon\"}").andExpect(jsonPath("$.ok").value(false));
        http.post("/api/auth/security/verify", q, "{}").andExpect(jsonPath("$.ok").value(false));
        assertThat(nombreAudit("auth.security.verify.ok")).isEqualTo(1);
        assertThat(nombreAudit("auth.security.verify.fail")).isEqualTo(2);
    }

    @Test
    void leTotpSeConfigureAvecLeSecretEnAttentePuisEstExigeAuLogin() throws Exception {
        http.post("/api/auth/users", admin(), "{\"username\":\"totpuser\",\"password\":\"TempSecret1\",\"role\":\"employee\"}").andExpect(status().isCreated());
        ContexteRequete t = session(login("totpuser", "TempSecret1", null));
        http.post("/api/auth/security/setup", t, "{\"method\":\"totp\",\"code\":\"000000\"}").andExpect(status().isConflict())
                .andExpect(jsonPath("$.errors[0]").value("call /security/totp/init first"));
        String secret = Totp.encoderBase32("12345678901234567890".getBytes());
        comptes.mettreTotpEnAttente(t.userId(), secret);
        http.post("/api/auth/security/setup", t, "{\"method\":\"totp\",\"code\":\"000000\"}").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0]").value("invalid TOTP code"));
        http.post("/api/auth/security/setup", t, "{\"method\":\"totp\",\"code\":\"" + Totp.code(secret, HORLOGE.millis()) + "\"}").andExpect(status().isOk())
                .andExpect(jsonPath("$.method").value("totp"));

        MvcResult etape1 = login("totpuser", "TempSecret1", null);
        assertThat(etape1.getResponse().getContentAsString())
                .isEqualTo("{\"mfaRequired\":true,\"securityMethod\":\"totp\",\"mustChangePassword\":true,\"securitySetupRequired\":false}");
        MvcResult etape2 = MockMvcBuilders.standaloneSetup(controleur).build().perform(MockMvcRequestBuilders.post("/api/auth/login")
                .contentType("application/json").content("{\"username\":\"totpuser\",\"password\":\"TempSecret1\",\"totpCode\":\"" + Totp.code(secret, HORLOGE.millis()) + "\"}")).andReturn();
        assertThat(etape2.getResponse().getContentAsString()).contains("\"token\":\"");
        http.post("/api/auth/security/verify", t, "{\"code\":\"" + Totp.code(secret, HORLOGE.millis()) + "\"}").andExpect(jsonPath("$.ok").value(true));
        http.post("/api/auth/security/verify", t, "{\"code\":\"000000\"}").andExpect(jsonPath("$.ok").value(false));

        // Une méthode inconnue en base : 500, comme le Node.
        jdbc.sql("UPDATE users SET securityMethod = 'carte' WHERE id = ?").params(t.userId()).update();
        http.post("/api/auth/security/verify", t, "{}").andExpect(status().is(500)).andExpect(jsonPath("$.errors[0]").value("unknown security method"));
        http.get("/api/auth/security/status", new ContexteRequete("usr-fantome", "f", "employee", null, null)).andExpect(status().isNotFound());
        http.post("/api/auth/security/setup", new ContexteRequete("usr-fantome", "f", "employee", null, null), "{}").andExpect(status().isNotFound());
        http.post("/api/auth/security/verify", new ContexteRequete("usr-fantome", "f", "employee", null, null), "{}").andExpect(status().isNotFound());
        http.get("/api/auth/security/question", new ContexteRequete("usr-fantome", "f", "employee", null, null)).andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("trois échecs depuis une même adresse verrouillent le compte un quart d'heure ; une réussite efface les échecs")
    void verrouDeConnexion() throws Exception {
        http.post("/api/auth/login", null, "{\"username\":\"admin\",\"password\":\"faux\"}").andExpect(status().isUnauthorized());
        http.post("/api/auth/login", null, "{\"username\":\"admin\",\"password\":\"admin\"}").andExpect(status().isOk());
        for (int i = 0; i < 3; i++) {
            http.post("/api/auth/login", null, "{\"username\":\"admin\",\"password\":\"faux\"}").andExpect(status().isUnauthorized());
        }
        assertThat(journalWaf).containsExactly("securite.waf.bloque");
        http.post("/api/auth/login", null, "{\"username\":\"admin\",\"password\":\"admin\"}").andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.errors[0]").value(containsString("verrouillé après trop d'échecs : réessayez dans 15 min")));
    }

    // ── Tests d'intrusion du 22 novembre 2026 ─────────────────────────────────────────────────

    /** Le contrôleur et le vrai filtre de session, qui partagent la liste des sessions révoquées. */
    private org.springframework.test.web.servlet.MockMvc avecFiltre(SessionsRevoquees revoquees) {
        DepotQuestionsSecretes questions = new DepotQuestionsSecretes(jdbc, HORLOGE);
        RegistrePolitiqueAcces acces = new RegistrePolitiqueAcces();
        ControleurAuth c = new ControleurAuth(comptes, questions, new DepotRolesEtablissement(jdbc, acces, HORLOGE), new DepotCatalogueQuestions(jdbc, HORLOGE),
                new DepotAudit(jdbc, new JournalAudit(jdbc), HORLOGE), new DepotEtablissementsConnexion(jdbc), new DepotQuotas(jdbc), new JournalAudit(jdbc),
                new GardeAcces(acces), HORLOGE, pareFeu, new ServiceConnexion(comptes, questions, jetons, HORLOGE), false, HoteEtablissement.selecteur(),
                new DepotEmprunts(jdbc, HORLOGE, comptes, new JournalAudit(jdbc), jetons), RestrictionPaysConnexion.AUCUNE, DroitDApprouver.PERSONNE, revoquees);
        return MockMvcBuilders.standaloneSetup(c).addFilters(new FiltreSession(SECRET, HoteEtablissement.selecteur(), SessionsEmpruntees.AUCUNE, revoquees)).build();
    }

    private static String jetonDe(MvcResult r) throws Exception {
        Matcher m = JETON.matcher(r.getResponse().getContentAsString());
        assertThat(m.find()).isTrue();
        return m.group(1);
    }

    @Test
    @DisplayName("PT-02 : après la déconnexion, le jeton ne vaut plus, ni en Bearer ni en témoin ; une nouvelle connexion, si")
    void laDeconnexionRevoqueLeJeton() throws Exception {
        var mvc = avecFiltre(new DepotSessionsRevoquees(jdbc, HORLOGE));
        String jeton = jetonDe(login("admin", "admin", null));
        mvc.perform(MockMvcRequestBuilders.get("/api/auth/me").header("Authorization", "Bearer " + jeton)).andExpect(status().isOk());
        mvc.perform(MockMvcRequestBuilders.post("/api/auth/logout").header("Authorization", "Bearer " + jeton)).andExpect(status().isOk());
        mvc.perform(MockMvcRequestBuilders.get("/api/auth/me").header("Authorization", "Bearer " + jeton)).andExpect(status().isUnauthorized());
        mvc.perform(MockMvcRequestBuilders.get("/api/auth/me").cookie(new Cookie("session", jeton))).andExpect(status().isUnauthorized());
        assertThat(jdbc.sql("SELECT COUNT(*) FROM auth_sessions_revoquees").query(Integer.class).single()).isEqualTo(1);
        String nouveau = jetonDe(login("admin", "admin", null));
        mvc.perform(MockMvcRequestBuilders.get("/api/auth/me").header("Authorization", "Bearer " + nouveau)).andExpect(status().isOk());
    }

    @Test
    @DisplayName("PT-02 : un jeton émis avant l'identifiant (sans jti) reste lu, et sa déconnexion n'inscrit rien")
    void unJetonSansIdentifiant() throws Exception {
        var mvc = avecFiltre(new DepotSessionsRevoquees(jdbc, HORLOGE));
        Compte admin = comptes.parNom("admin", null).orElseThrow();
        String ancien = io.jsonwebtoken.Jwts.builder().subject(admin.id()).claim("username", "admin").claim("role", "admin")
                .expiration(new java.util.Date(System.currentTimeMillis() + 60_000))
                .signWith(new javax.crypto.spec.SecretKeySpec(SECRET.getBytes(java.nio.charset.StandardCharsets.UTF_8), "HmacSHA256")).compact();
        mvc.perform(MockMvcRequestBuilders.get("/api/auth/me").header("Authorization", "Bearer " + ancien)).andExpect(status().isOk());
        mvc.perform(MockMvcRequestBuilders.post("/api/auth/logout").header("Authorization", "Bearer " + ancien)).andExpect(status().isOk());
        assertThat(jdbc.sql("SELECT COUNT(*) FROM auth_sessions_revoquees").query(Integer.class).single()).isZero();
    }

    @Test
    @DisplayName("PT-06 : un superadmin qui choisit un établissement en en-tête y crée le compte ; sans choix, le compte est global")
    void leSuperadminCreeDansLEtablissementChoisi() throws Exception {
        http.envoyer(MockMvcRequestBuilders.post("/api/auth/users").header("X-Tenant-Id", "T1").contentType("application/json").content(
                "{\"username\":\"chef-t1\",\"password\":\"p4ssword-chef\",\"role\":\"admin\"}"), ClientHttpEssai.superadmin()).andExpect(status().isCreated())
                .andExpect(jsonPath("$.tenantId").value("T1"));
        http.envoyer(MockMvcRequestBuilders.post("/api/auth/users").header("X-Tenant-Id", "T1").contentType("application/json").content(
                "{\"username\":\"chef-t3\",\"password\":\"p4ssword-chef\",\"role\":\"admin\",\"tenantId\":\"T3\"}"), ClientHttpEssai.superadmin())
                .andExpect(jsonPath("$.tenantId").value("T3"));
        http.post("/api/auth/users", ClientHttpEssai.superadmin(), "{\"username\":\"global\",\"password\":\"p4ssword-glob\",\"role\":\"admin\"}")
                .andExpect(status().isCreated()).andExpect(jsonPath("$.tenantId").value(org.hamcrest.Matchers.nullValue()));
    }
}
