package ca.vina.nexu.approbations.infrastructure.web;

import ca.vina.nexu.approbations.domaine.Approbations;

import com.jayway.jsonpath.JsonPath;

import ca.vina.nexu.personnel.infrastructure.persistance.DepotAnnuaireEmployes;
import ca.vina.nexu.approbations.infrastructure.configuration.ConfigurationApprobations;
import ca.vina.nexu.approbations.infrastructure.persistance.AdaptateurComptesApprobations;
import ca.vina.nexu.approbations.infrastructure.persistance.DepotApprobations;
import ca.vina.nexu.approbations.infrastructure.persistance.DepotTypesDeDocument;

import ca.vina.nexu.ClientHttpEssai;
import ca.vina.nexu.SchemaEssai;
import ca.vina.nexu.acces.infrastructure.persistance.RegistrePolitiqueAcces;
import ca.vina.nexu.auth.infrastructure.persistance.DepotComptes;
import ca.vina.nexu.partage.courrier.infrastructure.CourrierJournal;
import ca.vina.nexu.partage.securite.ContexteRequete;
import ca.vina.nexu.partage.web.GardeAcces;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.MvcResult;

import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Port de `approvals.test.ts` et `approvals-tenant-isolation` par l'API : règles, flux à deux niveaux, SoD, délégation, relance. */
@DisplayName("API approbations — règles, demandes, décisions, délégations, relance")
class ControleurApprobationsHttpTest {

    private static final Clock HORLOGE = Clock.fixed(Instant.parse("2026-09-13T12:00:00Z"), ZoneOffset.UTC);
    private static final ContexteRequete ADMIN = new ContexteRequete("usr-admin", "admin", "admin", null, "t-1");
    private static final ContexteRequete MANAGER = new ContexteRequete("usr-mgr", "mgr", "manager", null, "t-1");
    private static final ContexteRequete DIRECTEUR = new ContexteRequete("usr-dir", "dir", "director", null, "t-1");
    private static final ContexteRequete EMPLOYE = new ContexteRequete("usr-emp", "emp", "employee", null, "t-1");
    private static final ContexteRequete DELEGUE = new ContexteRequete("usr-del", "del", "employee", null, "t-1");
    private static final ContexteRequete AUTRE = new ContexteRequete("usr-autre", "autre", "admin", null, "t-2");

    private ClientHttpEssai http;
    private JdbcClient jdbc;
    private DepotApprobations depot;
    private CourrierJournal courrier;
    private DepotComptes comptes;

    @BeforeEach
    void preparer(@TempDir Path dossier) {
        jdbc = SchemaEssai.base(dossier);
        comptes = new DepotComptes(jdbc, HORLOGE);
        courrier = new CourrierJournal();
        depot = new DepotApprobations(jdbc, HORLOGE, comptes, courrier);
        ConfigurationApprobations config = new ConfigurationApprobations();
        AdaptateurComptesApprobations comptesApprobations = new AdaptateurComptesApprobations(jdbc,
                new DepotAnnuaireEmployes(jdbc), HORLOGE);
        ca.vina.nexu.partage.temps.domaine.Horloge horloge = () -> "2026-09-13T12:00:00.000Z";
        http = new ClientHttpEssai(new ControleurApprobations(config.lectureApprobations(depot, comptesApprobations),
                config.gestionnaireApprobations(depot, config.lectureTypesDeDocument(new DepotTypesDeDocument(jdbc), comptesApprobations, horloge), comptesApprobations),
                new GardeAcces(new RegistrePolitiqueAcces())));
        // Les délégataires des essais sont de vrais comptes : une délégation vers un identifiant inventé est refusée.
        jdbc.sql("INSERT INTO users (id, username, loginName, tenantId, passwordHash, role, employeeId, createdAt, active, email) VALUES"
                + " ('usr-mgr', 'mgr@@t-1', 'mgr', 't-1', 'x', 'manager', NULL, 'x', 1, NULL),"
                + " ('usr-del', 'delegue@@t-1', 'delegue', 't-1', 'x', 'employee', NULL, 'x', 1, NULL),"
                + " ('usr-sans-courriel', 'sans@@t-1', 'sans', 't-1', 'x', 'employee', NULL, 'x', 1, NULL),"
                + " ('usr-tard', 'tard@@t-1', 'tard', 't-1', 'x', 'employee', NULL, 'x', 1, NULL),"
                + " ('usr-inactif', 'inactif@@t-1', 'inactif', 't-1', 'x', 'employee', NULL, 'x', 0, NULL),"
                + " ('usr-ailleurs', 'ailleurs@@t-2', 'ailleurs', 't-2', 'x', 'admin', NULL, 'x', 1, NULL),"
                + " ('usr-global', 'global', 'global', NULL, 'x', 'superadmin', NULL, 'x', 1, NULL)").update();
    }

    private static List<String> ids(MvcResult r, String prefixe) throws Exception {
        Matcher m = Pattern.compile("\"id\":\"(" + prefixe + "-[^\"]+)\"").matcher(r.getResponse().getContentAsString());
        return m.results().map(x -> x.group(1)).toList();
    }

    private void regles() throws Exception {
        http.post("/api/approvals/rules", ADMIN, "{\"documentType\":\"purchase_order\",\"field\":\"amount\",\"operator\":\"gte\",\"value\":1000,\"level\":1,\"approverRole\":\"manager\"}").andExpect(status().isCreated());
        http.post("/api/approvals/rules", ADMIN, "{\"documentType\":\"purchase_order\",\"field\":\"amount\",\"operator\":\"gte\",\"value\":5000,\"level\":2,\"approverId\":\"usr-dir\"}").andExpect(status().isCreated());
    }

    @Test
    void lesReglesSeCreentParLEncadrementEtRestentDansLeurEtablissement() throws Exception {
        http.post("/api/approvals/rules", EMPLOYE, "{}").andExpect(status().isForbidden());
        http.post("/api/approvals/rules", ADMIN, "{\"documentType\":\"purchase_order\",\"operator\":\"gt\",\"level\":1.5}").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0]").value("field required; invalid operator (gte|lte|eq|neq); level must be a positive integer; approverRole or approverId required"));
        http.post("/api/approvals/rules", MANAGER, "{\"documentType\":\" purchase_order \",\"field\":\"amount\",\"operator\":\"gte\",\"value\":1000,\"level\":1,\"approverRole\":\" manager \"}")
                .andExpect(status().isCreated()).andExpect(jsonPath("$.tenantId").value("t-1")).andExpect(jsonPath("$.value").value(1000)).andExpect(jsonPath("$.approverRole").value("manager"))
                .andExpect(jsonPath("$.approverId").isEmpty());
        http.get("/api/approvals/rules", ADMIN).andExpect(jsonPath("$", hasSize(1))).andExpect(jsonPath("$[0].documentType").value("purchase_order")).andExpect(jsonPath("$[0].value").value("1000"))
                .andExpect(jsonPath("$[0].approverId").doesNotExist());
        http.get("/api/approvals/rules?documentType=autre", ADMIN).andExpect(jsonPath("$", hasSize(0)));
        http.get("/api/approvals/rules", AUTRE).andExpect(jsonPath("$", hasSize(0)));
        http.get("/api/approvals/rules", null).andExpect(status().isUnauthorized());
    }

    @Test
    void unFluxADeuxNiveauxSApprouveEtUnRejetRejetteLaDemande() throws Exception {
        regles();
        http.post("/api/approvals/requests", EMPLOYE, "{}").andExpect(status().isForbidden());
        http.post("/api/approvals/requests", ADMIN, "{\"documentType\":\"purchase_order\"}").andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors[0]").value("tenantId, documentType, documentId required"));
        http.post("/api/approvals/requests", ADMIN, "{\"documentType\":\"purchase_order\",\"documentId\":\"po-1\",\"context\":{\"amount\":10}}").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0]").value("no approval request matches this document context".replace("request", "rule")));
        MvcResult r = http.post("/api/approvals/requests", ADMIN, "{\"documentType\":\"purchase_order\",\"documentId\":\"po-1\",\"context\":{\"amount\":6000},\"requestedBy\":\"usr-req\"}").andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("pending")).andExpect(jsonPath("$.steps", hasSize(2))).andExpect(jsonPath("$.steps[0].approverRole").value("manager"))
                .andExpect(jsonPath("$.steps[1].approverId").value("usr-dir")).andExpect(jsonPath("$.steps[0].approverId").doesNotExist()).andExpect(jsonPath("$.requestedBy").value("usr-req")).andReturn();
        String demande = ids(r, "areq").get(0);
        List<String> etapes = ids(r, "astep");
        http.get("/api/approvals/requests/" + demande, MANAGER).andExpect(jsonPath("$.documentId").value("po-1"));
        http.get("/api/approvals/requests/" + demande, AUTRE).andExpect(status().isNotFound()).andExpect(jsonPath("$.errors[0]").value("approval request not found"));
        http.get("/api/approvals/requests", ADMIN).andExpect(jsonPath("$", hasSize(1)));
        http.get("/api/approvals/requests?status=approved", ADMIN).andExpect(jsonPath("$", hasSize(0)));
        http.get("/api/approvals/requests", AUTRE).andExpect(jsonPath("$", hasSize(0)));

        http.post("/api/approvals/steps/absent/decide", MANAGER, "{\"decision\":\"approved\"}").andExpect(status().isNotFound()).andExpect(jsonPath("$.errors[0]").value("approval step not found"));
        http.post("/api/approvals/steps/" + etapes.get(0) + "/decide", EMPLOYE, "{\"decision\":\"approved\"}").andExpect(status().isForbidden()).andExpect(jsonPath("$.errors[0]").value("not authorized to decide this step"));
        http.post("/api/approvals/steps/" + etapes.get(0) + "/decide", MANAGER, "{\"decision\":\"approved\"}").andExpect(jsonPath("$.status").value("pending")).andExpect(jsonPath("$.steps[0].status").value("approved"))
                .andExpect(jsonPath("$.steps[0].decidedBy").value("usr-mgr")).andExpect(jsonPath("$.steps[0].decidedAt").value("2026-09-13T12:00:00.000Z"));
        http.post("/api/approvals/steps/" + etapes.get(0) + "/decide", MANAGER, "{\"decision\":\"approved\"}").andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors[0]").value("step already decided"));
        http.post("/api/approvals/steps/" + etapes.get(1) + "/decide", DIRECTEUR, "{\"decision\":\"approved\"}").andExpect(jsonPath("$.status").value("approved"));

        MvcResult r2 = http.post("/api/approvals/requests", MANAGER, "{\"documentType\":\"purchase_order\",\"documentId\":\"po-2\",\"context\":{\"amount\":2000}}").andExpect(jsonPath("$.steps", hasSize(1)))
                .andExpect(jsonPath("$.requestedBy").value("usr-mgr")).andReturn();
        http.post("/api/approvals/steps/" + ids(r2, "astep").get(0) + "/decide", ADMIN, "{\"decision\":\"rejected\"}").andExpect(jsonPath("$.status").value("rejected"));
        http.get("/api/approvals/requests?status=rejected", ADMIN).andExpect(jsonPath("$", hasSize(1)));
    }

    @Test
    void laSeparationDesTachesSAppliqueAdminCompris() throws Exception {
        regles();
        MvcResult r = http.post("/api/approvals/requests", ADMIN, "{\"documentType\":\"purchase_order\",\"documentId\":\"po-1\",\"context\":{\"amount\":6000}}").andReturn();
        List<String> etapes = ids(r, "astep");
        // L'admin a soumis : il ne décide pas sa propre demande, même en admin.
        http.post("/api/approvals/steps/" + etapes.get(0) + "/decide", ADMIN, "{\"decision\":\"approved\"}").andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errors[0]").value("segregation of duties: the requester cannot approve their own request"));
        http.post("/api/approvals/steps/" + etapes.get(0) + "/decide", MANAGER, "{\"decision\":\"approved\"}").andExpect(status().isOk());
        // Le gestionnaire, déjà signataire du niveau 1, ne décide pas le niveau 2 même délégué par le directeur.
        http.post("/api/approvals/delegations", DIRECTEUR, "{\"toUserId\":\"usr-mgr\"}").andExpect(status().isCreated());
        http.post("/api/approvals/steps/" + etapes.get(1) + "/decide", MANAGER, "{\"decision\":\"approved\"}").andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errors[0]").value("segregation of duties: an approver cannot decide more than one level of the same request"));
        // Un délégataire tiers décide « pour le compte de » : la trace le dit.
        http.post("/api/approvals/delegations", DIRECTEUR, "{\"toUserId\":\"usr-del\",\"fromDate\":\"2026-09-01\",\"toDate\":\"2026-09-30\"}").andExpect(status().isCreated());
        http.post("/api/approvals/steps/" + etapes.get(1) + "/decide", DELEGUE, "{\"decision\":\"approved\"}").andExpect(jsonPath("$.status").value("approved"))
                .andExpect(jsonPath("$.steps[1].onBehalfOf").value("usr-dir")).andExpect(jsonPath("$.steps[1].decidedBy").value("usr-del"));
    }

    @Test
    void approbateurAdditionnelDelegationsEtRelance() throws Exception {
        regles();
        comptes.creer(new DepotComptes.Creation("del", "TempPass12", "employee", null, "t-1", false, false, "del@x.qc", false));
        String delId = comptes.parNom("del", "t-1").orElseThrow().id();
        MvcResult r = http.post("/api/approvals/requests", ADMIN, "{\"documentType\":\"purchase_order\",\"documentId\":\"po-1\",\"context\":{\"amount\":2000}}").andReturn();
        String demande = ids(r, "areq").get(0);
        http.post("/api/approvals/requests/absent/additional-approver", MANAGER, "{\"approverId\":\"u-9\"}").andExpect(status().isNotFound());
        http.post("/api/approvals/requests/" + demande + "/additional-approver", EMPLOYE, "{}").andExpect(status().isForbidden());
        http.post("/api/approvals/requests/" + demande + "/additional-approver", MANAGER, "{\"approverId\":\"" + delId + "\"}").andExpect(jsonPath("$.steps", hasSize(2)))
                .andExpect(jsonPath("$.steps[1].level").value(2)).andExpect(jsonPath("$.steps[1].additional").value(true));
        http.post("/api/approvals/steps/" + ids(r, "astep").get(0) + "/decide", MANAGER, "{\"decision\":\"approved\"}").andExpect(jsonPath("$.status").value("pending"));
        http.post("/api/approvals/steps/" + ids(r, "astep").get(0) + "/decide", DIRECTEUR, "{\"decision\":\"rejected\"}").andExpect(status().isBadRequest());

        // Délégations : validation, courriel au délégataire qui a une adresse, lecture des miennes.
        http.post("/api/approvals/delegations", MANAGER, "{\"toUserId\":\"usr-mgr\"}").andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors[0]").value("cannot delegate to oneself"));
        http.post("/api/approvals/delegations", MANAGER, "{}").andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors[0]").value("fromUserId and toUserId required"));
        http.post("/api/approvals/delegations", MANAGER, "{\"toUserId\":\"" + delId + "\",\"fromDate\":\"2026-09-01\",\"toDate\":\"2026-08-01\"}").andExpect(status().isBadRequest());
        http.post("/api/approvals/delegations", MANAGER, "{\"toUserId\":\"" + delId + "\",\"fromDate\":\"2026-09-01\"}").andExpect(status().isCreated())
                .andExpect(jsonPath("$.fromUserId").value("usr-mgr")).andExpect(jsonPath("$.toDate").isEmpty()).andExpect(jsonPath("$.createdAt").value("2026-09-13T12:00:00.000Z"));
        assertThat(courrier.envoyes()).hasSize(1);
        assertThat(courrier.envoyes().get(0).to()).isEqualTo("del@x.qc");
        assertThat(courrier.envoyes().get(0).text()).isEqualTo("Une délégation de validation vous a été confiée pour la période 2026-09-01 → …. Vous pouvez approuver les demandes au nom du délégant.");
        http.post("/api/approvals/delegations", DIRECTEUR, "{\"toUserId\":\"usr-sans-courriel\"}").andExpect(status().isCreated());
        assertThat(courrier.envoyes()).hasSize(1);
        http.get("/api/approvals/delegations?mine=1", MANAGER).andExpect(jsonPath("$", hasSize(1))).andExpect(jsonPath("$[0].toUserId").value(delId));
        http.get("/api/approvals/delegations", ADMIN).andExpect(jsonPath("$", hasSize(2)));
        http.get("/api/approvals/delegations", AUTRE).andExpect(jsonPath("$", hasSize(0)));
        // Une délégation expirée ne vaut plus : le délégataire n'est plus autorisé.
        assertThat(depot.delegataireActif("t-1", "usr-mgr")).isEqualTo(delId);
        http.post("/api/approvals/delegations", DIRECTEUR, "{\"toUserId\":\"usr-tard\",\"fromDate\":\"2027-01-01\"}").andExpect(status().isCreated());
        assertThat(depot.delegataireActif("t-1", "usr-dir")).isEqualTo("usr-sans-courriel");
        // Reçues : le délégataire sait pour qui il approuve aujourd'hui ; une délégation remplacée ne compte plus.
        assertThat(depot.delegationsRecues("t-1", delId)).extracting(Approbations.Delegation::fromUserId).containsExactly("usr-mgr");
        assertThat(depot.delegationsRecues("t-1", "usr-del")).isEmpty();
        // Le retour d'absence : l'auteur révoque la sienne ; celle d'un autre est introuvable, et ne bouge pas.
        String aRevoquer = JsonPath.read(http.get("/api/approvals/delegations?mine=1", MANAGER).andReturn().getResponse().getContentAsString(), "$[0].id");
        http.delete("/api/approvals/delegations/" + aRevoquer, DIRECTEUR).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errors[0]").value("délégation introuvable : " + aRevoquer));
        http.delete("/api/approvals/delegations/" + aRevoquer, MANAGER).andExpect(status().isNoContent());
        assertThat(depot.delegationActive("t-1", "usr-mgr")).isEmpty();
        assertThat(depot.delegataireActif("t-1", "usr-mgr")).isNull();

        // Par document, puis relance selon la tolérance.
        http.get("/api/approvals/by-document?documentType=purchase_order&documentId=po-1", ADMIN).andExpect(jsonPath("$.id").value(demande));
        http.get("/api/approvals/by-document?documentType=purchase_order&documentId=po-9", ADMIN).andExpect(status().isNotFound()).andExpect(jsonPath("$.errors[0]").value("no approval request for this document"));
        http.get("/api/approvals/by-document", ADMIN).andExpect(status().isNotFound());
        http.post("/api/approvals/relaunch", EMPLOYE, "{}").andExpect(status().isForbidden());
        http.post("/api/approvals/relaunch", DIRECTEUR, "{\"documentType\":\"purchase_order\",\"documentId\":\"po-9\",\"oldAmount\":1,\"newAmount\":2}").andExpect(jsonPath("$.relaunched").value(false))
                .andExpect(jsonPath("$.reason").value("no approval request for this document")).andExpect(jsonPath("$.request").isEmpty());
        http.post("/api/approvals/relaunch", DIRECTEUR, "{\"documentType\":\"purchase_order\",\"documentId\":\"po-1\",\"oldAmount\":2000,\"newAmount\":2100}").andExpect(jsonPath("$.relaunched").value(false))
                .andExpect(jsonPath("$.reason").value("change within 10% tolerance")).andExpect(jsonPath("$.request.id").value(demande));
        http.post("/api/approvals/relaunch", DIRECTEUR, "{\"documentType\":\"purchase_order\",\"documentId\":\"po-1\",\"oldAmount\":2000,\"newAmount\":2100,\"tolerancePct\":0.01}").andExpect(jsonPath("$.relaunched").value(true))
                .andExpect(jsonPath("$.reason").value("change exceeds 1% tolerance")).andExpect(jsonPath("$.request.steps[0].status").value("pending")).andExpect(jsonPath("$.request.status").value("pending"));
        http.post("/api/approvals/relaunch", DIRECTEUR, "{}").andExpect(jsonPath("$.relaunched").value(false));
        // Number("abc") et Number("2100") du Node : l'illisible vaut NaN, la chaîne numérique passe
        http.post("/api/approvals/relaunch", DIRECTEUR, "{\"documentType\":\"purchase_order\",\"documentId\":\"po-1\",\"oldAmount\":\"abc\",\"newAmount\":\"2100\"}").andExpect(jsonPath("$.relaunched").value(false));
        http.post("/api/approvals/rules", ADMIN, "{\"documentType\":\"purchase_order\",\"field\":\"amount\",\"operator\":\"gte\",\"value\":1,\"level\":1.5,\"approverRole\":\"manager\"}").andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors[0]").value("level must be a positive integer"));
    }

    @Test
    @DisplayName("une règle ne vise qu'un type du catalogue, actif ; l'erreur s'ajoute aux autres")
    void laRegleExigeUnTypeDuCatalogue() throws Exception {
        http.post("/api/approvals/rules", ADMIN, "{\"documentType\":\"po\",\"field\":\"amount\",\"operator\":\"gte\",\"value\":1,\"level\":1,\"approverRole\":\"manager\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0]").value("unknown or inactive documentType \"po\" (see the document type catalogue)"));
        http.post("/api/approvals/rules", ADMIN, "{\"documentType\":\"po\",\"operator\":\"gte\",\"level\":1,\"approverRole\":\"manager\"}")
                .andExpect(jsonPath("$.errors[0]").value("unknown or inactive documentType \"po\" (see the document type catalogue); field required"));
    }

    @Test
    @DisplayName("le délégataire existe, est actif, appartient à l'organisme — sauf un compte global ; la liste le nomme")
    void leDelegataireEstVerifieEtNomme() throws Exception {
        http.post("/api/approvals/delegations", DIRECTEUR, "{\"toUserId\":\"usr-fantome\"}").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0]").value("delegate not found"));
        http.post("/api/approvals/delegations", DIRECTEUR, "{\"toUserId\":\"usr-inactif\"}").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0]").value("delegate account is inactive"));
        http.post("/api/approvals/delegations", DIRECTEUR, "{\"toUserId\":\"usr-ailleurs\"}").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0]").value("delegate belongs to another tenant"));
        http.post("/api/approvals/delegations", DIRECTEUR, "{\"toUserId\":\"usr-global\"}").andExpect(status().isCreated());
        http.post("/api/approvals/delegations", DIRECTEUR, "{\"toUserId\":\"usr-del\"}").andExpect(status().isCreated());
        jdbc.sql("INSERT INTO approval_delegations (id, tenantId, fromUserId, toUserId, createdAt) VALUES ('d-vieux', 't-1', 'usr-dir', 'usr-disparu', '2000-01-01')").update();
        http.get("/api/approvals/delegations", ADMIN).andExpect(jsonPath("$[*].toNom").value(org.hamcrest.Matchers.containsInAnyOrder("global", "delegue", "usr-disparu")))
                .andExpect(jsonPath("$[0].toNom").exists());
    }
}
