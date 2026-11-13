package ca.vina.nexu.demonstration;

import ca.vina.nexu.ClientHttpEssai;
import ca.vina.nexu.auth.domaine.Saml;
import ca.vina.nexu.demonstration.domaine.DonneesDemo;
import ca.vina.nexu.demonstration.domaine.EntraDemo;
import ca.vina.nexu.demonstration.domaine.RecepteurWebhooksDemo;
import ca.vina.nexu.demonstration.domaine.SamlIdpDemo;
import ca.vina.nexu.demonstration.domaine.TelnyxDemo;
import ca.vina.nexu.demonstration.domaine.TwilioDemo;
import ca.vina.nexu.demonstration.infrastructure.configuration.ConfigurationDemonstration;
import ca.vina.nexu.demonstration.infrastructure.web.ControleurMockEntra;
import ca.vina.nexu.demonstration.infrastructure.web.ControleurMockOAuth;
import ca.vina.nexu.demonstration.infrastructure.web.ControleurMockSaml;
import ca.vina.nexu.demonstration.infrastructure.web.ControleurMockTelnyx;
import ca.vina.nexu.demonstration.infrastructure.web.ControleurMockTwilio;
import ca.vina.nexu.demonstration.infrastructure.web.ControleurMockWebhooks;
import ca.vina.nexu.demonstration.infrastructure.web.UrlServeur;
import ca.vina.nexu.integrations.infrastructure.persistance.JetonClient;
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
@DisplayName("Tiers de démonstration — contrat HTTP")
class MocksHttpTest {

    private static final Clock HORLOGE = Clock.fixed(Instant.parse("2026-09-14T12:00:00Z"), ZoneOffset.UTC);
    private static final String BASE = "http://localhost:4002";
    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static MockMvc mvc;
    private static EntraDemo entra;
    private static SamlIdpDemo idp;
    private static RecepteurWebhooksDemo recepteur;
    private static JetonClient jetons;

    @BeforeAll
    static void monter() {
        ConfigurationDemonstration cfg = new ConfigurationDemonstration();
        UrlServeur url = cfg.urlServeur(BASE + "/");
        entra = cfg.entraDemo(HORLOGE);
        idp = cfg.samlIdpDemo(HORLOGE);
        recepteur = cfg.recepteurWebhooksDemo(HORLOGE);
        jetons = new JetonClient("un-secret-de-session-assez-long-pour-hs256");
        mvc = MockMvcBuilders.standaloneSetup(
                new ControleurMockEntra(entra, url), new ControleurMockSaml(idp, url), new ControleurMockTwilio(cfg.twilioDemo(HORLOGE)),
                new ControleurMockTelnyx(cfg.telnyxDemo(HORLOGE)), new ControleurMockOAuth(jetons), new ControleurMockWebhooks(recepteur)).build();
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

    // ── SAML ───────────────────────────────────────────────────────────────

    private static Saml.Configuration configurationSp() {
        return new Saml.Configuration(true, true, "urn:nexu-vina:sp", "http://app/api/auth/sso/saml/acs", BASE + "/mock/saml/sso", DonneesDemo.SAML_ISSUER, idp.clePublique());
    }

    @Test
    @DisplayName("SAML : métadonnées, page de choix depuis une AuthnRequest déflatée, réponse signée acceptée par le SP")
    void samlFluxComplet() throws Exception {
        String xml = mvc.perform(get("/mock/saml/metadata")).andExpect(status().isOk()).andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_XML)).andReturn().getResponse().getContentAsString();
        assertThat(xml).contains("IDPSSODescriptor").contains("<ds:Modulus>").contains("Location=\"" + BASE + "/mock/saml/sso\"");

        String id = "_essai-" + UUID.randomUUID();
        String url = Saml.urlDeConnexion(configurationSp(), id, HORLOGE.instant());
        String samlRequest = parametre(url, "SAMLRequest");
        String page = mvc.perform(get("/mock/saml/sso").param("SAMLRequest", samlRequest).param("RelayState", "/admin")).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(page).contains("data-testid=\"saml-compte-manager\"").contains("urn:nexu-vina:sp");

        String formulaire = mvc.perform(post("/mock/saml/sso").contentType(MediaType.APPLICATION_FORM_URLENCODED).param("SAMLRequest", samlRequest).param("RelayState", "/admin").param("compte", "marc.gagnon@cssdc-demo.qc.ca"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(formulaire).contains("action=\"http://app/api/auth/sso/saml/acs\"").contains("name=\"RelayState\" value=\"/admin\"").contains("saml-continuer");
        Matcher m = Pattern.compile("name=\"SAMLResponse\" value=\"([^\"]+)\"").matcher(formulaire);
        assertThat(m.find()).isTrue();
        Saml.AssertionValidee a = Saml.recevoirReponse(m.group(1), configurationSp(), HORLOGE.instant(), id::equals);
        assertThat(a.claims().email()).isEqualTo("marc.gagnon@cssdc-demo.qc.ca");
        assertThat(a.claims().name()).isEqualTo("Marc Gagnon");
        assertThat(a.claims().roles()).containsExactly("Nexu.Manager");
        assertThat(a.inResponseTo()).isEqualTo(id);
    }

    @Test
    @DisplayName("SAML : un compte inconnu reçoit le statut Requester, une demande illisible est refusée")
    void samlRefus() throws Exception {
        String id = "_r-" + UUID.randomUUID();
        String samlRequest = parametre(Saml.urlDeConnexion(configurationSp(), id, HORLOGE.instant()), "SAMLRequest");
        String formulaire = mvc.perform(post("/mock/saml/sso").contentType(MediaType.APPLICATION_FORM_URLENCODED).param("SAMLRequest", samlRequest).param("compte", "inconnu@x"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        Matcher m = Pattern.compile("name=\"SAMLResponse\" value=\"([^\"]+)\"").matcher(formulaire);
        assertThat(m.find()).isTrue();
        String b64 = m.group(1);
        assertThat(new String(Base64.getDecoder().decode(b64), StandardCharsets.UTF_8)).contains("status:Requester");
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> Saml.recevoirReponse(b64, configurationSp(), HORLOGE.instant(), id::equals))
                .isInstanceOf(Saml.ErreurSaml.class).hasMessageContaining("statut SAML");

        mvc.perform(get("/mock/saml/sso").param("SAMLRequest", "pas-du-base64!!")).andExpect(status().isBadRequest()).andExpect(content().string(org.hamcrest.Matchers.containsString("ni base64")));
        mvc.perform(get("/mock/saml/sso")).andExpect(status().isBadRequest());
        // une demande mal encodée (%%%) : la page « Demande illisible », pas le 400 du conteneur
        assertThat(ControleurMockSaml.parametresBruts("SAMLRequest=%%%&RelayState=%2Fadmin&sans-valeur")).containsEntry("SAMLRequest", "%%%").containsEntry("RelayState", "/admin").hasSize(2);
        assertThat(ControleurMockSaml.parametresBruts(null)).isEmpty();
        mvc.perform(get("/mock/saml/sso").queryParam("SAMLRequest", "%%%")).andExpect(status().isBadRequest()).andExpect(content().string(org.hamcrest.Matchers.containsString("Demande illisible")));
        mvc.perform(post("/mock/saml/sso").contentType(MediaType.APPLICATION_FORM_URLENCODED)).andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors[0]").value(org.hamcrest.Matchers.containsString("SAMLRequest")));
        String sansId = Base64.getEncoder().encodeToString(Saml.deflater("<samlp:AuthnRequest xmlns:samlp=\"urn:oasis:names:tc:SAML:2.0:protocol\"/>".getBytes(StandardCharsets.UTF_8)));
        mvc.perform(post("/mock/saml/sso").contentType(MediaType.APPLICATION_FORM_URLENCODED).param("SAMLRequest", sansId)).andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors[0]").value(org.hamcrest.Matchers.containsString("ID, Issuer")));
        String autre = Base64.getEncoder().encodeToString(Saml.deflater("<x/>".getBytes(StandardCharsets.UTF_8)));
        mvc.perform(post("/mock/saml/sso").contentType(MediaType.APPLICATION_FORM_URLENCODED).param("SAMLRequest", autre)).andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors[0]").value("SAMLRequest : AuthnRequest absente"));
    }

    // ── Twilio ─────────────────────────────────────────────────────────────

    private static String basic(String sid, String jeton) {
        return "Basic " + Base64.getEncoder().encodeToString((sid + ":" + jeton).getBytes(StandardCharsets.UTF_8));
    }

    @Test
    @DisplayName("Twilio : Basic, création et suivi d'un message et d'un appel, erreurs à la forme Twilio")
    void twilio() throws Exception {
        String sid = DonneesDemo.TWILIO_ACCOUNT_SID;
        String auth = basic(sid, DonneesDemo.TWILIO_AUTH_TOKEN);
        String messages = "/mock/twilio/2010-04-01/Accounts/" + sid + "/Messages.json";
        mvc.perform(post(messages).contentType(MediaType.APPLICATION_FORM_URLENCODED)).andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value(20003));
        mvc.perform(post(messages).header("Authorization", basic(sid, "faux")).contentType(MediaType.APPLICATION_FORM_URLENCODED)).andExpect(status().isUnauthorized());
        mvc.perform(post("/mock/twilio/2010-04-01/Accounts/ACautre/Messages.json").header("Authorization", auth).contentType(MediaType.APPLICATION_FORM_URLENCODED)).andExpect(status().isUnauthorized());
        mvc.perform(post(messages).header("Authorization", "Basic ***").contentType(MediaType.APPLICATION_FORM_URLENCODED)).andExpect(status().isUnauthorized());
        mvc.perform(post(messages).header("Authorization", "Basic " + Base64.getEncoder().encodeToString("sansdeuxpoints".getBytes())).contentType(MediaType.APPLICATION_FORM_URLENCODED)).andExpect(status().isUnauthorized());

        mvc.perform(post(messages).header("Authorization", auth).contentType(MediaType.APPLICATION_FORM_URLENCODED).param("To", "123")).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value(21211));
        mvc.perform(post(messages).header("Authorization", auth).contentType(MediaType.APPLICATION_FORM_URLENCODED).param("To", "+15550100150")).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value(21212));
        mvc.perform(post(messages).header("Authorization", auth).contentType(MediaType.APPLICATION_FORM_URLENCODED).param("To", "+15550100150").param("From", DonneesDemo.TWILIO_FROM).param("Body", "  ")).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value(21602));
        mvc.perform(post(messages).header("Authorization", auth).contentType(MediaType.APPLICATION_FORM_URLENCODED).param("To", DonneesDemo.NUMERO_LIMITE).param("From", DonneesDemo.TWILIO_FROM).param("Body", "x")).andExpect(status().isTooManyRequests()).andExpect(jsonPath("$.code").value(20429));

        String corps = mvc.perform(post(messages).header("Authorization", auth).contentType(MediaType.APPLICATION_FORM_URLENCODED).param("To", "+15550100150").param("From", DonneesDemo.TWILIO_FROM).param("Body", "Bonjour"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.status").value("queued")).andExpect(jsonPath("$.num_segments").value("1")).andExpect(jsonPath("$.error_code").value(org.hamcrest.Matchers.nullValue()))
                .andReturn().getResponse().getContentAsString();
        String msid = (String) json(corps).get("sid");
        assertThat(msid).startsWith("SM").hasSize(34);
        mvc.perform(get("/mock/twilio/2010-04-01/Accounts/" + sid + "/Messages/" + msid + ".json").header("Authorization", auth)).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("delivered")).andExpect(jsonPath("$.date_sent").value("Mon, 14 Sep 2026 12:00:00 GMT"));
        mvc.perform(get("/mock/twilio/2010-04-01/Accounts/" + sid + "/Messages/" + msid + ".json").header("Authorization", auth)).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("delivered"));
        mvc.perform(get("/mock/twilio/2010-04-01/Accounts/" + sid + "/Messages/SMinconnu.json").header("Authorization", auth)).andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value(20404));
        mvc.perform(get("/mock/twilio/2010-04-01/Accounts/" + sid + "/Messages/SMinconnu.json")).andExpect(status().isUnauthorized());

        corps = mvc.perform(post(messages).header("Authorization", auth).contentType(MediaType.APPLICATION_FORM_URLENCODED).param("To", DonneesDemo.NUMERO_INJOIGNABLE).param("From", DonneesDemo.TWILIO_FROM).param("Body", "x".repeat(161)))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.status").value("failed")).andExpect(jsonPath("$.error_code").value(30003)).andExpect(jsonPath("$.num_segments").value("2")).andReturn().getResponse().getContentAsString();
        mvc.perform(get("/mock/twilio/2010-04-01/Accounts/" + sid + "/Messages/" + json(corps).get("sid") + ".json").header("Authorization", auth)).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("undelivered"));

        String appels = "/mock/twilio/2010-04-01/Accounts/" + sid + "/Calls.json";
        mvc.perform(post(appels).contentType(MediaType.APPLICATION_FORM_URLENCODED)).andExpect(status().isUnauthorized());
        mvc.perform(post(appels).header("Authorization", auth).contentType(MediaType.APPLICATION_FORM_URLENCODED).param("To", "x")).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value(21211));
        mvc.perform(post(appels).header("Authorization", auth).contentType(MediaType.APPLICATION_FORM_URLENCODED).param("To", "+15550100151").param("From", "abc")).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value(21212));
        mvc.perform(post(appels).header("Authorization", auth).contentType(MediaType.APPLICATION_FORM_URLENCODED).param("To", "+15550100151").param("From", DonneesDemo.TWILIO_FROM)).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value(21205));
        mvc.perform(post(appels).header("Authorization", auth).contentType(MediaType.APPLICATION_FORM_URLENCODED).param("To", DonneesDemo.NUMERO_LIMITE).param("From", DonneesDemo.TWILIO_FROM).param("Url", "http://x")).andExpect(status().isTooManyRequests());
        corps = mvc.perform(post(appels).header("Authorization", auth).contentType(MediaType.APPLICATION_FORM_URLENCODED).param("To", "+15550100151").param("From", DonneesDemo.TWILIO_FROM).param("Twiml", "<Response><Dial callerId=\"+15550100152\">+15550100153</Dial></Response>"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.status").value("queued")).andReturn().getResponse().getContentAsString();
        String casid = (String) json(corps).get("sid");
        assertThat(casid).startsWith("CA");
        mvc.perform(get("/mock/twilio/2010-04-01/Accounts/" + sid + "/Calls/" + casid + ".json").header("Authorization", auth)).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("completed")).andExpect(jsonPath("$.duration").value("42"));
        mvc.perform(get("/mock/twilio/2010-04-01/Accounts/" + sid + "/Calls/" + casid + ".json").header("Authorization", auth)).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("completed"));
        mvc.perform(get("/mock/twilio/2010-04-01/Accounts/" + sid + "/Calls/CAinconnu.json").header("Authorization", auth)).andExpect(status().isNotFound());
        mvc.perform(get("/mock/twilio/2010-04-01/Accounts/" + sid + "/Calls/CAinconnu.json")).andExpect(status().isUnauthorized());
        mvc.perform(post(appels).header("Authorization", auth).contentType(MediaType.APPLICATION_FORM_URLENCODED).param("To", "+15550100151").param("From", DonneesDemo.TWILIO_FROM).param("Twiml", "<Response><Dial>" + DonneesDemo.NUMERO_INJOIGNABLE + "</Dial></Response>"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.status").value("no-answer"));
        mvc.perform(post(appels).header("Authorization", auth).contentType(MediaType.APPLICATION_FORM_URLENCODED).param("To", DonneesDemo.NUMERO_INJOIGNABLE).param("From", DonneesDemo.TWILIO_FROM).param("Url", "http://x"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.status").value("no-answer")).andExpect(jsonPath("$.twiml").value(org.hamcrest.Matchers.nullValue()));
        assertThat(TwilioDemo.numeroCompose("<Response><Say>allo</Say></Response>")).isNull();
    }

    @Test
    @DisplayName("Twilio : l'état de l'instance se lit et se vide")
    void twilioEtat() {
        TwilioDemo t = new TwilioDemo(HORLOGE, new SecureRandom());
        assertThat(t.creerMessage(Map.of("To", "+15550100150", "From", "+15550100100", "Body", "x"), "AC1").statut()).isEqualTo(201);
        assertThat(t.creerAppel(Map.of("To", "+15550100150", "From", "+15550100100", "Url", "http://x"), "AC1").statut()).isEqualTo(201);
        assertThat(t.messages()).hasSize(1);
        assertThat(t.appels()).hasSize(1);
        t.vider();
        assertThat(t.messages()).isEmpty();
        assertThat(t.appels()).isEmpty();
    }

    // ── Telnyx ─────────────────────────────────────────────────────────────

    @Test
    @DisplayName("Telnyx : Bearer, création et suivi, erreurs à la forme Telnyx")
    void telnyx() throws Exception {
        String auth = "Bearer " + DonneesDemo.TELNYX_CLE_API;
        mvc.perform(post("/mock/telnyx/v2/messages").contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().isUnauthorized()).andExpect(jsonPath("$.errors[0].code").value("10009"));
        mvc.perform(post("/mock/telnyx/v2/messages").header("Authorization", auth).contentType(MediaType.APPLICATION_JSON).content("{\"to\":\"12\"}")).andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.errors[0].code").value("40001"));
        mvc.perform(post("/mock/telnyx/v2/messages").header("Authorization", auth)).andExpect(status().isUnprocessableEntity());
        mvc.perform(post("/mock/telnyx/v2/messages").header("Authorization", auth).contentType(MediaType.APPLICATION_JSON).content("{\"to\":\"+15550100150\"}")).andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.errors[0].detail").value("'from' is required."));
        mvc.perform(post("/mock/telnyx/v2/messages").header("Authorization", auth).contentType(MediaType.APPLICATION_JSON).content("{\"to\":\"+15550100150\",\"from\":\"+15550100101\",\"text\":\" \"}")).andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.errors[0].code").value("40301"));
        mvc.perform(post("/mock/telnyx/v2/messages").header("Authorization", auth).contentType(MediaType.APPLICATION_JSON).content("{\"to\":\"" + DonneesDemo.NUMERO_LIMITE + "\",\"from\":\"+15550100101\",\"text\":\"x\"}")).andExpect(status().isTooManyRequests());
        String corps = mvc.perform(post("/mock/telnyx/v2/messages").header("Authorization", auth).contentType(MediaType.APPLICATION_JSON).content("{\"to\":\"+15550100150\",\"from\":\"+15550100101\",\"text\":\"Bonjour\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.to[0].status").value("queued")).andExpect(jsonPath("$.data.parts").value(1)).andReturn().getResponse().getContentAsString();
        @SuppressWarnings("unchecked") String id = (String) ((Map<String, Object>) json(corps).get("data")).get("id");
        mvc.perform(get("/mock/telnyx/v2/messages/" + id).header("Authorization", auth)).andExpect(status().isOk()).andExpect(jsonPath("$.data.to[0].status").value("delivered")).andExpect(jsonPath("$.data.completed_at").value("2026-09-14T12:00:00.000Z"));
        mvc.perform(get("/mock/telnyx/v2/messages/" + id).header("Authorization", auth)).andExpect(status().isOk()).andExpect(jsonPath("$.data.to[0].status").value("delivered"));
        mvc.perform(get("/mock/telnyx/v2/messages/inconnu").header("Authorization", auth)).andExpect(status().isNotFound()).andExpect(jsonPath("$.errors[0].code").value("10005"));
        mvc.perform(get("/mock/telnyx/v2/messages/inconnu")).andExpect(status().isUnauthorized());
        corps = mvc.perform(post("/mock/telnyx/v2/messages").header("Authorization", auth).contentType(MediaType.APPLICATION_JSON).content("{\"to\":\"" + DonneesDemo.NUMERO_INJOIGNABLE + "\",\"from\":\"+15550100101\",\"text\":\"" + "y".repeat(321) + "\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.to[0].status").value("sending_failed")).andExpect(jsonPath("$.data.parts").value(3)).andExpect(jsonPath("$.data.errors[0].code").value("40008")).andReturn().getResponse().getContentAsString();
        @SuppressWarnings("unchecked") String id2 = (String) ((Map<String, Object>) json(corps).get("data")).get("id");
        mvc.perform(get("/mock/telnyx/v2/messages/" + id2).header("Authorization", auth)).andExpect(status().isOk()).andExpect(jsonPath("$.data.to[0].status").value("delivery_failed"));

        TelnyxDemo t = new TelnyxDemo(HORLOGE, UUID::randomUUID);
        t.creerMessage(Map.of("to", "+15550100150", "from", "+1", "text", "x"));
        assertThat(t.messages()).hasSize(1);
        t.vider();
        assertThat(t.messages()).isEmpty();
    }

    // ── OAuth ──────────────────────────────────────────────────────────────

    @Test
    @DisplayName("ressource OAuth : 401 avec WWW-Authenticate, 403 insufficient_scope, 200 avec les revendications")
    void ressourceOAuth() throws Exception {
        mvc.perform(get("/mock/oauth/ressource")).andExpect(status().isUnauthorized()).andExpect(header().string("WWW-Authenticate", "Bearer realm=\"nexu-vina\"")).andExpect(jsonPath("$.error").value("invalid_request"));
        mvc.perform(get("/mock/oauth/ressource").header("Authorization", "Bearer faux")).andExpect(status().isUnauthorized()).andExpect(header().string("WWW-Authenticate", org.hamcrest.Matchers.containsString("invalid_token")));
        String jeton = jetons.emettre("cli_1", "t-1", List.of("read"));
        mvc.perform(get("/mock/oauth/ressource").header("Authorization", "Bearer " + jeton).param("scope", "write")).andExpect(status().isForbidden()).andExpect(jsonPath("$.error").value("insufficient_scope"))
                .andExpect(header().string("WWW-Authenticate", org.hamcrest.Matchers.containsString("scope=\"write\"")));
        mvc.perform(get("/mock/oauth/ressource").header("Authorization", "Bearer " + jeton)).andExpect(status().isOk()).andExpect(jsonPath("$.client").value("cli_1")).andExpect(jsonPath("$.tenantId").value("t-1")).andExpect(jsonPath("$.scopes[0]").value("read"));
    }

    // ── Webhooks ───────────────────────────────────────────────────────────

    private static String signature(String secret, String corps) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        return "sha256=" + HexFormat.of().formatHex(mac.doFinal(corps.getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    @DisplayName("récepteur de webhooks : réglages, signature HMAC vérifiée sur les octets reçus, journal JSON et HTML, code de panne")
    void recepteurWebhooks() throws Exception {
        recepteur.reinitialiser();
        mvc.perform(get("/mock/webhooks/recepteur/reglages")).andExpect(status().isOk()).andExpect(jsonPath("$.secretConfigure").value(false)).andExpect(jsonPath("$.codeReponse").value(200));
        mvc.perform(put("/mock/webhooks/recepteur/reglages").contentType(MediaType.APPLICATION_JSON).content("{\"codeReponse\":99}")).andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors[0]").value("codeReponse : un code HTTP entre 200 et 599"));
        mvc.perform(put("/mock/webhooks/recepteur/reglages").contentType(MediaType.APPLICATION_JSON).content("{\"codeReponse\":\"x\"}")).andExpect(status().isBadRequest());
        mvc.perform(put("/mock/webhooks/recepteur/reglages").contentType(MediaType.APPLICATION_JSON).content("{\"codeReponse\":200.5}")).andExpect(status().isBadRequest());
        mvc.perform(put("/mock/webhooks/recepteur/reglages")).andExpect(status().isOk()).andExpect(jsonPath("$.secretConfigure").value(false));
        String page = mvc.perform(get("/mock/webhooks/recepteur/journal.html")).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(page).contains("non configuré").contains("Aucun envoi reçu.");
        // sans secret : toute signature est invalide
        mvc.perform(post("/mock/webhooks/recepteur").contentType(MediaType.APPLICATION_JSON).content("{\"a\":1}")).andExpect(status().isOk()).andExpect(jsonPath("$.recu").value(true)).andExpect(jsonPath("$.signatureValide").value(false));
        mvc.perform(put("/mock/webhooks/recepteur/reglages").contentType(MediaType.APPLICATION_JSON).content("{\"secret\":\"s3cret\",\"codeReponse\":503}")).andExpect(status().isOk()).andExpect(jsonPath("$.secretConfigure").value(true)).andExpect(jsonPath("$.codeReponse").value(503));
        String corps = "{\"evenement\":\"employe.cree\",\"donnees\":{\"x\":1}}";
        mvc.perform(post("/mock/webhooks/recepteur").contentType(MediaType.APPLICATION_JSON).content(corps).header("X-Evenement", "employe.cree").header("X-Livraison", "liv-1").header("X-Signature-256", signature("s3cret", corps)))
                .andExpect(status().isServiceUnavailable()).andExpect(jsonPath("$.signatureValide").value(true));
        mvc.perform(post("/mock/webhooks/recepteur").contentType(MediaType.APPLICATION_JSON).content(corps).header("X-Signature-256", signature("autre", corps))).andExpect(status().isServiceUnavailable()).andExpect(jsonPath("$.signatureValide").value(false));
        mvc.perform(post("/mock/webhooks/recepteur").contentType(MediaType.APPLICATION_JSON).content(corps).header("X-Signature-256", "sha256=court")).andExpect(status().isServiceUnavailable()).andExpect(jsonPath("$.signatureValide").value(false));
        mvc.perform(post("/mock/webhooks/recepteur")).andExpect(status().isServiceUnavailable());
        String journal = mvc.perform(get("/mock/webhooks/recepteur/journal")).andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(5)).andExpect(jsonPath("$[4].evenement").value("?")).andExpect(jsonPath("$[3].livraison").value("liv-1")).andExpect(jsonPath("$[3].signatureValide").value(true)).andExpect(jsonPath("$[0].corps").value("\"\"")).andReturn().getResponse().getContentAsString();
        assertThat(journal).contains("\"recuLe\":\"2026-09-14T12:00:00.000Z\"");
        page = mvc.perform(get("/mock/webhooks/recepteur/journal.html")).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(page).contains("Secret configuré").contains("<code>503</code>").contains("Valide</td>").contains("Invalide ou absente");
        mvc.perform(delete("/mock/webhooks/recepteur/journal")).andExpect(status().isNoContent());
        mvc.perform(get("/mock/webhooks/recepteur/journal")).andExpect(status().isOk()).andExpect(content().json("[]"));
        mvc.perform(put("/mock/webhooks/recepteur/reglages").contentType(MediaType.APPLICATION_JSON).content("{\"secret\":null}")).andExpect(status().isOk()).andExpect(jsonPath("$.secretConfigure").value(false));
        // le journal ne garde que 200 entrées
        RecepteurWebhooksDemo r = new RecepteurWebhooksDemo(HORLOGE);
        for (int i = 0; i < 205; i++) {
            r.recevoir("{}", "e", "l", null);
        }
        assertThat(r.journal()).hasSize(200);
        assertThat(RecepteurWebhooksDemo.signatureValide(null, "x", "y")).isFalse();
        assertThat(RecepteurWebhooksDemo.signatureValide("s", "x", null)).isFalse();
    }
}
