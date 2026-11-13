package ca.vina.nexu.saas.domaine;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Catalogue des modules — familles, prérequis, résolution, parité avec l'interface")
class CatalogueModulesTest {

    @Test
    @DisplayName("tout module appartient à une famille, tout prérequis existe, aucun code n'est répété")
    void toutModuleAppartientAUneFamilleEtToutPrerequisExiste() {
        List<String> familles = CatalogueModules.FAMILLES.stream().map(CatalogueModules.Famille::cle).toList();
        for (CatalogueModules.Definition m : CatalogueModules.MODULES) {
            assertThat(familles).contains(m.famille());
            if (m.requiert() != null) {
                assertThat(CatalogueModules.definitionDe(m.requiert())).isPresent();
            }
        }
        assertThat(CatalogueModules.CODES).doesNotHaveDuplicates();
        assertThat(CatalogueModules.dependantsDe("effectifs")).containsExactlyInAnyOrder("effectifs_regles", "effectifs_communication");
        assertThat(CatalogueModules.dependantsDe("effectifs_planification")).containsExactlyInAnyOrder("effectifs_disponibilites", "effectifs_absences",
                "effectifs_echanges", "effectifs_agph", "effectifs_presence");
        assertThat(CatalogueModules.definitionDe("effectifs").orElseThrow().requiert()).isNull();
        assertThat(CatalogueModules.definitionDe("inconnu")).isEqualTo(Optional.empty());
    }

    @Test
    @DisplayName("la communication ne dépend que du socle du module : plus d'horaires médicaux à acheter avec elle")
    void laCommunicationNeRequiertQueLeSocle() {
        CatalogueModules.Resolution r = CatalogueModules.resoudreDependances(List.of("effectifs_communication"), null);
        assertThat(r.modules()).containsExactly("effectifs", "effectifs_communication");
        assertThat(r.ajoutes()).containsExactly("effectifs");
    }

    @Test
    @DisplayName("cocher les feuilles de temps remonte la chaîne entière, et le dit")
    void cocherUnSousModuleEntraineSesPrerequisEtLeDit() {
        CatalogueModules.Resolution r = CatalogueModules.resoudreDependances(List.of("effectifs_temps"), null);
        assertThat(r.modules()).containsExactly("effectifs", "effectifs_regles", "effectifs_planification", "effectifs_presence", "effectifs_temps");
        assertThat(r.ajoutes()).containsExactlyInAnyOrder("effectifs", "effectifs_regles", "effectifs_planification", "effectifs_presence");
        assertThat(r.retires()).isEmpty();
    }

    @Test
    @DisplayName("décocher le socle emporte toute la cascade, étage par étage")
    void decocherLeSocleEmporteSesEnfantsEtLaCascadeSuitLaChaine() {
        List<String> actuels = List.of("approvals", "effectifs", "effectifs_regles", "effectifs_planification", "effectifs_presence", "effectifs_temps",
                "effectifs_integrations", "effectifs_communication");
        CatalogueModules.Resolution r = CatalogueModules.resoudreDependances(actuels.stream().filter(m -> !m.equals("effectifs")).toList(), actuels);
        assertThat(r.modules()).containsExactly("approvals");
        assertThat(r.retires()).containsExactlyInAnyOrder("effectifs_regles", "effectifs_planification", "effectifs_presence", "effectifs_temps",
                "effectifs_integrations", "effectifs_communication");
        assertThat(r.ajoutes()).isEmpty();
    }

    @Test
    @DisplayName("décocher un enfant ne touche ni le socle ni ses frères ; cocher un enfant ajoute son socle")
    void decocherUnEnfantNeToucheNiLeSocleNiSesFreresEtCocherUnEnfantAjouteSonSocle() {
        List<String> actuels = List.of("effectifs", "effectifs_regles", "effectifs_planification", "effectifs_echanges", "effectifs_agph");
        CatalogueModules.Resolution r = CatalogueModules.resoudreDependances(actuels.stream().filter(m -> !m.equals("effectifs_agph")).toList(), actuels);
        assertThat(r.modules()).containsExactly("effectifs", "effectifs_regles", "effectifs_planification", "effectifs_echanges");
        assertThat(r.retires()).isEmpty();
        assertThat(r.ajoutes()).isEmpty();
        CatalogueModules.Resolution ajout = CatalogueModules.resoudreDependances(List.of("approvals", "effectifs_communication"), List.of("approvals"));
        assertThat(ajout.modules()).containsExactly("approvals", "effectifs", "effectifs_communication");
        assertThat(ajout.ajoutes()).containsExactly("effectifs");
    }

    @Test
    @DisplayName("sans état de référence tout se lit comme un ajout ; inconnus, ordre et doublons")
    void sansEtatDeReferenceToutSeLitCommeUnAjoutInconnusOrdreEtDoublons() {
        CatalogueModules.Resolution fantome = CatalogueModules.resoudreDependances(List.of("approvals", "module-fantome"), null);
        assertThat(fantome.modules()).containsExactly("approvals");
        assertThat(fantome.retires()).isEmpty();
        assertThat(CatalogueModules.resoudreDependances(List.of("effectifs_regles", "approvals", "effectifs"), null).modules()).containsExactly("approvals", "effectifs", "effectifs_regles");
        assertThat(CatalogueModules.resoudreDependances(List.of("effectifs", "effectifs"), null).modules()).containsExactly("effectifs");
        assertThat(CatalogueModules.resoudreDependances(List.of(), null)).isEqualTo(new CatalogueModules.Resolution(List.of(), List.of(), List.of()));
        assertThat(CatalogueModules.codesInconnus(List.of("effectifs", "x", "x", "y"))).containsExactly("x", "y");
    }

    @Test
    @DisplayName("les forfaits n'accordent que des codes du catalogue, prérequis compris")
    void lesForfaitsSontCoherents() {
        for (String plan : Plans.CODES) {
            List<String> modules = Plans.defauts(plan).modules();
            assertThat(CatalogueModules.codesInconnus(modules)).as(plan).isEmpty();
            assertThat(CatalogueModules.resoudreDependances(modules, null).ajoutes()).as(plan).isEmpty();
        }
        assertThat(Plans.defauts("starter").modules()).contains("effectifs_planification").doesNotContain("effectifs_temps");
        assertThat(Plans.defauts("sovereign").modules()).contains("byok", "dedicated", "effectifs_communication");
    }

    /**
     * Un code que l'interface attend et que le catalogue ignore rend l'écran **invisible pour
     * tous** : aucun superadmin ne peut l'accorder. Le Node, qui tenait ce rôle de témoin, n'est
     * plus là ; c'est l'interface qui lit le catalogue, c'est donc contre elle qu'on vérifie.
     */
    @Test
    @DisplayName("chaque module nommé par l'interface existe au catalogue")
    void pariteAvecLInterface() throws Exception {
        Set<String> attendus = new LinkedHashSet<>();
        Matcher valeurs = Pattern.compile(":\\s*\"([a-z_]+)\",").matcher(Files.readString(Path.of("..", "frontend", "src", "modulesParPage.ts")));
        while (valeurs.find()) {
            attendus.add(valeurs.group(1));
        }
        Matcher menu = Pattern.compile("module:\\s*\"([a-z_]+)\"").matcher(Files.readString(Path.of("..", "frontend", "src", "urgencessante", "menu.ts")));
        while (menu.find()) {
            attendus.add(menu.group(1));
        }
        assertThat(attendus).hasSizeGreaterThan(10);
        assertThat(CatalogueModules.codesInconnus(List.copyOf(attendus))).isEmpty();
    }
}
