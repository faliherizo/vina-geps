package ca.vina.nexu.effectifs.infrastructure.persistance;

import ca.vina.nexu.SchemaEssai;
import ca.vina.nexu.effectifs.domaine.SoldesBanques.Solde;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** AD-06b : les soldes reçus, un par personne et par banque, isolés par organisme, en décimal exact. */
@DisplayName("Soldes des banques — adaptateur SQLite")
class DepotSoldesBanquesTest {

    @Test
    @DisplayName("poser remplace banque par banque, garde les autres ; lecture exacte ; aucun débordement d'organisme")
    void soldes(@TempDir Path dossier) {
        var jdbc = SchemaEssai.base(dossier);
        DepotSoldesBanques depot = new DepotSoldesBanques(jdbc, new SchemaEffectifs(jdbc));
        depot.poser("t-1", List.of(new Solde("r-1", "VAC", new BigDecimal("70.10"), "2026-11-20", "t0"),
                new Solde("r-1", "MAL", new BigDecimal("-3.00"), "2026-11-20", "t0")));
        depot.poser("t-1", List.of(new Solde("r-1", "VAC", new BigDecimal("56.25"), "2026-11-21", "t1")));
        depot.poser("t-2", List.of(new Solde("r-1", "VAC", new BigDecimal("1"), "2026-11-21", "t1")));

        assertThat(depot.solde("t-1", "r-1", "VAC")).hasValueSatisfying(s -> {
            assertThat(s.heures()).isEqualByComparingTo("56.25");
            assertThat(s.auJour()).isEqualTo("2026-11-21");
            assertThat(s.recuLe()).isEqualTo("t1");
        });
        assertThat(depot.solde("t-1", "r-1", "AUTRE")).isEmpty();
        assertThat(depot.solde("t-3", "r-1", "VAC")).isEmpty();
        assertThat(depot.deLaRessource("t-1", "r-1")).extracting(Solde::banqueCode).containsExactly("MAL", "VAC");
        assertThat(depot.deLaRessource("t-1", "r-1").get(0).heures()).isEqualByComparingTo("-3.00");
        assertThat(depot.deLaRessource("t-2", "r-1")).hasSize(1);
    }
}
