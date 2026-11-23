package ca.vina.nexu.notifications.infrastructure.persistance;

import ca.vina.nexu.partage.telephonie.AppelsVocauxPort;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * L'appel vocal automatique (FX-AO-20260810, ST-G06) : Twilio dit le message en synthèse française dans un
 * {@code <Gather>} qui attend une touche, puis rappelle l'URL du chiffre ; la fin d'appel rappelle l'URL du statut, avec
 * la détection de répondeur. Sans fournisseur de voix configuré, un simulateur trace l'appel sans rien composer.
 */
public final class AppelsVocaux {

    /** Combien de secondes la voix attend une touche. */
    static final int ATTENTE_TOUCHE_SECONDES = 10;
    static final String LANGUE = "fr-CA";

    private AppelsVocaux() {
    }

    /** Le TwiML de l'appel : le message, l'attente d'une touche, puis l'au revoir si rien n'est pressé. */
    public static String twiml(AppelsVocauxPort.Appel a) {
        return "<?xml version=\"1.0\" encoding=\"UTF-8\"?><Response><Gather numDigits=\"1\" timeout=\"" + ATTENTE_TOUCHE_SECONDES + "\" method=\"POST\" action=\""
                + xml(a.urlChiffre()) + "\"><Say language=\"" + LANGUE + "\">" + xml(a.message()) + "</Say></Gather><Say language=\"" + LANGUE
                + "\">Aucune touche reçue. Au revoir.</Say></Response>";
    }

    static String xml(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }

    /** Twilio : un appel du numéro de l'organisme, au TwiML ci-dessus, statut rappelé à la fin, répondeur détecté. */
    public static final class ParTwilio implements AppelsVocauxPort {
        private final ClientFournisseur client;
        private final Twilio.Config config;

        public ParTwilio(ClientFournisseur client, Twilio.Config config) {
            this.client = client;
            this.config = config;
        }

        @Override
        public String appeler(Appel a) {
            Map<String, String> corps = new LinkedHashMap<>();
            corps.put("To", a.numero());
            corps.put("From", config.from());
            corps.put("Twiml", twiml(a));
            corps.put("StatusCallback", a.urlStatut());
            corps.put("StatusCallbackEvent", "completed");
            corps.put("MachineDetection", "Enable");
            return String.valueOf(Twilio.requete(client, config, "/Calls.json", corps).get("sid"));
        }
    }

    /** Sans fournisseur : l'appel est tracé, rien n'est composé ; l'identifiant le dit. */
    public static final class Simule implements AppelsVocauxPort {
        private final List<Appel> passes = new ArrayList<>();

        @Override
        public synchronized String appeler(Appel a) {
            passes.add(a);
            return "SIM-" + a.reference();
        }

        public synchronized List<Appel> passes() {
            return List.copyOf(passes);
        }
    }
}
