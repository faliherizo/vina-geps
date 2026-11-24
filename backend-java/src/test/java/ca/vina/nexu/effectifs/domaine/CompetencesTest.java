package ca.vina.nexu.effectifs.domaine;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("Compétences et échéances (addenda 8, M-04) — détenues, exigées, échues, relevé")
class CompetencesTest {

    private static Competences.Detenue d(String code, String obtenue, String echeance) {
        return new Competences.Detenue("r1", code, obtenue, echeance);
    }

    @Test
    @DisplayName("une compétence vaut de son obtention à son échéance incluse ; sans échéance, pour toujours")
    void validite() {
        Competences.Detenue rcr = d("RCR", "2026-01-10", "2026-11-30");
        assertThat(rcr.valideLe("2026-01-09")).isFalse();
        assertThat(rcr.valideLe("2026-01-10")).isTrue();
        assertThat(rcr.valideLe("2026-11-30")).isTrue();
        assertThat(rcr.valideLe("2026-12-01")).isFalse();
        assertThat(d("PERMIS", "2020-05-01", null).valideLe("2040-01-01")).isTrue();
    }

    @Test
    @DisplayName("la saisie exige la date d'obtention ; l'échéance la suit, ou manque")
    void saisie() {
        assertThat(Competences.saisie("r1", "RCR", "2026-01-10", "2027-01-10")).isEqualTo(d("RCR", "2026-01-10", "2027-01-10"));
        assertThat(Competences.saisie("r1", "RCR", "2026-01-10", "").echeance()).isNull();
        assertThat(Competences.saisie("r1", "RCR", "2026-01-10", null).echeance()).isNull();
        assertThatThrownBy(() -> Competences.saisie("r1", "RCR", null, null)).hasMessageContaining("« obtenueLe »");
        assertThatThrownBy(() -> Competences.saisie("r1", "RCR", "2026-01-10", "10/01/2027")).hasMessageContaining("« echeance »");
        assertThatThrownBy(() -> Competences.saisie("r1", "RCR", "2026-01-10", "2025-12-31"))
                .isInstanceOf(EffectifsInvalideException.class).hasMessage("« echeance » (2025-12-31) précède « obtenueLe » (2026-01-10)");
    }

    @Test
    @DisplayName("au jour du quart : les exigées qui manquent ou sont échues, et les jours avant la prochaine échéance")
    void faits() {
        List<Competences.Detenue> siennes = List.of(d("RCR", "2026-01-10", "2026-12-05"), d("PERMIS", "2020-05-01", null), d("AUTRE", "2026-01-01", "2026-12-02"));
        Competences.Faits f = Competences.Faits.de(List.of("RCR", "PERMIS", "SIP"), siennes, "2026-12-01");
        assertThat(f.manquantes()).containsExactly("SIP");
        // Seule une compétence exigée compte : AUTRE échoit plus tôt, mais personne ne l'exige.
        assertThat(f.joursAvantEcheance()).isEqualTo(4);
        Competences.Faits apres = Competences.Faits.de(List.of("RCR", "PERMIS"), siennes, "2026-12-06");
        assertThat(apres.manquantes()).containsExactly("RCR");
        assertThat(apres.joursAvantEcheance()).isEqualTo(Competences.JOURS_SANS_ECHEANCE);
        // Un doublon de code ne fait pas tomber le calcul : la première détention compte.
        assertThat(Competences.manquantes(List.of("RCR"), List.of(d("RCR", "2026-01-10", null), d("RCR", "2026-01-10", "2026-01-11")), "2026-06-01")).isEmpty();
        assertThat(Competences.Faits.AUCUNE).isEqualTo(new Competences.Faits(List.of(), Competences.JOURS_SANS_ECHEANCE));
        assertThat(Competences.Source.AUCUNE.faits("t", "r", "PARA", "2026-01-01")).isSameAs(Competences.Faits.AUCUNE);
    }

    @Test
    @DisplayName("le relevé : échues et à échoir dans l'horizon, la plus pressante d'abord ; sans échéance ou au-delà, rien")
    void releve() {
        List<Competences.Echeance> r = Competences.releve(List.of(d("A", "2025-01-01", "2026-12-30"), d("B", "2025-01-01", "2026-11-20"),
                d("C", "2025-01-01", null), d("D", "2025-01-01", "2027-06-01"), d("E", "2025-01-01", "2026-11-23")), "2026-11-23", 60);
        assertThat(r).extracting(e -> e.competence().code(), e -> e.etat(), Competences.Echeance::jours)
                .containsExactly(org.assertj.core.groups.Tuple.tuple("B", Competences.Etat.ECHUE, -3L),
                        org.assertj.core.groups.Tuple.tuple("E", Competences.Etat.A_ECHOIR, 0L),
                        org.assertj.core.groups.Tuple.tuple("A", Competences.Etat.A_ECHOIR, 37L));
        assertThat(List.of(Competences.Etat.ECHUE.code(), Competences.Etat.A_ECHOIR.code(), Competences.Etat.VALIDE.code()))
                .containsExactly("echue", "a_echoir", "valide");
    }

    @Test
    @DisplayName("l'horizon : deux mois par défaut, un entier de 0 à 3650 sinon")
    void horizon() {
        assertThat(Competences.horizon(null)).isEqualTo(60);
        assertThat(Competences.horizon(" ")).isEqualTo(60);
        assertThat(Competences.horizon(" 30 ")).isEqualTo(30);
        assertThat(Competences.horizon("0")).isZero();
        assertThat(Competences.horizon("3650")).isEqualTo(3650);
        for (String mauvais : List.of("3651", "-1", "trente", "1.5")) {
            assertThatThrownBy(() -> Competences.horizon(mauvais)).as(mauvais).hasMessage("« dans » : un nombre de jours entre 0 et 3650");
        }
    }
}
