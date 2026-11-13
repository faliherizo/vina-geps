package ca.vina.nexu.communication.domaine;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * **L'aperçu d'une conversation** : de quoi reconnaître le fil, jamais de quoi le lire.
 *
 * Il sert la liste de gauche de la messagerie et le panneau de la barre du haut (maquette P11).
 * Les trois cas qui comptent sont ici : ce qui tient n'est pas coupé, ce qui dépasse est coupé
 * **sur un mot**, et un mot plus long que la limite est coupé net — sans quoi la recherche du
 * dernier espace rendrait une chaîne vide.
 */
@DisplayName("Messagerie — l'aperçu d'un fil")
class MessagerieApercuTest {

    @Test
    @DisplayName("les blancs sont ramenés à un espace, et ce qui tient n'est pas coupé")
    void courtEtNettoye() {
        assertThat(Messagerie.apercu("  Deux   civières \n au sous-sol. ")).isEqualTo("Deux civières au sous-sol.");
    }

    @Test
    @DisplayName("ce qui dépasse est coupé sur un mot, suivi d'une ellipse")
    void coupeSurUnMot() {
        String texte = "Le transfert de la civière quatre est reporté à quatorze heures trente, "
                + "le bloc opératoire étant occupé par une urgence.";
        String coupe = Messagerie.apercu(texte);
        assertThat(coupe).endsWith("…");
        // La partie conservée est bien un préfixe du texte : on n'a pas coupé au milieu d'un mot.
        assertThat(texte).startsWith(coupe.substring(0, coupe.length() - 1));
        assertThat(coupe).doesNotContain("  ");
    }

    @Test
    @DisplayName("un seul mot plus long que la limite est coupé net, jamais réduit à rien")
    void motUnique() {
        String coupe = Messagerie.apercu("x".repeat(200));
        assertThat(coupe).hasSize(91).endsWith("…");
    }
}
