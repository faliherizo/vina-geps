package ca.vina.nexu.auth.infrastructure.web;

import ca.vina.nexu.auth.application.ServiceResolutionIdentite;
import ca.vina.nexu.auth.domaine.Compte;
import ca.vina.nexu.auth.domaine.IdentitesExternes;
import ca.vina.nexu.auth.infrastructure.persistance.AdaptateurComptesAnnuaire;
import ca.vina.nexu.auth.infrastructure.persistance.DepotComptes;
import ca.vina.nexu.auth.infrastructure.persistance.DepotDemandesLiaison;
import ca.vina.nexu.auth.infrastructure.persistance.DepotIdentitesExternes;
import ca.vina.nexu.auth.infrastructure.persistance.DepotQuestionsSecretes;
import ca.vina.nexu.auth.infrastructure.persistance.EchangeurOidcHttp;
import ca.vina.nexu.auth.infrastructure.services.ServiceConnexion;
import ca.vina.nexu.auth.infrastructure.services.ServiceSso;

import ca.vina.nexu.ClientHttpEssai;
import ca.vina.nexu.SchemaEssai;
import ca.vina.nexu.auth.domaine.Annuaires;
import ca.vina.nexu.auth.domaine.Oidc;
import ca.vina.nexu.auth.domaine.Saml;
import ca.vina.nexu.demonstration.domaine.DonneesDemo;
import ca.vina.nexu.demonstration.domaine.EntraDemo;
import ca.vina.nexu.demonstration.domaine.SamlIdpDemo;
import ca.vina.nexu.notifications.infrastructure.FournisseurFactice;
import ca.vina.nexu.partage.audit.infrastructure.JournalAudit;
import ca.vina.nexu.partage.crypto.Primitives;
import ca.vina.nexu.partage.demonstration.RegistreDemonstrations;
import ca.vina.nexu.partage.securite.JetonDeSession;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import ca.vina.nexu.partage.securite.AdressePublique;
import ca.vina.nexu.partage.securite.ResolutionHote;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import tools.jackson.databind.json.JsonMapper;

import java.net.URI;
import java.net.URLDecoder;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Les deux SSO de bout en bout : Entra ID simulé (le code porte les revendications), Entra ID de
 * démonstration (le flux réel — échange du code, JWKS, {@code id_token} RS256 — contre l'Entra
 * embarqué servi par HTTP), SAML avec l'IdP embarqué qui signe une vraie assertion. Le compte
 * est provisionné à la première connexion, retrouvé ensuite, et la session posée comme par mot de passe.
 */
@DisplayName("/api/auth/sso — Entra ID (OIDC) et SAML 2.0, simulé, démonstration, réel")
class ControleurSsoHttpTest {

    private static final Clock HORLOGE = Clock.fixed(Instant.parse("2026-09-14T12:00:00Z"), ZoneOffset.UTC);
    private static final String SECRET = "secret-d-essai-de-trente-deux-octets-au-moins";

    /** Un registre d'essai : ce que l'on branche, et les réglages des tiers embarqués. */
    private static final class Registre implements RegistreDemonstrations {
        final java.util.Set<String> branchees = new java.util.HashSet<>();
        String urlServeur = "http://localhost:4000";
        String urlApplication;
        SamlIdpDemo samlIdp;

        @Override
        public boolean estBranchee(String code) {
            return branchees.contains(code);
        }

        @Override
        public Optional<String> urlApplication() {
            return Optional.ofNullable(urlApplication);
        }

        @Override
        public String urlServeur() {
            return urlServeur;
        }

        @Override
        public Map<String, String> reglages(String code) {
            return "entra".equals(code) ? Map.of("tenantId", DonneesDemo.ENTRA_TENANT, "clientId", DonneesDemo.ENTRA_CLIENT_ID, "clientSecret", DonneesDemo.ENTRA_CLIENT_SECRET)
                    : Map.of("issuer", DonneesDemo.SAML_ISSUER, "clePublique", Base64.getEncoder().encodeToString(samlIdp.clePublique().getEncoded()));
        }
    }

    private JdbcClient jdbc;
    private ServiceResolutionIdentite resolution;
    private DepotComptes comptes;
    private JetonDeSession jetons;
    private ServiceSso sso;
    private ClientHttpEssai http;
    private Registre registre;
    private Map<String, String> env;
    private FournisseurFactice tiers;
    private EntraDemo entra;
    private SamlIdpDemo samlIdp;
    private JournalAudit audit;

    @BeforeEach
    void preparer(@TempDir Path dossier) throws Exception {
        jdbc = SchemaEssai.base(dossier);
        comptes = new DepotComptes(jdbc, HORLOGE);
        jetons = new JetonDeSession(SECRET);
        audit = new JournalAudit(jdbc);
        tiers = new FournisseurFactice();
        entra = new EntraDemo(HORLOGE, new SecureRandom());
        samlIdp = new SamlIdpDemo(HORLOGE, new SecureRandom());
        registre = new Registre();
        registre.urlServeur = tiers.url();
        registre.samlIdp = samlIdp;
        env = new HashMap<>();
        JsonMapper json = JsonMapper.builder().build();
        String base = tiers.url();
        tiers.repondre = r -> {
            if (r.chemin().endsWith("/openid-configuration")) {
                return new FournisseurFactice.Reponse(200, json.writeValueAsString(EntraDemo.documentDecouverte(base, DonneesDemo.ENTRA_TENANT)));
            }
            if (r.chemin().endsWith("/keys")) {
                return new FournisseurFactice.Reponse(200, json.writeValueAsString(entra.jwks()));
            }
            EntraDemo.Reponse rep = entra.echangerCode(formulaire(r.texte()), base, DonneesDemo.ENTRA_TENANT);
            return new FournisseurFactice.Reponse(rep.statut(), json.writeValueAsString(rep.corps()));
        };
        // Le mode simulé n'existe que demandé, en essai (balayage des accès du 22 novembre 2026).
        env.put("NEXU_ESSAI", "1");
        env.put(Oidc.VARIABLE_SIMULATION, "1");
        DepotIdentitesExternes identites = new DepotIdentitesExternes(jdbc);
        DepotDemandesLiaison demandes = new DepotDemandesLiaison(jdbc);
        resolution = new ServiceResolutionIdentite(new ServiceResolutionIdentite.Ports(identites, identites, new AdaptateurComptesAnnuaire(comptes, new Random(5)),
                (j, c, t) -> Optional.empty(), demandes, demandes), HORLOGE, new Random(4));
        sso = service(env, new EchangeurOidcHttp(HttpClient.newHttpClient(), Duration.ofSeconds(5), HORLOGE)::echanger, HORLOGE);
        http = new ClientHttpEssai(new ControleurSso(sso, audit, AdressePublique.fixe(""), false));
    }

    private ServiceSso service(Map<String, String> environnement, ServiceSso.EchangeurOidc echangeur, Clock horloge) {
        return new ServiceSso(environnement, registre, resolution, new ServiceConnexion(comptes, new DepotQuestionsSecretes(jdbc, HORLOGE), jetons, HORLOGE),
                echangeur, horloge, new Random(3));
    }

    @AfterEach
    void fermer() {
        tiers.close();
    }

    private static Map<String, String> formulaire(String corps) {
        Map<String, String> m = new HashMap<>();
        for (String p : corps.split("&")) {
            int i = p.indexOf('=');
            if (i > 0) {
                m.put(URLDecoder.decode(p.substring(0, i), StandardCharsets.UTF_8), URLDecoder.decode(p.substring(i + 1), StandardCharsets.UTF_8));
            }
        }
        return m;
    }

    private static Map<String, String> parametres(String url) {
        return formulaire(URI.create(url).getRawQuery());
    }

    @Test
    @DisplayName("Entra simulé : état, redirection immédiate vers le rappel, session posée, compte provisionné puis retrouvé")
    void entraSimule() throws Exception {
        http.get("/api/auth/sso/entra/status", null).andExpect(status().isOk()).andExpect(jsonPath("$.enabled").value(true)).andExpect(jsonPath("$.mock").value(true)).andExpect(jsonPath("$.provider").value("Microsoft Entra ID"));
        MvcResult login = http.get("/api/auth/sso/entra/login", null).andExpect(status().isFound()).andReturn();
        String url = login.getResponse().getHeader("Location");
        assertThat(url).startsWith("http://localhost:4000/api/auth/sso/entra/callback?code=mock.");
        Map<String, String> p = parametres(url);
        MvcResult rappel = http.get("/api/auth/sso/entra/callback?code=" + java.net.URLEncoder.encode(p.get("code"), StandardCharsets.UTF_8) + "&state=" + p.get("state"), null)
                .andExpect(status().isFound()).andExpect(header().string("Location", "/admin/dashboard")).andReturn();
        List<String> cookies = rappel.getResponse().getHeaders("Set-Cookie");
        assertThat(cookies).hasSize(2);
        assertThat(cookies.get(0)).startsWith("session=").contains("HttpOnly").contains("SameSite=Lax");
        assertThat(cookies.get(1)).startsWith("csrf=").doesNotContain("HttpOnly");
        String jeton = cookies.get(0).substring("session=".length(), cookies.get(0).indexOf(';'));
        assertThat(jetons.lire(jeton).username()).isEqualTo("sso.user@nexuvina.ca");
        assertThat(comptes.lister()).extracting(c -> c.role()).containsExactly("manager");
        assertThat(comptes.lister().get(0).email()).isEqualTo("sso.user@nexuvina.ca");
        assertThat(jdbc.sql("SELECT action FROM audit_log").query(String.class).list()).containsExactly("auth.sso.login.ok");
        assertThat(jdbc.sql("SELECT details FROM audit_log").query(String.class).single()).isEqualTo("{\"role\":\"manager\",\"provisioned\":true}");
        // Un state rejoué ou inconnu est refusé.
        http.get("/api/auth/sso/entra/callback?code=x&state=" + p.get("state"), null).andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors[0]").value("invalid or expired SSO state"));
        http.get("/api/auth/sso/entra/callback", null).andExpect(status().isBadRequest());
        // La variante JSON : même flux, compte retrouvé, pas reprovisionné.
        MvcResult session = http.post("/api/auth/sso/entra/session", null, null).andExpect(status().isOk()).andExpect(jsonPath("$.provisioned").value(false)).andExpect(jsonPath("$.role").value("manager"))
                .andExpect(jsonPath("$.user.username").value("sso.user@nexuvina.ca")).andExpect(jsonPath("$.user.email").value("sso.user@nexuvina.ca")).andExpect(jsonPath("$.csrf").isString()).andExpect(jsonPath("$.token").isString()).andReturn();
        assertThat(session.getResponse().getHeaders("Set-Cookie")).hasSize(2);
        assertThat(comptes.lister()).hasSize(1);
    }

    @Test
    @DisplayName("Entra de démonstration : le flux réel contre l'Entra embarqué — PKCE, échange du code, JWKS, id_token vérifié, rôle mappé")
    void entraDemonstration() throws Exception {
        registre.branchees.add("entra");
        registre.urlApplication = "http://localhost:5173";
        http.get("/api/auth/sso/entra/status", null).andExpect(jsonPath("$.mock").value(false));
        String url = http.get("/api/auth/sso/entra/login", null).andExpect(status().isFound()).andReturn().getResponse().getHeader("Location");
        assertThat(url).startsWith(tiers.url() + "/mock/entra/" + DonneesDemo.ENTRA_TENANT + "/oauth2/v2.0/authorize?client_id=" + DonneesDemo.ENTRA_CLIENT_ID);
        Map<String, String> p = parametres(url);
        assertThat(p).containsEntry("redirect_uri", "http://localhost:5173/api/auth/sso/entra/callback").containsEntry("code_challenge_method", "S256").containsKey("nonce");
        // L'Entra embarqué émet le code pour Josée (administratrice), comme sa page de choix le ferait.
        String code = entra.emettreCode(p, DonneesDemo.COMPTES_ENTRA.get(0));
        MvcResult rappel = http.get("/api/auth/sso/entra/callback?code=" + code + "&state=" + p.get("state"), null).andExpect(status().isFound()).andReturn();
        assertThat(rappel.getResponse().getHeader("Location")).isEqualTo("/admin/dashboard");
        assertThat(tiers.requetes).extracting(FournisseurFactice.Requete::chemin).containsExactly(
                "/mock/entra/" + DonneesDemo.ENTRA_TENANT + "/oauth2/v2.0/token", "/mock/entra/" + DonneesDemo.ENTRA_TENANT + "/v2.0/.well-known/openid-configuration", "/mock/entra/" + DonneesDemo.ENTRA_TENANT + "/discovery/v2.0/keys");
        assertThat(comptes.parNom("josee.tremblay@cssdc-demo.qc.ca", null)).isPresent().get().extracting(c -> c.role()).isEqualTo("admin");
        // Un code inconnu : l'Entra embarqué refuse, l'erreur est nommée avec son code AADSTS.
        String url2 = http.get("/api/auth/sso/entra/login", null).andReturn().getResponse().getHeader("Location");
        http.get("/api/auth/sso/entra/callback?code=0.inconnu&state=" + parametres(url2).get("state"), null).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0]").value(startsWith("Entra : invalid_grant — AADSTS70000")));
        // Un code simulé est refusé hors du mode simulé : ses revendications sont écrites par qui le présente (prise de compte).
        String url3 = http.get("/api/auth/sso/entra/login", null).andReturn().getResponse().getHeader("Location");
        String simule = "mock." + Primitives.base64Url("{\"email\":\"josee.tremblay@cssdc-demo.qc.ca\",\"roles\":[\"Nexu.Admin\"]}".getBytes(StandardCharsets.UTF_8));
        http.get("/api/auth/sso/entra/callback?code=" + simule + "&state=" + parametres(url3).get("state"), null).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errors[0]").value("code d'autorisation simulé refusé : le mode simulé n'est pas actif"));
        // La variante JSON n'existe qu'en mode simulé.
        http.post("/api/auth/sso/entra/session", null, null).andExpect(status().isNotFound());
        assertThat(comptes.lister()).hasSize(1);
    }

    @Test
    @DisplayName("balayage des accès : sans Entra configuré, rien n'est simulé — statut désactivé, routes 404, aucune session ni compte")
    void entraDesactive() throws Exception {
        for (Map<String, String> sans : List.of(Map.<String, String>of(), Map.of(Oidc.VARIABLE_SIMULATION, "1"), Map.of("NEXU_ESSAI", "true"))) {
            ClientHttpEssai h = new ClientHttpEssai(new ControleurSso(service(sans, (c, cfg, d) -> Map.of(), HORLOGE), audit, AdressePublique.fixe(""), false));
            h.get("/api/auth/sso/entra/status", null).andExpect(status().isOk()).andExpect(jsonPath("$.enabled").value(false)).andExpect(jsonPath("$.mock").value(false));
            h.get("/api/auth/sso/entra/login", null).andExpect(status().isNotFound()).andExpect(jsonPath("$.errors[0]").value("SSO Entra ID non configuré"));
            h.post("/api/auth/sso/entra/session", null, null).andExpect(status().isNotFound())
                    .andExpect(header().doesNotExist("Set-Cookie"));
            String simule = "mock." + Primitives.base64Url("{\"email\":\"admin@x.ca\",\"roles\":[\"Nexu.Admin\"]}".getBytes(StandardCharsets.UTF_8));
            h.get("/api/auth/sso/entra/callback?code=" + simule + "&state=x", null).andExpect(status().isNotFound());
        }
        assertThat(comptes.lister()).isEmpty();
        // En mode réel, un code simulé est refusé avant tout échange, même avec un state valide.
        ServiceSso reel = service(Map.of("AZURE_CLIENT_ID", "cid", "NEXU_ESSAI", "1", Oidc.VARIABLE_SIMULATION, "1"), (c, cfg, d) -> Map.of(), HORLOGE);
        ClientHttpEssai h = new ClientHttpEssai(new ControleurSso(reel, audit, AdressePublique.fixe(""), false));
        h.get("/api/auth/sso/entra/status", null).andExpect(jsonPath("$.enabled").value(true)).andExpect(jsonPath("$.mock").value(false));
        String state = parametres(h.get("/api/auth/sso/entra/login", null).andExpect(status().isFound()).andReturn().getResponse().getHeader("Location")).get("state");
        String simule = "mock." + Primitives.base64Url("{\"email\":\"admin@x.ca\",\"roles\":[\"Nexu.Admin\"]}".getBytes(StandardCharsets.UTF_8));
        h.get("/api/auth/sso/entra/callback?code=" + simule + "&state=" + state, null).andExpect(status().isUnauthorized())
                .andExpect(header().doesNotExist("Set-Cookie"));
        h.post("/api/auth/sso/entra/session", null, null).andExpect(status().isNotFound());
        assertThat(comptes.lister()).isEmpty();
        assertThat(jdbc.sql("SELECT COUNT(*) FROM audit_log WHERE action = 'auth.sso.refus'").query(Integer.class).single()).isEqualTo(1);
    }

    @Test
    @DisplayName("SSO d'instance réel (ADR 0093) : identité inconnue → « relier », aucun compte ; reliée à un compte désactivé → 403 ; SAML réel en JSON → liaison")
    void instanceReelle() throws Exception {
        comptes.creer(new DepotComptes.Creation("marc.gagnon@cssdc-demo.qc.ca", "mot-de-passe", "manager", null, null, false, false, "marc.gagnon@cssdc-demo.qc.ca", false));
        java.util.concurrent.atomic.AtomicReference<String> sujet = new java.util.concurrent.atomic.AtomicReference<>("s-marc");
        ServiceSso reel = service(Map.of("AZURE_CLIENT_ID", "cid"), (c, cfg, d) -> Map.of("iss", "https://login.microsoftonline.com/t/v2.0", "sub", sujet.get(),
                "email", "marc.gagnon@cssdc-demo.qc.ca"), HORLOGE);
        ClientHttpEssai h = new ClientHttpEssai(new ControleurSso(reel, audit, AdressePublique.fixe(""), true));
        String state = parametres(h.get("/api/auth/sso/entra/login", null).andReturn().getResponse().getHeader("Location")).get("state");
        h.get("/api/auth/sso/entra/callback?code=0.c&state=" + state, null).andExpect(status().isFound()).andExpect(header().string("Location", "/connexion/relier"))
                .andExpect(header().string("Set-Cookie", containsString("SameSite=Strict")));
        assertThat(comptes.lister()).hasSize(1);
        assertThat(jdbc.sql("SELECT COUNT(*) FROM audit_log WHERE action = 'auth.sso.liaison.requise'").query(Integer.class).single()).isOne();
        // Relié à un compte désactivé : refus nommé, consigné.
        Compte inactif = comptes.creer(new DepotComptes.Creation("inactif", "mot-de-passe", "employee", null, null, false, false, "i@x.ca", false));
        comptes.modifier(inactif.id(), new DepotComptes.Modification(null, false, null, null, null, null, null));
        new DepotIdentitesExternes(jdbc).lier(new IdentitesExternes.Identite(IdentitesExternes.Cle.de("https://login.microsoftonline.com/t/v2.0", "s-inactif"),
                inactif.id(), IdentitesExternes.Provenance.instance(), HORLOGE.instant(), "x", IdentitesExternes.Preuve.APPROBATION, Optional.empty()));
        sujet.set("s-inactif");
        String state2 = parametres(h.get("/api/auth/sso/entra/login", null).andReturn().getResponse().getHeader("Location")).get("state");
        h.get("/api/auth/sso/entra/callback?code=0.c&state=" + state2, null).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errors[0]").value("le compte de l'application est désactivé"));

        // SAML réel (non démonstration), réponse en JSON : la liaison est rendue en JSON.
        String pem = "-----BEGIN PUBLIC KEY-----\n" + Base64.getMimeEncoder().encodeToString(samlIdp.clePublique().getEncoded()) + "\n-----END PUBLIC KEY-----";
        ServiceSso saml = service(Map.of("SAML_IDP_SSO_URL", tiers.url() + "/mock/saml/sso", "SAML_IDP_CERT", pem, "SAML_IDP_ISSUER", DonneesDemo.SAML_ISSUER,
                "SAML_SP_ACS_URL", tiers.url() + "/api/auth/sso/saml/acs"), (c, cfg, d) -> Map.of(), HORLOGE);
        ClientHttpEssai hs = new ClientHttpEssai(new ControleurSso(saml, audit, AdressePublique.fixe(""), false));
        String url = hs.get("/api/auth/sso/saml/login", null).andExpect(status().isFound()).andReturn().getResponse().getHeader("Location");
        String reponse = samlIdp.reponsePourCompte(Saml.lireAuthnRequest(parametres(url).get("SAMLRequest")), "marc.gagnon@cssdc-demo.qc.ca");
        hs.post("/api/auth/sso/saml/acs", null, "{\"SAMLResponse\":\"" + reponse + "\"}").andExpect(status().isOk())
                .andExpect(jsonPath("$.liaisonRequise").value(true)).andExpect(jsonPath("$.retour").value("/connexion/relier"));
        assertThat(comptes.lister()).hasSize(2);
    }

    @Test
    @DisplayName("Entra simulé : sans courriel dans les revendications, refus")
    void simuleSansCourriel() throws Exception {
        String state = parametres(http.get("/api/auth/sso/entra/login", null).andReturn().getResponse().getHeader("Location")).get("state");
        http.get("/api/auth/sso/entra/callback?code=mock." + Primitives.base64Url("{\"name\":\"x\"}".getBytes(StandardCharsets.UTF_8)) + "&state=" + state, null)
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors[0]").value("SSO claims missing email"));
    }

    @Test
    @DisplayName("la découverte OIDC (annuaire générique, ADR 0075) : les adresses publiées ; un document incomplet ou illisible est refusé")
    void decouverte() {
        EchangeurOidcHttp echangeur = new EchangeurOidcHttp(HttpClient.newHttpClient(), Duration.ofSeconds(5), HORLOGE);
        Annuaires.Decouverte.Points p = echangeur.points(tiers.url() + "/mock/entra/" + DonneesDemo.ENTRA_TENANT + "/v2.0");
        assertThat(p.autorisation()).startsWith(tiers.url()).endsWith("/oauth2/v2.0/authorize");
        assertThat(p.jeton()).startsWith(tiers.url()).endsWith("/oauth2/v2.0/token");
        assertThat(tiers.derniere().chemin()).endsWith("/v2.0/.well-known/openid-configuration");
        tiers.repondre = r -> new FournisseurFactice.Reponse(200, "{\"issuer\":\"x\",\"authorization_endpoint\":\"a\"}");
        assertThatThrownBy(() -> echangeur.points(tiers.url())).hasMessage("document de découverte sans authorization_endpoint ni token_endpoint");
        tiers.repondre = r -> new FournisseurFactice.Reponse(200, "<html>introuvable</html>");
        assertThatThrownBy(() -> echangeur.points(tiers.url())).isInstanceOf(IllegalStateException.class).hasMessage("document de découverte illisible");
    }

    @Test
    @DisplayName("l'échangeur vérifie ce que Microsoft prescrit : jeton absent, audience, kid inconnu, tiers injoignable")
    void echangeur() {
        EchangeurOidcHttp echangeur = new EchangeurOidcHttp(HttpClient.newHttpClient(), Duration.ofSeconds(5), HORLOGE);
        Oidc.Configuration cfg = Oidc.configuration(Map.of(), Optional.of(new Oidc.Demo(DonneesDemo.ENTRA_TENANT, "autre-client", "s", tiers.url(), Optional.empty())));
        Oidc.Demande d = Oidc.nouvelleDemande(new Random(1), HORLOGE.millis());
        // Le client n'est pas celui inscrit : l'Entra embarqué répond 400 unauthorized_client.
        assertThatThrownBy(() -> echangeur.echanger("c", cfg, d)).hasMessageStartingWith("Entra : unauthorized_client — AADSTS700016");
        // Un id_token signé par une autre clé, ou pour une autre audience.
        Oidc.Configuration bon = Oidc.configuration(Map.of(), Optional.of(new Oidc.Demo(DonneesDemo.ENTRA_TENANT, DonneesDemo.ENTRA_CLIENT_ID, DonneesDemo.ENTRA_CLIENT_SECRET, tiers.url(), Optional.empty())));
        KeyPair autre = Primitives.rsa2048();
        String enTete = Primitives.base64Url("{\"alg\":\"RS256\",\"kid\":\"inconnu\"}".getBytes(StandardCharsets.UTF_8));
        String charge = Primitives.base64Url("{\"aud\":\"x\"}".getBytes(StandardCharsets.UTF_8));
        String jwt = enTete + "." + charge + "." + Primitives.base64Url(Primitives.signerRs256(autre.getPrivate(), (enTete + "." + charge).getBytes(StandardCharsets.UTF_8)));
        assertThatThrownBy(() -> echangeur.verifierIdToken(jwt, bon, "n")).hasMessage("Entra : clé de signature « inconnu » absente du JWKS");
        String kid = String.valueOf(((Map<?, ?>) ((List<?>) entra.jwks().get("keys")).get(0)).get("kid"));
        String enTete2 = Primitives.base64Url(("{\"alg\":\"RS256\",\"kid\":\"" + kid + "\"}").getBytes(StandardCharsets.UTF_8));
        String jwt2 = enTete2 + "." + charge + "." + Primitives.base64Url(Primitives.signerRs256(autre.getPrivate(), (enTete2 + "." + charge).getBytes(StandardCharsets.UTF_8)));
        assertThatThrownBy(() -> echangeur.verifierIdToken(jwt2, bon, "n")).hasMessageStartingWith("Entra : ");
        assertThat(EchangeurOidcHttp.enTete("pas-du-json.x.y")).isEmpty();
        // Le tiers ne répond pas.
        tiers.close();
        assertThatThrownBy(() -> echangeur.echanger("c", bon, d)).isInstanceOf(IllegalStateException.class).hasMessageStartingWith("Entra : ");
        // Un fil interrompu pendant l'appel : l'interruption est conservée.
        Thread.currentThread().interrupt();
        assertThatThrownBy(() -> echangeur.echanger("c", bon, d)).hasMessage("Entra : interrompu");
        assertThat(Thread.interrupted()).isTrue();
        // Une réponse sans id_token.
        Oidc.Configuration muet = Oidc.configuration(Map.of("AZURE_CLIENT_ID", "cid", "AZURE_AUTHORITY", "http://127.0.0.1:1"), Optional.empty());
        assertThatThrownBy(() -> echangeur.echanger("c", muet, d)).hasMessageStartingWith("Entra : ");
    }

    @Test
    @DisplayName("SAML : désactivé sans IdP, métadonnées SP, puis avec l'IdP embarqué une assertion signée ouvre la session ; les refus sont consignés")
    void saml() throws Exception {
        http.get("/api/auth/sso/saml/status", null).andExpect(status().isOk()).andExpect(jsonPath("$.enabled").value(false)).andExpect(jsonPath("$.provider").value("SAML 2.0"))
                .andExpect(jsonPath("$.entityId").value("urn:nexu-vina:sp")).andExpect(jsonPath("$.acsUrl").value("http://localhost:4000/api/auth/sso/saml/acs"));
        http.get("/api/auth/sso/saml/login", null).andExpect(status().isServiceUnavailable()).andExpect(jsonPath("$.errors[0]").value("SSO SAML non configuré (SAML_IDP_SSO_URL, SAML_IDP_CERT)"));
        http.get("/api/auth/sso/saml/metadata", null).andExpect(status().isOk()).andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_XML))
                .andExpect(content().string(containsString("entityID=\"urn:nexu-vina:sp\""))).andExpect(content().string(containsString("Location=\"http://localhost:4000/api/auth/sso/saml/acs\"")));
        http.post("/api/auth/sso/saml/acs", null, "{\"SAMLResponse\":\"x\"}").andExpect(status().isUnauthorized()).andExpect(jsonPath("$.errors[0]").value("SSO SAML non configuré"));

        registre.branchees.add("saml");
        http.get("/api/auth/sso/saml/status", null).andExpect(jsonPath("$.enabled").value(true)).andExpect(jsonPath("$.acsUrl").value(tiers.url() + "/api/auth/sso/saml/acs"));
        String url = http.get("/api/auth/sso/saml/login", null).andExpect(status().isFound()).andReturn().getResponse().getHeader("Location");
        assertThat(url).startsWith(tiers.url() + "/mock/saml/sso?SAMLRequest=");
        Saml.DemandeAuthn demande = Saml.lireAuthnRequest(parametres(url).get("SAMLRequest"));
        assertThat(sso.demandesSamlEnCours()).containsExactly(demande.id());
        String reponse = samlIdp.reponsePourCompte(demande, "marc.gagnon@cssdc-demo.qc.ca");
        // Formulaire (le navigateur) : redirection vers l'application, cookies posés.
        MvcResult acs = http.envoyer(MockMvcRequestBuilders.post("/api/auth/sso/saml/acs").contentType(MediaType.APPLICATION_FORM_URLENCODED).param("SAMLResponse", reponse), null)
                .andExpect(status().isFound()).andExpect(header().string("Location", "/admin/dashboard")).andReturn();
        assertThat(acs.getResponse().getHeaders("Set-Cookie")).hasSize(2);
        assertThat(comptes.parNom("marc.gagnon@cssdc-demo.qc.ca", null)).isPresent().get().extracting(c -> c.role()).isEqualTo("manager");
        assertThat(sso.demandesSamlEnCours()).isEmpty();
        assertThat(jdbc.sql("SELECT details FROM audit_log ORDER BY rowid DESC LIMIT 1").query(String.class).single()).isEqualTo("{\"role\":\"manager\",\"provisioned\":true,\"protocole\":\"saml\"}");
        // Rejeu de la même assertion : sa demande n'est plus en cours.
        http.envoyer(MockMvcRequestBuilders.post("/api/auth/sso/saml/acs").contentType(MediaType.APPLICATION_FORM_URLENCODED).param("SAMLResponse", reponse), null)
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.errors[0]").value("assertion sans demande en cours (rejeu ou demande expirée)"));
        assertThat(jdbc.sql("SELECT action FROM audit_log ORDER BY rowid DESC LIMIT 1").query(String.class).single()).isEqualTo("auth.sso.login.refus");
        // JSON (l'application) : le corps de session.
        String url2 = http.get("/api/auth/sso/saml/login", null).andReturn().getResponse().getHeader("Location");
        String reponse2 = samlIdp.reponsePourCompte(Saml.lireAuthnRequest(parametres(url2).get("SAMLRequest")), "marc.gagnon@cssdc-demo.qc.ca");
        http.post("/api/auth/sso/saml/acs", null, "{\"SAMLResponse\":\"" + reponse2 + "\"}").andExpect(status().isOk()).andExpect(jsonPath("$.provisioned").value(false)).andExpect(jsonPath("$.user.role").value("manager"));
        // Sans corps JSON, ni paramètre : illisible.
        http.post("/api/auth/sso/saml/acs", null, null).andExpect(status().isUnauthorized()).andExpect(jsonPath("$.errors[0]").value("réponse SAML illisible"));
        http.envoyer(MockMvcRequestBuilders.post("/api/auth/sso/saml/acs").contentType(MediaType.APPLICATION_JSON), null).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("le provisionnement : mappage des rôles (défaut, surcharge JSON, JSON illisible), compte retrouvé par courriel ou nom, retour à l'application configurée")
    void provisionnement() throws Exception {
        assertThat(sso.mappage().map()).containsEntry("Nexu.Director", "director");
        env.put("AZURE_ROLE_MAPPING", "{\"claim\":\"groups\",\"map\":{\"G1\":\"rh\"},\"fallback\":null}");
        assertThat(sso.mappage().claim()).isEqualTo("groups");
        assertThat(sso.mappage().fallback()).isNull();
        // Démonstration ou simulé (non réel) : le compte naît de l'identité, avec le rôle mappé.
        ServiceSso.Session s = (ServiceSso.Session) sso.provisionner(verifiee("s-rh", false, Map.of("email", "Rh@X.ca", "groups", List.of("G1"))));
        assertThat(s.role()).isEqualTo("rh");
        assertThat(s.provisioned()).isTrue();
        // Aucun groupe connu et pas de repli : « employee » tout de même.
        assertThat(((ServiceSso.Session) sso.provisionner(verifiee("s-e", false, Map.of("email", "e@x.ca", "groups", List.of("G9"))))).role()).isEqualTo("employee");
        env.put("AZURE_ROLE_MAPPING", "{pas du json");
        assertThat(sso.mappage().claim()).isEqualTo("roles");
        // Retrouvé par (émetteur, sujet), même si le courriel a changé chez le fournisseur.
        assertThat(((ServiceSso.Session) sso.provisionner(verifiee("s-rh", true, Map.of("email", "autre@x.ca")))).provisioned()).isFalse();
        // Réel, sans juste-à-temps : un compte au même courriel n'est jamais pris d'office — la liaison est exigée, rien n'est créé.
        comptes.creer(new DepotComptes.Creation("marie", "mot-de-passe", "admin", null, null, false, false, "marie@x.ca", false));
        assertThat(sso.provisionner(verifiee("s-marie", true, Map.of("email", "marie@x.ca")))).isInstanceOf(ServiceSso.LiaisonRequise.class)
                .extracting(i -> ((ServiceSso.LiaisonRequise) i).email()).isEqualTo("marie@x.ca");
        assertThat(sso.provisionner(verifiee("s-inconnu", true, Map.of("email", "inconnu@x.ca")))).isInstanceOf(ServiceSso.LiaisonRequise.class);
        assertThat(comptes.lister()).hasSize(3);
        // Réel avec SSO_PROVISIONNEMENT_JAT : le compte inconnu naît ; celui qui existe reste à relier.
        env.put(ServiceSso.VARIABLE_JAT, "true");
        assertThat(sso.provisionner(verifiee("s-inconnu", true, Map.of("email", "inconnu@x.ca")))).isInstanceOf(ServiceSso.Session.class);
        assertThat(sso.provisionner(verifiee("s-marie", true, Map.of("email", "marie@x.ca")))).isInstanceOf(ServiceSso.LiaisonRequise.class);
        env.remove(ServiceSso.VARIABLE_JAT);
        // Le retour configuré, et les cookies sécurisés en production.
        ClientHttpEssai prod = new ClientHttpEssai(new ControleurSso(sso, audit, AdressePublique.fixe("https://app.exemple.qc.ca/admin"), true));
        Map<String, String> p = parametres(prod.get("/api/auth/sso/entra/login", null).andReturn().getResponse().getHeader("Location"));
        MvcResult r = prod.get("/api/auth/sso/entra/callback?code=" + java.net.URLEncoder.encode(p.get("code"), StandardCharsets.UTF_8) + "&state=" + p.get("state"), null)
                .andExpect(status().isFound()).andExpect(header().string("Location", "https://app.exemple.qc.ca/admin")).andReturn();
        assertThat(r.getResponse().getHeaders("Set-Cookie").get(0)).contains("Secure");
        sso.viderDemandes();
        assertThat(sso.demandesSamlEnCours()).isEmpty();
        // Les demandes expirent : après dix minutes, le state n'existe plus.
        Clock plusTard = Clock.offset(HORLOGE, Duration.ofMinutes(11));
        ServiceSso tardif = service(env, (c, cfg, d) -> Map.of(), plusTard);
        Oidc.Debut debut = sso.debuterEntra(Optional.empty());
        assertThatThrownBy(() -> tardif.completerEntra("mock.e30", debut.state(), Optional.empty())).hasMessage("invalid or expired SSO state");
        // L'échangeur injecté : le réel passe par lui ; la clé est (iss, sub), le sujet repli sur oid.
        ServiceSso reel = service(Map.of("AZURE_CLIENT_ID", "cid"), (c, cfg, d) -> Map.of("email", "reel@x.ca", "code", c, "iss", "https://idp", "oid", "o-1"), HORLOGE);
        Oidc.Debut d2 = reel.debuterEntra(Optional.empty());
        ServiceSso.Verifiee v = reel.completerEntra("0.code", d2.state(), Optional.empty());
        assertThat(v.revendications()).containsEntry("code", "0.code");
        assertThat(List.of(v.cle().emetteur().valeur(), v.cle().sujet().valeur(), String.valueOf(v.reel()))).containsExactly("https://idp", "o-1", "true");
        assertThat(reel.configurationEntraActive(Optional.empty()).clientId()).isEqualTo("cid");
    }

    private static ServiceSso.Verifiee verifiee(String sujet, boolean reel, Map<String, Object> revendications) {
        return new ServiceSso.Verifiee(IdentitesExternes.Cle.de("https://idp", sujet), revendications, reel);
    }

    @Test
    @DisplayName("SAML réel : la configuration lit le certificat PEM (ou la clé publique) et l'IdP déclaré")
    void samlReel() {
        KeyPair paire = Primitives.rsa2048();
        String pem = "-----BEGIN PUBLIC KEY-----\\n" + Base64.getMimeEncoder(64, "\n".getBytes(StandardCharsets.UTF_8)).encodeToString(paire.getPublic().getEncoded()).replace("\n", "\\n") + "\\n-----END PUBLIC KEY-----";
        Saml.Configuration cfg = Saml.configuration(Map.of("SAML_IDP_SSO_URL", "https://idp/sso", "SAML_IDP_CERT", pem, "SAML_IDP_ISSUER", "urn:idp", "SAML_SP_ENTITY_ID", "urn:sp", "SAML_SP_ACS_URL", "https://sp/acs"), Optional.empty());
        assertThat(cfg.enabled()).isTrue();
        assertThat(cfg.idpCle()).isEqualTo(paire.getPublic());
        assertThat(cfg.entityId()).isEqualTo("urn:sp");
        assertThat(cfg.idpIssuer()).isEqualTo("urn:idp");
        assertThat(Saml.configuration(Map.of("SAML_IDP_SSO_URL", "https://idp/sso"), Optional.empty()).enabled()).isFalse();
        // Adresse et certificat sans émetteur déclaré : désactivé, plutôt qu'accepter n'importe quel émetteur.
        assertThat(Saml.configuration(Map.of("SAML_IDP_SSO_URL", "https://idp/sso", "SAML_IDP_CERT", pem), Optional.empty()).enabled()).isFalse();
        assertThatThrownBy(() -> Saml.clePublique("-----BEGIN CERTIFICATE-----\nAAAA\n-----END CERTIFICATE-----")).isInstanceOf(IllegalStateException.class);
        assertThat(Saml.metadonneesSp(cfg)).contains("Location=\"https://sp/acs\"");
    }

    @Test
    @DisplayName("SAML de démonstration déployé par sous-domaine : l'IdP et l'ACS passent par l'hôte de la requête, jamais par localhost")
    void samlDemonstrationParHote() {
        java.security.PublicKey cle = Primitives.rsa2048().getPublic();
        Saml.Configuration publique = Saml.configuration(Map.of(), Optional.of(new Saml.Demo("urn:demo", cle, "http://localhost:4000",
                Optional.of("http://localhost:5173"), Optional.of("https://clincom.geps.exemple"))));
        assertThat(List.of(publique.idpSsoUrl(), publique.acsUrl()))
                .containsExactly("https://clincom.geps.exemple/mock/saml/sso", "https://clincom.geps.exemple/api/auth/sso/saml/acs");
        // Sans origine (développement, essais) : l'application apprise pour l'ACS, le serveur pour l'IdP — comme avant.
        Saml.Configuration locale = Saml.configuration(Map.of(), Optional.of(new Saml.Demo("urn:demo", cle, "http://localhost:4000", Optional.of("http://localhost:5173"))));
        assertThat(List.of(locale.idpSsoUrl(), locale.acsUrl())).containsExactly("http://localhost:4000/mock/saml/sso", "http://localhost:5173/api/auth/sso/saml/acs");
    }

    @Test
    @DisplayName("déployé par sous-domaine, la connexion Entra envoie le navigateur sur l'hôte de la requête ; un hôte forgé hors du domaine n'est jamais repris")
    void entraParHote() throws Exception {
        registre.branchees.add("entra");
        ResolutionHote.Configuration geps = ResolutionHote.Configuration.depuis("sous_domaine", "geps.exemple", null);
        ClientHttpEssai h = new ClientHttpEssai(new ControleurSso(sso, audit, AdressePublique.fixe(""), false, geps));
        String clincom = h.envoyer(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/auth/sso/entra/login")
                .header("X-Forwarded-Host", "clincom.geps.exemple"), null).andExpect(status().isFound()).andReturn().getResponse().getHeader("Location");
        assertThat(clincom).startsWith("https://clincom.geps.exemple/mock/entra/" + DonneesDemo.ENTRA_TENANT + "/oauth2/v2.0/authorize?")
                .contains("redirect_uri=" + java.net.URLEncoder.encode("https://clincom.geps.exemple/api/auth/sso/entra/callback", StandardCharsets.UTF_8));
        // Un en-tête forgé hors du domaine retombe sur l'adresse interne : il ne devient jamais une redirection.
        String forge = h.envoyer(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/auth/sso/entra/login")
                .header("X-Forwarded-Host", "evil.example"), null).andExpect(status().isFound()).andReturn().getResponse().getHeader("Location");
        assertThat(forge).startsWith(tiers.url()).doesNotContain("evil");
        assertThat(geps.originePublique("clincom.geps.exemple")).contains("https://clincom.geps.exemple");
        assertThat(geps.originePublique("geps.exemple")).contains("https://geps.exemple");
        assertThat(geps.originePublique("evil.example")).isEmpty();
        assertThat(geps.originePublique("a.b.geps.exemple")).isEmpty();
        assertThat(ResolutionHote.Configuration.depuis(null, null, null).originePublique("clincom.geps.exemple")).isEmpty();
    }
}
