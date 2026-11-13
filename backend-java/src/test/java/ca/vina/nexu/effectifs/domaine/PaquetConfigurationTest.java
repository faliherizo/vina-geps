package ca.vina.nexu.effectifs.domaine;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("P-TN-1 — le paquet de configuration : en-tête, comparaison, ordre des unités, prise d'effet")
class PaquetConfigurationTest {

    private static ValeurReferentiel unite(String code, String parent) {
        return new ValeurReferentiel("id-" + code, "unites", code, code, null, null, 0, parent == null ? Map.of() : Map.of("parent", parent), "2026-01-01", null);
    }

    @Test
    @DisplayName("seul un paquet de ce format et de cette version s'importe")
    void entete() {
        PaquetConfiguration.verifierEntete(Map.of("format", PaquetConfiguration.FORMAT, "version", 1));
        assertThatThrownBy(() -> PaquetConfiguration.verifierEntete(null)).isInstanceOf(EffectifsInvalideException.class).hasMessageContaining("pas un paquet");
        assertThatThrownBy(() -> PaquetConfiguration.verifierEntete(Map.of("format", "autre"))).isInstanceOf(EffectifsInvalideException.class);
        assertThatThrownBy(() -> PaquetConfiguration.verifierEntete(Map.of("format", PaquetConfiguration.FORMAT, "version", 2)))
                .isInstanceOf(EffectifsInvalideException.class).hasMessage("version de paquet non prise en charge (attendue : 1)");
        assertThatThrownBy(() -> PaquetConfiguration.verifierEntete(Map.of("format", PaquetConfiguration.FORMAT, "version", "1")))
                .isInstanceOf(EffectifsInvalideException.class);
    }

    @Test
    @DisplayName("une valeur absente se crée, une valeur différente se remplace, une valeur égale est laissée — 3 vaut 3.0")
    void comparer() {
        Map<String, Object> v = PaquetConfiguration.valeur(unite("EST", "US"));
        assertThat(v).containsEntry("code", "EST").containsEntry("ordre", 0).containsEntry("attributs", Map.of("parent", "US"));
        assertThat(PaquetConfiguration.comparerValeur(null, v)).isEqualTo(PaquetConfiguration.Action.CREER);
        assertThat(PaquetConfiguration.comparerValeur(v, new HashMap<>(v))).isEqualTo(PaquetConfiguration.Action.INCHANGE);
        Map<String, Object> autre = new HashMap<>(v);
        autre.put("libelleFr", "Secteur Est");
        assertThat(PaquetConfiguration.comparerValeur(v, autre)).isEqualTo(PaquetConfiguration.Action.REMPLACER);
        Map<String, Object> regle = Map.of("contexte", "ECHANGE", "cle", "contigu", "porteeType", "ORGANISME", "condition", Map.of(), "action", "AVERTIR",
                "message", "m", "priorite", 3);
        Map<String, Object> memeEnReel = new HashMap<>(regle);
        memeEnReel.put("priorite", 3.0);
        assertThat(PaquetConfiguration.comparerRegle(regle, memeEnReel)).isEqualTo(PaquetConfiguration.Action.INCHANGE);
        memeEnReel.put("priorite", 4);
        assertThat(PaquetConfiguration.comparerRegle(regle, memeEnReel)).isEqualTo(PaquetConfiguration.Action.REMPLACER);
        assertThat(PaquetConfiguration.cleRegle(regle)).isEqualTo("ECHANGE|contigu|ORGANISME|null");
        assertThat(PaquetConfiguration.regle(regle)).containsOnlyKeys("contexte", "cle", "porteeType", "porteeCode", "condition", "action", "message", "priorite");
        assertThat(PaquetConfiguration.contenuRegle(regle)).containsOnlyKeys("condition", "action", "message", "priorite");
    }

    @Test
    @DisplayName("les unités partent parentes d'abord ; un parent hors du paquet ne bloque pas ; un cycle part tel quel")
    void parentsDabord() {
        List<ValeurReferentiel> ordre = PaquetConfiguration.parentsDabord(List.of(unite("C", "B"), unite("B", "A"), unite("A", null), unite("X", "AILLEURS")));
        assertThat(ordre).extracting(ValeurReferentiel::code).containsExactly("A", "X", "B", "C");
        assertThat(PaquetConfiguration.parentsDabord(List.of(unite("P", "Q"), unite("Q", "P")))).extracting(ValeurReferentiel::code).containsExactly("P", "Q");
    }

    @Test
    @DisplayName("la prise d'effet : le lendemain par défaut, la date demandée sinon, jamais le passé")
    void priseDEffet() {
        assertThat(PaquetConfiguration.priseDEffet(null, "2027-01-31")).isEqualTo("2027-02-01");
        assertThat(PaquetConfiguration.priseDEffet(" ", "2027-01-31")).isEqualTo("2027-02-01");
        assertThat(PaquetConfiguration.priseDEffet("2027-01-31", "2027-01-31")).isEqualTo("2027-01-31");
        assertThatThrownBy(() -> PaquetConfiguration.priseDEffet("2027-01-30", "2027-01-31")).isInstanceOf(EffectifsInvalideException.class)
                .hasMessage("une configuration promue ne prend pas effet dans le passé (2027-01-30)");
        assertThat(new PaquetConfiguration.Ligne("regle", "k", PaquetConfiguration.Action.CREER).enCarte())
                .containsEntry("objet", "regle").containsEntry("cle", "k").containsEntry("action", "creer");
    }
}
