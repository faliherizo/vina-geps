package ca.vina.nexu.notifications.infrastructure.configuration;

import ca.vina.nexu.notifications.infrastructure.persistance.AppelHttp;
import ca.vina.nexu.notifications.infrastructure.persistance.AppelsVocaux;
import ca.vina.nexu.notifications.infrastructure.persistance.ClientFournisseur;
import ca.vina.nexu.notifications.infrastructure.persistance.PousseeExpoHttp;
import ca.vina.nexu.notifications.infrastructure.persistance.PousseeHttp;
import ca.vina.nexu.notifications.infrastructure.persistance.SmsHttp;
import ca.vina.nexu.notifications.infrastructure.persistance.SmsTelnyx;
import ca.vina.nexu.notifications.infrastructure.persistance.Twilio;

import ca.vina.nexu.notifications.domaine.Appels;
import ca.vina.nexu.notifications.domaine.Consommation;
import ca.vina.nexu.notifications.domaine.Fournisseurs;
import ca.vina.nexu.notifications.domaine.JournalTextos;
import ca.vina.nexu.notifications.domaine.PousseeNative;
import ca.vina.nexu.notifications.domaine.PousseeWeb;
import ca.vina.nexu.notifications.domaine.Textos;
import ca.vina.nexu.partage.demonstration.Commutateur;
import ca.vina.nexu.partage.temps.Iso;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

import java.net.http.HttpClient;
import java.security.SecureRandom;
import java.time.Clock;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Au démarrage : brancher ce que l'environnement dicte — textos, appels, poussée Web. Sans
 * variables, les simulateurs restent ; une configuration incomplète arrête le démarrage.
 */
@Configuration
public class ConfigurationFournisseurs {

    static final List<String> VARIABLES = List.of("SMS_FOURNISSEUR", "APPEL_FOURNISSEUR", "TWILIO_URL", "TWILIO_ACCOUNT_SID", "TWILIO_AUTH_TOKEN", "TWILIO_FROM",
            "TELNYX_URL", "TELNYX_API_KEY", "TELNYX_FROM", "SMS_URL", "SMS_JETON", "SMS_EXPEDITEUR", "APPEL_URL", "APPEL_JETON", "PUSH_VAPID_PUBLIQUE", "PUSH_VAPID_PRIVEE", "PUSH_SUJET",
            "PUBLIC_API_URL", "SMS_QUOTA_MENSUEL", "COURRIEL_QUOTA_MENSUEL");

    /** Les clés VAPID de l'instance et le fait qu'elles viennent de l'environnement (sinon une paire tirée au démarrage, que le journal dit). */
    public record ClesInstance(PousseeWeb.ClesVapid cles, boolean configurees) {
    }

    static Map<String, String> lire(Environment env) {
        Map<String, String> m = new HashMap<>();
        for (String v : VARIABLES) {
            m.put(v, env.getProperty(v, ""));
        }
        return m;
    }

    @Bean
    public ClientFournisseur clientFournisseur() {
        return new ClientFournisseur(HttpClient.newHttpClient(), 10_000);
    }

    /** Le port des textos, permutable à chaud par la démonstration ; le reste du code ne voit que {@link #porteurSms}. */
    @Bean
    public Commutateur<Textos.PorteurSms> commutateurSms(Environment env, ClientFournisseur client, Clock horloge) {
        return new Commutateur<>(porteurSms(lire(env), client, () -> Iso.de(horloge.instant())));
    }

    @Bean
    public Textos.PorteurSms porteurSms(Commutateur<Textos.PorteurSms> commutateur) {
        return new Textos.PorteurSms() {
            @Override
            public Textos.Envoi envoyer(String numero, String texte, String expediteur) {
                return commutateur.courant().envoyer(numero, texte, expediteur);
            }

            @Override
            public String statut(String envoiId) {
                return commutateur.courant().statut(envoiId);
            }
        };
    }

    public static Textos.PorteurSms porteurSms(Map<String, String> env, ClientFournisseur client, Supplier<String> horloge) {
        Fournisseurs.Choix choix = Fournisseurs.exigerChoix(env, "SMS_FOURNISSEUR");
        String expediteur = env.getOrDefault("SMS_EXPEDITEUR", "").isEmpty() ? Textos.EXPEDITEUR_PAR_DEFAUT : env.get("SMS_EXPEDITEUR");
        if (choix == null) {
            return new Textos.SmsSimule(List.of(), horloge, expediteur);
        }
        return switch (choix.fournisseur()) {
            case "twilio" -> new Twilio.Sms(client, twilio(env), horloge);
            case "telnyx" -> new SmsTelnyx(client, env.get("TELNYX_URL"), env.get("TELNYX_API_KEY"), env.get("TELNYX_FROM"), horloge);
            default -> new SmsHttp(client, env.get("SMS_URL"), env.get("SMS_JETON"), expediteur, horloge);
        };
    }

    public static Twilio.Config twilio(Map<String, String> env) {
        return new Twilio.Config(env.get("TWILIO_URL"), env.get("TWILIO_ACCOUNT_SID"), env.get("TWILIO_AUTH_TOKEN"), env.get("TWILIO_FROM"), JournalTextos.urlRappelTwilio(env));
    }

    /** Ce que le journal des textos sait du déploiement : fournisseur nommé, limite d'instance, rappel et jeton qui le signe. */
    @Bean
    public JournalTextos.Reglages reglagesTextos(Environment env) {
        return JournalTextos.Reglages.de(lire(env));
    }

    /** Le plafond de courriels par défaut — même variable que le Node, pour que les deux piles s'accordent. */
    @Bean
    public Consommation.ReglagesCourriel reglagesCourriel(Environment env) {
        return Consommation.ReglagesCourriel.de(lire(env));
    }

    @Bean
    public Commutateur<Appels.PorteurAppelSortant> commutateurAppel(Environment env, ClientFournisseur client, Clock horloge) {
        return new Commutateur<>(porteurAppel(lire(env), client, () -> Iso.de(horloge.instant())));
    }

    @Bean
    public Appels.PorteurAppelSortant porteurAppel(Commutateur<Appels.PorteurAppelSortant> commutateur) {
        return new Appels.PorteurAppelSortant() {
            @Override
            public Appels.AppelSortant appeler(String expediteur, String agent, String destinataire) {
                return commutateur.courant().appeler(expediteur, agent, destinataire);
            }

            @Override
            public String statut(String appelId) {
                return commutateur.courant().statut(appelId);
            }
        };
    }

    /**
     * L'appel vocal automatique des alertes (ST-G06) : par Twilio si c'est le fournisseur d'appel choisi — la saisie
     * d'une touche est du TwiML —, sinon simulé.
     */
    @Bean
    public ca.vina.nexu.partage.telephonie.AppelsVocauxPort appelsVocaux(Environment env, ClientFournisseur client) {
        return appelsVocaux(lire(env), client);
    }

    public static ca.vina.nexu.partage.telephonie.AppelsVocauxPort appelsVocaux(Map<String, String> env, ClientFournisseur client) {
        Fournisseurs.Choix choix = Fournisseurs.exigerChoix(env, "APPEL_FOURNISSEUR");
        return choix != null && Twilio.NOM.equals(choix.fournisseur()) ? new AppelsVocaux.ParTwilio(client, twilio(env)) : new AppelsVocaux.Simule();
    }

    public static Appels.PorteurAppelSortant porteurAppel(Map<String, String> env, ClientFournisseur client, Supplier<String> horloge) {
        Fournisseurs.Choix choix = Fournisseurs.exigerChoix(env, "APPEL_FOURNISSEUR");
        if (choix == null) {
            return new Appels.AppelSimule(List.of(), horloge);
        }
        return choix.fournisseur().equals("twilio") ? new Twilio.Appel(client, twilio(env), horloge) : new AppelHttp(client, env.get("APPEL_URL"), env.get("APPEL_JETON"), horloge);
    }

    @Bean
    public ClesInstance clesVapid(Environment env) {
        return clesVapid(lire(env), new SecureRandom());
    }

    public static ClesInstance clesVapid(Map<String, String> env, SecureRandom alea) {
        String publique = env.getOrDefault("PUSH_VAPID_PUBLIQUE", "");
        String privee = env.getOrDefault("PUSH_VAPID_PRIVEE", "");
        String sujet = env.getOrDefault("PUSH_SUJET", "").isEmpty() ? PousseeWeb.SUJET_PAR_DEFAUT : env.get("PUSH_SUJET");
        if (publique.isEmpty() || privee.isEmpty()) {
            return new ClesInstance(PousseeWeb.genererClesVapid(alea, sujet), false);
        }
        return new ClesInstance(new PousseeWeb.ClesVapid(publique, privee, sujet), true);
    }

    @Bean
    public PousseeWeb.PorteurPoussee porteurPoussee(ClesInstance cles, ClientFournisseur client, Clock horloge) {
        return new PousseeHttp(client, cles.cles(), horloge, new SecureRandom());
    }

    /** Les notifications natives (refonte E ★) : le service d'Expo si {@code EXPO_PUSH_URL} ou {@code EXPO_ACCESS_TOKEN} est réglé, sinon le simulateur. */
    @Bean
    public PousseeNative.PorteurNatif porteurNatif(ClientFournisseur client, Environment env) {
        String url = env.getProperty("EXPO_PUSH_URL");
        String jeton = env.getProperty("EXPO_ACCESS_TOKEN");
        boolean reel = (url != null && !url.isBlank()) || (jeton != null && !jeton.isBlank());
        return reel ? new PousseeExpoHttp(client, url == null || url.isBlank() ? "https://exp.host/--/api/v2/push/send" : url, jeton) : new PousseeNative.Simulee();
    }
}
