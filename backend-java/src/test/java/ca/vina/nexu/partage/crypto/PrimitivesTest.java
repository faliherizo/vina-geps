package ca.vina.nexu.partage.crypto;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.Signature;
import java.util.HexFormat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("Primitives — les algorithmes du JDK, sans exception vérifiée")
class PrimitivesTest {

    @Test
    void hacheSigneEtEncode() throws Exception {
        assertThat(HexFormat.of().formatHex(Primitives.sha256("abc".getBytes(StandardCharsets.UTF_8)))).isEqualTo("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad");
        assertThat(HexFormat.of().formatHex(Primitives.hmacSha256("key", "The quick brown fox jumps over the lazy dog".getBytes(StandardCharsets.UTF_8))))
                .isEqualTo("f7bc83f430538424b13298e6aa6fb143ef4d59a14946175997479dbc2d1a3cd8");
        assertThat(Primitives.base64Url(new byte[] {(byte) 0xfb, (byte) 0xff})).isEqualTo("-_8");
        KeyPair cles = Primitives.rsa2048();
        byte[] signature = Primitives.signerRs256(cles.getPrivate(), "donnees".getBytes(StandardCharsets.UTF_8));
        Signature v = Signature.getInstance("SHA256withRSA");
        v.initVerify(cles.getPublic());
        v.update("donnees".getBytes(StandardCharsets.UTF_8));
        assertThat(v.verify(signature)).isTrue();
    }

    @Test
    void unePanneDePlateformeDevientUneIllegalState() {
        assertThatThrownBy(() -> Primitives.surement("op", () -> {
            throw new java.security.NoSuchAlgorithmException("absent");
        })).isInstanceOf(IllegalStateException.class).hasMessage("op : absent");
        assertThatThrownBy(() -> Primitives.surement("op", () -> {
            throw new IllegalArgumentException("métier");
        })).isInstanceOf(IllegalArgumentException.class).hasMessage("métier");
    }
}
