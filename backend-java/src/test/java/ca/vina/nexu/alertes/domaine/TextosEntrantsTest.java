package ca.vina.nexu.alertes.domaine;

import ca.vina.nexu.alertes.domaine.TextosEntrants.Intention;
import ca.vina.nexu.alertes.domaine.TextosEntrants.Issue;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("Réponse par texto (ST-G05) — ce que veut dire un texto, et ce qu'on répond")
class TextosEntrantsTest {

    @Test
    @DisplayName("« 1 », « OK », « oui. » : prise en charge ; « 2 », « Non ! » : refus ; tout le reste est inconnu, jamais une confirmation")
    void intentions() {
        for (String oui : new String[]{"1", "ok", " OK ", "Oui.", "oui !", "Y"}) {
            assertThat(Intention.de(oui)).as(oui).isEqualTo(Intention.PRISE_EN_CHARGE);
        }
        for (String non : new String[]{"2", "non", "Non !", "N"}) {
            assertThat(Intention.de(non)).as(non).isEqualTo(Intention.REFUS);
        }
        for (String autre : new String[]{"", null, "ok merci", "3", "j'arrive", "11"}) {
            assertThat(Intention.de(autre)).as(String.valueOf(autre)).isEqualTo(Intention.INCONNUE);
        }
        assertThat(Intention.PRISE_EN_CHARGE.semantique()).isEqualTo(Semantique.PRISE_EN_CHARGE);
        assertThat(Intention.REFUS.semantique()).isEqualTo(Semantique.REFUS);
        assertThatThrownBy(Intention.INCONNUE::semantique).isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("chaque issue dit ce qui a été compris ; un message rejoué ne reçoit rien")
    void messages() {
        assertThat(Issue.PRISE_EN_CHARGE.message("Code bleu")).isEqualTo("Prise en charge enregistrée : « Code bleu ». Merci.");
        assertThat(Issue.REFUS.message("Code bleu")).isEqualTo("Refus enregistré : « Code bleu ».");
        assertThat(Issue.AIDE.message("x")).startsWith("Répondez 1 (ou OK)");
        assertThat(Issue.SANS_ALERTE.message("")).isEqualTo("Aucune alerte n'attend votre réponse.");
        assertThat(Issue.DEJA_REPONDU.message("Code bleu")).isEqualTo("Vous avez déjà répondu à « Code bleu ».");
        assertThat(Issue.NON_PROPOSEE.message("Code bleu")).contains("n'est pas proposée");
        assertThat(Issue.REJOUE.message("x")).isNull();
        assertThat(Issue.de("deja_repondu")).isEqualTo(Issue.DEJA_REPONDU);
        assertThat(Issue.AIDE.code()).isEqualTo("aide");
        assertThatThrownBy(() -> Issue.de("x")).isInstanceOf(IllegalArgumentException.class);
    }
}
