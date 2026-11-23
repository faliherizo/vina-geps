package ca.vina.nexu.partage.telephonie;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.Map;
import java.util.TreeMap;

/**
 * Ce que deux contextes savent du téléphone : un numéro en E.164, et la signature des requêtes de Twilio. Les
 * notifications l'emploient pour les rappels de statut, les alertes pour les textos et appels entrants (ST-G05, ST-G06).
 */
public final class Telephonie {

    private static final String HMAC_SHA1 = "HmacSHA1";
    private static final int CHIFFRES_NORD_AMERICAINS = 10;

    private Telephonie() {
    }

    /**
     * Un numéro nord-américain à dix chiffres, avec ou sans +1, espaces, tirets ou parenthèses ; ou un numéro
     * international E.164 (« + » puis 8 à 15 chiffres, le premier non nul). Nul s'il n'en est pas un.
     */
    public static String e164(String brut) {
        String texte = (brut == null ? "" : brut).trim();
        String chiffres = texte.replaceAll("\\D", "");
        if (texte.startsWith("+")) {
            return chiffres.matches("[1-9]\\d{7,14}") ? "+" + chiffres : null;
        }
        if (chiffres.length() == CHIFFRES_NORD_AMERICAINS) {
            return "+1" + chiffres;
        }
        if (chiffres.length() == CHIFFRES_NORD_AMERICAINS + 1 && chiffres.startsWith("1")) {
            return "+" + chiffres;
        }
        return null;
    }

    /**
     * La signature Twilio ({@code X-Twilio-Signature}) : HMAC-SHA1, clé = jeton d'authentification, sur l'URL complète
     * suivie des paramètres POST triés par nom, valeur accolée, en base64. Sans jeton, rien n'est accepté.
     */
    public static boolean signatureTwilioValide(String url, Map<String, String> params, String signature, String jeton) {
        if (jeton == null || jeton.isEmpty() || signature == null || signature.isEmpty()) {
            return false;
        }
        StringBuilder donnees = new StringBuilder(url);
        new TreeMap<>(params).forEach((k, v) -> donnees.append(k).append(v));
        return MessageDigest.isEqual(signer(donnees.toString(), jeton).getBytes(StandardCharsets.UTF_8), signature.getBytes(StandardCharsets.UTF_8));
    }

    /** La signature attendue : ce que Twilio calcule, et ce qu'un essai calcule pour se faire passer pour lui. */
    public static String signer(String donnees, String jeton) {
        return signer(donnees, jeton, HMAC_SHA1);
    }

    /** L'algorithme est une couture : sans elle, aucun essai ne prouverait qu'une JVM sans HMAC-SHA1 le dit au lieu de signer faux. */
    static String signer(String donnees, String jeton, String algorithme) {
        try {
            Mac mac = Mac.getInstance(algorithme);
            mac.init(new SecretKeySpec(jeton.getBytes(StandardCharsets.UTF_8), algorithme));
            return Base64.getEncoder().encodeToString(mac.doFinal(donnees.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(algorithme + " indisponible", e);
        }
    }
}
