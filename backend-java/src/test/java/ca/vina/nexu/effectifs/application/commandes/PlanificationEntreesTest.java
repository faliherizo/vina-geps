package ca.vina.nexu.effectifs.application.commandes;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** La lecture d'un nombre de corps JSON suit {@code Number(v)} du Node : ce qui n'en est pas un vaut NaN, jamais une exception. */
@DisplayName("PlanificationEntrees — un nombre comme le Node le lit")
class PlanificationEntreesTest {

    @Test
    @DisplayName("nombre, texte numérique, vide, booléen, nul et texte quelconque")
    void nombres() {
        assertThat(List.of(PlanificationEntrees.nombre(3), PlanificationEntrees.nombre(" 2.5 "), PlanificationEntrees.nombre(""), PlanificationEntrees.nombre(null),
                PlanificationEntrees.nombre(true), PlanificationEntrees.nombre(false))).containsExactly(3.0, 2.5, 0.0, 0.0, 1.0, 0.0);
        assertThat(PlanificationEntrees.nombre("abc")).isNaN();
    }
}
