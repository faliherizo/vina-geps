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
 *   <li><strong>Un motif coûteux est ignoré lui aussi</strong> (tests d'intrusion FX-AO, IN-FX-01). Le motif et le sujet
 *       viennent tous deux de l'appelant : {@code (a+)+$} contre une longue suite de « a » occupait un fil du serveur
 *       pendant des minutes. La recherche est donc bornée en <strong>lectures de caractères</strong> — une borne
 *       déterministe, qui ne dépend pas de l'horloge — et le motif comme le sujet en longueur.</li>
 * </ol>
 */
public final class RoutageCourriel {

    /** Au-delà, un motif n'est plus une règle de routage qu'un administrateur écrit à la main. */
    static final int LONGUEUR_MAX_MOTIF = 256;
    /** La longueur d'une ligne d'en-tête de courriel (RFC 5322 § 2.1.1) : un sujet plus long est tronqué. */
    static final int LONGUEUR_MAX_SUJET = 998;
    /** Lectures de caractères permises par motif : des milliers de fois la longueur d'un sujet, loin d'un emballement. */
    static final long LECTURES_MAX = 2_000_000L;

    private RoutageCourriel() {
    }

    public record Courriel(String from, String subject, String body) {
    }

    public record Route(String subjectPattern, String action) {
    }

    public record Resultat(String action, String matchedPattern) {
    }

    public static Resultat router(Courriel courriel, List<Route> routes) {
        String brut = courriel.subject() == null ? "" : courriel.subject();
        String sujet = brut.length() > LONGUEUR_MAX_SUJET ? brut.substring(0, LONGUEUR_MAX_SUJET) : brut;
        for (Route route : routes) {
            if (correspond(route.subjectPattern(), sujet)) {
                return new Resultat(route.action(), route.subjectPattern());
            }
        }
        return new Resultat(null, null);
    }

    /** Vrai si le motif se trouve dans le sujet ; un motif invalide, trop long ou trop coûteux ne correspond pas. */
    static boolean correspond(String motif, String sujet) {
        if (motif == null || motif.length() > LONGUEUR_MAX_MOTIF) {
            return false;
        }
        try {
            return Pattern.compile(motif, Pattern.CASE_INSENSITIVE).matcher(new SujetBorne(sujet, LECTURES_MAX)).find();
        } catch (PatternSyntaxException | BudgetEpuiseException e) {
            return false;
        }
    }

    /** Le budget de lectures d'un motif est épuisé : la recherche s'arrête là. */
    static final class BudgetEpuiseException extends RuntimeException {
        BudgetEpuiseException() {
            super("budget de lectures épuisé", null, false, false);
        }
    }

    /** Un sujet qui compte ses lectures : le moteur d'expressions n'a pas d'autre point d'arrêt que {@code charAt}. */
    static final class SujetBorne implements CharSequence {
        private final String texte;
        private final long[] restantes;

        SujetBorne(String texte, long lectures) {
            this(texte, new long[] {lectures});
        }

        private SujetBorne(String texte, long[] restantes) {
            this.texte = texte;
            this.restantes = restantes;
        }

        @Override
        public char charAt(int index) {
            if (--restantes[0] < 0) {
                throw new BudgetEpuiseException();
            }
            return texte.charAt(index);
        }

        @Override
        public int length() {
            return texte.length();
        }

        @Override
        public CharSequence subSequence(int debut, int fin) {
            return new SujetBorne(texte.substring(debut, fin), restantes);
        }

        @Override
        public String toString() {
            return texte;
        }
    }
}
