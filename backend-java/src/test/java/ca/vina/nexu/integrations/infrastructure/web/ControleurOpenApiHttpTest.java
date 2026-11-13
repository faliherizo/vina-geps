package ca.vina.nexu.integrations.infrastructure.web;

import ca.vina.nexu.ClientHttpEssai;
import ca.vina.nexu.acces.infrastructure.persistance.RegistrePolitiqueAcces;
import ca.vina.nexu.partage.identifiants.domaine.Sequence;
import ca.vina.nexu.partage.json.infrastructure.Json;
import ca.vina.nexu.partage.securite.ContexteRequete;
import ca.vina.nexu.partage.web.GardeAcces;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.support.StaticWebApplicationContext;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** La page Swagger et la spécification OpenAPI : seules les routes montées y figurent, décrites par le socle ou générées. */
@DisplayName("API intégrations — Swagger UI et OpenAPI des routes servies")
class ControleurOpenApiHttpTest {

    private static final ContexteRequete ADMIN = new ContexteRequete("u-adm", "admin", "admin", null, "t-1");

    /** Un contrôleur sans verbe : la table le dit servi pour tous ({@code *}) et la spécification l'ignore. */
    @RestController
    @RequestMapping("/api/essai/tout")
    static class ControleurSansVerbe {
        @RequestMapping
        public String tout() {
            return "ok";
        }
    }

    /** Des routes montées : une décrite par un contrat du socle, une par ses chemins, deux à générer, une interne. */
    @RestController
    static class ControleurMonte {
        @PostMapping("/api/auth/login")
        public String connexion() {
            return "ok";
        }

        @GetMapping("/api/soutien/politique")
        public String politique() {
            return "ok";
        }

        @GetMapping("/api/effectifs/postes/{id}")
        public String poste() {
            return "ok";
        }

        @PostMapping("/api/communication/conversations")
        public String conversation() {
            return "ok";
        }

        @GetMapping("/api/auth/_outbox")
        public String interne() {
            return "ok";
        }
    }

    /** Une séquence dont chaque jeton diffère : c'est tout ce que la page exige du nonce. */
    private static Sequence sequence() {
        AtomicInteger compteur = new AtomicInteger();
        return new Sequence() {
            @Override
            public String suivant(String prefixe) {
                return prefixe;
            }

            @Override
            public String uuid() {
                return "u";
            }

            @Override
            public String jetonSignature() {
                return "s";
            }

            @Override
            public String jetonBase64Url(int octets) {
                return String.format("%022d", compteur.incrementAndGet());
            }
        };
    }

    private static RequestMappingHandlerMapping correspondances(Object... controleurs) {
        StaticWebApplicationContext ctx = new StaticWebApplicationContext();
        for (Object c : controleurs) {
            ctx.getBeanFactory().registerSingleton(c.getClass().getSimpleName(), c);
        }
        ctx.refresh();
        RequestMappingHandlerMapping m = new RequestMappingHandlerMapping();
        m.setApplicationContext(ctx);
        m.afterPropertiesSet();
        return m;
    }

    private ClientHttpEssai http;

    @BeforeEach
    void preparer() {
        GardeAcces garde = new GardeAcces(new RegistrePolitiqueAcces());
        RequestMappingHandlerMapping table = correspondances(new ControleurSansVerbe(), new ControleurMonte());
        http = new ClientHttpEssai(new ControleurOpenApi(garde, new SpecificationOpenApi(table), sequence()));
    }

    @Test
    @DisplayName("la page Swagger : sans session, un nonce par réponse, la spécification de ce produit")
    void swagger() throws Exception {
        var reponse = http.get("/api/integrations/swagger", null).andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "text/html; charset=utf-8")).andReturn().getResponse();
        String csp = reponse.getHeader("Content-Security-Policy");
        Matcher m = Pattern.compile("'nonce-([0-9]{22})'").matcher(csp);
        assertThat(m.find()).isTrue();
        assertThat(reponse.getContentAsString(StandardCharsets.UTF_8)).startsWith("<!doctype html>")
                .contains("<script nonce=\"" + m.group(1) + "\">").contains("url: \"/api/integrations/openapi.json\"");
        String autre = http.get("/api/integrations/swagger", null).andReturn().getResponse().getHeader("Content-Security-Policy");
        assertThat(autre).as("un nonce par réponse").isNotEqualTo(csp);
    }

    @SuppressWarnings("unchecked")
    @Test
    @DisplayName("la spécification : sous session ; une opération par route montée, décrite si le socle la décrit, générée sinon ; rien d'autre")
    void openapi() throws Exception {
        http.get("/api/integrations/openapi.json", null).andExpect(status().isUnauthorized());
        String corps = http.get("/api/integrations/openapi.json", ADMIN).andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "application/json; charset=utf-8")).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        Map<String, Object> spec = Json.carte(corps);
        assertThat(spec).containsEntry("openapi", "3.0.3").doesNotContainKey("x-contrats").containsKey("components");
        Map<String, Object> chemins = (Map<String, Object>) spec.get("paths");
        assertThat(chemins).as("ni route interne, ni route sans verbe, ni chemin du socle non servi")
                .containsOnlyKeys("/api/auth/login", "/api/soutien/politique", "/api/effectifs/postes/{id}", "/api/communication/conversations");

        Map<String, Object> connexion = (Map<String, Object>) ((Map<String, Object>) chemins.get("/api/auth/login")).get("post");
        assertThat(connexion).doesNotContainKey("x-genere");
        Map<String, Object> politique = (Map<String, Object>) ((Map<String, Object>) chemins.get("/api/soutien/politique")).get("get");
        assertThat(politique).doesNotContainKey("x-genere");

        Map<String, Object> poste = (Map<String, Object>) ((Map<String, Object>) chemins.get("/api/effectifs/postes/{id}")).get("get");
        assertThat(poste).containsEntry("x-genere", true).containsEntry("tags", List.of("Effectifs")).doesNotContainKey("requestBody").doesNotContainKey("security");
        List<Object> parametres = (List<Object>) poste.get("parameters");
        assertThat((Map<String, Object>) parametres.get(0)).containsEntry("name", "id").containsEntry("example", "00000000-0000-4000-8000-000000000001");
        assertThat(parametres.get(1)).isEqualTo(Map.of("$ref", "#/components/parameters/TenantId"));
        assertThat((Map<String, Object>) poste.get("responses")).containsKeys("200", "400", "401", "403", "404");

        Map<String, Object> conversation = (Map<String, Object>) ((Map<String, Object>) chemins.get("/api/communication/conversations")).get("post");
        assertThat(conversation).containsKey("requestBody").containsEntry("tags", List.of("Communication"));
    }

    @Test
    @DisplayName("une route publique générée n'exige pas de jeton ; une étiquette inconnue tombe dans « Autres » ; un socle absent est refusé au démarrage")
    void bords() {
        Map<String, Object> pays = SpecificationOpenApi.operationGeneree(new ca.vina.nexu.partage.web.TableDeRoutage.Route("get", "/api/countries/{code}"));
        assertThat(pays).containsEntry("security", List.of()).containsEntry("tags", List.of("Pays"));
        assertThat(((Map<?, ?>) pays.get("responses")).containsKey("401") || ((Map<?, ?>) pays.get("responses")).containsKey("403")).isFalse();
        assertThat((List<?>) pays.get("parameters")).hasSize(1);
        assertThat(SpecificationOpenApi.etiquette("/api/inconnu")).isEqualTo("Autres");
        assertThat(SpecificationOpenApi.etiquette("/api/effectifs")).isEqualTo("Effectifs");
        assertThatThrownBy(() -> new SpecificationOpenApi(correspondances(), "/absent.json")).hasMessageContaining("socle OpenAPI absent");
    }
}
