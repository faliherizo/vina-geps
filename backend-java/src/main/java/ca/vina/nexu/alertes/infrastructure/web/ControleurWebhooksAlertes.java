package ca.vina.nexu.alertes.infrastructure.web;

import ca.vina.nexu.alertes.application.commandes.GestionnaireTextosEntrants;
import ca.vina.nexu.alertes.domaine.TextosEntrants;
import ca.vina.nexu.partage.telephonie.Telephonie;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * {@code /api/alertes/webhooks} — ce que le fournisseur de téléphonie (Twilio) rappelle, sans session : la garde est la
 * <strong>signature</strong> ({@code X-Twilio-Signature}), vérifiée avant toute lecture ; le débit est borné par le
 * pare-feu applicatif, comme toute route. La réponse est du TwiML : le texto renvoyé à la personne.
 */
@RestController
@RequestMapping("/api/alertes/webhooks")
public class ControleurWebhooksAlertes {

    public static final String CHEMIN_TEXTO = "/api/alertes/webhooks/texto";
    private static final int INTERDIT = 403;
    private static final int INVALIDE = 400;
    private static final MediaType TWIML = MediaType.APPLICATION_XML;

    /** L'URL publique sous laquelle Twilio signe (nulle : celle de la requête) et le jeton qui signe. */
    public record Reglages(String urlPublique, String jetonTwilio) { }

    private final GestionnaireTextosEntrants textos;
    private final Reglages reglages;

    public ControleurWebhooksAlertes(GestionnaireTextosEntrants textos, Reglages reglages) {
        this.textos = textos;
        this.reglages = reglages;
    }

    @PostMapping("/texto")
    public ResponseEntity<Object> texto(HttpServletRequest requete, @RequestParam Map<String, String> params,
                                        @RequestHeader(name = "X-Twilio-Signature", required = false) String signature) {
        String url = reglages.urlPublique() != null ? reglages.urlPublique() + CHEMIN_TEXTO : requete.getRequestURL().toString();
        if (!Telephonie.signatureTwilioValide(url, params, signature, reglages.jetonTwilio())) {
            return GardeAlertes.erreur(INTERDIT, "signature Twilio absente ou invalide");
        }
        String sid = params.get("MessageSid");
        String de = params.get("From");
        if (sid == null || sid.isBlank() || de == null || de.isBlank()) {
            return GardeAlertes.erreur(INVALIDE, "MessageSid et From requis");
        }
        GestionnaireTextosEntrants.Resultat r = textos.recevoir(new TextosEntrants.Entrant(sid, de, params.get("Body")));
        return ResponseEntity.ok().contentType(TWIML).body(twiml(r.message()));
    }

    /** Une réponse TwiML : le texto à renvoyer, échappé ; vide s'il n'y a rien à dire. */
    static String twiml(String message) {
        if (message == null) {
            return "<?xml version=\"1.0\" encoding=\"UTF-8\"?><Response/>";
        }
        String echappe = message.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
        return "<?xml version=\"1.0\" encoding=\"UTF-8\"?><Response><Message>" + echappe + "</Message></Response>";
    }
}
