package ca.vina.nexu.effectifs.domaine;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Jumeau de la section « Répercussion » de {@code effectifs-planification-domaine.test.ts} (E-CH-3). */
@DisplayName("Répercussion d'un poste modifié sur ses quarts (E-CH-3)")
class RepercussionPosteDomaineTest {

    private static final RepercussionPoste.FormePoste POSTE = new RepercussionPoste.FormePoste("EST", "m1", "REG", "PARA",
            List.of(Arrays.asList("J", "N", null, "X", null, null, null)), "2030-04-01");
    private static final Set<String> CONNUES = Set.of("J", "N");

    private static RepercussionPoste.PlaceGeneree place(String date, String unite, String milieu, String nature, String corps, boolean pointee) {
        return new RepercussionPoste.PlaceGeneree(date, unite, "J", milieu, nature, corps, pointee);
    }

    private static RepercussionPoste.Action action(RepercussionPoste.PlaceGeneree p) {
        return RepercussionPoste.decision(p, POSTE, CONNUES).action();
    }

    @Test
    @DisplayName("pointée : intouchée ; congé, plage inconnue ou autre unité : retirée")
    void ignorerEtRetirer() {
        assertThat(action(place("2030-04-03", "EST", "m1", "REG", "PARA", true))).isEqualTo(RepercussionPoste.Action.IGNORER_POINTEE);
        assertThat(action(place("2030-04-03", "EST", "m1", "REG", "PARA", false))).isEqualTo(RepercussionPoste.Action.RETIRER);
        assertThat(action(place("2030-04-04", "EST", "m1", "REG", "PARA", false))).isEqualTo(RepercussionPoste.Action.RETIRER);
        assertThat(action(place("2030-04-01", "OUEST", "m1", "REG", "PARA", false))).isEqualTo(RepercussionPoste.Action.RETIRER);
    }

    @Test
    @DisplayName("autre plage, milieu ou nature : déplacée ; autre corps : requalifiée ; sinon rien")
    void deplacerRequalifier() {
        assertThat(RepercussionPoste.decision(place("2030-04-02", "EST", "m1", "REG", "PARA", false), POSTE, CONNUES))
                .isEqualTo(new RepercussionPoste.Decision(RepercussionPoste.Action.DEPLACER, "N"));
        assertThat(action(place("2030-04-01", "EST", "m2", "REG", "PARA", false))).isEqualTo(RepercussionPoste.Action.DEPLACER);
        assertThat(action(place("2030-04-01", "EST", "m1", "ADH", "PARA", false))).isEqualTo(RepercussionPoste.Action.DEPLACER);
        assertThat(action(place("2030-04-01", "EST", "m1", "REG", "RMU", false))).isEqualTo(RepercussionPoste.Action.AJUSTER_CORPS);
        assertThat(action(place("2030-04-08", "EST", "m1", "REG", "PARA", false))).isEqualTo(RepercussionPoste.Action.AUCUNE);
    }

    @Test
    @DisplayName("ajoute les seuls jours futurs à plage connue non tenus ; les minimums suivent")
    void ajouterEtMinimums() {
        assertThat(RepercussionPoste.joursAAjouter(POSTE, List.of("2030-04-01", "2030-04-02", "2030-04-03", "2030-04-04", "2030-04-08", "2030-04-09"),
                "2030-04-01", Set.of("2030-04-08"), CONNUES))
                .containsExactly(new RepercussionPoste.JourAAjouter("2030-04-02", "N"), new RepercussionPoste.JourAAjouter("2030-04-09", "N"));
        assertThat(List.of(RepercussionPoste.minimumApresAjout(2, 2), RepercussionPoste.minimumApresAjout(0, 0), RepercussionPoste.minimumApresAjout(1, 3)))
                .containsExactly(3, 1, 1);
        assertThat(List.of(RepercussionPoste.minimumApresRetrait(3, 2), RepercussionPoste.minimumApresRetrait(1, 2))).containsExactly(2, 1);
    }

    @Test
    @DisplayName("un lot : au moins un changement, champs connus seulement — messages du Node")
    void changements() {
        assertThat(RepercussionPoste.changementsDe(Map.of("milieuId", "m"))).containsEntry("milieuId", "m");
        assertThatThrownBy(() -> RepercussionPoste.changementsDe(Map.of("code", "X")))
                .hasMessage("changement de poste inconnu : code (admis : uniteCode, statutEmploiCode, corpsEmploiCode, milieuId, natureCode, cycle, debutCycle, parametres)");
        assertThatThrownBy(() -> RepercussionPoste.changementsDe(Map.of())).hasMessage("aucun changement demandé");
        assertThatThrownBy(() -> RepercussionPoste.changementsDe(null)).hasMessage("aucun changement demandé");
        assertThat(RepercussionPoste.FormePoste.de(new DepotPostesPort.Champs("l", "EST", "TC", "PARA", "REG", "m1", List.of(), "2030-04-01", Map.of())).milieuId())
                .isEqualTo("m1");
    }
}
