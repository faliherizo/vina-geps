package ca.vina.nexu.integrations.domaine;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Le routage des courriels entrants ne se laisse pas occuper par un motif catastrophique (tests d'intrusion FX-AO, IN-FX-01).
 * Le motif et le sujet viennent tous deux de l'appelant : sans borne, {@code (a+)+$} contre trente « a » suivis d'un « ! »
 * explorait plus d'un milliard de chemins.
 */
@DisplayName("Routage des courriels — recherche bornée (ReDoS)")
class RoutageCourrielBorneTest {

    private static RoutageCourriel.Resultat router(String sujet, String... motifs) {
        return RoutageCourriel.router(new RoutageCourriel.Courriel("a@b.c", sujet, ""),
                java.util.Arrays.stream(motifs).map(m -> new RoutageCourriel.Route(m, "action-" + m)).toList());
    }

    @Test
    @Timeout(value = 5, unit = TimeUnit.SECONDS)
    @DisplayName("un motif catastrophique s'abandonne vite, sans correspondre, et la route suivante est essayée")
    void motifCatastrophique() {
        RoutageCourriel.Resultat r = router("a".repeat(40) + "!", "(a+)+$", "!");
        assertThat(r.matchedPattern()).isEqualTo("!");
        assertThat(r.action()).isEqualTo("action-!");
    }

    @Test
    @DisplayName("le routage ordinaire est inchangé : recherche partielle, sans tenir compte de la casse")
    void routageOrdinaire() {
        assertThat(router("Re: FACTURE 42", "facture").action()).isEqualTo("action-facture");
        assertThat(router("rien", "facture").action()).isNull();
    }

    @Test
    @DisplayName("un motif trop long est ignoré comme un motif invalide")
    void motifTropLong() {
        String motif = "a".repeat(RoutageCourriel.LONGUEUR_MAX_MOTIF + 1);
        assertThat(router(motif, motif).action()).isNull();
        assertThat(RoutageCourriel.correspond(null, "x")).isFalse();
        assertThat(RoutageCourriel.correspond("(", "x")).isFalse();
    }

    @Test
    @DisplayName("un sujet trop long est tronqué à la longueur d'une ligne d'en-tête")
    void sujetTronque() {
        String sujet = "x".repeat(RoutageCourriel.LONGUEUR_MAX_SUJET) + "FIN";
        assertThat(router(sujet, "FIN").action()).isNull();
        assertThat(router(sujet, "x{10}").action()).isEqualTo("action-x{10}");
    }

    @Test
    @DisplayName("le sujet borné compte ses lectures, y compris celles de ses sous-séquences, puis s'arrête")
    void sujetBorne() {
        RoutageCourriel.SujetBorne s = new RoutageCourriel.SujetBorne("abcdef", 3);
        CharSequence sous = s.subSequence(1, 4);
        assertThat(sous.toString()).isEqualTo("bcd");
        assertThat(sous.length()).isEqualTo(3);
        assertThat(s.charAt(0)).isEqualTo('a');
        assertThat(sous.charAt(0)).isEqualTo('b');
        assertThat(s.toString()).isEqualTo("abcdef");
        assertThat(s.charAt(1)).isEqualTo('b');
        assertThatThrownBy(() -> sous.charAt(1)).isInstanceOf(RoutageCourriel.BudgetEpuiseException.class);
        assertThat(List.of(s.length())).containsExactly(6);
    }
}
