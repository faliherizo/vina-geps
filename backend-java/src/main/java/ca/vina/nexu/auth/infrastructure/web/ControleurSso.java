package ca.vina.nexu.auth.infrastructure.web;

import ca.vina.nexu.auth.infrastructure.persistance.DepotComptes;
import ca.vina.nexu.auth.infrastructure.persistance.DepotQuestionsSecretes;
import ca.vina.nexu.auth.infrastructure.persistance.EchangeurOidcHttp;
import ca.vina.nexu.auth.infrastructure.services.ServiceConnexion;
import ca.vina.nexu.auth.infrastructure.services.ServiceSso;

import ca.vina.nexu.auth.domaine.Oidc;
import ca.vina.nexu.auth.domaine.Saml;
import ca.vina.nexu.partage.audit.infrastructure.JournalAudit;
import ca.vina.nexu.partage.demonstration.RegistreDemonstrations;
import ca.vina.nexu.partage.securite.AdressePublique;
import ca.vina.nexu.partage.securite.JetonDeSession;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Les deux SSO en HTTP : Entra ID (OIDC, NF-19/20) — état, redirection, rappel, variante JSON pour
 * l'application — et SAML 2.0 (grille 41) — état, métadonnées SP, redirection, ACS. Aucune de ces
 * routes n'exige de session : elles la <em>posent</em>, avec les mêmes cookies que la connexion par mot de passe.
 */
@RestController
@RequestMapping("/api/auth")
public class ControleurSso {

    private static final ObjectMapper JSON = JsonMapper.builder().build();
    private static final TypeReference<Map<String, Object>> CARTE = new TypeReference<>() { };
    private static final SecureRandom ALEA = new SecureRandom();

    private final ServiceSso sso;
    private final JournalAudit audit;
    private final AdressePublique adresse;
    private final boolean cookieSecurise;

    @Autowired
    public ControleurSso(DepotComptes comptes, DepotQuestionsSecretes questions, RegistreDemonstrations demonstrations, JournalAudit audit, Clock horloge,
                         @Value("${nexu.jwt.secret}") String secret, AdressePublique adresse, @Value("${nexu.cookie.secure:false}") boolean cookieSecurise) {
        this(new ServiceSso(System.getenv(), demonstrations, comptes, new ServiceConnexion(comptes, questions, new JetonDeSession(secret), horloge),
                new EchangeurOidcHttp(HttpClient.newHttpClient(), Duration.ofSeconds(10), horloge), horloge, ALEA), audit, adresse, cookieSecurise);
    }

    /** L'adresse de retour après le SSO : celle de l'établissement du compte (ADR 0033), sinon le tableau de bord. */
    public ControleurSso(ServiceSso sso, JournalAudit audit, AdressePublique adresse, boolean cookieSecurise) {
        this.sso = sso;
        this.audit = audit;
        this.adresse = adresse;
        this.cookieSecurise = cookieSecurise;
    }

    private static ResponseEntity<Object> erreur(int code, String message) {
        return ResponseEntity.status(code).body(Map.of("errors", List.of(message)));
    }

    private static ResponseEntity<Object> rediriger(String url) {
        return ResponseEntity.status(302).location(URI.create(url)).build();
    }

    private String retour(ServiceSso.Session s) {
        String url = adresse.pour(s.tenantId());
        return url.isEmpty() ? "/admin/dashboard" : url;
    }

    private record Session(ServiceSso.Session s, String csrf, List<String> cookies) {
    }

    /** Les cookies de session et de CSRF, comme la connexion par mot de passe les pose ; la connexion est consignée. */
    private Session ouvrir(ServiceSso.Session s, String protocole) {
        byte[] b = new byte[24];
        ALEA.nextBytes(b);
        String csrf = java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(b);
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("role", s.role());
        details.put("provisioned", s.provisioned());
        if (protocole != null) {
            details.put("protocole", protocole);
        }
        audit.consigner(s.userId(), s.email(), "auth.sso.login.ok", null, details);
        return new Session(s, csrf, List.of(
                ControleurAuth.cookie(ControleurAuth.COOKIE_SESSION, s.token(), true, ControleurAuth.DUREE_SESSION, cookieSecurise).toString(),
                ControleurAuth.cookie(ControleurAuth.COOKIE_CSRF, csrf, false, ControleurAuth.DUREE_SESSION, cookieSecurise).toString()));
    }

    private ResponseEntity<Object> repondreJson(ServiceSso.Session s, String protocole) {
        Session o = ouvrir(s, protocole);
        Map<String, Object> user = new LinkedHashMap<>();
        user.put("id", s.userId());
        user.put("username", s.email());
        user.put("role", s.role());
        user.put("email", s.email());
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("token", s.token());
        r.put("csrf", o.csrf());
        r.put("user", user);
        r.put("role", s.role());
        r.put("provisioned", s.provisioned());
        return ResponseEntity.ok().headers(h -> h.put(HttpHeaders.SET_COOKIE, o.cookies())).body(r);
    }

    private ResponseEntity<Object> repondreRedirection(ServiceSso.Session s, String protocole) {
        Session o = ouvrir(s, protocole);
        return ResponseEntity.status(302).location(URI.create(retour(s))).headers(h -> h.put(HttpHeaders.SET_COOKIE, o.cookies())).build();
    }

    private static ResponseEntity<Object> tenter(int codeErreur, Supplier<ResponseEntity<Object>> geste) {
        try {
            return geste.get();
        } catch (RuntimeException e) {
            return erreur(codeErreur, e.getMessage());
        }
    }

    // ── Entra ID ────────────────────────────────────────────────────────────

    @GetMapping("/sso/entra/status")
    public ResponseEntity<Object> statutEntra() {
        Oidc.Configuration cfg = sso.configurationEntra();
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("enabled", true);
        r.put("mock", cfg.mock());
        r.put("provider", "Microsoft Entra ID");
        return ResponseEntity.ok(r);
    }

    /** Démarre le flux : redirige vers Entra (réel, démonstration) ou l'émetteur de test (simulé). */
    @GetMapping("/sso/entra/login")
    public ResponseEntity<Object> connexionEntra() {
        return tenter(500, () -> rediriger(sso.debuterEntra().authUrl()));
    }

    /** Reçoit le code d'autorisation, provisionne l'utilisateur, pose la session et revient à l'application. */
    @GetMapping("/sso/entra/callback")
    public ResponseEntity<Object> rappelEntra(@RequestParam(name = "code", required = false) String code, @RequestParam(name = "state", required = false) String state) {
        return tenter(400, () -> repondreRedirection(sso.provisionner(sso.completerEntra(code == null ? "" : code, state == null ? "" : state)), null));
    }

    /** Variante pour l'application (JSON) : effectue le flux complet et pose la session — le mode simulé. */
    @PostMapping("/sso/entra/session")
    public ResponseEntity<Object> sessionEntra() {
        return tenter(400, () -> {
            Oidc.Debut debut = sso.debuterEntra();
            String code = URI.create(debut.authUrl()).getRawQuery() == null ? "" : parametre(URI.create(debut.authUrl()).getRawQuery(), "code");
            return repondreJson(sso.provisionner(sso.completerEntra(code, debut.state())), null);
        });
    }

    private static String parametre(String query, String nom) {
        for (String p : query.split("&")) {
            int i = p.indexOf('=');
            if (i > 0 && p.substring(0, i).equals(nom)) {
                return java.net.URLDecoder.decode(p.substring(i + 1), java.nio.charset.StandardCharsets.UTF_8);
            }
        }
        return "";
    }

    // ── SAML 2.0 ────────────────────────────────────────────────────────────

    @GetMapping("/sso/saml/status")
    public ResponseEntity<Object> statutSaml() {
        Saml.Configuration cfg = sso.configurationSaml();
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("enabled", cfg.enabled());
        r.put("provider", "SAML 2.0");
        r.put("entityId", cfg.entityId());
        r.put("acsUrl", cfg.acsUrl());
        return ResponseEntity.ok(r);
    }

    @GetMapping("/sso/saml/metadata")
    public ResponseEntity<Object> metadonneesSaml() {
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_XML).body(Saml.metadonneesSp(sso.configurationSaml()));
    }

    @GetMapping("/sso/saml/login")
    public ResponseEntity<Object> connexionSaml() {
        return tenter(503, () -> rediriger(sso.debuterSaml().url()));
    }

    /** ACS : l'IdP poste la réponse ; la signature, l'émetteur, l'audience, la validité et le InResponseTo sont vérifiés avant tout provisionnement. */
    @PostMapping("/sso/saml/acs")
    public ResponseEntity<Object> acsSaml(HttpServletRequest requete, @RequestBody(required = false) String corps) {
        boolean json = requete.getContentType() != null && requete.getContentType().startsWith("application/json");
        try {
            String reponse = json ? String.valueOf(JSON.readValue(corps == null ? "{}" : corps, CARTE).getOrDefault("SAMLResponse", "")) : requete.getParameter("SAMLResponse");
            Saml.AssertionValidee a = sso.recevoirSaml(reponse == null ? "" : reponse);
            ServiceSso.Session s = sso.provisionner(sso.revendications(a.claims()));
            return json ? repondreJson(s, "saml") : repondreRedirection(s, "saml");
        } catch (RuntimeException e) {
            audit.consigner("anonymous", "saml", "auth.sso.login.refus", null, Map.of("motif", String.valueOf(e.getMessage())));
            return erreur(e instanceof Saml.ErreurSaml ? 401 : 400, e.getMessage());
        }
    }
}
