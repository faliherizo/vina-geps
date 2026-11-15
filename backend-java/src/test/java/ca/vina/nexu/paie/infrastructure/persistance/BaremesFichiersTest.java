package ca.vina.nexu.paie.infrastructure.persistance;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("Barèmes de paie — un fichier par année, lu strictement")
class BaremesFichiersTest {

    @Test
    @DisplayName("un champ inconnu arrête la paie en nommant l'année ; une année absente est vide, pas une erreur")
    void lectureStricte() {
        BaremesFichiers essai = new BaremesFichiers("/paie-essai/bareme-%d.json");
        assertThatThrownBy(() -> essai.pour(1998)).isInstanceOf(IllegalStateException.class).hasMessageStartingWith("barème de paie 1998 illisible");
        assertThat(essai.pour(1997)).isEmpty();
    }
}
