package ca.vina.nexu;

import liquibase.integration.spring.SpringLiquibase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * ST-G13 (critère 74) : le retour arrière de schéma. Chaque changeset depuis 0006 a son script de retour
 * ({@code db/retour/00NN-retour.sql}). Appliqués du plus récent au plus ancien sur une base à jour, ils rendent
 * exactement le schéma d'avant 0006 ; la migration suivante les rejoue tous.
 */
@DisplayName("Retour arrière de schéma — les scripts de retour rendent le schéma d'avant 0006, et la remigration repasse")
class RetourArriereSchemaTest {

    private static final List<String> RETOURS = List.of("0025", "0024", "0023", "0022", "0021", "0020", "0019", "0018", "0017", "0016", "0015", "0014", "0013", "0012", "0011", "0010", "0009", "0008", "0007", "0006");

    private static void migrer(SingleConnectionDataSource ds, String changelog) throws Exception {
        SpringLiquibase l = new SpringLiquibase();
        l.setDataSource(ds);
        l.setChangeLog("classpath:" + changelog);
        l.setResourceLoader(new DefaultResourceLoader());
        l.afterPropertiesSet();
    }

    /** Les tables et, pour chacune, ses colonnes dans l'ordre : ce que la version précédente lirait. */
    private static Map<String, List<String>> schema(JdbcClient jdbc) {
        List<String> tables = jdbc.sql("SELECT name FROM sqlite_master WHERE type = 'table' AND name NOT LIKE 'DATABASECHANGELOG%' AND name NOT LIKE 'sqlite_%'")
                .query(String.class).list();
        return tables.stream().collect(Collectors.toMap(t -> t,
                t -> jdbc.sql("SELECT name || ' ' || type FROM pragma_table_info(?)").param(t).query(String.class).list(), (a, b) -> a, TreeMap::new));
    }

    private static List<String> journal(JdbcClient jdbc) {
        return jdbc.sql("SELECT ID FROM DATABASECHANGELOG ORDER BY ID").query(String.class).list();
    }

    private static void retour(JdbcClient jdbc, String numero) throws IOException {
        try (InputStream flux = RetourArriereSchemaTest.class.getResourceAsStream("/db/retour/" + numero + "-retour.sql")) {
            assertThat(flux).as("script de retour " + numero).isNotNull();
            String sql = Arrays.stream(new String(flux.readAllBytes(), StandardCharsets.UTF_8).split("\n"))
                    .filter(l -> !l.trim().startsWith("--")).collect(Collectors.joining("\n"));
            for (String instruction : sql.split(";")) {
                if (!instruction.isBlank()) {
                    jdbc.sql(instruction).update();
                }
            }
        }
    }

    @Test
    @DisplayName("du plus récent au plus ancien, les retours rendent le schéma et le journal d'avant 0006 ; une feuille payée redevient approuvée")
    void retourComplet(@TempDir Path dossier) throws Exception {
        SingleConnectionDataSource avant = new SingleConnectionDataSource("jdbc:sqlite:" + dossier.resolve("avant.db"), true);
        migrer(avant, "db/changelog-jusqua-0005.xml");
        JdbcClient reference = JdbcClient.create(avant);

        SingleConnectionDataSource base = new SingleConnectionDataSource("jdbc:sqlite:" + dossier.resolve("base.db"), true);
        migrer(base, "db/changelog/db.changelog-master.xml");
        JdbcClient jdbc = JdbcClient.create(base);
        Map<String, List<String>> complet = schema(jdbc);
        jdbc.sql("INSERT INTO eff_feuilles (id, tenantId, ressourceId, uniteCode, debut, fin, statut, paliersRequis, creeLe, creePar, payeeLe, paieId) "
                + "VALUES ('f1', 'T', 'r1', 'EST', '2026-11-09', '2026-11-15', 'PAYEE', 1, 'x', 'x', 'x', 'c1')").update();

        for (String n : RETOURS) {
            retour(jdbc, n);
        }
        assertThat(schema(jdbc)).isEqualTo(schema(reference));
        assertThat(journal(jdbc)).isEqualTo(journal(reference));
        assertThat(jdbc.sql("SELECT statut FROM eff_feuilles WHERE id = 'f1'").query(String.class).single()).isEqualTo("APPROUVEE");

        // La remise à niveau rejoue tous les changesets retirés : on retrouve le schéma complet, et la feuille.
        migrer(base, "db/changelog/db.changelog-master.xml");
        assertThat(schema(jdbc)).isEqualTo(complet);
        assertThat(jdbc.sql("SELECT COUNT(*) FROM eff_feuilles").query(Integer.class).single()).isOne();
    }
}
