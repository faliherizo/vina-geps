package ca.vina.nexu.ordonnanceur.domaine;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Ordonnanceur — échue, verrou périmé, issue d'une tentative")
class EcheancesTest {

    private static TacheDifferee tache(String statut, String echeance, int tentatives, String verrouLe) {
        return new TacheDifferee("t", "t-1", "avancer_recherche", "r-1", null, echeance, statut, tentatives, null, verrouLe, null);
    }

    private static final PolitiqueRetentative P = PolitiqueRetentative.PAR_DEFAUT;

    @Test
    void echueEtVerrou() {
        String m = "2026-01-01T10:00:00.000Z";
        assertThat(Echeances.estEchue(tache("en_attente", "2026-01-01T10:00:00.000Z", 0, null), m)).isTrue();
        assertThat(Echeances.estEchue(tache("en_attente", "2026-01-01T10:00:01.000Z", 0, null), m)).isFalse();
        assertThat(Echeances.estEchue(tache("faite", "2026-01-01T09:00:00.000Z", 0, null), m)).isFalse();
        assertThat(Echeances.verrouPerime(tache("en_cours", "x", 0, "2026-01-01T09:49:00.000Z"), m, P)).isTrue();
        assertThat(Echeances.verrouPerime(tache("en_cours", "x", 0, "2026-01-01T09:51:00.000Z"), m, P)).isFalse();
        assertThat(Echeances.verrouPerime(tache("en_cours", "x", 0, null), m, P)).isFalse();
        assertThat(Echeances.verrouPerime(tache("en_attente", "x", 0, "2026-01-01T08:00:00.000Z"), m, P)).isFalse();
        var t1 = tache("en_attente", "2026-01-01T09:30:00.000Z", 0, null);
        var t2 = tache("en_cours", "2026-01-01T09:00:00.000Z", 0, "2026-01-01T09:00:00.000Z");
        var t3 = tache("en_attente", "2026-01-01T11:00:00.000Z", 0, null);
        assertThat(Echeances.aPrendre(List.of(t1, t3, t2), m, P)).containsExactly(t2, t1);
        assertThat(Echeances.cleDeDedoublonnage(t1)).isEqualTo("t-1|avancer_recherche|r-1");
        assertThat(new TacheDifferee("t", "t-1", "x", "c", Map.of("a", 1), "e", "en_attente", 0, null, null, null).charge()).containsEntry("a", 1);
    }

    @Test
    void issueDUneTentative() {
        String m = "2026-01-01T10:00:00.000Z";
        assertThat(Echeances.apresTentative(tache("en_cours", "x", 0, null), true, m, P)).isEqualTo(new Echeances.Issue("faite", null, 1));
        assertThat(Echeances.apresTentative(tache("en_cours", "x", 0, null), false, m, P)).isEqualTo(new Echeances.Issue("en_attente", "2026-01-01T10:01:00.000Z", 1));
        assertThat(Echeances.apresTentative(tache("en_cours", "x", 1, null), false, m, P)).isEqualTo(new Echeances.Issue("en_attente", "2026-01-01T10:05:00.000Z", 2));
        assertThat(Echeances.apresTentative(tache("en_cours", "x", 2, null), false, m, P)).isEqualTo(new Echeances.Issue("echouee", null, 3));
        var patiente = new PolitiqueRetentative(5, List.of(1), 10);
        assertThat(Echeances.apresTentative(tache("en_cours", "x", 3, null), false, m, patiente)).isEqualTo(new Echeances.Issue("en_attente", "2026-01-01T10:01:00.000Z", 4));
    }

    @Test
    void validation() {
        assertThat(Echeances.validerPlanification("t-1", "x", "c", "2026-01-01T10:00:00.000Z")).isEmpty();
        assertThat(Echeances.validerPlanification(" ", null, "", "demain"))
                .containsExactly("tenantId requis", "type requis", "cibleId requis", "echeanceLe doit être un instant ISO");
        assertThat(Echeances.validerPlanification("t", "x", "c", null)).containsExactly("echeanceLe doit être un instant ISO");
    }

    @Test
    void validationAvecNuls() {
        assertThat(Echeances.validerPlanification(null, null, null, "2026-01-01T10:00:00.000Z")).containsExactly("tenantId requis", "type requis", "cibleId requis");
    }
}
