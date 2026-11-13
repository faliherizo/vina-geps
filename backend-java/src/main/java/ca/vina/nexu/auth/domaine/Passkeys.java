package ca.vina.nexu.auth.domaine;

import ca.vina.nexu.partage.crypto.Primitives;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.random.RandomGenerator;

/**
 * MFA <strong>FIDO2 / WebAuthn</strong> (passkeys) — exigence AO-5338 #93, en complément du TOTP.
 * La part pure des cérémonies : le défi, les options que le navigateur reçoit, la vérification
 * déléguée à un {@link Verificateur} (simulé par défaut ; un vérificateur cryptographique se branche
 * au déploiement), et la <strong>protection anti-rejeu</strong> par compteur croissant.
 */
public final class Passkeys {

    private Passkeys() {
    }

    public static final long DUREE_DEFI_MS = 5 * 60_000L;
    public static final String RP_ID_PAR_DEFAUT = "nexuvina.ca";
    public static final String RP_NOM = "Nexu VINA";

    public record Identifiant(String id, String credentialId, int counter, String label, String createdAt) {
        public Map<String, Object> vue() {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", id);
            m.put("credentialId", credentialId);
            m.put("counter", counter);
            m.put("label", label);
            m.put("createdAt", createdAt);
            return m;
        }
    }

    /** Un défi en attente : celui de l'enregistrement ou de l'authentification, et quand il a été émis. */
    public record Defi(String challenge, long creeLe) {
        public boolean expire(long maintenant) {
            return maintenant - creeLe > DUREE_DEFI_MS;
        }
    }

    public interface Verificateur {
        boolean enregistrement(Map<String, Object> reponse, String defiAttendu);

        boolean authentification(Map<String, Object> reponse, String defiAttendu, String clePubliqueStockee);
    }

    private static String texte(Map<String, Object> m, String cle) {
        Object v = m.get(cle);
        return v == null ? "" : String.valueOf(v);
    }

    /** Le vérificateur simulé : le défi doit être celui émis, et l'identifiant présent — ce qu'une démonstration peut prouver. */
    public static final Verificateur SIMULE = new Verificateur() {
        @Override
        public boolean enregistrement(Map<String, Object> reponse, String defiAttendu) {
            return defiAttendu.equals(texte(reponse, "challenge")) && !texte(reponse, "credentialId").isEmpty() && !texte(reponse, "publicKey").isEmpty();
        }

        @Override
        public boolean authentification(Map<String, Object> reponse, String defiAttendu, String clePubliqueStockee) {
            return defiAttendu.equals(texte(reponse, "challenge")) && !texte(reponse, "credentialId").isEmpty();
        }
    };

    public static String defi(RandomGenerator alea) {
        byte[] b = new byte[32];
        alea.nextBytes(b);
        return Primitives.base64Url(b);
    }

    public static Map<String, Object> optionsEnregistrement(String challenge, String rpId, String userId) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("challenge", challenge);
        m.put("rp", Map.of("id", rpId, "name", RP_NOM));
        m.put("user", Map.of("id", userId));
        m.put("pubKeyCredParams", List.of(param(-7), param(-257)));
        return m;
    }

    private static Map<String, Object> param(int alg) {
        Map<String, Object> p = new LinkedHashMap<>();
        p.put("type", "public-key");
        p.put("alg", alg);
        return p;
    }

    public static Map<String, Object> optionsAuthentification(String challenge, String rpId, List<Identifiant> identifiants) {
        if (identifiants.isEmpty()) {
            throw new IllegalStateException("no registered passkey for this user");
        }
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("challenge", challenge);
        m.put("rpId", rpId);
        m.put("allowCredentials", identifiants.stream().map(c -> {
            Map<String, Object> a = new LinkedHashMap<>();
            a.put("type", "public-key");
            a.put("id", c.credentialId());
            return a;
        }).toList());
        return m;
    }

    /** Le compteur de la réponse, ou 0 s'il manque — comme {@code resp.counter ?? 0}. */
    public static int compteur(Map<String, Object> reponse) {
        Object c = reponse.get("counter");
        if (c instanceof Number n) {
            return n.intValue();
        }
        try {
            return c == null ? 0 : (int) Double.parseDouble(String.valueOf(c));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    /** Anti-rejeu : un authentificateur incrémente son compteur à chaque assertion ; une valeur qui n'avance pas est une copie. */
    public static void exigerCompteurCroissant(int recu, int stocke) {
        if (recu <= stocke) {
            throw new IllegalStateException("replay detected (counter not incremented)");
        }
    }
}
