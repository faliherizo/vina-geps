package ca.vina.nexu.auth.domaine;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("Questions secrètes — trois au moins, distinctes, réponses normalisées")
class QuestionsSecretesTest {

    private static QuestionsSecretes.Paire p(String q, String r) {
        return new QuestionsSecretes.Paire(q, r);
    }

    @Test
    void troisQuestionsPropresSontNormalisees() {
        List<QuestionsSecretes.PaireNettoyee> n = QuestionsSecretes.nettoyer(List.of(
                p("  Ville de naissance ? ", " Montréal "), p("Premier animal ?", "Rex"), p("École primaire ?", "St-Jean")));
        assertThat(n).extracting(QuestionsSecretes.PaireNettoyee::question)
                .containsExactly("Ville de naissance ?", "Premier animal ?", "École primaire ?");
        assertThat(n).extracting(QuestionsSecretes.PaireNettoyee::reponseNormalisee).containsExactly("montréal", "rex", "st-jean");
    }

    @Test
    void chaqueRefusPorteLeMessageDuNode() {
        assertThatThrownBy(() -> QuestionsSecretes.nettoyer(null)).hasMessage("at least 3 security questions are required");
        assertThatThrownBy(() -> QuestionsSecretes.nettoyer(List.of(p("Ville ?", "a"), p("Animal ?", "b"))))
                .hasMessage("at least 3 security questions are required");
        assertThatThrownBy(() -> QuestionsSecretes.nettoyer(List.of(p("Ville ?", "aa"), p("Ani", "bb"), p("École ?", "cc"))))
                .hasMessage("question #2 must be at least 5 characters");
        assertThatThrownBy(() -> QuestionsSecretes.nettoyer(List.of(p("Ville ?", "aa"), p("Animal ?", "bb"), p("École ?", " c "))))
                .hasMessage("answer #3 must be at least 2 characters");
        assertThatThrownBy(() -> QuestionsSecretes.nettoyer(List.of(p("Ville ?", "aa"), p("ville ?", "bb"), p("École ?", "cc"))))
                .hasMessage("security questions must be distinct");
        assertThatThrownBy(() -> QuestionsSecretes.nettoyer(Arrays.asList(null, p("Animal ?", "bb"), p("École ?", "cc"))))
                .hasMessage("question #1 must be at least 5 characters");
        assertThatThrownBy(() -> QuestionsSecretes.nettoyer(List.of(p("Ville ?", "aa"), new QuestionsSecretes.Paire("Animal ?", null), p("École ?", "cc"))))
                .hasMessage("answer #2 must be at least 2 characters");
        assertThatThrownBy(() -> QuestionsSecretes.nettoyer(List.of(new QuestionsSecretes.Paire(null, "aa"), p("Animal ?", "bb"), p("École ?", "cc"))))
                .hasMessage("question #1 must be at least 5 characters");
    }

    @Test
    void laListeEffectiveDedoublonneEtRetombeSurLesQuestionsDeSecours() {
        assertThat(QuestionsSecretes.effectives(List.of("Ville ?", " ville ? ", "", "Animal ?"))).containsExactly("Ville ?", "Animal ?");
        assertThat(QuestionsSecretes.effectives(List.of("  "))).isEqualTo(QuestionsSecretes.DEFAUT);
        assertThat(QuestionsSecretes.questionDeCatalogue("  Ville ? ")).isEqualTo("Ville ?");
        assertThatThrownBy(() -> QuestionsSecretes.questionDeCatalogue("abc")).hasMessage("la question doit comporter au moins 5 caractères");
        assertThatThrownBy(() -> QuestionsSecretes.questionDeCatalogue(null)).hasMessage("la question doit comporter au moins 5 caractères");
    }

    @Test
    void normaliserRogneEtAbaisse() {
        assertThat(QuestionsSecretes.normaliser(" ReX ")).isEqualTo("rex");
        assertThat(QuestionsSecretes.normaliser(null)).isEmpty();
    }
}
