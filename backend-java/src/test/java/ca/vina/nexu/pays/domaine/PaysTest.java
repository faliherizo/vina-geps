package ca.vina.nexu.pays.domaine;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Pays — Number(x) de JavaScript, reproduit au chiffre près")
class PaysTest {

    @Test
    void nombreCommeJavaScript() {
        assertThat(Pays.nombre(null)).isNull();
        assertThat(Pays.nombre(Double.NaN)).isNull();
        assertThat(Pays.nombre(Double.POSITIVE_INFINITY)).isNull();
        assertThat(Pays.nombre("Infinity")).isNull();
        assertThat(Pays.nombre(" 12.5 ")).isEqualTo(12.5);
        assertThat(Pays.nombre("")).isZero();
        assertThat(Pays.nombre("x")).isNull();
        assertThat(Pays.nombre(true)).isNull();
        assertThat(Pays.periodes(null)).isEqualTo(12);
        assertThat(Pays.periodes(0)).isEqualTo(12);
        assertThat(Pays.periodes("26")).isEqualTo(26);
        assertThat(Pays.periodes("abc")).isEqualTo(12);
    }
}
