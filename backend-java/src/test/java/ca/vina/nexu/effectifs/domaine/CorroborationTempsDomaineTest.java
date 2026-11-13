package ca.vina.nexu.effectifs.domaine;

import ca.vina.nexu.effectifs.domaine.CorroborationTemps.CorrelationSources;
import ca.vina.nexu.effectifs.domaine.CorroborationTemps.HorodatageExterne;
import ca.vina.nexu.effectifs.domaine.CorroborationTemps.Plage;
import ca.vina.nexu.effectifs.domaine.PresenceEffectifs.PointageCourt;
import ca.vina.nexu.effectifs.domaine.PresenceEffectifs.Sens;
import ca.vina.nexu.effectifs.domaine.TempsEffectifs.Anomalie;
import ca.vina.nexu.effectifs.domaine.TempsEffectifs.CodeAnomalie;
import ca.vina.nexu.effectifs.domaine.TempsEffectifs.Source;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.groups.Tuple.tuple;

/**
 * <strong>La corrélation de sources multiples (E-RF-13) — le domaine pur</strong>, jumeau des ajouts de
 * {@code effectifs-temps-domaine.test.ts} et {@code effectifs-presence-domaine.test.ts} : ce qui confirme un pointage, ce que
 * deux sources disent l'une de l'autre, le temps qu'une source propose seule, et les anomalies qui en découlent.
 */
@DisplayName("CorroborationTemps — sources tierces, concordance, anomalies")
class CorroborationTempsDomaineTest {

    private static final long CINQ_MINUTES = 5 * 60_000L;

    private static HorodatageExterne ext(String source, Sens sens, String hm) {
        return new HorodatageExterne(sens, "2031-03-10T" + hm + ":00.000Z", source);
    }

    private static PointageCourt pointage(Sens sens, String hm) {
        return new PointageCourt(sens, "2031-03-10T" + hm + ":00.000Z");
    }

    /** Un segment de feuille tel que les anomalies le lisent. */
    private record Seg(String date, Source source, Boolean corrobore, String sourceExterne, String confirmeLe) implements CorroborationTemps.SegmentProuve { }

    private static Seg pointe(String date, Boolean corrobore) {
        return new Seg(date, Source.POINTAGE, corrobore, null, null);
    }

    @Test
    @DisplayName("dit quelles sources confirment un pointage, et de combien, à la tolérance donnée")
    void corroboration() {
        CorroborationTemps.Corroboration c = CorroborationTemps.corroborationDe(pointage(Sens.ENTREE, "11:00"),
                List.of(ext("repartiteur", Sens.ENTREE, "11:04"), ext("borne", Sens.ENTREE, "11:02"), ext("borne", Sens.ENTREE, "10:59")), CINQ_MINUTES);
        assertThat(c).isEqualTo(new CorroborationTemps.Corroboration(true, List.of("borne", "repartiteur"), 4L));
        assertThat(CorroborationTemps.corroborationDe(pointage(Sens.ENTREE, "11:00"), List.of(ext("borne", Sens.SORTIE, "11:01")), CINQ_MINUTES))
                .isEqualTo(new CorroborationTemps.Corroboration(false, List.of(), null));
        assertThat(CorroborationTemps.corroborationDe(pointage(Sens.ENTREE, "11:00"), List.of(ext("borne", Sens.ENTREE, "11:06")), CINQ_MINUTES).corrobore()).isFalse();
        assertThat(CorroborationTemps.corroborationDe(pointage(Sens.ENTREE, "11:00"), List.of(ext("borne", Sens.ENTREE, "11:05")), CINQ_MINUTES).corrobore()).isTrue();
        assertThat(CorroborationTemps.corroborationDe(pointage(Sens.ENTREE, "11:00"), List.of(ext("borne", Sens.ENTREE, "11:20")), 30 * 60_000L).sources())
                .containsExactly("borne");
    }

    @Test
    @DisplayName("deux sources se confrontent paire par paire : concordants et divergents, motifs nommés")
    void concordance() {
        List<HorodatageExterne> deux = List.of(ext("a", Sens.ENTREE, "11:00"), ext("b", Sens.ENTREE, "11:01"));
        assertThat(CorroborationTemps.concordanceDesSources(deux, 0).getFirst()).extracting("concordants", "divergents").containsExactly(0, 2);
        CorroborationTemps.ConcordanceSources d = CorroborationTemps.concordanceDesSources(deux, 2 * 60_000L).getFirst();
        assertThat(d).isEqualTo(new CorroborationTemps.ConcordanceSources(List.of("a", "b"), 1, 0, List.of("1 horodatage(s) concordant(s) entre « a » et « b »")));
        List<CorroborationTemps.ConcordanceSources> trois = CorroborationTemps.concordanceDesSources(List.of(ext("c", Sens.SORTIE, "19:00"),
                ext("a", Sens.ENTREE, "11:00"), ext("b", Sens.ENTREE, "12:30")), CINQ_MINUTES);
        assertThat(trois).extracting(CorroborationTemps.ConcordanceSources::sources).containsExactly(List.of("a", "b"), List.of("a", "c"), List.of("b", "c"));
        assertThat(trois.getFirst().motifs()).containsExactly("0 horodatage(s) concordant(s) entre « a » et « b »",
                "2 horodatage(s) qu'une source porte et que l'autre ne confirme pas : à régler avant de valider la feuille");
        assertThat(CorroborationTemps.concordanceDesSources(List.of(ext("a", Sens.ENTREE, "11:00")), CINQ_MINUTES)).isEmpty();
    }

    @Test
    @DisplayName("une source couvre des plages par paires ; une entrée sans sortie ne produit rien")
    void plages() {
        List<HorodatageExterne> e = List.of(ext("a", Sens.SORTIE, "09:00"), ext("a", Sens.ENTREE, "10:00"), ext("a", Sens.ENTREE, "10:05"),
                ext("a", Sens.DEBUT_PAUSE, "12:00"), ext("a", Sens.FIN_PAUSE, "12:30"), ext("a", Sens.SORTIE, "18:00"), ext("a", Sens.ENTREE, "20:00"),
                ext("b", Sens.ENTREE, "07:00"));
        assertThat(CorroborationTemps.plagesDUneSource(e, "a")).containsExactly(new Plage("2031-03-10T10:00:00.000Z", "2031-03-10T12:00:00.000Z"),
                new Plage("2031-03-10T12:30:00.000Z", "2031-03-10T18:00:00.000Z"));
        assertThat(CorroborationTemps.plagesDUneSource(e, "b")).isEmpty();
    }

    @Test
    @DisplayName("dans un quart, la première source par ordre alphabétique fait foi, bornée au quart")
    void plagesDuQuart() {
        String debut = "2031-03-10T10:00:00.000Z";
        String fin = "2031-03-10T22:00:00.000Z";
        List<HorodatageExterne> e = List.of(ext("RAO", Sens.ENTREE, "10:02"), ext("RAO", Sens.SORTIE, "21:58"), ext("BORNE", Sens.ENTREE, "09:50"),
                ext("BORNE", Sens.SORTIE, "22:01"), ext("ALPHA", Sens.ENTREE, "23:00"), ext("ALPHA", Sens.SORTIE, "23:30"));
        assertThat(CorroborationTemps.plagesDuQuart(e, debut, fin)).contains(new CorroborationTemps.PlagesExternes("BORNE", List.of(new Plage(debut, fin))));
        assertThat(CorroborationTemps.plagesDuQuart(List.of(ext("RAO", Sens.ENTREE, "11:00"), ext("RAO", Sens.SORTIE, "12:00")), debut, fin))
                .contains(new CorroborationTemps.PlagesExternes("RAO", List.of(new Plage("2031-03-10T11:00:00.000Z", "2031-03-10T12:00:00.000Z"))));
        assertThat(CorroborationTemps.plagesDuQuart(e.subList(4, 6), debut, fin)).isEmpty();
    }

    @Test
    @DisplayName("la corrélation se fait par jour local ; les jours avec source se connaissent")
    void correlationParJour() {
        List<HorodatageExterne> e = List.of(new HorodatageExterne(Sens.ENTREE, "2031-03-13T11:15:00.000Z", "BORNE"),
                new HorodatageExterne(Sens.ENTREE, "2031-03-12T10:00:00.000Z", "BORNE"), new HorodatageExterne(Sens.ENTREE, "2031-03-12T10:02:00.000Z", "RAO"),
                new HorodatageExterne(Sens.ENTREE, "2031-03-13T10:00:00.000Z", "RAO"), new HorodatageExterne(Sens.ENTREE, "2031-03-11T10:20:00.000Z", "BORNE"));
        assertThat(CorroborationTemps.correlationParJour(e, CINQ_MINUTES)).containsExactly(new CorrelationSources("2031-03-12", List.of("BORNE", "RAO"), 1, 0),
                new CorrelationSources("2031-03-13", List.of("BORNE", "RAO"), 0, 2));
        assertThat(CorroborationTemps.joursAvecSource(e)).isEqualTo(Set.of("2031-03-11", "2031-03-12", "2031-03-13"));
    }

    @Test
    @DisplayName("un temps pointé non confirmé se signale quand une source existe ce jour-là, regroupé par jour, au sort paramétré")
    void nonCorrobore() {
        List<Seg> s = List.of(pointe("2031-03-11", false), pointe("2031-03-10", false), pointe("2031-03-10", false), pointe("2031-03-10", true),
                pointe("2031-03-12", null), new Seg("2031-03-10", Source.GENERE, null, null, null), new Seg("2031-03-10", Source.SAISIE, false, null, null),
                pointe("2031-03-14", false));
        List<Anomalie> a = CorroborationTemps.anomaliesDeCorroboration(s, d -> !d.equals("2031-03-14"), ActionCorroboration.SIGNALER);
        assertThat(a).extracting(Anomalie::code, Anomalie::date, Anomalie::bloquante).containsExactly(tuple(CodeAnomalie.NON_CORROBORE, "2031-03-10", false),
                tuple(CodeAnomalie.NON_CORROBORE, "2031-03-11", false));
        assertThat(a.getFirst().message())
                .isEqualTo("le 2031-03-10 : 2 segment(s) pointé(s) qu'aucune source tierce ne confirme, alors qu'une source a été importée ce jour-là");
        assertThat(CorroborationTemps.anomaliesDeCorroboration(s, d -> false, ActionCorroboration.SIGNALER)).isEmpty();
        assertThat(CorroborationTemps.anomaliesDeCorroboration(s, d -> true, ActionCorroboration.IGNORER)).isEmpty();
        assertThat(CorroborationTemps.anomaliesDeCorroboration(s, d -> true, ActionCorroboration.BLOQUER).getFirst().bloquante()).isTrue();
    }

    @Test
    @DisplayName("deux sources qui divergent : bloquant par défaut, au sort paramétré ; d'accord, rien")
    void divergence() {
        List<CorrelationSources> c = List.of(new CorrelationSources("2031-03-10", List.of("borne", "repartiteur"), 0, 2),
                new CorrelationSources("2031-03-11", List.of("a", "b"), 3, 0));
        List<Anomalie> a = CorroborationTemps.anomaliesDeDivergence(c, ActionCorroboration.BLOQUER);
        assertThat(a).containsExactly(new Anomalie(CodeAnomalie.SOURCES_DIVERGENTES, "2031-03-10",
                "le 2031-03-10 : « borne » et « repartiteur » divergent sur 2 horodatage(s) — le temps validé sur leur foi ne vaudrait rien", true));
        assertThat(CorroborationTemps.anomaliesDeDivergence(c, ActionCorroboration.SIGNALER).getFirst().bloquante()).isFalse();
        assertThat(CorroborationTemps.anomaliesDeDivergence(c, ActionCorroboration.IGNORER)).isEmpty();
    }

    @Test
    @DisplayName("un temps proposé par une source seule attend sa confirmation, jour par jour, sources nommées")
    void aConfirmer() {
        List<Anomalie> a = CorroborationTemps.anomaliesAConfirmer(List.of(new Seg("2031-03-12", Source.EXTERNE, null, "RAO", null),
                new Seg("2031-03-12", Source.EXTERNE, null, "BORNE", null), new Seg("2031-03-11", Source.EXTERNE, null, null, null),
                new Seg("2031-03-13", Source.EXTERNE, null, "RAO", "2031-03-14T00:00:00.000Z"), new Seg("2031-03-14", Source.POINTAGE, false, null, null)));
        assertThat(a).extracting(Anomalie::date, Anomalie::code, Anomalie::bloquante).containsExactly(tuple("2031-03-11", CodeAnomalie.EXTERNE_A_CONFIRMER, true),
                tuple("2031-03-12", CodeAnomalie.EXTERNE_A_CONFIRMER, true));
        assertThat(a.getFirst().message()).contains("« source externe »");
        assertThat(a.get(1).message()).isEqualTo("le 2031-03-12 : temps proposé depuis l'horodatage de « BORNE », « RAO » seul, sans pointage — "
                + "confirmez-le ou corrigez-le avant de soumettre");
    }
}
