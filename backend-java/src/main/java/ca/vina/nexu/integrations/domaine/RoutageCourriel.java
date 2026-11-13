package ca.vina.nexu.integrations.domaine;

import java.util.List;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * Courriel entrant → action (AO-5338 #NF-21/22).
 *
 * <p>Portage de {@code routeInboundEmail}. Deux pièges de portage, tous deux silencieux :
 *
 * <ol>
 *   <li>{@code RegExp.test} <strong>cherche</strong> le motif dans la chaîne ; {@code
 *       String.matches} exige qu'il la couvre entièrement. Utiliser {@code matches} ferait échouer
 *       tous les motifs partiels — c'est-à-dire la quasi-totalité d'entre eux — et les courriels
 *       tomberaient silencieusement sans action.</li>
 *   <li>Un motif syntaxiquement invalide est <strong>ignoré</strong>, pas fatal : une règle mal
 *       saisie par un administrateur ne doit pas neutraliser les règles suivantes.</li>
 * </ol>
 */
public final class RoutageCourriel {

    private RoutageCourriel() {
    }

    public record Courriel(String from, String subject, String body) {
    }

    public record Route(String subjectPattern, String action) {
    }

    public record Resultat(String action, String matchedPattern) {
    }

    public static Resultat router(Courriel courriel, List<Route> routes) {
        String sujet = courriel.subject() == null ? "" : courriel.subject();
        for (Route route : routes) {
            Pattern motif;
            try {
                motif = Pattern.compile(route.subjectPattern(), Pattern.CASE_INSENSITIVE);
            } catch (PatternSyntaxException | NullPointerException e) {
                continue;
            }
            if (motif.matcher(sujet).find()) {
                return new Resultat(route.action(), route.subjectPattern());
            }
        }
        return new Resultat(null, null);
    }
}
