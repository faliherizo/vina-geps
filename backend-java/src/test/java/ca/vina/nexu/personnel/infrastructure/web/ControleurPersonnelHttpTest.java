package ca.vina.nexu.personnel.infrastructure.web;

import ca.vina.nexu.ClientHttpEssai;
import ca.vina.nexu.SchemaEssai;
import ca.vina.nexu.acces.infrastructure.persistance.RegistrePolitiqueAcces;
import ca.vina.nexu.partage.securite.ContexteRequete;
import ca.vina.nexu.partage.web.GardeAcces;
import ca.vina.nexu.personnel.application.requetes.LecturePersonnes;
import ca.vina.nexu.personnel.infrastructure.persistance.DepotRepertoirePersonnes;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.jdbc.core.simple.JdbcClient;

import java.nio.file.Path;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@DisplayName("API Personnel — suggestions à trois caractères, résolution d'un identifiant, établissement cloisonné")
class ControleurPersonnelHttpTest {

    private static final ContexteRequete EMPLOYE = ClientHttpEssai.session("employee", "t-1");
    private static final ContexteRequete VOISIN = ClientHttpEssai.session("admin", "t-2");

    private ClientHttpEssai http;

    @BeforeEach
    void preparer(@TempDir Path dossier) {
        JdbcClient jdbc = SchemaEssai.base(dossier);
        jdbc.sql("INSERT INTO employees (id, firstName, lastName, tenantId, email, jobTitle, status) VALUES "
                + "('e-lea', 'Léa', 'Tremblay', 't-1', 'lea@exemple.ca', 'Paramédic', 'active'), "
                + "('e-max', 'Maxime', 'Gagnon', 't-1', NULL, NULL, 'active'), "
                + "('e-ail', 'Léa', 'Ailleurs', 't-2', NULL, NULL, 'active')").update();
        DepotRepertoirePersonnes depot = new DepotRepertoirePersonnes(jdbc);
        http = new ClientHttpEssai(new ControleurPersonnel(new GardeAcces(new RegistrePolitiqueAcces()),
                new LecturePersonnes(depot), EcriturePersonnelHttpTest.gestionnaire(depot)));
    }

    @Test
    @DisplayName("sans session : 401")
    void sansSession() throws Exception {
        http.get("/api/personnel/employes", null).andExpect(status().isUnauthorized());
        http.get("/api/personnel/employes/suggestions?q=lea", null).andExpect(status().isUnauthorized());
        http.get("/api/personnel/employes/e-lea", null).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("la liste se trie par nom et ne sort pas de l'établissement")
    void liste() throws Exception {
        http.get("/api/personnel/employes", EMPLOYE).andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].id").value("e-max")).andExpect(jsonPath("$[0].lastName").value("Gagnon"))
                .andExpect(jsonPath("$[1].firstName").value("Léa"));
        http.get("/api/personnel/employes", VOISIN).andExpect(jsonPath("$.length()").value(1));
    }

    @Test
    @DisplayName("une suggestion exige trois caractères, cherche nom, prénom ou courriel, sans casse, et rend le nom complet")
    void suggestions() throws Exception {
        http.get("/api/personnel/employes/suggestions?q=le", EMPLOYE).andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(0));
        http.get("/api/personnel/employes/suggestions", EMPLOYE).andExpect(jsonPath("$.length()").value(0));
        http.get("/api/personnel/employes/suggestions?q=TREM", EMPLOYE).andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].nom").value("Léa Tremblay")).andExpect(jsonPath("$[0].titre").value("Paramédic"))
                .andExpect(jsonPath("$[0].courriel").value("lea@exemple.ca")).andExpect(jsonPath("$[0].statut").value("active"));
        http.get("/api/personnel/employes/suggestions?q=exemple.ca", EMPLOYE).andExpect(jsonPath("$[0].id").value("e-lea"));
        http.get("/api/personnel/employes/suggestions?q=gagnon max", EMPLOYE).andExpect(jsonPath("$[0].id").value("e-max"));
        http.get("/api/personnel/employes/suggestions?q=Ailleurs", EMPLOYE).andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    @DisplayName("un identifiant se résout en nom ; inconnu ou d'un autre établissement : 404 nommé")
    void resolution() throws Exception {
        http.get("/api/personnel/employes/e-max", EMPLOYE).andExpect(status().isOk()).andExpect(jsonPath("$.nom").value("Maxime Gagnon"))
                .andExpect(jsonPath("$.titre").isEmpty());
        http.get("/api/personnel/employes/e-ail", EMPLOYE).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errors[0]").value("employé introuvable : e-ail"));
    }
}
