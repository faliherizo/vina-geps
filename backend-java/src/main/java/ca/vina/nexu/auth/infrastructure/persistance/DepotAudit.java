package ca.vina.nexu.auth.infrastructure.persistance;

import ca.vina.nexu.auth.domaine.EvenementAudit;
import ca.vina.nexu.auth.domaine.RevueAudit;
import ca.vina.nexu.partage.audit.infrastructure.JournalAudit;
import ca.vina.nexu.partage.temps.Iso;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Lecture, revue, rétention et effacement du journal d'audit — port de {@code audit.ts} et
 * {@code retention.ts}. L'écriture chaînée reste à {@link JournalAudit}.
 */
@Repository
public class DepotAudit {

    private final JdbcClient jdbc;
    private final JournalAudit journal;
    private final Clock horloge;

    public DepotAudit(JdbcClient jdbc, JournalAudit journal, Clock horloge) {
        this.jdbc = jdbc;
        this.journal = journal;
        this.horloge = horloge;
    }

    private static EvenementAudit lire(ResultSet rs, int i) throws SQLException {
        return new EvenementAudit(rs.getString("id"), rs.getString("at"), rs.getString("userId"), rs.getString("username"),
                rs.getString("action"), rs.getString("target"), rs.getString("details"));
    }

    /** Les plus récentes d'abord, au plus 1 000 ({@code listAudit}). */
    public List<EvenementAudit> lister(String action, int limite) {
        int n = Math.min(limite, 1000);
        return action == null
                ? jdbc.sql("SELECT * FROM audit_log ORDER BY at DESC LIMIT ?").params(n).query(DepotAudit::lire).list()
                : jdbc.sql("SELECT * FROM audit_log WHERE action = ? ORDER BY at DESC LIMIT ?").params(action, n).query(DepotAudit::lire).list();
    }

    public Map<String, Object> integrite() {
        List<RevueAudit.Ligne> lignes = jdbc.sql("SELECT id, at, userId, username, action, target, details, empreinte FROM audit_log ORDER BY rowid")
                .query((rs, i) -> new RevueAudit.Ligne(lire(rs, i), rs.getString("empreinte"))).list();
        return RevueAudit.integrite(lignes, (precedente, e) -> JournalAudit.empreinteDe(precedente,
                new JournalAudit.Entree(e.id(), e.at(), e.userId(), e.username(), e.action(), e.target(), e.details())));
    }

    private static Map<String, Object> revue(ResultSet rs, int i) throws SQLException {
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("id", rs.getString("id"));
        r.put("du", rs.getString("du"));
        r.put("au", rs.getString("au"));
        r.put("entrees", rs.getInt("entrees"));
        r.put("empreinteFin", rs.getString("empreinteFin"));
        r.put("reviseurId", rs.getString("reviseurId"));
        r.put("reviseurNom", rs.getString("reviseurNom"));
        r.put("constat", rs.getString("constat"));
        r.put("faiteLe", rs.getString("faiteLe"));
        return r;
    }

    public List<Map<String, Object>> revues() {
        return jdbc.sql("SELECT * FROM audit_revues ORDER BY faiteLe DESC").query(DepotAudit::revue).list();
    }

    public Map<String, Object> echeance() {
        Optional<String> derniere = jdbc.sql("SELECT faiteLe FROM audit_revues ORDER BY faiteLe DESC LIMIT 1").query(String.class).optional();
        Optional<String> premiere = jdbc.sql("SELECT MIN(at) FROM audit_log").query(String.class).optional();
        return RevueAudit.echeance(derniere.orElse(null), premiere.orElse(null), horloge.instant());
    }

    /** Consigne une revue : la période lue, le nombre d'entrées, l'empreinte de fin, le constat — et la journalise. */
    public Map<String, Object> consignerRevue(String reviseurId, String reviseurNom, String du, String au, String constat) {
        RevueAudit.Demande d = RevueAudit.valider(du, au, constat);
        int entrees = jdbc.sql("SELECT COUNT(*) FROM audit_log WHERE at >= ? AND at <= ?").params(d.debutIso(), d.finIso()).query(Integer.class).single();
        String fin = jdbc.sql("SELECT empreinte FROM audit_log WHERE at <= ? AND empreinte IS NOT NULL ORDER BY rowid DESC LIMIT 1")
                .params(d.finIso()).query(String.class).optional().orElse(null);
        String id = UUID.randomUUID().toString();
        jdbc.sql("INSERT INTO audit_revues (id, du, au, entrees, empreinteFin, reviseurId, reviseurNom, constat, faiteLe) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)")
                .params(id, d.du(), d.au(), entrees, fin, reviseurId, reviseurNom, d.constat(), Iso.de(horloge.instant())).update();
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("entrees", entrees);
        details.put("empreinteFin", fin);
        journal.consigner(reviseurId, reviseurNom, "audit.revue", d.du() + ".." + d.au(), details);
        return jdbc.sql("SELECT * FROM audit_revues WHERE id = ?").params(id).query(DepotAudit::revue).single();
    }

    /** Supprime les entrées antérieures à la période de rétention ({@code purgeAuditOlderThan}). */
    public Map<String, Object> purger(double joursDeRetention) {
        if (Double.isNaN(joursDeRetention) || Double.isInfinite(joursDeRetention) || joursDeRetention <= 0) {
            throw new IllegalArgumentException("retentionDays must be > 0");
        }
        String seuil = Iso.de(horloge.instant().minus(Duration.ofMillis(Math.round(joursDeRetention * 86_400_000L))));
        int purgees = jdbc.sql("DELETE FROM audit_log WHERE at < ?").params(seuil).update();
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("cutoff", seuil);
        r.put("purged", purgees);
        r.put("retentionDays", joursDeRetention);
        return r;
    }

    /** Droit à l'effacement : anonymise nom et détails des entrées d'une personne, la trace technique reste. */
    public Map<String, Object> effacer(String username) {
        if (username == null || username.trim().isEmpty()) {
            throw new IllegalArgumentException("username required");
        }
        int n = jdbc.sql("UPDATE audit_log SET username = 'ERASED', details = NULL WHERE username = ?").params(username.trim()).update();
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("username", username.trim());
        r.put("anonymized", n);
        return r;
    }
}
