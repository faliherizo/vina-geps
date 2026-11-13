package ca.vina.nexu.auth.infrastructure.web;

import ca.vina.nexu.auth.infrastructure.persistance.DepotComptes;
import ca.vina.nexu.auth.infrastructure.persistance.DepotEmprunts;

import ca.vina.nexu.ClientHttpEssai;
import ca.vina.nexu.SchemaEssai;
import ca.vina.nexu.acces.infrastructure.persistance.RegistrePolitiqueAcces;
import ca.vina.nexu.partage.audit.infrastructure.JournalAudit;
import ca.vina.nexu.partage.securite.ContexteRequete;
import ca.vina.nexu.partage.securite.FiltreSession;
import ca.vina.nexu.partage.securite.HoteEtablissement;
import ca.vina.nexu.partage.securite.JetonDeSession;
import ca.vina.nexu.partage.web.GardeAcces;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MvcResult;

import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * L'emprunt d'identité de bout en bout (CSSMB 25 ; ADR 0038), port de `auth-emprunt.integration.test.ts` :
 * fermé par défaut, ouvert par l'organisme ; l'administration agit comme un compte inférieur du même
 * organisme pour une heure ; l'audit porte les deux identités ; le filtre de session refuse les secrets
 * de la cible et une session terminée ou expirée ; terminer rend la session de l'administrateur.
 */
@DisplayName("API auth — emprunt d'identité : réglage, début, filtre, audit, fin")
class ControleurEmpruntHttpTest {

    private static final String SECRET = "un-secret-assez-long-pour-les-essais-1234567890";
    private static final ContexteRequete ADMIN = new ContexteRequete("u-admin", "admin-t1", "admin", null, "t-1");
    private static final ContexteRequete RH = new ContexteRequete("u-rh", "rh-t1", "rh", null, "t-1");
    private static final ContexteRequete GLOBAL = new ContexteRequete("u-sa", "sa", "superadmin", null, null);

    private final AtomicReference<Instant> instant = new AtomicReference<>(Instant.parse("2026-09-17T10:00:00Z"));
    private JdbcClient jdbc;
    private ClientHttpEssai http;
    private DepotEmprunts emprunts;
    private JetonDeSession jetons;

    @BeforeEach
    void preparer(@TempDir Path dossier) {
        jdbc = SchemaEssai.base(dossier);
        Clock horloge = new Clock() {
            @Override
            public ZoneOffset getZone() {
                return ZoneOffset.UTC;
            }

            @Override
            public Clock withZone(java.time.ZoneId zone) {
                return this;
            }

            @Override
            public Instant instant() {
                return instant.get();
            }
        };
        // La cible « u-emp » garde ses portes de première connexion : sous emprunt, elles ne doivent pas s'ouvrir.
        jdbc.sql("INSERT INTO users (id, username, loginName, tenantId, passwordHash, role, employeeId, createdAt, active, mustChangePassword, securitySetupRequired) VALUES "
                + "('u-admin','admin-t1@@t-1','admin-t1','t-1','x','admin',NULL,'x',1,0,0), ('u-rh','rh-t1@@t-1','rh-t1','t-1','x','rh',NULL,'x',1,0,0), "
                + "('u-emp','emp-t1@@t-1','emp-t1','t-1','x','employee','emp-1','x',1,1,1), ('u-off','off@@t-1','off','t-1','x','employee',NULL,'x',0,0,0), ('u-t2','emp-t2@@t-2','emp-t2','t-2','x','employee',NULL,'x',1,0,0)").update();
        DepotComptes comptes = new DepotComptes(jdbc, horloge);
        jetons = new JetonDeSession(SECRET);
        emprunts = new DepotEmprunts(jdbc, horloge, comptes, new JournalAudit(jdbc), jetons);
        http = new ClientHttpEssai(new ControleurEmprunt(emprunts, new GardeAcces(new RegistrePolitiqueAcces()), false));
    }

    private static String champ(MvcResult r, String nom) throws Exception {
        return r.getResponse().getContentAsString().replaceAll("^.*?\"" + nom + "\":\"([^\"]+)\".*$", "$1");
    }

    /** Le filtre de session, avec le dépôt des emprunts : ce qu'une requête portant ce jeton obtient avant tout contrôleur. */
    private MockHttpServletResponse filtrer(String jeton, String methode, String chemin) throws Exception {
        MockHttpServletRequest r = new MockHttpServletRequest(methode, chemin);
        r.setRequestURI(chemin);
        r.addHeader("Authorization", "Bearer " + jeton);
        MockHttpServletResponse reponse = new MockHttpServletResponse();
        new FiltreSession(SECRET, HoteEtablissement.selecteur(), emprunts).doFilter(r, reponse, new MockFilterChain());
        return reponse;
    }

    @Test
    void reglageDebutFiltreAuditEtFin() throws Exception {
        // Le réglage : fermé, seul l'administrateur le voit et l'ouvre ; un corps fautif est nommé.
        http.get("/api/auth/emprunt/reglage", null).andExpect(status().isUnauthorized());
        http.get("/api/auth/emprunt/reglage", RH).andExpect(status().isForbidden());
        http.get("/api/auth/emprunt/reglage", ADMIN).andExpect(status().isOk()).andExpect(jsonPath("$.empruntIdentite").value(false)).andExpect(jsonPath("$.modifieLe").isEmpty());
        http.post("/api/auth/emprunt", ADMIN, "{\"cibleId\":\"u-emp\",\"motif\":\"reproduire le problème\"}").andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errors[0]").value("l'emprunt d'identité n'est pas autorisé pour cet organisme : ouvrez-le dans Administration › Comptes"));
        http.put("/api/auth/emprunt/reglage", ADMIN, "{\"actif\":\"oui\"}").andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors[0]").value("actif : vrai ou faux"));
        http.put("/api/auth/emprunt/reglage", ADMIN, null).andExpect(status().isBadRequest());
        http.put("/api/auth/emprunt/reglage", ADMIN, "{\"actif\":true}").andExpect(status().isOk()).andExpect(jsonPath("$.empruntIdentite").value(true)).andExpect(jsonPath("$.modifiePar").value("u-admin"))
                .andExpect(jsonPath("$.modifieLe").value("2026-09-17T10:00:00.000Z"));
        // Qui emprunte qui : chaque refus nommé.
        http.post("/api/auth/emprunt", RH, "{\"cibleId\":\"u-emp\",\"motif\":\"curiosité\"}").andExpect(status().isForbidden());
        http.post("/api/auth/emprunt", ADMIN, "{\"cibleId\":\"u-admin\",\"motif\":\"moi-même\"}").andExpect(status().isForbidden()).andExpect(jsonPath("$.errors[0]").value("on n'emprunte pas sa propre identité"));
        http.post("/api/auth/emprunt", ADMIN, "{\"cibleId\":\"u-t2\",\"motif\":\"hors organisme\"}").andExpect(status().isForbidden()).andExpect(jsonPath("$.errors[0]").value("compte hors de votre organisme"));
        http.post("/api/auth/emprunt", ADMIN, "{\"cibleId\":\"u-off\",\"motif\":\"compte fermé\"}").andExpect(status().isForbidden()).andExpect(jsonPath("$.errors[0]").value("compte désactivé"));
        http.post("/api/auth/emprunt", ADMIN, "{\"cibleId\":\"fantome\",\"motif\":\"introuvable\"}").andExpect(status().isNotFound());
        http.post("/api/auth/emprunt", ADMIN, "{\"cibleId\":\"u-emp\",\"motif\":\"x\"}").andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors[0]").value("motif requis (cinq caractères au moins) : il est écrit au journal"));
        http.post("/api/auth/emprunt", ADMIN, "{\"motif\":\"sans cible\"}").andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors[0]").value("cibleId requis"));
        http.post("/api/auth/emprunt", ADMIN, null).andExpect(status().isBadRequest());
        // L'emprunt : une session d'une heure, au nom de la cible, cookies renouvelés ; le journal le montre.
        MvcResult debut = http.post("/api/auth/emprunt", ADMIN, "{\"cibleId\":\"u-emp\",\"motif\":\" reproduire le problème de saisie \"}").andExpect(status().isOk())
                .andExpect(jsonPath("$.user.id").value("u-emp")).andExpect(jsonPath("$.user.mustChangePassword").value(false)).andExpect(jsonPath("$.user.securitySetupRequired").value(false))
                .andExpect(jsonPath("$.emprunt.emprunteurId").value("u-admin")).andExpect(jsonPath("$.emprunt.motif").value("reproduire le problème de saisie"))
                .andExpect(jsonPath("$.emprunt.expireLe").value("2026-09-17T11:00:00.000Z")).andExpect(jsonPath("$.emprunt.termineLe").isEmpty()).andExpect(jsonPath("$.csrf").isNotEmpty())
                .andExpect(header().stringValues("Set-Cookie", org.hamcrest.Matchers.hasItems(org.hamcrest.Matchers.startsWith("session="), org.hamcrest.Matchers.startsWith("csrf=")))).andReturn();
        String jetonEmprunte = champ(debut, "token");
        JetonDeSession.Revendications r = jetons.lire(jetonEmprunte);
        assertThat(r.sub()).isEqualTo("u-emp");
        assertThat(r.emprunteur()).isEqualTo("u-admin");
        assertThat(r.empruntId()).isNotBlank();
        ContexteRequete empruntee = new ContexteRequete("u-emp", "emp-t1", "employee", "emp-1", "t-1", "u-admin", r.empruntId());
        assertThat(emprunts.empruntDe(empruntee)).containsEntry("expireLe", "2026-09-17T11:00:00.000Z").containsEntry("emprunteur", Map.of("id", "u-admin", "username", "admin-t1"));
        assertThat(emprunts.empruntDe(ADMIN)).isNull();
        http.get("/api/auth/emprunt/journal", ADMIN).andExpect(status().isOk()).andExpect(jsonPath("$[0].emprunteur").value("admin-t1")).andExpect(jsonPath("$[0].cible").value("emp-t1")).andExpect(jsonPath("$[0].actif").value(true));
        // Le filtre : la session empruntée passe, sauf sur les secrets de la cible et un second emprunt ; l'audit nomme l'acteur réel.
        assertThat(filtrer(jetonEmprunte, "GET", "/api/auth/security/status").getStatus()).isEqualTo(200);
        assertThat(filtrer(jetonEmprunte, "POST", "/api/auth/change-password").getStatus()).isEqualTo(403);
        assertThat(filtrer(jetonEmprunte, "POST", "/api/auth/emprunt").getContentAsString()).contains("emprunt : interdit");
        assertThat(jdbc.sql("SELECT action || '|' || COALESCE(acteurReel, '-') FROM audit_log WHERE action LIKE 'auth.emprunt.%' ORDER BY rowid").query(String.class).list())
                .containsExactly("auth.emprunt.autorise|-", "auth.emprunt.debut|u-admin");
        // Une session empruntée n'emprunte pas à son tour, même par le contrôleur.
        http.post("/api/auth/emprunt", empruntee, "{\"cibleId\":\"u-rh\",\"motif\":\"encore un emprunt\"}").andExpect(status().isForbidden());
        // La fin : la session de l'administrateur revient ; le jeton emprunté ne vaut plus ; l'audit le dit.
        http.post("/api/auth/emprunt/terminer", null, null).andExpect(status().isUnauthorized());
        http.post("/api/auth/emprunt/terminer", ADMIN, null).andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors[0]").value("cette session n'est pas empruntée"));
        MvcResult fin = http.post("/api/auth/emprunt/terminer", empruntee, null).andExpect(status().isOk()).andExpect(jsonPath("$.user.id").value("u-admin")).andReturn();
        assertThat(jetons.lire(champ(fin, "token")).emprunteur()).isNull();
        MockHttpServletResponse apres = filtrer(jetonEmprunte, "GET", "/api/auth/me");
        assertThat(apres.getStatus()).isEqualTo(401);
        assertThat(apres.getContentAsString()).contains("emprunt terminé : reconnectez-vous");
        http.get("/api/auth/emprunt/journal", ADMIN).andExpect(status().isOk()).andExpect(jsonPath("$[0].actif").value(false)).andExpect(jsonPath("$[0].termineLe").value("2026-09-17T10:00:00.000Z"));
        assertThat(jdbc.sql("SELECT COUNT(*) FROM audit_log WHERE action = 'auth.emprunt.fin' AND acteurReel = 'u-admin'").query(Integer.class).single()).isEqualTo(1);
        // L'expiration : passée l'heure, la session empruntée ne vaut plus non plus.
        String second = champ(http.post("/api/auth/emprunt", ADMIN, "{\"cibleId\":\"u-emp\",\"motif\":\"encore une vérification\"}").andExpect(status().isOk()).andReturn(), "token");
        instant.set(Instant.parse("2026-09-17T11:01:00Z"));
        assertThat(filtrer(second, "GET", "/api/auth/me").getStatus()).isEqualTo(401);
        // Refermer : plus d'emprunt ; un compte global emprunte sous son propre réglage — fermé.
        http.put("/api/auth/emprunt/reglage", ADMIN, "{\"actif\":false}").andExpect(status().isOk()).andExpect(jsonPath("$.empruntIdentite").value(false));
        http.post("/api/auth/emprunt", ADMIN, "{\"cibleId\":\"u-emp\",\"motif\":\"après fermeture\"}").andExpect(status().isForbidden());
        http.post("/api/auth/emprunt", GLOBAL, "{\"cibleId\":\"u-t2\",\"motif\":\"soutien de niveau 2\"}").andExpect(status().isForbidden());
        http.get("/api/auth/emprunt/journal", GLOBAL).andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(0));
        assertThat(emprunts.active(null)).isFalse();
    }
}
