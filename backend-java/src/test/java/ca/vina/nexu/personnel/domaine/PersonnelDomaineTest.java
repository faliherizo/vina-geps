package ca.vina.nexu.personnel.domaine;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Personnel — le nom affiché et la ligne hiérarchique")
class PersonnelDomaineTest {

    @Test
    @DisplayName("le nom complet tolère un prénom ou un nom absent, sans espace parasite")
    void nomComplet() {
        assertThat(new AnnuaireEmployes.Fiche("e", "Léa", "Roy", null).nomComplet()).isEqualTo("Léa Roy");
        assertThat(new AnnuaireEmployes.Fiche("e", null, "Roy", null).nomComplet()).isEqualTo("Roy");
        assertThat(new AnnuaireEmployes.Fiche("e", "Léa", null, null).nomComplet()).isEqualTo("Léa");
        assertThat(new RepertoirePersonnes.Personne("e", null, "Roy", null, null, null).nomComplet()).isEqualTo("Roy");
        assertThat(new RepertoirePersonnes.Personne("e", null, null, null, null, null).nomComplet()).isEmpty();
    }

    @Test
    @DisplayName("les subordonnés suivent toute la ligne, en largeur d'abord, et rien d'autre")
    void subordonnes() {
        List<AnnuaireEmployes.Fiche> bassin = List.of(
                new AnnuaireEmployes.Fiche("chef", "C", "Chef", null),
                new AnnuaireEmployes.Fiche("a", "A", "A", "chef"),
                new AnnuaireEmployes.Fiche("b", "B", "B", "chef"),
                new AnnuaireEmployes.Fiche("a1", "A1", "A1", "a"),
                new AnnuaireEmployes.Fiche("x", "X", "X", "ailleurs"));
        assertThat(AnnuaireEmployes.subordonnes("chef", bassin)).extracting(AnnuaireEmployes.Fiche::id).containsExactly("a", "b", "a1");
        assertThat(AnnuaireEmployes.subordonnes("a1", bassin)).isEmpty();
    }
}
