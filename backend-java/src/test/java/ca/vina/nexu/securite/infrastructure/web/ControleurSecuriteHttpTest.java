package ca.vina.nexu.securite.infrastructure.web;

import ca.vina.nexu.securite.infrastructure.configuration.ConfigurationIncidents;
import ca.vina.nexu.securite.infrastructure.persistance.DepotIncidents;

import ca.vina.nexu.ClientHttpEssai;
import ca.vina.nexu.SchemaEssai;
import ca.vina.nexu.acces.infrastructure.persistance.RegistrePolitiqueAcces;
import ca.vina.nexu.partage.securite.ContexteRequete;
import ca.vina.nexu.partage.web.GardeAcces;
import ca.vina.nexu.partage.modules.infrastructure.DepotModulesActives;
import org.springframework.http.MediaType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.MvcResult;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Port de `securite-incidents.integration.test.ts` : mêmes appels, mêmes codes, mêmes messages. */
@DisplayName("API sécurité — registre des incidents Loi 25")
class ControleurSecuriteHttpTest {

    private static final ContexteRequete ADMIN = ClientHttpEssai.session("admin", "t-1");
    private static final ContexteRequete RH = ClientHttpEssai.session("rh", "t-1");
    private static final ContexteRequete AUTRE = ClientHttpEssai.session("admin", "t-2");
    private static final ContexteRequete SANS_MODULE = ClientHttpEssai.session("admin", "t-3");
    private static final ContexteRequete SUPERADMIN = ClientHttpEssai.superadmin();
    private static final String BASE = "{\"survenuLe\":\"2026-09-01\",\"decouvertLe\":\"2026-09-03\",\"nature\":\"acces_non_autorise\","
            + "\"description\":\"Un compte de secrétariat a consulté trois dossiers hors de son école.\",\"renseignements\":[\"identite\",\"emploi\"],"
            + "\"personnesTouchees\":3,\"gravite\":\"moderee\",\"mesures\":\"Accès retiré, journal relu, personnes avisées.\"";
    private ClientHttpEssai http;
    private DepotIncidents depot;

    @BeforeEach
    void preparer(@TempDir Path dossier) {
        JdbcClient jdbc = SchemaEssai.base(dossier);
        jdbc.sql("INSERT INTO tenant_quotas VALUES ('t-1', 10, 10, '[\"confidentialite\"]'), ('t-2', 10, 10, '[\"confidentialite\"]'), ('t-3', 10, 10, '[]')").update();
        jdbc.sql("INSERT INTO tenants (id, name) VALUES ('t-1', 'CSS des Chênes'), ('t-2', 'CSS Autre')").update();
        jdbc.sql("INSERT INTO employees (id, firstName, lastName, tenantId) VALUES ('emp-marie','Marie','Côté','t-1')").update();
        depot = new DepotIncidents(jdbc);
        ConfigurationIncidents config = new ConfigurationIncidents();
        ca.vina.nexu.partage.temps.domaine.Horloge horloge = () -> "2026-09-14T12:00:00.000Z";
        http = new ClientHttpEssai(new ControleurSecurite(config.lectureIncidents(depot, new RenduPdfIncidents(), horloge), config.gestionnaireIncidents(depot, horloge), new DepotModulesActives(jdbc), new GardeAcces(new RegistrePolitiqueAcces())));
    }

    private String id(MvcResult r) throws Exception {
        return r.getResponse().getContentAsString().replaceAll(".*\"id\":\"([^\"]+)\".*", "$1");
    }

    @Test
    void gardesVocabulaireEtConsignation() throws Exception {
        http.get("/api/securite/incidents", null).andExpect(status().isUnauthorized());
        http.get("/api/securite/incidents", SANS_MODULE).andExpect(status().isForbidden()).andExpect(jsonPath("$.errors[0]", containsString("module 'confidentialite'")));
        http.post("/api/securite/incidents", RH, BASE + "}").andExpect(status().isForbidden());
        http.get("/api/securite/incidents/vocabulaire", ADMIN).andExpect(status().isOk()).andExpect(jsonPath("$.gravites", hasSize(3))).andExpect(jsonPath("$.natures[3].code").value("perte"));
        http.post("/api/securite/incidents", ADMIN, BASE + ",\"decouvertLe\":\"2026-08-01\",\"renseignements\":[]}").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0]", containsString("la découverte ne peut précéder la survenue; renseignements visés : au moins un")));
        http.post("/api/securite/incidents", ADMIN, BASE + ",\"responsableId\":\"inconnu\"}").andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors[0]").value("responsable introuvable dans l'établissement"));
        http.post("/api/securite/incidents", ADMIN, null).andExpect(status().isBadRequest());
        MvcResult r = http.post("/api/securite/incidents", ADMIN, BASE + ",\"responsableId\":\"emp-marie\"}").andExpect(status().isCreated())
                .andExpect(jsonPath("$.statut").value("ouvert")).andExpect(jsonPath("$.responsableNom").value("Marie Côté")).andExpect(jsonPath("$.nomEtablissement").value("CSS des Chênes"))
                .andExpect(jsonPath("$.attendue.cai").value(false)).andReturn();
        String id = id(r);
        http.post("/api/securite/incidents", ADMIN, BASE + ",\"survenuLe\":\"2026-09-05\",\"decouvertLe\":\"2026-09-05\",\"nature\":\"perte\",\"gravite\":\"elevee\",\"personnesTouchees\":null}")
                .andExpect(status().isCreated()).andExpect(jsonPath("$.attendue.cai").value(true)).andExpect(jsonPath("$.attendue.personnes").value(true)).andExpect(jsonPath("$.personnesTouchees").isEmpty());
        // Liste du plus récent, filtres, isolation.
        http.get("/api/securite/incidents", ADMIN).andExpect(status().isOk()).andExpect(jsonPath("$", hasSize(2))).andExpect(jsonPath("$[1].id").value(id));
        http.get("/api/securite/incidents?gravite=elevee", ADMIN).andExpect(jsonPath("$", hasSize(1)));
        http.get("/api/securite/incidents?statut=clos", ADMIN).andExpect(jsonPath("$", hasSize(0)));
        http.get("/api/securite/incidents", AUTRE).andExpect(jsonPath("$", hasSize(0)));
        http.post("/api/securite/incidents/" + id + "/clore", AUTRE, null).andExpect(status().isNotFound());
        http.post("/api/securite/incidents", AUTRE, BASE + ",\"description\":\"Une clé USB contenant des relevés de paie a été perdue dans un autobus.\"}").andExpect(status().isCreated());
        http.get("/api/securite/incidents/tous", SUPERADMIN).andExpect(status().isOk()).andExpect(jsonPath("$", hasSize(3)));
        http.get("/api/securite/incidents/tous", RH).andExpect(status().isForbidden());
        http.get("/api/securite/incidents/tous", null).andExpect(status().isUnauthorized());
        http.get("/api/securite/incidents/tous", new ContexteRequete("usr-admin-global", "admin", "admin", null, null)).andExpect(status().isOk());
        // Modifier, notifier, clore ; clos = notifications seulement ; déjà clos = 409 ; jamais de suppression.
        http.put("/api/securite/incidents/" + id, ADMIN, "{\"personnesTouchees\":4,\"notifieCai\":true,\"notifieCaiLe\":\"2026-09-04\"}").andExpect(status().isOk())
                .andExpect(jsonPath("$.personnesTouchees").value(4)).andExpect(jsonPath("$.notifieCaiLe").value("2026-09-04")).andExpect(jsonPath("$.responsableNom").value("Marie Côté"));
        http.put("/api/securite/incidents/" + id, ADMIN, "{\"notifiePersonnes\":false,\"notifiePersonnesLe\":\"2026-09-04\"}").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0]").value("notifiePersonnesLe : une date sans notification déclarée"));
        http.put("/api/securite/incidents/" + id, ADMIN, "{\"responsableId\":null}").andExpect(status().isOk()).andExpect(jsonPath("$.responsableNom").isEmpty());
        http.put("/api/securite/incidents/" + id, ADMIN, null).andExpect(status().isOk());
        http.post("/api/securite/incidents/" + id + "/clore", ADMIN, null).andExpect(status().isOk()).andExpect(jsonPath("$.statut").value("clos"));
        http.post("/api/securite/incidents/" + id + "/clore", ADMIN, null).andExpect(status().isConflict()).andExpect(jsonPath("$.errors[0]").value("incident déjà clos"));
        http.put("/api/securite/incidents/" + id, ADMIN, "{\"description\":\"Une autre description, plus longue que vingt.\",\"notifiePersonnes\":true}").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0]").value("incident clos : seules les notifications se modifient (refusé : description)"));
        http.put("/api/securite/incidents/" + id, ADMIN, "{\"notifiePersonnes\":true,\"notifiePersonnesLe\":\"2026-09-08\"}").andExpect(status().isOk())
                .andExpect(jsonPath("$.notifiePersonnesLe").value("2026-09-08")).andExpect(jsonPath("$.personnesTouchees").value(4));
        http.put("/api/securite/incidents/" + id, ADMIN, "{\"notifiePersonnes\":true,\"notifiePersonnesLe\":\"hier\"}").andExpect(status().isBadRequest());
        http.put("/api/securite/incidents/inconnu", ADMIN, "{}").andExpect(status().isNotFound());
        http.delete("/api/securite/incidents/" + id, ADMIN).andExpect(status().isMethodNotAllowed());
        http.get("/api/securite/incidents?statut=clos", ADMIN).andExpect(jsonPath("$", hasSize(1)));
        // Exports : CSV (BOM, filtres dans le nom), PDF non porté (501, motif écrit).
        MvcResult csv = http.get("/api/securite/incidents.csv?gravite=elevee", ADMIN).andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", containsString("registre-incidents-elevee.csv"))).andReturn();
        assertThat(csv.getResponse().getContentAsString()).contains("\"Perte\";\"Élevée\"").startsWith("\uFEFF");
        assertThat(csv.getResponse().getContentAsString().split("\r\n")).hasSize(2);
        MvcResult tousCsv = http.get("/api/securite/incidents/tous.csv", SUPERADMIN).andExpect(status().isOk()).andReturn();
        assertThat(tousCsv.getResponse().getContentAsString().split("\r\n")[0]).startsWith("\uFEFF\"Établissement\"");
        assertThat(tousCsv.getResponse().getContentAsString().split("\r\n")).hasSize(4);
        http.get("/api/securite/incidents/tous.csv", RH).andExpect(status().isForbidden());
        http.get("/api/securite/incidents.csv", SANS_MODULE).andExpect(status().isForbidden());
        byte[] pdf = http.get("/api/securite/incidents.pdf?gravite=elevee", ADMIN).andExpect(status().isOk()).andExpect(content().contentType(MediaType.APPLICATION_PDF))
                .andExpect(header().string("Content-Disposition", "attachment; filename=\"registre-incidents-elevee.pdf\"")).andReturn().getResponse().getContentAsByteArray();
        assertThat(new String(pdf, 0, 5, java.nio.charset.StandardCharsets.ISO_8859_1)).isEqualTo("%PDF-");
        try (org.apache.pdfbox.pdmodel.PDDocument d = org.apache.pdfbox.Loader.loadPDF(pdf)) {
            String texte = new org.apache.pdfbox.text.PDFTextStripper().getText(d);
            assertThat(texte).contains("Registre des incidents de confidentialité").contains("produit le 2026-09-14").contains("Filtres : gravité : elevee").contains("Description :").contains("CAI notifiée :");
            assertThat(d.getPage(0).getMediaBox().getWidth()).isGreaterThan(d.getPage(0).getMediaBox().getHeight());   // paysage
        }
        http.get("/api/securite/incidents.pdf", SANS_MODULE).andExpect(status().isForbidden());
        byte[] tous = http.get("/api/securite/incidents/tous.pdf?statut=clos", SUPERADMIN).andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray();
        try (org.apache.pdfbox.pdmodel.PDDocument d = org.apache.pdfbox.Loader.loadPDF(tous)) {
            assertThat(new org.apache.pdfbox.text.PDFTextStripper().getText(d)).contains("Tous les établissements").contains("clos le 2026-09-14 - CSS des Chênes");
        }
        byte[] vide = http.get("/api/securite/incidents/tous.pdf?gravite=inexistante", SUPERADMIN).andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray();
        try (org.apache.pdfbox.pdmodel.PDDocument d = org.apache.pdfbox.Loader.loadPDF(vide)) {
            assertThat(new org.apache.pdfbox.text.PDFTextStripper().getText(d)).contains("Aucun incident consigné.");
        }
        http.get("/api/securite/incidents/tous.pdf", RH).andExpect(status().isForbidden());
        http.get("/api/securite/incidents/vocabulaire", SANS_MODULE).andExpect(status().isForbidden());
        // Le dépôt, hors HTTP : lecture et clôture par instant explicite.
        assertThat(depot.lister("t-1", null, null)).hasSize(2);
        assertThat(depot.clore("t-1", depot.lister("t-1", "ouvert", null).get(0).id(), "2026-09-13T00:00:00.000Z").closLe()).isEqualTo("2026-09-13");
    }
}
