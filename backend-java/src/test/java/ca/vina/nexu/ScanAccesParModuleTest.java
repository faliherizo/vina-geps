package ca.vina.nexu;

import ca.vina.nexu.auth.infrastructure.persistance.DepotComptes;
import ca.vina.nexu.partage.securite.JetonDeSession;
import ca.vina.nexu.saas.domaine.CatalogueModules;
import jakarta.servlet.Filter;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.annotation.AnnotationAwareOrderComparator;
import org.springframework.http.HttpMethod;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.SimpleDriverDataSource;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
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
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * <strong>Le balayage des accès par module</strong> : un organisme à qui <em>aucun</em> module n'est accordé, et son
 * administrateur — le rôle qui a toutes les capacités — appelle chaque route que le catalogue attribue à un module.
 * Toutes doivent répondre 403.
 *
 * <p>Pourquoi : masquer une vue au menu n'est pas une permission (FX-AO-20260810, ST-H05). Ce qui ferme une fonctionnalité
 * à un organisme, c'est le module, vérifié par le serveur. Les tests d'intrusion FX-AO l'ont montré (IN-FX-02) : les clés
 * d'API n'exigeaient que le rôle, et l'organisme FX-AO, privé du module {@code integrations}, pouvait en émettre.
 *
 * <p>La règle vient du catalogue lui-même ({@link CatalogueModules#MODULES}, « routes /api/… » de chaque module) et les
 * routes du registre de Spring : ni l'une ni l'autre n'est une liste tenue à la main. Les écritures sont jouées elles
 * aussi : une garde de module refuse avant de lire le corps, et la base est jetable. Matrice :
 * {@code target/scan-acces/acces-par-module.md}.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@TestPropertySource(properties = {
        "spring.liquibase.enabled=false",
        "nexu.jwt.secret=" + ScanAccesParModuleTest.SECRET,
        "nexu.migration.exposer-routes=false",
})
@DisplayName("Balayage des accès par module — un organisme sans module n'atteint aucune route de module")
class ScanAccesParModuleTest {

    static final String SECRET = "secret-d-essai-du-balayage-par-module-32-oct";
    private static final String TENANT = "t-sans-module";
    private static final int REFUS = 403;
    private static final Path BASE = Path.of(System.getProperty("java.io.tmpdir"),
            "nexu-scan-module-" + ProcessHandle.current().pid() + "-" + System.nanoTime() + ".db");
    private static final Path RAPPORT = Path.of("target", "scan-acces", "acces-par-module.md");
    private static final Pattern ROUTE_DECLAREE = Pattern.compile("/api/[a-z0-9/_-]+");
    private static final Pattern VARIABLE = Pattern.compile("\\{[^}]+}");

    /**
     * Les routes d'un préfixe de module qui restent ouvertes sans le module, et pourquoi. Toute autre route qui ne répond
     * pas 403 est une fonctionnalité servie à un organisme qui ne l'a pas.
     */
    static final Map<String, String> HORS_MODULE = Map.of(
            "GET /api/integrations/swagger", "coquille HTML publique de Swagger UI, sans données (ScanAccesParRoleTest.PUBLIQUES)",
            "GET /api/integrations/openapi.json", "description de la plateforme, identique pour tous les organismes, sans donnée d'organisme");

    /**
     * Les routes refusées <strong>avant</strong> la garde de module, par Spring lui-même, et pourquoi : elles ne répondent
     * pas 403, mais n'atteignent ni la garde ni les données. Tout autre statut que 403 reste un défaut.
     */
    static final Map<String, String> REFUSEES_AVANT_LA_GARDE = Map.of(
            "GET /api/effectifs/offres", "paramètre absenceId obligatoire : 400 de Spring avant l'appel de la méthode");

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

    /** Les préfixes de routes de chaque module, lus dans sa déclaration au catalogue. */
    static Map<String, List<String>> prefixesParModule() {
        Map<String, List<String>> prefixes = new TreeMap<>();
        for (CatalogueModules.Definition m : CatalogueModules.MODULES) {
            Matcher r = ROUTE_DECLAREE.matcher(m.debloque());
            List<String> liste = new ArrayList<>();
            while (r.find()) {
                liste.add(r.group());
            }
            if (!liste.isEmpty()) {
                prefixes.put(m.code(), liste);
            }
        }
        return prefixes;
    }

    private List<String> routesDeModule(Map<String, List<String>> prefixes) {
        RequestMappingHandlerMapping mapping = contexte.getBean("requestMappingHandlerMapping", RequestMappingHandlerMapping.class);
        List<String> tous = prefixes.values().stream().flatMap(List::stream).toList();
        Set<String> routes = new TreeSet<>();
        for (RequestMappingInfo info : mapping.getHandlerMethods().keySet()) {
            Set<RequestMethod> methodes = info.getMethodsCondition().getMethods();
            for (String chemin : info.getPatternValues()) {
                if (tous.stream().anyMatch(p -> chemin.equals(p) || chemin.startsWith(p + "/"))) {
                    for (RequestMethod m : methodes.isEmpty() ? Set.of(RequestMethod.GET) : methodes) {
                        routes.add(m + " " + chemin);
                    }
                }
            }
        }
        return new ArrayList<>(routes);
    }

    private String jetonAdministrateur() {
        JdbcClient jdbc = contexte.getBean(JdbcClient.class);
        jdbc.sql("INSERT OR IGNORE INTO tenants (id, name, slug, country, status, createdAt) VALUES (?, 'Sans module', 'sans-module', 'CA', 'active', 'x')")
                .params(TENANT).update();
        jdbc.sql("INSERT OR IGNORE INTO tenant_quotas (tenantId, maxTeachers, maxContractsPerYear, modules) VALUES (?, -1, -1, '[]')").params(TENANT).update();
        DepotComptes comptes = contexte.getBean(DepotComptes.class);
        var compte = comptes.parNom("scan-admin", TENANT).orElseGet(() -> comptes.creer(
                new DepotComptes.Creation("scan-admin", "Balayage-par-module-2026", "admin", null, TENANT, false, false, null, false)));
        return new JetonDeSession(SECRET).emettre(new JetonDeSession.Revendications(compte.id(), "scan-admin", "admin", null, TENANT));
    }

    private MockMvc mvc() {
        List<Filter> filtres = new ArrayList<>(contexte.getBeansOfType(Filter.class).values());
        AnnotationAwareOrderComparator.sort(filtres);
        return MockMvcBuilders.webAppContextSetup(contexte).addFilters(filtres.toArray(Filter[]::new)).build();
    }

    private static int statut(MockMvc mvc, String route, String jeton) {
        String[] parties = route.split(" ", 2);
        var requete = MockMvcRequestBuilders.request(HttpMethod.valueOf(parties[0]), VARIABLE.matcher(parties[1]).replaceAll("x"))
                .header("Authorization", "Bearer " + jeton).header("X-Tenant-Id", TENANT)
                .contentType("application/json").content("{}");
        try {
            return mvc.perform(requete).andReturn().getResponse().getStatus();
        } catch (Exception e) {
            return 500;
        }
    }

    @Test
    void aucuneRouteDeModuleSansLeModule() throws Exception {
        MockMvc mvc = mvc();
        String jeton = jetonAdministrateur();
        Map<String, List<String>> prefixes = prefixesParModule();
        List<String> routes = routesDeModule(prefixes);
        StringBuilder md = new StringBuilder("# Accès par module — organisme sans aucun module, rôle admin\n\n| Route | Statut |\n|---|---|\n");
        List<String> ouvertes = new ArrayList<>();
        for (String route : routes) {
            int s = statut(mvc, route, jeton);
            md.append("| `").append(route).append("` | ").append(s).append(" |\n");
            if (s != REFUS && !HORS_MODULE.containsKey(route) && !REFUSEES_AVANT_LA_GARDE.containsKey(route)) {
                ouvertes.add(route + " → " + s);
            }
        }
        Files.createDirectories(RAPPORT.getParent());
        Files.writeString(RAPPORT, md);
        assertThat(prefixes).as("le catalogue déclare les routes des modules").containsKeys("alertes", "paie", "effectifs", "integrations");
        assertThat(routes).as("routes de module trouvées au registre").hasSizeGreaterThan(100);
        assertThat(ouvertes).as("routes de module servies à un organisme sans le module — rapport : %s", RAPPORT).isEmpty();
        assertThat(HORS_MODULE.keySet()).as("une exemption sans route n'a plus lieu d'être").allMatch(routes::contains);
        assertThat(REFUSEES_AVANT_LA_GARDE.keySet()).as("une exemption sans route n'a plus lieu d'être").allMatch(routes::contains);
    }
}
