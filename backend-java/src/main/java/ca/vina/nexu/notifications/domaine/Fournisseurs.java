package ca.vina.nexu.notifications.domaine;

import java.net.URI;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Le choix du fournisseur de textos et d'appels par la configuration — jamais par une condition
 * dans le code métier (port de {@code notifications/fournisseurs.ts}). {@code SMS_FOURNISSEUR}
 * vaut {@code twilio}, {@code telnyx} ou {@code http} ; absent, le simulateur reste le défaut.
 * {@code APPEL_FOURNISSEUR} suit la même règle, avec {@code SMS_FOURNISSEUR} pour défaut. Une
 * configuration incomplète arrête le démarrage plutôt que de laisser le simulateur faire croire que
 * les textos partent.
 */
public final class Fournisseurs {

    private Fournisseurs() {
    }

    public static final List<String> ADMIS = List.of("twilio", "telnyx", "http");

    /** Les adresses de fournisseur qu'un déploiement peut poser : chacune part en HTTPS (E-TN-8). */
    private static final List<String> ADRESSES = List.of("SMS_URL", "APPEL_URL", "TELNYX_URL", "TWILIO_URL");
    private static final String SCHEMA_CHIFFRE = "https";
    private static final String SCHEMA_LOCAL = "http";
    /** Un simulateur sur le poste ou dans l'essai : le seul cas où le texte clair ne quitte pas la machine. */
    private enum HoteLocal {
        NOM("localhost"), BOUCLE("127.0.0.1");

        private final String hote;

        HoteLocal(String hote) {
            this.hote = hote;
        }

        static boolean est(String hote) {
            return hote != null && java.util.Arrays.stream(values()).anyMatch(h -> h.hote.equals(hote));
        }
    }

    public record Choix(String fournisseur, List<String> manquantes) {
    }

    /** Ce que le fournisseur choisi exige, nommé — pour que l'erreur dise quoi configurer. */
    public static List<String> variablesRequises(String fournisseur, boolean pourAppel) {
        if (fournisseur.equals("twilio")) {
            return pourAppel ? List.of("TWILIO_ACCOUNT_SID", "TWILIO_AUTH_TOKEN") : List.of("TWILIO_ACCOUNT_SID", "TWILIO_AUTH_TOKEN", "TWILIO_FROM");
        }
        if (fournisseur.equals("telnyx")) {
            return List.of("TELNYX_API_KEY", "TELNYX_FROM");
        }
        return pourAppel ? List.of("APPEL_URL", "APPEL_JETON") : List.of("SMS_URL", "SMS_JETON");
    }

    private static String valeur(Map<String, String> env, String cle) {
        String v = env.get(cle);
        return v == null ? "" : v;
    }

    /** {@code null} quand rien n'est posé ; sinon le fournisseur et les variables qui lui manquent. */
    public static Choix lireChoix(Map<String, String> env, String cle) {
        boolean appel = cle.equals("APPEL_FOURNISSEUR");
        String brut = valeur(env, cle);
        if (brut.isEmpty() && appel) {
            brut = valeur(env, "SMS_FOURNISSEUR");
        }
        brut = brut.toLowerCase(Locale.ROOT);
        if (brut.isEmpty()) {
            return null;
        }
        if (!ADMIS.contains(brut)) {
            throw new IllegalStateException(cle + " inconnu : « " + brut + " » (admis : twilio, telnyx, http)");
        }
        if (appel && brut.equals("telnyx")) {
            throw new IllegalStateException("APPEL_FOURNISSEUR=telnyx : l'appel sortant n'est pris en charge que par twilio ou http");
        }
        String fournisseur = brut;
        return new Choix(fournisseur, variablesRequises(fournisseur, appel).stream().filter(v -> valeur(env, v).isEmpty()).toList());
    }

    /** Le choix, ou l'erreur qui nomme les variables manquantes. */
    public static Choix exigerChoix(Map<String, String> env, String cle) {
        Choix choix = lireChoix(env, cle);
        if (choix != null && !choix.manquantes().isEmpty()) {
            throw new IllegalStateException(cle + "=" + choix.fournisseur() + " : variables manquantes " + String.join(", ", choix.manquantes()));
        }
        if (choix != null) {
            ADRESSES.forEach(v -> exigerChiffrement(v, valeur(env, v)));
        }
        return choix;
    }

    /**
     * Un numéro de téléphone et le texte d'une alerte ne partent jamais en clair : une adresse de fournisseur hors HTTPS
     * arrête le démarrage, qui la nomme. Seul un hôte local admet {@code http}.
     */
    static void exigerChiffrement(String variable, String adresse) {
        if (adresse.isBlank()) {
            return;
        }
        URI u;
        try {
            u = URI.create(adresse.trim());
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException(variable + " illisible : « " + adresse + " »", e);
        }
        String schema = u.getScheme() == null ? "" : u.getScheme().toLowerCase(Locale.ROOT);
        boolean local = HoteLocal.est(u.getHost());
        if (!schema.equals(SCHEMA_CHIFFRE) && !(local && schema.equals(SCHEMA_LOCAL))) {
            throw new IllegalStateException(variable + " doit être en https (E-TN-8) : « " + adresse + " »");
        }
    }
}
