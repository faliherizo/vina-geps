package ca.vina.nexu.soutien.domaine;

import ca.vina.nexu.soutien.domaine.EngagementsDeService.Delai;
import ca.vina.nexu.soutien.domaine.EngagementsDeService.Engagement;
import ca.vina.nexu.soutien.domaine.EngagementsDeService.EtatDelai;
import ca.vina.nexu.soutien.domaine.EngagementsDeService.Jalons;
import ca.vina.nexu.soutien.domaine.EngagementsDeService.Manquement;
import ca.vina.nexu.soutien.domaine.EngagementsDeService.Politique;
import ca.vina.nexu.soutien.domaine.EngagementsDeService.Priorite;
import ca.vina.nexu.soutien.domaine.EngagementsDeService.Suivi;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalDouble;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Les engagements du contrat 26-015-DO (5.02, 6.01) contre ceux de la plateforme. En novembre,
 * Montréal est à UTC−5 : 10 h 00 UTC = 5 h 00, ouverture des heures de soutien.
 */
@DisplayName("Soutien — engagements de service par politique")
class EngagementsDeServiceTest {

    private static final Engagement STANDARD = EngagementsDeService.de(Politique.STANDARD);
    private static final Engagement ESSENTIEL = EngagementsDeService.de(Politique.SERVICE_ESSENTIEL);
    private static final Instant CINQ_HEURES = Instant.parse("2026-11-12T10:00:00Z");
    private static final Instant TROIS_HEURES = Instant.parse("2026-11-12T08:00:00Z");

    private static Jalons ouvert(Instant ouvert, Instant retour, Instant resolu) {
        return new Jalons(ouvert, Optional.ofNullable(retour), Optional.ofNullable(resolu));
    }

    @Nested
    class Catalogue {

        @Test
        void lesCodesSeLisentEtUnCodeInconnuEstRefuseEnLeNommant() {
            assertThat(Priorite.codes()).containsExactly("N1", "N2", "N3", "N4");
            assertThat(Priorite.de("N3")).contains(Priorite.N3);
            assertThat(Priorite.de("P1")).isEmpty();
            assertThat(Priorite.N2.code()).isEqualTo("N2");
            assertThat(Politique.de("service-essentiel")).isEqualTo(Politique.SERVICE_ESSENTIEL);
            assertThat(Politique.de("standard")).isEqualTo(Politique.STANDARD);
            assertThat(Politique.SERVICE_ESSENTIEL.libelle()).contains("5 h à 23 h");
            assertThatThrownBy(() -> Politique.de("or"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("politique de soutien inconnue : « or » (attendu : standard, service-essentiel, communications-cliniques)");
            assertThat(EtatDelai.MANQUE.code()).isEqualTo("manque");
            assertThat(Manquement.MANQUEMENT_MAJEUR.code()).isEqualTo("manquement_majeur");
        }

        @Test
        void leServiceEssentielReprendLaClause502MotPourMot() {
            assertThat(ESSENTIEL.clause(Priorite.N1).resolution()).isEqualTo(Duration.ofHours(2));
            assertThat(ESSENTIEL.clause(Priorite.N2).resolution()).isEqualTo(Duration.ofHours(6));
            assertThat(ESSENTIEL.clause(Priorite.N3).resolution()).isEqualTo(Duration.ofHours(48));
            assertThat(ESSENTIEL.clause(Priorite.N4).resolution()).isEqualTo(Duration.ofDays(7));
            assertThat(ESSENTIEL.clause(Priorite.N2).reponse().orElseThrow().enHeures()).isEqualTo(Duration.ofMinutes(30));
            assertThat(ESSENTIEL.clause(Priorite.N3).reponse().orElseThrow().horsHeures()).isEqualTo(Duration.ofMinutes(120));
            assertThat(ESSENTIEL.objectifPourcent()).isEqualTo(99.7);
            assertThat(STANDARD.clause(Priorite.N1).reponse()).isEmpty();
            assertThat(STANDARD.heures()).isEmpty();
        }

        @Test
        void lesHeuresDeSoutienVontDeCinqHeuresInclusesAVingtTroisHeuresExclues() {
            EngagementsDeService.HeuresDeSoutien h = ESSENTIEL.heures().orElseThrow();
            assertThat(h.couvre(CINQ_HEURES.minusSeconds(60))).isFalse();
            assertThat(h.couvre(CINQ_HEURES)).isTrue();
            assertThat(h.couvre(Instant.parse("2026-11-13T03:59:00Z"))).isTrue();
            assertThat(h.couvre(Instant.parse("2026-11-13T04:00:00Z"))).isFalse();
        }
    }

    @Nested
    class Suivre {

        @Test
        void laPolitiqueStandardNePrometQueLaResolutionDepuisLOuverture() {
            Suivi s = EngagementsDeService.suivre(STANDARD, Priorite.N1, ouvert(CINQ_HEURES, null, null), CINQ_HEURES);
            assertThat(s.politique()).isEqualTo(Politique.STANDARD);
            assertThat(s.reponse()).isEmpty();
            assertThat(s.echeanceResolution()).isEqualTo(CINQ_HEURES.plusSeconds(3600));
            assertThat(s.enRetard(CINQ_HEURES.plusSeconds(3601), Optional.empty())).isTrue();
            assertThat(s.enRetard(CINQ_HEURES.plusSeconds(3601), Optional.of(CINQ_HEURES))).isFalse();
            assertThat(s.enRetard(CINQ_HEURES, Optional.empty())).isFalse();
        }

        @Test
        void ouvertEnHeuresLeN1AttendUnRappelEnTrenteMinutesPuisDeuxHeuresDeResolution() {
            Suivi s = EngagementsDeService.suivre(ESSENTIEL, Priorite.N1, ouvert(CINQ_HEURES, null, null), CINQ_HEURES.plusSeconds(600));
            Delai r = s.reponse().orElseThrow();
            assertThat(r.echeance()).isEqualTo(CINQ_HEURES.plusSeconds(1800));
            assertThat(r.etat()).isEqualTo(EtatDelai.DANS_LES_DELAIS);
            assertThat(r.ecartMinutes()).isEqualTo(20);
            assertThat(r.enRetard()).isFalse();
            // Sans rappel, la résolution se compte depuis la dernière minute admise pour rappeler.
            assertThat(s.echeanceResolution()).isEqualTo(CINQ_HEURES.plusSeconds(1800 + 7200));
        }

        @Test
        void ouvertHorsHeuresLeDelaiDeReponseEstLeLong() {
            Suivi s = EngagementsDeService.suivre(ESSENTIEL, Priorite.N3, ouvert(TROIS_HEURES, null, null), TROIS_HEURES);
            assertThat(s.reponse().orElseThrow().echeance()).isEqualTo(TROIS_HEURES.plus(Duration.ofMinutes(120)));
        }

        @Test
        void unRappelATempsTientLaReponseEtLaResolutionPartDuRappel() {
            Instant rappel = CINQ_HEURES.plus(Duration.ofMinutes(25));
            Suivi s = EngagementsDeService.suivre(ESSENTIEL, Priorite.N2, ouvert(CINQ_HEURES, rappel, null), CINQ_HEURES.plus(Duration.ofHours(1)));
            assertThat(s.reponse().orElseThrow().etat()).isEqualTo(EtatDelai.TENU);
            assertThat(s.reponse().orElseThrow().atteintLe()).contains(rappel);
            assertThat(s.reponse().orElseThrow().ecartMinutes()).isEqualTo(5);
            assertThat(s.echeanceResolution()).isEqualTo(rappel.plus(Duration.ofHours(6)));
            assertThat(s.enRetard(CINQ_HEURES.plus(Duration.ofHours(1)), Optional.empty())).isFalse();
        }

        @Test
        void unRappelTardifEstUnDelaiManqueMemeUneFoisLeBilletResolu() {
            Instant rappel = CINQ_HEURES.plus(Duration.ofMinutes(45));
            Instant resolu = CINQ_HEURES.plus(Duration.ofHours(1));
            Suivi s = EngagementsDeService.suivre(ESSENTIEL, Priorite.N1, ouvert(CINQ_HEURES, rappel, resolu), resolu);
            assertThat(s.reponse().orElseThrow().etat()).isEqualTo(EtatDelai.MANQUE);
            assertThat(s.reponse().orElseThrow().ecartMinutes()).isEqualTo(-15);
            assertThat(s.reponse().orElseThrow().enRetard()).isTrue();
            assertThat(s.enRetard(resolu, Optional.of(resolu))).isTrue();
        }

        @Test
        void sansRappelApresLEcheanceLaReponseEstEnRetard() {
            Instant maintenant = CINQ_HEURES.plus(Duration.ofMinutes(31));
            Suivi s = EngagementsDeService.suivre(ESSENTIEL, Priorite.N1, ouvert(CINQ_HEURES, null, null), maintenant);
            assertThat(s.reponse().orElseThrow().etat()).isEqualTo(EtatDelai.EN_RETARD);
            assertThat(s.reponse().orElseThrow().ecartMinutes()).isEqualTo(-1);
            assertThat(s.enRetard(maintenant, Optional.empty())).isTrue();
        }

        @Test
        void unDelaiDeReponseSansHeuresDeSoutienPrendLeDelaiCourt() {
            Engagement sansHeures = new Engagement(Politique.SERVICE_ESSENTIEL, Map.of(Priorite.N1, ESSENTIEL.clause(Priorite.N1)), Optional.empty(), 99.7, OptionalDouble.empty());
            Suivi s = EngagementsDeService.suivre(sansHeures, Priorite.N1, ouvert(TROIS_HEURES, null, null), TROIS_HEURES);
            assertThat(s.reponse().orElseThrow().echeance()).isEqualTo(TROIS_HEURES.plus(Duration.ofMinutes(30)));
        }
    }

    @Nested
    class Disponibilite {

        @Test
        void sousLObjectifCEstUnManquementSousLeSeuilMajeurUnManquementMajeur() {
            assertThat(EngagementsDeService.manquementDe(ESSENTIEL, 99.7)).isEqualTo(Manquement.AUCUN);
            assertThat(EngagementsDeService.manquementDe(ESSENTIEL, 99.69)).isEqualTo(Manquement.MANQUEMENT);
            assertThat(EngagementsDeService.manquementDe(ESSENTIEL, 99.0)).isEqualTo(Manquement.MANQUEMENT);
            assertThat(EngagementsDeService.manquementDe(ESSENTIEL, 98.99)).isEqualTo(Manquement.MANQUEMENT_MAJEUR);
            // La politique standard n'a pas de seuil majeur : tout écart reste un manquement simple.
            assertThat(EngagementsDeService.manquementDe(STANDARD, 50.0)).isEqualTo(Manquement.MANQUEMENT);
            assertThat(EngagementsDeService.manquementDe(STANDARD, 99.5)).isEqualTo(Manquement.AUCUN);
        }
    }

    @Nested
    @DisplayName("communications cliniques — FX-AO-20260810, grille 98 et contrat 10.19.07 (ST-H05)")
    class CommunicationsCliniques {

        private final Engagement cliniques = EngagementsDeService.de(Politique.COMMUNICATIONS_CLINIQUES);

        @Test
        @DisplayName("une réponse en 4 h à tous les niveaux, en tout temps ; la résolution de la politique standard")
        void reponseEnQuatreHeures() {
            assertThat(Politique.de("communications-cliniques")).isEqualTo(Politique.COMMUNICATIONS_CLINIQUES);
            assertThat(Politique.COMMUNICATIONS_CLINIQUES.libelle()).contains("4 h", "95 %");
            assertThat(cliniques.heures()).isEmpty();
            for (Priorite p : Priorite.values()) {
                assertThat(cliniques.clause(p).reponse()).contains(new EngagementsDeService.Reponse(Duration.ofHours(4), Duration.ofHours(4)));
                assertThat(cliniques.clause(p).resolution()).isEqualTo(STANDARD.clause(p).resolution());
                assertThat(cliniques.clause(p).description()).isEqualTo(STANDARD.clause(p).description());
            }
            // Un billet N4 ouvert un dimanche à 3 h : la réponse est due à 7 h, quelle que soit l'heure.
            Instant dimancheNuit = Instant.parse("2026-11-22T08:00:00Z");
            Suivi s = EngagementsDeService.suivre(cliniques, Priorite.N4, new Jalons(dimancheNuit, Optional.empty(), Optional.empty()), dimancheNuit);
            assertThat(s.reponse().orElseThrow().echeance()).isEqualTo(dimancheNuit.plus(Duration.ofHours(4)));
        }

        @Test
        @DisplayName("95 % : au-dessus, aucun manquement ; au-dessous, un manquement, jamais « majeur » — le contrat n'en définit pas")
        void quatreVingtQuinze() {
            assertThat(cliniques.objectifPourcent()).isEqualTo(95.0);
            assertThat(cliniques.seuilManquementMajeurPourcent()).isEmpty();
            assertThat(EngagementsDeService.manquementDe(cliniques, 95.0)).isEqualTo(Manquement.AUCUN);
            assertThat(EngagementsDeService.manquementDe(cliniques, 94.9)).isEqualTo(Manquement.MANQUEMENT);
            assertThat(EngagementsDeService.manquementDe(cliniques, 50.0)).isEqualTo(Manquement.MANQUEMENT);
        }
    }
}
