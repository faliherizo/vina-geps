package ca.vina.nexu.integrations.infrastructure.persistance;

import ca.vina.nexu.integrations.domaine.Transport;
import ca.vina.nexu.integrations.domaine.Webhooks;
import ca.vina.nexu.integrations.domaine.Webhooks.Webhook;
import ca.vina.nexu.ordonnanceur.domaine.Executeur;
import ca.vina.nexu.ordonnanceur.domaine.TacheDifferee;
import ca.vina.nexu.ordonnanceur.infrastructure.persistance.DepotTaches;
import ca.vina.nexu.ordonnanceur.infrastructure.persistance.RegistreExecuteurs;
import ca.vina.nexu.partage.temps.Iso;
import ca.vina.nexu.partage.modules.EmetteurEvenements;
import ca.vina.nexu.partage.modules.ModulesActives;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import tools.jackson.databind.json.JsonMapper;

import java.security.SecureRandom;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Webhooks sortants — persistance (migration Node 0073), émission, exécution par l'ordonnanceur,
 * retentatives, suspension, rejeu. Port de {@code integrations/serviceWebhooks.ts} ; l'envoi HTTP
 * passe par le port {@link Transport}, que les essais remplacent par un faux récepteur.
 */
@Repository
public class DepotWebhooks implements Executeur, EmetteurEvenements {

    public static final String TYPE_TACHE = "webhook_envoi";
    private static final JsonMapper JSON = JsonMapper.builder().build();

    public static final class IntrouvableException extends RuntimeException {
        public IntrouvableException(String message) {
            super(message);
        }
    }

    public static final class DejaLivreException extends IllegalStateException {
        public DejaLivreException(String message) {
            super(message);
        }
    }

    /** Le webhook tel que l'écran le lit : jamais son secret. */
    public record WebhookVue(String id, String url, String description, List<String> evenements, boolean actif, boolean suspendu, int echecsConsecutifs, String creeLe,
                             int envoisEnAttente, String dernierEnvoiLe) {
        Webhook webhook() {
            return new Webhook(id, url, description, evenements, actif, suspendu, echecsConsecutifs, creeLe);
        }
    }

    public record Envoi(String id, String webhookId, String evenement, String statut, int tentatives, Integer dernierCode, String derniereErreur, String creeLe, String livreLe) { }

    private record Ligne(String id, String tenantId, String url, String secret, boolean actif, boolean suspendu, int echecsConsecutifs, String description, String evenementsJson, String creeLe) { }

    private final JdbcClient jdbc;
    private final DepotTaches taches;
    private final ModulesActives modules;
    private final Transport transport;
    private final SecureRandom aleatoire = new SecureRandom();

    public DepotWebhooks(JdbcClient jdbc, DepotTaches taches, ModulesActives modules, Transport transport, RegistreExecuteurs registre) {
        this.jdbc = jdbc;
        this.taches = taches;
        this.modules = modules;
        this.transport = transport;
        registre.enregistrer(TYPE_TACHE, this);
    }

    private static Ligne versLigne(ResultSet rs, int i) throws SQLException {
        return new Ligne(rs.getString("id"), rs.getString("tenantId"), rs.getString("url"), rs.getString("secret"), rs.getInt("actif") != 0, rs.getInt("suspendu") != 0,
                rs.getInt("echecsConsecutifs"), rs.getString("description"), rs.getString("evenementsJson"), rs.getString("creeLe"));
    }

    private static Envoi versEnvoi(ResultSet rs, int i) throws SQLException {
        int code = rs.getInt("dernierCode");
        Integer dernierCode = rs.wasNull() ? null : code;
        return new Envoi(rs.getString("id"), rs.getString("webhookId"), rs.getString("evenement"), rs.getString("statut"), rs.getInt("tentatives"), dernierCode,
                rs.getString("derniereErreur"), rs.getString("creeLe"), rs.getString("livreLe"));
    }

    private WebhookVue vue(Ligne l) {
        Map<String, Object> stats = jdbc.sql("SELECT SUM(CASE WHEN statut = 'en_attente' THEN 1 ELSE 0 END) AS attente, MAX(creeLe) AS dernier FROM integrations_webhook_envois WHERE webhookId = ?")
                .params(l.id()).query().singleRow();
        List<String> evenements = JSON.readValue(l.evenementsJson(), JSON.getTypeFactory().constructCollectionType(List.class, String.class));
        int attente = stats.get("attente") == null ? 0 : ((Number) stats.get("attente")).intValue();
        return new WebhookVue(l.id(), l.url(), l.description(), evenements, l.actif(), l.suspendu(), l.echecsConsecutifs(), l.creeLe(), attente, (String) stats.get("dernier"));
    }

    public List<WebhookVue> lister(String tenantId) {
        return jdbc.sql("SELECT * FROM integrations_webhooks WHERE tenantId = ? ORDER BY creeLe").params(tenantId).query(DepotWebhooks::versLigne).list().stream().map(this::vue).toList();
    }

    private WebhookVue lire(String tenantId, String webhookId) {
        return lister(tenantId).stream().filter(w -> w.id().equals(webhookId)).findFirst().orElseThrow(() -> new IntrouvableException("webhook introuvable"));
    }

    /** Le secret n'est rendu qu'ici, une fois (règle WH-1). */
    public Map<String, Object> creer(String tenantId, String acteurId, Map<String, Object> w, String instant) {
        List<String> erreurs = Webhooks.validerWebhook(w);
        if (!erreurs.isEmpty()) {
            throw new IllegalArgumentException(String.join("; ", erreurs));
        }
        byte[] octets = new byte[24];
        aleatoire.nextBytes(octets);
        String secret = Webhooks.secretDepuis(octets);
        String id = UUID.randomUUID().toString();
        String description = w.get("description") == null ? "" : String.valueOf(w.get("description")).trim();
        jdbc.sql("INSERT INTO integrations_webhooks (id, tenantId, url, description, evenementsJson, secret, actif, suspendu, echecsConsecutifs, creeLe, creePar) VALUES (?,?,?,?,?,?,1,0,0,?,?)")
                .params(id, tenantId, String.valueOf(w.get("url")).trim(), description.isEmpty() ? null : description,
                        JSON.writeValueAsString(Webhooks.evenementsOrdonnes((List<?>) w.get("evenements"))), secret, instant, acteurId).update();
        @SuppressWarnings("unchecked") Map<String, Object> vue = JSON.convertValue(lire(tenantId, id), Map.class);
        Map<String, Object> reponse = new LinkedHashMap<>(vue);
        reponse.put("secret", secret);
        return reponse;
    }

    public Map<String, Object> creer(String tenantId, String acteurId, Map<String, Object> w) {
        return creer(tenantId, acteurId, w, Iso.maintenant());
    }

    /** Réactiver lève la suspension et remet le compteur : c'est une décision humaine. */
    public WebhookVue reglerActivite(String tenantId, String webhookId, boolean actif) {
        int n = jdbc.sql("UPDATE integrations_webhooks SET actif = ?, suspendu = CASE WHEN ? = 1 THEN 0 ELSE suspendu END, echecsConsecutifs = CASE WHEN ? = 1 THEN 0 ELSE echecsConsecutifs END WHERE tenantId = ? AND id = ?")
                .params(actif ? 1 : 0, actif ? 1 : 0, actif ? 1 : 0, tenantId, webhookId).update();
        if (n == 0) {
            throw new IntrouvableException("webhook introuvable");
        }
        return lire(tenantId, webhookId);
    }

    /** Suppression physique seulement sans envoi livré (règle WH-6) : sinon le journal des envois perdrait sa cible. */
    public void supprimer(String tenantId, String webhookId) {
        lire(tenantId, webhookId);
        int livres = jdbc.sql("SELECT COUNT(*) FROM integrations_webhook_envois WHERE webhookId = ? AND statut = 'livre'").params(webhookId).query(Integer.class).single();
        if (livres > 0) {
            throw new DejaLivreException("ce webhook a déjà livré " + livres + " envoi(s) : désactivez-le plutôt que de le supprimer");
        }
        jdbc.sql("DELETE FROM integrations_webhook_envois WHERE webhookId = ?").params(webhookId).update();
        jdbc.sql("DELETE FROM integrations_webhooks WHERE tenantId = ? AND id = ?").params(tenantId, webhookId).update();
    }

    public List<Envoi> listerEnvois(String tenantId, String webhookId) {
        return jdbc.sql("SELECT * FROM integrations_webhook_envois WHERE tenantId = ? AND webhookId = ? ORDER BY creeLe DESC LIMIT 100").params(tenantId, webhookId).query(DepotWebhooks::versEnvoi).list();
    }

    /**
     * Émet un événement : un envoi par webhook actif abonné, planifié tout de suite. {@code cle}
     * déduplique (le même document expirant n'est pas annoncé deux fois le même jour). Ne lève
     * jamais : un webhook n'est pas une raison d'échouer le geste métier qui l'a déclenché.
     */
    @Override
    public int emettre(String tenantId, String evenement, Map<String, Object> donnees, String cle, String instant) {
        int n = 0;
        try {
            // Module fermé par le superadmin : rien ne sort, même si des webhooks restent déclarés.
            if (!modules.estActive(tenantId, "integrations")) {
                return 0;
            }
            for (WebhookVue w : lister(tenantId)) {
                if (!w.actif() || w.suspendu() || !w.evenements().contains(evenement)) {
                    continue;
                }
                String envoiId = UUID.randomUUID().toString();
                String corps = Webhooks.corpsLivraison(evenement, tenantId, instant, envoiId, JSON.writeValueAsString(donnees));
                // Déjà annoncé sous cette clé : l'index unique (webhookId, cle) fait taire la répétition.
                int insere = jdbc.sql("INSERT OR IGNORE INTO integrations_webhook_envois (id, tenantId, webhookId, evenement, cle, corps, statut, tentatives, creeLe) VALUES (?,?,?,?,?,?,'en_attente',0,?)")
                        .params(envoiId, tenantId, w.id(), evenement, cle, corps, instant).update();
                if (insere == 0) {
                    continue;
                }
                taches.planifier(tenantId, TYPE_TACHE, envoiId, instant, Map.of("webhookId", w.id()), "systeme");
                n++;
            }
        } catch (RuntimeException e) {
            // Journalisé par l'ordonnanceur ; le métier continue.
            return n;
        }
        return n;
    }

    public int emettre(String tenantId, String evenement, Map<String, Object> donnees, String cle) {
        return emettre(tenantId, evenement, donnees, cle, Iso.maintenant());
    }

    /** Rejouer = une nouvelle livraison du même corps (règle WH-5) ; l'ancienne reste dans le journal. */
    public Envoi rejouer(String tenantId, String envoiId, String instant) {
        Map<String, Object> ancien = jdbc.sql("SELECT webhookId, evenement, corps FROM integrations_webhook_envois WHERE tenantId = ? AND id = ?").params(tenantId, envoiId).query().listOfRows().stream().findFirst()
                .orElseThrow(() -> new IntrouvableException("envoi introuvable"));
        String nouveau = UUID.randomUUID().toString();
        String webhookId = (String) ancien.get("webhookId");
        jdbc.sql("INSERT INTO integrations_webhook_envois (id, tenantId, webhookId, evenement, cle, corps, statut, tentatives, creeLe) VALUES (?,?,?,?,NULL,?,'en_attente',0,?)")
                .params(nouveau, tenantId, webhookId, ancien.get("evenement"), ancien.get("corps"), instant).update();
        taches.planifier(tenantId, TYPE_TACHE, nouveau, instant, Map.of("webhookId", webhookId), "systeme");
        return listerEnvois(tenantId, webhookId).stream().filter(e -> e.id().equals(nouveau)).findFirst().orElseThrow();
    }

    public Envoi rejouer(String tenantId, String envoiId) {
        return rejouer(tenantId, envoiId, Iso.maintenant());
    }

    // ── Exécution par l'ordonnanceur ────────────────────────────────────────

    /**
     * Une tentative d'envoi ; ne lève jamais. Une retentative est un <strong>report</strong> de la
     * même tâche, à l'instant que dicte la politique du webhook — l'ordonnanceur n'a pas à
     * connaître nos attentes, et l'instant vient de lui (les essais le fixent).
     */
    @Override
    public Optional<String> executer(TacheDifferee t, String instant) {
        Optional<Map<String, Object>> envoi = jdbc.sql("SELECT id, webhookId, corps, evenement, statut, tentatives FROM integrations_webhook_envois WHERE id = ?").params(t.cibleId()).query().listOfRows().stream().findFirst();
        if (envoi.isEmpty() || !"en_attente".equals(envoi.get().get("statut"))) {
            return Optional.empty();
        }
        Map<String, Object> e = envoi.get();
        Optional<Ligne> webhook = jdbc.sql("SELECT * FROM integrations_webhooks WHERE id = ?").params(e.get("webhookId")).query(DepotWebhooks::versLigne).optional();
        if (webhook.isEmpty() || !webhook.get().actif() || webhook.get().suspendu()) {
            jdbc.sql("UPDATE integrations_webhook_envois SET statut = 'echoue', derniereErreur = 'webhook inactif ou suspendu' WHERE id = ?").params(e.get("id")).update();
            return Optional.empty();
        }
        Ligne w = webhook.get();
        int tentative = ((Number) e.get("tentatives")).intValue() + 1;
        String corps = (String) e.get("corps");
        Integer code = null;
        String erreur = null;
        try {
            Map<String, String> enTetes = new LinkedHashMap<>();
            enTetes.put("content-type", "application/json");
            enTetes.put("x-signature-256", Webhooks.signer(w.secret(), corps));
            enTetes.put("x-evenement", (String) e.get("evenement"));
            enTetes.put("x-livraison", (String) e.get("id"));
            enTetes.put("user-agent", "NexuVINA-Webhook/1");
            code = transport.envoyer(w.url(), corps, enTetes);
            if (code < 200 || code >= 300) {
                erreur = "réponse " + code;
            }
        } catch (Transport.EchecTransportException ex) {
            erreur = ex.getMessage();
        }
        if (erreur == null) {
            jdbc.sql("UPDATE integrations_webhook_envois SET statut = 'livre', tentatives = ?, dernierCode = ?, derniereErreur = NULL, livreLe = ? WHERE id = ?").params(tentative, code, instant, e.get("id")).update();
            jdbc.sql("UPDATE integrations_webhooks SET echecsConsecutifs = 0 WHERE id = ?").params(w.id()).update();
            return Optional.empty();
        }
        int echecs = w.echecsConsecutifs() + 1;
        Webhooks.DecisionEchec decision = Webhooks.apresEchec(tentative, echecs, instant);
        jdbc.sql("UPDATE integrations_webhook_envois SET statut = ?, tentatives = ?, dernierCode = ?, derniereErreur = ? WHERE id = ?").params(decision.statut(), tentative, code, erreur, e.get("id")).update();
        jdbc.sql("UPDATE integrations_webhooks SET echecsConsecutifs = ?, suspendu = CASE WHEN ? = 1 THEN 1 ELSE suspendu END WHERE id = ?").params(echecs, decision.suspendre() ? 1 : 0, w.id()).update();
        return Optional.ofNullable(decision.prochaineTentativeLe());
    }

    // ── Chiffres pour les cartes d'état ─────────────────────────────────────

    public int envoisEchouesDepuis(String tenantId, String depuis) {
        return jdbc.sql("SELECT COUNT(*) FROM integrations_webhook_envois WHERE tenantId = ? AND statut = 'echoue' AND creeLe >= ?").params(tenantId, depuis).query(Integer.class).single();
    }

    public static List<Webhook> webhooksDe(List<WebhookVue> vues) {
        return vues.stream().map(WebhookVue::webhook).toList();
    }
}
