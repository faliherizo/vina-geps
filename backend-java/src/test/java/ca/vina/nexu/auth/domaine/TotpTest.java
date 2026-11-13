package ca.vina.nexu.auth.domaine;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("TOTP — vecteurs de la RFC 6238 et fenêtre de vérification")
class TotpTest {

    /** Le secret de référence de la RFC 6238 (« 12345678901234567890 ») en base32. */
    private static final String SECRET = Totp.encoderBase32("12345678901234567890".getBytes(StandardCharsets.US_ASCII));

    @Test
    void base32EstCeluiDeLaRfc4648() {
        assertThat(SECRET).isEqualTo("GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ");
        assertThat(Totp.encoderBase32("f".getBytes(StandardCharsets.US_ASCII))).isEqualTo("MY");
        assertThat(Totp.decoderBase32("my======")).isEqualTo("f".getBytes(StandardCharsets.US_ASCII));
        assertThat(Totp.decoderBase32("M-Y")).isEqualTo("f".getBytes(StandardCharsets.US_ASCII));
    }

    @Test
    void lesCodesDeLaRfcSontReproduits() {
        // Annexe B de la RFC 6238, SHA-1, huit chiffres tronqués à six par le Node : 59 s → 94287082.
        assertThat(Totp.code(SECRET, 59_000L)).isEqualTo("287082");
        assertThat(Totp.code(SECRET, 1_111_111_109_000L)).isEqualTo("081804");
        assertThat(Totp.code(SECRET, 1_234_567_890_000L)).isEqualTo("005924");
    }

    @Test
    void unePlateformeSansHmacEstUnDefautNomme() {
        assertThatThrownBy(() -> Totp.hmac("HmacInexistant", new byte[] {1}, new byte[] {2}))
                .isInstanceOf(IllegalStateException.class).hasMessage("HmacInexistant indisponible");
    }

    @Test
    void laVerificationAccepteLePasVoisinEtRefuseLeReste() {
        long maintenant = 1_234_567_890_000L;
        assertThat(Totp.verifier(SECRET, "005924", maintenant)).isTrue();
        assertThat(Totp.verifier(SECRET, Totp.code(SECRET, maintenant - 30_000), maintenant)).isTrue();
        assertThat(Totp.verifier(SECRET, Totp.code(SECRET, maintenant + 30_000), maintenant)).isTrue();
        assertThat(Totp.verifier(SECRET, Totp.code(SECRET, maintenant + 60_000), maintenant)).isFalse();
        assertThat(Totp.verifier(SECRET, "12345", maintenant)).isFalse();
        assertThat(Totp.verifier(SECRET, null, maintenant)).isFalse();
    }
}
