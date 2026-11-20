package ca.vina.nexu.effectifs.domaine;

import ca.vina.nexu.effectifs.domaine.EchangeEffectifs.Geste;
import ca.vina.nexu.effectifs.domaine.EchangeEffectifs.Statut;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * <strong>Domaine des échanges de quarts</strong> (AO 26-015-DO, lot R6) — jumeau de
 * {@code effectifs-echanges-domaine.test.ts}. Ce qui compte : un échange se juge dans les deux sens, chaque motif
 * nomme la personne concernée, et l'issue d'une acceptation dépend du verdict.
 */
@DisplayName("Échanges de quarts — domaine pur")
class EchangeEffectifsDomaineTest {

    private static Regles.Verdict verdict(ActionRegle action, String message) {
        List<Regles.Motif> motifs = message == null ? List.of() : List.of(new Regles.Motif("r", 1, "k", action, message));
        return new Regles.Verdict(action, motifs, List.of());
    }

    @Nested
    @DisplayName("Transitions")
    class Transitions {

        @Test
        @DisplayName("refuser, valider, rejeter, annuler mènent où il faut")
        void menent() {
            assertThat(List.of(EchangeEffectifs.transition(Statut.PROPOSE, Geste.REFUSER), EchangeEffectifs.transition(Statut.A_VALIDER, Geste.VALIDER),
                    EchangeEffectifs.transition(Statut.A_VALIDER, Geste.REJETER), EchangeEffectifs.transition(Statut.PROPOSE, Geste.ANNULER),
                    EchangeEffectifs.transition(Statut.A_VALIDER, Geste.ANNULER), EchangeEffectifs.transition(Statut.PROPOSE, Geste.ACCEPTER)))
                    .containsExactly(Statut.REFUSE, Statut.APPLIQUE, Statut.REJETE, Statut.ANNULE, Statut.ANNULE, Statut.APPLIQUE);
        }

        @Test
        @DisplayName("refuse le reste en nommant l'état")
        void refuse() {
            assertThatThrownBy(() -> EchangeEffectifs.transition(Statut.APPLIQUE, Geste.ANNULER)).isInstanceOf(EffectifsInvalideException.class)
                    .hasMessage("un échange appliqué ne se prête plus à ce geste (annuler)");
            assertThatThrownBy(() -> EchangeEffectifs.transition(Statut.PROPOSE, Geste.VALIDER)).hasMessageContaining("proposé");
            assertThat(Statut.values()).hasSize(6);
            assertThat(Arrays.stream(Statut.values()).map(Statut::libelle)).containsExactly("proposé", "à valider", "appliqué", "refusé", "rejeté", "annulé");
            assertThat(Arrays.stream(Statut.values()).filter(Statut::estOuvert)).containsExactly(Statut.PROPOSE, Statut.A_VALIDER);
        }

        @Test
        @DisplayName("une acceptation applique, ou attend la validation si les règles l'exigent")
        void acceptation() {
            assertThat(List.of(EchangeEffectifs.issueDeLAcceptation(Statut.PROPOSE, ActionRegle.ACCEPTER),
                    EchangeEffectifs.issueDeLAcceptation(Statut.PROPOSE, ActionRegle.AVERTIR),
                    EchangeEffectifs.issueDeLAcceptation(Statut.PROPOSE, ActionRegle.EXIGER_APPROBATION)))
                    .containsExactly(Statut.APPLIQUE, Statut.APPLIQUE, Statut.A_VALIDER);
            assertThatThrownBy(() -> EchangeEffectifs.issueDeLAcceptation(Statut.REFUSE, ActionRegle.ACCEPTER)).hasMessage("un échange refusé ne s'accepte plus");
        }

        @Test
        @DisplayName("lit un geste à la frontière ; accepter et valider rejugent")
        void gestes() {
            assertThat(Arrays.stream(Geste.values()).map(g -> Geste.de(g.name()))).containsExactly(Geste.values());
            assertThatThrownBy(() -> Geste.de("FORCER")).hasMessage("« geste » : ACCEPTER, REFUSER, VALIDER, REJETER, ANNULER");
            assertThatThrownBy(() -> Geste.de(3)).hasMessageContaining("geste");
            assertThat(Arrays.stream(Geste.values()).filter(Geste::rejuge)).containsExactly(Geste.ACCEPTER, Geste.VALIDER);
            assertThat(Geste.REJETER.minuscule()).isEqualTo("rejeter");
        }
    }

    @Nested
    @DisplayName("Faits d'un sens (E-GR-7)")
    class FaitsDUnSens {

        private final List<Planification.Creneau> horaire = List.of(new Planification.Creneau("2030-03-04", 360, 1080));

        @Test
        @DisplayName("repos, semaine, contiguïté et période")
        void faits() {
            assertThat(EchangeEffectifs.faitsDUnSens(horaire, new Planification.Creneau("2030-03-05", 360, 1080), true))
                    .isEqualTo(new EchangeEffectifs.Faits(12, 24, false, true, 12));
            assertThat(EchangeEffectifs.faitsDUnSens(horaire, new Planification.Creneau("2030-03-04", 1080, 1320), false))
                    .isEqualTo(new EchangeEffectifs.Faits(0, 16, true, false, 16));
            EchangeEffectifs.Faits seul = EchangeEffectifs.faitsDUnSens(List.of(), new Planification.Creneau("2030-03-04", 360, 1080), true);
            assertThat(seul).isEqualTo(new EchangeEffectifs.Faits(999, 12, false, true, 12));
            assertThat(seul.enCarte()).containsExactly(Map.entry("reposHeures", 999.0), Map.entry("heuresSemaine", 12.0), Map.entry("contigu", false),
                    Map.entry("memePeriode", true), Map.entry("heures24h", 12.0));
        }

        @Test
        @DisplayName("un quart qui chevaucherait l'horaire rend l'échange impossible")
        void chevauchement() {
            assertThatThrownBy(() -> EchangeEffectifs.faitsDUnSens(horaire, new Planification.Creneau("2030-03-04", 600, 900), true))
                    .isInstanceOf(EffectifsInvalideException.class).hasMessage("le quart du 2030-03-04 chevaucherait un autre quart de la même personne");
        }
    }

    @Nested
    @DisplayName("Verdict combiné des deux sens")
    class Combine {

        @Test
        @DisplayName("garde l'action la plus stricte et nomme la personne de chaque motif")
        void combiner() {
            EchangeEffectifs.VerdictCombine c = EchangeEffectifs.combiner(List.of(new EchangeEffectifs.Sens("Marc", verdict(ActionRegle.AVERTIR, "repos de 8 h")),
                    new EchangeEffectifs.Sens("Julie", verdict(ActionRegle.EXIGER_APPROBATION, "entre périodes"))));
            assertThat(c.action()).isEqualTo(ActionRegle.EXIGER_APPROBATION);
            assertThat(c.motifs()).extracting(Regles.Motif::message).containsExactly("Pour Marc : repos de 8 h", "Pour Julie : entre périodes");
            assertThat(c.message()).isEqualTo("Pour Marc : repos de 8 h ; Pour Julie : entre périodes");
            assertThat(c.refuse()).isFalse();
            assertThat(c.enVerdict().motifs()).isEqualTo(c.motifs());
            assertThat(c.enVerdict().versions()).isEmpty();
            EchangeEffectifs.VerdictCombine b = EchangeEffectifs.combiner(List.of(new EchangeEffectifs.Sens("A", verdict(ActionRegle.BLOQUER, null)),
                    new EchangeEffectifs.Sens("B", verdict(ActionRegle.ACCEPTER, null))));
            assertThat(b.action()).isEqualTo(ActionRegle.BLOQUER);
            assertThat(b.refuse()).isTrue();
        }

        @Test
        @DisplayName("dit ce qui refuse")
        void refuse() {
            assertThat(List.of(EchangeEffectifs.refuse(ActionRegle.ACCEPTER), EchangeEffectifs.refuse(ActionRegle.EXIGER_APPROBATION),
                    EchangeEffectifs.refuse(ActionRegle.BLOQUER))).containsExactly(false, false, true);
        }
    }

    @Test
    @DisplayName("le motif d'une décision : facultatif, rogné, borné à 500 caractères")
    void motifDecision() {
        assertThat(EchangeEffectifs.motifDecision(null)).isNull();
        assertThat(EchangeEffectifs.motifDecision(12)).isNull();
        assertThat(EchangeEffectifs.motifDecision("   ")).isNull();
        assertThat(EchangeEffectifs.motifDecision("  Pas disponible ")).isEqualTo("Pas disponible");
        assertThat(EchangeEffectifs.motifDecision("x".repeat(600))).hasSize(EchangeEffectifs.MOTIF_MAX);
    }

    @Test
    @DisplayName("une place tenue ou vacante, et son créneau")
    void place() {
        EchangeEffectifs.PlaceDetaillee p = new EchangeEffectifs.PlaceDetaillee("pl", "r1", "PARA", "2030-03-04", 360, 1080, "p1",
                Planification.EtatPeriode.PUBLIEE, "EST");
        assertThat(p.estTenue()).isTrue();
        assertThat(p.creneau()).isEqualTo(new Planification.Creneau("2030-03-04", 360, 1080));
        assertThat(new EchangeEffectifs.PlaceDetaillee("pl", null, "PARA", "2030-03-04", 360, 1080, "p1", Planification.EtatPeriode.PUBLIEE, "EST").estTenue())
                .isFalse();
    }

    @Test
    @DisplayName("un échange se voit des deux personnes et de l'encadrement de l'une des deux unités")
    void visibilite() {
        EchangeEffectifs.Quart q = new EchangeEffectifs.Quart("2030-03-04", 360, 1080);
        EchangeEffectifs.Fiche x = new EchangeEffectifs.Fiche("x", "pa", "pb", "ra", "rb", "EST", "OUEST", Statut.PROPOSE, ActionRegle.ACCEPTER, List.of(),
                "t", "u", null, null, null, "A", "B", q, q);
        assertThat(List.of(x.estVisiblePar(Set.of("ra"), Set.of()), x.estVisiblePar(Set.of("rb"), Set.of()), x.estVisiblePar(Set.of(), Set.of("EST")),
                x.estVisiblePar(Set.of(), Set.of("OUEST")), x.estVisiblePar(Set.of("rc"), Set.of("NORD")))).containsExactly(true, true, true, true, false);
    }
}
