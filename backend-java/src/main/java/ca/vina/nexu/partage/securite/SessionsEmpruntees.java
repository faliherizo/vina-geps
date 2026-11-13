package ca.vina.nexu.partage.securite;

import java.util.List;
import java.util.regex.Pattern;

/**
 * Port : ce que le filtre de session sait d'une session <strong>empruntée</strong> (CSSMB 25, ADR 0038)
 * — l'emprunt vaut-il encore ? — et ce qu'elle ne fait jamais : toucher aux secrets de la cible, ou
 * emprunter à son tour. Le dépôt des emprunts (contexte auth) répond ; les règles vivent ici.
 */
public interface SessionsEmpruntees {

    /** Vrai tant que l'emprunt n'est ni terminé ni expiré. */
    boolean active(String empruntId);

    String MESSAGE_INTERDIT = "emprunt : interdit — cette action revient à la personne elle-même";
    String MESSAGE_TERMINE = "emprunt terminé : reconnectez-vous";

    /** Aucune session n'est empruntée : le port par défaut des essais et des filtres sans dépôt. */
    SessionsEmpruntees AUCUNE = empruntId -> false;

    record Interdit(List<String> methodes, Pattern chemin) {
    }

    List<Interdit> INTERDITS = List.of(
            new Interdit(List.of("POST", "PUT", "PATCH", "DELETE"), Pattern.compile("^/api/auth/change-password$")),
            new Interdit(List.of("POST", "PUT", "PATCH", "DELETE"), Pattern.compile("^/api/auth/security/(setup|totp/init|verify|questions)(/|$)")),
            new Interdit(List.of("POST", "PUT", "PATCH", "DELETE"), Pattern.compile("^/api/auth/webauthn/")),
            new Interdit(List.of("POST"), Pattern.compile("^/api/auth/emprunt$")),
            new Interdit(List.of("POST", "PUT", "PATCH", "DELETE"), Pattern.compile("^/api/auth/users/[^/]+/reset-security$")));

    static boolean interdit(String methode, String chemin) {
        String m = methode.toUpperCase(java.util.Locale.ROOT);
        return INTERDITS.stream().anyMatch(i -> i.methodes().contains(m) && i.chemin().matcher(chemin).find());
    }
}
