package ca.vina.nexu.integrations.infrastructure.web;

import ca.vina.nexu.integrations.domaine.ClientApi;
import ca.vina.nexu.integrations.infrastructure.persistance.DepotClientsApi;
import ca.vina.nexu.integrations.infrastructure.persistance.JetonClient;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Point de terminaison OAuth2 {@code client_credentials} — <strong>public</strong>.
 *
 * <p>C'est la seule route de ce module hors session : c'est précisément ici qu'une intégration
 * obtient sa session. Elle ne doit donc rien révéler. Un identifiant inconnu, un secret erroné et
 * un client révoqué rendent tous {@code 401 invalid_client}, sans distinction — les distinguer
 * transformerait cette route en oracle d'existence d'identifiants.
 *
 * <p><strong>Standard</strong> (26-015-DO, addenda 5 : « une API standard ») : la demande se fait en
 * {@code application/x-www-form-urlencoded} (RFC 6749 § 4.4.2), et le client s'authentifie par l'en-tête
 * {@code Authorization: Basic} ({@code client_secret_basic}, § 2.3.1) ou par le corps ({@code client_secret_post}).
 * Le corps JSON d'origine reste admis pour les intégrations déjà branchées.
 */
@RestController
@RequestMapping("/api/oauth")
public class ControleurOAuth {

    /** Le seul type d'octroi servi : l'application agit pour elle-même, sans utilisateur. */
    static final String OCTROI_CLIENT = "client_credentials";
    /** Codes d'erreur fixés par la RFC 6749 § 5.2. */
    static final String ERREUR_OCTROI = "unsupported_grant_type";
    static final String ERREUR_CLIENT = "invalid_client";
    private static final String PREFIXE_BASIC = "Basic ";
    private static final int NON_AUTORISE = 401;

    private final DepotClientsApi depot;
    private final JetonClient jetons;

    public ControleurOAuth(DepotClientsApi depot, JetonClient jetons) {
        this.depot = depot;
        this.jetons = jetons;
    }

    /** La forme standard : paramètres de formulaire. */
    @PostMapping(value = "/token", consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE)
    public ResponseEntity<Object> jetonFormulaire(@RequestParam MultiValueMap<String, String> parametres,
                                                  @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String autorisation) {
        return emettre(parametres.getFirst("grant_type"), parametres.getFirst("client_id"), parametres.getFirst("client_secret"), autorisation);
    }

    /** La forme d'origine : un corps JSON. */
    @PostMapping("/token")
    public ResponseEntity<Object> jeton(@RequestBody(required = false) Map<String, Object> corps,
                                        @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String autorisation) {
        Map<String, Object> c = corps == null ? Map.of() : corps;
        return emettre(texte(c.get("grant_type")), texte(c.get("client_id")), texte(c.get("client_secret")), autorisation);
    }

    private ResponseEntity<Object> emettre(String octroi, String clientId, String secret, String autorisation) {
        if (!OCTROI_CLIENT.equals(octroi)) {
            return ResponseEntity.badRequest().body(Map.of("error", ERREUR_OCTROI));
        }
        Identifiants id = basic(autorisation).orElse(new Identifiants(clientId, secret));
        Optional<ClientApi> client = depot.verifier(id.clientId(), id.secret());
        if (client.isEmpty()) {
            return ResponseEntity.status(NON_AUTORISE).header(HttpHeaders.WWW_AUTHENTICATE, "Basic").body(Map.of("error", ERREUR_CLIENT));
        }
        ClientApi cl = client.get();
        Map<String, Object> reponse = new LinkedHashMap<>();
        reponse.put("access_token", jetons.emettre(cl.clientId(), cl.tenantId(), cl.scopes()));
        reponse.put("token_type", "Bearer");
        reponse.put("expires_in", JetonClient.VALIDITE_SECONDES);
        reponse.put("scope", String.join(" ", cl.scopes()));
        // RFC 6749 § 5.1 : une réponse portant un jeton ne se met jamais en cache.
        return ResponseEntity.ok().header(HttpHeaders.CACHE_CONTROL, "no-store").header(HttpHeaders.PRAGMA, "no-cache").body(reponse);
    }

    record Identifiants(String clientId, String secret) { }

    /**
     * {@code client_secret_basic} : {@code base64(urlencode(id) ":" urlencode(secret))} (RFC 6749 § 2.3.1). Un en-tête
     * illisible ne l'emporte pas : la demande retombe sur les identifiants du corps, et un échec reste un {@code 401}.
     */
    static Optional<Identifiants> basic(String autorisation) {
        if (autorisation == null || !autorisation.regionMatches(true, 0, PREFIXE_BASIC, 0, PREFIXE_BASIC.length())) {
            return Optional.empty();
        }
        try {
            String clair = new String(Base64.getDecoder().decode(autorisation.substring(PREFIXE_BASIC.length()).strip()), StandardCharsets.UTF_8);
            int deuxPoints = clair.indexOf(':');
            if (deuxPoints < 0) {
                return Optional.empty();
            }
            return Optional.of(new Identifiants(URLDecoder.decode(clair.substring(0, deuxPoints), StandardCharsets.UTF_8),
                    URLDecoder.decode(clair.substring(deuxPoints + 1), StandardCharsets.UTF_8)));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    private static String texte(Object valeur) {
        return valeur instanceof String s ? s : null;
    }
}
