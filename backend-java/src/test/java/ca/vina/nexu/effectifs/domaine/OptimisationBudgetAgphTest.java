package ca.vina.nexu.effectifs.domaine;

import ca.vina.nexu.effectifs.domaine.AgphSemaineType.PlageType;
import ca.vina.nexu.effectifs.domaine.OptimisationBudgetAgph.MotifEcart;
import ca.vina.nexu.effectifs.domaine.OptimisationBudgetAgph.PosteAEvaluer;
import ca.vina.nexu.effectifs.domaine.OptimisationBudgetAgph.PosteJuge;
import ca.vina.nexu.effectifs.domaine.OptimisationBudgetAgph.Proposition;
import ca.vina.nexu.effectifs.domaine.OptimisationBudgetAgph.QuartDuPoste;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

@DisplayName("OptimisationBudgetAgph — la proposition d'optimisation du budget d'heures (E-CH-1 ; addenda 7, Q11)")
class OptimisationBudgetAgphTest {

    private static final Disponibilites.TypeDisponibilite DISPO = Disponibilites.TypeDisponibilite.values()[0];

    /** Un poste de cinq jours (lundi à vendredi) sur la plage donnée, cycle d'une semaine. */
    private static PosteAEvaluer poste(String code, int debut, int fin) {
        List<QuartDuPoste> q = new ArrayList<>();
        for (int j = 1; j <= 5; j++) {
            q.add(new QuartDuPoste(j, debut, fin));
        }
        return new PosteAEvaluer(code, code, "Poste " + code, 1, q);
    }

    /** Une semaine type : la même plage du lundi au vendredi. */
    private static List<PlageType> semaine(int debut, int fin) {
        List<PlageType> s = new ArrayList<>();
        for (int j = 1; j <= 5; j++) {
            s.add(new PlageType(j, debut, fin, DISPO));
        }
        return s;
    }

    private static List<String> codes(List<PosteJuge> postes) {
        return postes.stream().map(PosteJuge::code).toList();
    }

    @Test
    @DisplayName("une semaine type couvre un poste si chaque quart tient dans une plage du même jour, nuit comprise")
    void couverture() {
        PosteAEvaluer jour = poste("J", 360, 1080);
        assertThat(OptimisationBudgetAgph.couvre(semaine(300, 1140), jour)).isTrue();
        assertThat(OptimisationBudgetAgph.couvre(semaine(420, 1140), jour)).isFalse();
        // Le vendredi manque : un seul quart non couvert suffit à écarter.
        assertThat(OptimisationBudgetAgph.couvre(semaine(300, 1140).subList(0, 4), jour)).isFalse();
        // La nuit qui déborde au lendemain se compare sur son intervalle réel.
        PosteAEvaluer nuit = poste("N", 1080, 420);
        assertThat(OptimisationBudgetAgph.couvre(semaine(1020, 480), nuit)).isTrue();
        assertThat(OptimisationBudgetAgph.couvre(semaine(1080, 360), nuit)).isFalse();
        assertThat(OptimisationBudgetAgph.couvre(semaine(0, 1440), new PosteAEvaluer("V", "V", "Vide", 1, List.of()))).isFalse();
    }

    @Test
    @DisplayName("les heures d'un poste sont la moyenne hebdomadaire de son cycle")
    void heures() {
        assertThat(poste("J", 360, 1080).heuresSemaine()).isEqualTo(60.0);
        Planification.Plage jour = new Planification.Plage(480, 945);
        List<QuartDuPoste> quarts = OptimisationBudgetAgph.quartsDuCycle(
                List.of(Arrays.asList("J", "J", "J", "J", "J", null, null), Arrays.asList("J", "J", "J", "J", null, null, null)), code -> jour);
        assertThat(quarts).hasSize(9).startsWith(new QuartDuPoste(1, 480, 945));
        // 9 quarts de 7,75 h sur deux semaines : 34,88 h par semaine.
        assertThat(new PosteAEvaluer("A", "A", "A", 2, quarts).heuresSemaine()).isEqualTo(34.88);
        assertThat(new PosteAEvaluer("Z", "Z", "Z", 0, List.of(new QuartDuPoste(1, 0, 60))).heuresSemaine()).isEqualTo(1.0);
    }

    @Test
    @DisplayName("la proposition maximise les heures sous le budget, là où un glouton se tromperait")
    void optimum() {
        // Postes de 50, 40 et 40 h ; budget 80 h. Le glouton prend 50 h et s'arrête ; l'optimum prend 40 + 40.
        PosteAEvaluer a = poste("A", 0, 600);
        PosteAEvaluer b = poste("B", 600, 1080);
        PosteAEvaluer c = poste("C", 960, 1440);
        List<List<PlageType>> depots = List.of(semaine(0, 1440));
        Proposition p = OptimisationBudgetAgph.proposer(List.of(a, b, c), depots, 80);
        assertThat(codes(p.retenus())).containsExactlyInAnyOrder("B", "C");
        assertThat(List.of(p.totalHeures(), p.resteHeures(), p.budgetHeures())).containsExactly(80.0, 0.0, 80.0);
        assertThat(p.ecartes()).singleElement().satisfies(j -> {
            assertThat(j.code()).isEqualTo("A");
            assertThat(j.motif()).isEqualTo(MotifEcart.HORS_BUDGET);
        });
    }

    @Test
    @DisplayName("un poste que rien ne couvre est écarté avec son motif ; à heures égales, le plus de candidats l'emporte")
    void motifsEtDepartage() {
        PosteAEvaluer jour = poste("J", 360, 840);
        PosteAEvaluer jour2 = poste("K", 360, 840);
        PosteAEvaluer nuit = poste("N", 1200, 360);
        // Deux semaines types couvrent K, une seule couvre J ; aucune ne couvre la nuit.
        List<List<PlageType>> depots = List.of(semaine(360, 840), semaine(300, 900));
        PosteAEvaluer jEtroit = poste("J", 300, 900);
        Proposition p = OptimisationBudgetAgph.proposer(List.of(jEtroit, jour2, nuit), depots, 40);
        assertThat(codes(p.retenus())).containsExactly("K");
        assertThat(p.ecartes()).extracting(PosteJuge::code, PosteJuge::motif)
                .containsExactly(tuple("N", MotifEcart.AUCUNE_DISPONIBILITE), tuple("J", MotifEcart.HORS_BUDGET));
        assertThat(p.retenus().getFirst().candidats()).isEqualTo(2);
        assertThat(MotifEcart.AUCUNE_DISPONIBILITE.libelle()).isEqualTo("aucune semaine type déposée ne couvre tous ses quarts");
        assertThat(MotifEcart.HORS_BUDGET.libelle()).isEqualTo("il ferait dépasser le budget d'heures");
        assertThat(jour.heuresSemaine()).isEqualTo(40.0);
    }

    @Test
    @DisplayName("un budget nul ou négatif ne retient rien, sans erreur")
    void budgetNul() {
        Proposition p = OptimisationBudgetAgph.proposer(List.of(poste("J", 360, 840)), List.of(semaine(0, 1440)), -5);
        assertThat(p.retenus()).isEmpty();
        assertThat(p.ecartes()).extracting(PosteJuge::motif).containsExactly(MotifEcart.HORS_BUDGET);
        assertThat(p.totalHeures()).isZero();
        assertThat(p.resteHeures()).isEqualTo(-5.0);
    }
}
