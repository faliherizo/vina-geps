package ca.vina.nexu.securite.domaine;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Port antivirus — port de {@code securite/antivirus.ts} : un fichier téléversé est analysé avant
 * de rejoindre un dossier (grille 33, contrat 9.25.01). Un fichier refusé est nommé avec la
 * signature détectée, et un analyseur injoignable fait <em>échouer</em> le dépôt — jamais
 * l'accepter faute d'avoir pu vérifier (« fail closed »).
 */
public final class Antivirus {

    private Antivirus() {
    }

    public record Verdict(boolean sain, String signature) {
    }

    public interface AnalyseurFichier {
        Verdict analyser(byte[] contenu);
    }

    /** La chaîne de test EICAR : reconnue par tout antivirus, inoffensive, standard depuis 1997. */
    public static final String EICAR = "X5O!P%@AP[4\\PZX54(P^)7CC)7}$EICAR-STANDARD-ANTIVIRUS-TEST-FILE!$H+H*";

    /** Le simulateur, défaut tant que {@code CLAMD_HOTE} n'est pas posé : il ne reconnaît qu'EICAR. */
    public static final class AnalyseurSimule implements AnalyseurFichier {
        private final List<Integer> analyses = new ArrayList<>();

        @Override
        public Verdict analyser(byte[] contenu) {
            analyses.add(contenu.length);
            return new String(contenu, StandardCharsets.ISO_8859_1).contains(EICAR)
                    ? new Verdict(false, "Eicar-Test-Signature")
                    : new Verdict(true, null);
        }

        public List<Integer> analyses() {
            return List.copyOf(analyses);
        }
    }

    private static final Pattern OK = Pattern.compile("\\bOK$");
    private static final Pattern TROUVE = Pattern.compile(":\\s*(.+?)\\s+FOUND$");

    /** Traduit la réponse de clamd. Une réponse inattendue n'est pas « sain » : elle est une erreur. */
    public static Verdict interpreter(String reponse) {
        String texte = reponse.replace("\0", "").trim();
        if (OK.matcher(texte).find()) {
            return new Verdict(true, null);
        }
        Matcher m = TROUVE.matcher(texte);
        if (m.find()) {
            return new Verdict(false, m.group(1));
        }
        throw new IllegalStateException("réponse antivirus inattendue : « " + texte.substring(0, Math.min(120, texte.length())) + " »");
    }

    /** Le contrôle à poser à chaque entrée de fichier : lève si le fichier est infecté ou si l'analyse est impossible. */
    public static void exigerSain(AnalyseurFichier analyseur, byte[] contenu) {
        Verdict verdict = analyseur.analyser(contenu);
        if (!verdict.sain()) {
            throw new IllegalStateException("fichier refusé par l'antivirus (" + verdict.signature() + ")");
        }
    }
}
