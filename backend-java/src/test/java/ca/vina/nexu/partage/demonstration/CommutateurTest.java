package ca.vina.nexu.partage.demonstration;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Commutateur — brancher un remplaçant, revenir au défaut")
class CommutateurTest {

    @Test
    void permutation() {
        Commutateur<String> c = new Commutateur<>("defaut");
        assertThat(c.courant()).isEqualTo("defaut");
        assertThat(c.estBranche()).isFalse();
        c.brancher("demo");
        assertThat(c.courant()).isEqualTo("demo");
        assertThat(c.defaut()).isEqualTo("defaut");
        assertThat(c.estBranche()).isTrue();
        c.debrancher();
        assertThat(c.courant()).isEqualTo("defaut");
        assertThat(c.estBranche()).isFalse();
        RegistreDemonstrations aucune = RegistreDemonstrations.aucune("http://localhost:4000");
        assertThat(aucune.estBranchee("entra")).isFalse();
        assertThat(aucune.urlApplication()).isEmpty();
        assertThat(aucune.urlServeur()).isEqualTo("http://localhost:4000");
        assertThat(aucune.reglages("saml")).isEmpty();
    }
}
