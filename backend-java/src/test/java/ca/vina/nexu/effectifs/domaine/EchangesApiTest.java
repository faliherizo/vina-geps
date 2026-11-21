package ca.vina.nexu.effectifs.domaine;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** AD-06 : les bornes de l'API d'échange, converties une fois à la frontière et refusées en nommant le paramètre. */
@DisplayName("API d'échange : fenêtre, limite, curseur")
class EchangesApiTest {

    @Test
    @DisplayName("la fenêtre : deux dates incluses, ordonnées, 62 jours au plus")
    void fenetre() {
        assertThat(EchangesApi.Fenetre.de(" 2026-12-01", "2026-12-01 ")).isEqualTo(new EchangesApi.Fenetre(LocalDate.of(2026, 12, 1), LocalDate.of(2026, 12, 1)));
        assertThat(EchangesApi.Fenetre.de("2026-12-01", "2027-01-31").au()).isEqualTo(LocalDate.of(2027, 1, 31));
        assertThatThrownBy(() -> EchangesApi.Fenetre.de("2026-12-01", "2027-02-01")).hasMessage("fenêtre : 62 jours au plus ; paginez par fenêtres");
        assertThatThrownBy(() -> EchangesApi.Fenetre.de("2026-12-02", "2026-12-01")).hasMessage("« au » : ne précède pas « du »");
        assertThatThrownBy(() -> EchangesApi.Fenetre.de(null, "2026-12-01")).isInstanceOf(EffectifsInvalideException.class).hasMessage("« du » : date AAAA-MM-JJ exigée");
        assertThatThrownBy(() -> EchangesApi.Fenetre.de("2026-12-01", " ")).hasMessage("« au » : date AAAA-MM-JJ exigée");
        assertThatThrownBy(() -> EchangesApi.Fenetre.de("01/12/2026", "2026-12-01")).hasMessage("« du » : « 01/12/2026 » n'est pas une date AAAA-MM-JJ");
    }

    @Test
    @DisplayName("la limite : 100 par défaut, de 1 à 500 ; le curseur vide repart du début")
    void limiteEtCurseur() {
        assertThat(EchangesApi.limite(null)).isEqualTo(100);
        assertThat(EchangesApi.limite(" ")).isEqualTo(100);
        assertThat(EchangesApi.limite(" 1")).isEqualTo(1);
        assertThat(EchangesApi.limite("500")).isEqualTo(500);
        for (String hors : new String[] {"0", "501", "cent"}) {
            assertThatThrownBy(() -> EchangesApi.limite(hors)).isInstanceOf(EffectifsInvalideException.class).hasMessage("« limite » : entier de 1 à 500");
        }
        assertThat(EchangesApi.curseur(null)).isNull();
        assertThat(EchangesApi.curseur("  ")).isNull();
        assertThat(EchangesApi.curseur(" 2026-12-01T10:00:00.000Zj-1 ")).isEqualTo("2026-12-01T10:00:00.000Zj-1");
    }

    @Test
    @DisplayName("les formes rendues sont immuables, et seuls les états et objets voulus sont exposés")
    void formes() {
        List<EchangesApi.Place> places = new java.util.ArrayList<>(List.of(new EchangesApi.Place("p", 1, "PARA", null, null)));
        EchangesApi.Quart q = new EchangesApi.Quart("e", "per", "U1", "2026-12-01", 420, 1140, "JOUR", "m", places);
        places.clear();
        assertThat(q.places()).hasSize(1);
        assertThatThrownBy(() -> q.places().add(null)).isInstanceOf(UnsupportedOperationException.class);
        assertThat(new EchangesApi.Releve("c", "f", "r", "M", "U1", "d", "f", "APPROUVEE", "a", List.of()).segments()).isEmpty();
        assertThat(new EchangesApi.Page<>(List.of(1), "c").elements()).containsExactly(1);
        assertThat(EchangesApi.ETATS_EXPOSES).containsExactlyInAnyOrder(Planification.EtatPeriode.PUBLIEE, Planification.EtatPeriode.CLOSE);
        assertThat(EchangesApi.OBJETS_QUART).containsExactlyInAnyOrder("equipage", "place");
        assertThat(EchangesApi.STATUTS_VALIDES).containsExactlyInAnyOrder(TempsEffectifs.StatutFeuille.APPROUVEE, TempsEffectifs.StatutFeuille.PAYEE);
    }
}
