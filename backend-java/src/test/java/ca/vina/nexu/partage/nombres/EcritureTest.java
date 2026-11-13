package ca.vina.nexu.partage.nombres;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Écrire un nombre comme JavaScript l'écrit.
 *
 * <p>Les deux cas qui comptent sont les deux bords : l'absence de nombre, et le zéro négatif —
 * qui existe en virgule flottante et jamais dans une phrase. Un motif qui annoncerait « -0 $
 * restent » ferait douter du reste du rapport.
 */
@DisplayName("Écriture — un nombre dans une phrase, comme le Node l'écrit")
class EcritureTest {

    @Test
    @DisplayName("retire les zéros décimaux inutiles et défait la notation scientifique")
    void formeSimple() {
        assertThat(Ecriture.js(new BigDecimal("20.00"))).isEqualTo("20");
        assertThat(Ecriture.js(new BigDecimal("100"))).isEqualTo("100");
        assertThat(Ecriture.js(new BigDecimal("1E+2"))).isEqualTo("100");
        assertThat(Ecriture.js(new BigDecimal("0.3300"))).isEqualTo("0.33");
        assertThat(Ecriture.js(new BigDecimal("-12.50"))).isEqualTo("-12.5");
    }

    @Test
    @DisplayName("**l'absence de nombre s'écrit « 0 »**, jamais « null » au milieu d'une phrase")
    void absence() {
        assertThat(Ecriture.js(null)).isEqualTo("0");
    }

    @Test
    @DisplayName("**le zéro négatif devient « 0 »** : il existe en virgule flottante, jamais dans un état financier")
    void zeroNegatif() {
        assertThat(Ecriture.js(new BigDecimal("-0.00"))).isEqualTo("0");
        assertThat(Ecriture.js(new BigDecimal("0.00"))).isEqualTo("0");
        assertThat(Ecriture.js(BigDecimal.ZERO.negate())).isEqualTo("0");
    }
}
