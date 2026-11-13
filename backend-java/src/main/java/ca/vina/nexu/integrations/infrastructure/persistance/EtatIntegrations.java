package ca.vina.nexu.integrations.infrastructure.persistance;

import ca.vina.nexu.integrations.infrastructure.persistance.DepotClientsApi;
import ca.vina.nexu.integrations.infrastructure.persistance.DepotWebhooks;

import ca.vina.nexu.integrations.domaine.CartesIntegrations;
import ca.vina.nexu.partage.temps.Iso;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.time.Clock;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Rassemble les chiffres des cartes d'état (ÉC-5) : chaque module est lu par son dépôt, jamais dupliqué. */
@Component
public class EtatIntegrations {

    private static final Duration SEPT_JOURS = Duration.ofDays(7);
    private static final TypeReference<Map<String, Object>> CARTE = new TypeReference<>() { };
    private static final ObjectMapper JSON = JsonMapper.builder().build();

    /** Ce que le registre des démonstrations sait d'un établissement — fourni par le module de démonstration, absent sinon. */
    public interface LecteurDemonstrations {
        CartesIntegrations.Demonstrations pour(String tenantId, Map<String, String> env);
    }

    private final JdbcClient jdbc;
    private final DepotClientsApi clients;
    private final DepotWebhooks webhooks;
    private final Clock horloge;
    private final LecteurDemonstrations demonstrations;

    public EtatIntegrations(JdbcClient jdbc, DepotClientsApi clients, DepotWebhooks webhooks, Clock horloge) {
        this(jdbc, clients, webhooks, horloge, (t, env) -> CartesIntegrations.Demonstrations.aucune(""));
    }

    @Autowired
    public EtatIntegrations(JdbcClient jdbc, DepotClientsApi clients, DepotWebhooks webhooks, Clock horloge, LecteurDemonstrations demonstrations) {
        this.jdbc = jdbc;
        this.clients = clients;
        this.webhooks = webhooks;
        this.horloge = horloge;
        this.demonstrations = demonstrations;
    }

    /** Le mode d'une source scolaire ({@code simule}, {@code http}, {@code mock}) tel que l'établissement l'a réglé. */
    public String modeSource(String tenantId, String source) {
        return jdbc.sql("SELECT configJson FROM scol_integrations WHERE tenantId = ? AND source = ?").params(tenantId, source).query(String.class).optional()
                .map(j -> JSON.readValue(j, CARTE)).map(c -> c.get("mode")).map(String::valueOf).orElse("simule");
    }

    public List<CartesIntegrations.Carte> cartes(String tenantId, Map<String, String> env) {
        String depuis = Iso.de(horloge.instant().minus(SEPT_JOURS));
        String derniereSynchro = jdbc.sql("SELECT MAX(debuteLe) FROM scol_synchronisations WHERE tenantId = ?").params(tenantId).query(String.class).optional().orElse(null);
        int synchrosEchouees = jdbc.sql("SELECT COUNT(*) FROM scol_synchronisations WHERE tenantId = ? AND statut = 'echouee' AND debuteLe >= ?").params(tenantId, depuis).query(Integer.class).single();
        List<DepotWebhooks.WebhookVue> vues = webhooks.lister(tenantId);
        Map<String, Integer> enAttente = new LinkedHashMap<>();
        vues.forEach(w -> enAttente.put(w.id(), w.envoisEnAttente()));
        CartesIntegrations.Chiffres chiffres = new CartesIntegrations.Chiffres(derniereSynchro, synchrosEchouees, clients.lister(tenantId).size(), DepotWebhooks.webhooksDe(vues),
                enAttente, vues.stream().map(DepotWebhooks.WebhookVue::dernierEnvoiLe).toList(), webhooks.envoisEchouesDepuis(tenantId, depuis));
        CartesIntegrations.Demonstrations d = demonstrations.pour(tenantId, env);
        return CartesIntegrations.cartes(env, chiffres, new CartesIntegrations.Demonstrations(modeSource(tenantId, "grics"), modeSource(tenantId, "ats"), d.parCode(), d.clientApiDemo(), d.portClamdDemo(), d.urlServeur()));
    }
}
