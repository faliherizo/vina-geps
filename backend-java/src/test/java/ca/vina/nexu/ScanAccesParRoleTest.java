package ca.vina.nexu;

import ca.vina.nexu.auth.infrastructure.persistance.DepotComptes;
import ca.vina.nexu.partage.securite.JetonDeSession;
import jakarta.servlet.Filter;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.annotation.AnnotationAwareOrderComparator;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.SimpleDriverDataSource;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * <strong>Le balayage des accès</strong> : chaque route du serveur, appelée par un visiteur anonyme et par chaque rôle,
 * à travers les vrais filtres (session, CSRF, pare-feu). Les routes viennent du registre de Spring lui-même : aucune ne
 * peut être oubliée d'une liste tenue à la main.
 *
 * <p>La matrice complète est écrite dans {@code target/scan-acces/acces-par-role.md}. Deux invariants y sont tenus :
 * <ol>
 *   <li>aucune route ne répond 2xx à un visiteur anonyme, hors une liste <strong>justifiée route par route</strong> ;</li>
 *   <li>aucune route d'administration ne répond 2xx à un compte qui n'administre pas.</li>
 * </ol>
 * Seules les lectures ({@code GET}) sont jouées sous un rôle : un balayage ne doit rien écrire. Les écritures sont jouées
 * sans session, où elles doivent toutes être refusées avant de rien toucher.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@TestPropertySource(properties = {
        "spring.liquibase.enabled=false",
        "nexu.jwt.secret=" + ScanAccesParRoleTest.SECRET,
        "nexu.migration.exposer-routes=false",
})
@DisplayName("Balayage des accès — chaque route, chaque rôle, à travers les vrais filtres")
class ScanAccesParRoleTest {

    static final String SECRET = "secret-d-essai-du-balayage-des-acces-32-octets";
    private static final String TENANT = "t-scan";
    private static final Path BASE = Path.of(System.getProperty("java.io.tmpdir"),
            "nexu-scan-acces-" + ProcessHandle.current().pid() + "-" + System.nanoTime() + ".db");
    private static final Path RAPPORT = Path.of("target", "scan-acces", "acces-par-role.md");
    private static final List<String> ROLES = List.of("employee", "manager", "director", "rh", "accountant", "admin", "superadmin");
    private static final Pattern VARIABLE = Pattern.compile("\\{[^}]+}");

    /**
     * Ce qu'un visiteur anonyme peut lire, et pourquoi. Toute autre route qui lui répond 2xx est une fuite.
     * Une entrée ajoutée ici se justifie par sa raison, comme les autres.
     */
    static final Map<String, String> PUBLIQUES = Map.ofEntries(
            Map.entry("GET /api/health", "sonde de disponibilité du répartiteur"),
            Map.entry("GET /api/countries", "liste des pays de la page de connexion"),
            Map.entry("GET /api/auth/tenants", "sélecteur d'établissement de la page de connexion (actifs ou à l'essai seulement)"),
            Map.entry("GET /api/auth/deploiement", "mode de déploiement (sélecteur ou sous-domaine) lu par la page de connexion"),
            Map.entry("GET /api/auth/password-policy", "politique de mot de passe affichée à l'activation d'un compte"),
            Map.entry("POST /api/auth/password-policy/check", "vérification d'un mot de passe saisi à l'activation, sans rien enregistrer"),
            Map.entry("POST /api/auth/logout", "la déconnexion répond toujours ; sans session, elle n'a rien à fermer"),
            Map.entry("GET /api/auth/sso/annuaires", "boutons de connexion fédérée de l'établissement : identifiant, nom, fournisseur"),
            Map.entry("GET /api/auth/sso/entra/status", "la page de connexion sait si Entra ID est offert"),
            Map.entry("GET /api/auth/sso/saml/status", "la page de connexion sait si SAML est offert"),
            Map.entry("GET /api/auth/sso/saml/metadata", "métadonnées SAML publiques par nature, lues par le fournisseur d'identité"),
            Map.entry("GET /api/saas/maintenance", "bandeau de maintenance affiché avant connexion"),
            Map.entry("GET /api/saas/plans", "offre publique"),
            Map.entry("GET /api/soutien/politique", "heures et canaux du soutien, affichés avant connexion"),
            Map.entry("GET /api/integrations/swagger", "coquille HTML de Swagger UI ; la spécification qu'elle charge exige une session"));

    /**
     * Les préfixes d'administration : un compte qui n'administre pas n'y obtient rien. La liste suit la politique des
     * capacités ({@code politique-acces.json}) et le menu (entrées réservées à l'administration).
     */
    static final List<String> ADMINISTRATION = List.of("/api/effectifs/referentiels", "/api/habilitations/octrois", "/api/habilitations/objets",
            "/api/auth/users", "/api/auth/audit", "/api/auth/liaisons", "/api/exploitation/copies-organisme");

    /** Les routes de la plateforme : aucun rôle d'un établissement n'y obtient rien, l'administrateur compris. */
    static final List<String> PLATEFORME = List.of("/api/superadmin/", "/api/saas/tenants");

    @Autowired
    private WebApplicationContext contexte;

    @DynamicPropertySource
    static void base(DynamicPropertyRegistry proprietes) {
        proprietes.add("spring.datasource.url", () -> "jdbc:sqlite:" + BASE);
    }

    @BeforeAll
    static void preparerLaBase() throws IOException {
        Files.deleteIfExists(BASE);
        BASE.toFile().deleteOnExit();
        SchemaEssai.base(new SimpleDriverDataSource(new org.sqlite.JDBC(), "jdbc:sqlite:" + BASE));
    }

    record Route(String methode, String chemin) {
        String appel() {
            return VARIABLE.matcher(chemin).replaceAll("x");
        }
    }

    private List<Route> routes() {
        RequestMappingHandlerMapping mapping = contexte.getBean("requestMappingHandlerMapping", RequestMappingHandlerMapping.class);
        Set<String> vues = new TreeSet<>();
        List<Route> routes = new ArrayList<>();
        for (RequestMappingInfo info : mapping.getHandlerMethods().keySet()) {
            Set<RequestMethod> methodes = info.getMethodsCondition().getMethods();
            for (String chemin : info.getPatternValues()) {
                for (RequestMethod m : methodes.isEmpty() ? Set.of(RequestMethod.GET) : methodes) {
                    if (chemin.startsWith("/api/") && vues.add(m + " " + chemin)) {
                        routes.add(new Route(m.name(), chemin));
                    }
                }
            }
        }
        routes.sort((a, b) -> (a.chemin() + a.methode()).compareTo(b.chemin() + b.methode()));
        return routes;
    }

    private MockMvc mvc() {
        List<Filter> filtres = new ArrayList<>(contexte.getBeansOfType(Filter.class).values());
        AnnotationAwareOrderComparator.sort(filtres);
        return MockMvcBuilders.webAppContextSetup(contexte).addFilters(filtres.toArray(Filter[]::new)).build();
    }

    private Map<String, String> jetons() {
        JdbcClient jdbc = contexte.getBean(JdbcClient.class);
        jdbc.sql("INSERT OR IGNORE INTO tenants (id, name, slug, country, status, createdAt) VALUES (?, 'Balayage', 'balayage', 'CA', 'active', 'x')").params(TENANT).update();
        jdbc.sql("INSERT OR IGNORE INTO tenant_quotas (tenantId, maxTeachers, maxContractsPerYear, modules) VALUES (?, -1, -1, ?)").params(TENANT, MODULES).update();
        DepotComptes comptes = contexte.getBean(DepotComptes.class);
        JetonDeSession emetteur = new JetonDeSession(SECRET);
        Map<String, String> jetons = new LinkedHashMap<>();
        for (String role : ROLES) {
            String nom = "scan-" + role;
            String tenant = "superadmin".equals(role) ? null : TENANT;
            var compte = comptes.parNom(nom, tenant).orElseGet(() -> comptes.creer(new DepotComptes.Creation(nom, "Balayage-des-acces-2026", role, null, tenant, false, false, null, false)));
            jetons.put(role, emetteur.emettre(new JetonDeSession.Revendications(compte.id(), nom, role, null, tenant)));
        }
        return jetons;
    }

    private static final String MODULES = "[\"personnel\",\"effectifs\",\"effectifs_planification\",\"effectifs_regles\",\"effectifs_agph\","
            + "\"effectifs_absences\",\"effectifs_echanges\",\"effectifs_disponibilites\",\"effectifs_presence\",\"effectifs_temps\","
            + "\"effectifs_integrations\",\"effectifs_communication\",\"alertes\",\"approvals\",\"notifications\",\"integrations\","
            + "\"confidentialite\",\"paie\"]";

    private static int statut(MockMvc mvc, Route r, String jeton) {
        MockHttpServletRequestBuilder b = MockMvcRequestBuilders.request(org.springframework.http.HttpMethod.valueOf(r.methode()), r.appel());
        if (jeton != null) {
            b.header("Authorization", "Bearer " + jeton).header("X-Tenant-Id", TENANT);
        }
        try {
            return mvc.perform(b).andReturn().getResponse().getStatus();
        } catch (Exception e) {
            return 500;
        }
    }

    @Test
    void chaqueRouteChaqueRole() throws Exception {
        MockMvc mvc = mvc();
        Map<String, String> jetons = jetons();
        List<Route> routes = routes();
        StringBuilder md = new StringBuilder("# Accès par rôle — balayage de toutes les routes\n\n| Méthode | Route | anonyme | ")
                .append(String.join(" | ", ROLES)).append(" |\n|---|---|---|").append("---|".repeat(ROLES.size())).append('\n');
        List<String> anonymesOuverts = new ArrayList<>();
        Map<String, List<String>> ouvertsParRole = new LinkedHashMap<>();
        for (Route r : routes) {
            int anonyme = statut(mvc, r, null);
            md.append("| ").append(r.methode()).append(" | `").append(r.chemin()).append("` | ").append(anonyme);
            if (anonyme < 300) {
                anonymesOuverts.add(r.methode() + " " + r.chemin());
            }
            for (String role : ROLES) {
                int s = "GET".equals(r.methode()) ? statut(mvc, r, jetons.get(role)) : -1;
                md.append(" | ").append(s < 0 ? "·" : String.valueOf(s));
                if (s >= 200 && s < 300) {
                    ouvertsParRole.computeIfAbsent(role, k -> new ArrayList<>()).add(r.chemin());
                }
            }
            md.append(" |\n");
        }
        md.append("\n## Ouvertes sans session\n\n").append(String.join("\n", anonymesOuverts.stream().map(x -> "- `" + x + "`").toList())).append('\n');
        Files.createDirectories(RAPPORT.getParent());
        Files.writeString(RAPPORT, md);
        Files.writeString(RAPPORT.resolveSibling("ouvertes-par-role.txt"), ouvertsParRole.entrySet().stream()
                .map(e -> "## " + e.getKey() + "\n" + String.join("\n", e.getValue())).reduce("", (a, b) -> a + b + "\n\n"));
        Files.writeString(RAPPORT.resolveSibling("anonymes.txt"), String.join("\n", anonymesOuverts));
        assertThat(routes).hasSizeGreaterThan(100);
        assertThat(anonymesOuverts).as("routes ouvertes sans session hors de la liste justifiée — rapport : %s", RAPPORT)
                .allMatch(PUBLIQUES::containsKey);
        assertThat(PUBLIQUES.keySet()).as("une route publique retirée ou fermée sort aussi de la liste").allMatch(anonymesOuverts::contains);
        for (String role : ROLES.subList(0, ROLES.indexOf("admin"))) {
            assertThat(ouvertsParRole.getOrDefault(role, List.of())).as("routes d'administration ouvertes au rôle %s", role)
                    .doesNotContainAnyElementsOf(ADMINISTRATION);
        }
        assertThat(ouvertsParRole.get("admin")).as("l'administrateur garde ses écrans").containsAll(ADMINISTRATION);
        for (String role : ROLES.subList(0, ROLES.indexOf("superadmin"))) {
            assertThat(ouvertsParRole.getOrDefault(role, List.of())).as("routes de la plateforme ouvertes au rôle %s", role)
                    .noneMatch(chemin -> PLATEFORME.stream().anyMatch(chemin::startsWith));
        }
        assertThat(md.toString()).as("aucune route ne répond 500 — rapport : %s", RAPPORT).doesNotContain("| 500");
    }
}
