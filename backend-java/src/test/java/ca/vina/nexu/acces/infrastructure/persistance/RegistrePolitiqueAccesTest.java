package ca.vina.nexu.acces.infrastructure.persistance;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Registre de la politique d'accès — chargement et échecs de chargement.
 */
@DisplayName("Registre de la politique d'accès")
class RegistrePolitiqueAccesTest {

    @Test
    @DisplayName("la table des droits se charge et alimente la décision")
    void chargement() {
        RegistrePolitiqueAcces registre = new RegistrePolitiqueAcces();
        assertThat(registre.politique()).isNotNull();
        assertThat(registre.decision()).isNotNull();
    }

    @Test
    @DisplayName("une table de droits absente arrête le démarrage")
    void tableAbsente() {
        // C'est le cas le plus dangereux des trois : une table de droits vide ne refuse pas
        // tout, elle rend des listes de capacités vides — un utilisateur légitime perdrait ses
        // accès sans qu'aucune erreur ne soit journalisée.
        assertThatThrownBy(() -> new RegistrePolitiqueAcces("/politique-acces-inexistant.json"))
                .isInstanceOf(IllegalStateException.class)
                .rootCause().hasMessageContaining("/politique-acces-inexistant.json");
    }

    @Test
    @DisplayName("une table de droits illisible arrête le démarrage")
    void tableIllisible() {
        assertThatThrownBy(() -> new RegistrePolitiqueAcces("/registre-corrompu.json"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("table des droits illisible");
    }
}
