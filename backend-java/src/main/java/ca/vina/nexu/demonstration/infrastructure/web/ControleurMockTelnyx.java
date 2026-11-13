package ca.vina.nexu.demonstration.infrastructure.web;

import ca.vina.nexu.demonstration.domaine.TelnyxDemo;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/** Le Telnyx de démonstration sur HTTP : JSON, jeton porteur, erreurs à la forme Telnyx. */
@RestController
@RequestMapping("/mock/telnyx")
public class ControleurMockTelnyx {

    private final TelnyxDemo telnyx;

    public ControleurMockTelnyx(TelnyxDemo telnyx) {
        this.telnyx = telnyx;
    }

    private static ResponseEntity<Object> refus() {
        return ResponseEntity.status(401).body(TelnyxDemo.erreurs("10009", "Authentication failed", "The API key is missing, malformed or revoked."));
    }

    @PostMapping("/v2/messages")
    public ResponseEntity<Object> creer(@RequestHeader(name = "Authorization", required = false) String auth, @RequestBody(required = false) Map<String, Object> corps) {
        if (!TelnyxDemo.authentifie(auth)) {
            return refus();
        }
        TelnyxDemo.Reponse r = telnyx.creerMessage(corps == null ? Map.of() : corps);
        return ResponseEntity.status(r.statut()).body(r.corps());
    }

    @GetMapping("/v2/messages/{id}")
    public ResponseEntity<Object> lire(@PathVariable("id") String id, @RequestHeader(name = "Authorization", required = false) String auth) {
        if (!TelnyxDemo.authentifie(auth)) {
            return refus();
        }
        Map<String, Object> m = telnyx.lireMessage(id);
        return m == null ? ResponseEntity.status(404).body(TelnyxDemo.erreurs("10005", "Resource not found", "No message with id '" + id + "'.")) : ResponseEntity.ok(Map.of("data", m));
    }
}
