package ca.vina.nexu.effectifs.domaine;

import ca.vina.nexu.effectifs.domaine.RetentionEffectifs.Classe;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** <strong>Rétention des effectifs (lot R11) — domaine pur</strong>, jumeau de {@code effectifs-retention-domaine.test.ts}. */
@DisplayName("rétention — domaine")
class RetentionEffectifsDomaineTest {

    private static Map<String, Object> carte(Object... paires) {
        Map<String, Object> m = new LinkedHashMap<>();
        for (int i = 0; i < paires.length; i += 2) {
            m.put((String) paires[i], paires[i + 1]);
        }
        return m;
    }

    @Test
    @DisplayName("les classes, dans l'ordre du Node, et leurs libellés")
    void classes() {
        assertThat(List.of(Classe.values())).extracting(Enum::name).containsExactly("HORAIRES", "PRESENCE", "TEMPS", "ABSENCES", "JOURNAUX");
        assertThat(Classe.TEMPS.libelle()).isEqualTo("Feuilles de temps approuvées");
    }

    @Test
    @DisplayName("valide la classe")
    void classe() {
        assertThat(RetentionEffectifs.classeDe("TEMPS")).isEqualTo(Classe.TEMPS);
        assertThatThrownBy(() -> RetentionEffectifs.classeDe("TOUT")).isInstanceOf(EffectifsInvalideException.class)
                .hasMessage("classe : HORAIRES, PRESENCE, TEMPS, ABSENCES, JOURNAUX");
    }

    @Test
    @DisplayName("valide la politique ; null retire une durée ; l'ordre de la demande est gardé")
    void politique() {
        Map<Classe, Integer> p = RetentionEffectifs.politiqueDe(carte("JOURNAUX", 7, "TEMPS", null, "ABSENCES", "  12 ", "HORAIRES", true, "PRESENCE", 30L));
        assertThat(p.keySet()).containsExactly(Classe.JOURNAUX, Classe.TEMPS, Classe.ABSENCES, Classe.HORAIRES, Classe.PRESENCE);
        assertThat(p.values()).containsExactly(7, null, 12, 1, 30);
        assertThat(RetentionEffectifs.politiqueDe(new HashMap<>())).isEmpty();
        assertThatThrownBy(() -> RetentionEffectifs.politiqueDe(List.of())).hasMessage("« politique » : un objet { classe: années }");
        assertThatThrownBy(() -> RetentionEffectifs.politiqueDe(null)).hasMessageStartingWith("« politique »");
        assertThatThrownBy(() -> RetentionEffectifs.politiqueDe(carte("AUTRE", 3))).hasMessageStartingWith("classe :");
    }

    @Test
    @DisplayName("une durée est un entier de 1 à 30 ans, quelle que soit sa forme")
    void durees() {
        String message = "« JOURNAUX » : un nombre entier d'années, de 1 à 30";
        for (Object v : new Object[]{0, 31, 2.5, -1, false, "", "abc", "2.5", List.of(5), Double.NaN}) {
            assertThatThrownBy(() -> RetentionEffectifs.politiqueDe(carte("JOURNAUX", v))).as("valeur %s", v)
                    .isInstanceOf(EffectifsInvalideException.class).hasMessage(message);
        }
        assertThat(RetentionEffectifs.politiqueDe(carte("JOURNAUX", 1.0))).containsEntry(Classe.JOURNAUX, 1);
        assertThat(RetentionEffectifs.politiqueDe(carte("JOURNAUX", "30"))).containsEntry(Classe.JOURNAUX, 30);
    }

    @Test
    @DisplayName("calcule la limite en années civiles, le 29 février compris")
    void limite() {
        assertThat(RetentionEffectifs.limiteDe("2031-01-15", 5)).isEqualTo("2026-01-15");
        assertThat(RetentionEffectifs.limiteDe("2028-02-29", 1)).isEqualTo("2027-02-28");
        assertThat(RetentionEffectifs.limiteDe("2028-02-29", 4)).isEqualTo("2024-02-29");
    }
}
