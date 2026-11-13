package ca.vina.nexu.effectifs.domaine;

import ca.vina.nexu.effectifs.domaine.Milieux.Mecanisme;
import ca.vina.nexu.effectifs.domaine.PresenceEffectifs.Ecart;
import ca.vina.nexu.effectifs.domaine.PresenceEffectifs.Jugement;
import ca.vina.nexu.effectifs.domaine.PresenceEffectifs.MilieuPreuves;
import ca.vina.nexu.effectifs.domaine.PresenceEffectifs.PointageCourt;
import ca.vina.nexu.effectifs.domaine.PresenceEffectifs.QuartPointe;
import ca.vina.nexu.effectifs.domaine.PresenceEffectifs.QuartPublie;
import ca.vina.nexu.effectifs.domaine.PresenceEffectifs.Resultat;
import ca.vina.nexu.effectifs.domaine.PresenceEffectifs.Segment;
import ca.vina.nexu.effectifs.domaine.PresenceEffectifs.SegmentSaisi;
import ca.vina.nexu.effectifs.domaine.PresenceEffectifs.Sens;
import ca.vina.nexu.effectifs.domaine.PresenceEffectifs.Soumission;
import ca.vina.nexu.effectifs.domaine.PresenceEffectifs.TypeSegment;
import ca.vina.nexu.effectifs.domaine.PresenceEffectifs.VerdictPreuve;
import ca.vina.nexu.effectifs.domaine.PresenceEffectifs.VerdictQr;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static ca.vina.nexu.effectifs.domaine.PresenceEffectifs.FENETRE_QR_MS;
import static ca.vina.nexu.effectifs.domaine.PresenceEffectifs.TOLERANCE_MAX_METRES;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * <strong>Présence (lot R8) — le domaine pur</strong>, jumeau de {@code effectifs-presence-domaine.test.ts} : séquence
 * des pointages, code QR de lieu, jugement des preuves, segments de temps, saisie a posteriori, heure locale,
 * corroboration et quart pointé.
 */
@DisplayName("Présence des effectifs — domaine pur")
class PresenceEffectifsDomaineTest {

    private static final long T = Instant.parse("2031-03-10T12:00:00.000Z").toEpochMilli();

    private static Map<String, Object> carte(Object... kv) {
        Map<String, Object> m = new HashMap<>();
        for (int i = 0; i < kv.length; i += 2) {
            m.put((String) kv[i], kv[i + 1]);
        }
        return m;
    }

    @Nested
    @DisplayName("séquence")
    class Sequence {

        @Test
        @DisplayName("valide le sens et nomme les valeurs attendues")
        void sens() {
            assertThat(PresenceEffectifs.sensDe("ENTREE")).isEqualTo(Sens.ENTREE);
            assertThatThrownBy(() -> PresenceEffectifs.sensDe("SIESTE")).isInstanceOf(EffectifsInvalideException.class)
                    .hasMessage("« sens » : ENTREE, DEBUT_PAUSE, FIN_PAUSE, SORTIE");
        }

        @Test
        @DisplayName("entre, prend des pauses, sort ; rien après la sortie")
        void suivants() {
            assertThat(PresenceEffectifs.sensPermis(null)).containsExactly(Sens.ENTREE);
            assertThat(PresenceEffectifs.sensPermis(Sens.ENTREE)).containsExactly(Sens.DEBUT_PAUSE, Sens.SORTIE);
            assertThat(PresenceEffectifs.sensPermis(Sens.DEBUT_PAUSE)).containsExactly(Sens.FIN_PAUSE);
            assertThat(PresenceEffectifs.sensPermis(Sens.FIN_PAUSE)).containsExactly(Sens.DEBUT_PAUSE, Sens.SORTIE);
            assertThat(PresenceEffectifs.sensPermis(Sens.SORTIE)).isEmpty();
            assertThatNoException().isThrownBy(() -> PresenceEffectifs.verifierSequence(null, Sens.ENTREE));
            assertThatThrownBy(() -> PresenceEffectifs.verifierSequence(Sens.ENTREE, Sens.FIN_PAUSE))
                    .hasMessage("après l'entrée, on ne peut pointer que le début de pause ou la sortie");
            assertThatThrownBy(() -> PresenceEffectifs.verifierSequence(null, Sens.SORTIE)).hasMessage("après aucun pointage, on ne peut pointer que l'entrée");
            assertThatThrownBy(() -> PresenceEffectifs.verifierSequence(Sens.SORTIE, Sens.ENTREE))
                    .hasMessage("après la sortie, on ne peut pointer que rien : le quart est terminé");
        }

        @Test
        @DisplayName("statuts, origines et décisions portent leur sens")
        void enumerations() {
            assertThat(PresenceEffectifs.StatutPointage.REJETE.estActif()).isFalse();
            assertThat(PresenceEffectifs.StatutPointage.A_APPROUVER.estActif()).isTrue();
            assertThat(PresenceEffectifs.OriginePointage.SAISIE.actionJournal()).isEqualTo("presence.saisie");
            assertThat(PresenceEffectifs.OriginePointage.APPAREIL.actionJournal()).isEqualTo("presence.pointage");
            assertThat(PresenceEffectifs.DecisionPointage.REJETER.vers()).isEqualTo(PresenceEffectifs.StatutPointage.REJETE);
            assertThat(PresenceEffectifs.DecisionPointage.APPROUVER.actionJournal()).isEqualTo("presence.approuver");
        }
    }

    @Nested
    @DisplayName("code QR de lieu")
    class CodeQr {

        @Test
        @DisplayName("change à chaque fenêtre de cinq minutes et accepte la précédente, pas l'antépénultième")
        void fenetres() {
            String c = PresenceEffectifs.codeQr("s", "m1", T);
            assertThat(c).matches("^m1\\.\\d+\\.[A-Za-z0-9_-]{16}$");
            assertThat(PresenceEffectifs.qrValide("s", "m1", c, T)).isEqualTo(VerdictQr.VALIDE);
            assertThat(PresenceEffectifs.qrValide("s", "m1", c, T + FENETRE_QR_MS)).isEqualTo(VerdictQr.VALIDE);
            assertThat(PresenceEffectifs.qrValide("s", "m1", c, T + 2 * FENETRE_QR_MS)).isEqualTo(VerdictQr.INVALIDE);
            assertThat(PresenceEffectifs.qrValide("autre-secret", "m1", c, T)).isEqualTo(VerdictQr.INVALIDE);
            assertThat(PresenceEffectifs.qrValide("s", "m2", c, T)).isEqualTo(VerdictQr.AUTRE_MILIEU);
            assertThat(PresenceEffectifs.qrValide("s", "m1", "", T)).isEqualTo(VerdictQr.INVALIDE);
        }

        @Test
        @DisplayName("l'empreinte est celle du Node (HMAC-SHA256, base64url, 16 caractères) et la fenêtre finit à la suivante")
        void empreinte() {
            assertThat(PresenceEffectifs.codeQr("s", "m1", 0)).isEqualTo("m1.0.wDHQd31Cac85Z-6h");
            assertThat(PresenceEffectifs.finDeFenetre(T + 1)).isEqualTo("2031-03-10T12:05:00.000Z");
        }
    }

    @Nested
    @DisplayName("soumission et preuves")
    class Preuves {

        private final Milieux.Preuve geo = new Milieux.Preuve(Mecanisme.GEOREPERAGE, true, Map.of());
        private final Milieux.Preuve qr = new Milieux.Preuve(Mecanisme.CODE_QR_DE_LIEU, false, Map.of());
        private final Milieux.Preuve net = new Milieux.Preuve(Mecanisme.RESEAU, false, Map.of("plages", List.of("10.1.0.0/16")));
        private final Milieux.Preuve borne = new Milieux.Preuve(Mecanisme.BORNE, false, Map.of());

        private MilieuPreuves milieu(List<Milieux.Preuve> preuves, boolean avecCoord, String secret) {
            return new MilieuPreuves("m1", avecCoord ? new Milieux.Coordonnees(45.5, -73.6, 100) : null, preuves, secret);
        }

        private MilieuPreuves milieu(Milieux.Preuve... preuves) {
            return milieu(List.of(preuves), true, "s");
        }

        private Soumission position(double lat, double lon, Double precision) {
            return new Soumission(lat, lon, precision, null, null);
        }

        private final Soumission rien = new Soumission(null, null, null, null, null);

        @Test
        @DisplayName("lit la soumission : position par paire, bornée ; code QR nettoyé")
        void soumission() {
            assertThat(PresenceEffectifs.soumissionDe(carte("latitude", "45.5", "longitude", -73.6, "precisionMetres", 12, "codeQr", " abc "), "10.0.0.1"))
                    .isEqualTo(new Soumission(45.5, -73.6, 12.0, "abc", "10.0.0.1"));
            assertThat(PresenceEffectifs.soumissionDe(carte("latitude", "", "longitude", null, "codeQr", "  "), null)).isEqualTo(rien);
            assertThat(PresenceEffectifs.soumissionDe(carte("codeQr", 12), null)).isEqualTo(rien);
            assertThatThrownBy(() -> PresenceEffectifs.soumissionDe(carte("latitude", 45), null)).hasMessage("latitude et longitude se transmettent ensemble");
            assertThatThrownBy(() -> PresenceEffectifs.soumissionDe(carte("longitude", 45), null)).hasMessage("latitude et longitude se transmettent ensemble");
            assertThatThrownBy(() -> PresenceEffectifs.soumissionDe(carte("latitude", 91, "longitude", 0), null)).hasMessage("position invalide");
            assertThatThrownBy(() -> PresenceEffectifs.soumissionDe(carte("latitude", 0, "longitude", 181), null)).hasMessage("position invalide");
            assertThatThrownBy(() -> PresenceEffectifs.soumissionDe(carte("latitude", "x", "longitude", 0), null)).hasMessage("position invalide");
            assertThatThrownBy(() -> PresenceEffectifs.soumissionDe(carte("latitude", 0, "longitude", "x"), null)).hasMessage("position invalide");
        }

        @Test
        @DisplayName("un nombre se lit comme Number() du Node")
        void nombres() {
            assertThat(PresenceEffectifs.nombre(true)).isEqualTo(1.0);
            assertThat(PresenceEffectifs.nombre(false)).isEqualTo(0.0);
            assertThat(PresenceEffectifs.nombre("  ")).isEqualTo(0.0);
            assertThat(PresenceEffectifs.nombre(" 7 ")).isEqualTo(7.0);
            assertThat(PresenceEffectifs.nombre(List.of())).isNaN();
        }

        @Test
        @DisplayName("géorepérage : la distance décide, la précision annoncée tolère, plafonnée")
        void georeperage() {
            VerdictPreuve pres = PresenceEffectifs.jugerPreuves(milieu(geo), position(45.5, -73.6, null), T).getFirst();
            assertThat(pres).isEqualTo(new VerdictPreuve(Mecanisme.GEOREPERAGE, true, true, true, 0L, null));
            assertThat(PresenceEffectifs.jugerPreuves(milieu(geo), position(45.502, -73.6, 500.0), T).getFirst().satisfaite()).isFalse();
            assertThat(PresenceEffectifs.jugerPreuves(milieu(geo), position(45.5017, -73.6, (double) TOLERANCE_MAX_METRES), T).getFirst().satisfaite()).isTrue();
            assertThat(PresenceEffectifs.jugerPreuves(milieu(geo), position(45.5017, -73.6, -50.0), T).getFirst().satisfaite()).isFalse();
            assertThat(PresenceEffectifs.jugerPreuves(milieu(geo), rien, T).getFirst()).isEqualTo(new VerdictPreuve(Mecanisme.GEOREPERAGE, true, false, false, null, null));
            assertThat(PresenceEffectifs.jugerPreuves(milieu(List.of(geo), false, "s"), position(45.5, -73.6, null), T).getFirst().fournie()).isFalse();
        }

        @Test
        @DisplayName("code QR : absent, sans secret, valide, d'un autre milieu")
        void codeQr() {
            assertThat(PresenceEffectifs.jugerPreuves(milieu(qr), rien, T).getFirst()).extracting(VerdictPreuve::fournie, VerdictPreuve::satisfaite).containsExactly(false, false);
            Soumission x = new Soumission(null, null, null, "x", null);
            assertThat(PresenceEffectifs.jugerPreuves(milieu(List.of(qr), true, null), x, T).getFirst()).extracting(VerdictPreuve::fournie, VerdictPreuve::satisfaite)
                    .containsExactly(true, false);
            Soumission bon = new Soumission(null, null, null, PresenceEffectifs.codeQr("s", "m1", T), null);
            assertThat(PresenceEffectifs.jugerPreuves(milieu(qr), bon, T).getFirst()).isEqualTo(new VerdictPreuve(Mecanisme.CODE_QR_DE_LIEU, false, true, true, null, VerdictQr.VALIDE));
            Soumission autre = new Soumission(null, null, null, PresenceEffectifs.codeQr("s", "m9", T), null);
            assertThat(PresenceEffectifs.jugerPreuves(milieu(qr), autre, T).getFirst().detail()).isEqualTo(VerdictQr.AUTRE_MILIEU);
        }

        @Test
        @DisplayName("réseau : l'adresse dans une plage déclarée ; sans plage, rien ne passe ; une IPv6 n'est pas une preuve")
        void reseau() {
            Soumission ip = new Soumission(null, null, null, null, "10.1.4.5");
            assertThat(PresenceEffectifs.jugerPreuves(milieu(net), ip, T).getFirst()).extracting(VerdictPreuve::fournie, VerdictPreuve::satisfaite).containsExactly(true, true);
            assertThat(PresenceEffectifs.jugerPreuves(milieu(net), new Soumission(null, null, null, null, "10.2.4.5"), T).getFirst().satisfaite()).isFalse();
            assertThat(PresenceEffectifs.jugerPreuves(milieu(new Milieux.Preuve(Mecanisme.RESEAU, false, Map.of())), ip, T).getFirst().satisfaite()).isFalse();
            assertThat(PresenceEffectifs.jugerPreuves(milieu(net), new Soumission(null, null, null, null, "::1"), T).getFirst().fournie()).isFalse();
            assertThat(PresenceEffectifs.jugerPreuves(milieu(net), rien, T).getFirst().fournie()).isFalse();
        }

        @Test
        @DisplayName("borne et source externe : la preuve arrive par un autre canal")
        void autreCanal() {
            Soumission ip = new Soumission(null, null, null, null, "10.1.0.1");
            assertThat(PresenceEffectifs.jugerPreuves(milieu(borne), ip, T).getFirst()).isEqualTo(new VerdictPreuve(Mecanisme.BORNE, false, false, false, null, null));
            Milieux.Preuve externe = new Milieux.Preuve(Mecanisme.SOURCE_EXTERNE, true, Map.of());
            assertThat(PresenceEffectifs.jugerPreuves(milieu(externe), ip, T).getFirst().fournie()).isFalse();
        }

        private VerdictPreuve v(Mecanisme m, boolean exigee, boolean fournie, boolean satisfaite, VerdictQr detail) {
            return new VerdictPreuve(m, exigee, fournie, satisfaite, null, detail);
        }

        @Test
        @DisplayName("résultat : conforme, écart nommé, ou non vérifié")
        void resultat() {
            Mecanisme g = Mecanisme.GEOREPERAGE;
            assertThat(PresenceEffectifs.resultatDe(List.of(v(Mecanisme.CODE_QR_DE_LIEU, false, true, false, VerdictQr.AUTRE_MILIEU))))
                    .isEqualTo(new Jugement(Resultat.ECART, Ecart.MAUVAIS_MILIEU));
            assertThat(PresenceEffectifs.resultatDe(List.of(v(g, true, false, false, null)))).isEqualTo(new Jugement(Resultat.ECART, Ecart.PREUVE_ABSENTE));
            assertThat(PresenceEffectifs.resultatDe(List.of(v(g, true, true, false, null)))).isEqualTo(new Jugement(Resultat.ECART, Ecart.HORS_MILIEU));
            assertThat(PresenceEffectifs.resultatDe(List.of(v(g, true, true, true, null)))).isEqualTo(new Jugement(Resultat.CONFORME, null));
            assertThat(PresenceEffectifs.resultatDe(List.of(v(g, false, false, false, null)))).isEqualTo(new Jugement(Resultat.NON_VERIFIE, null));
            assertThat(PresenceEffectifs.resultatDe(List.of())).isEqualTo(new Jugement(Resultat.NON_VERIFIE, null));
            assertThat(PresenceEffectifs.resultatDe(List.of(v(g, false, true, true, null), v(Mecanisme.RESEAU, false, true, false, null))))
                    .isEqualTo(new Jugement(Resultat.CONFORME, null));
            assertThat(PresenceEffectifs.resultatDe(List.of(v(g, false, true, false, null)))).isEqualTo(new Jugement(Resultat.ECART, Ecart.HORS_MILIEU));
        }

        @Test
        @DisplayName("les faits du pointage : écart au début ou à la fin prévue, distance (-1 sans position), mécanisme satisfait")
        void faits() {
            QuartPointe q = new QuartPointe("pl", "m1", "C12", "EST", "REG", "2031-01-15", 360, 1080, "2031-01-15T11:00:00.000Z", "2031-01-15T23:00:00.000Z", 15);
            List<VerdictPreuve> geoOk = List.of(v(Mecanisme.CODE_QR_DE_LIEU, false, false, false, null), new VerdictPreuve(Mecanisme.GEOREPERAGE, true, true, true, 12L, null));
            assertThat(PresenceEffectifs.faitsDuPointage(q, Sens.ENTREE, "2031-01-15T11:20:00.000Z", geoOk))
                    .containsExactly(Map.entry("ecartDebutMinutes", 20L), Map.entry("distanceMetres", 12L), Map.entry("mecanisme", "GEOREPERAGE"),
                            Map.entry("preuveExigeePresente", true));
            List<VerdictPreuve> absent = List.of(v(Mecanisme.GEOREPERAGE, true, false, false, null));
            assertThat(PresenceEffectifs.faitsDuPointage(q, Sens.SORTIE, "2031-01-15T22:50:00.000Z", absent))
                    .containsExactly(Map.entry("ecartDebutMinutes", -10L), Map.entry("distanceMetres", -1L), Map.entry("mecanisme", "AUCUN"),
                            Map.entry("preuveExigeePresente", false));
            assertThat(PresenceEffectifs.faitsDuPointage(q, Sens.DEBUT_PAUSE, "2031-01-15T16:00:00.000Z", List.of())).containsEntry("ecartDebutMinutes", 0L);
        }
    }

    @Nested
    @DisplayName("segments de temps")
    class Segments {

        private static final String DEBUT = "2031-03-10T11:00:00.000Z";

        private PointageCourt p(Sens sens, String h) {
            return new PointageCourt(sens, h);
        }

        @Test
        @DisplayName("entrée, pause, reprise, sortie : la pause n'est pas payée")
        void pause() {
            List<Segment> s = PresenceEffectifs.segmentsDe(List.of(p(Sens.SORTIE, "2031-03-10T19:00:00.000Z"), p(Sens.ENTREE, DEBUT),
                    p(Sens.DEBUT_PAUSE, "2031-03-10T15:00:00.000Z"), p(Sens.FIN_PAUSE, "2031-03-10T15:30:00.000Z")), DEBUT, 0, "2031-03-10T20:00:00.000Z");
            assertThat(s).extracting(Segment::type, Segment::minutes)
                    .containsExactly(org.assertj.core.groups.Tuple.tuple(TypeSegment.TRAVAIL, 240L), org.assertj.core.groups.Tuple.tuple(TypeSegment.PAUSE, 30L),
                            org.assertj.core.groups.Tuple.tuple(TypeSegment.TRAVAIL, 210L));
            assertThat(PresenceEffectifs.minutesPayees(s)).isEqualTo(450);
        }

        @Test
        @DisplayName("présence préalable : comptée dans sa limite, jamais au-delà ; sans elle, une entrée en avance part du début prévu")
        void prealable() {
            List<Segment> tot = PresenceEffectifs.segmentsDe(List.of(p(Sens.ENTREE, "2031-03-10T10:30:00.000Z")), DEBUT, 15, "2031-03-10T12:00:00.000Z");
            assertThat(tot).containsExactly(new Segment("2031-03-10T10:45:00.000Z", DEBUT, TypeSegment.PRESENCE_PREALABLE, 15, null, null),
                    new Segment(DEBUT, null, TypeSegment.TRAVAIL, 60, null, null));
            List<Segment> juste = PresenceEffectifs.segmentsDe(List.of(p(Sens.ENTREE, "2031-03-10T10:50:00.000Z")), DEBUT, 15, DEBUT);
            assertThat(juste.getFirst()).isEqualTo(new Segment("2031-03-10T10:50:00.000Z", DEBUT, TypeSegment.PRESENCE_PREALABLE, 10, null, null));
            assertThat(PresenceEffectifs.segmentsDe(List.of(p(Sens.ENTREE, "2031-03-10T10:30:00.000Z")), DEBUT, 0, "2031-03-10T12:00:00.000Z"))
                    .containsExactly(new Segment(DEBUT, null, TypeSegment.TRAVAIL, 60, null, null));
        }

        @Test
        @DisplayName("sans début prévu, l'entrée ouvre le travail ; un retard compte de l'entrée ; aucun pointage, aucun segment")
        void bords() {
            assertThat(PresenceEffectifs.segmentsDe(List.of(p(Sens.ENTREE, "2031-03-10T11:10:00.000Z")), null, 15, "2031-03-10T11:40:00.000Z").getFirst())
                    .isEqualTo(new Segment("2031-03-10T11:10:00.000Z", null, TypeSegment.TRAVAIL, 30, null, null));
            assertThat(PresenceEffectifs.segmentsDe(List.of(p(Sens.ENTREE, "2031-03-10T11:10:00.000Z")), DEBUT, 15, "2031-03-10T11:40:00.000Z").getFirst().minutes())
                    .isEqualTo(30);
            assertThat(PresenceEffectifs.segmentsDe(List.of(), DEBUT, 0, DEBUT)).isEmpty();
            assertThat(PresenceEffectifs.segmentsDe(List.of(p(Sens.SORTIE, DEBUT)), DEBUT, 0, DEBUT)).isEmpty();
            List<Segment> deuxSorties = PresenceEffectifs.segmentsDe(List.of(p(Sens.ENTREE, DEBUT), p(Sens.SORTIE, "2031-03-10T12:00:00.000Z"),
                    p(Sens.SORTIE, "2031-03-10T13:00:00.000Z")), DEBUT, 0, "2031-03-10T14:00:00.000Z");
            assertThat(deuxSorties).containsExactly(new Segment(DEBUT, "2031-03-10T12:00:00.000Z", TypeSegment.TRAVAIL, 60, null, null));
        }
    }

    @Nested
    @DisplayName("saisie a posteriori")
    class Saisie {

        private Map<String, Object> seg(String debut, String fin, String type) {
            return carte("debut", debut, "fin", fin, "type", type);
        }

        private Map<String, Object> seg(String debut, String fin) {
            return seg(debut, fin, "TRAVAIL");
        }

        @Test
        @DisplayName("valide, normalise et trie les segments")
        void valide() {
            assertThat(PresenceEffectifs.segmentsSaisis(List.of(seg("2031-03-10T15:00:00Z", "2031-03-10T15:30:00Z", "PAUSE"), seg("2031-03-10T11:00:00Z", "2031-03-10T15:00:00Z"))))
                    .containsExactly(new SegmentSaisi("2031-03-10T11:00:00.000Z", "2031-03-10T15:00:00.000Z", TypeSegment.TRAVAIL),
                            new SegmentSaisi("2031-03-10T15:00:00.000Z", "2031-03-10T15:30:00.000Z", TypeSegment.PAUSE, NaturePause.PAUSE, null));
            assertThatThrownBy(() -> PresenceEffectifs.segmentsSaisis(List.of())).hasMessage("« segments » : une liste non vide de { debut, fin, type }");
            assertThatThrownBy(() -> PresenceEffectifs.segmentsSaisis("x")).hasMessageStartingWith("« segments » : une liste non vide");
            assertThatThrownBy(() -> PresenceEffectifs.segmentsSaisis(Collections.nCopies(21, seg("2031-03-10T11:00:00Z", "2031-03-10T12:00:00Z"))))
                    .hasMessage("vingt segments au plus par saisie");
            assertThatThrownBy(() -> PresenceEffectifs.segmentsSaisis(List.of(seg("x", "2031-03-10T12:00:00Z"))))
                    .hasMessage("segment 1 : début et fin sont des instants ISO 8601");
            assertThatThrownBy(() -> PresenceEffectifs.segmentsSaisis(List.of(seg("2031-03-10T12:00:00Z", "y")))).hasMessageStartingWith("segment 1 : début et fin");
            assertThatThrownBy(() -> PresenceEffectifs.segmentsSaisis(List.of(carte("debut", 1, "fin", 2, "type", "TRAVAIL")))).hasMessageStartingWith("segment 1 : début et fin");
            assertThatThrownBy(() -> PresenceEffectifs.segmentsSaisis(Collections.singletonList(null))).hasMessageStartingWith("segment 1 : début et fin");
            assertThatThrownBy(() -> PresenceEffectifs.segmentsSaisis(List.of(seg("2031-03-10T12:00:00Z", "2031-03-10T12:00:00Z"))))
                    .hasMessage("segment 1 : la fin doit suivre le début");
            assertThatThrownBy(() -> PresenceEffectifs.segmentsSaisis(List.of(seg("2031-03-10T11:00:00Z", "2031-03-10T12:00:00Z", "SIESTE"))))
                    .hasMessage("segment 1 : type TRAVAIL ou PAUSE");
            assertThatThrownBy(() -> PresenceEffectifs.segmentsSaisis(List.of(seg("2031-03-10T11:00:00Z", "2031-03-10T13:00:00Z"), seg("2031-03-10T12:00:00Z", "2031-03-10T14:00:00Z"))))
                    .hasMessage("deux segments se chevauchent : corrigez l'un des deux");
            assertThatThrownBy(() -> PresenceEffectifs.segmentsSaisis(List.of(seg("2031-03-10T00:00:00Z", "2031-03-11T00:00:01Z"))))
                    .hasMessage("une saisie couvre 24 heures au plus");
        }

        @Test
        @DisplayName("déduit les pointages : entrée, pauses, sortie ; refuse trous, doublons de type, bords en pause")
        void pointages() {
            List<SegmentSaisi> s = PresenceEffectifs.segmentsSaisis(List.of(seg("2031-03-10T11:00:00Z", "2031-03-10T15:00:00Z"),
                    seg("2031-03-10T15:00:00Z", "2031-03-10T15:30:00Z", "PAUSE"), seg("2031-03-10T15:30:00Z", "2031-03-10T19:00:00Z")));
            assertThat(PresenceEffectifs.pointagesDeSegments(s)).extracting(PointageCourt::sens).containsExactly(Sens.ENTREE, Sens.DEBUT_PAUSE, Sens.FIN_PAUSE, Sens.SORTIE);
            assertThatThrownBy(() -> PresenceEffectifs.pointagesDeSegments(PresenceEffectifs.segmentsSaisis(List.of(seg("2031-03-10T11:00:00Z", "2031-03-10T12:00:00Z", "PAUSE")))))
                    .hasMessage("une saisie commence et finit par du travail");
            assertThatThrownBy(() -> PresenceEffectifs.pointagesDeSegments(PresenceEffectifs.segmentsSaisis(List.of(seg("2031-03-10T11:00:00Z", "2031-03-10T12:00:00Z"),
                    seg("2031-03-10T12:00:00Z", "2031-03-10T13:00:00Z", "PAUSE"))))).hasMessage("une saisie commence et finit par du travail");
            assertThatThrownBy(() -> PresenceEffectifs.pointagesDeSegments(PresenceEffectifs.segmentsSaisis(List.of(seg("2031-03-10T11:00:00Z", "2031-03-10T12:00:00Z"),
                    seg("2031-03-10T12:30:00Z", "2031-03-10T13:00:00Z"))))).hasMessage("segment 2 : il doit commencer à la fin du précédent (un trou se déclare comme pause)");
            assertThatThrownBy(() -> PresenceEffectifs.pointagesDeSegments(PresenceEffectifs.segmentsSaisis(List.of(seg("2031-03-10T11:00:00Z", "2031-03-10T12:00:00Z"),
                    seg("2031-03-10T12:00:00Z", "2031-03-10T13:00:00Z"))))).hasMessage("segment 2 : travail et pause alternent");
        }
    }

    @Nested
    @DisplayName("heure locale, corroboration et quart pointé")
    class HeureLocale {

        @Test
        @DisplayName("convertit l'heure planifiée du Québec en UTC, heure normale comme avancée, au-delà de minuit")
        void instantLocal() {
            assertThat(PresenceEffectifs.instantLocal("2031-01-15", 360)).isEqualTo("2031-01-15T11:00:00.000Z");
            assertThat(PresenceEffectifs.instantLocal("2031-07-15", 360)).isEqualTo("2031-07-15T10:00:00.000Z");
            assertThat(PresenceEffectifs.instantLocal("2031-01-15", 1440 + 120)).isEqualTo("2031-01-16T07:00:00.000Z");
            assertThat(PresenceEffectifs.instantLocal("2031-01-15", 0, "UTC")).isEqualTo("2031-01-15T00:00:00.000Z");
        }

        @Test
        @DisplayName("mesure l'écart à l'heure prévue et corrobore à cinq minutes près, du même sens")
        void corroboration() {
            assertThat(PresenceEffectifs.ecartMinutes("2031-01-15T11:07:29.000Z", "2031-01-15T11:00:00.000Z")).isEqualTo(7);
            assertThat(PresenceEffectifs.ecartMinutes("2031-01-15T10:50:00.000Z", "2031-01-15T11:00:00.000Z")).isEqualTo(-10);
            PointageCourt p = new PointageCourt(Sens.ENTREE, "2031-01-15T11:00:00.000Z");
            assertThat(PresenceEffectifs.corrobore(p, List.of(new PointageCourt(Sens.ENTREE, "2031-01-15T11:05:00.000Z")))).isTrue();
            assertThat(PresenceEffectifs.corrobore(p, List.of(new PointageCourt(Sens.ENTREE, "2031-01-15T11:05:01.000Z")))).isFalse();
            assertThat(PresenceEffectifs.corrobore(p, List.of(new PointageCourt(Sens.SORTIE, "2031-01-15T11:00:00.000Z")))).isFalse();
            assertThat(PresenceEffectifs.corrobore(p, List.of())).isFalse();
        }

        @Test
        @DisplayName("le quart pointé : heures prévues en UTC, fenêtre élargie par la présence préalable, le plus proche de son début")
        void quart() {
            QuartPointe jour = PresenceEffectifs.quartPointe(new QuartPublie("p1", "m1", "C12", "EST", "REG", "2031-01-15", 360, 1080), 180);
            assertThat(jour).extracting(QuartPointe::debutPrevu, QuartPointe::finPrevue).containsExactly("2031-01-15T11:00:00.000Z", "2031-01-15T23:00:00.000Z");
            QuartPointe nuit = PresenceEffectifs.quartPointe(new QuartPublie("p2", "m1", "C12", "EST", "REG", "2031-01-15", 1080, 360), 0);
            assertThat(nuit.finPrevue()).isEqualTo("2031-01-16T11:00:00.000Z");
            long marge = 2 * 3_600_000L;
            assertThat(PresenceEffectifs.quartAutourDe(List.of(jour, nuit), "2031-01-15T08:30:00.000Z", marge)).contains(jour);
            assertThat(PresenceEffectifs.quartAutourDe(List.of(jour, nuit), "2031-01-15T07:30:00.000Z", marge)).isEmpty();
            assertThat(PresenceEffectifs.quartAutourDe(List.of(jour, nuit), "2031-01-16T00:30:00.000Z", marge)).contains(nuit);
            assertThat(PresenceEffectifs.quartAutourDe(List.of(jour), "2031-01-16T01:30:00.000Z", marge)).isEmpty();
            assertThat(PresenceEffectifs.quartAutourDe(List.of(nuit, jour), "2031-01-15T20:00:00.000Z", marge)).contains(jour);
        }
    }
}
