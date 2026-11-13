package ca.vina.nexu.soutien.domaine;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Port de `soutien-domaine.test.ts` : échéance par niveau, état du délai, préavis de 72 h, disponibilité hors maintenance. */
@DisplayName("Soutien — domaine")
class SoutienTest {

    private static final List<EngagementsDeService.Engagement> STANDARD = List.of(EngagementsDeService.de(EngagementsDeService.Politique.STANDARD));

    private static Map<String, Object> champs(Object... kv) {
        Map<String, Object> m = new HashMap<>();
        for (int i = 0; i < kv.length; i += 2) {
            m.put((String) kv[i], kv[i + 1]);
        }
        return m;
    }

    @Test
    void laPolitiqueDitLesDelaisDeLaGrille() {
        Soutien.Politique p = Soutien.politique(EngagementsDeService.de(EngagementsDeService.Politique.STANDARD));
        assertThat(p.langue()).isEqualTo("fr");
        assertThat(p.niveaux().stream().map(n -> n.code() + "=" + n.delaiResolutionMinutes())).containsExactly("N1=60", "N2=120", "N3=240", "N4=1440");
        assertThat(p.objectifDisponibilitePourcent()).isEqualTo(99.5);
        assertThat(p.preavisMaintenanceHeures()).isEqualTo(72);
        p.niveaux().forEach(n -> assertThat(n.description().length()).isGreaterThan(10));
        assertThat(Soutien.echeanceDe("N1", "2026-09-12T10:00:00.000Z")).isEqualTo("2026-09-12T11:00:00.000Z");
        assertThat(Soutien.echeanceDe("N4", "2026-09-12T10:00:00.000Z")).isEqualTo("2026-09-13T10:00:00.000Z");
        assertThatThrownBy(() -> Soutien.echeanceDe("P1", "2026-09-12T10:00:00.000Z")).isInstanceOf(IllegalArgumentException.class).hasMessage("niveau inconnu : P1");
        assertThat(p.code()).isEqualTo("standard");
        assertThat(p.heuresDeSoutien()).isNull();
        assertThat(p.seuilManquementMajeurPourcent()).isNull();
        assertThat(p.niveaux().get(0).delaiReponseEnHeuresMinutes()).isNull();
        assertThat(Soutien.descriptionNiveau("N1")).startsWith("Service interrompu");
        assertThat(Soutien.descriptionNiveau("P9")).isEqualTo("P9");
    }

    @Test
    void laPolitiqueDuServiceEssentielDitLesDeuxDelaisLesHeuresEtLeSeuilMajeur() {
        Soutien.Politique p = Soutien.politique(EngagementsDeService.de(EngagementsDeService.Politique.SERVICE_ESSENTIEL));
        assertThat(p.code()).isEqualTo("service-essentiel");
        assertThat(p.objectifDisponibilitePourcent()).isEqualTo(99.7);
        assertThat(p.seuilManquementMajeurPourcent()).isEqualTo(99.0);
        assertThat(p.heuresDeSoutien()).isEqualTo(new Soutien.Heures("05:00", "23:00", "America/Montreal"));
        assertThat(p.niveaux().stream().map(n -> n.code() + "=" + n.delaiReponseEnHeuresMinutes() + "/" + n.delaiReponseHorsHeuresMinutes() + "/" + n.delaiResolutionMinutes()))
                .containsExactly("N1=30/60/120", "N2=30/60/360", "N3=60/120/2880", "N4=60/120/10080");
    }

    @Test
    void lEtatDuDelaiOuvertOuResoluJugeALaResolution() {
        String e = "2026-09-12T11:00:00.000Z";
        assertThat(Soutien.etatSla(e, null, "2026-09-12T10:30:00.000Z")).isEqualTo(new Soutien.Sla("dans_les_delais", 30));
        assertThat(Soutien.etatSla(e, null, "2026-09-12T11:10:00.000Z")).isEqualTo(new Soutien.Sla("en_retard", -10));
        assertThat(Soutien.etatSla(e, "2026-09-12T10:59:00.000Z", "2026-09-13T00:00:00.000Z")).isEqualTo(new Soutien.Sla("resolu_a_temps", 1));
        assertThat(Soutien.etatSla(e, "2026-09-12T12:00:00.000Z", "2026-09-12T12:00:00.000Z")).isEqualTo(new Soutien.Sla("resolu_en_retard", -60));
    }

    @Test
    void unBilletAUnSujetUneDescriptionEtUnNiveauEtLesTransitionsSontBornees() {
        assertThat(Soutien.validerTicket(champs("sujet", "Export Excel vide", "description", "Le classeur des statistiques n'a aucune ligne.", "niveau", "N3"))).isEmpty();
        assertThat(Soutien.validerTicket(champs("sujet", "abc", "description", "court", "niveau", "P1"))).containsExactly(
                "sujet requis (5 caractères au moins)", "description requise (10 caractères au moins) : dites ce qui se passe et ce qui était attendu", "niveau requis : N1, N2, N3, N4");
        assertThat(Soutien.validerTicket(champs("sujet", "x".repeat(201), "description", "y".repeat(5001), "niveau", "N1")))
                .containsExactly("sujet trop long (200 caractères au plus)", "description trop longue (5000 caractères au plus)");
        assertThat(Soutien.validerTicket(Map.of())).hasSize(3);
        assertThat(Soutien.transitionPermise("nouveau", "en_cours")).isTrue();
        assertThat(Soutien.transitionPermise("nouveau", "resolu")).isTrue();
        assertThat(Soutien.transitionPermise("en_cours", "resolu")).isTrue();
        assertThat(Soutien.transitionPermise("resolu", "en_cours")).isTrue();
        assertThat(Soutien.transitionPermise("resolu", "ferme")).isTrue();
        assertThat(Soutien.transitionPermise("ferme", "en_cours")).isFalse();
        assertThat(Soutien.transitionPermise("nouveau", "ferme")).isFalse();
        assertThat(Soutien.transitionPermise("inconnu", "ferme")).isFalse();
    }

    @Test
    void uneMaintenanceSAnnonce72hDAvanceFinitApresSonDebutEtNeDepassePas12h() {
        String now = "2026-09-12T10:00:00.000Z";
        assertThat(Soutien.validerMaintenance(champs("debut", "2026-09-16T02:00:00.000Z", "fin", "2026-09-16T04:00:00.000Z", "description", "Mise à jour de la base de données"), now)).isEmpty();
        assertThat(Soutien.validerMaintenance(champs("debut", "2026-09-16T02:00", "fin", "2026-09-16T04:00:00", "description", "Mise à jour de la base de données"), now)).isEmpty();
        assertThat(Soutien.validerMaintenance(champs("debut", "2026-09-16T02:00:00+00:00", "fin", "2026-09-16T04:00:00.000Z", "description", "Mise à jour de la base de données"), now)).isEmpty();
        assertThat(Soutien.validerMaintenance(champs("debut", "2026-09-13T02:00:00.000Z", "fin", "2026-09-13T04:00:00.000Z", "description", "Mise à jour de la base de données"), now))
                .containsExactly("préavis insuffisant : 72 h au moins avant le début (il en reste 16)");
        assertThat(Soutien.validerMaintenance(champs("debut", "2026-09-16T04:00:00.000Z", "fin", "2026-09-16T02:00:00.000Z", "description", "Mise à jour de la base de données"), now))
                .containsExactly("la fin doit suivre le début");
        assertThat(Soutien.validerMaintenance(champs("debut", "2026-09-16T02:00:00.000Z", "fin", "2026-09-16T15:00:00.000Z", "description", "Mise à jour de la base de données"), now))
                .containsExactly("une fenêtre de maintenance ne dépasse pas 12 h");
        assertThat(Soutien.validerMaintenance(champs("debut", "2026-13-01T00:00", "fin", "2026-09-16T04:00:00.000Z"), now))
                .containsExactly("debut : instant ISO attendu", "description requise (10 caractères au moins) : ce qui sera indisponible, et pourquoi");
        assertThat(Soutien.validerMaintenance(champs("debut", "demain", "fin", "", "description", "court"), now))
                .containsExactly("debut : instant ISO attendu", "fin : instant ISO attendu", "description requise (10 caractères au moins) : ce qui sera indisponible, et pourquoi");
        assertThat(Soutien.validerMaintenance(champs("debut", "2020-01-01T00:00:00.000Z", "fin", "2020-01-01T01:00:00.000Z", "description", "Fenêtre dans le passé"), now))
                .containsExactly("préavis insuffisant : 72 h au moins avant le début (il en reste 0)");
        assertThat(Soutien.validerMaintenance(Map.of(), now)).hasSize(3);
    }

    @Test
    void laDisponibiliteCompteLesMinutesBattuesSurLesMinutesObserveesHorsMaintenance() {
        List<String> battements = List.of("10:00", "10:01", "10:02", "10:03", "10:04", "10:07", "10:08", "10:09").stream().map(h -> "2026-09-12T" + h + ":30.000Z").toList();
        Soutien.Disponibilite d = Soutien.calculerDisponibilite("2026-09", battements, List.of(new Soutien.Fenetre("2026-09-12T10:04:00.000Z", "2026-09-12T10:06:00.000Z")),
                "2026-09-12T10:10:00.000Z", "2026-09-12T10:00:30.000Z", STANDARD);
        assertThat(d).isEqualTo(new Soutien.Disponibilite("2026-09", "2026-09-12T10:00:00.000Z", "2026-09-12T10:10:00.000Z", 8, 2, 1, 87.5, 99.5, false,
                List.of(new Soutien.Verdict("standard", EngagementsDeService.Politique.STANDARD.libelle(), 99.5, null, "manquement"))));
        List<String> mille = IntStream.range(0, 1000).mapToObj(i -> Soutien.iso(Instant.parse("2026-09-01T00:00:00Z").plusSeconds(i * 60L))).toList();
        Soutien.Disponibilite plein = Soutien.calculerDisponibilite("2026-09", mille, List.of(), "2026-09-01T16:40:00.000Z", mille.get(0), STANDARD);
        assertThat(plein.minutesObservees()).isEqualTo(1000);
        assertThat(plein.minutesIndisponibles()).isZero();
        assertThat(plein.pourcentage()).isEqualTo(100.0);
        assertThat(plein.objectifAtteint()).isTrue();
        Soutien.Disponibilite cinq = Soutien.calculerDisponibilite("2026-09", mille.subList(5, 1000), List.of(), "2026-09-01T16:40:00.000Z", mille.get(0), STANDARD);
        assertThat(cinq.pourcentage()).isEqualTo(99.5);
        assertThat(cinq.objectifAtteint()).isTrue();
        // Le même mois, jugé contre le service essentiel : 99,5 % est un manquement, pas un manquement majeur.
        Soutien.Disponibilite jugee = Soutien.calculerDisponibilite("2026-09", mille.subList(5, 1000), List.of(), "2026-09-01T16:40:00.000Z", mille.get(0),
                List.of(EngagementsDeService.de(EngagementsDeService.Politique.STANDARD), EngagementsDeService.de(EngagementsDeService.Politique.SERVICE_ESSENTIEL)));
        assertThat(jugee.engagements().stream().map(v -> v.politique() + ":" + v.manquement() + ":" + v.seuilManquementMajeurPourcent()))
                .containsExactly("standard:aucun:null", "service-essentiel:manquement:99.0");
        assertThat(Soutien.calculerDisponibilite("2026-09", mille.subList(6, 1000), List.of(), "2026-09-01T16:40:00.000Z", mille.get(0), STANDARD).objectifAtteint()).isFalse();
    }

    @Test
    void rienAMesurerMoisMalFormeAucunBattementOuObservationVide() {
        Soutien.Disponibilite vide = new Soutien.Disponibilite("2026-09", null, null, 0, 0, 0, null, 99.5, null, List.of());
        assertThat(Soutien.calculerDisponibilite("2026-9", List.of(), List.of(), "2026-09-12T10:00:00.000Z", "2026-09-12T09:00:00.000Z", STANDARD).pourcentage()).isNull();
        assertThat(Soutien.calculerDisponibilite("2026-09", List.of(), List.of(), "2026-09-12T10:00:00.000Z", null, STANDARD)).isEqualTo(vide);
        assertThat(Soutien.calculerDisponibilite("2026-09", List.of(), List.of(), "2026-09-12T10:00:00.000Z", "2026-09-12T11:00:00.000Z", STANDARD)).isEqualTo(vide);
        // Un mois entièrement passé s'arrête à sa fin, pas à maintenant.
        Soutien.Disponibilite fin = Soutien.calculerDisponibilite("2026-08", List.of("2026-08-31T23:59:10.000Z"), List.of(), "2026-09-12T10:00:00.000Z", "2026-08-31T23:58:00.000Z", STANDARD);
        assertThat(fin.observeJusqua()).isEqualTo("2026-09-01T00:00:00.000Z");
        assertThat(List.of(fin.minutesObservees(), fin.minutesIndisponibles())).containsExactly(2, 1);
        assertThat(fin.pourcentage()).isEqualTo(50.0);
        assertThat(Soutien.cleMinute("2026-09-12T10:03:59.999Z")).isEqualTo("2026-09-12T10:03");
        // Toute la fenêtre observée en maintenance : zéro minute observée, donc pas de pourcentage — pas 100 %.
        Soutien.Disponibilite tout = Soutien.calculerDisponibilite("2026-09", List.of(), List.of(new Soutien.Fenetre("2026-09-12T09:00:00.000Z", "2026-09-12T11:00:00.000Z")),
                "2026-09-12T10:00:00.000Z", "2026-09-12T09:30:00.000Z", STANDARD);
        assertThat(tout.minutesObservees()).isZero();
        assertThat(tout.minutesEnMaintenance()).isEqualTo(30);
        assertThat(tout.pourcentage()).isNull();
        assertThat(tout.objectifAtteint()).isNull();
    }
}
