package ca.vina.nexu.organisme.domaine;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * <strong>Reconnaître un téléchargement à sa réponse</strong> (P-TN-10) : {@code Content-Disposition: attachment}
 * et un statut 2xx. C'est la réponse qui le dit, pas l'écran : un export neuf ne peut donc pas oublier l'alerte.
 */
public final class Telechargements {

    private static final int SUCCES_MIN = 200;
    private static final int SUCCES_MAX = 300;
    private static final Pattern PIECE_JOINTE = Pattern.compile("^\\s*attachment\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern FICHIER = Pattern.compile("filename\\*?=(?:UTF-8'')?\"?([^\";]+)\"?", Pattern.CASE_INSENSITIVE);
    private static final String SANS_NOM = "export";

    private Telechargements() {
    }

    public static boolean estTelechargement(int statut, String disposition) {
        return statut >= SUCCES_MIN && statut < SUCCES_MAX && PIECE_JOINTE.matcher(disposition == null ? "" : disposition).find();
    }

    /** Le nom du fichier annoncé, décodé comme {@code decodeURIComponent} ; « export » s'il n'en porte pas. */
    public static String fichierDe(String disposition) {
        Matcher m = FICHIER.matcher(disposition == null ? "" : disposition);
        return m.find() ? decoder(m.group(1)) : SANS_NOM;
    }

    // `decodeURIComponent` ne fait pas d'un « + » une espace : on le protège avant URLDecoder. Un encodage
    // défectueux garde le nom brut plutôt que de perdre l'alerte.
    private static String decoder(String brut) {
        try {
            return URLDecoder.decode(brut.replace("+", "%2B"), StandardCharsets.UTF_8);
        } catch (IllegalArgumentException e) {
            return brut;
        }
    }

    /** Le « quoi » d'une alerte de téléchargement : le fichier, la méthode et le chemin (sans la requête). */
    public static String quoi(String disposition, String methode, String chemin) {
        int q = chemin.indexOf('?');
        return fichierDe(disposition) + " (" + methode + " " + (q < 0 ? chemin : chemin.substring(0, q)) + ")";
    }
}
