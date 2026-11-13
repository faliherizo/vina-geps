package ca.vina.nexu.ordonnanceur.infrastructure.persistance;

import ca.vina.nexu.ordonnanceur.domaine.BilanTic;
import ca.vina.nexu.SchemaEssai;
import ca.vina.nexu.ordonnanceur.domaine.PolitiqueRetentative;
import ca.vina.nexu.ordonnanceur.domaine.TacheDifferee;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.jdbc.core.simple.JdbcClient;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("Ordonnanceur — planifier sans empiler, tic à verrou atomique, issues consignées")
class DepotTachesTest {

    private static final String M = "2026-01-01T10:00:00.000Z";
    private JdbcClient jdbc;
    private RegistreExecuteurs registre;
    private DepotTaches depot;

    @BeforeEach
    void preparer(@TempDir Path dossier) {
        jdbc = SchemaEssai.base(dossier);
        registre = new RegistreExecuteurs();
        depot = new DepotTaches(jdbc, registre);
    }

    @Test
    void planifierRemplaceLEcheanceDeLaTacheEnAttente() {
        TacheDifferee t = depot.planifier("t-1", "avancer_recherche", "r-1", "2026-01-01T10:30:00.000Z", Map.of("k", "v"), null);
        assertThat(t.statut()).isEqualTo("en_attente");
        assertThat(t.charge()).containsEntry("k", "v");
        TacheDifferee bis = depot.planifier("t-1", "avancer_recherche", "r-1", "2026-01-01T11:00:00.000Z", null, "moi");
        assertThat(bis.id()).isEqualTo(t.id());
        assertThat(bis.echeanceLe()).isEqualTo("2026-01-01T11:00:00.000Z");
        assertThat(bis.charge()).containsEntry("k", "v");
        assertThat(depot.planifier("t-1", "avancer_recherche", "r-1", "2026-01-01T11:00:00.000Z", Map.of("k", "w"), null).charge()).containsEntry("k", "w");
        assertThat(depot.lister("t-1", null, null)).hasSize(1);
        assertThat(depot.lister("t-2", null, null)).isEmpty();
        assertThatThrownBy(() -> depot.planifier("t-1", " ", "r-1", "x", null, null)).isInstanceOf(IllegalArgumentException.class)
                .hasMessage("type requis; echeanceLe doit être un instant ISO");
        assertThat(depot.annulerPour("t-1", "avancer_recherche", "r-1")).isEqualTo(1);
        assertThat(depot.annulerPour("t-1", "avancer_recherche", "r-1")).isZero();
        assertThat(depot.lister("t-1", "annulee", "avancer_recherche")).hasSize(1);
        assertThat(depot.lireTache("nope")).isEmpty();
    }

    @Test
    void ticFaitReporteRetenteEtEchoue() {
        AtomicInteger appels = new AtomicInteger();
        registre.enregistrer("ok", (t, m) -> { appels.incrementAndGet(); return Optional.empty(); });
        registre.enregistrer("report", (t, m) -> Optional.of("2026-01-01T12:00:00.000Z"));
        registre.enregistrer("boum", (t, m) -> { throw new IllegalStateException("le texto n'est pas parti"); });
        depot.planifier("t-1", "ok", "a", "2026-01-01T09:00:00.000Z", null, null);
        depot.planifier("t-1", "report", "b", "2026-01-01T09:00:00.000Z", null, null);
        TacheDifferee boum = depot.planifier("t-1", "boum", "c", "2026-01-01T09:00:00.000Z", null, null);
        depot.planifier("t-1", "orphelin", "d", "2026-01-01T09:00:00.000Z", null, null);
        depot.planifier("t-1", "ok", "futur", "2026-01-01T10:00:01.000Z", null, null);

        BilanTic bilan = depot.tic(M, PolitiqueRetentative.PAR_DEFAUT, "tic-essai");
        assertThat(bilan).isEqualTo(new BilanTic(4, 1, 1, 1, 0, 1));
        assertThat(appels).hasValue(1);
        assertThat(depot.lister("t-1", "faite", null)).singleElement().satisfies(t -> assertThat(t.tentatives()).isEqualTo(1));
        assertThat(depot.lister("t-1", "en_attente", "report")).singleElement().satisfies(t -> {
            assertThat(t.echeanceLe()).isEqualTo("2026-01-01T12:00:00.000Z");
            assertThat(t.verrouLe()).isNull();
        });
        TacheDifferee retentee = depot.lireTache(boum.id()).orElseThrow();
        assertThat(retentee.statut()).isEqualTo("en_attente");
        assertThat(retentee.tentatives()).isEqualTo(1);
        assertThat(retentee.echeanceLe()).isEqualTo("2026-01-01T10:01:00.000Z");
        assertThat(retentee.derniereErreur()).isEqualTo("le texto n'est pas parti");
        assertThat(depot.lister("t-1", "echouee", "orphelin")).singleElement().satisfies(t -> assertThat(t.derniereErreur()).isEqualTo("aucun exécuteur enregistré pour « orphelin »"));

        // Deux retentatives de plus : la tâche échoue, visible, avec sa dernière erreur.
        assertThat(depot.tic("2026-01-01T10:01:00.000Z", PolitiqueRetentative.PAR_DEFAUT, "tic").retentees()).isEqualTo(1);
        assertThat(depot.tic("2026-01-01T10:06:00.000Z", PolitiqueRetentative.PAR_DEFAUT, "tic").echouees()).isEqualTo(1);
        assertThat(depot.lireTache(boum.id()).orElseThrow().statut()).isEqualTo("echouee");
    }

    @Test
    void verrouPerimeSeReprendEtPriseConcurrenteSePasse() {
        registre.enregistrer("ok", (t, m) -> Optional.empty());
        TacheDifferee t = depot.planifier("t-1", "ok", "a", "2026-01-01T09:00:00.000Z", null, null);
        jdbc.sql("UPDATE taches_differees SET statut = 'en_cours', verrouLe = '2026-01-01T09:00:00.000Z', verrouPar = 'mort' WHERE id = ?").param(t.id()).update();
        assertThat(depot.tic("2026-01-01T09:05:00.000Z", PolitiqueRetentative.PAR_DEFAUT, "tic").prises()).isZero();
        assertThat(depot.tic(M, PolitiqueRetentative.PAR_DEFAUT, "tic").faites()).isEqualTo(1);

        // Un autre tic a pris la tâche entre la lecture et la prise : aucune ligne ne change, on passe.
        TacheDifferee b = depot.planifier("t-1", "ok", "b", "2026-01-01T09:00:00.000Z", null, null);
        registre.enregistrer("ok", (x, m) -> Optional.empty());
        RegistreExecuteurs autre = new RegistreExecuteurs();
        DepotTaches concurrent = new DepotTaches(jdbc, autre) {
            @Override
            public BilanTic tic(String maintenant, PolitiqueRetentative p, String identite) {
                jdbc.sql("UPDATE taches_differees SET statut = 'faite' WHERE id = ?").param(b.id()).update();
                return super.tic(maintenant, p, identite);
            }
        };
        assertThat(concurrent.tic(M, PolitiqueRetentative.PAR_DEFAUT, "tic-2").prises()).isZero();
        assertThat(registre.pour("inconnu")).isEmpty();
        assertThat(List.of()).isEmpty();
    }

    @Test
    void chargeVideEtPriseRatee() {
        TacheDifferee t = depot.planifier("t-1", "ok", "a", "2026-01-01T09:00:00.000Z", null, null);
        jdbc.sql("UPDATE taches_differees SET charge = '' WHERE id = ?").param(t.id()).update();
        assertThat(depot.lireTache(t.id()).orElseThrow().charge()).isEmpty();
        registre.enregistrer("ok", (x, m) -> Optional.empty());
        DepotTaches devance = new DepotTaches(jdbc, registre) {
            @Override
            boolean prendre(TacheDifferee tache, String maintenant, String identite) {
                return false;   // un autre tic l'a prise entre la lecture et la prise
            }
        };
        assertThat(devance.tic(M, PolitiqueRetentative.PAR_DEFAUT, "tic")).isEqualTo(new BilanTic(0, 0, 0, 0, 0, 0));
    }
}
