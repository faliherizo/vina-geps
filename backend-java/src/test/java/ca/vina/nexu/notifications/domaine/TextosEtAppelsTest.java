package ca.vina.nexu.notifications.domaine;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("Textos et appels — numéros normalisés, statuts ramenés à trois, simulateurs qui retiennent")
class TextosEtAppelsTest {

    @Test
    @DisplayName("un numéro nord-américain se normalise en +1 ; texte requis et borné")
    void numerosEtTextes() {
        assertThat(Textos.normaliserNumero("(418) 555-0100")).isEqualTo("+14185550100");
        assertThat(Textos.normaliserNumero("1 418 555 0100")).isEqualTo("+14185550100");
        assertThat(Textos.normaliserNumero("+1-418-555-0100")).isEqualTo("+14185550100");
        assertThat(Textos.normaliserNumero("555-0100")).isNull();
        assertThat(Textos.normaliserNumero(null)).isNull();
        assertThat(Textos.normaliserNumero("24185550100")).isNull();
        // E.164 : « + » puis 8 à 15 chiffres, le premier non nul.
        assertThat(Textos.normaliserNumero("+33 6 12 34 56 78")).isEqualTo("+33612345678");
        assertThat(Textos.normaliserNumero("+261 34 12 345 67")).isEqualTo("+261341234567");
        assertThat(Textos.normaliserNumero("+0123456789")).isNull();
        assertThat(Textos.normaliserNumero("+1234567")).isNull();
        assertThat(Textos.normaliserNumero("+1234567890123456")).isNull();
        assertThat(Textos.validerTexte("Bonjour")).isEmpty();
        assertThat(Textos.validerTexte(" ")).containsExactly("texte requis");
        assertThat(Textos.validerTexte("x".repeat(481))).containsExactly("texte trop long (481 > 480)");
        assertThat(Textos.validerTexte(null)).containsExactly("texte requis");
        assertThat(Textos.traduireStatut(42)).isEqualTo("envoye");
        assertThat(Textos.traduireStatutTelnyx(null)).isEqualTo("envoye");
        assertThat(Textos.traduireStatutTelnyx("sending_failed")).isEqualTo("echoue");
        assertThat(Appels.traduireStatutAppel(null)).isEqualTo("en_cours");
        assertThatThrownBy(() -> Textos.exigerNumeroEtTexte("12", "x")).hasMessage("numéro invalide : « 12 »");
        assertThatThrownBy(() -> Textos.exigerNumeroEtTexte("4185550100", "")).hasMessage("texte requis");
        assertThat(Textos.traduireStatut("Delivered")).isEqualTo("livre");
        assertThat(Textos.traduireStatut("undelivered")).isEqualTo("echoue");
        assertThat(Textos.traduireStatut(null)).isEqualTo("envoye");
        assertThat(Textos.traduireStatutTelnyx("delivery_failed")).isEqualTo("echoue");
        assertThat(Textos.traduireStatutTelnyx("read")).isEqualTo("livre");
    }

    @Test
    @DisplayName("le simulateur SMS retient l'envoi, échoue sur les injoignables et sert le statut")
    void smsSimule() {
        Textos.SmsSimule sms = new Textos.SmsSimule(List.of("+14185550199"), () -> "2026-09-16T12:00:00.000Z", "NexuVINA");
        Textos.Envoi ok = sms.envoyer("418-555-0100", "Bonjour", null);
        assertThat(ok).isEqualTo(new Textos.Envoi("sim-1", "envoye", "2026-09-16T12:00:00.000Z"));
        Textos.Envoi ko = sms.envoyer("418-555-0199", "Bonjour", "+15145550000");
        assertThat(ko.statut()).isEqualTo("echoue");
        assertThat(sms.envois()).extracting(Textos.SmsSimule.Trace::expediteur).containsExactly("NexuVINA", "+15145550000");
        assertThat(sms.statut("sim-2")).isEqualTo("echoue");
        assertThatThrownBy(() -> sms.statut("x")).hasMessage("envoi introuvable");
    }

    @Test
    @DisplayName("un appel a trois numéros, chacun nommé s'il est illisible ; le TwiML relie au nom du client")
    void appels() {
        assertThat(Appels.normaliserTrio("418 555 0100", "514 555 0100", "819 555 0100")).isEqualTo(new Appels.Trio("+14185550100", "+15145550100", "+18195550100"));
        assertThatThrownBy(() -> Appels.normaliserTrio("x", "5145550100", "8195550100")).hasMessage("numéro du client invalide : « x »");
        assertThatThrownBy(() -> Appels.normaliserTrio("4185550100", "y", "8195550100")).hasMessage("numéro de l'agent invalide : « y »");
        assertThatThrownBy(() -> Appels.normaliserTrio("4185550100", "5145550100", "z")).hasMessage("numéro du destinataire invalide : « z »");
        assertThat(Appels.traduireStatutAppel("completed")).isEqualTo("termine");
        assertThat(Appels.traduireStatutAppel("no-answer")).isEqualTo("echoue");
        assertThat(Appels.traduireStatutAppel("queued")).isEqualTo("en_cours");
        assertThat(Appels.twimlMiseEnRelation("+14185550100", "<a&\"b>")).isEqualTo("<?xml version=\"1.0\" encoding=\"UTF-8\"?><Response><Say language=\"fr-CA\">Mise en relation par Nexu VINA.</Say><Dial callerId=\"+14185550100\">&lt;a&amp;&quot;b&gt;</Dial></Response>");
        Appels.AppelSimule appel = new Appels.AppelSimule(List.of("+18195550199"), () -> "t");
        assertThat(appel.appeler("4185550100", "5145550100", "8195550100")).isEqualTo(new Appels.AppelSortant("sim-appel-1", "en_cours", "t"));
        assertThat(appel.appeler("4185550100", "5145550100", "8195550199").statut()).isEqualTo("echoue");
        assertThat(appel.appels()).hasSize(2);
        assertThat(appel.statut("sim-appel-2")).isEqualTo("echoue");
        assertThatThrownBy(() -> appel.statut("x")).hasMessage("appel introuvable");
    }

    @Test
    @DisplayName("le choix du fournisseur vient de la configuration et nomme ce qui manque")
    void fournisseurs() {
        assertThat(Fournisseurs.lireChoix(Map.of(), "SMS_FOURNISSEUR")).isNull();
        assertThat(Fournisseurs.lireChoix(Map.of("SMS_FOURNISSEUR", "Twilio"), "SMS_FOURNISSEUR")).isEqualTo(new Fournisseurs.Choix("twilio", List.of("TWILIO_ACCOUNT_SID", "TWILIO_AUTH_TOKEN", "TWILIO_FROM")));
        assertThat(Fournisseurs.lireChoix(Map.of("SMS_FOURNISSEUR", "twilio", "TWILIO_ACCOUNT_SID", "a", "TWILIO_AUTH_TOKEN", "b"), "APPEL_FOURNISSEUR")).isEqualTo(new Fournisseurs.Choix("twilio", List.of()));
        assertThat(Fournisseurs.lireChoix(Map.of("SMS_FOURNISSEUR", "telnyx", "TELNYX_API_KEY", "k"), "SMS_FOURNISSEUR").manquantes()).containsExactly("TELNYX_FROM");
        assertThat(Fournisseurs.lireChoix(Map.of("APPEL_FOURNISSEUR", "http"), "APPEL_FOURNISSEUR").manquantes()).containsExactly("APPEL_URL", "APPEL_JETON");
        assertThat(Fournisseurs.lireChoix(Map.of("SMS_FOURNISSEUR", "http", "SMS_URL", "u", "SMS_JETON", "j"), "SMS_FOURNISSEUR").manquantes()).isEmpty();
        assertThatThrownBy(() -> Fournisseurs.lireChoix(Map.of("SMS_FOURNISSEUR", "vonage"), "SMS_FOURNISSEUR")).hasMessage("SMS_FOURNISSEUR inconnu : « vonage » (admis : twilio, telnyx, http)");
        assertThatThrownBy(() -> Fournisseurs.lireChoix(Map.of("APPEL_FOURNISSEUR", "telnyx"), "APPEL_FOURNISSEUR")).hasMessage("APPEL_FOURNISSEUR=telnyx : l'appel sortant n'est pris en charge que par twilio ou http");
        assertThatThrownBy(() -> Fournisseurs.exigerChoix(Map.of("SMS_FOURNISSEUR", "telnyx"), "SMS_FOURNISSEUR")).hasMessage("SMS_FOURNISSEUR=telnyx : variables manquantes TELNYX_API_KEY, TELNYX_FROM");
        assertThat(Fournisseurs.exigerChoix(Map.of(), "SMS_FOURNISSEUR")).isNull();
        // E-TN-8 : une adresse de fournisseur part en https ; le http n'est admis que vers un simulateur local.
        assertThatThrownBy(() -> Fournisseurs.exigerChoix(Map.of("SMS_FOURNISSEUR", "http", "SMS_URL", "http://sms.exemple.qc", "SMS_JETON", "j"), "SMS_FOURNISSEUR"))
                .hasMessage("SMS_URL doit être en https (E-TN-8) : « http://sms.exemple.qc »");
        assertThatThrownBy(() -> Fournisseurs.exigerChoix(Map.of("SMS_FOURNISSEUR", "twilio", "TWILIO_ACCOUNT_SID", "a", "TWILIO_AUTH_TOKEN", "b", "TWILIO_FROM", "c", "TWILIO_URL", "ftp://twilio"), "SMS_FOURNISSEUR"))
                .hasMessageContaining("TWILIO_URL doit être en https");
        assertThatThrownBy(() -> Fournisseurs.exigerChoix(Map.of("SMS_FOURNISSEUR", "http", "SMS_URL", "https://a b", "SMS_JETON", "j"), "SMS_FOURNISSEUR"))
                .hasMessageContaining("SMS_URL illisible");
        assertThat(Fournisseurs.exigerChoix(Map.of("SMS_FOURNISSEUR", "http", "SMS_URL", "http://127.0.0.1:9000/sms", "SMS_JETON", "j"), "SMS_FOURNISSEUR")).isNotNull();
        assertThat(Fournisseurs.exigerChoix(Map.of("SMS_FOURNISSEUR", "http", "SMS_URL", "http://localhost/sms", "SMS_JETON", "j"), "SMS_FOURNISSEUR")).isNotNull();
        assertThat(Fournisseurs.exigerChoix(Map.of("SMS_FOURNISSEUR", "http", "SMS_URL", "HTTPS://sms.exemple.qc", "SMS_JETON", "j", "APPEL_URL", " "), "SMS_FOURNISSEUR")).isNotNull();
        assertThatThrownBy(() -> Fournisseurs.exigerChoix(Map.of("SMS_FOURNISSEUR", "http", "SMS_URL", "sms.exemple.qc", "SMS_JETON", "j"), "SMS_FOURNISSEUR"))
                .hasMessageContaining("doit être en https");
        assertThat(Fournisseurs.lireChoix(Map.of("APPEL_FOURNISSEUR", "", "SMS_FOURNISSEUR", ""), "APPEL_FOURNISSEUR")).isNull();
    }

    @Test
    @DisplayName("les gabarits substituent {{cle}} et un envoi se valide dans l'ordre du Node")
    void notifications() {
        assertThat(Notifications.rendre("Bonjour {{ nom }}, {{jours}} — {{absent}}", Map.of("nom", "Léa", "jours", 3))).isEqualTo("Bonjour Léa, 3 — ");
        assertThat(Notifications.rendre("sans variable", null)).isEqualTo("sans variable");
        assertThat(Notifications.rendre("{{x}}", null)).isEmpty();
        assertThat(Notifications.valider(new Notifications.Demande(null, "teacher", List.of("e1"), "S", "C", null))).containsExactly("channel must be one of email, sms");
        assertThat(Notifications.valider(new Notifications.Demande("sms", null, List.of("e1"), "S", "C", null))).containsExactly("recipientType must be one of teacher, establishment, employee");
        assertThat(Notifications.valider(new Notifications.Demande("sms", "teacher", null, "S", "C", null))).containsExactly("recipients[] required");
        assertThat(Notifications.valider(new Notifications.Demande("sms", "teacher", List.of("e1"), null, "C", null))).containsExactly("subject required");
        assertThat(Notifications.valider(new Notifications.Demande("sms", "teacher", List.of("e1"), "S", " ", null))).containsExactly("body required");
        assertThat(Notifications.valider(new Notifications.Demande("email", "teacher", List.of("e1"), "Sujet", "Corps", null))).isEmpty();
        assertThat(Notifications.valider(new Notifications.Demande("fax", "teacher", List.of("e1"), "S", "C", null))).containsExactly("channel must be one of email, sms");
        assertThat(Notifications.valider(new Notifications.Demande("sms", "parent", List.of("e1"), "S", "C", null))).containsExactly("recipientType must be one of teacher, establishment, employee");
        assertThat(Notifications.valider(new Notifications.Demande("sms", "teacher", List.of(), "S", "C", null))).containsExactly("recipients[] required");
        assertThat(Notifications.valider(new Notifications.Demande("sms", "teacher", List.of("e1"), " ", "C", null))).containsExactly("subject required");
        assertThat(Notifications.valider(new Notifications.Demande("sms", "teacher", List.of("e1"), "S", null, null))).containsExactly("body required");
    }
}
