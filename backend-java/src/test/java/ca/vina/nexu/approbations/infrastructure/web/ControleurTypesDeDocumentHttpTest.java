package ca.vina.nexu.approbations.infrastructure.web;

import ca.vina.nexu.personnel.infrastructure.persistance.DepotAnnuaireEmployes;
import ca.vina.nexu.ClientHttpEssai;
import ca.vina.nexu.SchemaEssai;
import ca.vina.nexu.acces.infrastructure.persistance.RegistrePolitiqueAcces;
import ca.vina.nexu.approbations.infrastructure.configuration.ConfigurationApprobations;
import ca.vina.nexu.approbations.infrastructure.persistance.AdaptateurComptesApprobations;
import ca.vina.nexu.approbations.infrastructure.persistance.AdaptateurDroitDApprouver;
import ca.vina.nexu.approbations.infrastructure.persistance.DepotTypesDeDocument;
import ca.vina.nexu.auth.infrastructure.persistance.DepotRolesEtablissement;
import ca.vina.nexu.partage.securite.ContexteRequete;
import ca.vina.nexu.partage.web.GardeAcces;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.jdbc.core.simple.JdbcClient;

import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * {@code /api/approvals/document-types} et {@code /delegataires} — le jumeau Java de
 * {@code approval-document-types.integration.test.ts} et de la partie suggestion de
 * {@code delegation-feuilles.integration.test.ts}.
 */
@DisplayName("API approbations — catalogue des types de document et délégataires")
class ControleurTypesDeDocumentHttpTest {

    private static final Clock HORLOGE = Clock.fixed(Instant.parse("2026-09-13T12:00:00Z"), ZoneOffset.UTC);
    private static final ContexteRequete ADMIN = new ContexteRequete("usr-admin", "admin", "admin", null, "t-1");
    private static final ContexteRequete GESTIONNAIRE = new ContexteRequete("usr-mgr", "mgr", "manager", null, "t-1");
    private static final ContexteRequete CHANTAL = new ContexteRequete("usr-chantal", "chantal", "employee", "e-chef", "t-1");
    private static final ContexteRequete ALEX = new ContexteRequete("usr-alex", "alex", "employee", "e-alex", "t-1");
    private static final ContexteRequete AUTRE = new ContexteRequete("usr-autre", "autre", "admin", null, "t-2");
    private static final String D = "/api/approvals/document-types";

    private ClientHttpEssai http;
    private JdbcClient jdbc;

    @BeforeEach
    void preparer(@TempDir Path dossier) {
        jdbc = SchemaEssai.base(dossier);
        RegistrePolitiqueAcces acces = new RegistrePolitiqueAcces();
        DepotTypesDeDocument types = new DepotTypesDeDocument(jdbc);
        DepotAnnuaireEmployes personnes = new DepotAnnuaireEmployes(jdbc);
        AdaptateurComptesApprobations comptes = new AdaptateurComptesApprobations(jdbc, personnes, HORLOGE);
        AdaptateurDroitDApprouver droit = new AdaptateurDroitDApprouver(new DepotRolesEtablissement(jdbc, acces, HORLOGE), personnes, comptes);
        GardeAcces garde = new GardeAcces(acces);
        ConfigurationApprobations config = new ConfigurationApprobations();
        ca.vina.nexu.partage.temps.domaine.Horloge horloge = () -> "2026-09-13T12:00:00.000Z";
        http = new ClientHttpEssai(new ControleurTypesDeDocument(garde, new GardeApprobateurs(garde, droit),
                config.lectureTypesDeDocument(types, comptes, horloge), config.gestionnaireTypesDeDocument(types, horloge)));
        jdbc.sql("INSERT INTO employees (id, firstName, lastName, tenantId, hireDate, status, annualSalary, frequency, province, role, ytd_json, managerId) VALUES"
                + " ('e-chef', 'Chantal', 'Roy', 't-1', '2020-01-01', 'active', 52000, 'biweekly', 'QC', 'employee', '{}', NULL),"
                + " ('e-alex', 'Alex', 'Bérubé', 't-1', '2020-01-01', 'active', 52000, 'biweekly', 'QC', 'employee', '{}', 'e-chef')").update();
        jdbc.sql("INSERT INTO users (id, username, loginName, tenantId, passwordHash, role, employeeId, createdAt, active, email) VALUES"
                + " ('usr-chantal', 'chantal@@t-1', 'chantal', 't-1', 'x', 'employee', 'e-chef', 'x', 1, NULL),"
                + " ('usr-alex', 'alex@@t-1', 'alex', 't-1', 'x', 'employee', 'e-alex', 'x', 1, 'alex@ex.ca'),"
                + " ('usr-inactif', 'alexis@@t-1', 'alexis', 't-1', 'x', 'employee', NULL, 'x', 0, NULL),"
                + " ('usr-ailleurs', 'alexandre@@t-2', 'alexandre', 't-2', 'x', 'employee', NULL, 'x', 1, NULL)").update();
    }

    @Test
    @DisplayName("se lit par tout compte de l'organisme, sème les types système, ne sert que les actifs sauf « tous=1 »")
    void lecture() throws Exception {
        String liste = http.get(D, GESTIONNAIRE).andExpect(status().isOk()).andExpect(jsonPath("$[*].code").value(hasItem("contract")))
                .andExpect(jsonPath("$", hasSize(7))).andReturn().getResponse().getContentAsString();
        String contrat = ((List<String>) JsonPath.read(liste, "$[?(@.code=='contract')].id")).getFirst();
        http.put(D + "/" + contrat, ADMIN, "{\"active\":false}").andExpect(status().isOk()).andExpect(jsonPath("$.active").value(false));
        http.get(D, ADMIN).andExpect(jsonPath("$[*].code").value(not(hasItem("contract"))));
        http.get(D + "?tous=1", ADMIN).andExpect(jsonPath("$[*].code").value(hasItem("contract")));
        http.get(D, AUTRE).andExpect(jsonPath("$", hasSize(7)));
        http.get(D, null).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("ne se modifie que par l'administration ; un doublon de code est refusé ; un type système ne se supprime pas")
    void ecriture() throws Exception {
        http.post(D, GESTIONNAIRE, "{\"labelFr\":\"Entente\"}").andExpect(status().isForbidden());
        String cree = http.post(D, ADMIN, "{\"labelFr\":\"Entente de service\"}").andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").value("entente_de_service")).andExpect(jsonPath("$.systeme").value(false))
                .andReturn().getResponse().getContentAsString();
        http.post(D, ADMIN, "{\"labelFr\":\"Contrat bis\",\"code\":\"contract\"}").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0]").value("a document type with this code already exists"));
        http.post(D, ADMIN, "{\"labelFr\":\"x\"}").andExpect(status().isBadRequest());
        http.put(D + "/absent", ADMIN, "{\"labelFr\":\"Rien\"}").andExpect(status().isNotFound());
        http.put(D + "/" + JsonPath.read(cree, "$.id"), ADMIN, "{\"labelFr\":\"Entente renommée\",\"labelMg\":\"Fifanarahana\",\"sortOrder\":1}")
                .andExpect(jsonPath("$.labelFr").value("Entente renommée")).andExpect(jsonPath("$.sortOrder").value(1));
        String systeme = ((List<String>) JsonPath.read(http.get(D, ADMIN).andReturn().getResponse().getContentAsString(), "$[?(@.systeme==true)].id")).getFirst();
        http.delete(D + "/" + systeme, ADMIN).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors").value(contains("a system document type cannot be deleted; deactivate it instead")));
        http.delete(D + "/" + JsonPath.read(cree, "$.id"), ADMIN).andExpect(status().isNoContent());
        http.delete(D + "/" + JsonPath.read(cree, "$.id"), ADMIN).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errors[0]").value("document type not found"));
    }

    @Test
    @DisplayName("les délégataires : réservés à qui peut approuver, trois caractères au moins, l'organisme seul, nommés par leur fiche")
    void delegataires() throws Exception {
        http.get("/api/approvals/delegataires?q=alex", ALEX).andExpect(status().isForbidden());
        http.get("/api/approvals/delegataires?q=alex", null).andExpect(status().isUnauthorized());
        http.get("/api/approvals/delegataires?q=al", CHANTAL).andExpect(status().isOk()).andExpect(jsonPath("$", hasSize(0)));
        http.get("/api/approvals/delegataires?q=alex", CHANTAL).andExpect(jsonPath("$[*].nom").value(contains("Alex Bérubé")))
                .andExpect(jsonPath("$[0].id").value("usr-alex")).andExpect(jsonPath("$[0].courriel").value("alex@ex.ca"))
                .andExpect(jsonPath("$[0].statut").value("actif"));
        http.get("/api/approvals/delegataires?q=chantal", ADMIN).andExpect(jsonPath("$[*].nom").value(contains("Chantal Roy")));
        jdbc.sql("INSERT INTO approval_delegations (id, tenantId, fromUserId, toUserId, createdAt, fromDate, toDate) VALUES ('d1', 't-1', 'usr-chantal', 'usr-alex', 'x', '2026-09-01', '2026-09-30')").update();
        http.get("/api/approvals/delegataires?q=chantal", ALEX).andExpect(status().isOk()).andExpect(jsonPath("$[*].nom").value(contains("Chantal Roy")));
    }
}
