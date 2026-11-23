package ca.vina.nexu.alertes.infrastructure.web;

import ca.vina.nexu.alertes.application.commandes.GestionnaireAppelsAlertes;
import ca.vina.nexu.alertes.application.commandes.GestionnaireAppelsAlertes.Rappels;
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
    private final GestionnaireAppelsAlertes appels;
    private final Reglages reglages;

    public ControleurWebhooksAlertes(GestionnaireTextosEntrants textos, GestionnaireAppelsAlertes appels, Reglages reglages) {
        this.textos = textos;
        this.appels = appels;
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

    /** La touche pressée pendant l'appel (ST-G06) ; la réponse est ce que la voix dit. */
    @PostMapping("/voix")
    public ResponseEntity<Object> voix(HttpServletRequest requete, @RequestParam Map<String, String> params,
                                       @RequestHeader(name = "X-Twilio-Signature", required = false) String signature) {
        String appel = params.get(Rappels.PARAMETRE);
        if (!signee(requete, Rappels.CHEMIN_CHIFFRE, params, signature)) {
            return GardeAlertes.erreur(INTERDIT, "signature Twilio absente ou invalide");
        }
        try {
            return ResponseEntity.ok().contentType(TWIML).body(dire(appels.touche(appel, params.get("Digits"))));
        } catch (ca.vina.nexu.alertes.domaine.RefusAlerte e) {
            return GardeAlertes.erreur(GardeAlertes.statutDe(e.nature()), e.getMessage());
        }
    }

    /** La fin de l'appel : son résultat, sa durée. */
    @PostMapping("/voix/statut")
    public ResponseEntity<Object> statutVoix(HttpServletRequest requete, @RequestParam Map<String, String> params,
                                             @RequestHeader(name = "X-Twilio-Signature", required = false) String signature) {
        if (!signee(requete, Rappels.CHEMIN_STATUT, params, signature)) {
            return GardeAlertes.erreur(INTERDIT, "signature Twilio absente ou invalide");
        }
        appels.statut(params.get(Rappels.PARAMETRE), params.get("CallStatus"), params.get("AnsweredBy"), duree(params.get("CallDuration")));
        return ResponseEntity.ok().contentType(TWIML).body(twiml(null));
    }

    /**
     * Twilio signe l'URL complète, chaîne de requête comprise ({@code ?appel=…}), suivie des seuls paramètres du corps :
     * celui de la chaîne de requête, que Spring mêle aux autres, en est retiré.
     */
    private boolean signee(HttpServletRequest requete, String chemin, Map<String, String> params, String signature) {
        String appel = params.get(Rappels.PARAMETRE);
        String url = (reglages.urlPublique() != null ? reglages.urlPublique() + chemin : requete.getRequestURL().toString())
                + "?" + Rappels.PARAMETRE + "=" + appel;
        Map<String, String> corps = new java.util.HashMap<>(params);
        corps.remove(Rappels.PARAMETRE);
        return appel != null && Telephonie.signatureTwilioValide(url, corps, signature, reglages.jetonTwilio());
    }

    private static Integer duree(String brute) {
        try {
            return brute == null ? null : Integer.valueOf(brute);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** Ce que la voix dit, en TwiML. */
    static String dire(String texte) {
        return "<?xml version=\"1.0\" encoding=\"UTF-8\"?><Response><Say language=\"fr-CA\">" + echapper(texte) + "</Say></Response>";
    }

    private static String echapper(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    /** Une réponse TwiML : le texto à renvoyer, échappé ; vide s'il n'y a rien à dire. */
    static String twiml(String message) {
        if (message == null) {
            return "<?xml version=\"1.0\" encoding=\"UTF-8\"?><Response/>";
        }
        return "<?xml version=\"1.0\" encoding=\"UTF-8\"?><Response><Message>" + echapper(message) + "</Message></Response>";
    }
}
