package ca.vina.nexu.demonstration;

import ca.vina.nexu.ClientHttpEssai;
import ca.vina.nexu.auth.domaine.Saml;
import ca.vina.nexu.demonstration.domaine.DonneesDemo;
import ca.vina.nexu.demonstration.domaine.EntraDemo;
import ca.vina.nexu.demonstration.infrastructure.configuration.ConfigurationDemonstration;
import ca.vina.nexu.demonstration.infrastructure.web.ControleurMockEntra;
import ca.vina.nexu.demonstration.infrastructure.web.UrlServeur;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import tools.jackson.databind.json.JsonMapper;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.math.BigInteger;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.MessageDigest;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.Signature;
import java.security.spec.RSAPublicKeySpec;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Les tiers de démonstration parlent leur vrai protocole : chaque essai joue le client tel que
 * la documentation du tiers le décrit (PKCE Entra, HTTP-Redirect/POST SAML, Basic Twilio, Bearer
 * Telnyx, HMAC des webhooks, jeton Mozaïk) et vérifie ce que le client réel vérifierait.
 */
@DisplayName("Entra ID de démonstration — contrat HTTP")
class MocksEntraHttpTest {

    private static final Clock HORLOGE = Clock.fixed(Instant.parse("2026-09-14T12:00:00Z"), ZoneOffset.UTC);
    private static final String BASE = "http://localhost:4002";
    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static MockMvc mvc;
    private static EntraDemo entra;

    @BeforeAll
    static void monter() {
        ConfigurationDemonstration cfg = new ConfigurationDemonstration();
        UrlServeur url = cfg.urlServeur(BASE + "/");
        entra = cfg.entraDemo(HORLOGE);
        mvc = MockMvcBuilders.standaloneSetup(new ControleurMockEntra(entra, url)).build();
        assertThat(url.base()).isEqualTo(BASE);
    }

    private static Map<String, Object> json(String texte) {
        return JSON.readValue(texte, new tools.jackson.core.type.TypeReference<Map<String, Object>>() { });
    }

    private static String parametre(String url, String nom) {
        Matcher m = Pattern.compile("[?&]" + nom + "=([^&]*)").matcher(url);
        return m.find() ? URLDecoder.decode(m.group(1), StandardCharsets.UTF_8) : null;
    }

    // ── Entra ──────────────────────────────────────────────────────────────

    private static final String VERIFIER = "verifier-de-demonstration-assez-long-pour-pkce-0123456789";

    private static String challenge() throws Exception {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(MessageDigest.getInstance("SHA-256").digest(VERIFIER.getBytes(StandardCharsets.UTF_8)));
    }

    private static org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder autorisation(String tenant, String... surcharges) throws Exception {
        Map<String, String> p = new java.util.LinkedHashMap<>(Map.of("client_id", DonneesDemo.ENTRA_CLIENT_ID, "response_type", "code",
                "redirect_uri", "http://app/api/auth/sso/entra/callback", "scope", "openid profile", "state", "etat-1", "nonce", "n-1",
                "code_challenge", challenge(), "code_challenge_method", "S256"));
        for (String s : surcharges) {
            p.put(s.substring(0, s.indexOf('=')), s.substring(s.indexOf('=') + 1));
        }
        var b = get("/mock/entra/" + tenant + "/oauth2/v2.0/authorize");
        p.forEach(b::param);
        return b;
    }

    @Test
    @DisplayName("Entra : découverte, JWKS, page de choix, code, échange PKCE et id_token RS256 vérifiable")
    void entraFluxComplet() throws Exception {
        String t = DonneesDemo.ENTRA_TENANT;
        Map<String, Object> d = json(mvc.perform(get("/mock/entra/" + t + "/v2.0/.well-known/openid-configuration")).andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        assertThat(d.get("issuer")).isEqualTo(BASE + "/mock/entra/" + t + "/v2.0");
        assertThat(d.get("jwks_uri")).isEqualTo(BASE + "/mock/entra/" + t + "/discovery/v2.0/keys");

        String page = mvc.perform(autorisation(t)).andExpect(status().isOk()).andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_HTML)).andReturn().getResponse().getContentAsString();
        assertThat(page).contains("data-testid=\"entra-compte-admin\"").contains("entra-refuser").contains("<html lang=\"fr\"");

        String retour = mvc.perform(post("/mock/entra/" + t + "/login").contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("client_id", DonneesDemo.ENTRA_CLIENT_ID).param("response_type", "code").param("redirect_uri", "http://app/api/auth/sso/entra/callback")
                        .param("scope", "openid profile").param("state", "etat-1").param("nonce", "n-1").param("code_challenge", challenge()).param("code_challenge_method", "S256")
                        .param("compte", "josee.tremblay@cssdc-demo.qc.ca"))
                .andExpect(status().isFound()).andReturn().getResponse().getHeader("Location");
        String code = parametre(retour, "code");
        assertThat(code).startsWith("0.");
        assertThat(parametre(retour, "state")).isEqualTo("etat-1");

        String jeton = mvc.perform(post("/mock/entra/" + t + "/oauth2/v2.0/token").contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("client_id", DonneesDemo.ENTRA_CLIENT_ID).param("client_secret", DonneesDemo.ENTRA_CLIENT_SECRET).param("grant_type", "authorization_code")
                        .param("code", code).param("code_verifier", VERIFIER).param("redirect_uri", "http://app/api/auth/sso/entra/callback"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.token_type").value("Bearer")).andReturn().getResponse().getContentAsString();
        String idToken = (String) json(jeton).get("id_token");
        String[] parties = idToken.split("\\.");
        Map<String, Object> enTete = json(new String(Base64.getUrlDecoder().decode(parties[0]), StandardCharsets.UTF_8));
        Map<String, Object> charge = json(new String(Base64.getUrlDecoder().decode(parties[1]), StandardCharsets.UTF_8));
        assertThat(enTete.get("alg")).isEqualTo("RS256");
        assertThat(charge).containsEntry("preferred_username", "josee.tremblay@cssdc-demo.qc.ca").containsEntry("tid", t).containsEntry("nonce", "n-1").containsEntry("ver", "2.0");
        assertThat(charge.get("roles")).isEqualTo(List.of("Nexu.Admin"));

        Map<String, Object> jwks = json(mvc.perform(get("/mock/entra/" + t + "/discovery/v2.0/keys")).andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        @SuppressWarnings("unchecked") Map<String, Object> cle = ((List<Map<String, Object>>) jwks.get("keys")).get(0);
        assertThat(cle.get("kid")).isEqualTo(enTete.get("kid"));
        PublicKey pub = KeyFactory.getInstance("RSA").generatePublic(new RSAPublicKeySpec(new BigInteger(1, Base64.getUrlDecoder().decode((String) cle.get("n"))), new BigInteger(1, Base64.getUrlDecoder().decode((String) cle.get("e")))));
        Signature s = Signature.getInstance("SHA256withRSA");
        s.initVerify(pub);
        s.update((parties[0] + "." + parties[1]).getBytes(StandardCharsets.UTF_8));
        assertThat(s.verify(Base64.getUrlDecoder().decode(parties[2]))).isTrue();

        // Un code ne s'échange qu'une fois.
        mvc.perform(post("/mock/entra/" + t + "/oauth2/v2.0/token").contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("client_id", DonneesDemo.ENTRA_CLIENT_ID).param("client_secret", DonneesDemo.ENTRA_CLIENT_SECRET).param("grant_type", "authorization_code")
                        .param("code", code).param("code_verifier", VERIFIER).param("redirect_uri", "http://app/api/auth/sso/entra/callback"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error").value("invalid_grant")).andExpect(jsonPath("$.error_codes[0]").value(70000));
    }

    @Test
    @DisplayName("Entra : les refus du point de terminaison des jetons, dans l'ordre d'Entra")
    void entraRefusDesJetons() throws Exception {
        String t = DonneesDemo.ENTRA_TENANT;
        String chemin = "/mock/entra/" + t + "/oauth2/v2.0/token";
        mvc.perform(post(chemin).contentType(MediaType.APPLICATION_FORM_URLENCODED).param("client_id", "autre")).andExpect(status().isBadRequest()).andExpect(jsonPath("$.error").value("unauthorized_client"));
        mvc.perform(post(chemin).contentType(MediaType.APPLICATION_FORM_URLENCODED)).andExpect(status().isBadRequest()).andExpect(jsonPath("$.error_description").value(org.hamcrest.Matchers.containsString("identifier ''")));
        mvc.perform(post(chemin).contentType(MediaType.APPLICATION_FORM_URLENCODED).param("client_id", DonneesDemo.ENTRA_CLIENT_ID).param("client_secret", "faux")).andExpect(status().isUnauthorized()).andExpect(jsonPath("$.error").value("invalid_client"));
        mvc.perform(post(chemin).contentType(MediaType.APPLICATION_FORM_URLENCODED).param("client_id", DonneesDemo.ENTRA_CLIENT_ID).param("client_secret", DonneesDemo.ENTRA_CLIENT_SECRET).param("grant_type", "password"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error").value("unsupported_grant_type"));
        mvc.perform(post(chemin).contentType(MediaType.APPLICATION_FORM_URLENCODED).param("client_id", DonneesDemo.ENTRA_CLIENT_ID).param("client_secret", DonneesDemo.ENTRA_CLIENT_SECRET))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error_description").value(org.hamcrest.Matchers.containsString("grant type ''")));
        mvc.perform(post(chemin).contentType(MediaType.APPLICATION_FORM_URLENCODED).param("client_id", DonneesDemo.ENTRA_CLIENT_ID).param("client_secret", DonneesDemo.ENTRA_CLIENT_SECRET).param("grant_type", "authorization_code"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error_codes[0]").value(70000));

        // PKCE faux, puis redirect_uri divergent : deux codes, deux refus.
        Map<String, String> demande = Map.of("client_id", DonneesDemo.ENTRA_CLIENT_ID, "redirect_uri", "http://app/cb", "code_challenge", challenge(), "scope", "openid");
        String code = entra.emettreCode(demande, DonneesDemo.COMPTES_ENTRA.get(1));
        mvc.perform(post(chemin).contentType(MediaType.APPLICATION_FORM_URLENCODED).param("client_id", DonneesDemo.ENTRA_CLIENT_ID).param("client_secret", DonneesDemo.ENTRA_CLIENT_SECRET)
                        .param("grant_type", "authorization_code").param("code", code).param("code_verifier", "mauvais").param("redirect_uri", "http://app/cb"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error_codes[0]").value(501481));
        code = entra.emettreCode(demande, DonneesDemo.COMPTES_ENTRA.get(1));
        mvc.perform(post(chemin).contentType(MediaType.APPLICATION_FORM_URLENCODED).param("client_id", DonneesDemo.ENTRA_CLIENT_ID).param("client_secret", DonneesDemo.ENTRA_CLIENT_SECRET)
                        .param("grant_type", "authorization_code").param("code", code).param("code_verifier", VERIFIER).param("redirect_uri", "http://autre/cb"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error_codes[0]").value(50011));
        // Sans nonce ni scope : le jeton n'en porte pas, la portée retombe sur openid.
        code = entra.emettreCode(Map.of("client_id", DonneesDemo.ENTRA_CLIENT_ID, "redirect_uri", "http://app/cb", "code_challenge", challenge()), DonneesDemo.COMPTES_ENTRA.get(2));
        String corps = mvc.perform(post(chemin).contentType(MediaType.APPLICATION_FORM_URLENCODED).param("client_id", DonneesDemo.ENTRA_CLIENT_ID).param("client_secret", DonneesDemo.ENTRA_CLIENT_SECRET)
                        .param("grant_type", "authorization_code").param("code", code).param("code_verifier", VERIFIER).param("redirect_uri", "http://app/cb"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.scope").value("openid")).andReturn().getResponse().getContentAsString();
        String charge = new String(Base64.getUrlDecoder().decode(((String) json(corps).get("id_token")).split("\\.")[1]), StandardCharsets.UTF_8);
        assertThat(charge).doesNotContain("nonce");
        assertThat(entra.codesEnAttente()).doesNotContain(code);
    }

    @Test
    @DisplayName("Entra : la demande d'autorisation est validée comme Entra le fait, page ou redirection selon le cas")
    void entraDemandeAutorisation() throws Exception {
        String t = DonneesDemo.ENTRA_TENANT;
        // client inconnu : page d'erreur (aucun redirect_uri fiable)
        mvc.perform(get("/mock/entra/" + t + "/oauth2/v2.0/authorize?client_id=autre&redirect_uri=http://app/cb")).andExpect(status().isBadRequest())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("unauthorized_client")));
        mvc.perform(get("/mock/entra/" + t + "/oauth2/v2.0/authorize")).andExpect(status().isBadRequest())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("identifier &#39;&#39;")));
        // redirect_uri absent : page d'erreur aussi
        mvc.perform(get("/mock/entra/" + t + "/oauth2/v2.0/authorize?client_id=" + DonneesDemo.ENTRA_CLIENT_ID)).andExpect(status().isBadRequest())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("AADSTS90014")));
        // les autres erreurs reviennent à l'application
        String loc = mvc.perform(get("/mock/entra/" + t + "/oauth2/v2.0/authorize?client_id=" + DonneesDemo.ENTRA_CLIENT_ID + "&redirect_uri=http://app/cb&response_type=token&state=s"))
                .andExpect(status().isFound()).andReturn().getResponse().getHeader("Location");
        assertThat(parametre(loc, "error")).isEqualTo("unsupported_response_type");
        assertThat(parametre(loc, "state")).isEqualTo("s");
        loc = mvc.perform(get("/mock/entra/" + t + "/oauth2/v2.0/authorize?client_id=" + DonneesDemo.ENTRA_CLIENT_ID + "&redirect_uri=http://app/cb&response_type=code&scope=profile"))
                .andExpect(status().isFound()).andReturn().getResponse().getHeader("Location");
        assertThat(parametre(loc, "error_description")).contains("AADSTS70011");
        assertThat(loc).doesNotContain("state=");
        loc = mvc.perform(get("/mock/entra/" + t + "/oauth2/v2.0/authorize?client_id=" + DonneesDemo.ENTRA_CLIENT_ID + "&redirect_uri=http://app/cb&response_type=code&scope=openid"))
                .andExpect(status().isFound()).andReturn().getResponse().getHeader("Location");
        assertThat(parametre(loc, "error_description")).contains("AADSTS501481");
        loc = mvc.perform(get("/mock/entra/" + t + "/oauth2/v2.0/authorize?client_id=" + DonneesDemo.ENTRA_CLIENT_ID + "&redirect_uri=http://app/cb&response_type=code&scope=openid&code_challenge=x&code_challenge_method=plain"))
                .andExpect(status().isFound()).andReturn().getResponse().getHeader("Location");
        assertThat(parametre(loc, "error_description")).contains("AADSTS501481");
        // prompt=none : connexion silencieuse pour un compte connu, login_required sinon
        loc = mvc.perform(autorisation(t, "prompt=none", "login_hint=marc.gagnon@cssdc-demo.qc.ca")).andExpect(status().isFound()).andReturn().getResponse().getHeader("Location");
        assertThat(parametre(loc, "code")).startsWith("0.");
        loc = mvc.perform(autorisation(t, "prompt=none", "login_hint=inconnu@x")).andExpect(status().isFound()).andReturn().getResponse().getHeader("Location");
        assertThat(parametre(loc, "error")).isEqualTo("login_required");
        // redirect_uri qui porte déjà une requête : les paramètres s'ajoutent
        loc = mvc.perform(autorisation(t, "prompt=none", "login_hint=marc.gagnon@cssdc-demo.qc.ca", "redirect_uri=http://app/cb?x=1"))
                .andExpect(status().isFound()).andReturn().getResponse().getHeader("Location");
        assertThat(loc).startsWith("http://app/cb?x=1&code=");
        // la page de connexion refuse une demande invalide en JSON, et un compte inconnu en access_denied
        mvc.perform(post("/mock/entra/" + t + "/login").contentType(MediaType.APPLICATION_FORM_URLENCODED).param("client_id", "autre")).andExpect(status().isBadRequest()).andExpect(jsonPath("$.error").value("unauthorized_client"));
        loc = mvc.perform(post("/mock/entra/" + t + "/login").contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("client_id", DonneesDemo.ENTRA_CLIENT_ID).param("response_type", "code").param("redirect_uri", "http://app/cb").param("scope", "openid")
                        .param("code_challenge", challenge()).param("code_challenge_method", "S256").param("compte", "personne@x"))
                .andExpect(status().isFound()).andReturn().getResponse().getHeader("Location");
        assertThat(parametre(loc, "error")).isEqualTo("access_denied");
        // déconnexion
        mvc.perform(get("/mock/entra/" + t + "/oauth2/v2.0/logout?post_logout_redirect_uri=http://app/")).andExpect(status().isFound()).andExpect(header().string("Location", "http://app/"));
        mvc.perform(get("/mock/entra/" + t + "/oauth2/v2.0/logout")).andExpect(status().isOk()).andExpect(content().string(org.hamcrest.Matchers.containsString("déconnecté")));
        mvc.perform(get("/mock/entra/" + t + "/oauth2/v2.0/logout?post_logout_redirect_uri=")).andExpect(status().isOk());
    }

    @Test
    @DisplayName("Entra : un code expire après dix minutes et le nettoyage l'oublie")
    void entraCodeExpire() {
        Clock passe = Clock.fixed(Instant.parse("2026-09-14T11:00:00Z"), ZoneOffset.UTC);
        EntraDemo ancien = new EntraDemo(passe, new SecureRandom());
        Map<String, String> demande = Map.of("client_id", DonneesDemo.ENTRA_CLIENT_ID, "redirect_uri", "http://app/cb", "code_challenge", "c");
        String code = ancien.emettreCode(demande, DonneesDemo.COMPTES_ENTRA.get(0));
        assertThat(ancien.codesEnAttente()).contains(code);
        EntraDemo.Reponse r = ancien.echangerCode(Map.of("client_id", DonneesDemo.ENTRA_CLIENT_ID, "client_secret", DonneesDemo.ENTRA_CLIENT_SECRET, "grant_type", "authorization_code", "code", code), BASE, "t");
        assertThat(r.statut()).isEqualTo(400);   // PKCE faux : le code a été consommé
        ancien.vider();
        assertThat(ancien.codesEnAttente()).isEmpty();
        assertThat(EntraDemo.validerDemande(Map.of("client_id", DonneesDemo.ENTRA_CLIENT_ID, "redirect_uri", "http://x", "response_type", "code", "scope", "openid", "code_challenge", "c", "code_challenge_method", "S256"))).isNull();
    }
}
