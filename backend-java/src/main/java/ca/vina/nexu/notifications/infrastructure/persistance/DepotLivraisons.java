package ca.vina.nexu.notifications.infrastructure.persistance;

import ca.vina.nexu.notifications.domaine.Livraisons;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** Les envois d'avis, lus dans {@code varpay_notifications} (colonnes {@code evenement} et {@code tentative} : migration 0159). */
@Repository
public class DepotLivraisons {

    private final JdbcClient jdbc;

    public DepotLivraisons(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public List<Livraisons.Envoi> lister(String tenantId, Livraisons.Filtre f, int limite) {
        StringBuilder sql = new StringBuilder("SELECT * FROM varpay_notifications WHERE tenantId = ?");
        List<Object> params = new ArrayList<>(List.of(tenantId));
        filtrer(sql, params, " AND evenement = ?", f.evenement());
        filtrer(sql, params, " AND channel = ?", f.canal());
        filtrer(sql, params, " AND status = ?", f.etat());
        filtrer(sql, params, " AND recipientId = ?", f.destinataire());
        params.add(limite);
        return jdbc.sql(sql + " ORDER BY createdAt DESC LIMIT ?").params(params).query(DepotLivraisons::envoi).list();
    }

    public Optional<Livraisons.Envoi> lire(String tenantId, String id) {
        return jdbc.sql("SELECT * FROM varpay_notifications WHERE tenantId = ? AND id = ?").params(tenantId, id)
                .query(DepotLivraisons::envoi).optional();
    }

    private static void filtrer(StringBuilder sql, List<Object> params, String clause, String valeur) {
        if (valeur != null && !valeur.isEmpty()) {
            sql.append(clause);
            params.add(valeur);
        }
    }

    private static Livraisons.Envoi envoi(ResultSet rs, int i) throws SQLException {
        int tentative = rs.getInt("tentative");
        return new Livraisons.Envoi(rs.getString("id"), rs.getString("evenement"), rs.getString("channel"), rs.getString("recipientType"),
                rs.getString("recipientId"), rs.getString("subject"), rs.getString("body"), rs.getString("status"), rs.getString("error"),
                tentative == 0 ? 1 : tentative, rs.getString("createdAt"), rs.getString("sentAt"));
    }
}
