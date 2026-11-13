package ca.vina.nexu.saas.domaine;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Les verrous et l'empreinte de la destruction attestée — déterministes, sans base. */
class DestructionTest {

    @Test
    void lesVerrousParlentDansLOrdreDuNode() {
        assertThatThrownBy(() -> Destruction.verifierVerrous("terminated", "CSS", "CSS", true)).hasMessageContaining("déjà été détruites");
        assertThatThrownBy(() -> Destruction.verifierVerrous("active", "CSS", "CSS", false)).hasMessage("l'établissement doit être résilié avant la destruction de ses données");
        assertThatThrownBy(() -> Destruction.verifierVerrous("terminated", "CSS", "autre", false)).hasMessage("confirmation : retapez le nom exact de l'établissement");
        Destruction.verifierVerrous("terminated", "CSS", "CSS", false);
    }

    @Test
    void lEmpreinteEstStableEtLAlgorithmeGaranti() {
        List<Destruction.Table> tables = List.of(new Destruction.Table("employees", 2), new Destruction.Table("users", 1));
        String e1 = Destruction.empreinte("t", "2026-09-14T00:00:00.000Z", "sa", tables);
        assertThat(e1).matches("[0-9a-f]{64}").isEqualTo(Destruction.empreinte("t", "2026-09-14T00:00:00.000Z", "sa", tables));
        assertThat(Destruction.empreinte("t", "2026-09-14T00:00:00.000Z", "sa", List.of())).isNotEqualTo(e1);
        assertThatThrownBy(() -> Destruction.condensat("inexistant")).isInstanceOf(IllegalStateException.class).hasMessageContaining("inexistant");
        assertThat(Destruction.enCarte(new Destruction.Attestation("i", "t", "N", "d", "p", "m", tables, 3, e1))).containsEntry("totalLignes", 3);
    }
}
