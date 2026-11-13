package ca.vina.nexu.demonstration.infrastructure.web;

import ca.vina.nexu.demonstration.domaine.RecepteurWebhooksDemo;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * Le récepteur de webhooks de démonstration sur HTTP. Le corps est lu <strong>tel quel</strong> :
 * la signature couvre les octets envoyés, pas un JSON ré-encodé.
 */
@RestController
@RequestMapping("/mock/webhooks")
public class ControleurMockWebhooks {

    private final RecepteurWebhooksDemo recepteur;

    public ControleurMockWebhooks(RecepteurWebhooksDemo recepteur) {
        this.recepteur = recepteur;
    }

    @PostMapping("/recepteur")
    public ResponseEntity<Object> recevoir(@RequestBody(required = false) String corps,
                                           @RequestHeader(name = "X-Evenement", required = false) String evenement,
                                           @RequestHeader(name = "X-Livraison", required = false) String livraison,
                                           @RequestHeader(name = "X-Signature-256", required = false) String signature) {
        Map<String, Object> e = recepteur.recevoir(corps == null ? "\"\"" : corps, evenement, livraison, signature);
        return ResponseEntity.status((Integer) e.get("codeRendu")).body(Map.of("recu", true, "signatureValide", e.get("signatureValide")));
    }

    @GetMapping("/recepteur/journal")
    public ResponseEntity<Object> journal() {
        return ResponseEntity.ok(recepteur.journal());
    }

    @DeleteMapping("/recepteur/journal")
    public ResponseEntity<Object> vider() {
        recepteur.vider();
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/recepteur/journal.html")
    public ResponseEntity<Object> page() {
        return ResponseEntity.ok().contentType(MediaType.TEXT_HTML).body(recepteur.pageJournal());
    }

    @GetMapping("/recepteur/reglages")
    public ResponseEntity<Object> reglages() {
        return ResponseEntity.ok(recepteur.reglages());
    }

    @PutMapping("/recepteur/reglages")
    public ResponseEntity<Object> regler(@RequestBody(required = false) Map<String, Object> corps) {
        try {
            return ResponseEntity.ok(recepteur.regler(corps == null ? Map.of() : corps));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(400).body(Map.of("errors", List.of(e.getMessage())));
        }
    }
}
