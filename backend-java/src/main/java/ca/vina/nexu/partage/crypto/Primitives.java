package ca.vina.nexu.partage.crypto;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.MessageDigest;
import java.security.PrivateKey;
import java.security.Signature;
import java.util.Base64;

/**
 * Les primitives cryptographiques que le domaine emploie, sans leurs exceptions vérifiées : un
 * algorithme du JDK absent ({@code SHA-256}, {@code HmacSHA256}, {@code RSA}) est une panne de
 * plateforme, pas un cas métier — elle devient une {@link IllegalStateException} ici, une seule
 * fois, plutôt qu'un bloc {@code catch} identique dans chaque classe qui hache ou signe.
 */
public final class Primitives {

    private Primitives() {
    }

    /** Un calcul qui peut lever une exception vérifiée du JDK. */
    @FunctionalInterface
    public interface Calcul<T> {
        T calculer() throws Exception;
    }

    /** Exécute le calcul ; une exception vérifiée devient une {@link IllegalStateException} nommant l'opération. */
    public static <T> T surement(String operation, Calcul<T> calcul) {
        try {
            return calcul.calculer();
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException(operation + " : " + e.getMessage(), e);
        }
    }

    public static byte[] sha256(byte[] donnees) {
        return surement("SHA-256", () -> MessageDigest.getInstance("SHA-256").digest(donnees));
    }

    public static byte[] hmacSha256(String secret, byte[] donnees) {
        return surement("HmacSHA256", () -> {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return mac.doFinal(donnees);
        });
    }

    public static KeyPair rsa2048() {
        return surement("RSA", () -> {
            KeyPairGenerator g = KeyPairGenerator.getInstance("RSA");
            g.initialize(2048);
            return g.generateKeyPair();
        });
    }

    public static byte[] signerRs256(PrivateKey cle, byte[] donnees) {
        return surement("SHA256withRSA", () -> {
            Signature s = Signature.getInstance("SHA256withRSA");
            s.initSign(cle);
            s.update(donnees);
            return s.sign();
        });
    }

    public static String base64Url(byte[] b) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(b);
    }
}
