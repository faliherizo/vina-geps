package ca.vina.nexu.effectifs.domaine;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("Agph — traitement du délai échu et renonciation (AD-05 ; addenda 7, Q12)")
class AgphDelaiEchuTest {

    @Test
    @DisplayName("absent : l'attribution, comme avant ; sinon un ensemble fermé, refusé en le nommant")
    void traitement() {
        assertThat(Agph.TraitementDelaiEchu.de(null)).isEqualTo(Agph.TraitementDelaiEchu.ATTRIBUER);
        assertThat(Agph.TraitementDelaiEchu.de("PASSER")).isEqualTo(Agph.TraitementDelaiEchu.PASSER);
        assertThatThrownBy(() -> Agph.TraitementDelaiEchu.de("passer")).isInstanceOf(EffectifsInvalideException.class)
                .hasMessage("« traitementDelaiEchu » : ATTRIBUER, PASSER");
        assertThat(Arrays.asList(Agph.EtatRang.values())).contains(Agph.EtatRang.RENONCE);
    }
}
