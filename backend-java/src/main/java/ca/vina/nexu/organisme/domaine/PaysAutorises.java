package ca.vina.nexu.organisme.domaine;

import java.util.List;
import java.util.Locale;
import java.util.TreeSet;
import java.util.regex.Pattern;

/**
 * <strong>Pays autorisés à la connexion</strong> (AO 26-015-DO, P-TN-11) — jumeau de {@code paysDe} et
 * {@code verdictPays} du Node. Une liste vide lève la restriction ; un pays inconnu est refusé dès qu'il y en
 * a une, puisqu'on ne peut pas prouver qu'il est dans la liste.
 */
public final class PaysAutorises {

    private static final int MAXIMUM = 50;
    private static final Pattern CODE = Pattern.compile("^[A-Z]{2}$");
    private static final Pattern CODE_RECU = Pattern.compile("^[A-Za-z]{2}$");
    private static final String VIDE = "vide";

    private PaysAutorises() {
    }

    /** Le verdict d'une connexion : autorisée, ou refusée avec la raison. */
    public record Verdict(boolean autorise, String motif) {

        static Verdict admis() {
            return new Verdict(true, null);
        }

        static Verdict refusePour(String motif) {
            return new Verdict(false, motif);
        }
    }

    /** Des codes ISO 3166-1 alpha-2, sans doublon, triés. */
    public static List<String> paysDe(Object v) {
        if (!(v instanceof List<?> l) || l.size() > MAXIMUM) {
            throw new OptionInvalideException("« pays » : une liste de " + MAXIMUM + " codes ISO au plus (ex. CA, US)");
        }
        List<String> codes = l.stream().map(x -> x instanceof String s ? s.strip().toUpperCase(Locale.ROOT) : "").toList();
        List<String> faux = codes.stream().filter(c -> !CODE.matcher(c).matches()).map(c -> c.isEmpty() ? VIDE : c).toList();
        if (!faux.isEmpty()) {
            throw new OptionInvalideException("« pays » : codes ISO à deux lettres attendus (reçu : " + String.join(", ", faux) + ")");
        }
        return List.copyOf(new TreeSet<>(codes));
    }

    /** Juge une connexion ; {@code pays} vaut {@code null} quand la passerelle ne l'a pas transmis. */
    public static Verdict verdict(List<String> autorises, String pays) {
        if (autorises.isEmpty()) {
            return Verdict.admis();
        }
        if (pays == null || pays.isEmpty()) {
            return Verdict.refusePour("connexion refusée : le pays d'origine n'a pas pu être établi, et cet organisme restreint les connexions par pays");
        }
        String code = pays.toUpperCase(Locale.ROOT);
        return autorises.contains(code) ? Verdict.admis() : Verdict.refusePour("connexion refusée depuis ce pays (" + code + ") : cet organisme ne l'autorise pas");
    }

    /** Le pays tel que la passerelle l'a posé dans l'en-tête, en majuscules ; {@code null} s'il manque ou n'est pas un code. */
    public static String paysDeLEntete(String valeur) {
        return valeur != null && CODE_RECU.matcher(valeur.strip()).matches() ? valeur.strip().toUpperCase(Locale.ROOT) : null;
    }
}
