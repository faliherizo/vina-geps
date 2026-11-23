package ca.vina.nexu.personnel.infrastructure.web;

import ca.vina.nexu.ClientHttpEssai;
import ca.vina.nexu.acces.infrastructure.persistance.RegistrePolitiqueAcces;
import ca.vina.nexu.auth.infrastructure.persistance.DepotComptes;
import ca.vina.nexu.partage.securite.ContexteRequete;
import ca.vina.nexu.partage.web.GardeAcces;
import ca.vina.nexu.personnel.infrastructure.configuration.ConfigurationPersonnel;
import ca.vina.nexu.personnel.infrastructure.persistance.AdaptateurComptesPersonnel;
import ca.vina.nexu.personnel.infrastructure.persistance.DepotAnnuaireEmployes;
import ca.vina.nexu.personnel.infrastructure.persistance.DepotRepertoirePersonnes;
import liquibase.integration.spring.SpringLiquibase;
import org.hamcrest.Matchers;
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
import java.util.List;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * <strong>Mon équipe</strong>, sur le schéma de production : chacun voit sa ligne hiérarchique, directe et indirecte ; un
 * gestionnaire rattache, réaffecte et détache dans son équipe, et nulle part ailleurs.
 *
 * <pre>
 * Diane (dir) ── Marc (chef) ─┬─ Julie
 *                             └─ Luc
 * Paul (autre chef) ── Sam          Léa (sans supérieur)
 * </pre>
 */
@DisplayName("API personnel — Mon équipe : voir sa ligne hiérarchique, la réorganiser dans ses bornes")
class ControleurEquipeHttpTest {

    private static final String T = "t-1";
    private static final String R = "/api/personnel/equipe";
    private static final ContexteRequete MARC = new ContexteRequete("usr-marc", "marc", "manager", "e-marc", T);
    private static final ContexteRequete DIANE = new ContexteRequete("usr-diane", "diane", "manager", "e-diane", T);
    private static final ContexteRequete JULIE = new ContexteRequete("usr-julie", "julie", "employee", "e-julie", T);
    private static final ContexteRequete SANS_FICHE = new ContexteRequete("usr-sans", "sans", "manager", null, T);

    private ClientHttpEssai http;

    @BeforeEach
    void preparer(@TempDir Path dossier) throws Exception {
        SingleConnectionDataSource ds = new SingleConnectionDataSource("jdbc:sqlite:" + dossier.resolve("equipe.db"), true);
        SpringLiquibase liquibase = new SpringLiquibase();
        liquibase.setDataSource(ds);
        liquibase.setChangeLog("classpath:db/changelog/db.changelog-master.xml");
        liquibase.setResourceLoader(new DefaultResourceLoader());
        liquibase.afterPropertiesSet();
        JdbcClient jdbc = JdbcClient.create(ds);
        for (String[] e : List.of(new String[] {"e-diane", "Diane", "Roy", null, "Directrice"}, new String[] {"e-marc", "Marc", "Lavoie", "e-diane", "Chef d'équipe"},
                new String[] {"e-julie", "Julie", "Tremblay", "e-marc", "Paramédic"}, new String[] {"e-luc", "Luc", "Bouchard", "e-marc", "Paramédic"},
                new String[] {"e-paul", "Paul", "Côté", null, "Chef d'équipe"}, new String[] {"e-sam", "Sam", "Fortin", "e-paul", "Répartiteur"},
                new String[] {"e-lea", "Léa", "Morin", null, null})) {
            jdbc.sql("INSERT INTO employees (id, firstName, lastName, email, jobTitle, managerId, hireDate, annualSalary, frequency, province, role, ytd_json, tenantId) "
                    + "VALUES (?, ?, ?, ?, ?, ?, '2026-01-01', 0, 'biweekly', 'QC', 'employee', '{}', ?)")
                    .params(e[0], e[1], e[2], e[1].toLowerCase() + "@us.qc.ca", e[4], e[3], T).update();
        }
        for (ContexteRequete c : List.of(MARC, DIANE, JULIE, SANS_FICHE)) {
            jdbc.sql("INSERT INTO users (id, username, tenantId, passwordHash, role, createdAt, employeeId) VALUES (?, ?, ?, 'x', ?, 'x', ?)")
                    .params(c.userId(), c.username(), T, c.role(), c.employeeId()).update();
        }
        DepotAnnuaireEmployes annuaire = new DepotAnnuaireEmployes(jdbc);
        http = new ClientHttpEssai(new ControleurEquipe(new GardeAcces(new RegistrePolitiqueAcces()), new ConfigurationPersonnel().gestionnaireEquipe(
                new AdaptateurComptesPersonnel(new DepotComptes(jdbc, Clock.fixed(Instant.parse("2031-01-15T10:00:00Z"), ZoneOffset.UTC))), annuaire,
                new DepotRepertoirePersonnes(jdbc))));
    }

    @Test
    @DisplayName("chacun voit sa ligne : directe puis indirecte, avec le supérieur de chacun ; les personnes sans supérieur à rattacher")
    void voir() throws Exception {
        http.get(R, MARC).andExpect(status().isOk()).andExpect(jsonPath("$.moiId").value("e-marc")).andExpect(jsonPath("$.moiNom").value("Marc Lavoie"))
                .andExpect(jsonPath("$.membres[*].nom", Matchers.contains("Julie Tremblay", "Luc Bouchard")))
                .andExpect(jsonPath("$.membres[0].titre").value("Paramédic")).andExpect(jsonPath("$.membres[0].courriel").value("julie@us.qc.ca"))
                .andExpect(jsonPath("$.membres[*].superieurNom", Matchers.everyItem(Matchers.is("Marc Lavoie"))))
                .andExpect(jsonPath("$.sansSuperieur[*].nom", Matchers.contains("Diane Roy", "Léa Morin", "Paul Côté")));
        http.get(R, DIANE).andExpect(jsonPath("$.membres[*].nom", Matchers.contains("Marc Lavoie", "Julie Tremblay", "Luc Bouchard")))
                .andExpect(jsonPath("$.membres[*].niveau", Matchers.contains(1, 2, 2)))
                .andExpect(jsonPath("$.sansSuperieur[*].nom", Matchers.contains("Léa Morin", "Paul Côté")));
        http.get(R, JULIE).andExpect(jsonPath("$.membres").isEmpty());
        http.get(R, SANS_FICHE).andExpect(jsonPath("$.moiId").isEmpty()).andExpect(jsonPath("$.membres").isEmpty());
        http.get(R, new ContexteRequete("usr-marc", "marc", "manager", "e-marc", "t-2")).andExpect(jsonPath("$.moiId").isEmpty());
    }

    @Test
    @DisplayName("le gestionnaire rattache une personne libre, réaffecte un membre à un autre membre, détache un subordonné direct")
    void gerer() throws Exception {
        http.put(R + "/e-lea/superieur", MARC, "{\"superieurId\":\"e-marc\"}").andExpect(status().isOk())
                .andExpect(jsonPath("$.membres[*].nom", Matchers.contains("Julie Tremblay", "Léa Morin", "Luc Bouchard")));
        http.put(R + "/e-luc/superieur", MARC, "{\"superieurId\":\"e-julie\"}").andExpect(status().isOk())
                .andExpect(jsonPath("$.membres[?(@.nom == 'Luc Bouchard')].niveau", Matchers.contains(2)))
                .andExpect(jsonPath("$.membres[?(@.nom == 'Luc Bouchard')].superieurNom", Matchers.contains("Julie Tremblay")));
        http.put(R + "/e-lea/superieur", MARC, "{\"superieurId\":null}").andExpect(status().isOk())
                .andExpect(jsonPath("$.sansSuperieur[*].nom", Matchers.hasItem("Léa Morin")));
        // La directrice réorganise plus bas dans sa ligne.
        http.put(R + "/e-luc/superieur", DIANE, "{\"superieurId\":\"e-diane\"}").andExpect(status().isOk())
                .andExpect(jsonPath("$.membres[?(@.nom == 'Luc Bouchard')].niveau", Matchers.contains(1)));
    }

    @Test
    @DisplayName("hors de son équipe, rien : autre branche, nouveau supérieur étranger, détacher un indirect, cycle, employé, compte sans fiche")
    void bornes() throws Exception {
        http.put(R + "/e-sam/superieur", MARC, "{\"superieurId\":\"e-marc\"}").andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errors[0]").value("« Sam Fortin » ne relève pas de votre équipe"));
        http.put(R + "/e-julie/superieur", MARC, "{\"superieurId\":\"e-paul\"}").andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errors[0]").value("le nouveau supérieur doit être vous ou un membre de votre équipe"));
        http.put(R + "/e-julie/superieur", DIANE, "{\"superieurId\":null}").andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errors[0]").value("seul le supérieur direct de « Julie Tremblay » peut le détacher"));
        http.put(R + "/e-diane/superieur", MARC, "{\"superieurId\":\"e-julie\"}").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0]").value("le supérieur choisi relève déjà de cette personne : la hiérarchie tournerait en rond"));
        http.put(R + "/e-inconnu/superieur", MARC, "{\"superieurId\":\"e-marc\"}").andExpect(status().isNotFound());
        http.put(R + "/e-lea/superieur", MARC, "{\"superieurId\":7}").andExpect(status().isBadRequest());
        http.put(R + "/e-lea/superieur", JULIE, "{\"superieurId\":\"e-julie\"}").andExpect(status().isForbidden())
                .andExpect(jsonPath("$.capability").value("employee.update"));
        http.put(R + "/e-lea/superieur", SANS_FICHE, "{\"superieurId\":\"e-lea\"}").andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errors[0]").value(Matchers.startsWith("votre compte n'est relié à aucune fiche du personnel")));
    }
}
