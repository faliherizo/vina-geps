package ca.vina.nexu.integrations.infrastructure.web;

import ca.vina.nexu.ClientHttpEssai;
import ca.vina.nexu.SchemaEssai;
import ca.vina.nexu.integrations.domaine.ClientApi;
import ca.vina.nexu.integrations.infrastructure.persistance.DepotClientsApi;
import ca.vina.nexu.integrations.infrastructure.persistance.JetonClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.http.MediaType;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Base64;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * {@code POST /api/oauth/token} — l'API d'échange de 26-015-DO (addenda 5) doit être « standard » : formulaire
 * (RFC 6749 § 4.4.2), client_secret_basic et client_secret_post, réponse non mise en cache, refus indistincts.
 */
@DisplayName("Jeton client_credentials : formulaire, Basic, JSON ; refus indistincts")
class ControleurOAuthHttpTest {

    private static final JetonClient JETONS = new JetonClient("secret-d-essai-du-point-de-terminaison-oauth");
    private ClientHttpEssai http;
    private ClientApi.AvecSecret client;

    @BeforeEach
    void preparer(@TempDir Path dossier) {
        DepotClientsApi depot = new DepotClientsApi(SchemaEssai.base(dossier));
        client = depot.creer("t-1", "RAO", List.of("read", "write"));
        http = new ClientHttpEssai(new ControleurOAuth(depot, JETONS));
    }

    private static String basic(String id, String secret) {
        return "Basic " + Base64.getEncoder().encodeToString((id + ":" + secret).getBytes(StandardCharsets.UTF_8));
    }

    @Test
    @DisplayName("formulaire et client_secret_post : jeton machine de l'organisme, portées, non mis en cache")
    void formulaire() throws Exception {
        var r = http.envoyer(post("/api/oauth/token").contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .param("grant_type", "client_credentials").param("client_id", client.client().clientId()).param("client_secret", client.clientSecret()), null)
                .andExpect(status().isOk()).andExpect(header().string("Cache-Control", "no-store")).andExpect(header().string("Pragma", "no-cache"))
                .andExpect(jsonPath("$.token_type").value("Bearer")).andExpect(jsonPath("$.scope").value("read write")).andReturn();
        String jeton = com.jayway.jsonpath.JsonPath.read(r.getResponse().getContentAsString(), "$.access_token");
        assertThat(JETONS.lire(jeton)).hasValueSatisfying(a -> assertThat(a.tenantId()).isEqualTo("t-1"));
    }

    @Test
    @DisplayName("client_secret_basic : l'en-tête l'emporte ; insensible à la casse du schéma")
    void basic() throws Exception {
        http.envoyer(post("/api/oauth/token").contentType(MediaType.APPLICATION_FORM_URLENCODED).param("grant_type", "client_credentials")
                .header("Authorization", basic(client.client().clientId(), client.clientSecret())), null).andExpect(status().isOk());
        http.envoyer(post("/api/oauth/token").contentType(MediaType.APPLICATION_FORM_URLENCODED).param("grant_type", "client_credentials")
                .param("client_id", "autre").param("client_secret", "faux")
                .header("Authorization", basic(client.client().clientId(), client.clientSecret()).replace("Basic", "basic")), null).andExpect(status().isOk());
    }

    @Test
    @DisplayName("le corps JSON d'origine reste admis")
    void json() throws Exception {
        http.envoyer(post("/api/oauth/token").contentType(MediaType.APPLICATION_JSON).content("{\"grant_type\":\"client_credentials\",\"client_id\":\""
                + client.client().clientId() + "\",\"client_secret\":\"" + client.clientSecret() + "\"}"), null).andExpect(status().isOk());
        http.envoyer(post("/api/oauth/token").contentType(MediaType.APPLICATION_JSON).content("{\"grant_type\":\"client_credentials\",\"client_id\":3}"), null)
                .andExpect(status().isUnauthorized());
        http.envoyer(post("/api/oauth/token").contentType(MediaType.APPLICATION_JSON), null).andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("refus : octroi non servi 400 ; secret faux, client inconnu ou en-tête illisible 401 invalid_client, sans distinction")
    void refus() throws Exception {
        http.envoyer(post("/api/oauth/token").contentType(MediaType.APPLICATION_FORM_URLENCODED).param("grant_type", "password"), null)
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error").value("unsupported_grant_type"));
        for (String autorisation : new String[] {basic(client.client().clientId(), "faux"), basic("inconnu", "x"), "Basic !!!pas-du-base64",
                "Basic " + Base64.getEncoder().encodeToString("sans-deux-points".getBytes(StandardCharsets.UTF_8)), "Bearer abc"}) {
            http.envoyer(post("/api/oauth/token").contentType(MediaType.APPLICATION_FORM_URLENCODED).param("grant_type", "client_credentials")
                    .header("Authorization", autorisation), null)
                    .andExpect(status().isUnauthorized()).andExpect(header().string("WWW-Authenticate", "Basic"))
                    .andExpect(jsonPath("$.error").value("invalid_client"));
        }
    }

    @Test
    @DisplayName("client_secret_basic décode les identifiants encodés en URL (RFC 6749 § 2.3.1)")
    void decodageUrl() {
        assertThat(ControleurOAuth.basic(basic("cli%3Aa", "s%2Bb"))).contains(new ControleurOAuth.Identifiants("cli:a", "s+b"));
        assertThat(ControleurOAuth.basic(null)).isEmpty();
    }
}
