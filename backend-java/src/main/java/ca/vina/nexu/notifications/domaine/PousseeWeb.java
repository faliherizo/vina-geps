package ca.vina.nexu.notifications.domaine;

import ca.vina.nexu.notifications.domaine.AbonnementsPoussee.Abonnement;
import ca.vina.nexu.partage.crypto.Primitives;
import ca.vina.nexu.partage.json.JsonMinimal;

import javax.crypto.Cipher;
import javax.crypto.KeyAgreement;
import javax.crypto.Mac;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.io.ByteArrayOutputStream;
import java.math.BigInteger;
import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.AlgorithmParameters;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.Signature;
import java.security.interfaces.ECPrivateKey;
import java.security.interfaces.ECPublicKey;
import java.security.spec.ECGenParameterSpec;
import java.security.spec.ECParameterSpec;
import java.security.spec.ECPoint;
import java.security.spec.ECPrivateKeySpec;
import java.security.spec.ECPublicKeySpec;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Notifications poussées Web — port de {@code notifications/pousseeWeb.ts} : RFC 8291
 * (chiffrement {@code aes128gcm}) et RFC 8292 (VAPID) sur le JCE, sans dépendance à auditer pour
 * cent lignes de mathématiques publiées. Un port sépare le chiffrement de l'acheminement : le
 * simulateur retient ce qui part, l'adaptateur HTTP livre au service de poussée du navigateur.
 */
public final class PousseeWeb {

    private PousseeWeb() {
    }

    public static final String ENVOYE = "envoye";
    public static final String ECHOUE = "echoue";
    public static final String EXPIRE = "expire";
    public static final String SUJET_PAR_DEFAUT = "mailto:soutien@nexu-vina.ca";

    public record Charge(String titre, String corps, String url, String etiquette) {
        public Map<String, Object> versJson() {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("titre", titre);
            m.put("corps", corps);
            if (url != null) {
                m.put("url", url);
            }
            if (etiquette != null) {
                m.put("etiquette", etiquette);
            }
            return m;
        }
    }

    public record Resultat(String statut, Integer code, String detail) {
    }

    public interface PorteurPoussee {
        Resultat envoyer(Abonnement abonnement, Charge charge);
    }

    /** La publique en point non compressé (65 octets), la privée en scalaire (32 octets), base64url. */
    public record ClesVapid(String publique, String privee, String sujet) {
    }

    public record MessageChiffre(byte[] corps, Map<String, String> enTetes) {
    }

    // ── Clés ───────────────────────────────────────────────────────────────

    private static byte[] deB64url(String s) {
        return Base64.getUrlDecoder().decode(s.replace('+', '-').replace('/', '_').replaceAll("=+$", ""));
    }

    private static final ECParameterSpec P256 = Primitives.surement("courbe P-256", () -> {
        AlgorithmParameters p = AlgorithmParameters.getInstance("EC");
        p.init(new ECGenParameterSpec("secp256r1"));
        return p.getParameterSpec(ECParameterSpec.class);
    });

    private static byte[] scalaire(BigInteger n) {
        byte[] b = n.toByteArray();
        byte[] r = new byte[32];
        System.arraycopy(b, Math.max(0, b.length - 32), r, Math.max(0, 32 - b.length), Math.min(32, b.length));
        return r;
    }

    private static byte[] pointNonCompresse(ECPublicKey cle) {
        byte[] p = new byte[65];
        p[0] = 4;
        System.arraycopy(scalaire(cle.getW().getAffineX()), 0, p, 1, 32);
        System.arraycopy(scalaire(cle.getW().getAffineY()), 0, p, 33, 32);
        return p;
    }

    private static PublicKey clePublique(byte[] point) {
        ECPoint w = new ECPoint(new BigInteger(1, Arrays.copyOfRange(point, 1, 33)), new BigInteger(1, Arrays.copyOfRange(point, 33, 65)));
        return Primitives.surement("clé publique P-256", () -> KeyFactory.getInstance("EC").generatePublic(new ECPublicKeySpec(w, P256)));
    }

    private static PrivateKey clePrivee(byte[] scalaire) {
        return Primitives.surement("clé privée P-256", () -> KeyFactory.getInstance("EC").generatePrivate(new ECPrivateKeySpec(new BigInteger(1, scalaire), P256)));
    }

    public static KeyPair pairePhemere(SecureRandom alea) {
        return Primitives.surement("paire P-256", () -> {
            KeyPairGenerator g = KeyPairGenerator.getInstance("EC");
            g.initialize(new ECGenParameterSpec("secp256r1"), alea);
            return g.generateKeyPair();
        });
    }

    public static ClesVapid genererClesVapid(SecureRandom alea, String sujet) {
        KeyPair paire = pairePhemere(alea);
        return new ClesVapid(Primitives.base64Url(pointNonCompresse((ECPublicKey) paire.getPublic())),
                Primitives.base64Url(scalaire(((ECPrivateKey) paire.getPrivate()).getS())), sujet == null ? SUJET_PAR_DEFAUT : sujet);
    }

    /** Le jeton VAPID : un JWT ES256 pour l'origine du service de poussée, valable douze heures. */
    public static String jetonVapid(ClesVapid cles, String endpoint, Instant maintenant) {
        URI u = URI.create(endpoint);
        String audience = u.getScheme() + "://" + u.getHost() + (u.getPort() == -1 ? "" : ":" + u.getPort());
        String entete = Primitives.base64Url("{\"typ\":\"JWT\",\"alg\":\"ES256\"}".getBytes(StandardCharsets.UTF_8));
        Map<String, Object> corps = new LinkedHashMap<>();
        corps.put("aud", audience);
        corps.put("exp", maintenant.getEpochSecond() + 12 * 3600);
        corps.put("sub", cles.sujet());
        String charge = Primitives.base64Url(JsonMinimal.ecrire(corps).getBytes(StandardCharsets.UTF_8));
        byte[] signature = Primitives.surement("signature ES256", () -> {
            Signature s = Signature.getInstance("SHA256withECDSAinP1363Format");
            s.initSign(clePrivee(deB64url(cles.privee())));
            s.update((entete + "." + charge).getBytes(StandardCharsets.US_ASCII));
            return s.sign();
        });
        return entete + "." + charge + "." + Primitives.base64Url(signature);
    }

    // ── Chiffrement (RFC 8291, aes128gcm) ──────────────────────────────────

    private static byte[] hkdf(byte[] sel, byte[] ikm, byte[] info, int longueur) {
        return Primitives.surement("HKDF-SHA256", () -> {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(sel, "HmacSHA256"));
            byte[] prk = mac.doFinal(ikm);
            mac.init(new SecretKeySpec(prk, "HmacSHA256"));
            mac.update(info);
            mac.update((byte) 1);
            return Arrays.copyOf(mac.doFinal(), longueur);
        });
    }

    private static byte[] concat(byte[]... parties) {
        ByteArrayOutputStream b = new ByteArrayOutputStream();
        for (byte[] p : parties) {
            b.writeBytes(p);
        }
        return b.toByteArray();
    }

    /** Chiffre la charge pour un abonnement ; sel et clé éphémère sont injectés pour qu'un essai rejoue un message connu. */
    public static MessageChiffre chiffrer(Abonnement abonnement, Charge charge, ClesVapid cles, byte[] sel, KeyPair ephemere, Instant maintenant) {
        byte[] clientPub = deB64url(abonnement.p256dh());
        byte[] auth = deB64url(abonnement.auth());
        if (clientPub.length != 65 || auth.length != 16) {
            throw new IllegalArgumentException("abonnement invalide : clés p256dh (65 octets) et auth (16 octets) attendues");
        }
        byte[] serveurPub = pointNonCompresse((ECPublicKey) ephemere.getPublic());
        byte[] secret = Primitives.surement("ECDH", () -> {
            KeyAgreement ka = KeyAgreement.getInstance("ECDH");
            ka.init(ephemere.getPrivate());
            ka.doPhase(clePublique(clientPub), true);
            return ka.generateSecret();
        });
        byte[] ikm = hkdf(auth, secret, concat("WebPush: info\0".getBytes(StandardCharsets.US_ASCII), clientPub, serveurPub), 32);
        byte[] cek = hkdf(sel, ikm, "Content-Encoding: aes128gcm\0".getBytes(StandardCharsets.US_ASCII), 16);
        byte[] nonce = hkdf(sel, ikm, "Content-Encoding: nonce\0".getBytes(StandardCharsets.US_ASCII), 12);
        // 0x02 : dernier enregistrement
        byte[] clair = concat(JsonMinimal.ecrire(charge.versJson()).getBytes(StandardCharsets.UTF_8), new byte[]{2});
        byte[] chiffre = Primitives.surement("AES-128-GCM", () -> {
            Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
            c.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(cek, "AES"), new GCMParameterSpec(128, nonce));
            return c.doFinal(clair);
        });
        byte[] corps = concat(sel, ByteBuffer.allocate(4).putInt(4096).array(), new byte[]{65}, serveurPub, chiffre);
        Map<String, String> enTetes = new LinkedHashMap<>();
        enTetes.put("content-type", "application/octet-stream");
        enTetes.put("content-encoding", "aes128gcm");
        enTetes.put("ttl", "86400");
        enTetes.put("urgency", "high");
        enTetes.put("authorization", "vapid t=" + jetonVapid(cles, abonnement.endpoint(), maintenant) + ", k=" + cles.publique());
        return new MessageChiffre(corps, enTetes);
    }

    // ── Porteurs ───────────────────────────────────────────────────────────

    /** Le simulateur retient les envois ; {@code echouer} et {@code expirer} rejouent les réponses d'un service réel. */
    public static final class PousseeSimulee implements PorteurPoussee {
        public record Trace(Abonnement abonnement, Charge charge) {
        }

        private final Set<String> echouer;
        private final Set<String> expirer;
        private final List<Trace> envois = new ArrayList<>();

        public PousseeSimulee(Set<String> echouer, Set<String> expirer) {
            this.echouer = echouer;
            this.expirer = expirer;
        }

        @Override
        public synchronized Resultat envoyer(Abonnement abonnement, Charge charge) {
            envois.add(new Trace(abonnement, charge));
            if (expirer.contains(abonnement.endpoint())) {
                return new Resultat(EXPIRE, 410, "abonnement expiré");
            }
            if (echouer.contains(abonnement.endpoint())) {
                return new Resultat(ECHOUE, 500, "service de poussée indisponible");
            }
            return new Resultat(ENVOYE, 201, null);
        }

        public synchronized List<Trace> envois() {
            return List.copyOf(envois);
        }
    }
}
