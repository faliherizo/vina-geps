package ca.vina.nexu.partage.telephonie;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("Téléphonie partagée — E.164 et signature Twilio")
class TelephonieTest {

    @Test
    @DisplayName("un numéro nord-américain ou international devient E.164 ; le reste n'en est pas un")
    void e164() {
        assertThat(Telephonie.e164("(514) 555-0101")).isEqualTo("+15145550101");
        assertThat(Telephonie.e164("1 514 555 0101")).isEqualTo("+15145550101");
        assertThat(Telephonie.e164("+33 1 23 45 67 89")).isEqualTo("+33123456789");
        assertThat(Telephonie.e164("+0123")).isNull();
        assertThat(Telephonie.e164("555-0101")).isNull();
        assertThat(Telephonie.e164(null)).isNull();
    }

    @Test
    @DisplayName("la signature de Twilio se vérifie ; sans jeton ni signature, rien ne passe ; sans l'algorithme, l'erreur le dit")
    void signature() {
        Map<String, String> p = Map.of("From", "+15145550101", "Body", "1");
        String signee = Telephonie.signer("https://x/cBody1From+15145550101", "jeton");
        assertThat(Telephonie.signatureTwilioValide("https://x/c", p, signee, "jeton")).isTrue();
        assertThat(Telephonie.signatureTwilioValide("https://x/c", p, signee, "autre")).isFalse();
        assertThat(Telephonie.signatureTwilioValide("https://x/c", p, null, "jeton")).isFalse();
        assertThat(Telephonie.signatureTwilioValide("https://x/c", p, signee, "")).isFalse();
        assertThat(Telephonie.signatureTwilioValide("https://x/c", p, "", "jeton")).isFalse();
        assertThat(Telephonie.signatureTwilioValide("https://x/c", p, signee, null)).isFalse();
        assertThatThrownBy(() -> Telephonie.signer("x", "jeton", "HmacInexistant")).isInstanceOf(IllegalStateException.class)
                .hasMessage("HmacInexistant indisponible");
    }
}
