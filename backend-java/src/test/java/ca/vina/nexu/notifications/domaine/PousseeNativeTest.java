package ca.vina.nexu.notifications.domaine;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("Notifications natives — jeton Expo, réponses du service, simulateur")
class PousseeNativeTest {

    @Test
    void jetonEtManques() {
        Map<String, Object> ok = new HashMap<>();
        ok.put("jeton", "ExpoPushToken[abcdefgh12]"); ok.put("plateforme", "web"); ok.put("nom", "  ");
        assertThat(PousseeNative.lire(ok)).isEqualTo(new PousseeNative.Appareil("ExpoPushToken[abcdefgh12]", "web", null));
        assertThatThrownBy(() -> PousseeNative.lire(null)).hasMessage("jeton : jeton de poussée Expo attendu (ExponentPushToken[…]) ; plateforme : l'une de ios, android, web");
        assertThatThrownBy(() -> PousseeNative.lire(Map.of("jeton", "ExponentPushToken[ab]", "plateforme", "ios", "nom", 3))).hasMessageContaining("jeton :").hasMessageContaining("nom : texte de 80");
    }

    @Test
    void reponsesDuService() {
        assertThat(PousseeNative.interpreter(503, false, null)).isEqualTo(new PousseeWeb.Resultat(PousseeWeb.ECHOUE, 503, "réponse 503"));
        assertThat(PousseeNative.interpreter(200, true, Map.of("data", Map.of("status", "ok")))).isEqualTo(new PousseeWeb.Resultat(PousseeWeb.ENVOYE, 200, null));
        assertThat(PousseeNative.interpreter(200, true, Map.of("data", Map.of("status", "error", "details", Map.of("error", "DeviceNotRegistered"))))).isEqualTo(new PousseeWeb.Resultat(PousseeWeb.EXPIRE, 200, "DeviceNotRegistered"));
        assertThat(PousseeNative.interpreter(200, true, Map.of("data", Map.of("status", "error", "message", "Trop de messages"))).detail()).isEqualTo("Trop de messages");
        assertThat(PousseeNative.interpreter(200, true, Map.of("data", Map.of("status", "error", "details", Map.of("error", "MessageTooBig")))).detail()).isEqualTo("MessageTooBig");
        assertThat(PousseeNative.interpreter(200, true, Map.of("data", Map.of("status", "error", "details", Map.of("error", "x".repeat(300))))).detail()).hasSize(200);
        assertThat(PousseeNative.interpreter(200, true, Map.of()).detail()).isEqualTo("réponse inattendue");
        assertThat(PousseeNative.interpreter(200, true, null).detail()).isEqualTo("réponse inattendue");
    }

    @Test
    void simulateur() {
        PousseeNative.Simulee s = new PousseeNative.Simulee().perimer("p").tomberEnPanne("e");
        PousseeWeb.Charge c = new PousseeWeb.Charge("t", "c", null, null);
        assertThat(s.envoyer("p", c).statut()).isEqualTo(PousseeWeb.EXPIRE);
        assertThat(s.envoyer("e", c).statut()).isEqualTo(PousseeWeb.ECHOUE);
        assertThat(s.envoyer("ok", c).statut()).isEqualTo(PousseeWeb.ENVOYE);
        assertThat(s.envois()).hasSize(3);
    }
}
