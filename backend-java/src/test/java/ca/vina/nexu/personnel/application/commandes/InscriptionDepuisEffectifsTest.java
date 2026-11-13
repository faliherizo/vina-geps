package ca.vina.nexu.personnel.application.commandes;

import ca.vina.nexu.auth.domaine.Compte;
import ca.vina.nexu.auth.infrastructure.persistance.DepotComptes;
import ca.vina.nexu.partage.personnel.FichesPersonnelPort;
import ca.vina.nexu.personnel.domaine.FichesPersonnes;
import ca.vina.nexu.personnel.infrastructure.configuration.ConfigurationPersonnel;
import ca.vina.nexu.personnel.infrastructure.persistance.AdaptateurComptesPersonnel;
import ca.vina.nexu.personnel.infrastructure.persistance.DepotRepertoirePersonnes;
import ca.vina.nexu.personnel.infrastructure.web.EcriturePersonnelHttpTest;
import liquibase.integration.spring.SpringLiquibase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;

import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Une ressource d'effectifs reliée à un compte inscrit sa personne au répertoire, sur le schéma de
 * production : la fiche est créée, le compte y est rattaché, et l'inscription ne se répète pas.
 */
@DisplayName("Inscription au répertoire depuis une ressource d'effectifs — un compte, une ressource, une fiche")
class InscriptionDepuisEffectifsTest {

    private JdbcClient jdbc;
    private DepotComptes comptes;
    private FichesPersonnelPort inscription;

    @BeforeEach
    void preparer(@TempDir Path dossier) throws Exception {
        SingleConnectionDataSource ds = new SingleConnectionDataSource("jdbc:sqlite:" + dossier.resolve("inscription.db"), true);
        SpringLiquibase liquibase = new SpringLiquibase();
        liquibase.setDataSource(ds);
        liquibase.setChangeLog("classpath:db/changelog/db.changelog-master.xml");
        liquibase.setResourceLoader(new DefaultResourceLoader());
        liquibase.afterPropertiesSet();
        jdbc = JdbcClient.create(ds);
        comptes = new DepotComptes(jdbc, Clock.fixed(Instant.parse("2026-11-13T10:00:00Z"), ZoneOffset.UTC));
        DepotRepertoirePersonnes depot = new DepotRepertoirePersonnes(jdbc);
        inscription = new ConfigurationPersonnel().fichesPersonnel(new AdaptateurComptesPersonnel(comptes), EcriturePersonnelHttpTest.gestionnaire(depot));
    }

    private Compte compte(String nom, String tenant, String employeeId) {
        return comptes.creer(new DepotComptes.Creation(nom, "Passe!Longue2026", "employee", employeeId, tenant, false, false, nom + "@css.qc", false));
    }

    private String ficheDe(Compte c) {
        return comptes.parId(c.id()).orElseThrow().employeeId();
    }

    @Test
    @DisplayName("le compte d'une ressource reçoit sa fiche : prénom, nom et courriel du compte ; une seconde inscription ne crée rien")
    void inscrire() {
        Compte julie = compte("julie", "t-1", null);
        inscription.inscrire("t-1", new FichesPersonnelPort.Inscription("eres-1", "  Julie   Marie Roy ", julie.id()));
        String fiche = ficheDe(julie);
        assertThat(fiche).isNotNull();
        assertThat(jdbc.sql("SELECT firstName || '|' || lastName || '|' || email || '|' || tenantId FROM employees WHERE id = ?").param(fiche).query(String.class).single())
                .isEqualTo("Julie Marie|Roy|julie@css.qc|t-1");
        inscription.inscrire("t-1", new FichesPersonnelPort.Inscription("eres-1", "Julie Roy", julie.id()));
        assertThat(ficheDe(julie)).isEqualTo(fiche);
        assertThat(jdbc.sql("SELECT COUNT(*) FROM employees").query(Integer.class).single()).isEqualTo(1);
    }

    @Test
    @DisplayName("rien ne s'écrit pour un compte absent, déjà rattaché, ou d'un autre établissement")
    void refus() {
        Compte voisin = compte("voisin", "t-2", null);
        Compte lie = compte("lie", "t-1", "emp-existant");
        inscription.inscrire("t-1", new FichesPersonnelPort.Inscription("eres-1", "Voisin Ailleurs", voisin.id()));
        inscription.inscrire("t-1", new FichesPersonnelPort.Inscription("eres-2", "Déjà Liée", lie.id()));
        inscription.inscrire("t-1", new FichesPersonnelPort.Inscription("eres-3", "Personne", "absent"));
        assertThat(ficheDe(voisin)).isNull();
        assertThat(ficheDe(lie)).isEqualTo("emp-existant");
        assertThat(jdbc.sql("SELECT COUNT(*) FROM employees").query(Integer.class).single()).isZero();
    }

    @Test
    @DisplayName("un nom d'un seul mot sert de prénom et de nom ; le dernier mot est le nom")
    void nomComplet() {
        assertThat(FichesPersonnes.depuisNomComplet("Roy", null)).isEqualTo(new FichesPersonnes.Demande("Roy", "Roy", null, null, null, null));
        assertThat(FichesPersonnes.depuisNomComplet("Jean-Luc De La Salle", "a@b.ca").nom()).isEqualTo("Salle");
        assertThat(FichesPersonnes.depuisNomComplet(null, null).prenom()).isEmpty();
    }
}
