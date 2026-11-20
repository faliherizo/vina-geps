package ca.vina.nexu.alertes.domaine;

import ca.vina.nexu.alertes.domaine.Alertes.Composition;
import ca.vina.nexu.alertes.domaine.Alertes.ContenuModele;
import ca.vina.nexu.alertes.domaine.Alertes.Destinataire;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** ST-H04 (ADR 0085) : FX-AO-20260810, addenda 1, Q1 — aucun renseignement de santé ne transite par la solution. */
@DisplayName("Aucun numéro d'assurance maladie dans une alerte, un modèle ou une note")
class ContenuSansRenseignementDeSanteTest {

    private static final Destinataire JULIE = new Destinataire("julie", "Julie Roy", "E1");

    @Test
    @DisplayName("le numéro d'assurance maladie est refusé, avec ou sans séparateurs, en nommant le champ et jamais le texte")
    void refuse() {
        for (String texte : List.of("Patient TREM 1234 5678 en salle 4", "nam:trem12345678", "TREM-1234-5678", "TREM12345678")) {
            assertThatThrownBy(() -> ContenuSansRenseignementDeSante.verifier("corps", texte)).isInstanceOf(RefusAlerte.class)
                    .hasMessageStartingWith("« corps » : un numéro d'assurance maladie ne peut pas y figurer")
                    .hasMessageNotContaining("1234")
                    .satisfies(e -> assertThat(((RefusAlerte) e).nature()).isEqualTo(RefusAlerte.Nature.INVALIDE));
        }
    }

    @Test
    @DisplayName("aucun faux refus sur un message usuel : salle, poste, heure, téléphone, code de garde")
    void laisseLeTexteUsuel() {
        for (String texte : List.of("Code bleu, salle 1234, 5e étage", "Rappelez le poste 4567 avant 18 h 30", "Tél. 450 555 1234",
                "Garde CARDIO-2026 : présentez-vous à l'urgence", "Salle 1234 5678", "Lot ABCDE 1234 5678")) {
            assertThat(ContenuSansRenseignementDeSante.verifier("corps", texte)).isEqualTo(texte);
        }
        assertThat(ContenuSansRenseignementDeSante.verifier("corps", null)).isNull();
    }

    @Test
    @DisplayName("le refus tient à la composition d'une alerte, au contenu d'un modèle et à la note sur un destinataire")
    void partout() {
        assertThatThrownBy(() -> Alertes.composer("a", "T", "chef", "2026-11-19T10:00:00Z",
                new Composition("Code bleu", "Patient TREM 1234 5678", Priorite.URGENTE, List.of()), List.of(JULIE), List.of()))
                .isInstanceOf(RefusAlerte.class).hasMessageContaining("« corps »");
        assertThatThrownBy(() -> Alertes.composer("a", "T", "chef", "2026-11-19T10:00:00Z",
                new Composition("TREM 1234 5678", "Salle 4", Priorite.URGENTE, List.of()), List.of(JULIE), List.of()))
                .isInstanceOf(RefusAlerte.class).hasMessageContaining("« titre »");
        assertThatThrownBy(() -> new ContenuModele("Garde", "NAM TREM12345678", Priorite.URGENTE, List.of(), List.of()).valide())
                .isInstanceOf(RefusAlerte.class).hasMessageContaining("« corps »");
        assertThatThrownBy(() -> NotesDestinataires.Note.ecrire("julie", "Son NAM : TREM 1234 5678", "2026-11-19T10:00:00Z", "chef"))
                .isInstanceOf(RefusAlerte.class).hasMessageContaining("« texte »");
        assertThat(NotesDestinataires.Note.ecrire("julie", "Joindre plutôt par texto la nuit", "2026-11-19T10:00:00Z", "chef").texte())
                .isEqualTo("Joindre plutôt par texto la nuit");
    }
}
