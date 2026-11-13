package ca.vina.nexu.personnel.infrastructure.web;

import ca.vina.nexu.ClientHttpEssai;
import ca.vina.nexu.acces.infrastructure.persistance.RegistrePolitiqueAcces;
import ca.vina.nexu.partage.identifiants.domaine.Sequence;
import ca.vina.nexu.partage.securite.ContexteRequete;
import ca.vina.nexu.partage.web.GardeAcces;
import ca.vina.nexu.personnel.application.commandes.GestionnairePersonnes;
import ca.vina.nexu.personnel.application.requetes.LecturePersonnes;
import ca.vina.nexu.personnel.domaine.FichesPersonnes;
import ca.vina.nexu.personnel.infrastructure.configuration.ConfigurationPersonnel;
import ca.vina.nexu.personnel.infrastructure.persistance.DepotRepertoirePersonnes;
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
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Écrire au répertoire du personnel, sur une base créée par <b>Liquibase</b> : la table {@code employees}
 * porte encore les colonnes de paie obligatoires du schéma d'origine, et c'est contre elles qu'une
 * insertion doit réussir — la base d'essai permissive ne l'aurait pas montré.
 */
@DisplayName("API personnel — ajouter et corriger une fiche, sur le schéma de production")
public class EcriturePersonnelHttpTest {

    private static final String R = "/api/personnel/employes";
    private static final ContexteRequete ADMIN = ClientHttpEssai.session("admin", "t-1");
    private static final ContexteRequete EMPLOYE = ClientHttpEssai.session("employee", "t-1");
    private static final ContexteRequete VOISIN = ClientHttpEssai.session("admin", "t-2");

    /** Des identifiants prévisibles : {@code emp-1}, {@code emp-2}… */
    public static GestionnairePersonnes gestionnaire(DepotRepertoirePersonnes depot) {
        AtomicInteger n = new AtomicInteger();
        Sequence sequence = new Sequence() {
            @Override
            public String suivant(String prefixe) {
                return prefixe + "-" + n.incrementAndGet();
            }

            @Override
            public String uuid() {
                return "emp-" + n.incrementAndGet();
            }

            @Override
            public String jetonSignature() {
                return "jeton";
            }

            @Override
            public String jetonBase64Url(int octets) {
                return "secret";
            }
        };
        return new ConfigurationPersonnel().gestionnairePersonnes(depot, sequence, Clock.fixed(Instant.parse("2026-11-13T10:00:00Z"), ZoneOffset.UTC));
    }

    private ClientHttpEssai http;
    private JdbcClient jdbc;

    @BeforeEach
    void preparer(@TempDir Path dossier) throws Exception {
        SingleConnectionDataSource ds = new SingleConnectionDataSource("jdbc:sqlite:" + dossier.resolve("personnel.db"), true);
        SpringLiquibase liquibase = new SpringLiquibase();
        liquibase.setDataSource(ds);
        liquibase.setChangeLog("classpath:db/changelog/db.changelog-master.xml");
        liquibase.setResourceLoader(new DefaultResourceLoader());
        liquibase.afterPropertiesSet();
        jdbc = JdbcClient.create(ds);
        DepotRepertoirePersonnes depot = new DepotRepertoirePersonnes(jdbc);
        http = new ClientHttpEssai(new ControleurPersonnel(new GardeAcces(new RegistrePolitiqueAcces()), new LecturePersonnes(depot), gestionnaire(depot)));
    }

    @Test
    @DisplayName("l'administration ajoute une personne ; elle est aussitôt trouvable par trois lettres de son nom, et seulement dans son établissement")
    void ajouter() throws Exception {
        http.post(R, EMPLOYE, "{}").andExpect(status().isForbidden()).andExpect(jsonPath("$.capability").value("employee.create"));
        http.post(R, ADMIN, "{\"firstName\":\" Léa \",\"lastName\":\"Tremblay\",\"email\":\"lea@css.qc\",\"phone\":\"418 555-0101\",\"jobTitle\":\" \"}")
                .andExpect(status().isCreated()).andExpect(jsonPath("$.id").value("emp-1")).andExpect(jsonPath("$.nom").value("Léa Tremblay"))
                .andExpect(jsonPath("$.firstName").value("Léa")).andExpect(jsonPath("$.courriel").value("lea@css.qc")).andExpect(jsonPath("$.titre").isEmpty())
                .andExpect(jsonPath("$.statut").value("active"));
        assertThat(jdbc.sql("SELECT hireDate || '|' || phone || '|' || annualSalary FROM employees WHERE id = 'emp-1'").query(String.class).single())
                .isEqualTo("2026-11-13|418 555-0101|0.0");
        http.get(R + "/suggestions?q=tre", ADMIN).andExpect(jsonPath("$[0].nom").value("Léa Tremblay"));
        http.get(R + "/suggestions?q=tre", VOISIN).andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    @DisplayName("une fiche se corrige ; une fiche d'un autre établissement est introuvable ; chaque motif de refus est nommé")
    void corrigerEtRefuser() throws Exception {
        http.post(R, ADMIN, "{\"firstName\":\"Max\",\"lastName\":\"Gagnon\",\"hireDate\":\"2024-08-15\"}").andExpect(status().isCreated());
        http.put(R + "/emp-1", ADMIN, "{\"firstName\":\"Maxime\",\"lastName\":\"Gagnon\",\"jobTitle\":\"Paramédic\"}")
                .andExpect(status().isOk()).andExpect(jsonPath("$.nom").value("Maxime Gagnon")).andExpect(jsonPath("$.titre").value("Paramédic"));
        http.put(R + "/emp-1", VOISIN, "{\"firstName\":\"X\",\"lastName\":\"Y\"}").andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errors[0]").value("employé introuvable : emp-1"));
        http.put(R + "/emp-1", EMPLOYE, "{}").andExpect(status().isForbidden()).andExpect(jsonPath("$.capability").value("employee.update"));
        http.post(R, ADMIN, null).andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors[0]").value("prénom requis · nom requis"));
        http.post(R, ADMIN, "{\"firstName\":\"A\",\"lastName\":\"B\",\"email\":\"pas-un-courriel\",\"hireDate\":\"15/08/2024\"}").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0]").value("courriel invalide · date d'embauche attendue au format AAAA-MM-JJ"));
        http.post(R, ADMIN, "{\"firstName\":7,\"lastName\":\"B\"}").andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("la validation du domaine, seule : une fiche valide est rognée, ses champs vides deviennent nuls")
    void domaine() {
        FichesPersonnes.Fiche f = FichesPersonnes.fiche("x", new FichesPersonnes.Demande(" A ", " B ", " a@b.ca ", "", null, null), "2026-11-13");
        assertThat(f).isEqualTo(new FichesPersonnes.Fiche("x", "A", "B", "a@b.ca", null, null, "2026-11-13"));
        assertThat(FichesPersonnes.valider(new FichesPersonnes.Demande("A", "B", null, null, null, "2024-01-01"))).isEmpty();
        assertThatThrownBy(() -> FichesPersonnes.fiche("x", new FichesPersonnes.Demande(" ", "B", null, null, null, null), "d")).hasMessage("prénom requis");
        assertThat(ControleurPersonnel.demande(Map.of("firstName", List.of()))).isEqualTo(new FichesPersonnes.Demande(null, null, null, null, null, null));
    }
}
