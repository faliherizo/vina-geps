package ca.vina.nexu.effectifs.domaine;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static ca.vina.nexu.effectifs.domaine.Disponibilites.TypeDisponibilite.DISPONIBLE;
import static ca.vina.nexu.effectifs.domaine.Disponibilites.TypeDisponibilite.INDISPONIBLE;
import static ca.vina.nexu.effectifs.domaine.Disponibilites.TypeDisponibilite.PREFERENCE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * <strong>Domaine des disponibilités</strong> (lot R4, jumeau de {@code effectifs-disponibilites-domaine.test.ts}) :
 * un dépôt se résume en faits justes — la pire semaine entière, les fins de semaine, les nuits consécutives — et
 * une indisponibilité déclarée ne compte jamais comme offerte.
 */
@DisplayName("Disponibilités — domaine pur")
class DisponibilitesDomaineTest {

    private static final String D = "2030-03-04";
    private static final String F = "2030-03-17";

    private static Disponibilites.PlageDisponible p(String date, int debut, int fin, Disponibilites.TypeDisponibilite type) {
        return new Disponibilites.PlageDisponible(date, debut, fin, type);
    }

    private static Map<String, Object> brut(Object date, Object debut, Object fin, Object type) {
        Map<String, Object> m = new HashMap<>();
        m.put("date", date);
        m.put("debut", debut);
        m.put("fin", fin);
        m.put("type", type);
        return m;
    }

    @Test
    @DisplayName("trie et accepte des plages valides dans la période")
    void plagesValides() {
        assertThat(Disponibilites.plagesDe(List.of(brut("2030-03-05", 360, 1080, "PREFERENCE"), brut(D, 360, 1080, "DISPONIBLE")), D, F))
                .extracting(Disponibilites.PlageDisponible::date).containsExactly(D, "2030-03-05");
    }

    @Test
    @DisplayName("refuse une liste absente, trop longue, une date hors période, un type inconnu, un chevauchement")
    void plagesInvalides() {
        assertThatThrownBy(() -> Disponibilites.plagesDe("x", D, F)).hasMessageContaining("liste");
        assertThatThrownBy(() -> Disponibilites.plagesDe(Collections.nCopies(401, brut(D, 0, 60, "DISPONIBLE")), D, F)).hasMessage("un dépôt compte au plus 400 plages");
        assertThatThrownBy(() -> Disponibilites.plagesDe(List.of(brut("2030-04-01", 360, 1080, "DISPONIBLE")), D, F))
                .hasMessage("plage 1 : le 2030-04-01 est hors de la période (2030-03-04 au 2030-03-17)");
        assertThatThrownBy(() -> Disponibilites.plagesDe(List.of(brut("2030-03-01", 360, 1080, "DISPONIBLE")), D, F)).hasMessageContaining("hors de la période");
        assertThatThrownBy(() -> Disponibilites.plagesDe(List.of(brut(D, 360, 1080, "PEUT-ETRE")), D, F))
                .hasMessage("plage 1 : type inconnu « PEUT-ETRE » (attendu : DISPONIBLE, PREFERENCE, INDISPONIBLE)");
        assertThatThrownBy(() -> Disponibilites.plagesDe(List.of(brut(D, 360, 1080, null)), D, F)).hasMessageContaining("« undefined »");
        assertThatThrownBy(() -> Disponibilites.plagesDe(List.of(brut(D, 360, 1080, 2.0)), D, F)).hasMessageContaining("« 2 »");
        assertThatThrownBy(() -> Disponibilites.plagesDe(Arrays.asList((Object) null), D, F)).hasMessageContaining("date de la plage 1");
        assertThatThrownBy(() -> Disponibilites.plagesDe(List.of(brut(D, 1080, 360, "DISPONIBLE"), brut("2030-03-05", 300, 600, "DISPONIBLE")), D, F))
                .hasMessage("deux plages se chevauchent le 2030-03-05 : corrigez l'une des deux");
    }

    @Test
    @DisplayName("admet la journée entière, que le quart refuse ; délègue le reste à la règle du quart")
    void creneau() {
        assertThat(Disponibilites.creneauDePlage(0, 1440)).isEqualTo(new Planification.Plage(0, 1440));
        assertThat(Disponibilites.creneauDePlage("0", "1440")).isEqualTo(new Planification.Plage(0, 1440));
        assertThat(Disponibilites.creneauDePlage(" ", "1440")).isEqualTo(new Planification.Plage(0, 1440));
        assertThat(Disponibilites.creneauDePlage(false, 1440)).isEqualTo(new Planification.Plage(0, 1440));
        assertThat(Disponibilites.creneauDePlage(1080, 360)).isEqualTo(new Planification.Plage(1080, 360));
        assertThat(Disponibilites.creneauDePlage(true, 360)).isEqualTo(new Planification.Plage(1, 360));
        assertThatThrownBy(() -> Disponibilites.creneauDePlage(360, 360)).hasMessageContaining("zéro minute");
        assertThatThrownBy(() -> Disponibilites.creneauDePlage(0, 1441)).hasMessageContaining("fin");
        assertThatThrownBy(() -> Disponibilites.creneauDePlage(null, 1440)).hasMessageContaining("début");
        assertThatThrownBy(() -> Disponibilites.creneauDePlage("abc", 1440)).hasMessageContaining("début");
    }

    @Test
    @DisplayName("juge les semaines entières ; toutes celles touchées si la période est plus courte")
    void semaines() {
        assertThat(Disponibilites.semainesDe(D, F)).containsExactly(D, "2030-03-11");
        assertThat(Disponibilites.semainesDe("2030-03-06", F)).containsExactly("2030-03-11");
        assertThat(Disponibilites.semainesDe("2030-03-05", "2030-03-07")).containsExactly(D);
        assertThat(Disponibilites.semainesDe(D, "2030-03-13")).containsExactly(D);
        assertThat(Disponibilites.semainesDe(D, "2030-03-08")).containsExactly(D);
    }

    @Test
    @DisplayName("calcule la pire semaine, les heures, la plage la plus courte, fins de semaine et nuits consécutives")
    void faits() {
        List<Disponibilites.PlageDisponible> plages = List.of(p(D, 360, 1080, DISPONIBLE), p("2030-03-05", 360, 1080, DISPONIBLE),
                p("2030-03-06", 360, 1080, DISPONIBLE), p("2030-03-09", 1080, 360, PREFERENCE), p("2030-03-10", 1080, 360, DISPONIBLE),
                p("2030-03-11", 1200, 1380, DISPONIBLE), p("2030-03-12", 360, 1080, INDISPONIBLE), p("2030-03-25", 360, 1080, DISPONIBLE));
        Disponibilites.Faits f = Disponibilites.faitsDuDepot(plages, D, F);
        assertThat(f).isEqualTo(new Disponibilites.Faits(1, 75, 180, 1, 3));
        assertThat(f.enCarte()).containsExactly(Map.entry("quartsParSemaine", 1), Map.entry("heuresParPeriode", 75.0), Map.entry("dureeMinutes", 180),
                Map.entry("finsDeSemaine", 1), Map.entry("nuitsConsecutives", 3));
    }

    @Test
    @DisplayName("un dépôt vide vaut zéro partout ; une nuit isolée compte pour une")
    void faitsVides() {
        assertThat(Disponibilites.faitsDuDepot(List.of(), D, "2030-03-10")).isEqualTo(new Disponibilites.Faits(0, 0, 0, 0, 0));
        assertThat(Disponibilites.faitsDuDepot(List.of(p(D, 1080, 1320, DISPONIBLE), p("2030-03-06", 1080, 1320, DISPONIBLE)), D, "2030-03-10").nuitsConsecutives())
                .isEqualTo(1);
        assertThat(List.of(Disponibilites.estNuit(1080, 1320), Disponibilites.estNuit(1200, 360), Disponibilites.estNuit(360, 1080))).containsExactly(true, true, false);
    }

    @Test
    @DisplayName("traduit l'action des règles en conformité enregistrée")
    void conformite() {
        assertThat(Arrays.stream(ActionRegle.values()).map(Disponibilites::conformiteDe).toList()).containsExactly(Disponibilites.Conformite.CONFORME,
                Disponibilites.Conformite.AVERTIE, Disponibilites.Conformite.APPROBATION_REQUISE, Disponibilites.Conformite.CONFORME,
                Disponibilites.Conformite.CONFORME, Disponibilites.Conformite.CONFORME);
    }

    @Test
    @DisplayName("n'accepte un dépôt que pendant la collecte et jusqu'à sa fin incluse")
    void fenetre() {
        Planification.EtatPeriode collecte = Planification.EtatPeriode.COLLECTE;
        assertThatCode(() -> Disponibilites.verifierFenetre(collecte, "2030-02-25", "2030-02-25")).doesNotThrowAnyException();
        assertThatCode(() -> Disponibilites.verifierFenetre(collecte, null, "2030-02-25")).doesNotThrowAnyException();
        assertThatThrownBy(() -> Disponibilites.verifierFenetre(collecte, "2030-02-25", "2030-02-26")).hasMessage("la collecte s'est terminée le 2030-02-25");
        assertThatThrownBy(() -> Disponibilites.verifierFenetre(Planification.EtatPeriode.CONFECTION, "2030-02-25", "2030-02-20"))
                .hasMessageContaining("n'est pas ouverte");
    }

    @Test
    @DisplayName("une plage offerte couvre un quart entier ; une indisponibilité le touche")
    void couverture() {
        List<Disponibilites.PlageDisponible> plages = List.of(p("2030-03-05", 360, 720, INDISPONIBLE), p(D, 300, 1140, PREFERENCE));
        assertThat(Disponibilites.couvre(plages, new Planification.Creneau(D, 360, 1080))).map(Disponibilites.PlageDisponible::type).contains(PREFERENCE);
        assertThat(Disponibilites.couvre(plages, new Planification.Creneau(D, 360, 1200))).isEmpty();
        assertThat(Disponibilites.couvre(plages, new Planification.Creneau(D, 200, 600))).isEmpty();
        assertThat(Disponibilites.couvre(plages, new Planification.Creneau("2030-03-05", 360, 600))).isEmpty();
        assertThat(Disponibilites.indisponible(plages, new Planification.Creneau("2030-03-05", 600, 900))).isTrue();
        assertThat(Disponibilites.indisponible(plages, new Planification.Creneau(D, 600, 900))).isFalse();
    }

    @Test
    @DisplayName("ordre de fin de collecte : préférence, puis ancienneté, puis moins d'heures, puis identifiant")
    void classement() {
        List<Disponibilites.Candidature> c = new ArrayList<>(List.of(cand("a", false, 10, 0), cand("b", true, 1, 40), cand("c", false, 10, 12),
                cand("d", false, 10, 0), cand("e", false, 20, 50)));
        assertThat(Disponibilites.classer(c)).extracting(Disponibilites.Candidature::ressourceId).containsExactly("b", "e", "a", "d", "c");
    }

    private static Disponibilites.Candidature cand(String id, boolean pref, double anc, double heures) {
        return new Disponibilites.Candidature(id, id.toUpperCase(), pref, anc, heures);
    }

    @Test
    @DisplayName("un dépôt dit s'il attend une approbation")
    void attente() {
        DisponibilitesDeposees d = new DisponibilitesDeposees("p", "r", Disponibilites.Conformite.APPROBATION_REQUISE, List.of(), "x", "u", null, null, List.of());
        assertThat(d.attendApprobation()).isTrue();
        assertThat(new DisponibilitesDeposees("p", "r", Disponibilites.Conformite.AVERTIE, List.of(), "x", "u", null, null, List.of()).attendApprobation()).isFalse();
    }

    @Test
    @DisplayName("la fin de collecte part au lendemain de la date de fin, à minuit UTC (P-GH-4)")
    void echeanceFinDeCollecte() {
        assertThat(List.of(Disponibilites.echeanceFinDeCollecte("2030-02-28"), Disponibilites.echeanceFinDeCollecte("2030-12-31")))
                .containsExactly("2030-03-01T00:00:00.000Z", "2031-01-01T00:00:00.000Z");
        assertThat(Disponibilites.TACHE_FIN_DE_COLLECTE).isEqualTo("effectifs_fin_collecte");
    }
}
