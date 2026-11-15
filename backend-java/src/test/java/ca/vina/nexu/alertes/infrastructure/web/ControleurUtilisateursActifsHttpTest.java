package ca.vina.nexu.alertes.infrastructure.web;

import ca.vina.nexu.ClientHttpEssai;
import ca.vina.nexu.SchemaEssai;
import ca.vina.nexu.acces.infrastructure.persistance.RegistrePolitiqueAcces;
import ca.vina.nexu.alertes.application.requetes.LectureUtilisateursActifs;
import ca.vina.nexu.alertes.domaine.UtilisateursActifs;
import ca.vina.nexu.alertes.infrastructure.configuration.ConfigurationAlertes;
import ca.vina.nexu.partage.modules.infrastructure.DepotModulesActives;
import ca.vina.nexu.partage.securite.ContexteRequete;
import ca.vina.nexu.partage.web.GardeAcces;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.jdbc.core.simple.JdbcClient;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * ST-G08 : une personne compte une fois par mois civil de Toronto, dès qu'une tentative la vise — remise, étape de
 * cascade, appel —, avec sa première tentative et l'alerte en cause.
 */
@DisplayName("/api/alertes/utilisateurs-actifs — compte du mois, frontière de Toronto, justificatif exportable")
class ControleurUtilisateursActifsHttpTest {

    private static final String T = "T1";
    private static final String A = "/api/alertes/utilisateurs-actifs";
    private static final ContexteRequete ADMIN = new ContexteRequete("u-adm", "adm", "admin", null, T);
    private static final ContexteRequete CHEF = new ContexteRequete("u-chef", "chef", "manager", null, T);

    private JdbcClient jdbc;
    private ClientHttpEssai http;

    @BeforeEach
    void preparer(@TempDir Path dossier) {
        jdbc = SchemaEssai.base(dossier);
        jdbc.sql("INSERT INTO tenant_quotas (tenantId, maxTeachers, maxContractsPerYear, modules) VALUES (?, 1, 1, '[\"alertes\"]')").param(T).update();
        alerte("a-oct", "Octobre", "2026-11-01T03:59:59Z");
        alerte("a-nov", "Code bleu", "2026-11-01T04:00:00Z");
        alerte("a-dec", "Décembre", "2026-12-01T05:00:00Z");
        // Julie : le 31 octobre à 23 h 59 à Toronto (en octobre), puis le 1er novembre à 0 h (en novembre).
        remise("a-oct", "julie", "Julie Roy", "2026-11-01T03:59:59Z");
        remise("a-nov", "julie", "Julie Roy", "2026-11-01T04:00:00.000Z");
        remise("a-nov", "marc", "Marc Dubé", "2026-11-03T10:00:00Z");
        // Léa n'est visée que par une étape de cascade ; Rémi, que par un appel. Zoé, en décembre seulement.
        jdbc.sql("INSERT INTO alr_cascade_traces (tenantId, alerteId, rang, evenement, etape, personnes, instant, par) VALUES (?, 'a-nov', 1, 'etape', 0, '[\"lea\",\"julie\"]', '2026-11-20T12:00:00Z', 'cascade'), "
                + "(?, 'a-nov', 2, 'armee', 0, '[\"intrus\"]', '2026-11-20T12:00:00Z', 'chef')").params(T, T).update();
        jdbc.sql("INSERT INTO alr_appels (id, tenantId, alerteId, etape, personId, resultat, lanceLe) VALUES ('ap', ?, 'a-nov', 1, 'remi', 'repondu', '2026-11-30T23:00:00Z')")
                .param(T).update();
        remise("a-dec", "zoe", "Zoé Lavoie", "2026-12-01T05:00:00Z");
        Clock horloge = Clock.fixed(Instant.parse("2026-11-15T12:00:00Z"), ZoneOffset.UTC);
        ConfigurationAlertes c = new ConfigurationAlertes();
        LectureUtilisateursActifs lecture = c.lectureUtilisateursActifs(jdbc);
        http = new ClientHttpEssai(new ControleurUtilisateursActifs(new GardeAlertes(new GardeAcces(new RegistrePolitiqueAcces()), new DepotModulesActives(jdbc)),
                lecture, horloge));
    }

    private void alerte(String id, String titre, String envoyeeLe) {
        jdbc.sql("INSERT INTO alr_alertes (id, tenantId, titre, corps, priorite, expediteurId, envoyeeLe) VALUES (?, ?, ?, 'x', 'urgente', 'chef', ?)")
                .params(id, T, titre, envoyeeLe).update();
    }

    private void remise(String alerteId, String personId, String nom, String envoyeeLe) {
        jdbc.sql("INSERT INTO alr_remises (tenantId, alerteId, personId, nom, envoyeeLe) VALUES (?, ?, ?, ?, ?)").params(T, alerteId, personId, nom, envoyeeLe).update();
    }

    @Test
    @DisplayName("novembre : quatre personnes, une fois chacune, à leur première tentative ; la frontière est minuit à Toronto")
    void novembre() throws Exception {
        http.get(A + "?mois=2026-11", ADMIN).andExpect(status().isOk()).andExpect(jsonPath("$.mois").value("2026-11"))
                .andExpect(jsonPath("$.total").value(4))
                .andExpect(jsonPath("$.actifs[*].nom").value(org.hamcrest.Matchers.contains("Julie Roy", "lea", "Marc Dubé", "remi")))
                .andExpect(jsonPath("$.actifs[0].premiereTentative").value("2026-11-01T04:00:00.000Z"))
                .andExpect(jsonPath("$.actifs[0].alerteId").value("a-nov")).andExpect(jsonPath("$.actifs[3].titre").value("Code bleu"));
        // Sans mois : le mois en cours à Toronto ; octobre compte Julie seule ; décembre, Zoé.
        http.get(A, ADMIN).andExpect(jsonPath("$.mois").value("2026-11"));
        http.get(A + "?mois=2026-10", ADMIN).andExpect(jsonPath("$.total").value(1)).andExpect(jsonPath("$.actifs[0].alerteId").value("a-oct"));
        http.get(A + "?mois=2026-12", ADMIN).andExpect(jsonPath("$.total").value(1)).andExpect(jsonPath("$.actifs[0].nom").value("Zoé Lavoie"));
        http.get(A + "?mois=novembre", ADMIN).andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors[0]").value("« mois » : AAAA-MM, par exemple 2026-11"));
        http.get(A + "?mois=2026-11", CHEF).andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("le justificatif s'exporte au même mois, en CSV ou en JSON ; un format inconnu est refusé")
    void export() throws Exception {
        String csv = http.get(A + "/export?mois=2026-11&format=csv", ADMIN).andExpect(status().isOk()).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertThat(csv).contains("Julie Roy").contains("Code bleu").contains("2026-11-30T23:00:00Z").doesNotContain("Zoé");
        http.get(A + "/export?mois=2026-11&format=json", ADMIN).andExpect(status().isOk());
        http.get(A + "/export?mois=2026-11&format=doc", ADMIN).andExpect(status().isBadRequest());
        http.get(A + "/export?format=csv", CHEF).andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("le mois se borne à Toronto, heure d'été comprise")
    void bornes() {
        UtilisateursActifs.Mois juillet = UtilisateursActifs.Mois.de("2026-07");
        assertThat(juillet.debut()).isEqualTo(Instant.parse("2026-07-01T04:00:00Z"));
        assertThat(juillet.fin()).isEqualTo(Instant.parse("2026-08-01T04:00:00Z"));
        assertThat(UtilisateursActifs.Mois.de("2026-12").fin()).isEqualTo(Instant.parse("2027-01-01T05:00:00Z"));
        assertThat(juillet.contient("2026-07-01T04:00:00Z")).isTrue();
        assertThat(juillet.contient("2026-08-01T04:00:00Z")).isFalse();
        assertThatThrownBy(() -> UtilisateursActifs.Mois.de(null)).hasMessageContaining("AAAA-MM");
    }
}
