package ca.vina.nexu.demonstration.infrastructure.web;

import ca.vina.nexu.integrations.infrastructure.persistance.JetonClient;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Ressource protégée de démonstration — ce que voit un consommateur de l'API : un jeton
 * {@code client_credentials} présenté en {@code Authorization: Bearer} (RFC 6750). Sans jeton ou
 * avec un jeton invalide, 401 et l'en-tête {@code WWW-Authenticate} que la RFC 6750 §3 prescrit ;
 * sans la portée demandée, 403 {@code insufficient_scope}.
 */
@RestController
@RequestMapping("/mock/oauth")
public class ControleurMockOAuth {

    private final JetonClient jetons;

    public ControleurMockOAuth(JetonClient jetons) {
        this.jetons = jetons;
    }

    private static ResponseEntity<Object> refus(int statut, String defi, String erreur, String description) {
        return ResponseEntity.status(statut).header("WWW-Authenticate", defi).body(Map.of("error", erreur, "error_description", description));
    }

    @GetMapping("/ressource")
    public ResponseEntity<Object> ressource(@RequestHeader(name = "Authorization", required = false) String auth, @RequestParam(name = "scope", required = false) String scope) {
        if (auth == null || !auth.startsWith("Bearer ")) {
            return refus(401, "Bearer realm=\"nexu-vina\"", "invalid_request", "jeton porteur absent");
        }
        JetonClient.Revendications claims = jetons.lire(auth.substring(7));
        if (claims == null) {
            return refus(401, "Bearer realm=\"nexu-vina\", error=\"invalid_token\"", "invalid_token", "jeton invalide ou expiré");
        }
        String portee = scope == null ? "read" : scope;
        if (!claims.scopes().contains(portee)) {
            return refus(403, "Bearer realm=\"nexu-vina\", error=\"insufficient_scope\", scope=\"" + portee + "\"", "insufficient_scope", "portée « " + portee + " » requise");
        }
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("client", claims.sub());
        r.put("tenantId", claims.tenantId());
        r.put("scopes", claims.scopes());
        r.put("message", "Bienvenue : le jeton client_credentials est valide.");
        return ResponseEntity.ok(r);
    }
}
