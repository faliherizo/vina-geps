package ca.vina.nexu.ordonnanceur.infrastructure.persistance;

import ca.vina.nexu.SchemaEssai;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.jdbc.core.simple.JdbcClient;

import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

@DisplayName("Boucle de l'ordonnanceur — un tic à l'horloge réelle, qui prend l'échu et survit à une panne")
class BoucleOrdonnanceurTest {

    private static final Clock HORLOGE = Clock.fixed(Instant.parse("2026-01-01T12:00:00Z"), ZoneOffset.UTC);

    @Test
    void unTicExecuteLEchuEtUnTicEnPanneNArretePasLaBoucle(@TempDir Path dossier) {
        JdbcClient jdbc = SchemaEssai.base(dossier);
        RegistreExecuteurs registre = new RegistreExecuteurs();
        AtomicInteger appels = new AtomicInteger();
        registre.enregistrer("ok", (t, m) -> { appels.incrementAndGet(); return Optional.empty(); });
        DepotTaches depot = new DepotTaches(jdbc, registre);
        depot.planifier("t-1", "ok", "r-1", "2026-01-01T11:00:00.000Z", Map.of(), null);
        depot.planifier("t-1", "ok", "r-2", "2026-01-01T13:00:00.000Z", Map.of(), null);

        BoucleOrdonnanceur boucle = new BoucleOrdonnanceur(depot, HORLOGE);
        boucle.tic();
        boucle.tic();
        assertThat(appels.get()).isEqualTo(1);

        jdbc.sql("DROP TABLE taches_differees").update();
        assertThatCode(boucle::tic).doesNotThrowAnyException();
    }
}
