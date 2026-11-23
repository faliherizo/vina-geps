package ca.vina.nexu.alertes.infrastructure.web;

import ca.vina.nexu.ClientHttpEssai;
import ca.vina.nexu.SchemaEssai;
import ca.vina.nexu.acces.infrastructure.persistance.RegistrePolitiqueAcces;
import ca.vina.nexu.alertes.infrastructure.configuration.ConfigurationAlertes;
import ca.vina.nexu.alertes.infrastructure.persistance.AdaptateurAnnuaireAlertes;
import ca.vina.nexu.communication.infrastructure.persistance.DepotGroupesTransverses;
import ca.vina.nexu.communication.domaine.GroupesDeTravailPort;
import ca.vina.nexu.communication.domaine.LectureListesDistributionPort;
import ca.vina.nexu.communication.domaine.ListesDistribution;
import ca.vina.nexu.partage.modules.infrastructure.DepotModulesActives;
import ca.vina.nexu.partage.securite.ContexteRequete;
import ca.vina.nexu.partage.web.GardeAcces;
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
import java.util.Optional;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** ST-G12 : une note courte et datée sur la fiche d'un destinataire, tenue et lue par les expéditeurs autorisés. */
@DisplayName("/api/alertes/notes — écrire, remplacer, lire plusieurs, effacer ; personne inconnue ; droit d'envoi exigé")
class ControleurNotesHttpTest {

    private static final String T = "T1";
    private static final String N = "/api/alertes/notes";
    private static final ContexteRequete CHEF = new ContexteRequete("u-chef", "chef", "manager", null, T);
    private static final ContexteRequete JULIE = new ContexteRequete("u-julie", "julie", "employee", null, T);

    private ClientHttpEssai http;

    @BeforeEach
    void preparer(@TempDir Path dossier) {
        JdbcClient jdbc = SchemaEssai.base(dossier);
        jdbc.sql("INSERT INTO tenant_quotas (tenantId, maxTeachers, maxContractsPerYear, modules) VALUES (?, 1, 1, '[\"alertes\"]')").param(T).update();
        for (String login : List.of("chef", "julie", "marc")) {
            jdbc.sql("INSERT INTO users (id, username, loginName, tenantId, passwordHash, role, createdAt) VALUES (?, ?, ?, ?, 'x', 'employee', 'x')")
                    .params("u-" + login, login + "@@" + T, login, T).update();
        }
        new ca.vina.nexu.effectifs.infrastructure.persistance.SchemaPlanification(jdbc);
        AdaptateurAnnuaireAlertes annuaire = new AdaptateurAnnuaireAlertes(new GroupesDeTravailPort() {
            @Override
            public List<Groupe> groupes(String tenantId) {
                return List.of();
            }

            @Override
            public List<Membre> membres(String tenantId, String groupeId) {
                return List.of();
            }
        }, new LectureListesDistributionPort() {
            @Override
            public Optional<ListesDistribution.Liste> lire(String tenantId, String listeId) {
                return Optional.empty();
            }

            @Override
            public List<ListesDistribution.Liste> visibles(String tenantId, String acteurId) {
                return List.of();
            }

            @Override
            public boolean nomPris(String tenantId, String auteurId, String nom) {
                return false;
            }

            @Override
            public List<String> membresActifsDuGroupe(String tenantId, String groupeId) {
                return List.of();
            }
        }, new DepotGroupesTransverses(jdbc), jdbc);
        Clock horloge = Clock.fixed(Instant.parse("2026-11-15T12:00:00Z"), ZoneOffset.UTC);
        http = new ClientHttpEssai(new ControleurNotes(new GardeAlertes(new GardeAcces(new RegistrePolitiqueAcces()), new DepotModulesActives(jdbc)),
                new ConfigurationAlertes().gestionnaireNotes(jdbc, annuaire, horloge)));
    }

    @Test
    @DisplayName("une note se pose, se remplace, se lit pour plusieurs, s'efface ; datée et signée")
    void cycle() throws Exception {
        http.put(N + "/julie", CHEF, "{\"texte\":\"  Joindre sur le cellulaire de garde  \"}").andExpect(status().isOk())
                .andExpect(jsonPath("$.texte").value("Joindre sur le cellulaire de garde")).andExpect(jsonPath("$.ecriteLe").value("2026-11-15T12:00:00Z"))
                .andExpect(jsonPath("$.ecritePar").value("chef"));
        http.put(N + "/julie", CHEF, "{\"texte\":\"Pas d'appel avant 7 h\"}").andExpect(jsonPath("$.texte").value("Pas d'appel avant 7 h"));
        http.put(N + "/marc", CHEF, "{\"texte\":\"En congé jusqu'au 20\"}").andExpect(status().isOk());
        http.get(N + "?personnes=julie, marc,,inconnu,julie", CHEF).andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].personId").value("julie")).andExpect(jsonPath("$[0].texte").value("Pas d'appel avant 7 h"));
        http.get(N, CHEF).andExpect(jsonPath("$.length()").value(0));
        http.delete(N + "/marc", CHEF).andExpect(status().isOk()).andExpect(jsonPath("$.effacee").value(true));
        http.delete(N + "/marc", CHEF).andExpect(status().isNotFound()).andExpect(jsonPath("$.errors[0]").value("aucune note sur cette personne"));
        http.get(N + "?personnes=marc", CHEF).andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    @DisplayName("une personne inconnue, un texte vide ou trop long sont refusés ; sans le droit d'envoi, rien")
    void refus() throws Exception {
        http.put(N + "/fantome", CHEF, "{\"texte\":\"x\"}").andExpect(status().isNotFound()).andExpect(jsonPath("$.errors[0]").value("personne introuvable"));
        http.put(N + "/julie", CHEF, "{\"texte\":\"  \"}").andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors[0]").value("« texte » est requis"));
        http.put(N + "/julie", CHEF, "{\"texte\":\"" + "x".repeat(281) + "\"}").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0]").value("« texte » : 280 caractères au plus"));
        http.put(N + "/julie", JULIE, "{\"texte\":\"x\"}").andExpect(status().isForbidden());
        http.get(N + "?personnes=julie", JULIE).andExpect(status().isForbidden());
    }
}
