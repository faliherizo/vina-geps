package ca.vina.nexu.pays.infrastructure.persistance;

import ca.vina.nexu.pays.domaine.PaysPack;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Registre des packs pays — chargement et échecs de chargement.
 *
 * <p>Un registre de données qui échoue doit le faire <strong>bruyamment, au démarrage</strong>.
 * Le pire comportement serait de démarrer sur un registre vide : la page de connexion n'offrirait
 * plus aucun pays, et les moteurs rendraient des paies à zéro sans qu'aucune erreur ne remonte.
 */
@DisplayName("Registre des packs pays")
class RegistrePaysTest {

    private final RegistrePays registre = new RegistrePays();

    @Test
    @DisplayName("l'ordre du catalogue est celui du Node — il fait partie du contrat")
    void ordreDuCatalogue() {
        // Cet ordre pilote le sélecteur de pays de la page de connexion. Un tri « amélioré »
        // côté Java changerait l'écran sans qu'aucun code d'interface n'ait bougé.
        List<PaysPack> tous = registre.tous();
        assertThat(tous).extracting(PaysPack::code).containsExactly("CA", "FR", "MG", "US");
        assertThat(registre.paysParDefaut()).isEqualTo("CA");
    }

    @Test
    @DisplayName("l'existence d'un code se distingue de l'absence de pack")
    void existence() {
        assertThat(registre.existe("CA")).isTrue();
        assertThat(registre.existe("ZZ")).isFalse();
        assertThat(registre.pack("ZZ")).isEmpty();
    }

    @Test
    @DisplayName("le pack brut conserve les champs que le modèle typé ne porte pas")
    void packBrut() {
        // La retransmission passe par le JSON d'origine : `ceiling: null` explicite et
        // `employeeRate` absent coexistent dans la source, et aucune politique d'inclusion
        // unique ne saurait rendre les deux.
        assertThat(registre.brut("CA")).isNotNull();
        assertThat(registre.brut("CA").get("code").asString()).isEqualTo("CA");
        assertThat(registre.brut("ZZ")).isNull();
        // la vue publique : les champs de publicView dans l'ordre, additionalRates jamais absent
        assertThat(registre.vuePublique("CA").keySet()).startsWith("code", "name", "flag").endsWith("additionalRates");
        assertThat(registre.vuePublique("CA").get("additionalRates")).isEqualTo(java.util.List.of());
        assertThat(registre.vuePublique("FR").get("additionalRates")).isNotEqualTo(java.util.List.of());
    }

    @Test
    @DisplayName("une ressource absente arrête le démarrage en nommant le fichier")
    void ressourceAbsente() {
        assertThatThrownBy(() -> new RegistrePays("/pays-packs-inexistant.json"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("registre des pays illisible")
                .rootCause().hasMessageContaining("/pays-packs-inexistant.json");
    }

    @Test
    @DisplayName("une ressource illisible arrête le démarrage plutôt que de vider le registre")
    void ressourceIllisible() {
        assertThatThrownBy(() -> new RegistrePays("/registre-corrompu.json"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("registre des pays illisible");
    }
}
