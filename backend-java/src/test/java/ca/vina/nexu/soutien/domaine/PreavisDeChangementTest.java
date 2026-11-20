package ca.vina.nexu.soutien.domaine;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * ST-H02 (ADR 0085) : le préavis d'une maintenance suit sa nature — FX-AO-20260810, grille 99 (rehaussement, 21 jours
 * ouvrables) et 99.1 (correctif, 3 jours ouvrables) ; l'entretien garde ses 72 h.
 */
@DisplayName("Préavis selon la nature du changement, en jours ouvrables du Québec")
class PreavisDeChangementTest {

    /** Un samedi : le jour de l'annonce ne compte pas, la fin de semaine non plus. */
    private static final Instant SAMEDI = Instant.parse("2026-09-12T10:00:00Z");

    @Test
    @DisplayName("Pâques et les jours fériés de la Loi sur les normes du travail, reports du dimanche compris")
    void feries() {
        assertThat(JoursOuvrablesDuQuebec.paques(2026)).isEqualTo(LocalDate.of(2026, 4, 5));
        assertThat(JoursOuvrablesDuQuebec.paques(2027)).isEqualTo(LocalDate.of(2027, 3, 28));
        assertThat(JoursOuvrablesDuQuebec.paques(2038)).isEqualTo(LocalDate.of(2038, 4, 25));
        assertThat(JoursOuvrablesDuQuebec.feries(2026)).containsExactlyInAnyOrder(
                LocalDate.of(2026, 1, 1), LocalDate.of(2026, 4, 3), LocalDate.of(2026, 4, 6), LocalDate.of(2026, 5, 18),
                LocalDate.of(2026, 6, 24), LocalDate.of(2026, 7, 1), LocalDate.of(2026, 9, 7), LocalDate.of(2026, 10, 12),
                LocalDate.of(2026, 12, 25));
        // En 2029, le 1er juillet est un dimanche : le congé passe au lundi 2 ; en 2029 la Saint-Jean est un dimanche aussi.
        assertThat(JoursOuvrablesDuQuebec.feries(2029)).contains(LocalDate.of(2029, 7, 2), LocalDate.of(2029, 6, 25));
        assertThat(JoursOuvrablesDuQuebec.estOuvrable(LocalDate.of(2026, 9, 14))).isTrue();
        assertThat(JoursOuvrablesDuQuebec.estOuvrable(LocalDate.of(2026, 9, 12))).isFalse();
        assertThat(JoursOuvrablesDuQuebec.estOuvrable(LocalDate.of(2026, 9, 13))).isFalse();
        assertThat(JoursOuvrablesDuQuebec.estOuvrable(LocalDate.of(2026, 10, 12))).isFalse();
    }

    @Test
    @DisplayName("le premier début admis : N jours ouvrables pleins après l'annonce, puis minuit à Montréal")
    void premierDebutAdmis() {
        // Correctif annoncé un samedi : lundi, mardi, mercredi ; début admis le jeudi 17 à 0 h (4 h UTC).
        assertThat(NatureChangement.CORRECTIF.premierDebutAdmis(SAMEDI)).isEqualTo(Instant.parse("2026-09-17T04:00:00Z"));
        // Rehaussement : 21 jours ouvrables, l'Action de grâce (12 octobre) sautée ; début admis le 14 octobre.
        assertThat(NatureChangement.REHAUSSEMENT.premierDebutAdmis(SAMEDI)).isEqualTo(Instant.parse("2026-10-14T04:00:00Z"));
        assertThat(NatureChangement.ENTRETIEN.premierDebutAdmis(SAMEDI)).isEqualTo(SAMEDI.plusSeconds(72 * 3_600));
        // Annoncé le mardi 22 décembre : 23, 24, puis Noël et la fin de semaine sautés, le lundi 28 ; début admis le 29.
        assertThat(JoursOuvrablesDuQuebec.premierDebutAdmis(Instant.parse("2026-12-22T15:00:00Z"), 3)).isEqualTo(Instant.parse("2026-12-29T05:00:00Z"));
    }

    @Test
    @DisplayName("la nature : ensemble fermé, entretien par défaut, refus qui nomme les natures admises")
    void nature() {
        assertThat(NatureChangement.de(null)).isEqualTo(NatureChangement.ENTRETIEN);
        assertThat(NatureChangement.de(" ")).isEqualTo(NatureChangement.ENTRETIEN);
        assertThat(NatureChangement.de(" rehaussement ")).isEqualTo(NatureChangement.REHAUSSEMENT);
        assertThat(NatureChangement.codes()).containsExactly("entretien", "correctif", "rehaussement");
        assertThat(NatureChangement.CORRECTIF.joursOuvrables()).isEqualTo(3);
        assertThat(NatureChangement.ENTRETIEN.heures()).isEqualTo(72);
        assertThat(NatureChangement.REHAUSSEMENT.libelle()).contains("Rehaussement");
        assertThatThrownBy(() -> NatureChangement.de("urgence")).isInstanceOf(NatureChangement.NatureInconnueException.class)
                .hasMessage("nature : « urgence » inconnue (attendu : entretien, correctif, rehaussement)");
    }

    @Test
    @DisplayName("la validation d'une annonce applique le préavis de sa nature et dit la date qui l'aurait respecté")
    void validation() {
        String maintenant = SAMEDI.toString();
        Map<String, Object> correctifTropTot = Map.of("debut", "2026-09-16T06:00:00Z", "fin", "2026-09-16T07:00:00Z",
                "description", "Correctif de sécurité de la bibliothèque TLS", "nature", "correctif");
        assertThat(Soutien.validerMaintenance(correctifTropTot, maintenant))
                .containsExactly("préavis insuffisant pour un correctif : 3 jours ouvrables au moins ; premier début admis : 2026-09-17 (heure de Montréal)");
        Map<String, Object> correctifATemps = Map.of("debut", "2026-09-17T06:00:00Z", "fin", "2026-09-17T07:00:00Z",
                "description", "Correctif de sécurité de la bibliothèque TLS", "nature", "correctif");
        assertThat(Soutien.validerMaintenance(correctifATemps, maintenant)).isEmpty();
        Map<String, Object> rehaussement = Map.of("debut", "2026-09-17T06:00:00Z", "fin", "2026-09-17T07:00:00Z",
                "description", "Version 5 : nouveau tableau de bord", "nature", "rehaussement");
        assertThat(Soutien.validerMaintenance(rehaussement, maintenant)).singleElement().asString().contains("21 jours ouvrables", "2026-10-14");
        Map<String, Object> inconnue = Map.of("debut", "2026-12-17T06:00:00Z", "fin", "2026-12-17T07:00:00Z",
                "description", "Version 5 : nouveau tableau de bord", "nature", "urgence");
        assertThat(Soutien.validerMaintenance(inconnue, maintenant)).singleElement().asString().startsWith("nature : « urgence » inconnue");
    }
}
