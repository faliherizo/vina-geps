package ca.vina.nexu.personnel.infrastructure.web;

import ca.vina.nexu.ClientHttpEssai;
import ca.vina.nexu.acces.infrastructure.persistance.RegistrePolitiqueAcces;
import ca.vina.nexu.partage.erreurs.domaine.IntrouvableException;
import ca.vina.nexu.partage.securite.ContexteRequete;
import ca.vina.nexu.partage.web.GardeAcces;
import ca.vina.nexu.personnel.domaine.AnnuaireEmployes.Fiche;
import ca.vina.nexu.personnel.domaine.FichesPersonnes;
import ca.vina.nexu.personnel.domaine.Hierarchie;
import ca.vina.nexu.personnel.infrastructure.configuration.ConfigurationPersonnel;
import ca.vina.nexu.personnel.infrastructure.persistance.DepotAnnuaireEmployes;
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
import java.util.List;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * <strong>Qui relève de qui</strong>, sur le schéma de production : désigner le supérieur hiérarchique que le flux
 * des feuilles de temps lit, le retirer, et refuser soi-même, un inconnu et un cycle.
 */
@DisplayName("API personnel — l'organigramme : désigner le supérieur hiérarchique")
class ControleurHierarchieHttpTest {

    private static final String R = "/api/personnel";
    private static final ContexteRequete ADMIN = ClientHttpEssai.session("admin", "t-1");
    private static final ContexteRequete EMPLOYE = ClientHttpEssai.session("employee", "t-1");
    private static final ContexteRequete VOISIN = ClientHttpEssai.session("admin", "t-2");

    private ClientHttpEssai http;
    private JdbcClient jdbc;

    @BeforeEach
    void preparer(@TempDir Path dossier) throws Exception {
        SingleConnectionDataSource ds = new SingleConnectionDataSource("jdbc:sqlite:" + dossier.resolve("hierarchie.db"), true);
        SpringLiquibase liquibase = new SpringLiquibase();
        liquibase.setDataSource(ds);
        liquibase.setChangeLog("classpath:db/changelog/db.changelog-master.xml");
        liquibase.setResourceLoader(new DefaultResourceLoader());
        liquibase.afterPropertiesSet();
        jdbc = JdbcClient.create(ds);
        for (String[] e : List.of(new String[] {"e-dir", "Diane", "Roy"}, new String[] {"e-chef", "Marc", "Lavoie"}, new String[] {"e-julie", "Julie", "Tremblay"})) {
            jdbc.sql("INSERT INTO employees (id, firstName, lastName, email, hireDate, annualSalary, frequency, province, role, ytd_json, tenantId) "
                    + "VALUES (?, ?, ?, '', '2026-01-01', 0, 'biweekly', 'QC', 'employee', '{}', 't-1')").params(e[0], e[1], e[2]).update();
        }
        http = new ClientHttpEssai(new ControleurHierarchie(new GardeAcces(new RegistrePolitiqueAcces()),
                new ConfigurationPersonnel().gestionnaireHierarchie(new DepotAnnuaireEmployes(jdbc))));
    }

    @Test
    @DisplayName("l'administration désigne puis retire un supérieur ; l'organigramme le montre ; l'employé ne le peut pas")
    void designerEtRetirer() throws Exception {
        http.put(R + "/employes/e-julie/superieur", EMPLOYE, "{\"superieurId\":\"e-chef\"}").andExpect(status().isForbidden())
                .andExpect(jsonPath("$.capability").value("employee.update"));
        http.put(R + "/employes/e-julie/superieur", ADMIN, "{\"superieurId\":\" e-chef \"}").andExpect(status().isOk())
                .andExpect(jsonPath("$.nom").value("Julie Tremblay")).andExpect(jsonPath("$.superieurId").value("e-chef"));
        http.put(R + "/employes/e-chef/superieur", ADMIN, "{\"superieurId\":\"e-dir\"}").andExpect(status().isOk());
        http.get(R + "/hierarchie", EMPLOYE).andExpect(status().isOk())
                .andExpect(jsonPath("$[*].nom", Matchers.contains("Marc Lavoie", "Diane Roy", "Julie Tremblay")))
                .andExpect(jsonPath("$[*].superieurId", Matchers.contains("e-dir", null, "e-chef")));
        http.put(R + "/employes/e-julie/superieur", ADMIN, "{\"superieurId\":\"\"}").andExpect(status().isOk()).andExpect(jsonPath("$.superieurId").isEmpty());
        http.put(R + "/employes/e-chef/superieur", ADMIN, null).andExpect(status().isOk()).andExpect(jsonPath("$.superieurId").isEmpty());
        http.get(R + "/hierarchie", VOISIN).andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    @DisplayName("refusés : soi-même, un cycle, un inconnu, un autre établissement, une valeur qui n'est pas un identifiant")
    void refus() throws Exception {
        http.put(R + "/employes/e-chef/superieur", ADMIN, "{\"superieurId\":\"e-dir\"}").andExpect(status().isOk());
        http.put(R + "/employes/e-julie/superieur", ADMIN, "{\"superieurId\":\"e-chef\"}").andExpect(status().isOk());
        http.put(R + "/employes/e-julie/superieur", ADMIN, "{\"superieurId\":\"e-julie\"}").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0]").value("une personne ne relève pas d'elle-même"));
        http.put(R + "/employes/e-dir/superieur", ADMIN, "{\"superieurId\":\"e-julie\"}").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0]").value("le supérieur choisi relève déjà de cette personne : la hiérarchie tournerait en rond"));
        http.put(R + "/employes/e-julie/superieur", ADMIN, "{\"superieurId\":\"e-x\"}").andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errors[0]").value("supérieur introuvable : e-x"));
        http.put(R + "/employes/e-julie/superieur", VOISIN, "{\"superieurId\":\"e-chef\"}").andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errors[0]").value("employé introuvable : e-julie"));
        http.put(R + "/employes/e-julie/superieur", ADMIN, "{\"superieurId\":7}").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0]").value("« superieurId » : l'identifiant d'une fiche, ou null pour retirer le supérieur"));
    }

    @Test
    @DisplayName("le domaine seul : retirer est toujours permis à une fiche connue")
    void domaine() {
        List<Fiche> bassin = List.of(new Fiche("a", "A", "A", null), new Fiche("b", "B", "B", "a"));
        assertThatCode(() -> Hierarchie.verifier("b", null, bassin)).doesNotThrowAnyException();
        assertThatCode(() -> Hierarchie.verifier("a", null, bassin)).doesNotThrowAnyException();
        assertThatThrownBy(() -> Hierarchie.verifier("z", null, bassin)).isInstanceOf(IntrouvableException.class);
        assertThatThrownBy(() -> Hierarchie.verifier("a", "b", bassin)).isInstanceOf(FichesPersonnes.FicheInvalideException.class);
    }
}
