package ca.vina.nexu;

import ca.vina.nexu.auth.infrastructure.web.ControleurAuth;
import ca.vina.nexu.auth.infrastructure.persistance.DepotComptes;
import ca.vina.nexu.partage.securite.FiltreCsrf;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.jdbc.datasource.SimpleDriverDataSource;
import org.springframework.test.context.TestPropertySource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Le contexte Spring démarre — avec tous les contrôleurs, dépôts et filtres.
 *
 * <p>Chaque essai de contrat construit son contrôleur à la main : aucun ne voyait que deux dépôts
 * portaient le même nom de bean et que l'application ne démarrait plus. Ce que la construction
 * seule décide (noms de beans, injections, propriétés) se vérifie ici, une fois pour toutes.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:sqlite:${java.io.tmpdir}/nexu-demarrage-essai.db",
        "spring.liquibase.enabled=false",
        "nexu.jwt.secret=secret-d-essai-de-trente-deux-octets-au-moins",
})
@DisplayName("Démarrage — le contexte Spring se construit avec tous ses beans")
class DemarrageTest {

    @Autowired
    private ApplicationContext contexte;

    /** La base du démarrage porte le schéma des essais : le provisionnement des comptes et le premier tic de l'ordonnanceur s'y exécutent pour de vrai. */
    @BeforeAll
    static void preparerLaBase() throws IOException {
        Path base = Path.of(System.getProperty("java.io.tmpdir"), "nexu-demarrage-essai.db");
        Files.deleteIfExists(base);
        SchemaEssai.base(new SimpleDriverDataSource(new org.sqlite.JDBC(), "jdbc:sqlite:" + base));
    }

    @Test
    void lesControleursEtLesFiltresSontDesBeans() {
        assertThat(contexte.getBean(ControleurAuth.class)).isNotNull();
        // les comptes que le Node assure au démarrage existent aussi ici
        assertThat(contexte.getBean(DepotComptes.class).parNom("admin", null)).isPresent();
        assertThat(contexte.getBean(DepotComptes.class).parNom("superadmin", null)).isPresent();
        // un avis au soutien part par le courrier configuré, pas par un journal privé (constructeur choisi par Spring)
        assertThat(contexte.getBean(ca.vina.nexu.soutien.infrastructure.persistance.AvisSoutienJournal.class).transport()).isEqualTo(contexte.getBean(ca.vina.nexu.partage.courrier.Courrier.class).transport());
        contexte.getBean(ca.vina.nexu.soutien.infrastructure.persistance.AvisSoutienJournal.class).aviser(java.util.List.of(new ca.vina.nexu.soutien.domaine.AvisSoutien.Courriel("soutien@x.ca", "[Soutien] essai", "corps")));
        assertThat(contexte.getBean(ca.vina.nexu.partage.courrier.infrastructure.CourrierJournal.class).boite()).anySatisfy(m -> assertThat(m).containsEntry("subject", "[Soutien] essai"));
        assertThat(contexte.getBean(FiltreCsrf.class)).isNotNull();
        // La base s'ouvre en journal WAL : sans lui, une lecture longue (copie par organisme) bloque les écritures.
        assertThat(contexte.getBean(org.springframework.jdbc.core.simple.JdbcClient.class).sql("PRAGMA journal_mode").query(String.class).single()).isEqualToIgnoringCase("wal");
    }
}
