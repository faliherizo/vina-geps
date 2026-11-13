package ca.vina.nexu.saas.domaine;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Le <b>catalogue des vues</b> doit rester le miroir du menu, dans les deux sens.
 *
 * <p>Le menu se lit à deux endroits : {@code navigation.tsx} pour le socle, et
 * {@code urgencessante/menu.ts} pour le module, que {@code navigation.tsx} importe. Lire les deux
 * fichiers permet d'exiger l'égalité : une vue réglable absente du menu est un réglage sans effet,
 * une entrée du menu absente du catalogue est une entrée que personne ne peut masquer.
 */
@DisplayName("Catalogue des vues — miroir exact du menu")
class CatalogueVuesTest {

    private static final Path NAVIGATION = Path.of("..", "frontend", "src", "navigation.tsx");
    private static final Path MENU_US = Path.of("..", "frontend", "src", "urgencessante", "menu.ts");
    private static final Pattern VUE = Pattern.compile("\\{\\s*page:\\s*\"([a-z]+)\"(?:,\\s*onglet:\\s*\"([a-z-]+)\")?");
    private static final Pattern ENTREE_US = Pattern.compile("\\{\\s*cle:\\s*\"([a-z]+)\",\\s*icone:\\s*\"[^\"]*\",\\s*roles:");

    private static List<String> clesDuMenu() throws IOException {
        String source = Files.readString(NAVIGATION);
        int debut = source.indexOf("export const NAVIGATION");
        String bloc = source.substring(debut, source.indexOf("\n];", debut));
        Set<String> cles = new LinkedHashSet<>();
        Matcher m = VUE.matcher(bloc);
        while (m.find()) {
            cles.add(m.group(2) == null ? m.group(1) : m.group(1) + ":" + m.group(2));
        }
        Matcher us = ENTREE_US.matcher(Files.readString(MENU_US));
        while (us.find()) {
            cles.add(us.group(1));
        }
        return new ArrayList<>(cles);
    }

    @Test
    @DisplayName("le menu et le catalogue déclarent exactement les mêmes vues")
    void miroirDuMenu() throws IOException {
        List<String> menu = clesDuMenu();
        // Un motif de lecture cassé rendrait la comparaison triviale — et verte.
        assertThat(menu).hasSizeGreaterThan(30).contains("dashboard", "access:qui", "uscomblement", "monhoraire");
        assertThat(CatalogueVues.VUES.stream().map(CatalogueVues.Vue::cle).toList()).containsExactlyInAnyOrderElementsOf(menu);
    }

    @Test
    @DisplayName("chaque vue se rattache à une section connue, et sa clé se compose de sa page et de son onglet")
    void coherence() {
        Set<String> sections = CatalogueVues.SECTIONS.stream().map(CatalogueVues.Section::cle).collect(java.util.stream.Collectors.toSet());
        assertThat(CatalogueVues.VUES.stream().filter(v -> !sections.contains(v.section())).map(CatalogueVues.Vue::cle).toList()).isEmpty();
        assertThat(CatalogueVues.VUES.stream()
                .filter(v -> !v.cle().equals(v.onglet() == null ? v.page() : v.page() + ":" + v.onglet()))
                .map(CatalogueVues.Vue::cle).toList()).isEmpty();
        // Une clé en double masquerait deux entrées d'un seul clic.
        assertThat(CatalogueVues.VUES.stream().map(CatalogueVues.Vue::cle).distinct().count()).isEqualTo(CatalogueVues.VUES.size());
        // Le module d'une vue existe au catalogue des modules : sinon la vue ne s'ouvrirait jamais.
        assertThat(CatalogueModules.codesInconnus(CatalogueVues.VUES.stream().map(CatalogueVues.Vue::module).filter(java.util.Objects::nonNull).distinct().toList())).isEmpty();
        assertThat(CatalogueVues.SECTIONS.get(1).vue()).containsEntry("module", "effectifs");
        assertThat(CatalogueVues.VUES.get(0).vue()).containsEntry("cle", "dashboard").containsEntry("onglet", null);
    }

    @Test
    @DisplayName("une clé inconnue est refusée, jamais ignorée, et ne se répète pas dans le refus")
    void clesInconnues() {
        assertThat(CatalogueVues.connait("access:octrois")).isTrue();
        assertThat(CatalogueVues.clesInconnues(List.of("dashboard", "uscomblement"))).isEmpty();
        assertThat(CatalogueVues.clesInconnues(List.of("dashboard", "inventé", "scolaire:suivi")))
                .containsExactly("inventé", "scolaire:suivi");
        assertThat(CatalogueVues.clesInconnues(List.of("x", "x"))).containsExactly("x");
    }
}
