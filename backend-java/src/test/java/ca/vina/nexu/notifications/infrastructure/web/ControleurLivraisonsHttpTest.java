package ca.vina.nexu.notifications.infrastructure.web;

import ca.vina.nexu.ClientHttpEssai;
import ca.vina.nexu.SchemaEssai;
import ca.vina.nexu.acces.infrastructure.persistance.RegistrePolitiqueAcces;
import ca.vina.nexu.notifications.domaine.JournalTextos;
import ca.vina.nexu.notifications.domaine.PousseeNative;
import ca.vina.nexu.notifications.domaine.PousseeWeb;
import ca.vina.nexu.notifications.domaine.Textos;
import ca.vina.nexu.notifications.infrastructure.configuration.ConfigurationFournisseurs;
import ca.vina.nexu.notifications.infrastructure.persistance.DepotAbonnementsPoussee;
import ca.vina.nexu.notifications.infrastructure.persistance.DepotAppareils;
import ca.vina.nexu.notifications.infrastructure.persistance.DepotJournalTextos;
import ca.vina.nexu.notifications.infrastructure.persistance.DepotLivraisons;
import ca.vina.nexu.notifications.infrastructure.persistance.DepotNotifications;
import ca.vina.nexu.notifications.infrastructure.persistance.DepotPreferences;
import ca.vina.nexu.notifications.infrastructure.persistance.DepotRegroupement;
import ca.vina.nexu.partage.courrier.infrastructure.CourrierJournal;
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
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * {@code /api/notifications/livraisons} et {@code /regroupement} — le journal des envois et sa
 * relance (maquette P69), le regroupement des avis (BF-124) ; jumeau de {@code routesAvis.ts} et
 * de {@code notifications-regroupement.integration.test.ts} pour ce qui ne dépend pas des horaires.
 */
@DisplayName("API notifications — journal des livraisons, relance, regroupement des avis")
class ControleurLivraisonsHttpTest {

    private static final Clock HORLOGE = Clock.fixed(Instant.parse("2026-11-09T12:00:00Z"), ZoneOffset.UTC);
    private static final ContexteRequete ADMIN = new ContexteRequete("usr-admin", "admin", "admin", null, "t-1");
    private static final ContexteRequete EMPLOYE = new ContexteRequete("usr-emp", "emp", "employee", null, "t-1");

    private ClientHttpEssai http;
    private JdbcClient jdbc;

    @BeforeEach
    void preparer(@TempDir Path dossier) {
        jdbc = SchemaEssai.base(dossier);
        DepotPreferences preferences = new DepotPreferences(jdbc, HORLOGE);
        DepotNotifications notifications = new DepotNotifications(jdbc, HORLOGE, new DepotAbonnementsPoussee(jdbc, HORLOGE,
                new ConfigurationFournisseurs.ClesInstance(new PousseeWeb.ClesVapid("pub", "priv", "mailto:x"), false),
                new PousseeWeb.PousseeSimulee(Set.of(), Set.of()), new DepotAppareils(jdbc, HORLOGE, new PousseeNative.Simulee())),
                new CourrierJournal(), preferences, new DepotJournalTextos(jdbc, HORLOGE,
                new Textos.SmsSimule(List.of(), () -> "2026-11-09T12:00:00.000Z", "+15145550000"), JournalTextos.Reglages.de(Map.of())));
        http = new ClientHttpEssai(new ControleurLivraisons(new GardeAcces(new RegistrePolitiqueAcces()), new DepotLivraisons(jdbc), notifications,
                new DepotRegroupement(jdbc, HORLOGE, preferences)));
    }

    private void envoi(String id, String statut, String erreur, String cree) {
        jdbc.sql("INSERT INTO varpay_notifications (id, tenantId, channel, recipientType, recipientId, subject, body, status, createdAt, sentAt, error, evenement, tentative)"
                        + " VALUES (?, 't-1', 'email', 'establishment', 'etab-1', 'Horaire publié', 'Corps', ?, ?, NULL, ?, 'horaire.publie', 1)")
                .params(id, statut, cree, erreur).update();
    }

    @Test
    @DisplayName("le journal : résumé par état, motifs expurgés, filtres, réservé à l'encadrement")
    void journal() throws Exception {
        envoi("n-1", "failed", "boîte pleine pour nadia@sante.qc.ca", "2026-11-09T10:00:00.000Z");
        envoi("n-2", "sent", null, "2026-11-09T11:00:00.000Z");
        http.get("/api/notifications/livraisons", ADMIN).andExpect(status().isOk()).andExpect(jsonPath("$.resume.total").value(2))
                .andExpect(jsonPath("$.lignes", hasSize(2))).andExpect(jsonPath("$.lignes[?(@.id=='n-1')].erreur").value(org.hamcrest.Matchers.hasItem(
                        containsString("(adresse masquée)"))));
        http.get("/api/notifications/livraisons?etat=failed&limite=abc", ADMIN).andExpect(jsonPath("$.lignes", hasSize(1)));
        http.get("/api/notifications/livraisons?evenement=horaire.publie&canal=email&destinataire=etab-1&limite=1", ADMIN)
                .andExpect(jsonPath("$.lignes", hasSize(1))).andExpect(jsonPath("$.resume.total").value(2));
        http.get("/api/notifications/livraisons", EMPLOYE).andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("la relance : un échec repart, tentative comptée et événement gardé ; parti, inconnu refusés")
    void relance() throws Exception {
        envoi("n-1", "failed", "délai dépassé", "2026-11-09T10:00:00.000Z");
        envoi("n-2", "sent", null, "2026-11-09T11:00:00.000Z");
        http.post("/api/notifications/livraisons/n-1/relancer", ADMIN, "{}").andExpect(status().isCreated())
                .andExpect(jsonPath("$.tentative").value(2)).andExpect(jsonPath("$.evenement").value("horaire.publie"))
                .andExpect(jsonPath("$.etat").value("sent"));
        Map<String, Object> neuve = jdbc.sql("SELECT evenement, tentative FROM varpay_notifications WHERE id NOT IN ('n-1', 'n-2')").query().singleRow();
        assertThat(neuve).containsEntry("evenement", "horaire.publie").containsEntry("tentative", 2);
        http.post("/api/notifications/livraisons/n-2/relancer", ADMIN, "{}").andExpect(status().isConflict())
                .andExpect(jsonPath("$.errors[0]").value(containsString("déjà parti")));
        http.post("/api/notifications/livraisons/absent/relancer", ADMIN, "{}").andExpect(status().isNotFound());
        http.post("/api/notifications/livraisons/n-1/relancer", EMPLOYE, "{}").andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("le regroupement : catalogue servi, défaut de l'établissement, choix personnel qui l'emporte, nul qui rend au défaut")
    void regroupement() throws Exception {
        http.get("/api/notifications/regroupement", EMPLOYE).andExpect(jsonPath("$.modes[*].code").value(is(List.of("un_avis_groupe", "un_avis_par_objet"))))
                .andExpect(jsonPath("$.defautEtablissement").isEmpty()).andExpect(jsonPath("$.mien").isEmpty())
                .andExpect(jsonPath("$.effectif").value("un_avis_groupe"));
        http.put("/api/notifications/regroupement/defaut", EMPLOYE, "{\"mode\":\"un_avis_par_objet\"}").andExpect(status().isForbidden());
        http.put("/api/notifications/regroupement/defaut", ADMIN, "{\"mode\":\"un_avis_par_objet\"}").andExpect(jsonPath("$.mode").value("un_avis_par_objet"));
        http.get("/api/notifications/regroupement", EMPLOYE).andExpect(jsonPath("$.effectif").value("un_avis_par_objet"));
        http.put("/api/notifications/regroupement/mien", EMPLOYE, "{\"mode\":\"un_avis_groupe\"}").andExpect(jsonPath("$.mien").value("un_avis_groupe"))
                .andExpect(jsonPath("$.effectif").value("un_avis_groupe"));
        http.put("/api/notifications/regroupement/mien", EMPLOYE, "{\"mode\":\"un_avis_par_objet\"}").andExpect(jsonPath("$.mien").value("un_avis_par_objet"));
        http.put("/api/notifications/regroupement/mien", EMPLOYE, "{\"mode\":\"\"}").andExpect(jsonPath("$.mien").isEmpty());
        http.put("/api/notifications/regroupement/mien", EMPLOYE, "{\"mode\":null}").andExpect(jsonPath("$.mien").isEmpty())
                .andExpect(jsonPath("$.effectif").value("un_avis_par_objet"));
        for (String url : List.of("/api/notifications/regroupement/mien", "/api/notifications/regroupement/defaut")) {
            http.put(url, ADMIN, "{\"mode\":\"digest-hebdo\"}").andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errors[0]").value(containsString("un_avis_groupe, un_avis_par_objet")));
        }
        http.get("/api/notifications/regroupement", null).andExpect(status().isUnauthorized());
    }
}
