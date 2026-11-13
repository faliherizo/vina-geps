package ca.vina.nexu.auth.infrastructure.web;

import ca.vina.nexu.ClientHttpEssai;
import ca.vina.nexu.SchemaEssai;
import ca.vina.nexu.acces.infrastructure.persistance.RegistrePolitiqueAcces;
import ca.vina.nexu.auth.infrastructure.persistance.DepotComptes;
import ca.vina.nexu.auth.infrastructure.persistance.DepotInvitations;
import ca.vina.nexu.auth.infrastructure.persistance.DepotRolesEtablissement;
import ca.vina.nexu.partage.audit.infrastructure.JournalAudit;
import ca.vina.nexu.partage.courrier.infrastructure.CourrierJournal;
import ca.vina.nexu.partage.securite.AdressePublique;
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
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.matchesPattern;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * {@code /api/auth/invitations} — jumeau Java de {@code auth-invitations.integration.test.ts} :
 * lien à usage unique dont seule l'empreinte est gardée, page publique minimale, refus nommés,
 * compte né avec la configuration de sécurité exigée.
 */
@DisplayName("API auth — invitation d'un nouvel utilisateur")
class ControleurInvitationsHttpTest {

    private static final Clock HORLOGE = Clock.fixed(Instant.parse("2026-11-09T12:00:00Z"), ZoneOffset.UTC);
    private static final String FORT = "Lanaudiere-2027!Nexu";
    private static final ContexteRequete ADMIN = new ContexteRequete("usr-admin", "admin", "admin", null, "t-1");
    private static final ContexteRequete GLOBAL = new ContexteRequete("usr-sa", "sa", "superadmin", null, null);
    private static final ContexteRequete EMPLOYE = new ContexteRequete("usr-emp", "emp", "employee", null, "t-1");
    private static final String I = "/api/auth/invitations";

    private ClientHttpEssai http;
    private JdbcClient jdbc;
    private CourrierJournal courrier;

    @BeforeEach
    void preparer(@TempDir Path dossier) {
        jdbc = SchemaEssai.base(dossier);
        RegistrePolitiqueAcces acces = new RegistrePolitiqueAcces();
        courrier = new CourrierJournal();
        http = new ClientHttpEssai(new ControleurInvitations(new GardeAcces(acces), new DepotInvitations(jdbc), new DepotComptes(jdbc, HORLOGE), HORLOGE,
                new DepotRolesEtablissement(jdbc, acces, HORLOGE), AdressePublique.fixe("https://nexu.exemple"), courrier, new JournalAudit(jdbc)));
    }

    private Map<String, Object> inviter(String corps) throws Exception {
        String r = http.post(I, ADMIN, corps).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        String lien = JsonPath.read(r, "$.lien");
        return Map.of("jeton", lien.substring(lien.lastIndexOf('/') + 1), "id", JsonPath.read(r, "$.invitation.id"));
    }

    @Test
    @DisplayName("pose une invitation, rend le lien une seule fois, l'envoie, et ne garde que l'empreinte")
    void pose() throws Exception {
        String r = http.post(I, ADMIN, "{\"courriel\":\"Nadia.Cote@sante.qc.ca\",\"username\":\"ncote\",\"role\":\"employee\"}")
                .andExpect(status().isCreated()).andExpect(jsonPath("$.courrielEnvoyeA").value("nadia.cote@sante.qc.ca"))
                .andExpect(jsonPath("$.invitation.username").value("ncote")).andExpect(jsonPath("$.invitation.etat").value("offerte"))
                .andExpect(jsonPath("$.lien").value(matchesPattern("^https://nexu\\.exemple/admin/invitation/[A-Za-z0-9_-]{16,}$")))
                .andReturn().getResponse().getContentAsString();
        String lien = JsonPath.read(r, "$.lien");
        String jeton = lien.substring(lien.lastIndexOf('/') + 1);
        String empreinte = jdbc.sql("SELECT empreinte FROM auth_invitations").query(String.class).single();
        assertThat(empreinte).isNotEqualTo(jeton).matches("^[0-9a-f]{64}$");
        assertThat(http.get(I, ADMIN).andReturn().getResponse().getContentAsString()).doesNotContain(jeton).contains("ncote");
        assertThat(courrier.envoyes()).hasSize(1);
        assertThat(courrier.envoyes().getFirst().to()).isEqualTo("nadia.cote@sante.qc.ca");
        assertThat(courrier.envoyes().getFirst().text()).contains(lien).contains("expire le 2026-11-16");
    }

    @Test
    @DisplayName("refuse courriel invalide, nom trop court, rôle inconnu, validité hors bornes, invitation déjà en cours")
    void refus() throws Exception {
        http.post(I, ADMIN, "{\"courriel\":\"pas-un-courriel\",\"username\":\"abc\",\"role\":\"employee\"}").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0]").value(containsString("un courriel valide est obligatoire")));
        http.post(I, ADMIN, "{\"courriel\":\"a@b.ca\",\"username\":\"ab\",\"role\":\"employee\"}")
                .andExpect(jsonPath("$.errors[0]").value(containsString("de 3 à 64 caractères")));
        http.post(I, ADMIN, "{\"courriel\":\"a@b.ca\",\"username\":\"abc\",\"role\":\"sorcier\"}")
                .andExpect(jsonPath("$.errors[0]").value(containsString("rôle inconnu")));
        http.post(I, ADMIN, "{\"courriel\":\"a@b.ca\",\"username\":\"abc\",\"role\":\"employee\",\"jours\":90}")
                .andExpect(jsonPath("$.errors[0]").value(containsString("de 1 à 30 jours")));
        inviter("{\"courriel\":\"n@sante.qc.ca\",\"username\":\"ncote\",\"role\":\"employee\"}");
        http.post(I, ADMIN, "{\"courriel\":\"autre@sante.qc.ca\",\"username\":\"ncote\",\"role\":\"employee\"}").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0]").value(containsString("une invitation est déjà en cours")));
        http.post(I, EMPLOYE, "{}").andExpect(status().isForbidden());
        http.get(I, EMPLOYE).andExpect(status().isForbidden());
        http.get(I, null).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("la page publique montre le minimum ; un lien inconnu se distingue ; un mot de passe faible est refusé sans épuiser le lien")
    void pagePublique() throws Exception {
        String jeton = (String) inviter("{\"courriel\":\"n@sante.qc.ca\",\"username\":\"ncote\",\"role\":\"employee\"}").get("jeton");
        String vue = http.get(I + "/jeton/" + jeton, null).andExpect(status().isOk()).andExpect(jsonPath("$.username").value("ncote"))
                .andExpect(jsonPath("$.courriel").exists()).andExpect(jsonPath("$.expireLe").exists())
                .andReturn().getResponse().getContentAsString();
        assertThat(vue).doesNotContain("employee").doesNotContain("t-1");
        http.get(I + "/jeton/ceci-nest-pas-un-jeton-valide", null).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0]").value(containsString("n'existe pas")));
        http.post(I + "/jeton/" + jeton + "/accepter", null, "{\"password\":\"motdepasse\"}").andExpect(status().isBadRequest());
        http.get(I + "/jeton/" + jeton, null).andExpect(status().isOk());
    }

    @Test
    @DisplayName("accepter fait naître le compte, sécurité exigée ; le lien est épuisé ; une invitation acceptée ne se révoque pas")
    void acceptation() throws Exception {
        Map<String, Object> pose = inviter("{\"courriel\":\"Nadia.Cote@sante.qc.ca\",\"username\":\"ncote\",\"role\":\"employee\"}");
        String r = http.post(I + "/jeton/" + pose.get("jeton") + "/accepter", null, "{\"username\":\"ncote\",\"password\":\"" + FORT + "\"}")
                .andExpect(status().isCreated()).andExpect(jsonPath("$.username").value("ncote")).andExpect(jsonPath("$.prochaineEtape").value("connexion"))
                .andReturn().getResponse().getContentAsString();
        Map<String, Object> compte = jdbc.sql("SELECT mustChangePassword, securitySetupRequired, email, role, tenantId FROM users WHERE id = ?")
                .param((String) JsonPath.read(r, "$.compteId")).query().singleRow();
        assertThat(compte).containsEntry("mustChangePassword", 0).containsEntry("securitySetupRequired", 1)
                .containsEntry("email", "nadia.cote@sante.qc.ca").containsEntry("role", "employee").containsEntry("tenantId", "t-1");
        http.get(I + "/jeton/" + pose.get("jeton"), null).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0]").value(containsString("a déjà servi")));
        http.post(I + "/jeton/" + pose.get("jeton") + "/accepter", null, "{\"username\":\"ncote2\",\"password\":\"" + FORT + "\"}")
                .andExpect(status().isBadRequest());
        http.delete(I + "/" + pose.get("id"), ADMIN).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0]").value(containsString("Archiver le compte")));
        http.post(I, ADMIN, "{\"courriel\":\"n@sante.qc.ca\",\"username\":\"ncote\",\"role\":\"employee\"}").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0]").value(containsString("a déjà un compte")));
    }

    @Test
    @DisplayName("révoquer une invitation en attente éteint le lien ; un lien expiré dit d'en demander un nouveau ; le compte global invite")
    void revocationEtExpiration() throws Exception {
        Map<String, Object> roy = inviter("{\"courriel\":\"jroy@sante.qc.ca\",\"username\":\"jroy\",\"role\":\"employee\"}");
        http.delete(I + "/" + roy.get("id"), ADMIN).andExpect(jsonPath("$.etat").value("revoquee"));
        http.get(I + "/jeton/" + roy.get("jeton"), null).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0]").value(containsString("a été retirée")));
        http.delete(I + "/absente", ADMIN).andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors[0]").value("invitation introuvable"));

        Map<String, Object> pare = inviter("{\"courriel\":\"lpare@sante.qc.ca\",\"username\":\"lpare\",\"role\":\"employee\",\"jours\":1}");
        jdbc.sql("UPDATE auth_invitations SET expireLe = '2020-01-01T00:00:00.000Z' WHERE id = ?").param(pare.get("id")).update();
        http.get(I + "/jeton/" + pare.get("jeton"), null).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0]").value(containsString("a expiré")));
        http.get(I, ADMIN).andExpect(jsonPath("$[?(@.username=='lpare')].etat").value(org.hamcrest.Matchers.hasItem("expiree")));
        http.post(I, GLOBAL, "{\"courriel\":\"g@sante.qc.ca\",\"username\":\"global1\",\"role\":\"employee\"}").andExpect(status().isCreated());
    }
}
