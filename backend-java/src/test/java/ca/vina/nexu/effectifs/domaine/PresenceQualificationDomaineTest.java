package ca.vina.nexu.effectifs.domaine;

import ca.vina.nexu.effectifs.domaine.PresenceEffectifs.PointageCourt;
import ca.vina.nexu.effectifs.domaine.PresenceEffectifs.Segment;
import ca.vina.nexu.effectifs.domaine.PresenceEffectifs.SegmentSaisi;
import ca.vina.nexu.effectifs.domaine.PresenceEffectifs.Sens;
import ca.vina.nexu.effectifs.domaine.PresenceEffectifs.TypeSegment;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Le site, la nature de pause et l'activité portés par les pointages et les segments (E-GP-2, E-GP-7, E-GP-15). */
@DisplayName("Présence qualifiée — site, nature, activité")
class PresenceQualificationDomaineTest {

    private static final String J = "2031-03-10T";

    private static String h(String heure) {
        return J + heure + ":00.000Z";
    }

    private static Map<String, Object> seg(String debut, String fin, String type, String nature, String activite) {
        Map<String, Object> m = new HashMap<>();
        m.put("debut", h(debut));
        m.put("fin", h(fin));
        m.put("type", type);
        m.put("nature", nature);
        m.put("activiteCode", activite);
        return m;
    }

    @Test
    @DisplayName("le site suit le dernier pointage qui en déclare un ; l'activité se réarme à chaque reprise, et la pause n'en porte pas")
    void segmentsQualifies() {
        List<PointageCourt> faits = List.of(new PointageCourt(Sens.ENTREE, h("11:00"), "NORD", null, "INTERVENTION"),
                new PointageCourt(Sens.DEBUT_PAUSE, h("13:00"), null, NaturePause.REPAS, null),
                new PointageCourt(Sens.FIN_PAUSE, h("13:30"), "SUD", null, null),
                new PointageCourt(Sens.SORTIE, h("15:00"), null, null, null));
        assertThat(PresenceEffectifs.segmentsDe(faits, h("11:00"), 0, h("16:00"))).containsExactly(
                new Segment(h("11:00"), h("13:00"), TypeSegment.TRAVAIL, 120, "NORD", "INTERVENTION"),
                new Segment(h("13:00"), h("13:30"), TypeSegment.PAUSE, 30, "NORD", null),
                new Segment(h("13:30"), h("15:00"), TypeSegment.TRAVAIL, 90, "SUD", null));
    }

    @Test
    @DisplayName("une présence préalable porte le site et l'activité de l'entrée")
    void prealableQualifiee() {
        List<Segment> s = PresenceEffectifs.segmentsDe(List.of(new PointageCourt(Sens.ENTREE, h("10:50"), "NORD", null, "SOUTIEN")), h("11:00"), 15, h("12:00"));
        assertThat(s).extracting(Segment::milieuId, Segment::activiteCode).containsOnly(org.assertj.core.groups.Tuple.tuple("NORD", "SOUTIEN"));
    }

    @Test
    @DisplayName("une pause saisie vaut PAUSE à défaut ; sa nature et l'activité du travail voyagent sur les pointages qui les ouvrent")
    void saisieQualifiee() {
        List<SegmentSaisi> s = PresenceEffectifs.segmentsSaisis(List.of(seg("11:00", "13:00", "TRAVAIL", null, " INTERVENTION "),
                seg("13:00", "13:30", "PAUSE", "REPAS", ""), seg("13:30", "14:00", "TRAVAIL", "", null), seg("14:00", "14:15", "PAUSE", null, null),
                seg("14:15", "15:00", "TRAVAIL", null, "SOUTIEN")));
        assertThat(s).extracting(SegmentSaisi::nature).containsExactly(null, NaturePause.REPAS, null, NaturePause.PAUSE, null);
        assertThat(PresenceEffectifs.pointagesDeSegments(s)).containsExactly(new PointageCourt(Sens.ENTREE, h("11:00"), null, null, "INTERVENTION"),
                new PointageCourt(Sens.DEBUT_PAUSE, h("13:00"), null, NaturePause.REPAS, null), new PointageCourt(Sens.FIN_PAUSE, h("13:30"), null, null, null),
                new PointageCourt(Sens.DEBUT_PAUSE, h("14:00"), null, NaturePause.PAUSE, null),
                new PointageCourt(Sens.FIN_PAUSE, h("14:15"), null, null, "SOUTIEN"), new PointageCourt(Sens.SORTIE, h("15:00")));
    }

    @Test
    @DisplayName("les contresens sont refusés en nommant le segment : nature sur du travail, activité sur une pause, nature inconnue")
    void contresens() {
        assertThatThrownBy(() -> PresenceEffectifs.segmentsSaisis(List.of(seg("11:00", "12:00", "TRAVAIL", "REPAS", null))))
                .hasMessage("segment 1 : une nature ne se pose que sur une pause");
        assertThatThrownBy(() -> PresenceEffectifs.segmentsSaisis(List.of(seg("11:00", "12:00", "TRAVAIL", null, null), seg("12:00", "12:30", "PAUSE", null, "SOUTIEN"))))
                .hasMessage("segment 2 : une activité ne se pose que sur du travail");
        assertThatThrownBy(() -> PresenceEffectifs.segmentsSaisis(List.of(seg("11:00", "12:00", "TRAVAIL", null, null), seg("12:00", "12:30", "PAUSE", "SIESTE", null))))
                .hasMessage("segment 2 : nature PAUSE, REPAS, REPOS");
    }

    @Test
    @DisplayName("le pointage relu se réduit à son sens, son instant, son site, sa nature et son activité")
    void court() {
        PointageEffectifs p = new PointageEffectifs("p1", "r1", "Julie", new PointageEffectifs.Lieu("pl", "NORD", "EST"), Sens.FIN_PAUSE, h("13:30"),
                PresenceEffectifs.OriginePointage.APPAREIL, null, null, null, new PointageEffectifs.Qualification(null, "SOUTIEN"));
        assertThat(p.court()).isEqualTo(new PointageCourt(Sens.FIN_PAUSE, h("13:30"), "NORD", null, "SOUTIEN"));
    }
}
