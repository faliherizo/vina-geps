package ca.vina.nexu.saas.infrastructure.web;

import ca.vina.nexu.ClientHttpEssai;
import ca.vina.nexu.acces.infrastructure.persistance.RegistrePolitiqueAcces;
import ca.vina.nexu.partage.securite.ContexteRequete;
import ca.vina.nexu.partage.web.GardeAcces;
import ca.vina.nexu.saas.domaine.AnnexeDestruction;
import ca.vina.nexu.saas.domaine.AttestationsDestruction;
import ca.vina.nexu.saas.domaine.Destruction;
import ca.vina.nexu.saas.infrastructure.configuration.ConfigurationAttestation;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** {@code GET /api/saas/tenants/{id}/attestation-destruction/annexe-c} — FX-AO-20260810, annexe 10.18.04 C. */
@DisplayName("Annexe 10.18.04 C par HTTP")
class ControleurAnnexeDestructionHttpTest {

    private static final ContexteRequete ADMIN_GLOBAL = new ContexteRequete("usr-g", "g", "admin", null, null);
    /** 3 avril 2031, 1 h UTC : encore le 2 avril à Montréal — la date de l'attestation est celle du Québec. */
    private static final Clock HORLOGE = Clock.fixed(Instant.parse("2031-04-03T01:00:00Z"), ZoneOffset.UTC);
    private static final Destruction.Attestation ATTESTATION = new Destruction.Attestation("a-1", "t-9", "Santé Québec Laurentides",
            "2031-04-02T14:00:00Z", "superadmin", null, List.of(new Destruction.Table("employees", 3)), 3, "ab12");
    private static final AttestationsDestruction DEPOT = id -> "t-9".equals(id) ? Optional.of(ATTESTATION) : Optional.empty();

    private static ClientHttpEssai client(AnnexeDestruction.Signataire signataire) {
        return new ClientHttpEssai(new ControleurAnnexeDestruction(new GardeAcces(new RegistrePolitiqueAcces()), DEPOT, signataire, HORLOGE));
    }

    private static final ClientHttpEssai HTTP = client(new ConfigurationAttestation().signataireAttestation("Claire Gagnon", "VINA IT Solution", "Montréal"));

    @Test
    @DisplayName("rendue en texte, datée à Montréal, avec l'échéance de transmission")
    void annexe() throws Exception {
        HTTP.get("/api/saas/tenants/t-9/attestation-destruction/annexe-c?finContrat=2031-03-31", ADMIN_GLOBAL).andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("text/plain"))
                .andExpect(content().string(containsString("Date : 2031-04-02")))
                .andExpect(content().string(containsString("Je, soussigné(e), Claire Gagnon")))
                .andExpect(content().string(containsString("au plus tard le 2031-05-30")));
    }

    @Test
    @DisplayName("refus nommés : date absente ou mal formée 400, aucune destruction 404, signataire non configuré 409, hors administration 403")
    void refus() throws Exception {
        HTTP.get("/api/saas/tenants/t-9/attestation-destruction/annexe-c", ADMIN_GLOBAL).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0]").value("finContrat : date de fin du contrat attendue (AAAA-MM-JJ)"));
        HTTP.get("/api/saas/tenants/t-9/attestation-destruction/annexe-c?finContrat=demain", ADMIN_GLOBAL).andExpect(status().isBadRequest());
        HTTP.get("/api/saas/tenants/t-1/attestation-destruction/annexe-c?finContrat=2031-03-31", ADMIN_GLOBAL).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errors[0]").value("aucune attestation : les données n'ont pas été détruites"));
        client(new AnnexeDestruction.Signataire("", "", "")).get("/api/saas/tenants/t-9/attestation-destruction/annexe-c?finContrat=2031-03-31", ADMIN_GLOBAL)
                .andExpect(status().isConflict()).andExpect(jsonPath("$.errors[0]").value(containsString("SAAS_ATTESTATION_SIGNATAIRE")));
        HTTP.get("/api/saas/tenants/t-9/attestation-destruction/annexe-c?finContrat=2031-03-31", ClientHttpEssai.session("admin", "t-9"))
                .andExpect(status().isForbidden());
    }
}
