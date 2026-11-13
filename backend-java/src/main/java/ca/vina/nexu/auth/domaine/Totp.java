package ca.vina.nexu.auth.domaine;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.ByteArrayOutputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.ByteBuffer;
import java.security.GeneralSecurityException;
import java.util.Locale;
import java.util.random.RandomGenerator;
import java.util.regex.Pattern;

/**
 * TOTP (RFC 6238) — port de {@code backend/src/auth/totp.ts} : secret en base32 (RFC 4648),
 * HMAC-SHA1, pas de 30 s, six chiffres, une fenêtre de ± 1 pas à la vérification.
 *
 * <p>L'instant est un paramètre : le domaine n'a pas d'horloge. Un code émis par l'application
 * d'authentification de l'utilisateur doit être accepté par les deux backends pendant
 * l'étranglement, d'où un port bit à bit, y compris l'encodage base32.
 */
public final class Totp {

    public static final int PAS_SECONDES = 30;
    public static final int CHIFFRES = 6;
    private static final String ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567";
    private static final Pattern SIX_CHIFFRES = Pattern.compile("^\\d{6}$");

    private Totp() {
    }

    /** Un secret de vingt octets tirés au sort, en base32 — ce que l'application d'authentification saisit. */
    public static String secretAleatoire(RandomGenerator alea) {
        byte[] b = new byte[20];
        alea.nextBytes(b);
        return encoderBase32(b);
    }

    /** {@code encodeURIComponent} : ce que le Node met dans l'URI, caractère pour caractère — une application lit le libellé tel quel. */
    static String encoder(String s) {
        return URLEncoder.encode(s, StandardCharsets.UTF_8).replace("+", "%20").replace("%21", "!").replace("%27", "'").replace("%28", "(").replace("%29", ")").replace("%7E", "~");
    }

    /** L'URI {@code otpauth://} complète, avec algorithme, chiffres et période explicites. */
    public static String urlOtpauth(String label, String secretBase32, String issuer) {
        return "otpauth://totp/" + encoder(issuer) + ":" + encoder(label) + "?secret=" + secretBase32 + "&issuer=" + encoder(issuer) + "&algorithm=SHA1&digits=6&period=30";
    }

    /** La forme compacte pour le code QR (libellé tronqué à 32 caractères, valeurs par défaut implicites) : elle tient en version 6. */
    public static String urlOtpauthCompacte(String label, String secretBase32, String issuer) {
        String court = label.length() > 32 ? label.substring(0, 32) : label;
        return "otpauth://totp/" + encoder(issuer) + ":" + encoder(court) + "?secret=" + secretBase32 + "&issuer=" + encoder(issuer);
    }

    public static String encoderBase32(byte[] octets) {
        StringBuilder sortie = new StringBuilder();
        int bits = 0;
        int valeur = 0;
        for (byte b : octets) {
            valeur = (valeur << 8) | (b & 0xff);
            bits += 8;
            while (bits >= 5) {
                sortie.append(ALPHABET.charAt((valeur >>> (bits - 5)) & 31));
                bits -= 5;
            }
        }
        if (bits > 0) {
            sortie.append(ALPHABET.charAt((valeur << (5 - bits)) & 31));
        }
        return sortie.toString();
    }

    static byte[] decoderBase32(String s) {
        String propre = s.replaceAll("=+$", "").toUpperCase(Locale.ROOT);
        ByteArrayOutputStream sortie = new ByteArrayOutputStream();
        int bits = 0;
        int valeur = 0;
        for (char c : propre.toCharArray()) {
            int index = ALPHABET.indexOf(c);
            if (index < 0) {
                continue;
            }
            valeur = (valeur << 5) | index;
            bits += 5;
            if (bits >= 8) {
                sortie.write((valeur >>> (bits - 8)) & 0xff);
                bits -= 8;
            }
        }
        return sortie.toByteArray();
    }

    /** Le code à cet instant (millisecondes depuis l'époque). */
    public static String code(String secretBase32, long instantMs) {
        long compteur = Math.floorDiv(instantMs / 1000, PAS_SECONDES);
        byte[] message = ByteBuffer.allocate(8).putLong(compteur).array();
        byte[] empreinte = hmac("HmacSHA1", decoderBase32(secretBase32), message);
        int decalage = empreinte[empreinte.length - 1] & 0xf;
        int valeur = ((empreinte[decalage] & 0x7f) << 24)
                | ((empreinte[decalage + 1] & 0xff) << 16)
                | ((empreinte[decalage + 2] & 0xff) << 8)
                | (empreinte[decalage + 3] & 0xff);
        int modulo = (int) Math.pow(10, CHIFFRES);
        return String.format(Locale.ROOT, "%0" + CHIFFRES + "d", valeur % modulo);
    }

    /** Vrai si le code correspond au pas courant ou à l'un de ses voisins immédiats. */
    public static boolean verifier(String secretBase32, String code, long instantMs) {
        if (code == null || !SIX_CHIFFRES.matcher(code).matches()) {
            return false;
        }
        for (int fenetre = -1; fenetre <= 1; fenetre++) {
            if (code(secretBase32, instantMs + fenetre * PAS_SECONDES * 1000L).equals(code)) {
                return true;
            }
        }
        return false;
    }

    /** L'algorithme est un paramètre pour que le défaut de plateforme (JCA sans HMAC) soit vérifiable. */
    static byte[] hmac(String algorithme, byte[] cle, byte[] message) {
        try {
            Mac mac = Mac.getInstance(algorithme);
            mac.init(new SecretKeySpec(cle, algorithme));
            return mac.doFinal(message);
        } catch (GeneralSecurityException e) {
            // HmacSHA1 est obligatoire dans toute JVM (JCA) : cette branche est un défaut de
            // plateforme, pas un cas métier.
            throw new IllegalStateException(algorithme + " indisponible", e);
        }
    }
}
