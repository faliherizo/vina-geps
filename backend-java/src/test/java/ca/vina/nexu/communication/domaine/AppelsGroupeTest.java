package ca.vina.nexu.communication.domaine;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("Appel de groupe et réactions — règles pures")
class AppelsGroupeTest {

    private static final String T0 = "2026-09-16T12:00:00.000Z";

    @Test
    @DisplayName("les codes se convertissent une fois ; un code inconnu est refusé en le nommant")
    void codes() {
        assertThat(AppelsGroupe.EtatAppel.de("en_cours")).isEqualTo(AppelsGroupe.EtatAppel.EN_COURS);
        assertThat(AppelsGroupe.EtatAppel.MANQUE.libelle()).isEqualTo("manqué");
        assertThat(AppelsGroupe.EtatInvite.de("ignore")).isEqualTo(AppelsGroupe.EtatInvite.IGNORE);
        assertThatThrownBy(() -> AppelsGroupe.EtatAppel.de("perdu")).hasMessage("état d'appel de groupe inconnu : perdu");
        assertThatThrownBy(() -> AppelsGroupe.EtatInvite.de("absent")).hasMessage("état d'invité inconnu : absent");
        assertThat(Reaction.de("regarde").mots()).isEqualTo("je regarde");
        assertThat(AppelsGroupe.Geste.IGNORER.code()).isEqualTo("ignorer");
    }

    @Test
    @DisplayName("un participant répété n'est invité qu'une fois ; la sonnerie n'expire qu'au-delà du délai, et pas un appel commencé")
    void lancerEtExpirer() {
        AppelsGroupe.Appel a = AppelsGroupe.lancer("a1", "c1", "chef", List.of("chef", "julie", "julie"), T0);
        assertThat(a.invites()).extracting(AppelsGroupe.Invite::personId).containsExactly("chef", "julie");
        assertThat(AppelsGroupe.expirerSi(a, "2026-09-16T12:00:45.000Z")).isSameAs(a);
        AppelsGroupe.Appel manque = AppelsGroupe.expirerSi(a, "2026-09-16T12:00:46.000Z");
        assertThat(manque.etat()).isEqualTo(AppelsGroupe.EtatAppel.MANQUE);
        assertThat(manque.invite("chef").orElseThrow().etat()).isEqualTo(AppelsGroupe.EtatInvite.PARTI);
        assertThat(AppelsGroupe.dureeSecondes(manque)).isZero();
        AppelsGroupe.Appel commence = AppelsGroupe.appliquer(a, AppelsGroupe.Geste.REJOINDRE, "julie", "2026-09-16T12:00:05.000Z");
        assertThat(AppelsGroupe.expirerSi(commence, "2026-09-16T13:00:00.000Z")).isSameAs(commence);
        assertThat(AppelsGroupe.dureeSecondes(commence)).isZero();
        assertThat(commence.enCarte("julie")).containsEntry("entrant", false).containsEntry("monEtat", "present");
        assertThat(commence.enCarte("inconnu")).containsEntry("monEtat", null).containsEntry("entrant", false);
    }

    @Test
    @DisplayName("seul l'auteur et le destinataire lisent un message privé ; un message public se lit de tous")
    void adresse() {
        Messagerie.Adresse privee = new Messagerie.Adresse("marc", null);
        assertThat(privee.visiblePar("marc", "julie")).isTrue();
        assertThat(privee.visiblePar("julie", "julie")).isTrue();
        assertThat(privee.visiblePar("chef", "julie")).isFalse();
        assertThat(Messagerie.Adresse.PUBLIQUE.visiblePar("chef", "julie")).isTrue();
    }
}
