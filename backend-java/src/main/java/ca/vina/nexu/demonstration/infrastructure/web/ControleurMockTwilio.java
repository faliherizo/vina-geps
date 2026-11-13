package ca.vina.nexu.demonstration.infrastructure.web;

import ca.vina.nexu.demonstration.domaine.TwilioDemo;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/** Le Twilio de démonstration sur HTTP : formulaires, authentification Basic, erreurs à la forme Twilio. */
@RestController
@RequestMapping("/mock/twilio")
public class ControleurMockTwilio {

    private final TwilioDemo twilio;

    public ControleurMockTwilio(TwilioDemo twilio) {
        this.twilio = twilio;
    }

    private static ResponseEntity<Object> refus() {
        return ResponseEntity.status(401).body(TwilioDemo.erreur(401, 20003, "Authenticate"));
    }

    private static ResponseEntity<Object> introuvable(String chemin) {
        return ResponseEntity.status(404).body(TwilioDemo.erreur(404, 20404, "The requested resource " + chemin + " was not found"));
    }

    @PostMapping("/2010-04-01/Accounts/{sid}/Messages.json")
    public ResponseEntity<Object> creerMessage(@PathVariable("sid") String sid, @RequestHeader(name = "Authorization", required = false) String auth, @RequestParam Map<String, String> corps) {
        if (!TwilioDemo.authentifie(auth, sid)) {
            return refus();
        }
        TwilioDemo.Reponse r = twilio.creerMessage(corps, sid);
        return ResponseEntity.status(r.statut()).body(r.corps());
    }

    @GetMapping("/2010-04-01/Accounts/{sid}/Messages/{msid}.json")
    public ResponseEntity<Object> lireMessage(@PathVariable("sid") String sid, @PathVariable("msid") String msid, @RequestHeader(name = "Authorization", required = false) String auth) {
        if (!TwilioDemo.authentifie(auth, sid)) {
            return refus();
        }
        Map<String, Object> m = twilio.lireMessage(msid);
        return m == null ? introuvable("/2010-04-01/Accounts/" + sid + "/Messages/" + msid + ".json") : ResponseEntity.ok(m);
    }

    @PostMapping("/2010-04-01/Accounts/{sid}/Calls.json")
    public ResponseEntity<Object> creerAppel(@PathVariable("sid") String sid, @RequestHeader(name = "Authorization", required = false) String auth, @RequestParam Map<String, String> corps) {
        if (!TwilioDemo.authentifie(auth, sid)) {
            return refus();
        }
        TwilioDemo.Reponse r = twilio.creerAppel(corps, sid);
        return ResponseEntity.status(r.statut()).body(r.corps());
    }

    @GetMapping("/2010-04-01/Accounts/{sid}/Calls/{casid}.json")
    public ResponseEntity<Object> lireAppel(@PathVariable("sid") String sid, @PathVariable("casid") String casid, @RequestHeader(name = "Authorization", required = false) String auth) {
        if (!TwilioDemo.authentifie(auth, sid)) {
            return refus();
        }
        Map<String, Object> a = twilio.lireAppel(casid);
        return a == null ? introuvable("/2010-04-01/Accounts/" + sid + "/Calls/" + casid + ".json") : ResponseEntity.ok(a);
    }
}
