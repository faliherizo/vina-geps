package ca.vina.nexu.auth.infrastructure.web;

import ca.vina.nexu.auth.application.ServiceResolutionIdentite;
import ca.vina.nexu.auth.domaine.IdentitesExternes;
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
import ca.vina.nexu.partage.securite.HoteEtablissement;
import ca.vina.nexu.partage.securite.ResolutionHote;
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
import java.util.Optional;
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
    /** Le protocole consigné quand la route n'en nomme pas d'autre. */
    private static final String ENTRA = "entra";

    private final ServiceSso sso;
    private final JournalAudit audit;
    private final AdressePublique adresse;
    private final boolean cookieSecurise;
    private final ResolutionHote.Configuration hote;

    @Autowired
    public ControleurSso(DepotComptes comptes, ServiceResolutionIdentite resolution, DepotQuestionsSecretes questions, RegistreDemonstrations demonstrations,
                         JournalAudit audit, Clock horloge, @Value("${nexu.jwt.secret}") String secret, AdressePublique adresse,
                         @Value("${nexu.cookie.secure:false}") boolean cookieSecurise, HoteEtablissement hote) {
        this(new ServiceSso(System.getenv(), demonstrations, resolution, new ServiceConnexion(comptes, questions, new JetonDeSession(secret), horloge),
                new EchangeurOidcHttp(HttpClient.newHttpClient(), Duration.ofSeconds(10), horloge), horloge, ALEA), audit, adresse, cookieSecurise, hote.configuration());
    }

    /** Hors déploiement par sous-domaine (essais, développement) : aucune origine de requête n'est retenue. */
    public ControleurSso(ServiceSso sso, JournalAudit audit, AdressePublique adresse, boolean cookieSecurise) {
        this(sso, audit, adresse, cookieSecurise, ResolutionHote.Configuration.depuis(null, null, null));
    }

    /** L'adresse de retour après le SSO : celle de l'établissement du compte (ADR 0033), sinon le tableau de bord. */
    public ControleurSso(ServiceSso sso, JournalAudit audit, AdressePublique adresse, boolean cookieSecurise, ResolutionHote.Configuration hote) {
        this.sso = sso;
        this.audit = audit;
        this.adresse = adresse;
        this.cookieSecurise = cookieSecurise;
        this.hote = hote;
    }

    /** L'origine que le navigateur atteint pour l'hôte de la requête : le SSO commence et finit là. */
    private Optional<String> origine(HttpServletRequest requete) {
        return hote.originePublique(ResolutionHote.hoteDe(requete));
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

    /** La session, ou — identité reliée à aucun compte — la liaison à prouver (ADR 0093). */
    private ResponseEntity<Object> repondreJson(ServiceSso.Issue issue, String protocole) {
        return switch (issue) {
            case ServiceSso.Session s -> sessionJson(s, protocole);
            case ServiceSso.LiaisonRequise l -> {
                ControleurLiaisonIdentite.consignerLiaisonRequise(audit, l.email(), protocole == null ? ENTRA : protocole);
                yield ControleurLiaisonIdentite.versRelierJson(l.jetonLiaison(), cookieSecurise);
            }
        };
    }

    private ResponseEntity<Object> repondreRedirection(ServiceSso.Issue issue, String protocole) {
        return switch (issue) {
            case ServiceSso.Session s -> {
                Session o = ouvrir(s, protocole);
                yield ResponseEntity.status(302).location(URI.create(retour(s))).headers(h -> h.put(HttpHeaders.SET_COOKIE, o.cookies())).build();
            }
            case ServiceSso.LiaisonRequise l -> {
                ControleurLiaisonIdentite.consignerLiaisonRequise(audit, l.email(), protocole == null ? ENTRA : protocole);
                yield ControleurLiaisonIdentite.versRelier(l.jetonLiaison(), cookieSecurise);
            }
        };
    }

    private ResponseEntity<Object> sessionJson(ServiceSso.Session s, String protocole) {
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

    /**
     * Les refus nommés ont leur statut : Entra non configuré 404, authentification non prouvée 401, compte relié
     * inadmissible 403 ; le reste (state inconnu, corps illisible) prend le code de la route. Chaque refus est consigné.
     */
    private ResponseEntity<Object> tenter(int codeErreur, Supplier<ResponseEntity<Object>> geste) {
        try {
            return geste.get();
        } catch (Oidc.DesactiveException e) {
            return erreur(404, e.getMessage());
        } catch (RuntimeException e) {
            int code = e instanceof Oidc.AuthentificationRefuseeException ? 401 : e instanceof IdentitesExternes.LiaisonRefuseeException ? 403 : codeErreur;
            audit.consigner("anonymous", ENTRA, "auth.sso.refus", null, Map.of("motif", String.valueOf(e.getMessage())));
            return erreur(code, e.getMessage());
        }
    }

    // ── Entra ID ────────────────────────────────────────────────────────────

    @GetMapping("/sso/entra/status")
    public ResponseEntity<Object> statutEntra(HttpServletRequest requete) {
        Oidc.Configuration cfg = sso.configurationEntra(origine(requete));
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("enabled", cfg.actif());
        r.put("mock", cfg.mock());
        r.put("provider", "Microsoft Entra ID");
        return ResponseEntity.ok(r);
    }

    /** Démarre le flux : redirige vers Entra (réel, démonstration) ou l'émetteur de test (simulé). */
    @GetMapping("/sso/entra/login")
    public ResponseEntity<Object> connexionEntra(HttpServletRequest requete) {
        return tenter(500, () -> rediriger(sso.debuterEntra(origine(requete)).authUrl()));
    }

    /** Reçoit le code d'autorisation, provisionne l'utilisateur, pose la session et revient à l'application. */
    @GetMapping("/sso/entra/callback")
    public ResponseEntity<Object> rappelEntra(HttpServletRequest requete, @RequestParam(name = "code", required = false) String code,
                                              @RequestParam(name = "state", required = false) String state) {
        return tenter(400, () -> repondreRedirection(sso.provisionner(sso.completerEntra(code == null ? "" : code, state == null ? "" : state, origine(requete))), null));
    }

    /**
     * Variante pour l'application (JSON), <strong>en mode simulé seulement</strong> : elle joue le flux complet sans
     * fournisseur. Hors de ce mode, elle n'existe pas (404) : elle rendait une session à tout appel anonyme.
     */
    @PostMapping("/sso/entra/session")
    public ResponseEntity<Object> sessionEntra(HttpServletRequest requete) {
        Optional<String> origine = origine(requete);
        return tenter(400, () -> {
            if (!sso.configurationEntra(origine).mock()) {
                throw new Oidc.DesactiveException();
            }
            Oidc.Debut debut = sso.debuterEntra(origine);
            String code = URI.create(debut.authUrl()).getRawQuery() == null ? "" : parametre(URI.create(debut.authUrl()).getRawQuery(), "code");
            return repondreJson(sso.provisionner(sso.completerEntra(code, debut.state(), origine)), null);
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
    public ResponseEntity<Object> statutSaml(HttpServletRequest requete) {
        Saml.Configuration cfg = sso.configurationSaml(origine(requete));
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("enabled", cfg.enabled());
        r.put("provider", "SAML 2.0");
        r.put("entityId", cfg.entityId());
        r.put("acsUrl", cfg.acsUrl());
        return ResponseEntity.ok(r);
    }

    @GetMapping("/sso/saml/metadata")
    public ResponseEntity<Object> metadonneesSaml(HttpServletRequest requete) {
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_XML).body(Saml.metadonneesSp(sso.configurationSaml(origine(requete))));
    }

    @GetMapping("/sso/saml/login")
    public ResponseEntity<Object> connexionSaml(HttpServletRequest requete) {
        return tenter(503, () -> rediriger(sso.debuterSaml(origine(requete)).url()));
    }

    /** ACS : l'IdP poste la réponse ; la signature, l'émetteur, l'audience, la validité et le InResponseTo sont vérifiés avant tout provisionnement. */
    @PostMapping("/sso/saml/acs")
    public ResponseEntity<Object> acsSaml(HttpServletRequest requete, @RequestBody(required = false) String corps) {
        boolean json = requete.getContentType() != null && requete.getContentType().startsWith("application/json");
        try {
            String reponse = json ? String.valueOf(JSON.readValue(corps == null ? "{}" : corps, CARTE).getOrDefault("SAMLResponse", "")) : requete.getParameter("SAMLResponse");
            ServiceSso.Issue s = sso.provisionner(sso.recevoirSaml(reponse == null ? "" : reponse, origine(requete)));
            return json ? repondreJson(s, "saml") : repondreRedirection(s, "saml");
        } catch (RuntimeException e) {
            audit.consigner("anonymous", "saml", "auth.sso.login.refus", null, Map.of("motif", String.valueOf(e.getMessage())));
            int code = e instanceof Saml.ErreurSaml ? 401 : e instanceof IdentitesExternes.LiaisonRefuseeException ? 403 : 400;
            return erreur(code, e.getMessage());
        }
    }
}
