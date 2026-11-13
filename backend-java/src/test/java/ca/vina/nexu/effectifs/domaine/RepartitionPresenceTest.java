package ca.vina.nexu.effectifs.domaine;

import ca.vina.nexu.effectifs.domaine.PresenceEffectifs.PointageCourt;
import ca.vina.nexu.effectifs.domaine.PresenceEffectifs.Segment;
import ca.vina.nexu.effectifs.domaine.PresenceEffectifs.SegmentSaisi;
import ca.vina.nexu.effectifs.domaine.PresenceEffectifs.Sens;
import ca.vina.nexu.effectifs.domaine.PresenceEffectifs.TypeSegment;
import ca.vina.nexu.effectifs.domaine.RepartitionPresence.Deplacement;
import ca.vina.nexu.effectifs.domaine.RepartitionPresence.PlageOccupee;
import ca.vina.nexu.effectifs.domaine.RepartitionPresence.TempsParActivite;
import ca.vina.nexu.effectifs.domaine.RepartitionPresence.TempsParMilieu;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Multi-site (E-GP-7), activités (E-GP-15) et cumul des saisies (P-GP-4) — jumeau des essais du domaine Node. */
@DisplayName("Répartition de la présence — domaine pur")
class RepartitionPresenceTest {

    private static final String J = "2031-03-10T";

    private static Segment seg(TypeSegment type, long minutes, String milieu, String activite) {
        return new Segment(J + "11:00:00.000Z", J + "12:00:00.000Z", type, minutes, milieu, activite);
    }

    private static PointageCourt p(Sens sens, String heure, String milieu) {
        return new PointageCourt(sens, J + heure + ":00.000Z", milieu, null, null);
    }

    @Test
    @DisplayName("le temps payé par milieu : pauses exclues, clés fondues, durées nulles écartées, du plus long au plus court puis par clé")
    void parMilieu() {
        List<Segment> s = List.of(seg(TypeSegment.TRAVAIL, 60, "NORD", null), seg(TypeSegment.PAUSE, 30, "NORD", null),
                seg(TypeSegment.TRAVAIL, 90, "SUD", null), seg(TypeSegment.PRESENCE_PREALABLE, 30, "NORD", null),
                seg(TypeSegment.TRAVAIL, 45, null, null), seg(TypeSegment.TRAVAIL, 45, "EST", null), seg(TypeSegment.TRAVAIL, 0, "OUEST", null));
        assertThat(RepartitionPresence.tempsParMilieu(s)).containsExactly(new TempsParMilieu("NORD", 90), new TempsParMilieu("SUD", 90),
                new TempsParMilieu("EST", 45), new TempsParMilieu(null, 45));
    }

    @Test
    @DisplayName("le temps payé par activité, une activité non déclarée restant une ligne comptée")
    void parActivite() {
        List<Segment> s = List.of(seg(TypeSegment.TRAVAIL, 120, "N", "INTERVENTION"), seg(TypeSegment.TRAVAIL, 30, "N", null),
                seg(TypeSegment.PAUSE, 30, "N", null), seg(TypeSegment.TRAVAIL, 30, "N", "INTERVENTION"));
        assertThat(RepartitionPresence.tempsParActivite(s)).containsExactly(new TempsParActivite("INTERVENTION", 150), new TempsParActivite(null, 30));
        assertThat(RepartitionPresence.tempsParActivite(List.of())).isEmpty();
    }

    @Test
    @DisplayName("les déplacements : un changement de milieu entre deux pointages consécutifs, dans l'ordre du temps ; un pointage sans milieu ne compte pas")
    void deplacements() {
        List<PointageCourt> faits = List.of(p(Sens.SORTIE, "18:00", "NORD"), p(Sens.ENTREE, "11:00", "NORD"), p(Sens.DEBUT_PAUSE, "13:00", "SUD"),
                p(Sens.FIN_PAUSE, "13:30", null), p(Sens.FIN_PAUSE, "14:00", "SUD"));
        assertThat(RepartitionPresence.deplacementsDe(faits)).containsExactly(new Deplacement(J + "13:00:00.000Z", "NORD", "SUD"),
                new Deplacement(J + "18:00:00.000Z", "SUD", "NORD"));
        assertThat(RepartitionPresence.deplacementsDe(List.of(p(Sens.ENTREE, "11:00", "NORD")))).isEmpty();
    }

    @Test
    @DisplayName("les plages couvertes : une ouverture et la fermeture suivante ; une ouverture répétée garde la première ; une ouverture sans fermeture ne couvre rien")
    void plages() {
        List<PointageCourt> faits = List.of(p(Sens.DEBUT_PAUSE, "13:00", null), p(Sens.ENTREE, "11:00", null), p(Sens.FIN_PAUSE, "11:30", null),
                p(Sens.SORTIE, "10:00", null), p(Sens.FIN_PAUSE, "14:00", null));
        assertThat(RepartitionPresence.plagesCouvertes(faits)).containsExactly(new PlageOccupee(J + "11:00:00.000Z", J + "13:00:00.000Z"));
    }

    @Test
    @DisplayName("le cumul : un temps contigu passe ; un chevauchement est refusé en nommant les deux plages")
    void cumul() {
        List<PlageOccupee> deja = List.of(new PlageOccupee(J + "11:00:00.000Z", J + "15:00:00.000Z"));
        SegmentSaisi apres = new SegmentSaisi(J + "15:00:00.000Z", J + "18:00:00.000Z", TypeSegment.TRAVAIL);
        SegmentSaisi avant = new SegmentSaisi(J + "08:00:00.000Z", J + "11:00:00.000Z", TypeSegment.TRAVAIL);
        assertThatCode(() -> RepartitionPresence.verifierCumul(List.of(avant, apres), deja)).doesNotThrowAnyException();
        SegmentSaisi chevauche = new SegmentSaisi(J + "14:00:00.000Z", J + "16:00:00.000Z", TypeSegment.TRAVAIL);
        assertThatThrownBy(() -> RepartitionPresence.verifierCumul(List.of(avant, chevauche), deja)).isInstanceOf(EffectifsInvalideException.class)
                .hasMessage("le temps du 2031-03-10 de 14:00 à 16:00 chevauche du temps déjà déclaré (11:00 – 15:00) : corrigez l'un des deux plutôt que de compter deux fois");
    }
}
