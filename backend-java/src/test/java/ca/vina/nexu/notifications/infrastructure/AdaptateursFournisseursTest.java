package ca.vina.nexu.notifications.infrastructure;

import ca.vina.nexu.notifications.infrastructure.configuration.ConfigurationFournisseurs;
import ca.vina.nexu.notifications.infrastructure.persistance.AppelHttp;
import ca.vina.nexu.notifications.infrastructure.persistance.ClientFournisseur;
import ca.vina.nexu.notifications.infrastructure.persistance.PousseeExpoHttp;
import ca.vina.nexu.notifications.infrastructure.persistance.PousseeHttp;
import ca.vina.nexu.notifications.infrastructure.persistance.SmsHttp;
import ca.vina.nexu.notifications.infrastructure.persistance.SmsTelnyx;
import ca.vina.nexu.notifications.infrastructure.persistance.Twilio;

import ca.vina.nexu.notifications.domaine.AbonnementsPoussee.Abonnement;
import ca.vina.nexu.notifications.domaine.Appels;
import ca.vina.nexu.notifications.domaine.PousseeWeb;
import ca.vina.nexu.notifications.domaine.Textos;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.http.HttpClient;
import java.security.SecureRandom;
import java.security.interfaces.ECPublicKey;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("Adaptateurs de fournisseurs — passerelle HTTP, Twilio, Telnyx, poussée : requêtes conformes, erreurs nommées, délai borné")
class AdaptateursFournisseursTest {

    private static final String T = "2026-09-16T12:00:00.000Z";
    private FournisseurFactice f;
    private ClientFournisseur client;

    @BeforeEach
    void demarrer() throws IOException {
        f = new FournisseurFactice();
        client = new ClientFournisseur(HttpClient.newHttpClient(), 500);
    }

    @AfterEach
    void arreter() {
        f.close();
    }

    @Test
    @DisplayName("la passerelle SMS générique parle JSON avec Bearer, lit id et statut, nomme l'erreur du fournisseur")
    void smsHttp() {
        SmsHttp sms = new SmsHttp(client, f.url() + "/", "jeton-1", "NexuVINA", () -> T);
        f.repondre = r -> new FournisseurFactice.Reponse(201, "{\"id\":\"m-1\",\"status\":\"queued\"}");
        assertThat(sms.envoyer("418 555 0100", "Bonjour", null)).isEqualTo(new Textos.Envoi("m-1", "envoye", T));
        assertThat(f.derniere().chemin()).isEqualTo("/messages");
        assertThat(f.derniere().enTete("Authorization")).isEqualTo("Bearer jeton-1");
        assertThat(f.derniere().enTete("Content-type")).isEqualTo("application/json");
        assertThat(f.derniere().texte()).isEqualTo("{\"to\":\"+14185550100\",\"from\":\"NexuVINA\",\"body\":\"Bonjour\"}");
        sms.envoyer("418 555 0100", "Bonjour", "+15145550000");
        assertThat(f.derniere().texte()).contains("\"from\":\"+15145550000\"");
        f.repondre = r -> new FournisseurFactice.Reponse(200, "{\"status\":\"delivered\"}");
        assertThat(sms.statut("m 1")).isEqualTo("livre");
        assertThat(f.derniere().methode()).isEqualTo("GET");
        assertThat(f.derniere().chemin()).isEqualTo("/messages/m+1");
        f.repondre = r -> new FournisseurFactice.Reponse(502, "passerelle en panne");
        assertThatThrownBy(() -> sms.envoyer("4185550100", "x", null)).hasMessage("fournisseur SMS : 502 passerelle en panne");
        assertThatThrownBy(() -> sms.envoyer("12", "x", null)).hasMessage("numéro invalide : « 12 »");
        f.attenteMs = 1500;
        assertThatThrownBy(() -> sms.statut("m-1")).isInstanceOf(IllegalStateException.class).hasMessageStartingWith("fournisseur SMS injoignable");
    }

    @Test
    @DisplayName("Twilio : formulaire encodé et Basic ; l'erreur porte le code Twilio ; l'appel relie au nom du client")
    void twilio() {
        Twilio.Config config = new Twilio.Config(f.url(), "AC123", "secret", "+15145550000");
        Twilio.Sms sms = new Twilio.Sms(client, config, () -> T);
        f.repondre = r -> new FournisseurFactice.Reponse(201, "{\"sid\":\"SM1\",\"status\":\"queued\"}");
        assertThat(sms.envoyer("418-555-0100", "Allô", null)).isEqualTo(new Textos.Envoi("SM1", "envoye", T));
        assertThat(f.derniere().chemin()).isEqualTo("/2010-04-01/Accounts/AC123/Messages.json");
        assertThat(f.derniere().enTete("Authorization")).isEqualTo("Basic " + Base64.getEncoder().encodeToString("AC123:secret".getBytes()));
        assertThat(f.derniere().enTete("Content-type")).isEqualTo("application/x-www-form-urlencoded");
        assertThat(f.derniere().texte()).isEqualTo("To=%2B14185550100&From=%2B15145550000&Body=All%C3%B4");
        sms.envoyer("418-555-0100", "Allô", "+18195550000");
        assertThat(f.derniere().texte()).startsWith("To=%2B14185550100&From=%2B18195550000");
        f.repondre = r -> new FournisseurFactice.Reponse(200, "{\"sid\":\"SM1\",\"status\":\"delivered\"}");
        assertThat(sms.statut("SM1")).isEqualTo("livre");
        assertThat(f.derniere().chemin()).isEqualTo("/2010-04-01/Accounts/AC123/Messages/SM1.json");
        f.repondre = r -> new FournisseurFactice.Reponse(401, "{\"code\":20003,\"message\":\"Authenticate\"}");
        assertThatThrownBy(() -> sms.envoyer("4185550100", "x", null)).hasMessage("Twilio : 401 (code 20003) Authenticate");
        f.repondre = r -> new FournisseurFactice.Reponse(500, "pas du json");
        assertThatThrownBy(() -> sms.statut("SM1")).hasMessage("Twilio : 500 (code ?)");
        assertThat(new Twilio.Config(null, "a", "b", "c").base()).isEqualTo("https://api.twilio.com");
        assertThat(new Twilio.Config(" ", "a", "b", "c").base()).isEqualTo("https://api.twilio.com");
        // Le rappel de statut part avec le texto quand le déploiement se connaît (PUBLIC_API_URL).
        Twilio.Config avecRappel = ConfigurationFournisseurs.twilio(Map.of("TWILIO_URL", f.url(), "TWILIO_ACCOUNT_SID", "AC123", "TWILIO_AUTH_TOKEN", "secret", "TWILIO_FROM", "+15145550000", "PUBLIC_API_URL", "https://api.exemple.qc.ca/"));
        assertThat(avecRappel.statusCallback()).isEqualTo("https://api.exemple.qc.ca/api/notifications/sms/statut/twilio");
        f.repondre = r -> new FournisseurFactice.Reponse(201, "{\"sid\":\"SM2\",\"status\":\"queued\"}");
        new Twilio.Sms(client, avecRappel, () -> T).envoyer("4185550100", "x", null);
        assertThat(f.derniere().texte()).endsWith("&StatusCallback=https%3A%2F%2Fapi.exemple.qc.ca%2Fapi%2Fnotifications%2Fsms%2Fstatut%2Ftwilio");
        assertThat(ConfigurationFournisseurs.twilio(Map.of("TWILIO_ACCOUNT_SID", "a", "TWILIO_AUTH_TOKEN", "b", "TWILIO_FROM", "c")).statusCallback()).isNull();

        Twilio.Appel appel = new Twilio.Appel(client, config, () -> T);
        f.repondre = r -> new FournisseurFactice.Reponse(201, "{\"sid\":\"CA1\",\"status\":\"queued\"}");
        assertThat(appel.appeler("418 555 0100", "514 555 0100", "819 555 0100")).isEqualTo(new Appels.AppelSortant("CA1", "en_cours", T));
        assertThat(f.derniere().chemin()).isEqualTo("/2010-04-01/Accounts/AC123/Calls.json");
        assertThat(f.derniere().texte()).startsWith("To=%2B15145550100&From=%2B14185550100&Twiml=");
        assertThat(java.net.URLDecoder.decode(f.derniere().texte(), java.nio.charset.StandardCharsets.UTF_8)).contains("<Dial callerId=\"+14185550100\">+18195550100</Dial>");
        f.repondre = r -> new FournisseurFactice.Reponse(200, "{\"status\":\"completed\"}");
        assertThat(appel.statut("CA1")).isEqualTo("termine");
        assertThat(f.derniere().chemin()).isEqualTo("/2010-04-01/Accounts/AC123/Calls/CA1.json");
    }

    @Test
    @DisplayName("ST-G06 : l'appel vocal Twilio dit le message en français, attend une touche, rappelle le statut ; sans Twilio, il est simulé")
    void appelVocal() {
        Twilio.Config config = new Twilio.Config(f.url(), "AC123", "secret", "+15145550000");
        ca.vina.nexu.partage.telephonie.AppelsVocauxPort.Appel a = new ca.vina.nexu.partage.telephonie.AppelsVocauxPort.Appel("T", "ap-1", "+14185550100",
                "Alerte urgente : Code <bleu> & 5e", "https://x/voix?appel=ap-1", "https://x/statut?appel=ap-1");
        f.repondre = r -> new FournisseurFactice.Reponse(201, "{\"sid\":\"CA9\",\"status\":\"queued\"}");
        assertThat(new ca.vina.nexu.notifications.infrastructure.persistance.AppelsVocaux.ParTwilio(client, config).appeler(a)).isEqualTo("CA9");
        assertThat(f.derniere().chemin()).isEqualTo("/2010-04-01/Accounts/AC123/Calls.json");
        String corps = java.net.URLDecoder.decode(f.derniere().texte(), java.nio.charset.StandardCharsets.UTF_8);
        assertThat(corps).startsWith("To=+14185550100&From=+15145550000&Twiml=").contains("StatusCallback=https://x/statut?appel=ap-1")
                .contains("MachineDetection=Enable").contains("<Gather numDigits=\"1\" timeout=\"10\" method=\"POST\" action=\"https://x/voix?appel=ap-1\">")
                .contains("<Say language=\"fr-CA\">Alerte urgente : Code &lt;bleu&gt; &amp; 5e</Say>");
        ca.vina.nexu.notifications.infrastructure.persistance.AppelsVocaux.Simule simule = new ca.vina.nexu.notifications.infrastructure.persistance.AppelsVocaux.Simule();
        assertThat(simule.appeler(a)).isEqualTo("SIM-ap-1");
        assertThat(simule.passes()).containsExactly(a);
        assertThat(ConfigurationFournisseurs.appelsVocaux(Map.of(), client)).isInstanceOf(ca.vina.nexu.notifications.infrastructure.persistance.AppelsVocaux.Simule.class);
        assertThat(ConfigurationFournisseurs.appelsVocaux(Map.of("APPEL_FOURNISSEUR", "twilio", "TWILIO_ACCOUNT_SID", "a", "TWILIO_AUTH_TOKEN", "b"), client))
                .isInstanceOf(ca.vina.nexu.notifications.infrastructure.persistance.AppelsVocaux.ParTwilio.class);
        assertThat(ConfigurationFournisseurs.appelsVocaux(Map.of("APPEL_FOURNISSEUR", "http", "APPEL_URL", "https://x", "APPEL_JETON", "j"), client))
                .isInstanceOf(ca.vina.nexu.notifications.infrastructure.persistance.AppelsVocaux.Simule.class);
    }

    @Test
    @DisplayName("Telnyx : JSON avec Bearer, statut dans to[0], erreur nommée par son code")
    void telnyx() {
        SmsTelnyx sms = new SmsTelnyx(client, f.url(), "KEY", "+15145550000", () -> T);
        f.repondre = r -> new FournisseurFactice.Reponse(200, "{\"data\":{\"id\":\"t-1\",\"to\":[{\"status\":\"queued\"}]}}");
        assertThat(sms.envoyer("418 555 0100", "Salut", null)).isEqualTo(new Textos.Envoi("t-1", "envoye", T));
        assertThat(f.derniere().chemin()).isEqualTo("/v2/messages");
        assertThat(f.derniere().enTete("Authorization")).isEqualTo("Bearer KEY");
        assertThat(f.derniere().texte()).isEqualTo("{\"from\":\"+15145550000\",\"to\":\"+14185550100\",\"text\":\"Salut\"}");
        f.repondre = r -> new FournisseurFactice.Reponse(200, "{\"data\":{\"id\":\"t-1\",\"to\":[{\"status\":\"delivery_failed\"}]}}");
        assertThat(sms.statut("t-1")).isEqualTo("echoue");
        assertThat(f.derniere().chemin()).isEqualTo("/v2/messages/t-1");
        f.repondre = r -> new FournisseurFactice.Reponse(200, "{\"data\":{\"id\":\"t-2\"}}");
        assertThat(sms.envoyer("418 555 0100", "Salut", "+18195550000").statut()).isEqualTo("envoye");
        f.repondre = r -> new FournisseurFactice.Reponse(200, "{\"data\":\"x\"}");
        assertThat(sms.statut("t-1")).isEqualTo("envoye");
        f.repondre = r -> new FournisseurFactice.Reponse(422, "{\"errors\":[{\"code\":\"40001\",\"title\":\"Invalid to\"}]}");
        assertThatThrownBy(() -> sms.envoyer("4185550100", "x", null)).hasMessage("Telnyx : 422 (code 40001) Invalid to");
        f.repondre = r -> new FournisseurFactice.Reponse(500, "{\"errors\":[]}");
        assertThatThrownBy(() -> sms.envoyer("4185550100", "x", null)).hasMessage("Telnyx : 500 (code ?)");
        assertThat(new SmsTelnyx(client, null, "k", "f", () -> T)).isNotNull();
    }

    @Test
    @DisplayName("la passerelle d'appels générique : POST /calls, GET /calls/:id, erreur nommée")
    void appelHttp() {
        AppelHttp appel = new AppelHttp(client, f.url(), "jeton", () -> T);
        f.repondre = r -> new FournisseurFactice.Reponse(201, "{\"id\":\"c-1\",\"status\":\"ringing\"}");
        assertThat(appel.appeler("418 555 0100", "514 555 0100", "819 555 0100")).isEqualTo(new Appels.AppelSortant("c-1", "en_cours", T));
        assertThat(f.derniere().chemin()).isEqualTo("/calls");
        assertThat(f.derniere().texte()).isEqualTo("{\"from\":\"+14185550100\",\"agent\":\"+15145550100\",\"to\":\"+18195550100\"}");
        f.repondre = r -> new FournisseurFactice.Reponse(200, "{\"status\":\"busy\"}");
        assertThat(appel.statut("c-1")).isEqualTo("echoue");
        assertThat(f.derniere().chemin()).isEqualTo("/calls/c-1");
        f.repondre = r -> new FournisseurFactice.Reponse(503, "hors service");
        assertThatThrownBy(() -> appel.statut("c-1")).hasMessage("fournisseur d'appels : 503 hors service");
    }

    @Test
    @DisplayName("la poussée HTTP chiffre, signe et lit 201, 410 et l'échec ; injoignable, elle échoue sans lever")
    void poussee() throws Exception {
        SecureRandom alea = new SecureRandom();
        PousseeWeb.ClesVapid cles = PousseeWeb.genererClesVapid(alea, null);
        var navigateur = PousseeWeb.pairePhemere(alea);
        byte[] w = new byte[65];
        w[0] = 4;
        byte[] x = ((ECPublicKey) navigateur.getPublic()).getW().getAffineX().toByteArray();
        byte[] y = ((ECPublicKey) navigateur.getPublic()).getW().getAffineY().toByteArray();
        System.arraycopy(x, Math.max(0, x.length - 32), w, 1 + Math.max(0, 32 - x.length), Math.min(32, x.length));
        System.arraycopy(y, Math.max(0, y.length - 32), w, 33 + Math.max(0, 32 - y.length), Math.min(32, y.length));
        Abonnement abonnement = new Abonnement(f.url() + "/push/abc", Base64.getUrlEncoder().withoutPadding().encodeToString(w), Base64.getUrlEncoder().withoutPadding().encodeToString(new byte[16]));
        PousseeHttp poussee = new PousseeHttp(client, cles, Clock.fixed(Instant.parse("2026-09-16T12:00:00Z"), ZoneOffset.UTC), alea);
        PousseeWeb.Charge charge = new PousseeWeb.Charge("Titre", "Corps", "/app", "e");
        f.repondre = r -> new FournisseurFactice.Reponse(201, "");
        assertThat(poussee.envoyer(abonnement, charge)).isEqualTo(new PousseeWeb.Resultat("envoye", 201, null));
        assertThat(f.derniere().enTete("Content-encoding")).isEqualTo("aes128gcm");
        assertThat(f.derniere().enTete("Content-type")).isEqualTo("application/octet-stream");
        assertThat(f.derniere().enTete("Ttl")).isEqualTo("86400");
        assertThat(f.derniere().enTete("Authorization")).startsWith("vapid t=").contains(", k=" + cles.publique());
        assertThat(f.derniere().corps().length).isGreaterThan(86);
        f.repondre = r -> new FournisseurFactice.Reponse(410, "");
        assertThat(poussee.envoyer(abonnement, charge)).isEqualTo(new PousseeWeb.Resultat("expire", 410, "abonnement expiré"));
        f.repondre = r -> new FournisseurFactice.Reponse(404, "");
        assertThat(poussee.envoyer(abonnement, charge).statut()).isEqualTo("expire");
        f.repondre = r -> new FournisseurFactice.Reponse(500, "boum");
        assertThat(poussee.envoyer(abonnement, charge)).isEqualTo(new PousseeWeb.Resultat("echoue", 500, "boum"));
        f.attenteMs = 1500;
        PousseeWeb.Resultat r = poussee.envoyer(abonnement, charge);
        assertThat(r.statut()).isEqualTo("echoue");
        assertThat(r.code()).isNull();
        assertThat(r.detail()).startsWith("service de poussée injoignable");
    }

    @Test
    @DisplayName("la configuration branche ce que l'environnement dicte, et le simulateur sinon")
    void configuration() {
        assertThat(ConfigurationFournisseurs.porteurSms(Map.of(), client, () -> T)).isInstanceOf(Textos.SmsSimule.class);
        assertThat(ConfigurationFournisseurs.porteurSms(Map.of("SMS_FOURNISSEUR", "twilio", "TWILIO_ACCOUNT_SID", "a", "TWILIO_AUTH_TOKEN", "b", "TWILIO_FROM", "c"), client, () -> T)).isInstanceOf(Twilio.Sms.class);
        assertThat(ConfigurationFournisseurs.porteurSms(Map.of("SMS_FOURNISSEUR", "telnyx", "TELNYX_API_KEY", "a", "TELNYX_FROM", "b"), client, () -> T)).isInstanceOf(SmsTelnyx.class);
        assertThat(ConfigurationFournisseurs.porteurSms(Map.of("SMS_FOURNISSEUR", "http", "SMS_URL", "https://x", "SMS_JETON", "j", "SMS_EXPEDITEUR", "Moi"), client, () -> T)).isInstanceOf(SmsHttp.class);
        assertThatThrownBy(() -> ConfigurationFournisseurs.porteurSms(Map.of("SMS_FOURNISSEUR", "http"), client, () -> T)).hasMessage("SMS_FOURNISSEUR=http : variables manquantes SMS_URL, SMS_JETON");
        assertThat(ConfigurationFournisseurs.porteurAppel(Map.of(), client, () -> T)).isInstanceOf(Appels.AppelSimule.class);
        assertThat(ConfigurationFournisseurs.porteurAppel(Map.of("SMS_FOURNISSEUR", "twilio", "TWILIO_ACCOUNT_SID", "a", "TWILIO_AUTH_TOKEN", "b"), client, () -> T)).isInstanceOf(Twilio.Appel.class);
        assertThat(ConfigurationFournisseurs.porteurAppel(Map.of("APPEL_FOURNISSEUR", "http", "APPEL_URL", "https://x", "APPEL_JETON", "j"), client, () -> T)).isInstanceOf(AppelHttp.class);
        ConfigurationFournisseurs.ClesInstance tirees = ConfigurationFournisseurs.clesVapid(Map.of(), new SecureRandom());
        assertThat(tirees.configurees()).isFalse();
        assertThat(tirees.cles().sujet()).isEqualTo(PousseeWeb.SUJET_PAR_DEFAUT);
        ConfigurationFournisseurs.ClesInstance posees = ConfigurationFournisseurs.clesVapid(Map.of("PUSH_VAPID_PUBLIQUE", "pub", "PUSH_VAPID_PRIVEE", "priv", "PUSH_SUJET", "mailto:a@b.ca"), new SecureRandom());
        assertThat(posees).isEqualTo(new ConfigurationFournisseurs.ClesInstance(new PousseeWeb.ClesVapid("pub", "priv", "mailto:a@b.ca"), true));
        ConfigurationFournisseurs c = new ConfigurationFournisseurs();
        org.springframework.mock.env.MockEnvironment env = new org.springframework.mock.env.MockEnvironment();
        Clock horloge = Clock.systemUTC();
        assertThat(c.commutateurSms(env, c.clientFournisseur(), horloge).courant()).isInstanceOf(Textos.SmsSimule.class);
        assertThat(c.commutateurAppel(env, c.clientFournisseur(), horloge).courant()).isInstanceOf(Appels.AppelSimule.class);
        // Les ports vus par le reste du code suivent le commutateur : ce qui est branché répond, le défaut revient au débranchement.
        Textos.SmsSimule simule = new Textos.SmsSimule(List.of(), () -> T, "X");
        Textos.SmsSimule demo = new Textos.SmsSimule(List.of("+15550100199"), () -> T, "Y");
        var commutateurSms = new ca.vina.nexu.partage.demonstration.Commutateur<Textos.PorteurSms>(simule);
        Textos.PorteurSms sms = c.porteurSms(commutateurSms);
        commutateurSms.brancher(demo);
        Textos.Envoi e = sms.envoyer("+15550100199", "allo", null);
        assertThat(e.statut()).isEqualTo(Textos.ECHOUE);
        assertThat(sms.statut(e.id())).isEqualTo(Textos.ECHOUE);
        assertThat(demo.envois()).hasSize(1);
        assertThat(simule.envois()).isEmpty();
        Appels.AppelSimule appels = new Appels.AppelSimule(List.of(), () -> T);
        Appels.PorteurAppelSortant appel = c.porteurAppel(new ca.vina.nexu.partage.demonstration.Commutateur<>(appels));
        Appels.AppelSortant a = appel.appeler("+15550100100", "+15550100101", "+15550100102");
        assertThat(appel.statut(a.id())).isEqualTo(a.statut());
        assertThat(c.porteurPoussee(c.clesVapid(env), c.clientFournisseur(), horloge)).isInstanceOf(PousseeHttp.class);
        assertThat(ClientFournisseur.chemin(Map.of("a", List.of("x")), "a", "1")).isNull();
        assertThat(ClientFournisseur.chemin(Map.of("a", "x"), "a", "b")).isNull();
    }

    @Test
    @DisplayName("poussée native (Expo) : requête JSON avec jeton d'accès, ok → envoyé, appareil inconnu → périmé, refus nommé, erreur HTTP, délai dépassé")
    void pousseeNativeExpo() {
        PousseeWeb.Charge charge = new PousseeWeb.Charge("Titre", "Corps", "/admin/notifications", "notification");
        f.repondre = r -> new FournisseurFactice.Reponse(200, "{\"data\":{\"status\":\"ok\"}}");
        PousseeExpoHttp expo = new PousseeExpoHttp(client, f.url() + "/push/send", "secret-expo");
        assertThat(expo.envoyer("ExponentPushToken[abcdefgh12]", charge)).isEqualTo(new PousseeWeb.Resultat(PousseeWeb.ENVOYE, 200, null));
        FournisseurFactice.Requete envoyee = f.derniere();
        assertThat(envoyee.chemin()).isEqualTo("/push/send");
        assertThat(envoyee.enTete("Authorization")).isEqualTo("Bearer secret-expo");
        assertThat(envoyee.texte()).contains("\"to\":\"ExponentPushToken[abcdefgh12]\"").contains("\"title\":\"Titre\"").contains("\"url\":\"/admin/notifications\"").contains("\"sound\":\"default\"");
        // ST-G07 : une poussée critique part en priorité haute, sur le canal Android « critique », au niveau critique d'iOS.
        expo.envoyer("j", new PousseeWeb.Charge("Code bleu", "5e", "/admin/mesalertes", ca.vina.nexu.notifications.domaine.Evenements.ETIQUETTE_CRITIQUE));
        assertThat(f.derniere().texte()).contains("\"priority\":\"high\"").contains("\"channelId\":\"critique\"").contains("\"interruptionLevel\":\"critical\"")
                .contains("\"sound\":{\"critical\":true,\"name\":\"default\",\"volume\":1}").doesNotContain("\"sound\":\"default\"");
        assertThat(ca.vina.nexu.notifications.domaine.Evenements.etiquetteDe("alerte.critique")).isEqualTo("critique");
        assertThat(ca.vina.nexu.notifications.domaine.Evenements.etiquetteDe("alerte.recue")).isEqualTo("notification");
        assertThat(ca.vina.nexu.notifications.domaine.Evenements.etiquetteDe(null)).isEqualTo("notification");
        assertThat(ca.vina.nexu.notifications.domaine.Evenements.parCode("alerte.critique")).hasValueSatisfying(e -> assertThat(e.essentiel()).isTrue());
        f.repondre = r -> new FournisseurFactice.Reponse(200, "{\"data\":{\"status\":\"error\",\"details\":{\"error\":\"DeviceNotRegistered\"}}}");
        assertThat(expo.envoyer("j", charge).statut()).isEqualTo(PousseeWeb.EXPIRE);
        f.repondre = r -> new FournisseurFactice.Reponse(200, "{\"data\":{\"status\":\"error\",\"message\":\"Trop de messages\"}}");
        assertThat(expo.envoyer("j", charge)).isEqualTo(new PousseeWeb.Resultat(PousseeWeb.ECHOUE, 200, "Trop de messages"));
        f.repondre = r -> new FournisseurFactice.Reponse(503, "indisponible");
        assertThat(expo.envoyer("j", charge)).isEqualTo(new PousseeWeb.Resultat(PousseeWeb.ECHOUE, 503, "réponse 503"));
        // Sans jeton d'accès, aucun en-tête d'autorisation ; un délai dépassé est un échec nommé, jamais une exception.
        PousseeExpoHttp sansJeton = new PousseeExpoHttp(client, f.url() + "/push/send", " ");
        f.repondre = r -> new FournisseurFactice.Reponse(200, "{\"data\":{\"status\":\"ok\"}}");
        assertThat(sansJeton.envoyer("j", charge).statut()).isEqualTo(PousseeWeb.ENVOYE);
        assertThat(f.derniere().enTete("Authorization")).isNull();
        f.attenteMs = 1500;
        assertThat(sansJeton.envoyer("j", charge).statut()).isEqualTo(PousseeWeb.ECHOUE);
        f.attenteMs = 0;
    }
}
