package ca.vina.nexu.integrations.infrastructure.web;

import ca.vina.nexu.integrations.infrastructure.persistance.DepotClientsApi;
import ca.vina.nexu.integrations.infrastructure.persistance.DepotWebhooks;
import ca.vina.nexu.integrations.infrastructure.persistance.EtatIntegrations;

import ca.vina.nexu.ClientHttpEssai;
import ca.vina.nexu.SchemaEssai;
import ca.vina.nexu.acces.infrastructure.persistance.RegistrePolitiqueAcces;
import ca.vina.nexu.integrations.domaine.Transport;
import ca.vina.nexu.integrations.domaine.Webhooks;
import ca.vina.nexu.ordonnanceur.domaine.PolitiqueRetentative;
import ca.vina.nexu.ordonnanceur.infrastructure.persistance.DepotTaches;
import ca.vina.nexu.ordonnanceur.infrastructure.persistance.RegistreExecuteurs;
import ca.vina.nexu.partage.securite.ContexteRequete;
import ca.vina.nexu.partage.web.GardeAcces;
import ca.vina.nexu.partage.modules.infrastructure.DepotModulesActives;
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
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Port de `integrations-webhooks.integration.test.ts` : secret montré une fois, envoi signé reçu,
 * retentatives, suspension au dixième échec, réactivation, rejeu, suppression refusée après une
 * livraison, isolation, déduplication par clé, cartes d'état depuis l'environnement injecté.
 */
@DisplayName("API intégrations — webhooks sortants et cartes d'état")
class ControleurIntegrationsWebhooksHttpTest {

    private static final ContexteRequete ADMIN = ClientHttpEssai.session("admin", "t-1");
    private static final ContexteRequete AUTRE = ClientHttpEssai.session("admin", "t-2");
    private static final ContexteRequete SANS_MODULE = ClientHttpEssai.session("admin", "t-3");
    private static final ContexteRequete EMPLOYE = ClientHttpEssai.session("employee", "t-1");
    private static final PolitiqueRetentative POLITIQUE = PolitiqueRetentative.PAR_DEFAUT;

    /** Un récepteur commandé : il rend le code voulu, ou refuse la connexion. */
    static final class Recepteur implements Transport {
        record Recu(String url, String corps, Map<String, String> enTetes) { }
        final List<Recu> recus = new ArrayList<>();
        int code = 200;
        boolean refuse = false;

        @Override
        public int envoyer(String url, String corps, Map<String, String> enTetes) throws EchecTransportException {
            if (refuse) {
                throw new EchecTransportException("connexion refusée");
            }
            recus.add(new Recu(url, corps, enTetes));
            return code;
        }
    }

    private ClientHttpEssai http;
    private JdbcClient jdbc;
    private DepotTaches taches;
    private DepotWebhooks depot;
    private final Recepteur recepteur = new Recepteur();
    private String horloge = "2026-09-12T10:00:00.000Z";

    @BeforeEach
    void preparer(@TempDir Path dossier) {
        jdbc = SchemaEssai.base(dossier);
        jdbc.sql("INSERT INTO tenant_quotas VALUES ('t-1', 10, 10, '[\"integrations\"]'), ('t-2', 10, 10, '[\"integrations\"]'), ('t-3', 10, 10, '[]')").update();
        RegistreExecuteurs registre = new RegistreExecuteurs();
        taches = new DepotTaches(jdbc, registre);
        DepotModulesActives modules = new DepotModulesActives(jdbc);
        depot = new DepotWebhooks(jdbc, taches, modules, recepteur, registre);
        DepotClientsApi clients = new DepotClientsApi(jdbc);
        EtatIntegrations etat = new EtatIntegrations(jdbc, clients, depot, Clock.fixed(Instant.parse("2026-09-12T12:00:00Z"), ZoneOffset.UTC));
        http = new ClientHttpEssai(new ControleurIntegrations(clients, depot, etat, modules, new GardeAcces(new RegistrePolitiqueAcces()),
                Map.of("GRICS_CLIENT_ID", "id", "GRICS_CLIENT_SECRET", "s", "ATS_WEBHOOK_SECRET", "x", "AZURE_CLIENT_ID", "abcdef123", "AZURE_TENANT_ID", "css")));
    }

    /** Un tic de l'ordonnanceur à l'instant courant, puis l'envoi le plus récent du webhook. */
    private Map<String, Object> tic(String webhookId) {
        taches.tic(horloge, POLITIQUE, "essai");
        return jdbc.sql("SELECT * FROM integrations_webhook_envois WHERE webhookId = ? ORDER BY creeLe DESC, rowid DESC LIMIT 1").params(webhookId).query().listOfRows().get(0);
    }

    private static String champ(MvcResult r, String nom) throws Exception {
        return r.getResponse().getContentAsString().replaceAll("^.*?\"" + nom + "\":\"([^\"]+)\".*$", "$1");
    }

    @Test
    void seCreeAvecUnSecretMontreUneFoisLEnvoiEstSigneEtRecuLeJournalLeDitLivre() throws Exception {
        http.get("/api/integrations/webhooks/evenements", null).andExpect(status().isUnauthorized());
        http.get("/api/integrations/webhooks/evenements", SANS_MODULE).andExpect(status().isForbidden()).andExpect(jsonPath("$.errors[0]").value("module « integrations » non activé pour cet organisme"));
        http.get("/api/integrations/webhooks/evenements", EMPLOYE).andExpect(status().isForbidden());
        http.get("/api/integrations/webhooks/evenements", ADMIN).andExpect(status().isOk()).andExpect(jsonPath("$[*].code", contains(Webhooks.EVENEMENTS.toArray())));
        http.post("/api/integrations/webhooks", ADMIN, "{\"url\":\"http://paie.exemple.qc.ca\",\"evenements\":[]}").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0]", containsString("https:// obligatoire (http admis sur localhost seulement); evenements : au moins un")));
        http.post("/api/integrations/webhooks", ADMIN, null).andExpect(status().isBadRequest());
        MvcResult w = http.post("/api/integrations/webhooks", ADMIN, "{\"url\":\"https://paie.exemple.qc.ca/hooks\",\"evenements\":[\"employe.cree\",\"absence.declaree\",\"employe.cree\"],\"description\":\" Paie \"}")
                .andExpect(status().isCreated()).andExpect(jsonPath("$.secret", containsString("whsec_"))).andExpect(jsonPath("$.description").value("Paie"))
                .andExpect(jsonPath("$.evenements", contains("absence.declaree", "employe.cree"))).andReturn();
        String id = champ(w, "id");
        String secret = champ(w, "secret");
        http.get("/api/integrations/webhooks", ADMIN).andExpect(status().isOk()).andExpect(jsonPath("$", hasSize(1))).andExpect(jsonPath("$[0].secret").doesNotExist())
                .andExpect(jsonPath("$[0].actif").value(true)).andExpect(jsonPath("$[0].suspendu").value(false)).andExpect(jsonPath("$[0].envoisEnAttente").value(0)).andExpect(jsonPath("$[0].dernierEnvoiLe").isEmpty());
        assertThat(depot.emettre("t-1", "absence.declaree", Map.of("absenceId", "a-1"), null, horloge)).isEqualTo(1);
        assertThat(depot.emettre("t-1", "besoin.cree", Map.of("besoinId", "b-1"), null, horloge)).isZero();   // pas abonné
        http.get("/api/integrations/webhooks", ADMIN).andExpect(jsonPath("$[0].envoisEnAttente").value(1)).andExpect(jsonPath("$[0].dernierEnvoiLe").value(horloge));
        Map<String, Object> envoi = tic(id);
        assertThat(recepteur.recus).hasSize(1);
        Recepteur.Recu recu = recepteur.recus.get(0);
        assertThat(recu.url()).isEqualTo("https://paie.exemple.qc.ca/hooks");
        assertThat(recu.enTetes()).containsEntry("x-evenement", "absence.declaree").containsEntry("content-type", "application/json").containsEntry("user-agent", "NexuVINA-Webhook/1")
                .containsEntry("x-livraison", (String) envoi.get("id")).containsEntry("x-signature-256", Webhooks.signer(secret, recu.corps()));
        assertThat(recu.corps()).contains("\"evenement\":\"absence.declaree\",\"tenantId\":\"t-1\"").contains("\"donnees\":{\"absenceId\":\"a-1\"}");
        http.get("/api/integrations/webhooks/" + id + "/envois", ADMIN).andExpect(status().isOk()).andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].statut").value("livre")).andExpect(jsonPath("$[0].tentatives").value(1)).andExpect(jsonPath("$[0].dernierCode").value(200)).andExpect(jsonPath("$[0].livreLe").value(horloge));
        // Un second abonnement reçoit son propre envoi ; un tic déjà fait ne relivre pas.
        assertThat(depot.emettre("t-1", "employe.cree", Map.of("employeeId", "e-1"), null, horloge)).isEqualTo(1);
        tic(id);
        tic(id);
        assertThat(recepteur.recus.stream().map(r -> r.enTetes().get("x-evenement"))).containsExactly("absence.declaree", "employe.cree");
        // Supprimer : refusé après une livraison (409) ; désactiver, puis un envoi en attente échoue en le disant.
        http.delete("/api/integrations/webhooks/" + id, ADMIN).andExpect(status().isConflict()).andExpect(jsonPath("$.errors[0]", containsString("a déjà livré 2 envoi(s)")));
        assertThat(depot.emettre("t-1", "employe.cree", Map.of("employeeId", "e-2"), null, horloge)).isEqualTo(1);
        http.post("/api/integrations/webhooks/" + id + "/activite", ADMIN, "{\"actif\":false}").andExpect(status().isOk()).andExpect(jsonPath("$.actif").value(false));
        assertThat(tic(id)).containsEntry("statut", "echoue").containsEntry("derniereErreur", "webhook inactif ou suspendu");
        assertThat(depot.emettre("t-1", "employe.cree", Map.of("employeeId", "e-3"), null, horloge)).isZero();   // inactif : rien n'est émis
    }

    @Test
    void unEchecSeRetenteDixEchecsSuspendentReactiverLeveLaSuspensionRejouerRelivre() throws Exception {
        MvcResult w = http.post("/api/integrations/webhooks", ADMIN, "{\"url\":\"https://bi.exemple.qc.ca/hooks\",\"evenements\":[\"absence.declaree\"]}").andExpect(status().isCreated()).andReturn();
        String id = champ(w, "id");
        recepteur.refuse = true;
        assertThat(depot.emettre("t-1", "absence.declaree", Map.of("absenceId", "a-1"), null, horloge)).isEqualTo(1);
        Map<String, Object> e = tic(id);
        assertThat(e).containsEntry("statut", "en_attente").containsEntry("tentatives", 1).containsEntry("derniereErreur", "connexion refusée");
        String envoiId = (String) e.get("id");
        // Retentée à 1 min : rien avant, une tentative de plus après.
        horloge = "2026-09-12T10:00:30.000Z";
        assertThat(tic(id)).containsEntry("tentatives", 1);
        horloge = "2026-09-12T10:01:00.000Z";
        assertThat(tic(id)).containsEntry("tentatives", 2);
        for (String instant : List.of("2026-09-12T10:06:00.000Z", "2026-09-12T10:36:00.000Z", "2026-09-12T12:36:00.000Z")) {
            horloge = instant;
            tic(id);
        }
        assertThat(tic(id)).containsEntry("statut", "en_attente").containsEntry("tentatives", 5);
        horloge = "2026-09-13T00:36:00.000Z";
        assertThat(tic(id)).containsEntry("statut", "echoue").containsEntry("tentatives", 6);
        http.get("/api/integrations/webhooks", ADMIN).andExpect(jsonPath("$[0].echecsConsecutifs").value(6)).andExpect(jsonPath("$[0].suspendu").value(false));
        // Quatre échecs de plus : suspendu au dixième, et plus rien n'est émis.
        for (int i = 0; i < 4; i++) {
            assertThat(depot.emettre("t-1", "absence.declaree", Map.of("absenceId", "a-" + i), null, horloge)).isEqualTo(1);
            tic(id);
        }
        http.get("/api/integrations/webhooks", ADMIN).andExpect(jsonPath("$[0].echecsConsecutifs").value(10)).andExpect(jsonPath("$[0].suspendu").value(true));
        assertThat(depot.emettre("t-1", "absence.declaree", Map.of("absenceId", "a-ignoree"), null, horloge)).isZero();
        http.get("/api/integrations/etat", ADMIN).andExpect(status().isOk()).andExpect(jsonPath("$[*].code", contains("grics", "ats", "entra", "saml", "sms", "antivirus", "api", "webhooks")))
                .andExpect(jsonPath("$[7].etat").value("non_configure")).andExpect(jsonPath("$[7].detail", containsString("1 suspendu"))).andExpect(jsonPath("$[7].erreursRecentes").value(2))
                .andExpect(jsonPath("$[0].etat").value("configure")).andExpect(jsonPath("$[2].detail").value("client abcdef…, locataire css")).andExpect(jsonPath("$[6].etat").value("non_configure"));
        http.get("/api/integrations/etat", SANS_MODULE).andExpect(status().isForbidden());
        // Réactiver lève la suspension ; rejouer relivre le même corps, l'ancien reste dans le journal.
        http.post("/api/integrations/webhooks/" + id + "/activite", ADMIN, null).andExpect(status().isOk()).andExpect(jsonPath("$.suspendu").value(false))
                .andExpect(jsonPath("$.echecsConsecutifs").value(0)).andExpect(jsonPath("$.actif").value(true));
        recepteur.refuse = false;
        MvcResult rejoue = http.post("/api/integrations/webhooks/envois/" + envoiId + "/rejouer", ADMIN, null).andExpect(status().isCreated()).andExpect(jsonPath("$.statut").value("en_attente")).andReturn();
        String rejoueId = champ(rejoue, "id");
        // Le rejeu est planifié à l'instant réel : le tic avance jusqu'à lui (les retentatives en attente partent aussi).
        horloge = ca.vina.nexu.partage.temps.Iso.maintenant();
        tic(id);
        assertThat(jdbc.sql("SELECT statut FROM integrations_webhook_envois WHERE id = ?").params(rejoueId).query(String.class).single()).isEqualTo("livre");
        assertThat(jdbc.sql("SELECT statut FROM integrations_webhook_envois WHERE id = ?").params(envoiId).query(String.class).single()).isEqualTo("echoue");
        http.post("/api/integrations/webhooks/envois/inconnu/rejouer", ADMIN, null).andExpect(status().isNotFound());
        // Une réponse hors 2xx est un échec nommé par son code.
        recepteur.code = 500;
        assertThat(depot.emettre("t-1", "absence.declaree", Map.of("absenceId", "a-500"), null, horloge)).isEqualTo(1);
        assertThat(tic(id)).containsEntry("statut", "en_attente").containsEntry("dernierCode", 500).containsEntry("derniereErreur", "réponse 500");
    }

    @Test
    void supprimerIsolationEtDeduplicationParCle() throws Exception {
        MvcResult neuf = http.post("/api/integrations/webhooks", ADMIN, "{\"url\":\"https://x.ca\",\"evenements\":[\"document.expirant\"]}").andExpect(status().isCreated()).andReturn();
        String id = champ(neuf, "id");
        assertThat(depot.emettre("t-1", "document.expirant", Map.of("documentId", "d1"), "document.expirant:d1:2026-09-12")).isEqualTo(1);
        assertThat(depot.emettre("t-1", "document.expirant", Map.of("documentId", "d1"), "document.expirant:d1:2026-09-12")).isZero();   // même clé : pas deux fois
        assertThat(depot.emettre("t-3", "document.expirant", Map.of("documentId", "d1"), null)).isZero();   // module fermé
        assertThat(depot.emettre("t-2", "document.expirant", Map.of("documentId", "d1"), null)).isZero();   // autre locataire : aucun webhook
        http.get("/api/integrations/webhooks", AUTRE).andExpect(jsonPath("$", hasSize(0)));
        http.get("/api/integrations/webhooks/" + id + "/envois", AUTRE).andExpect(jsonPath("$", hasSize(0)));
        http.post("/api/integrations/webhooks/" + id + "/activite", AUTRE, null).andExpect(status().isNotFound());
        http.delete("/api/integrations/webhooks/" + id, AUTRE).andExpect(status().isNotFound());
        http.delete("/api/integrations/webhooks/" + id, ADMIN).andExpect(status().isOk()).andExpect(jsonPath("$.supprime").value(true));
        http.delete("/api/integrations/webhooks/" + id, ADMIN).andExpect(status().isNotFound());
        http.post("/api/integrations/webhooks/inconnu/activite", ADMIN, "{}").andExpect(status().isNotFound());
        assertThat(jdbc.sql("SELECT COUNT(*) FROM integrations_webhook_envois").query(Integer.class).single()).isZero();
        // Une tâche dont l'envoi a disparu ne fait rien ; une base cassée n'échoue pas le geste métier.
        taches.planifier("t-1", DepotWebhooks.TYPE_TACHE, "envoi-disparu", horloge, Map.of(), "essai");
        assertThat(taches.tic(horloge, POLITIQUE, "essai").faites()).isEqualTo(1);
        jdbc.sql("DROP TABLE integrations_webhooks").update();
        assertThat(depot.emettre("t-1", "document.expirant", Map.of(), null)).isZero();
    }
}
