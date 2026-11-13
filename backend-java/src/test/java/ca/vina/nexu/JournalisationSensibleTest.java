package ca.vina.nexu;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Garde-fou (ADR 0041, JOURNALISATION.md § 5) : <strong>aucun appel du journal ne nomme un champ
 * sensible</strong> dans ses expressions, et rien n'écrit sur la sortie standard en dehors du journal.
 * Miroir de {@code journalisation-sensible.test.ts} : la règle ne dépend pas d'une revue.
 */
@DisplayName("Journalisation — rien de sensible, jamais System.out")
class JournalisationSensibleTest {

    private static final Path SOURCES = Path.of("src/main/java/ca/vina/nexu");
    /** Un appel du journal : {@code JOURNAL.info(}, {@code LOG.warn(}, ou la forme fluide {@code .atInfo()…log(}. */
    private static final Pattern APPEL = Pattern.compile("\\b(?:JOURNAL|LOG|journal|logger)\\.(?:at(?:Debug|Info|Warn|Error)\\(\\)|debug\\(|info\\(|warn\\(|error\\()");
    private static final Pattern SENSIBLE = Pattern.compile("\\b(password|passwordHash|motDePasse|token|jeton|secret|cookie|authorization|body|corps|courriel|email|telephone|phone|nas|salaire|salary|montant|commentaire|motif)\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern CHAINES = Pattern.compile("\"(?:[^\"\\\\]|\\\\.)*\"");
    private static final Pattern MASQUE = Pattern.compile("Masquage\\.masquer(?:Chemin)?\\([^)]*\\)");
    private static final Pattern SORTIE_STANDARD = Pattern.compile("System\\.(out|err)\\.print|printStackTrace\\(");

    /** L'appel entier, jusqu'à la fin de sa chaîne fluide (le {@code ;} qui ferme l'instruction). */
    private static String instruction(String texte, int debut) {
        int profondeur = 0;
        boolean dansChaine = false;
        for (int i = debut; i < texte.length(); i++) {
            char c = texte.charAt(i);
            if (c == '"' && texte.charAt(i - 1) != '\\') {
                dansChaine = !dansChaine;
            } else if (!dansChaine) {
                if (c == '(') {
                    profondeur++;
                } else if (c == ')') {
                    profondeur--;
                } else if (c == ';' && profondeur == 0) {
                    return texte.substring(debut, i);
                }
            }
        }
        return texte.substring(debut);
    }

    private static List<String> fautes(Pattern motif, boolean surLesAppels) throws IOException {
        List<String> fautes = new ArrayList<>();
        try (Stream<Path> fichiers = Files.walk(SOURCES)) {
            for (Path p : fichiers.filter(f -> f.toString().endsWith(".java")).toList()) {
                String texte = Files.readString(p);
                if (!surLesAppels) {
                    if (motif.matcher(texte).find()) {
                        fautes.add(SOURCES.relativize(p).toString());
                    }
                    continue;
                }
                Matcher m = APPEL.matcher(texte);
                while (m.find()) {
                    String appel = instruction(texte, m.start());
                    // Les textes (messages français) ne sont pas des données : on ne juge que les expressions, un argument masqué n'est pas une fuite.
                    String expressions = MASQUE.matcher(CHAINES.matcher(appel).replaceAll("\"\"")).replaceAll("masque");
                    if (motif.matcher(expressions).find()) {
                        fautes.add(SOURCES.relativize(p) + " : " + appel.replaceAll("\\s+", " "));
                    }
                }
            }
        }
        return fautes;
    }

    @Test
    @DisplayName("aucun appel du journal ne nomme un mot de passe, un jeton, un corps, un courriel, un montant, un motif")
    void rienDeSensible() throws IOException {
        assertThat(fautes(SENSIBLE, true)).isEmpty();
    }

    @Test
    @DisplayName("rien n'écrit sur System.out/System.err ni ne déroule une pile hors du journal")
    void jamaisSystemOut() throws IOException {
        assertThat(fautes(SORTIE_STANDARD, false)).isEmpty();
    }
}
