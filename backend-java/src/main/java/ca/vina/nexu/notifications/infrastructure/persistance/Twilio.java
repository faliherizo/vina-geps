package ca.vina.nexu.notifications.infrastructure.persistance;

import ca.vina.nexu.notifications.domaine.Appels;
import ca.vina.nexu.notifications.domaine.Textos;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Les adaptateurs Twilio des ports SMS et appel sortant — l'API REST publiée
 * ({@code POST /2010-04-01/Accounts/{AccountSid}/Messages.json} et {@code /Calls.json}, formulaire
 * encodé, Basic {@code AccountSid:AuthToken}). Une erreur est nommée avec son code Twilio
 * ({@code 20003}, {@code 21211}, {@code 20429}…) : c'est ce que l'exploitant cherchera. Le Twilio
 * de démonstration embarqué se branche par {@code url} sur {@code /mock/twilio}.
 */
public final class Twilio {

    private Twilio() {
    }

    public static final String URL = "https://api.twilio.com";
    /** Le nom du fournisseur dans {@code APPEL_FOURNISSEUR} et {@code SMS_FOURNISSEUR}. */
    public static final String NOM = "twilio";

    /** {@code statusCallback} : l'URL que Twilio rappelle à chaque changement de statut ; absente, le statut se redemande à la main. */
    public record Config(String url, String accountSid, String authToken, String from, String statusCallback) {
        public Config(String url, String accountSid, String authToken, String from) {
            this(url, accountSid, authToken, from, null);
        }

        public String base() {
            return (url == null || url.isBlank() ? URL : url).replaceFirst("/$", "");
        }
    }

    /** Une requête Twilio : formulaire encodé, Basic ; l'erreur porte le code du fournisseur. */
    static Map<String, Object> requete(ClientFournisseur client, Config config, String chemin, Map<String, String> corps) {
        String url = config.base() + "/2010-04-01/Accounts/" + ClientFournisseur.encoder(config.accountSid()) + chemin;
        ClientFournisseur.Reponse r = client.appeler(url, Map.of("authorization", ClientFournisseur.basique(config.accountSid(), config.authToken())),
                "application/x-www-form-urlencoded", corps == null ? null : ClientFournisseur.formulaire(corps), "Twilio");
        Map<String, Object> j = r.json();
        if (!r.ok()) {
            throw new IllegalStateException(("Twilio : " + r.statut() + " (code " + (j.get("code") == null ? "?" : j.get("code")) + ") " + (j.get("message") == null ? "" : j.get("message"))).trim());
        }
        return j;
    }

    public static final class Sms implements Textos.PorteurSms {
        private final ClientFournisseur client;
        private final Config config;
        private final Supplier<String> horloge;

        public Sms(ClientFournisseur client, Config config, Supplier<String> horloge) {
            this.client = client;
            this.config = config;
            this.horloge = horloge;
        }

        @Override
        public Textos.Envoi envoyer(String numero, String texte, String expediteur) {
            String n = Textos.exigerNumeroEtTexte(numero, texte);
            Map<String, String> corps = new LinkedHashMap<>();
            corps.put("To", n);
            corps.put("From", expediteur == null ? config.from() : expediteur);
            corps.put("Body", texte);
            if (config.statusCallback() != null && !config.statusCallback().isEmpty()) {
                corps.put("StatusCallback", config.statusCallback());
            }
            Map<String, Object> j = requete(client, config, "/Messages.json", corps);
            return new Textos.Envoi(String.valueOf(j.get("sid")), Textos.traduireStatut(j.get("status")), horloge.get());
        }

        @Override
        public String statut(String envoiId) {
            return Textos.traduireStatut(requete(client, config, "/Messages/" + ClientFournisseur.encoder(envoiId) + ".json", null).get("status"));
        }
    }

    /** Twilio sonne l'agent en affichant le numéro du client, puis le TwiML relie l'agent au destinataire (ADR 0022). */
    public static final class Appel implements Appels.PorteurAppelSortant {
        private final ClientFournisseur client;
        private final Config config;
        private final Supplier<String> horloge;

        public Appel(ClientFournisseur client, Config config, Supplier<String> horloge) {
            this.client = client;
            this.config = config;
            this.horloge = horloge;
        }

        @Override
        public Appels.AppelSortant appeler(String expediteur, String agent, String destinataire) {
            Appels.Trio n = Appels.normaliserTrio(expediteur, agent, destinataire);
            Map<String, String> corps = new LinkedHashMap<>();
            corps.put("To", n.agent());
            corps.put("From", n.expediteur());
            corps.put("Twiml", Appels.twimlMiseEnRelation(n.expediteur(), n.destinataire()));
            Map<String, Object> j = requete(client, config, "/Calls.json", corps);
            return new Appels.AppelSortant(String.valueOf(j.get("sid")), Appels.traduireStatutAppel(j.get("status")), horloge.get());
        }

        @Override
        public String statut(String appelId) {
            return Appels.traduireStatutAppel(requete(client, config, "/Calls/" + ClientFournisseur.encoder(appelId) + ".json", null).get("status"));
        }
    }
}
