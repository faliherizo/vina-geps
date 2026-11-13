package ca.vina.nexu.partage.erreurs.domaine;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Hors périmètre — le dossier visé et le motif")
class HorsPerimetreExceptionTest {

    @Test
    @DisplayName("le refus nomme le dossier visé ; un motif propre remplace le message par défaut")
    void dossierEtMotif() {
        HorsPerimetreException defaut = new HorsPerimetreException("e-1");
        assertThat(defaut.employeeId()).isEqualTo("e-1");
        assertThat(defaut.getMessage()).isEqualTo("forbidden (out of scope)");
        HorsPerimetreException motive = new HorsPerimetreException(null, "réservé à la direction");
        assertThat(motive.employeeId()).isNull();
        assertThat(motive.getMessage()).isEqualTo("réservé à la direction");
    }
}
