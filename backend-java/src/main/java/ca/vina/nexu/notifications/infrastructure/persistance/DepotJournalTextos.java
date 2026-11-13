package ca.vina.nexu.notifications.infrastructure.persistance;

import ca.vina.nexu.notifications.domaine.JournalTextos;
import ca.vina.nexu.notifications.domaine.Segments;
import ca.vina.nexu.notifications.domaine.Textos;
import ca.vina.nexu.partage.temps.Iso;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Le journal des textos ({@code sms_message}, {@code sms_quota}) — port de la persistance de
 * {@code notifications/journalSms.ts}. Chaque envoi passe par ici, quel que soit le module qui
 * parle : la ligne existe <em>avant</em> l'appel au fournisseur, en attente ; un refus du porteur
 * ne lève pas, il s'inscrit (statut échoué, erreur nommée) et l'appelant décide.
 */
@Repository
public class DepotJournalTextos {

    private final JdbcClient jdbc;
    private final Clock horloge;
    private final Textos.PorteurSms porteur;
    private final JournalTextos.Reglages reglages;

    public DepotJournalTextos(JdbcClient jdbc, Clock horloge, Textos.PorteurSms porteur, JournalTextos.Reglages reglages) {
        this.jdbc = jdbc;
        this.horloge = horloge;
        this.porteur = porteur;
        this.reglages = reglages;
    }

    private static JournalTextos.Texto texto(ResultSet rs, int i) throws SQLException {
        return new JournalTextos.Texto(rs.getLong("id"), rs.getString("tenant_id"), rs.getString("phone_number"), rs.getString("message"), rs.getString("status"), rs.getString("provider"),
                rs.getString("provider_id"), rs.getString("created_at"), rs.getString("sent_at"), rs.getString("error_message"), rs.getString("contexte"), rs.getString("expediteur"),
                Math.max(1, rs.getInt("segments")));
    }

    private String maintenant() {
        return Iso.de(horloge.instant());
    }

    public JournalTextos.Etat etat() {
        return reglages.etat();
    }

    // ── Quota ─────────────────────────────────────────────────────────────

    /** Le quota du mois : la limite de l'établissement (sinon celle de l'instance), et le consommé compté sur le journal. */
    public JournalTextos.Quota quotaDe(String tenantId) {
        String mois = JournalTextos.moisDe(maintenant());
        int limite = jdbc.sql("SELECT monthly_limit FROM sms_quota WHERE tenant_id = ?").params(tenantId).query(Integer.class).optional().orElse(reglages.limiteParDefaut());
        Map<String, Object> c = jdbc.sql("SELECT COALESCE(SUM(segments), 0) AS segments, COUNT(*) AS messages FROM sms_message"
                + " WHERE tenant_id = ? AND substr(created_at, 1, 7) = ? AND provider_id IS NOT NULL").params(tenantId, mois).query().singleRow();
        return JournalTextos.Quota.de(mois, limite, ((Number) c.get("segments")).intValue(), ((Number) c.get("messages")).intValue());
    }

    public JournalTextos.Quota reglerQuota(String tenantId, Object limite) {
        int n = JournalTextos.exigerLimite(limite);
        jdbc.sql("INSERT INTO sms_quota (tenant_id, monthly_limit) VALUES (?, ?) ON CONFLICT(tenant_id) DO UPDATE SET monthly_limit = excluded.monthly_limit").params(tenantId, n).update();
        return quotaDe(tenantId);
    }

    // ── Envoi ─────────────────────────────────────────────────────────────

    public JournalTextos.Texto envoyer(JournalTextos.Demande d) {
        Segments.Decoupage decoupe = Segments.decouper(d.texte());
        KeyHolder cle = new GeneratedKeyHolder();
        jdbc.sql("INSERT INTO sms_message (tenant_id, phone_number, message, status, provider, created_at, contexte, expediteur, segments)"
                + " VALUES (?, ?, ?, 'en_attente', ?, ?, ?, ?, ?)")
                .params(d.tenantId(), d.numero(), d.texte(), reglages.fournisseur(), maintenant(), d.contexte(), d.expediteur(), decoupe.segments()).update(cle);
        long id = cle.getKey().longValue();
        // Le quota se vérifie avant le fournisseur : un texto refusé pour quota laisse sa trace, et
        // ne coûte rien. Le message **entier** est refusé s'il ne tient pas dans ce qui reste.
        JournalTextos.Quota quota = quotaDe(d.tenantId());
        if (quota.refuse(decoupe.segments())) {
            echouer(id, quota.motifDeRefus(decoupe));
            return lire(id).orElseThrow();
        }
        try {
            Textos.Envoi e = porteur.envoyer(d.numero(), d.texte(), d.expediteur());
            jdbc.sql("UPDATE sms_message SET status = ?, provider_id = ?, sent_at = ?, error_message = ? WHERE id = ?")
                    .params(e.statut(), e.id(), e.emisLe(), Textos.ECHOUE.equals(e.statut()) ? JournalTextos.REFUS_FOURNISSEUR : null, id).update();
        } catch (RuntimeException erreur) {
            String message = erreur.getMessage() == null ? erreur.toString() : erreur.getMessage();
            echouer(id, message.length() > 1000 ? message.substring(0, 1000) : message);
        }
        return lire(id).orElseThrow();
    }

    private void echouer(long id, String erreur) {
        jdbc.sql("UPDATE sms_message SET status = 'echoue', error_message = ? WHERE id = ?").params(erreur, id).update();
    }

    public Optional<JournalTextos.Texto> lire(long id) {
        return jdbc.sql("SELECT * FROM sms_message WHERE id = ?").params(id).query(DepotJournalTextos::texto).optional();
    }

    public List<JournalTextos.Texto> lister(String tenantId, String statut, String contexte, Integer limite) {
        StringBuilder sql = new StringBuilder("SELECT * FROM sms_message WHERE tenant_id = ?");
        List<Object> params = new ArrayList<>(List.of(tenantId));
        if (statut != null && !statut.isEmpty()) {
            sql.append(" AND status = ?");
            params.add(statut);
        }
        if (contexte != null && !contexte.isEmpty()) {
            sql.append(" AND contexte = ?");
            params.add(contexte);
        }
        sql.append(" ORDER BY created_at DESC, id DESC LIMIT ?");
        params.add(JournalTextos.limiteListe(limite));
        return jdbc.sql(sql.toString()).params(params).query(DepotJournalTextos::texto).list();
    }

    public Map<String, Object> resume(String tenantId) {
        return JournalTextos.resume(jdbc.sql("SELECT status, COUNT(*) AS n, MAX(created_at) AS dernier FROM sms_message WHERE tenant_id = ? GROUP BY status").params(tenantId).query().listOfRows());
    }

    /** Redemande le statut au fournisseur — pour un texto de cet établissement qui a un identifiant chez lui. */
    public JournalTextos.Texto rafraichir(String tenantId, long id) {
        JournalTextos.Texto t = lire(id).filter(x -> x.tenantId().equals(tenantId)).orElseThrow(() -> new IntrouvableException("texto introuvable"));
        if (t.identifiantFournisseur() == null) {
            throw new IllegalArgumentException("texto sans identifiant chez le fournisseur : rien à redemander");
        }
        String statut = porteur.statut(t.identifiantFournisseur());
        jdbc.sql("UPDATE sms_message SET status = ?, error_message = ? WHERE id = ?").params(statut, JournalTextos.erreurApresRappel(statut, null, t.erreur(), statut), id).update();
        return lire(id).orElseThrow();
    }

    /**
     * Le rappel du fournisseur ({@code StatusCallback} Twilio) : le statut brut se ramène aux nôtres,
     * l'erreur est gardée. Rend le nombre de lignes touchées — zéro pour un identifiant inconnu, ce
     * qui n'est pas une erreur : le rappel peut précéder l'écriture.
     */
    public int appliquerRappel(String identifiantFournisseur, String statutBrut, String erreur) {
        String statut = Textos.traduireStatut(statutBrut);
        int touchees = 0;
        for (JournalTextos.Texto t : jdbc.sql("SELECT * FROM sms_message WHERE provider_id = ?").params(identifiantFournisseur).query(DepotJournalTextos::texto).list()) {
            touchees += jdbc.sql("UPDATE sms_message SET status = ?, error_message = ? WHERE id = ?").params(statut, JournalTextos.erreurApresRappel(statut, erreur, t.erreur(), statutBrut), t.id()).update();
        }
        return touchees;
    }

    public static final class IntrouvableException extends RuntimeException {
        public IntrouvableException(String message) {
            super(message);
        }
    }
}
