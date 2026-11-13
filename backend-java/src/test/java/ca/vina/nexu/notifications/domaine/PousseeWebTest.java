package ca.vina.nexu.notifications.domaine;

import ca.vina.nexu.notifications.domaine.AbonnementsPoussee.Abonnement;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import javax.crypto.Cipher;
import javax.crypto.KeyAgreement;
import javax.crypto.Mac;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.io.ByteArrayOutputStream;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.AlgorithmParameters;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.SecureRandom;
import java.security.Signature;
import java.security.interfaces.ECPublicKey;
import java.security.spec.ECGenParameterSpec;
import java.security.spec.ECParameterSpec;
import java.security.spec.ECPoint;
import java.security.spec.ECPublicKeySpec;
import java.time.Instant;
import java.util.Arrays;
import java.util.Base64;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("PousseeWeb — clés VAPID, jeton ES256 vérifiable, chiffrement aes128gcm que le navigateur saurait déchiffrer")
class PousseeWebTest {

    private static final SecureRandom ALEA = new SecureRandom();

    private static byte[] b64(String s) {
        return Base64.getUrlDecoder().decode(s);
    }

    private static byte[] point(ECPublicKey k) {
        byte[] p = new byte[65];
        p[0] = 4;
        byte[] x = k.getW().getAffineX().toByteArray();
        byte[] y = k.getW().getAffineY().toByteArray();
        System.arraycopy(x, Math.max(0, x.length - 32), p, 1 + Math.max(0, 32 - x.length), Math.min(32, x.length));
        System.arraycopy(y, Math.max(0, y.length - 32), p, 33 + Math.max(0, 32 - y.length), Math.min(32, y.length));
        return p;
    }

    private static byte[] hkdf(byte[] sel, byte[] ikm, byte[] info, int longueur) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(sel, "HmacSHA256"));
        byte[] prk = mac.doFinal(ikm);
        mac.init(new SecretKeySpec(prk, "HmacSHA256"));
        mac.update(info);
        mac.update((byte) 1);
        return Arrays.copyOf(mac.doFinal(), longueur);
    }

    @Test
    @DisplayName("une paire générée donne une publique de 65 octets et une privée de 32, et signe un jeton que la publique vérifie")
    void clesEtJeton() throws Exception {
        PousseeWeb.ClesVapid cles = PousseeWeb.genererClesVapid(ALEA, null);
        assertThat(b64(cles.publique())).hasSize(65);
        assertThat(b64(cles.privee())).hasSize(32);
        assertThat(cles.sujet()).isEqualTo("mailto:soutien@nexu-vina.ca");
        String jeton = PousseeWeb.jetonVapid(cles, "https://push.exemple.qc.ca:8443/abc/def", Instant.parse("2026-09-16T12:00:00Z"));
        String[] parties = jeton.split("\\.");
        assertThat(new String(b64(parties[0]), StandardCharsets.UTF_8)).isEqualTo("{\"typ\":\"JWT\",\"alg\":\"ES256\"}");
        assertThat(new String(b64(parties[1]), StandardCharsets.UTF_8)).isEqualTo("{\"aud\":\"https://push.exemple.qc.ca:8443\",\"exp\":" + (Instant.parse("2026-09-16T12:00:00Z").getEpochSecond() + 43200) + ",\"sub\":\"mailto:soutien@nexu-vina.ca\"}");
        AlgorithmParameters p = AlgorithmParameters.getInstance("EC");
        p.init(new ECGenParameterSpec("secp256r1"));
        ECParameterSpec spec = p.getParameterSpec(ECParameterSpec.class);
        byte[] pub = b64(cles.publique());
        ECPoint w = new ECPoint(new BigInteger(1, Arrays.copyOfRange(pub, 1, 33)), new BigInteger(1, Arrays.copyOfRange(pub, 33, 65)));
        Signature v = Signature.getInstance("SHA256withECDSAinP1363Format");
        v.initVerify(KeyFactory.getInstance("EC").generatePublic(new ECPublicKeySpec(w, spec)));
        v.update((parties[0] + "." + parties[1]).getBytes(StandardCharsets.US_ASCII));
        assertThat(v.verify(b64(parties[2]))).isTrue();
        assertThat(b64(parties[2])).hasSize(64);
    }

    @Test
    @DisplayName("le message chiffré se déchiffre avec les clés du navigateur et porte les en-têtes du protocole")
    void chiffrement() throws Exception {
        PousseeWeb.ClesVapid cles = PousseeWeb.genererClesVapid(ALEA, "mailto:x@y.ca");
        KeyPair navigateur = PousseeWeb.pairePhemere(ALEA);
        byte[] auth = new byte[16];
        ALEA.nextBytes(auth);
        Abonnement abonnement = new Abonnement("https://push.exemple.qc.ca/a1", Base64.getUrlEncoder().withoutPadding().encodeToString(point((ECPublicKey) navigateur.getPublic())), Base64.getUrlEncoder().withoutPadding().encodeToString(auth));
        byte[] sel = new byte[16];
        ALEA.nextBytes(sel);
        KeyPair ephemere = PousseeWeb.pairePhemere(ALEA);
        PousseeWeb.MessageChiffre m = PousseeWeb.chiffrer(abonnement, new PousseeWeb.Charge("Titre", "Corps", "/app", null), cles, sel, ephemere, Instant.parse("2026-09-16T12:00:00Z"));
        assertThat(m.enTetes()).containsEntry("content-encoding", "aes128gcm").containsEntry("ttl", "86400").containsEntry("urgency", "high").containsEntry("content-type", "application/octet-stream");
        assertThat(m.enTetes().get("authorization")).startsWith("vapid t=").endsWith(", k=" + cles.publique());
        byte[] corps = m.corps();
        assertThat(Arrays.copyOfRange(corps, 0, 16)).isEqualTo(sel);
        assertThat(Arrays.copyOfRange(corps, 16, 20)).isEqualTo(new byte[]{0, 0, 16, 0});
        assertThat(corps[20]).isEqualTo((byte) 65);
        byte[] serveurPub = Arrays.copyOfRange(corps, 21, 86);
        assertThat(serveurPub).isEqualTo(point((ECPublicKey) ephemere.getPublic()));
        // Côté navigateur (RFC 8291) : le même secret par ECDH, les mêmes dérivations, puis AES-GCM.
        AlgorithmParameters p = AlgorithmParameters.getInstance("EC");
        p.init(new ECGenParameterSpec("secp256r1"));
        ECParameterSpec spec = p.getParameterSpec(ECParameterSpec.class);
        ECPoint w = new ECPoint(new BigInteger(1, Arrays.copyOfRange(serveurPub, 1, 33)), new BigInteger(1, Arrays.copyOfRange(serveurPub, 33, 65)));
        KeyAgreement ka = KeyAgreement.getInstance("ECDH");
        ka.init(navigateur.getPrivate());
        ka.doPhase(KeyFactory.getInstance("EC").generatePublic(new ECPublicKeySpec(w, spec)), true);
        byte[] secret = ka.generateSecret();
        ByteArrayOutputStream info = new ByteArrayOutputStream();
        info.writeBytes("WebPush: info\0".getBytes(StandardCharsets.US_ASCII));
        info.writeBytes(point((ECPublicKey) navigateur.getPublic()));
        info.writeBytes(serveurPub);
        byte[] ikm = hkdf(auth, secret, info.toByteArray(), 32);
        byte[] cek = hkdf(sel, ikm, "Content-Encoding: aes128gcm\0".getBytes(StandardCharsets.US_ASCII), 16);
        byte[] nonce = hkdf(sel, ikm, "Content-Encoding: nonce\0".getBytes(StandardCharsets.US_ASCII), 12);
        Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
        c.init(Cipher.DECRYPT_MODE, new SecretKeySpec(cek, "AES"), new GCMParameterSpec(128, nonce));
        byte[] clair = c.doFinal(Arrays.copyOfRange(corps, 86, corps.length));
        assertThat(clair[clair.length - 1]).isEqualTo((byte) 2);
        assertThat(new String(Arrays.copyOf(clair, clair.length - 1), StandardCharsets.UTF_8)).isEqualTo("{\"titre\":\"Titre\",\"corps\":\"Corps\",\"url\":\"/app\"}");
        assertThatThrownBy(() -> PousseeWeb.chiffrer(new Abonnement("https://x", "AA", "AA"), new PousseeWeb.Charge("t", "c", null, "e"), cles, sel, ephemere, Instant.EPOCH))
                .hasMessage("abonnement invalide : clés p256dh (65 octets) et auth (16 octets) attendues");
        assertThatThrownBy(() -> PousseeWeb.chiffrer(new Abonnement("https://x", abonnement.p256dh(), "AA"), new PousseeWeb.Charge("t", "c", null, "e"), cles, sel, ephemere, Instant.EPOCH)).hasMessageStartingWith("abonnement invalide");
        assertThat(new PousseeWeb.Charge("t", "c", null, "e").versJson()).containsExactly(java.util.Map.entry("titre", "t"), java.util.Map.entry("corps", "c"), java.util.Map.entry("etiquette", "e"));
    }

    @Test
    @DisplayName("le simulateur retient les envois et rejoue l'expiration et l'échec")
    void simulateur() {
        PousseeWeb.PousseeSimulee s = new PousseeWeb.PousseeSimulee(Set.of("https://ko"), Set.of("https://expire"));
        PousseeWeb.Charge charge = new PousseeWeb.Charge("t", "c", null, null);
        assertThat(s.envoyer(new Abonnement("https://ok", "p", "a"), charge)).isEqualTo(new PousseeWeb.Resultat("envoye", 201, null));
        assertThat(s.envoyer(new Abonnement("https://ko", "p", "a"), charge)).isEqualTo(new PousseeWeb.Resultat("echoue", 500, "service de poussée indisponible"));
        assertThat(s.envoyer(new Abonnement("https://expire", "p", "a"), charge)).isEqualTo(new PousseeWeb.Resultat("expire", 410, "abonnement expiré"));
        assertThat(s.envois()).hasSize(3);
    }
}
