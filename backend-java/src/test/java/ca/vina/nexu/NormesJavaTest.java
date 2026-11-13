package ca.vina.nexu;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * **Le cliquet des normes Java** ({@code docs/NORMES-JAVA.md}, § 7) : les chaînes en dur qu'on compare
 * ({@code "x".equals(…)}), qu'on aiguille ({@code case "x"}) ou qu'on liste ({@code List.of("x", "y")})
 * à la place d'un {@code enum} sont comptées par contexte, et aucun compte ne peut **monter**. Le code
 * nouveau suit la norme ; le code hérité ne s'aggrave jamais ; chaque contexte converti abaisse son
 * plafond dans {@code normes-java-plafonds.properties}, et le nouveau plafond devient la règle.
 */
@DisplayName("Normes Java — les chaînes en dur ne montent jamais")
class NormesJavaTest {

    private static final Path SOURCES = Path.of("src/main/java/ca/vina/nexu");
    private static final Path PLAFONDS = Path.of("normes-java-plafonds.properties");
    private static final List<Pattern> ECARTS = List.of(
            Pattern.compile("\"[a-z_]+\"\\.equals\\("),
            Pattern.compile("case \""),
            Pattern.compile("List\\.of\\(\"[a-z_]+\", \""));

    /** Le compte d'écarts par contexte (premier paquet sous {@code ca.vina.nexu}). */
    static Map<String, Integer> compter() throws IOException {
        Map<String, Integer> comptes = new TreeMap<>();
        try (Stream<Path> fichiers = Files.walk(SOURCES)) {
            for (Path p : fichiers.filter(f -> f.toString().endsWith(".java")).toList()) {
                String contexte = SOURCES.relativize(p).getName(0).toString();
                String texte = Files.readString(p);
                int n = 0;
                for (Pattern e : ECARTS) {
                    n += (int) e.matcher(texte).results().count();
                }
                comptes.merge(contexte.endsWith(".java") ? "racine" : contexte, n, Integer::sum);
            }
        }
        return comptes;
    }

    static Map<String, Integer> plafonds() throws IOException {
        Map<String, Integer> p = new TreeMap<>();
        for (String ligne : Files.readAllLines(PLAFONDS)) {
            if (ligne.isBlank() || ligne.startsWith("#")) {
                continue;
            }
            String[] kv = ligne.split("=");
            p.put(kv[0].trim(), Integer.parseInt(kv[1].trim()));
        }
        return p;
    }

    @Test
    @DisplayName("aucun contexte ne compte plus de chaînes en dur que son plafond ; un contexte nouveau part de zéro")
    void aucuneHausse() throws IOException {
        Map<String, Integer> comptes = compter();
        Map<String, Integer> plafonds = plafonds();
        List<String> hausses = new ArrayList<>();
        comptes.forEach((contexte, n) -> {
            int plafond = plafonds.getOrDefault(contexte, 0);
            if (n > plafond) {
                hausses.add(contexte + " : " + n + " écarts, plafond " + plafond + " — remplacez les chaînes par des enums (docs/NORMES-JAVA.md § 1)");
            }
        });
        assertThat(hausses).as("chaînes en dur (equals, case, List.of de codes) par contexte").isEmpty();
    }

    @Test
    @DisplayName("un plafond qui dépasse le compte réel de plus de zéro se resserre : le cliquet ne remonte pas")
    void plafondsSerres() throws IOException {
        Map<String, Integer> comptes = compter();
        List<String> laches = new ArrayList<>();
        plafonds().forEach((contexte, plafond) -> {
            int n = comptes.getOrDefault(contexte, 0);
            if (plafond > n) {
                laches.add(contexte + " : plafond " + plafond + " > compte " + n + " — abaissez-le dans normes-java-plafonds.properties");
            }
        });
        assertThat(laches).as("plafonds à resserrer").isEmpty();
    }
}
