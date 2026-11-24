package ca.vina.nexu.effectifs.domaine;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("Rattachements à plusieurs unités (addenda 8, M-09) — saisie, chevauchement, unités en vigueur")
class RattachementsTest {

    private static final Ressource JULIE = new Ressource("r1", "P1", "Julie Roy", "u1", "EST", "PARA", null, null, null, 4, true);

    private static Rattachements.Rattachement r(String unite, String depuis, String jusqua) {
        return new Rattachements.Rattachement("r1", unite, depuis, jusqua);
    }

    @Test
    @DisplayName("la saisie : unité requise et autre que la principale, dates ordonnées, aucun chevauchement sur la même unité")
    void saisie() {
        assertThat(Rattachements.saisie(JULIE, " OUEST ", "2027-01-01", "", List.of())).isEqualTo(r("OUEST", "2027-01-01", null));
        assertThat(Rattachements.saisie(JULIE, "OUEST", "2027-01-01", "2027-03-31", List.of(r("OUEST", "2027-04-01", null), r("NORD", "2027-01-01", null))))
                .isEqualTo(r("OUEST", "2027-01-01", "2027-03-31"));
        assertThatThrownBy(() -> Rattachements.saisie(JULIE, null, "2027-01-01", null, List.of())).hasMessage("« uniteCode » requis");
        assertThatThrownBy(() -> Rattachements.saisie(JULIE, "EST", "2027-01-01", null, List.of())).hasMessage("« EST » est déjà l'unité principale de « Julie Roy »");
        assertThatThrownBy(() -> Rattachements.saisie(JULIE, "OUEST", "2027-02-01", "2027-01-01", List.of())).hasMessage("« jusqua » (2027-01-01) précède « depuis » (2027-02-01)");
        assertThatThrownBy(() -> Rattachements.saisie(JULIE, "OUEST", "2027-02-01", null, List.of(r("OUEST", "2027-01-01", null))))
                .isInstanceOf(ConflitException.class).hasMessage("« Julie Roy » est déjà rattachée à « OUEST » sur cette période");
        assertThatThrownBy(() -> Rattachements.saisie(JULIE, "OUEST", "2026-12-01", "2027-01-15", List.of(r("OUEST", "2027-01-01", "2027-01-31")))).isInstanceOf(ConflitException.class);
    }

    @Test
    @DisplayName("en vigueur du premier au dernier jour ; les autres unités du jour, par personne")
    void enVigueur() {
        assertThat(List.of(r("OUEST", "2027-01-01", "2027-01-31").enVigueurLe("2027-01-01"), r("OUEST", "2027-01-01", "2027-01-31").enVigueurLe("2027-01-31"),
                r("OUEST", "2027-01-01", "2027-01-31").enVigueurLe("2027-02-01"), r("OUEST", "2027-01-01", null).enVigueurLe("2030-01-01"),
                r("OUEST", "2027-01-01", null).enVigueurLe("2026-12-31"))).containsExactly(true, true, false, true, false);
        assertThat(Rattachements.unitesLe(List.of(r("OUEST", "2027-01-01", null), r("NORD", "2028-01-01", null),
                new Rattachements.Rattachement("r2", "SUD", "2026-01-01", "2026-12-31")), "2027-06-01")).isEqualTo(Map.of("r1", Set.of("OUEST")));
    }

    @Test
    @DisplayName("sans dépôt : aucune autre unité, aucun retrait, et l'ajout refusé")
    void aucun() {
        assertThat(RattachementsPort.AUCUN.tous("t")).isEmpty();
        assertThat(RattachementsPort.AUCUN.deLaRessource("t", "r1")).isEmpty();
        assertThat(RattachementsPort.AUCUN.retirer("t", "r1", "OUEST", "2027-01-01")).isFalse();
        assertThatThrownBy(() -> RattachementsPort.AUCUN.ajouter("t", r("OUEST", "2027-01-01", null), "a", "b")).isInstanceOf(UnsupportedOperationException.class);
    }
}
