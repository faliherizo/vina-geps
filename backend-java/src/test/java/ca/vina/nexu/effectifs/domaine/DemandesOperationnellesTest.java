package ca.vina.nexu.effectifs.domaine;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("Demandes opérationnelles (addenda 8, M-08) — saisie, décision, annulation, liste de temps supplémentaire")
class DemandesOperationnellesTest {

    private static DemandesOperationnelles.Demande enAttente() {
        return new DemandesOperationnelles.Demande("d1", "r1", "EST", "LISTE-TS", "2027-02-01", "2027-02-28", null,
                DemandesOperationnelles.Statut.EN_ATTENTE, "t0", null, null, null);
    }

    @Test
    @DisplayName("une décision : acceptée sans motif, refusée avec ; une seule fois")
    void decision() {
        DemandesOperationnelles.Demande a = enAttente().decidee(DemandesOperationnelles.Decision.ACCEPTER, "  ", "chef", "t1");
        assertThat(List.of(a.statut(), a.decideePar(), a.decideeLe())).containsExactly(DemandesOperationnelles.Statut.ACCEPTEE, "chef", "t1");
        assertThat(a.motifDecision()).isNull();
        assertThat(enAttente().decidee(DemandesOperationnelles.Decision.REFUSER, " Quota ", "chef", "t1").motifDecision()).isEqualTo("Quota");
        assertThatThrownBy(() -> enAttente().decidee(DemandesOperationnelles.Decision.REFUSER, null, "chef", "t1")).hasMessage("« motif » : un refus se motive");
        assertThatThrownBy(() -> a.decidee(DemandesOperationnelles.Decision.REFUSER, "x", "chef", "t2")).isInstanceOf(ConflitException.class)
                .hasMessage("la demande est déjà acceptee");
        assertThat(enAttente().annulee("julie", "t3").statut()).isEqualTo(DemandesOperationnelles.Statut.ANNULEE);
        assertThatThrownBy(() -> a.annulee("julie", "t3")).isInstanceOf(ConflitException.class);
        assertThat(List.of(enAttente().couvre("2027-02-01"), enAttente().couvre("2027-02-28"), enAttente().couvre("2027-03-01"), enAttente().couvre("2027-01-31")))
                .containsExactly(true, true, false, false);
        assertThat(DemandesOperationnelles.Decision.de("REFUSER")).isEqualTo(DemandesOperationnelles.Decision.REFUSER);
        assertThatThrownBy(() -> DemandesOperationnelles.Decision.de(null)).hasMessage("« decision » : ACCEPTER ou REFUSER");
        assertThat(DemandesOperationnelles.Statut.de("ANNULEE")).isEqualTo(DemandesOperationnelles.Statut.ANNULEE);
        assertThatThrownBy(() -> DemandesOperationnelles.Statut.de("x")).hasMessageContaining("« statut »");
    }

    @Test
    @DisplayName("la saisie : type requis, dates ordonnées, précisions bornées et rognées")
    void saisie() {
        assertThat(DemandesOperationnelles.saisie(" FORMATION ", "2027-03-01", "2027-03-02", "  "))
                .isEqualTo(new DemandesOperationnelles.Saisie("FORMATION", "2027-03-01", "2027-03-02", null));
        assertThat(DemandesOperationnelles.saisie("A", "2027-03-01", "2027-03-01", null).commentaire()).isNull();
        assertThat(DemandesOperationnelles.saisie("A", "2027-03-01", "2027-03-01", " ok ").commentaire()).isEqualTo("ok");
        assertThatThrownBy(() -> DemandesOperationnelles.saisie(" ", "2027-03-01", "2027-03-01", null)).hasMessage("« typeCode » requis");
        assertThatThrownBy(() -> DemandesOperationnelles.saisie(3, "2027-03-01", "2027-03-01", null)).hasMessage("« typeCode » requis");
        assertThatThrownBy(() -> DemandesOperationnelles.saisie("A", "2027-03-02", "2027-03-01", null)).hasMessage("« au » (2027-03-01) précède « du » (2027-03-02)");
        assertThatThrownBy(() -> DemandesOperationnelles.saisie("A", "2027-03-01", "2027-03-01", "x".repeat(501))).hasMessage("« commentaire » : au plus 500 caractères");
    }

    @Test
    @DisplayName("la liste de temps supplémentaire : ancienneté décroissante, puis nom")
    void listeTempsSupplementaire() {
        List<DemandesOperationnelles.Inscrit> l = DemandesOperationnelles.listeTempsSupplementaire(List.of(
                new DemandesOperationnelles.Inscrit("a", "Zoé", "EST", 4, "d", "a"), new DemandesOperationnelles.Inscrit("b", "Marc", "EST", 9, "d", "a"),
                new DemandesOperationnelles.Inscrit("c", "Ana", "EST", 4, "d", "a")));
        assertThat(l).extracting(DemandesOperationnelles.Inscrit::ressourceNom).containsExactly("Marc", "Ana", "Zoé");
    }
}
