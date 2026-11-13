package ca.vina.nexu.effectifs.domaine;

import ca.vina.nexu.effectifs.domaine.AbsencesEffectifs.Decision;
import ca.vina.nexu.effectifs.domaine.AbsencesEffectifs.Statut;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * <strong>Domaine des absences d'effectifs</strong> (lot R5, jumeau de {@code effectifs-absences-domaine.test.ts}) :
 * un refus des règles est un <strong>état</strong> (REFUSEE_AUTO), pas une exception — c'est lui que la dérogation
 * renverse ; les transitions interdites disent pourquoi ; le taux « après » se calcule.
 */
@DisplayName("Absences d'effectifs — domaine pur")
class AbsencesEffectifsDomaineTest {

    private static AbsenceEffectifs absence(String du, String au) {
        return new AbsenceEffectifs("a", "r", null, "EST", "MAL", du, au, null, Statut.ACCEPTEE, ActionRegle.ACCEPTER, List.of(),
                new AbsenceEffectifs.Trace("t", "u", null, null, null, null, null));
    }

    @Test
    @DisplayName("accepter et avertir accordent d'office, exiger une approbation met en attente, le reste refuse — et le refus reste un état (P-GN-3)")
    void decisionAutomatique() {
        assertThat(Arrays.stream(ActionRegle.values()).map(AbsencesEffectifs::statutDepuisAction).toList())
                .containsExactly(Statut.ACCEPTEE, Statut.ACCEPTEE, Statut.A_APPROUVER, Statut.REFUSEE_AUTO, Statut.REFUSEE_AUTO, Statut.REFUSEE_AUTO);
        assertThat(Arrays.stream(Statut.values()).filter(Statut::estActive).toList()).containsExactly(Statut.ACCEPTEE, Statut.A_APPROUVER);
        assertThat(Arrays.stream(Statut.values()).filter(s -> !s.estDecidee()).toList()).containsExactly(Statut.A_APPROUVER);
        assertThat(Statut.de("REFUSEE")).isEqualTo(Statut.REFUSEE);
        assertThatThrownBy(() -> Statut.de("X")).isInstanceOf(EffectifsInvalideException.class);
    }

    @Test
    @DisplayName("approuver, refuser, déroger et annuler mènent où il faut ; le reste est refusé en nommant l'état et le geste")
    void transitions() {
        assertThat(List.of(AbsencesEffectifs.transition(Statut.A_APPROUVER, Decision.APPROUVER), AbsencesEffectifs.transition(Statut.A_APPROUVER, Decision.REFUSER),
                AbsencesEffectifs.transition(Statut.REFUSEE_AUTO, Decision.DEROGER), AbsencesEffectifs.transition(Statut.ACCEPTEE, Decision.ANNULER),
                AbsencesEffectifs.transition(Statut.A_APPROUVER, Decision.ANNULER)))
                .containsExactly(Statut.ACCEPTEE, Statut.REFUSEE, Statut.ACCEPTEE, Statut.ANNULEE, Statut.ANNULEE);
        assertThatThrownBy(() -> AbsencesEffectifs.transition(Statut.ACCEPTEE, Decision.DEROGER))
                .hasMessage("une absence acceptée ne peut pas être accordée par dérogation");
        assertThatThrownBy(() -> AbsencesEffectifs.transition(Statut.REFUSEE_AUTO, Decision.APPROUVER))
                .hasMessage("une absence refusée par les règles ne peut pas être approuvée");
        assertThatThrownBy(() -> AbsencesEffectifs.transition(Statut.ANNULEE, Decision.ANNULER)).hasMessage("une absence annulée ne peut pas être annulée");
        assertThatThrownBy(() -> AbsencesEffectifs.transition(Statut.REFUSEE, Decision.REFUSER)).hasMessage("une absence refusée ne peut pas être refusée");
        assertThat(Decision.values()).hasSize(4);
    }

    @Test
    @DisplayName("la décision lue à la frontière : trois codes admis, l'annulation et l'inconnu refusés en les nommant")
    void decisionHumaine() {
        assertThat(Decision.humaineDe("DEROGER")).isEqualTo(Decision.DEROGER);
        assertThatThrownBy(() -> Decision.humaineDe("ANNULER"))
                .hasMessage("« decision » : APPROUVER, REFUSER, DEROGER (l'annulation a sa propre route)");
        assertThatThrownBy(() -> Decision.humaineDe(null)).isInstanceOf(EffectifsInvalideException.class);
    }

    @Test
    @DisplayName("un code sans titres admet tout le monde ; sinon il faut le titre")
    void codeAdmissible() {
        assertThat(List.of(AbsencesEffectifs.codeAdmissible(List.of(), null), AbsencesEffectifs.codeAdmissible(List.of("PSA"), "PSA"),
                AbsencesEffectifs.codeAdmissible(List.of("PSA"), "TUA"), AbsencesEffectifs.codeAdmissible(List.of("PSA"), null)))
                .containsExactly(true, true, false, false);
    }

    @Test
    @DisplayName("compte les jours, le préavis, et borne une absence à un an")
    void dates() {
        assertThat(List.of(AbsencesEffectifs.joursDemandes("2030-03-04", "2030-03-04"), AbsencesEffectifs.joursDemandes("2030-02-27", "2030-03-02")))
                .containsExactly(1, 4);
        assertThat(List.of(AbsencesEffectifs.joursPreavis("2030-03-01", "2030-03-04"), AbsencesEffectifs.joursPreavis("2030-03-05", "2030-03-04")))
                .containsExactly(3, -1);
        assertThatCode(() -> AbsencesEffectifs.bornesValides("2030-03-04", "2030-03-10")).doesNotThrowAnyException();
        assertThatThrownBy(() -> AbsencesEffectifs.bornesValides("2030-03-04", "2030-03-03")).hasMessage("la fin (2030-03-03) précède le début (2030-03-04)");
        assertThatThrownBy(() -> AbsencesEffectifs.bornesValides("2030-01-01", "2031-01-02")).hasMessageContaining("remplacement long terme");
    }

    @Test
    @DisplayName("deux absences se chevauchent dès qu'elles partagent un jour")
    void chevauchement() {
        assertThat(AbsencesEffectifs.chevauche(absence("2030-03-04", "2030-03-06"), "2030-03-06", "2030-03-08")).isTrue();
        assertThat(AbsencesEffectifs.chevauche(absence("2030-03-04", "2030-03-05"), "2030-03-06", "2030-03-08")).isFalse();
        assertThat(AbsencesEffectifs.chevauche(absence("2030-03-09", "2030-03-10"), "2030-03-06", "2030-03-08")).isFalse();
    }

    @Test
    @DisplayName("calcule le taux après l'absence ; une unité sans place est comblée par définition ; l'impact les réunit")
    void taux() {
        assertThat(List.of(AbsencesEffectifs.tauxApres(8, 8, 2), AbsencesEffectifs.tauxApres(3, 2, 1), AbsencesEffectifs.tauxApres(0, 0, 0)))
                .containsExactly(75.0, 33.3, 100.0);
        AbsencesEffectifs.Impact i = AbsencesEffectifs.Impact.de(List.of(new AbsencesEffectifs.PlaceTenue("p", "2030-03-04", 360, 1080)),
                new AbsencesEffectifs.Comblement(2, 2));
        assertThat(List.of(i.places().size(), i.tauxAvant(), i.tauxApres())).containsExactly(1, 100.0, 50.0);
    }

    @Test
    @DisplayName("une dérogation exige un motif de 10 caractères et un code (E-GN-5) ; un motif libre est élagué et borné")
    void derogation() {
        assertThat(AbsencesEffectifs.derogationDe(Map.of("motif", "  Décès dans la famille ", "codeAbsence", " DEUIL ")))
                .isEqualTo(new AbsencesEffectifs.Derogation("Décès dans la famille", "DEUIL"));
        assertThatThrownBy(() -> AbsencesEffectifs.derogationDe(Map.of("motif", "court", "codeAbsence", "DEUIL"))).hasMessageContaining("10 caractères");
        assertThatThrownBy(() -> AbsencesEffectifs.derogationDe(Map.of("codeAbsence", "DEUIL"))).hasMessageContaining("10 caractères");
        assertThatThrownBy(() -> AbsencesEffectifs.derogationDe(Map.of("motif", "Motif suffisant ici"))).hasMessageContaining("code d'absence");
        assertThatThrownBy(() -> AbsencesEffectifs.derogationDe(Map.of("motif", "Motif suffisant ici", "codeAbsence", 4))).hasMessageContaining("code d'absence");
        assertThat(AbsencesEffectifs.motifLibre(null)).isNull();
        assertThat(AbsencesEffectifs.motifLibre("   ")).isNull();
        assertThat(AbsencesEffectifs.motifLibre(" Grippe ")).isEqualTo("Grippe");
        assertThat(AbsencesEffectifs.motifLibre("x".repeat(600))).hasSize(AbsencesEffectifs.MOTIF_MAX);
    }

    @Test
    @DisplayName("U5 — planifiée = déclarée avant le premier jour ; seul un « planifiable : faux » ferme la déclaration d'avance, en disant quoi faire")
    void absencesPlanifiees() {
        assertThat(List.of(AbsencesEffectifs.estPlanifiee(1), AbsencesEffectifs.estPlanifiee(0), AbsencesEffectifs.estPlanifiee(-2))).containsExactly(true, false, false);
        assertThat(List.of(AbsencesEffectifs.codePlanifiable(false), AbsencesEffectifs.codePlanifiable(true), AbsencesEffectifs.codePlanifiable("non")))
                .containsExactly(false, true, true);
        assertThat(AbsencesEffectifs.codePlanifiable(null)).isTrue();
        assertThatThrownBy(() -> AbsencesEffectifs.verifierPlanifiable("DEC", false, 3, "2030-04-01")).isInstanceOf(EffectifsInvalideException.class)
                .hasMessage("le code « DEC » ne se planifie pas d'avance : il se déclare à partir du premier jour d'absence (2030-04-01, dans 3 jours). "
                        + "Choisissez un code planifiable, ou déclarez-la le jour venu");
        assertThatThrownBy(() -> AbsencesEffectifs.verifierPlanifiable("DEC", false, 1, "2030-04-01")).hasMessageContaining("(2030-04-01, dans 1 jour)");
        assertThatCode(() -> AbsencesEffectifs.verifierPlanifiable("DEC", false, 0, "2030-04-01")).doesNotThrowAnyException();
        assertThatCode(() -> AbsencesEffectifs.verifierPlanifiable("MAL", true, 30, "2030-04-01")).doesNotThrowAnyException();
        assertThat(List.of(AbsencesEffectifs.estDureeLongue("MOYEN_TERME"), AbsencesEffectifs.estDureeLongue("LONG_TERME"), AbsencesEffectifs.estDureeLongue("PONCTUELLE")))
                .containsExactly(true, true, false);
        assertThat(AbsencesEffectifs.estDureeLongue(null)).isFalse();
    }

    @Test
    @DisplayName("U5 — une absence longue réserve la place ; sinon le mode du quart choisit ; le bilan compte chaque résultat")
    void comblement() {
        assertThat(List.of(AbsencesEffectifs.voieDeComblement(true, Planification.ModeAttribution.AUTOMATISE), AbsencesEffectifs.voieDeComblement(true, Planification.ModeAttribution.MANUEL),
                AbsencesEffectifs.voieDeComblement(false, Planification.ModeAttribution.AUTOMATISE), AbsencesEffectifs.voieDeComblement(false, Planification.ModeAttribution.MANUEL)))
                .containsExactly(AbsencesEffectifs.VoieComblement.RESERVER, AbsencesEffectifs.VoieComblement.RESERVER, AbsencesEffectifs.VoieComblement.ATTRIBUER,
                        AbsencesEffectifs.VoieComblement.OFFRIR);
        List<AbsencesEffectifs.ResultatComblement> tous = new ArrayList<>(Arrays.asList(AbsencesEffectifs.ResultatComblement.values()));
        tous.add(AbsencesEffectifs.ResultatComblement.ATTRIBUEE);
        AbsencesEffectifs.BilanComblement b = AbsencesEffectifs.BilanComblement.de(tous);
        assertThat(b).isEqualTo(new AbsencesEffectifs.BilanComblement(2, 1, 1, 1));
        assertThat(b.vue()).containsExactly(Map.entry("attribuees", 2), Map.entry("offertes", 1), Map.entry("sansCandidat", 1), Map.entry("reservees", 1));
    }
}
