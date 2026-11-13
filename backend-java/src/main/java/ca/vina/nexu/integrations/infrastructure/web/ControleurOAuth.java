package ca.vina.nexu.integrations.infrastructure.web;

import ca.vina.nexu.integrations.infrastructure.persistance.DepotClientsApi;
import ca.vina.nexu.integrations.infrastructure.persistance.JetonClient;

import ca.vina.nexu.integrations.domaine.ClientApi;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

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
 */
@RestController
@RequestMapping("/api/oauth")
public class ControleurOAuth {

    private final DepotClientsApi depot;
    private final JetonClient jetons;

    public ControleurOAuth(DepotClientsApi depot, JetonClient jetons) {
        this.depot = depot;
        this.jetons = jetons;
    }

    @PostMapping("/token")
    public ResponseEntity<Object> jeton(@RequestBody(required = false) Map<String, Object> corps) {
        Map<String, Object> c = corps == null ? Map.of() : corps;

        if (!"client_credentials".equals(c.get("grant_type"))) {
            return ResponseEntity.badRequest().body(Map.of("error", "unsupported_grant_type"));
        }

        Optional<ClientApi> client = depot.verifier(
                c.get("client_id") instanceof String i ? i : null,
                c.get("client_secret") instanceof String s ? s : null);

        if (client.isEmpty()) {
            return ResponseEntity.status(401).body(Map.of("error", "invalid_client"));
        }

        ClientApi cl = client.get();
        Map<String, Object> reponse = new LinkedHashMap<>();
        reponse.put("access_token", jetons.emettre(cl.clientId(), cl.tenantId(), cl.scopes()));
        reponse.put("token_type", "Bearer");
        reponse.put("expires_in", JetonClient.VALIDITE_SECONDES);
        reponse.put("scope", String.join(" ", cl.scopes()));
        return ResponseEntity.ok(reponse);
    }
}
