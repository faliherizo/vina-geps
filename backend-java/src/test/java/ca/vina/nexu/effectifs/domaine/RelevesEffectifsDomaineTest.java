package ca.vina.nexu.effectifs.domaine;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * <strong>Les axes des relevés d'effectifs</strong> (P-RF-1) — jumeau de {@code effectifs-releves-domaine.test.ts} : une même
 * personne tombe dans le même groupe quelle que soit la source lue.
 */
@DisplayName("Relevés d'effectifs — période, unité et ancêtres, personne, quart")
class RelevesEffectifsDomaineTest {

    private static Map<String, String> parents() {
        Map<String, String> p = new HashMap<>();
        p.put("US", null);
        p.put("EST", "US");
        p.put("C12", "EST");
        p.put("X", "Y");
        p.put("Y", "X");
        return p;
    }

    @Test
    @DisplayName("la période d'une date : le jour, le lundi de sa semaine, son mois")
    void periode() {
        assertThat(RelevesEffectifs.axesDePeriode("2031-01-16")).containsExactly(Map.entry("jour", "2031-01-16"), Map.entry("semaine", "2031-01-13"),
                Map.entry("mois", "2031-01"));
        assertThat(new RelevesEffectifs.Periode("2031-01-10", "2031-01-12").contient("2031-01-12")).isTrue();
        assertThat(new RelevesEffectifs.Periode("2031-01-10", "2031-01-12").contient("2031-01-09")).isFalse();
        assertThat(new RelevesEffectifs.Periode("2031-01-10", "2031-01-12").contient("2031-01-13")).isFalse();
    }

    @Test
    @DisplayName("le chemin d'une unité va de la racine à elle ; une boucle s'arrête ; les ancêtres deviennent des niveaux")
    void unites() {
        assertThat(RelevesEffectifs.cheminDe(parents(), "C12")).containsExactly("US", "EST", "C12");
        assertThat(RelevesEffectifs.cheminDe(parents(), "INCONNUE")).containsExactly("INCONNUE");
        assertThat(RelevesEffectifs.cheminDe(parents(), "X")).containsExactly("Y", "X");
        assertThat(RelevesEffectifs.axesDUnite(parents(), "C12")).containsExactly(Map.entry("unite", "C12"), Map.entry("uniteParente", "EST"),
                Map.entry("uniteNiveau1", "US"), Map.entry("uniteNiveau2", "EST"), Map.entry("uniteNiveau3", "C12"));
        Map<String, Object> racine = RelevesEffectifs.axesDUnite(parents(), "US");
        assertThat(racine.get("uniteParente")).isNull();
        assertThat(racine.get("uniteNiveau2")).isNull();
        assertThat(racine.get("uniteNiveau1")).isEqualTo("US");
    }

    @Test
    @DisplayName("la personne et le quart ; nuls quand on ne les connaît pas")
    void personneEtQuart() {
        assertThat(RelevesEffectifs.axesDePersonne(new RelevesEffectifs.PersonneLue("P1", "Julie", "PARA", "TECH", "TC", "FSSS")))
                .isEqualTo(Map.of("ressource", "P1 — Julie", "corpsEmploi", "PARA", "titreEmploi", "TECH", "statutEmploi", "TC", "convention", "FSSS"));
        assertThat(RelevesEffectifs.axesDePersonne(null)).hasSize(5).allSatisfy((k, v) -> assertThat(v).isNull());
        assertThat(RelevesEffectifs.axesDeQuart(new RelevesEffectifs.QuartLu("REG", "C12"))).isEqualTo(Map.of("milieu", "C12", "natureQuart", "REG"));
        assertThat(RelevesEffectifs.axesDeQuart(null)).hasSize(2).allSatisfy((k, v) -> assertThat(v).isNull());
    }

    @Test
    @DisplayName("les jours d'une absence bornés à la période ; les heures décimales ; les sources par leur code")
    void joursHeuresSources() {
        RelevesEffectifs.Periode p = new RelevesEffectifs.Periode("2031-01-10", "2031-01-12");
        assertThat(RelevesEffectifs.joursEntre("2031-01-08", "2031-01-11", p)).containsExactly("2031-01-10", "2031-01-11");
        assertThat(RelevesEffectifs.joursEntre("2031-01-11", "2031-01-20", p)).containsExactly("2031-01-11", "2031-01-12");
        assertThat(RelevesEffectifs.joursEntre("2031-01-13", "2031-01-20", p)).isEmpty();
        assertThat(List.of(RelevesEffectifs.heures(500), RelevesEffectifs.heures(-20), RelevesEffectifs.heures(0), RelevesEffectifs.heures(720)))
                .containsExactly(8.33, -0.33, 0L, 12L);
        assertThat(RelevesEffectifs.Source.de("effectifs_places")).isEqualTo(RelevesEffectifs.Source.PLACES);
        assertThat(RelevesEffectifs.Source.PLACES.code()).isEqualTo("effectifs_places");
        assertThatThrownBy(() -> RelevesEffectifs.Source.de("users")).isInstanceOf(EffectifsInvalideException.class)
                .hasMessage("source de relevé inconnue : « users »");
    }

    @org.junit.jupiter.api.Test
    @org.junit.jupiter.api.DisplayName("addenda 8, M-18 : durée d'un quart (la nuit passe minuit), heure lisible, écart au budget, sources nommées")
    void horairesEtBudget() {
        assertThat(List.of(RelevesEffectifs.dureeMinutes(360, 1080), RelevesEffectifs.dureeMinutes(1080, 360), RelevesEffectifs.dureeMinutes(0, 1440)))
                .containsExactly(720, 720, 1440);
        assertThat(List.of(RelevesEffectifs.hhmm(0), RelevesEffectifs.hhmm(365), RelevesEffectifs.hhmm(1440))).containsExactly("00:00", "06:05", "24:00");
        assertThat(new RelevesEffectifs.HeuresSemaine(20, 2160, 1440).ecart()).isEqualTo(16L);
        assertThat(new RelevesEffectifs.HeuresSemaine(37.5, 2160, 0).ecart()).isEqualTo(-1.5);
        assertThat(List.of(RelevesEffectifs.Source.de("effectifs_horaires"), RelevesEffectifs.Source.de("effectifs_budget")))
                .containsExactly(RelevesEffectifs.Source.HORAIRES, RelevesEffectifs.Source.BUDGET);
    }
}

