package ca.vina.nexu.communication.infrastructure.web;

import ca.vina.nexu.ClientHttpEssai;
import ca.vina.nexu.SchemaEssai;
import ca.vina.nexu.acces.infrastructure.persistance.RegistrePolitiqueAcces;
import ca.vina.nexu.communication.domaine.GroupesDeTravailPort;
import ca.vina.nexu.partage.modules.infrastructure.DepotModulesActives;
import ca.vina.nexu.partage.securite.ContexteRequete;
import ca.vina.nexu.partage.web.GardeAcces;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.jdbc.core.simple.JdbcClient;

import java.nio.file.Path;
import java.util.List;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@DisplayName("API Communication — groupes de travail et leurs membres, sous la garde du module")
class ControleurGroupesDeTravailHttpTest {

    private static final ContexteRequete ROY = new ContexteRequete("u-roy", "roy", "employee", null, "T");
    private static final ContexteRequete SANS_MODULE = new ContexteRequete("u-x", "x", "employee", null, "T2");

    /** Un annuaire de groupes en mémoire : un groupe connu, un identifiant refusé, une panne sans message. */
    private static final GroupesDeTravailPort GROUPES = new GroupesDeTravailPort() {
        @Override
        public List<Groupe> groupes(String tenantId) {
            return List.of(new Groupe("EST", "EST", "Secteur Est"));
        }

        @Override
        public List<Membre> membres(String tenantId, String groupeId) {
            if ("refuse".equals(groupeId)) {
                throw new IllegalArgumentException("groupe inconnu : refuse");
            }
            if ("panne".equals(groupeId)) {
                throw new IllegalStateException();
            }
            return List.of(new Membre("roy", "Julie Roy"));
        }
    };

    private ClientHttpEssai http;

    @BeforeEach
    void preparer(@TempDir Path dossier) {
        JdbcClient jdbc = SchemaEssai.base(dossier);
        jdbc.sql("INSERT INTO tenant_quotas (tenantId, maxTeachers, maxContractsPerYear, modules) VALUES ('T', 1, 1, '[\"effectifs_communication\"]'), ('T2', 1, 1, '[\"effectifs\"]')").update();
        GardeCommunication garde = new GardeCommunication(new GardeAcces(new RegistrePolitiqueAcces()), new DepotModulesActives(jdbc));
        http = new ClientHttpEssai(new ControleurGroupesDeTravail(garde, GROUPES));
    }

    @Test
    @DisplayName("les groupes et leurs membres se lisent sous le module ; sans lui, 403 qui le nomme ; sans session, 401")
    void lecture() throws Exception {
        http.get("/api/communication/groupes", ROY).andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value("EST")).andExpect(jsonPath("$[0].nom").value("Secteur Est"));
        http.get("/api/communication/groupes/EST/membres", ROY).andExpect(status().isOk())
                .andExpect(jsonPath("$[0].personId").value("roy")).andExpect(jsonPath("$[0].nom").value("Julie Roy"));
        http.get("/api/communication/groupes", SANS_MODULE).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errors[0]").value("module 'effectifs_communication' not included in plan"));
        http.get("/api/communication/groupes", null).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("les groupes de la personne connectée : ceux dont elle est membre, et aucun pour qui n'y figure pas")
    void miens() throws Exception {
        http.get("/api/communication/groupes/miens", ROY).andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1)).andExpect(jsonPath("$[0]").value("EST"));
        http.get("/api/communication/groupes/miens", new ContexteRequete("u-lea", "lea", "employee", null, "T")).andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
        http.get("/api/communication/groupes/miens", SANS_MODULE).andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("une erreur de la source devient un 400 nommé ; sans message, « requête invalide »")
    void erreurs() throws Exception {
        http.get("/api/communication/groupes/refuse/membres", ROY).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0]").value("groupe inconnu : refuse"));
        http.get("/api/communication/groupes/panne/membres", ROY).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0]").value("requête invalide"));
    }
}
