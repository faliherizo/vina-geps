package ca.vina.nexu.notifications.domaine;

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
        return choix;
    }
}
